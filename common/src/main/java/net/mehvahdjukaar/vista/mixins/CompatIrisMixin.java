package net.mehvahdjukaar.vista.mixins;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = PipelineManager.class, remap = false)
public class CompatIrisMixin  {

    @Shadow
    @Nullable
    private WorldRenderingPipeline pipeline;

    @Inject(method = "preparePipeline",
            remap = false,
            require = 0,
            at = @At("HEAD"), cancellable = true)
    private void vista$preparePipeline(NamespacedId currentDimension,
                                       CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        var modified = IrisCompat.getModifiedPipeline();
        if (modified != null) {
            this.pipeline = modified;
            cir.setReturnValue(modified);
        }
    }
}
