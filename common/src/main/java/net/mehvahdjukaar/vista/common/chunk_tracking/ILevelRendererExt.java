package net.mehvahdjukaar.vista.common.chunk_tracking;

// Injected onto LevelRenderer to expose a cheaper allChanged() that only rebuilds the pinned
// (extra-zone) render sections.
public interface ILevelRendererExt {
    /**
     * Rebuilds only the pinned section slots in the ViewArea and invalidates
     * the occlusion graph so the BFS picks them up, without destroying any
     * existing compiled section geometry.
     */
    void vista$refreshPinnedSections();
}
