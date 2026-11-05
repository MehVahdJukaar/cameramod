package net.mehvahdjukaar.vista.client;

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
        //0..1, the update fires when this wraps past 1
        double phase;
        double lastRequestedMs;

        private Entry(ID id, double nowMs) {
            this.phase = stablePhaseFromId(id);
            lastRequestedMs = nowMs;
        }
    }

    private final Map<ID, Entry> entries = new HashMap<>();

    private final double maxUpdatesPerSecond;
    private final double minUpdatesPerSecond;

    //throttle when mirrors go slow
    private final double budgetMsPerFrame;
    private final double averageUpdateMsSmoothingMs;

    private final double budgetThrottleSmoothingMs;
    private final double budgetThrottleMaxChangePerFrame;

    //when enabled and game drops below 60fps we slow down still
    private final boolean fpsGuardEnabled;
    private final double fpsGuardFrameMs;
    private final double fpsGuardSmoothingMs;
    //the guard scales our scale. this is a minimum bound ,i.e 20%
    private final double fpsGuardMinScale;


    private final double evictUnseenAfterMs;

    //live state here

    private double averageUpdateMsPerFrame = 0.0;
    private double fpsGuardAverageFrameMs;
    private long updateNanosThisFrame = 0L;
    //1 = within budget, lower = over budget so slow down
    private double budgetThrottle = 1.0;

    private double elapsedMs = 0;
    private double lastFrameMs = 16.667;
    private long lastFrameEndNanos = -1L;
    private long frameCount = 0L;
    private double phaseStepThisFrame = 1.0;

    public AdaptiveUpdateScheduler(
            double maxUpdatesPerSecond,
            double minUpdatesPerSecond,
            double budgetMsPerFrame,
            double averageUpdateMsSmoothingMs,
            double budgetThrottleSmoothingMs,
            double budgetThrottleMaxChangePerFrame,
            boolean fpsGuardEnabled,
            double fpsGuardFrameMs,
            double fpsGuardSmoothingMs,
            double fpsGuardMinScale,
            double evictUnseenAfterMs
    ) {
        this.maxUpdatesPerSecond = maxUpdatesPerSecond;

        this.minUpdatesPerSecond = minUpdatesPerSecond;
        this.budgetMsPerFrame = budgetMsPerFrame;
        this.averageUpdateMsSmoothingMs = averageUpdateMsSmoothingMs;
        this.budgetThrottleSmoothingMs = budgetThrottleSmoothingMs;
        this.budgetThrottleMaxChangePerFrame = budgetThrottleMaxChangePerFrame;
        this.fpsGuardEnabled = fpsGuardEnabled;
        this.fpsGuardFrameMs = fpsGuardEnabled ? fpsGuardFrameMs : 16.667;
        this.fpsGuardSmoothingMs = fpsGuardSmoothingMs;
        this.fpsGuardMinScale = fpsGuardMinScale;
        this.evictUnseenAfterMs = evictUnseenAfterMs;

        this.fpsGuardAverageFrameMs = this.fpsGuardFrameMs;
    }

    public double getAverageUpdateMsPerFrame() {
        return this.averageUpdateMsPerFrame;
    }

    public void runIfShouldUpdate(ID id, Runnable update) {
        runIfShouldUpdate(id, Double.MAX_VALUE, update);
    }

    //maxUpdatesPerSecondForThis caps just this entry below the shared rate. it cant go above it
    public void runIfShouldUpdate(ID id, double maxUpdatesPerSecondForThis, Runnable update) {
        //not using computeIfAbsent since its a hot path
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry(id, elapsedMs);
            entries.put(id, e);
        }
        e.lastRequestedMs = elapsedMs;

        double step = Math.min(phaseStepThisFrame, maxUpdatesPerSecondForThis * lastFrameMs / 1000.0);
        if (advancePhaseAndCheckIfDue(e, step)) {
            long t0 = System.nanoTime();
            try {
                update.run();
            } finally {
                updateNanosThisFrame += (System.nanoTime() - t0);
            }
        }
    }


    private boolean advancePhaseAndCheckIfDue(Entry e, double step) {
        double newPhase = e.phase + step;

        if (newPhase >= 1.0) {
            //wrap
            e.phase = newPhase - 1.0;
            return true;
        }

        e.phase = newPhase;
        return false;
    }

    public void onEndOfFrame() {
        //not using mc.getFrameTimeNs, that one only times the render part and skips vsync wait and tick
        long now = System.nanoTime();
        if (lastFrameEndNanos > 0) lastFrameMs = Mth.clamp((now - lastFrameEndNanos) / 1_000_000.0, 0.001, 1000);
        lastFrameEndNanos = now;
        elapsedMs += lastFrameMs;

        double updateMsThisFrame = updateNanosThisFrame / 1_000_000.0;
        double averageUpdateAlpha = 1.0 - Math.exp(-lastFrameMs / averageUpdateMsSmoothingMs);

        averageUpdateMsPerFrame = (1.0 - averageUpdateAlpha) * averageUpdateMsPerFrame + averageUpdateAlpha * updateMsThisFrame;
        updateNanosThisFrame = 0L;

        double wantedBudgetThrottle = (averageUpdateMsPerFrame <= 0.0)
                ? 1.0
                : Mth.clamp(budgetMsPerFrame / averageUpdateMsPerFrame, 0.0, 1.0);

        double budgetThrottleAlpha = 1.0 - Math.exp(-lastFrameMs / budgetThrottleSmoothingMs);
        double smoothedBudgetThrottle = (1.0 - budgetThrottleAlpha) * budgetThrottle + budgetThrottleAlpha * wantedBudgetThrottle;

        //rate limit to avoid jitteryness
        double budgetThrottleChange = Mth.clamp(smoothedBudgetThrottle - budgetThrottle, -budgetThrottleMaxChangePerFrame, +budgetThrottleMaxChangePerFrame);
        budgetThrottle += budgetThrottleChange;

        if (fpsGuardEnabled) {
            double fpsGuardAlpha = 1.0 - Math.exp(-lastFrameMs / fpsGuardSmoothingMs);
            fpsGuardAverageFrameMs = (1.0 - fpsGuardAlpha) * fpsGuardAverageFrameMs + fpsGuardAlpha * lastFrameMs;
        }

        frameCount++;
        phaseStepThisFrame = Mth.clamp(throttledUpdatesPerSecond() * lastFrameMs / 1000.0, 0.0, 1.0);

        if ((frameCount & 0xFF) == 0) {
            double cutoff = elapsedMs - evictUnseenAfterMs;
            entries.entrySet().removeIf(en -> en.getValue().lastRequestedMs < cutoff);
        }
    }

    private double throttledUpdatesPerSecond() {
        double updatesPerSecond = Math.max(minUpdatesPerSecond, maxUpdatesPerSecond * budgetThrottle);

        if (fpsGuardEnabled) {
            double fpsGuardScale = Mth.clamp(
                    fpsGuardFrameMs / Math.max(fpsGuardAverageFrameMs, fpsGuardFrameMs),
                    0.0, 1.0
            );
            fpsGuardScale = Math.max(fpsGuardMinScale, fpsGuardScale);
            updatesPerSecond = Math.max(minUpdatesPerSecond, updatesPerSecond * fpsGuardScale);
        }

        return updatesPerSecond;
    }

    public void forceUpdateNextFrame(ID id) {
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry(id, elapsedMs);
            entries.put(id, e);
        }
        e.lastRequestedMs = elapsedMs;
        e.phase = 0.999999;
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
        private double maxUpdatesPerSecond;
        private double minUpdatesPerSecond;

        private double budgetMsPerFrame = 5.0;
        private double averageUpdateMsSmoothingMs = 300;
        private double budgetThrottleSmoothingMs = 350;
        private double budgetThrottleMaxChangePerFrame = 0.08;

        private boolean fpsGuardEnabled = false;
        private double fpsGuardFrameMs = 16.667;
        private double fpsGuardSmoothingMs = 75;
        private double fpsGuardMinScale = 0.2;

        private double evictUnseenAfterMs = 5000;

        public Builder maxUpdatesPerSecond(double updatesPerSecond) {
            this.maxUpdatesPerSecond = updatesPerSecond;
            return this;
        }

        //never throttled below this
        public Builder minUpdatesPerSecond(double updatesPerSecond) {
            this.minUpdatesPerSecond = updatesPerSecond;
            return this;
        }

        //all updates together may take this long per frame before throttling kicks in
        public Builder budgetMsPerFrame(double ms) {
            this.budgetMsPerFrame = ms;
            return this;
        }

        //share of one frame, 0..1
        public Builder budgetAsShareOfFrame(double fps, double share) {
            return budgetMsPerFrame((1000.0 / fps) * share);
        }

        public Builder averageUpdateMsSmoothingMs(double ms) {
            this.averageUpdateMsSmoothingMs = ms;
            return this;
        }

        public Builder budgetThrottleSmoothingMs(double ms) {
            this.budgetThrottleSmoothingMs = ms;
            return this;
        }

        public Builder budgetThrottleMaxChangePerFrame(double change) {
            this.budgetThrottleMaxChangePerFrame = change;
            return this;
        }

        //under this fps the update rate also gets scaled down by how far below we are
        public Builder slowDownBelowFps(double fps) {
            this.fpsGuardFrameMs = 1000.0 / fps;
            this.fpsGuardEnabled = true;
            return this;
        }


        public Builder fpsGuardSmoothingMs(double ms) {
            this.fpsGuardSmoothingMs = ms;
            return this;
        }

        public Builder fpsGuardMinScale(double scale) {
            this.fpsGuardMinScale = scale;
            return this;
        }

        public Builder evictUnseenAfterSeconds(double seconds) {
            this.evictUnseenAfterMs = seconds * 1000;
            return this;
        }

        public <T> AdaptiveUpdateScheduler<T> build() {
            return new AdaptiveUpdateScheduler<>(
                    maxUpdatesPerSecond, minUpdatesPerSecond,
                    budgetMsPerFrame,
                    averageUpdateMsSmoothingMs,
                    budgetThrottleSmoothingMs,
                    budgetThrottleMaxChangePerFrame,
                    fpsGuardEnabled, fpsGuardFrameMs, fpsGuardSmoothingMs, fpsGuardMinScale,
                    evictUnseenAfterMs
            );
        }
    }


    public static Builder builder() {
        return new Builder();
    }
}
