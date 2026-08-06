package net.mehvahdjukaar.vista.integration.camera_mod;

import net.mehvahdjukaar.vista.client.ui.PictureTapeRenderers;

public class CameraModCompatClient {

    public static void init() {
        PictureTapeRenderers.register(new CameraModPictureRenderer());
    }
}
