package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.BlockingQueue;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    @Final
    public BlockingQueue<ChunkRenderDispatcher.RenderChunk> recentlyCompiledChunks;

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

    @Inject(method = "addRecentlyCompiledChunk", at = @At("HEAD"))
    public void vista$onRecentlyCompiledSection(ChunkRenderDispatcher.RenderChunk renderChunk, CallbackInfo ci) {
        VistaLevelRenderer.onRecentlyCompiledSection(renderChunk, this.recentlyCompiledChunks);
    }
}
