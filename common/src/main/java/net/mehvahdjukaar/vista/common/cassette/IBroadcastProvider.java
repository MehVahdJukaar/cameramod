package net.mehvahdjukaar.vista.common.cassette;

import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.BroadcastManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface IBroadcastProvider {

    default void ensureLinked(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            BroadcastManager.getInstance(sl)
                    .linkFeed(this.getUUID(), GlobalPos.of(sl.dimension(), pos));
        }
    }

    default void removeLink(Level level) {
        if (level instanceof ServerLevel sl) {
            BroadcastManager.getInstance(sl)
                    .unlinkFeed(this.getUUID());
        }
    }

    UUID getUUID();

    @Nullable IVideoSource getBroadcastVideoSource();
}
