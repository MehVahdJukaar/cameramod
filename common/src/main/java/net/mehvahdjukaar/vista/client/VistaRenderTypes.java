package net.mehvahdjukaar.vista.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.textures.MirrorReflectionTexture;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
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
    private static final ShaderStateShard MIRROR_MATERIAL_SHADER_STATE = new ShaderStateShard(VistaModClient.MIRROR_MATERIAL_SHADER);
    private static final ShaderStateShard WAVE_GATE_SHADER_STATE = new ShaderStateShard(VistaModClient.WAVE_GATE_SHADER);

    private record CrtKey(ResourceLocation texture, float frameW, float frameH, Vec2i scale,
                          IntAnimationState turnOnAnim, IntAnimationState staticAnim,
                          CrtOverlay overlay) {
    }


    public static RenderType crtRenderType(
            ResourceLocation id, Vec2i scale, float frameW, float frameH,
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
                        .setCullState(NO_CULL)
                        // wins over the coplanar block face without a manual forward nudge
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .setTextureState(textureStateBuilder.build())
                        .setTexturingState(new TexturingStateShard("set_texel_size",
                                () -> setCameraDrawUniforms(k),
                                () -> {
                                }))
                        .createCompositeState(false);

                return create("vista_camera_view", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                        1536, true, false, compositeState);
            });

    private static void setCameraDrawUniforms(CrtKey key) {
        ShaderInstance shader = VistaModClient.CAMERA_VIEW_SHADER.get();
        shader.safeGetUniform("SpriteDimensions").set(key.frameW, key.frameH);
        shader.safeGetUniform("OverlayIndex").set(key.overlay.ordinal());
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        //float max = 24000;
        //float myTime = ((Minecraft.getInstance().level.getGameTime() % max) + pt)/max;
        //shader.safeGetUniform("Time").set(myTime);
        float pixelDensity = ClientConfigs.PIXEL_DENSITY.get() / 12f;
        setFloat2(shader, "TriadsPerPixel",
                pixelDensity * key.scale.x(), pixelDensity * key.scale.y());
        setFloat(shader, "Smear", 1f);
        setFloat(shader, "EnableEnergyNormalize", 0.0f);

        setFloat(shader, "VignetteIntensity", ClientConfigs.VIGNETTE.get());
        setFloat(shader, "NoiseIntensity", key.staticAnim.getValue(pt));
        setFloat(shader, "FadeAnimation", key.turnOnAnim.getValue(pt));
    }

    private record MirrorKey(ResourceLocation reflectionTexture, int wTiles, int hTiles, boolean smooth) {
    }

    public static RenderType mirrorMaterial(ResourceLocation reflectionTexture, int wTiles, int hTiles) {
        // smoothing is part of the key because the blur flag bakes into the texture shard at build
        // time, so toggling it has to land on a different cached render type
        return MIRROR_MATERIAL_RENDER_TYPE.apply(
                new MirrorKey(reflectionTexture, wTiles, hTiles, ClientConfigs.MIRROR_SMOOTH.get()));
    }

    private static final Function<MirrorKey, RenderType> MIRROR_MATERIAL_RENDER_TYPE = Util.memoize(k -> {
        var textureState = MultiTextureStateShard.builder()
                .add(k.reflectionTexture, k.smooth, false)
                .add(VistaModClient.MIRROR_UNDERLAY, false, false)
                .build();
        CompositeState compositeState = CompositeState.builder()
                .setShaderState(MIRROR_MATERIAL_SHADER_STATE)
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .setLayeringState(POLYGON_OFFSET_LAYERING)
                .setTextureState(textureState)
                // Overlay goes straight to unit 3: the shard binds 0 and 1, LIGHTMAP takes 2, so
                // adding it to MultiTextureStateShard would collide there.
                .setTexturingState(new TexturingStateShard("set_mirror_uniforms",
                        () -> {
                            RenderSystem.setShaderTexture(3, VistaModClient.MIRROR_OVERLAY);
                            ShaderInstance shader = VistaModClient.MIRROR_MATERIAL_SHADER.get();
                            setFloat2(shader, "Tiles", k.wTiles, k.hTiles);
                            // default to fully faded so a missing texture never draws un-silvered
                            float fade = 1f;
                            var t = DynamicTextureRenderer.getTextureIfPresent(k.reflectionTexture);
                            if (t instanceof MirrorReflectionTexture mrt) fade = mrt.getFadeProgress();
                            setFloat(shader, "Fade", fade);
                        },
                        () -> {}))
                .createCompositeState(false);
        return create("vista_mirror_material", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                1536, true, false, compositeState);
    });

    public static final RenderType NOISE =
            create("vista_noise", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
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

    private static void setFloat2(ShaderInstance shader, String name, float v1, float v2) {
        shader.safeGetUniform(name).set(v1, v2);
    }

    public static ResourceLocation getColoredShader(DyeColor c) {
        return VistaMod.res("shaders/post/" + c.getSerializedName() + "_tint.json");
    }


    public static final RenderType WAVE_PARTICLE =
            create("vista_wave_gate", DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536, true, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(WAVE_GATE_SHADER_STATE)
                            .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS,
                                    false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .createCompositeState(false));
}
