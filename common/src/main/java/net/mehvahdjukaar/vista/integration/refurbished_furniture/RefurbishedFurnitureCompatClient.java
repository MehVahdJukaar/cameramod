package net.mehvahdjukaar.vista.integration.refurbished_furniture;

import com.mrcrayfish.furniture.refurbished.item.PoweredItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Optional;

public class RefurbishedFurnitureCompatClient {

    // Same "Requires power from an Electricity Generator" line Refurbished Furniture puts on its own
    // powered blocks, wrapped the same way it wraps it (PoweredItem does this inline).
    public static void addRequiresPowerTooltip(List<Component> lines) {
        Minecraft.getInstance().font.getSplitter()
                .splitLines(PoweredItem.POWER_TOOLTIP, 150, Style.EMPTY)
                .forEach(text -> {
                    MutableComponent line = Component.empty();
                    text.visit((style, s) -> {
                        line.append(Component.literal(s).withStyle(style));
                        return Optional.empty();
                    }, Style.EMPTY);
                    lines.add(line);
                });
    }
}
