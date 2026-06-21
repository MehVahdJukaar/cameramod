package net.mehvahdjukaar.vista.integration.distant_horizons;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;

import java.util.function.Supplier;

public class DistantHorizonsCompat {

    private static Supplier<DHMode> dhMode = () -> DHMode.OFF;

    public static Runnable decorateRenderWithoutLOD(Runnable task) {
        return () -> {
            // DH uses the camera position for LOD quality drop-off; with multiple cameras (TVs) that
            // produces stutters and inaccurate LODs, so fall back to the legacy player-position behavior.
            IDhApiConfigValue<Boolean> cameraPositionConfig = DhApi.Delayed.configs.graphics().useCameraPositionForQualityDropOff();
            Boolean cameraPositionBefore = cameraPositionConfig.getValue();

            DHMode mode = dhMode.get();

            try {
                cameraPositionConfig.setValue(false);

                if (mode != DHMode.OFF) {
                    var qualityConfig = DhApi.Delayed.configs.graphics().horizontalQuality();
                    var qualityBefore = qualityConfig.getValue();
                    try {
                        qualityConfig.setValue(mode.horizontal());
                        task.run();
                    } finally {
                        qualityConfig.setValue(qualityBefore);
                    }
                } else {
                    task.run();
                }
            } finally {
                cameraPositionConfig.setValue(cameraPositionBefore);
            }
        };
    }

    public static void addConfigs(ConfigBuilder builder) {
        dhMode = builder
                .comment("Distant Horizons compatibility lod render quality")
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
