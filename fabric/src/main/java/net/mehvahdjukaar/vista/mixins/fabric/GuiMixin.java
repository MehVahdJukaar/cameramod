package net.mehvahdjukaar.vista.mixins.fabric;

import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    public void vista$cancelXPBar(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        if (ViewFinderController.isActive()) ci.cancel();
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    public void vista$cancelHotbar(float f, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ViewFinderController.isActive()) ci.cancel();
    }

}
