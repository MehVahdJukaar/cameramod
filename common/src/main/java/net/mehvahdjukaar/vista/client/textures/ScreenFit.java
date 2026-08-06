package net.mehvahdjukaar.vista.client.textures;

public record ScreenFit(ScalingMode mode, float frameAspectRatio) {

    public static final ScreenFit FILL = new ScreenFit(ScalingMode.STRETCH, 0);

    public record Bounds(float quadScaleX, float quadScaleY, float uScale, float vScale) {
        public static final Bounds FULL = new Bounds(1, 1, 1, 1);
    }

    public Bounds computeBounds(float screenAspectRatio) {
        if (mode == ScalingMode.STRETCH || frameAspectRatio <= 0 || screenAspectRatio <= 0) return Bounds.FULL;
        // above 1 when the frame is wider than the screen
        float ratio = frameAspectRatio / screenAspectRatio;
        if (ratio == 1) return Bounds.FULL;
        return switch (mode) {
            case CONTAIN -> contain(ratio);
            case COVER -> cover(ratio);
            // these pin one axis to the screen, so they end up letterboxing or cropping the other
            // one depending on which way the frame leans
            case FIT_WIDTH -> ratio > 1 ? contain(ratio) : cover(ratio);
            case FIT_HEIGHT -> ratio > 1 ? cover(ratio) : contain(ratio);
            case STRETCH -> Bounds.FULL;
        };
    }

    private static Bounds contain(float ratio) {
        return ratio > 1 ? new Bounds(1, 1 / ratio, 1, 1) : new Bounds(ratio, 1, 1, 1);
    }

    private static Bounds cover(float ratio) {
        return ratio > 1 ? new Bounds(1, 1, 1 / ratio, 1) : new Bounds(1, 1, 1, ratio);
    }
}
