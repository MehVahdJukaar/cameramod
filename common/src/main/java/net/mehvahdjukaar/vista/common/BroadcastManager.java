package net.mehvahdjukaar.vista.common;

import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.supplementaries.client.GlobeManager;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastProvider;
import net.mehvahdjukaar.vista.network.ClientBoundSyncBroadcastManagerPacket;
import net.mehvahdjukaar.vista.network.ModNetwork;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BroadcastManager extends SavedData {

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

    public static final String DATA_NAME = "broadcast_manager";

    //generate new from seed
    public BroadcastManager(long seed) {
    }

    //from tag
    public BroadcastManager(CompoundTag tag) {
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {

        Tag dataTag = CODEC.encodeStart(NbtOps.INSTANCE, this)
                .getOrThrow(false, s -> {
                    throw new IllegalStateException("Failed to encode BroadcastManager: " + s);
                });
        nbt.put(DATA_NAME, dataTag);
        return nbt;
    }

    //data received from network is stored here
    private static final BroadcastManager CLIENT_SIDE_INSTANCE = new BroadcastManager();

    public static BroadcastManager getInstance(Level world) {
        if (world instanceof ServerLevel server) {
            return world.getServer().overworld().getDataStorage().computeIfAbsent(BroadcastManager::new,
                    () -> new BroadcastManager(server.getSeed()),
                    DATA_NAME);
        } else {
            return CLIENT_SIDE_INSTANCE;
        }
    }

    public static void set(ServerLevel level, BroadcastManager pData) {
        level.getServer().overworld().getDataStorage().set(DATA_NAME, pData);
    }

    public void setClientData(Map<UUID, GlobalPos> data) {
        synchronized (lock) {
            uuidToPos.clear();
            uuidToPos.putAll(data);
            publishSnapshot();
        }
    }

    public void sync() {
        ModNetwork.CHANNEL.sendToAllClientPlayers(new ClientBoundSyncBroadcastManagerPacket(this.snapshot));
    }

    public void syncTo(ServerPlayer player) {
        ModNetwork.CHANNEL.sendToClientPlayer(player, new ClientBoundSyncBroadcastManagerPacket(this.snapshot));
    }

    public static void clearClientData() {
        CLIENT_SIDE_INSTANCE.setClientData(Map.of());
    }

}
