package net.mehvahdjukaar.vista.client.textures.web;

import net.mehvahdjukaar.vista.client.web.IMediaSession;
import net.mehvahdjukaar.vista.client.web.MediaError;
import net.mehvahdjukaar.vista.client.web.MediaStatus;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public interface IWebTexture extends AutoCloseable {

    //same curve as vanilla linear attenuation
    int SPEAKER_RANGE = 16;

    ResourceLocation getTextureLocation();

    default void register() {
        Minecraft.getInstance().getTextureManager().register(this.getTextureLocation(), (AbstractTexture) this);
    }

    default void unregister() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        AbstractTexture texture = tm.getTexture(this.getTextureLocation());
        if (texture == this) {
            tm.release(this.getTextureLocation());
        }
    }

    IMediaSession getSession();

    MediaStatus uploadFrameAtTime(int ticks, float deltaTime, boolean paused);

    default void updateAudio(TVBlockEntity tv, boolean playing) {
    }

    default boolean isPlayingLoudAudio() {
        return false;
    }

    static double distanceToCamera(Vec3 pos) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(pos);
    }

    default int getDownloadProgress() {
        return getSession().getDownloadProgress();
    }

    default MediaError getError() {
        return getSession().getError();
    }

    default boolean isRetrying() {
        return getSession().isRetrying();
    }

    default boolean isAudioOnly() {
        return getSession().isAudioOnly();
    }
}
