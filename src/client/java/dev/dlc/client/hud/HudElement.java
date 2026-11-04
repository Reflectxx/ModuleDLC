package dev.dlc.client.hud;

import dev.dlc.client.render.ui.UiDrawList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class HudElement {
    private final String id;
    private float x;
    private float y;
    private boolean draggable;
    private float opacity = 1;
    private HudElement parent;
    private final List<HudElement> children = new ArrayList<>();
    private Object owner;

    protected HudElement(String id, float x, float y, boolean draggable) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("HUD id must not be blank");
        this.id = id.strip();
        setPosition(x, y);
        this.draggable = draggable;
    }

    public final String id() {
        return id;
    }

    public final float x() {
        return parent == null ? x : parent.x() + x;
    }

    public final float y() {
        return parent == null ? y : parent.y() + y;
    }

    public final void setX(float x) {
        setPosition(x, y);
    }

    public final void setY(float y) {
        setPosition(x, y);
    }

    public final void setPosition(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("HUD coordinates must be finite");
        }
        this.x = parent == null ? x : x - parent.x();
        this.y = parent == null ? y : y - parent.y();
    }

    public final boolean draggable() {
        return draggable;
    }

    public final void setDraggable(boolean draggable) {
        this.draggable = draggable;
    }

    public final float opacity() {
        return opacity;
    }

    public final void setOpacity(float opacity) {
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("HUD opacity must be between 0 and 1");
        }
        this.opacity = opacity;
    }

    public final boolean attached() {
        return parent != null;
    }

    public final int depth() {
        return parent == null ? 0 : parent.depth() + 1;
    }

    public final HudElement attach(HudElement child, float offsetX, float offsetY) {
        Objects.requireNonNull(child);
        if (child == this) throw new IllegalArgumentException("A HUD element cannot attach to itself");
        if (owner == null || child.owner != owner) {
            throw new IllegalArgumentException("HUD elements can only be attached within one module");
        }
        if (child.parent != null) {
            throw new IllegalArgumentException("HUD element is already attached");
        }
        if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new IllegalArgumentException("HUD attachment offsets must be finite");
        }
        for (HudElement ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) {
                throw new IllegalArgumentException("HUD attachment would create a cycle");
            }
        }
        child.parent = this;
        child.x = offsetX;
        child.y = offsetY;
        children.add(child);
        return this;
    }

    public final HudElement attach(HudElement child) {
        Objects.requireNonNull(child);
        return attach(child, child.x() - x(), child.y() - y());
    }

    public final float groupX() {
        return groupBounds().x();
    }

    public final float groupY() {
        return groupBounds().y();
    }

    public final float groupWidth() {
        return groupBounds().width();
    }

    public final float groupHeight() {
        return groupBounds().height();
    }

    public abstract float width();

    public abstract float height();

    protected abstract void drawSelf(UiDrawList ui);

    public final boolean contains(float px, float py) {
        return px >= groupX()
                && px <= groupX() + groupWidth()
                && py >= groupY()
                && py <= groupY() + groupHeight();
    }

    public final void draw(UiDrawList ui) {
        ui.pushOpacity(opacity);
        try {
            drawSelf(ui);
            for (HudElement child : List.copyOf(children)) child.draw(ui);
        } finally {
            ui.popOpacity();
        }
    }

    public final void assignToModule(Object owner) {
        if (this.owner != null && this.owner != owner) {
            throw new IllegalStateException("HUD element already belongs to a module");
        }
        Objects.requireNonNull(owner);
        this.owner = owner;
    }

    private Bounds groupBounds() {
        float left = x();
        float top = y();
        float right = left + width();
        float bottom = top + height();
        for (HudElement child : children) {
            Bounds bounds = child.groupBounds();
            left = Math.min(left, bounds.x());
            top = Math.min(top, bounds.y());
            right = Math.max(right, bounds.x() + bounds.width());
            bottom = Math.max(bottom, bounds.y() + bounds.height());
        }
        return new Bounds(left, top, right - left, bottom - top);
    }

    private record Bounds(float x, float y, float width, float height) {}

    protected static String requireText(String text) {
        return Objects.requireNonNull(text);
    }

    protected static int requireArgb(double color) {
        if (!Double.isFinite(color)
                || color != Math.rint(color)
                || color < Integer.MIN_VALUE
                || color > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("HUD color must be a 32-bit ARGB integer");
        }
        return (int) (long) color;
    }

    protected static int requireRgb(double red, double green, double blue) {
        int r = requireColorChannel(red, "red");
        int g = requireColorChannel(green, "green");
        int b = requireColorChannel(blue, "blue");
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int requireColorChannel(double channel, String name) {
        if (!Double.isFinite(channel)
                || channel != Math.rint(channel)
                || channel < 0
                || channel > 255) {
            throw new IllegalArgumentException(
                    "HUD " + name + " color channel must be an integer from 0 to 255");
        }
        return (int) channel;
    }
}
