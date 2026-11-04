#version 150

in vec3 Position; // POSITION_TEXTURE_COLOR vertex attributes
in vec2 UV0;
in vec4 Color;

layout(std140) uniform ScreenSize { vec2 ScreenSizeVal; };

out vec2 TexCoord;
out vec4 FragColor;

void main() {
    TexCoord = UV0;
    FragColor = Color;

    vec2 clip = vec2(
        Position.x / ScreenSizeVal.x * 2.0 - 1.0,
        1.0 - Position.y / ScreenSizeVal.y * 2.0
    );
    gl_Position = vec4(clip, 0.0, 1.0);
}