package net.mehvahdjukaar.vista.common.view_finder;

import net.mehvahdjukaar.moonlight.api.misc.TileOrEntityTarget;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.vista.network.SyncViewFinderPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public interface ReferenceFrame {
    Vec3 position(float partialTicks);

    Quaternionf getRotation(float partialTicks);

    Vec3 velocity();

    TileOrEntityTarget makeNetworkTarget();

    boolean isStillValid(Player player);

    /**
     * Send a locally-made aim change to the server. Default routes through SyncViewFinderPacket, which
     * locates the view finder by makeNetworkTarget(); frames whose view finder has no addressable
     * server-side block entity (e.g. inside a Create contraption) override this with their own packet.
     */
    default void syncToServer(Quaternionf localRot, int zoom, boolean locked, boolean removeOwner, Player player) {
        NetworkHelper.sendToServer(new SyncViewFinderPacket(
                localRot, zoom, locked, removeOwner, makeNetworkTarget(), player.getUUID()));
    }

    /**
     * Broadcast an authoritative aim to tracking clients. Default routes through SyncViewFinderPacket.
     */
    default void syncToCLients(ServerLevel level, Quaternionf localRot, int zoom, boolean locked) {
        NetworkHelper.sendToAllClientPlayersInDefaultRange(level, BlockPos.containing(position(1)),
                new SyncViewFinderPacket(localRot, zoom, locked, false, makeNetworkTarget(), null));
    }
}
