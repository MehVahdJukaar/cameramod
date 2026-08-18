package net.mehvahdjukaar.vista.mixins.fabric;

import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric equivalent of NeoForge's IBlockEntityExtension.onLoad/onChunkUnloaded. Calls
// TVBlockEntity.onLoad() and onChunkUnloaded(), which NeoForge picks up on its own.
// addAndRegisterBlockEntity fires for both chunk loading and in-game placement, so newly placed TVs
// get tracked right away too.
@Mixin(LevelChunk.class)
public class LevelChunkTVTrackingMixin {

    @Inject(method = "addAndRegisterBlockEntity", at = @At("RETURN"))
    private void vista$onBEAdded(BlockEntity be, CallbackInfo ci) {
        if (be instanceof TVBlockEntity tv && be.hasLevel() && be.getLevel() instanceof ServerLevel) {
            tv.onLoad();
        }
    }

    // iterate before setRemoved() strips the level reference from each BE
    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void vista$onChunkUnloading(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getLevel() == null || !(self.getLevel() instanceof ServerLevel)) return;
        for (BlockEntity be : self.getBlockEntities().values()) {
            if (be instanceof TVBlockEntity tv) tv.onChunkUnloaded();
        }
    }
}
