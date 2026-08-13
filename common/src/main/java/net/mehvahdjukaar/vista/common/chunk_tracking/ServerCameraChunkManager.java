package net.mehvahdjukaar.vista.common.chunk_tracking;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.broadcast.IBroadcastLocation;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.network.ClientBoundSyncExtraChunksPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Position;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * Server-authoritative camera chunk manager.
 *
 * <p>Every {@link #TICK_INTERVAL} game-ticks (staggered per player) this class:
 * <ol>
 *   <li>Scans every loaded chunk in the player's normal view distance for
 *       {@link TVBlockEntity} instances whose cassette has a linked ViewFinder UUID.</li>
 *   <li>Follows the {@link BroadcastManager} connection to find the ViewFinder's
 *       world position.</li>
 *   <li>Force-loads the circle of chunks around the ViewFinder via
 *       {@link ServerLevel#setChunkForced} (ref-counted across multiple players
 *       watching the same ViewFinder).</li>
 *   <li>Updates the player's {@link ExtraChunkViewData} attachment and syncs it to the
 *       client so the extra chunk zone system sends the ViewFinder chunks to them.</li>
 * </ol>
 *
 * <p>ViewFinders already inside the player's normal view distance are skipped, since the server
 * sends those chunks anyway.
 *
 * <p>Cross-dimension ViewFinders are force-loaded in their own dimension but are
 * <em>not</em> added to the player's ExtraChunkViewData, since chunk-sending zones
 * only apply to the player's current dimension.
 */
public class ServerCameraChunkManager {

    public static final int RECURSIVE_SCAN_RADIUS = 4;
    private static final int TICK_INTERVAL = 40;
    // retry interval for zone chunks that weren't loaded on the first attempt
    private static final int FLUSH_INTERVAL = 5;

    // Depth of ViewFinder-through-TV chains. 0 is TVs in normal view and their ViewFinders, 1 adds
    // the TVs inside those zones, and so on.
    private static final int MAX_RECURSION_DEPTH = 2;

    // Watcher count per ViewFinder, for ref-counting setChunkForced. Global on purpose, not per player.
    private static final Map<GlobalPos, Integer> linkedViewFindersTrackedByPlayers = new HashMap<>();

    // Every loaded TV, by dimension. Fed by trackTv/untrackTv off chunk load and unload, which saves
    // findViewFindersNeededForPlayer from scanning chunks per player.
    private static final Map<ResourceKey<Level>, Set<TVBlockEntity>> loadedServerTVs = new HashMap<>();

    // ── TV lifecycle events (called from platform code) ───────────────────────

    /**
     * Call when a {@link TVBlockEntity} becomes live on the server
     * (chunk loaded or block placed).
     */
    public static void trackTv(TVBlockEntity tv) {
        if (tv.getLevel() instanceof ServerLevel sl) {
            loadedServerTVs.computeIfAbsent(sl.dimension(), k -> new HashSet<>()).add(tv);
        }
    }

    /**
     * Call when a {@link TVBlockEntity} is removed from the server
     * (chunk unloaded or block broken).
     */
    public static void untrackTv(TVBlockEntity tv) {
        if (tv.getLevel() instanceof ServerLevel sl) {
            Set<TVBlockEntity> set = loadedServerTVs.get(sl.dimension());
            if (set != null) set.remove(tv);
        }
    }

    // ── Per-player tick ───────────────────────────────────────────────────────

    /**
     * Called every server tick for each {@link ServerPlayer} (via {@code ServerPlayerMixin}).
     * Updates are staggered so not all players recalculate on the same tick.
     */
    public static void onServerPlayerTick(ServerPlayer player) {
        int chunkRadius = CommonConfigs.SEND_CHUNKS_VIEWED_BY_VIEW_FINDER.get();
        boolean sends = chunkRadius > 0;
        boolean loads = CommonConfigs.LOAD_CHUNKS_VIEWED_BY_VIEW_FINDER.get() || PlatHelper.isDev();
        if (!sends && !loads) return;
        long gameTime = player.serverLevel().getGameTime();

        // Periodic flush: retry sending zone chunks that weren't loaded on the first attempt.
        // Runs more frequently than the zone-detection scan so chunks appear promptly after
        // force-loading completes (which is async and may take several ticks).
        if ((gameTime + player.getId()) % FLUSH_INTERVAL == 0) {
            flushPendingZoneChunks(player);
        }

        if ((gameTime + player.getId()) % TICK_INTERVAL != 0) return;

        ServerExtraChunkViewData data = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        Set<GlobalPos> desired = findViewFindersNeededForPlayer(player);
        Set<GlobalPos> current = data.getTrackedWantedZoneCenters();
        if (desired.equals(current)) return;

        Set<GlobalPos> added = new HashSet<>(desired);
        added.removeAll(current);
        Set<GlobalPos> removed = new HashSet<>(current);
        removed.removeAll(desired);

        if (loads) {
            for (GlobalPos vf : added) {
                int refs = linkedViewFindersTrackedByPlayers.getOrDefault(vf, 0);
                if (refs == 0) {
                    ServerLevel vfLevel = player.getServer().getLevel(vf.dimension());
                    if (vfLevel != null) setChunksForceLoaded(vfLevel, vf.pos(), chunkRadius, true);
                }
                linkedViewFindersTrackedByPlayers.put(vf, refs + 1);
            }
            updateVfReferences(player, chunkRadius, removed);
        }
        // Persist the new tracked set inside the per-player attachment.
        data.setTrackedWantedZoneCenters(desired);

        // Rebuild zones (same-dimension ViewFinders only; cross-dim only force-loads).
        data.clearZones();
        if (sends) {
            for (GlobalPos vf : desired) {
                if (vf.dimension().equals(player.level().dimension())) {
                    data.addZone(new ChunkPos(vf.pos()), chunkRadius);
                }
            }
        }

        NetworkHelper.sendToClientPlayer(player, new ClientBoundSyncExtraChunksPacket(data));
        VistaMod.LOGGER.debug("[Vista] {} zones updated: {} viewfinders, {} same-dim zones",
                player.getName().getString(), desired.size(), data.getZones().size());
    }

    // ── Zone chunk flushing ───────────────────────────────────────────────────

    /**
     * Directly calls {@code markChunkPendingToSend} for every zone chunk that is now
     * loaded but not yet queued. This is the server-side counterpart to
     * {@code ChunkMapMixin.vista$flushPendingZoneChunks} and runs independently of
     * player movement so chunks are sent promptly after force-loading completes.
     */
    private static void flushPendingZoneChunks(ServerPlayer player) {
        ServerExtraChunkViewData data = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        if (data.getZones().isEmpty()) return;

        var chunkMap = player.serverLevel().getChunkSource().chunkMap;
        int flushed = 0;
        for (ChunkPos pos : data.getAllChunks()) {
            if (!player.getChunkTrackingView().isInViewDistance(pos.x, pos.z) && !data.isZoneChunkQueued(pos)) {
                if (chunkMap.getChunkToSend(pos.toLong()) != null) {
                    chunkMap.markChunkPendingToSend(player, pos);
                    data.markZoneChunkQueued(pos);
                    flushed++;
                }
            }
        }
        if (flushed > 0) {
            VistaMod.LOGGER.debug("[Vista] Periodic flush sent {} zone chunks to {}", flushed, player.getName().getString());
        }
    }

    // ── ViewFinder discovery ──────────────────────────────────────────────────

    private static Set<GlobalPos> findViewFindersNeededForPlayer(ServerPlayer player) {
        Set<GlobalPos> result = new HashSet<>();
        ServerLevel level = player.serverLevel();
        Set<TVBlockEntity> candidates = loadedServerTVs.getOrDefault(level.dimension(), Set.of());
        collectViewFinders(candidates, player.getChunkTrackingView()::isInViewDistance,
                level, result, new HashSet<>(), 0);
        return result;
    }

    /**
     * Recursively collects ViewFinder destinations reachable from TVs inside {@code inZone}.
     *
     * <p>At depth 0 the zone is the player's normal view distance. At each subsequent depth
     * the zone is a CHUNK_RADIUS circle around a newly discovered ViewFinder, so that TVs
     * already force-loaded inside a camera zone can chain to further ViewFinders.
     */
    private static void collectViewFinders(
            Set<TVBlockEntity> candidates,
            BiPredicate<Integer, Integer> inZone,
            ServerLevel level,
            Set<GlobalPos> result,
            Set<TVBlockEntity> visited,
            int depth) {

        if (depth >= MAX_RECURSION_DEPTH) return;
        BroadcastManager bm = BroadcastManager.getInstance(level);
        List<GlobalPos> newlyAdded = new ArrayList<>();

        for (TVBlockEntity tv : candidates) {
            if (!visited.add(tv)) continue;
            if (tv.isRemoved()) continue;
            BlockPos tvRealPos = BlockPos.containing(SableCompanion.INSTANCE.projectOutOfSubLevel(level,(Position) Vec3.atLowerCornerOf(tv.getBlockPos())));
            // TV on a Sable sublevel whose projection no-ops (sublevel held/removed): stale, unreachable
            if (SableCompanion.INSTANCE.isInPlotGrid(level, tvRealPos)) continue;
            ChunkPos tvChunk = new ChunkPos(tvRealPos);
            if (!inZone.test(tvChunk.x, tvChunk.z)) continue;

            UUID feedId = tv.getViewingFeedId();
            if (feedId == null) continue;
            IBroadcastLocation broadCastLoc = bm.getFeedLocationById(feedId);
            if (broadCastLoc == null) continue;
            GlobalPos dest = broadCastLoc.getChunkSendPosition();
            if (dest == null) continue;

            // If the ViewFinder sits inside a Sable sublevel (a "ship" placed in the world), its
            // stored position is the plot-grid storage coordinate. Force-loading/sending those plot
            // chunks fights Sable's plot lifecycle (shutdown hangs, chunks not loading). Resolve the
            // real-world chunk anchor where the sublevel logically is; Sable manages the sublevel's
            // own chunks itself. Null when the position cannot be resolved to a real-world chunk
            // (held/removed sublevel with no cached anchor): skip entirely rather than let a
            // plot-grid coordinate reach setChunkForced / the zone system.
            dest = normalizeGlobalPos(level, dest);
            if (dest == null) continue;

            //TODO: send chunks even if outside of current dim.
            if (dest.dimension().equals(level.dimension())) {
                // Skip ViewFinders already within this zone (the caller already covers them)
                BlockPos pos = dest.pos();
                pos = BlockPos.containing(SableCompanion.INSTANCE.projectOutOfSubLevel(level,(Position) Vec3.atLowerCornerOf(pos)));
                ChunkPos vfChunk = new ChunkPos(pos);
                if (inZone.test(vfChunk.x, vfChunk.z)) continue;
            }

            if (result.add(dest)) newlyAdded.add(dest);
        }

        // Recurse into same-dimension camera zones that were just discovered.
        // Only TVs already loaded (force-loaded on a prior tick) will be in
        // loadedServerTVs, so this naturally stalls until the zone is live.
        for (GlobalPos vfPos : newlyAdded) {
            if (!vfPos.dimension().equals(level.dimension())) continue;
            ChunkPos vfChunk = new ChunkPos(vfPos.pos());
            collectViewFinders(candidates, (x,y)->isInRecursiveDistance(vfChunk, x, y)
                    , level, result, visited, depth + 1);
        }
    }

    private static @NotNull boolean isInRecursiveDistance(ChunkPos centerChunk, int cx, int cz) {
        return centerChunk.getChessboardDistance(cx, cz) <= RECURSIVE_SCAN_RADIUS;
    }

    /**
     * Resolves the real-world chunk anchor for a ViewFinder destination.
     *
     * <p>Sublevels (movable "ships") store their blocks at plot-grid coordinates; a ViewFinder
     * inside one resolves to where the ship is logically placed in the world. We project out of the
     * sublevel (a no-op outside one) and snap to chunk granularity (Y dropped) so sub-chunk movement
     * doesn't churn the desired-set or re-send packets. Everything downstream operates on
     * {@link ChunkPos}, so this loses nothing.
     *
     * <p>Returns null when the position lies in a Sable plot grid and the projection no-ops
     * (the sublevel is held/unloaded or removed). Plot-grid coordinates must never reach
     * {@code setChunkForced} or the zone system: vanilla tickets/holders in the plot grid fight
     * Sable's injected PlotChunkHolders (shutdown drain loop never ends, chunks never load).
     */
    @Nullable
    private static GlobalPos normalizeGlobalPos(ServerLevel playerLevel, GlobalPos dest) {
        // Project against the ViewFinder's OWN level (it may be cross-dimension).
        ServerLevel destLevel = dest.dimension().equals(playerLevel.dimension())
                ? playerLevel
                : playerLevel.getServer().getLevel(dest.dimension());
        if (destLevel == null) return dest;
        Vec3 world = SableCompanion.INSTANCE.projectOutOfSubLevel(
                destLevel, (Position) Vec3.atLowerCornerOf(dest.pos()));
        if (SableCompanion.INSTANCE.isInPlotGrid(destLevel, (Position) world)) return null;
        ChunkPos chunk = new ChunkPos(BlockPos.containing(world));
        return GlobalPos.of(dest.dimension(), chunk.getWorldPosition());
    }

    // ── Force-loading ─────────────────────────────────────────────────────────

    private static void setChunksForceLoaded(ServerLevel level, BlockPos viewFinderPos, int radius, boolean force) {
        ChunkPos cp = new ChunkPos(viewFinderPos);
        ChunkPos.rangeClosed(cp, radius)
                .filter(p -> p.distanceSquared(cp) <= radius * radius)
                .forEach(p -> level.setChunkForced(p.x, p.z, force));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Call when a player disconnects to release their force-loading references.
     */
    public static void onPlayerLeave(ServerPlayer player) {
        int chunkRadius = CommonConfigs.SEND_CHUNKS_VIEWED_BY_VIEW_FINDER.get();
        Set<GlobalPos> watching = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player).getTrackedWantedZoneCenters();
        updateVfReferences(player, chunkRadius, watching);
    }

    private static void updateVfReferences(ServerPlayer player, int chunkRadius, Set<GlobalPos> watching) {
        for (GlobalPos vf : watching) {
            int refs = linkedViewFindersTrackedByPlayers.getOrDefault(vf, 0) - 1;
            if (refs <= 0) {
                linkedViewFindersTrackedByPlayers.remove(vf);
                ServerLevel vfLevel = player.getServer().getLevel(vf.dimension());
                if (vfLevel != null) setChunksForceLoaded(vfLevel, vf.pos(), chunkRadius, false);
            } else {
                linkedViewFindersTrackedByPlayers.put(vf, refs);
            }
        }
    }

    /**
     * Releases every outstanding force-load ticket and clears all state on server stop.
     * <p>
     * {@code setChunkForced} writes to the persistent {@code ForcedChunksSavedData}, so tickets still
     * held at shutdown get saved to disk and come back orphaned next session, with the in-memory
     * ref-counts gone and nothing left to release them. Unforcing happens before the maps are
     * cleared, since those are static and would otherwise leak across worlds in one client session.
     */
    public static void clearAll(MinecraftServer server) {
        int chunkRadius = CommonConfigs.SEND_CHUNKS_VIEWED_BY_VIEW_FINDER.get();
        for (GlobalPos vf : linkedViewFindersTrackedByPlayers.keySet()) {
            ServerLevel vfLevel = server.getLevel(vf.dimension());
            if (vfLevel != null) setChunksForceLoaded(vfLevel, vf.pos(), chunkRadius, false);
        }
        linkedViewFindersTrackedByPlayers.clear();
        loadedServerTVs.clear();
    }
}
