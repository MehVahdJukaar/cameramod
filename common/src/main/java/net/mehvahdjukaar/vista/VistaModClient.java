package net.mehvahdjukaar.vista;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.mehvahdjukaar.moonlight.api.client.CoreShaderContainer;
import net.mehvahdjukaar.moonlight.api.client.gui.ConfigScreenExtensions;
import net.mehvahdjukaar.moonlight.api.misc.EventCalled;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.client.VistaDynamicResources;
import net.mehvahdjukaar.vista.client.renderer.*;
import net.mehvahdjukaar.vista.client.textures.CassetteTexturesManager;
import net.mehvahdjukaar.vista.client.textures.LiveFeedTexturesManager;
import net.mehvahdjukaar.vista.client.textures.MirrorTextureManager;
import net.mehvahdjukaar.vista.client.textures.WebTexturesManager;
import net.mehvahdjukaar.vista.client.ui.*;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpeg;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpegManager;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;

public class VistaModClient {

    private static final ResourceLocation SHULKER_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/shulker_boxes.png");

    public static final CoreShaderContainer POSTERIZE_SHADER = new CoreShaderContainer(GameRenderer::getPositionTexColorShader);
    public static final CoreShaderContainer CAMERA_VIEW_SHADER = new CoreShaderContainer(GameRenderer::getRendertypeEntitySolidShader);
    public static final CoreShaderContainer MIRROR_MATERIAL_SHADER = new CoreShaderContainer(GameRenderer::getRendertypeEntitySolidShader);
    public static final CoreShaderContainer STATIC_SHADER = new CoreShaderContainer(GameRenderer::getPositionColorShader);
    /**
     * Own core shader rather than reusing {@code getPositionColorTexLightmapShader()}: Iris hardcodes that
     * particular vanilla shader as text rendering (same override hook as the rendertype_text shaders), so
     * anything else drawn with it gets Iris's block-entity-text gbuffer program instead of real geometry
     * shading and comes out black.
     */
    public static final CoreShaderContainer WAVE_GATE_SHADER = new CoreShaderContainer(GameRenderer::getPositionColorTexLightmapShader);

    public static final ModelLayerLocation VIEWFINDER_MODEL = loc("viewfinder");
    public static final Material WAVE_EFFECT = new Material(LOCATION_BLOCKS, VistaMod.res("block/wave_gate/wave"));

    public static final ResourceLocation MIRROR_UNDERLAY = VistaMod.res("textures/block/mirror/underlay.png");
    public static final ResourceLocation MIRROR_OVERLAY = VistaMod.res("textures/block/mirror/overlay.png");
    public static final ResourceLocation LL_OVERLAY = VistaMod.res("textures/cassette_tape/liveleak.png");
    public static final ResourceLocation PAUSE_OVERLAY = VistaMod.res("textures/cassette_tape/pause.png");
    public static final ResourceLocation PAUSE_PLAY_OVERLAY = VistaMod.res("textures/cassette_tape/pause_play.png");
    public static final ResourceLocation DISCONNECT_OVERLAY = VistaMod.res("textures/cassette_tape/disconnect.png");
    public static final ResourceLocation LOADING_OVERLAY = VistaMod.res("textures/cassette_tape/loading_dots.png");
    public static final ResourceLocation BLACK_LOADING_SCREEN = VistaMod.res("loading_dots_black");
    public static final ResourceLocation RETRYING_SCREEN = VistaMod.res("loading_dots_orange");

    public static final ResourceLocation BARS_SCREEN = VistaMod.res("color_bars");
    public static final ResourceLocation FORBIDDEN_SCREEN = VistaMod.res("forbidden");
    public static final ResourceLocation NOT_FOUND_SCREEN = VistaMod.res("not_found");
    public static final ResourceLocation BAD_LINK_SCREEN = VistaMod.res("bad_link");
    public static final ResourceLocation NO_ENERGY_SCREEN = VistaMod.res("no_power");
    public static final ResourceLocation SMILE_SCREEN = VistaMod.res("smile");
    public static final ResourceLocation NEUTRAL_SCREEN = VistaMod.res("neutral");
    public static final ResourceLocation SAD_SCREEN = VistaMod.res("sad");
    public static final ResourceLocation DOWNLOADING_SCREEN = VistaMod.res("download_bar");

    public static final ResourceLocation REFRESH_ICON = VistaMod.res("icon/refresh");

    public static final Material VIEW_FINDER_MATERIAL = new Material(SHULKER_SHEET,
            VistaMod.res("entity/view_finder/viewfinder"));

    public static final Function<Item, ResourceLocation> VIEW_FINDER_LENS_TEXTURES = Util.memoize(item -> {
        ResourceLocation id = Utils.getID(item);
        return VistaMod.res("textures/entity/view_finder/lenses/" + id.getPath() + ".png");
    });

    public static final Function<Item, @Nullable ResourceLocation> VIEW_FINDER_LENS_EMISSIVE_TEXTURES = Util.memoize(item -> {
        ResourceLocation id = Utils.getID(item);
        if (id.getPath().equals("spider_head") || id.getPath().equals("dragon_head") || id.getPath().equals("enderman_head")) {
            return VistaMod.res("textures/entity/view_finder/lenses/" + id.getPath() + "_emissive.png");
        }
        return null;
    });

    public static final ExtraChunkViewData CLIENT_EXTRA_CHUNK_VIEW_DATA = new ExtraChunkViewData();

    //hack since the resource key to level mapping isn't guaranteed 1:1. Don't even know if this will be used by any mods because in vanilla it isn't
    private static final Map<ResourceKey<Level>, Level> KNOWN_LEVELS_BY_DIMENSION = new MapMaker()
            .weakValues()
            .makeMap();

    @Nullable
    private static CompletableFuture<FFmpeg> ffmpegFuture;

    public static synchronized FFmpeg getFFmpeg() {
        if (ffmpegFuture == null) {
            return null;
        }
        if (ffmpegFuture.isCompletedExceptionally()) {
            return null;
        } else if (ffmpegFuture.isDone()) {
            return ffmpegFuture.resultNow();
        }
        // setup failed. we give up and have no ffmpeg for this game
        return null;
    }

    public static synchronized boolean isFFmpegDownloading() {
        return ffmpegFuture != null && !ffmpegFuture.isDone();
    }

    public static int getFFmpegDownloadProgress() {
        if (!isFFmpegDownloading()) {
            return -1;
        }
        return FFmpegManager.getDownloadProgress();
    }

    @Nullable
    public static synchronized CompletableFuture<FFmpeg> getFFmpegFuture() {
        return ffmpegFuture;
    }

    private static void instantiateFFmpeg(@Nullable String url) {
        ffmpegFuture = FFmpegManager.getOrDownload(url);
    }

    private static void useFFmpeg(FFmpeg ffmpeg) {
        ffmpegFuture = CompletableFuture.completedFuture(ffmpeg);
    }

    // Tries to make FFmpeg ready without any download: existing downloaded binaries,
    // or a system-wide install on the user's PATH. Returns true if FFmpeg is now ready.
    private static boolean tryReadyWithoutDownload() {
        if (FFmpegManager.hasRequiredFiles()) {
            instantiateFFmpeg(null);
            return true;
        }
        FFmpeg system = FFmpegManager.detectSystemFFmpeg();
        if (system != null) {
            useFFmpeg(system);
            return true;
        }
        return false;
    }

    private static ModelLayerLocation loc(String name) {
        return new ModelLayerLocation(VistaMod.res(name), name);
    }

    public static void init() {
        ClientConfigs.init();
        //init instantly if it's already available without downloading (local binaries or system PATH)
        if (ClientConfigs.canUseFFmpeg()) tryReadyWithoutDownload();

        ClientHelper.addBlockEntityRenderersRegistration(VistaModClient::registerBlockEntityRenderers);
        ClientHelper.addShaderRegistration(VistaModClient::registerShaders);
        ClientHelper.addModelLayerRegistration(VistaModClient::registerModelLayers);
        ClientHelper.addItemColorsRegistration(VistaModClient::registerItemColors);
        ClientHelper.addItemRenderersRegistration(VistaModClient::registerItemRenderers);
        ClientHelper.addMenuScreensRegistration(VistaModClient::registerMenuScreens);

        ClientHelper.addClientReloadListener(() -> CassetteTexturesManager.INSTANCE, VistaMod.res("gif_manager"));

        RegHelper.registerDynamicResourceProvider(new VistaDynamicResources());


        ClientHelper.addClientSetup(VistaModClient::onClientSetup);
    }

    private static void onClientSetup() {
        ItemProperties.register(VistaMod.WAVE_GATE.get().asItem(),
                VistaMod.res("creative"),
                (stack, world, entity, s) -> CommonConfigs.isWaveGateCraftable() ? 0 : 1
        );
        ConfigScreenExtensions.registerShowcase(VistaMod.MOD_ID, TvShowcaseWidget.SHOWCASE);
    }

    @EventCalled
    public static void onFirstScreen(Screen screen) {
        Screen newScreen = screen;
        if (ClientConfigs.canUseFFmpeg() && ffmpegFuture == null) {
            newScreen = new VistaWelcomeScreen(newScreen,
                    VistaModClient::instantiateFFmpeg,
                    () -> instantiateFFmpeg(null),
                    ClientConfigs::turnOffFFmpeg
            );
        }
        if (newScreen != screen) Minecraft.getInstance().setScreen(newScreen);
    }


    private static void registerItemRenderers(ClientHelper.ItemRendererEvent event) {
        event.register(VistaMod.TV_ITEM.get(), new TvItemRenderer());
    }

    private static void registerMenuScreens(ClientHelper.MenuScreenEvent event) {
        event.register(VistaMod.VIEWFINDER_MENU.get(), ViewFinderScreen::new);
        event.register(VistaMod.PICTURE_TAPE_MENU.get(), PictureTapeScreen::new);
    }

    private static void registerItemColors(ClientHelper.ItemColorEvent event) {
        event.register((itemStack, i) -> {
            if (i == 1) {
                var tape = itemStack.get(VistaMod.CASSETTE_TAPE_COMPONENT.get());
                if (tape == null) return -1;
                return tape.value().color();
            }
            return -1;
        }, VistaMod.CASSETTE.get());
    }

    private static void registerBlockEntityRenderers(ClientHelper.BlockEntityRendererEvent event) {
        event.register(VistaMod.TV_TILE.get(), TvBlockEntityRenderer::new);
        event.register(VistaMod.VIEWFINDER_TILE.get(), ViewFinderBlockEntityRenderer::new);
        event.register(VistaMod.WAVE_GATE_TILE.get(), WaveGateBlockEntityRenderer::new);
        event.register(VistaMod.MIRROR_TILE.get(), MirrorBlockEntityRenderer::new);
    }

    private static void registerShaders(ClientHelper.ShaderEvent event) {
        event.register(VistaMod.res("static_noise"), DefaultVertexFormat.NEW_ENTITY, STATIC_SHADER::assign);
        event.register(VistaMod.res("camera_view"), DefaultVertexFormat.NEW_ENTITY, CAMERA_VIEW_SHADER::assign);
        event.register(VistaMod.res("mirror_material"), DefaultVertexFormat.NEW_ENTITY, MIRROR_MATERIAL_SHADER::assign);
        event.register(VistaMod.res("wave_gate"), DefaultVertexFormat.NEW_ENTITY, WAVE_GATE_SHADER::assign);
    }

    private static void registerModelLayers(ClientHelper.ModelLayerEvent event) {
        event.register(VIEWFINDER_MODEL, ViewFinderBlockEntityRenderer::createMesh);
    }

    public static void onClientDisconnect() {
        LiveFeedTexturesManager.clear();
        WebTexturesManager.clear();
        MirrorTextureManager.clear();
        VistaLevelRenderer.clear();
        MapTapeEntryRenderer.clear();
    }

    public static void onClientTick(Minecraft minecraft) {
        if (minecraft.isPaused() || minecraft.level == null) return;

        Player p = minecraft.player;
        if (p == null) return;

        ViewFinderController.onClientTick(minecraft);
    }

    public static void onRenderTickEnd(Minecraft minecraft) {
        LiveFeedTexturesManager.onRenderTickEnd();
        // No-op when MIRROR_UPDATE_MODE = TEXTURE_REFRESH (queue stays empty).
        MirrorTextureManager.processPending();
    }


    private static boolean preventShiftTillNextKeyUp = false;

    public static void modifyInputUpdate(Input instance, LocalPlayer player) {
        if (ViewFinderController.isActive()) {
            ViewFinderController.onInputUpdate(instance);
            preventShiftTillNextKeyUp = true;
        } else if (preventShiftTillNextKeyUp) {
            if (!instance.shiftKeyDown) {
                preventShiftTillNextKeyUp = false;
            } else {
                instance.shiftKeyDown = false;
            }
        }
    }

    public static void onLevelUnloaded(ClientLevel cl) {
        KNOWN_LEVELS_BY_DIMENSION.remove(cl.dimension());
        LiveFeedTexturesManager.clear();

        CLIENT_EXTRA_CHUNK_VIEW_DATA.clearZones();
    }

    public static void onLevelLoaded(ClientLevel cl) {
        KNOWN_LEVELS_BY_DIMENSION.put(cl.dimension(), cl);
    }

    public static Level getLocalLevel() {
        return Minecraft.getInstance().level;
    }

    @Nullable
    public static Level getLocalLevelByDimension(ResourceKey<Level> dimension) {
        Level local = getLocalLevel();
        if (local != null && local.dimension() == dimension) {
            return local;
        }
        return KNOWN_LEVELS_BY_DIMENSION.get(dimension);
    }
}
