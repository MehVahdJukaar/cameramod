package net.mehvahdjukaar.vista.client.textures;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.CrtOverlay;
import net.mehvahdjukaar.vista.client.VistaRenderTypes;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.cassette.CassetteTape;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.lang.Math.PI;

public class TvScreenVertexConsumers {
    /*
     Previous flushQuad backup (simplified):
     - computed center from 4 world verts
     - projected offsets on local ex/ey
     - rebuilt transformed vertices and emitted all layers
     This was replaced by strict half-extent reconstruction to guarantee rectangular symmetry.
    */

    private static final AtomicInteger DEBUG_SHUTDOWN_LOG_STAGES = new AtomicInteger(0);
    private static final boolean OUTWARD_PULSE = true;
    private static final float PHASE_SPLIT = 0.6f;

    private static final ResourceLocation DUMMY_LOCATION = VistaMod.res("textures/cassette_tape/color_bars.png");
    private static final ResourceLocation WHITE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final ResourceLocation BARS_LOCATION = VistaMod.res("color_bars");
    private static final ResourceLocation SMILE_LOCATION = VistaMod.res("smile");
    private static final ResourceLocation NEUTRAL_LOCATION = VistaMod.res("neutral");
    private static final ResourceLocation SAD_LOCATION = VistaMod.res("sad");
    private static final Map<Smile, ResourceLocation> SMILES = Map.of(
            Smile.HAPPY, SMILE_LOCATION,
            Smile.NEUTRAL, NEUTRAL_LOCATION,
            Smile.SAD, SAD_LOCATION
    );

    private enum Smile {
        HAPPY, NEUTRAL, SAD;

        public static Smile fromHealth(LivingEntity entity) {
            float health = entity.getHealth() / entity.getMaxHealth();
            if (health > 0.66f) {
                return HAPPY;
            } else if (health > 0.33f) {
                return NEUTRAL;
            } else {
                return SAD;
            }
        }
    }

    public static VertexConsumer getTapeVC(MultiBufferSource buffer, @NotNull Holder<CassetteTape> tapeHolder, int scale,
                                           int tickCount, boolean paused, IntAnimationState switchAnim) {
        ResourceLocation tapeTexture = tapeHolder.value().assetId();
        return createAnimatedStripVC(buffer, tapeTexture, scale, tickCount, paused, switchAnim);
    }

    public static VertexConsumer getNoiseVC(MultiBufferSource buffer, int scale, IntAnimationState switchAnim) {
        return createVC(DUMMY_LOCATION, scale, 1, 1,
                CrtOverlay.NONE, switchAnim, IntAnimationState.MAX_ANIM, buffer::getBuffer);
    }

    public static VertexConsumer getBarsVC(MultiBufferSource buffer, int scale, boolean paused, IntAnimationState switchAnim) {
        return createAnimatedStripVC(buffer, BARS_LOCATION, scale, 0, paused, switchAnim);
    }

    public static VertexConsumer getSmileTapeVC(MultiBufferSource buffer, LivingEntity player) {
        Smile smile = Smile.fromHealth(player);
        ResourceLocation id = SMILES.get(smile);
        int scale = 12;//always on a 1x1 tv
        return createAnimatedStripVC(buffer, id, scale, player.tickCount, false, IntAnimationState.NO_ANIM);
    }

    private static VertexConsumer createAnimatedStripVC(MultiBufferSource buffer,
                                                        ResourceLocation id,
                                                        int scale, int tickCount,
                                                        boolean paused,
                                                        IntAnimationState switchAnim) {
        AnimatedStripTexture animatedStrip = CassetteTexturesManager.INSTANCE.getAnimatedTexture(id);
        if (animatedStrip == null) {
            return getNoiseVC(buffer, scale, switchAnim); //missing
        }

        CrtOverlay overlay = paused ? CrtOverlay.PAUSE : CrtOverlay.NONE;
        ResourceLocation textureId = animatedStrip.getTextureLocation();
        AnimationStripData stripData = animatedStrip.getStripData();

        return createVC(textureId, scale, stripData.frameRelativeW(), stripData.frameRelativeH(),
                overlay, switchAnim, IntAnimationState.NO_ANIM, rt ->
                        new AnimatedStripVertexConsumer(tickCount, stripData, buffer.getBuffer(rt)));
    }

    public static VertexConsumer getLiveFeedVC(MultiBufferSource buffer,
                                               LiveFeedTexture tex,
                                               int scale, boolean paused,
                                               IntAnimationState switchAnim,
                                               IntAnimationState noiseAnim) {
        CrtOverlay overlay = tex.getOverlay(paused) ;
        return createVC(tex.getTextureLocation(), scale, 1, 1,
                overlay, switchAnim, noiseAnim, buffer::getBuffer);
    }

    public static VertexConsumer createVC(ResourceLocation texture,
                                          int scale, float frameW, float frameH,
                                          CrtOverlay overlay,
                                          IntAnimationState switchAnim,
                                          IntAnimationState noiseAnim,
                                          Function<RenderType, VertexConsumer> func) {
        boolean isLiveFeedRender = VistaLevelRenderer.isRenderingLiveFeed();
        boolean sfxEnabled = hasSfxEnabled();
        boolean hasSfx = sfxEnabled && !isLiveFeedRender;
        boolean irisShaderPackActive = CompatHandler.IRIS && IrisCompat.isShaderPackInUse();
        boolean useIrisFallback = irisShaderPackActive && (sfxEnabled || isLiveFeedRender);

        if (!hasSfx && switchAnim.isDecreasing() && !useIrisFallback) return EMPTY_VC;

        if (useIrisFallback) {
            // Using translucent for base so we can fade it in (alpha 0 -> 1)
            // Original code used entitySolid which caused the texture to pop in immediately
            VertexConsumer base = func.apply(RenderType.entityTranslucent(texture));
            VertexConsumer crtRedLayer = func.apply(RenderType.entityTranslucent(texture));
            VertexConsumer crtBlueLayer = func.apply(RenderType.entityTranslucent(texture));
            VertexConsumer overlayLayer = overlay != CrtOverlay.NONE
                ? func.apply(RenderType.entityTranslucent(overlay.texture))
                : null;

            float noiseAlpha = noiseAnim.getValue(1.0f);
            VertexConsumer noiseLayer = noiseAlpha > 0.01f
                ? func.apply(VistaRenderTypes.ENTITY_ADDITIVE_TRANSLUCENT.apply(DUMMY_LOCATION))
                : null;

                PowerAnimState power = PowerAnimState.fromSwitchAnim(
                    switchAnim.getValue(1.0f),
                    switchAnim.isIncreasing(),
                    switchAnim.isDecreasing());

            VertexConsumer blackoutLayer = power.blackoutAlpha > 0.01f
                ? func.apply(RenderType.entityTranslucent(WHITE_LOCATION))
                : null;

            VertexConsumer whiteFlashLayer = power.whiteFlashAlpha > 0.01f
                ? func.apply(VistaRenderTypes.ENTITY_ADDITIVE_TRANSLUCENT.apply(WHITE_LOCATION))
                : null;

            VertexConsumer glowLineLayer = power.glowLineAlpha > 0.01f
                ? func.apply(RenderType.entityTranslucent(WHITE_LOCATION))
                : null;

            VertexConsumer glowDotLayer = power.glowDotAlpha > 0.01f
                ? func.apply(RenderType.entityTranslucent(WHITE_LOCATION))
                : null;

            return new IrisFallbackLayeredVertexConsumer(base, overlayLayer, noiseLayer,
                blackoutLayer, whiteFlashLayer, glowLineLayer, glowDotLayer,
                crtRedLayer, crtBlueLayer,
                noiseAlpha, power);
        }

        RenderType rt = hasSfx ?
                VistaRenderTypes.crtRenderType(texture, scale,
                        frameW, frameH,
                        switchAnim, noiseAnim, overlay) :
                RenderType.entitySolid(texture); //for normal
        return func.apply(rt);
    }

    private static boolean hasSfxEnabled() {
        return Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FAST
                && ClientConfigs.SCREEN_EFFECTS.get();
    }


    private static boolean hasSfx() {
        return !VistaLevelRenderer.isRenderingLiveFeed() && hasSfxEnabled();
    }


    private static final VertexConsumer EMPTY_VC = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    };

    private static class IrisFallbackLayeredVertexConsumer implements VertexConsumer {
        private final VertexConsumer base;
        @Nullable
        private final VertexConsumer overlay;
        @Nullable
        private final VertexConsumer noise;
        @Nullable
        private final VertexConsumer blackout;
        @Nullable
        private final VertexConsumer whiteFlash;
        @Nullable
        private final VertexConsumer glowLine;
        @Nullable
        private final VertexConsumer glowDot;
        @Nullable
        private final VertexConsumer crtRed;
        @Nullable
        private final VertexConsumer crtBlue;
        private final float noiseAlpha;
        private final PowerAnimState power;
        private final float vignetteIntensity;
        private int quadVertexCount;
        private final float[] quadX = new float[4];
        private final float[] quadY = new float[4];
        private final float[] quadZ = new float[4];
        private final int[] quadColor = new int[4];
        private final float[] quadU = new float[4];
        private final float[] quadV = new float[4];
        private final int[] quadOverlay = new int[4];
        private final int[] quadLight = new int[4];
        private final float[] quadNx = new float[4];
        private final float[] quadNy = new float[4];
        private final float[] quadNz = new float[4];
        private float lastProgress = -1.0f;

        private IrisFallbackLayeredVertexConsumer(VertexConsumer base,
                                                  @Nullable VertexConsumer overlay,
                                                  @Nullable VertexConsumer noise,
                                                  @Nullable VertexConsumer blackout,
                                                  @Nullable VertexConsumer whiteFlash,
                                                  @Nullable VertexConsumer glowLine,
                                                  @Nullable VertexConsumer glowDot,
                                                  @Nullable VertexConsumer crtRed,
                                                  @Nullable VertexConsumer crtBlue,
                                                  float noiseAlpha,
                                                  PowerAnimState power) {
            this.base = base;
            this.overlay = overlay;
            this.noise = noise;
            this.blackout = blackout;
            this.whiteFlash = whiteFlash;
            this.glowLine = glowLine;
            this.glowDot = glowDot;
            this.crtRed = crtRed;
            this.crtBlue = crtBlue;
            this.noiseAlpha = noiseAlpha;
            this.power = power;
            this.vignetteIntensity = Math.max(ClientConfigs.VIGNETTE.get(), 0.42f);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            float cx = x * power.contentScaleX;
            float cy = y * power.contentScaleY;

            base.addVertex(cx, cy, z);
            if (overlay != null) overlay.addVertex(cx, cy, z);
            if (noise != null) noise.addVertex(cx, cy, z);
            if (blackout != null) blackout.addVertex(x, y, z);
            if (whiteFlash != null) whiteFlash.addVertex(cx, cy, z);

            if (glowLine != null) {
                glowLine.addVertex(x * power.glowLineScaleX, y * power.glowLineScaleY, z);
            }
            if (glowDot != null) {
                glowDot.addVertex(x * power.glowDotScale, y * power.glowDotScale, z);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            base.setColor(red, green, blue, alpha);
            if (overlay != null) {
                overlay.setColor(red, green, blue, alpha);
            }
            if (noise != null) {
                noise.setColor(255, 255, 255, mulAlpha(alpha, noiseAlpha));
            }
            if (blackout != null) {
                blackout.setColor(0, 0, 0, mulAlpha(alpha, power.blackoutAlpha));
            }
            if (whiteFlash != null) {
                int flash = mulAlpha(alpha, power.whiteFlashAlpha);
                whiteFlash.setColor(255, 255, 255, flash);
            }
            if (glowLine != null) {
                int glow = mulAlpha(alpha, power.glowLineAlpha);
                glowLine.setColor(255, 255, 255, glow);
            }
            if (glowDot != null) {
                int glow = mulAlpha(alpha, power.glowDotAlpha);
                glowDot.setColor(255, 255, 255, glow);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            base.setUv(u, v);
            if (overlay != null) overlay.setUv(u, v);
            if (noise != null) noise.setUv(u, v);
            if (blackout != null) blackout.setUv(u, v);
            if (whiteFlash != null) whiteFlash.setUv(u, v);
            if (glowLine != null) glowLine.setUv(u, v);
            if (glowDot != null) glowDot.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            base.setUv1(u, v);
            if (overlay != null) overlay.setUv1(u, v);
            if (noise != null) noise.setUv1(u, v);
            if (blackout != null) blackout.setUv1(u, v);
            if (whiteFlash != null) whiteFlash.setUv1(u, v);
            if (glowLine != null) glowLine.setUv1(u, v);
            if (glowDot != null) glowDot.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            base.setUv2(u, v);
            if (overlay != null) overlay.setUv2(u, v);
            if (noise != null) noise.setUv2(u, v);
            if (blackout != null) blackout.setUv2(u, v);
            if (whiteFlash != null) whiteFlash.setUv2(u, v);
            if (glowLine != null) glowLine.setUv2(u, v);
            if (glowDot != null) glowDot.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            base.setNormal(normalX, normalY, normalZ);
            if (overlay != null) overlay.setNormal(normalX, normalY, normalZ);
            if (noise != null) noise.setNormal(normalX, normalY, normalZ);
            if (blackout != null) blackout.setNormal(normalX, normalY, normalZ);
            if (whiteFlash != null) whiteFlash.setNormal(normalX, normalY, normalZ);
            if (glowLine != null) glowLine.setNormal(normalX, normalY, normalZ);
            if (glowDot != null) glowDot.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int packedOverlay, int packedLight,
                              float normalX, float normalY, float normalZ) {
            int i = quadVertexCount;
            quadX[i] = x;
            quadY[i] = y;
            quadZ[i] = z;
            quadColor[i] = color;
            quadU[i] = u;
            quadV[i] = v;
            quadOverlay[i] = packedOverlay;
            quadLight[i] = packedLight;
            quadNx[i] = normalX;
            quadNy[i] = normalY;
            quadNz[i] = normalZ;
            quadVertexCount++;

            if (quadVertexCount == 4) {
                flushQuad();
                quadVertexCount = 0;
            }
        }

        private static int mulAlpha(int alpha, float factor) {
            return Math.max(0, Math.min(255, (int) (alpha * factor)));
        }

        private static int scaledAlpha(int color, float factor) {
            int alpha = (color >>> 24) & 0xFF;
            return mulAlpha(alpha, factor);
        }

        private static int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
        }

        private static int radialFlashColor(int baseAlpha, float u, float v) {
            float dx = clamp01(u) - 0.5f;
            float dy = clamp01(v) - 0.5f;
            float radius = (float) Math.sqrt(dx * dx + dy * dy);
            float radial = clamp01(radius / 0.70710677f);
            float center = 1.0f - radial;

            float brightness = lerp(0.58f, 1.0f, (float) Math.pow(center, 1.35f));
            int shade = clampColor((int) (255.0f * brightness));
            int alpha = mulAlpha(baseAlpha, lerp(0.86f, 1.15f, center));
            return (alpha << 24) | (shade << 16) | (shade << 8) | shade;
        }

        private void flushQuad() {
            float centerX = (quadX[0] + quadX[1] + quadX[2] + quadX[3]) * 0.25f;
            float centerY = (quadY[0] + quadY[1] + quadY[2] + quadY[3]) * 0.25f;
            float centerZ = (quadZ[0] + quadZ[1] + quadZ[2] + quadZ[3]) * 0.25f;

            float exX = quadX[1] - quadX[0];
            float exY = quadY[1] - quadY[0];
            float exZ = quadZ[1] - quadZ[0];

            float eyX = quadX[3] - quadX[0];
            float eyY = quadY[3] - quadY[0];
            float eyZ = quadZ[3] - quadZ[0];

            float exLen = length(exX, exY, exZ);
            float eyLen = length(eyX, eyY, eyZ);
            if (exLen < 1.0e-6f || eyLen < 1.0e-6f) {
                for (int i = 0; i < 4; i++) {
                    int color = quadColor[i];
                    float u = quadU[i];
                    float v = quadV[i];
                    int packedOverlay = quadOverlay[i];
                    int packedLight = quadLight[i];
                    float normalX = quadNx[i];
                    float normalY = quadNy[i];
                    float normalZ = quadNz[i];
                    float x = quadX[i];
                    float y = quadY[i];
                    float z = quadZ[i];

                    base.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    if (overlay != null) overlay.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    if (noise != null) {
                        noise.addVertex(x, y, z, withAlpha(0xFFFFFFFF, scaledAlpha(color, noiseAlpha)),
                                u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    }
                    if (blackout != null) {
                        blackout.addVertex(x, y, z, withAlpha(0xFF000000, scaledAlpha(color, power.blackoutAlpha)),
                                u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    }
                    if (whiteFlash != null) {
                        int flashColor = radialFlashColor(scaledAlpha(color, power.whiteFlashAlpha), u, v);
                        whiteFlash.addVertex(x, y, z, flashColor,
                                u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    }
                    if (glowLine != null) {
                        glowLine.addVertex(x, y, z, withAlpha(0xFFFFFFFF, scaledAlpha(color, power.glowLineAlpha)),
                                u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    }
                    if (glowDot != null) {
                        glowDot.addVertex(x, y, z, withAlpha(0xFFFFFFFF, scaledAlpha(color, power.glowDotAlpha)),
                                u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    }
                }
                return;
            }

            exX /= exLen;
            exY /= exLen;
            exZ /= exLen;

            eyX /= eyLen;
            eyY /= eyLen;
            eyZ /= eyLen;

            float halfWidth = exLen * 0.5f;
            float halfHeight = eyLen * 0.5f;

            float[] localBeforeX = new float[4];
            float[] localBeforeY = new float[4];
            for (int i = 0; i < 4; i++) {
                float offX = quadX[i] - centerX;
                float offY = quadY[i] - centerY;
                float offZ = quadZ[i] - centerZ;
                localBeforeX[i] = dot(offX, offY, offZ, exX, exY, exZ);
                localBeforeY[i] = dot(offX, offY, offZ, eyX, eyY, eyZ);
            }

            float[] beforeX = new float[4];
            float[] beforeY = new float[4];
            float[] beforeZ = new float[4];
            float[] contentX = new float[4];
            float[] contentY = new float[4];
            float[] contentZ = new float[4];
            float[] lineX = new float[4];
            float[] lineY = new float[4];
            float[] lineZ = new float[4];
            float[] dotX = new float[4];
            float[] dotY = new float[4];
            float[] dotZ = new float[4];

            for (int i = 0; i < 4; i++) {
                float x = quadX[i];
                float y = quadY[i];
                float z = quadZ[i];
                int color = quadColor[i];
                float u = quadU[i];
                float v = quadV[i];
                int packedOverlay = quadOverlay[i];
                int packedLight = quadLight[i];
                float normalX = quadNx[i];
                float normalY = quadNy[i];
                float normalZ = quadNz[i];

                beforeX[i] = x;
                beforeY[i] = y;
                beforeZ[i] = z;

                float signX = (i == 0 || i == 3) ? -1.0f : 1.0f; // TL/TR/BR/BL
                float signY = (i == 0 || i == 1) ? -1.0f : 1.0f;

                float targetExContent = signX * halfWidth * power.contentScaleX;
                float targetEyContent = signY * halfHeight * power.contentScaleY;

                float cX = centerX + exX * targetExContent + eyX * targetEyContent;
                float cY = centerY + exY * targetExContent + eyY * targetEyContent;
                float cZ = centerZ + exZ * targetExContent + eyZ * targetEyContent;

                float targetExLine = signX * halfWidth * power.glowLineScaleX;
                float targetEyLine = signY * halfHeight * power.glowLineScaleY;
                float lX = centerX + exX * targetExLine + eyX * targetEyLine;
                float lY = centerY + exY * targetExLine + eyY * targetEyLine;
                float lZ = centerZ + exZ * targetExLine + eyZ * targetEyLine;

                float targetExDot = signX * halfWidth * power.glowDotScale;
                float targetEyDot = signY * halfHeight * power.glowDotScale;
                float dX = centerX + exX * targetExDot + eyX * targetEyDot;
                float dY = centerY + exY * targetExDot + eyY * targetEyDot;
                float dZ = centerZ + exZ * targetExDot + eyZ * targetEyDot;

                contentX[i] = cX;
                contentY[i] = cY;
                contentZ[i] = cZ;
                lineX[i] = lX;
                lineY[i] = lY;
                lineZ[i] = lZ;
                dotX[i] = dX;
                dotY[i] = dY;
                dotZ[i] = dZ;
            }

            int[] baseColors = new int[4];
            
            // Check if flash is strong enough to hide content
            float flashStrength = power.whiteFlashAlpha;
            boolean suppressContent = flashStrength > 0.8f; // Aggressive suppression
            float contentAlphaFactor = suppressContent ? 0.0f : power.contentAlpha;

            for (int i = 0; i < 4; i++) {
                int color = quadColor[i];
                float u = quadU[i];
                float v = quadV[i];
                int packedOverlay = quadOverlay[i];
                int packedLight = quadLight[i];
                float normalX = quadNx[i];
                float normalY = quadNy[i];
                float normalZ = quadNz[i];

                int baseColor = applyVignette(color, u, v, vignetteIntensity);
                baseColor = withAlpha(baseColor, scaledAlpha(baseColor, contentAlphaFactor));
                baseColors[i] = baseColor;
                
                if (!suppressContent) {
                    base.addVertex(contentX[i], contentY[i], contentZ[i], baseColor, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    if (overlay != null) {
                        overlay.addVertex(contentX[i], contentY[i], contentZ[i], baseColor, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    }
                    if (noise != null) {
                        int noiseAlphaInt = mulAlpha(255, Math.min(noiseAlpha, 0.08f));
                            noiseAlphaInt = mulAlpha(noiseAlphaInt, contentAlphaFactor);
                        noise.addVertex(contentX[i], contentY[i], contentZ[i], withAlpha(0xFFFFFFFF, noiseAlphaInt),
                                u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
                    }
                }
            }

            float rgbShift = getCrtRgbShift(power);
            if (crtRed != null) {
                for (int i = 0; i < 4; i++) {
                    float u = clamp01(quadU[i] + rgbShift);
                    float v = quadV[i];
                    int crtColor = tintColor(baseColors[i], 1.0f, 0.65f, 0.65f, 0.24f);
                    crtRed.addVertex(contentX[i], contentY[i], contentZ[i], crtColor,
                            u, v, quadOverlay[i], quadLight[i], quadNx[i], quadNy[i], quadNz[i]);
                }
            }

            if (crtBlue != null) {
                for (int i = 0; i < 4; i++) {
                    float u = clamp01(quadU[i] - rgbShift);
                    float v = quadV[i];
                    int crtColor = tintColor(baseColors[i], 0.65f, 0.65f, 1.0f, 0.24f);
                    crtBlue.addVertex(contentX[i], contentY[i], contentZ[i], crtColor,
                            u, v, quadOverlay[i], quadLight[i], quadNx[i], quadNy[i], quadNz[i]);
                }
            }

            if (blackout != null) {
                for (int i = 0; i < 4; i++) {
                    int color = quadColor[i];
                    blackout.addVertex(quadX[i], quadY[i], quadZ[i],
                            withAlpha(0xFF000000, scaledAlpha(color, power.blackoutAlpha)),
                            quadU[i], quadV[i], quadOverlay[i], quadLight[i], quadNx[i], quadNy[i], quadNz[i]);
                }
            }

            if (whiteFlash != null) {
                for (int i = 0; i < 4; i++) {
                    int color = quadColor[i];
                    int flashColor = radialFlashColor(scaledAlpha(color, power.whiteFlashAlpha), quadU[i], quadV[i]);
                    // Use full bright lightmap for the flash so it glows
                    // Add small offset along normal to ensure it renders on top
                    float nudge = 0.05f; 
                    float fx = contentX[i] + quadNx[i] * nudge;
                    float fy = contentY[i] + quadNy[i] * nudge;
                    float fz = contentZ[i] + quadNz[i] * nudge;
                    
                    whiteFlash.addVertex(fx, fy, fz,
                            flashColor,
                            quadU[i], quadV[i], quadOverlay[i], 0xF000F0, quadNx[i], quadNy[i], quadNz[i]);
                }
            }

            if (glowLine != null) {
                for (int i = 0; i < 4; i++) {
                    int color = quadColor[i];
                    glowLine.addVertex(lineX[i], lineY[i], lineZ[i],
                            withAlpha(0xFFFFFFFF, scaledAlpha(color, power.glowLineAlpha)),
                            quadU[i], quadV[i], quadOverlay[i], quadLight[i], quadNx[i], quadNy[i], quadNz[i]);
                }
            }

            if (glowDot != null) {
                for (int i = 0; i < 4; i++) {
                    int color = quadColor[i];
                    glowDot.addVertex(dotX[i], dotY[i], dotZ[i],
                            withAlpha(0xFFFFFFFF, scaledAlpha(color, power.glowDotAlpha)),
                            quadU[i], quadV[i], quadOverlay[i], quadLight[i], quadNx[i], quadNy[i], quadNz[i]);
                }
            }

                maybeLogGeometry(power.progress, centerX, centerY, centerZ,
                    exX, exY, exZ, eyX, eyY, eyZ,
                    halfWidth, halfHeight,
                        localBeforeX, localBeforeY,
                    beforeX, beforeY, beforeZ,
                    contentX, contentY, contentZ);
        }

        private void maybeLogGeometry(float progress,
                                      float centerX, float centerY, float centerZ,
                                      float exX, float exY, float exZ,
                                      float eyX, float eyY, float eyZ,
                                      float halfWidth, float halfHeight,
                                      float[] localBeforeX,
                                      float[] localBeforeY,
                                      float[] beforeX, float[] beforeY, float[] beforeZ,
                                      float[] afterX, float[] afterY, float[] afterZ) {
            if (!isDebugTvAnim()) return;

            if (lastProgress > 0.95f && progress < 0.05f) {
                DEBUG_SHUTDOWN_LOG_STAGES.set(0);
            }
            lastProgress = progress;

            int stageBit = 0;
            if (progress <= 0.08f) stageBit = 1;
            else if (progress >= 0.45f && progress <= 0.55f) stageBit = 2;
            else if (progress >= 0.90f) stageBit = 4;
            if (stageBit == 0) return;

            int old = DEBUG_SHUTDOWN_LOG_STAGES.get();
            if ((old & stageBit) != 0) return;
            if (!DEBUG_SHUTDOWN_LOG_STAGES.compareAndSet(old, old | stageBit)) return;

            String stage = stageBit == 1 ? "start" : (stageBit == 2 ? "mid" : "end");
            VistaMod.LOGGER.info("[TV shutdown debug] stage={} progress={} center=({}, {}, {}) ex=({}, {}, {}) ey=({}, {}, {}) half=({}, {}) localBefore={} localAfter={} newWorld={}",
                    stage,
                    String.format("%.3f", progress),
                    f3(centerX), f3(centerY), f3(centerZ),
                    f3(exX), f3(exY), f3(exZ),
                    f3(eyX), f3(eyY), f3(eyZ),
                    f3(halfWidth), f3(halfHeight),
                    verticesToString(localBeforeX, localBeforeY, new float[]{0, 0, 0, 0}),
                    verticesToString(localAfterFromWorld(afterX, afterY, afterZ, centerX, centerY, centerZ, exX, exY, exZ, eyX, eyY, eyZ),
                            localAfterFromWorldY(afterX, afterY, afterZ, centerX, centerY, centerZ, exX, exY, exZ, eyX, eyY, eyZ),
                            new float[]{0, 0, 0, 0}),
                    verticesToString(afterX, afterY, afterZ));
        }

        private static float[] localAfterFromWorld(float[] xs, float[] ys, float[] zs,
                                                    float centerX, float centerY, float centerZ,
                                                    float exX, float exY, float exZ,
                                                    float eyX, float eyY, float eyZ) {
            float[] out = new float[4];
            for (int i = 0; i < 4; i++) {
                out[i] = dot(xs[i] - centerX, ys[i] - centerY, zs[i] - centerZ, exX, exY, exZ);
            }
            return out;
        }

        private static float[] localAfterFromWorldY(float[] xs, float[] ys, float[] zs,
                                                     float centerX, float centerY, float centerZ,
                                                     float exX, float exY, float exZ,
                                                     float eyX, float eyY, float eyZ) {
            float[] out = new float[4];
            for (int i = 0; i < 4; i++) {
                out[i] = dot(xs[i] - centerX, ys[i] - centerY, zs[i] - centerZ, eyX, eyY, eyZ);
            }
            return out;
        }

        private static String verticesToString(float[] xs, float[] ys, float[] zs) {
            return "[" + vToString(xs[0], ys[0], zs[0]) + ", "
                    + vToString(xs[1], ys[1], zs[1]) + ", "
                    + vToString(xs[2], ys[2], zs[2]) + ", "
                    + vToString(xs[3], ys[3], zs[3]) + "]";
        }

        private static String vToString(float x, float y, float z) {
            return "(" + f3(x) + "," + f3(y) + "," + f3(z) + ")";
        }

        private static String f3(float value) {
            return String.format("%.3f", value);
        }

        private static float dot(float ax, float ay, float az, float bx, float by, float bz) {
            return ax * bx + ay * by + az * bz;
        }

        private static float length(float x, float y, float z) {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        private static boolean isDebugTvAnim() {
            if (Boolean.getBoolean("vista.debugTvAnim")) return true;
            return ClientConfigs.rendersDebug();
        }

        private static int applyVignette(int color, float u, float v, float intensity) {
            float clampedIntensity = clamp01(intensity);
            float uvX = clamp01(u);
            float uvY = clamp01(v);

            float dx = uvX - 0.5f;
            float dy = uvY - 0.5f;
            float radius = (float) Math.sqrt(dx * dx + dy * dy);
            float radial = clamp01((radius - 0.20f) / 0.42f);
            float vignette = 1.0f - 0.26f * radial;
            float mul = lerp(1.0f, vignette, clampedIntensity);

            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;

            float rl = srgbToLinear(r / 255.0f);
            float gl = srgbToLinear(g / 255.0f);
            float bl = srgbToLinear(b / 255.0f);
            rl = clamp01(rl * mul);
            gl = clamp01(gl * mul);
            bl = clamp01(bl * mul);

            r = clampColor((int) (linearToSrgb(rl) * 255.0f));
            g = clampColor((int) (linearToSrgb(gl) * 255.0f));
            b = clampColor((int) (linearToSrgb(bl) * 255.0f));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static float srgbToLinear(float c) {
            if (c <= 0.04045f) return c / 12.92f;
            return (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
        }

        private static float linearToSrgb(float c) {
            if (c <= 0.0031308f) return c * 12.92f;
            return 1.055f * (float) Math.pow(c, 1.0f / 2.4f) - 0.055f;
        }

        private static int tintColor(int color, float rMul, float gMul, float bMul, float aMul) {
            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;
            a = clampColor((int) (a * aMul));
            r = clampColor((int) (r * rMul));
            g = clampColor((int) (g * gMul));
            b = clampColor((int) (b * bMul));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static float getCrtRgbShift(PowerAnimState power) {
            float animBlend = 1.0f - power.progress;
            return 0.0065f + 0.0012f * animBlend;
        }

        private static int clampColor(int c) {
            return Math.max(0, Math.min(255, c));
        }

        private static float clamp01(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    private record PowerAnimState(
            float progress,
            float contentScaleX,
            float contentScaleY,
            float contentAlpha,
            float blackoutAlpha,
            float whiteFlashAlpha,
            float glowLineScaleX,
            float glowLineScaleY,
            float glowLineAlpha,
            float glowDotScale,
            float glowDotAlpha) {

        private static PowerAnimState fromSwitchAnim(float switchValue, boolean turningOn, boolean turningOff) {
            float p = 1.0f - clamp01(switchValue);
            if (p <= 0.0001f) {
            return new PowerAnimState(0.0f,
                1.0f, 1.0f, 1.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f);
            }

            float open = clamp01(switchValue);
            float t = open;

                float split = PHASE_SPLIT;

                float phaseX = easeInOutCubic(animate(t, 0.0f, split));
                float phaseY = easeInOutCubic(animate(t, split, 0.30f));

                float contentScaleX = (t < split) ? phaseX : 1.0f;
                float contentScaleY = (t < split) ? 0.0125f : Math.max(0.0125f, phaseY);

                float lineScaleX = contentScaleX;
                float lineScaleY = Math.max(0.0125f, 0.020f * (1.0f - phaseY));
                float lineAlpha = clamp01(1.0f - smoothstep(split + 0.04f, split + 0.22f, t));

                float pulseT = easeInOutCubic(animate(t, 0.0f, split));
                float pulseScale = OUTWARD_PULSE ? pulseT : (1.0f - pulseT);
                float dotScale = clamp01(0.02f + 0.10f * pulseScale);
                    float dotAlpha = clamp01(1.0f - smoothstep(0.10f, 0.34f, t));

                    float blackoutAlpha = turningOn ? clamp01(1.0f - smoothstep(0.01f, 0.22f, t)) : 0.0f;
                    float expansionWhite = smoothstep(split + 0.02f, split + 0.10f, t)
                        * (1.0f - smoothstep(0.90f, 0.96f, t));
                    float flashSpike = smoothstep(0.92f, 0.965f, t)
                        * (1.0f - smoothstep(0.978f, 0.996f, t));

                    float whiteFlashAlpha;
                    float contentAlpha;

                    if (turningOn) {
                        whiteFlashAlpha = clamp01(expansionWhite * 0.95f + flashSpike * 1.6f);
                        contentAlpha = smoothstep(0.972f, 0.998f, t);
                    } else if (turningOff) {
                        float offCrossFade = smoothstep(0.88f, 0.97f, t)
                                * (1.0f - smoothstep(0.985f, 1.0f, t));
                        whiteFlashAlpha = clamp01(Math.max(expansionWhite * 0.90f, offCrossFade * 1.25f));
                        contentAlpha = smoothstep(0.90f, 0.99f, t);
                    } else {
                        whiteFlashAlpha = 0.0f;
                        contentAlpha = t > 0.999f ? 1.0f : 0.0f;
                    }

            return new PowerAnimState(
                        open,
                    contentScaleX,
                    contentScaleY,
                    contentAlpha,
                blackoutAlpha,
                    whiteFlashAlpha,
                    lineScaleX,
                    lineScaleY,
                    lineAlpha,
                    dotScale,
                    dotAlpha);
        }

        private static float animate(float t, float startTime, float duration) {
            if (duration == 0.0f) return 1.0f;
            return clamp01((t - startTime) / duration);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static float easeInOutCubic(float x) {
            x = clamp01(x);
            return x < 0.5f ? 4.0f * x * x * x : 1.0f - (float) Math.pow(-2.0f * x + 2.0f, 3.0f) * 0.5f;
        }

        private static float smoothstep(float edge0, float edge1, float x) {
            float t = clamp01((x - edge0) / (edge1 - edge0));
            return t * t * (3.0f - 2.0f * t);
        }

        private static float clamp01(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }
    }

}
