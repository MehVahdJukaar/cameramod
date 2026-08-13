package net.mehvahdjukaar.vista.common.chunk_tracking;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Server side ExtraChunkViewData. Adds two runtime only fields the client has no use for: the
 * viewfinders this player is watching (cross dimension ones included, even though they make no
 * zone) and the zone chunks already handed to markChunkPendingToSend, so the periodic flush
 * doesn't queue them twice. Neither one is saved, the codec only handles the base class zones.
 */
public class ServerExtraChunkViewData extends ExtraChunkViewData {

    public static final Codec<ServerExtraChunkViewData> CODEC =
            ExtraChunkViewData.CODEC.xmap(
                    base -> {
                        ServerExtraChunkViewData s = new ServerExtraChunkViewData();
                        s.zones.addAll(base.zones);
                        return s;
                    },
                    s -> s
            );

    private final Set<GlobalPos> trackedWantedZoneCenters = new HashSet<>();
    // cleared whenever zones change so the flush re-evaluates everything
    private final Set<Long> queuedZoneChunks = new HashSet<>();
    // clients that can't render pinned chunks say so on join, see ServerBoundExtraChunksSupportPacket
    private boolean clientSupportsZones = true;

    public ServerExtraChunkViewData() {}

    public void setClientSupportsZones(boolean supported) {
        clientSupportsZones = supported;
    }

    public boolean clientSupportsZones() {
        return clientSupportsZones;
    }

    @Override
    protected void onZonesChanged() {
        super.onZonesChanged();
        queuedZoneChunks.clear();
    }

    public void setTrackedWantedZoneCenters(Set<GlobalPos> viewfinders) {
        trackedWantedZoneCenters.clear();
        trackedWantedZoneCenters.addAll(viewfinders);
    }

    public Set<GlobalPos> getTrackedWantedZoneCenters() {
        return Collections.unmodifiableSet(trackedWantedZoneCenters);
    }

    public void markZoneChunkQueued(ChunkPos pos) {
        queuedZoneChunks.add(pos.toLong());
    }

    public boolean isZoneChunkQueued(ChunkPos pos) {
        return queuedZoneChunks.contains(pos.toLong());
    }
}
