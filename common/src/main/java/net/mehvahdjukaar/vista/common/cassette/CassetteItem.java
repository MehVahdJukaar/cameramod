package net.mehvahdjukaar.vista.common.cassette;

import net.mehvahdjukaar.moonlight.api.resources.assets.LangBuilder;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CassetteItem extends Item {

    public CassetteItem(Properties properties) {
        super(properties);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return super.getTooltipImage(stack);
    }

    @Nullable
    public static Holder<CassetteTape> getCassette(ItemStack stack) {
        var tag = stack.getTagElement("cassette_tape");
        if (tag != null) {
            var registry = Utils.hackyGetRegistry(CassetteTape.REGISTRY_KEY);
            return registry.getHolder(ResourceKey.create(
                    CassetteTape.REGISTRY_KEY,
                    ResourceLocation.tryParse(tag.getAsString()))).orElse(null);
        }
        return null;
    }

    public static void setCassette(ItemStack stack, Holder<CassetteTape> tape) {
        stack.getOrCreateTag().putString("cassette_tape", tape.unwrapKey()
                .map(k -> k.location().toString()).orElse(""));
    }

    public static void assignCustomCassette(ItemStack stack, Level level) {
        if (stack.hasCustomHoverName()) {
            Component customName = stack.getDisplayName();
            String name = customName.getString().toLowerCase(Locale.ROOT);
            assignCustomCassette(stack, level, name);
        }

    }

    public static void assignCustomCassette(ItemStack stack, Level level, String name) {
        //supporters cassettes
        for (var h : level.registryAccess().registryOrThrow(CassetteTape.REGISTRY_KEY).getTagOrEmpty(VistaMod.SUPPORTER_TAPES_TAG)) {
            var key = h.unwrapKey().get();
            if (key.location().getPath().equals(name)) {
                setCassette(stack, h);
                stack.resetHoverName();
                break;
            }
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        assignCustomCassette(stack, level);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, list, tooltipFlag);

        var tape = getCassette(stack);
        if (tape != null) {
            tape.unwrapKey().ifPresent((resourceKey) -> {
                ResourceLocation location = resourceKey.location();
                if (tape.is(VistaMod.SUPPORTER_TAPES_TAG)) {
                    list.add(Component.literal(LangBuilder.getReadableName(location.getPath()))
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    list.add(Component.translatable(location.toLanguageKey("cassette_tape", "tooltip")).withStyle(ChatFormatting.GRAY));
                }
            });
        }
    }
}
