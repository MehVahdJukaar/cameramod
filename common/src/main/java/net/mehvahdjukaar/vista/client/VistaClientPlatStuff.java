package net.mehvahdjukaar.vista.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.candlelight.api.PlatformImpl;
import net.mehvahdjukaar.vista.client.web.audio.PcmAudioSource;
import net.mehvahdjukaar.vista.client.web.audio.TvSpeakerSoundInstance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.joml.Matrix4f;

public class VistaClientPlatStuff {

    @Contract
    @PlatformImpl
    public static void dispatchRenderStageAfterLevel(Minecraft mc, PoseStack poseStack, Camera camera, Matrix4f modelViewMatrix, Matrix4f projMatrix) {
        throw new AssertionError();
    }

    @Contract
    @PlatformImpl
    public static TvSpeakerSoundInstance createTvSpeakerSound(PcmAudioSource source, Vec3 pos, double startSeconds) {
        throw new AssertionError();
    }
}
