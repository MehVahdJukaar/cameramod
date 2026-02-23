package net.mehvahdjukaar.vista.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.client.renderer.FeedConnectionDebugRenderer;
import net.mehvahdjukaar.vista.client.textures.GifPathSpriteSource;
import net.mehvahdjukaar.vista.client.ui.ViewFinderHud;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.mixins.fabric.SpriteSourcesAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

public class VistaFabricClient {

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(VistaModClient::onClientTick);
        ViewFinderHud hud = new ViewFinderHud();
        HudRenderCallback.EVENT.register(hud::render);

        /*
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((minecraft, clientLevel) -> {
            VistaModClient.onLevelLoaded(clientLevel);
        });*/

        ClientPlayConnectionEvents.DISCONNECT .register((clientPacketListener, minecraft) -> {
            VistaModClient.onClientDisconnect();
        });

        WorldRenderEvents.AFTER_ENTITIES.register(worldRenderContext -> {
            if (ClientConfigs.rendersDebug()) {
                Vec3 camera = worldRenderContext.camera().getPosition();
                FeedConnectionDebugRenderer.INSTANCE.render(worldRenderContext.matrixStack(),
                        Minecraft.getInstance().renderBuffers().bufferSource(),
                        camera.x, camera.y, camera.z);
            }
        });

        ClientPreAttackCallback.EVENT.register((minecraft, localPlayer, i) -> {
            if (ViewFinderController.onPlayerAttack()) {
                return true;
            }
            return false;
        });


        SpriteSourcesAccessor.getTYPES().put(VistaMod.res("directory_gifs"), GifPathSpriteSource.TYPE);


    }

}
