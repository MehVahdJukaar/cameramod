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

    @WrapWithCondition(method = "renderShadows", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V"))
    private boolean vista$cockblockIrisShadowGlobalStateMessBugs(ShadowRenderer instance, LevelRendererAccessor fullyBufferedMultiBufferSource, Camera camera) {
        return !IrisCompat.shouldSkipShadows();
    }

    // The first beginLevelRendering on a fresh pipeline calls allChanged(), releasing every section's
    // VertexBuffer, which mid-feed tears down the geometry we're drawing. Skipping it just defers the
    // feed pipeline's block ID init to the next call.
    @WrapWithCondition(method = "beginLevelRendering", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;allChanged()V"))
    private boolean vista$skipFirstFrameAllChanged(LevelRenderer instance) {
        return !IrisCompat.shouldSkipShadows();
    }
}
