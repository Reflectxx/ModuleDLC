package dev.dlc.client.render.ui;

public class SliderModel {
    public final float min, max;
    private float value;

    public SliderModel(float min, float max, float value) {
        this.min = min; this.max = max; this.value = value;
    }

    public float value() { return value; }
    public void setValue(float v) { value = Math.max(min, Math.min(max, v)); }

    public float update(float dt) {
        // no animation; return normalized progress
        return (value - min) / (max - min);
    }

    public void setFromMouse(int mx, float x, float w) {
        float p = Math.max(0f, Math.min(1f, (mx - x) / w));
        value = min + p * (max - min);
    }
}
