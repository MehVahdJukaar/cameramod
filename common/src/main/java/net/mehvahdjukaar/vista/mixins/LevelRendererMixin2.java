package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mehvahdjukaar.moonlight.api.misc.OptionalMixin;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

//i hate sodium. why do you have to redirect
@Mixin(LevelRenderer.class)
@OptionalMixin(value = "me.jellysquid.mods.sodium.mixin.core.render.world.WorldRendererMixin" , classLoaded = false)
public class LevelRendererMixin2 {

    @WrapOperation(method = "setupRender",
            require =  0, //embeddium port on 1.20 has diff class name.. lets just make this optional.
            at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ExecutorService;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"))
    public Future<?> vista$wrapFrustumUpdate(ExecutorService instance, Runnable runnable, Operation<Future<?>> op) {
        return op.call(instance, VistaLevelRenderer.wrapFrustumUpdate(runnable));
    }
}
