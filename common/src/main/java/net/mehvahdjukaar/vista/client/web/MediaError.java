package net.mehvahdjukaar.vista.client.web;

import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpegManager;
import org.jetbrains.annotations.Nullable;


public enum MediaError {
    /**
     * Failure with no specific category yet -> falls back to generic static noise.
     */
    NONE,
    /**
     * Server refused the request (HTTP 403).
     */
    FORBIDDEN,
    /**
     * Resource is gone / not on the server (HTTP 404 / 410).
     */
    NOT_FOUND,
    /**
     * The link itself is unusable: malformed, unsupported scheme, or missing local file.
     */
    BAD_LINK,
    /**
     * FFmpeg is unavailable, so the media can never be decoded (our side, not the channel's).
     */
    NO_FFMPEG;

    /**
     * Classifies a download/decode failure by walking the exception cause chain. HTTP failures
     * arrive as a typed {@link FileDownloadUtils.HttpStatusException} (status code), while bad
     * inputs surface as "Unsupported protocol" / "Malformed URL" messages or URL exceptions.
     */
    public static MediaError classify(@Nullable Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            if (e instanceof FileDownloadUtils.HttpStatusException http) {
                return switch (http.statusCode) {
                    case 401, 403 -> FORBIDDEN;
                    case 404, 410 -> NOT_FOUND;
                    default -> NONE;
                };
            }
            if (e instanceof FFmpegManager.UnusableFFmpegException) {
                return NO_FFMPEG;
            }
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Cannot run program") || msg.contains("Bad CPU type"))) {
                return NO_FFMPEG;
            }
            if (msg != null && (msg.contains("Unsupported protocol") || msg.contains("Malformed URL")
                    || msg.contains("does not exist"))) {
                return BAD_LINK;
            }
            if (e instanceof java.net.MalformedURLException || e instanceof java.net.URISyntaxException) {
                return BAD_LINK;
            }
        }
        return NONE;
    }
}
