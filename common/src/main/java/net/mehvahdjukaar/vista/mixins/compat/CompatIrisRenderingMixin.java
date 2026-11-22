package net.mehvahdjukaar.vista.mixins.compat;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(IrisRenderingPipeline.class)
public class CompatIrisRenderingMixin {

    @Shadow(remap = false)
    private ShaderStorageBufferHolder shaderStorageBufferHolder;

    @Inject(method = "beginLevelRendering", at = @At("HEAD"), remap = false)
    private void vista$rebindSsbos(CallbackInfo ci) {
        IrisCompat.onBeforeBeginLevelRendering(this.shaderStorageBufferHolder);
    }

    @Inject(method = "destroy", at = @At("TAIL"), remap = false)
    private void vista$forgetMainSsbos(CallbackInfo ci) {
        IrisCompat.onPipelineDestroyed();
    }

    @WrapWithCondition(method = "renderShadows", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V"))
    private boolean vista$cockblockIrisShadowGlobalStateMessBugs(ShadowRenderer instance, LevelRendererAccessor fullyBufferedMultiBufferSource, Camera camera) {
        return !IrisCompat.shouldSkipShadows();
    }

    @WrapWithCondition(method = "beginLevelRendering", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;allChanged()V"))
    private boolean vista$skipFirstFrameAllChanged(LevelRenderer instance) {
        return !IrisCompat.shouldSkipShadows();
    }
}
