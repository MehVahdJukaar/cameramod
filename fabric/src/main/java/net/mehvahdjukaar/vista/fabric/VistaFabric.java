package net.mehvahdjukaar.vista.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.mehvahdjukaar.vista.common.cassette.CassetteItem;
import net.mehvahdjukaar.vista.common.cassette.CassetteTape;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import static net.mehvahdjukaar.vista.VistaMod.SUPPORTER_TAPES_TAG;

public class VistaFabric implements ModInitializer {


    public void onInitialize() {
        VistaMod.init();
        if (PlatHelper.getPhysicalSide().isClient()) {
            VistaFabricClient.init();
        }

        DynamicRegistries.registerSynced(CassetteTape.REGISTRY_KEY, CassetteTape.DIRECT_CODEC, CassetteTape.DIRECT_CODEC,
                DynamicRegistries.SyncOption.SKIP_WHEN_EMPTY);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            BroadcastManager.getInstance(handler.player.serverLevel()).syncTo(handler.player));


        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    for (var v : entries.getContext().holders().lookupOrThrow(CassetteTape.REGISTRY_KEY).listElements().toList()) {
                        if (v.is(SUPPORTER_TAPES_TAG)) continue;
                        ItemStack stack = VistaMod.CASSETTE.get().getDefaultInstance();
                        CassetteItem.setCassette(stack, v);
                        entries.accept(stack);
                    }
                });
    }

}
