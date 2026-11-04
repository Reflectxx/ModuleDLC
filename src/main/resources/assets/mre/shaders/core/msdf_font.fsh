#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
layout(std140) uniform Range { float RangeVal; };
layout(std140) uniform Thickness { float ThicknessVal; };
layout(std140) uniform Smoothness { float SmoothnessVal; };
layout(std140) uniform Outline { int OutlineVal; };
layout(std140) uniform OutlineThickness { float OutlineThicknessVal; };
layout(std140) uniform OutlineColor { vec4 OutlineColorVal; };

out vec4 OutColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + ThicknessVal;
    vec2 h = vec2(dFdx(TexCoord.x), dFdy(TexCoord.y)) * textureSize(Sampler0, 0);
    float pixels = RangeVal * inversesqrt(h.x * h.x + h.y * h.y);
    float alpha = smoothstep(-SmoothnessVal, SmoothnessVal, dist * pixels);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);
    
    if (OutlineVal != 0) {
        color = mix(OutlineColorVal, FragColor, alpha);
        color.a *= smoothstep(-SmoothnessVal, SmoothnessVal, (dist + OutlineThicknessVal) * pixels);
    }
    
    OutColor = color;
}