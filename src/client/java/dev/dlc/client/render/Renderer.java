package dev.dlc.client.render;

public class Renderer {
    public float textWidth(String text, float size, boolean shadow) {
        return ShaderText.width(text, size);
    }
}
