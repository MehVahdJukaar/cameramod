package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.renderer.FeedSectionOcclusionGraph;
import net.mehvahdjukaar.vista.common.chunk_tracking.IPinnableRenderSection;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {

    @Shadow
    private ViewArea viewArea;

    @WrapOperation(
            method = "isInViewDistance",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkTrackingView;isInViewDistance(IIIII)Z"))
    private boolean vista$allowPinnedChunk(int centerX, int centerZ, int viewDistance, int x, int z,
                                           Operation<Boolean> original) {
        return original.call(centerX, centerZ, viewDistance, x, z)
                || VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.containsChunk(x, z);
    }

    // Seeds pinned sections as extra BFS roots, but only on feed graphs. The full BFS runs off
    // thread so isRenderingLiveFeed() is useless here; the instanceof check works from anywhere.
    // On a feed graph the camera is at the ViewFinder, so seeding is the only thing that gets
    // pinned sections into the BFS at all, since they sit outside the torus. On the player's graph
    // it would leak far-away sections into their own view whenever they looked at the ViewFinder.
    @SuppressWarnings("unchecked")
    @Inject(method = "initializeQueueForFullUpdate", at = @At("TAIL"))
    private void vista$seedPinnedSections(Camera camera, Queue nodeQueue, CallbackInfo ci) {
        if (!((Object) this instanceof FeedSectionOcclusionGraph)) return;
        if (this.viewArea == null) return;
        for (SectionRenderDispatcher.RenderSection section : this.viewArea.sections) {
            if (section instanceof IPinnableRenderSection ps && ps.vista$isPinned()) {
                nodeQueue.add(new SectionOcclusionGraph.Node(section, null, 0));
            }
        }
    }

}
