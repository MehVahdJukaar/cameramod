package net.mehvahdjukaar.vista.client.textures;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.web.FFmpegMediaSession;
import net.mehvahdjukaar.vista.client.web.IMediaSession;
import net.mehvahdjukaar.vista.client.web.MediaCacheManager;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpeg;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.watermedia.WatermediaSession;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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

    private static final LoadingCache<String, IMediaSession> SESSION_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
            .removalListener(notification -> {
                IMediaSession session = (IMediaSession) notification.getValue();
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            })
            .build(new CacheLoader<>() {
                @Override
                public IMediaSession load(String key) {
                    throw new IllegalStateException("not supported!");
                }
            });

    private static final Map<URI, Set<String>> URL_TO_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<URI, Set<ResourceLocation>> URL_TO_TEXTURES = new ConcurrentHashMap<>();

    private static final LoadingCache<ResourceLocation, IWebTexture> TEXTURE_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
            .removalListener(notification -> {
                IWebTexture texture = (IWebTexture) notification.getValue();
                if (texture != null) {
                    RenderSystem.recordRenderCall(texture::unregister);
                }
            })
            .build(new CacheLoader<>() {
                @Override
                public IWebTexture load(ResourceLocation key) {
                    throw new IllegalStateException("not supported!");
                }
            });

    public static class Handle {

        private final ResourceLocation textureId;
        private final String sessionId;
        private final URI uri;
        private final Vec2i screenSize;

        public Handle(URI uri, UUID id, Vec2i screenSize) {
            this.textureId = makeUniqueTextureLoc(uri, id, screenSize);
            this.sessionId = makeUniqueSessionLoc(uri, screenSize);
            this.uri = uri;
            this.screenSize = screenSize;

            URL_TO_TEXTURES.computeIfAbsent(uri, k -> ConcurrentHashMap.newKeySet()).add(this.textureId);
            URL_TO_SESSIONS.computeIfAbsent(uri, k -> ConcurrentHashMap.newKeySet()).add(this.sessionId);
        }

        public IWebTexture getTexture() {
            //refresh sessions first for loading cache.

            IMediaSession session = getSession();

            IWebTexture wt = TEXTURE_CACHE.asMap()
                    .computeIfAbsent(textureId,
                            resourceLocation -> {
                                IWebTexture texture = session.createTextureView(resourceLocation);
                                texture.register();
                                return texture;
                            });
            if (session.shouldRefreshTexture(wt)) {
                TEXTURE_CACHE.invalidate(textureId);
            }
            return wt;
        }

        private IMediaSession getSession() {
            return SESSION_CACHE.asMap()
                    .computeIfAbsent(sessionId, res -> {
                        int imageW = ClientConfigs.WEB_RESOLUTION_SCALE.get() * screenSize.x();
                        int imageH = ClientConfigs.WEB_RESOLUTION_SCALE.get() * screenSize.y();
                        return createMediaSession(uri, imageW, imageH);
                    });
        }
    }


    public static Handle createHandle(URI url, UUID projectorUUID, Vec2i screenSize) {
        return new Handle(url, projectorUUID, screenSize);
    }

    public static void clear() {
        TEXTURE_CACHE.invalidateAll();
        TEXTURE_CACHE.cleanUp();

        SESSION_CACHE.invalidateAll();
        SESSION_CACHE.cleanUp();

        URL_TO_SESSIONS.clear();
        URL_TO_TEXTURES.clear();
    }

    public static void invalidateUrl(String url) {
        Set<String> sessionKeys = URL_TO_SESSIONS.remove(url);
        if (sessionKeys != null) {
            for (String key : sessionKeys) {
                SESSION_CACHE.invalidate(key);
            }
        }
        SESSION_CACHE.cleanUp();

        Set<ResourceLocation> textureKeys = URL_TO_TEXTURES.remove(url);
        if (textureKeys != null) {
            for (ResourceLocation key : textureKeys) {
                TEXTURE_CACHE.invalidate(key);
            }
        }
        TEXTURE_CACHE.cleanUp();
    }

    private static String makeUniqueSessionLoc(URI url, Vec2i screenSize) {
        return url.toString() + "@" + screenSize.x() + "x" + screenSize.y();
    }

    private static ResourceLocation makeUniqueTextureLoc(URI url, UUID uuid, Vec2i screenSize) {
        String uniqueTextureKey = url.toString() + "@" + uuid + "@" + screenSize.x() + "x" + screenSize.y();
        return VistaMod.res("web_feed/" + sanitizePath(uniqueTextureKey));
    }

    private static String sanitizePath(String key) {
        String sanitized = key.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");

        return sanitized.isBlank() ? "unnamed" : sanitized;
    }


    public static IMediaSession createMediaSession(URI url, int width, int height) {
        ClientConfigs.EngineMode engine = ClientConfigs.VIDEO_ENGINE.get();
        FFmpeg fFmpeg = VistaModClient.getFFmpeg();
        if (CompatHandler.WATERMEDIA && engine != ClientConfigs.EngineMode.USE_FFMPEG) {
            if (engine == ClientConfigs.EngineMode.USE_VLC || !looksLikeMedia(url)) {
                return createWatermediaSession(url, width, height);
            } else {
                return new AlternativeSession(
                        () -> createFFmpegSession(url, width, height, fFmpeg),
                        () -> createWatermediaSession(url, width, height)
                );
            }
        } else {
            return createFFmpegSession(url, width, height, fFmpeg);
        }
    }

    private static @NotNull WatermediaSession createWatermediaSession(URI uri, int width, int height) {
        return new WatermediaSession(uri, SESSION_LOADER_EXECUTOR, width, height);
    }

    private static @NotNull FFmpegMediaSession createFFmpegSession(URI uri, int width, int height, FFmpeg fFmpeg) {
        return new FFmpegMediaSession(uri, fFmpeg, MEDIA_CACHE_MANAGER, SESSION_LOADER_EXECUTOR, width, height);
    }

    private static final Pattern MEDIA_EXT = Pattern.compile("\\.[a-zA-Z0-9]+(?:\\?.*)?$");

    public static boolean looksLikeMedia(URI url) {
        return MEDIA_EXT.matcher(url.toString()).find();
    }
}
