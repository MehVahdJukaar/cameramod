package net.mehvahdjukaar.vista.integration.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.ShaderRenderingPipeline;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class IrisCompat {

    // true while Vista is rendering a camera pass (should work)
    private static final WorldRenderingPipeline VISTA_PIPELINE = createFeedPipeline();
    private static final ThreadLocal<Boolean> VISTA_RENDERING = ThreadLocal.withInitial(() -> false);
    private static Supplier<Boolean> irisShaderPacksOff;

    // Mirror reflections keep the vanilla pipeline. Iris's feed pipeline composites every pass into
    // whichever canvas was bound when the pipeline was first constructed, and reflections come in
    // many canvases and sizes, so running them through it leaves every canvas but the first blank or
    // writing a depth-map wash with ghosting. Camera feeds (TV/viewfinder) can still take the shader
    // path.
    private static final ThreadLocal<Boolean> MIRROR_PASS = ThreadLocal.withInitial(() -> false);

    public static void setMirrorPass(boolean mirrorPass) {
        MIRROR_PASS.set(mirrorPass);
    }

    public static boolean isMirrorPass() {
        return MIRROR_PASS.get();
    }

    // Iris's frame counter and timer advance once per game frame, but feeds run slower (10Hz by
    // default), so shader pack TAA jitter would skip samples between observations and never resolve.
    // These feed-local clocks advance one step per feed render instead. See the two Compat mixins.
    private static int feedFrameCounter = 0;
    private static float feedFrameTimeCounter = 0F;
    private static float feedLastFrameTime = 1F / 60F;
    private static long feedLastFrameNanos = 0L;

    public static boolean isFeedRendering() {
        return VISTA_RENDERING.get();
    }

    // Whether an Iris shaderpack pipeline is currently driving world rendering. Used to gate the
    // custom CRT screen shader, which is not safe under an active pack (see hasSfx()).
    public static boolean hasActiveShaderPack() {
        return CompatHandler.IRIS
                && Iris.getPipelineManager().getPipelineNullable() instanceof ShaderRenderingPipeline;
    }

    // A bare static Iris flips at the head and return of renderLevel, so nesting a second one inside
    // the main pass leaves it stuck false. See the call site in VistaLevelRenderer#renderLevel.
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

    // Iris detects a recreated render target through version counters that only increment in
    // destroyBuffers, so a brand new RenderTarget starts at 0 just like the old one did. Resizing a TV
    // swaps in exactly that, and Iris keeps its gbuffers on the old (possibly freed) depth texture.
    //
    // The feed pipelines are cached per dimension and canvas size (see CompatIrisMixin) and shared
    // by every feed matching that key (two TVs of the same size on the same world, say). Iris
    // tracks a single "current depth texture" per pipeline. Bumping the counters on EVERY feed
    // render makes the next beginLevelRendering re-attach that pipeline to the canvas actually
    // bound for this pass. If this only happened on canvas *changes*, then once one feed's canvas
    // is destroyed (turning a TV off evicts its texture and frees its GL buffers), the pipeline
    // would keep pointing its depth attachment at that freed buffer forever: any other feed still
    // sharing the pipeline would end up drawing into an incomplete framebuffer and go white +
    // flicker.
    public static void onFeedCanvasBound(RenderTarget canvas, String texturePath) {
        CURRENT_FEED_TEXTURE.set(texturePath);
        bumpIrisVersionCounters(canvas);
    }

    // The dimension key the feed pass renders under. One pipeline per CANVAS (each TV texture),
    // not per size: same-size TVs sharing a pipeline alternate canvases every render, which either
    // cross-blends the pack's temporal buffers between cameras or -- with the full-clear safety --
    // wipes them (colortex1 clears to plain white) before every accumulation pass, washing the
    // composite white. Separate pipelines isolate everything, at the cost of one shader program set
    // and shadow target set per TV, so the number of issued keys is capped; past the cap feeds fall
    // back to sharing a pipeline per canvas size (the pre-isolation behavior).
    public static final int MAX_FEED_PIPELINES = 6;
    private static final Set<String> ISSUED_FEED_KEYS = new HashSet<>();

    @Nullable
    public static String feedPipelineKey() {
        String texturePath = CURRENT_FEED_TEXTURE.get();
        if (texturePath == null) return null;
        if (ISSUED_FEED_KEYS.contains(texturePath)) return texturePath;
        if (ISSUED_FEED_KEYS.size() >= MAX_FEED_PIPELINES) {
            // "<w>x<h>": the shared per-size key used before canvas isolation.
            return texturePath.substring(texturePath.lastIndexOf('_') + 1);
        }
        ISSUED_FEED_KEYS.add(texturePath);
        return texturePath;
    }

    // The per-size pipeline is still shared by every same-size canvas (two identical TVs in view).
    // Its colortex ping-pong buffers hold the pack's temporal state -- TAA history, SSR, any
    // per-frame accumulation -- and those are view-dependent: with two cameras alternating on one
    // pipeline, each feed's composite blends the other camera's previous result into its own. The
    // blended terrain reads as white flashing on one TV and as a slight shimmer on the other (the
    // sky survives because it looks the same from any viewpoint). A single feed never switches
    // canvases and keeps accumulating normally.
    //
    // So: whenever a pipeline is handed a canvas it was not last bound to, arm Iris's
    // full-clear flag. The next beginLevelRendering runs clearPassesFull, wiping both halves of
    // every colortex pair. Feeds sharing a pipeline lose cross-feed temporal accumulation while
    // more than one of them is cycling (mild edge aliasing), which is far less visible than the
    // cross-camera ghosting. Distinct-size feeds run on distinct pipelines and never hit this.
    public static void onFeedPipelineBound(WorldRenderingPipeline pipeline, RenderTarget canvas) {
        RenderTarget last = LAST_FEED_CANVAS.put(pipeline, canvas);
        if (last != null && last != canvas) {
            VistaMod.LOGGER.info("[VistaFeed] pipeline canvas switch #{} -> #{}: arming full clear",
                    System.identityHashCode(last), System.identityHashCode(canvas));
            clearTemporalBuffers(pipeline);
        }
        logVertexFormatFlip();
    }

    // Temporary diagnostics: each IrisRenderingPipeline constructor overwrites the global terrain
    // vertex format with its own analysis; if the feed pipelines' formats differ from the main
    // one, section meshes get built against one layout and rendered with another.
    private static void logVertexFormatFlip() {
        if (GET_VERTEX_FORMAT_METHOD == null) return;
        try {
            Object format = GET_VERTEX_FORMAT_METHOD.invoke(WorldRenderingSettings.INSTANCE);
            if (!Objects.equals(format, LAST_FORMAT)) {
                LAST_FORMAT = format;
                VistaMod.LOGGER.info("[VistaFeed] world vertex format is now: {}", format);
                // Pipeline (re)creation changed the global terrain layout (Veil wraps the main
                // pipeline in its own chunk vertex type, feed pipelines use Iris's). Meshes built
                // against the old layout render as garbage under the new one, so rebuild them all.
                scheduleWorldRebuild();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VistaMod.LOGGER.warn("Failed to read Iris vertex format", e);
        }
    }

    @Nullable
    private static Object LAST_FORMAT;
    // Returns a ChunkVertexType that is not on the compile classpath, hence reflection.
    @Nullable
    private static final Method GET_VERTEX_FORMAT_METHOD = lookupMethod(WorldRenderingSettings.class, "getVertexFormat");

    @Nullable
    private static Method lookupMethod(Class<?> clazz, String name) {
        try {
            return clazz.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // The feed pipeline's first beginLevelRendering registers the pack's block-id map and is due
    // to rebuild every section so meshes carry it (CompatIrisRenderingMixin defers that rebuild
    // out of the feed pass, since tearing down the geometry being drawn wrecks it). This runs the
    // deferred rebuild once the outermost feed has fully finished: meshes built before the id map
    // existed have no pack data, which reads as untinted grass/leaves ("depth map" patches) until
    // a random block update happens to rebuild them.
    public static void scheduleWorldRebuild() {
        PENDING_WORLD_REBUILD = true;
    }

    private static boolean PENDING_WORLD_REBUILD;

    public static void runPendingWorldRebuild(boolean outermostFeedFinished) {
        if (!outermostFeedFinished || !PENDING_WORLD_REBUILD) return;
        PENDING_WORLD_REBUILD = false;
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        if (levelRenderer != null) {
            VistaMod.LOGGER.info("[VistaFeed] running deferred world rebuild for block id init");
            levelRenderer.allChanged();
        }
    }

    private static void clearTemporalBuffers(WorldRenderingPipeline pipeline) {
        // Toggling shaderpacks off mid-session makes preparePipeline hand back a bare
        // VanillaRenderingPipeline for the feed dimension, which has no renderTargets field.
        if (!(pipeline instanceof IrisRenderingPipeline)) return;
        if (PIPELINE_RENDER_TARGETS_FIELD == null || FULL_CLEAR_REQUIRED_FIELD == null) return;
        try {
            Object renderTargets = PIPELINE_RENDER_TARGETS_FIELD.get(pipeline);
            if (renderTargets != null) {
                FULL_CLEAR_REQUIRED_FIELD.setBoolean(renderTargets, true);
            }
        } catch (ReflectiveOperationException e) {
            VistaMod.LOGGER.warn("Failed to arm Iris full clear for feed pipeline switch", e);
        }
    }

    // Main render thread only, keyed weakly so pipelines destroyed on a pack reload don't leak.
    private static final Map<WorldRenderingPipeline, RenderTarget> LAST_FEED_CANVAS = new WeakHashMap<>();

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
    private static Field lookupField(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Nullable
    private static final Field DEPTH_BUFFER_VERSION_FIELD = lookupField(RenderTarget.class, "iris$depthBufferVersion");
    @Nullable
    private static final Field COLOR_BUFFER_VERSION_FIELD = lookupField(RenderTarget.class, "iris$colorBufferVersion");
    // IrisRenderingPipeline.renderTargets and RenderTargets.fullClearRequired: arming the flag makes
    // the next beginLevelRendering run its full clear passes over every colortex, history included.
    @Nullable
    private static final Field PIPELINE_RENDER_TARGETS_FIELD = lookupField(IrisRenderingPipeline.class, "renderTargets");
    @Nullable
    private static final Field FULL_CLEAR_REQUIRED_FIELD = lookupField(RenderTargets.class, "fullClearRequired");

    // VanillaRenderingPipeline's constructor is not inert: it rewrites WorldRenderingSettings as if a
    // pack had just unloaded, and each setter arms Iris's reload flag. It happens to land on untouched
    // defaults today only because addConfigs touches the class at startup before any pack exists. Load
    // it later and it would reset the vertex format under a live pack, forcing a terrain rebuild
    // mid-frame. Hence the snapshot and restore.
    //
    // Fields are copied directly rather than through the getters: those name Sodium and Minecraft
    // types, and the Iris artifact on the NeoForge compile classpath still carries Fabric mappings,
    // so they're unusable from common. Walking the fields also covers the reload flag itself.
    private static WorldRenderingPipeline createFeedPipeline() {
        WorldRenderingSettings settings = WorldRenderingSettings.INSTANCE;
        Map<Field, Object> snapshot = new LinkedHashMap<>();
        try {
            for (Field field : WorldRenderingSettings.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                snapshot.put(field, field.get(settings));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VistaMod.LOGGER.warn("Failed to snapshot Iris world rendering settings", e);
            snapshot.clear();
        }

        WorldRenderingPipeline pipeline = new VanillaRenderingPipeline();

        try {
            for (Map.Entry<Field, Object> entry : snapshot.entrySet()) {
                entry.getKey().set(settings, entry.getValue());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VistaMod.LOGGER.warn("Failed to restore Iris world rendering settings", e);
        }
        return pipeline;
    }

    @Nullable
    public static WorldRenderingPipeline getModifiedPipeline() {
        return VISTA_RENDERING.get() && (irisShaderPacksOff.get() || MIRROR_PASS.get()) ? VISTA_PIPELINE : null;
    }

    // Whether the feed pass gets its own IrisRenderingPipeline. Sharing one means its render targets
    // get resized between the feed canvas and the main framebuffer every frame, and RenderTargets
    // reallocates every gbuffer on a size change, so it flickers and costs a lot.
    public static boolean shouldSwapDimensionForFeed() {
        return VISTA_RENDERING.get() && !irisShaderPacksOff.get() && !MIRROR_PASS.get();
    }

    private static final ThreadLocal<String> CURRENT_FEED_TEXTURE = new ThreadLocal<>();

    public static Runnable decorateRendererWithoutShaderPacks(Runnable renderTask) {
        return () -> {
            LevelRenderer lr = Minecraft.getInstance().levelRenderer;
            PipelineManager pm = Iris.getPipelineManager();
            boolean oldShadowActive = ShadowRenderer.ACTIVE;
            boolean oldVistaRendering = VISTA_RENDERING.get();
            OldRenderState oldState = OldRenderState.loadFrom(CapturedRenderingState.INSTANCE);

            WorldRenderingPipeline oldLrPipeline = getCurrentPipeline(lr);
            // Iris's own MixinLevelRenderer only restores the LevelRenderer-side copy, so this
            // singleton has to be saved too or anything reading the pipeline manager between the feed
            // render and the next main renderLevel gets the stub VanillaRenderingPipeline.
            WorldRenderingPipeline oldPmPipeline = getPipelineManagerPipeline(pm);

            try {
                ShadowRenderer.ACTIVE = false;
                VISTA_RENDERING.set(true);
                releaseIrisStateLocks();
                // Only the outermost pass is one feed frame. Recursive mirrors nest through here, and
                // bumping the global jitter clock per level lands right back on skipped samples.
                if (!oldVistaRendering) advanceFeedClocks();
                renderTask.run();
            } finally {
                ShadowRenderer.ACTIVE = oldShadowActive;
                VISTA_RENDERING.set(oldVistaRendering);
                oldState.saveTo(CapturedRenderingState.INSTANCE);
                setCurrentPipeline(lr, oldLrPipeline);
                setPipelineManagerPipeline(pm, oldPmPipeline);
                runPendingWorldRebuild(!oldVistaRendering);
            }
        };
    }

    // A pack program that overrode blend or the color/depth mask leaves Iris's global storages locked,
    // recording every later GlStateManager call instead of applying it until an Iris program hands the
    // state back. Feeds run on a VanillaRenderingPipeline where no Iris program ever applies, so
    // nothing releases the lock and the whole nested render draws with the pack's frozen state. That's
    // what makes blended geometry come out opaque in a TV or mirror.
    //
    // Releasing applies the right state, not a stale one: the locked calls were deferred into the
    // storages and hold what the last vanilla caller asked for. No need to re-lock either, the next
    // pack program re-applies its override anyway.
    private static void releaseIrisStateLocks() {
        BlendModeStorage.restoreBlend();
        DepthColorStorage.unlockDepthColor();
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
        // Feeds render through their own IrisRenderingPipeline instances (one per TV), but Iris
        // assumes a single world pipeline: every pipeline creation overwrites the global terrain
        // vertex format, and when the main pipeline is wrapped by another mod (Veil) the two
        // formats can never agree -- meshes built against either layout render as garbage in the
        // other. The stub vanilla pipeline keeps the global format owned by the main pipeline and
        // the TVs rock-solid, at the cost of no shaderpack effects inside the feed image.
        irisShaderPacksOff = builder
                .comment("Renders the live feed view with the vanilla pipeline instead of the shaderpack. " +
                        "Feeds stay stable with any shader/mod rendering setup, but won't show shader effects. " +
                        "Set false to let iris shaders render in the feed (experimental: flickers/corrupts with " +
                        "some shaderpacks and when Veil or similar pipeline-wrapping mods are installed)")
                .define("iris_off_hack", true);
    }
}
