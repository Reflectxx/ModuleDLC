package dev.dlc.client.setting;

import java.util.function.BooleanSupplier;

public class NumberSetting implements Setting<Float> {
    private final String name;
    private final String hint;
    private final float min, max, step;
    private final String suffix;
    private float value;
    private final BooleanSupplier visible;

    public NumberSetting(String name, String hint, float min, float max, float step, float value, String suffix) {
        this(name, hint, min, max, step, value, suffix, () -> true);
    }
    public NumberSetting(String name, String hint, float min, float max, float step, float value, String suffix, BooleanSupplier visible) {
        this.name = name; this.hint = hint; this.min = min; this.max = max; this.step = step;
        this.value = value; this.suffix = suffix; this.visible = visible;
    }
    public String name() { return name; }
    public String hint() { return hint; }
    public boolean visible() { return visible.getAsBoolean(); }
    public float min() { return min; }
    public float max() { return max; }
    public float step() { return step; }
    public String suffix() { return suffix; }
    public float value() { return value; }
    public void set(float v) { value = Math.max(min, Math.min(max, v)); }
}
