#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;   // <-- ADD (needed for textureSize)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform mat3 IViewRotMat;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

uniform vec2 SpriteDimensions;

/* ADD */
uniform float GameTime;
uniform int OverlayIndex;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;

out vec2 texCoord0;
out vec2 texCoord1;

flat out vec2 spriteSizePx;
flat out vec2 atlasSizePx;

flat out float vOverlayFrameHeightUV;
flat out vec2 vOverlayFrameOffsetUV;
flat out int vOverlayEnabled;

void main() {

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);

    vertexColor =
    minecraft_mix_light(
        Light0_Direction,
        Light1_Direction,
        Normal,
        Color
    );

    atlasSizePx = vec2(textureSize(Sampler0, 0));
    spriteSizePx = SpriteDimensions * atlasSizePx;

    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);

    texCoord0 = UV0;
    texCoord1 = vec2(UV1) / 16.0;

    /* ===== OVERLAY ===== */

    vOverlayEnabled = OverlayIndex > 0 ? 1 : 0;

    if (vOverlayEnabled == 1) {

        ivec2 texSize = textureSize(Sampler1, 0);
        int frameCount = texSize.y / texSize.x;

        vOverlayFrameHeightUV =
        1.0 / float(frameCount);

        int millis =
        int(GameTime * 24000.0 * 0.05 * 1000.0);

        int frameIndex =
        int(millis * 0.01) % frameCount;

        vOverlayFrameOffsetUV =
        vec2(0.0,
        float(frameIndex) * vOverlayFrameHeightUV);
    }
    else {
        vOverlayFrameHeightUV = 0.0;
        vOverlayFrameOffsetUV = vec2(0.0);
    }
}