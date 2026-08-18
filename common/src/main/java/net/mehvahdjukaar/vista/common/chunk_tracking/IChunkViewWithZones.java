package net.mehvahdjukaar.vista.common.chunk_tracking;

// Duck-typed interface injected into ChunkTrackingView.Positioned by ChunkTrackingViewMixin.
// The zone data rides on the view object itself, so the same instance that ChunkMapMixin fills in
// comes back as the "old view" on the next call. Both views stay Positioned, which keeps
// ChunkTrackingView.difference on its bounding-box fast path instead of re-sending zone chunks
// every time the player moves.
public interface IChunkViewWithZones {
    ExtraChunkViewData vista$getExtraZones();
    void vista$setExtraZones(ExtraChunkViewData data);
}
