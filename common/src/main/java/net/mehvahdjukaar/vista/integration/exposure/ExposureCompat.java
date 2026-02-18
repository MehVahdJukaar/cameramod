package net.mehvahdjukaar.vista.integration.exposure;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;

public class ExposureCompat {

    public static void init() {

        if (PlatHelper.getPhysicalSide().isClient()) {
            ExposureCompatClient.init();
        }
    }


}
