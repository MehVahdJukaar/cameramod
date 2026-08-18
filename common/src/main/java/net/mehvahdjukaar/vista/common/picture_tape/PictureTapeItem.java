package net.mehvahdjukaar.vista.common.picture_tape;

import net.mehvahdjukaar.candlelight.api.VirtualOverride;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.cassette.ITvCassette;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PictureTapeItem extends Item implements ITvCassette {

    private static final int BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);

    public PictureTapeItem(Properties properties) {
        super(properties);
    }

    public static int getMaxEntries() {
        return CommonConfigs.PICTURE_TAPE_MAX_ENTRIES.get();
    }

    public static PictureTapeContent getContent(ItemStack stack) {
        return stack.getOrDefault(VistaMod.PICTURE_TAPE_CONTENT.get(), PictureTapeContent.EMPTY);
    }

    public static void setContent(ItemStack stack, PictureTapeContent content) {
        stack.set(VistaMod.PICTURE_TAPE_CONTENT.get(), content);
    }

    public static boolean isValidEntry(ItemStack stack) {
        return PictureTapeEntries.isValid(stack);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = usedHand == InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
            open(serverPlayer, stack, slot);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public void open(ServerPlayer player, final ItemStack tapeStack, final int tapeSlot) {
        MenuProvider menuProvider = new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return tapeStack.getHoverName();
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player p) {
                return new PictureTapeMenu(containerId, playerInventory, tapeSlot);
            }
        };
        PlatHelper.openCustomMenu(player, menuProvider, buffer -> buffer.writeVarInt(tapeSlot));
    }

    // right-clicking the held tape onto another slot
    @Override
    public boolean overrideStackedOnOther(ItemStack tape, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack slotItem = slot.getItem();
        if (slotItem.isEmpty()) {
            ItemStack removed = removeLast(tape);
            if (removed != null) {
                playRemoveSound(player);
                ItemStack leftover = slot.safeInsert(removed);
                if (!leftover.isEmpty()) addLast(tape, leftover);
            }
        } else if (isValidEntry(slotItem) && canFit(tape)) {
            addLast(tape, slot.safeTake(slotItem.getCount(), 1, player));
            playInsertSound(player);
        } else if (slot.allowModification(player)) {
            ItemStack leftover = addUnpacked(tape, slotItem, player);
            if (leftover != null) slot.set(leftover);
        }
        //consume the right-click either way so it edits the tape instead of swapping it into the slot
        return true;
    }

    // right-clicking another item onto the tape sitting in a slot
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack tape, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        if (other.isEmpty()) {
            ItemStack removed = removeLast(tape);
            if (removed != null) {
                playRemoveSound(player);
                access.set(removed);
            }
        } else if (isValidEntry(other) && canFit(tape)) {
            addLast(tape, other.split(1));
            playInsertSound(player);
        } else {
            ItemStack leftover = addUnpacked(tape, other, player);
            if (leftover != null) access.set(leftover);
        }
        return true;
    }

    // a pile of pictures goes in as the pictures it holds. Gives back what's left of the pile,
    // or null if this wasn't one to begin with
    @Nullable
    private static ItemStack addUnpacked(ItemStack tape, ItemStack container, Player player) {
        PictureTapeEntries.Unpacked unpacked = PictureTapeEntries.unpack(container, freeSpace(tape));
        if (unpacked == null) return null;
        unpacked.pictures().forEach(picture -> addLast(tape, picture));
        playInsertSound(player);
        return unpacked.remainder();
    }

    private static boolean canFit(ItemStack tape) {
        return freeSpace(tape) > 0;
    }

    private static int freeSpace(ItemStack tape) {
        return getMaxEntries() - getContent(tape).size();
    }

    private static void addLast(ItemStack tape, ItemStack picture) {
        PictureTapeContent content = getContent(tape);
        setContent(tape, content.withInsertedAtIndex(content.size(), picture));
    }

    @Nullable
    private static ItemStack removeLast(ItemStack tape) {
        PictureTapeContent content = getContent(tape);
        if (content.size() == 0) return null;
        int last = content.size() - 1;
        ItemStack removed = content.getPicture(last).copy();
        setContent(tape, content.withRemovedAtIndex(last));
        return removed;
    }

    private static void playInsertSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playRemoveSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    // fullness bar under the item, like a bundle

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getContent(stack).size() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(1 + 12 * getContent(stack).size() / getMaxEntries(), 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PictureTapeContent content = getContent(stack);
        if (content.size() > 0) {
            tooltip.add(Component.translatable("item.vista.picture_tape.pictures_count", content.size())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.vista.picture_tape.speed", content.playbackSpeed())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public int getAnalogSignalStrengthInTv(ItemStack stack) {
        return Math.min(15, getContent(stack).size());
    }

    @VirtualOverride("neoforge")
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @VirtualOverride("fabric")
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }
}
