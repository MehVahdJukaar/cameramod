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

        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(blockEntity);

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
            Vec3 eye = mainCamera.getPosition().add(VistaLevelRenderer.getMainBobEyeOffset());
            if (subLevel != null) eye = subLevel.renderPose(partialTick).transformPositionInverse(eye);
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

    @Nullable
    private MirrorReflectionTexture resolveNestedTexture(MirrorBlockEntity blockEntity,
                                                          Vec3 eye, int depth) {
        ClientConfigs.MirrorRecursionMode mode = ClientConfigs.MIRROR_RECURSION_MODE.get();
        Vec2i screenSize = blockEntity.getScreenPixelSize();
        switch (mode) {
            case OFF:
                return null;
            case SHARED: {
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
        float inset = 1f / 16f;
        VertexUtil.addQuad(vc, poseStack,
                0.5f - w + inset, -0.5f + inset, 0.5f - inset, h - 0.5f - inset,
                1f, 1f, 0f, 0f,
                255, 255, 255, 255,
                VertexUtil.lightU(light), VertexUtil.lightV(light));

        poseStack.popPose();
    }
}
