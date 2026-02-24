package net.mehvahdjukaar.vista.mixins;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessorMixin {

    @Invoker("doEntityOutline")
    void vista$doEntityOutline();
}
