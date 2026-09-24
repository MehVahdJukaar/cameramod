package net.mehvahdjukaar.vista.client.web;

import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpegManager;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URISyntaxException;


public enum MediaError {
    NONE,
    FORBIDDEN, //403
    NOT_FOUND, //404, 410
    BAD_LINK, //malformed
    NO_FFMPEG; //unavailable

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
            if (e instanceof MalformedURLException || e instanceof URISyntaxException) {
                return BAD_LINK;
            }
        }
        return NONE;
    }
}
