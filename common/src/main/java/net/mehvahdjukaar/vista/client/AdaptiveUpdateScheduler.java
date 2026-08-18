package net.mehvahdjukaar.vista.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.Map;

/**
 * Spreads a set of expensive per-object updates over frames, keeping their total cost near a ms
 * budget. Driven off render frames rather than ticks, and every object advances by the same rate each
 * frame so they all converge to the same frequency.
 */
public final class AdaptiveUpdateScheduler<ID> {

    private final class Entry {
        double phase01;
        long lastFrameSeen;

        Entry(ID id, long frame) {
            this.phase01 = stablePhaseFromId(id);
            this.lastFrameSeen = frame;
        }
    }

    private final Map<ID, Entry> entries = new HashMap<>();

    // normalized to per-frame values
    private final double baseUpdateRatePerFrame;
    private final double minUpdateRatePerFrame;
    private final double updateTimeTargetMs;
    private final double updateTimeSmoothingWindowMs;
    private final double scaleSmoothingTimeConstantMs;
    private final double maxScaleChangePerFrame;

    private final boolean useFpsGuard;
    private final double fpsGuardTargetFrameMs;
    private final double fpsEmaAlpha;
    private final double minFpsScale;
    private final int evictAfterFrames;

    public double smoothedAverageUpdateTimeMs = 0.0;
    private double smoothedAverageFrameTimeMs;
    private long thisFrameAccumulatedUpdateTimeNano = 0L;
    private double smoothedBudgetScale = 1.0;

    private long currentFrame = 0L;
    private double effectiveRateThisFrame = 1.0;

    public AdaptiveUpdateScheduler(
            double baseRatePerFrame,         // 0.1 = once every 10 frames baseline
            double minRatePerFrame,          // 0.01 = at worst once every 100 frames
            double targetBudgetMs,
            double smoothingWindowMs,
            double scaleSmoothingTimeConstantMs,
            double maxScaleChangePerFrame,
            boolean useFpsGuard,
            double fpsGuardTargetFrameMs,
            double fpsEmaAlpha,
            double minFpsScale,
            int evictAfterFrames
    ) {
        this.baseUpdateRatePerFrame = requirePos(baseRatePerFrame, "baseRatePerFrame");
        this.minUpdateRatePerFrame = requirePos(minRatePerFrame, "minRatePerFrame");
        this.updateTimeTargetMs = requirePos(targetBudgetMs, "targetBudgetMs");
        this.updateTimeSmoothingWindowMs = requirePos(smoothingWindowMs, "smoothingWindowMs");
        this.scaleSmoothingTimeConstantMs = requirePos(scaleSmoothingTimeConstantMs, "scaleSmoothingTimeConstantMs");
        this.maxScaleChangePerFrame = requirePos(maxScaleChangePerFrame, "maxScaleChangePerFrame");
        this.useFpsGuard = useFpsGuard;
        this.fpsGuardTargetFrameMs = useFpsGuard ? requirePos(fpsGuardTargetFrameMs, "fpsGuardTargetFrameMs") : 16.667;
        this.fpsEmaAlpha = requireAlpha(fpsEmaAlpha, "fpsEmaAlpha");
        this.minFpsScale = requireAlpha(minFpsScale, "minFpsScale");
        this.evictAfterFrames = evictAfterFrames;

        this.smoothedAverageFrameTimeMs = this.fpsGuardTargetFrameMs;
    }

    public double getAverageUpdateTimeMs() {
        return this.smoothedAverageUpdateTimeMs;
    }

    /**
     * Runs update only if this object's turn has come around, and charges it to the budget.
     */
    public void runIfShouldUpdate(ID id, Runnable update) {
        // not computeIfAbsent: this runs per object per frame and we'd rather not capture a lambda
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry(id, currentFrame);
            entries.put(id, e);
        }
        e.lastFrameSeen = currentFrame;

        if (stepPhaseAndGrant(e)) {
            long t0 = System.nanoTime();
            try {
                update.run();
            } finally {
                thisFrameAccumulatedUpdateTimeNano += (System.nanoTime() - t0);
            }
        }
    }

    private boolean stepPhaseAndGrant(Entry e) {
        double newPhase = e.phase01 + effectiveRateThisFrame;

        if (newPhase >= 1.0) {
            e.phase01 = newPhase - 1.0; // wrap rather than reset, so phases stay spread out
            return true;
        }

        e.phase01 = newPhase;
        return false;
    }

    /** Must be called exactly once at the very end of every rendered frame. */
    public void onEndOfFrame() {
        double lastFrameMs = Math.max(0.001, Minecraft.getInstance().getFrameTimeNs() / 1_000_000.0);

        double updateMsThisFrame = thisFrameAccumulatedUpdateTimeNano / 1_000_000.0;
        double alpha = 1.0 - Math.exp(-lastFrameMs / updateTimeSmoothingWindowMs);

        smoothedAverageUpdateTimeMs = (1.0 - alpha) * smoothedAverageUpdateTimeMs + alpha * updateMsThisFrame;
        thisFrameAccumulatedUpdateTimeNano = 0L;

        // headroom against the budget target
        double rawScale = (smoothedAverageUpdateTimeMs <= 0.0)
                ? 1.0
                : Mth.clamp(updateTimeTargetMs / smoothedAverageUpdateTimeMs, 0.0, 1.0);

        double beta = 1.0 - Math.exp(-lastFrameMs / scaleSmoothingTimeConstantMs);
        double targetScale = (1.0 - beta) * smoothedBudgetScale + beta * rawScale;

        // rate limited, otherwise frame pacing gets jittery
        double delta = Mth.clamp(targetScale - smoothedBudgetScale, -maxScaleChangePerFrame, +maxScaleChangePerFrame);
        smoothedBudgetScale += delta;

        if (useFpsGuard) {
            smoothedAverageFrameTimeMs = (1.0 - fpsEmaAlpha) * smoothedAverageFrameTimeMs + fpsEmaAlpha * lastFrameMs;
        }

        // rate is frozen for the whole upcoming frame so every object steps by the same amount
        currentFrame++;
        effectiveRateThisFrame = computeEffectiveUpdateRate();

        if ((currentFrame & 0xFF) == 0) {
            long cutoff = currentFrame - evictAfterFrames;
            entries.entrySet().removeIf(en -> en.getValue().lastFrameSeen < cutoff);
        }
    }

    private double computeEffectiveUpdateRate() {
        double scaled = Math.max(minUpdateRatePerFrame, baseUpdateRatePerFrame * smoothedBudgetScale);

        if (useFpsGuard) {
            double fpsScale = Mth.clamp(
                    fpsGuardTargetFrameMs / Math.max(smoothedAverageFrameTimeMs, fpsGuardTargetFrameMs),
                    0.0, 1.0
            );
            fpsScale = Math.max(minFpsScale, fpsScale);
            scaled = Math.max(minUpdateRatePerFrame, scaled * fpsScale);
        }

        return Mth.clamp(scaled, 0.0, 1.0);
    }

    public void forceUpdateNextTick(ID id) {
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry(id, currentFrame);
            entries.put(id, e);
        }
        e.lastFrameSeen = currentFrame;
        e.phase01 = 0.999999;
    }

    private static double stablePhaseFromId(Object id) {
        int x = (id == null) ? 0 : id.hashCode();
        long z = mix64(x);
        return (z >>> 11) * (1.0 / (1L << 53));
    }

    private static long mix64(int x) {
        long z = (x * 0x9E3779B9L) ^ 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double requirePos(double v, String name) {
        if (v <= 0) throw new IllegalArgumentException(name + " must be > 0");
        return v;
    }

    private static double requireAlpha(double v, String name) {
        if (v <= 0 || v > 1) throw new IllegalArgumentException(name + " must be in (0,1]");
        return v;
    }

    public static final class Builder {
        private double baseRatePerTick;    // updates/tick/object at healthy perf
        private double minRatePerTick;     // hard floor

        private double updateTimeTargetMs = 5.0;
        private double updateTimeSmoothingTimeWindowMs = 300; // EMA tau, real time
        private double scaleSmoothingTimeWindowMs = 350;
        private double maxScaleChangePerFrame = 0.08;

        private boolean useFpsGuard = false;
        private double fpsGuardTargetFrameMs = 16.667;
        private double fpsEmaAlpha = 0.2;
        private double minFpsScale = 0.2;

        private int evictAfterTicks = 20 * 5;

        public Builder baseRatePerTick(double v) {
            if (v <= 0) throw new IllegalArgumentException("baseRatePerTick must be > 0");
            this.baseRatePerTick = v; return this;
        }

        public Builder basePeriodTicks(int ticks) {
            if (ticks <= 0) throw new IllegalArgumentException("ticks must be > 0");
            return baseRatePerTick(1.0 / ticks);
        }

        public Builder baseFps(double fps) {
            if (fps <= 0) throw new IllegalArgumentException("fps must be > 0");
            return baseRatePerTick(fps / 20.0); // assuming 20 TPS
        }

        public Builder minRatePerTick(double v) {
            if (v <= 0) throw new IllegalArgumentException("minRatePerTick must be > 0");
            this.minRatePerTick = v; return this;
        }

        public Builder minPeriodTicks(int ticks) {
            if (ticks <= 0) throw new IllegalArgumentException("ticks must be > 0");
            return minRatePerTick(1.0 / ticks);
        }

        public Builder minFps(double fps) {
            if (fps <= 0) throw new IllegalArgumentException("fps must be > 0");
            return minRatePerTick(fps / 20.0); // assuming 20 TPS
        }

        public Builder targetBudgetMs(double v) {
            if (v <= 0) throw new IllegalArgumentException("targetBudgetMs must be > 0");
            this.updateTimeTargetMs = v; return this;
        }

        /** Budget as a share (0..1] of one frame at the given fps. */
        public Builder targetBudgetFromFps(double fps, double share) {
            if (fps <= 0) throw new IllegalArgumentException("fps must be > 0");
            if (share <= 0 || share > 1) throw new IllegalArgumentException("share must be in (0,1]");
            return targetBudgetMs((1000.0 / fps) * share);
        }

        public Builder smoothingTimeConstantMs(double v) {
            if (v <= 0) throw new IllegalArgumentException("smoothingTimeConstantMs must be > 0");
            this.updateTimeSmoothingTimeWindowMs = v; return this;
        }

        public Builder scaleSmoothingTimeConstantMs(double v) {
            if (v <= 0) throw new IllegalArgumentException("scaleSmoothingTimeConstantMs must be > 0");
            this.scaleSmoothingTimeWindowMs = v; return this;
        }

        public Builder maxScaleChangePerFrame(double v) {
            if (v <= 0 || v > 1) throw new IllegalArgumentException("maxScaleChangePerFrame must be in (0,1]");
            this.maxScaleChangePerFrame = v; return this;
        }

        public Builder guardTargetFps(double fps) {
            if (fps <= 0) throw new IllegalArgumentException("fps must be > 0");
            this.fpsGuardTargetFrameMs = 1000.0 / fps;
            this.useFpsGuard = true;
            return this;

        }

        public Builder fpsGuardAlpha(double alpha) {
            if (alpha <= 0 || alpha > 1) throw new IllegalArgumentException("fpsEmaAlpha must be in (0,1]");
            this.fpsEmaAlpha = alpha; return this;
        }

        public Builder minFpsScale(double v) {
            if (v <= 0 || v > 1) throw new IllegalArgumentException("minFpsScale must be in (0,1]");
            this.minFpsScale = v; return this;
        }

        public Builder evictAfterTicks(int v) {
            if (v < 1) throw new IllegalArgumentException("evictAfterTicks must be >= 1");
            this.evictAfterTicks = v; return this;
        }

        public <T> AdaptiveUpdateScheduler<T> build() {
            return new AdaptiveUpdateScheduler<>(
                    baseRatePerTick, minRatePerTick,
                    updateTimeTargetMs,
                    updateTimeSmoothingTimeWindowMs,
                    scaleSmoothingTimeWindowMs,
                    maxScaleChangePerFrame,
                    useFpsGuard, fpsGuardTargetFrameMs, fpsEmaAlpha, minFpsScale,
                    evictAfterTicks
            );
        }
    }

    public static Builder builder() { return new Builder(); }
}
