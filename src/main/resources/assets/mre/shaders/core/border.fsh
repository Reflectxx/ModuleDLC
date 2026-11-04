#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

layout(std140) uniform Size { vec2 SizeVal; };
layout(std140) uniform Radius { vec4 RadiusVal; };
layout(std140) uniform Thickness { float ThicknessVal; };
layout(std140) uniform Smoothness { vec2 SmoothnessVal; };

out vec4 OutColor;

void main() {
    vec2 center = SizeVal * 0.5;
    float dist = rdist(center - (FragCoord.xy * SizeVal), center - 1.0, RadiusVal);
    float alpha = smoothstep(1.0 - ThicknessVal - SmoothnessVal.x - SmoothnessVal.y,
        1.0 - ThicknessVal - SmoothnessVal.y, dist);
    alpha *= 1.0 - smoothstep(1.0 - SmoothnessVal.y, 1.0, dist);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);

    if (color.a == 0.0) {
        discard;
    }

    OutColor = color;
}
