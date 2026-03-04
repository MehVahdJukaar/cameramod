package net.mehvahdjukaar.vista.common.view_finder;

import net.mehvahdjukaar.moonlight.api.block.IRotatable;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.moonlight.api.util.math.MthUtils;
import net.mehvahdjukaar.supplementaries.common.utils.BlockUtil;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public class ViewFinderBlock extends DirectionalBlock implements EntityBlock, IRotatable {

    protected static final VoxelShape SHAPE_DOWN = Block.box(0.0, 0.0, 0.0, 14, 2.0, 14);
    protected static final VoxelShape SHAPE_UP = Block.box(0.0, 14.0, 0.0, 14, 14, 14);
    protected static final VoxelShape SHAPE_SOUTH = Block.box(0.0, 0.0, 14.0, 14, 14, 14);
    protected static final VoxelShape SHAPE_NORTH = Block.box(0.0, 0.0, 0.0, 14, 14, 2.0);
    protected static final VoxelShape SHAPE_EAST = Block.box(14.0, 0.0, 0.0, 14, 14, 14);
    protected static final VoxelShape SHAPE_WEST = Block.box(0.0, 0.0, 0.0, 2.0, 14, 14);

    public static final EnumProperty<Rotation> ROTATE_TILE = EnumProperty.create("rotate_tile", Rotation.class);

    public ViewFinderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(ROTATE_TILE, Rotation.NONE));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return Utils.getTicker(pBlockEntityType, VistaMod.VIEWFINDER_TILE.get(), ViewFinderBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult blockHitResult) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.is(VistaMod.HOLLOW_CASSETTE.get())) {
            if (level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
                ItemStack stack = player.getItemInHand(hand);
                return tile.tryInteracting(player, hand, stack, pos);
            }
        }
        return super.use(blockState, level, pos, player, hand, blockHitResult);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            Direction dir = Direction.orderedByNearest(placer)[0];
            Direction myDir = state.getValue(FACING).getOpposite();
            if (dir.getAxis() == Direction.Axis.Y) {
                float pitch = dir == Direction.UP ? -90 : 90;
                tile.setPitch(tile.selfAccess, (myDir.getOpposite() == dir ? pitch + 180 : pitch));

            } else {
                float yaw = dir.toYRot();
                tile.setYaw(tile.selfAccess, (myDir.getOpposite() == dir ? yaw + 180 : yaw));
            }
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)))
                .setValue(ROTATE_TILE, state.getValue(ROTATE_TILE).getRotated(rot));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
        //  .setValue(ROTATE_TILE, state.getValue(ROTATE_TILE).getRotated(Rotation.CLOCKWISE_180));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ROTATE_TILE);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getClickedFace());
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            Containers.dropContents(level, pos, tile);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        if (oldState.getBlock() instanceof ViewFinderBlock &&
                !(newState.getBlock() instanceof ViewFinderBlock) &&
                level instanceof ServerLevel sl) {
            BroadcastManager.getInstance(sl).unlinkFeed(GlobalPos.of(level.dimension(), pos));
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ViewFinderBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (state.getValue(FACING).getOpposite()) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }


    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (PlatHelper.getPhysicalSide().isClient() && ViewFinderController.isActiveAt(pos)) {
            return Shapes.empty();
        }
        return Shapes.block();
    }

    @Override
    public Optional<BlockState> getRotatedState(BlockState state, LevelAccessor levelAccessor, BlockPos blockPos,
                                                Rotation rotation, Direction axis, @Nullable Vec3 hit) {
        boolean ccw = rotation == Rotation.COUNTERCLOCKWISE_90;
        return BlockUtil.getRotatedDirectionalBlock(state, axis, ccw).or(() -> Optional.of(state));
    }

    @Override
    public void onRotated(BlockState newState, BlockState oldState, LevelAccessor world, BlockPos pos, Rotation rotation,
                          Direction axis, @Nullable Vec3 hit) {
        if (axis.getAxis() == newState.getValue(FACING).getAxis() && world.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            float angle = rotation.rotate(0, 4) * -90;
            Vector3f currentDir = Vec3.directionFromRotation(tile.getPitch(), tile.getYaw()).toVector3f();
            Quaternionf q = new Quaternionf().rotateAxis(angle * Mth.DEG_TO_RAD, axis.step());
            currentDir.rotate(q);
            Vec3 newDir = new Vec3(currentDir);
            tile.setYaw(tile.selfAccess, (float) MthUtils.getYaw(newDir));
            tile.setPitch(tile.selfAccess, (float) MthUtils.getPitch(newDir));
            tile.setChanged();
            tile.getLevel().sendBlockUpdated(pos, oldState, newState, 3);
        }
    }
}
