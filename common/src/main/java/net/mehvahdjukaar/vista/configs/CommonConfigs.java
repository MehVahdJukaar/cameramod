package net.mehvahdjukaar.vista.configs;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.moonlight.api.platform.configs.ModConfigHolder;
import net.mehvahdjukaar.vista.VistaMod;

import java.util.function.Supplier;

public class CommonConfigs {


    public static final ModConfigHolder SPEC;

    public static final Supplier<Integer> MAX_CONNECTED_TV_SIZE;
    public static final Supplier<Boolean> CREEPER_DROP;
    public static final Supplier<Boolean> CHEST_DROP;
        public static final Supplier<Integer> ENTITY_DETECTOR_RANGE;
        public static final Supplier<String> ENTITY_DETECTOR_FILTER;
        public static final Supplier<Boolean> ENTITY_DETECTOR_REDSTONE_SIGNAL;
        public static final Supplier<Integer> ENTITY_DETECTOR_RETRIGGER_OFF_TICKS;

    static {
        ConfigBuilder builder = ConfigBuilder.create(VistaMod.MOD_ID, ConfigType.COMMON_SYNCED);

        builder.push("general");
        MAX_CONNECTED_TV_SIZE = builder
                .comment("Maximum size of connected TVs (in blocks). Set to 1 to disable multi-block TVs.")
                .define("max_connected_tv_size", 8, 1, 32);
        CREEPER_DROP = builder
                .comment("Whether creepers should drop tapes when killed by the pillagers.")
                .define("creeper_drop", true);
        CHEST_DROP = builder
                .comment("Whether loot chests could contain cassette tapes.")
                .define("chest_drop", true);

        builder.push("entity_detector_filter");
        ENTITY_DETECTOR_RANGE = builder
                .comment("Maximum distance in blocks where the redstone detector filter can detect entities in the camera view.")
                .define("detection_range", 32, 4, 128);
        ENTITY_DETECTOR_FILTER = builder
                .comment("Entity categories detected by the redstone detector filter. Valid values: hostile, passive, players. Use a comma-separated list.")
                .define("entity-detector_filter", "hostile, passive, players");
        ENTITY_DETECTOR_REDSTONE_SIGNAL = builder
                .comment("If true, TVs emit a constant redstone signal while an entity is detected by a linked redstone detector filter.")
                .define("redstone_signal_enabled", true);
        ENTITY_DETECTOR_RETRIGGER_OFF_TICKS = builder
                .comment("How many ticks the TV detector signal stays OFF before re-triggering when a new entity enters while already active.")
                .define("redstone_retrigger_off_ticks", 4, 1, 40);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
        SPEC.forceLoad();
    }

    public static void init() {

    }

}
