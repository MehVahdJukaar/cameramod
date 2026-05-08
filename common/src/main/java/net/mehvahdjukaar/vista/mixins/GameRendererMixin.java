package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Definition(id = "fov", field = "Lnet/minecraft/client/renderer/GameRenderer;fov:F")
    @Expression("this.fov < 0.1")
    @ModifyExpressionValue(method = "tickFov", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean vista$uncapFOV(boolean original) {
        if (!original) {
            return false;
        }
        return !ViewFinderController.isActive();
    }

    @Inject(method = "loadEffect", at = @At("HEAD"), cancellable = true)
    public void vista$blockNewEffects(ResourceLocation resourceLocation, CallbackInfo ci) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            ci.cancel();
        }
    }

}
