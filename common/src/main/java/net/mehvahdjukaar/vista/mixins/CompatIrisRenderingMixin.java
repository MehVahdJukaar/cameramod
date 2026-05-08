package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class CompatIrisRenderingMixin {

    @WrapWithCondition(method = "renderShadows",
            remap = false,
            require = 0,
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V"))
    private boolean vista$blockIrisShadowGlobalStateMessBugs(ShadowRenderer instance, LevelRendererAccessor fullyBufferedMultiBufferSource, Camera camera) {
        return !IrisCompat.shouldSkipShadows();
    }
}
