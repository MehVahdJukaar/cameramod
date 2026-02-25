package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.moonlight.api.misc.OptionalMixin;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@OptionalMixin(value = "net.irisshaders.iris.pipeline.PipelineManager")
@Mixin(GameRenderer.class)
public class CompatIrisBobbingMixin {

    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private boolean vista$skipBobViewDuringFeed(GameRenderer instance, PoseStack poseStack, float partialTick) {
        return CompatHandler.IRIS && !IrisCompat.shouldSkipBobbing();
    }

    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private boolean vista$skipBobHurtDuringFeed(GameRenderer instance, PoseStack poseStack, float partialTick) {
        return CompatHandler.IRIS && !IrisCompat.shouldSkipBobbing();
    }
}
