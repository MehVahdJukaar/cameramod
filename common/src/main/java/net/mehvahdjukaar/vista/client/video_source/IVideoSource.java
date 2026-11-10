package net.mehvahdjukaar.vista.client.video_source;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.textures.ScreenFit;
import net.mehvahdjukaar.vista.client.textures.TvScreenVertexConsumers;
import net.mehvahdjukaar.vista.common.cassette.CassetteItem;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeItem;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public interface IVideoSource {

    IVideoSource EMPTY = new Empty();
    IVideoSource NO_ENERGY = new NoEnergy();
    IVideoSource NO_SIGNAL = new NoSignal();

    @NotNull
    VertexConsumer getVideoFrameBuilder(
            TVBlockEntity tv, float partialTick, MultiBufferSource buffer,
            boolean shouldUpdate, Vec2i screenSize, Vec2i pixelEffectRes,
            int videoAnimationTick, boolean paused,
            IntAnimationState switchAnim, IntAnimationState staticAnim, boolean showsTime);

    default void updateAudio(TVBlockEntity tv, boolean playing) {
    }

    default boolean isPlayingMusic(TVBlockEntity tv) {
        return false;
    }

    static void notifyNearbyDancers(TVBlockEntity tv, boolean playing) {
        Minecraft mc = Minecraft.getInstance();
        Level level = tv.getLevel();
        AABB hearingArea = tv.getScreenBlocksBox().inflate(3);
        //shoulder birds only hear it through LevelRenderer
        for (Player player : level.getEntitiesOfClass(Player.class, hearingArea)) {
            mc.levelRenderer.notifyNearbyEntities(level, tv.getNearestScreenTile(player.position()), playing);
        }
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, hearingArea)) {
            mob.setRecordPlayingNearby(tv.getNearestScreenTile(mob.position()), playing);
        }
    }

    default ScreenFit getScreenFit() {
        return ScreenFit.FILL;
    }

    static IVideoSource create(ItemStack stack) {
        //we could have also implemented in the item but its better separation like this
        if (stack.getItem() instanceof CassetteItem) {
            var tape = stack.get(VistaMod.CASSETTE_TAPE_COMPONENT.get());
            if (tape != null) return new CassetteTapeVideoSource(tape);
        } else if (stack.getItem() instanceof PictureTapeItem) {
            return new PictureTapeVideoSource(stack);
        } else if (stack.has(VistaMod.LINKED_FEED_COMPONENT.get())) {
            return new BroadcastVideoSource(stack.get(VistaMod.LINKED_FEED_COMPONENT.get()));
        }
        return stack.isEmpty() ? EMPTY : NO_SIGNAL;
    }

    class Empty implements IVideoSource {

        @Override
        public @NotNull VertexConsumer getVideoFrameBuilder(
                TVBlockEntity tv, float partialTick, MultiBufferSource buffer,
                boolean shouldUpdate, Vec2i screenSize, Vec2i pixelEffectRes,
            int videoAnimationTick, boolean paused,
            IntAnimationState switchAnim, IntAnimationState staticAnim, boolean showsTime) {

            if (VistaModClient.isFFmpegDownloading()) {
                int progress = VistaModClient.getFFmpegDownloadProgress();
                if (progress >= 0) {
                    return TvScreenVertexConsumers.getDownloadingVc(buffer, pixelEffectRes, progress, switchAnim);
                }
            }
            return TvScreenVertexConsumers.getBarsVC(buffer, pixelEffectRes, switchAnim);
        }
    }

    class NoEnergy implements IVideoSource {

        @Override
        public @NotNull VertexConsumer getVideoFrameBuilder(
                TVBlockEntity tv, float partialTick, MultiBufferSource buffer,
                boolean shouldUpdate, Vec2i screenSize, Vec2i pixelEffectRes,
                int videoAnimationTick, boolean paused,
                IntAnimationState switchAnim, IntAnimationState staticAnim, boolean showsTime) {

            return TvScreenVertexConsumers.getNoEnergyVC(buffer, pixelEffectRes, switchAnim);
        }
    }

    class NoSignal implements IVideoSource {
        @Override
        public @NotNull VertexConsumer getVideoFrameBuilder(
                TVBlockEntity tv, float partialTick, MultiBufferSource buffer,
                boolean shouldUpdate, Vec2i screenSize, Vec2i pixelEffectRes,
                int videoAnimationTick, boolean paused,
                IntAnimationState switchAnim, IntAnimationState staticAnim, boolean showsTime) {

            return TvScreenVertexConsumers.getNoiseVC(buffer, pixelEffectRes, switchAnim);
        }
    }
}
