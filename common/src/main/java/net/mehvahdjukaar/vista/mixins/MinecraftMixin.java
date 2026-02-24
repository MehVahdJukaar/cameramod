package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.moonlight.api.misc.TileOrEntityTarget;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.network.ServerBoundToggleViewFinderOutlinesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public HitResult hitResult;

    @Shadow
    public LocalPlayer player;

    @Shadow
    public ClientLevel level;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void vista$ctrlRightClickToggleDetectorOutlines(CallbackInfo ci) {
        if (!Screen.hasControlDown()) {
            return;
        }
        if (this.level == null || this.player == null || this.hitResult == null || this.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) this.hitResult;
        if (!(this.level.getBlockEntity(blockHit.getBlockPos()) instanceof ViewFinderBlockEntity viewFinder)) {
            return;
        }
        if (!viewFinder.hasEntityDetectorFilter()) {
            return;
        }

        NetworkHelper.sendToServer(new ServerBoundToggleViewFinderOutlinesPacket(TileOrEntityTarget.of(viewFinder)));
        viewFinder.toggleDetectorOutlinesEnabled();
        ci.cancel();
    }
}
