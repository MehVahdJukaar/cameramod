package net.mehvahdjukaar.vista.integration.supplementaries;

import com.google.common.base.Suppliers;
import net.mehvahdjukaar.supplementaries.client.MobHeadShadersManager;
import net.mehvahdjukaar.supplementaries.common.utils.MiscUtils;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public class SuppCompat {

    private static final Supplier<Item> QUARK_GLASS_PANE =
            Suppliers.memoize(() -> (BuiltInRegistries.ITEM.getOptional(new ResourceLocation("quark:dirty_glass_pane")))
                    .orElse(null));
    private static final ResourceLocation RETRO_SHADER = new ResourceLocation("vista:shaders/post/ntsc_codec.json");

    public static ResourceLocation getShaderForItem(Item item) {
        if (item == VistaMod.TV_ITEM.get()) return null;
        if (item == QUARK_GLASS_PANE.get()) return RETRO_SHADER;
        String shaderForItem = MobHeadShadersManager.INSTANCE.getShaderForItem(item);
        return shaderForItem == null ? null : new ResourceLocation(shaderForItem);
    }

    public static boolean isFunny() {
        return MiscUtils.FESTIVITY.isAprilsFool();
    }
}
