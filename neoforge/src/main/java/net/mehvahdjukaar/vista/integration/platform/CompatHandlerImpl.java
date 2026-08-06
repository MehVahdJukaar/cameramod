package net.mehvahdjukaar.vista.integration.platform;

import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.camera_mod.CameraModCompat;
import net.mehvahdjukaar.vista.integration.create.CreateCompat;

public class CompatHandlerImpl {
    public static void initPlat() {
        if (CompatHandler.CREATE) CreateCompat.init();
        if (CompatHandler.CAMERA_MOD) CameraModCompat.init();
    }

    public static void setupPlat() {
        if (CompatHandler.CREATE) CreateCompat.setup();
    }

}
