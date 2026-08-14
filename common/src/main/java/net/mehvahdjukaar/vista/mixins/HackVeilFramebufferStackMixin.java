package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import foundry.veil.api.client.render.framebuffer.FramebufferStack;
import net.mehvahdjukaar.moonlight.api.misc.OptionalMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

//prevents an issue with flares mod. issue on veil. that stack isnt a stack at all.
@OptionalMixin("foundry.veil.api.client.render.framebuffer.FramebufferStack")
@Pseudo
@Mixin(FramebufferStack.class)
public class HackVeilFramebufferStackMixin {

    @WrapOperation(
            method = "pop",
            at = @At(value = "INVOKE", target = "Ljava/util/List;removeFirst()Ljava/lang/Object;"),
            remap = false,
            require = 0
    )
    private static Object vista$popWhatWasPushedLast(List<Object> stateStack, Operation<Object> original) {
        return stateStack.removeLast();
    }
}
