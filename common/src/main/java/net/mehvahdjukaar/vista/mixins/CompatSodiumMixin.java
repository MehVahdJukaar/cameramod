package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class CompatSodiumMixin {

    // A feed camera is parked at the viewfinder block's center. When that section is opaque (a
    // camera embedded in a hillside or wall), Sodium's occlusion BFS cannot propagate out of the
    // seed and returns no visible sections at all: the feed then draws only sky and clouds, which
    // reads as the TV flashing white. Which feeds break, and when, drifts with chunk rebuilds
    // around the camera, so it flickers instead of failing outright. Feeds render a small,
    // throttled distance with a static camera, so the octree-style flood fill costs little and
    // makes visibility deterministic.
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true, remap = false)
    private void vista$disableOcclusionCullingForFeeds(CallbackInfoReturnable<Boolean> cir) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            cir.setReturnValue(false);
        }
    }
}
