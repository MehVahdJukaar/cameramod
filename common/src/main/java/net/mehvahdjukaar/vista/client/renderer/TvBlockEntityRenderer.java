package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.mehvahdjukaar.candlelight.api.VirtualOverride;
import net.mehvahdjukaar.moonlight.api.client.util.LOD;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.client.textures.ScreenFit;
import net.mehvahdjukaar.vista.client.textures.TvScreenVertexConsumers;
import net.mehvahdjukaar.vista.client.textures.perspective.LiveFeedTexturesManager;
import net.mehvahdjukaar.vista.client.video_source.BroadcastVideoSource;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.common.tv.TVBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.mehvahdjukaar.vista.integration.refurbished_furniture.RefurbishedFurnitureCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector3f;

public class TvBlockEntityRenderer implements BlockEntityRenderer<TVBlockEntity> {

    public TvBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 128; //very high, we then throttle based off the tv size
    }

    @Override
    public boolean shouldRenderOffScreen(TVBlockEntity blockEntity) {
        return PlatHelper.getPlatform().isFabric();
    }

    @VirtualOverride("neoforge")
    public AABB getRenderBoundingBox(TVBlockEntity tile) {
        AABB aabb = new AABB(tile.getBlockPos());
        Direction dir = tile.getBlockState().getValue(TVBlock.FACING);
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
    public void render(TVBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        // node and wires show regardless of the screen being on
        if (CommonConfigs.isTvElectricityEnabled()) {
            RefurbishedFurnitureCompat.renderNodeAndWires(blockEntity);
        }
        if (!blockEntity.isScreenOn(partialTick)) return;

        Direction dir = blockEntity.getBlockState().getValue(TVBlock.FACING);

        LOD lod = LOD.at(blockEntity);
        Vec2i screenSize = blockEntity.getScreenPixelSize();

        Vec2 screenCenter = blockEntity.getScreenBlockCenter();
        int max = Math.max(screenSize.x(), screenSize.y());

        if (lod.isPlaneCulled(dir, 0.5f, max / 16f * 1.5f, 0f)) return;
        float length = blockEntity.getConnectedCount().lengthSquared();

        if (length <= 1 && !lod.isMedium()) {
            return;
        }
        if (length <= 4 && !lod.isFar()) {
            return;
        }

        float yaw = dir.toYRot();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        float screenZ = TvScreenVertexConsumers.hasSfx() ? -0.5f : -0.505f;
        poseStack.translate(-screenCenter.x, screenCenter.y, screenZ);

        Vec2i pixelEffectRes = ClientConfigs.SCALE_PIXELS.get() ? screenSize : TVBlockEntity.MIN_SCREEN_PIXEL_SIZE_VEC;

        boolean paused = blockEntity.isPaused();
        boolean shouldUpdate = !paused && lod.within(ClientConfigs.UPDATE_DISTANCE.get());
        IntAnimationState switchAnim = blockEntity.fadeAnimation;
        IntAnimationState staticAnim = blockEntity.endermanLook.animation;
        int videoTicks = blockEntity.getPlaybackTicks();
        boolean showsTime = blockEntity.showsTime();

        IVideoSource videoSource = blockEntity.getVideoSource();
        VertexConsumer vc = videoSource
                .getVideoFrameBuilder(blockEntity, paused ? 1 : partialTick, buffer,
                        shouldUpdate, screenSize, pixelEffectRes,
                        videoTicks, paused, switchAnim, staticAnim, showsTime);

        //technically not correct as tv could be multiple block. just matters for transition so it's probably ok
        Level level = blockEntity.getLevel();
        int skyBrightness = level.getBrightness(LightLayer.SKY, blockEntity.getBlockPos().relative(dir));
        light = LightTexture.pack(15, skyBrightness);

        float quadW = screenSize.x() / 32f;
        float quadH = screenSize.y() / 32f;

        ScreenFit.Bounds fit = videoSource.getScreenFit()
                .computeBounds(screenSize.x() / (float) screenSize.y());
        float uInset = (1 - fit.uScale()) / 2f;
        float vInset = (1 - fit.vScale()) / 2f;
        quadW *= fit.quadScaleX();
        quadH *= fit.quadScaleY();

        addQuad(vc, poseStack, -quadW, -quadH, quadW, quadH,
                uInset, vInset, 1 - uInset, 1 - vInset, light);


        if (ClientConfigs.rendersDebug() && videoSource instanceof BroadcastVideoSource broadcast) {
            float endermanAnim = blockEntity.endermanLook.animation.getValue(partialTick);
            ScreenDebugText.draw(broadcast.uuid(), LiveFeedTexturesManager.SCHEDULER.get(), poseStack, buffer,
                    1, 1.5f, endermanAnim != 0 ? "p " + endermanAnim : null);
        }

        poseStack.popPose();
    }

    public static void addQuad(VertexConsumer builder, PoseStack poseStack,
                               float x0, float y0,
                               float x1, float y1,
                               int light) {
        addQuad(builder, poseStack, x0, y0, x1, y1, 0, 0, 1, 1, light);
    }

    public static void addQuad(VertexConsumer builder, PoseStack poseStack,
                               float x0, float y0,
                               float x1, float y1,
                               float u0, float v0,
                               float u1, float v1,
                               int light) {
        int lu = light & 0xFFFF;
        int lv = (light >> 16) & 0xFFFF;
        PoseStack.Pose last = poseStack.last();
        Vector3f normal = last.normal().transform(new Vector3f(0, 0, -1));
        vert(builder, poseStack, x0, y1, u1, v0, lu, lv, normal);
        vert(builder, poseStack, x1, y1, u0, v0, lu, lv, normal);
        vert(builder, poseStack, x1, y0, u0, v1, lu, lv, normal);
        vert(builder, poseStack, x0, y0, u1, v1, lu, lv, normal);
    }

    private static void vert(VertexConsumer builder, PoseStack poseStack,
                             float x, float y,
                             float u, float v,

                             int lu, int lv, Vector3f normal) {
        //not chained because of MC263524
        builder.addVertex(poseStack.last().pose(), x, y, 0);
        builder.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        builder.setUv(u, v);
        builder.setOverlay(OverlayTexture.NO_OVERLAY);
        builder.setUv2(lu, lv);
        builder.setNormal(normal.x, normal.y, normal.z);
    }

}
