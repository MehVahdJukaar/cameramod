package net.mehvahdjukaar.vista.integration.computer_craft.forge;


import dan200.computercraft.api.ForgeComputerCraftAPI;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.integration.computer_craft.SignalProjectorPeripheral;
import net.mehvahdjukaar.vista.integration.computer_craft.ViewFinderPeripheral;
import net.mehvahdjukaar.vista.forge.VistaForge;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;


public class CCCompatImpl {

    protected static final BlockCapability<ViewFinderPeripheral, Direction> VIEW_FINDER_CAP =
            BlockCapability.createSided(VistaMod.res("view_finder"), ViewFinderPeripheral.class);
    protected static final BlockCapability<SignalProjectorPeripheral, Direction> CASSETTE_BURNER_CAP =
            BlockCapability.createSided(VistaMod.res("cassette_burner"), SignalProjectorPeripheral.class);


    public static void init() {
        VistaForge.modBus.get().addListener(CCCompatImpl::registerCap);
    }

    public static void setup() {
        ForgeComputerCraftAPI.registerGenericCapability(VIEW_FINDER_CAP);
    }

    public static void registerCap(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(VIEW_FINDER_CAP, VistaMod.VIEWFINDER_TILE.get(),
                (tile, object2) -> new ViewFinderPeripheral(tile));
        event.registerBlockEntity(CASSETTE_BURNER_CAP, VistaMod.SIGNAL_PROJECTOR_TILE.get(),
                (tile, object2) -> new SignalProjectorPeripheral(tile));
    }

}
