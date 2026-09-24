package net.mehvahdjukaar.vista.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.vista.client.web.audio.PcmAudioSource;
import net.mehvahdjukaar.vista.client.web.audio.TvSpeakerSoundInstance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.concurrent.CompletableFuture;

public class VistaClientPlatStuffImpl {

    public static void dispatchRenderStageAfterLevel(Minecraft mc, PoseStack poseStack, Camera camera,
                                                     Matrix4f modelViewMatrix, Matrix4f projMatrix) {
        mc.getProfiler().popPush("neoforge_render_last");

        ClientHooks.dispatchRenderStage(RenderLevelStageEvent.Stage.AFTER_LEVEL, mc.levelRenderer,
                null, modelViewMatrix, projMatrix, mc.levelRenderer.getTicks(),
                camera, mc.levelRenderer.getFrustum());
        mc.getProfiler().pop();
    }

    public static TvSpeakerSoundInstance createTvSpeakerSound(PcmAudioSource source, Vec3 pos, double startSeconds) {
        return new TvSpeakerSoundInstance(source, pos, startSeconds) {
            @Override
            public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
                return CompletableFuture.completedFuture(openStream());
            }
        };
    }
}
