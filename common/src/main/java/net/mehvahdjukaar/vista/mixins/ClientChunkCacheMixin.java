package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.client.PinnedChunks;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Resolves camera zone chunks that live outside the circular buffer. See PinnedChunks for why they
// aren't in it; ClientChunkCacheStorageMixin is what keeps them out.
@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("HEAD"), cancellable = true)
    private void vista$getPinnedChunk(int x, int z, ChunkStatus status, boolean require,
            CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk chunk = PinnedChunks.get(x, z);
        if (chunk != null) {
            cir.setReturnValue(chunk);
        }
    }

    // vanilla resolves the chunk through the array and would just log "not present" for a pinned one
    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void vista$replacePinnedBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
        LevelChunk chunk = PinnedChunks.get(x, z);
        if (chunk != null) {
            chunk.replaceBiomes(buffer);
            ci.cancel();
        }
    }
}
