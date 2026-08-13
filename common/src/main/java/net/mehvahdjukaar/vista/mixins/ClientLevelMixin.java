package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaModClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps camera-zone chunks ticking after the client's chunk storage evicts them.
 * <p>
 * Storage is a floorMod torus, so a far zone chunk and a nearby one can share a slot. When the
 * nearby one streams in, replace() unloads the zone chunk: block entity tickers go, entities stop
 * ticking, light is disabled. Vista's pinned map keeps it rendering, so the feed shows a frozen
 * scene. Cancelling is safe because replace() only fires when the slot's chunk actually differs,
 * so this is always a collision and never a same-position refresh. Chunks whose zone is really
 * gone are no longer in the view data and unload normally.
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    private void vista$keepZoneChunkTicking(LevelChunk chunk, CallbackInfo ci) {
        ChunkPos pos = chunk.getPos();
        if (VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.containsChunk(pos.x, pos.z)) {
            ci.cancel();
        }
    }
}
