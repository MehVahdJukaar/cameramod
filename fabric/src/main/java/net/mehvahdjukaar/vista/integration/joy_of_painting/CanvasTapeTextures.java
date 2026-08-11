package net.mehvahdjukaar.vista.integration.joy_of_painting;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import xerca.xercapaint.item.ItemCanvas;
import xerca.xercapaint.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Canvas pixels as a DynamicTexture, so tapes can draw them in the reel gallery and across a TV screen.
// Keyed by canvas id + version so an edited canvas rebuilds instead of showing a stale image.
public class CanvasTapeTextures {

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    @Nullable
    public static ResourceLocation getOrCreate(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemCanvas canvas)) return null;
        List<Integer> pixels = stack.get(Items.CANVAS_PIXELS);
        String canvasId = stack.get(Items.CANVAS_ID);
        if (pixels == null || canvasId == null) return null;

        int width = canvas.getWidth();
        int height = canvas.getHeight();
        if (pixels.size() < width * height) return null;

        int version = stack.getOrDefault(Items.CANVAS_VERSION, 1);
        String key = canvasId + "_" + version;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) return cached;

        ResourceLocation created = build(key, pixels, width, height);
        CACHE.put(key, created);
        return created;
    }

    private static ResourceLocation build(String key, List<Integer> pixels, int width, int height) {
        DynamicTexture texture = new DynamicTexture(width, height, true);
        NativeImage image = texture.getPixels();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, toAbgr(pixels.get(x + y * width)));
            }
        }
        texture.upload();
        return Minecraft.getInstance().getTextureManager().register("vista_tape_canvas/" + key, texture);
    }

    // canvas pixels are stored as 0xAARRGGBB; NativeImage wants 0xAABBGGRR (red and blue swapped)
    private static int toAbgr(int argb) {
        return FastColor.ARGB32.color(
                FastColor.ARGB32.alpha(argb),
                FastColor.ARGB32.blue(argb),
                FastColor.ARGB32.green(argb),
                FastColor.ARGB32.red(argb));
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : CACHE.values()) {
            mc.getTextureManager().release(loc);
        }
        CACHE.clear();
    }
}
