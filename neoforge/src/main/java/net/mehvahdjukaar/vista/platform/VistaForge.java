package net.mehvahdjukaar.vista.platform;

import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.chunk_tracking.ServerCameraChunkManager;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import static net.mehvahdjukaar.vista.VistaMod.MOD_ID;

/**
 * Author: MehVahdJukaar
 */
@Mod(MOD_ID)
public class VistaForge {

    public static WeakReference<IEventBus> modBus;

    public VistaForge(IEventBus bus) {
        modBus = new WeakReference<>(bus);
        VistaMod.init();
        bus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                VistaMod.TV_TILE.get(),
                (tv, side) -> TvEnergyHandler.getOrCreate(tv));

    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            VistaMod.addEntityGoal(event.getEntity(), serverLevel);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ServerCameraChunkManager.onPlayerLeave(sp);
        }
    }

    @SubscribeEvent
    public void onServerPlayerTick(PlayerTickEvent.Post event) {
        Player entity = event.getEntity();
        if (entity instanceof ServerPlayer sp) VistaMod.onServerPlayerTick(sp);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerCameraChunkManager.clearAll(event.getServer());
    }

}
