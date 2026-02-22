package net.mehvahdjukaar.vista;

import com.mojang.serialization.Codec;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluidRegistry;
import net.mehvahdjukaar.moonlight.api.misc.EventCalled;
import net.mehvahdjukaar.moonlight.api.misc.RegSupplier;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.util.Utils;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.mehvahdjukaar.vista.common.ModLootOverrides;
import net.mehvahdjukaar.vista.common.cassette.CassetteItem;
import net.mehvahdjukaar.vista.common.cassette.CassetteTape;
import net.mehvahdjukaar.vista.common.cassette.CassetteTapeLootFunction;
import net.mehvahdjukaar.vista.common.cassette.HollowCassetteItem;
import net.mehvahdjukaar.vista.common.tv.TVBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.tv.TVItem;
import net.mehvahdjukaar.vista.common.tv.enderman.AngeredFromTvCondition;
import net.mehvahdjukaar.vista.common.tv.enderman.EndermanFreezeWhenLookedAtThroughTVGoal;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlock;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.supplementaries.SuppCompat;
import net.mehvahdjukaar.vista.network.ModNetwork;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
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

import java.util.function.Supplier;


public class VistaMod {
    public static final String MOD_ID = "vista";
    public static final Logger LOGGER = LogManager.getLogger("Vista");

    public static ResourceLocation res(String name) {
        return new ResourceLocation(MOD_ID, name);
    }


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
            () -> new ViewFinderBlock(Block.Properties.of().strength(1.5f).noOcclusion()));

    public static final Supplier<BlockEntityType<ViewFinderBlockEntity>> VIEWFINDER_TILE = RegHelper.registerBlockEntityType(
            res("viewfinder"), ViewFinderBlockEntity::new, VIEWFINDER);

    public static final Supplier<CassetteItem> CASSETTE = RegHelper.registerItem(res("cassette"),
            () -> new CassetteItem(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .stacksTo(1)));

    public static final Supplier<HollowCassetteItem> HOLLOW_CASSETTE = RegHelper.registerItem(res("hollow_cassette"),
            () -> new HollowCassetteItem(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .stacksTo(1)));


    public static final Supplier<LootItemConditionType> TV_ENDERMAN_CONDITION = RegHelper.register(
            VistaMod.res("angered_from_tv"), () -> new LootItemConditionType(AngeredFromTvCondition.SERIALIZER),
            Registries.LOOT_CONDITION_TYPE
    );

    public static final RegSupplier<SoundEvent> CASSETTE_INSERT_SOUND = RegHelper.registerSound(res("block.television.insert"));
    public static final RegSupplier<SoundEvent> CASSETTE_EJECT_SOUND = RegHelper.registerSound(res("block.television.eject"));
    public static final RegSupplier<SoundEvent> TV_STATIC_SOUND = RegHelper.registerSound(res("block.television.static"));
    public static final RegSupplier<SoundEvent> SOJOURN_DISC_SOUND = RegHelper.registerSound(res("music_disc.sojourn"));
    public static final int STATIC_SOUND_DURATION = 4 * 20; //4 seconds

    public static final Supplier<LootItemFunctionType> CASSETTE_TAPE_LOOT_FUNCTION =
            RegHelper.register(res("random_tape"), () ->
                            new LootItemFunctionType(CassetteTapeLootFunction.SERIALIZER),
                    Registries.LOOT_FUNCTION_TYPE);

    public static final TagKey<CassetteTape> SUPPORTER_TAPES_TAG = TagKey.create(
            CassetteTape.REGISTRY_KEY, res("supporter_tapes"));

    public static final TagKey<Item> GLASS_PANES_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("c", "glass_panes"));

    public static final Supplier<Item> SOJOURN_MUSIC_DISC = RegHelper.registerItem(res("music_disc_sojourn"),
            () -> PlatHelper.newMusicDisc(13, SOJOURN_DISC_SOUND,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 159)
    );

    public static void init() {
        if (CompatHandler.IRIS) {
            VistaMod.LOGGER.error("IRIS mod detected. Vista WILL have visual issues and degraded performance. This is not something that I can fix and happens even with shaderpacks off. Trust me I tried but it's inherently due to how Iris was made and should be handled there.");
        }

        //TODO:
        //wrech and cannon for when facing same axis
        // view finder rotation bug wirh wrench?
        //pause not working with multiple tvs
        //change update range to be higher!!!
        //cassettes dont go back in right tv
        //lenses shaders for view finder
        //aurora with snow and sleeping fox cabin
        //check if view are sections are not duplicated
        //frustum shenanigans
        //amendments mixed pot not saving on servers
        //divining rod can use maybe has on the block container of a chunk
        //no adjusted frustum causing to clip through blocks when placed next to it
        //view finder scroll bug
        //creative only cassette that targets a video
        //turn table rotation thing
        //ball item
        //jittery scroll
        //turn on sound
        //player holding hands like when using explosure cameera and cannons
        //shader when you wear a tv. fnaf
        //new cassettes
        //view distance scales with zoom
        //check turn table
        //cannon maoeuvering sound
        //view finder maneuvering sound
        //tv glitch shader
        //exposure compat

        CommonConfigs.init();

        ModNetwork.init();
        ModLootOverrides.init();
        CompatHandler.init();

        RegHelper.addItemsToTabsRegistration(VistaMod::addItemsToTabs);

        if (PlatHelper.getPhysicalSide().isClient()) {
            VistaModClient.init();
            PlatHelper.addReloadableCommonSetup((ra, dataReload) -> {
                // if (!dataReload) CassetteTexturesMaterials.onWorldReload();
            });
        }
    }

    private static final TagKey<Item> C_MUSIC_DISCS = TagKey.create(Registries.ITEM,
            new ResourceLocation("c", "music_discs"));

    private static void addItemsToTabs(RegHelper.ItemToTabEvent event) {
        event.add(CreativeModeTabs.REDSTONE_BLOCKS, TV.get());
        event.add(CreativeModeTabs.REDSTONE_BLOCKS, VIEWFINDER.get());
        var ra = Utils.hackyGetRegistryAccess();
        for (var v : ra.lookupOrThrow(CassetteTape.REGISTRY_KEY).listElements().toList()) {
            if (v.is(SUPPORTER_TAPES_TAG)) continue;
            ItemStack stack = CASSETTE.get().getDefaultInstance();
            CassetteItem.setCassette(stack, v);
            event.add(CreativeModeTabs.TOOLS_AND_UTILITIES, stack);
        }
        event.add(CreativeModeTabs.TOOLS_AND_UTILITIES, HOLLOW_CASSETTE.get());
        event.addAfter(CreativeModeTabs.TOOLS_AND_UTILITIES, i -> i.is(C_MUSIC_DISCS), SOJOURN_MUSIC_DISC.get());

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
        return PlatHelper.isDev() || (CompatHandler.SUPPLEMENTARIES && SuppCompat.isFunny());
    }
}
