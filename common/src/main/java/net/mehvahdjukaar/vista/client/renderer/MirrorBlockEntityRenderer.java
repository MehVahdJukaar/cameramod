package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.mehvahdjukaar.candlelight.api.VirtualOverride;
import net.mehvahdjukaar.moonlight.api.client.util.LOD;
import net.mehvahdjukaar.moonlight.api.client.util.VertexUtil;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.client.MirrorReflection;
import net.mehvahdjukaar.vista.client.VistaRenderTypes;
import net.mehvahdjukaar.vista.client.textures.MirrorReflectionTexture;
import net.mehvahdjukaar.vista.client.textures.MirrorTextureManager;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlock;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class MirrorBlockEntityRenderer implements BlockEntityRenderer<MirrorBlockEntity> {

    // fallback nudge off the coplanar block face, only used where polygon offset doesn't hold.
    // See VistaLevelRenderer#needsManualSurfaceOffset.
    private static final float SURFACE_OFFSET = 0.01f;

    public MirrorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return ClientConfigs.MIRROR_RENDER_DISTANCE.get();
    }

    @VirtualOverride("neoforge")
    public AABB getRenderBoundingBox(MirrorBlockEntity tile) {
        AABB aabb = new AABB(tile.getBlockPos());
        Direction dir = tile.getBlockState().getValue(MirrorBlock.FACING);
        Vec2i connection = tile.getConnectedCount();
        float width = connection.x();
        float height = connection.y();
        if (dir == Direction.EAST) {
            return aabb.expandTowards(0, height - 1, -width + 1);
        } else if (dir == Direction.WEST) {
            return aabb.expandTowards(0, height - 1, width - 1);
        } else if (dir == Direction.NORTH) {
            return aabb.expandTowards(-width + 1, height - 1, 0);
        } else if (dir == Direction.SOUTH) {
            return aabb.expandTowards(width - 1, height - 1, 0);
        }
        return aabb;
    }

    @Override
    public void render(MirrorBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        Direction dir = blockEntity.getBlockState().getValue(MirrorBlock.FACING);
        double recession = MirrorBlock.surfaceRecession(blockEntity.getBlockState());

        // A mirror on a Sable sublevel has a plot-grid block position, and the nested render drives its
        // camera in plot space, so every bit of reflection math below has to use the eye in that space.
        // Mix the two and the eye reflects across a plane thousands of blocks off, leaving only sky.
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(blockEntity);

        // Cull against the recessed surface, not the front face, or the FAR model's quad disappears
        // once the viewer crosses the face but not the surface. Skipped on sublevels since LOD is
        // world space, the viewerInFront() gate below handles back-side culling there.
        LOD lod = LOD.at(blockEntity);
        if (subLevel == null && lod.isPlaneCulled(dir, (float) (0.5 - recession), 1.5f, 0f)) return;

        Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 planePoint = Vec3.atCenterOf(blockEntity.getBlockPos()).add(normal.scale(0.5 - recession));

        Minecraft mc = Minecraft.getInstance();
        Camera mainCamera = mc.gameRenderer.mainCamera;
        Vec3 eyeLocal = subLevel == null
                ? mainCamera.getPosition()
                : subLevel.renderPose(partialTick).transformPositionInverse(mainCamera.getPosition());
        MirrorReflection reflection = MirrorReflection.compute(planePoint, normal, eyeLocal);
        if (!reflection.viewerInFront()) return;

        // depth > 0 means we're inside another mirror's reflection
        int depth = VistaLevelRenderer.getCurrentDepth();
        MirrorReflectionTexture text;
        if (depth == 0) {
            Vec2i screenSize = blockEntity.getScreenPixelSize();
            // Capture the eye now, while we still hold the camera the BE was rendered with. The
            // reflection itself fires later in the frame and would resample a drifted camera. Bob
            // offset makes the reflection track the bobbed POV the quad is drawn with, otherwise the
            // far scene wobbles against the surface as you walk.
            Vec3 eye = mainCamera.getPosition().add(VistaLevelRenderer.getMainBobEyeOffset());
            if (subLevel != null) eye = subLevel.renderPose(partialTick).transformPositionInverse(eye);
            // at depth 0 the LOD from the top of render() is the player's real distance, except on
            // sublevels where it compares world space to plot space
            int texLod = subLevel == null
                    ? MirrorTextureManager.distanceLod(lod)
                    : MirrorTextureManager.distanceLod(eyeLocal, blockEntity.getBlockPos());
            text = MirrorTextureManager.getMirrorTexture(blockEntity, screenSize, eye, texLod);
        } else {
            text = resolveNestedTexture(blockEntity, eyeLocal, depth);
        }

        if (text == null) return;

        drawMirrorFace(blockEntity, dir, poseStack, buffer, text, recession);
    }

    // Texture lookup for a mirror drawn inside another mirror's reflection. SHARED keeps cost flat at
    // one texture per mirror but has wrong parallax past depth 0; RECURSIVE pays for a texture per
    // chain and draws nothing past the depth cap, since a wrong-parallax fallback gives the lie away.
    @Nullable
    private MirrorReflectionTexture resolveNestedTexture(MirrorBlockEntity blockEntity,
                                                          Vec3 eye, int depth) {
        ClientConfigs.MirrorRecursionMode mode = ClientConfigs.MIRROR_RECURSION_MODE.get();
        Vec2i screenSize = blockEntity.getScreenPixelSize();
        switch (mode) {
            case OFF:
                return null;
            case SHARED: {
                // Read-only reuse of the direct-view texture: re-queueing it with the reflected eye
                // would clobber its depth-0 PENDING entry and flicker the real reflection. Only
                // schedule when nothing has rendered yet, i.e. the mirror is visible solely through
                // this one, where there's no direct-view entry to stomp. LOD comes from the main
                // camera so we key to the same texture the depth-0 pass draws into.
                int sharedLod = MirrorTextureManager.distanceLod(blockEntity);
                MirrorReflectionTexture shared =
                        MirrorTextureManager.getMirrorTexture(blockEntity.getId(), screenSize, sharedLod);
                if (shared != null && shared.hasRendered()) return shared;
                return MirrorTextureManager.getMirrorTexture(blockEntity, screenSize, eye, sharedLod);
            }
            case RECURSIVE: {
                int maxDepth = ClientConfigs.MIRROR_MAX_RECURSION_DEPTH.get();
                if (depth > maxDepth) return null;
                List<UUID> chain = VistaLevelRenderer.getCurrentMirrorChain();
                return MirrorTextureManager.getMirrorTextureForChain(
                        blockEntity, screenSize, eye, depth, chain);
            }
            default:
                return null;
        }
    }

    private void drawMirrorFace(MirrorBlockEntity blockEntity, Direction dir, PoseStack poseStack,
                                MultiBufferSource buffer, MirrorReflectionTexture text,
                                double recession) {
        Vec2i connection = blockEntity.getConnectedCount();
        float w = connection.x();
        float h = connection.y();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - dir.toYRot()));
        // recession pushes the surface back into the cell for the FAR model
        float zFightOffset = VistaLevelRenderer.needsManualSurfaceOffset() ? SURFACE_OFFSET : 0f;
        poseStack.translate(0, 0, -0.5 + (float) recession - zFightOffset);

        Level level = blockEntity.getLevel();
        int skyBrightness = level.getBrightness(LightLayer.SKY, blockEntity.getBlockPos().relative(dir));
        int light = LightTexture.pack(15, skyBrightness);

        VertexConsumer vc = buffer.getBuffer(VistaRenderTypes.mirrorMaterial(
                text.getTextureLocation(), (int) w, (int) h));
        // Fixed 1px inset so frame_front shows around the quad. It doesn't scale with the grid, which
        // leaves a (16w-2)x(16h-2) surface matching the framebuffer 1:1 at any size.
        float inset = 1f / 16f;
        // Master sits at bottom-right in local-rotated space, so the quad runs from x=0.5-w to x=0.5.
        // UVs are rotated 180 since the framebuffer comes out flipped and mirrored against it.
        VertexUtil.addQuad(vc, poseStack,
                0.5f - w + inset, -0.5f + inset, 0.5f - inset, h - 0.5f - inset,
                1f, 1f, 0f, 0f,
                255, 255, 255, 255,
                VertexUtil.lightU(light), VertexUtil.lightV(light));

        poseStack.popPose();
    }
}
