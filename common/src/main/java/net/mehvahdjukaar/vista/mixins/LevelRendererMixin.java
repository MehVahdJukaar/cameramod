package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    public SectionOcclusionGraph sectionOcclusionGraph;

    @ModifyReturnValue(method = "shouldShowEntityOutlines", at = @At(value = "RETURN"))
    public boolean vista$disableEntityOutlines(boolean original) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"),
            require = 1)
    public boolean vista$isCameraDetached(boolean original) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            return true;
        }
        return original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getEntity()Lnet/minecraft/world/entity/Entity;",
            ordinal = 3), require = 1)
    public Entity vista$getActualPlayer(Entity original, @Local(ordinal = 0) Entity entity) {
        if (VistaLevelRenderer.isRenderingLiveFeed() && entity instanceof LocalPlayer) {
            return entity;
        }
        return original;
    }

    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    public void vista$alterSetupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator, CallbackInfo ci) {
        if (VistaLevelRenderer.setupRender((LevelRenderer) (Object) this, camera, frustum, hasCapturedFrustum, isSpectator)) {
            ci.cancel();
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    public void vista$onChunkLoaded(ChunkPos chunkPos, CallbackInfo ci) {
        VistaLevelRenderer.onChunkLoaded(chunkPos, this.sectionOcclusionGraph);
    }

    @Inject(method = "addRecentlyCompiledSection", at = @At("HEAD"))
    public void vista$onRecentlyCompiledSection(SectionRenderDispatcher.RenderSection renderSection, CallbackInfo ci) {
        VistaLevelRenderer.onRecentlyCompiledSection(renderSection, this.sectionOcclusionGraph);
    }
}
