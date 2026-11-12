package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.world.entity.monster.EnderMan;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EnderMan.class)
public class EndermanMixin {

    @Inject(method = "setPersistentAngerTarget", at = @At("HEAD"))
    public void vista$clearTvAnger(@Nullable UUID persistentAngerTarget, CallbackInfo ci) {
        if (persistentAngerTarget == null) {
            VistaMod.ENDERMAN_CAP.set((EnderMan) (Object) this, null);
        }
    }
}
