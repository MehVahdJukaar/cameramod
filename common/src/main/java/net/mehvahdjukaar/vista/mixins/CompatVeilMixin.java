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

    // Veil's deferred light pass binds its own framebuffers and wrecks the target feeds render into, so
    // skip it like Veil does for its own nested perspectives. Returning false takes Veil's no-lights
    // branch, which unbinds properly; suppressing the whole tail instead left Veil's framebuffer bound
    // and terrain vanished from feeds.
    //
    // drawLights gained a renderInscattering param in Veil 4.4.0, and Sable still bundles 4.1.4, so both
    // signatures are targeted. Per version only one descriptor exists; the other wrap gets dropped
    // silently, without even a log (that silence is how the 4.4 change slipped by: Supplementaries#2136).
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

    // Veil 4.4.0+ variant of the above
    @TargetHandler(
            mixin = "foundry.veil.mixin.pipeline.client.PipelineLevelRendererMixin",
            name = "blit"
    )
    @WrapOperation(
            method = "@MixinSquared:Handler",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/api/client/render/VeilRenderSystem;drawLights(Lnet/minecraft/util/profiling/ProfilerFiller;Lfoundry/veil/api/client/render/CullFrustum;Z)Z")
    )
    private boolean vista$skipVeilLightPassInFeeds44(ProfilerFiller profiler, CullFrustum cullFrustum,
                                                     boolean renderInscattering, Operation<Boolean> original) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) return false;
        return original.call(profiler, cullFrustum, renderInscattering);
    }

}
