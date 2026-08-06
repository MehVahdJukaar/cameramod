package net.mehvahdjukaar.vista.integration.camera_mod;

import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.inventory.AlbumInventory;
import de.maxhenkel.camera.items.AlbumItem;
import de.maxhenkel.camera.items.ImageItem;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeEntries;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CameraModCompat {

    public static void init() {
        PictureTapeEntries.register(CameraModCompat::isPicture);
        PictureTapeEntries.registerUnpacker(CameraModCompat::unpackAlbum);

        if (PlatHelper.getPhysicalSide().isClient()) {
            CameraModCompatClient.init();
        }
    }

    public static boolean isPicture(ItemStack stack) {
        return stack.getItem() instanceof ImageItem && ImageData.fromStack(stack) != null;
    }

    @Nullable
    private static PictureTapeEntries.Unpacked unpackAlbum(ItemStack container, int maxCount) {
        if (!(container.getItem() instanceof AlbumItem)) return null;
        ItemContainerContents contents = container.get(DataComponents.CONTAINER);
        // albums written before the mod moved to data components only convert once opened
        if (contents == null) return null;

        NonNullList<ItemStack> slots = NonNullList.withSize(AlbumInventory.SIZE, ItemStack.EMPTY);
        contents.copyInto(slots);

        List<ItemStack> taken = new ArrayList<>();
        for (int i = 0; i < slots.size() && taken.size() < maxCount; i++) {
            ItemStack picture = slots.get(i);
            if (!isPicture(picture)) continue;
            taken.add(picture);
            slots.set(i, ItemStack.EMPTY);
        }
        if (taken.isEmpty()) return null;

        ItemStack remainder = container.copy();
        remainder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        return new PictureTapeEntries.Unpacked(taken, remainder);
    }
}
