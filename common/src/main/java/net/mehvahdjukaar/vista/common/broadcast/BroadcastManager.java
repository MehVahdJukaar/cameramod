package net.mehvahdjukaar.vista.common.broadcast;

import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import net.mehvahdjukaar.moonlight.api.misc.WorldSavedData;
import net.mehvahdjukaar.moonlight.api.misc.WorldSavedDataType;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BroadcastManager extends WorldSavedData {

    public static BroadcastManager create(ServerLevel serverLevel) {
        return new BroadcastManager();
    }

    public static final Codec<BroadcastManager> CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, IBroadcastLocation.CODEC)
                    .xmap(
                            map -> {
                                BroadcastManager storage = new BroadcastManager();
                                map.forEach((uuid, pos) -> storage.addFeedInternal(uuid, pos, false));
                                storage.publishSnapshot();
                                return storage;
                            },
                            storage -> storage.snapshot
                    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BroadcastManager> STREAM_CODEC =
            ByteBufCodecs.map(
                    i -> new HashMap<>(),
                    UUIDUtil.STREAM_CODEC,
                    IBroadcastLocation.STREAM_CODEC
            ).map(
                    map -> {
                        BroadcastManager storage = new BroadcastManager();
                        map.forEach((uuid, pos) -> storage.addFeedInternal(uuid, pos, true));
                        storage.publishSnapshot();
                        return storage;
                    },
                    storage -> new HashMap<>(storage.snapshot)
            );

    /* -------------------- STATE -------------------- */

    private final Object lock = new Object();
    private final HashBiMap<UUID, IBroadcastLocation> uuidToPos = HashBiMap.create(); //thread safe, mutable
    private volatile Map<UUID, IBroadcastLocation> snapshot = Map.of(); //fast read only

    private BroadcastManager() {
    }

    /* -------------------- INTERNALS -------------------- */

    private void publishSnapshot() {
        snapshot = Map.copyOf(uuidToPos);
    }

    private boolean addFeedInternal(UUID feedUUID, IBroadcastLocation projectorPos, boolean trusted) {
        boolean changed = false;

        synchronized (lock) {
            boolean occupied = uuidToPos.inverse().containsKey(projectorPos);
            if (!occupied || trusted) {
                uuidToPos.forcePut(feedUUID, projectorPos);
                publishSnapshot();
                changed = true;
            }
        }

        return changed;
    }

    /* -------------------- PUBLIC API (WRITES) -------------------- */

    public void linkFeed(UUID feedUUID, IBroadcastLocation projectorPos) {
        boolean changed = false;

        synchronized (lock) {
            IBroadcastLocation old = uuidToPos.get(feedUUID);
            if (!projectorPos.equals(old)) {
                uuidToPos.forcePut(feedUUID, projectorPos);
                publishSnapshot();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
            sync();
        }
    }


    public void unlinkFeed(IBroadcastLocation projectorPos) {
        boolean changed = false;

        synchronized (lock) {
            UUID old = uuidToPos.inverse().remove(projectorPos);
            if (old != null) {
                publishSnapshot();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
            sync();
        }
    }

    public void unlinkFeed(UUID feedUUID) {
        boolean changed = false;

        synchronized (lock) {
            if (uuidToPos.remove(feedUUID) != null) {
                publishSnapshot();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
            sync();
        }
    }

    /* -------------------- PUBLIC API (READS - FAST) -------------------- */

    @Nullable
    public IBroadcastLocation getFeedLocationById(UUID viewFinderUUID) {
        return snapshot.get(viewFinderUUID);
    }

    @Nullable
    public UUID getIdOfFeedAt(IBroadcastLocation from) {
        for (var e : snapshot.entrySet()) {
            if (e.getValue().equals(from)) {
                return e.getKey();
            }
        }
        return null;
    }

    public Iterable<Map.Entry<UUID, IBroadcastLocation>> getAll() {
        return snapshot.entrySet();
    }

    @Nullable
    public IBroadcastSource getBroadcast(@NotNull UUID feedId, boolean clientSide) {
        IBroadcastLocation pos = getFeedLocationById(feedId);
        if (pos == null) return null;

        var result = pos.get(clientSide);
        if (result.isValid()) {
            return result.getValue();
        } else {
            // Do NOT auto-unlink here. getBroadcast is a hot read called every tick
            // (enderman observation, feed rendering, ...). pos.get() returns "invalid"
            // for transient states too: a chunk that is loaded mid-tick before its block
            // entities are placed, or a position whose level/sub-level doesn't resolve
            // this tick. Unlinking on that permanently destroys a valid TV<->ViewFinder
            // link and syncs the deletion to clients, leaving the feed stuck on the
            // disconnected (white) overlay. Links are removed explicitly when the
            // ViewFinder / WaveGate block is broken (see ViewFinderBlock, WaveGateBlock,
            // IBroadcastSource#setRemoved).
            return null;
        }
    }

    /* -------------------- WORLD DATA -------------------- */

    @Override
    public WorldSavedDataType<BroadcastManager> getType() {
        return VistaMod.VIEWFINDER_CONNECTION;
    }

    public static BroadcastManager getInstance(Level level) {
        return VistaMod.VIEWFINDER_CONNECTION.getData(level);
    }

    /**
     * Returns the manager only when the server's levels are fully constructed and their saved-data
     * storage is bound, null otherwise. During server boot some mods (e.g. Sable) force-load
     * sub-level chunks from inside the {@code ServerLevel} constructor, when
     * {@code server.overworld()} and its data storage are not available yet; linking must be
     * deferred in that window or {@code WorldSavedDataType#getData} throws a NullPointerException.
     */
    @Nullable
    public static BroadcastManager getInstanceIfReady(Level level) {
        if (level instanceof ServerLevel sl) {
            ServerLevel target = sl.getServer().overworld();
            if (target != null && target.getDataStorage() != null) {
                return getInstance(level);
            }
        }
        return null;
    }

}
