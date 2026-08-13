package net.mehvahdjukaar.vista.client.textures;

import com.mojang.blaze3d.platform.NativeImage;
import net.mehvahdjukaar.moonlight.api.resources.textures.TextureCollager;
import net.mehvahdjukaar.moonlight.api.resources.textures.TextureImage;
import net.mehvahdjukaar.moonlight.api.util.math.Rect2D;

import static net.mehvahdjukaar.vista.client.textures.ScalingMode.FIT_HEIGHT;
import static net.mehvahdjukaar.vista.client.textures.ScalingMode.FIT_WIDTH;

public final class ImageRescaler {

    public static NativeImage resize(NativeImage source,
                                     int targetW, int targetH,
                                     ScalingMode fitMode,
                                     boolean bilinear) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW == targetW && srcH == targetH) return source;

        TextureImage srcTex = TextureImage.of(source);
        TextureImage destTex = TextureImage.createNew(targetW, targetH);

        Rect2D srcRect;
        Rect2D dstRect;

        switch (fitMode) {
            case STRETCH:
                srcRect = new Rect2D(0, 0, srcW, srcH);
                dstRect = new Rect2D(0, 0, targetW, targetH);
                break;

            case FIT_WIDTH: {
                int outH = Math.max(1, (int) (targetW * (double) srcH / srcW));
                srcRect = new Rect2D(0, 0, srcW, srcH);
                dstRect = new Rect2D(0, 0, targetW, outH);
                break;
            }

            case FIT_HEIGHT: {
                int outW = Math.max(1, (int) (targetH * (double) srcW / srcH));
                srcRect = new Rect2D(0, 0, srcW, srcH);
                dstRect = new Rect2D(0, 0, outW, targetH);
                break;
            }

            case CONTAIN: {
                double scale = Math.min((double) targetW / srcW, (double) targetH / srcH);
                int scaledW = (int) (srcW * scale);
                int scaledH = (int) (srcH * scale);
                int offX = (targetW - scaledW) / 2;
                int offY = (targetH - scaledH) / 2;
                srcRect = new Rect2D(0, 0, srcW, srcH);
                dstRect = new Rect2D(offX, offY, scaledW, scaledH);
                break;
            }

            case COVER: {
                double scale = Math.max((double) targetW / srcW, (double) targetH / srcH);
                int cropW = (int) Math.ceil(targetW / scale);
                int cropH = (int) Math.ceil(targetH / scale);
                int cropX = (srcW - cropW) / 2;
                int cropY = (srcH - cropH) / 2;
                srcRect = new Rect2D(cropX, cropY, cropW, cropH);
                dstRect = new Rect2D(0, 0, targetW, targetH);
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown fit mode: " + fitMode);
        }

        if (fitMode == FIT_WIDTH && dstRect.height() > targetH) {
            int newHeight = targetH;
            double ratio = (double) newHeight / dstRect.height();
            int newSrcHeight = (int) (srcRect.height() * ratio);
            srcRect = new Rect2D(srcRect.x(), srcRect.y(), srcRect.width(), newSrcHeight);
            dstRect = new Rect2D(dstRect.x(), dstRect.y(), dstRect.width(), newHeight);
        }
        if (fitMode == FIT_HEIGHT && dstRect.width() > targetW) {
            int newWidth = targetW;
            double ratio = (double) newWidth / dstRect.width();
            int newSrcWidth = (int) (srcRect.width() * ratio);
            srcRect = new Rect2D(srcRect.x(), srcRect.y(), newSrcWidth, srcRect.height());
            dstRect = new Rect2D(dstRect.x(), dstRect.y(), newWidth, dstRect.height());
        }

        if (dstRect.x() < 0) {
            int offset = -dstRect.x();
            double ratio = (double) offset / dstRect.width();
            int srcOffset = (int) (srcRect.width() * ratio);
            srcRect = new Rect2D(srcRect.x() + srcOffset, srcRect.y(), srcRect.width() - srcOffset, srcRect.height());
            dstRect = new Rect2D(0, dstRect.y(), dstRect.width() - offset, dstRect.height());
        }
        if (dstRect.y() < 0) {
            int offset = -dstRect.y();
            double ratio = (double) offset / dstRect.height();
            int srcOffset = (int) (srcRect.height() * ratio);
            srcRect = new Rect2D(srcRect.x(), srcRect.y() + srcOffset, srcRect.width(), srcRect.height() - srcOffset);
            dstRect = new Rect2D(dstRect.x(), 0, dstRect.width(), dstRect.height() - offset);
        }
        if (dstRect.x() + dstRect.width() > targetW) {
            int overflow = dstRect.x() + dstRect.width() - targetW;
            double ratio = (double) overflow / dstRect.width();
            int srcReduction = (int) (srcRect.width() * ratio);
            srcRect = new Rect2D(srcRect.x(), srcRect.y(), srcRect.width() - srcReduction, srcRect.height());
            dstRect = new Rect2D(dstRect.x(), dstRect.y(), dstRect.width() - overflow, dstRect.height());
        }
        if (dstRect.y() + dstRect.height() > targetH) {
            int overflow = dstRect.y() + dstRect.height() - targetH;
            double ratio = (double) overflow / dstRect.height();
            int srcReduction = (int) (srcRect.height() * ratio);
            srcRect = new Rect2D(srcRect.x(), srcRect.y(), srcRect.width(), srcRect.height() - srcReduction);
            dstRect = new Rect2D(dstRect.x(), dstRect.y(), dstRect.width(), dstRect.height() - overflow);
        }

        if (fitMode == FIT_WIDTH && dstRect.height() < targetH) {
            int newY = (targetH - dstRect.height()) / 2;
            dstRect = new Rect2D(dstRect.x(), newY, dstRect.width(), dstRect.height());
        }
        if (fitMode == FIT_HEIGHT && dstRect.width() < targetW) {
            int newX = (targetW - dstRect.width()) / 2;
            dstRect = new Rect2D(newX, dstRect.y(), dstRect.width(), dstRect.height());
        }

        int clampedSrcX = Math.clamp(srcRect.x(), 0, srcW - 1);
        int clampedSrcY = Math.clamp(srcRect.y(), 0, srcH - 1);
        int maxSrcWidth = srcW - clampedSrcX;
        int maxSrcHeight = srcH - clampedSrcY;
        int clampedSrcW = Math.clamp(srcRect.width(), 1, maxSrcWidth);
        int clampedSrcH = Math.clamp(srcRect.height(), 1, maxSrcHeight);
        srcRect = new Rect2D(clampedSrcX, clampedSrcY, clampedSrcW, clampedSrcH);

        int clampedDstX = Math.clamp(dstRect.x(), 0, targetW - 1);
        int clampedDstY = Math.clamp(dstRect.y(), 0, targetH - 1);
        int maxDstWidth = targetW - clampedDstX;
        int maxDstHeight = targetH - clampedDstY;
        int clampedDstW = Math.clamp(dstRect.width(), 1, maxDstWidth);
        int clampedDstH = Math.clamp(dstRect.height(), 1, maxDstHeight);
        dstRect = new Rect2D(clampedDstX, clampedDstY, clampedDstW, clampedDstH);

        if (srcRect.width() <= 0 || srcRect.height() <= 0 || dstRect.width() <= 0 || dstRect.height() <= 0) {
            throw new IllegalStateException("After clipping, one of the rectangles became non-positive");
        }

        TextureCollager.Builder builder = TextureCollager.builder(srcW, srcH, targetW, targetH)
                .copyFrom(srcRect.x(), srcRect.y(), srcRect.width(), srcRect.height())
                .to(dstRect.x(), dstRect.y(), dstRect.width(), dstRect.height());

        if (bilinear) {
            builder.bilinearScaling();
        }

        TextureCollager collager = builder.build();
        collager.apply(srcTex, destTex);

        return destTex.getImage();
    }
}