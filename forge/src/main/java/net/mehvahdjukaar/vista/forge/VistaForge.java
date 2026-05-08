package net.mehvahdjukaar.vista.forge;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.mehvahdjukaar.vista.common.cassette.CassetteTape;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DataPackRegistryEvent;

import static net.mehvahdjukaar.vista.VistaMod.MOD_ID;

/**
 * Author: MehVahdJukaar
 */
@Mod(MOD_ID)
public class VistaForge {

    public VistaForge() {
        VistaMod.init();
        MinecraftForge.EVENT_BUS.register(this);
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        if (PlatHelper.getPhysicalSide().isClient()) VistaForgeClient.init(bus);
        bus.addListener(VistaForge::registerDataPackRegistry);
    }

    public static void registerDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(CassetteTape.REGISTRY_KEY, CassetteTape.DIRECT_CODEC, CassetteTape.DIRECT_CODEC);
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        var level = event.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            VistaMod.addEntityGoal(event.getEntity(), serverLevel);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
                BroadcastManager.getInstance(serverLevel).syncTo(player);
            }
        }
    }


}
