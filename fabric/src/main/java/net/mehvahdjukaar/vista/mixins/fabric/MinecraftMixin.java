package net.mehvahdjukaar.vista.mixins.fabric;

import net.mehvahdjukaar.vista.VistaModClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V",
    shift = At.Shift.AFTER))
    public void vista$whyIsntThereAnEventForThis(boolean renderLevel, CallbackInfo ci) {
        VistaModClient.onRenderTickEnd((Minecraft) (Object) this);
    }

}
