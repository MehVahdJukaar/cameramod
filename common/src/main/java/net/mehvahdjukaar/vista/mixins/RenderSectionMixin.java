package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mehvahdjukaar.vista.common.chunk_tracking.IPinnableRenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// Per-section pinned flag, set by ViewAreaMixin when it appends the extra-zone slots, so pinned
// sections can be identified without global state.
// Do NOT @Shadow the final index field: Mixin merges its initializer into every constructor,
// zeroing all indices and breaking SectionToNodeMap.
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection")
public class RenderSectionMixin implements IPinnableRenderSection {

    @Unique
    private boolean vista$pinned = false;

    @Override
    public boolean vista$isPinned() {
        return vista$pinned;
    }

    @Override
    public void vista$setPinned(boolean pinned) {
        this.vista$pinned = pinned;
    }

    @ModifyReturnValue(method = "hasAllNeighbors", at = @At("RETURN"))
    private boolean vista$pinnedAlwaysHasNeighbors(boolean original) {
        return original || vista$pinned;
    }
}
