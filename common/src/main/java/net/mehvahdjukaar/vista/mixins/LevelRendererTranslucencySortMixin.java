package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//sodium overwrites. 0 care for compatbililty given there...
@Mixin(value = LevelRenderer.class, priority = 1100)
public class LevelRendererTranslucencySortMixin {

    @Expression("? * ? + ? * ? + ? * ? > 1.0")
    @ModifyExpressionValue(method = "renderSectionLayer", at = @At("MIXINEXTRAS:EXPRESSION"), require = 0)
    private boolean vista$dontResortTranslucencyInFeedsPls(boolean original) {
        //sort is shared so this would just be wasted work. idk if this can alter the feed look tho
        return original && (!VistaLevelRenderer.isRenderingLiveFeed());
    }
}
