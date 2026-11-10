package net.mehvahdjukaar.vista.client.web;

import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public class UrlUtils {
    //hack. we use these to go directly to streamable path. rest we dont bother
    private static final Set<String> LONG_VIDEO_EXTENSIONS = Set.of("mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "ts", "m3u8", "mpd");

    @Nullable
    public static URI createUri(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String s = url.trim();

        try {
            URI parsed = URI.create(s);
            if (parsed.getScheme() != null) {
                return hardcodedFixupLinks(parsed);
            }
        } catch (Exception ignored) {
        }
        try {
            return Paths.get(s).toUri();
        } catch (Exception ignored) {
            return null;
        }
    }

    //since i stumbled across this issue. Alters known links
    private static URI hardcodedFixupLinks(URI uri) {
        String host = uri.getHost();
        if (host == null || !(host.equals("archive.org") || host.equals("www.archive.org"))) return uri;
        String path = uri.getRawPath();
        if (path == null || !path.startsWith("/details/")) return uri;
        String itemAndFile = path.substring("/details/".length());
        if (!itemAndFile.contains("/")) return uri;
        return URI.create(uri.getScheme() + "://" + host + "/download/" + itemAndFile.replace("+", "%20"));
    }

    public static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    public static boolean hasFileExtension(URI url) {
        return !getFileExtension(url).isEmpty();
    }

    public static String getFileExtension(URI url) {
        String path = url.getPath();
        if (path == null) {
            return "";
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static Path asLocalFile(URI uri) throws MalformedURLException {
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

    public static boolean isLongVideoExtension(URI uri) {
        return isHttp(uri) && LONG_VIDEO_EXTENSIONS.contains(getFileExtension(uri));
    }

    public static String hashOf(URI uri) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(uri.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    //ffmpeg only says "failed". this is to differentiate for the forbidden/not found screens
    public static void checkReachable(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        try {
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Range", "bytes=0-0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new FileDownloadUtils.HttpStatusException(code, uri.toString());
            }
        } finally {
            conn.disconnect();
        }
    }
}
