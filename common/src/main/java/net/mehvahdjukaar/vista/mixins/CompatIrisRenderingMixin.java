package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(IrisRenderingPipeline.class)
public class CompatIrisRenderingMixin {

    // Feed passes used to skip shadow rendering entirely (a leftover from the mirror era's global
    // state hacks). That leaves the feed sampling a shadow map centered on the PLAYER: pack fog and
    // volumetric light wash the whole feed white whenever the player walks away from the TV, and
    // recover when they come back into coverage. Render shadows for the feed camera like any other
    // pass; each feed pipeline owns its shadow targets, so there is nothing to clobber.
    @WrapWithCondition(method = "renderShadows", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V"))
    private boolean vista$renderShadowsForFeedCamera(ShadowRenderer instance, LevelRendererAccessor fullyBufferedMultiBufferSource, Camera camera) {
        return true;
    }

    // The first beginLevelRendering on a fresh pipeline calls allChanged(), releasing every section's
    // VertexBuffer, which mid-feed tears down the geometry we're drawing. Deferring it to the end of
    // the outermost feed (IrisCompat.runPendingWorldRebuild) instead of dropping it: the rebuild is
    // what backfills the pack's block-id data into sections meshed before the pipeline existed, and
    // without it those sections render untinted until a random block update rebuilds them.
    @WrapWithCondition(method = "beginLevelRendering", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;allChanged()V"))
    private boolean vista$skipFirstFrameAllChanged(LevelRenderer instance) {
        if (IrisCompat.isFeedRendering()) {
            IrisCompat.scheduleWorldRebuild();
            return false;
        }
        return true;
    }
}
