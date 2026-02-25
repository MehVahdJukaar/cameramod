#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec3 Mul;
uniform vec3 Add;
uniform float Saturation;
uniform float Contrast;

out vec4 fragColor;

void main() {
    vec4 InTexel = texture(DiffuseSampler, texCoord);

    vec3 RGB = InTexel.rgb * Mul + Add;

    vec3 Gray = vec3(0.3, 0.59, 0.11);
    float Luma = dot(RGB, Gray);
    vec3 Chroma = RGB - Luma;
    RGB = (Chroma * Saturation) + Luma;

    RGB = (RGB - 0.5) * Contrast + 0.5;

    fragColor = vec4(RGB, 1.0);
}
