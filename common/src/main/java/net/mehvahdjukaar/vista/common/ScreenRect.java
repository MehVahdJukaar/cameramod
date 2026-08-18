package net.mehvahdjukaar.vista.common;

import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A flat rectangular surface in the world (a TV screen, a mirror grid). The normal points out of
 * the front face; width and height are in blocks. Local coordinates are as seen by someone facing
 * the surface, so +x is their right.
 */
public record ScreenRect(Vec3 center, Vec3 normal, float width, float height) {

    public static final Vec3 UP = new Vec3(0, 1, 0);

    public static Vec3 rightOf(Vec3 normal) {
        return UP.cross(normal);
    }

    public Vec3 right() {
        return rightOf(normal);
    }

    public Vec3 up() {
        return UP;
    }

    /**
     * Local UV of a world point on this rect, in [-0.5, 0.5]. Null if the point is off it.
     */
    @Nullable
    public Vec2 projectLocal(Vec3 worldPoint) {
        Vec3 local = worldPoint.subtract(center);
        double x = local.dot(right());
        double y = local.dot(UP);
        if (Math.abs(x) > width / 2f || Math.abs(y) > height / 2f) return null;
        return new Vec2((float) (x / width), (float) (y / height));
    }

    public Vec3 localToWorld(Vec2 localHit) {
        return center.add(right().scale(localHit.x * width)).add(UP.scale(localHit.y * height));
    }
}
