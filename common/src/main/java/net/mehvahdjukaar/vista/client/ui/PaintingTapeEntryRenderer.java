package net.mehvahdjukaar.vista.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

// paintings render as their variant art (from the item's entity nbt, or a stable fallback one when it
// carries none), with the flat texture stretched to fill
public class PaintingTapeEntryRenderer implements TapeEntryRenderer {

    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/item/painting.png");

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(Items.PAINTING);
    }

    @Override
    public ResourceLocation getTexture(ItemStack stack) {
        PaintingVariant variant = resolveVariant(stack);
        return variant == null ? FALLBACK_TEXTURE : textureOf(variant);
    }

    @Override
    public float getAspectRatio(ItemStack stack) {
        PaintingVariant variant = resolveVariant(stack);
        return variant == null ? 1 : (float) variant.width() / variant.height();
    }

    private static ResourceLocation textureOf(PaintingVariant variant) {
        return variant.assetId().withPrefix("textures/painting/").withSuffix(".png");
    }

    @Nullable
    private static PaintingVariant resolveVariant(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Registry<PaintingVariant> registry = mc.level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT);

        // use the variant baked into the painting's entity data, when it has one
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        if (data != null) {
            ResourceLocation id = ResourceLocation.tryParse(data.copyTag().getString("variant"));
            if (id != null) {
                PaintingVariant stored = registry.get(id);
                if (stored != null) return stored;
            }
        }
        // otherwise a stable pick, so gallery and tv always agree on the same art
        return registry.getRandom(RandomSource.create(ItemStack.hashItemAndComponents(stack)))
                .map(Holder::value).orElse(null);
    }
}
