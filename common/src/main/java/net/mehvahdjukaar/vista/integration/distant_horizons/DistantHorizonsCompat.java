package net.mehvahdjukaar.vista.integration.distant_horizons;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class DistantHorizonsCompat {

    private static Supplier<DHMode> dhMode = () -> DHMode.OFF;

    // These two are *global* DH settings, not per-render-pass state: setValue() installs an API override
    // that outranks whatever the user picked and greys the entry out in DH's own config screen until
    // clearValue() is called. So they affect the player's own view just as much as our feeds, and we must
    // hand them back rather than leaving DH permanently overridden.
    //  - horizontalQuality(): changing it forces DH to rebuild the entire LOD dataset (several seconds),
    //    so per-frame toggling is off the table (DH dev's advice). We only write it when the mode changes.
    //  - useCameraPositionForQualityDropOff(): DH uses the camera position for LOD quality drop-off; with
    //    multiple cameras (TVs) that produces stutters and inaccurate LODs, so we fall back to the legacy
    //    player-position behavior.
    @Nullable
    private static DHMode appliedMode = null;

    public static Runnable decorateRenderWithoutLOD(Runnable task) {
        return () -> {
            DHMode mode = dhMode.get();
            if (mode == DHMode.OFF) {
                releaseConfigOverrides();
            } else if (mode != appliedMode) {
                DhApi.Delayed.configs.graphics().useCameraPositionForQualityDropOff().setValue(false);
                DhApi.Delayed.configs.graphics().horizontalQuality().setValue(mode.horizontal());
                appliedMode = mode;
            }

            task.run();
        };
    }

    /**
     * Gives the user back control of the DH settings we overrode. Must run whenever the compat stops
     * applying, otherwise those entries stay locked in DH's config screen for the rest of the session.
     */
    public static void releaseConfigOverrides() {
        if (appliedMode == null) return;
        appliedMode = null;
        DhApi.Delayed.configs.graphics().useCameraPositionForQualityDropOff().clearValue();
        DhApi.Delayed.configs.graphics().horizontalQuality().clearValue();
    }

    public static void addConfigs(ConfigBuilder builder) {
        dhMode = builder
                .comment("Distant Horizons compatibility lod render quality. Note that this overrides DH's own " +
                        "horizontal quality setting globally, so it affects your normal view too")
                .define("distant_horizons_LOD", DHMode.OFF);
    }

    private enum DHMode {
        OFF,
        LOW,
        MED,
        HIGH;

        public EDhApiHorizontalQuality horizontal() {
            return switch (this) {
                case OFF -> EDhApiHorizontalQuality.LOWEST;
                case LOW -> EDhApiHorizontalQuality.LOWEST;
                case MED -> EDhApiHorizontalQuality.LOW;
                case HIGH -> EDhApiHorizontalQuality.MEDIUM;
            };
        }
    }
}
