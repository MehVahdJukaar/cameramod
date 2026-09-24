package net.mehvahdjukaar.vista.network;

import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.chunk_tracking.ClientPinnedChunksManager;
import net.mehvahdjukaar.vista.client.chunk_tracking.ILevelRendererExt;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;

//Replaces the client's zone set with the server's and rebuilds the ViewArea
public record ClientBoundSyncExtraChunksPacket(ExtraChunkViewData data) implements Message {

    public static final TypeAndCodec<RegistryFriendlyByteBuf, ClientBoundSyncExtraChunksPacket> CODEC =
            Message.makeType(VistaMod.res("s2c_sync_extra_chunks"), ClientBoundSyncExtraChunksPacket::new);

    public ClientBoundSyncExtraChunksPacket(RegistryFriendlyByteBuf buf) {
        this(ExtraChunkViewData.STREAM_CODEC.decode(buf));
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        ExtraChunkViewData.STREAM_CODEC.encode(buf, this.data);
    }

    @Override
    public void handle(Context context) {
        ExtraChunkViewData clientData = VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA;
        Set<ChunkPos> oldChunks = clientData.getAllChunks();

        clientData.clearZones();
        for (ExtraChunkViewData.Zone zone : this.data.getZones()) {
            clientData.addZone(zone.center(), zone.radius());
        }
        Set<ChunkPos> newChunks = clientData.getAllChunks();
        // ViewArea already has the right sections
        if (newChunks.equals(oldChunks)) return;

        ClientPinnedChunksManager.keepOnly(newChunks);

        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer instanceof ILevelRendererExt ext) {
            //only recreates the pinned slots, keeping compiled geometry
            ext.vista$refreshPinnedSections();
        } else {
            mc.levelRenderer.allChanged();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return CODEC.type();
    }
}
