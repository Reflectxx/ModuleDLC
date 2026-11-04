package dev.dlc.client.setting;

import java.util.function.BooleanSupplier;

public class ColorSetting implements Setting<Integer> {
    private final String name;
    private final String hint;
    private float hue, sat, bri, alpha;
    private final BooleanSupplier visible;

    public ColorSetting(String name, String hint, int argb) {
        this(name, hint, argb, () -> true);
    }
    public ColorSetting(String name, String hint, int argb, BooleanSupplier visible) {
        this.name = name; this.hint = hint; this.visible = visible;
        this.alpha = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        this.bri = max;
        if (max == 0) { this.sat = 0; this.hue = 0; return; }
        this.sat = (max - min) / max;
        if (max == min) { this.hue = 0; return; }
        float d = max - min;
        if (max == r) this.hue = (g - b) / d + (g < b ? 6 : 0);
        else if (max == g) this.hue = (b - r) / d + 2;
        else this.hue = (r - g) / d + 4;
        this.hue /= 6f;
    }

    public String name() { return name; }
    public String hint() { return hint; }
    public boolean visible() { return visible.getAsBoolean(); }
    public int argb() {
        int rgb = java.awt.Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
        return (Math.round(alpha * 255f) << 24) | rgb;
    }
    public String hex() {
        return String.format("#%06X", argb() & 0xFFFFFF);
    }
    public float hue() { return hue; }
    public float saturation() { return sat; }
    public float brightness() { return bri; }
    public float alpha() { return alpha; }
    public void setHsb(float h, float s, float b) { hue = h; sat = s; bri = b; }
    public void setAlpha(float a) { alpha = a; }
}
