package net.mehvahdjukaar.vista.integration.modmenu.platform;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.mehvahdjukaar.vista.VistaMod;

public class ModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> ClientHelper.getMoonlightConfigScreen(VistaMod.MOD_ID, parent, null);
    }
}
