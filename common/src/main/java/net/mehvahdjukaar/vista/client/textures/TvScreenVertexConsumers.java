package net.mehvahdjukaar.vista.client.textures;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.vista.VistaMod;
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
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public class TvScreenVertexConsumers {

    private static final ResourceLocation DUMMY_LOCATION = VistaMod.res("textures/cassette_tape/color_bars.png");

    private static final ResourceLocation BARS_LOCATION = VistaMod.res("color_bars");
    private static final ResourceLocation SMILE_LOCATION = VistaMod.res("smile");
    private static final ResourceLocation NEUTRAL_LOCATION = VistaMod.res("neutral");
    private static final ResourceLocation SAD_LOCATION = VistaMod.res("sad");
    private static final Map<Smile, ResourceLocation> SMILES = Map.of(
            Smile.HAPPY, SMILE_LOCATION,
            Smile.NEUTRAL, NEUTRAL_LOCATION,
            Smile.SAD, SAD_LOCATION
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

    public static VertexConsumer getTapeVC(MultiBufferSource buffer, @NotNull Holder<CassetteTape> tapeHolder, int scale,
                                           int tickCount, boolean paused, IntAnimationState switchAnim) {
        ResourceLocation tapeTexture = tapeHolder.value().assetId();
        return createAnimatedStripVC(buffer, tapeTexture, scale, tickCount, paused, switchAnim);
    }

    public static VertexConsumer getNoiseVC(MultiBufferSource buffer, int scale, IntAnimationState switchAnim) {
        return createVC(DUMMY_LOCATION, scale, 1, 1,
                CrtOverlay.NONE, switchAnim, IntAnimationState.MAX_ANIM, buffer::getBuffer);
    }

    public static VertexConsumer getBarsVC(MultiBufferSource buffer, int scale, boolean paused, IntAnimationState switchAnim) {
        return createAnimatedStripVC(buffer, BARS_LOCATION, scale, 0, paused, switchAnim);
    }

    public static VertexConsumer getSmileTapeVC(MultiBufferSource buffer, LivingEntity player) {
        Smile smile = Smile.fromHealth(player);
        ResourceLocation id = SMILES.get(smile);
        int scale = 12;//always on a 1x1 tv
        return createAnimatedStripVC(buffer, id, scale, player.tickCount, false, IntAnimationState.NO_ANIM);
    }

    private static VertexConsumer createAnimatedStripVC(MultiBufferSource buffer,
                                                        ResourceLocation id,
                                                        int scale, int tickCount,
                                                        boolean paused,
                                                        IntAnimationState switchAnim) {
        AnimatedStripTexture animatedStrip = CassetteTexturesManager.INSTANCE.getAnimatedTexture(id);
        if (animatedStrip == null) {
            return getNoiseVC(buffer, scale, switchAnim); //missing
        }

        CrtOverlay overlay = paused ? CrtOverlay.PAUSE : CrtOverlay.NONE;
        ResourceLocation textureId = animatedStrip.getTextureLocation();
        AnimationStripData stripData = animatedStrip.getStripData();

        return createVC(textureId, scale, stripData.frameRelativeW(), stripData.frameRelativeH(),
                overlay, switchAnim, IntAnimationState.NO_ANIM, rt ->
                        new AnimatedStripVertexConsumer(tickCount, stripData, buffer.getBuffer(rt)));
    }

    public static VertexConsumer getLiveFeedVC(MultiBufferSource buffer,
                                               LiveFeedTexture tex,
                                               int scale, boolean paused,
                                               IntAnimationState switchAnim,
                                               IntAnimationState noiseAnim) {
        CrtOverlay overlay = tex.getOverlay(paused) ;
        return createVC(tex.getTextureLocation(), scale, 1, 1,
                overlay, switchAnim, noiseAnim, buffer::getBuffer);
    }

    public static VertexConsumer createVC(ResourceLocation texture,
                                          int scale, float frameW, float frameH,
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
        public VertexConsumer vertex(double d, double e, double f) {
            return this;
        }

        @Override
        public VertexConsumer color(int i, int j, int k, int l) {
            return this;
        }

        @Override
        public VertexConsumer uv(float f, float g) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int i, int j) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int i, int j) {
            return this;
        }

        @Override
        public VertexConsumer normal(float f, float g, float h) {
            return this;
        }

        @Override
        public void endVertex() {

        }

        @Override
        public void defaultColor(int i, int j, int k, int l) {

        }

        @Override
        public void unsetDefaultColor() {

        }

    };

}
