package net.mehvahdjukaar.vista.common.broadcast;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Feed location backed by a block entity at a fixed position in a level.
 *
 * <p>Sable sublevel support: when the block entity sits inside a Sable plot grid (a movable "ship"),
 * {@link #globalPos} is the plot-grid storage coordinate, which must NEVER be fed into the vanilla
 * chunk system (tickets/holders in the plot grid fight Sable's injected {@code PlotChunkHolder}s:
 * shutdown hangs, chunks not loading). {@link #getChunkSendPosition()} therefore always resolves the
 * ship's real-world anchor instead. Because Sable projections silently no-op while a sublevel is
 * "held" (serialized when its anchor world chunk unloads), the last successfully projected anchor is
 * cached in {@link #subLevelAnchor} and persisted, so a far-away held ship can still be force-loaded
 * back into existence.
 */
public final class LevelBEBroadcastLocation implements IBroadcastLocation {

    public static final BroadcastLocationType TYPE = new BroadcastLocationType(
            RecordCodecBuilder.<LevelBEBroadcastLocation>mapCodec(i -> i.group(
                    GlobalPos.MAP_CODEC.forGetter(l -> l.globalPos),
                    BlockPos.CODEC.optionalFieldOf("sub_level_anchor")
                            .forGetter(l -> Optional.ofNullable(l.subLevelAnchor))
            ).apply(i, (pos, anchor) -> new LevelBEBroadcastLocation(pos, anchor.orElse(null)))),
            // the anchor is server-side bookkeeping only; clients resolve the BE via globalPos
            GlobalPos.STREAM_CODEC.map(LevelBEBroadcastLocation::new, LevelBEBroadcastLocation::globalPos)
    );

    private final GlobalPos globalPos;
    /**
     * Chunk-snapped world position of the Sable sublevel this BE rides on, refreshed whenever the
     * sublevel is resolvable. Null for normal in-world block entities. Mutable cache, deliberately
     * excluded from {@link #equals}: unlink-by-value and linkFeed's no-op check must match on
     * {@link #globalPos} alone.
     */
    @Nullable
    private BlockPos subLevelAnchor;

    public LevelBEBroadcastLocation(GlobalPos globalPos) {
        this(globalPos, null);
    }

    private LevelBEBroadcastLocation(GlobalPos globalPos, @Nullable BlockPos subLevelAnchor) {
        this.globalPos = globalPos;
        this.subLevelAnchor = subLevelAnchor;
    }

    public static LevelBEBroadcastLocation of(Level level, BlockPos pos) {
        BlockPos anchor = null;
        if (!level.isClientSide() && SableCompanion.INSTANCE.isInPlotGrid(level, pos)) {
            anchor = projectToWorldAnchor(level, pos);
        }
        return new LevelBEBroadcastLocation(GlobalPos.of(level.dimension(), pos), anchor);
    }

    public static LevelBEBroadcastLocation of(BlockEntity be) {
        return of(be.getLevel(), be.getBlockPos());
    }

    public GlobalPos globalPos() {
        return globalPos;
    }

    @Override
    public TriResult<IBroadcastSource> get(boolean isClient) {
        MinecraftServer server = PlatHelper.getCurrentServer();
        Level otherLevel = isClient
                ? VistaModClient.getLocalLevelByDimension(globalPos.dimension())
                : (server == null ? null : server.getLevel(globalPos.dimension()));

        if (otherLevel != null && otherLevel.isLoaded(globalPos.pos())) {
            if (otherLevel.getBlockEntity(globalPos.pos()) instanceof IBroadcastSource provider) {
                return TriResult.valid(provider);
            } else if (!isClient) {
                return TriResult.invalid();
            }
        }
        return TriResult.empty();
    }

    @Override
    public BroadcastLocationType type() {
        return TYPE;
    }

    @Override
    public MutableComponent getTooltipComponent(Level level) {
        if (level.dimension() == globalPos.dimension()) {
            BlockPos pos = globalPos.pos();
            return Component.translatable("tooltip.vista.hollow_cassette.linked",
                    pos.getX(), pos.getY(), pos.getZ());
        } else {
            return Component.translatable("tooltip.vista.hollow_cassette.linked_away", globalPos.dimension());
        }
    }

    @Override
    public @Nullable GlobalPos getChunkSendPosition() {
        MinecraftServer server = PlatHelper.getCurrentServer();
        Level level = server == null ? null : server.getLevel(globalPos.dimension());
        if (level == null || !SableCompanion.INSTANCE.isInPlotGrid(level, globalPos.pos())) {
            return globalPos; // normal in-world block entity: fast path
        }
        BlockPos anchor = projectToWorldAnchor(level, globalPos.pos());
        if (anchor != null) {
            // sublevel currently resolvable: refresh the persisted anchor as the ship moves
            if (!anchor.equals(this.subLevelAnchor)) {
                this.subLevelAnchor = anchor;
                BroadcastManager.getInstance(level).setDirty();
            }
            return GlobalPos.of(globalPos.dimension(), anchor);
        }
        // sublevel held/unloaded: fall back to the last known anchor (force-loading it makes Sable
        // restore the held sublevel, after which live projection takes over again)
        return subLevelAnchor == null ? null : GlobalPos.of(globalPos.dimension(), subLevelAnchor);
    }

    /**
     * Projects a plot-grid position to the ship's world anchor, snapped to chunk granularity.
     * Returns null when the projection no-ops (sublevel held or removed). The result must never be
     * a plot-grid coordinate.
     */
    @Nullable
    private static BlockPos projectToWorldAnchor(Level level, BlockPos plotPos) {
        Vec3 world = SableCompanion.INSTANCE.projectOutOfSubLevel(level, (Position) Vec3.atCenterOf(plotPos));
        if (SableCompanion.INSTANCE.isInPlotGrid(level, (Position) world)) return null;
        return new ChunkPos(BlockPos.containing(world)).getWorldPosition();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LevelBEBroadcastLocation other && globalPos.equals(other.globalPos);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(globalPos);
    }

    @Override
    public String toString() {
        return "LevelBEBroadcastLocation[" + globalPos + (subLevelAnchor != null ? ", anchor=" + subLevelAnchor : "") + "]";
    }
}
