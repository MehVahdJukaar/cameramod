package net.mehvahdjukaar.vista.common.tv;

import net.mehvahdjukaar.candlelight.api.VirtualOverride;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.integration.refurbished_furniture.RefurbishedFurnitureCompatClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class TVItem extends BlockItem {

    public TVItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (CommonConfigs.isTvElectricityEnabled() && PlatHelper.getPhysicalSide().isClient()) {
            RefurbishedFurnitureCompatClient.addRequiresPowerTooltip(lines);
        }
        super.appendHoverText(stack, context, lines, flag);
    }

    @VirtualOverride("neoforge")
    public boolean isEnderMask(ItemStack stack, Player player, EnderMan enderMan) {
        return true;
    }
}
