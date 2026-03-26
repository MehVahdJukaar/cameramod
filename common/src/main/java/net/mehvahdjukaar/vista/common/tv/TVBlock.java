package net.mehvahdjukaar.vista.common.tv;

import net.mehvahdjukaar.ml_classes.*;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.tv.connection.GridAccessor;
import net.mehvahdjukaar.vista.common.tv.connection.GridTile;
import net.mehvahdjukaar.vista.common.tv.connection.RectFinder;
import net.mehvahdjukaar.vista.common.tv.connection.RectSelection;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TVBlock extends HorizontalDirectionalBlock implements EntityBlock, Equipable, IOptionalEntityBlock {

    public static final EnumProperty<PowerState> POWER_STATE = EnumProperty.create("powered", PowerState.class);
    public static final EnumProperty<TVType> CONNECTION = EnumProperty.create("connection", TVType.class);

    public TVBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(POWER_STATE).isOn() ? 3 : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWER_STATE, PowerState.OFF)
                .setValue(CONNECTION, TVType.SINGLE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
        builder.add(POWER_STATE);
        builder.add(CONNECTION);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return Utils.getTicker(blockEntityType, VistaMod.TV_TILE.get(), TVBlockEntity::onTick);
    }

    @Nullable
    private TVBlockEntity getMasterBlockEntity(Level level, BlockPos pos, BlockState state) {
        //find bottom left
        //for block that cant have tile entity first iterate down (depending on the connection state), then left until you either dont reach no more tv blocks or you reach one that has a tile entity
        TVType type = state.getValue(CONNECTION);
        Direction facing = state.getValue(FACING);
        BlockPos currentPos = pos;

        BlockEntity be = level.getBlockEntity(currentPos);
        if (be instanceof TVBlockEntity tv) {
            //Not needed
            if (this.shouldHaveBlockEntity(state)) {
                return tv;
            } else {
                int err0r = 1;
            }

        }

        while (type.isConnected(Direction.DOWN, facing)) {
            currentPos = currentPos.below();
            BlockState belowState = level.getBlockState(currentPos);
            if (!belowState.is(this) || belowState.getValue(FACING) != facing) {
                return null;
            }
            type = belowState.getValue(CONNECTION);
        }
        Direction myLeft = facing.getClockWise();
        while (type.isConnected(myLeft, facing)) {
            currentPos = currentPos.relative(myLeft);
            BlockState sideState = level.getBlockState(currentPos);
            if (!sideState.is(this) || sideState.getValue(FACING) != facing) {
                return null;
            }
            type = sideState.getValue(CONNECTION);
        }
        be = level.getBlockEntity(currentPos);
        if (be instanceof TVBlockEntity tv) {
            return tv;
        }
        return null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        Direction facing = context.getHorizontalDirection().getOpposite();
        TVType type = CommonConfigs.MAX_CONNECTED_TV_SIZE.get() > 1 ?
                getTypeFromNeighbors(context.getLevel(), context.getClickedPos(), facing)
                : TVType.SINGLE;

        return this.defaultBlockState()
                .setValue(POWER_STATE, PowerState.direct(powered))
                .setValue(FACING, facing)
                .setValue(CONNECTION, type);
    }

    private TVType getTypeFromNeighbors(Level level, BlockPos clickedPos, Direction facing) {
        boolean up = isNeighborConnected(level, clickedPos, facing, Direction.UP);
        boolean down = isNeighborConnected(level, clickedPos, facing, Direction.DOWN);
        boolean left = isNeighborConnected(level, clickedPos, facing, facing.getClockWise());
        boolean right = isNeighborConnected(level, clickedPos, facing, facing.getCounterClockWise());
        return TVType.fromConnections(up, down, left, right);
    }

    private boolean isNeighborConnected(Level level, BlockPos myPos, Direction myFacing, Direction toDir) {
        BlockState neighborState = level.getBlockState(myPos.relative(toDir));
        if (!neighborState.is(this)) return false;
        return neighborState.getValue(FACING) == myFacing &&
                neighborState.getValue(CONNECTION).isConnected(toDir.getOpposite(), myFacing);
    }


    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == 1) {
            if (level.isClientSide) {
                if (level.getBlockEntity(pos) instanceof TVBlockEntity tile) {
                    tile.updateEndermanLookAnimation(param);
                    return true;
                }
            } else return true;
        }
        return super.triggerEvent(state, level, pos, id, param);
    }

    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos pos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        ItemStack stack = player.getItemInHand(interactionHand);
        TVBlockEntity masterTile = getMasterBlockEntity(level, pos, blockState);
        if (masterTile != null) {
            return masterTile.interactWithPlayerItem(player, interactionHand, stack, 0, blockHitResult);
        }
        return super.use(blockState, level, pos, player, interactionHand, blockHitResult);
    }

    @Override
    public boolean shouldHaveBlockEntity(BlockStateBase state) {
        TVType conn = state.getValue(CONNECTION);
        return conn == TVType.SINGLE || conn == TVType.BOTTOM_LEFT;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return shouldHaveBlockEntity(state) ? new TVBlockEntity(pos, state) : null;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (neighborBlock == this) return;
        boolean powered = level.hasNeighborSignal(pos);
        PowerState oldPower = state.getValue(POWER_STATE);
        PowerState newPower = PowerState.direct(powered);
        if (newPower != oldPower)
            level.setBlock(pos, state.setValue(POWER_STATE, newPower),
                    Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_NONE);
        if (oldPower.isOn() != newPower.isOn()) {
            //update neighbors
            TVGridAccess gridAccess = new TVGridAccess(level, pos, state);
            Rect2D old = RectFinder.findMaxRect(gridAccess, Vec2i.ZERO, false);
            gridAccess.transform(old, old, null);
            gridAccess.applyChanges();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) this.shrinkConnection(state, level, pos);
        if (level.getBlockEntity(pos) instanceof TVBlockEntity tile) {
            Containers.dropContents(level, pos, tile);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) this.enlargeConnection(state, level, pos);
    }

    private void enlargeConnection(BlockState tvState, Level level, BlockPos pos) {
        int maxSize = CommonConfigs.MAX_CONNECTED_TV_SIZE.get();
        if (maxSize <= 1) return;
        TVGridAccess gridAccess = new TVGridAccess(level, pos, tvState);
        Rect2D old = RectFinder.findMaxRect(gridAccess, Vec2i.ZERO, true);
        RectSelection newRec = RectFinder.findMaxExpandedRect(gridAccess, Vec2i.ZERO, maxSize, true);
        gridAccess.transform(old, newRec.selection(), newRec.touchedRect());
        gridAccess.applyChanges();
    }

    private void shrinkConnection(BlockState tvState, Level level, BlockPos pos) {
        if (tvState.getValue(CONNECTION) == TVType.SINGLE) return;
        int maxSize = CommonConfigs.MAX_CONNECTED_TV_SIZE.get();
        if (maxSize <= 1) return;
        TVGridAccess gridAccess = new TVGridAccess(level, pos, tvState);
        gridAccess.setAt(Vec2i.ZERO, tvState.getValue(CONNECTION));
        Rect2D old = RectFinder.findMaxRect(gridAccess, Vec2i.ZERO, true);

        gridAccess.setAt(Vec2i.ZERO, null);
        Direction2D closestDir = closestDirToCenter(old);
        Vec2i newCenter = Vec2i.ZERO.offset(closestDir);
        Rect2D newRec = RectFinder.findMaxRect(gridAccess, newCenter, true);
        gridAccess.transform(old, newRec, old);
        gridAccess.applyChanges();
    }

    private static Direction2D closestDirToCenter(Rect2D rect) {
        Vec2 center = rect.getCenter();
        Vec2 myPos = new Vec2(0.5f, 0.5f);
        Vec2 diff = center.add(myPos.scale(-1));
        return Direction2D.closest(diff);
    }

    //TODO: make blockstate?
    private static boolean hasCassette(BlockPos pos, Level level) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TVBlockEntity tv) {
            return !tv.isEmpty();
        }
        return false;
    }

    public static class TVGridAccess implements GridAccessor {

        private final BlockPos pos;
        private final Direction facing;
        private final Level level;

        private final Map<Vec2i, GridTile> statesCache = new HashMap<>();
        private final Map<Vec2i, GridTile> statesChanged = new HashMap<>();
        private ItemStack cassetteTransfer = null;
        private Rect2D finalTileRect = null;

        public TVGridAccess(Level level, BlockPos pos, BlockState state) {
            this.pos = pos;
            this.facing = state.getValue(FACING);
            this.level = level;
        }

        @Override
        public void planBeMove(@Nullable Rect2D fromRec, Rect2D toRec) {
            finalTileRect = toRec;
            if (fromRec == null) return;
            Vec2i from = fromRec.bottomLeft();
            BlockPos target = MthUtils.relativePos(pos, facing, from.x(), from.y(), 0);
            if (level.getBlockEntity(target) instanceof TVBlockEntity tv) {
                cassetteTransfer = tv.getDisplayedItem().copy();

                tv.clearContent();
                tv.setConnectionSize(1);
            }
        }

        @Override
        public @NotNull GridTile getAt(Vec2i key) {
            int x = key.x();
            int y = key.y();
            GridTile cached = statesCache.get(key);
            if (cached != null) return cached;
            BlockPos target = MthUtils.relativePos(pos, facing, x, y, 0);
            BlockState bs = level.getBlockState(target);
            GridTile value = GridTile.EMPTY;
            if (bs.getBlock() instanceof TVBlock && bs.getValue(FACING) == facing) {
                TVType t = bs.getValue(CONNECTION);
                PowerState hasPower = bs.getValue(POWER_STATE);
                boolean hasBe = hasCassette(target, level);
                value = new GridTile(t, hasBe, hasPower);
            }
            statesCache.put(key, value);
            return value;
        }

        @Override
        public void setAt(Vec2i key, @Nullable TVType type, boolean setPower) {
            GridTile old = getAt(key);
            // if (old.type() != type) {
            GridTile tile = GridTile.of(type, PowerState.indirect(old.powerState(), setPower));
            statesChanged.put(key, tile);
            statesCache.put(key, tile);
            //  }
        }

        public void applyChanges() {
            for (var e : statesChanged.entrySet()) {
                Vec2i key = e.getKey();
                GridTile tile = e.getValue();
                TVType conn = tile.type();
                PowerState power = tile.powerState();
                BlockPos target = MthUtils.relativePos(pos, facing, key.x(), key.y(), 0);
                BlockState bs = level.getBlockState(target);
                if (bs.getBlock() instanceof TVBlock && conn != null) {
                    BlockState newState = bs.setValue(CONNECTION, conn)
                            .setValue(POWER_STATE, power);
                    level.setBlockAndUpdate(target, newState);
                    //clear BE if they dont have them
                    if (newState.getBlock() instanceof TVBlock tv) {
                        if (!tv.shouldHaveBlockEntity(newState)) {
                            level.removeBlockEntity(target);
                        }
                    }
                }
            }
            if (finalTileRect != null) {
                Vec2i to = finalTileRect.bottomLeft();
                BlockPos target = MthUtils.relativePos(pos, facing, to.x(), to.y(), 0);
                if (level.getBlockEntity(target) instanceof TVBlockEntity tv) {
                    if (cassetteTransfer != null) tv.setDisplayedItem(cassetteTransfer);
                    tv.setChanged();
                    tv.setConnectionSize(finalTileRect.width());
                }
            }
        }

    }


}


