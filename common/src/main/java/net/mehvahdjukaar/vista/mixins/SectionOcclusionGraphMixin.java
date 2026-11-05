package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.chunk_tracking.IPinnableRenderSection;
import net.mehvahdjukaar.vista.client.renderer.FeedSectionOcclusionGraph;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
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
        boolean visible = original.call(centerX, centerZ, viewDistance, x, z)
                || VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.containsChunk(x, z);
        if (!visible || !((Object) this instanceof FeedSectionOcclusionGraph)) {
            return visible;
        }

        //feed renderdistance is in blocks and by itself only moves the fog. discard everything past it
        int feedChunks = Mth.ceil(Minecraft.getInstance().gameRenderer.getRenderDistance() / 16f) + 1;
        return ChunkTrackingView.isInViewDistance(centerX, centerZ, feedChunks, x, z);
    }

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
