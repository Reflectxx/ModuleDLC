#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;

out vec4 OutColor;

void main() {
    vec4 color = texture(Sampler0, TexCoord) * FragColor;
    if (color.a == 0.0) { // alpha test
        discard;
    }
    OutColor = color;
}