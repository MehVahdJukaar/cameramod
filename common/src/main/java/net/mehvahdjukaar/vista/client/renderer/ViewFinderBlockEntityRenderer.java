package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlock;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;

public class ViewFinderBlockEntityRenderer implements BlockEntityRenderer<ViewFinderBlockEntity> {

    private final ModelPart head;
    private final ModelPart legs;
    private final ModelPart legsVisual;
    private final ModelPart pivot;
    private final ModelPart lens;
    private final ModelPart base;
    private final ModelPart model;



    public ViewFinderBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart model = context.bakeLayer(VistaModClient.VIEWFINDER_MODEL);
        this.legs = model.getChild("legs");
        this.legsVisual = legs.getChild("legs_visual");
        this.pivot = legs.getChild("head_pivot");
        this.head = pivot.getChild("head");
        this.lens = pivot.getChild("lens");
        this.base = model.getChild("base");
        this.model = model;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(ViewFinderBlockEntity tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        this.renderModel(tile, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (ClientConfigs.rendersDebug()) {
            renderDebug(tile, poseStack, bufferSource);
        }
    }


    public void renderModel(ViewFinderBlockEntity tile, float partialTick, PoseStack poseStack,
                            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        boolean isControlledByLocalInstance = ViewFinderController.isActiveFor(tile);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        Quaternionf rotation = tile.getBlockState().getValue(ViewFinderBlock.FACING).getOpposite().getRotation();

        poseStack.mulPose(rotation);


        float pitchRad = tile.getPitch(partialTick) * Mth.DEG_TO_RAD;
        float yawRad = tile.getYaw(partialTick) * Mth.DEG_TO_RAD;

        Vector3f forward = new Vector3f(0f, 0, 1);

        forward.rotateX(Mth.PI - pitchRad);

        forward.rotateY(Mth.PI - yawRad);
        forward.rotate(rotation.invert());

        yawRad = (float) Mth.atan2(forward.x, forward.z);

        pitchRad = (float) Mth.atan2(-forward.y, Mth.sqrt(forward.x * forward.x + forward.z * forward.z));
        //float rollRad = (float) Math.atan2(forward.y, forward.z);

        this.legs.yRot = yawRad;
        this.pivot.xRot = pitchRad;
        this.pivot.zRot = 0;

        this.legsVisual.visible = !isControlledByLocalInstance;
        this.head.visible = !isControlledByLocalInstance;
        this.base.visible = true;
        this.lens.visible = false;

        VertexConsumer builder = VistaModClient.VIEW_FINDER_MATERIAL.buffer(bufferSource, RenderType::entitySolid);
        this.model.render(poseStack, builder, packedLight, packedOverlay);

        ItemStack lens = tile.getDisplayedItem();
        if (!isControlledByLocalInstance && !lens.isEmpty() &&
                !VistaLevelRenderer.isViewFinderRenderingLiveFeed(tile)) {

            this.base.visible = false;
            this.legsVisual.visible = false;
            this.head.visible = false;
            this.lens.visible = true;


            ResourceLocation lensTexture = VistaModClient.VIEW_FINDER_LENS_TEXTURES.apply(lens.getItem());
            VertexConsumer lensBuilder = bufferSource.getBuffer(RenderType.entityCutoutNoCull(lensTexture));
            this.model.render(poseStack, lensBuilder, packedLight, packedOverlay);

            ResourceLocation emissiveTexture = VistaModClient.VIEW_FINDER_LENS_EMISSIVE_TEXTURES.apply(lens.getItem());
            if (emissiveTexture != null) {
                VertexConsumer emissiveBuilder = bufferSource.getBuffer(RenderType.breezeEyes(emissiveTexture));
                //int eyeLight = LightTexture.pack(LightTexture.FULL_BLOCK, LightTexture.sky(packedLight));
                this.model.render(poseStack, emissiveBuilder, packedLight, packedOverlay);
            }
        }
        poseStack.popPose();
    }


    public static LayerDefinition createMesh() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition legs = partdefinition.addOrReplaceChild("legs", CubeListBuilder.create(),
                PartPose.ZERO);

        legs.addOrReplaceChild("legs_visual", CubeListBuilder.create()
                        .texOffs(0, 36).addBox(5.0F, -4.0F, -2.0F, 2.0F, 10.0F, 4.0F)
                        .texOffs(12, 36).addBox(-7.0F, -4.0F, -2.0F, 2.0F, 10.0F, 4.0F),
                PartPose.ZERO);

        PartDefinition pivot = legs.addOrReplaceChild("head_pivot", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, -0.1745F, 0.0F, 0.0F));

        pivot.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(-5.0F, -6.0F, -4F, 10.0F, 12.0F, 8.0F),
                PartPose.ZERO);

        partdefinition.addOrReplaceChild("base", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-7.0F, 6.0F, -7.0F, 14.0F, 2.0F, 14.0F),
                PartPose.ZERO);

        //camera lens
        pivot.addOrReplaceChild("lens", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-3.0F, -4.0F, -5.0F, 6.0F, 6.0F, 1.0F),
                PartPose.rotation(0, Mth.PI, 0));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @VisibleForDebug
    public static WeakReference<Player> debugLastPlayer = new WeakReference<>(null);

    private static void renderDebug(ViewFinderBlockEntity tile, PoseStack poseStack, MultiBufferSource bufferSource) {

        Player player = debugLastPlayer.get();
        if (player != null) {
            poseStack.pushPose();
            Vec3 pos = player.getEyePosition().subtract(Vec3.atLowerCornerOf(tile.getBlockPos()));
            poseStack.translate(pos.x, pos.y, pos.z);
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer vc = bufferSource.getBuffer(RenderType.lines());
            var last = pose.pose();
            Matrix3f normal = pose.normal();
            vc.vertex(last, 0, 0, 0)
                    .color(255, 0, 255, 255)
                    .normal(normal, 0, 1, 0);
            vc.vertex(last, 0, 1, 0)
                    .color(255, 0, 255, 255)
                    .normal(normal, 0, 1, 0);

            float tileYaw = tile.getYaw();
            float tilePitch = tile.getPitch();
            var tileView = Vec3.directionFromRotation(tilePitch, tileYaw).normalize();
            vc.vertex(last, 0, 0, 0)
                    .color(30, 30, 255, 255)
                    .normal(normal, 0, 1, 0);
            vc.vertex(last, (float) tileView.x, (float) tileView.y, (float) tileView.z)
                    .color(30, 30, 255, 255)
                    .normal(normal, 0, 1, 0);

            float headY = player.getYHeadRot();
            float headX = player.getXRot();
            var view = Vec3.directionFromRotation(headX, headY).normalize();
            vc.vertex(last, 0, 0, 0)
                    .color(30, 255, 30, 255)
                    .normal(normal, 0, 1, 0);
            vc.vertex(last, (float) view.x, (float) view.y, (float) view.z)
                    .color(30, 255, 30, 255)
                    .normal(normal, 0, 1, 0);

            poseStack.popPose();
        }
    }
}
