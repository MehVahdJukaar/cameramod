package net.mehvahdjukaar.vista.client.web;

import net.mehvahdjukaar.vista.client.textures.IWebTexture;
import net.minecraft.resources.ResourceLocation;

//one instance per url
//can create multiple texture views
public interface IMediaSession extends AutoCloseable {

    IWebTexture createTextureView(ResourceLocation resourceLocation);

    boolean shouldRefreshTexture(IWebTexture tt);

    boolean isFailed();

    /**
     * Category of the failure when isFailed() is true, otherwise MediaError.NONE.
     */
    default MediaError getError() {
        return MediaError.NONE;
    }

    /**
     * True while a transient download failure is being retried (backoff in progress).
     */
    default boolean isRetrying() {
        return false;
    }

    default int getDownloadProgress() {
        return -1;
    }
}
