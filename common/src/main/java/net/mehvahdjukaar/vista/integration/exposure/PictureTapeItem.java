package net.mehvahdjukaar.vista.integration.exposure;

import io.github.mortuusars.exposure.world.inventory.AlbumMenu;
import net.mehvahdjukaar.moonlight.api.misc.ForgeOverride;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PictureTapeItem extends Item {
    public PictureTapeItem(Item.Properties properties) {
        super(properties);
    }

    public static PictureTapeContent getContent(ItemStack stack) {
        return stack.getOrDefault(ExposureCompat.PICTURE_TAPE_CONTENT.get(), PictureTapeContent.EMPTY);
    }

    public static void setContent(ItemStack stack, PictureTapeContent content) {
        stack.set(ExposureCompat.PICTURE_TAPE_CONTENT.get(), content);
    }

    public static void setPictureAtIndex(ItemStack stack, int index, ItemStack picture) {
        PictureTapeContent content = getContent(stack);
        PictureTapeContent newContent = content.withInsertedAtIndex(index, picture);
        setContent(stack, newContent);
    }

    public static void removePictureAtIndex(ItemStack stack, int index) {
        PictureTapeContent content = getContent(stack);
        PictureTapeContent newContent = content.withRemovedAtIndex(index);
        setContent(stack, newContent);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer) {
            int albumSlot = usedHand == InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
            this.open(serverPlayer, itemStack, albumSlot);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    public void open(ServerPlayer player, final ItemStack albumStack, final int albumSlot) {
        MenuProvider menuProvider = new MenuProvider() {
            public @NotNull Component getDisplayName() {
                return albumStack.getHoverName();
            }

            public @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
                return new PictureTapeMenu(containerId, playerInventory, albumSlot);
            }
        };

        PlatHelper.openCustomMenu(player, menuProvider, (buffer) -> {
            buffer.writeVarInt(albumSlot);
        });
    }

    @Override
    public void appendHoverText(ItemStack itemStack, @Nullable Level level, List<Component> list, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, level, list, tooltipFlag);
        list.add(Component.literal("WIP"));
        int photographsCount = getContent(itemStack).size();
        if (photographsCount > 0) {
            list.add(Component.translatable("item.exposure.album.tooltip.photos_count", photographsCount));
        }
    }

    @ForgeOverride
    public boolean shouldPlayEquipAnimation(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    //TODO. faric override

}