package net.mehvahdjukaar.vista.integration.iris;

import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Supplier;

public class IrisCompat {


    // true while Vista is rendering a camera pass (should work)
    private static final WorldRenderingPipeline VISTA_PIPELINE = new VanillaRenderingPipeline();
    private static final ThreadLocal<Boolean> VISTA_RENDERING = ThreadLocal.withInitial(() -> false);
    private static Supplier<Boolean> irisShaderPacksOff;

    @Nullable
    public static WorldRenderingPipeline getModifiedPipeline() {
        return VISTA_RENDERING.get() && irisShaderPacksOff.get() ? VISTA_PIPELINE : null;
    }

    public static Runnable decorateRendererWithoutShaderPacks(Runnable renderTask) {
        return () -> {
            LevelRenderer lr = Minecraft.getInstance().levelRenderer;
            boolean oldShadowActive = ShadowRenderer.ACTIVE;
            boolean oldVistaRendering = VISTA_RENDERING.get();
            OldRenderState oldState = OldRenderState.loadFrom(CapturedRenderingState.INSTANCE);

            WorldRenderingPipeline oldPipeline = getCurrentPipeline(lr);

            try {
                ShadowRenderer.ACTIVE = false;
                VISTA_RENDERING.set(true);
                renderTask.run();
            } finally {
                ShadowRenderer.ACTIVE = oldShadowActive;
                VISTA_RENDERING.set(oldVistaRendering);
                oldState.saveTo(CapturedRenderingState.INSTANCE);
                setCurrentPipeline(lr, oldPipeline);

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
