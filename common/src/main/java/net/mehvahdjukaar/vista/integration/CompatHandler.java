package net.mehvahdjukaar.vista.integration;

import net.mehvahdjukaar.candlelight.api.PlatformImpl;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.integration.computer_craft.CCCompat;
import net.mehvahdjukaar.vista.integration.entity_culling.EntityCullingCompat;
import net.mehvahdjukaar.vista.integration.exposure.ExposureCompat;
import net.mehvahdjukaar.vista.integration.flashback.FlashbackCompat;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.mehvahdjukaar.vista.integration.joy_of_painting.JoyOfPaintingCompat;
import net.mehvahdjukaar.vista.integration.supplementaries.SuppCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static final boolean VEIL_BONK = PlatHelper.isModLoaded("veil_bonk");
    public static final boolean IRIS_BONK = PlatHelper.isModLoaded("iris_bonk");
    public static final boolean VAMPIRISM = PlatHelper.isModLoaded("vampirism");
    public static final boolean SUPERNATURAL = PlatHelper.isModLoaded("supernatural");
    public static final boolean CREATE = PlatHelper.isModLoaded("create");
    public static final boolean JOY_OF_PAINTING = PlatHelper.isModLoaded("xercapaint");
    public static final boolean REFURBISHED_FURNITURE = PlatHelper.isModLoaded("refurbished_furniture");
    public static final boolean CAMERA_MOD = PlatHelper.isModLoaded("camera");
    public static final boolean WATERMEDIA = isWatermediaCompatible();

    private static boolean isWatermediaCompatible() {
        if (!PlatHelper.isModLoaded("watermedia")) return false;
        String version = PlatHelper.getModVersion("watermedia");
        if (!isVersionAtLeast3(version)) {
            VistaMod.LOGGER.warn("Detected WaterMedia {}. Vista needs WaterMedia 3.0.0 or newer, its compatibility has been disabled", version);
            return false;
        }
        return true;
    }

    private static boolean isVersionAtLeast3(String version) {
        if (version == null) return false;
        Matcher m = Pattern.compile("(\\d+)").matcher(version);
        return m.find() && Integer.parseInt(m.group(1)) >= 3;
    }

    public static void init() {
        initPlat();
        if (EXPOSURE) ExposureCompat.init();
        if (SUPPLEMENTARIES) SuppCompat.init();
        if (COMPUTER_CRAFT) CCCompat.init();
        if (JOY_OF_PAINTING) JoyOfPaintingCompat.init();
        PlatHelper.addCommonSetup(() -> {
            if (COMPUTER_CRAFT) CCCompat.setup();
            setupPlat();
        });
    }

    @PlatformImpl
    public static void initPlat() {
        throw new AssertionError();
    }

    @PlatformImpl
    public static void setupPlat() {
        throw new AssertionError();
    }

    public static void addItemsToTabs(RegHelper.ItemToTabEvent event) {
    }

    public static void addConfigs(ConfigBuilder builder) {
        if (IRIS) IrisCompat.addConfigs(builder);
    }

    public static Runnable decorateRenderer(Runnable runTask) {
        if (IRIS) {
            runTask = IrisCompat.decorateRendererWithoutShaderPacks(runTask);
        }
        if (ENTITYCULLING) {
            runTask = EntityCullingCompat.decorateRenderWithoutCulling(runTask);
        }
        if (FLASHBACK) {
            runTask = FlashbackCompat.decorateRenderRestoringMatrices(runTask);
        }
        return runTask;
    }
}
