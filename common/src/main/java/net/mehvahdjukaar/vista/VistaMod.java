package net.mehvahdjukaar.vista;

import com.mojang.serialization.Codec;
import net.mehvahdjukaar.moonlight.api.misc.*;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.vista.common.ModLootOverrides;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastLocationType;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.broadcast.LevelBEBroadcastLocation;
import net.mehvahdjukaar.vista.common.cassette.CassetteItem;
import net.mehvahdjukaar.vista.common.cassette.CassetteTape;
import net.mehvahdjukaar.vista.common.cassette.CassetteTapeLootFunction;
import net.mehvahdjukaar.vista.common.cassette.HollowCassetteItem;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.mehvahdjukaar.vista.common.chunk_tracking.ServerCameraChunkManager;
import net.mehvahdjukaar.vista.common.chunk_tracking.ServerExtraChunkViewData;
import net.mehvahdjukaar.vista.common.enderman.AngeredFromTvCondition;
import net.mehvahdjukaar.vista.common.enderman.EndermanFreezeWhenLookedAtThroughTVGoal;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlock;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlockEntity;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeContent;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeItem;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeMenu;
import net.mehvahdjukaar.vista.common.tv.TVBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.tv.TVItem;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlock;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderMenu;
import net.mehvahdjukaar.vista.common.wave_gate.WaveGateBlock;
import net.mehvahdjukaar.vista.common.wave_gate.WaveGateBlockEntity;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.supplementaries.SuppCompat;
import net.mehvahdjukaar.vista.network.ClientBoundSyncExtraChunksPacket;
import net.mehvahdjukaar.vista.network.ModNetwork;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;


public class VistaMod {
    public static final String MOD_ID = "vista";
    public static final Logger LOGGER = LogManager.getLogger("Vista");

    public static ResourceLocation res(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }


    //not synced. no need to sync to have all players synced. we just send to each players their own, not the others. hence we handle syncing manually
    //just serves as a way to store stuff on server since server needs to send chunks to players
    public static final IAttachmentType<ServerExtraChunkViewData, ServerPlayer> EXTRA_VIEW_AREAS =
            RegHelper.registerDataAttachment(res("camera_chunks"),
                    () -> RegHelper.AttachmentBuilder.create(ServerExtraChunkViewData::new),
                    ServerPlayer.class);

    public static final ResourceKey<Registry<CassetteTape>> CASSETTE_TAPE_REGISTRY_KEY =
            RegHelper.registerDataPackRegistry(res("cassette_tape"),
                    CassetteTape.DIRECT_CODEC, CassetteTape.DIRECT_CODEC);

    public static final Registry<BroadcastLocationType> BROADCAST_LOCATION_REGISTRY =
            RegHelper.registerRegistry(res("broadcast_location"), true);

    public static final Supplier<BroadcastLocationType> LEVEL_BE_BROADCAST =
            RegHelper.register(res("level_be_location"),
                    () -> LevelBEBroadcastLocation.TYPE, BROADCAST_LOCATION_REGISTRY.key());

    // view finders broadcasting from inside a Create contraption: registered from :neoforge only, since Create
    // has no Fabric build for this Minecraft version (see integration.create.CreateCompat there)

    public static final WorldSavedDataType<BroadcastManager> VIEWFINDER_CONNECTION =
            RegHelper.registerWorldSavedData(res("viewfinder_connection"), BroadcastManager::create,
                    () -> BroadcastManager.CODEC, () -> BroadcastManager.STREAM_CODEC, false);

    public static final ResourceLocation CINEMA_ADVANCEMENT = res("absolute_cinema");

    public static final Supplier<Block> TV = RegHelper.registerBlock(res("television"),
            () -> new TVBlock(Block.Properties.of()
                    .sound(SoundType.WOOD)
                    .isRedstoneConductor((blockState, blockGetter, blockPos) -> false)
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(1.5f)));

    public static final Supplier<BlockItem> TV_ITEM = RegHelper.registerItem(res("television"),
            () -> new TVItem(TV.get(), new Item.Properties()));

    public final static Supplier<BlockEntityType<TVBlockEntity>> TV_TILE = RegHelper.registerBlockEntityType(
            res("television"), TVBlockEntity::new, TV);

    public static final Supplier<Block> VIEWFINDER = RegHelper.registerBlockWithItem(res("viewfinder"),
            () -> new ViewFinderBlock(Block.Properties.of()
                    .strength(1.5f)
                    .isRedstoneConductor((blockState, blockGetter, blockPos) -> false)
                    .forceSolidOff()
                    .noOcclusion()));

    public static final Supplier<BlockEntityType<ViewFinderBlockEntity>> VIEWFINDER_TILE = RegHelper.registerBlockEntityType(
            res("viewfinder"), ViewFinderBlockEntity::new, VIEWFINDER);

    public static final Supplier<MenuType<ViewFinderMenu>> VIEWFINDER_MENU = RegHelper.registerMenuType(
            res("viewfinder"), ViewFinderMenu::create);

    public static final Supplier<Block> MIRROR = RegHelper.registerBlockWithItem(res("mirror"),
            () -> new MirrorBlock(Block.Properties.of()
                    .sound(SoundType.GLASS)
                    .mapColor(MapColor.METAL)
                    .strength(0.3f)
                    .noOcclusion()), new Item.Properties().rarity(Rarity.RARE));

    public static final Supplier<BlockEntityType<MirrorBlockEntity>> MIRROR_TILE = RegHelper.registerBlockEntityType(
            res("mirror"), MirrorBlockEntity::new, MIRROR);

    public static final RegSupplier<WaveGateBlock> WAVE_GATE =
            RegHelper.registerBlockWithItem(VistaMod.res("wave_gate"), //wideband reciver, wideband listener, signal harvester
                    () -> new WaveGateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COBBLESTONE)));

    public static final RegSupplier<BlockEntityType<WaveGateBlockEntity>> WAVE_GATE_TILE =
            RegHelper.registerBlockEntityType(VistaMod.res("wave_gate"),
                    WaveGateBlockEntity::new,
                    WAVE_GATE);

    public static final Supplier<CassetteItem> CASSETTE = RegHelper.registerItem(res("cassette"),
            () -> new CassetteItem(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .stacksTo(1)));

    public static final Supplier<HollowCassetteItem> HOLLOW_CASSETTE = RegHelper.registerItem(res("hollow_cassette"),
            () -> new HollowCassetteItem(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .stacksTo(1)));

    public static final Supplier<DataComponentType<PictureTapeContent>> PICTURE_TAPE_CONTENT = RegHelper.registerDataComponent(
            res("picture_tape"), () ->
                    DataComponentType.<PictureTapeContent>builder()
                            .persistent(PictureTapeContent.CODEC)
                            .networkSynchronized(PictureTapeContent.STREAM_CODEC)
                            .build());

    public static final Supplier<PictureTapeItem> PICTURE_TAPE = RegHelper.registerItem(res("picture_tape"),
            () -> new PictureTapeItem(new Item.Properties()
                    .stacksTo(1)
                    .component(PICTURE_TAPE_CONTENT.get(), PictureTapeContent.EMPTY)));

    public static final Supplier<MenuType<PictureTapeMenu>> PICTURE_TAPE_MENU = RegHelper.registerMenuType(
            res("picture_tape"), PictureTapeMenu::fromBuffer);


    public static final Supplier<DataComponentType<UUID>> LINKED_FEED_COMPONENT = RegHelper.registerDataComponent(
            res("linked_feed"), () ->
                    DataComponentType.<UUID>builder()
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC)
                            .build());

    public static final Supplier<DataComponentType<Holder<CassetteTape>>> CASSETTE_TAPE_COMPONENT = RegHelper.registerDataComponent(
            res("cassette_tape"), () ->
                    DataComponentType.<Holder<CassetteTape>>builder()
                            .persistent(RegistryFileCodec.create(VistaMod.CASSETTE_TAPE_REGISTRY_KEY, CassetteTape.DIRECT_CODEC))
                            .networkSynchronized(ByteBufCodecs.holderRegistry(VistaMod.CASSETTE_TAPE_REGISTRY_KEY))
                            .build());

    public static final IAttachmentType<Boolean, EnderMan> ENDERMAN_CAP = RegHelper.registerDataAttachment(
            res("angered_from_tv"),
            () -> RegHelper.AttachmentBuilder.create(() -> Boolean.FALSE).persistent(Codec.BOOL),
            EnderMan.class
    );

    public static final Supplier<LootItemConditionType> TV_ENDERMAN_CONDITION = RegHelper.registerLootCondition(
            VistaMod.res("angered_from_tv"), () -> AngeredFromTvCondition.CODEC
    );

    public static final RegSupplier<SoundEvent> CASSETTE_INSERT_SOUND = RegHelper.registerSound(res("block.television.insert"));
    public static final RegSupplier<SoundEvent> CASSETTE_EJECT_SOUND = RegHelper.registerSound(res("block.television.eject"));
    public static final RegSupplier<SoundEvent> TV_STATIC_SOUND = RegHelper.registerSound(res("block.television.static"));
    public static final RegSupplier<SoundEvent> SOJOURN_DISC_SOUND = RegHelper.registerSound(res("music_disc.sojourn"));
    public static final HolderRef<JukeboxSong> SOJOURN_DISC_SONG = HolderRef.of(
            res("sojourn"), Registries.JUKEBOX_SONG);
    public static final int STATIC_SOUND_DURATION = 4 * 20; //4 seconds

    public static final Supplier<LootItemFunctionType<CassetteTapeLootFunction>> CASSETTE_TAPE_LOOT_FUNCTION =
            RegHelper.registerLootFunction(res("random_tape"), CassetteTapeLootFunction.CODEC);

    public static final TagKey<CassetteTape> SUPPORTER_TAPES_TAG = TagKey.create(
            CASSETTE_TAPE_REGISTRY_KEY, res("supporter_tapes"));

    public static final TagKey<Item> GLASS_PANES_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));

    // Entities of these types are not drawn in mirror reflections (e.g. vampires).
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> CANT_SEE_THROUGH_MIRROR = TagKey.create(
            Registries.ENTITY_TYPE, res("cant_see_through_mirror"));

    // Entities of these types are not drawn in camera/TV feeds (e.g. vampires).
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> CANT_SEE_THROUGH_TV = TagKey.create(
            Registries.ENTITY_TYPE, res("cant_see_through_tv"));

    public static final Supplier<Item> SOJOURN_MUSIC_DISC = RegHelper.registerItem(res("music_disc_sojourn"),
            () -> new Item(new Item.Properties()
                    .jukeboxPlayable(SOJOURN_DISC_SONG.getKey())
                    .stacksTo(1).rarity(Rarity.RARE)));

    public static final Supplier<Item> CRYSTALLINE = RegHelper.registerItem(res("crystalline"),
            () -> new Item(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static void init() {
        if (CompatHandler.IRIS) {
            VistaMod.LOGGER.error("IRIS mod detected. Vista WILL have visual issues and degraded performance. This is not something that I can fix and happens even with shaderpacks off. Trust me I tried but it's inherently due to how Iris was made and should be handled there.");
        }


        //named cassettr name the video on screen ontop left

        //turn on animation independent of texture size
        //TODO: alex mobs shaders compat
        //TODO: check panes not working for some
        //night vision and ctr effects for view finders
        //check update rate when multiple view finders are queried
        //change view distance based off fov
        //change update rate based off tv size
        //wrech and cannon for when facing same axis
        //pause not working with multiple tvs
        //change update range to be higher!!!
        //aurora with snow and sleeping fox cabin
        //check if view are sections are not duplicated
        //amendments mixed pot not saving on servers
        //divining rod can use maybe has on the block container of a chunk
        //no adjusted frustum causing to clip through blocks when placed next to it
        //turn on sound
        //player holding hands like when using explosure cameera and cannons
        //new cassettes
        //view distance scales with zoom
        //cannon maoeuvering sound
        //view finder maneuvering sound
        //exposure compat
        CommonConfigs.init();

        ModNetwork.init();
        ModLootOverrides.init();
        CompatHandler.init();

        RegHelper.addItemsToTabsRegistration(VistaMod::addItemsToTabs);
        RegHelper.registerSimpleRecipeCondition(VistaMod.res("flag"),
                s -> {
                    if (Objects.equals(s, "wave_gate")) return CommonConfigs.isWaveGateCraftable();
                    if (Objects.equals(s, "mirror")) return CommonConfigs.isMirrorEnabled();
                    if (Objects.equals(s, "picture_tape")) return CommonConfigs.isPictureTapeEnabled();
                    return true;
                });

        if (PlatHelper.getPhysicalSide().isClient()) {
            VistaModClient.init();
            PlatHelper.addReloadableCommonSetup((ra, dataReload) -> {
                // if (!dataReload) CassetteTexturesMaterials.onWorldReload();
            });
        }
    }

    private static final TagKey<Item> C_MUSIC_DISCS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(
            "c", "music_discs"));

    private static void addItemsToTabs(RegHelper.ItemToTabEvent event) {
        event.add(CreativeModeTabs.REDSTONE_BLOCKS, VIEWFINDER.get());
        event.add(CreativeModeTabs.REDSTONE_BLOCKS, TV.get());
        if (CommonConfigs.isMirrorEnabled()) {
            event.add(CreativeModeTabs.FUNCTIONAL_BLOCKS, MIRROR.get());
            event.addAfter(CreativeModeTabs.INGREDIENTS, (i) -> i.is(Items.NAUTILUS_SHELL), CRYSTALLINE.get());
        }
        CreativeModeTab.ItemDisplayParameters parameters = event.getParameters();
        for (var v : parameters.holders().lookupOrThrow(CASSETTE_TAPE_REGISTRY_KEY).listElements().toList()) {
            if (v.is(SUPPORTER_TAPES_TAG)) continue;
            ItemStack stack = CASSETTE.get().getDefaultInstance();
            stack.set(CASSETTE_TAPE_COMPONENT.get(), v);
            event.add(CreativeModeTabs.TOOLS_AND_UTILITIES, stack);
        }
        event.add(CreativeModeTabs.TOOLS_AND_UTILITIES, HOLLOW_CASSETTE.get());
        if (CommonConfigs.isPictureTapeEnabled()) {
            event.add(CreativeModeTabs.TOOLS_AND_UTILITIES, PICTURE_TAPE.get());
        }
        event.addAfter(CreativeModeTabs.TOOLS_AND_UTILITIES, i -> i.is(C_MUSIC_DISCS), SOJOURN_MUSIC_DISC.get());

        if (CommonConfigs.isWaveGateCraftable()) {
            event.add(CreativeModeTabs.REDSTONE_BLOCKS, WAVE_GATE.get());
        } else {
            if (event.getTab().hasAnyItems()) {
                event.add(CreativeModeTabs.OP_BLOCKS, WAVE_GATE.get());
            }
        }

        CompatHandler.addItemsToTabs(event);
    }

    @EventCalled
    public static void addEntityGoal(Entity entity, ServerLevel serverLevel) {
        if (entity instanceof EnderMan man) {
            EndermanFreezeWhenLookedAtThroughTVGoal task = new EndermanFreezeWhenLookedAtThroughTVGoal(man);
            man.goalSelector.addGoal(1, task);
        }
    }


    public static boolean isFunny() {
        return (CompatHandler.SUPPLEMENTARIES && SuppCompat.isFunny());
    }

    public static void onPlayerLoggedIn(ServerPlayer sp) {
        // Zones are populated server-side by ServerCameraChunkManager on the first tick.
        // Send an empty sync now so the client starts with a clean slate.
        NetworkHelper.sendToClientPlayer(sp, new ClientBoundSyncExtraChunksPacket(new ExtraChunkViewData()));
    }

    public static void onServerPlayerTick(ServerPlayer p) {
        ServerCameraChunkManager.onServerPlayerTick(p);

    }
}
