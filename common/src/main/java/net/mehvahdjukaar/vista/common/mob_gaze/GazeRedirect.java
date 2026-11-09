package net.mehvahdjukaar.vista.common.mob_gaze;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.client.renderer.ViewFinderBlockEntityRenderer;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.sable.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GazeRedirect {

    public static final double MAX_DISTANCE = 64.0;
    public static final int MAX_BOUNCES = 2;
    private static final double LENS_EXIT_OFFSET = 0.8;

    public record Ray(Vec3 origin, Vec3 dir, @Nullable TVBlockEntity fromTv) {
    }

    public record ScreenGaze(Player player, List<TVBlockEntity> tvsPassed) {
    }

    @Nullable
    public static BlockHitResult tryHitThroughScreens(Player player, Level level, BlockPos target) {
        Vec3 origin = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0F).normalize();
        double remaining = MAX_DISTANCE;

        for (int bounces = 0; bounces <= MAX_BOUNCES; bounces++) {
            BlockHitResult hit = clip(level, player, origin, dir, remaining);
            if (hit.getType() == HitResult.Type.MISS) return null;
            if (hit.getBlockPos().equals(target)) return hit;

            Vec3 stop = SableCompat.projectOutOfSubLevel(level, hit.getLocation());
            Ray next = redirect(level, dir, hit);
            if (next == null) return null;
            remaining -= stop.distanceTo(origin);
            origin = next.origin();
            dir = next.dir();
        }
        return null;
    }

    public static List<Player> addRemoteViewers(Level level, BlockPos watched, List<Player> nearby) {
        List<? extends Player> all = level.players();
        if (all.size() == nearby.size() || !hasCameraNear(level, watched)) {
            return nearby;
        }

        List<Player> result = new ArrayList<>(nearby);
        for (Player p : all) {
            if (!nearby.contains(p)) {
                result.add(p);
            }
        }
        return result;
    }

    public static boolean isWatchedThroughScreens(Level level, Entity watched) {
        return findWatcherThroughScreens(level, watched) != null;
    }

    @Nullable
    public static ScreenGaze findWatcherThroughScreens(Level level, Entity watched) {
        if (level.isClientSide) return null;
        boolean cameraNear = hasCameraNear(level, watched.blockPosition());
        for (Player player : level.players()) {
            boolean onlyReachableByCamera = player.distanceToSqr(watched) > MAX_DISTANCE * MAX_DISTANCE;
            if (onlyReachableByCamera && !cameraNear) {
                continue;
            }
            ScreenGaze gaze = traceGazeTo(level, player, watched);
            if (gaze != null) {
                return gaze;
            }
        }
        return null;
    }

    // null if the gaze doesnt land on the entity after at least one bounce
    @Nullable
    public static ScreenGaze traceGazeTo(Level level, Player player, Entity watched) {
        if (player.isSpectator() || player.isCreative()) return null;
        Vec3 origin = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0F).normalize();
        AABB box = watched.getBoundingBox().inflate(0.3);
        double remaining = MAX_DISTANCE;
        List<TVBlockEntity> tvsPassed = new ArrayList<>();

        for (int bounces = 0; bounces <= MAX_BOUNCES; bounces++) {
            BlockHitResult hit = clip(level, player, origin, dir, remaining);
            Vec3 stop = SableCompat.projectOutOfSubLevel(level, hit.getLocation());
            if (bounces != 0 && box.clip(origin, stop).isPresent()) return new ScreenGaze(player, tvsPassed);
            if (hit.getType() == HitResult.Type.MISS) return null;

            Ray next = redirect(level, dir, hit);
            if (next == null) return null;
            if (next.fromTv() != null) tvsPassed.add(next.fromTv());
            remaining -= stop.distanceTo(origin);
            origin = next.origin();
            dir = next.dir();
        }
        return null;
    }

    private static BlockHitResult clip(Level level, Player player, Vec3 origin, Vec3 dir, double distance) {
        Vec3 end = origin.add(dir.scale(distance));
        return level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static boolean hasCameraNear(Level level, BlockPos watched) {
        double rangeSq = MAX_DISTANCE * MAX_DISTANCE;
        for (var entry : BroadcastManager.getInstance(level).getAll()) {
            GlobalPos pos = entry.getValue().getChunkSendPosition();
            if (pos == null || pos.dimension() != level.dimension()) continue;
            if (pos.pos().distSqr(watched) < rangeSq) return true;
        }
        return false;
    }

    @Nullable
    private static Ray redirect(Level level, Vec3 dir, BlockHitResult hit) {
        BlockPos hitPos = hit.getBlockPos();
        BlockState state = level.getBlockState(hitPos);

        if (state.getBlock() instanceof MirrorBlock) {
            Direction facing = state.getValue(MirrorBlock.FACING);
            if (hit.getDirection() != facing) return null;

            Vec3 worldHit = SableCompat.projectOutOfSubLevel(level, hit.getLocation());
            Vec3 localNormal = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 normal = SableCompat.projectOutOfSubLevel(level, hit.getLocation().add(localNormal))
                    .subtract(worldHit).normalize();
            Vec3 reflectedDir = dir.subtract(normal.scale(2 * dir.dot(normal))).normalize();
            return new Ray(worldHit.add(reflectedDir.scale(1.0e-3)), reflectedDir, null);
        }
        if (state.getBlock() instanceof TVBlock tvBlock) {
            if (hit.getDirection() != state.getValue(TVBlock.FACING)) {
                return null;
            }
            if (!(tvBlock.findMasterBlockEntity(level, hitPos, state) instanceof TVBlockEntity tv)) {
                return null;
            }
            return cameraRayThroughScreen(level, tv, hit.getLocation());
        }
        return null;
    }

    @Nullable
    private static Ray cameraRayThroughScreen(Level level, TVBlockEntity tv, Vec3 screenHit) {
        UUID feed = tv.getViewingFeedId();
        if (feed == null) return null;
        if (!(BroadcastManager.getInstance(level).getBroadcast(feed, level.isClientSide)
                instanceof ViewFinderBlockEntity camera)) {
            return null;
        }

        Vec2 uv = tv.getScreenRect().projectToLocal(screenHit);
        if (uv == null) return null;

        Vec3 dir = camera.getPixelRayDirection(uv.x, uv.y);
        Vec3 origin = camera.getGlobalPosition(1).add(dir.scale(LENS_EXIT_OFFSET));
        Ray ray = new Ray(origin, dir, tv);
        if (PlatHelper.getPhysicalSide().isClient() && ClientConfigs.rendersDebug()) {
            ViewFinderBlockEntityRenderer.debugLastCameraRay = ray;
        }
        return ray;
    }
}
