package net.mehvahdjukaar.vista.configs;

import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigSpec;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.vista.VistaMod;

import java.util.function.Supplier;

public class CommonConfigs {


    public static final ConfigSpec SPEC;

    public static final Supplier<Integer> MAX_CONNECTED_TV_SIZE;
    public static final Supplier<Boolean> CREEPER_DROP;
    public static final Supplier<Boolean> CHEST_DROP;

    static {
        ConfigBuilder builder = ConfigBuilder.create(VistaMod.MOD_ID, ConfigType.COMMON);
        builder.setSynced();

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
        builder.pop();

        SPEC = builder.build();
        SPEC.loadFromFile();
    }

    public static void init() {

    }

}
