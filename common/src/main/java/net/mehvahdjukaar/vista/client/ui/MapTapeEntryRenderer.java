package net.mehvahdjukaar.vista.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders a filled map as its actual map image. The gallery goes through the vanilla map renderer,
 * while TV playback needs a flat texture, so the map colours get baked into a cached
 * DynamicTexture instead.
 */
public class MapTapeEntryRenderer implements TapeEntryRenderer {

    private static final int MAP_SIZE = 128;
    private static final Map<Integer, ResourceLocation> CACHE = new HashMap<>();

    @Override
    public boolean matches(ItemStack stack) {
        return stack.has(DataComponents.MAP_ID);
    }

    @Override
    public void render(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = savedData(mapId);
        if (mapId == null || data == null) {
            // map data not received yet
            TapeEntryRenderer.renderUnknown(graphics, stack, x, y, size);
            return;
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 1.0);
        float scale = size / (float) MAP_SIZE;
        pose.scale(scale, scale, 1f);
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();
        Minecraft.getInstance().gameRenderer.getMapRenderer()
                .render(pose, buffer, mapId, data, false, LightTexture.FULL_BRIGHT);
        graphics.flush();
        pose.popPose();
    }

    @Override
    @Nullable
    public ResourceLocation getTexture(ItemStack stack) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null) return null;
        ResourceLocation cached = CACHE.get(mapId.id());
        if (cached != null) return cached;
        MapItemSavedData data = savedData(mapId);
        if (data == null) return null;
        ResourceLocation created = build(mapId.id(), data);
        CACHE.put(mapId.id(), created);
        return created;
    }

    @Nullable
    private static MapItemSavedData savedData(@Nullable MapId mapId) {
        Minecraft mc = Minecraft.getInstance();
        if (mapId == null || mc.level == null) return null;
        return MapItem.getSavedData(mapId, mc.level);
    }

    private static ResourceLocation build(int id, MapItemSavedData data) {
        DynamicTexture texture = new DynamicTexture(MAP_SIZE, MAP_SIZE, true);
        NativeImage image = texture.getPixels();
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                image.setPixelRGBA(x, y, MapColor.getColorFromPackedId(data.colors[x + y * MAP_SIZE]));
            }
        }
        texture.upload();
        return Minecraft.getInstance().getTextureManager().register("vista_tape_map/" + id, texture);
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : CACHE.values()) {
            mc.getTextureManager().release(loc);
        }
        CACHE.clear();
    }
}
