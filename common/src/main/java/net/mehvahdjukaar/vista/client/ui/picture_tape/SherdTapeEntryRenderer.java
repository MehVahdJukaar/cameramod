package net.mehvahdjukaar.vista.client.ui.picture_tape;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import org.jetbrains.annotations.Nullable;

public class SherdTapeEntryRenderer implements TapeEntryRenderer {

    @Override
    public boolean matches(ItemStack stack) {
        return DecoratedPotPatterns.getPatternFromItem(stack.getItem()) != null;
    }

    @Override
    @Nullable
    public ResourceLocation getTexture(ItemStack stack) {
        ResourceKey<DecoratedPotPattern> key = DecoratedPotPatterns.getPatternFromItem(stack.getItem());
        if (key == null) return null;
        DecoratedPotPattern pattern = BuiltInRegistries.DECORATED_POT_PATTERN.get(key);
        if (pattern == null) return null;
        return pattern.assetId().withPrefix("textures/entity/decorated_pot/").withSuffix(".png");
    }
}
