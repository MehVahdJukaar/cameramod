package net.mehvahdjukaar.vista.common;

import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import net.mehvahdjukaar.moonlight.api.misc.WorldSavedData;
import net.mehvahdjukaar.moonlight.api.misc.WorldSavedDataType;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastProvider;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
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
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, GlobalPos.CODEC)
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
            (StreamCodec) ByteBufCodecs.map(
                    i -> new HashMap<>(),
                    UUIDUtil.STREAM_CODEC,
                    GlobalPos.STREAM_CODEC
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
    private final HashBiMap<UUID, GlobalPos> uuidToPos = HashBiMap.create(); //thread safe, mutable
    private volatile Map<UUID, GlobalPos> snapshot = Map.of(); //fast read only

    private BroadcastManager() {
    }

    /* -------------------- INTERNALS -------------------- */

    private void publishSnapshot() {
        snapshot = Map.copyOf(uuidToPos);
    }

    private boolean addFeedInternal(UUID viewFinderUUID, GlobalPos projectorPos, boolean trusted) {
        boolean changed = false;

        synchronized (lock) {
            boolean occupied = uuidToPos.inverse().containsKey(projectorPos);
            if (!occupied || trusted) {
                uuidToPos.forcePut(viewFinderUUID, projectorPos);
                publishSnapshot();
                changed = true;
            }
        }

        return changed;
    }

    /* -------------------- PUBLIC API (WRITES) -------------------- */

    public void linkFeed(UUID viewFinderUUID, GlobalPos projectorPos) {
        boolean changed = false;

        synchronized (lock) {
            GlobalPos old = uuidToPos.get(viewFinderUUID);
            if (!projectorPos.equals(old)) {
                uuidToPos.forcePut(viewFinderUUID, projectorPos);
                publishSnapshot();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
            sync();
        }
    }

    public void unlinkFeed(GlobalPos projectorPos) {
        boolean changed = false;

        synchronized (lock) {
            UUID id = uuidToPos.inverse().remove(projectorPos);
            if (id != null) {
                publishSnapshot();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
            sync();
        }
    }

    public void unlinkFeed(UUID viewFinderUUID) {
        boolean changed = false;

        synchronized (lock) {
            if (uuidToPos.remove(viewFinderUUID) != null) {
                publishSnapshot();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
            sync();
        }
    }

    /* -------------------- PUBLIC API (READS – FAST) -------------------- */

    @Nullable
    public GlobalPos getBroadcastOriginById(UUID viewFinderUUID) {
        return snapshot.get(viewFinderUUID);
    }

    @Nullable
    public UUID getIdOfFeedAt(GlobalPos from) {
        for (var e : snapshot.entrySet()) {
            if (e.getValue().equals(from)) {
                return e.getKey();
            }
        }
        return null;
    }

    public Iterable<Map.Entry<UUID, GlobalPos>> getAll() {
        return snapshot.entrySet();
    }

    @Nullable
    public IBroadcastProvider getBroadcast(@NotNull UUID feedId, boolean clientSide) {
        if(feedId == null){
            int aa = 1;
            return null;
        }
        GlobalPos pos = snapshot.get(feedId);
        if (pos == null) return null;

        Level otherLevel = clientSide
                ? VistaModClient.getLocalLevelByDimension(pos.dimension())
                : PlatHelper.getCurrentServer().getLevel(pos.dimension());

        if (otherLevel != null && otherLevel.isLoaded(pos.pos())) {
            if (otherLevel.getBlockEntity(pos.pos()) instanceof IBroadcastProvider provider) {
                return provider;
            } else if (!clientSide) {
                unlinkFeed(feedId);
            }
        }
        return null;
    }

    /* -------------------- WORLD DATA -------------------- */

    @Override
    public WorldSavedDataType<BroadcastManager> getType() {
        return VistaMod.VIEWFINDER_CONNECTION;
    }

    public static BroadcastManager getInstance(Level level) {
        return VistaMod.VIEWFINDER_CONNECTION.getData(level);
    }

}
