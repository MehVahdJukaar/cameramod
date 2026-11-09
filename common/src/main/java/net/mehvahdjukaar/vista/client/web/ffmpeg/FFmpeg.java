package net.mehvahdjukaar.vista.client.web.ffmpeg;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

//our ffmpeg instance
public final class FFmpeg {

    private final Path ffprobePath;
    private final Path ffmpegPath;

    public FFmpeg(Path ffmpegPath, Path ffprobePath) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    public Process runFFmpeg(String... args) throws IOException {
        return run(ffmpegPath, args);
    }

    public Process runFFprobe(String... args) throws IOException {
        return run(ffprobePath, args);
    }

    public Process decode(String input, List<String> outputArgs) throws IOException {
        List<String> args = new ArrayList<>(List.of("-hide_banner", "-loglevel", "error"));
        args.addAll(inputArgs(input));
        args.addAll(outputArgs);
        return runFFmpeg(args.toArray(String[]::new));
    }

    public static List<String> inputArgs(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return List.of("-user_agent", "Mozilla/5.0", "-reconnect", "1", "-reconnect_streamed", "1", "-i", input);
        }
        return List.of("-i", input);
    }

    private Process run(Path bin, String... args) throws IOException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = bin.toString();
        System.arraycopy(args, 0, cmd, 1, args.length);
        return new ProcessBuilder(cmd)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

}
