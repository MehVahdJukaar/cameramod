package net.mehvahdjukaar.vista.client.web.ffmpeg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.mehvahdjukaar.moonlight.api.util.ArchiveUtils;
import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import net.mehvahdjukaar.moonlight.api.util.OsType;
import net.mehvahdjukaar.vista.VistaMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class FFmpegManager {

    private static final Path SOURCES_CONFIG_PATH = Paths.get("vista_ffmpeg_sources.json");
    private static final String SOURCES_RESOURCE_PATH = "/vista_ffmpeg_sources.json";
    // Bump when the bundled sources file changes in a way that must reach users who already have one on disk.
    private static final int SOURCES_CONFIG_VERSION = 2;

    private static final Path PROGRAM_FOLDER = Paths.get("vista_ffmpeg_bin");
    private static final OsType OS_TYPE = OsType.current();
    private static final String FFMPEG_FILE_NAME = OS_TYPE.executableName("ffmpeg");
    private static final String FFPROBE_FILE_NAME = OS_TYPE.executableName("ffprobe");
    private static final Path FFMPEG_PATH = PROGRAM_FOLDER.resolve(FFMPEG_FILE_NAME);
    private static final Path FFPROBE_PATH = PROGRAM_FOLDER.resolve(FFPROBE_FILE_NAME);

    private static final long VERSION_CHECK_TIMEOUT_SECONDS = 20;

    private static volatile int downloadProgress = -1;

    public static CompletableFuture<FFmpeg> getOrDownload(@Nullable String customUrl) {
        return CompletableFuture.supplyAsync(() -> initialize(customUrl));
    }

    public static int getDownloadProgress() {
        return downloadProgress;
    }

    private static FFmpeg initialize(@Nullable String customUrl) {
        FFmpeg ffmpeg;
        try {
            Files.createDirectories(PROGRAM_FOLDER);
            ffmpeg = findOrDownload(customUrl);
        } catch (Exception e) {
            throw new RuntimeException("FFmpeg setup failed. Aborting.", e);
        } finally {
            downloadProgress = -1;
        }
        return verified(ffmpeg);
    }

    private static FFmpeg findOrDownload(@Nullable String customUrl) throws IOException, InterruptedException {
        if (!hasManagedBinaries()) {
            if (customUrl == null) {
                FFmpeg system = detectSystemFFmpeg();
                if (system != null) return system;
            }
            List<String> urls = customUrl != null ? List.of(customUrl) : readDownloadUrls();
            downloadAndInstall(urls);
        }
        VistaMod.LOGGER.info("Using managed FFmpeg binaries at {}", FFMPEG_PATH.toAbsolutePath());
        return new FFmpeg(FFMPEG_PATH, FFPROBE_PATH);
    }

    // Runs both binaries once so a wrong-CPU build or a truncated archive fails here with a clear
    // message instead of much later as a decode error.
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

    public static boolean hasManagedBinaries() {
        return Files.exists(FFMPEG_PATH) && Files.exists(FFPROBE_PATH);
    }

    @Nullable
    public static FFmpeg detectSystemFFmpeg() {
        Path ffmpeg = OS_TYPE.findExecutable("ffmpeg");
        Path ffprobe = OS_TYPE.findExecutable("ffprobe");
        if (ffmpeg == null || ffprobe == null) return null;
        VistaMod.LOGGER.info("Using system FFmpeg from PATH: {} and {}", ffmpeg, ffprobe);
        return new FFmpeg(ffmpeg, ffprobe);
    }

    private static List<String> readDownloadUrls() throws IOException {
        ensureSourcesConfigUpToDate();

        JsonObject root = readSourcesConfig();
        String key = OS_TYPE.key();
        JsonElement value = root.get(key);
        if (value == null) {
            throw new IOException("Missing key '" + key + "' in " + SOURCES_CONFIG_PATH);
        }

        List<String> urls = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement e : value.getAsJsonArray()) {
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
            if (readSourcesConfigVersion() >= SOURCES_CONFIG_VERSION) return;
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

    private static int readSourcesConfigVersion() {
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
            moveExtractedBinariesIntoPlace();
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

        String extension = ArchiveUtils.detectExtension(raw);
        if (extension == null) {
            throw new IOException("Unrecognized archive format downloaded from " + url);
        }
        Path archive = raw.resolveSibling("download-" + index + extension);
        Files.move(raw, archive, StandardCopyOption.REPLACE_EXISTING);
        return archive;
    }

    private static void moveExtractedBinariesIntoPlace() throws IOException {
        Path ffmpeg = findExtractedFile(FFMPEG_FILE_NAME);
        Path ffprobe = findExtractedFile(FFPROBE_FILE_NAME);
        if (ffmpeg == null || ffprobe == null) {
            throw new IOException("Archives do not contain required binaries: "
                    + FFMPEG_FILE_NAME + ", " + FFPROBE_FILE_NAME);
        }
        Files.move(ffmpeg, FFMPEG_PATH, StandardCopyOption.REPLACE_EXISTING);
        Files.move(ffprobe, FFPROBE_PATH, StandardCopyOption.REPLACE_EXISTING);
    }

    @Nullable
    private static Path findExtractedFile(String fileName) throws IOException {
        try (Stream<Path> files = Files.walk(PROGRAM_FOLDER)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static void markExecutables() throws IOException {
        if (!FFMPEG_PATH.toFile().setExecutable(true) || !FFPROBE_PATH.toFile().setExecutable(true)) {
            throw new IOException("Could not mark FFmpeg binaries as executable");
        }
    }

}
