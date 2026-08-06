package net.mehvahdjukaar.vista.configs;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.moonlight.api.platform.configs.ModConfigHolder;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.textures.ScalingMode;
import net.mehvahdjukaar.vista.integration.CompatHandler;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ClientConfigs {

    public static void init() {
    }

    public static final ModConfigHolder SPEC;

    public static final Supplier<Integer> RENDER_DISTANCE;
    public static final Supplier<Integer> MIRROR_RENDER_DISTANCE;
    public static final Supplier<Integer> MIRROR_RESOLUTION_SCALE;
    public static final Supplier<MirrorUpdateMode> MIRROR_UPDATE_MODE;
    public static final Supplier<MirrorRecursionMode> MIRROR_RECURSION_MODE;
    public static final Supplier<Integer> MIRROR_MAX_RECURSION_DEPTH;
    public static final Supplier<Double> MIRROR_RECURSION_RES_DIVIDER;
    public static final Supplier<Double> MIRROR_RECURSION_DIST_DIVIDER;
    public static final Supplier<Boolean> MIRROR_SMOOTH;
    public static final Supplier<Double> UPDATE_FPS;
    public static final Supplier<Double> MIN_UPDATE_FPS;
    public static final Supplier<Double> THROTTLING_UPDATE_MS;
    public static final Supplier<Double> UPDATE_DISTANCE;
    public static final Supplier<Integer> LIVE_FEED_RESOLUTION_SCALE;
    public static final Supplier<Boolean> RENDER_DEBUG;
    public static final Supplier<LinkedFeedDisplayMode> LINKED_FEED_DISPLAY_MODE;
    public static final Supplier<Boolean> SCALE_PIXELS;
    public static final Supplier<Boolean> TURN_OFF_EFFECTS;
    public static final Supplier<Float> PIXEL_DENSITY;
    public static final Supplier<Float> VIGNETTE;
    public static final Supplier<Boolean> SCREEN_EFFECTS;
    public static final Supplier<Boolean> ENABLE_FFMPEG;
    public static final Supplier<Integer> WEB_RESOLUTION_SCALE;
    public static final Supplier<ScalingMode> SCALING_MODE;
    public static final Supplier<Boolean> BILINEAR;
    public static final Supplier<EngineMode> VIDEO_ENGINE;
    public static final Supplier<List<String>> SAFE_URLS;
    public static Pattern safeRegex;


    static {
        ConfigBuilder builder = ConfigBuilder.create(VistaMod.MOD_ID, ConfigType.CLIENT);

        builder.icon("mirror").push("mirror");
        MIRROR_RENDER_DISTANCE = builder
                .comment("Block entity render distance for mirrors. Mirrors beyond this distance will not render their reflection.")
                .define("render_distance", 64, 1, 2048);
        MIRROR_RESOLUTION_SCALE = builder
                .comment("Scale factor for mirror reflection resolution. Each mirror block is 16 virtual pixels wide; this multiplies that area. Higher values are sharper but more expensive.")
                .define("resolution_scale", 8, 1, 32);
        MIRROR_UPDATE_MODE = builder
                .comment("How mirror reflections are dispatched. RENDER_TICK_END (default, recommended): the BE renderer queues mirrors into a pending list; the queue is flushed from a top-level frame hook (Fabric mixin after GameRenderer.render; NeoForge RenderFrameEvent.Post) that's guaranteed to run outside any level render — safe under recursive renderLevel calls from other mods. TEXTURE_REFRESH: piggybacks on the live-feed texture refresh dispatch (one render per visible mirror, end-of-frame). Switch to TEXTURE_REFRESH if you suspect a timing-related rendering glitch.")
                .define("update_mode", MirrorUpdateMode.RENDER_TICK_END);
        MIRROR_SMOOTH = builder
                .comment("Smooth the mirror reflection with bilinear texture filtering. Enabled gives a softer, less pixelated reflection; disabled keeps it crisp and pixelated.")
                .define("smooth_reflection", false);

        builder.push("recursion");
        MIRROR_RECURSION_MODE = builder
                .comment("How mirrors-inside-mirrors are handled. OFF: nested mirrors don't render at all (you see the frame, no reflection). SHARED (cheap): each mirror reuses its own self-reflection texture when seen inside another mirror — looks fine at a glance but parallax is wrong at depth >=1 (the deeper reflections won't slide correctly as you move). RECURSIVE (expensive): each chain gets its own off-axis render with correct parallax, up to max_depth. Beyond the depth cap the nested mirror is not drawn at all.")
                .define("mode", MirrorRecursionMode.RECURSIVE);
        MIRROR_MAX_RECURSION_DEPTH = builder
                .comment("Max nesting depth in RECURSIVE recursion mode. 0 = no recursion (equivalent to OFF). 1 = one level of correct nested reflection. Each extra level multiplies cost, but resolution_divider and distance_divider attenuate per-level cost.")
                .define("max_depth", 1, 0, 8);
        MIRROR_RECURSION_RES_DIVIDER = builder
                .comment("Per-level resolution divider for RECURSIVE recursion mode. Texture resolution at depth D = base * (1 / divider^D). 2.0 means each nesting halves resolution.")
                .define("resolution_divider", 2.0, 1.0, 16.0);
        MIRROR_RECURSION_DIST_DIVIDER = builder
                .comment("Per-level render-distance divider for RECURSIVE recursion mode. Render distance at depth D = base / divider^D. 2.0 means each nesting halves render distance.")
                .define("distance_divider", 2.0, 1.0, 16.0);
        builder.pop(); // recursion
        builder.pop(); // mirror

        builder.icon("television").push("television");
        RENDER_DISTANCE = builder
                .comment("Render distance that television will use when rendering the live feed. Decreasing this will improve the performance of TVs, possibly by a lot")
                .define("render_distance", 64, 1, 2048);

        // screen_effects gates all the visual options below it: when off, they gray out in the config screen.
        // No explicit icon (the television icon already marks the parent tv entry) - it auto-infers from the name,
        // which has no matching item, so it shows as a plain checkmark toggle.
        builder.comment("Turns off all the tv screen effects and draws it as a simple texture. Disabling can make the render slightly faster. All below options will be ignored if this is disabled");
        SCREEN_EFFECTS = builder.pushFeature("screen_effects");
        PIXEL_DENSITY = builder
                .comment("Pixel density of televisions, in pixels per block side")
                .define("pixel_density", 1.37f, 0.1f, 10);
        SCALE_PIXELS = builder.comment("Make connected tvs have higher pixel density, such that the per block pixel density is constant")
                .define("constant_pixel_density", true);
        VIGNETTE = builder
                .comment("Amount of vignette effect applied to television live feed (0 = none, 1 = full)")
                .define("vignette", 1f, 0f, 1f);
        TURN_OFF_EFFECTS = builder
                .comment("Plays an animation when the television is turned off or on")
                .define("turn_off_animation", true);
        builder.pop(); // screen_effects

        builder.icon("hollow_cassette").push("live_feed");
        UPDATE_FPS = builder
                .gameRestart()
                .comment("How many times per second the television updates its live feed texture. Lowering this will improve performance but make the video less smooth, fractions work too")
                .define("update_fps", 10.0, 1, 60);
        MIN_UPDATE_FPS = builder
                .gameRestart()
                .comment("The minimum update fps for live feed. The mod will throttle update rate when fps are low so this serves at a lower limit")
                .define("min_update_fps", 4.0, 1, 60); //once every 5 ticks
        THROTTLING_UPDATE_MS = builder
                .gameRestart()
                .comment("The maximum number of milliseconds all the logic for updating live feeds can take before fps throttling begins. Lowering this will improve performance but make the video less smooth. 16.66ms equals to 5fps")
                .define("throttling_update_ms", 16.66, 0, 1000000);

        UPDATE_DISTANCE = builder
                .comment("Distance from a TV after which the feed will update in real time")
                .define("update_distance", 24, 1, 512d);

        LIVE_FEED_RESOLUTION_SCALE = builder
                .comment("Scale factor for live feed resolution. A tv screen is 12x12 pixels, this number multiplies that area")
                .define("resolution_scale", 8, 1, 32);

        RENDER_DEBUG = builder
                .comment("Enables rendering of debug information for televisions")
                .define("render_debug", false);

        LINKED_FEED_DISPLAY_MODE = builder
                .comment("What a hollow cassette's tooltip reveals about its linked target once linked. HIDDEN: nothing is shown. COORDINATES: the exact block coordinates are shown. CIPHERED (default): a 5-letter word rendered in the enchanting table's font is shown instead, unique to that link but unreadable, so you can tell cassettes apart without leaking the actual location")
                .define("linked_feed_display_mode", LinkedFeedDisplayMode.CIPHERED);

        CompatHandler.addConfigs(builder);

        builder.pop(); // live_feed
        builder.pop(); // television

        builder.icon("wave_gate").push("wave_gate");
        VIDEO_ENGINE = CompatHandler.WATERMEDIA ? builder.comment("Toggle between local FFmpeg driven video loading and WaterMedia (VLC) mod usage. Requires Watermedia mod. FFmpeg mode has improved visuals and functionality, and likely supports more media types. Watermedia on the other hand supports youtube links. The first mode uses both, prioritizing our local FFmpeg impl and falling back to watermedia on media player links.")
                                                  .define("media_engine", EngineMode.TRY_FFMPEG_FIRST_THEN_VLC) : () -> EngineMode.TRY_FFMPEG_FIRST_THEN_VLC;
        ENABLE_FFMPEG = builder
                .icon("wave_gate")
                .comment("Enable FFmpeg use. This is needed if you want to use the Wave Gate")
                .define("ffmpeg_enabled", true);
        WEB_RESOLUTION_SCALE = builder
                .comment("Scale factor for web images resolution")
                .define("resolution_scale", 8, 1, 32);
        SCALING_MODE = builder
                .comment("Scaling mode for web images")
                .define("scaling_mode", ScalingMode.COVER);
        BILINEAR = builder.comment("Enable bilinear sampling for rescaled images. Enable for a less pixelated look")
                .define("bilinear_sampling", false);
        SAFE_URLS = builder.comment("A list of regex which will filter out valid URLs. At least one of these must match for a URL video to work")
                .define("safe_urls", List.of());
        builder.pop(); // wave_gate

        builder.onChange(() -> {
            List<String> elements = SAFE_URLS.get();
            if (elements.isEmpty()) {
                safeRegex = Pattern.compile(".*");
            } else {
                String combined = String.join("|", elements);
                safeRegex = Pattern.compile(combined);
            }
        });
        SPEC = builder.build();
        SPEC.forceLoad();
    }

    public static boolean rendersDebug() {
        return RENDER_DEBUG.get() || PlatHelper.isDev();
    }


    public static boolean canUseFFmpeg() {
        return ENABLE_FFMPEG.get();
    }

    public static boolean canUseWatermedia() {
        if (PlatHelper.isDev()) return true;
        return CompatHandler.WATERMEDIA && VIDEO_ENGINE.get() == EngineMode.USE_VLC;
    }

    public static void turnOffFFmpeg() {
        SPEC.manuallySetValue(ENABLE_FFMPEG, false);
    }

    public static boolean isSafeUrl(String input) {
        return safeRegex.matcher(input).find();
    }

    public enum EngineMode {
        TRY_FFMPEG_FIRST_THEN_VLC,
        USE_FFMPEG,
        USE_VLC
    }

    public enum MirrorUpdateMode {
        TEXTURE_REFRESH,
        RENDER_TICK_END
    }

    public enum MirrorRecursionMode {
        OFF,
        SHARED,
        RECURSIVE
    }

    public enum LinkedFeedDisplayMode {
        HIDDEN,
        COORDINATES,
        CIPHERED
    }
}
