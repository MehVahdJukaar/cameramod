package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Subscribes the player to entity tracking for entities in a camera-zone chunk.
// TrackedEntity.updatePlayer gates on e <= f && broadcastToPlayer && isChunkTracked. ChunkMapMixin
// fixes isChunkTracked, but the e <= f distance check still fails since zone chunks sit far outside
// view distance, so those entities never pair and never spawn on the client. Force the bl flag true
// for zone-chunk entities that still want to be broadcast.
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class ChunkMapTrackedEntityMixin {

    @Shadow
    @org.spongepowered.asm.mixin.Final
    Entity entity;

    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), ordinal = 0)
    private boolean vista$trackZoneEntities(boolean bl, ServerPlayer player) {
        if (bl) return true;
        ExtraChunkViewData data = VistaMod.EXTRA_VIEW_AREAS.getOrCreate(player);
        if (data != null
                && data.containsChunk(this.entity.chunkPosition().x, this.entity.chunkPosition().z)
                && this.entity.broadcastToPlayer(player)) {
            return true;
        }
        return bl;
    }
}
