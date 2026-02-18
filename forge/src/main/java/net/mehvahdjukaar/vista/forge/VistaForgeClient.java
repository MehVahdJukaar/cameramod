package net.mehvahdjukaar.vista.forge;

import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.client.renderer.FeedConnectionDebugRenderer;
import net.mehvahdjukaar.vista.client.textures.GifPathSpriteSource;
import net.mehvahdjukaar.vista.client.ui.ViewFinderHud;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class VistaForgeClient {


    @SubscribeEvent
    public static void onLevelLoaded(LevelEvent.Load event) {
        if(event.getLevel() instanceof ClientLevel cl){
            VistaModClient.onLevelLoaded(cl);
        }
    }
    @SubscribeEvent
    public static void onClientEndTick(TickEvent.ClientTickEvent.Post event) {
        VistaModClient.onClientTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onRenderTick(RenderFrameEvent.Post event) {
        VistaModClient.onRenderTickEnd(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onAddGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CAMERA_OVERLAYS, VistaMod.res("viewfinder"),
                ViewFinderHud.INSTANCE);
    }

    @SubscribeEvent
    public static void renderVistaDebug(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            if (ClientConfigs.rendersDebug()) {
                Vec3 camera = event.getCamera().getPosition();
                FeedConnectionDebugRenderer.INSTANCE.render(event.getPoseStack(),
                        Minecraft.getInstance().renderBuffers().bufferSource(),
                        camera.x, camera.y, camera.z);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiLayerEvent.Pre event) {
        if (ViewFinderController.isActive()) {
            ResourceLocation overlay = event.getName();
            if (overlay == (VanillaGuiLayers.EXPERIENCE_BAR) ||
                    overlay == (VanillaGuiLayers.EXPERIENCE_LEVEL) ||
                    overlay == (VanillaGuiLayers.HOTBAR)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void computeFOV(ComputeFovModifierEvent event) {
        float original = event.getFovModifier();
        float modified = event.getNewFovModifier();
        float newFOV = ViewFinderController.modifyFOV(original, modified, event.getPlayer());
        if (newFOV != modified) {
            event.setNewFovModifier(newFOV);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (ViewFinderController.onMouseScrolled(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
         VistaModClient.onClientDisconnect();
    }

    @SubscribeEvent
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && ViewFinderController.onPlayerAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isUseItem() && ViewFinderController.onPlayerUse()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
