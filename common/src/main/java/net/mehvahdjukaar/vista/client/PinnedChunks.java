package net.mehvahdjukaar.vista.client;

import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Camera zone chunks the client holds outside of ClientChunkCache. Its storage is a floorMod'd array
 * exactly as wide as the in range window, so every slot already belongs to one of the player's own
 * chunks and a far zone chunk written there would evict a real one.
 * <p>
 * Read from chunk building threads, hence the concurrent map.
 */
public class PinnedChunks {

    private static final Map<Long, LevelChunk> PINNED = new ConcurrentHashMap<>();

    // Sodium runs its own chunk renderer and never builds a ViewArea, so the pinned sections that make
    // far chunks show up on a feed are never created. No point asking the server for the chunks.
    public static boolean isSupported() {
        return !CompatHandler.SODIUM;
    }

    @Nullable
    public static LevelChunk get(int chunkX, int chunkZ) {
        if (PINNED.isEmpty()) return null;
        return PINNED.get(ChunkPos.asLong(chunkX, chunkZ));
    }


    public static boolean isPinned(LevelChunk chunk) {
        if (PINNED.isEmpty()) return false;
        return PINNED.get(chunk.getPos().toLong()) == chunk;
    }

    public static Map<Long, LevelChunk> view() {
        return Collections.unmodifiableMap(PINNED);
    }

    // takes over a chunk the storage refused
    public static void pin(LevelChunk chunk) {
        LevelChunk old = PINNED.put(chunk.getPos().toLong(), chunk);
        if (old != null && old != chunk) unload(old);
    }

    // hands one back to the storage
    public static void unpin(int chunkX, int chunkZ) {
        if (PINNED.isEmpty()) return;
        LevelChunk chunk = PINNED.remove(ChunkPos.asLong(chunkX, chunkZ));
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
    }

    public static void clear() {
        if (PINNED.isEmpty()) return;
        List<LevelChunk> chunks = new ArrayList<>(PINNED.values());
        PINNED.clear();
        for (LevelChunk chunk : chunks) {
            unload(chunk);
        }
    }

    // has to run once the chunk is out of the map, ClientLevelMixin cancels unload for pinned ones
    private static void unload(LevelChunk chunk) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && chunk.getLevel() == level) {
            level.unload(chunk);
        }
    }
}
