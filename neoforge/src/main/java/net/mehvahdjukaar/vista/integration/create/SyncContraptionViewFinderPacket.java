package net.mehvahdjukaar.vista.integration.create;

import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaternionf;

import java.util.UUID;

// Aim sync for a view finder riding inside a Create contraption. A contraption has no server-side block
// entity, so SyncViewFinderPacket (which addresses by block pos) can't be used and this carries the
// contraption entity id + local pos instead.
//
// Client to server: the controlling player's aim change. The server relays it to everyone tracking the
// contraption (remote TVs update live) and bakes it into the stored block NBT, so the aim survives a
// render rebuild, a new viewer coming into range, and save/load while assembled.
public record SyncContraptionViewFinderPacket(
        UUID contraptionId, BlockPos localPos, Quaternionf localRot, int zoom,
        boolean locked, boolean stopControlling) implements Message {

    public static final TypeAndCodec<RegistryFriendlyByteBuf, SyncContraptionViewFinderPacket> CODEC =
            Message.makeType(VistaMod.res("sync_contraption_view_finder"), SyncContraptionViewFinderPacket::new);

    public SyncContraptionViewFinderPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readBlockPos(), ByteBufCodecs.QUATERNIONF.decode(buf),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(contraptionId);
        buf.writeBlockPos(localPos);
        ByteBufCodecs.QUATERNIONF.encode(buf, localRot);
        buf.writeVarInt(zoom);
        buf.writeBoolean(locked);
        buf.writeBoolean(stopControlling);
    }

    @Override
    public void handle(Context context) {
        Level level = context.getPlayer().level();
        if (level.isClientSide) {
            applyToRenderBlockEntity(level);
        } else if (level instanceof ServerLevel sl) {
            relayAndPersist(sl);
        }
    }

    private void applyToRenderBlockEntity(Level level) {
        Entity contraption = CreateCompat.findContraption(level, contraptionId);
        if (contraption == null) return;
        BlockEntity be = CreateCompat.getClientBlockEntity(contraption, localPos);
        if (be instanceof ViewFinderBlockEntity vf) {
            vf.setTrustedInternalAttributes(localRot, zoom, locked);
            if (stopControlling) vf.setCurrentUser(null);
        }
    }

    private void relayAndPersist(ServerLevel level) {
        Entity contraption = CreateCompat.findContraption(level, contraptionId);
        if (contraption == null) return;
        // durable: bake the aim into stored block data so late viewers + save/load keep it
        CreateCompat.persistViewFinderAim(contraption, localPos, localRot, zoom, locked);
        // live: everyone tracking the contraption applies it to their own render block entity
        NetworkHelper.sendToAllClientPlayersInDefaultRange(level, contraption.blockPosition(),
                new SyncContraptionViewFinderPacket(contraptionId, localPos, localRot, zoom, locked, stopControlling));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return CODEC.type();
    }
}
