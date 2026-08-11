package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Sable syncs a sublevel ("ship") to a client purely by player-to-ship distance
// (sub_level_tracking_range, 320 blocks by default): out-of-range players get a StopTracking packet and
// lose the ship's plot chunks on the next tick re-check. So a TV watching a ViewFinder that rides a
// far-away ship never receives the ship client-side. Vista's zone system only sends the ordinary world
// chunks around the ship's anchor, while the ViewFinder block entity lives in a plot chunk only Sable
// can deliver.
//
// shouldLoad(player, shipPos) is the single gate for start/keep/stop tracking. We widen it: if the
// ship's world position falls inside one of the player's camera chunk zones (centred on watched
// ViewFinder anchors, see ServerCameraChunkManager), the player tracks the ship at any distance and
// Sable ships the plot chunks + pose snapshots itself.
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem", remap = false)
public class CompatSableTrackingMixin {

    @Inject(method = "shouldLoad", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void vista$trackSubLevelsInCameraZones(Player player, Vector3dc shipPosition,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer serverPlayer) {
            ExtraChunkViewData data = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(serverPlayer);
            if (data != null && data.containsChunk(
                    Mth.floor(shipPosition.x()) >> 4,
                    Mth.floor(shipPosition.z()) >> 4)) {
                cir.setReturnValue(true);
            }
        }
    }
}
