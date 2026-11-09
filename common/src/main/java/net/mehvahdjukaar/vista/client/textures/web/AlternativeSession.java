package net.mehvahdjukaar.vista.client.textures.web;

import net.mehvahdjukaar.vista.client.web.IMediaSession;
import net.mehvahdjukaar.vista.client.web.MediaError;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class AlternativeSession implements IMediaSession {

    private final Supplier<IMediaSession> fallbackFactory;
    @Nullable
    private IMediaSession primary;
    @Nullable
    private IMediaSession fallback;

    public AlternativeSession(Supplier<IMediaSession> primary, Supplier<IMediaSession> fallbackFactory) {
        this.primary = primary.get();
        this.fallbackFactory = fallbackFactory;
    }

    private IMediaSession getActiveSession() {
        if (fallback == null && primary.isFailed()) {
            primary.close();
            primary = null;
            fallback = fallbackFactory.get();
        }
        return fallback != null ? fallback : primary;
    }

    @Override
    public IWebTexture createTextureView(ResourceLocation resourceLocation) {
        return getActiveSession().createTextureView(resourceLocation);
    }

    @Override
    public boolean shouldRefreshTexture(IWebTexture texture) {
        IMediaSession activeSession = getActiveSession();
        if (activeSession != texture.getSession()) {
            return true;
        }
        return activeSession.shouldRefreshTexture(texture);
    }

    @Override
    public boolean isFailed() {
        return getActiveSession().isFailed();
    }

    @Override
    public MediaError getError() {
        return getActiveSession().getError();
    }

    @Override
    public boolean isRetrying() {
        return getActiveSession().isRetrying();
    }

    @Override
    public int getDownloadProgress() {
        return getActiveSession().getDownloadProgress();
    }

    @Override
    public boolean isAudioOnly() {
        return getActiveSession().isAudioOnly();
    }

    @Override
    public void close() {
        if (primary != null) primary.close();
        if (fallback != null) fallback.close();
    }
}
