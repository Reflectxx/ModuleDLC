package dev.dlc.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ShaderIcon {
    private static RenderPipeline pipeline;

    private ShaderIcon() {}

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline =
                    RenderPipelines.register(
                            RenderPipeline.builder()
                                    .withLocation("pipeline/moduledlc_icon")
                                    .withUniform("ScreenSize", UniformType.UNIFORM_BUFFER)
                                    .withSampler("Sampler0")
                                    .withVertexShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/texture"))
                                    .withFragmentShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/texture"))
                                    .withBlend(BlendFunction.TRANSLUCENT)
                                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                    .withDepthWrite(false)
                                    .withCull(false)
                                    .withVertexFormat(
                                            DefaultVertexFormat.POSITION_TEX_COLOR,
                                            VertexFormat.Mode.QUADS)
                                    .build());
        }
        return pipeline;
    }

    public static void flame(
            GuiGraphics graphics,
            float x,
            float y,
            float size,
            int color,
            Render2D.Scissor scissor) {
        draw(graphics, "flame", x, y, size, color, 0, scissor);
    }

    public static void draw(
            GuiGraphics graphics,
            String name,
            float x,
            float y,
            float size,
            int color,
            Render2D.Scissor scissor) {
        draw(graphics, name, x, y, size, color, 0, scissor);
    }

    public static void draw(
            GuiGraphics graphics,
            String name,
            float x,
            float y,
            float size,
            int color,
            float rotation,
            Render2D.Scissor scissor) {
        Identifier textureId =
                Identifier.fromNamespaceAndPath(
                        "moduledlc", "textures/gui/icons/" + name + ".png");
        drawTexture(graphics, textureId, x, y, size, size, color, rotation, scissor);
    }

    public static void drawTexture(
            GuiGraphics graphics,
            Identifier textureId,
            float x,
            float y,
            float width,
            float height,
            int color,
            Render2D.Scissor scissor) {
        drawTexture(graphics, textureId, x, y, width, height, color, 0, 0, 1, 1, 0, scissor);
    }

    public static void drawTextureRegion(
            GuiGraphics graphics,
            Identifier textureId,
            float x,
            float y,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int color,
            Render2D.Scissor scissor) {
        drawTexture(graphics, textureId, x, y, width, height, color, 0, u0, v0, u1, v1, scissor);
    }

    private static void drawTexture(
            GuiGraphics graphics,
            Identifier textureId,
            float x,
            float y,
            float width,
            float height,
            int color,
            float rotation,
            Render2D.Scissor scissor) {
        drawTexture(graphics, textureId, x, y, width, height, color, rotation, 0, 0, 1, 1, scissor);
    }

    private static void drawTexture(
            GuiGraphics graphics,
            Identifier textureId,
            float x,
            float y,
            float width,
            float height,
            int color,
            float rotation,
            float u0,
            float v0,
            float u1,
            float v1,
            Render2D.Scissor scissor) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
        float centerX = x + width / 2;
        float centerY = y + height / 2;
        float cos = (float) Math.cos(rotation);
        float sin = (float) Math.sin(rotation);
        Render2D.draw(
                graphics,
                textureId.toString(),
                pipeline(),
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder -> {
                    addVertex(
                            builder,
                            centerX,
                            centerY,
                            -width / 2,
                            -height / 2,
                            cos,
                            sin,
                            u0,
                            v0,
                            color);
                    addVertex(
                            builder,
                            centerX,
                            centerY,
                            -width / 2,
                            height / 2,
                            cos,
                            sin,
                            u0,
                            v1,
                            color);
                    addVertex(
                            builder,
                            centerX,
                            centerY,
                            width / 2,
                            height / 2,
                            cos,
                            sin,
                            u1,
                            v1,
                            color);
                    addVertex(
                            builder,
                            centerX,
                            centerY,
                            width / 2,
                            -height / 2,
                            cos,
                            sin,
                            u1,
                            v0,
                            color);
                },
                Map.of(),
                texture,
                scissor);
    }

    private static void addVertex(
            BufferBuilder builder,
            float centerX,
            float centerY,
            float offsetX,
            float offsetY,
            float cos,
            float sin,
            float u,
            float v,
            int color) {
        builder.addVertex(
                        centerX + offsetX * cos - offsetY * sin,
                        centerY + offsetX * sin + offsetY * cos,
                        0)
                .setUv(u, v)
                .setColor(color);
    }
}
