package net.mehvahdjukaar.vista.client.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.mehvahdjukaar.moonlight.api.client.gui.ConfigScreenExtensions;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.renderer.TvBlockEntityRenderer;
import net.mehvahdjukaar.vista.client.textures.TvScreenVertexConsumers;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TvShowcaseWidget extends AbstractWidget {

    public static final ConfigScreenExtensions.Showcase SHOWCASE = new ConfigScreenExtensions.Showcase() {
        @Override
        public AbstractWidget create(String modId, int x, int y, int width, int maxHeight) {
            return new TvShowcaseWidget(x, y, width, maxHeight);
        }

        @Override
        public boolean replacesCarousel() {
            return false;
        }
    };

    // raw texture asset ids, not tape registry entries: there's no level here, so the cassette_tape datapack
    // registry isn't loaded
    private static final List<ResourceLocation> CHANNELS = List.of(
            VistaMod.res("block_wave"),
            VistaModClient.BARS_SCREEN,
            VistaMod.res("bounce"),
            VistaModClient.SMILE_SCREEN);

    private static final int MIN_CHANNEL_TICKS = 9 * 20;
    private static final int MAX_CHANNEL_TICKS = 18 * 20;
    private static final int MIN_STATIC_TICKS = 12;
    private static final int MAX_STATIC_TICKS = 22;

    // between the inventory-slot angle every block model gets (30/225) and dead-on, so it reads as a solid box
    // without turning the screen away
    private static final float TILT = 15f;
    private static final float YAW = 198f;            // the screen is on the block's north face, so 180 turns it to us
    // of the smaller widget side. the turned box projects wider than one block unit, so this leaves room for the
    // corners to swing out without reaching the item carousel underneath
    private static final float BLOCK_FILL = 0.72f;
    private static final float SCREEN_NUDGE = 0.005f; // the gui has no polygon offset to lean on, so lift it manually
    private static final float SCREEN_HALF_SIZE = TVBlockEntity.MIN_SCREEN_PIXEL_SIZE / 32f;
    private static final float SECONDS_PER_TICK = 0.05f;
    // sounds.json keeps this one at 0.01 because in world it's a quiet loop the tv plays forever. here it's a
    // one-off burst that should be heard, so it needs scaling back up
    private static final float STATIC_VOLUME = 50f;

    private final RandomSource random = RandomSource.create();

    private int channel;
    private int channelTicksLeft;
    private int staticTicksLeft;
    private int animationTicks;
    private int clockTicks;
    private float tickTimer;
    private long lastMs = -1;
    @Nullable
    private SoundInstance staticSound;

    public TvShowcaseWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("block.vista.television"));
        this.channel = this.random.nextInt(CHANNELS.size());
        this.channelTicksLeft = this.rollChannelTicks();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.advance();
        // the crt noise scrolls off the vanilla GameTime uniform, which only the level render keeps current. there's
        // no level behind a config screen, so drive it here or the static sits frozen on a single frame
        RenderSystem.setShaderGameTime(this.clockTicks, this.tickTimer / SECONDS_PER_TICK);

        float size = Math.min(this.width, this.height) * BLOCK_FILL;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.getX() + this.width / 2f, this.getY() + this.height / 2f, 150);
        // same chain an item goes through in a slot: the negative y cancels out the gui projection's own flip, so the
        // quads keep their winding and the culled block render types don't turn inside out
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(TILT));
        pose.mulPose(Axis.YP.rotationDegrees(YAW));
        pose.translate(-0.5f, -0.5f, -0.5f);  // the block renderer starts from the block corner

        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(VistaMod.TV.get().defaultBlockState(),
                pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        pose.translate(0.5f, 0.5f, -SCREEN_NUDGE);
        TvBlockEntityRenderer.addQuad(this.currentFrame(buffer), pose,
                -SCREEN_HALF_SIZE, -SCREEN_HALF_SIZE, SCREEN_HALF_SIZE, SCREEN_HALF_SIZE, LightTexture.FULL_BRIGHT);

        graphics.flush();
        pose.popPose();
    }

    private VertexConsumer currentFrame(MultiBufferSource buffer) {
        Vec2i res = TVBlockEntity.MIN_SCREEN_PIXEL_SIZE_VEC;
        if (this.staticTicksLeft > 0) {
            return TvScreenVertexConsumers.getNoiseVC(buffer, res, IntAnimationState.NO_ANIM);
        }
        return TvScreenVertexConsumers.getChannelVC(buffer, CHANNELS.get(this.channel), res, this.animationTicks);
    }

    private void advance() {
        long now = Util.getMillis();
        float dt = this.lastMs < 0 ? 0 : Math.min((now - this.lastMs) / 1000f, 0.1f); // clamp screen-reopen gaps
        this.lastMs = now;
        this.tickTimer += dt;
        while (this.tickTimer >= SECONDS_PER_TICK) {
            this.tickTimer -= SECONDS_PER_TICK;
            this.tick();
        }
    }

    private void tick() {
        this.clockTicks++;
        this.animationTicks++;
        if (this.staticTicksLeft > 0) {
            if (--this.staticTicksLeft == 0) {
                // any channel but the one we just left
                this.channel = (this.channel + 1 + this.random.nextInt(CHANNELS.size() - 1)) % CHANNELS.size();
                this.animationTicks = 0;
                this.channelTicksLeft = this.rollChannelTicks();
                this.stopStatic();
            }
        } else if (--this.channelTicksLeft <= 0) {
            this.startStatic();
        }
    }

    private void startStatic() {
        this.staticTicksLeft = Mth.nextInt(this.random, MIN_STATIC_TICKS, MAX_STATIC_TICKS);
        this.staticSound = SimpleSoundInstance.forUI(VistaMod.TV_STATIC_SOUND.get(), 1, STATIC_VOLUME);
        Minecraft.getInstance().getSoundManager().play(this.staticSound);
    }

    private void stopStatic() {
        if (this.staticSound != null) {
            Minecraft.getInstance().getSoundManager().stop(this.staticSound);
            this.staticSound = null;
        }
    }

    private int rollChannelTicks() {
        return Mth.nextInt(this.random, MIN_CHANNEL_TICKS, MAX_CHANNEL_TICKS);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // a click during the static burst is already a channel switch in progress
        if (this.staticTicksLeft == 0) {
            this.startStatic();
        }
    }

    @Override
    public void playDownSound(SoundManager handler) {
        // the static burst is the click feedback, a ui click on top of it would just sound like a misfire
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
