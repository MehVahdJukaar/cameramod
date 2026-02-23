package net.mehvahdjukaar.vista.common.cassette;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;


public class HollowCassetteItem extends Item {

    public HollowCassetteItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static UUID getLinkedFeed(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag orCreateTag = stack.getOrCreateTag();
        if (!orCreateTag.hasUUID("linked_feed")) return null;
        return orCreateTag.getUUID("linked_feed");
    }

    public static void setLinkedFeed(ItemStack stack, UUID id) {
        stack.getOrCreateTag().putUUID("linked_feed", id);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be instanceof IBroadcastProvider feed) {
            if (!level.isClientSide) {

                ItemStack stack = context.getItemInHand();
                setLinkedFeed(stack, feed.getUUID());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) ||
                getLinkedFeed(stack) != null;
    }


    @Override
    public void appendHoverText(ItemStack itemStack, @Nullable Level level, List<Component> list, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, level, list, tooltipFlag);
        UUID feedId = getLinkedFeed(itemStack);
        if (feedId != null) {
            if (PlatHelper.getPhysicalSide().isClient()) {
                if (level == null) level = VistaModClient.getLocalLevel();
                BroadcastManager connection = BroadcastManager.getInstance(level);
                if (connection == null) return;
                GlobalPos gp = connection.getBroadcastOriginById(feedId);
                if (gp == null) {
                    list.add(Component.translatable("tooltip.vista.hollow_cassette.linked_unknown")
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    if (gp.dimension() == level.dimension()) {
                        BlockPos pos = gp.pos();
                        list.add(Component.translatable("tooltip.vista.hollow_cassette.linked",
                                        pos.getX(), pos.getY(), pos.getZ())
                                .withStyle(ChatFormatting.GRAY));
                    } else {
                        list.add(Component.translatable("tooltip.vista.hollow_cassette.linked_away", gp.dimension())
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }
    }
}
