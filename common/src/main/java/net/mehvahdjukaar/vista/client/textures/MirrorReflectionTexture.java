package net.mehvahdjukaar.vista.client.textures;

import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.client.MirrorReflection;
import net.mehvahdjukaar.vista.client.renderer.SceneCameraSetup;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlock;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

/**
 * Texture backing a mirror's reflection. The BE renderer stamps the frame's mirror and eye position
 * via {@link #setPending} (eye captured there, not at refresh, so it matches the requesting frame)
 * and the end-of-frame refresh draws it.
 * <p>
 * The reflection uses an off-axis frustum: camera at the viewer's mirror image looking perpendicular
 * into the mirror, near plane sitting exactly on the mirror plane and l/r/b/t taken from the mirror's
 * frame corners. That's what makes coplanar mirrors each show a different view and keeps the
 * reflection's parallax glued to the surface as you move.
 */
public class MirrorReflectionTexture extends PerspectiveTexture {

    // only kicks in when the eye is basically touching the surface, where depth precision collapses
    private static final float MIN_NEAR = 0.05f;
    private static final float FAR = 1000f;

    // Total frame width across the group: a fixed 1px per outer side regardless of grid size.
    // Matches the quad inset in MirrorBlockEntityRenderer and MirrorBlockEntity.FRAME_PIXELS.
    private static final double FRAME_BLOCKS = 2.0 / 16.0;

    // wall clock so the silvering fade stays snappy regardless of TPS or config update mode
    private static final long FADE_DURATION_NANOS = 300_000_000L;

    @Nullable
    private MirrorBlockEntity pendingMirror;
    @Nullable
    private Vec3 pendingEye;

    // The framebuffer samples as white until the first draw lands, so callers skip the mirror surface
    // entirely on that first frame and the flash never reaches the screen.
    private boolean hasRendered = false;
    private long firstRenderNanos = -1L;

    // 0 = mirror seen by the player, 1 = mirror seen inside one parent mirror, and so on
    private final int recursionDepth;
    // Parent mirrors that led here, empty at depth 0. VistaLevelRenderer reads it when pushing a
    // render frame so nested mirrors inside this one build their own chain from it.
    private final List<UUID> parentChain;

    public MirrorReflectionTexture(ResourceLocation resourceLocation, int width, int height, UUID id) {
        this(resourceLocation, width, height, id, 0, List.of());
    }

    public MirrorReflectionTexture(ResourceLocation resourceLocation, int width, int height,
                                   UUID id, int recursionDepth, List<UUID> parentChain) {
        super(resourceLocation, width, height, id);
        this.recursionDepth = recursionDepth;
        this.parentChain = List.copyOf(parentChain);
    }

    public int getRecursionDepth() {
        return recursionDepth;
    }

    public List<UUID> getParentChain() {
        return parentChain;
    }

    public boolean hasRendered() {
        return hasRendered;
    }

    public float getFadeProgress() {
        if (firstRenderNanos < 0) return 0f;
        long elapsed = System.nanoTime() - firstRenderNanos;
        if (elapsed >= FADE_DURATION_NANOS) return 1f;
        if (elapsed <= 0) return 0f;
        return (float) ((double) elapsed / (double) FADE_DURATION_NANOS);
    }

    public void setPending(MirrorBlockEntity mirror, Vec3 eye) {
        this.pendingMirror = mirror;
        this.pendingEye = eye;
    }

    @Override
    protected void refresh() {
        MirrorBlockEntity mirror = pendingMirror;
        Vec3 eye = pendingEye;
        pendingMirror = null;
        pendingEye = null;
        if (mirror == null || eye == null) return;
        renderReflection(mirror, eye);
    }

    public void renderReflection(MirrorBlockEntity mirror, Vec3 eye) {
        if (mirror.isRemoved()) return;
        Level level = mirror.getLevel();
        if (level == null) return;

        Direction dir = mirror.getBlockState().getValue(MirrorBlock.FACING);
        BlockPos pos = mirror.getBlockPos();
        double recession = MirrorBlock.surfaceRecession(mirror.getBlockState());
        Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());

        // Right axis from the VIEWER's POV, i.e. standing in front looking back along -normal.
        // Flipping the cross order gives viewer-left instead, which looks identical on a 1x1 mirror
        // but walks groupCenter away from the group for w>1 and shifts the whole reflection.
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 camRight = normal.cross(worldUp).normalize();

        // master sits at the bottom-left of a connected group, so the group centre is offset from it
        Vec2i connection = mirror.getConnectedCount();
        // frustum corners use the inset extent, matching the visible surface rather than the whole face
        double halfW = (connection.x() - FRAME_BLOCKS) * 0.5;
        double halfH = (connection.y() - FRAME_BLOCKS) * 0.5;

        Vec3 masterCenter = Vec3.atCenterOf(pos).add(normal.scale(0.5 - recession));
        Vec3 groupCenter = masterCenter
                .add(camRight.scale((1-connection.x() ) * 0.5))
                .add(worldUp.scale((connection.y() - 1) * 0.5));

        MirrorReflection reflection = MirrorReflection.compute(groupCenter, normal, eye);
        if (!reflection.viewerInFront()) return;

        Vec3 halfRight = camRight.scale(halfW);
        Vec3 halfUp    = worldUp.scale(halfH);
        Vec3 bottomLeft  = groupCenter.subtract(halfRight).subtract(halfUp);
        Vec3 bottomRight = groupCenter.add(halfRight).subtract(halfUp);
        Vec3 topLeft     = groupCenter.subtract(halfRight).add(halfUp);

        // All four corners lie on the mirror plane so they share one depth from the eye. Setting
        // near to it puts the near plane on the mirror, which z-clips everything between the
        // reflected camera and the surface (the wall behind it, the viewer's own legs) for free.
        double depth = reflection.signedDistance();
        float near = Math.max(MIN_NEAR, (float) depth);

        Vec3 vbl = bottomLeft.subtract(reflection.reflectedEye());
        Vec3 vbr = bottomRight.subtract(reflection.reflectedEye());
        Vec3 vtl = topLeft.subtract(reflection.reflectedEye());

        float scale = near / (float) depth;
        float l = (float) vbl.dot(camRight) * scale;
        float r = (float) vbr.dot(camRight) * scale;
        float b = (float) vbl.dot(worldUp)  * scale;
        float t = (float) vtl.dot(worldUp)  * scale;

        Matrix4f projection = new Matrix4f().frustum(l, r, b, t, near, FAR);

        final float camYaw = dir.toYRot();
        SceneCameraSetup setup = (camera, pt) ->
                setupMirrorCamera(camera, level, reflection.reflectedEye(), camYaw);

        // One block in front of the mirror. If the player can see the front face then this chunk is
        // visible and meshed, unlike the wall block a flush-mounted mirror would otherwise seed into.
        Vec3 bfsStart = groupCenter.add(normal.scale(1.0));

        // attenuate render distance per level so deep reflections cost exponentially less
        Integer renderDistanceOverride = null;
        if (recursionDepth > 0) {
            double divider = Math.pow(ClientConfigs.MIRROR_RECURSION_DIST_DIVIDER.get(), recursionDepth);
            renderDistanceOverride = (int) Math.max(1,
                    ClientConfigs.RENDER_DISTANCE.get() / divider);
        }

        // fov is ignored since we pass a projection. Moving the camera preserves winding, so
        // back-face culling stays as-is.
        VistaLevelRenderer.render(this, mirror, setup, 0f, false, projection, bfsStart,
                renderDistanceOverride);

        // Nothing is composited here: mirror_material.fsh takes this as Sampler0 and layers the
        // underlay and overlay itself.
        swapBackToFront();
        if (!hasRendered) {
            hasRendered = true;
            firstRenderNanos = System.nanoTime();
        }
    }

    // Pitch stays 0: the camera only needs to face into the plane, the off-axis projection bends the
    // frustum to the mirror's frame from there.
    private void setupMirrorCamera(Camera camera, Level level, Vec3 reflectedEye, float yaw) {
        camera.initialized = true;
        camera.level = level;
        if (camera.entity == null) {
            camera.entity = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        }
        Entity dummy = camera.getEntity();
        dummy.setPos(reflectedEye);
        dummy.setXRot(0f);
        dummy.setYRot(yaw);
        camera.setPosition(reflectedEye);
        camera.setRotation(yaw, 0f);
    }
}
