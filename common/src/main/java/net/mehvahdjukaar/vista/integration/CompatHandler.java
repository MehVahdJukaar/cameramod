package net.mehvahdjukaar.vista.integration;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.vista.integration.computer_craft.CCCompat;
import net.mehvahdjukaar.vista.integration.distant_horizons.DistantHorizonsCompat;
import net.mehvahdjukaar.vista.integration.exposure.ExposureCompat;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.world.item.CreativeModeTabs;

public class CompatHandler {

    public static final boolean EXPOSURE = PlatHelper.isModLoaded("exposure");
    public static final boolean DISTANT_HORIZONS = PlatHelper.isModLoaded("distanthorizons");
    public static final boolean COMPUTER_CRAFT = PlatHelper.isModLoaded("computercraft");
    public static final boolean SUPPLEMENTARIES = PlatHelper.isModLoaded("supplementaries");
    public static final boolean IRIS = PlatHelper.isModLoaded("iris") || PlatHelper.isModLoaded("oculus");
    public static final boolean SODIUM = PlatHelper.isModLoaded("sodium") || PlatHelper.isModLoaded("embeddium");
    public static final boolean ENTITYCULLING = PlatHelper.isModLoaded("entityculling");

    public static void init() {
        if (EXPOSURE) ExposureCompat.init();
        if (COMPUTER_CRAFT) CCCompat.init();
        PlatHelper.addCommonSetup(() -> {
            if (COMPUTER_CRAFT) CCCompat.setup();
        });
    }


    public static void addItemsToTabs(RegHelper.ItemToTabEvent event) {
    }

    public static void addConfigs(ConfigBuilder builder) {
        if (DISTANT_HORIZONS) DistantHorizonsCompat.addConfigs(builder);
        if (IRIS) IrisCompat.addConfigs(builder);
    }
}
