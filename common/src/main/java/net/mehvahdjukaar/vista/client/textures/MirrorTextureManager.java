package net.mehvahdjukaar.vista.client.textures;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.client.util.LOD;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Texture cache lookups and the pending render queue for mirror reflections. The rendering itself
 * lives on {@link MirrorReflectionTexture}.
 * <p>
 * Entries are keyed by chain, so in RECURSIVE mode one physical mirror seen through different parent
 * chains queues independently and gets its own off-axis render per chain.
 */
public class MirrorTextureManager {

    private static final Map<String, Pending> PENDING = new HashMap<>();

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

    // Texture resolution LOD, halving per level. Does not touch MIRROR_RENDER_DISTANCE, a distant
    // mirror still renders its full scene, just into a smaller target.
    public static int distanceLod(LOD lod) {
        if (lod.within(24)) return 0;
        if (lod.within(40)) return 1;
        return 2;
    }

    // Same, but always from the main camera, for use inside a nested render where the dispatcher
    // camera is the reflected one. For a mirror riding a Sable ship the camera has to be pulled into
    // plot space first, since comparing it to a plot position directly is meaningless.
    public static int distanceLod(MirrorBlockEntity mirror) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera;
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(mirror);
        if (subLevel != null) {
            return distanceLod(subLevel.renderPose().transformPositionInverse(camera.getPosition()),
                    mirror.getBlockPos());
        }
        return distanceLod(LOD.at(camera, mirror.getBlockPos()));
    }

    // eye must already be in the mirror's own space, which for sublevel mirrors is plot space
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
        // chains attenuate by depth only, distance LOD doesn't apply
        int w = scaledResolution(screenSize.x(), depth, 0);
        int h = scaledResolution(screenSize.y(), depth, 0);
        String name = "mirror_chain_" + chainKey(uuid, parentChain) + "_" + w + "x" + h + "_d" + depth;
        ResourceLocation textureId = VistaMod.res(name);
        // freeze the chain: textures are cached by id, so every later lookup must hand back one whose
        // parentChain still matches what VistaLevelRenderer pushes
        final List<UUID> capturedChain = List.copyOf(parentChain);
        return DynamicTextureRenderer.requestTexture(textureId, () ->
                new MirrorReflectionTexture(textureId, w, h, uuid, depth, capturedChain));
    }

    // Direct-view scheduling entry point. Returns null until the first draw has landed, since the
    // freshly allocated framebuffer would otherwise show up as a white flash.
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
        return texture.hasRendered() ? texture : null;
    }

    // Called from the BE renderer when it's already running inside another mirror's reflection. Always
    // defers instead of taking the TEXTURE_REFRESH fast path: rendering synchronously from inside the
    // BE pass would corrupt vanilla's in-flight bufferSource.
    @Nullable
    public static MirrorReflectionTexture getMirrorTextureForChain(MirrorBlockEntity mirror, Vec2i screenSize,
                                                                    Vec3 eye, int depth, List<UUID> parentChain) {
        MirrorReflectionTexture texture = getMirrorTextureForChain(mirror.getId(), screenSize, depth, parentChain);
        if (texture == null) return null;
        requestUpdate(mirror, screenSize, eye, depth, parentChain, 0);
        return texture.hasRendered() ? texture : null;
    }

    private static void requestUpdate(MirrorBlockEntity mirror, Vec2i screenSize, Vec3 eye,
                                      int depth, List<UUID> parentChain, int lod) {
        String key = "d" + depth + "/" + chainKey(mirror.getId(), parentChain);
        PENDING.put(key, new Pending(mirror, screenSize, eye, depth, parentChain, lod));
    }

    public static void processPending() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PENDING.clear();
            return;
        }
        // Snapshot and clear first: each render walks block entities, and any mirror in there calls
        // requestUpdate again. Those re-queues land in a fresh PENDING for next frame instead of CMEing.
        List<Pending> snapshot = new ArrayList<>(PENDING.values());
        PENDING.clear();
        for (Pending p : snapshot) {
            MirrorReflectionTexture text = p.depth == 0
                    ? getMirrorTexture(p.mirror.getId(), p.screenSize, p.lod)
                    : getMirrorTextureForChain(p.mirror.getId(), p.screenSize, p.depth, p.parentChain);
            if (text != null) text.renderReflection(p.mirror, p.eye);
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
