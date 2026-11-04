#version 150

#moj_import <mre:common.glsl>

in vec3 Position;
in vec4 Color;

layout(std140) uniform ScreenSize { vec2 ScreenSizeVal; };

out vec2 FragCoord;
out vec4 FragColor;

void main() {
    FragCoord = rvertexcoord(gl_VertexID);
    FragColor = Color;

    vec2 clip = vec2(
        Position.x / ScreenSizeVal.x * 2.0 - 1.0,
        1.0 - Position.y / ScreenSizeVal.y * 2.0
    );
    gl_Position = vec4(clip, 0.0, 1.0);
}