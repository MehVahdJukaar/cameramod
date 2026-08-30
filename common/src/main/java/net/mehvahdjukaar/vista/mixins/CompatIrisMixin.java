package net.mehvahdjukaar.vista.mixins;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = PipelineManager.class, remap = false)
public class CompatIrisMixin  {

    @Shadow
    @Nullable
    private WorldRenderingPipeline pipeline;

    // iris_off_hack=true: hand back a no-op VanillaRenderingPipeline so Iris stays
    // out of the feed pass entirely.
    @Inject(method = "preparePipeline", remap = false, at = @At("HEAD"), cancellable = true)
    private void vista$swapToVanillaPipeline(NamespacedId currentDimension,
                                             CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        WorldRenderingPipeline modified = IrisCompat.getModifiedPipeline();
        if (modified != null) {
            this.pipeline = modified;
            cir.setReturnValue(modified);
        }
    }

    // iris_off_hack=false: force PipelineManager to keep separate IrisRenderingPipeline
    // entries for feeds by rewriting the dimension namespace. Without this the single
    // shared per-dim pipeline gets its RenderTargets resized back to the feed canvas
    // size and then back to the main framebuffer size every frame, which reallocates
    // every gbuffer and is what produces the flicker + perf cliff.
    //
    // The key comes from IrisCompat: one pipeline per feed canvas (falling back to per-size
    // sharing past a cap), so same-size TVs never alternate canvases -- and never wipe each
    // other's temporal buffers via the full-clear safety -- on one pipeline.
    //
    // Once the feed pipeline is resolved, tell IrisCompat which canvas it was bound to; a switch
    // between canvases on one pipeline arms a full clear so the pack's temporal buffers don't
    // carry one camera's TAA history into the other one's composite.
    @Inject(method = "preparePipeline", at = @At("RETURN"), remap = false)
    private void vista$onFeedPipelineBound(NamespacedId currentDimension,
                                           CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        if (IrisCompat.shouldSwapDimensionForFeed()) {
            IrisCompat.onFeedPipelineBound(cir.getReturnValue(), Minecraft.getInstance().getMainRenderTarget());
        }
    }

    @ModifyVariable(method = "preparePipeline", at = @At("HEAD"), argsOnly = true, remap = false)
    private NamespacedId vista$rewriteDimensionForFeed(NamespacedId value) {
        if (IrisCompat.shouldSwapDimensionForFeed()) {
            String key = IrisCompat.feedPipelineKey();
            if (key != null) {
                return new NamespacedId(value.getNamespace(), "vista_live_feed_" + key);
            }
        }
        return value;
    }
}
