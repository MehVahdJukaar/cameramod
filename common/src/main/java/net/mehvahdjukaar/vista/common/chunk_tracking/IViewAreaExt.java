package net.mehvahdjukaar.vista.common.chunk_tracking;

// Lets the pinned section slots be rebuilt without a full allChanged().
public interface IViewAreaExt {
    void vista$rebuildPinnedSections();

    // ViewArea.setDirty is floorMod-indexed and can only address the torus grid, so block and light
    // updates in a far zone chunk have to come through here to refresh their mesh.
    void vista$setPinnedSectionDirty(int secX, int secY, int secZ, boolean reRenderOnMainThread);

    // Answers LevelRenderer.isSectionCompiled, the entity render gate, which otherwise goes through
    // the torus, never finds pinned sections, and skips every far-chunk entity.
    boolean vista$isPinnedSectionCompiled(int secX, int secY, int secZ);
}
