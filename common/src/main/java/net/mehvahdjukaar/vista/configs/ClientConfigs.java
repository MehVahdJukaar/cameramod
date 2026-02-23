package net.mehvahdjukaar.vista.configs;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigSpec;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.integration.CompatHandler;

import java.util.function.Supplier;

public class ClientConfigs {

    public static void init() {
    }

    public static final ConfigSpec SPEC;

    public static final Supplier<Integer> RENDER_DISTANCE;
    public static final Supplier<Double> UPDATE_FPS;
    public static final Supplier<Double> MIN_UPDATE_FPS;
    public static final Supplier<Double> THROTTLING_UPDATE_MS;
    public static final Supplier<Double> UPDATE_DISTANCE;
    public static final Supplier<Integer> RESOLUTION_SCALE;
    public static final Supplier<Boolean> RENDER_DEBUG;
    public static final Supplier<Boolean> SCALE_PIXELS;
    public static final Supplier<Boolean> TURN_OFF_EFFECTS;
    public static final Supplier<Double> PIXEL_DENSITY;
    public static final Supplier<Double> VIGNETTE;
    public static final Supplier<Boolean> DRAW_DATE;
    public static final Supplier<Boolean> SCREEN_EFFECTS;

    static {
        ConfigBuilder builder = ConfigBuilder.create(VistaMod.MOD_ID, ConfigType.CLIENT);

        builder.push("television");
        builder.push("visuals");
        RENDER_DISTANCE = builder
                .comment("Render distance that television will use when rendering the live feed. Decreasing this will improve the performance of TVs, possibly by a lot")
                .define("render_distance", 64, 1, 256);

        SCREEN_EFFECTS = builder
                .comment("Turns off all the tv screen effects and draws it as a simple texture. Disabling can make the render slightly faster. All below options will be ignored if this is disabled")
                .define("screen_effects", true);
        PIXEL_DENSITY = builder
                .comment("Pixel density of televisions, in pixels per block side")
                .define("pixel_density", 1.37, 0.1, 10);
        SCALE_PIXELS = builder.comment("Make connected tvs have higher pixel density, such that the per block pixel density is constant")
                .define("constant_pixel_density", true);
        VIGNETTE = builder
                .comment("Amount of vignette effect applied to television live feed (0 = none, 1 = full)")
                .define("vignette", 1, 0, 1.);
        TURN_OFF_EFFECTS = builder
                .comment("Plays an animation when the television is turned off or on")
                .define("turn_off_animation", true);
        builder.pop();
        builder.push("live_feed");

        DRAW_DATE = builder
                .comment("Draws the date and time on the live feed texture")
                .define("draw_date", false);
        UPDATE_FPS = builder
                .gameRestart()
                .comment("How many times per second the television updates its live feed texture. Lowering this will improve performance but make the video less smooth, fractions work too")
                .define("update_fps", 10.0, 0, 60);
        MIN_UPDATE_FPS = builder
                .gameRestart()
                .comment("The minimum update fps for live feed. The mod will throttle update rate when fps are low so this serves at a lower limit")
                .define("min_update_fps", 4.0, 0, 60); //once every 5 ticks
        THROTTLING_UPDATE_MS = builder
                .gameRestart()
                .comment("The maximum number of milliseconds all the logic for updating live feeds can take before fps throttling begins. Lowering this will improve performance but make the video less smooth. 16.66ms equals to 5fps")
                .define("throttling_update_ms", 16.66, 0, 1000000);

        UPDATE_DISTANCE = builder
                .comment("Distance from a TV after which the feed will update in real time")
                .define("update_distance", 20, 1, 512d);

        RESOLUTION_SCALE = builder
                .comment("Scale factor for live feed resolution")
                .define("resolution_scale", 8, 1, 32);

        RENDER_DEBUG = builder
                .comment("Enables rendering of debug information for televisions")
                .define("render_debug", false);

        CompatHandler.addConfigs(builder);

        builder.pop();
        builder.pop();

        SPEC = builder.build();
        SPEC.loadFromFile();
    }

    public static boolean rendersDebug() {
        return RENDER_DEBUG.get()|| PlatHelper.isDev();
    }
}
