package net.mehvahdjukaar.vista.integration.watermedia;

import net.mehvahdjukaar.vista.client.textures.web.IWebTexture;
import net.mehvahdjukaar.vista.client.web.IMediaSession;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.watermedia.api.player.MRL;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.player.PlayerAPI;

import java.net.URI;

public class WatermediaSession implements IMediaSession {

    private final MRL mrl;
    private final int targetWidth;
    private final int targetHeight;
    private volatile boolean closed;

    public WatermediaSession(URI uri, int targetWidth, int targetHeight) {
        this.mrl = PlayerAPI.mrl(uri);
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
    }

    @Override
    public IWebTexture createTextureView(ResourceLocation resourceLocation) {
        return new WatermediaTexture(resourceLocation, this);
    }

    public boolean isFetching() {
        return mrl.status() == MRL.Status.FETCHING;
    }

    //render thread only
    @Nullable
    public MediaPlayer createPlayer() {
        MediaPlayer player = PlayerAPI.player(mrl, () -> PlayerAPI.glEngine(Thread.currentThread(), Minecraft.getInstance()), PlayerAPI::alEngine);
        if (player == null) return null;
        player.scale(targetWidth, targetHeight);
        player.repeat(true);
        player.start();
        return player;
    }

    @Override
    public boolean shouldRefreshTexture(IWebTexture tt) {
        return false;
    }

    @Override
    public boolean isFailed() {
        return mrl.status().failed();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
