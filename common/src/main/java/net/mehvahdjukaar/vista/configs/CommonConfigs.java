package net.mehvahdjukaar.vista.configs;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.moonlight.api.platform.configs.ModConfigHolder;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Supplier;

public class CommonConfigs {


    public static final ModConfigHolder SPEC;

    public static final Supplier<Integer> TV_MAX_CONNECTED_TV_SIZE;
    public static final Supplier<Boolean> TV_SQUARE_ASPECT_RATIO;
    public static final Supplier<Integer> MAX_CONNECTED_MIRROR_SIZE;
    public static final Supplier<Boolean> MIRROR_SQUARE_ASPECT_RATIO;
    public static final Supplier<MirrorPlacement> MIRROR_PLACEMENT;
    public static final Supplier<Boolean> MIRROR_ENABLED;
    public static final Supplier<Boolean> CREEPER_DROP;
    public static final Supplier<Boolean> CHEST_DROP;
    public static final Supplier<Boolean> WAVE_GATE_ENABLED;
    public static final Supplier<Boolean> WAVE_GATE_CRAFTABLE;
    public static final Supplier<Integer> SEND_CHUNKS_VIEWED_BY_VIEW_FINDER;
    public static final Supplier<Boolean> LOAD_CHUNKS_VIEWED_BY_VIEW_FINDER;
    public static final Supplier<ViewFinderInteraction> VIEW_FINDER_INTERACTION;
    public static final Supplier<Boolean> TV_CONSUME_ENERGY;
    public static final Supplier<Integer> TV_ENERGY_CONSUMPTION_RATE;
    public static final Supplier<Boolean> PICTURE_TAPE_ENABLED;
    public static final Supplier<Integer> PICTURE_TAPE_MAX_ENTRIES;

    static {
        ConfigBuilder builder = ConfigBuilder.create(VistaMod.MOD_ID, ConfigType.COMMON_SYNCED);

        boolean fabric = PlatHelper.getPlatform().isFabric();

        builder.icon("television").push("television");
        TV_MAX_CONNECTED_TV_SIZE = builder
                .worldReload()
                .comment("Maximum size of connected TVs (in blocks). Set to 1 to disable multi-block TVs.")
                .define("max_connected_size", 8, 1, 24);
        TV_SQUARE_ASPECT_RATIO = builder
                .comment("Makes connected tvs just have a square aspect ratio. If you set to false cassettes will be stretched and will look worse as a result")
                .define("square_aspect_ratio", true);
        // NeoForge only: skip defining on Fabric so the option never appears there
        TV_CONSUME_ENERGY = fabric ? () -> false : builder
                .icon("television")
                .comment("Whether TVs should consume Forge energy (NeoForge only).")
                .define("consume_energy", false);
        TV_ENERGY_CONSUMPTION_RATE = fabric ? () -> 0 : builder
                .comment("Energy consumption rate per tick when TV is powered and has a cassette.")
                .define("energy_consumption_rate", 20, 1, 10000);
        builder.pop(); // television

        builder.icon("picture_tape").push("picture_tape");
        builder.affectsDynamicPacks()
                .worldReload()
                .comment("Whether the picture tape (and its recipe) is enabled. Disabling hides it from the creative tab and disables its recipe.");
        PICTURE_TAPE_ENABLED = builder.mainFeature();
        PICTURE_TAPE_MAX_ENTRIES = builder
                .comment("Maximum number of pictures (filled maps) a single picture tape can hold.")
                .define("max_entries", 32, 1, 256);
        builder.pop(); // picture_tape

        // mirror is a feature-gated category: its "enabled" toggle gates every option under it in the config screen.
        // Set the reload/pack flags after push so they land on the mainFeature() define, not on the push itself
        // (NeoForge rejects a pending restart flag consumed by a push -> "Dangling restart value").
        builder.icon("mirror").push("mirror");
        builder.affectsDynamicPacks()
                .worldReload()
                .comment("Whether mirrors (and the crystalline item used to craft them) are enabled. Disabling hides them from creative tabs, disables the mirror recipe, and stops crystalline from dropping from elder guardians.");
        MIRROR_ENABLED = builder.mainFeature();
        MAX_CONNECTED_MIRROR_SIZE = builder
                .worldReload()
                .comment("Maximum size of connected mirrors (in blocks). Set to 1 to disable multi-block mirrors.")
                .define("max_connected_size", 8, 1, 24);
        MIRROR_SQUARE_ASPECT_RATIO = builder
                .comment("Forces connected mirrors to have a square aspect ratio.")
                .define("square_aspect_ratio", true);
        MIRROR_PLACEMENT = builder
                .comment("Which mirror placements are allowed. NEAR: surface always flush with the front face. FAR: surface always recessed into the block. BOTH: near or far is chosen from where you click along the block's depth axis (near half = near, far half = recessed).")
                .define("placement", MirrorPlacement.BOTH);
        builder.pop(); // mirror

        // wave gate: a feature "enabled" gate (was Mode.OFF) plus a 2-state "craftable" toggle (was CRAFTABLE vs
        // CREATIVE_ONLY). Both gate the recipe/obtainability, so both are world-reload + dynamic-pack flagged.
        builder.icon("wave_gate").push("wave_gate");
        builder.affectsDynamicPacks()
                .worldReload()
                .comment("Whether the wave gate exists at all. Disabling hides it from creative tabs and disables its recipe.");
        WAVE_GATE_ENABLED = builder.mainFeature();
        WAVE_GATE_CRAFTABLE = builder
                .affectsDynamicPacks()
                .worldReload()
                .comment("Whether the wave gate is craftable in survival. When off it is creative-only. Ignored when ComputerCraft is installed, which always makes it craftable.")
                .define("craftable", false);
        builder.pop(); // wave_gate

        builder.icon("viewfinder").push("view_finder");
        VIEW_FINDER_INTERACTION = builder
                .comment("How right-clicking a view finder behaves. GUI: opens a screen with the lens slot, pitch/yaw angle controls and a 'view' button (sneak to jump straight into viewing). LEGACY: no screen at all - click to look through it directly, and use items on it to insert/remove the lens.")
                .define("interaction", ViewFinderInteraction.GUI);
        SEND_CHUNKS_VIEWED_BY_VIEW_FINDER = builder
                .comment("Radius (in chunks) of the extra chunk zone sent to clients for each far-away view finder linked to a nearby TV. Set to 0 to disable client chunk sending.")
                .define("send_viewed_chunks", 4, 0, 16);
        LOAD_CHUNKS_VIEWED_BY_VIEW_FINDER = builder
                .comment("Server loads chunks that are near a far away view finder linked to a tv that's close to at least 1 player. Will increase server strain")
                .define("load_viewed_chunks", false);
        builder.pop(); // view_finder

        builder.icon("cassette").push("drops");
        CREEPER_DROP = builder
                .icon("minecraft:creeper_spawn_egg")
                .comment("Whether creepers should drop tapes when killed by the pillagers.")
                .feature("creeper_drop", true);
        CHEST_DROP = builder
                .icon("minecraft:chest")
                .comment("Whether loot chests could contain cassette tapes.")
                .feature("chest_drop", true);
        builder.pop(); // drops

        SPEC = builder.build();
        SPEC.forceLoad();
    }

    public static void init() {

    }

    public static boolean isMirrorEnabled() {
        return MIRROR_ENABLED.get();
    }

    public static boolean isViewFinderGuiEnabled() {
        return VIEW_FINDER_INTERACTION.get() == ViewFinderInteraction.GUI;
    }

    public static boolean isPictureTapeEnabled() {
        return PICTURE_TAPE_ENABLED.get();
    }

    public static boolean isWaveGateOn() {
        return WAVE_GATE_ENABLED.get();
    }

    public static boolean isWaveGateCraftable() {
        if (!WAVE_GATE_ENABLED.get()) return false;
        if (CompatHandler.COMPUTER_CRAFT) return true;
        return WAVE_GATE_CRAFTABLE.get();
    }

    //TODO:
    public static int distanceFromTvForServerToLoadViewFinders(ServerLevel level) {
        return level.getServer().getPlayerList().getViewDistance();
    }

    public enum MirrorPlacement {
        NEAR,
        FAR,
        BOTH
    }

    public enum ViewFinderInteraction {
        // opens the container screen (lens slot + angle controls + view button)
        GUI,
        // old behavior: no screen, direct look-through and item-based lens insertion
        LEGACY
    }

}
