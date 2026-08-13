package net.mehvahdjukaar.vista.mixins;

import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Iris advances its frame counter once per game frame, but feeds render slower, so the counter jumps
// by N between feed renders. Shader pack TAA then cycles N halton samples per feed step and can never
// accumulate, leaving every pixel wobbling. A feed-local counter advances one sample per feed render,
// which the TAA composite can actually blend.
@Pseudo
@Mixin(value = SystemTimeUniforms.FrameCounter.class, remap = false)
public class CompatIrisFrameCounterMixin {

    @Inject(method = "getAsInt", at = @At("HEAD"), cancellable = true, remap = false)
    private void vista$feedScopedFrameCounter(CallbackInfoReturnable<Integer> cir) {
        if (IrisCompat.isFeedRendering()) {
            cir.setReturnValue(IrisCompat.getFeedFrameCounter());
        }
    }
}
