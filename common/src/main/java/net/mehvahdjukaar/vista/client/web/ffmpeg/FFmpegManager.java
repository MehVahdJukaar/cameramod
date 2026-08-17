package net.mehvahdjukaar.vista.client.web.ffmpeg;

import com.google.gson.*;
import net.mehvahdjukaar.moonlight.api.util.ArchiveUtils;
import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import net.mehvahdjukaar.moonlight.api.util.OsType;
import net.mehvahdjukaar.vista.VistaMod;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class FFmpegManager {

    private static final Path SOURCES_CONFIG_PATH = Paths.get("vista_ffmpeg_sources.json");
    private static final String SOURCES_RESOURCE_PATH = "/vista_ffmpeg_sources.json";
    private static final Path PROGRAM_FOLDER = Paths.get("vista_ffmpeg_bin");
    // Bump when the bundled sources file changes in a way that must reach users who already have one on disk.
    private static final int SOURCES_CONFIG_VERSION = 2;
    private static volatile int downloadProgress = -1;

    private static final OsType OS_TYPE = OsType.current();
    private static final Path FFMPEG_PATH = PROGRAM_FOLDER.resolve(OS_TYPE.executableName("ffmpeg"));
    private static final Path FFPROBE_PATH = PROGRAM_FOLDER.resolve(OS_TYPE.executableName("ffprobe"));

    // A launcher started from Finder or the Dock gets a bare PATH, so a Homebrew ffmpeg is invisible there.
    private static final List<String> EXTRA_MAC_BIN_DIRS = List.of("/opt/homebrew/bin", "/usr/local/bin");

    private static final int MAX_MAGIC_LENGTH = 6;
    private static final long VERSION_CHECK_TIMEOUT_SECONDS = 20;

    public static CompletableFuture<FFmpeg> getOrDownload(@Nullable String customUrl) {
        return CompletableFuture.supplyAsync(() -> initialize(customUrl));
    }

    public static int getDownloadProgress() {
        return downloadProgress;
    }

    private static FFmpeg initialize(@Nullable String customUrl) {
        try {
            Files.createDirectories(PROGRAM_FOLDER);
            if (!hasRequiredFiles()) {
                // Prefer a system-wide install (in PATH) before downloading our own copy,
                // unless the user explicitly forced a custom download URL.
                if (customUrl == null) {
                    FFmpeg system = detectSystemFFmpeg();
                    if (system != null) {
                        downloadProgress = -1;
                        return verified(system);
                    }
                }
                downloadProgress = -1;
                List<String> urls = customUrl != null ? List.of(customUrl) : getDownloadUrlsFromSources();
                downloadAndInstall(urls);
            }
            downloadProgress = -1;
        } catch (Exception e) {
            downloadProgress = -1;
            throw new RuntimeException("FFmpeg setup failed. Aborting.", e);
        }
        VistaMod.LOGGER.info("Using managed FFmpeg binaries at {}", FFMPEG_PATH.toAbsolutePath());
        return verified(new FFmpeg(FFMPEG_PATH, FFPROBE_PATH));
    }

    /**
     * Runs both binaries once to prove they actually start on this machine. Downloading the wrong
     * build for the CPU, or a truncated archive, otherwise only shows up much later as a decode
     * failure with no useful message.
     */
    public static FFmpeg verified(FFmpeg ffmpeg) {
        checkRuns("ffmpeg", ffmpeg::runFFmpeg);
        checkRuns("ffprobe", ffmpeg::runFFprobe);
        return ffmpeg;
    }

    private static void checkRuns(String name, ProcessLauncher launcher) {
        Process process;
        try {
            process = launcher.launch("-version");
        } catch (IOException e) {
            throw new UnusableFFmpegException(name + " is present but will not start: " + e.getMessage()
                    + architectureHint(e), e);
        }
        try {
            if (!process.waitFor(VERSION_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new UnusableFFmpegException(name + " did not respond to -version within "
                        + VERSION_CHECK_TIMEOUT_SECONDS + "s", null);
            }
            if (process.exitValue() != 0) {
                throw new UnusableFFmpegException(name + " exited with code " + process.exitValue()
                        + " on -version", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnusableFFmpegException("Interrupted while checking " + name, e);
        }
    }

    // The mac sources only publish x86_64 builds, so an Apple Silicon machine without Rosetta 2 fails here.
    private static String architectureHint(IOException e) {
        String msg = e.getMessage();
        boolean wrongArchitecture = msg != null && msg.contains("Bad CPU type");
        if (!wrongArchitecture) return "";
        return ". The downloaded build does not match this CPU ("
                + System.getProperty("os.arch") + "). On Apple Silicon, install Rosetta 2"
                + " (softwareupdate --install-rosetta) or install FFmpeg yourself, e.g. brew install ffmpeg";
    }

    @FunctionalInterface
    private interface ProcessLauncher {
        Process launch(String... args) throws IOException;
    }

    public static class UnusableFFmpegException extends RuntimeException {
        public UnusableFFmpegException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    public static boolean hasRequiredFiles() {
        return Files.exists(FFMPEG_PATH) && Files.exists(FFPROBE_PATH);
    }

    /**
     * Looks for a system-wide FFmpeg install on the user's PATH. Both ffmpeg and ffprobe
     * must be present, otherwise we fall back to downloading our own copy.
     * Cheap (a handful of filesystem stats) and never downloads anything, so it's safe
     * to call on the main thread to decide whether a download is even needed.
     */
    @Nullable
    public static FFmpeg detectSystemFFmpeg() {
        Path ffmpeg = findInPath(OS_TYPE.executableName("ffmpeg"));
        Path ffprobe = findInPath(OS_TYPE.executableName("ffprobe"));
        if (ffmpeg != null && ffprobe != null) {
            VistaMod.LOGGER.info("Using system FFmpeg from PATH: {} and {}", ffmpeg, ffprobe);
            return new FFmpeg(ffmpeg, ffprobe);
        }
        return null;
    }

    @Nullable
    private static Path findInPath(String executableName) {
        for (String dir : binarySearchDirs()) {
            if (dir.isEmpty()) continue;
            try {
                Path candidate = Paths.get(dir).resolve(executableName);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath();
                }
            } catch (Exception ignored) {
                // malformed PATH entry, skip
            }
        }
        return null;
    }

    private static List<String> binarySearchDirs() {
        List<String> dirs = new ArrayList<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null && !pathEnv.isEmpty()) {
            Collections.addAll(dirs, pathEnv.split(File.pathSeparator));
        }
        if (OS_TYPE.isMac()) {
            dirs.addAll(EXTRA_MAC_BIN_DIRS);
        }
        return dirs;
    }

    private static List<String> getDownloadUrlsFromSources() throws IOException {
        ensureSourcesConfigUpToDate();

        JsonObject root = readSourcesConfig();
        String key = OS_TYPE.key();
        if (!root.has(key)) {
            throw new IOException("Missing key '" + key + "' in " + SOURCES_CONFIG_PATH);
        }

        JsonElement value = root.get(key);
        List<String> urls = new ArrayList<>();
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (JsonElement e : array) {
                urls.add(normalizeUrl(e.getAsString(), key));
            }
        } else {
            urls.add(normalizeUrl(value.getAsString(), key));
        }

        if (urls.isEmpty()) {
            throw new IOException("No URLs for key '" + key + "' in " + SOURCES_CONFIG_PATH);
        }
        return urls;
    }

    private static String normalizeUrl(String raw, String key) throws IOException {
        String url = raw.trim();
        if (url.isEmpty()) {
            throw new IOException("Empty URL for key '" + key + "' in " + SOURCES_CONFIG_PATH);
        }
        return url.startsWith("http") ? url : "https://" + url;
    }

    private static JsonObject readSourcesConfig() throws IOException {
        String json = Files.readString(SOURCES_CONFIG_PATH, StandardCharsets.UTF_8);
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IllegalStateException | JsonParseException e) {
            throw new IOException("Invalid JSON in " + SOURCES_CONFIG_PATH, e);
        }
    }

    private static void ensureSourcesConfigUpToDate() throws IOException {
        if (Files.exists(SOURCES_CONFIG_PATH)) {
            if (readConfigVersion() >= SOURCES_CONFIG_VERSION) return;
            // Old file has sources we know to be broken. Keep the user's copy around, but stop using it.
            Path backup = SOURCES_CONFIG_PATH.resolveSibling(SOURCES_CONFIG_PATH.getFileName() + ".old");
            Files.move(SOURCES_CONFIG_PATH, backup, StandardCopyOption.REPLACE_EXISTING);
            VistaMod.LOGGER.info("Replaced outdated {} with the current defaults. Old file kept as {}",
                    SOURCES_CONFIG_PATH, backup.getFileName());
        }

        try (InputStream in = FFmpegManager.class.getResourceAsStream(SOURCES_RESOURCE_PATH)) {
            if (in == null) {
                throw new IOException("Resource not found: " + SOURCES_RESOURCE_PATH);
            }
            Files.copy(in, SOURCES_CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int readConfigVersion() {
        try {
            JsonObject root = readSourcesConfig();
            return root.has("config_version") ? root.get("config_version").getAsInt() : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private static void downloadAndInstall(List<String> urls) throws IOException, InterruptedException {
        List<Path> archives = new ArrayList<>();
        try {
            for (int i = 0; i < urls.size(); i++) {
                archives.add(downloadArchive(urls.get(i), i, urls.size()));
            }
            for (Path archive : archives) {
                ArchiveUtils.extract(archive, PROGRAM_FOLDER);
            }
            moveRequiredBinariesFromProgramFolder();
            if (OS_TYPE.requiresExecutableBit()) {
                markExecutables();
            }
        } finally {
            for (Path archive : archives) {
                Files.deleteIfExists(archive);
            }
        }
    }

    private static Path downloadArchive(String url, int index, int total) throws IOException {
        Path raw = PROGRAM_FOLDER.resolve("download-" + index + ".bin");
        Files.deleteIfExists(raw);
        Files.deleteIfExists(raw.resolveSibling(raw.getFileName() + ".part"));

        int completedBefore = index * 100;
        FileDownloadUtils.download(url, raw, null,
                percent -> downloadProgress = (completedBefore + percent) / total);

        Path archive = PROGRAM_FOLDER.resolve("download-" + index + archiveExtension(raw));
        Files.move(raw, archive, StandardCopyOption.REPLACE_EXISTING);
        if (!ArchiveUtils.isSupported(archive)) {
            throw new IOException("Unrecognized archive format downloaded from " + url);
        }
        return archive;
    }

    // Leading bytes every file of that format starts with. See the "magic number" table in file(1).
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] XZ_MAGIC = {(byte) 0xFD, '7', 'z', 'X', 'Z', 0};
    private static final byte[] GZIP_MAGIC = {0x1F, (byte) 0x8B};
    private static final byte[] BZIP2_MAGIC = {'B', 'Z', 'h'};

    private static String archiveExtension(Path file) throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(MAX_MAGIC_LENGTH);
        }
        if (startsWith(head, ZIP_MAGIC)) return ".zip";
        if (startsWith(head, XZ_MAGIC)) return ".tar.xz";
        if (startsWith(head, GZIP_MAGIC)) return ".tar.gz";
        if (startsWith(head, BZIP2_MAGIC)) return ".tar.bz2";
        return ".unknown";
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        return Arrays.equals(data, 0, magic.length, magic, 0, magic.length);
    }

    private static void moveRequiredBinariesFromProgramFolder() throws IOException {
        Path ffmpeg = null;
        Path ffprobe = null;

        try (Stream<Path> stream = Files.walk(PROGRAM_FOLDER)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                String name = p.getFileName().toString();
                if (name.equals(FFMPEG_PATH.getFileName().toString())) {
                    ffmpeg = p;
                } else if (name.equals(FFPROBE_PATH.getFileName().toString())) {
                    ffprobe = p;
                }
                if (ffmpeg != null && ffprobe != null) {
                    break;
                }
            }
        }

        if (ffmpeg == null || ffprobe == null) {
            throw new IOException("Archives do not contain required binaries: "
                    + FFMPEG_PATH.getFileName() + ", " + FFPROBE_PATH.getFileName());
        }

        Files.move(ffmpeg, FFMPEG_PATH, StandardCopyOption.REPLACE_EXISTING);
        Files.move(ffprobe, FFPROBE_PATH, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void markExecutables() throws IOException {
        if (!FFMPEG_PATH.toFile().setExecutable(true) || !FFPROBE_PATH.toFile().setExecutable(true)) {
            throw new IOException("Could not mark FFmpeg binaries as executable");
        }
    }

}
