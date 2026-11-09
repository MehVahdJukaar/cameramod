package net.mehvahdjukaar.vista.client.web;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;


public enum MediaStatus {
    LOADING,
    READY,
    BUFFERING,
    COMPLETE,
    FAILED,
    CLOSED;

    public boolean isGood() {
        return this != FAILED && this != CLOSED;
    }

    public record Pair(MediaStatus state, @Nullable NativeImage frame) {
    }
}
