package net.mehvahdjukaar.vista.integration;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.integration.computer_craft.CCCompat;
import net.mehvahdjukaar.vista.integration.distant_horizons.DistantHorizonsCompat;
import net.mehvahdjukaar.vista.integration.entity_culling.EntityCullingCompat;
import net.mehvahdjukaar.vista.integration.exposure.ExposureCompat;
import net.mehvahdjukaar.vista.integration.flashback.FlashbackCompat;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.mehvahdjukaar.vista.integration.veil.VeilCompat;
import net.mehvahdjukaar.vista.integration.watermedia.WatermediaSession;
import net.minecraft.world.item.CreativeModeTabs;

public class CompatHandler {

    public static final boolean EXPOSURE = PlatHelper.isModLoaded("exposure");
    public static final boolean DISTANT_HORIZONS = PlatHelper.isModLoaded("distanthorizons");
    public static final boolean COMPUTER_CRAFT = PlatHelper.isModLoaded("computercraft");
    public static final boolean SUPPLEMENTARIES = PlatHelper.isModLoaded("supplementaries");
    public static final boolean IRIS = PlatHelper.isModLoaded("iris") || PlatHelper.isModLoaded("oculus");
    public static final boolean SODIUM = PlatHelper.isModLoaded("sodium") || PlatHelper.isModLoaded("embeddium");
    public static final boolean ENTITYCULLING = PlatHelper.isModLoaded("entityculling");
    public static final boolean ALEX_CAVES = PlatHelper.isModLoaded("alexs-caves");
    public static final boolean FLASHBACK = PlatHelper.isModLoaded("flashback");
    public static final boolean VEIL = PlatHelper.isModLoaded("veil");
    public static final boolean WATERMEDIA = isWatermediaCompatible();

    private static boolean isWatermediaCompatible() {
        if (!PlatHelper.isModLoaded("watermedia")) return false;
        String version = PlatHelper.getModVersion("watermedia");
        // WaterMedia 3.0.0+ reworked its API in a way incompatible with our compat. Disable it gracefully.
        if (isVersionAtLeast3(version)) {
            VistaMod.LOGGER.warn("Detected WaterMedia {} (3.0.0 or greater). Vista's WaterMedia compatibility is " +
                    "incompatible with this version and has been disabled. Video and web textures will be unavailable. " +
                    "Use WaterMedia below 3.0.0 to restore them.", version);
            return false;
        }
        return true;
    }

    private static boolean isVersionAtLeast3(String version) {
        if (version == null) return false;
        // strip any leading non-digit prefix and read the major version number
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(version);
        return m.find() && Integer.parseInt(m.group(1)) >= 3;
    }

    public static void init() {
        if (EXPOSURE) ExposureCompat.init();
        if (COMPUTER_CRAFT) CCCompat.init();
        PlatHelper.addCommonSetup(() -> {
            if (COMPUTER_CRAFT) CCCompat.setup();
            if (PlatHelper.getPhysicalSide().isClient() && WATERMEDIA) WatermediaSession.initHack();
        });
    }


    public static void addItemsToTabs(RegHelper.ItemToTabEvent event) {
        if (EXPOSURE) event.add(CreativeModeTabs.TOOLS_AND_UTILITIES, ExposureCompat.PICTURE_TAPE.get());
    }

    public static void addConfigs(ConfigBuilder builder) {
        if (DISTANT_HORIZONS) DistantHorizonsCompat.addConfigs(builder);
        if (IRIS) IrisCompat.addConfigs(builder);
    }

    public static Runnable decorateRenderer(Runnable runTask) {
        if (DISTANT_HORIZONS) {
            runTask = DistantHorizonsCompat.decorateRenderWithoutLOD(runTask);
        }
        if (IRIS) {
            runTask = IrisCompat.decorateRendererWithoutShaderPacks(runTask);
        }
        if (ENTITYCULLING) {
            runTask = EntityCullingCompat.decorateRenderWithoutCulling(runTask);
        }
        if (FLASHBACK) {
            runTask = FlashbackCompat.decorateRenderRestoringMatrices(runTask);
        }
        if(VEIL) {
            runTask = VeilCompat.decorateWithSameDarnHacksVeilUses(runTask);
        }
        return runTask;
    }
}
