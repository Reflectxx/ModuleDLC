package dev.dlc.client.hud;

import dev.dlc.client.render.ui.UiDrawList;

public final class HudRectangle extends HudElement {
    private static final String RECTANGLE = "rectangle";
    private static final String ROUNDED = "rounded";
    private static final String CIRCLE = "circle";
    private float width;
    private float height;
    private int color;
    private float radius = 5;
    private String shape = RECTANGLE;

    public HudRectangle(
            String id,
            float x,
            float y,
            float width,
            float height,
            double color,
            boolean draggable) {
        super(id, x, y, draggable);
        setSize(width, height);
        this.color = requireArgb(color);
    }

    public void setSize(float width, float height) {
        if (!Float.isFinite(width)
                || !Float.isFinite(height)
                || width <= 0
                || height <= 0) {
            throw new IllegalArgumentException("HUD rectangle dimensions must be positive and finite");
        }
        this.width = width;
        this.height = height;
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

    public float radius() {
        return radius;
    }

    public void setRadius(float radius) {
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("HUD corner radius must be finite and non-negative");
        }
        this.radius = radius;
    }

    public String shape() {
        return shape;
    }

    public void setShape(String shape) {
        if (shape == null) throw new IllegalArgumentException("HUD shape must not be null");
        String normalized = shape.strip().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals(RECTANGLE)
                && !normalized.equals(ROUNDED)
                && !normalized.equals(CIRCLE)) {
            throw new IllegalArgumentException(
                    "HUD shape must be 'rectangle', 'rounded', or 'circle'");
        }
        if (normalized.equals(ROUNDED) && radius == 0) radius = 5;
        this.shape = normalized;
    }

    @Override
    public float width() {
        return width;
    }

    @Override
    public float height() {
        return height;
    }

    @Override
    protected void drawSelf(UiDrawList ui) {
        switch (shape) {
            case ROUNDED ->
                    ui.roundedRect(x(), y(), width, height, radius, color);
            case CIRCLE ->
                    ui.circle(
                            x() + width / 2,
                            y() + height / 2,
                            Math.min(width, height) / 2,
                            color);
            default -> ui.rect(x(), y(), width, height, color);
        }
    }
}
