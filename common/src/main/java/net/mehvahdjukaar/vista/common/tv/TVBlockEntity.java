package net.mehvahdjukaar.vista.common.tv;

import net.mehvahdjukaar.candlelight.api.VirtualOverride;
import net.mehvahdjukaar.moonlight.api.block.ItemDisplayTile;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaPlatStuff;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.mehvahdjukaar.vista.common.cassette.ITvCassette;
import net.mehvahdjukaar.vista.common.chunk_tracking.ServerCameraChunkManager;
import net.mehvahdjukaar.vista.common.enderman.TVEndermanObservationController;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeContent;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeItem;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeMaps;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class TVBlockEntity extends ItemDisplayTile {

    @Nullable
    private TVEndermanObservationController observationController = null;

    private boolean paused = false;
    private int videoPlaybackTicks = 0;
    private boolean showsTime = false;
    // slideshow: last map frame index whose data we pushed to nearby players (-1 = none)
    private int lastSentMapFrame = -1;

    //client, I think
    private IVideoSource videoSource = IVideoSource.EMPTY;

    private Vec2i connectedTvsAmount = Vec2i.ONE;

    private int soundLoopTicks = 0;
    public final IntAnimationState fadeAnimation = new IntAnimationState(3, 9);
    public final IntAnimationState endermanAnimation = new IntAnimationState(20, 20, 0.6f);
    private boolean isLookingAtEnderman = false;
    private boolean wasScreenOn = false;

    private boolean hasEnergy = false;

    public Object energyCap = null;

    public TVBlockEntity(BlockPos pos, BlockState state) {
        super(VistaMod.TV_TILE.get(), pos, state);
    }

    @Override
    public void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
        super.saveAdditional(compound, registries);
        compound.putInt("ConnectionWidth", connectedTvsAmount.x());
        compound.putInt("ConnectionHeight", connectedTvsAmount.y());
        compound.putBoolean("Paused", paused);
        compound.putInt("VideoPlaybackTicks", videoPlaybackTicks);
        compound.putBoolean("ShowsTime", showsTime);
        if (hasEnergy) compound.putBoolean("HasEnergy", hasEnergy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains("ConnectionHeight")) {
            int w = tag.getInt("ConnectionWidth");
            this.connectedTvsAmount = new Vec2i(w, w);
        } else {
            this.connectedTvsAmount = new Vec2i(Math.max(1, tag.getInt("ConnectionWidth")),
                    Math.max(1, tag.getInt("ConnectionHeight")));
        }
        this.paused = tag.getBoolean("Paused");
        this.videoPlaybackTicks = tag.getInt("VideoPlaybackTicks");
        this.showsTime = tag.getBoolean("ShowsTime");
        // must not be guarded on the key being there: the tag is only written when powered, and the
        // client reuses the same tile across updates, so a missing key has to clear the flag
        this.hasEnergy = tag.getBoolean("HasEnergy");
        updateObservationController();
    }

    public boolean showsTime() {
        return showsTime;
    }

    @NotNull
    public IVideoSource getVideoSource() {
        if (!this.hasEnergy()) return IVideoSource.NO_ENERGY;
        return videoSource;
    }

    public Vec2i getConnectedCount() {
        return connectedTvsAmount;
    }

    public void setConnectionSize(Vec2i size) {
        this.connectedTvsAmount = size;
    }

    public boolean isPaused() {
        return paused;
    }

    @Override
    public SoundEvent getAddItemSound() {
        return VistaMod.CASSETTE_INSERT_SOUND.get();
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("tv");
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return true;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return stack.getItem() instanceof ITvCassette;
    }

    @Override
    public void serverSideUpdateWhenChanged(HolderLookup.Provider registries) {
        super.serverSideUpdateWhenChanged(registries);
        ItemStack displayedItem = this.getDisplayedItem();
        if (displayedItem.isEmpty()) {
            this.paused = false;
            this.videoPlaybackTicks = 0;
        }
        updateObservationController();
    }

    private void updateObservationController() {
        ItemStack displayedItem = this.getDisplayedItem();
        var uuid = displayedItem.get(VistaMod.LINKED_FEED_COMPONENT.get());
        this.observationController = uuid == null ? null : new TVEndermanObservationController(uuid, this);
    }

    @Override
    public void clientSideUpdateWhenChanged(HolderLookup.Provider registries) {
        super.clientSideUpdateWhenChanged(registries);
        this.videoSource = IVideoSource.create(this.getDisplayedItem());
        this.videoPlaybackTicks = 0;
    }

    public ItemInteractionResult interactWithPlayerItem(Player player, InteractionHand handIn, ItemStack stack, int slot, BlockHitResult hit) {

        boolean powered = this.getBlockState().getValue(TVBlock.POWER_STATE).isOn();
        ItemStack current = this.getDisplayedItem();
        boolean isEmpty = current.isEmpty();

        if (stack.is(Items.CLOCK)) {
            this.showsTime = !this.showsTime;
            this.setChanged();
            return ItemInteractionResult.sidedSuccess(this.level.isClientSide);
        }

        //toggle pause
        if (!isEmpty && powered && player.isSecondaryUseActive()) {
            this.paused = !this.paused;
            this.setChanged();
            return ItemInteractionResult.sidedSuccess(this.level.isClientSide);
        }

        if (!isEmpty && (canPlaceItem(0, stack) || stack.isEmpty())) {
            level.playSound(player, worldPosition, VistaMod.CASSETTE_EJECT_SOUND.get(),
                    SoundSource.BLOCKS, 1, 1);
            //pop current
            Vec3 vec3 = hit.getLocation().add(new Vec3(hit.getDirection().step().mul(0.5f)));

            ItemStack itemStack2 = current.copy();
            ItemEntity itemEntity = new ItemEntity(this.level, vec3.x(), vec3.y(), vec3.z(), itemStack2);
            itemEntity.setDefaultPickUpDelay();
            this.level.addFreshEntity(itemEntity);
            this.clearContent();
            this.setChanged();
            return ItemInteractionResult.sidedSuccess(this.level.isClientSide);
        }

        //add item

        var result = super.interactWithPlayerItem(player, handIn, stack, slot);
        if (result.consumesAction() && isEmpty && powered &&
                connectedTvsAmount.lengthSquared() >= 9 &&
                player instanceof ServerPlayer sp) {
            //advancement
            Utils.awardAdvancement(sp, VistaMod.CINEMA_ADVANCEMENT);
        }
        return result;
    }

    public int getPlaybackTicks() {
        return videoPlaybackTicks;
    }

    // No @Override: on NeoForge this satisfies IBlockEntityExtension and is called automatically
    // for both chunk loading and block placement. On Fabric it is called explicitly from
    // LevelChunkTVTrackingMixin so we get the same coverage on both platforms.
    @VirtualOverride("neoforge")
    public void onLoad() {
        ServerCameraChunkManager.trackTv(this);
    }

    // Counterpart to onLoad(); on NeoForge called by LevelChunk.clearAllBlockEntities,
    // on Fabric also called from LevelChunkTVTrackingMixin.
    @VirtualOverride("neoforge")
    public void onChunkUnloaded() {
        ServerCameraChunkManager.untrackTv(this);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (PlatHelper.getPlatform().isFabric()) {
            ServerCameraChunkManager.trackTv(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (PlatHelper.getPlatform().isFabric()) {
            ServerCameraChunkManager.untrackTv(this);
        }
    }

    public static void onTick(Level world, BlockPos pos, BlockState state, TVBlockEntity tv) {
        boolean powered = state.getValue(TVBlock.POWER_STATE).isOn();
        //both sides

        // energy lives on the server only, the client just reads the synced powered flag
        if (!world.isClientSide && CommonConfigs.doesTvConsumeForgeEnergy()) {
            VistaPlatStuff.tickEnergy(tv);
        }
        if (powered) {
            if (!tv.paused) tv.videoPlaybackTicks++;
        } else {
            tv.fadeAnimation.decrement();
            tv.videoPlaybackTicks = 0;
        }

        tv.wasScreenOn = powered;
        if (world.isClientSide) {

            if (powered) {
                if (ClientConfigs.TURN_OFF_EFFECTS.get()) tv.fadeAnimation.increment();
                if (!tv.hasEnergy()) return;
                float duration = tv.videoSource.getVideoDuration();
                // Play on the first tick (soundLoopTicks == 0) so the sound starts immediately
                // when a cassette is inserted, then repeat every `duration` ticks.
                if (tv.soundLoopTicks == 0) {
                    SoundEvent sound = tv.videoSource.getVideoSound();
                    if (sound != null) {
                        world.playLocalSound(pos, sound, SoundSource.BLOCKS, 1, 1.0f, false);
                    }
                }
                if (++tv.soundLoopTicks >= (duration)) {
                    tv.soundLoopTicks = 0;
                }
            } else {
                tv.soundLoopTicks = 0;
            }
            if (tv.isLookingAtEnderman) {
                tv.endermanAnimation.increment();
            } else {
                tv.endermanAnimation.decrement();
            }


        } else {
            if (!tv.hasEnergy()) return;

            tv.pushSlideshowMaps(world, pos, powered);

            //enderman stuff
            // Run once every TV_OBSERVATION_INTERVAL ticks, offset by pos so different TVs
            // don't all fire on the same tick. Previous `% 27 == 0` skipped only 1/27 ticks
            // (no real staggering).
            if ((world.getGameTime() + pos.asLong()) % TV_OBSERVATION_INTERVAL != 0) {
                return;
            }
            boolean hasAngeredEntity = false;
            if (powered && tv.observationController != null) {
                //server tick logic
                hasAngeredEntity = tv.observationController.tick();
            }
            boolean couldSeeEnderman = tv.isLookingAtEnderman;
            //if changed send block event
            if (hasAngeredEntity != couldSeeEnderman) {
                tv.isLookingAtEnderman = hasAngeredEntity;
                world.blockEvent(pos, state.getBlock(), 1, hasAngeredEntity ? 1 : 0);
            }
        }
    }


    public void updateEndermanLookAnimation(int param) {
        this.isLookingAtEnderman = param > 0;
    }

    public boolean isScreenOn(float partialTicks) {
        return this.wasScreenOn || this.fadeAnimation.getValue(partialTicks) != 0;
    }

    private static final int TV_OBSERVATION_INTERVAL = 10;

    private static final int EDGE_PIXEL_LEN = 4;
    public static final int MIN_SCREEN_PIXEL_SIZE = 16 - EDGE_PIXEL_LEN;
    public static final Vec2i MIN_SCREEN_PIXEL_SIZE_VEC = new Vec2i(MIN_SCREEN_PIXEL_SIZE, MIN_SCREEN_PIXEL_SIZE);

    public Vec2 getScreenBlockCenter() {
        return new Vec2(0.5f * (connectedTvsAmount.x() - 1), 0.5f * (connectedTvsAmount.y() - 1));
    }

    public int getScreenPixelWidth() {
        return Math.max(1, connectedTvsAmount.x()) * 16 - EDGE_PIXEL_LEN;
    }

    public int getScreenPixelHeight() {
        return Math.max(1, connectedTvsAmount.y()) * 16 - EDGE_PIXEL_LEN;
    }

    public Vec2i getScreenPixelSize() {
        return new Vec2i(getScreenPixelWidth(), getScreenPixelHeight());
    }

    public int getComparatorOutput() {
        ItemStack displayed = this.getDisplayedItem();
        if (displayed.getItem() instanceof ITvCassette tc) return tc.getAnalogSignalStrengthInTv(displayed);
        return 0;
    }

    @Nullable
    public UUID getViewingFeedId() {
        if (this.isPaused() || !this.getBlockState().getValue(TVBlock.POWER_STATE).isOn()) {
            return null;
        }
        if (level != null && level.isClientSide) {
            return this.videoSource instanceof IBroadcastSource bc ? bc.getBroadcastUUID() : null;
        } else {
            if (!hasEnergy()) return null;
            return getDisplayedItem().get(VistaMod.LINKED_FEED_COMPONENT.get());
        }
    }

    private boolean hasEnergy() {
        return !CommonConfigs.doesTvNeedExternalPower() || hasEnergy;
    }

    public boolean isHasEnergy() {
        return hasEnergy;
    }

    // Set by whichever power system is in charge: CompatRefurbishedFurnitureSelfTvBlockEntityMixin
    // when the electricity integration is on, TvEnergyHandler otherwise.
    public void setHasEnergy(boolean powered) {
        if (this.hasEnergy != powered) {
            this.hasEnergy = powered;
            this.setChanged();
        }
    }

    // Maps stored on a picture tape aren't tracked by vanilla, so when one is playing we push the
    // current (and next) frame's map data to nearby players as the slideshow advances.
    private void pushSlideshowMaps(Level level, BlockPos pos, boolean powered) {
        if (!powered || paused || !(level instanceof ServerLevel serverLevel)) {
            lastSentMapFrame = -1;
            return;
        }
        ItemStack displayed = getDisplayedItem();
        if (!(displayed.getItem() instanceof PictureTapeItem)) {
            lastSentMapFrame = -1;
            return;
        }
        PictureTapeContent content = PictureTapeItem.getContent(displayed);
        int count = content.size();
        if (count == 0) return;
        int speed = Math.max(1, content.playbackSpeed());
        int frame = (videoPlaybackTicks / speed) % count;
        if (frame == lastSentMapFrame) return;
        lastSentMapFrame = frame;

        List<ItemStack> pics = content.pictures().toList();
        ItemStack current = pics.get(frame);
        ItemStack next = pics.get((frame + 1) % count);
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp.blockPosition().distSqr(pos) > 96 * 96) continue;
            PictureTapeMaps.sendMapData(sp, current);
            if (next != current) PictureTapeMaps.sendMapData(sp, next);
        }
    }
}
