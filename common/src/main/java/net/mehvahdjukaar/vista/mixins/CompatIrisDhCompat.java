package net.mehvahdjukaar.vista.mixins;

import net.irisshaders.iris.compat.dh.DHCompat;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//Nonsense mixin. thanks iris...
@Mixin(value = DHCompat.class, remap = false)
public class CompatIrisDhCompat {

    @Inject(method = "checkFrame", remap = false,
            require = 0,
            at = @At("HEAD"), cancellable = true)
    private static void vista$shushIrisWTF(CallbackInfoReturnable<Boolean> cir) {
        //no op
        if (IrisCompat.shouldShushDHCompat()) {
            cir.setReturnValue(false);
        }
    }

}
