package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.common.tv.enderman.ITVAngeredEnderman;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.monster.EnderMan;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EnderMan.class)
public class EndermanMixin implements ITVAngeredEnderman {

    @Unique
    private boolean vista$angeredFromTV = false;

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    public void vista$saveTvAnger(CompoundTag tag, CallbackInfo ci) {
        if (vista$angeredFromTV) {
            tag.putBoolean("angered_from_tv", true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    public void vista$loadTvAnger(CompoundTag tag, CallbackInfo ci) {
        if (tag.getBoolean("angered_from_tv")) {
            this.vista$angeredFromTV = true;
        }
    }

    @Inject(method = "setPersistentAngerTarget", at = @At("HEAD"))
    public void vista$clearTvAnger(@Nullable UUID persistentAngerTarget, CallbackInfo ci) {
        if (persistentAngerTarget == null) {
            this.vista$angeredFromTV = false;
        }
    }

    @Override
    public boolean vista$isAngry() {
        return vista$angeredFromTV;
    }

    @Override
    public void vista$setAngry(boolean angry) {
        this.vista$angeredFromTV = angry;
    }
}
