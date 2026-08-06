package net.mehvahdjukaar.vista.integration.refurbished_furniture;

import com.mrcrayfish.furniture.refurbished.client.renderer.blockentity.ElectricBlockEntityRenderer;
import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;

public class RefurbishedFurnitureCompat {

    public static void renderNodeAndWires(TVBlockEntity tv) {
        if (tv instanceof IElectricityNode node) {
            ElectricBlockEntityRenderer.drawNodeAndConnections(node);
        }
    }
}
