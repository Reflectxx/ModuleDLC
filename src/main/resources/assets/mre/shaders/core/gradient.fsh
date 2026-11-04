#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

layout(std140) uniform Size { vec2 SizeVal; };
layout(std140) uniform Radius { vec4 RadiusVal; };
layout(std140) uniform Smoothness { float SmoothnessVal; };
layout(std140) uniform Color1 { vec4 Color1Val; };
layout(std140) uniform Color2 { vec4 Color2Val; };
layout(std140) uniform Horizontal { int HorizontalVal; };

out vec4 OutColor;

void main() {
    float alpha = ralpha(SizeVal, FragCoord, RadiusVal, SmoothnessVal);
    float t = (HorizontalVal != 0) ? FragCoord.x : FragCoord.y;
    vec4 grad = mix(Color1Val, Color2Val, clamp(t, 0.0, 1.0));
    vec4 color = vec4(grad.rgb, grad.a * alpha * FragColor.a);

    if (color.a == 0.0) {
        discard;
    }

    OutColor = color;
}
