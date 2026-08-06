package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.mehvahdjukaar.moonlight.api.client.util.RotHlpr;
import net.mehvahdjukaar.moonlight.api.client.util.VertexUtil;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.VistaRenderTypes;
import net.mehvahdjukaar.vista.common.wave_gate.WaveGateBlock;
import net.mehvahdjukaar.vista.common.wave_gate.WaveGateBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class WaveGateBlockEntityRenderer implements BlockEntityRenderer<WaveGateBlockEntity> {
    public WaveGateBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(WaveGateBlockEntity tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {

        BlockState blockState = tile.getBlockState();
        if (blockState.getValue(WaveGateBlock.POWERED)) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.pushPose();
        poseStack.translate(0.5, 14 / 16f, 0.5);
        // poseStack.mulPose(RotHlpr.rot(tile.getBlockState().getValue(WaveGateBlock.FACING)));

        var facingRot = RotHlpr.rot(blockState.getValue(WaveGateBlock.FACING));

        poseStack.mulPose(facingRot);

        Vector3f dir = camera.getPosition()
                .subtract(
                        tile.getBlockPos().getX() + 0.5,
                        tile.getBlockPos().getY() + 14 / 16f,
                        tile.getBlockPos().getZ() + 0.5
                )
                .toVector3f()
                .normalize()
                .rotate(facingRot.get(new Quaternionf()).invert());

        poseStack.mulPose(Axis.XP.rotation((float) (Mth.PI - Mth.atan2(dir.y(), dir.z()))));

        long ticks = tile.getLevel().getGameTime();

        VertexConsumer builder = VistaModClient.WAVE_EFFECT.buffer(bufferSource,
                r -> VistaRenderTypes.WAVE_PARTICLE);

        float time = (ticks + partialTick) * 0.08f;

        float baseWidth = 6 / 16f;
        float baseHeight = 3.5f / 16f;

        float width = baseWidth + (1 + Mth.sin(time * 1.97f + 1.3f)) * 0.01f;
        float height = baseHeight + (1 + Mth.cos(time * 2.171f + 4.7f)) * 0.02f;

        VertexUtil.addQuad(
                builder,
                poseStack,
                -width,
                -height,
                width,
                height,
                2 / 16f, 8 / 16f,
                14 / 16f, 15 / 16f,
                255, 255, 255, 255,
                VertexUtil.lightU(LightTexture.FULL_BRIGHT),
                VertexUtil.lightV(LightTexture.FULL_BRIGHT));

        poseStack.popPose();
    }

}
