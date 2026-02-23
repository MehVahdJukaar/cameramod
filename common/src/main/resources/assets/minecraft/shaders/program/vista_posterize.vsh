#version 150

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;

out vec2 texCoord0;
out vec2 oneTexel;

void main() {
    //same as sobel + vertical flip
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);

    oneTexel = 1.0 / InSize;

    // Flip vertically by subtracting y from OutSize
    texCoord0 = vec2(Position.x / OutSize.x, Position.y / OutSize.y);
}
