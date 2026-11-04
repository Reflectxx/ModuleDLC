package dev.dlc.client.hud;

import dev.dlc.client.render.ui.UiDrawList;
import net.minecraft.resources.Identifier;

public final class HudImage extends HudElement {
    private Identifier texture;
    private float width;
    private float height;
    private int tint;

    public HudImage(
            String id,
            Identifier texture,
            float x,
            float y,
            float width,
            float height,
            double tint,
            boolean draggable) {
        super(id, x, y, draggable);
        this.texture = texture;
        setSize(width, height);
        this.tint = requireArgb(tint);
    }

    public void setSize(float width, float height) {
        if (!Float.isFinite(width)
                || !Float.isFinite(height)
                || width <= 0
                || height <= 0) {
            throw new IllegalArgumentException("HUD image dimensions must be positive and finite");
        }
        this.width = width;
        this.height = height;
    }

    public int tint() {
        return tint;
    }

    public String resource() {
        return texture == null ? "" : texture.toString();
    }

    public void setResource(String resource) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("HUD image resource must not be blank");
        }
        int separator = resource.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : resource.substring(0, separator);
        String path = separator < 0 ? resource : resource.substring(separator + 1);
        if (namespace.isBlank() || path.isBlank() || path.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Invalid HUD image resource: " + resource);
        }
        texture = Identifier.fromNamespaceAndPath(namespace, path);
    }

    public void setTint(double tint) {
        this.tint = requireArgb(tint);
    }

    public void setTint(double red, double green, double blue) {
        this.tint = requireRgb(red, green, blue);
    }

    public void setColor(double color) {
        setTint(color);
    }

    public void setColor(double red, double green, double blue) {
        setTint(red, green, blue);
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
        if (texture != null) ui.image(texture, x(), y(), width, height, tint);
    }
}
