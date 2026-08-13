package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    // Fabulous's deferred targets and transparency post-chain don't compose into our off-screen
    // canvases, so nested renders fall back to the currently bound target. Pairs with nulling
    // LevelRenderer.transparencyChain, which renderLevel branches on directly instead of this.
    @ModifyReturnValue(method = "useShaderTransparency", at = @At("RETURN"))
    private static boolean vista$noFabulousInFeeds(boolean original) {
        return original && !VistaLevelRenderer.isRenderingLiveFeed();
    }
}
