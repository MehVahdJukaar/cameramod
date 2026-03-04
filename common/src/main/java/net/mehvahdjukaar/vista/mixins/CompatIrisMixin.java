package net.mehvahdjukaar.vista.mixins;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.mehvahdjukaar.vista.integration.iris.VistaIrisCameraPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

@Pseudo
@Mixin(value = PipelineManager.class, remap = false)
public class CompatIrisMixin implements IrisCompat.PipelineAccess {

    @Shadow
    private WorldRenderingPipeline pipeline;

    @Unique
    private final ThreadLocal<Deque<Optional<WorldRenderingPipeline>>> vista$pipelineStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private final WorldRenderingPipeline vista$cameraPipeline = new VistaIrisCameraPipeline();

    @Inject(method = "preparePipeline", remap = false, require = 0, at = @At("HEAD"), cancellable = true)
    private void vista$preparePipeline(NamespacedId currentDimension,
                                       CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        vista$pipelineStack.get().push(Optional.ofNullable(this.pipeline));
        if (IrisCompat.isVistaRendering()) {
            this.pipeline = vista$cameraPipeline;
            cir.setReturnValue(this.pipeline);
        }
    }

    @Inject(method = "destroyPipeline", remap = false, require = 0, at = @At("RETURN"))
    private void vista$clearStackOnDestroy(CallbackInfo ci) {
        vista$pipelineStack.get().clear();
    }

    @Override
    public void vista$restorePipelineAfterRender() {
        Deque<Optional<WorldRenderingPipeline>> stack = vista$pipelineStack.get();
        if (!stack.isEmpty()) {
            this.pipeline = stack.pop().orElse(null);
        }
    }
}
