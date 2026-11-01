package net.mehvahdjukaar.vista.mixins.compat;
//
//import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
//import net.minecraft.client.Camera;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Shadow;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
/// / sable overwrites yRot/xRot inside setRotation with the euler angles of its ship-rotated quaternion,
/// / so getYRot no longer returns what was set making it asymmetric
/// / for https://github.com/ryanhcode/sable/issues/1563
//@Mixin(Camera.class)
//public abstract class HackFixSable1563CameraAnglesMixin {
//
//    @Shadow
//    private float yRot;
//    @Shadow
//    private float xRot;
//
//    @Inject(method = "setRotation(FF)V", at = @At("TAIL"))
//    public void vista$hackFixSableIssue_1563(float yRot, float xRot, CallbackInfo ci) {
//        if (PlatHelper.isDev()) return; //off in dev so we notice when upstream fixes it
//        this.yRot = yRot;
//        this.xRot = xRot;
//    }
//}
