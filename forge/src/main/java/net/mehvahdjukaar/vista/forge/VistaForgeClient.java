package net.mehvahdjukaar.vista.forge;

import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.client.renderer.FeedConnectionDebugRenderer;
import net.mehvahdjukaar.vista.client.ui.ViewFinderHud;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class VistaForgeClient {


    @SubscribeEvent
    public static void onLevelLoaded(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel cl) {
            VistaModClient.onLevelLoaded(cl);
        }
    }


    @SubscribeEvent
    public static void onClientEndTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) VistaModClient.onClientTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) VistaModClient.onRenderTickEnd(Minecraft.getInstance());
    }

    public static void onAddGuiLayers(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.SPYGLASS.id(), "viewfinder", new Hud());
    }

    public static void init(IEventBus bus) {
        bus.addListener(VistaForgeClient::onAddGuiLayers);
    }


    public static class Hud extends ViewFinderHud implements IGuiOverlay {

        @Override
        public void render(ForgeGui forgeGui, GuiGraphics arg, float f, int i, int j) {
            this.render(arg, f);
        }
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
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (ViewFinderController.isActive()) {
            var overlay = event.getOverlay();
            if (overlay == (VanillaGuiOverlay.EXPERIENCE_BAR.type()) ||
                    overlay == (VanillaGuiOverlay.HOTBAR.type())) {
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
        if (ViewFinderController.onMouseScrolled(event.getScrollDelta())) {
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
