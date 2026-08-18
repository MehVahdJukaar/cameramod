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
 * Keeps the chunks around ViewFinders loaded and sent to whoever is watching them through a TV.
 * Every TICK_INTERVAL ticks, staggered per player, it walks the loaded TVs in that player's view
 * distance, follows the feed to the ViewFinder position, force loads a circle of chunks there and
 * writes the zones into the player's ExtraChunkViewData so the chunk sending code picks them up.
 * Force loading is ref counted, so several players watching the same ViewFinder only load it once.
 * ViewFinders already in normal view distance are skipped, the server sends those anyway. Cross
 * dimension ones get force loaded but no zone, since zones only apply to the player's current
 * dimension.
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

    // called from platform code when a TV goes live, chunk loaded or block placed
    public static void trackTv(TVBlockEntity tv) {
        if (tv.getLevel() instanceof ServerLevel sl) {
            loadedServerTVs.computeIfAbsent(sl.dimension(), k -> new HashSet<>()).add(tv);
        }
    }

    // same, for chunk unloaded or block broken
    public static void untrackTv(TVBlockEntity tv) {
        if (tv.getLevel() instanceof ServerLevel sl) {
            Set<TVBlockEntity> set = loadedServerTVs.get(sl.dimension());
            if (set != null) set.remove(tv);
        }
    }

    // called from ServerPlayerMixin every tick, staggered so players don't all recalculate together
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
        // an empty desired set walks the normal remove path, so a client that reports late still gets
        // its zones cleared and its force load references released
        Set<GlobalPos> desired = data.clientSupportsZones()
                ? findViewFindersNeededForPlayer(player) : Set.of();
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

    // Queues every zone chunk that is loaded by now but wasn't sent yet. Server side counterpart to
    // ChunkMapMixin.vista$flushPendingZoneChunks, but this one doesn't need the player to move.
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

    private static Set<GlobalPos> findViewFindersNeededForPlayer(ServerPlayer player) {
        Set<GlobalPos> result = new HashSet<>();
        ServerLevel level = player.serverLevel();
        Set<TVBlockEntity> candidates = loadedServerTVs.getOrDefault(level.dimension(), Set.of());
        collectViewFinders(candidates, player.getChunkTrackingView()::isInViewDistance,
                level, result, new HashSet<>(), 0);
        return result;
    }

    // Collects the ViewFinders reachable from the TVs inside inZone. At depth 0 that zone is the
    // player's view distance, deeper down it's a circle around a ViewFinder we just found, so TVs
    // sitting inside a camera zone can chain on to further ViewFinders.
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

    // Finds the real world chunk a ViewFinder destination maps to. Sublevels (movable ships) keep
    // their blocks at plot grid coords, so a ViewFinder inside one has to resolve to wherever the
    // ship actually sits in the world. Projecting out is a no-op outside a sublevel. Snapping to
    // chunk granularity (Y dropped) keeps small movements from churning the desired set and
    // re-sending packets, and nothing downstream needs finer than a ChunkPos anyway.
    //
    // Null when the pos is in a plot grid and the projection did nothing, meaning the sublevel is
    // held or gone. Plot grid coords must never reach setChunkForced or the zone system: vanilla
    // tickets in there fight Sable's own PlotChunkHolders and the shutdown drain loop never ends.
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

    private static void setChunksForceLoaded(ServerLevel level, BlockPos viewFinderPos, int radius, boolean force) {
        ChunkPos cp = new ChunkPos(viewFinderPos);
        ChunkPos.rangeClosed(cp, radius)
                .filter(p -> p.distanceSquared(cp) <= radius * radius)
                .forEach(p -> level.setChunkForced(p.x, p.z, force));
    }

    // drops the player's force load references on disconnect
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

    // Drops every force load ticket we still hold on server stop. setChunkForced writes into the
    // saved ForcedChunksSavedData, so anything still held at shutdown comes back next session with
    // no ref counts left to release it. Unforce before clearing the maps, they're static and would
    // otherwise leak between worlds in the same client session.
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
