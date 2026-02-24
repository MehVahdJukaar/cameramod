package net.mehvahdjukaar.vista.client.textures;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mehvahdjukaar.texture_renderer.RenderTargetDynamicTexture;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.CrtOverlay;
import net.mehvahdjukaar.vista.client.SlidingWindowCounter;
import net.mehvahdjukaar.vista.client.renderer.LevelRendererCameraState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class LiveFeedTexture extends RenderTargetDynamicTexture {

    private static final ResourceLocation EMPTY_PIPELINE = VistaMod.res("shaders/post/empty.json");

    private final UUID associatedUUID;

    private final LevelRendererCameraState rendererState = LevelRendererCameraState.createNew();
    @Nullable
    private final ResourceLocation postFragment;
    @Nullable
    private ResourceLocation postChainID;
    private PostChain postChain;
    private boolean disconnected = false;

    private enum RefType {
        LIVE, PAUSED
    }

    private final SlidingWindowCounter<RefType> references =
            new SlidingWindowCounter<>(Duration.ofSeconds(3), Duration.ofSeconds(1));

    public LiveFeedTexture(ResourceLocation resourceLocation, int size,
                           @NotNull Consumer<LiveFeedTexture> textureDrawingFunction,
                           UUID id, @Nullable ResourceLocation postFragment) {
        super(resourceLocation, size, textureDrawingFunction);
        this.associatedUUID = id;
        this.postChainID = null;
        this.postFragment = postFragment;
        //can cause flicker?
        //this too?
        this.recomputePostChain();
    }

    public void markReferenced(boolean paused) {
        references.record(paused ? RefType.PAUSED : RefType.LIVE);
    }

    public CrtOverlay getOverlay(boolean wantPaused) {
        if (isDisconnected()) return CrtOverlay.DISCONNECT;
        int paused = references.getCount(RefType.LIVE);
        int live = references.getCount(RefType.PAUSED);
        if (live == 0 && paused != 0) {
            return (paused == 0) ? CrtOverlay.NONE : CrtOverlay.PAUSE;
        }
        if (paused == 0 || !wantPaused) {
            return CrtOverlay.NONE;
        }
        return CrtOverlay.PAUSE_PLAY;
    }

    public LevelRendererCameraState getRendererState() {
        return rendererState;
    }

    public UUID getAssociatedUUID() {
        return associatedUUID;
    }

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
            ResourceLocation chainId = postChainID == null ? EMPTY_PIPELINE : postChainID;
            postChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), canvasTarget, chainId);
            //add extra pass
            RenderTarget swapTarget = postChain.getTempTarget("swap");
            if (swapTarget == null) {
                postChain.addTempTarget("swap", getWidth(), getHeight());
            }
            if (postFragment != null) {
                postChain.addPass(postFragment.toString(), canvasTarget, swapTarget, false);
                //swap back
                postChain.addPass("vista:blit_flip_y", swapTarget, canvasTarget, false);
            } else {
                postChain.addPass("blit", canvasTarget, swapTarget, false);
                postChain.addPass("vista:blit_flip_y", swapTarget, canvasTarget, false);
            }
            for (PostPass postPass : postChain.passes) {
                postPass.setOrthoMatrix(postChain.shaderOrthoMatrix);
            }
        } catch (IOException ioexception) {
            VistaMod.LOGGER.warn("Failed to load shader: {}", postChainID, ioexception);
            gameRenderer.effectActive = false;
        } catch (JsonSyntaxException jsonsyntaxexception) {
            VistaMod.LOGGER.warn("Failed to parse shader: {}", postChainID, jsonsyntaxexception);
            gameRenderer.effectActive = false;
        }
    }

    public boolean setPostChain(@Nullable ResourceLocation newPostChainId) {
        ResourceLocation currentShader = this.postChainID;
        if (Objects.equals(currentShader, newPostChainId)) {
            return false;
        }
        this.postChainID = newPostChainId;

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

    public boolean isDisconnected() {
        return disconnected;
    }

    public void setDisconnected(boolean inactive) {
        this.disconnected = inactive;
    }
}
