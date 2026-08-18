package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.PinnedChunks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReferenceArray;

// Keeps far camera zone chunks out of the storage array. The array is indexed
// floorMod(x/z, viewRange) and the in-range window is exactly viewRange wide, so every slot already
// belongs to one of the player's own chunks. A far zone chunk stored here evicts the real chunk at
// the same residue, unloading it and leaving a hole in the world until the server resends it. Those
// chunks go to PinnedChunks instead.
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public class ClientChunkCacheStorageMixin {

    @Shadow
    @Final
    AtomicReferenceArray<LevelChunk> chunks;
    @Shadow
    @Final
    int chunkRadius;
    @Shadow
    volatile int viewCenterX;
    @Shadow
    volatile int viewCenterZ;

    // replaceWithPacketData bails out before building the chunk if this says no, so zone chunks still
    // have to pass. The write is what gets stopped, in replace().
    @Inject(method = "inRange", at = @At("HEAD"), cancellable = true)
    private void vista$alwaysInRangeForPinnedZone(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.containsChunk(x, z)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "replace(ILnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("HEAD"), cancellable = true)
    private void vista$keepZoneChunksOutOfArray(int chunkIndex, LevelChunk chunk, CallbackInfo ci) {
        if (chunk != null) {
            ChunkPos pos = chunk.getPos();
            if (vista$isFarZoneChunk(pos)) {
                PinnedChunks.pin(chunk);
                ci.cancel();
                return;
            }
            // walked close enough that it is an ordinary chunk now, let the storage own it
            PinnedChunks.unpin(pos.x, pos.z);
        }

        // The player walking away can push a zone chunk out of range and then hand its slot to a real
        // chunk. Grab it before replace() unloads it or the feed loses that chunk for good.
        LevelChunk evicted = this.chunks.get(chunkIndex);
        if (evicted != null && evicted != chunk && vista$isFarZoneChunk(evicted.getPos())) {
            PinnedChunks.pin(evicted);
        }
    }

    @Unique
    private boolean vista$isFarZoneChunk(ChunkPos pos) {
        return !vista$inNormalRange(pos.x, pos.z)
                && VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.containsChunk(pos.x, pos.z);
    }

    // what inRange would say without our own override
    @Unique
    private boolean vista$inNormalRange(int x, int z) {
        return Math.abs(x - this.viewCenterX) <= this.chunkRadius
                && Math.abs(z - this.viewCenterZ) <= this.chunkRadius;
    }
}
