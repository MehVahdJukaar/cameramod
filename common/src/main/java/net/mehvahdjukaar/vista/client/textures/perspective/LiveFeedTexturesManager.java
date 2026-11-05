package net.mehvahdjukaar.vista.client.textures.perspective;

import com.google.common.base.Suppliers;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.AdaptiveUpdateScheduler;
import net.mehvahdjukaar.vista.client.renderer.ScreenDebugText;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.VisibleForDebug;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

public class LiveFeedTexturesManager {

    @VisibleForDebug
    public static final Supplier<AdaptiveUpdateScheduler<ResourceLocation>> SCHEDULER =
            Suppliers.memoize(() ->
                    AdaptiveUpdateScheduler.builder()
                            .maxUpdatesPerSecond(ClientConfigs.MAX_UPDATE_FPS.get())
                            .minUpdatesPerSecond(ClientConfigs.MIN_UPDATE_FPS.get())
                            .budgetMsPerFrame(ClientConfigs.THROTTLING_UPDATE_MS.get()) //10% of a frame which at 60fps = 16.6ms is ~1.66ms which should lower fps from 60 to 54. in other words at most a 6fps drop
                            .evictUnseenAfterSeconds(5)
                            .slowDownBelowFps(60) //if we go under 60 fps, be more aggressive
                            .build()
            );

    public static class Handle {

        private final ResourceLocation textureId;
        private final UUID uuid;
        private final Vec2i screenSize;

        public Handle(UUID uuid, Vec2i screenSize) {
            this.textureId = VistaMod.res("live_feed_" + uuid + "_" + screenSize.x() + "x" + screenSize.y());
            this.uuid = uuid;
            this.screenSize = screenSize;
        }

        @Nullable
        public LiveFeedTexture getTexture(@Nullable ResourceLocation postShader, boolean requiresUpdate, boolean showsTime) {

            LiveFeedTexture texture = DynamicTextureRenderer.requestTexture(textureId, () ->
                    new LiveFeedTexture(textureId,
                            screenSize.x() * ClientConfigs.LIVE_FEED_RESOLUTION_SCALE.get(),
                            screenSize.y() * ClientConfigs.LIVE_FEED_RESOLUTION_SCALE.get(),
                            uuid));


            if (texture == null) {
                SCHEDULER.get().forceUpdateNextFrame(textureId);
                return null;
            }

            texture.markReferenced(requiresUpdate);

            if (texture.setPostChain(postShader)) {
                requiresUpdate = true;
            }
            if (requiresUpdate && !canUpdateFromCurrentPass(uuid)) {
                requiresUpdate = false;
            }
            texture.setShowsTime(showsTime);
            if (requiresUpdate) {
                texture.setUpdateNextTick(true);
            }
            return texture;
        }
    }

    private static boolean canUpdateFromCurrentPass(UUID uuid) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            if (VistaLevelRenderer.isFeedOnRenderStack(uuid)) {
                return false;
            }
            if (ClientConfigs.MIRROR_RECURSION_MODE.get() == ClientConfigs.MirrorRecursionMode.OFF) {
                return false;
            }
            return VistaLevelRenderer.getCurrentDepth() <= ClientConfigs.MIRROR_MAX_RECURSION_DEPTH.get();
        } else {
            return true;
        }
    }

    public static Handle createHandle(UUID uuid, Vec2i screenSize) {
        return new Handle(uuid, screenSize);
    }

    @SuppressWarnings("ConstantConditions")
    public static void clear() {
        ScreenDebugText.clear();
        //TODO: change just invalidate our own
        DynamicTextureRenderer.clearCache();
    }

    public static void onRenderTickEnd() {
        SCHEDULER.get().onEndOfFrame();
    }
}
