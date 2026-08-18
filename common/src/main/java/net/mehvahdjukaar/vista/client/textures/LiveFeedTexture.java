package net.mehvahdjukaar.vista.client.textures;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.mehvahdjukaar.moonlight.api.client.PostShadersHelper;
import net.mehvahdjukaar.moonlight.api.misc.RollingBuffer;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.CrtOverlay;
import net.mehvahdjukaar.vista.client.SlidingWindowCounter;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Texture backing a camera feed on a TV or view finder. Owns its post chain and overlay state, plus
 * the AdaptiveUpdateScheduler shared by every feed to throttle refreshes.
 */
public class LiveFeedTexture extends PerspectiveTexture {

    private static final PostShadersHelper.Group POSTERIZE_GROUP = new PostShadersHelper.Group(
            VistaMod.res("1"), 0
    );

    private static final PostShadersHelper.Group LENS_EFFECTS_GROUP = new PostShadersHelper.Group(
            VistaMod.res("0"), 1
    );

    private static final ResourceLocation CAMERA_POST_PIPELINE = VistaMod.res("shaders/post/posterize_camera.json");

    @Nullable
    private ResourceLocation extraPostChainID;
    private PostChain postChain;
    private boolean disconnected = false;
    private boolean showsTime = false;

    private enum RefType {
        LIVE, PAUSED
    }

    private final SlidingWindowCounter<RefType> references =
            new SlidingWindowCounter<>(Duration.ofSeconds(3), Duration.ofSeconds(1));

    public LiveFeedTexture(ResourceLocation resourceLocation, int width, int height, UUID id) {
        super(resourceLocation, width, height, id);
        this.extraPostChainID = null;
        //can cause flicker?
        //this too?
        this.recomputePostChain();
    }

    @Override
    protected void refresh() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!mc.isGameLoadFinished() || level == null) return;
        if (mc.isPaused()) return;

        Runnable runTask = () -> {
            setLastUpdatedTime(level);
            BroadcastManager manager = BroadcastManager.getInstance(level);
            IBroadcastSource provider = manager.getBroadcast(getAssociatedUUID(), true); //touch the feed to make sure it's still valid and linked
            if (!(provider instanceof ViewFinderBlockEntity vf)) {
                if (!disconnected) {
                    setDisconnected(true);
                }
                return;
            }
            setDisconnected(false);

            VistaLevelRenderer.render(this, vf);

            if (showsTime() || VistaMod.isFunny()) {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
                String cctvTimestamp = now.format(formatter);
                drawText(this, cctvTimestamp, 2, 4, false, true);
            }

            if (VistaMod.isFunny()) {
                drawOverlay(this, VistaModClient.LL_OVERLAY);
            }
        };

        LiveFeedTexturesManager.SCHEDULER.get().runIfShouldUpdate(getTextureLocation(), runTask);
    }

    public void markReferenced(boolean paused) {
        references.record(paused ? RefType.PAUSED : RefType.LIVE);
    }

    public CrtOverlay getOverlay(boolean wantPaused) {
        if (isDisconnected()) return CrtOverlay.DISCONNECT;
        int paused = references.getCount(RefType.LIVE);
        int live = references.getCount(RefType.PAUSED);
        if (live == 0 && paused != 0) {
            return CrtOverlay.PAUSE;
        }
        if (paused == 0 || !wantPaused) {
            return CrtOverlay.NONE;
        }
        return CrtOverlay.PAUSE_PLAY;
    }

    public boolean showsTime() {
        return showsTime;
    }

    public void setShowsTime(boolean showsTime) {
        this.showsTime = showsTime;
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    public void setDisconnected(boolean inactive) {
        this.disconnected = inactive;
    }

    @Override
    public void applyPostChain() {
        if (isClosed()) {
            VistaMod.LOGGER.error("apply post on closed");
            return;
        }
        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
        if (postChain != null) {
            gameRenderer.effectActive = true;
            gameRenderer.postEffect = this.postChain;
        } else {
            gameRenderer.postEffect = null;
            gameRenderer.effectActive = false;
        }
    }

    private void recomputePostChain() {
        if (isClosed()) {
            VistaMod.LOGGER.error("recompute post on closed");
            return;
        }

        if (postChain != null) {
            postChain.close();
            postChain = null;
        }
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gameRenderer = mc.gameRenderer;
        try {
            RenderTarget canvasTarget = this.getRenderTarget();

            postChain = PostShadersHelper.refreshComposite(postChain, CAMERA_POST_PIPELINE, POSTERIZE_GROUP, canvasTarget);

            if (extraPostChainID != null) {
                postChain = PostShadersHelper.refreshComposite(postChain, extraPostChainID, LENS_EFFECTS_GROUP, canvasTarget);
            }
        } catch (IOException ioexception) {
            VistaMod.LOGGER.warn("Failed to load shader: {}", extraPostChainID, ioexception);
            gameRenderer.effectActive = false;
        } catch (JsonSyntaxException jsonsyntaxexception) {
            VistaMod.LOGGER.warn("Failed to parse shader: {}", extraPostChainID, jsonsyntaxexception);
            gameRenderer.effectActive = false;
        }
    }

    public boolean setPostChain(@Nullable ResourceLocation newPostChainId) {
        ResourceLocation currentShader = this.extraPostChainID;
        if (Objects.equals(currentShader, newPostChainId)) {
            return false;
        }
        this.extraPostChainID = newPostChainId;

        RenderSystem.recordRenderCall(this::recomputePostChain);
        return true;
    }

    @Override
    public void close() {
        super.close();
        this.closed = true;
        if (postChain != null) {
            postChain.close();
            postChain = null;
        }
    }

    private void setLastUpdatedTime(ClientLevel level) {
        if (ClientConfigs.rendersDebug()) {
            LiveFeedTexturesManager.UPDATE_TIMES.computeIfAbsent(getAssociatedUUID(), k -> new RollingBuffer<>(20))
                    .push(level.getGameTime());
        }
    }

    private static void drawText(LiveFeedTexture target, String text,
                                 int x, int y, boolean shadow, boolean background) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget oldTarget = mc.getMainRenderTarget();
        target.getRenderTarget().bindWrite(true);

        Font font = mc.font;
        MultiBufferSource.BufferSource bf = mc.renderBuffers().bufferSource();

        RenderSystem.backupProjectionMatrix();
        float baseScale = TVBlockEntity.MIN_SCREEN_PIXEL_SIZE * ClientConfigs.LIVE_FEED_RESOLUTION_SCALE.get();
        float size = baseScale * (target.getWidth() / baseScale);
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

    private static void drawOverlay(LiveFeedTexture target, ResourceLocation overlayTexture) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget oldTarget = mc.getMainRenderTarget();
        target.getRenderTarget().bindWrite(true);

        AbstractTexture texture = mc.getTextureManager().getTexture(overlayTexture);

        RenderSystem.assertOnRenderThread();
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._enableBlend();


        ShaderInstance shaderInstance = Objects.requireNonNull(mc.gameRenderer.blitShader, "Blit shader not loaded");
        shaderInstance.setSampler("DiffuseSampler", texture.getId());
        shaderInstance.apply();
        BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
        bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
        BufferUploader.draw(bufferBuilder.buildOrThrow());
        shaderInstance.clear();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        oldTarget.bindWrite(true);
    }
}
