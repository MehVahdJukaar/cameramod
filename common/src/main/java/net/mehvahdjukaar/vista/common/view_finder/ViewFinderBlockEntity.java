package net.mehvahdjukaar.vista.common.view_finder;

import com.mojang.math.Axis;
import net.mehvahdjukaar.moonlight.api.block.IOneUserInteractable;
import net.mehvahdjukaar.moonlight.api.block.ItemDisplayTile;
import net.mehvahdjukaar.moonlight.api.misc.OrientationRig;
import net.mehvahdjukaar.moonlight.api.util.math.EntityAngles;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.client.video_source.LiveFeedVideoSource;
import net.mehvahdjukaar.vista.common.broadcast.LevelBEBroadcastLocation;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public class ViewFinderBlockEntity extends ItemDisplayTile implements IOneUserInteractable, IBroadcastSource {

    public static final int MAX_ZOOM = 44;

    public static final float NEAR_PLANE = 0.05f;
    private static final float BASE_FOV = 70;


    private final OrientationRig orientation = new OrientationRig();
    @Nullable
    public Object ccPeripheral = null;
    //not saved
    @Nullable
    private UUID controllingEntity = null;
    //delegate all position and rotation logic to this object which is basically a transform object
    private ReferenceFrame referenceFrame = new WorldReferenceFrame(this);
    private YawPitchRestraint restraint = YawPitchRestraint.UNBOUND;


    private final LiveFeedVideoSource videoSource;
    private UUID myUUID;
    private boolean linkDeferred = false; //true when setLevel ran before the server levels were fully bound
    private int powerLevelWantedZoom = 0; //0 means HOLD, ignore wanted level
    private int zoom = 1; //from 1 to 44
    private boolean locked = false;
    private boolean invisible = false;
    private AdventureModeOperation adventureModeOperation = AdventureModeOperation.NONE;


    public ViewFinderBlockEntity(BlockPos pos, BlockState state) {
        super(VistaMod.VIEWFINDER_TILE.get(), pos, state);

        this.myUUID = UUID.randomUUID();
        this.videoSource = new LiveFeedVideoSource(this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ViewFinderBlockEntity tile) {
        tile.orientation.tick();
        if (tile.linkDeferred) {
            tile.linkDeferred = !tile.linkFeedIfReady(level, LevelBEBroadcastLocation.of(tile));
        }
        if (tile.powerLevelWantedZoom > 0 && tile.zoom != tile.powerLevelWantedZoom && tile.getCurrentUser() == null) {
            int zoomDiff = tile.powerLevelWantedZoom - tile.zoom;
            int zoomStep = Mth.clamp(zoomDiff, -1, 1);
            tile.setZoomLevel(tile.zoom + zoomStep);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.linkDeferred = level instanceof ServerLevel &&
                !this.linkFeedIfReady(level, LevelBEBroadcastLocation.of(this));
    }


    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        this.myUUID = tag.getUUID("UUID");
        this.locked = tag.getBoolean("locked");
        this.setZoomLevel(tag.getInt("zoom"));
        this.powerLevelWantedZoom = tag.getInt("power_level_zoom");
        if (tag.contains("invisible")) this.invisible = tag.getBoolean("invisible");
        if (tag.contains("adventure_mode")) {
            this.adventureModeOperation = AdventureModeOperation.fromName(tag.getString("adventure_mode"));
        }
        if (level != null) this.ensureLinked(level, LevelBEBroadcastLocation.of(this));

        Quaternionf quat;
        //backwards compat
        if (tag.contains("yaw")) {
            float yaw = 180 + tag.getFloat("yaw");
            float pitch = tag.getFloat("pitch");
            quat = EntityAngles.of(pitch, yaw).toQuaternion();
        } else {
            quat = ExtraCodecs.QUATERNIONF.parse(NbtOps.INSTANCE, tag.get("orientation"))
                    .resultOrPartial(VistaMod.LOGGER::error).orElse(new Quaternionf());
        }
        this.orientation.orient(quat);
        this.snapToWantedRotationInstantly();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("orientation", ExtraCodecs.QUATERNIONF.encodeStart(NbtOps.INSTANCE,
                orientation.getRotation(1)).getOrThrow());
        tag.putUUID("UUID", this.myUUID);
        tag.putBoolean("locked", this.locked);
        tag.putInt("zoom", this.zoom);
        tag.putInt("power_level_zoom", this.powerLevelWantedZoom);
        if (this.invisible) tag.putBoolean("invisible", true);
        tag.putString("adventure_mode", this.adventureModeOperation.name());
    }


    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.vista.viewfinder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory) {
        //thanks mojank
        if (inventory.player.isSpectator()) return null;
        return new ViewFinderMenu(id, inventory, this);
    }

    public int getZoomLevel() {
        return zoom;
    }

    public boolean isLocked() {
        return locked;
    }

    public @Nullable ResourceLocation getLensShader() {
        return videoSource.getPostShader();
    }

    public void setZoomLevel(int zoom) {
        this.zoom = zoom;
    }

    public void setLocked(boolean b) {
        this.locked = b;
    }

    public UUID getBroadcastUUID() {
        return myUUID;
    }

    public float getFOV() {
        return BASE_FOV * getFOVModifier();
    }

    public float getFOVModifier() {
        float spyglassZoom = 0.1f;
        float maxZoom = spyglassZoom / 5;
        float normalizedZoom = getNormalizedZoomFactor();
        return Mth.lerp(normalizedZoom, 1, maxZoom);
    }

    public float getNormalizedZoomFactor() {
        float normalizedZoom = (this.getZoomLevel() - 1f) / (MAX_ZOOM - 1f);
        normalizedZoom = 1 - ((1 - normalizedZoom) * (1 - normalizedZoom)); //easing
        return normalizedZoom;
    }


    public void updateRedstonePower(int directPower) {
        int prevWantedZoom = this.powerLevelWantedZoom;
        this.powerLevelWantedZoom = directPower == 0 ? 0 : (int) Mth.map(directPower, 1, 15, 1, MAX_ZOOM);
        if (powerLevelWantedZoom != prevWantedZoom) {
            this.setChanged(); //update clients
        }
    }


    @Override
    public void clientSideUpdateWhenChanged(HolderLookup.Provider registries) {
        super.clientSideUpdateWhenChanged(registries);
        videoSource.onItemChanged();
    }

    @Override
    public @Nullable IVideoSource getBroadcastVideo() {
        return this.videoSource;
    }

    public void setRestraint(YawPitchRestraint restraint) {
        //this.restraint = restraint;
        //TODO: this is bugged. Restraints dont work properly. Disabled for now.
    }

    @VisibleForDebug
    @ApiStatus.Internal
    public ReferenceFrame getReferenceFrame() {
        return this.referenceFrame;
    }

    public void setReferenceFrame(ReferenceFrame mount) {
        this.referenceFrame = mount;
    }


    @Override
    public BlockPos getBlockPos() {
        return isInWorld() ? super.getBlockPos() :
                BlockPos.containing(this.referenceFrame.position(1));
    }


    @Override
    public boolean stillValid(Player player) {
        return referenceFrame.isStillValid(player);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        //recomputes it
    }

    public boolean isInvisible() {
        return invisible;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return this.isEmpty() && (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof StainedGlassPaneBlock) ||
                (CompatHandler.SUPPLEMENTARIES && stack.is(ItemTags.SKULLS));
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return canPlaceItem(index, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return true;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{0}; //only one slot, the lens
    }

    @Nullable
    @Override
    public UUID getCurrentUser() {
        return controllingEntity;
    }

    @Override
    public void setCurrentUser(@Nullable UUID uuid) {
        this.controllingEntity = uuid;
    }

    public void snapToWantedRotationInstantly() {
        this.orientation.tick();
        this.orientation.tick();
    }

    public void setLocalOrientation(Quaternionf localRot) {
        //remove structure rot
        Quaternionf structureRot = getStructureAdditionalRotation();
        Quaternionf cannonRot = localRot.mul(structureRot.invert(new Quaternionf()));
        //clamp
        this.orientation.orient(this.restraint.clamp(cannonRot));
    }

    public void setWorldOrientation(Quaternionf worldRot) {
        Quaternionf referenceRot = referenceFrame.getRotation(1);
        Quaternionf inverseReferenceRot = referenceRot.invert(new Quaternionf());
        Quaternionf localRot = inverseReferenceRot.mul(worldRot, new Quaternionf());
        setLocalOrientation(localRot);
    }

    public Quaternionf getLocalOrientation(float partialTicks) {
        Quaternionf rot = orientation.getRotation(partialTicks);
        Quaternionf additionalRot = getStructureAdditionalRotation();
        return rot.mul(additionalRot);
    }

    public Quaternionf getWorldOrientation(float partialTicks) {
        Quaternionf localRot = getLocalOrientation(partialTicks);
        Quaternionf referenceRot = referenceFrame.getRotation(partialTicks);
        return localRot.mul(referenceRot);
    }

    public void setTrustedInternalAttributes(Quaternionf localRotation, int zoom, boolean locked) {
        this.setLocalOrientation(localRotation);
        this.setZoomLevel( zoom);
        this.locked = locked;
    }

    private Quaternionf getStructureAdditionalRotation() {
        return getStructureAdditionalRotation(this.getBlockState());
    }

    private static Quaternionf getStructureAdditionalRotation(BlockState state) {
        return Axis.YP.rotationDegrees(-state.getValue(ViewFinderBlock.ROTATE_TILE).ordinal() * 90);
    }

    /**
     * Bakes a view finder's aim into save NBT (matching {@link #saveAdditional}/{@link #loadAdditional}) without a
     * live block entity - used to persist aim into a Create contraption's stored block data. {@code localRot} is the
     * world-local orientation as sent over the network; the stored rig rotation strips the block's structural spin.
     */
    public static CompoundTag buildAimNbt(CompoundTag base, BlockState state, Quaternionf localRot, int zoom, boolean locked) {
        Quaternionf additional = getStructureAdditionalRotation(state);
        Quaternionf rig = localRot.mul(additional.invert(new Quaternionf()), new Quaternionf());
        base.put("orientation", ExtraCodecs.QUATERNIONF.encodeStart(NbtOps.INSTANCE, rig).getOrThrow());
        base.putInt("zoom", zoom);
        base.putBoolean("locked", locked);
        return base;
    }

    public Vector3f getGlobalFacing(float partialTicks) {
        Quaternionf rot = getWorldOrientation(partialTicks);
        Vector3f forward = new Vector3f(0, 0, 1);
        rot.transform(forward);
        return forward;
    }

    private Vector3f getLocalFacing(float partialTicks) {
        Quaternionf rot = getLocalOrientation(partialTicks);
        Vector3f forward = new Vector3f(0, 0, 1);
        rot.transform(forward);
        return forward;
    }

    public Vec3 getGlobalPosition(float partialTicks) {
        return referenceFrame.position(partialTicks);
    }

    public Vec3 getGlobalVelocity() {
        return referenceFrame.velocity();
    }

    public YawPitchRestraint getOrientationRestraints() {
        BlockState state = this.getBlockState();
        Direction dir = state.getValue(ViewFinderBlock.FACING).getOpposite();
        return restraint.rotated(dir);
    }

    private Quaternionf getWantedLocalOrientation() {
        Quaternionf rot = orientation.getWantedRotation();
        Quaternionf additionalRot = getStructureAdditionalRotation();
        return rot.mul(additionalRot);
    }

    // Network. The reference frame decides how the aim travels: a world view finder locates itself by block pos,
    // a contraption view finder needs its own packet (no server-side block entity exists inside a contraption).
    public void syncToServer(boolean removeOwner, Player playerWhoChangedIt) {
        referenceFrame.syncToServer(this.getWantedLocalOrientation(), this.zoom,
                this.locked, removeOwner, playerWhoChangedIt);
    }

    public void syncToClients() {
        if (level instanceof ServerLevel sl) {
            referenceFrame.syncToCLients(sl, this.getWantedLocalOrientation(), this.zoom, this.locked);
        }
    }

    public boolean isInWorld() {
        return referenceFrame instanceof WorldReferenceFrame;
    }

    public void cycleLock() {
        this.locked = !locked;
    }

    public boolean shouldRotatePlayerFaceWhenManeuvering() {
        return false;
    }

    public AdventureModeOperation getAdventureModeOperation() {
        return adventureModeOperation;
    }

    public void setAdventureModeOperation(AdventureModeOperation adventureModeOperation) {
        this.adventureModeOperation = adventureModeOperation;
        this.setChanged();
    }

    public enum AdventureModeOperation {
        NONE,
        VIEW_ONLY,
        NO_INTERACTION;

        public static AdventureModeOperation fromName(String name) {
            for (AdventureModeOperation value : values()) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
            return NONE;
        }
    }
}
