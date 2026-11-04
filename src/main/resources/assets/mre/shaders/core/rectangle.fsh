#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec4 FragColor;

layout(std140) uniform Size { vec2 SizeVal; };
layout(std140) uniform Radius { vec4 RadiusVal; };
layout(std140) uniform Smoothness { float SmoothnessVal; };

out vec4 OutColor;

void main() {
    float alpha = ralpha(SizeVal, FragCoord, RadiusVal, SmoothnessVal);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);

    if (color.a == 0.0) { // alpha test
        discard;
    }

    OutColor = color;
}