package net.mehvahdjukaar.vista.client.textures;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static net.mehvahdjukaar.vista.client.textures.GifPathSpriteSource.computeAtlasLayout;
import static net.mehvahdjukaar.vista.client.textures.GifPathSpriteSource.loadGifContent;

public class AnimatedStripTexture extends AbstractTexture implements Dumpable {

    private final ResourceLocation fileLocation;
    private final ResourceLocation textureLocation;
    private AnimationStripData stripData = AnimationStripData.EMPTY;

    public AnimatedStripTexture(ResourceLocation location) {
        this.fileLocation = location;
        //remove extension
        this.textureLocation = location.withPath(p ->
                p.substring(0, p.lastIndexOf('.')));
    }

    @NotNull
    public AnimationStripData getStripData() {
        return stripData;
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        SpriteContents spriteContents = loadContent(resourceManager);
        if (spriteContents == null) return;

        this.stripData = AnimationStripData.create(spriteContents);

        NativeImage nativeImage = spriteContents.originalImage;
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> this.doLoad(nativeImage));
        } else {
            this.doLoad(nativeImage);
        }

    }

    private @Nullable SpriteContents loadContent(ResourceManager resourceManager) throws FileNotFoundException {
        Resource resource = resourceManager.getResourceOrThrow(this.fileLocation);
        if (fileLocation.getPath().endsWith(".gif")) {
            return loadGifContent(fileLocation, resource);
        } else {
            return loadPngReshaping(fileLocation, resource);
        }
    }

    private void doLoad(NativeImage image) {
        TextureUtil.prepareImage(this.getId(), 0, image.getWidth(), image.getHeight());
        image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(),
                false, false, true, true);
    }


    @Override
    public void dumpContents(ResourceLocation resourceLocation, Path path) throws IOException {
        SpriteContents spriteContents = this.loadContent(Minecraft.getInstance().getResourceManager());
        if (spriteContents == null) return;
        String string = resourceLocation.toDebugFileName() + ".png";
        Path path2 = path.resolve(string);
        spriteContents.originalImage.writeToFile(path2);
    }


    //same as default logic but with reshaping capability
    public static @Nullable SpriteContents loadPngReshaping(ResourceLocation resourceLocation, Resource resource) {

        ResourceMetadata resourceMetadata;
        try {
            resourceMetadata = resource.metadata().copySections(SpriteLoader.DEFAULT_METADATA_SECTIONS);
        } catch (Exception e) {
            VistaMod.LOGGER.error("Unable to parse metadata from {}", resourceLocation, e);
            return null;
        }

        NativeImage nativeImage;
        try (InputStream inputStream = resource.open()) {
            nativeImage = NativeImage.read(inputStream);
        } catch (IOException e) {
            VistaMod.LOGGER.error("Using missing texture, unable to load {}", resourceLocation, e);
            return null;
        }

        AnimationMetadataSection metadata =
                resourceMetadata.getSection(AnimationMetadataSection.SERIALIZER)
                        .orElse(AnimationMetadataSection.EMPTY);

        FrameSize frameSize = metadata.calculateFrameSize(nativeImage.getWidth(), nativeImage.getHeight());
        int frameW = frameSize.width();
        int frameH = frameSize.height();

        if (!Mth.isMultipleOf(nativeImage.getWidth(), frameW) ||
                !Mth.isMultipleOf(nativeImage.getHeight(), frameH)) {

            VistaMod.LOGGER.error("Image {} size {},{} is not multiple of frame size {},{}",
                    resourceLocation, nativeImage.getWidth(), nativeImage.getHeight(), frameW, frameH);
            nativeImage.close();
            return null;
        }

        int framesX = nativeImage.getWidth() / frameW;
        int framesY = nativeImage.getHeight() / frameH;
        int frameCount = framesX * framesY;

        int maxDim = RenderSystem.maxSupportedTextureSize() / 2;

        if (nativeImage.getWidth() <= maxDim && nativeImage.getHeight() <= maxDim) {
            return new SpriteContents(resourceLocation, frameSize, nativeImage, resourceMetadata);
        }

        NativeImage atlas = buildTileAtlasFromStrip(
                nativeImage, frameW, frameH, frameCount, maxDim
        );

        nativeImage.close();
        return new SpriteContents(resourceLocation, frameSize, atlas, resourceMetadata);
    }

    private static NativeImage buildTileAtlasFromStrip(
            NativeImage originalImage,
            int frameW,
            int frameH,
            int frameCount,
            int maxTextureSize
    ) {
        Vec2i layout = computeAtlasLayout(frameCount, frameW, frameH, maxTextureSize, maxTextureSize);
        int rows = layout.x(); // number of rows per column
        int cols = layout.y(); // number of columns

        int atlasW = cols * frameW;
        int atlasH = rows * frameH;

        NativeImage out = new NativeImage(NativeImage.Format.RGBA, atlasW, atlasH, true);

        for (int i = 0; i < frameCount; i++) {
            int row = i % rows;
            int col = i / rows;

            int xOff = col * frameW;
            int yOff = row * frameH;

            originalImage.copyRect(out, 0, i * frameH, xOff, yOff, frameW, frameH, false, false);
        }

        return out;
    }


}
