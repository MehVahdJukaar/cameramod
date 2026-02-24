package net.mehvahdjukaar.vista.common.tv;

import net.mehvahdjukaar.moonlight.api.block.ItemDisplayTile;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.moonlight.api.util.math.MthUtils;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.mehvahdjukaar.vista.common.tv.enderman.TVEndermanObservationController;
import net.mehvahdjukaar.vista.common.view_finder.EntityDetectorHelper;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.exposure.ExposureCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TVBlockEntity extends ItemDisplayTile {

    @Nullable
    private TVEndermanObservationController observationController = null;

    private boolean paused = false;
    private int videoPlaybackTicks = 0;

    //client, I think
    private IVideoSource videoSource = IVideoSource.EMPTY;

    private int connectedTvsAmount = 1;

    private int soundLoopTicks = 0;
    public final IntAnimationState fadeAnimation = new IntAnimationState(5, 10);
    public final IntAnimationState endermanAnimation = new IntAnimationState(20, 20, 0.6f);
    private boolean isLookingAtEnderman = false;
    private boolean wasScreenOn = false;
    private boolean entityDetectorTriggered = false;
    private int lastDetectedEntityCount = 0;
    private boolean detectorRetriggerPending = false;
    private int detectorRetriggerOffTicks = 0;


    public TVBlockEntity(BlockPos pos, BlockState state) {
        super(VistaMod.TV_TILE.get(), pos, state);
    }

    @Override
    public void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
        super.saveAdditional(compound, registries);
        compound.putInt("ConnectionWidth", connectedTvsAmount);
        compound.putBoolean("Paused", paused);
        compound.putInt("VideoPlaybackTicks", videoPlaybackTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.connectedTvsAmount = Math.max(1, tag.getInt("ConnectionWidth"));
        this.paused = tag.getBoolean("Paused");
        this.videoPlaybackTicks = tag.getInt("VideoPlaybackTicks");
        updateObservationController();
    }

    @NotNull
    public IVideoSource getVideoSource() {
        return videoSource;
    }

    public int getConnectedCount() {
        return connectedTvsAmount;
    }

    public void setConnectionSize(int width) {
        this.connectedTvsAmount = width;
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
        return stack.is(VistaMod.CASSETTE.get()) || stack.is(VistaMod.HOLLOW_CASSETTE.get()) ||
                (CompatHandler.EXPOSURE && ExposureCompat.isPictureItem(stack));
    }

    @Override
    public void updateTileOnInventoryChanged() {
        super.updateTileOnInventoryChanged();
        ItemStack displayedItem = this.getDisplayedItem();
        if (displayedItem.isEmpty()) {
            this.paused = false;
            this.videoPlaybackTicks = 0;
            this.lastDetectedEntityCount = 0;
            this.detectorRetriggerPending = false;
            this.detectorRetriggerOffTicks = 0;
            this.entityDetectorTriggered = false;
        }
        updateObservationController();
    }

    private void updateObservationController() {
        ItemStack displayedItem = this.getDisplayedItem();
        var uuid = displayedItem.get(VistaMod.LINKED_FEED_COMPONENT.get());
        this.observationController = uuid == null ? null : new TVEndermanObservationController(uuid, this);
    }

    @Override
    public void updateClientVisualsOnLoad() {
        super.updateClientVisualsOnLoad();
        this.videoSource = IVideoSource.create(this.getDisplayedItem());
        this.videoPlaybackTicks = 0;
    }

    public ItemInteractionResult interactWithPlayerItem(
            Player player, InteractionHand handIn, ItemStack stack, int slot, BlockHitResult hit) {

        boolean powered = this.getBlockState().getValue(TVBlock.POWER_STATE).isOn();
        ItemStack current = this.getDisplayedItem();
        boolean isEmpty = current.isEmpty();
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
            Vec3 vec3 = hit.getLocation().add(new Vec3(hit.getDirection().step().mul(0.05f)));

            vec3 = vec3.offsetRandom(this.level.random, 0.7F);

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
        if (result.consumesAction() && isEmpty && powered && this.connectedTvsAmount >=3 &&
                player instanceof ServerPlayer sp) {
            //advancement
            Utils.awardAdvancement(sp, VistaMod.CINEMA_ADVANCEMENT);
        }
        return result;
    }

    public int getPlaybackTicks() {
        return videoPlaybackTicks;
    }

    public boolean emitsEntityDetectorSignal() {
        return entityDetectorTriggered;
    }

    public static void onTick(Level world, BlockPos pos, BlockState state, TVBlockEntity tv) {
        boolean powered = state.getValue(TVBlock.POWER_STATE).isOn();
        //both sides

        if (powered) {
            if (!tv.paused) tv.videoPlaybackTicks++;
        } else {
            tv.fadeAnimation.decrement();
            tv.videoPlaybackTicks = 0;
        }
        if (world.isClientSide) {
            tv.wasScreenOn = powered;

            if (powered) {
                if (ClientConfigs.TURN_OFF_EFFECTS.get()) tv.fadeAnimation.increment();
                float duration = tv.videoSource.getVideoDuration();
                if (++tv.soundLoopTicks >= (duration)) {
                    tv.soundLoopTicks = 0;
                    SoundEvent sound = tv.videoSource.getVideoSound();
                    if (sound != null) {
                        world.playLocalSound(pos, sound, SoundSource.BLOCKS, 1, 1.0f, false);
                    }
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
            if (powered) {
                tv.updateEntityDetectorStateServer(world, state);
            } else {
                tv.resetEntityDetectorPulseState(world, state);
            }

            //stagger updates since this is expensive
            if ((world.getGameTime() + pos.asLong()) % 27 == 0) {
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

    private void updateEntityDetectorStateServer(Level world, BlockState state) {
        if (!CommonConfigs.ENTITY_DETECTOR_REDSTONE_SIGNAL.get()) {
            resetEntityDetectorPulseState(world, state);
            return;
        }

        var feedId = this.getDisplayedItem().get(VistaMod.LINKED_FEED_COMPONENT.get());
        if (feedId == null) {
            resetEntityDetectorPulseState(world, state);
            return;
        }

        int detectedCount = 0;
        var source = BroadcastManager.getInstance(world).getBroadcast(feedId, false);
        if (source instanceof ViewFinderBlockEntity viewFinder && viewFinder.hasEntityDetectorFilter()) {
            Level sourceLevel = viewFinder.getLevel();
            if (sourceLevel != null) {
                detectedCount = EntityDetectorHelper.getDetectedEntities(viewFinder, sourceLevel).size();
            }
        }

        if (detectedCount <= 0) {
            resetEntityDetectorPulseState(world, state);
            return;
        }

        if (this.lastDetectedEntityCount > 0 && detectedCount > this.lastDetectedEntityCount) {
            int retriggerOffTicks = Math.max(1, CommonConfigs.ENTITY_DETECTOR_RETRIGGER_OFF_TICKS.get());
            this.detectorRetriggerPending = true;
            this.detectorRetriggerOffTicks = retriggerOffTicks;
        }

        if (this.detectorRetriggerPending) {
            if (this.entityDetectorTriggered) {
                setEntityDetectorTriggered(world, state, false);
            } else if (this.detectorRetriggerOffTicks > 0) {
                this.detectorRetriggerOffTicks--;
            } else {
                setEntityDetectorTriggered(world, state, true);
                this.detectorRetriggerPending = false;
            }
        } else {
            setEntityDetectorTriggered(world, state, true);
        }

        this.lastDetectedEntityCount = detectedCount;
    }

    private void resetEntityDetectorPulseState(Level world, BlockState state) {
        this.lastDetectedEntityCount = 0;
        this.detectorRetriggerPending = false;
        this.detectorRetriggerOffTicks = 0;
        setEntityDetectorTriggered(world, state, false);
    }

    private void setEntityDetectorTriggered(Level world, BlockState state, boolean detected) {
        if (this.entityDetectorTriggered == detected) return;
        this.entityDetectorTriggered = detected;
        this.setChanged();
        this.updateAllTvNeighbors(world, state);
    }

    private void updateAllTvNeighbors(Level level, BlockState state) {
        Direction facing = state.getValue(TVBlock.FACING);
        int size = Math.max(1, this.connectedTvsAmount);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                BlockPos target = MthUtils.relativePos(this.worldPosition, facing, x, y, 0);
                BlockState targetState = level.getBlockState(target);
                if (targetState.getBlock() instanceof TVBlock block) {
                    level.updateNeighborsAt(target, block);
                }
            }
        }
    }


    public void updateEndermanLookAnimation(int param) {
        this.isLookingAtEnderman = param > 0;
    }

    public boolean isScreenOn(float partialTicks) {
        return this.wasScreenOn || this.fadeAnimation.getValue(partialTicks) != 0;
    }

    private static final int EDGE_PIXEL_LEN = 4;
    public static final int MIN_SCREEN_PIXEL_SIZE = 16 - EDGE_PIXEL_LEN;

    public Vec2 getScreenBlockCenter() {
        return new Vec2(0.5f * (connectedTvsAmount - 1), 0.5f * (connectedTvsAmount - 1));
    }

    public int getScreenPixelWidth() {
        return Math.max(1, connectedTvsAmount) * 16 - EDGE_PIXEL_LEN;
    }

    public int getScreenPixelHeight() {
        return Math.max(1, connectedTvsAmount) * 16 - EDGE_PIXEL_LEN;
    }
}
