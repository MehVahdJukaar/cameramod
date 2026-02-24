package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(IrisRenderingPipeline.class)
public class CompatIrisRenderingMixin {

    @WrapWithCondition(
            method = "renderShadows",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V"
            ),
            remap = false
    )
    private boolean vista$cockblockIrisShadowGlobalStateMessBugs(
            ShadowRenderer instance,
            LevelRendererAccessor levelRendererAccessor,
            Camera camera
    ) {
                return true;
    }
}
