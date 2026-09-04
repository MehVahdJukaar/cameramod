package net.mehvahdjukaar.vista.client.textures;

import com.google.common.base.Suppliers;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.misc.RollingBuffer;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.AdaptiveUpdateScheduler;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.VisibleForDebug;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class LiveFeedTexturesManager {

    @VisibleForDebug
    public static final Map<UUID, RollingBuffer<Long>> UPDATE_TIMES = new HashMap<>();

    @VisibleForDebug
    public static final Supplier<AdaptiveUpdateScheduler<ResourceLocation>> SCHEDULER =
            Suppliers.memoize(() ->
                    AdaptiveUpdateScheduler.builder()
                            .baseFps(ClientConfigs.UPDATE_FPS.get())
                            .minFps(ClientConfigs.MIN_UPDATE_FPS.get())
                            .targetBudgetMs(ClientConfigs.THROTTLING_UPDATE_MS.get()) //10% of a frame which at 60fps = 16.6ms is ~1.66ms which should lower fps from 60 to 54. in other words at most a 6fps drop
                            .evictAfterTicks(20 * 5) //5 seconds
                            .guardTargetFps(60) //if we go under 60 fps, be more aggressive
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
                SCHEDULER.get().forceUpdateNextTick(textureId);
                return null;
            }

            texture.markReferenced(requiresUpdate);

            if (texture.setPostChain(postShader)) {
                requiresUpdate = true;
            }
            if (VistaLevelRenderer.isRenderingLiveFeed()) {
                requiresUpdate = false; //suppress recursive updates
            }
            texture.setShowsTime(showsTime);
            if (requiresUpdate) {
                texture.setUpdateNextTick(true);
            }
            return texture;
        }
    }

    public static Handle createHandle(UUID uuid, Vec2i screenSize) {
        return new Handle(uuid, screenSize);
    }

    @SuppressWarnings("ConstantConditions")
    public static void clear() {
        UPDATE_TIMES.clear();
        //TODO: change just invalidate our own
        DynamicTextureRenderer.clearCache();
    }

    public static void onRenderTickEnd() {
        SCHEDULER.get().onEndOfFrame();
    }
}
