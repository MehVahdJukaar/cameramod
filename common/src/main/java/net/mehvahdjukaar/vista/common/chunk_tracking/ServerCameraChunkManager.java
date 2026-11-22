package net.mehvahdjukaar.vista.common.chunk_tracking;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.broadcast.IBroadcastLocation;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.integration.sable.SableCompat;
import net.mehvahdjukaar.vista.network.ClientBoundSyncExtraChunksPacket;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;

public class ServerCameraChunkManager {

    private static final int SCAN_INTERVAL = 40;
    private static final int SEND_INTERVAL = 5;
    private static final int MAX_TV_CHAIN_DEPTH = 2;
    private static final int CHAINED_TV_SCAN_RADIUS = 4;

    private static final Map<ResourceKey<Level>, Set<TVBlockEntity>> loadedTvsByDimension = new HashMap<>();
    private static final Map<GlobalPos, Integer> forceLoadRefCountByViewFinder = new HashMap<>();

    public static void trackTv(TVBlockEntity tv) {
        if (tv.getLevel() instanceof ServerLevel sl) {
            loadedTvsByDimension.computeIfAbsent(sl.dimension(), k -> new HashSet<>()).add(tv);
        }
    }

    public static void untrackTv(TVBlockEntity tv) {
        if (tv.getLevel() instanceof ServerLevel sl) {
            Set<TVBlockEntity> tvs = loadedTvsByDimension.get(sl.dimension());
            if (tvs != null) tvs.remove(tv);
        }
    }

    public static void onServerPlayerTick(ServerPlayer player) {
        int zoneRadius = CommonConfigs.SEND_CHUNKS_VIEWED_BY_VIEW_FINDER.get();
        boolean sendsChunks = zoneRadius > 0;
        boolean forceLoadsChunks = CommonConfigs.LOAD_CHUNKS_VIEWED_BY_VIEW_FINDER.get() || PlatHelper.isDev();
        if (!sendsChunks && !forceLoadsChunks) return;

        long staggeredTime = player.serverLevel().getGameTime() + player.getId();
        if (staggeredTime % SEND_INTERVAL == 0) {
            ChunkMap chunkMap = player.serverLevel().getChunkSource().chunkMap;
            sendLoadedZoneChunks(chunkMap, player, player.getChunkTrackingView());
        }
        if (staggeredTime % SCAN_INTERVAL == 0) {
            refreshWatchedViewFinders(player, zoneRadius, sendsChunks, forceLoadsChunks);
        }
    }

    private static void refreshWatchedViewFinders(ServerPlayer player, int zoneRadius,
                                                  boolean sendsChunks, boolean forceLoadsChunks) {
        ServerExtraChunkViewData viewData = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        Set<GlobalPos> wanted = findViewFindersWatchedBy(player);
        Set<GlobalPos> watched = viewData.getWatchedViewFinders();
        if (wanted.equals(watched)) return;

        MinecraftServer server = player.getServer();
        if (forceLoadsChunks) {
            for (GlobalPos viewFinder : wanted) {
                if (!watched.contains(viewFinder)) acquireForceLoad(server, viewFinder, zoneRadius);
            }
            for (GlobalPos viewFinder : watched) {
                if (!wanted.contains(viewFinder)) releaseForceLoad(server, viewFinder, zoneRadius);
            }
        }
        viewData.setWatchedViewFinders(wanted);

        //TODO: send chunks even if outside of current dim.
        viewData.clearZones();
        if (sendsChunks) {
            for (GlobalPos viewFinder : wanted) {
                if (viewFinder.dimension().equals(player.level().dimension())) {
                    viewData.addZone(new ChunkPos(viewFinder.pos()), zoneRadius);
                }
            }
        }

        NetworkHelper.sendToClientPlayer(player, new ClientBoundSyncExtraChunksPacket(viewData));
        VistaMod.LOGGER.debug("{} now watches {} view finders, {} in its dimension",
                player.getName().getString(), wanted.size(), viewData.getZones().size());
    }

    public static void sendLoadedZoneChunks(ChunkMap chunkMap, ServerPlayer player, ChunkTrackingView view) {
        ServerExtraChunkViewData viewData = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        if (viewData.getZones().isEmpty()) return;

        for (ChunkPos pos : viewData.getAllChunks()) {
            if (view.isInViewDistance(pos.x, pos.z) || viewData.isZoneChunkQueued(pos)) continue;
            boolean loadedYet = chunkMap.getChunkToSend(pos.toLong()) != null;
            if (!loadedYet) continue;
            chunkMap.markChunkPendingToSend(player, pos);
            viewData.markZoneChunkQueued(pos);
        }
    }

    private static Set<GlobalPos> findViewFindersWatchedBy(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Set<TVBlockEntity> loadedTvs = loadedTvsByDimension.getOrDefault(level.dimension(), Set.of());
        Set<GlobalPos> found = new HashSet<>();
        collectViewFindersShownByTvs(level, loadedTvs, player.getChunkTrackingView()::isInViewDistance,
                found, new HashSet<>(), 0);
        return found;
    }

    private static void collectViewFindersShownByTvs(ServerLevel level, Set<TVBlockEntity> loadedTvs,
                                                     BiPredicate<Integer, Integer> isChunkInArea,
                                                     Set<GlobalPos> found, Set<TVBlockEntity> visitedTvs, int chainDepth) {
        if (chainDepth >= MAX_TV_CHAIN_DEPTH) return;
        BroadcastManager broadcasts = BroadcastManager.getInstance(level);
        List<GlobalPos> foundAtThisDepth = new ArrayList<>();

        for (TVBlockEntity tv : loadedTvs) {
            if (tv.isRemoved() || visitedTvs.contains(tv)) continue;
            ChunkPos tvChunk = SableCompat.projectChunkOutOfSubLevel(level, tv.getBlockPos());
            // TV on a Sable sublevel whose projection no-ops (sublevel held/removed): stale, unreachable
            if (tvChunk == null || !isChunkInArea.test(tvChunk.x, tvChunk.z)) continue;
            visitedTvs.add(tv);

            GlobalPos viewFinder = findViewFinderShownBy(tv, broadcasts, level.getServer());
            if (viewFinder == null) continue;
            if (viewFinder.dimension().equals(level.dimension())) {
                ChunkPos viewFinderChunk = new ChunkPos(viewFinder.pos());
                boolean alreadyBeingSent = isChunkInArea.test(viewFinderChunk.x, viewFinderChunk.z);
                if (alreadyBeingSent) continue;
            }
            if (found.add(viewFinder)) foundAtThisDepth.add(viewFinder);
        }

        for (GlobalPos viewFinder : foundAtThisDepth) {
            if (!viewFinder.dimension().equals(level.dimension())) continue;
            ChunkPos center = new ChunkPos(viewFinder.pos());
            BiPredicate<Integer, Integer> isNearViewFinder = (x, z) -> center.getChessboardDistance(x, z) <= CHAINED_TV_SCAN_RADIUS;
            collectViewFindersShownByTvs(level, loadedTvs, isNearViewFinder, found, visitedTvs, chainDepth + 1);
        }
    }

    @Nullable
    private static GlobalPos findViewFinderShownBy(TVBlockEntity tv, BroadcastManager broadcasts, MinecraftServer server) {
        UUID feedId = tv.getViewingFeedId();
        if (feedId == null) return null;
        IBroadcastLocation feedLocation = broadcasts.getFeedLocationById(feedId);
        if (feedLocation == null) return null;
        GlobalPos viewFinder = feedLocation.getChunkSendPosition();
        if (viewFinder == null) return null;

        //project against the ViewFinder's OWN level (it may be cross-dimension).
        ServerLevel viewFinderLevel = server.getLevel(viewFinder.dimension());
        if (viewFinderLevel == null) return viewFinder;
        ChunkPos chunk = SableCompat.projectChunkOutOfSubLevel(viewFinderLevel, viewFinder.pos());
        if (chunk == null) return null;
        return GlobalPos.of(viewFinder.dimension(), chunk.getWorldPosition());
    }

    private static void acquireForceLoad(MinecraftServer server, GlobalPos viewFinder, int zoneRadius) {
        int refs = forceLoadRefCountByViewFinder.merge(viewFinder, 1, Integer::sum);
        if (refs == 1) setZoneChunksForced(server, viewFinder, zoneRadius, true);
    }

    private static void releaseForceLoad(MinecraftServer server, GlobalPos viewFinder, int zoneRadius) {
        Integer refs = forceLoadRefCountByViewFinder.get(viewFinder);
        if (refs == null) return;
        if (refs > 1) {
            forceLoadRefCountByViewFinder.put(viewFinder, refs - 1);
        } else {
            forceLoadRefCountByViewFinder.remove(viewFinder);
            setZoneChunksForced(server, viewFinder, zoneRadius, false);
        }
    }

    private static void setZoneChunksForced(MinecraftServer server, GlobalPos viewFinder, int zoneRadius, boolean forced) {
        ServerLevel level = server.getLevel(viewFinder.dimension());
        if (level == null) return;
        var zone = new ExtraChunkViewData.Zone(new ChunkPos(viewFinder.pos()), (byte) zoneRadius);
        for (ChunkPos pos : zone.chunks()) {
            level.setChunkForced(pos.x, pos.z, forced);
        }
    }

    public static void onPlayerLeave(ServerPlayer player) {
        int zoneRadius = CommonConfigs.SEND_CHUNKS_VIEWED_BY_VIEW_FINDER.get();
        for (GlobalPos viewFinder : VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player).getWatchedViewFinders()) {
            releaseForceLoad(player.getServer(), viewFinder, zoneRadius);
        }
    }

    public static void clearAll(MinecraftServer server) {
        int zoneRadius = CommonConfigs.SEND_CHUNKS_VIEWED_BY_VIEW_FINDER.get();
        for (GlobalPos viewFinder : forceLoadRefCountByViewFinder.keySet()) {
            setZoneChunksForced(server, viewFinder, zoneRadius, false);
        }
        forceLoadRefCountByViewFinder.clear();
        loadedTvsByDimension.clear();
    }
}
