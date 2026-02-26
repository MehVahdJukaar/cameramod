package net.mehvahdjukaar.vista.client.textures;

import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.mehvahdjukaar.ml_classes.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.misc.RollingBuffer;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.AdaptiveUpdateScheduler;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastProvider;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.distant_horizons.DistantHorizonsCompat;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.VisibleForDebug;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class LiveFeedTexturesManager {

    private static final String POSTERIZE_FRAGMENT_SHADER = "vista_posterize";
    private static final BiMap<FeedKey, ResourceLocation> LIVE_FEED_LOCATIONS = HashBiMap.create();
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

    private record FeedKey(UUID name, Integer size) {
    }

    private static long feedCounter = 0;


    @Nullable
    public static LiveFeedTexture requestLiveFeedTexture(UUID location, int screenSize,
                                                         boolean requiresUpdate,
                                                         @Nullable ResourceLocation postShader) {

        ResourceLocation feedId = getOrCreateFeedId(location, screenSize);
        LiveFeedTexture texture = DynamicTextureRenderer.requestTexture(feedId, () ->
                new LiveFeedTexture(feedId, screenSize * ClientConfigs.RESOLUTION_SCALE.get(),
                        LiveFeedTexturesManager::refreshTexture, location, POSTERIZE_FRAGMENT_SHADER));


        if (texture == null) {
            SCHEDULER.get().forceUpdateNextTick(feedId);
            return null;
        }

        texture.markReferenced(requiresUpdate);

        if (texture.setPostChain(postShader)) {
            requiresUpdate = true;
        }
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            requiresUpdate = false; //suppress recursive updates
        }
        texture.setUpdateNextTick(requiresUpdate);
        return texture;
    }

    private static ResourceLocation getOrCreateFeedId(UUID uuid, int screenSize) {
        FeedKey key = new FeedKey(uuid, screenSize);
        ResourceLocation loc = LIVE_FEED_LOCATIONS.get(key);
        if (loc == null) {
            loc = VistaMod.res("live_feed_" + feedCounter++);
            LIVE_FEED_LOCATIONS.put(key, loc);
        }
        return loc;
    }

    @SuppressWarnings("ConstantConditions")
    public static void clear() {
        LIVE_FEED_LOCATIONS.clear();
        UPDATE_TIMES.clear();
        feedCounter = 0;
    }

    public static void onRenderTickEnd() {
        SCHEDULER.get().onEndOfFrame();
    }

    private static void refreshTexture(LiveFeedTexture text) {
        Minecraft mc = Minecraft.getInstance();

        ClientLevel level = mc.level;
        if (level == null) return;
        if (mc.isPaused()) return;

        Runnable runTask = () -> {

            setLastUpdatedTime(text.getAssociatedUUID(), level);

            UUID uuid = text.getAssociatedUUID();
            BroadcastManager manager = BroadcastManager.getInstance(level);
            IBroadcastProvider provider = manager.getBroadcast(uuid, true); //touch the feed to make sure it's still valid and linked
            if (!(provider instanceof ViewFinderBlockEntity vf)) {
                if (!text.isDisconnected()) {
                    text.setDisconnected(true);
                }
                return;
            }
            text.setDisconnected(false);

            try {
                VistaLevelRenderer.render(text, vf);
            } catch (Throwable throwable) {
                VistaMod.LOGGER.error("Failed to render live feed {}", uuid, throwable);
                text.setDisconnected(true);
                return;
            }

            if (ClientConfigs.DRAW_DATE.get() || VistaMod.isFunny()) {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
                String cctvTimestamp = now.format(formatter);
                drawText(text, cctvTimestamp, 2, 4, false, true);
            }


            if (VistaMod.isFunny()) {
                drawOverlay(text, VistaModClient.LL_OVERLAY);
            }


        };
        if (CompatHandler.DISTANT_HORIZONS) {
            runTask = DistantHorizonsCompat.decorateRenderWithoutLOD(runTask);
        }
        if (CompatHandler.IRIS) {
            runTask = IrisCompat.decorateRendererWithoutShaderPacks(runTask);
        }

        ResourceLocation textureId = text.getTextureLocation();

        SCHEDULER.get().runIfShouldUpdate(textureId, runTask);

    }


    private static void setLastUpdatedTime(UUID textureId, ClientLevel level) {
        if (ClientConfigs.rendersDebug()) {
            UPDATE_TIMES.computeIfAbsent(textureId, k -> new RollingBuffer<>(20))
                    .push(level.getGameTime());
        }
    }


    private static void drawText(LiveFeedTexture texture, String text,
                                 int x, int y, boolean shadow, boolean background) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget oldTarget = mc.getMainRenderTarget();

        Font font = mc.font;
        MultiBufferSource.BufferSource bf = mc.renderBuffers().bufferSource();

        RenderSystem.backupProjectionMatrix();
        float baseScale = TVBlockEntity.MIN_SCREEN_PIXEL_SIZE * ClientConfigs.RESOLUTION_SCALE.get();
        float size = baseScale * (texture.getWidth() / baseScale);
        Matrix4f matrix4f = new Matrix4f().setOrtho(
                0.0F, size, 0.0F, size, -1, 1);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
        GlStateManager._disableCull(); //since we render backwards or something

        Lighting.setupFor3DItems();
        int backColor = !background ? 0 : (int) (0.25F * 255.0F) << 24;

        font.drawInBatch(text, x, y, -1, shadow, new Matrix4f(),
                bf, Font.DisplayMode.NORMAL, backColor, LightTexture.FULL_BRIGHT);

        bf.endBatch();

        RenderSystem.restoreProjectionMatrix();
        oldTarget.bindWrite(true);
    }


    private static void drawOverlay(LiveFeedTexture text, ResourceLocation overlayTexture) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget oldTarget = mc.getMainRenderTarget();


        AbstractTexture texture = mc.getTextureManager().getTexture(overlayTexture);

        RenderSystem.assertOnRenderThread();
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._enableBlend();


        ShaderInstance shaderInstance = Objects.requireNonNull(mc.gameRenderer.blitShader, "Blit shader not loaded");
        shaderInstance.setSampler("DiffuseSampler", texture.getId());
        shaderInstance.apply();
        BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        bufferBuilder.vertex(0.0F, 0.0F, 0.0F).uv(0,0).color(-1).endVertex();
        bufferBuilder.vertex(1.0F, 0.0F, 0.0F).uv(1,0).color(-1).endVertex();
        bufferBuilder.vertex(1.0F, 1.0F, 0.0F).uv(1,1).color(-1).endVertex();
        bufferBuilder.vertex(0.0F, 1.0F, 0.0F).uv(0,1).color(-1).endVertex();
        BufferUploader.draw(bufferBuilder.end());
        shaderInstance.clear();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        oldTarget.bindWrite(true);
    }


}
