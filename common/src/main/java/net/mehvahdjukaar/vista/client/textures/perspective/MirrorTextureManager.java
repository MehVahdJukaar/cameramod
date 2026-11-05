package net.mehvahdjukaar.vista.client.textures.perspective;

import com.google.common.base.Suppliers;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.client.util.LOD;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.AdaptiveUpdateScheduler;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.sable.SableCompatClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class MirrorTextureManager {

    private static final Map<String, Pending> PENDING = new HashMap<>();

    public static final Supplier<AdaptiveUpdateScheduler<ResourceLocation>> SCHEDULER =
            Suppliers.memoize(() ->
                    AdaptiveUpdateScheduler.builder()
                            .maxUpdatesPerSecond(ClientConfigs.MIRROR_MAX_UPDATE_FPS.get())
                            .minUpdatesPerSecond(ClientConfigs.MIRROR_MIN_UPDATE_FPS.get())
                            .budgetMsPerFrame(ClientConfigs.MIRROR_THROTTLING_UPDATE_MS.get())
                            .evictUnseenAfterSeconds(5)
                            .slowDownBelowFps(60)
                            .build()
            );

    private record Pending(MirrorBlockEntity mirror, Vec2i screenSize, Vec3 eye,
                           int depth, List<UUID> parentChain, int lod) {
    }

    private static int scaledResolution(int baseSize, int depth, int lod) {
        int scaled = baseSize * ClientConfigs.MIRROR_RESOLUTION_SCALE.get();
        if (depth > 0) {
            double divider = Math.pow(ClientConfigs.MIRROR_RECURSION_RES_DIVIDER.get(), depth);
            scaled = (int) (scaled / divider);
        }
        scaled >>= lod;
        return Math.max(1, scaled);
    }

    // Texture resolution LOD, halving per level. Does not touch MIRROR_RENDER_DISTANCE.
    //a distant mirror still renders its full scene, just into a smaller target.
    public static int distanceLod(LOD lod) {
        if (lod.within(24)) return 0;
        if (lod.within(40)) return 1;
        return 2;
    }

    public static int distanceLod(MirrorBlockEntity mirror) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera;
        if (SableCompatClient.isOnSubLevel(mirror)) {
            return distanceLod(SableCompatClient.projectIntoSubLevel(mirror, camera.getPosition()), mirror.getBlockPos());
        }
        return distanceLod(LOD.at(camera, mirror.getBlockPos()));
    }

    public static int distanceLod(Vec3 eye, BlockPos mirrorPos) {
        double distSq = eye.distanceToSqr(Vec3.atCenterOf(mirrorPos));
        if (distSq <= 24 * 24) return 0;
        if (distSq <= 40 * 40) return 1;
        return 2;
    }

    private static String chainKey(UUID self, List<UUID> parentChain) {
        if (parentChain.isEmpty()) return self.toString();
        StringBuilder sb = new StringBuilder(parentChain.size() * 37 + 36);
        for (UUID u : parentChain) sb.append(u).append('_');
        sb.append(self);
        return sb.toString();
    }

    @Nullable
    public static MirrorReflectionTexture getMirrorTexture(UUID uuid, Vec2i screenSize, int lod) {
        int w = scaledResolution(screenSize.x(), 0, lod);
        int h = scaledResolution(screenSize.y(), 0, lod);
        // size is part of the id, so crossing a LOD band just resolves to a different cached texture
        ResourceLocation textureId = VistaMod.res(
                "mirror_" + uuid + "_" + w + "x" + h);
        return DynamicTextureRenderer.requestTexture(textureId, () ->
                new MirrorReflectionTexture(textureId, w, h, uuid, 0, List.of()));
    }

    @Nullable
    public static MirrorReflectionTexture getMirrorTextureForChain(UUID uuid, Vec2i screenSize,
                                                                    int depth, List<UUID> parentChain) {
        int w = scaledResolution(screenSize.x(), depth, 0);
        int h = scaledResolution(screenSize.y(), depth, 0);
        String name = "mirror_chain_" + chainKey(uuid, parentChain) + "_" + w + "x" + h + "_d" + depth;
        ResourceLocation textureId = VistaMod.res(name);
        final List<UUID> capturedChain = List.copyOf(parentChain);
        return DynamicTextureRenderer.requestTexture(textureId, () ->
                new MirrorReflectionTexture(textureId, w, h, uuid, depth, capturedChain));
    }
    @Nullable
    public static MirrorReflectionTexture getMirrorTexture(MirrorBlockEntity mirror, Vec2i screenSize, Vec3 eye, int lod) {
        MirrorReflectionTexture texture = getMirrorTexture(mirror.getId(), screenSize, lod);
        if (texture == null) return null;
        if (ClientConfigs.MIRROR_UPDATE_MODE.get() != ClientConfigs.MirrorUpdateMode.TEXTURE_REFRESH) {
            requestUpdate(mirror, screenSize, eye, 0, List.of(), lod);
        } else {
            texture.setPending(mirror, eye);
            texture.setUpdateNextTick(true);
        }
        return texture.hasRenderedOnce() ? texture : null;
    }

    @Nullable
    public static MirrorReflectionTexture getMirrorTextureForChain(MirrorBlockEntity mirror, Vec2i screenSize,
                                                                    Vec3 eye, int depth, List<UUID> parentChain) {
        MirrorReflectionTexture texture = getMirrorTextureForChain(mirror.getId(), screenSize, depth, parentChain);
        if (texture == null) return null;
        requestUpdate(mirror, screenSize, eye, depth, parentChain, 0);
        return texture.hasRenderedOnce() ? texture : null;
    }

    private static void requestUpdate(MirrorBlockEntity mirror, Vec2i screenSize, Vec3 eye,
                                      int depth, List<UUID> parentChain, int lod) {
        String key = "d" + depth + "/" + chainKey(mirror.getId(), parentChain);
        PENDING.put(key, new Pending(mirror, screenSize, eye, depth, parentChain, lod));
    }

    public static void onRenderTickEnd() {
        processPending();

        SCHEDULER.get().onEndOfFrame();
    }

    private static void processPending() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PENDING.clear();
            return;
        }
        List<Pending> snapshot = new ArrayList<>(PENDING.values());
        PENDING.clear();
        for (Pending p : snapshot) {
            MirrorReflectionTexture text = p.depth == 0
                    ? getMirrorTexture(p.mirror.getId(), p.screenSize, p.lod)
                    : getMirrorTextureForChain(p.mirror.getId(), p.screenSize, p.depth, p.parentChain);
            if (text != null) {
                text.maybeRenderReflection(p.mirror, p.eye);
            }
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
