package net.mehvahdjukaar.vista.network;

import net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkDir;
import net.mehvahdjukaar.vista.VistaMod;

public class ModNetwork {

    public static ChannelHandler CHANNEL;

    public static void init() {
        CHANNEL = ChannelHandler.builder(VistaMod.MOD_ID)
                .register(NetworkDir.PLAY_TO_SERVER,ServerBoundSyncViewFinderPacket.class,
                        ServerBoundSyncViewFinderPacket::new)
                .register(NetworkDir.PLAY_TO_CLIENT, ClientBoundControlViewFinderPacket.class,
                        ClientBoundControlViewFinderPacket::new)
                .register(NetworkDir.PLAY_TO_CLIENT, ClientBoundSyncBroadcastManagerPacket.class,
                        ClientBoundSyncBroadcastManagerPacket::fromBuffer)

                .build();

    }

}
