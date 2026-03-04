package net.mehvahdjukaar.vista.integration.iris;

import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.lang.reflect.Method;

public class IrisCompat {

    public interface PipelineAccess {
        void vista$restorePipelineAfterRender();
    }

    private static final ThreadLocal<Boolean> VISTA_RENDERING = ThreadLocal.withInitial(() -> false);

    public static boolean isVistaRendering() {
        return VISTA_RENDERING.get();
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

    public static Runnable decorateRendererWithoutShaderPacks(Runnable renderTask) {
        return () -> {
            boolean oldVistaRendering = VISTA_RENDERING.get();
            OldRenderState oldState = OldRenderState.loadFrom(CapturedRenderingState.INSTANCE);

            try {
                VISTA_RENDERING.set(true);
                renderTask.run();
            } finally {
                VISTA_RENDERING.set(oldVistaRendering);
                oldState.saveTo(CapturedRenderingState.INSTANCE);
            }
        };
    }

    public static void restorePipelineAfterRender() {
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method getPipelineManager = irisClass.getMethod("getPipelineManager");
            Object manager = getPipelineManager.invoke(null);
            if (manager instanceof PipelineAccess access) {
                access.vista$restorePipelineAfterRender();
            }
        } catch (Throwable ignored) {
        }
    }

    private record OldRenderState(
            Matrix4f gbufferModelView,
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
    }
}
