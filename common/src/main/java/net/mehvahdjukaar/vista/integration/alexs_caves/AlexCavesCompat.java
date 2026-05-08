package net.mehvahdjukaar.vista.integration.alexs_caves;

public class AlexCavesCompat {

    public static Runnable decorateRenderWithoutACShaders(Runnable task) {

        return () -> {
            task.run();
        };
    }
}
