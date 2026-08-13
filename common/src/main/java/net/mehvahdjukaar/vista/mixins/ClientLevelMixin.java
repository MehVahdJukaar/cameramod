package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.client.PinnedChunks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps pinned camera zone chunks ticking. unload() clears their block entity tickers, stops their
 * entities and disables their light, which is what made far feeds render a frozen scene. PinnedChunks
 * always drops a chunk from its map before unloading it, so a real unload still goes through.
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    private void vista$keepPinnedChunkAlive(LevelChunk chunk, CallbackInfo ci) {
        if (PinnedChunks.isPinned(chunk)) {
            ci.cancel();
        }
    }
}
