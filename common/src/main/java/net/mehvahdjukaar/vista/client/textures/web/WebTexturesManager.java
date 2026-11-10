package net.mehvahdjukaar.vista.client.textures.web;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.web.FFmpegMediaSession;
import net.mehvahdjukaar.vista.client.web.IMediaSession;
import net.mehvahdjukaar.vista.client.web.MediaCacheManager;
import net.mehvahdjukaar.vista.client.web.UrlUtils;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.watermedia.WatermediaSession;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WebTexturesManager {
    private static final long DEFAULT_CACHE_SIZE_BYTES = 512L * 1024L * 1024L;
    private static final long CACHE_EXPIRY_MINUTES = 2L;


    private static final ExecutorService SESSION_LOADER_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "WebMediaLoader");
        thread.setDaemon(true);
        return thread;
    });

    private static final MediaCacheManager MEDIA_CACHE_MANAGER = new MediaCacheManager(
            PlatHelper.getGamePath(), DEFAULT_CACHE_SIZE_BYTES);

    private static final Cache<SessionKey, IMediaSession> SESSION_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
            .removalListener(notification -> {
                IMediaSession session = (IMediaSession) notification.getValue();
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception e) {
                        VistaMod.LOGGER.error("Failed to close media session", e);
                    }
                }
            })
            .build();

    private static final Cache<TextureKey, IWebTexture> TEXTURE_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
            .removalListener(notification -> {
                IWebTexture texture = (IWebTexture) notification.getValue();
                if (texture != null) {
                    RenderSystem.recordRenderCall(texture::unregister);
                }
            })
            .build();

    private static final AtomicInteger TEXTURE_ID_COUNTER = new AtomicInteger();

    //one texture per screen
    public static IWebTexture getTexture(URI uri, BlockPos tvPos, Vec2i screenSize) {
        IMediaSession session = SESSION_CACHE.asMap()
                .computeIfAbsent(new SessionKey(uri, screenSize.x(), screenSize.y()), key -> {
                    int imageW = ClientConfigs.WEB_RESOLUTION_SCALE.get() * screenSize.x();
                    int imageH = ClientConfigs.WEB_RESOLUTION_SCALE.get() * screenSize.y();
                    return createMediaSession(uri, imageW, imageH);
                });

        TextureKey textureKey = new TextureKey(uri, tvPos.immutable(), screenSize.x(), screenSize.y());
        IWebTexture wt = TEXTURE_CACHE.asMap()
                .computeIfAbsent(textureKey, key -> {
                    IWebTexture texture = session.createTextureView(VistaMod.res("web_feed/" + TEXTURE_ID_COUNTER.incrementAndGet()));
                    texture.register();
                    return texture;
                });
        if (session.shouldRefreshTexture(wt)) {
            TEXTURE_CACHE.invalidate(textureKey);
        }
        return wt;
    }

    @Nullable
    public static IWebTexture getTextureIfPresent(URI uri, BlockPos tvPos, Vec2i screenSize) {
        SESSION_CACHE.getIfPresent(new SessionKey(uri, screenSize.x(), screenSize.y())); //keeps session from expiring
        return TEXTURE_CACHE.getIfPresent(new TextureKey(uri, tvPos, screenSize.x(), screenSize.y()));
    }

    public static void tick() {
        for (IWebTexture texture : TEXTURE_CACHE.asMap().values()) {
            texture.tick();
        }
    }

    public static void clear() {
        TEXTURE_CACHE.invalidateAll();
        TEXTURE_CACHE.cleanUp();

        SESSION_CACHE.invalidateAll();
        SESSION_CACHE.cleanUp();
    }

    public static void invalidateUrl(String url) {
        URI uri = UrlUtils.createUri(url);
        if (uri == null) return;
        SESSION_CACHE.asMap().keySet().removeIf(k -> k.uri().equals(uri));
        SESSION_CACHE.cleanUp();

        TEXTURE_CACHE.asMap().keySet().removeIf(k -> k.uri().equals(uri));
        TEXTURE_CACHE.cleanUp();
    }

    public static IMediaSession createMediaSession(URI url, int width, int height) {
        ClientConfigs.EngineMode engine = ClientConfigs.VIDEO_ENGINE.get();
        if (CompatHandler.WATERMEDIA && engine != ClientConfigs.EngineMode.USE_FFMPEG) {
            if (engine == ClientConfigs.EngineMode.USE_VLC || !UrlUtils.hasFileExtension(url)) {
                return createWatermediaSession(url, width, height);
            } else {
                return new AlternativeSession(
                        () -> createFFmpegSession(url, width, height),
                        () -> createWatermediaSession(url, width, height)
                );
            }
        } else {
            return createFFmpegSession(url, width, height);
        }
    }

    private static WatermediaSession createWatermediaSession(URI uri, int width, int height) {
        return new WatermediaSession(uri, width, height);
    }

    private static FFmpegMediaSession createFFmpegSession(URI uri, int width, int height) {
        return new FFmpegMediaSession(uri, MEDIA_CACHE_MANAGER, SESSION_LOADER_EXECUTOR, width, height);
    }

    private record SessionKey(URI uri, int width, int height) {
    }

    private record TextureKey(URI uri, BlockPos tvPos, int width, int height) {
    }
}
