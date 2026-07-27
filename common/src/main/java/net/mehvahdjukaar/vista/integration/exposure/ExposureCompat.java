package net.mehvahdjukaar.vista.integration.exposure;

import io.github.mortuusars.exposure.world.item.AlbumItem;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import io.github.mortuusars.exposure.world.item.component.StackedPhotographs;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeEntries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ExposureCompat {

    public static void init() {
        // let the picture tape store exposure pictures too
        PictureTapeEntries.register(ExposureCompat::isTapeEntry);
        // a pile of photographs goes in as its individual photographs instead
        PictureTapeEntries.registerUnpacker(ExposureCompat::unpackPhotographStack);

        if (PlatHelper.getPhysicalSide().isClient()) {
            ExposureCompatClient.init();
        }
    }

    public static boolean isExposurePicture(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof PhotographItem
                || item instanceof StackedPhotographsItem
                || item instanceof AlbumItem;
    }

    private static boolean isTapeEntry(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof PhotographItem || item instanceof AlbumItem;
    }

    // taken off the top of the pile, so they land on the tape in the order they're stacked in
    @Nullable
    private static PictureTapeEntries.Unpacked unpackPhotographStack(ItemStack container, int maxCount) {
        if (!(container.getItem() instanceof StackedPhotographsItem stackedItem)) return null;
        ItemStack copy = container.copy();
        List<ItemStack> taken = new ArrayList<>();
        while (taken.size() < maxCount && !stackedItem.getPhotographs(copy).isEmpty()) {
            taken.add(stackedItem.removeTopPhotograph(copy).getItemStack());
        }
        StackedPhotographs left = stackedItem.getPhotographs(copy);
        // exposure only keeps the stacked item around while it holds more than one photograph
        ItemStack remainder = switch (left.size()) {
            case 0 -> ItemStack.EMPTY;
            case 1 -> stackedItem.removeTopPhotograph(copy).getItemStack();
            default -> copy;
        };
        return new PictureTapeEntries.Unpacked(taken, remainder);
    }
}
