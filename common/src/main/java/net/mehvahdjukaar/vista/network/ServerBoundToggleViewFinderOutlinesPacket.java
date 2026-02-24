package net.mehvahdjukaar.vista.network;

import net.mehvahdjukaar.moonlight.api.misc.TileOrEntityTarget;
import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ServerBoundToggleViewFinderOutlinesPacket(TileOrEntityTarget target) implements Message {

    public static final TypeAndCodec<RegistryFriendlyByteBuf, ServerBoundToggleViewFinderOutlinesPacket> CODEC = Message.makeType(
            VistaMod.res("c2s_toggle_viewfinder_outlines"), ServerBoundToggleViewFinderOutlinesPacket::new);

    public ServerBoundToggleViewFinderOutlinesPacket(FriendlyByteBuf buf) {
        this(TileOrEntityTarget.read(buf));
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        this.target.write(buf);
    }

    @Override
    public void handle(Context context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            ViewFinderAccess access = ViewFinderAccess.find(player.level(), this.target);
            if (access != null) {
                var viewFinder = access.getInternalTile();
                if (viewFinder.canBeUsedBy(BlockPos.containing(access.getCannonGlobalPosition(1)), player)
                        && viewFinder.hasEntityDetectorFilter()) {
                    viewFinder.toggleDetectorOutlinesEnabled();
                    viewFinder.setChanged();
                    access.updateClients();
                }
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return CODEC.type();
    }
}
