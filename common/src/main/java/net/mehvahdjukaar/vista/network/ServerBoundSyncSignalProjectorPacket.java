package net.mehvahdjukaar.vista.network;

import net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler;
import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.projector.SignalProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record ServerBoundSyncSignalProjectorPacket(
        BlockPos pos, String str) implements Message {

    public ServerBoundSyncSignalProjectorPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readUtf());
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeUtf(this.str);
    }

    @Override
    public void handle(ChannelHandler.Context context) {
        // server level
        if (context.getSender() instanceof ServerPlayer sender) {
            Level level = sender.level();
            BlockPos pos = this.pos;
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof SignalProjectorBlockEntity proj) {
                if (!proj.canBeEditedBy(sender)) {
                    VistaMod.LOGGER.warn("Player {} tried to edit signal projector at {} without permission",
                            sender.getName().getString(), pos);
                    return;
                }
                proj.setUrl(str);
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

}