package dev.dlc.client.render.ui;

public class AnimatedFloat {
    private float value;
    private float target;

    public AnimatedFloat(float initial) {
        this.value = initial;
        this.target = initial;
    }

    public void setTarget(float t) { target = t; }

    public float update(float dt, float response) {
        float elapsed = Math.max(0f, Math.min(0.1f, dt));
        float freq = Math.max(0f, response);
        float error = value - target;
        float impulse = freq * error;
        float decay = (float) Math.exp(-freq * elapsed);
        value = target + (error + impulse * elapsed) * decay;
        value = Math.max(0f, Math.min(1f, value));
        return value;
    }

    // single-arg update used by SliderModel? no; SliderModel handles its own
    public float value() { return value; }
}
