package net.mehvahdjukaar.vista.mixins.neoforge;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.mehvahdjukaar.moonlight.api.misc.OptionalMixin;
import net.mehvahdjukaar.vista.common.GazeRedirect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.violetmoon.quark.content.automation.block.be.EnderWatcherBlockEntity;

import java.util.List;

@OptionalMixin(value = "org.violetmoon.quark.content.automation.block.be.EnderWatcherBlockEntity")
@Pseudo
@Mixin(EnderWatcherBlockEntity.class)
public abstract class EnderWatcherBlockEntityMixin {

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")
    )
    private static List<Player> vista$addRemoteViewers(List<Player> original,
                                                       Level level, BlockPos pos, BlockState state,
                                                       EnderWatcherBlockEntity be) {
        return GazeRedirect.addRemoteViewers(level, pos, original);
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lorg/violetmoon/zeta/util/RaytracingUtil;rayTrace(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Lnet/minecraft/world/phys/HitResult;")
    )
    private static HitResult vista$reflectivePick(HitResult original,
                                                  Level level, BlockPos pos, BlockState state,
                                                  EnderWatcherBlockEntity be,
                                                  @Local Player player) {
        if (original instanceof BlockHitResult bh && bh.getBlockPos().equals(pos)) return original;
        BlockHitResult redirected = GazeRedirect.tryHitThroughScreens(
                player, level, pos,
                GazeRedirect.MAX_DISTANCE, GazeRedirect.MAX_BOUNCES);
        return redirected != null ? redirected : original;
    }
}
