package net.mehvahdjukaar.vista.common.view_finder;

import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.moonlight.api.block.IAnalogRotatable;
import net.mehvahdjukaar.moonlight.api.block.IRotatable;
import net.mehvahdjukaar.moonlight.api.client.util.RotHlpr;
import net.mehvahdjukaar.moonlight.api.misc.TileOrEntityTarget;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.moonlight.api.util.math.MthUtils;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.broadcast.LevelBEBroadcastLocation;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.network.ClientBoundControlViewFinderPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Optional;

public class ViewFinderBlock extends DirectionalBlock implements EntityBlock, IRotatable, IAnalogRotatable {

    public static final EnumProperty<Rotation> ROTATE_TILE = EnumProperty.create("rotate_tile", Rotation.class);
    public static final MapCodec<ViewFinderBlock> CODEC = simpleCodec(ViewFinderBlock::new);
    protected static final EnumMap<Direction, VoxelShape> SHAPES = MthUtils.getAllRotatedVoxelShapes(
            Block.box(2.0, 2.0, 0.0, 14, 14, 2.0));

    public ViewFinderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(ROTATE_TILE, Rotation.NONE));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            return tile;
        }
        return null;
    }

    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ROTATE_TILE);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getFluidState().isEmpty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getClickedFace());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)))
                .setValue(ROTATE_TILE, state.getValue(ROTATE_TILE).getRotated(rot));
    }


    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
        //  .setValue(ROTATE_TILE, mirrorIn.ro(Rotation.CLOCKWISE_180)); //todo: bug here
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof ViewFinderBlockEntity cannon) {
            Direction[] nearest = Direction.orderedByNearest(placer);
            Direction dir = nearest[0].getOpposite();
            Direction myDir = state.getValue(FACING);
            if (dir == myDir) dir = nearest[1].getOpposite();
            cannon.setWorldOrientation(new Quaternionf(RotHlpr.rot(dir)));
            cannon.snapToWantedRotationInstantly();
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            int directPower = level.getDirectSignalTo(pos);
            tile.updateRedstonePower(directPower);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ViewFinderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return Utils.getTicker(pBlockEntityType, VistaMod.VIEWFINDER_TILE.get(), ViewFinderBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.is(VistaMod.HOLLOW_CASSETTE.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            if (CommonConfigs.isViewFinderGuiEnabled()) {
                openGuiOrView(tile, level, pos, player, stack, hitResult);
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            // LEGACY behavior: item-based lens insertion + direct look-through, no GUI
            if (player.isSecondaryUseActive() || tile.isEmpty()) {
                ItemInteractionResult itemAdd = tile.interactWithPlayerItem(player, hand, stack);
                if (itemAdd.consumesAction()) {
                    return itemAdd;
                }
            }
            if (player instanceof ServerPlayer sp) {
                //same as super but sends custom packet
                if (isAdventureNoInteraction(sp, tile)) {
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                startViewing(tile, pos, sp);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // only the GUI path handles empty-hand clicks; legacy mode keeps its item-driven interaction
        if (CommonConfigs.isViewFinderGuiEnabled() && level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            openGuiOrView(tile, level, pos, player, ItemStack.EMPTY, hitResult);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    // GUI path: a normal click looks straight through the view finder; sneaking opens the screen to
    // manage the lens and set angles. Adventure view-only always jumps straight into viewing.
    private void openGuiOrView(ViewFinderBlockEntity tile, Level level, BlockPos pos, Player player, ItemStack stack, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (isAdventureNoInteraction(sp, tile)) return;
        boolean viewOnly = sp.gameMode.getGameModeForPlayer() == GameType.ADVENTURE &&
                tile.getAdventureModeOperation() == ViewFinderBlockEntity.AdventureModeOperation.VIEW_ONLY;
        if (player.isSecondaryUseActive() && !viewOnly) {
            Utils.openGuiIfPossible(tile, sp, stack, hitResult.getDirection(), hitResult.getLocation());
        } else {
            startViewing(tile, pos, sp);
        }
    }

    private static boolean isAdventureNoInteraction(ServerPlayer sp, ViewFinderBlockEntity tile) {
        return sp.gameMode.getGameModeForPlayer() == GameType.ADVENTURE &&
                tile.getAdventureModeOperation() == ViewFinderBlockEntity.AdventureModeOperation.NO_INTERACTION;
    }

    private static void startViewing(ViewFinderBlockEntity tile, BlockPos pos, ServerPlayer sp) {
        if (tile.canBeUsedBy(pos, sp)) {
            tile.setCurrentUser(sp.getUUID());
            NetworkHelper.sendToClientPlayer(sp, new ClientBoundControlViewFinderPacket(TileOrEntityTarget.of(tile)));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, isMoving);
        if (state.getBlock() == this && newState.getBlock() != this && level instanceof ServerLevel sl) {
            BroadcastManager.getInstance(sl).unlinkFeed(LevelBEBroadcastLocation.of(level, pos));
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Optional<BlockState> getRotatedState(BlockState state, LevelAccessor levelAccessor, BlockPos blockPos,
                                                Rotation rotation, Direction axis, @Nullable Vec3 hit) {
        boolean ccw = rotation == Rotation.COUNTERCLOCKWISE_90;
        return Utils.getRotatedDirectionalBlock(state, axis, ccw).or(() -> Optional.of(state));
    }

    @Override
    public void onRotated(BlockState newState, BlockState oldState, LevelAccessor world, BlockPos pos, Rotation rotation,
                          Direction axis, @Nullable Vec3 hit) {
        if (axis.getAxis() == newState.getValue(FACING).getAxis() && world.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            float angle = rotation.rotate(0, 4) * -90;
            Quaternionf currentDir = tile.getWorldOrientation(1);
            Quaternionf q = new Quaternionf().rotateAxis(angle * Mth.DEG_TO_RAD, axis.step());
            q.mul(currentDir);
            tile.setWorldOrientation(q);
            tile.snapToWantedRotationInstantly();
            tile.setChanged();
            tile.getLevel().sendBlockUpdated(pos, oldState, newState, 3);
        }
    }


    @Override
    public void rotateAnalog(BlockState state, Level level, BlockPos pos, Direction face, boolean ccw, float speed) {
        if (level.getBlockEntity(pos) instanceof ViewFinderBlockEntity tile) {
            speed = speed * 0.01f;

            float deltaAngle = -speed * (ccw ? -1 : 1);
            Vector3f rotAxis = face.step();
            Quaternionf cannonRot = tile.getWorldOrientation(1);
            //this is the way we face. now a rotation is being performend on the face "face", either ccw or cw. make this vector rotate acocrdingly
            cannonRot = new Quaternionf().rotateAxis(deltaAngle, rotAxis).mul(cannonRot);
            tile.setWorldOrientation(cannonRot);
        }
    }

    @Override
    public boolean canRotateAnalog(BlockState state, Level level, BlockPos pos, Direction face) {
        return true;
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES.get(state.getValue(FACING).getOpposite());
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING).getOpposite());
    }

}
