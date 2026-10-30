package net.mehvahdjukaar.vista.integration.joy_of_painting;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeEntries;
import net.minecraft.world.item.ItemStack;
import xerca.xercapaint.item.ItemCanvas;
import xerca.xercapaint.item.Items;

public class JoyOfPaintingCompat {

    public static void init() {
        // let the picture tape store painted canvases too
        PictureTapeEntries.register(JoyOfPaintingCompat::isCanvas);

        if (PlatHelper.getPhysicalSide().isClient()) {
            JoyOfPaintingCompatClient.init();
        }
    }

    // a painted canvas: the canvas item plus actual pixel data (a blank canvas carries none)
    public static boolean isCanvas(ItemStack stack) {
        return stack.getItem() instanceof ItemCanvas && stack.has(Items.CANVAS_PIXELS);
    }
}
