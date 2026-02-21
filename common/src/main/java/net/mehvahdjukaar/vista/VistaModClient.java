package net.mehvahdjukaar.vista;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.mehvahdjukaar.ml_classes.DynamicTextureRenderer;
import net.mehvahdjukaar.moonlight.api.client.CoreShaderContainer;
import net.mehvahdjukaar.moonlight.api.misc.EventCalled;
import net.mehvahdjukaar.moonlight.api.platform.ClientHelper;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.client.VistaDynamicResources;
import net.mehvahdjukaar.vista.client.renderer.TvBlockEntityRenderer;
import net.mehvahdjukaar.vista.client.renderer.ViewFinderBlockEntityRenderer;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.client.textures.CassetteTexturesManager;
import net.mehvahdjukaar.vista.client.textures.LiveFeedTexturesManager;
import net.mehvahdjukaar.vista.common.cassette.CassetteItem;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public class VistaModClient {

    private static final ResourceLocation SHULKER_SHEET = new ResourceLocation("textures/atlas/shulker_boxes.png");

    public static final CoreShaderContainer POSTERIZE_SHADER = new CoreShaderContainer(GameRenderer::getPositionTexColorShader);
    public static final CoreShaderContainer CAMERA_VIEW_SHADER = new CoreShaderContainer(GameRenderer::getRendertypeEntitySolidShader);
    public static final CoreShaderContainer STATIC_SHADER = new CoreShaderContainer(GameRenderer::getPositionColorShader);

    public static final ModelLayerLocation VIEWFINDER_MODEL = loc("viewfinder");

    public static final ResourceLocation LL_OVERLAY = VistaMod.res("textures/cassette_tape/liveleak.png");
    public static final ResourceLocation PAUSE_OVERLAY = VistaMod.res("textures/cassette_tape/pause.png");
    public static final ResourceLocation PAUSE_PLAY_OVERLAY = VistaMod.res("textures/cassette_tape/pause_play.png");
    public static final ResourceLocation DISCONNECT_OVERLAY = VistaMod.res("textures/cassette_tape/disconnect.png");
    public static final ResourceLocation LOADING_OVERLAY = VistaMod.res("textures/cassette_tape/loading.png");

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


    //hack since resource key to level mapping isn't guaranteed 1:1. Don't even know if this will be used by any mods because in vanilla it isn't
    private static final Map<ResourceKey<Level>, Level> KNOWN_LEVELS_BY_DIMENSION = new MapMaker()
            .weakValues()
            .makeMap();


    private static ModelLayerLocation loc(String name) {
        return new ModelLayerLocation(VistaMod.res(name), name);
    }

    public static void init() {
        ClientConfigs.init();
        ClientHelper.addBlockEntityRenderersRegistration(VistaModClient::registerBlockEntityRenderers);
        ClientHelper.addShaderRegistration(VistaModClient::registerShaders);
        ClientHelper.addModelLayerRegistration(VistaModClient::registerModelLayers);
        ClientHelper.addItemColorsRegistration(VistaModClient::registerItemColors);

        ClientHelper.addClientReloadListener(() -> CassetteTexturesManager.INSTANCE, VistaMod.res("gif_manager"));

        new VistaDynamicResources().register();
    }

    private static void registerItemColors(ClientHelper.ItemColorEvent event) {
        event.register((itemStack, i) -> {
            if (i == 1) {
                var tape = CassetteItem.getCassette(itemStack);
                if (tape == null) return -1;
                return tape.value().color();
            }
            return -1;
        }, VistaMod.CASSETTE.get());
    }

    private static void registerBlockEntityRenderers(ClientHelper.BlockEntityRendererEvent event) {
        event.register(VistaMod.TV_TILE.get(), TvBlockEntityRenderer::new);
        event.register(VistaMod.VIEWFINDER_TILE.get(), ViewFinderBlockEntityRenderer::new);
    }

    private static void registerShaders(ClientHelper.ShaderEvent event) {
        event.register(VistaMod.res("static_noise"), DefaultVertexFormat.NEW_ENTITY, STATIC_SHADER::assign);
        event.register(VistaMod.res("camera_view"), DefaultVertexFormat.NEW_ENTITY, CAMERA_VIEW_SHADER::assign);
    }

    private static void registerModelLayers(ClientHelper.ModelLayerEvent event) {
        event.register(VIEWFINDER_MODEL, ViewFinderBlockEntityRenderer::createMesh);
    }

    public static void onClientDisconnect() {
        LiveFeedTexturesManager.clear();
        VistaLevelRenderer.clear();

        DynamicTextureRenderer.clearCache();
    }

    @EventCalled
    public static void onClientTick(Minecraft minecraft) {
        if (minecraft.isPaused() || minecraft.level == null) return;

        Player p = minecraft.player;
        if (p == null) return;

        ViewFinderController.onClientTick(minecraft);
    }

    @EventCalled
    public static void onRenderTickEnd(Minecraft minecraft) {
        LiveFeedTexturesManager.onRenderTickEnd();
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
