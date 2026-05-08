package net.mehvahdjukaar.vista.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.function.Function;

public class VistaRenderTypes extends RenderType {

    private VistaRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                             boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    private static final ShaderStateShard CAMERA_SHADER_STATE = new ShaderStateShard(VistaModClient.CAMERA_VIEW_SHADER);
    private static final ShaderStateShard STATIC_SHADER_STATE = new ShaderStateShard(VistaModClient.STATIC_SHADER);

    private record CrtKey(ResourceLocation texture, float frameW, float frameH, int scale,
                          IntAnimationState turnOnAnim, IntAnimationState staticAnim,
                          CrtOverlay overlay) {
    }


    public static RenderType crtRenderType(
            ResourceLocation id, int scale, float frameW, float frameH,
            IntAnimationState turnOnAnim, IntAnimationState staticAnim, CrtOverlay overlay) {
        CrtKey key = new CrtKey(id, frameW, frameH, scale, turnOnAnim, staticAnim, overlay);
        return CRT_RENDER_TYPE.apply(key);
    }

    private static final Function<CrtKey, RenderType> CRT_RENDER_TYPE =
            Util.memoize(k -> {
                var textureStateBuilder = MultiTextureStateShard.builder()
                        .add(k.texture, false, false);
                if (k.overlay != CrtOverlay.NONE) {
                    textureStateBuilder.add(k.overlay.texture, false, false);
                }
                CompositeState compositeState = CompositeState.builder()
                        .setShaderState(CAMERA_SHADER_STATE)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setTextureState(textureStateBuilder.build())
                        .setTexturingState(new TexturingStateShard("set_texel_size",
                                () -> setCameraDrawUniforms(k),
                                () -> {
                                }))
                        .createCompositeState(false);

                return create("camera_view", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                        1536, true, false, compositeState);
            });

    private static void setCameraDrawUniforms(CrtKey key) {
        ShaderInstance shader = VistaModClient.CAMERA_VIEW_SHADER.get();
        shader.safeGetUniform("SpriteDimensions").set(key.frameW, key.frameH);
        shader.safeGetUniform("OverlayIndex").set(key.overlay.ordinal());
        float pt = Minecraft.getInstance().getDeltaFrameTime();

        //float max = 24000;
        //float myTime = ((Minecraft.getInstance().level.getGameTime() % max) + pt)/max;
        //shader.safeGetUniform("Time").set(myTime);
        float scale = key.scale / 12f;
        double v = ClientConfigs.PIXEL_DENSITY.get() * scale;
        setFloat(shader, "TriadsPerPixel", (float) v);
        setFloat(shader, "Smear", 1f);
        setFloat(shader, "EnableEnergyNormalize", 0.0f);

        double v1 = ClientConfigs.VIGNETTE.get();
        setFloat(shader, "VignetteIntensity", (float)v1);
        setFloat(shader, "NoiseIntensity", key.staticAnim.getValue(pt));
        setFloat(shader, "FadeAnimation", key.turnOnAnim.getValue(pt));
    }

    public static final RenderType NOISE =
            create("noise", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    1536, true, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(STATIC_SHADER_STATE)
                            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                            .setLightmapState(LIGHTMAP)
                            .setTexturingState(new TexturingStateShard("set_texel_size",
                                    () -> {
                                        ShaderInstance shader = VistaModClient.STATIC_SHADER.get();
                                        setFloat(shader, "NoiseIntensity", 1f);
                                    },
                                    () -> {
                                    }))
                            .createCompositeState(false));

    private static void setFloat(ShaderInstance shader, String name, float value) {
        shader.safeGetUniform(name).set(value);
    }

    public static ResourceLocation getColoredShader(DyeColor c) {
        return VistaMod.res("shaders/post/" + c.getSerializedName() + "_tint.json");
    }
}
