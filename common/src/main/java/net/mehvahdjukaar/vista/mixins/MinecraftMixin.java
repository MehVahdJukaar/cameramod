package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @ModifyReturnValue(method = "useShaderTransparency", at = @At("RETURN"))
    private static boolean vista$noFabulousInFeeds(boolean original) {
        return original && !VistaLevelRenderer.isRenderingLiveFeed();
    }

    //we dont support this. npe occurs if this is on
    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    private boolean vista$noGlowingInFeeds(boolean original) {
        return original && !VistaLevelRenderer.isRenderingLiveFeed();
    }
}
