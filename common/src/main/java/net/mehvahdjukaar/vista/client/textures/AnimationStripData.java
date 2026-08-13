package net.mehvahdjukaar.vista.client.textures;

import net.minecraft.client.renderer.texture.SpriteContents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record AnimationStripData(
        int frameWidth,
        int frameHeight,
        int frameCount,
        int atlasWidth,
        int atlasHeight,
        int atlasColumns,
        int atlasRows,
        float frameRelativeH,
        float frameRelativeW,
        SpriteContents.FrameInfo[] frameInfos,
        int totalDuration
) {

    public static AnimationStripData create(SpriteContents spriteContents) {
        int atlasH = spriteContents.originalImage.getHeight();
        int atlasW = spriteContents.originalImage.getWidth();
        int spriteW = spriteContents.width();
        int spriteH = spriteContents.height();
        float invFullH = 1f / atlasH;
        float invFullW = 1f / atlasW;

        //calculate average time per frame
        SpriteContents.AnimatedTexture animatedTexture = spriteContents.animatedTexture;
        SpriteContents.FrameInfo[] times = new SpriteContents.FrameInfo[]{};
        if (animatedTexture != null) {
            List<SpriteContents.FrameInfo> allTimes = new ArrayList<>(animatedTexture.frames);
            times = allTimes.toArray(new SpriteContents.FrameInfo[0]);
        }

        int totalDuration = Arrays.stream(times)
                .mapToInt(f -> f.time)
                .sum();

        return new AnimationStripData(
                spriteW,
                spriteH,
                spriteContents.getFrameCount(),
                atlasW,
                atlasH,
                atlasW / spriteW,
                atlasH / spriteH,
                spriteH * invFullH,
                spriteW * invFullW,
                times,
                totalDuration
        );
    }

    public static final AnimationStripData EMPTY =
            new AnimationStripData(16, 16, 1, 16, 16,
                    1,1,1f, 1f, new SpriteContents.FrameInfo[]{}, 1);

    public float getU(float u, int time) {
        int frameIndex = getFrameIndexFromTime(time);
        return getUForFrame(u, frameIndex);
    }

    public float getUForFrame(float u, int frameIndex) {
        int col = frameIndex / atlasRows;
        return u * frameRelativeW + col * frameRelativeW;
    }

    public float getV(float v, int time) {
        int frameIndex = getFrameIndexFromTime(time);
        return getVForFrame(v, frameIndex);
    }

    public float getVForFrame(float v, int frameIndex) {
        int row = frameIndex % atlasRows;
        return v * frameRelativeH + row * frameRelativeH;
    }

    // time in ticks to actual textureMatrix frame index
    public int getFrameIndexFromTime(int time) {
        if (frameInfos.length == 0) return 0;

        // Single repeated frame
        if (frameInfos.length == 1) {
            SpriteContents.FrameInfo f = frameInfos[0];
            return f.index;
        }

        int localTime = time % totalDuration;
        int acc = 0;

        for (var f : frameInfos) {
            acc += f.time;
            if (localTime < acc) {
                return f.index;
            }
        }

        return frameInfos[0].index; // fallback
    }
}
