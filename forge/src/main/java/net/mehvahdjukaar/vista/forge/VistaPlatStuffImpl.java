package net.mehvahdjukaar.vista.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public class VistaPlatStuffImpl {
    public static void dispatchRenderStageAfterLevel(Minecraft mc, PoseStack poseStack, Camera camera,
                                                     Matrix4f modelViewMatrix, Matrix4f projMatrix) {
        mc.getProfiler().popPush("forge_render_last");
        ForgeHooksClient.dispatchRenderStage(RenderLevelStageEvent.Stage.AFTER_LEVEL,
                mc.levelRenderer, poseStack, projMatrix,
                mc.levelRenderer.getTicks(), camera,
                mc.levelRenderer.getFrustum());

        mc.getProfiler().pop();

    }
}
