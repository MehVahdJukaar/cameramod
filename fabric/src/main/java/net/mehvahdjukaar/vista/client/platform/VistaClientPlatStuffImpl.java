package net.mehvahdjukaar.vista.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.vista.client.web.audio.PcmAudioSource;
import net.mehvahdjukaar.vista.client.web.audio.TvSpeakerSoundInstance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.concurrent.CompletableFuture;

public class VistaClientPlatStuffImpl {

    public static void dispatchRenderStageAfterLevel(Minecraft mc, PoseStack poseStack, Camera camera,
                                                     Matrix4f modelViewMatrix, Matrix4f projMatrix) {
    }

    public static TvSpeakerSoundInstance createTvSpeakerSound(PcmAudioSource source, Vec3 pos, double startSeconds) {
        return new TvSpeakerSoundInstance(source, pos, startSeconds) {
            @Override
            public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, ResourceLocation id, boolean repeatInstantly) {
                return CompletableFuture.completedFuture(openStream());
            }
        };
    }
}
