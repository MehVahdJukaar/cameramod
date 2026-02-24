package net.mehvahdjukaar.vista.network;

import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;

public class ModNetwork {

    public static void init() {
        NetworkHelper.addNetworkRegistration(ModNetwork::registerMessages, 3);
    }

    private static void registerMessages(NetworkHelper.RegisterMessagesEvent event) {
        event.registerServerBound(ServerBoundSyncViewFinderPacket.CODEC);
        event.registerServerBound(ServerBoundSyncSignalProjectorPacket.CODEC);
        event.registerServerBound(ServerBoundToggleViewFinderOutlinesPacket.CODEC);
        event.registerClientBound(ClientBoundControlViewFinderPacket.CODEC);
    }
}
