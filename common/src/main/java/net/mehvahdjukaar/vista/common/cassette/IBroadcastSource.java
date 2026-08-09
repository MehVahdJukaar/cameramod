package net.mehvahdjukaar.vista.common.cassette;

import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.broadcast.IBroadcastLocation;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface IBroadcastSource {

    default void ensureLinked(Level level, IBroadcastLocation location) {
        if (level instanceof ServerLevel sl) {
            BroadcastManager.getInstance(sl)
                    .linkFeed(this.getBroadcastUUID(), location);
        }
    }

    /**
     * Links the feed immediately when the level's saved-data storage is available, returning false
     * when the link was deferred (level still under construction during early chunk loading).
     * Callers should retry later, e.g. from their tick method. See {@link BroadcastManager#getInstanceIfReady}.
     */
    default boolean linkFeedIfReady(Level level, IBroadcastLocation location) {
        BroadcastManager manager = BroadcastManager.getInstanceIfReady(level);
        if (manager != null) {
            manager.linkFeed(this.getBroadcastUUID(), location);
            return true;
        }
        return false;
    }

    default void removeLink(Level level) {
        if (level instanceof ServerLevel sl) {
            BroadcastManager.getInstance(sl)
                    .unlinkFeed(this.getBroadcastUUID());
        }
    }

    UUID getBroadcastUUID();

    @Nullable IVideoSource getBroadcastVideo();

}
