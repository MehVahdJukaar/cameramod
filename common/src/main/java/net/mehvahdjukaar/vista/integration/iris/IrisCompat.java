package net.mehvahdjukaar.vista.integration.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Supplier;

public class IrisCompat {

    // true while Vista is rendering a camera pass (should work)
    private static final WorldRenderingPipeline VISTA_PIPELINE = new VanillaRenderingPipeline();
    private static final ThreadLocal<Boolean> VISTA_RENDERING = ThreadLocal.withInitial(() -> false);
    private static Supplier<Boolean> irisShaderPacksOff;

    // Iris's SystemTimeUniforms.COUNTER (int frameCounter) and TIMER (float
    // frameTimeCounter / lastFrameTime) advance once per game frame in iris$startFrame.
    // Feed renders run at a lower rate (default 10 Hz), so between two consecutive
    // feed renders these jump by several samples. Many shader packs implement TAA
    // sub-pixel jitter or procedural per-frame offsets using one of these uniforms,
    // and the composite TAA pass can't resolve when the jitter samples skip values
    // between observations — every pixel wobbles. We shim both to feed-local values
    // that advance +1 / +(real elapsed) per feed render so TAA can accumulate
    // properly. Mixin'd via CompatIrisFrameCounterMixin / CompatIrisTimerMixin.
    private static int feedFrameCounter = 0;
    private static float feedFrameTimeCounter = 0F;
    private static float feedLastFrameTime = 1F / 60F;
    private static long feedLastFrameNanos = 0L;

    public static boolean isFeedRendering() {
        return VISTA_RENDERING.get();
    }

    // ImmediateState.isRenderingLevel is a bare static that Iris flips true/false at the head and
    // return of LevelRenderer#renderLevel. Nesting a second renderLevel inside the main one leaves
    // it stuck false — see the call site in VistaLevelRenderer#renderLevel.
    public static boolean isIrisRenderingLevel() {
        return ImmediateState.isRenderingLevel;
    }

    public static void setIrisRenderingLevel(boolean renderingLevel) {
        ImmediateState.isRenderingLevel = renderingLevel;
    }

    public static int getFeedFrameCounter() {
        return feedFrameCounter;
    }

    public static float getFeedFrameTimeCounter() {
        return feedFrameTimeCounter;
    }

    public static float getFeedLastFrameTime() {
        return feedLastFrameTime;
    }

    private static void advanceFeedClocks() {
        feedFrameCounter = (feedFrameCounter + 1) % 720720;
        long now = System.nanoTime();
        if (feedLastFrameNanos != 0L) {
            float dt = (float) ((now - feedLastFrameNanos) / 1_000_000_000.0);
            feedLastFrameTime = dt;
            feedFrameTimeCounter += dt;
            if (feedFrameTimeCounter >= 3600F) {
                feedFrameTimeCounter = 0F;
            }
        }
        feedLastFrameNanos = now;
    }

    // Iris's RenderTargets.resizeIfNeeded uses iris$depthBufferVersion / iris$colorBufferVersion
    // to detect whether the main render target has been recreated, but those versions only
    // increment in destroyBuffers — a freshly-constructed RenderTarget starts at 0. When a TV
    // is resized, Vista swaps to a *new* PerspectiveTexture instance whose RenderTarget has version
    // 0 — same as the previous canvas's initial version — so Iris's check misses the swap and
    // keeps its gbuffers attached to the old canvas's depth texture (which may also have been
    // freed). Force a bump whenever the canvas instance changes so Iris's next beginLevelRendering
    // sees the change and re-attaches.
    private static WeakReference<RenderTarget> lastFeedCanvas = new WeakReference<>(null);

    public static void onFeedCanvasBound(RenderTarget canvas) {
        if (lastFeedCanvas.get() == canvas) return;
        lastFeedCanvas = new WeakReference<>(canvas);
        bumpIrisVersionCounters(canvas);
    }

    private static void bumpIrisVersionCounters(RenderTarget canvas) {
        if (DEPTH_BUFFER_VERSION_FIELD == null && COLOR_BUFFER_VERSION_FIELD == null) return;
        try {
            if (DEPTH_BUFFER_VERSION_FIELD != null) {
                DEPTH_BUFFER_VERSION_FIELD.setInt(canvas, DEPTH_BUFFER_VERSION_FIELD.getInt(canvas) + 1);
            }
            if (COLOR_BUFFER_VERSION_FIELD != null) {
                COLOR_BUFFER_VERSION_FIELD.setInt(canvas, COLOR_BUFFER_VERSION_FIELD.getInt(canvas) + 1);
            }
        } catch (IllegalAccessException e) {
            VistaMod.LOGGER.warn("Failed to bump Iris render-target version counters", e);
        }
    }

    @Nullable
    private static Field lookupIrisRtField(String name) {
        try {
            Field f = RenderTarget.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Nullable
    private static final Field DEPTH_BUFFER_VERSION_FIELD = lookupIrisRtField("iris$depthBufferVersion");
    @Nullable
    private static final Field COLOR_BUFFER_VERSION_FIELD = lookupIrisRtField("iris$colorBufferVersion");

    @Nullable
    public static WorldRenderingPipeline getModifiedPipeline() {
        return VISTA_RENDERING.get() && irisShaderPacksOff.get() ? VISTA_PIPELINE : null;
    }

    // True when we want PipelineManager to spin up a dedicated IrisRenderingPipeline
    // for the feed pass (separate per-fake-dimension entry). Otherwise the single
    // shared pipeline keeps its render targets resized back-and-forth between the
    // feed canvas and the main framebuffer each frame, which causes the flicker
    // and big perf hit since RenderTargets reallocates every gbuffer on size change.
    public static boolean shouldSwapDimensionForFeed() {
        return VISTA_RENDERING.get() && !irisShaderPacksOff.get();
    }

    public static Runnable decorateRendererWithoutShaderPacks(Runnable renderTask) {
        return () -> {
            LevelRenderer lr = Minecraft.getInstance().levelRenderer;
            PipelineManager pm = Iris.getPipelineManager();
            boolean oldShadowActive = ShadowRenderer.ACTIVE;
            boolean oldVistaRendering = VISTA_RENDERING.get();
            OldRenderState oldState = OldRenderState.loadFrom(CapturedRenderingState.INSTANCE);

            WorldRenderingPipeline oldLrPipeline = getCurrentPipeline(lr);
            // PipelineManager.pipeline is the singleton field every Iris caller reads
            // via Iris.getPipelineManager().getPipelineNullable(). Iris's MixinLevelRenderer
            // only restores the LevelRenderer-side copy, so without saving this one too
            // anything querying the pipeline manager between the feed render and the next
            // main renderLevel sees the stub VanillaRenderingPipeline.
            WorldRenderingPipeline oldPmPipeline = getPipelineManagerPipeline(pm);

            try {
                ShadowRenderer.ACTIVE = false;
                VISTA_RENDERING.set(true);
                // Only the outermost pass counts as one feed frame. Recursive mirrors nest through
                // here too, and bumping the (pipeline-global) jitter clock once per nesting level
                // would put us right back to the skipping-samples case this shim exists to avoid.
                if (!oldVistaRendering) advanceFeedClocks();
                renderTask.run();
            } finally {
                ShadowRenderer.ACTIVE = oldShadowActive;
                VISTA_RENDERING.set(oldVistaRendering);
                oldState.saveTo(CapturedRenderingState.INSTANCE);
                setCurrentPipeline(lr, oldLrPipeline);
                setPipelineManagerPipeline(pm, oldPmPipeline);
            }
        };
    }

    private static void setCurrentPipeline(LevelRenderer lr, WorldRenderingPipeline oldPipeline) {
        try {
            VANILLA_PIPELINE_FIELD.set(lr, oldPipeline);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static WorldRenderingPipeline getCurrentPipeline(LevelRenderer lr) {
        try {
            return (WorldRenderingPipeline) VANILLA_PIPELINE_FIELD.get(lr);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Field VANILLA_PIPELINE_FIELD = Arrays.stream(LevelRenderer.class.getDeclaredFields())
            .filter(f -> f.getType().equals(WorldRenderingPipeline.class))
            .findFirst()
            .map(p -> {
                p.setAccessible(true);
                return p;
            })
            .orElseThrow(() -> new RuntimeException("Failed to find vanilla pipeline field!"));

    private static final Field PIPELINE_MANAGER_PIPELINE_FIELD = Arrays.stream(PipelineManager.class.getDeclaredFields())
            .filter(f -> f.getType().equals(WorldRenderingPipeline.class))
            .findFirst()
            .map(p -> {
                p.setAccessible(true);
                return p;
            })
            .orElseThrow(() -> new RuntimeException("Failed to find PipelineManager.pipeline field!"));

    private static WorldRenderingPipeline getPipelineManagerPipeline(PipelineManager pm) {
        try {
            return (WorldRenderingPipeline) PIPELINE_MANAGER_PIPELINE_FIELD.get(pm);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setPipelineManagerPipeline(PipelineManager pm, WorldRenderingPipeline pipeline) {
        try {
            PIPELINE_MANAGER_PIPELINE_FIELD.set(pm, pipeline);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean shouldSkipShadows() {
        return VISTA_RENDERING.get();
    }

    public static boolean shouldSkipBobbing() {
        return VISTA_RENDERING.get();
    }

    public static boolean shouldShushDHCompat() {
        return VISTA_RENDERING.get();
    }

    private record OldRenderState(
            Matrix4fc gbufferModelView,
            Matrix4fc gbufferProjection,
            Vector3d fogColor,
            float fogDensity,
            float darknessLightFactor,
            float tickDelta,
            float realTickDelta,
            int currentRenderedBlockEntity,
            int currentRenderedEntity,
            int currentRenderedItem,
            float currentAlphaTest,
            float cloudTime) {

        public void saveTo(CapturedRenderingState state) {
            state.setGbufferModelView(gbufferModelView);
            state.setGbufferProjection((Matrix4f) gbufferProjection);
            state.setFogColor((float) fogColor.x, (float) fogColor.y, (float) fogColor.z);
            state.setFogDensity(fogDensity);
            state.setDarknessLightFactor(darknessLightFactor);
            state.setTickDelta(tickDelta);
            state.setRealTickDelta(realTickDelta);
            state.setCurrentBlockEntity(currentRenderedBlockEntity);
            state.setCurrentEntity(currentRenderedEntity);
            state.setCurrentRenderedItem(currentRenderedItem);
            state.setCurrentAlphaTest(currentAlphaTest);
            state.setCloudTime(cloudTime);
        }

        public static OldRenderState loadFrom(CapturedRenderingState state) {
            return new OldRenderState(
                    new Matrix4f(state.getGbufferModelView()),
                    new Matrix4f(state.getGbufferProjection()),
                    new Vector3d(state.getFogColor()),
                    state.getFogDensity(), state.getDarknessLightFactor(), state.getTickDelta(),
                    state.getRealTickDelta(), state.getCurrentRenderedBlockEntity(), state.getCurrentRenderedEntity(),
                    state.getCurrentRenderedItem(), state.getCurrentAlphaTest(), state.getCloudTime());
        }
    }

    public static void addConfigs(ConfigBuilder builder) {
        irisShaderPacksOff = builder
                .comment("Attempts to disable iris shaders in the live feed view")
                .define("iris_off_hack", true);
    }
}
