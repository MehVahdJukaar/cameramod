package net.mehvahdjukaar.vista.network;

import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;

public class ModNetwork {

    public static void init() {
        NetworkHelper.addNetworkRegistration(ModNetwork::registerMessages, 2);
    }

    private static void registerMessages(NetworkHelper.RegisterMessagesEvent event) {
        event.registerBidirectional(SyncViewFinderPacket.CODEC);
        // contraption view finder sync is registered from :neoforge only, see integration.create.CreateCompat
        event.registerServerBound(ServerBoundSyncWaveGatePacket.CODEC);
        event.registerServerBound(ServerBoundExtraChunksSupportPacket.CODEC);
        event.registerClientBound(ClientBoundControlViewFinderPacket.CODEC);
        event.registerClientBound(ClientBoundSyncExtraChunksPacket.CODEC);
    }
}
