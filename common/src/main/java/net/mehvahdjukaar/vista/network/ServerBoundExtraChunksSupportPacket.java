package net.mehvahdjukaar.vista.network;

import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the server whether this client can actually do anything with camera zone chunks. Sodium
 * replaces the chunk renderer with its own, and the pinned sections are built on vanilla's ViewArea,
 * so those clients would pay for the chunks (bandwidth, force loading) and render nothing.
 */
public record ServerBoundExtraChunksSupportPacket(boolean supported) implements Message {

    public static final TypeAndCodec<RegistryFriendlyByteBuf, ServerBoundExtraChunksSupportPacket> CODEC =
            Message.makeType(VistaMod.res("c2s_extra_chunks_support"), ServerBoundExtraChunksSupportPacket::new);

    public ServerBoundExtraChunksSupportPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(this.supported);
    }

    @Override
    public void handle(Context context) {
        if (context.getPlayer() instanceof ServerPlayer sender) {
            VistaMod.EXTRA_VIEW_AREAS.getOrCreate(sender).setClientSupportsZones(this.supported);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return CODEC.type();
    }
}
