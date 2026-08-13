package net.mehvahdjukaar.vista.common.chunk_tracking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extra chunk zones that get pinned into the ViewArea and stay visible no matter where the player
 * is or where they are looking. Each zone is a circle of chunks around a fixed chunk position.
 * This class only holds the geometry and the codecs, the server side bookkeeping (force loading,
 * send queue) is in ServerExtraChunkViewData, which is what the per player attachment stores.
 */
public class ExtraChunkViewData {

    public record Zone(ChunkPos center, byte radius) {

        public static final Codec<Zone> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.xmap(ChunkPos::new, ChunkPos::toLong).fieldOf("center").forGetter(Zone::center),
                Codec.BYTE.fieldOf("radius").forGetter(Zone::radius)
        ).apply(inst, Zone::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Zone> STREAM_CODEC = StreamCodec.of(
                (buf, zone) -> {
                    buf.writeLong(zone.center.toLong());
                    buf.writeByte(zone.radius);
                },
                buf -> new Zone(new ChunkPos(buf.readLong()), buf.readByte())
        );

        public boolean contains(int chunkX, int chunkZ) {
            int dx = chunkX - center.x;
            int dz = chunkZ - center.z;
            return dx * dx + dz * dz <= radius * radius;
        }

        public Set<ChunkPos> chunks() {
            Set<ChunkPos> result = new HashSet<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        result.add(new ChunkPos(center.x + dx, center.z + dz));
                    }
                }
            }
            return result;
        }
    }

    public static final Codec<ExtraChunkViewData> CODEC = Zone.CODEC.listOf().xmap(
            zones -> {
                ExtraChunkViewData d = new ExtraChunkViewData();
                d.zones.addAll(zones);
                d.rebuildCache();
                return d;
            },
            d -> List.copyOf(d.zones)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtraChunkViewData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ExtraChunkViewData decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            ExtraChunkViewData data = new ExtraChunkViewData();
            for (int i = 0; i < size; i++) {
                data.zones.add(Zone.STREAM_CODEC.decode(buf));
            }
            data.rebuildCache();
            return data;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ExtraChunkViewData data) {
            buf.writeVarInt(data.zones.size());
            for (Zone z : data.zones) {
                Zone.STREAM_CODEC.encode(buf, z);
            }
        }
    };

    protected final List<Zone> zones = new CopyOnWriteArrayList<>();

    // flat set of every chunk across all zones, so containsChunk is O(1)
    private Set<Long> cachedChunkLongs = Set.of();
    private Set<ChunkPos> cachedChunkSet = Set.of();

    public ExtraChunkViewData() {
    }

    public void addZone(ChunkPos center, int radius) {
        zones.add(new Zone(center, (byte) radius));
        onZonesChanged();
    }

    public void removeZone(ChunkPos center) {
        zones.removeIf(z -> z.center().equals(center));
        onZonesChanged();
    }

    public void clearZones() {
        zones.clear();
        onZonesChanged();
    }

    // subclasses override to drop their own derived caches
    protected void onZonesChanged() {
        rebuildCache();
    }

    private void rebuildCache() {
        if (zones.isEmpty()) {
            cachedChunkLongs = Set.of();
            cachedChunkSet = Set.of();
            return;
        }
        Set<Long> longs = new HashSet<>();
        for (Zone zone : zones) {
            for (ChunkPos cp : zone.chunks()) {
                longs.add(cp.toLong());
            }
        }
        cachedChunkLongs = longs;
        Set<ChunkPos> chunkSet = new HashSet<>(longs.size());
        longs.forEach(l -> chunkSet.add(new ChunkPos(l)));
        cachedChunkSet = Collections.unmodifiableSet(chunkSet);
    }

    public List<Zone> getZones() {
        return Collections.unmodifiableList(zones);
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        if (cachedChunkLongs.isEmpty()) return false;
        return cachedChunkLongs.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    // cached, don't mutate
    public Set<ChunkPos> getAllChunks() {
        return cachedChunkSet;
    }

}
