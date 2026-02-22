package net.mehvahdjukaar.vista.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler;
import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record ClientBoundSyncBroadcastManagerPacket(Map<UUID, GlobalPos> positions) implements Message {

    public static ClientBoundSyncBroadcastManagerPacket fromBuffer(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<UUID, GlobalPos> positions = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            GlobalPos pos = buf.readGlobalPos();
            positions.put(id, pos);
        }
        return new ClientBoundSyncBroadcastManagerPacket(positions);
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeVarInt(positions.size());
        for (Map.Entry<UUID, GlobalPos> entry : positions.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeGlobalPos(entry.getValue());
        }
    }


    @Override
    public void handle(ChannelHandler.Context context) {
        // client world
        handleCannonControlPacket(this);
    }

    @Environment(value = EnvType.CLIENT)
    public void handleCannonControlPacket(ClientBoundSyncBroadcastManagerPacket packet) {
        BroadcastManager.getInstance(Minecraft.getInstance().level).setClientData(positions);
    }
}