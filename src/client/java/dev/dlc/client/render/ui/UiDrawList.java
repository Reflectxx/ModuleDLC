package dev.dlc.client.render.ui;

import dev.dlc.client.render.Render2D;
import dev.dlc.client.render.ShaderBorder;
import dev.dlc.client.render.ShaderGradient;
import dev.dlc.client.render.ShaderIcon;
import dev.dlc.client.render.ShaderRect;
import dev.dlc.client.render.ShaderText;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public class UiDrawList {
    private final GuiGraphics graphics;
    private final Deque<Render2D.Scissor> scissors = new ArrayDeque<>();
    private final Deque<Float> opacityStack = new ArrayDeque<>();
    private float opacity = 1f;

    public UiDrawList(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public UiDrawList() {
        this(null);
    }

    public void opacity(float value) {
        opacity = Math.max(0, Math.min(1, value));
    }

    public void pushOpacity(float value) {
        opacityStack.push(opacity);
        opacity(Math.max(0, Math.min(1, value)) * opacity);
    }

    public void popOpacity() {
        if (opacityStack.isEmpty()) throw new IllegalStateException("Opacity stack is empty");
        opacity = opacityStack.pop();
    }

    private int apply(int color) {
        int alpha = Math.round(((color >>> 24) & 255) * opacity);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private Render2D.Scissor scissor() {
        return scissors.peek();
    }

    public void rect(float x, float y, float width, float height, int color) {
        if (graphics == null) return;
        ShaderRect.render(
                graphics, x, y, width, height, 0, 0, 0, 0, 0.75f, apply(color), scissor());
    }

    public void roundedRect(float x, float y, float width, float height, float radius, int color) {
        if (graphics == null) return;
        float r = Math.max(0, Math.min(radius, Math.min(width, height) * 0.5f));
        ShaderRect.render(
                graphics, x, y, width, height, r, r, r, r, 0.75f, apply(color), scissor());
    }

    public void outlineRoundedRect(
            float x, float y, float width, float height, float radius, float thickness, int color) {
        if (graphics == null) return;
        float r = Math.max(0, Math.min(radius, Math.min(width, height) * 0.5f));
        ShaderBorder.render(
                graphics,
                x,
                y,
                width,
                height,
                r,
                Math.max(0, thickness),
                apply(color),
                scissor());
    }

    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        if (graphics == null) return;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0) return;

        float halfWidth = Math.max(0.5f, thickness) * 0.5f;
        float halfLength = length * 0.5f;
        float angle = (float) Math.atan2(dy, dx);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float cx = (x1 + x2) * 0.5f;
        float cy = (y1 + y2) * 0.5f;
        float[][] corners = {
            {-halfLength, -halfWidth},
            {-halfLength, halfWidth},
            {halfLength, halfWidth},
            {halfLength, -halfWidth}
        };
        float[] xs = new float[4];
        float[] ys = new float[4];
        for (int i = 0; i < corners.length; i++) {
            xs[i] = cx + corners[i][0] * cos - corners[i][1] * sin;
            ys[i] = cy + corners[i][0] * sin + corners[i][1] * cos;
        }
        float r = Math.min(halfWidth, halfLength);
        ShaderRect.renderQuad(
                graphics,
                xs,
                ys,
                length,
                halfWidth * 2,
                r,
                r,
                r,
                r,
                0.75f,
                apply(color),
                scissor());
    }

    public void circle(float x, float y, float radius, int color) {
        if (graphics == null) return;
        roundedRect(x - radius, y - radius, radius * 2, radius * 2, radius, color);
    }

    public void flameIcon(float x, float y, float size, int color) {
        if (graphics == null) return;
        ShaderIcon.flame(graphics, x, y, size, apply(color), scissor());
    }

    public void icon(String name, float x, float y, float size, int color) {
        if (graphics == null) return;
        ShaderIcon.draw(graphics, name, x, y, size, apply(color), scissor());
    }

    public void image(
            Identifier texture, float x, float y, float width, float height, int tint) {
        if (graphics == null) return;
        ShaderIcon.drawTexture(
                graphics, texture, x, y, width, height, apply(tint), scissor());
    }

    public void centeredIcon(String name, float x, float y, float size, int color) {
        icon(name, x - size / 2, y - size / 2, size, color);
    }

    public void rotatedCenteredIcon(
            String name, float x, float y, float size, int color, float rotation) {
        if (graphics == null) return;
        ShaderIcon.draw(
                graphics, name, x - size / 2, y - size / 2, size, apply(color), rotation, scissor());
    }

    public void ring(float x, float y, float radius, float thickness, int segments, int color1, int color2) {
        if (graphics == null || radius <= 0) return;
        int count = Math.max(12, segments);
        float previousX = x + radius;
        float previousY = y;
        for (int i = 1; i <= count; i++) {
            float angle = i * (float) (Math.PI * 2 / count);
            float nextX = x + (float) Math.cos(angle) * radius;
            float nextY = y + (float) Math.sin(angle) * radius;
            line(
                    previousX,
                    previousY,
                    nextX,
                    nextY,
                    thickness,
                    blend(color1, color2, (float) (i - 1) / count));
            previousX = nextX;
            previousY = nextY;
        }
    }

    public void triangle(
            float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        if (graphics == null) return;
        int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3)));
        int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3)));
        float[] xs = {x1, x2, x3, x1};
        float[] ys = {y1, y2, y3, y1};
        for (int y = minY; y <= maxY; y++) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (int edge = 0; edge < 3; edge++) {
                float ya = ys[edge];
                float yb = ys[edge + 1];
                if ((ya <= y && yb > y) || (yb <= y && ya > y)) {
                    float xa = xs[edge];
                    float xb = xs[edge + 1];
                    int intersection = (int) (xa + (y - ya) / (yb - ya) * (xb - xa));
                    left = Math.min(left, intersection);
                    right = Math.max(right, intersection);
                }
            }
            if (left <= right) rect(left, y, right - left + 1, 1, color);
        }
    }

    public void gradientRoundedRect(
            float x,
            float y,
            float width,
            float height,
            float radius,
            int color1,
            int color2,
            boolean horizontal) {
        if (graphics == null) return;
        ShaderGradient.render(
                graphics,
                x,
                y,
                width,
                height,
                Math.max(0, Math.min(radius, Math.min(width, height) * 0.5f)),
                apply(color1),
                apply(color2),
                horizontal,
                scissor());
    }

    public void pushScissor(float x, float y, float width, float height) {
        Render2D.Scissor next = new Render2D.Scissor(x, y, width, height);
        if (!scissors.isEmpty()) next = scissors.peek().intersect(next);
        scissors.push(next);
    }

    public void popScissor() {
        if (scissors.isEmpty()) throw new IllegalStateException("Scissor stack is empty");
        scissors.pop();
    }

    public void text(float x, float y, float size, int color, String text) {
        if (graphics == null) return;
        ShaderText.render(graphics, text, x, y, size, apply(color), scissor());
    }

    public void strongText(float x, float y, float size, int color, String text) {
        text(x, y, size, color, text);
    }

    public void textCenteredVertically(
            float x, float centerY, float size, int color, String text) {
        text(x, centerY - ShaderText.verticalCenterOffset(text, size), size, color, text);
    }

    public void centeredText(float x, float y, float size, int color, String text, boolean shadow) {
        textCenteredVertically(
                x - ShaderText.width(text, size) / 2, y, size, color, text);
    }

    public void rightText(float x, float y, float size, int color, String text, boolean shadow) {
        text(x - ShaderText.width(text, size), y, size, color, text);
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int first = (from >>> shift) & 255;
            int second = (to >>> shift) & 255;
            result |= Math.round(first + (second - first) * amount) << shift;
        }
        return result;
    }
}
