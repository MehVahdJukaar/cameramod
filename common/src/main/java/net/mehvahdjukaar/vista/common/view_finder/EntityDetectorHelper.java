package net.mehvahdjukaar.vista.common.view_finder;

import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class EntityDetectorHelper {

    private static final double DETECTOR_LATERAL_MARGIN_BLOCKS = 1.0D;

    private static final TagKey<Block> GLASS_BLOCKS_TAG = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("c", "glass_blocks")
    );
    private static final TagKey<Block> GLASS_PANES_TAG = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("c", "glass_panes")
    );

    public static boolean isDetectorFilterItem(ItemStack stack) {
        return stack.is(Items.REDSTONE_BLOCK);
    }

    public static boolean hasDetectedEntities(ViewFinderBlockEntity viewFinder, Level level) {
        return !getDetectedEntities(viewFinder, level).isEmpty();
    }

    public static List<Entity> getDetectedEntities(ViewFinderBlockEntity viewFinder, Level level) {
        if (!hasAnyEnabledTargetType()) {
            return List.of();
        }

        int range = CommonConfigs.ENTITY_DETECTOR_RANGE.get();
        Vec3 forward = Vec3.directionFromRotation(viewFinder.getPitch(), viewFinder.getYaw()).normalize();
        Vec3 cameraPos = viewFinder.getBlockPos().getCenter().add(forward.scale(0.55));
        AABB searchArea = new AABB(cameraPos, cameraPos).inflate(range);

        List<Entity> detected = level.getEntities((Entity) null, searchArea, e ->
                e.isAlive() &&
                        !e.isSpectator() &&
                        isEntityTypeEnabled(e) &&
                        isInViewCone(viewFinder, e, cameraPos, range) &&
                        hasDetectorLineOfSight(level, viewFinder.getBlockPos(), cameraPos, e.getBoundingBox().getCenter())
        );

        return detected;
    }

    private static boolean hasDetectorLineOfSight(Level level, BlockPos sourcePos, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) return true;
        Vec3 n = dir.normalize();
        Vec3 start = from;

        for (int i = 0; i < 32; i++) {
            BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (hit.getType() != HitResult.Type.BLOCK) {
                return false;
            }

            BlockPos hitPos = hit.getBlockPos();
            if (hitPos.equals(sourcePos)) {
                start = hit.getLocation().add(n.scale(0.06));
                continue;
            }
            BlockState state = level.getBlockState(hitPos);
            if (!isDetectorTransparent(state)) {
                return false;
            }

            start = hit.getLocation().add(n.scale(0.04));
            if (start.distanceToSqr(to) < 0.0025) {
                return true;
            }
        }
        return true;
    }

    private static boolean isDetectorTransparent(BlockState state) {
        return state.is(GLASS_BLOCKS_TAG) || state.is(GLASS_PANES_TAG);
    }

    private static boolean isInViewCone(ViewFinderBlockEntity viewFinder, Entity entity, Vec3 cameraPos, float range) {
        Vec3 targetPos = entity.getBoundingBox().getCenter();
        Vec3 toTarget = targetPos.subtract(cameraPos);

        double distanceSq = toTarget.lengthSqr();
        if (distanceSq < 1.0E-6 || distanceSq > (double) (range * range)) {
            return false;
        }

        Vec3 forward = Vec3.directionFromRotation(viewFinder.getPitch(), viewFinder.getYaw()).normalize();
        float halfFov = viewFinder.getFOV() * 0.5f;
        double halfFovTan = Math.tan(halfFov * Mth.DEG_TO_RAD);
        double zoomScaledLateralMargin = DETECTOR_LATERAL_MARGIN_BLOCKS * viewFinder.getFOVModifier();

        double forwardDistance = toTarget.dot(forward);
        if (forwardDistance <= 0.0D) {
            return false;
        }

        double lateralSq = Math.max(0.0D, distanceSq - forwardDistance * forwardDistance);
        double maxLateral = forwardDistance * halfFovTan + zoomScaledLateralMargin;
        return lateralSq <= (maxLateral * maxLateral);
    }

    private static boolean hasAnyEnabledTargetType() {
        Set<String> filters = getEntityFilterSet();
        return filters.contains("hostile") ||
                filters.contains("passive") ||
                filters.contains("players");
    }

    private static boolean isEntityTypeEnabled(Entity entity) {
        Set<String> filters = getEntityFilterSet();
        if (entity instanceof Player) {
            return filters.contains("players");
        }
        if (entity instanceof Monster) {
            return filters.contains("hostile");
        }
        if (entity instanceof Mob) {
            return filters.contains("passive");
        }
        return false;
    }

    private static Set<String> getEntityFilterSet() {
        String raw = CommonConfigs.ENTITY_DETECTOR_FILTER.get();
        if (raw == null || raw.isBlank()) return Set.of();

        return Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

}
