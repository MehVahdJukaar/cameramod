package net.mehvahdjukaar.vista.integration.modmenu.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.mehvahdjukaar.moonlight.api.client.gui.LinkButton;
import net.mehvahdjukaar.moonlight.api.platform.configs.fabric.FabricConfigListScreen;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.TextAndImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModConfigSelectScreen::new;
    }

    public static class ModConfigSelectScreen extends FabricConfigListScreen {

        public ModConfigSelectScreen(Screen parent) {
            super(VistaMod.MOD_ID, VistaMod.TV_ITEM.get().getDefaultInstance(),
                    Component.literal("§6MmmMmmMmmMmm Configs"), new ResourceLocation("textures/block/hay_block_side.png"),
                    parent, ClientConfigs.SPEC, CommonConfigs.SPEC);
        }

        @Override
        protected void addExtraButtons() {

            int y = this.height - 27;
            int centerX = this.width / 2;

            this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, (button) -> this.minecraft.setScreen(this.parent))
                    .bounds(centerX - 45, y, 90, 20).build());

            TextAndImageButton patreon = LinkButton.create(this, centerX - 45 - 22, y, 3, 1,
                    "https://www.patreon.com/user?u=53696377", "Support me on Patreon :D");

            TextAndImageButton kofi = LinkButton.create(this, centerX - 45 - 22 * 2, y, 2, 2,
                    "https://ko-fi.com/mehvahdjukaar", "Donate a Coffee");

            TextAndImageButton curseforge = LinkButton.create(this, centerX - 45 - 22 * 3, y, 1, 2,
                    "https://www.curseforge.com/minecraft/mc-mods/vista", "CurseForge Page");

            TextAndImageButton github = LinkButton.create(this, centerX - 45 - 22 * 4, y, 0, 2,
                    "https://github.com/MehVahdJukaar/cameramod/wiki", "Mod Wiki");


            TextAndImageButton discord = LinkButton.create(this, centerX + 45 + 2, y, 1, 1,
                    "https://discord.com/invite/qdKRTDf8Cv", "Mod Discord");

            TextAndImageButton youtube = LinkButton.create(this, centerX + 45 + 2 + 22, y, 0, 1,
                    "https://www.youtube.com/watch?v=LSPNAtAEn28&t=1s", "Youtube Channel");

            TextAndImageButton twitter = LinkButton.create(this, centerX + 45 + 2 + 22 * 2, y, 2, 1,
                    "https://twitter.com/Supplementariez?s=09", "Twitter Page");

            TextAndImageButton akliz = LinkButton.create(this, centerX + 45 + 2 + 22 * 3, y, 3, 2,
                    "https://www.akliz.net/supplementaries", "Need a server? Get one with Akliz");


            this.addRenderableWidget(kofi);
            this.addRenderableWidget(akliz);
            this.addRenderableWidget(patreon);
            this.addRenderableWidget(curseforge);
            this.addRenderableWidget(discord);
            this.addRenderableWidget(youtube);
            this.addRenderableWidget(github);
            this.addRenderableWidget(twitter);
        }

    }
}