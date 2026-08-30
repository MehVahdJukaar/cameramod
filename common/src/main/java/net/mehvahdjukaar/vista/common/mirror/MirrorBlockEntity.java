package net.mehvahdjukaar.vista.common.mirror;

import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.ScreenRect;
import net.mehvahdjukaar.vista.common.enderman.MirrorEndermanObservationController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class MirrorBlockEntity extends BlockEntity {

    private UUID id = UUID.randomUUID();
    private Vec2i connectedMirrorsAmount = Vec2i.ONE;

    private final MirrorEndermanObservationController observationController = new MirrorEndermanObservationController(this);

    public MirrorBlockEntity(BlockPos pos, BlockState state) {
        super(VistaMod.MIRROR_TILE.get(), pos, state);
    }

    public static void onTick(Level world, BlockPos pos, BlockState state, MirrorBlockEntity mirror) {
        if (world.isClientSide) return;
        // Run once every UPDATE_INTERVAL ticks; offset by pos so different mirrors don't all
        // fire on the same tick.
        if ((world.getGameTime() + pos.asLong()) % UPDATE_INTERVAL != 0) return;
        mirror.observationController.tick();
    }

    private static final int UPDATE_INTERVAL = 10;

    public UUID getId() {
        return id;
    }

    public Vec2i getConnectedCount() {
        return connectedMirrorsAmount;
    }

    public void setConnectionSize(Vec2i size) {
        this.connectedMirrorsAmount = size;
    }

    // The surface leaves a fixed 1px frame per outer side no matter how wide the group is (same as
    // the TV's bezel), so the visible area is 16*N - 2 px. Sizing the framebuffer to exactly that
    // keeps the texel to screen ratio 1:1 at any size.
    public static final int FRAME_PIXELS = 2;

    public Vec2i getScreenPixelSize() {
        return new Vec2i(
                Math.max(1, connectedMirrorsAmount.x()) * 16 - FRAME_PIXELS,
                Math.max(1, connectedMirrorsAmount.y()) * 16 - FRAME_PIXELS);
    }

    // Master tile sits at the bottom-right corner of the grid (see MirrorBlockEntityRenderer);
    // the grid extends right and up from the master.
    public ScreenRect getScreenRect() {
        Direction facing = this.getBlockState().getValue(MirrorBlock.FACING);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        float w = connectedMirrorsAmount.x();
        float h = connectedMirrorsAmount.y();
        Vec3 center = Vec3.atCenterOf(this.getBlockPos())
                .add(normal.scale(0.5))
                .add(ScreenRect.rightOf(normal).scale((w - 1) * 0.5))
                .add(ScreenRect.UP.scale((h - 1) * 0.5));
        return new ScreenRect(center, normal, w, h);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("id")) {
            this.id = tag.getUUID("id");
        }
        this.connectedMirrorsAmount = new Vec2i(
                Math.max(1, tag.getInt("ConnectionWidth")),
                Math.max(1, tag.getInt("ConnectionHeight")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("id", id);
        tag.putInt("ConnectionWidth", connectedMirrorsAmount.x());
        tag.putInt("ConnectionHeight", connectedMirrorsAmount.y());
    }
}
