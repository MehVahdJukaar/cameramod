package net.mehvahdjukaar.vista.client.textures;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class GifPathSpriteSource implements SpriteSource {

    public static final MapCodec<GifPathSpriteSource> CODEC = RecordCodecBuilder.mapCodec((i) -> i.group(
            Codec.STRING.fieldOf("source").forGetter((l) -> l.sourcePath),
            Codec.STRING.fieldOf("prefix").forGetter((l) -> l.idPrefix)
    ).apply(i, GifPathSpriteSource::new));

    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    private final String sourcePath;
    private final String idPrefix;

    public GifPathSpriteSource(String sourcePath, String idPrefix) {
        this.sourcePath = sourcePath;
        this.idPrefix = idPrefix;
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        FileToIdConverter fileToIdConverter = new FileToIdConverter("textures/" + this.sourcePath, ".gif");
        fileToIdConverter.listMatchingResources(resourceManager).forEach((resourceLocation, resource) -> {
            ResourceLocation id = fileToIdConverter.fileToId(resourceLocation).withPrefix(this.idPrefix);
            Optional<Resource> optional = resourceManager.getResource(resourceLocation);
            if (optional.isEmpty()) {
                VistaMod.LOGGER.warn("Unable to find texture {}", id);
            } else {
                output.add(id, spriteLoader ->
                        loadGifContent(id, optional.get()));
            }
        });
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }


    public static @Nullable SpriteContents loadGifContent(ResourceLocation id, Resource resource) {
        try (InputStream inputStream = resource.open();
             ImageInputStream imageStream = ImageIO.createImageInputStream(inputStream)) {

            ImageReader reader = gifReader(imageStream);
            int frameCount = reader.getNumImages(true);

            int[] wh = new int[2];
            List<BufferedImage> frames = readFramesComposited(reader, frameCount, wh);
            List<Integer> frameTicks = readFrameTicks(reader, frameCount);

            int w = wh[0], h = wh[1];

            int maxTextureSize = RenderSystem.maxSupportedTextureSize() / 2;
            NativeImage strip = buildTiledAtlas(frames, w, h, maxTextureSize);
            // NativeImage strip = buildVerticalStrip(frames, w, h);
            strip.writeToFile(new File("temp_image_dump.png")); // debug if needed

            AnimationMetadataSection anim = buildAnimationMeta(frameTicks, w, h, frames.size());

            FrameSize size = new FrameSize(w, h);
            ResourceMetadata meta = new ResourceMetadata.Builder()
                    .put(AnimationMetadataSection.SERIALIZER, anim)
                    .build();

            return new SpriteContents(id, size, strip, meta);
        } catch (IllegalArgumentException | IOException e) {
            VistaMod.LOGGER.error("unable to build animated strip for {}", id, e);
        }
        return null;
    }


    private static ImageReader gifReader(ImageInputStream iis) throws IOException {
        Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
        if (!it.hasNext()) throw new IOException("No GIF ImageReader available");
        ImageReader r = it.next();
        r.setInput(iis, false, false);
        return r;
    }

    private static List<Integer> readFrameTicks(ImageReader reader, int count) {
        List<Integer> centis = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int cs = 5;
            try {
                var meta = reader.getImageMetadata(i);
                var root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
                var gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                if (gce != null) cs = Math.max(1, Integer.parseInt(gce.getAttribute("delayTime")));
            } catch (Exception ignored) {}
            centis.add(cs);
        }

        List<Integer> millis = new ArrayList<>(count);
        int totalMs = 0;
        for (int cs : centis) {
            int ms = cs * 10;
            millis.add(ms);
            totalMs += ms;
        }

        List<Integer> ticks = new ArrayList<>(count);
        double accError = 0.0;
        for (int ms : millis) {
            double exactTicks = ms / 50.0;
            double tickWithError = exactTicks + accError;
            int assigned = Math.max(1, (int) Math.round(tickWithError));
            accError = tickWithError - assigned;
            ticks.add(assigned);
        }

        int expectedTotalTicks = Math.max(1, (int) Math.round(totalMs / 50.0));
        int actualTotalTicks = ticks.stream().mapToInt(Integer::intValue).sum();
        if (actualTotalTicks != expectedTotalTicks && count > 0) {
            int diff = expectedTotalTicks - actualTotalTicks;
            int idx = 0;
            int step = diff > 0 ? 1 : -1;
            diff = Math.abs(diff);
            while (diff-- > 0) {
                ticks.set(idx, Math.max(1, ticks.get(idx) + step));
                idx = (idx + 1) % count;
            }
        }
        return ticks;
    }

    private static final String GIF_IMAGE_META = "javax_imageio_gif_image_1.0";
    private static final String GIF_STREAM_META = "javax_imageio_gif_stream_1.0";

    private static List<BufferedImage> readFramesComposited(ImageReader reader, int count, int[] outWH) throws IOException {

        IIOMetadata streamMeta = reader.getStreamMetadata();

        int screenW = -1;
        int screenH = -1;

        if (streamMeta != null) {
            try {
                IIOMetadataNode root =
                        (IIOMetadataNode) streamMeta.getAsTree(GIF_STREAM_META);

                IIOMetadataNode lsd =
                        (IIOMetadataNode) root
                                .getElementsByTagName("LogicalScreenDescriptor")
                                .item(0);

                if (lsd != null) {
                    screenW = Integer.parseInt(lsd.getAttribute("logicalScreenWidth"));
                    screenH = Integer.parseInt(lsd.getAttribute("logicalScreenHeight"));
                }
            } catch (Exception ignored) {
            }
        }

        if (screenW <= 0 || screenH <= 0) {
            BufferedImage first = reader.read(0);
            screenW = first.getWidth();
            screenH = first.getHeight();
        }

        outWH[0] = screenW;
        outWH[1] = screenH;

        BufferedImage canvas =
                new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB);

        Graphics2D gCanvas = canvas.createGraphics();

        gCanvas.setComposite(AlphaComposite.Clear);
        gCanvas.fillRect(0, 0, screenW, screenH);

        gCanvas.setComposite(AlphaComposite.SrcOver);

        gCanvas.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );

        BufferedImage prevSnapshot = null;

        List<BufferedImage> out = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {

            IIOMetadata meta = reader.getImageMetadata(i);
            FrameMeta fm = parseFrameMeta(meta);

            // snapshot BEFORE drawing current frame
            if (fm.disposal == 3) {
                prevSnapshot =
                        new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB);

                prevSnapshot.getRaster().setDataElements(
                        0,
                        0,
                        screenW,
                        screenH,
                        canvas.getRaster().getDataElements(
                                0,
                                0,
                                screenW,
                                screenH,
                                null
                        )
                );
            }

            BufferedImage frameImg = reader.read(i);

            // some readers hand back already composited full-size frames
            boolean fullCanvasFrame =
                    frameImg.getWidth() == screenW &&
                            frameImg.getHeight() == screenH;

            if (fullCanvasFrame) {
                gCanvas.drawImage(frameImg, 0, 0, null);
            } else {
                gCanvas.drawImage(frameImg, fm.left, fm.top, null);
            }

            BufferedImage copy =
                    new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB);

            copy.getRaster().setDataElements(
                    0,
                    0,
                    screenW,
                    screenH,
                    canvas.getRaster().getDataElements(
                            0,
                            0,
                            screenW,
                            screenH,
                            null
                    )
            );

            out.add(copy);

            // disposal AFTER current frame
            switch (fm.disposal) {

                // restore to transparent
                case 2 -> {
                    gCanvas.setComposite(AlphaComposite.Clear);

                    gCanvas.fillRect(
                            fm.left,
                            fm.top,
                            fm.width,
                            fm.height
                    );

                    gCanvas.setComposite(AlphaComposite.SrcOver);
                }

                // restore to previous
                case 3 -> {
                    if (prevSnapshot != null) {
                        canvas.getRaster().setDataElements(
                                0,
                                0,
                                screenW,
                                screenH,
                                prevSnapshot.getRaster().getDataElements(
                                        0,
                                        0,
                                        screenW,
                                        screenH,
                                        null
                                )
                        );
                    }
                }

                // 0/1 -> keep previous contents
            }
        }

        gCanvas.dispose();

        if (out.isEmpty()) {
            throw new IOException("GIF contained no frames");
        }

        return out;
    }
    private static class FrameMeta {
        int left, top, width, height;
        int disposal; // 0/1 none, 2 background, 3 previous
    }

    private static FrameMeta parseFrameMeta(IIOMetadata meta) {
        FrameMeta fm = new FrameMeta();
        try {
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(GIF_IMAGE_META);
            IIOMetadataNode id = (IIOMetadataNode) root.getElementsByTagName("ImageDescriptor").item(0);
            fm.left = Integer.parseInt(id.getAttribute("imageLeftPosition"));
            fm.top = Integer.parseInt(id.getAttribute("imageTopPosition"));
            fm.width = Integer.parseInt(id.getAttribute("imageWidth"));
            fm.height = Integer.parseInt(id.getAttribute("imageHeight"));

            IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
            if (gce != null) {
                String disp = gce.getAttribute("disposalMethod");
                if ("restoreToBackgroundColor".equals(disp)) fm.disposal = 2;
                else if ("restoreToPrevious".equals(disp)) fm.disposal = 3;
                else fm.disposal = 1; // 0 and 1 both mean “do not dispose”
            } else {
                fm.disposal = 1;
            }
        } catch (Exception ignored) {
            fm.left = fm.top = 0;
            fm.width = fm.height = 0;
            fm.disposal = 1;
        }
        return fm;
    }

    public static Vec2i computeAtlasLayout(int frameCount, int frameW, int frameH, int maxWidth, int maxHeight) {
        int bestRows = 1;
        int bestCols = frameCount;
        int minEmpty = frameCount;

        for (int cols = 1; cols <= frameCount; cols++) {
            int rows = (int) Math.ceil(frameCount / (double) cols);
            int atlasW = cols * frameW;
            int atlasH = rows * frameH;
            if (atlasW > maxWidth || atlasH > maxHeight) continue;
            int emptySlots = rows * cols - frameCount;
            if (emptySlots < minEmpty || (emptySlots == minEmpty && rows < bestRows)) {
                minEmpty = emptySlots;
                bestRows = rows;
                bestCols = cols;
                if (emptySlots == 0) break;
            }
        }
        return new Vec2i(bestRows, bestCols);
    }

    private static NativeImage buildTiledAtlas(List<BufferedImage> frames, int frameW, int frameH, int maxTextureSize) {
        int frameCount = frames.size();
        Vec2i layout = computeAtlasLayout(frameCount, frameW, frameH, maxTextureSize, maxTextureSize);
        int rows = layout.x();
        int cols = layout.y();
        int atlasW = cols * frameW;
        int atlasH = rows * frameH;

        NativeImage out = new NativeImage(NativeImage.Format.RGBA, atlasW, atlasH, true);

        for (int i = 0; i < frameCount; i++) {
            int col = i / rows;   // vertical strips: fill columns first
            int row = i % rows;
            int xOff = col * frameW;
            int yOff = row * frameH;
            copyArgbToAbgr(frames.get(i), out, xOff, yOff, frameW, frameH);
        }
        return out;
    }

    private static NativeImage buildVerticalStrip(List<BufferedImage> frames, int w, int h) {
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h * frames.size(), true);
        for (int i = 0; i < frames.size(); i++) {
            copyArgbToAbgr(frames.get(i), out, 0, i * h, w, h);
        }
        return out;
    }

    private static void copyArgbToAbgr(BufferedImage src, NativeImage dst, int dx, int dy, int w, int h) {
        int[] srcPixels = src.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = srcPixels[y * w + x];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                dst.setPixelRGBA(dx + x, dy + y, abgr);
            }
        }
    }

    private static AnimationMetadataSection buildAnimationMeta(List<Integer> ticks, int w, int h, int frameCount) {
        if (ticks.size() < frameCount) {
            int last = ticks.isEmpty() ? 1 : Math.max(1, ticks.getLast());
            while (ticks.size() < frameCount) ticks.add(last);
        }
        boolean uniform = true;
        int first = Math.max(1, ticks.getFirst());
        for (int i = 1; i < frameCount; i++) {
            if (!ticks.get(i).equals(first)) {
                uniform = false;
                break;
            }
        }
        if (uniform) {
            return new AnimationMetadataSection(List.of(), w, h, Math.max(1, first), false);
        } else {
            List<AnimationFrame> frames = new ArrayList<>(frameCount);
            for (int i = 0; i < frameCount; i++) {
                frames.add(new AnimationFrame(i, Math.max(1, ticks.get(i))));
            }
            return new AnimationMetadataSection(frames, w, h, 1, false);
        }
    }

}
