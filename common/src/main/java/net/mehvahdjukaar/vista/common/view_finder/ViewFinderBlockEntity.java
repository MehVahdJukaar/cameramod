package net.mehvahdjukaar.vista.common.view_finder;

import net.mehvahdjukaar.ml_classes.IOneUserInteractable;
import net.mehvahdjukaar.ml_classes.MthUtils;
import net.mehvahdjukaar.ml_classes.TileOrEntityTarget;
import net.mehvahdjukaar.moonlight.api.block.ItemDisplayTile;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.client.video_source.LiveFeedVideoSource;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastProvider;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.supplementaries.SuppCompat;
import net.mehvahdjukaar.vista.network.ClientBoundControlViewFinderPacket;
import net.mehvahdjukaar.vista.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ViewFinderBlockEntity extends ItemDisplayTile implements IOneUserInteractable, IBroadcastProvider {

    public static final int MAX_ZOOM = 44;

    public static final float NEAR_PLANE = 0.05f;
    private static final float BASE_FOV = 70;

    public Object ccPeripheral = null;

    private UUID myUUID;

    private float pitch = 0;
    private float prevPitch = 0;
    private float yaw = 0;
    private float prevYaw = 0;

    private int zoom = 1;
    private boolean locked = false;

    //not saved
    @Nullable
    private UUID controllingPlayer = null;
    private final LiveFeedVideoSource videoSource;

    public ViewFinderBlockEntity(BlockPos pos, BlockState state) {
        super(VistaMod.VIEWFINDER_TILE.get(), pos, state);

        this.myUUID = UUID.randomUUID();
        this.videoSource = new LiveFeedVideoSource(this);
    }

    @Override
    public @Nullable IVideoSource getBroadcastVideoSource() {
        return this.videoSource;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ViewFinderBlockEntity tile) {
        tile.prevYaw = tile.yaw;
        tile.prevPitch = tile.pitch;
    }

    @Override
    public void updateClientVisualsOnLoad() {
        super.updateClientVisualsOnLoad();
        videoSource.onItemChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.myUUID = tag.getUUID("UUID");
        this.yaw = tag.getFloat("yaw");
        this.pitch = tag.getFloat("pitch");
        this.locked = tag.getBoolean("locked");
        this.zoom = tag.getInt("zoom");
        this.ensureLinked(level, getBlockPos());
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.ensureLinked(level, getBlockPos());
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID("UUID", this.myUUID);
        tag.putFloat("yaw", this.yaw);
        tag.putFloat("pitch", this.pitch);
        tag.putBoolean("locked", this.locked);
        tag.putInt("zoom", this.zoom);
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("View Finder");
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return this.isEmpty() && stack.is(VistaMod.GLASS_PANES_TAG) ||
                (CompatHandler.SUPPLEMENTARIES && SuppCompat.getShaderForItem(stack.getItem()) != null);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public InteractionResult tryInteracting(Player player, InteractionHand hand, ItemStack stack,
                                            BlockPos pos) {
        if (player.isSecondaryUseActive() || this.isEmpty()) {
            InteractionResult itemAdd = this.interact(player, hand);
            if (itemAdd.consumesAction()) {
                return itemAdd;
            }
        }
        //same as super but sends custom packet
        if (player instanceof ServerPlayer sp && this.canBeUsedBy(pos, player)) {
            // open gui (edit sign with empty hand)
            this.setCurrentUser(player.getUUID());
            ModNetwork.CHANNEL.sendToClientPlayer(sp, new ClientBoundControlViewFinderPacket(TileOrEntityTarget.of(this)));
        }
        //always swing on fail
        return InteractionResult.SUCCESS;
    }

    public UUID getUUID() {
        return myUUID;
    }

    public float getYaw(float partialTicks) {
        return Mth.rotLerp(partialTicks, this.prevYaw, this.yaw);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch(float partialTicks) {
        return Mth.rotLerp(partialTicks, this.prevPitch, this.pitch);
    }

    public float getPitch() {
        return pitch;
    }

    public int getZoomLevel() {
        return zoom;
    }

    public boolean isLocked() {
        return locked;
    }


    //cannon bs

    public void setAttributes(float yaw, float pitch, int zoom, boolean locked,
                              Player controllingPlayer, ViewFinderAccess access) {
        this.setYaw(access, yaw);
        this.setPitch(access, pitch);
        this.zoom = zoom;
        this.locked = locked;
    }


    public void setZoomLevel(int zoom) {
        this.zoom = zoom;
    }

    public void setPitch(ViewFinderAccess access, float relativePitch) {
        var r = access.getPitchAndYawRestrains();
        this.pitch = MthUtils.clampDegrees(relativePitch, r.minPitch(), r.maxPitch());
    }

    public void setYaw(ViewFinderAccess access, float relativeYaw) {
        var r = access.getPitchAndYawRestrains();
        this.yaw = MthUtils.clampDegrees(relativeYaw, r.minYaw(), r.maxYaw());
    }

    public void setGlobalYaw(ViewFinderAccess access, float relativeYaw) {
        //calculateyaw here
        float yawOffset = access.getViewFinderGlobalYawOffset(1);
        setYaw(access, relativeYaw);
    }

    // sets both prev and current yaw. Only makes sense to be called from render thread
    public void setRenderYaw(ViewFinderAccess access, float relativeYaw) {
        setYaw(access, relativeYaw);
        this.prevYaw = this.yaw;
    }

    public void setRenderPitch(ViewFinderAccess access, float pitch) {
        setPitch(access, pitch);
        this.prevPitch = this.pitch;
    }


    public void setLocked(boolean b) {
        this.locked = b;
    }

    @Override
    public void setCurrentUser(@Nullable UUID uuid) {
        this.controllingPlayer = uuid;
    }

    @Override
    public @Nullable UUID getCurrentUser() {
        return controllingPlayer;
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
        float normalizedZoom = (this.getZoomLevel() - 1f) / (ViewFinderBlockEntity.MAX_ZOOM - 1f);
        normalizedZoom = 1 - ((1 - normalizedZoom) * (1 - normalizedZoom)); //easing
        return normalizedZoom;
    }


}
