package net.mehvahdjukaar.vista.mixins.compat;

import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.mixinterface.ShadowRenderRegion;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.mehvahdjukaar.moonlight.api.misc.OptionalMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//iris keeps a regular and a shadow render list on each region and only swaps them inside createTerrainRenderList.
//a feed is one more pas on same section manager, so any pass that skips rebuild draws with wrong list
@OptionalMixin(value = "net.irisshaders.iris.mixinterface.ShadowRenderRegion")
@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false, priority = 1100)
public class CompatIrisSodiumRenderListMixin {

    @Shadow
    @Final
    private RenderRegionManager regions;

    //added by iris. trust me this exists
    @Shadow
    private boolean renderListStateIsShadow;

    @Inject(method = "renderLayer", at = @At("HEAD"))
    private void vista$syncRenderListBeforeDraw(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        boolean shadow = ShadowRenderingState.areShadowsCurrentlyBeingRendered();

        if (this.renderListStateIsShadow == shadow) return;

        for (RenderRegion region : this.regions.getLoadedRegions()) {
            if (region instanceof ShadowRenderRegion r) {
                if (shadow) {
                    r.swapToShadowRenderList();
                } else {
                    r.swapToRegularRenderList();
                }
            }
        }
        this.renderListStateIsShadow = shadow;
    }
}
