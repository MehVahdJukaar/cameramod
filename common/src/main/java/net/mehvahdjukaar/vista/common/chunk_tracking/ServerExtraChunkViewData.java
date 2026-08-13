package net.mehvahdjukaar.vista.common.chunk_tracking;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-side extension of {@link ExtraChunkViewData}.
 *
 * <p>Adds two pieces of runtime state that are meaningless to the client:
 * <ul>
 *   <li>{@link #trackedWantedZoneCenters}: the complete set of ViewFinder
 *       {@link GlobalPos}es this player is currently watching (including
 *       cross-dimension ones that don't yet produce a zone). Replaces the old
 *       external {@code playerViewfinders} map in {@code ServerCameraChunkManager}.</li>
 *   <li>{@link #queuedZoneChunks}: zone chunk positions that have already been
 *       handed to {@code markChunkPendingToSend}, so the periodic flush doesn't
 *       re-queue them unnecessarily.</li>
 * </ul>
 *
 * <p>Neither field is serialized; both are rebuilt at runtime each session.
 * The persistent {@link #CODEC} delegates to the base-class codec (zones only).
 */
public class ServerExtraChunkViewData extends ExtraChunkViewData {

    /** Delegates to the base-class zone codec; server-only runtime fields are not persisted. */
    public static final Codec<ServerExtraChunkViewData> CODEC =
            ExtraChunkViewData.CODEC.xmap(
                    base -> {
                        ServerExtraChunkViewData s = new ServerExtraChunkViewData();
                        s.zones.addAll(base.zones);
                        return s;
                    },
                    s -> s
            );

    // ── Server-only runtime state ──────────────────────────────────────────────

    /**
     * All ViewFinders this player is currently watching (same-dim and cross-dim).
     * Updated by {@code ServerCameraChunkManager} on every zone-detection cycle.
     */
    private final Set<GlobalPos> trackedWantedZoneCenters = new HashSet<>();

    /**
     * Zone chunk positions that have already been queued for sending.
     * Cleared whenever zones change so the flush re-evaluates everything.
     */
    private final Set<Long> queuedZoneChunks = new HashSet<>();

    public ServerExtraChunkViewData() {}

    // ── onZonesChanged hook ────────────────────────────────────────────────────

    @Override
    protected void onZonesChanged() {
        super.onZonesChanged();
        queuedZoneChunks.clear();
    }

    // ── Tracked ViewFinders ────────────────────────────────────────────────────

    /**
     * Replaces the full set of ViewFinders this player is watching.
     * Call this whenever {@code ServerCameraChunkManager} recomputes the desired set.
     */
    public void setTrackedWantedZoneCenters(Set<GlobalPos> viewfinders) {
        trackedWantedZoneCenters.clear();
        trackedWantedZoneCenters.addAll(viewfinders);
    }

    /** Returns the current tracked ViewFinder set (unmodifiable). */
    public Set<GlobalPos> getTrackedWantedZoneCenters() {
        return Collections.unmodifiableSet(trackedWantedZoneCenters);
    }

    // ── Queued chunk tracking ──────────────────────────────────────────────────

    public void markZoneChunkQueued(ChunkPos pos) {
        queuedZoneChunks.add(pos.toLong());
    }

    public boolean isZoneChunkQueued(ChunkPos pos) {
        return queuedZoneChunks.contains(pos.toLong());
    }
}
