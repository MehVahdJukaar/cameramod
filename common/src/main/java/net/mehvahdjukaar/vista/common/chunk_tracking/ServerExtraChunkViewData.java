package net.mehvahdjukaar.vista.common.chunk_tracking;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ServerExtraChunkViewData extends ExtraChunkViewData {

    private final Set<GlobalPos> watchedViewFinders = new HashSet<>();
    private final Set<Long> queuedZoneChunks = new HashSet<>();

    public ServerExtraChunkViewData() {
    }

    @Override
    public void clearZones() {
        super.clearZones();
        queuedZoneChunks.clear();
    }

    public Set<GlobalPos> getWatchedViewFinders() {
        return Collections.unmodifiableSet(watchedViewFinders);
    }

    public void setWatchedViewFinders(Set<GlobalPos> viewFinders) {
        watchedViewFinders.clear();
        watchedViewFinders.addAll(viewFinders);
    }

    public boolean isZoneChunkQueued(ChunkPos pos) {
        return queuedZoneChunks.contains(pos.toLong());
    }

    public void markZoneChunkQueued(ChunkPos pos) {
        queuedZoneChunks.add(pos.toLong());
    }
}
