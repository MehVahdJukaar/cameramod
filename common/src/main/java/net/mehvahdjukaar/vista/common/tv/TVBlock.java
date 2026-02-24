package net.mehvahdjukaar.vista.common.tv;

import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.moonlight.api.block.IOptionalEntityBlock;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.moonlight.api.util.math.Direction2D;
import net.mehvahdjukaar.moonlight.api.util.math.MthUtils;
import net.mehvahdjukaar.moonlight.api.util.math.Rect2D;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.tv.connection.GridAccessor;
import net.mehvahdjukaar.vista.common.tv.connection.GridTile;
import net.mehvahdjukaar.vista.common.tv.connection.RectFinder;
import net.mehvahdjukaar.vista.common.tv.connection.RectSelection;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TVBlock extends HorizontalDirectionalBlock implements EntityBlock, Equipable, IOptionalEntityBlock {

    public static final MapCodec<TVBlock> CODEC = simpleCodec(TVBlock::new);
    public static final EnumProperty<PowerState> POWER_STATE = EnumProperty.create("powered", PowerState.class);
    public static final EnumProperty<TVType> CONNECTION = EnumProperty.create("connection", TVType.class);

        private static final Set<Block> REDSTONE_FEEDBACK_BLACKLIST = Set.of(
            Blocks.REDSTONE_LAMP,
            Blocks.NOTE_BLOCK,
            Blocks.PISTON,
            Blocks.STICKY_PISTON,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.IRON_DOOR,
            Blocks.IRON_TRAPDOOR,
            Blocks.COPPER_BULB,
            Blocks.EXPOSED_COPPER_BULB,
            Blocks.WEATHERED_COPPER_BULB,
            Blocks.OXIDIZED_COPPER_BULB,
            Blocks.WAXED_COPPER_BULB,
            Blocks.WAXED_EXPOSED_COPPER_BULB,
            Blocks.WAXED_WEATHERED_COPPER_BULB,
            Blocks.WAXED_OXIDIZED_COPPER_BULB
        );

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
            return tv;
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
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        TVType type = context.isSecondaryUseActive()
            ? TVType.SINGLE
            : getTypeFromNeighbors(context.getLevel(), context.getClickedPos(), facing);
        BlockState placedState = this.defaultBlockState().setValue(FACING, facing).setValue(CONNECTION, type);
        boolean powered = hasExternalInputPower(context.getLevel(), context.getClickedPos(), placedState, null);

        return placedState.setValue(POWER_STATE, PowerState.direct(powered));
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
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        TVBlockEntity masterTile = getMasterBlockEntity(level, pos, state);
        if (masterTile != null) {
            return masterTile.interactWithPlayerItem(player, hand, stack, 0, hitResult);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter blockGetter, BlockPos pos, Direction direction) {
        if (!(blockGetter instanceof Level level)) return 0;
        TVBlockEntity master = resolveSignalSourceTile(level, pos, state);
        return master != null && master.emitsEntityDetectorSignal() ? 15 : 0;
    }

    @Nullable
    private TVBlockEntity resolveSignalSourceTile(Level level, BlockPos pos, BlockState state) {
        TVBlockEntity master = getMasterBlockEntity(level, pos, state);
        if (master != null) return master;

        if (!(state.getBlock() instanceof TVBlock)) return null;

        Direction facing = state.getValue(FACING);
        TVGridAccess gridAccess = new TVGridAccess(level, pos, state);
        Rect2D tvRect = RectFinder.findMaxRect(gridAccess, Vec2i.ZERO, false);

        var iter = tvRect.iteratePoints();
        while (iter.hasNext()) {
            Vec2i p = iter.next();
            BlockPos candidatePos = MthUtils.relativePos(pos, facing, p.x(), p.y(), 0);
            if (level.getBlockEntity(candidatePos) instanceof TVBlockEntity tv) {
                return tv;
            }
        }
        return null;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter blockGetter, BlockPos pos, Direction direction) {
        return this.getSignal(state, blockGetter, pos, direction);
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
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (neighborBlock == this) return;

        TVBlockEntity masterTile = getMasterBlockEntity(level, pos, state);
        BlockPos rootPos = masterTile != null ? masterTile.getBlockPos() : pos;
        BlockState rootState = level.getBlockState(rootPos);
        if (!(rootState.getBlock() instanceof TVBlock)) return;

        boolean powered = hasExternalInputPower(level, rootPos, rootState, masterTile);
        PowerState oldPower = rootState.getValue(POWER_STATE);
        PowerState newPower = PowerState.direct(powered);
        if (newPower != oldPower)
            level.setBlock(rootPos, rootState.setValue(POWER_STATE, newPower),
                    Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_NONE);

        if (oldPower.isOn() != newPower.isOn()) {
            TVGridAccess gridAccess = new TVGridAccess(level, rootPos, rootState);
            Rect2D old = RectFinder.findMaxRect(gridAccess, Vec2i.ZERO, false);
            gridAccess.transform(old, old, null);
            gridAccess.applyChanges();
        }
    }

    private boolean hasExternalInputPower(Level level, BlockPos tvPos, BlockState tvState, @Nullable TVBlockEntity masterTile) {
        if (!(tvState.getBlock() instanceof TVBlock)) return false;

        Direction facing = tvState.getValue(FACING);
        TVGridAccess gridAccess = new TVGridAccess(level, tvPos, tvState);
        Rect2D tvRect = RectFinder.findMaxRect(gridAccess, Vec2i.ZERO, false);
        boolean isEmitting = masterTile != null && masterTile.emitsEntityDetectorSignal();

        var iter = tvRect.iteratePoints();
        while (iter.hasNext()) {
            Vec2i p = iter.next();
            BlockPos currentTvPos = MthUtils.relativePos(tvPos, facing, p.x(), p.y(), 0);
            if (hasExternalInputPowerAtPos(level, currentTvPos, isEmitting)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExternalInputPowerAtPos(Level level, BlockPos tvPos, boolean isEmitting) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = tvPos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (neighborState.getBlock() instanceof TVBlock) {
                continue;
            }

            if (isEmitting && REDSTONE_FEEDBACK_BLACKLIST.contains(neighborState.getBlock())) {
                continue;
            }

            int signal;
            if (neighborState.is(Blocks.REDSTONE_WIRE)) {
                signal = neighborState.getValue(RedStoneWireBlock.POWER);
                if (isEmitting && signal > 0 && !wireNetworkHasExternalSource(level, neighborPos)) {
                    continue;
                }
            } else {
                signal = level.getSignal(neighborPos, dir.getOpposite());
            }

            if (signal > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean wireNetworkHasExternalSource(Level level, BlockPos startWirePos) {
        Deque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(startWirePos);

        while (!open.isEmpty() && visited.size() < 128) {
            BlockPos current = open.removeFirst();
            if (!visited.add(current)) continue;

            BlockState wireState = level.getBlockState(current);
            if (!wireState.is(Blocks.REDSTONE_WIRE)) continue;

            if (hasNonTvPowerSource(level, current)) {
                return true;
            }

            for (Direction horizontal : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(horizontal);
                if (!visited.contains(next) && level.getBlockState(next).is(Blocks.REDSTONE_WIRE)) {
                    open.add(next);
                }
            }
        }

        return false;
    }

    private boolean hasNonTvPowerSource(Level level, BlockPos wirePos) {
        for (Direction dir : Direction.values()) {
            BlockPos sourcePos = wirePos.relative(dir);
            BlockState sourceState = level.getBlockState(sourcePos);
            if (sourceState.is(Blocks.REDSTONE_WIRE)) {
                continue;
            }
            if (sourceState.getBlock() instanceof TVBlock) {
                continue;
            }

            int signal = level.getSignal(sourcePos, dir.getOpposite());
            if (signal > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) this.shrinkConnection(state, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (placer != null && placer.isShiftKeyDown()) return;
        this.enlargeConnection(state, level, pos);
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
                    level.setBlockAndUpdate(target, bs.setValue(CONNECTION, conn)
                            .setValue(POWER_STATE, power));
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


