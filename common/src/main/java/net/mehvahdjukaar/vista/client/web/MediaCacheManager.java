package net.mehvahdjukaar.vista.client.web;

import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import net.mehvahdjukaar.vista.VistaMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disk cache for video files downloaded over HTTP. Maps a URL to a local file named after its
 * SHA-256, ref counts it so several TVs can share one file, and evicts least recently used entries
 * once the cache goes over the size limit. Thread safe, concurrent downloads of the same URL are
 * collapsed into one.
 */
public class MediaCacheManager {
    private static final String CACHE_SUBDIR = "vista_web_content_cache";
    private final Path cacheDir;
    private final long maxSizeBytes;
    private final Map<String, CachedEntry> urlToEntry = new ConcurrentHashMap<>();
    private final Map<Path, Integer> refCounts = new ConcurrentHashMap<>();
    private final Map<Path, Long> lastAccess = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Path>> pendingDownloads = new ConcurrentHashMap<>();
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

    /**
     * Obtains a local path for the given URL. HTTP/HTTPS URLs are downloaded and cached if not
     * already present. Local filesystem paths and file:// URLs are used directly, with no caching.
     */
    public Path getOrDownload(URI uri) throws Exception {
        return getOrDownload(uri, null, null);
    }

    public Path getOrDownload(URI uri, @Nullable FileDownloadUtils.ProgressCallback progressCallback) throws Exception {
        return getOrDownload(uri, progressCallback, null);
    }

    public Path getOrDownload(URI uri, @Nullable FileDownloadUtils.ProgressCallback progressCallback,
                              @Nullable FileDownloadUtils.RetryCallback retryCallback) throws Exception {
        // Handle local filesystem paths or file:// URLs without going through HTTP download/caching
        try {
            String scheme = uri.getScheme();
            if (scheme == null || scheme.equalsIgnoreCase("file")) {
                Path localPath = (scheme == null) ? Path.of(uri.toString()) : Path.of(uri);
                if (!Files.exists(localPath)) {
                    throw new IOException("Local media file does not exist: " + localPath);
                }
                VistaMod.LOGGER.info("Using local media file {} for URL {}", localPath, uri.toString());
                return localPath;
            }
        } catch (IllegalArgumentException ignored) {
            // If URI parsing fails, fall back to HTTP handling below
        }

        String key = hashUrl(uri.toString());
        // Fast path: already cached and available
        CachedEntry existing = urlToEntry.get(key);
        if (existing != null && Files.exists(existing.path)) {
            synchronized (this) {
                existing.refCount++;
                touch(existing.path);
            }
            VistaMod.LOGGER.info("Cache HIT for {} -> {} (refCount={})", uri.toString(), existing.path, existing.refCount);
            return existing.path;
        }

        CompletableFuture<Path> future = pendingDownloads.get(key);
        if (future != null && !future.isDone()) {
            VistaMod.LOGGER.info("Waiting for ongoing download of {}", uri.toString());
            return future.get(); // blocks until download finishes
        }

        // Start a new download on the caller's worker thread.
        // Serialized to prevent multiple downloads at once
        synchronized (downloadLock) {
            // Re-check after acquiring lock
            existing = urlToEntry.get(key);
            if (existing != null && Files.exists(existing.path)) {
                synchronized (this) {
                    existing.refCount++;
                    touch(existing.path);
                }
                return existing.path;
            }
            future = pendingDownloads.get(key);
            if (future != null && !future.isDone()) {
                return future.get();
            }

            CompletableFuture<Path> downloadFuture = new CompletableFuture<>();
            pendingDownloads.put(key, downloadFuture);

            try {
                Path cachedPath = downloadAndCache(uri.toString(), key, progressCallback, retryCallback);
                downloadFuture.complete(cachedPath);
                synchronized (this) {
                    urlToEntry.put(key, new CachedEntry(cachedPath, 1));
                    refCounts.put(cachedPath, 1);
                    touch(cachedPath);
                    enforceQuota();
                }
                VistaMod.LOGGER.info("Download & cache completed for {} -> {}", uri.toString(), cachedPath);
                return cachedPath;
            } catch (Exception e) {
                downloadFuture.completeExceptionally(e);
                throw e;
            } finally {
                pendingDownloads.remove(key);
            }
        }
    }

    /**
     * Releases a reference to a previously obtained URL. When refCount reaches zero, the file is deleted.
     */
    public void release(URI uri) {
        String key = hashUrl(uri.toString());
        CachedEntry entry = urlToEntry.get(key);
        if (entry == null) {
            VistaMod.LOGGER.warn("Release called for unknown URL: {}", uri.toString());
            return;
        }
        synchronized (this) {
            entry.refCount--;
            VistaMod.LOGGER.info("Released {}, new refCount={}", uri.toString(), entry.refCount);
            if (entry.refCount <= 0) {
                urlToEntry.remove(key);
                refCounts.remove(entry.path);
                lastAccess.remove(entry.path);
                try {
                    Files.deleteIfExists(entry.path);
                    VistaMod.LOGGER.info("Deleted cached file {} (no more references)", entry.path);
                } catch (IOException e) {
                    VistaMod.LOGGER.error("Failed to delete {}", entry.path, e);
                }
            }
        }
    }

    private Path downloadAndCache(String urlStr, String key, @Nullable FileDownloadUtils.ProgressCallback progressCallback,
                                  @Nullable FileDownloadUtils.RetryCallback retryCallback) throws Exception {
        // Deterministic client errors (403/404/...) fail fast inside moonlight's downloader now,
        // so a doomed request no longer sits through the ~30s retry backoff.
        Path cachedPath = cacheDir.resolve(key + ".video");
        FileDownloadUtils.download(urlStr, cachedPath, "Mozilla/5.0", progressCallback, retryCallback);
        return cachedPath;
    }

    private void restoreFromDisk() throws IOException {
        // Load existing files into cache map with refCount = 0 (they are not actively used)
        try (var stream = Files.list(cacheDir)) {
            List<Path> files = stream.toList();
            for (Path p : files) {
                if (Files.isRegularFile(p) && p.toString().endsWith(".video")) {
                    String key = p.getFileName().toString().replace(".video", "");
                    urlToEntry.put(key, new CachedEntry(p, 0));
                    refCounts.put(p, 0);
                    lastAccess.put(p, Files.getLastModifiedTime(p).toMillis());
                }
            }
        }
        VistaMod.LOGGER.info("Restored {} cached videos from disk", urlToEntry.size());
        enforceQuota(); // clean up if necessary
    }

    private void enforceQuota() {
        long total = 0;
        for (Path p : refCounts.keySet()) {
            try {
                total += Files.size(p);
            } catch (IOException ignored) {
            }
        }
        if (total <= maxSizeBytes) return;

        VistaMod.LOGGER.info("Cache size {} MB exceeds limit {} MB. Evicting...", total / (1024 * 1024), maxSizeBytes / (1024 * 1024));
        // LRU: sort by lastAccess, oldest first, only evict files with refCount == 0
        List<Map.Entry<Path, Long>> entries = new ArrayList<>(lastAccess.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        for (var e : entries) {
            Path p = e.getKey();
            if (refCounts.getOrDefault(p, 0) == 0) {
                try {
                    long size = Files.size(p);
                    Files.deleteIfExists(p);
                    total -= size;
                    // remove from all maps
                    String keyToRemove = null;
                    for (var entry : urlToEntry.entrySet()) {
                        if (entry.getValue().path.equals(p)) {
                            keyToRemove = entry.getKey();
                            break;
                        }
                    }
                    if (keyToRemove != null) urlToEntry.remove(keyToRemove);
                    refCounts.remove(p);
                    lastAccess.remove(p);
                    VistaMod.LOGGER.info("Evicted {} (size {} KB)", p, size / (1024));
                    if (total <= maxSizeBytes) break;
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void touch(Path p) {
        lastAccess.put(p, System.currentTimeMillis());
    }

    private String hashUrl(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private static class CachedEntry {
        final Path path;
        int refCount;

        CachedEntry(Path path, int refCount) {
            this.path = path;
            this.refCount = refCount;
        }
    }
}
