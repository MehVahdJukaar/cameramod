package net.mehvahdjukaar.vista.mixins;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import foundry.veil.api.client.render.CullFrustum;
import net.mehvahdjukaar.moonlight.api.misc.OptionalMixin;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@OptionalMixin(value = "foundry.veil.mixin.pipeline.client.PipelineLevelRendererMixin")
@Pseudo
@Mixin(value = LevelRenderer.class, priority = 1500)
public abstract class CompatVeilMixin {

    // Veil skips its own deferred light pass while rendering a nested perspective, so we do the same for
    // feeds: that pass binds Veil's light framebuffer and wrecks the target we render into.
    //
    // Skip the drawLights call rather than faking isRenderingPerspective(). That flag guards the whole
    // tail block, including the AdvancedFbo.unbind() Veil runs when there are no lights to draw, and
    // suppressing that left Veil's framebuffer bound for the rest of the feed render: the deferred
    // result never reached our target, so terrain went missing while entities and block entities (drawn
    // straight to the bound target) survived. Returning false takes Veil's own no-lights branch, which
    // unbinds as it should.
    @TargetHandler(
            mixin = "foundry.veil.mixin.pipeline.client.PipelineLevelRendererMixin",
            name = "blit"
    )
    @WrapOperation(
            method = "@MixinSquared:Handler",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/api/client/render/VeilRenderSystem;drawLights(Lnet/minecraft/util/profiling/ProfilerFiller;Lfoundry/veil/api/client/render/CullFrustum;)Z")
    )
    private boolean vista$skipVeilLightPassInFeeds(ProfilerFiller profiler, CullFrustum cullFrustum,
                                                   Operation<Boolean> original) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) return false;
        return original.call(profiler, cullFrustum);
    }

}
