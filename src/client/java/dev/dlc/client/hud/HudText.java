package dev.dlc.client.hud;

import dev.dlc.client.render.ShaderText;
import dev.dlc.client.render.ui.UiDrawList;

public final class HudText extends HudElement {
    private String text;
    private float size;
    private int color;

    public HudText(
            String id, String text, float x, float y, float size, double color, boolean draggable) {
        super(id, x, y, draggable);
        this.text = requireText(text);
        setSize(size);
        this.color = requireArgb(color);
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = requireText(text);
    }

    public float size() {
        return size;
    }

    public void setSize(float size) {
        if (!Float.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("HUD text size must be a positive finite number");
        }
        this.size = size;
    }

    public int color() {
        return color;
    }

    public void setColor(double color) {
        this.color = requireArgb(color);
    }

    public void setColor(double red, double green, double blue) {
        this.color = requireRgb(red, green, blue);
    }

    @Override
    public float width() {
        return ShaderText.width(text, size);
    }

    @Override
    public float height() {
        return size * 1.3f;
    }

    @Override
    protected void drawSelf(UiDrawList ui) {
        ui.text(x(), y(), size, color, text);
    }
}
