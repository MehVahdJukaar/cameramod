package net.mehvahdjukaar.vista.client.web;

import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import net.mehvahdjukaar.vista.VistaMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;

public class MediaCacheManager {
    private static final String CACHE_SUBDIR = "vista_web_content_cache";
    private static final String CACHED_FILE_EXT = ".video";
    private static final String PARTIAL_FILE_EXT = ".part";

    private final Path cacheDir;
    private final long maxSizeBytes;
    private final Map<String, CachedFile> filesByUrlHash = new HashMap<>();
    private final Object downloadLock = new Object();

    public MediaCacheManager(Path baseDir, long maxSizeBytes) {
        this.cacheDir = baseDir.resolve(CACHE_SUBDIR);
        this.maxSizeBytes = maxSizeBytes;
        try {
            Files.createDirectories(cacheDir);
            restoreFromDisk();
            VistaMod.LOGGER.info("Media Cache initialized at {}, max size = {} MB", cacheDir, maxSizeBytes / (1024 * 1024));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path getOrDownload(URI uri, @Nullable FileDownloadUtils.ProgressCallback progressCallback,
                              @Nullable FileDownloadUtils.RetryCallback retryCallback) throws Exception {
        Path localPath = asLocalFile(uri);
        if (localPath != null) {
            if (!Files.exists(localPath)) {
                throw new IOException("Local media file does not exist: " + localPath);
            }
            VistaMod.LOGGER.info("Using local media file {} for URL {}", localPath, uri);
            return localPath;
        }

        String key = hashUrl(uri.toString());
        Path cached = acquireIfCached(key);
        if (cached != null) {
            VistaMod.LOGGER.info("Cache HIT for {} -> {}", uri, cached);
            return cached;
        }

        //one download at a time
        synchronized (downloadLock) {
            //might have finished while we waited
            cached = acquireIfCached(key);
            if (cached != null) return cached;

            Path downloaded = downloadToCache(uri.toString(), key, progressCallback, retryCallback);

            synchronized (this) {
                CachedFile file = new CachedFile(downloaded, System.currentTimeMillis());
                file.users = 1;
                filesByUrlHash.put(key, file);
                enforceQuota();
            }
            VistaMod.LOGGER.info("Download & cache completed for {} -> {}", uri, downloaded);
            return downloaded;
        }
    }

    @Nullable
    private static Path asLocalFile(URI uri) throws MalformedURLException {
        String scheme = uri.getScheme();
        try {
            if (scheme == null) {
                return Path.of(uri.toString());
            }
            if (scheme.equalsIgnoreCase("file")) {
                return Path.of(uri);
            }
            return null;
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Bad local media path: " + uri);
        }
    }

    @Nullable
    private synchronized Path acquireIfCached(String key) {
        CachedFile file = filesByUrlHash.get(key);
        if (file == null || !Files.exists(file.path)) {
            return null;
        }
        file.users++;
        file.lastAccess = System.currentTimeMillis();
        return file.path;
    }

    public synchronized boolean isCached(URI uri) {
        CachedFile file = filesByUrlHash.get(hashUrl(uri.toString()));
        return file != null && Files.exists(file.path);
    }

    //unique name, two tvs with different sizes can stream same url at once
    public Path newPartialFile(URI uri) {
        return cacheDir.resolve(hashUrl(uri.toString()) + "-" + System.nanoTime() + PARTIAL_FILE_EXT);
    }

    public void finishPartialFile(URI uri, Path partial, boolean complete) {
        String key = hashUrl(uri.toString());
        try {
            if (!complete || isCached(uri)) {
                Files.deleteIfExists(partial);
                return;
            }
            Path target = cacheDir.resolve(key + CACHED_FILE_EXT);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);

            synchronized (this) {
                filesByUrlHash.put(key, new CachedFile(target, System.currentTimeMillis()));
                enforceQuota();
            }
            VistaMod.LOGGER.info("Cached streamed media {} -> {}", uri, target);
        } catch (IOException e) {
            VistaMod.LOGGER.warn("Failed to cache streamed media {}", uri, e);
        }
    }

    public synchronized void release(URI uri) {
        CachedFile file = filesByUrlHash.get(hashUrl(uri.toString()));
        if (file != null && file.users > 0) {
            file.users--;
        }
    }

    private Path downloadToCache(String url, String key, @Nullable FileDownloadUtils.ProgressCallback progressCallback,
                                 @Nullable FileDownloadUtils.RetryCallback retryCallback) throws Exception {
        Path partial = cacheDir.resolve(key + PARTIAL_FILE_EXT);
        Path target = cacheDir.resolve(key + CACHED_FILE_EXT);
        FileDownloadUtils.download(url, partial, "Mozilla/5.0", progressCallback, retryCallback);
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void restoreFromDisk() throws IOException {
        try (var stream = Files.list(cacheDir)) {
            for (Path p : stream.toList()) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }

                String name = p.getFileName().toString();

                if (name.endsWith(PARTIAL_FILE_EXT)) {
                    Files.deleteIfExists(p);
                } else if (name.endsWith(CACHED_FILE_EXT)) {
                    String key = name.substring(0, name.length() - CACHED_FILE_EXT.length());
                    filesByUrlHash.put(key, new CachedFile(p, Files.getLastModifiedTime(p).toMillis()));
                }
            }
        }
        VistaMod.LOGGER.info("Restored {} cached videos from disk", this.filesByUrlHash.size());
        enforceQuota();
    }

    private void enforceQuota() {
        long total = 0;
        for (CachedFile file : filesByUrlHash.values()) {
            try {
                total += Files.size(file.path);
            } catch (IOException ignored) {
            }
        }
        if (total <= maxSizeBytes) return;

        VistaMod.LOGGER.info("Cache size {} MB exceeds limit {} MB. Evicting...", total / (1024 * 1024), maxSizeBytes / (1024 * 1024));

        var oldestCachedFiles = new ArrayList<>(filesByUrlHash.entrySet());
        oldestCachedFiles.sort(Comparator.comparingLong(a -> a.getValue().lastAccess));

        for (var e : oldestCachedFiles) {
            CachedFile file = e.getValue();
            if (file.users > 0) {
                continue;
            }
            try {
                long size = Files.size(file.path);
                Files.deleteIfExists(file.path);

                total -= size;
                filesByUrlHash.remove(e.getKey());
                VistaMod.LOGGER.info("Evicted {} (size {} KB)", file.path, size / (1024));
                if (total <= maxSizeBytes) break;
            } catch (IOException ignored) {
            }
        }
    }

    private String hashUrl(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private static class CachedFile {
        private final Path path;
        private int users;
        private long lastAccess;

        private CachedFile(Path path, long lastAccess) {
            this.path = path;
            this.lastAccess = lastAccess;
        }
    }
}
