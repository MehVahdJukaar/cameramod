package net.mehvahdjukaar.vista.client.textures.perspective;

import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.client.AdaptiveUpdateScheduler;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

// keeps mirror renders within a frame budget, and small, far, nested, and idle mirrors update less often
public class MirrorUpdateScheduler {

    private static final double IMPORTANCE_FACTOR = 150.0;

    private static final Map<MirrorReflectionTexture, Entry> ENTRIES = new WeakHashMap<>();

    private static AdaptiveUpdateScheduler<ResourceLocation> scheduler;
    private static double builtBaseFps;
    private static double builtMinFps;
    private static double builtBudgetMs;

    private static int renderDepth;
    private static long renderStartNanos;

    private static AdaptiveUpdateScheduler<ResourceLocation> scheduler() {
        double baseFps = ClientConfigs.MIRROR_UPDATE_FPS.get();
        double minFps = Math.min(ClientConfigs.MIRROR_MIN_UPDATE_FPS.get(), baseFps);
        double budgetMs = ClientConfigs.MIRROR_THROTTLING_UPDATE_MS.get();

        if (scheduler == null || baseFps != builtBaseFps || minFps != builtMinFps || budgetMs != builtBudgetMs) {
            scheduler = AdaptiveUpdateScheduler.builder()
                    .baseFps(baseFps)
                    .minFps(minFps)
                    .targetBudgetMs(budgetMs)
                    .evictAfterTicks(20 * 5)
                    .guardTargetFps(60)
                    .build();
            builtBaseFps = baseFps;
            builtMinFps = minFps;
            builtBudgetMs = budgetMs;
            ENTRIES.clear();
        }
        return scheduler;
    }

    public static void onEndOfFrame() {
        scheduler().onEndOfFrame();
        renderDepth = 0;
    }

    public static boolean shouldRender(MirrorReflectionTexture text, MirrorBlockEntity mirror, Vec3 eye) {
        if (!scheduler().shouldUpdate(text.getTextureLocation())) return false;
        if (!text.hasRendered()) return true;

        Entry e = ENTRIES.computeIfAbsent(text, t -> new Entry());
        double scale = importanceScale(mirror, eye, text);

        double idleFps = Math.min(ClientConfigs.MIRROR_IDLE_UPDATE_FPS.get(), builtBaseFps);
        if (idleFps < builtBaseFps
                && e.lastRenderedEye != null && e.lastRenderedEye.distanceToSqr(eye) < 1.0E-8) {
            scale *= idleFps / builtBaseFps;
        }

        e.credit += scale;
        if (e.credit < 1.0) return false;
        e.credit -= 1.0;
        e.lastRenderedEye = eye;
        return true;
    }

    public static void beginRender() {
        if (renderDepth++ == 0) {
            renderStartNanos = System.nanoTime();
        }
    }

    public static void endRender() {
        renderDepth = Math.max(0, renderDepth - 1);
        if (renderDepth != 0) return;
        scheduler().addUpdateTime(System.nanoTime() - renderStartNanos);
    }

    private static double importanceScale(MirrorBlockEntity mirror, Vec3 eye, MirrorReflectionTexture text) {
        Vec2i connected = mirror.getConnectedCount();
        double area = Math.max(1, connected.x()) * (double) Math.max(1, connected.y());
        double distSq = Math.max(1.0, eye.distanceToSqr(Vec3.atCenterOf(mirror.getBlockPos())));
        double scale = IMPORTANCE_FACTOR * area / distSq;

        // lower lods are cheaper so they get some rate back
        int lod = MirrorTextureManager.distanceLod(eye, mirror.getBlockPos());
        if (lod > 0) {
            scale *= 1 << (2 * lod);
        }

        int depth = text.getRecursionDepth();
        if (depth > 0) {
            scale /= 1 << Math.min(depth, 3);
        }
        return Math.min(1.0, scale);
    }

    public static void clear() {
        ENTRIES.clear();
        renderDepth = 0;
    }

    private static final class Entry {
        double credit;
        Vec3 lastRenderedEye;
    }
}
