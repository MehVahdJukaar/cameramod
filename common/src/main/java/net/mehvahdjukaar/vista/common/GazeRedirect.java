package net.mehvahdjukaar.vista.common;

import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gaze ray for blocks that detect being looked at by picking from the player's eyes (enderman
 * heads, ender watchers). The ray bounces off mirrors and jumps from a TV screen to the camera
 * feeding it, then carries on in the new direction.
 */
public final class GazeRedirect {

    public static final double MAX_DISTANCE = 64.0;
    public static final int MAX_BOUNCES = 2;
    // how far a camera can be from the watched block for its viewers to count as nearby
    public static final double CAMERA_RANGE = 20.0;

    private static final double EPSILON = 1.0e-3;
    // the camera sits inside its own block, so the redirected ray has to start past it
    private static final double LENS_EXIT_OFFSET = 0.8;

    private record Ray(Vec3 origin, Vec3 dir) {
    }

    /**
     * Returns the hit only if the ray from the player's eyes ends up on the target block.
     */
    @Nullable
    public static BlockHitResult tryHitThroughScreens(Player player, Level level, BlockPos target,
                                                      double maxDistance, int maxBounces) {
        Vec3 origin = player.getEyePosition(1.0F);
        Vec3 dir = player.getViewVector(1.0F).normalize();
        return bouncePick(level, player, origin, dir, maxDistance, maxBounces, target);
    }

    public static List<Player> addRemoteViewers(Level level, BlockPos watched, List<Player> nearby) {
        List<? extends Player> all = level.players();
        if (all.size() == nearby.size() || !hasCameraNear(level, watched)) return nearby;
        List<Player> result = new ArrayList<>(nearby);
        for (Player p : all) {
            if (!nearby.contains(p)) result.add(p);
        }
        return result;
    }

    private static boolean hasCameraNear(Level level, BlockPos watched) {
        double rangeSq = CAMERA_RANGE * CAMERA_RANGE;
        for (var entry : BroadcastManager.getInstance(level).getAll()) {
            GlobalPos pos = entry.getValue().getChunkSendPosition();
            if (pos == null || pos.dimension() != level.dimension()) continue;
            if (pos.pos().distSqr(watched) < rangeSq) return true;
        }
        return false;
    }

    @Nullable
    private static BlockHitResult bouncePick(Level level, Player player, Vec3 origin, Vec3 dir,
                                             double remaining, int bouncesLeft, BlockPos target) {
        if (remaining <= 0) return null;
        Vec3 end = origin.add(dir.scale(remaining));
        BlockHitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return null;

        BlockPos hitPos = hit.getBlockPos();
        if (hitPos.equals(target)) return hit;
        if (bouncesLeft <= 0) return null;

        BlockState state = level.getBlockState(hitPos);
        Vec3 hitLoc = hit.getLocation();
        double left = remaining - hitLoc.subtract(origin).length();

        if (state.getBlock() instanceof MirrorBlock) {
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            if (hit.getDirection() != facing) return null;

            Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 reflectedDir = dir.subtract(normal.scale(2 * dir.dot(normal))).normalize();
            Vec3 nextOrigin = hitLoc.add(reflectedDir.scale(EPSILON));
            return bouncePick(level, player, nextOrigin, reflectedDir, left, bouncesLeft - 1, target);
        }

        if (state.getBlock() instanceof TVBlock) {
            Ray camera = cameraRayThroughScreen(level, hitPos, state, hit);
            if (camera == null) return null;
            return bouncePick(level, player, camera.origin(), camera.dir(), left, bouncesLeft - 1, target);
        }
        return null;
    }

    // ray that the hit point of a tv screen is showing, starting just outside the camera
    @Nullable
    private static Ray cameraRayThroughScreen(Level level, BlockPos pos, BlockState state, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(TVBlock.FACING)) return null;
        TVBlockEntity tv = getMasterTv(level, pos, state);
        if (tv == null) return null;
        UUID feed = tv.getViewingFeedId();
        if (feed == null) return null;
        if (!(BroadcastManager.getInstance(level).getBroadcast(feed, level.isClientSide)
                instanceof ViewFinderBlockEntity camera)) {
            return null;
        }
        Vec2 uv = tv.getScreenRect().projectLocal(hit.getLocation());
        if (uv == null) return null;

        Vec3 dir = camera.getPixelRayDirection(uv.x, uv.y);
        Vec3 origin = Vec3.atCenterOf(camera.getBlockPos()).add(dir.scale(LENS_EXIT_OFFSET));
        return new Ray(origin, dir);
    }

    @Nullable
    private static TVBlockEntity getMasterTv(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof TVBlock tvBlock)) return null;
        return tvBlock.findMasterBlockEntity(level, pos, state) instanceof TVBlockEntity tv ? tv : null;
    }
}
