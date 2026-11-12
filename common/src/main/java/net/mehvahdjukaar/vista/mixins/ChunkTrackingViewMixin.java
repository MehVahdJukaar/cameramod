package net.mehvahdjukaar.vista.mixins;

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

@Mixin(ChunkTrackingView.Positioned.class)
public class ChunkTrackingViewMixin implements IChunkViewWithZones {

    @Unique
    private ExtraChunkViewData vista$extraZones;

    @Override
    public void vista$setExtraZones(ExtraChunkViewData zones) {
        this.vista$extraZones = zones;
    }

    @Inject(method = "forEach", at = @At("RETURN"))
    private void vista$addZoneChunks(Consumer<ChunkPos> action, CallbackInfo ci) {
        if (vista$extraZones == null) return;
        for (ChunkPos pos : vista$extraZones.getAllChunks()) {
            if (!((ChunkTrackingView) this).isInViewDistance(pos.x, pos.z)) {
                action.accept(pos);
            }
        }
    }
}
