#version 150
#moj_import <fog.glsl>
#moj_import <vista:crt_effects.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float GameTime;

uniform float NoiseIntensity;
uniform float FadeAnimation;
uniform int OverlayIndex;

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;


/* SpriteDimensions = (sizeU, sizeV) in normalized UVs. First 2 are unused */
uniform vec2 SpriteDimensions;

/* ===================== KNOBS ===================== */
// 1.0 ≈ one triad per texel; >1 = denser triads; <1 = larger triads
uniform float TriadsPerPixel;
// Beam/spot width in TRIAD units; >1 = softer/smeared, <1 = sharper
uniform float Smear;
// 1.0 keeps average brightness stable across settings
uniform float EnableEnergyNormalize;

// Vignette strength: 0.0 = off, 1.0 = full
uniform float VignetteIntensity;

// Base kernel radius used if the dynamic one computes smaller.
// (Dynamic radius grows with Smear; this is a floor.)
const int TriadKernelRadius = 1;// 0 => fastest/sharpest (no neighbors)

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
flat in vec2 atlasSizePx;
flat in vec2 spriteSizePx;

flat in float vOverlayFrameHeightUV;
flat in vec2  vOverlayFrameOffsetUV;
flat in int   vOverlayEnabled;

in vec2 texCoord0;
in vec2 texCoord1;

out vec4 fragColor;

/* ===================== Helpers ===================== */

float beamFalloff(float d) {
    float expLike = exp2(-d * d * 2.5 - 0.3);
    float softTail = 0.05 / (d * d * d * 0.45 + 0.055);
    return mix(expLike, softTail, 0.65) * 0.99;
}

float triadDistance(vec2 triadA, vec2 triadB) {
    vec2 delta = (triadA - triadB) * (vec2(1.25, 1.8) * 0.905 * Smear);
    float cheb = max(abs(delta.x), abs(delta.y));
    float eucl = length(delta * vec2(1.05, 1.0)) * 0.85;
    return mix(cheb, eucl, 0.3);
}

vec2 normalizedTriadPerPixel() {
    vec2 invFrame = 96.0 / spriteSizePx;
    return TriadsPerPixel * invFrame;
}

vec2 triadToUV(vec2 triadP, vec2 frameOriginUV, vec2 invTpp) {
    vec2 pix = triadP * invTpp;
    vec2 localUV = pix / spriteSizePx;
    return frameOriginUV + localUV * SpriteDimensions;
}

vec2 clampToSpriteTexelCenters(vec2 uv, vec2 frameOriginUV) {
    vec2 p = (uv - frameOriginUV) * atlasSizePx;
    vec2 lo = vec2(0.5);
    vec2 hi = max(spriteSizePx - vec2(0.5), lo);
    p = clamp(p, lo, hi);
    return frameOriginUV + p / atlasSizePx;
}

/* Fast x^1.18 approximation (max error < 0.012 in [0,1]) */
vec3 fastGamma(vec3 x) {
    return x * (1.0 + 0.18 * (1.0 - x));
}

/* ===================== Sampling ===================== */

vec3 sampleImage(
    vec2 uv,
    vec2 frameOriginUV,
    vec2 tpp
) {
    float colorAdd = 0.0;

    if (OverlayIndex == 1) {
        vec3 uvAndCol = vcr_pause(
            uv, frameOriginUV,
            SpriteDimensions,
            atlasSizePx,
            GameTime,
            tpp
        );
        uv = uvAndCol.xy;
        colorAdd = uvAndCol.z;
    }

    vec3 color = texture(Sampler0, uv).rgb;

    if (vOverlayEnabled == 1) {
        vec2 overlayUV = vec2(
        uv.x,
        (uv.y - frameOriginUV.y) * vOverlayFrameHeightUV
        + vOverlayFrameOffsetUV.y
        );

        vec4 overlayColor = texture(Sampler1, overlayUV);
        color += overlayColor.rgb * overlayColor.a;
    }

    return color + colorAdd;
}

/* ===================== Phosphor ===================== */

vec3 accumulateTriadResponse(
    vec2 pixelPos,
    vec2 localUV,
    vec2 frameOriginUV
) {
    vec2 tpp = normalizedTriadPerPixel();
    vec2 invTpp = 1.0 / max(tpp, vec2(1e-6));

    vec2 triadPos = pixelPos * tpp - 0.25;

    vec2 dx = dFdx(triadPos);
    vec2 dy = dFdy(triadPos);
    float cpp = max(length(dx), length(dy));

    float invCpp = 0.5 / max(cpp, 1e-5);
    float t = min(invCpp, 1.0);
    float contrast = t * t;

    float amp = 0.03 * clamp(1.0 / max(tpp.x, 1e-6), 0.25, 1.0);

    float offset = 0.0;
    float fx = floor(triadPos.x);

    offset += (mod(fx, 3.0) < 1.5 ? 1.0 : -1.0) * amp;
    offset += (mod(fx, 5.0) < 2.5 ? 1.0 : -1.0) * (amp * 0.6);
    offset += (mod(fx, 7.0) < 3.5 ? 1.0 : -1.0) * (amp * 0.4);

    vec2 jittered = triadPos;
    jittered.y += offset;

    vec2 triadCell = floor(triadPos) + 0.5;

    const float lr = 0.25;
    const float up = 0.2;

    vec2 rBase = jittered + vec2(0.0, up);
    vec2 gBase = jittered + vec2(lr, 0.0);
    vec2 bBase = jittered + vec2(-lr, 0.0);

    vec3 sum = vec3(0.0);
    float wr = 0.0;
    float wg = 0.0;
    float wb = 0.0;

    int dynRadius = max(TriadKernelRadius, int(ceil(2.5 * Smear)));

    for (int dy_i = -dynRadius; dy_i <= dynRadius; ++dy_i) {
        for (int dx_i = -dynRadius; dx_i <= dynRadius; ++dx_i) {

            vec2 cellCenter = triadCell + vec2(dx_i, dy_i);

            float wR = beamFalloff(triadDistance(cellCenter, rBase));
            float wG = beamFalloff(triadDistance(cellCenter, gBase));
            float wB = beamFalloff(triadDistance(cellCenter, bBase));

            vec2 uvR = clampToSpriteTexelCenters(
                triadToUV(rBase, frameOriginUV, invTpp),
                frameOriginUV
            );
            vec2 uvG = clampToSpriteTexelCenters(
                triadToUV(gBase, frameOriginUV, invTpp),
                frameOriginUV
            );
            vec2 uvB = clampToSpriteTexelCenters(
                triadToUV(bBase, frameOriginUV, invTpp),
                frameOriginUV
            );

            vec3 sR = fastGamma(sampleImage(uvR, frameOriginUV, tpp)) * 1.08;
            vec3 sG = fastGamma(sampleImage(uvG, frameOriginUV, tpp)) * 1.08;
            vec3 sB = fastGamma(sampleImage(uvB, frameOriginUV, tpp)) * 1.08;

            sum.r += sR.r * wR; wr += wR;
            sum.g += sG.g * wG; wg += wG;
            sum.b += sB.b * wB; wb += wB;
        }
    }

    if (EnableEnergyNormalize > 0.5) {
        sum.r /= max(wr, 1e-6);
        sum.g /= max(wg, 1e-6);
        sum.b /= max(wb, 1e-6);
    }

    vec3 triadOut = clamp(sum, 0.0, 1.0);

    // Low-frequency fallback (no triad) using the sprite-local UV
    // compute frame-local UVs for fallback
    vec2 fallbackUV = frameOriginUV + localUV * SpriteDimensions;

    vec3 fallback = sampleImage(fallbackUV, frameOriginUV, tpp);

    return mix(fallback, triadOut, contrast);
}

/* ===================== Main ===================== */

void main() {
    // infer frame origin from texCoord0
    vec2 frameOriginUV = floor(texCoord0 / SpriteDimensions) * SpriteDimensions;;
    // frame-local UVs
    vec2 localUV = (texCoord0 - frameOriginUV) / SpriteDimensions;
    // frame-local pixel position for triad math
    vec2 pixelPos = localUV * spriteSizePx;

    vec3 triadRGB;

    if (NoiseIntensity >= 1.0) {
        triadRGB = vec3(0.0);
    } else {
        triadRGB = accumulateTriadResponse(
            pixelPos,
            localUV,
            frameOriginUV
        );
    }

    vec4 color = vec4(triadRGB, 1.0) * vertexColor;

    /* ---- Noise ---- */

    if (NoiseIntensity >= 0.0) {
        vec4 noise = crt_noise(texCoord0, GameTime);
        color.rgb =
        color.rgb * (1.0 - NoiseIntensity)
        + noise.rgb * NoiseIntensity;
    }

    /* ---- AC beat ---- */

    float acBeat = vhs_wave(localUV, frameOriginUV, SpriteDimensions, GameTime);

    color.rgb += (acBeat - 0.5) * (0.03 + (NoiseIntensity * 0.4));

    /* ---- Vignette ---- */

    float vFactor = crt_vignette(localUV);
    float vignette = mix(1.0, vFactor, clamp(VignetteIntensity, 0.0, 1.0));

    color.rgb *= vignette;

    /* ---- Fade animation ---- */

    if (FadeAnimation != 0.0) {
        color = crt_turn_on(
            color,
            pixelPos,
            spriteSizePx,
            FadeAnimation
        );
    }

    color *= lightMapColor;

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}