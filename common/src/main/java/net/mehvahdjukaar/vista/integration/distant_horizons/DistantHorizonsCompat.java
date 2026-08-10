package net.mehvahdjukaar.vista.integration.distant_horizons;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class DistantHorizonsCompat {

    private static Supplier<DHMode> dhMode = () -> DHMode.OFF;

    // setValue() isn't a setter, it installs a global API override that outranks the user's own DH config
    // and greys the entry out in DH's config screen until clearValue(). So these apply to the player's view
    // too and must be released. Writing horizontalQuality also forces a full LOD rebuild (several seconds),
    // hence only on mode change rather than per frame.
    @Nullable
    private static DHMode appliedMode = null;

    public static void setup() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new SkipLodsInFeeds());
    }

    public static Runnable decorateRenderWithoutLOD(Runnable task) {
        return () -> {
            DHMode mode = dhMode.get();
            if (mode.horizontal() == null) {
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
                .comment("""
                        How Distant Horizons LODs are handled inside TV feeds and mirrors.
                        OFF: compat does nothing, feeds render LODs just like your own view
                        NO_LODS: no LODs at all in feeds
                        LOW/MED/HIGH: lower the LOD quality drop-off. Careful, this is a global DH setting,
                        so it lowers the quality of your own view too for as long as it's applied""")
                .define("distant_horizons_LOD", DHMode.OFF);
    }

    // DH fires this once per render pass and lets listeners cancel it, so unlike the quality configs this
    // is genuinely per-pass and leaves nothing behind.
    private static class SkipLodsInFeeds extends DhApiBeforeRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (dhMode.get() == DHMode.NO_LODS && VistaLevelRenderer.isRenderingLiveFeed()) {
                event.cancelEvent();
            }
        }
    }

    private enum DHMode {
        OFF,
        NO_LODS,
        LOW,
        MED,
        HIGH;

        // null means we don't touch DH's quality at all
        @Nullable
        public EDhApiHorizontalQuality horizontal() {
            return switch (this) {
                case OFF, NO_LODS -> null;
                case LOW -> EDhApiHorizontalQuality.LOWEST;
                case MED -> EDhApiHorizontalQuality.LOW;
                case HIGH -> EDhApiHorizontalQuality.MEDIUM;
            };
        }
    }
}
