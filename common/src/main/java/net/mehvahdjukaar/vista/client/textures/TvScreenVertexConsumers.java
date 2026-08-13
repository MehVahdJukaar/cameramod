package net.mehvahdjukaar.vista.client.textures;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.CrtOverlay;
import net.mehvahdjukaar.vista.client.VistaRenderTypes;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.cassette.CassetteTape;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;

public class TvScreenVertexConsumers {
    private static final ResourceLocation DUMMY_TEXTURE = VistaMod.res("textures/cassette_tape/color_bars_static.png");

    private static final Map<Smile, ResourceLocation> SMILES = Map.of(
            Smile.HAPPY, VistaModClient.SMILE_SCREEN,
            Smile.NEUTRAL, VistaModClient.NEUTRAL_SCREEN,
            Smile.SAD, VistaModClient.SAD_SCREEN
    );

    private enum Smile {
        HAPPY, NEUTRAL, SAD;

        public static Smile fromHealth(LivingEntity entity) {
            float health = entity.getHealth() / entity.getMaxHealth();
            if (health > 0.66f) {
                return HAPPY;
            } else if (health > 0.33f) {
                return NEUTRAL;
            } else {
                return SAD;
            }
        }
    }

    public static VertexConsumer getTapeVC(MultiBufferSource buffer, @NotNull Holder<CassetteTape> tapeHolder, Vec2i scale,
                                           int tickCount, CrtOverlay overlay, IntAnimationState switchAnim) {
        ResourceLocation tapeTexture = tapeHolder.value().assetId();
        return createAnimatedStripVC(buffer, tapeTexture, scale, tickCount, overlay, switchAnim);
    }

    public static VertexConsumer getNoiseVC(MultiBufferSource buffer, Vec2i scale, IntAnimationState switchAnim) {
        return createVC(DUMMY_TEXTURE, scale, 1, 1,
                CrtOverlay.NONE, switchAnim, IntAnimationState.MAX_ANIM, buffer::getBuffer);
    }

    public static @NotNull VertexConsumer getDownloadingVc(MultiBufferSource buffer, Vec2i scale, int progressPercent, IntAnimationState switchAnim) {
        return createProgressStripVC(buffer, VistaModClient.DOWNLOADING_SCREEN, scale, progressPercent, CrtOverlay.NONE, switchAnim);
    }

    public static @NotNull VertexConsumer getWaitingVc(MultiBufferSource buffer, Vec2i scale, int tickCount, IntAnimationState switchAnim) {
        return createAnimatedStripVC(buffer, VistaModClient.BLACK_LOADING_SCREEN, scale, tickCount, CrtOverlay.NONE, switchAnim);
    }

    // same loading dots as getWaitingVc but orange, for a transient failure being retried
    public static @NotNull VertexConsumer getRetryingVc(MultiBufferSource buffer, Vec2i scale, int tickCount, IntAnimationState switchAnim) {
        return createAnimatedStripVC(buffer, VistaModClient.RETRYING_SCREEN, scale, tickCount, CrtOverlay.NONE, switchAnim);
    }

    // full screen error card: black screen with just the symbol
    public static @NotNull VertexConsumer getErrorVc(MultiBufferSource buffer, Vec2i scale, ResourceLocation screen, IntAnimationState switchAnim) {
        return createAnimatedStripVC(buffer, screen, scale, 0, CrtOverlay.NONE, switchAnim);
    }


    public static VertexConsumer getNoEnergyVC(MultiBufferSource buffer, Vec2i scale, IntAnimationState switchAnim) {
        return createAnimatedStripVC(buffer, VistaModClient.NO_ENERGY_SCREEN, scale, 0, CrtOverlay.NONE, switchAnim);
    }

    public static VertexConsumer getBarsVC(MultiBufferSource buffer, Vec2i scale, IntAnimationState switchAnim) {
        return createAnimatedStripVC(buffer, VistaModClient.BARS_SCREEN, scale, 0, CrtOverlay.NONE, switchAnim);
    }

    // Draws a cassette asset by id with no tape entry behind it, for places with no level and so no
    // access to the cassette_tape registry, like the config screen showcase.
    public static VertexConsumer getChannelVC(MultiBufferSource buffer, ResourceLocation assetId, Vec2i scale, int tickCount) {
        return createAnimatedStripVC(buffer, assetId, scale, tickCount, CrtOverlay.NONE, IntAnimationState.NO_ANIM);
    }

    public static VertexConsumer getSmileTapeVC(MultiBufferSource buffer, LivingEntity player) {
        Smile smile = Smile.fromHealth(player);
        ResourceLocation id = SMILES.get(smile);
        Vec2i scale = new Vec2i(12, 12);//always on a 1x1 tv
        return createAnimatedStripVC(buffer, id, scale, player.tickCount, CrtOverlay.NONE, IntAnimationState.NO_ANIM);
    }

    private static VertexConsumer createAnimatedStripVC(MultiBufferSource buffer,
                                                        ResourceLocation id,
                                                        Vec2i scale, int tickCount,
                                                        CrtOverlay overlay,
                                                        IntAnimationState switchAnim) {
        return createStripVC(buffer, id, scale, tickCount, overlay, switchAnim, false);
    }

    private static VertexConsumer createProgressStripVC(MultiBufferSource buffer,
                                                        ResourceLocation id,
                                                        Vec2i scale, int progressPercent,
                                                        CrtOverlay overlay,
                                                        IntAnimationState switchAnim) {
        return createStripVC(buffer, id, scale, progressPercent, overlay, switchAnim, true);
    }

    private static VertexConsumer createStripVC(MultiBufferSource buffer,
                                                ResourceLocation id,
                                                Vec2i scale, int value,
                                                CrtOverlay overlay,
                                                IntAnimationState switchAnim,
                                                boolean directFrameIndex) {
        AnimatedStripTexture animatedStrip = CassetteTexturesManager.INSTANCE.getAnimatedTexture(id);
        if (animatedStrip == null) {
            return getNoiseVC(buffer, scale, switchAnim); //missing
        }

        ResourceLocation textureId = animatedStrip.getTextureLocation();
        AnimationStripData stripData = animatedStrip.getStripData();
        int frameIndex = directFrameIndex ? progressToFrameIndex(stripData, value) : value;

        return createVC(textureId, scale, stripData.frameRelativeW(), stripData.frameRelativeH(),
                overlay, switchAnim, IntAnimationState.NO_ANIM, rt ->
                        new AnimatedStripVertexConsumer(frameIndex, stripData, buffer.getBuffer(rt), directFrameIndex));
    }

    private static int progressToFrameIndex(AnimationStripData stripData, int progressPercent) {
        int clamped = Mth.clamp(progressPercent, 0, 100);
        int maxFrameIndex = Math.max(0, stripData.frameCount() - 1);
        return Mth.clamp(Math.round(clamped * maxFrameIndex / 100.0f), 0, maxFrameIndex);
    }

    public static VertexConsumer getSingleTextureVC(MultiBufferSource buffer,
                                                    ResourceLocation textureId,
                                                    CrtOverlay overlay,
                                                    Vec2i scale,
                                                    IntAnimationState switchAnim,
                                                    IntAnimationState noiseAnim) {
        return createVC(textureId, scale, 1, 1,
                overlay, switchAnim, noiseAnim, buffer::getBuffer);
    }

    public static VertexConsumer createVC(ResourceLocation texture,
                                          Vec2i scale, float frameW, float frameH,
                                          CrtOverlay overlay,
                                          IntAnimationState switchAnim,
                                          IntAnimationState noiseAnim,
                                          Function<RenderType, VertexConsumer> func) {
        boolean hasSfx = hasSfx();
        if (!hasSfx && switchAnim.isDecreasing()) return EMPTY_VC;

        RenderType rt = hasSfx ?
                VistaRenderTypes.crtRenderType(texture, scale,
                        frameW, frameH,
                        switchAnim, noiseAnim, overlay) :
                RenderType.entitySolid(texture); //for normal
        return func.apply(rt);
    }


    private static boolean hasSfx() {
        return !VistaLevelRenderer.isRenderingLiveFeed() &&
                Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FAST
                && ClientConfigs.SCREEN_EFFECTS.get();
    }


    private static final VertexConsumer EMPTY_VC = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    };

}
