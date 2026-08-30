package net.mehvahdjukaar.vista.common.mirror;

import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.moonlight.api.block.IOptionalEntityBlock;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.moonlight.api.util.math.MthUtils;
import net.mehvahdjukaar.moonlight.api.util.math.Rect2D;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.connection.AbstractGridAccess;
import net.mehvahdjukaar.vista.common.connection.ConnectionType;
import net.mehvahdjukaar.vista.common.connection.GridTile;
import net.mehvahdjukaar.vista.common.connection.IConnectedBlock;
import net.mehvahdjukaar.vista.common.tv.PowerState;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MirrorBlock extends HorizontalDirectionalBlock implements EntityBlock, IOptionalEntityBlock, IConnectedBlock {

    public static final MapCodec<MirrorBlock> CODEC = simpleCodec(MirrorBlock::new);
    public static final EnumProperty<ConnectionType> CONNECTION = ConnectionType.STATE_PROPERTY;
    // false = mirror surface flush with the front (near) face of the block, toward the viewer.
    // true  = surface recessed to the back of the block cell, set away from the viewer.
    public static final BooleanProperty FAR = BooleanProperty.create("far");

    // Recession (in blocks) of the FAR model's mirror plane from the block's front face. The far
    // model element runs z=14..16, so its mirror face sits 14px deeper than the near model's.
    public static final double FAR_RECESSION = 14.0 / 16.0;

    // Shapes for the 2px-thick models, defined for NORTH and rotated for the other horizontals, the
    // same convention the blockstate uses on the model.
    private static final Map<Direction, VoxelShape> NEAR_SHAPES =
            MthUtils.getAllRotatedVoxelShapesHorizontal(Block.box(0, 0, 0, 16, 16, 2));
    private static final Map<Direction, VoxelShape> FAR_SHAPES =
            MthUtils.getAllRotatedVoxelShapesHorizontal(Block.box(0, 0, 14, 16, 16, 16));

    public MirrorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FAR, false)
                .setValue(CONNECTION, ConnectionType.SINGLE));
    }

    // depth from the front face to the mirror surface, 0 for the near model
    public static double surfaceRecession(BlockState state) {
        return state.hasProperty(FAR) && state.getValue(FAR) ? FAR_RECESSION : 0.0;
    }

    @Override
    protected MapCodec<MirrorBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(FAR) ? FAR_SHAPES : NEAR_SHAPES;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FAR, CONNECTION);
    }

    @Override
    public EnumProperty<ConnectionType> connectionProperty() {
        return CONNECTION;
    }

    // Mirrors only connect to neighbors sharing both facing AND near/far surface, so a near and a
    // far mirror sitting side by side stay separate grids (their reflection planes are at different
    // depths, so merging them would be visually wrong).
    @Override
    public boolean connectionMatches(BlockState self, BlockState other) {
        return IConnectedBlock.super.connectionMatches(self, other) &&
                other.getValue(FAR) == self.getValue(FAR);
    }

    @Override
    public int maxConnectedSize() {
        return CommonConfigs.MAX_CONNECTED_MIRROR_SIZE.get();
    }

    @Override
    public boolean squareAspectRatio() {
        return CommonConfigs.MIRROR_SQUARE_ASPECT_RATIO.get();
    }

    @Override
    public AbstractGridAccess createGridAccess(Level level, BlockPos pos, BlockState state) {
        return new MirrorGridAccess(level, pos, state, this);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState state = this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(FAR, shouldPlaceFar(context, facing));
        ConnectionType type = getTypeFromNeighbors(context.getLevel(), context.getClickedPos(), state);
        return state.setValue(CONNECTION, type);
    }

    // NEAR/FAR force a model, BOTH goes by where the player clicked
    private static boolean shouldPlaceFar(BlockPlaceContext context, Direction facing) {
        return switch (CommonConfigs.MIRROR_PLACEMENT.get()) {
            case NEAR -> false;
            case FAR -> true;
            case BOTH -> isFarHalf(context, facing);
        };
    }

    // facing points back toward the viewer, so a hit in the near half places the near model
    private static boolean isFarHalf(BlockPlaceContext context, Direction facing) {
        Vec3 hit = context.getClickLocation();
        BlockPos pos = context.getClickedPos();
        double frac = switch (facing.getAxis()) {
            case X -> hit.x - pos.getX();
            case Y -> hit.y - pos.getY();
            case Z -> hit.z - pos.getZ();
        };
        // (frac - 0.5) * step > 0 means the hit is on the viewer's side of the cell centre.
        double towardViewer = (frac - 0.5) * facing.getAxisDirection().getStep();
        return towardViewer < 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public boolean shouldHaveBlockEntity(BlockStateBase state) {
        return IConnectedBlock.super.shouldHaveBlockEntity((BlockState) state);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return shouldHaveBlockEntity(state) ? new MirrorBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return Utils.getTicker(blockEntityType, VistaMod.MIRROR_TILE.get(), MirrorBlockEntity::onTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(placer instanceof Player p) || !p.isSecondaryUseActive()) enlargeConnection(state, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) shrinkConnection(state, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    public static MirrorBlockEntity getMasterBlockEntity(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MirrorBlock mirror)) return null;
        BlockEntity be = mirror.findMasterBlockEntity(level, pos, state);
        return be instanceof MirrorBlockEntity m ? m : null;
    }

    public static class MirrorGridAccess extends AbstractGridAccess {

        public MirrorGridAccess(Level level, BlockPos pos, BlockState state, Block owner) {
            super(level, pos, state, owner);
        }

        @Override
        protected EnumProperty<ConnectionType> connectionProperty() {
            return CONNECTION;
        }

        @Override
        protected GridTile readTile(BlockPos target, BlockState bs) {
            ConnectionType t = bs.getValue(CONNECTION);
            boolean hasBe = level.getBlockEntity(target) instanceof MirrorBlockEntity;
            return new GridTile(t, hasBe, PowerState.OFF);
        }

        @Override
        protected GridTile buildTile(Vec2i key, @Nullable ConnectionType type, boolean setPower) {
            return new GridTile(type, false, PowerState.OFF);
        }

        @Override
        public void planBeMove(@Nullable Rect2D fromRec, Rect2D toRec) {
            super.planBeMove(fromRec, toRec);
            if (fromRec == null) return;
            // Reset the old master's cached size before anything gets re-stated to SINGLE. If a grid
            // shrinks past its master, that BE survives still reporting multi-block dimensions, and
            // the renderer keeps asking for the wrong screen size instead of a fresh 1x1 texture.
            BlockPos target = targetPos(fromRec.bottomLeft());
            if (level.getBlockEntity(target) instanceof MirrorBlockEntity mirror) {
                mirror.setConnectionSize(Vec2i.ONE);
                mirror.setChanged();
            }
        }

        @Override
        protected void onMasterApplied(BlockPos target, Rect2D rect) {
            if (level.getBlockEntity(target) instanceof MirrorBlockEntity mirror) {
                mirror.setConnectionSize(rect.getSize());
                mirror.setChanged(); // pushes the size to clients so the reflection resizes
            }
        }
    }
}
