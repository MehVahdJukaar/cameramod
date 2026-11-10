package net.mehvahdjukaar.vista.common.picture_tape;

import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PictureTapeMenu extends AbstractContainerMenu {

    public static final int MIN_SPEED = 2;
    public static final int MAX_SPEED = 100;
    public static final int INV_X = 8;
    public static final int INV_TOP = 99;
    public static final int HOTBAR_Y = INV_TOP + 58;

    private final int tapeSlot;
    private final ItemStack tapeStack;
    private final Player owner;
    private final SimpleContainer tapeContent;
    private final int maxEntries;
    private final DataSlot playSpeed = DataSlot.standalone();
    private boolean mutating;
    private int lastFilledCount;

    public static PictureTapeMenu fromBuffer(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        return new PictureTapeMenu(containerId, playerInventory, buffer.readVarInt());
    }

    public PictureTapeMenu(int containerId, Inventory playerInventory, int tapeSlot) {
        this(VistaMod.PICTURE_TAPE_MENU.get(), containerId, playerInventory, tapeSlot);
    }

    protected PictureTapeMenu(MenuType<?> type, int containerId, Inventory playerInventory, int tapeSlot) {
        super(type, containerId);
        this.maxEntries = PictureTapeItem.getMaxEntries();
        this.tapeContent = new SimpleContainer(maxEntries);
        this.tapeSlot = tapeSlot;
        this.owner = playerInventory.player;
        this.tapeStack = playerInventory.getItem(tapeSlot);
        if (!(tapeStack.getItem() instanceof PictureTapeItem)) {
            throw new IllegalStateException("Expected a picture tape in slot " + tapeSlot + " but got " + tapeStack);
        }

        this.playSpeed.set(PictureTapeItem.getContent(tapeStack).playbackSpeed());
        this.addDataSlot(playSpeed);
        this.mutating = true;
        List<ItemStack> stored = PictureTapeItem.getContent(tapeStack).pictures().toList();
        for (int i = 0; i < this.maxEntries && i < stored.size(); i++) {
            tapeContent.setItem(i, stored.get(i).copy());
        }
        this.mutating = false;
        this.lastFilledCount = getFilledCount();

        for (int i = 0; i < this.maxEntries; i++) {
            this.addSlot(new MapSlot(tapeContent, i));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new TapeAwareSlot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new TapeAwareSlot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        pushMapData();
    }

    public SimpleContainer getTapeContent() {
        return tapeContent;
    }

    public int getFilledCount() {
        int n = 0;
        for (int i = 0; i < this.maxEntries; i++) {
            if (!tapeContent.getItem(i).isEmpty()) n++;
        }
        return n;
    }

    public int getVisibleCells() {
        int filled = getFilledCount();
        return filled < this.maxEntries ? filled + 1 : this.maxEntries;
    }

    public int getPlaySpeed() {
        return playSpeed.get();
    }

    public void setPlaySpeedClientSide(int speed) {
        this.playSpeed.set(speed);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.PICKUP && button == 0 && slotId >= 0 && slotId < this.maxEntries
                && tryInsertCarried(slotId)) {
            compact();
            return;
        }
        super.clicked(slotId, button, clickType, player);
        compact();
    }

    private boolean tryInsertCarried(int index) {
        ItemStack carried = getCarried();
        int filled = getFilledCount();
        int freeSpace = this.maxEntries - filled;
        if (carried.isEmpty() || freeSpace <= 0) return false;
        if (PictureTapeItem.isValidEntry(carried)) {
            if (index >= filled) return false;
            insertShifted(index, List.of(carried.split(1)));
            setCarried(carried);
            return true;
        }
        PictureTapeEntries.Unpacked unpacked = PictureTapeEntries.unpack(carried, freeSpace);
        if (unpacked == null) return false;
        insertShifted(Math.min(index, filled), unpacked.pictures());
        setCarried(unpacked.remainder());
        return true;
    }

    private void insertShifted(int index, List<ItemStack> pictures) {
        List<ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < this.maxEntries; i++) {
            ItemStack s = tapeContent.getItem(i);
            if (!s.isEmpty()) entries.add(s);
        }
        entries.addAll(Math.min(index, entries.size()), pictures);
        this.mutating = true;
        for (int i = 0; i < this.maxEntries; i++) {
            tapeContent.setItem(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
        }
        this.mutating = false;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= MIN_SPEED && id <= MAX_SPEED) {
            this.playSpeed.set(id);
            compact();
            return true;
        }
        return false;
    }

    private void compact() {
        if (mutating) return;
        List<ItemStack> filled = new ArrayList<>();
        for (int i = 0; i < this.maxEntries; i++) {
            ItemStack s = tapeContent.getItem(i);
            if (!s.isEmpty()) filled.add(s);
        }
        boolean hasGap = false;
        for (int i = 0; i < this.maxEntries; i++) {
            boolean shouldBeFilled = i < filled.size();
            if (tapeContent.getItem(i).isEmpty() == shouldBeFilled) {
                hasGap = true;
                break;
            }
        }
        if (hasGap) {
            this.mutating = true;
            for (int i = 0; i < this.maxEntries; i++) {
                tapeContent.setItem(i, i < filled.size() ? filled.get(i) : ItemStack.EMPTY);
            }
            this.mutating = false;
        }
        PictureTapeItem.setContent(tapeStack, new PictureTapeContent(new ArrayList<>(filled), getPlaySpeed()));

        if (filled.size() > lastFilledCount) {
            PictureTapeItem.playInsertSound(owner);
        } else if (filled.size() < lastFilledCount) {
            PictureTapeItem.playRemoveSound(owner);
        }
        lastFilledCount = filled.size();
        pushMapData();
    }

    private void pushMapData() {
        if (!(owner instanceof ServerPlayer serverPlayer)) return;
        for (int i = 0; i < this.maxEntries; i++) {
            PictureTapeMaps.sendMapData(serverPlayer, tapeContent.getItem(i));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < this.maxEntries) {
                // map -> player inventory
                if (!this.moveItemStackTo(stack, this.maxEntries, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!PictureTapeItem.isValidEntry(stack)) {
                int filled = getFilledCount();
                PictureTapeEntries.Unpacked unpacked = PictureTapeEntries.unpack(stack, this.maxEntries - filled);
                if (unpacked == null) return ItemStack.EMPTY;
                insertShifted(filled, unpacked.pictures());
                slot.set(unpacked.remainder());
                compact();
                return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, 0, this.maxEntries, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory().getItem(tapeSlot).getItem() instanceof PictureTapeItem;
    }

    private static class MapSlot extends Slot {
        public MapSlot(SimpleContainer container, int index) {
            super(container, index, -9000, -9000);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PictureTapeItem.isValidEntry(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private class TapeAwareSlot extends Slot {
        public TapeAwareSlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.getContainerSlot() != tapeSlot && super.mayPickup(player);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.getContainerSlot() != tapeSlot && super.mayPlace(stack);
        }
    }
}
