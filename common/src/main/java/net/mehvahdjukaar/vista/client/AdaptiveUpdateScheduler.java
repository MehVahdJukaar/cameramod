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
//complex math nonsense below
public class AdaptiveUpdateScheduler<ID> {

    private final class Entry {
        double phase01;
        long lastFrameSeen;

        private Entry(ID id, long frame) {
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
            double baseRatePerFrame,
            double minRatePerFrame,
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
        this.baseUpdateRatePerFrame = baseRatePerFrame;
        this.minUpdateRatePerFrame = minRatePerFrame;
        this.updateTimeTargetMs = targetBudgetMs;
        this.updateTimeSmoothingWindowMs = smoothingWindowMs;
        this.scaleSmoothingTimeConstantMs = scaleSmoothingTimeConstantMs;
        this.maxScaleChangePerFrame = maxScaleChangePerFrame;
        this.useFpsGuard = useFpsGuard;
        this.fpsGuardTargetFrameMs = useFpsGuard ? fpsGuardTargetFrameMs : 16.667;
        this.fpsEmaAlpha = fpsEmaAlpha;
        this.minFpsScale = minFpsScale;
        this.evictAfterFrames = evictAfterFrames;

        this.smoothedAverageFrameTimeMs = this.fpsGuardTargetFrameMs;
    }

    public double getAverageUpdateTimeMs() {
        return this.smoothedAverageUpdateTimeMs;
    }

    public void runIfShouldUpdate(ID id, Runnable update) {
        //not using computeIfAbsent since its a hot path
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

    // for when the caller runs the update itself, report its cost with addUpdateTime
    public boolean shouldUpdate(ID id) {
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry(id, currentFrame);
            entries.put(id, e);
        }
        e.lastFrameSeen = currentFrame;
        return stepPhaseAndGrant(e);
    }

    public void addUpdateTime(long nanos) {
        thisFrameAccumulatedUpdateTimeNano += nanos;
    }

    private boolean stepPhaseAndGrant(Entry e) {
        double newPhase = e.phase01 + effectiveRateThisFrame;

        if (newPhase >= 1.0) {
            //wrap
            e.phase01 = newPhase - 1.0;
            return true;
        }

        e.phase01 = newPhase;
        return false;
    }

    public void onEndOfFrame() {
        double lastFrameMs = Math.max(0.001, Minecraft.getInstance().getFrameTimeNs() / 1_000_000.0);

        double updateMsThisFrame = thisFrameAccumulatedUpdateTimeNano / 1_000_000.0;
        double alpha = 1.0 - Math.exp(-lastFrameMs / updateTimeSmoothingWindowMs);

        smoothedAverageUpdateTimeMs = (1.0 - alpha) * smoothedAverageUpdateTimeMs + alpha * updateMsThisFrame;
        thisFrameAccumulatedUpdateTimeNano = 0L;

        double rawScale = (smoothedAverageUpdateTimeMs <= 0.0)
                ? 1.0
                : Mth.clamp(updateTimeTargetMs / smoothedAverageUpdateTimeMs, 0.0, 1.0);

        double beta = 1.0 - Math.exp(-lastFrameMs / scaleSmoothingTimeConstantMs);
        double targetScale = (1.0 - beta) * smoothedBudgetScale + beta * rawScale;

        //rate limit to avoid jitteryness
        double delta = Mth.clamp(targetScale - smoothedBudgetScale, -maxScaleChangePerFrame, +maxScaleChangePerFrame);
        smoothedBudgetScale += delta;

        if (useFpsGuard) {
            smoothedAverageFrameTimeMs = (1.0 - fpsEmaAlpha) * smoothedAverageFrameTimeMs + fpsEmaAlpha * lastFrameMs;
        }

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

    //TODO: move in math helpers
    private static long mix64(int x) {
        long z = (x * 0x9E3779B9L) ^ 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static class Builder {
        private double baseRatePerTick;
        private double minRatePerTick;

        private double updateTimeTargetMs = 5.0;
        private double updateTimeSmoothingTimeWindowMs = 300;
        private double scaleSmoothingTimeWindowMs = 350;
        private double maxScaleChangePerFrame = 0.08;

        private boolean useFpsGuard = false;
        private double fpsGuardTargetFrameMs = 16.667;
        private double fpsEmaAlpha = 0.2;
        private double minFpsScale = 0.2;

        private int evictAfterTicks = 20 * 5;

        public Builder baseRatePerTick(double v) {
            this.baseRatePerTick = v;
            return this;
        }

        public Builder basePeriodTicks(int ticks) {
            return baseRatePerTick(1.0 / ticks);
        }

        public Builder baseFps(double fps) {
            return baseRatePerTick(fps / 20.0); // assuming 20 TPS
        }

        public Builder minRatePerTick(double v) {
            this.minRatePerTick = v;
            return this;
        }

        public Builder minPeriodTicks(int ticks) {
            return minRatePerTick(1.0 / ticks);
        }

        public Builder minFps(double fps) {
            return minRatePerTick(fps / 20.0);
        }

        public Builder targetBudgetMs(double v) {
            this.updateTimeTargetMs = v;
            return this;
        }

        //share of one frame, 0..1
        public Builder targetBudgetFromFps(double fps, double share) {
            return targetBudgetMs((1000.0 / fps) * share);
        }

        public Builder smoothingTimeConstantMs(double v) {
            this.updateTimeSmoothingTimeWindowMs = v;
            return this;
        }

        public Builder scaleSmoothingTimeConstantMs(double v) {
            this.scaleSmoothingTimeWindowMs = v;
            return this;
        }

        public Builder maxScaleChangePerFrame(double v) {
            this.maxScaleChangePerFrame = v;
            return this;
        }

        public Builder guardTargetFps(double fps) {
            this.fpsGuardTargetFrameMs = 1000.0 / fps;
            this.useFpsGuard = true;
            return this;
        }


        public Builder fpsGuardAlpha(double alpha) {
            this.fpsEmaAlpha = alpha;
            return this;
        }

        public Builder minFpsScale(double v) {
            this.minFpsScale = v;
            return this;
        }

        public Builder evictAfterTicks(int v) {
            this.evictAfterTicks = v;
            return this;
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


    public static Builder builder() {
        return new Builder();
    }
}
