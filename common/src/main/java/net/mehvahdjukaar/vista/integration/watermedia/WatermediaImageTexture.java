package net.mehvahdjukaar.vista.integration.watermedia;

import net.mehvahdjukaar.vista.client.textures.IWebTexture;
import net.mehvahdjukaar.vista.client.web.MediaStatus;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.watermedia.api.image.ImageCache;
import org.watermedia.api.image.ImageRenderer;
import java.io.IOException;
import java.util.concurrent.Executor;

public class WatermediaImageTexture extends AbstractTexture implements IWebTexture {

    private final ResourceLocation textureLocation;
    private final WatermediaSession session;
    private final ImageCache imageCache;

    public WatermediaImageTexture(ResourceLocation textureLocation, WatermediaSession session, ImageCache imageCache,
                                  int width, int height, Executor executor) {
        //TODO: figure out width and height
        this.session = session;
        this.imageCache = imageCache;
        this.textureLocation = textureLocation;
            //this.id = imageCache.getRenderer().texture(0, 0, true);
    }

    @Override
    public WatermediaSession getSession() {
        return session;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void releaseId() {
        // no-op: the ImageRenderer / ImageCache owns the GL texture lifetime, not Minecraft
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
    }

    @Override
    public void close() {
        // Image lifetime is managed by WatermediaSession / ImageCache.
        // We intentionally do not call releaseId() here.
        super.close();
    }

    @Override
    public MediaStatus uploadFrameAtTime(int ticks, float deltaTime, boolean paused) {
        ImageRenderer renderer = imageCache.getRenderer();
        ImageCache.Status status = imageCache.getStatus();
        if(status == ImageCache.Status.FAILED) return MediaStatus.FAILED;
        if (renderer == null) return MediaStatus.LOADING;
        if (renderer.textures.length == 0) return MediaStatus.LOADING;
        this.id = renderer.texture(ticks, deltaTime, true);
        return switch (status) {
            case READY -> MediaStatus.READY;
            case LOADING -> MediaStatus.BUFFERING;
            case FAILED -> MediaStatus.FAILED;
            case FORGOTTEN -> MediaStatus.CLOSED;
            case WAITING -> MediaStatus.LOADING;
        };
    }

}
