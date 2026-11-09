package net.mehvahdjukaar.vista.client.web;

import net.mehvahdjukaar.vista.client.textures.web.IWebTexture;
import net.minecraft.resources.ResourceLocation;

//one instance per url
//can create multiple texture views
public interface IMediaSession extends AutoCloseable {

    IWebTexture createTextureView(ResourceLocation resourceLocation);

    boolean shouldRefreshTexture(IWebTexture tt);

    boolean isFailed();

    default MediaError getError() {
        return MediaError.NONE;
    }

    default boolean isRetrying() {
        return false;
    }

    default int getDownloadProgress() {
        return -1;
    }

    default boolean isAudioOnly() {
        return false;
    }

    @Override
    void close();
}
