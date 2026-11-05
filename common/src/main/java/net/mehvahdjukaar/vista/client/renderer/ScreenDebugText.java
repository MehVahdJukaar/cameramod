package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.mehvahdjukaar.moonlight.api.misc.RollingBuffer;
import net.mehvahdjukaar.vista.client.AdaptiveUpdateScheduler;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.*;

public class ScreenDebugText {

    private static final Map<UUID, RollingBuffer<Long>> UPDATE_TIMES = new HashMap<>();

    public static void recordUpdate(UUID id) {
        if (!ClientConfigs.rendersDebug()) return;
        UPDATE_TIMES.computeIfAbsent(id, k -> new RollingBuffer<>(20)).push(System.nanoTime());
    }

    public static void clear() {
        UPDATE_TIMES.clear();
    }

    //pose must be on screen face, x right, y up, looking at viewer. x and y are in blocks
    public static void draw(UUID id, AdaptiveUpdateScheduler<?> scheduler, PoseStack poseStack, MultiBufferSource buffer,
                            float x, float y, String... extraLines) {
        RollingBuffer<Long> updateTimes = UPDATE_TIMES.get(id);
        if (updateTimes == null) return;

        List<String> lines = new ArrayList<>();
        double intervalMs = averageIntervalMs(updateTimes);
        lines.add(String.format("up fps %.1f", intervalMs == 0 ? 0 : 1000 / intervalMs));
        lines.add(String.format("up time %.3f ms", scheduler.getAverageUpdateMsPerFrame()));

        for (String l : extraLines) {
            if (l != null) lines.add(l);
        }

        poseStack.pushPose();
        poseStack.translate(x, y, -0.1);
        poseStack.mulPose(Axis.YP.rotationDegrees(-180));
        poseStack.scale(1 / 32F, -1 / 32f, 1 / 32f);

        Font font = Minecraft.getInstance().font;
        int lineY = 7;
        for (String line : lines) {
            font.drawInBatch(line, 0, lineY, -1, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL,
                    OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT);
            lineY -= 9;
        }

        poseStack.popPose();
    }

    private static double averageIntervalMs(RollingBuffer<Long> times) {
        int size = times.size();
        if (size < 2) return 0;

        long totalDiff = 0;
        for (int i = 1; i < size; i++) {
            totalDiff += times.get(i) - times.get(i - 1);
        }
        return totalDiff / (size - 1d) / 1_000_000.0;
    }


}
