package net.mehvahdjukaar.vista.common.projector;

import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SignalProjectorBlock extends BaseEntityBlock {

    public SignalProjectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignalProjectorBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(oldState, level, pos, newState, movedByPiston);
        if (oldState.getBlock() instanceof SignalProjectorBlock &&
                !(newState.getBlock() instanceof SignalProjectorBlock) &&
                level instanceof ServerLevel sl) {
            BroadcastManager.getInstance(sl).unlinkFeed(GlobalPos.of(level.dimension(), pos));
        }
    }


    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (level.getBlockEntity(blockPos) instanceof SignalProjectorBlockEntity tile && tile.canBeEditedBy(player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = player.getItemInHand(interactionHand);
                Utils.openGuiIfPossible(tile, serverPlayer, stack, blockHitResult.getDirection(), blockHitResult.getLocation());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(blockState, level, blockPos, player, interactionHand, blockHitResult);
    }


}
