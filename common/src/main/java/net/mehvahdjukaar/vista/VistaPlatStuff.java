package net.mehvahdjukaar.vista;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.candlelight.api.PlatformImpl;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Contract;
import org.joml.Matrix4f;

public class VistaPlatStuff {

    @Contract
    @PlatformImpl
    public static void dispatchRenderStageAfterLevel(Minecraft mc, PoseStack poseStack, Camera camera, Matrix4f modelViewMatrix, Matrix4f projMatrix) {
        throw new AssertionError();
    }

    @Contract
    @PlatformImpl
    public static void tickEnergy(TVBlockEntity tv) {
        throw new AssertionError();
    }
}
