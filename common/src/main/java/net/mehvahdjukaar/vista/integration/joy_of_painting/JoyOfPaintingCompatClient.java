package net.mehvahdjukaar.vista.integration.joy_of_painting;

import net.mehvahdjukaar.vista.client.ui.picture_tape.PictureTapeRenderers;

public class JoyOfPaintingCompatClient {

    public static void init() {
        PictureTapeRenderers.register(new CanvasTapeEntryRenderer());
    }
}
