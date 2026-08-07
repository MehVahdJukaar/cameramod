package net.mehvahdjukaar.vista.common.connection;

import net.mehvahdjukaar.moonlight.api.util.math.MthUtils;
import net.mehvahdjukaar.moonlight.api.util.math.Rect2D;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaPlatStuff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractGridAccess implements GridAccessor {

    protected final BlockPos pos;
    protected final Direction facing;
    protected final BlockState referenceState;
    protected final Level level;
    protected final Block owner;

    protected final Map<Vec2i, GridTile> statesCache = new HashMap<>();
    protected final Map<Vec2i, GridTile> statesChanged = new HashMap<>();
    protected Rect2D finalTileRect = null;

    protected AbstractGridAccess(Level level, BlockPos pos, BlockState state, Block owner) {
        this.pos = pos;
        this.facing = state.getValue(HorizontalDirectionalBlock.FACING);
        this.referenceState = state;
        this.level = level;
        this.owner = owner;
    }

    protected abstract EnumProperty<ConnectionType> connectionProperty();

    protected abstract GridTile readTile(BlockPos pos, BlockState state);

    protected abstract GridTile buildTile(Vec2i key, @Nullable ConnectionType type, boolean setPower);

    protected abstract void onMasterApplied(BlockPos target, Rect2D rect);

    protected BlockState writeState(BlockState state, GridTile tile) {
        return state;
    }

    protected final BlockPos targetPos(Vec2i key) {
        return MthUtils.relativePos(pos, facing, key.x(), key.y(), 0);
    }

    protected final boolean matchesOwnBlock(BlockState state) {
        return owner instanceof IConnectedBlock cb && cb.connectionMatches(referenceState, state);
    }

    @Override
    public @NotNull GridTile getAt(Vec2i key) {
        GridTile cached = statesCache.get(key);
        if (cached != null) return cached;
        BlockPos target = targetPos(key);
        BlockState bs = level.getBlockState(target);
        GridTile value = matchesOwnBlock(bs) ? readTile(target, bs) : GridTile.EMPTY;
        statesCache.put(key, value);
        return value;
    }

    @Override
    public void setAt(Vec2i key, @Nullable ConnectionType type, boolean setPower) {
        GridTile tile = buildTile(key, type, setPower);
        statesChanged.put(key, tile);
        statesCache.put(key, tile);
    }

    @Override
    public void planBeMove(@Nullable Rect2D fromRec, Rect2D toRec) {
        finalTileRect = toRec;
    }

    public final void applyChanges() {
        for (var e : statesChanged.entrySet()) {
            Vec2i key = e.getKey();
            GridTile tile = e.getValue();
            ConnectionType conn = tile.type();
            if (conn == null) continue;
            BlockPos target = targetPos(key);
            BlockState bs = level.getBlockState(target);
            if (bs.getBlock() == owner) {
                level.setBlockAndUpdate(target, writeState(bs.setValue(connectionProperty(), conn), tile));
            }
        }
        if (finalTileRect != null) {
            BlockPos target = targetPos(finalTileRect.bottomLeft());
            onMasterApplied(target, finalTileRect);
        }
        // done last, once the master block entity has settled: a reshape can move the master while leaving
        // some of the touched blocks with an identical state, which by itself notifies nothing
        for (Vec2i key : statesChanged.keySet()) {
            VistaPlatStuff.invalidateBlockCapabilities(level, targetPos(key));
        }
    }
}
