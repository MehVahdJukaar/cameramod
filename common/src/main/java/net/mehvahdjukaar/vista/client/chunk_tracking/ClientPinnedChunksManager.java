package net.mehvahdjukaar.vista.client.chunk_tracking;

import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPinnedChunksManager {

    private static final Map<Long, LevelChunk> PINNED = new ConcurrentHashMap<>();
    //chunk lookups hit this on every block read, a volatile read beats isEmpty on a concurrent map
    private static volatile boolean anyPinned = false;

    public static boolean clientWantsZoneChunksSent() {
        //sodium does its own stuff.
        return !CompatHandler.SODIUM;
    }

    @Nullable
    public static LevelChunk get(int chunkX, int chunkZ) {
        if (!anyPinned) return null;
        return PINNED.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static boolean isPinned(LevelChunk chunk) {
        if (!anyPinned) return false;
        return PINNED.get(chunk.getPos().toLong()) == chunk;
    }

    public static Map<Long, LevelChunk> view() {
        return Collections.unmodifiableMap(PINNED);
    }

    public static void pin(LevelChunk chunk) {
        LevelChunk old = PINNED.put(chunk.getPos().toLong(), chunk);
        anyPinned = true;
        if (old != null && old != chunk) unload(old);
    }

    public static void unpin(int chunkX, int chunkZ) {
        if (PINNED.isEmpty()) return;
        LevelChunk chunk = PINNED.remove(ChunkPos.asLong(chunkX, chunkZ));
        anyPinned = !PINNED.isEmpty();
        if (chunk != null) unload(chunk);
    }

    public static void keepOnly(Set<ChunkPos> zoneChunks) {
        if (PINNED.isEmpty()) return;
        var it = PINNED.entrySet().iterator();
        while (it.hasNext()) {
            LevelChunk chunk = it.next().getValue();
            if (!zoneChunks.contains(chunk.getPos())) {
                it.remove();
                unload(chunk);
            }
        }
        anyPinned = !PINNED.isEmpty();
    }

    public static void clear() {
        if (PINNED.isEmpty()) return;
        List<LevelChunk> chunks = new ArrayList<>(PINNED.values());
        PINNED.clear();
        anyPinned = false;
        for (LevelChunk chunk : chunks) {
            unload(chunk);
        }
    }

    private static void unload(LevelChunk chunk) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && chunk.getLevel() == level) {
            level.unload(chunk);
        }
    }
}
