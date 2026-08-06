package net.mehvahdjukaar.vista.common.cassette;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.broadcast.IBroadcastLocation;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;


public class HollowCassetteItem extends Item implements  ITvCassette {

    private static final ResourceLocation ALT_FONT = ResourceLocation.withDefaultNamespace("alt");

    public HollowCassetteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be instanceof IBroadcastSource feed) {
            if (!level.isClientSide) {

                ItemStack stack = context.getItemInHand();
                stack.set(VistaMod.LINKED_FEED_COMPONENT.get(), feed.getBroadcastUUID());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) ||
                stack.get(VistaMod.LINKED_FEED_COMPONENT.get()) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        UUID feedId = stack.get(VistaMod.LINKED_FEED_COMPONENT.get());
        if (feedId != null) {
            if (PlatHelper.getPhysicalSide().isClient()) {
                var mode = ClientConfigs.LINKED_FEED_DISPLAY_MODE.get();
                if (mode == ClientConfigs.LinkedFeedDisplayMode.HIDDEN) return;

                if (mode == ClientConfigs.LinkedFeedDisplayMode.CIPHERED) {
                    tooltipComponents.add(Component.translatable("tooltip.vista.hollow_cassette.linked_ciphered",
                                    cipheredName(feedId))
                            .withStyle(ChatFormatting.GRAY));
                    return;
                }

                Level level = VistaModClient.getLocalLevel();
                BroadcastManager connection = BroadcastManager.getInstance(level);
                if (connection == null) return;
                IBroadcastLocation gp = connection.getFeedLocationById(feedId);
                if (gp == null) {
                    tooltipComponents.add(Component.translatable("tooltip.vista.hollow_cassette.linked_unknown")
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    var tooltip = gp.getTooltipComponent(level);
                    tooltipComponents.add(tooltip.withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private static Component cipheredName(UUID feedId) {
        RandomSource random = RandomSource.create(feedId.getMostSignificantBits() ^ feedId.getLeastSignificantBits());
        StringBuilder word = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            word.append((char) ('a' + random.nextInt(26)));
        }
        return Component.literal(word.toString()).withStyle(Style.EMPTY.withFont(ALT_FONT));
    }

    @Override
    public int getAnalogSignalStrengthInTv(ItemStack stack) {
        return 15;
    }
}
