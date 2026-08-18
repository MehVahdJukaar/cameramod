package net.mehvahdjukaar.vista.common.chunk_tracking;

// Duck-typed interface injected into SectionRenderDispatcher.RenderSection by RenderSectionMixin.
// Holds whether ViewArea created this section as a pinned extra-zone slot. Keeping the flag on the
// section drops the global PinnedSections set, so ownership stays with the ViewArea that made it.
public interface IPinnableRenderSection {
    boolean vista$isPinned();
    void vista$setPinned(boolean pinned);
}
