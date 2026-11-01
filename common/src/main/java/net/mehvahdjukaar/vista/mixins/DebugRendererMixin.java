package net.mehvahdjukaar.vista.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.vista.client.renderer.VistaChunksDebugRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    //TODO: do with event
    @Unique
    private final VistaChunksDebugRenderer vista$debugRenderer = new VistaChunksDebugRenderer();

    @Inject(method ="render", at = @At("HEAD"))
    public void vista$renderDebug(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci){
        if (false) vista$debugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
    }


}
