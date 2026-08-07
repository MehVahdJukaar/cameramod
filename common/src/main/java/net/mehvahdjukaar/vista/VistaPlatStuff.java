package net.mehvahdjukaar.vista;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.candlelight.api.PlatformImpl;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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

    // NeoForge block capabilities must be told when what they resolve to has changed. Reshaping a
    // connected block group can move the master block entity while some of the blocks keep the exact
    // same state, which alone would not invalidate the caches other mods hold on those positions.
    @Contract
    @PlatformImpl
    public static void invalidateBlockCapabilities(Level level, BlockPos pos) {
        throw new AssertionError();
    }
}
