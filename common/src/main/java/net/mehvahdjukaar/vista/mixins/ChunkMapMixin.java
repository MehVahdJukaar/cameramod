package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.mehvahdjukaar.vista.common.chunk_tracking.IChunkViewWithZones;
import net.mehvahdjukaar.vista.common.chunk_tracking.ServerExtraChunkViewData;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow
    public abstract void markChunkPendingToSend(ServerPlayer player, ChunkPos chunkPos);

    @Shadow
    @Nullable
    public abstract LevelChunk getChunkToSend(long chunkPos);

    // The stored Positioned instance is reused as the next call's old view, so attaching the zone
    // data here means old and new carry identical sets and no chunk gets re-sent on every move.
    @Inject(method = "applyChunkTrackingView", at = @At("HEAD"))
    private void vista$attachZonesToView(ServerPlayer player, ChunkTrackingView view, CallbackInfo ci) {
        if (view instanceof IChunkViewWithZones zv) {
            zv.vista$setExtraZones(VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player));
        }
    }

    // Catches zone chunks that weren't loaded yet when the camera registered, so the packet handler's
    // markChunkPendingToSend silently failed, and that have since become available.
    @Inject(method = "applyChunkTrackingView", at = @At("RETURN"))
    private void vista$flushPendingZoneChunks(ServerPlayer player, ChunkTrackingView view, CallbackInfo ci) {
        ServerExtraChunkViewData data = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        if (data.getZones().isEmpty()) return;
        int flushed = 0;
        for (ChunkPos pos : data.getAllChunks()) {
            if (!view.isInViewDistance(pos.x, pos.z) && !data.isZoneChunkQueued(pos)) {
                if (this.getChunkToSend(pos.toLong()) != null) {
                    this.markChunkPendingToSend(player, pos);
                    data.markZoneChunkQueued(pos);
                    flushed++;
                }
            }
        }
        if (flushed > 0) {
            VistaMod.LOGGER.debug("[Vista/Chunks] applyChunkTrackingView flushed {} zone chunks for {}", flushed, player.getName().getString());
        }
    }

    // Block changes, BE updates and entity tracking all gate on isChunkTracked, which routes through
    // the contains() we deliberately leave unpatched (see ChunkTrackingViewMixin). Without this hook
    // zone chunks get their initial snapshot and nothing else: pistons freeze, entities never sync.
    @ModifyReturnValue(method = "isChunkTracked", at = @At("RETURN"))
    private boolean vista$trackZoneChunksForBroadcast(boolean original, ServerPlayer player, int x, int z) {
        if (original) return true;
        ExtraChunkViewData data = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        return data != null && data.containsChunk(x, z);
    }

    // don't tell the client to forget a chunk that's still in one of its camera zones
    @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
    private static void vista$preventDropCameraZoneChunk(ServerPlayer player, ChunkPos chunkPos, CallbackInfo ci) {
        ExtraChunkViewData data = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        if (data != null && data.containsChunk(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }
}
