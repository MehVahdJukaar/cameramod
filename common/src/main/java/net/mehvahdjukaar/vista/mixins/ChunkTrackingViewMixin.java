package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.mehvahdjukaar.vista.common.chunk_tracking.IChunkViewWithZones;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// Carries the player's zone data on their tracking view. ChunkMapMixin sets it on the new view
// right before difference runs.
// Only forEach is patched, on purpose. Patching contains would make both old and new view report
// zone chunks as already tracked, so walking into the zone shows no difference on the fast path and
// the chunk never gets sent. With forEach alone, zone chunks go out on the fallback path (join,
// teleport) and then act like any other chunk coming into normal view distance.
@Mixin(ChunkTrackingView.Positioned.class)
public class ChunkTrackingViewMixin implements IChunkViewWithZones {

    @Unique
    private ExtraChunkViewData vista$zones;

    @Override
    public ExtraChunkViewData vista$getExtraZones() {
        return vista$zones;
    }

    @Override
    public void vista$setExtraZones(ExtraChunkViewData data) {
        this.vista$zones = data;
    }

    // Appends zone chunks outside normal view distance after the regular iteration. Only the
    // fallback path in difference() reaches this; normal movement takes the bounding box fast path.
    @Inject(method = "forEach", at = @At("RETURN"))
    private void vista$addExtraZoneChunks(Consumer<ChunkPos> action, CallbackInfo ci) {
        if (vista$zones == null || vista$zones.getZones().isEmpty()) return;
        ChunkTrackingView self = (ChunkTrackingView) this;
        int added = 0;
        for (ChunkPos pos : vista$zones.getAllChunks()) {
            if (!self.isInViewDistance(pos.x, pos.z)) {
                action.accept(pos);
                added++;
            }
        }
        if (added > 0) {
            VistaMod.LOGGER.info(
                    "[Vista/Chunks] ChunkTrackingView.forEach emitting {} extra zone chunks into tracking diff", added);
        }
    }
}
