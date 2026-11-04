package dev.dlc.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.Map;
import dev.dlc.client.render.msdf.MsdfFont;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ShaderText {
    private static RenderPipeline pipeline;
    private static MsdfFont font;

    private ShaderText() {}

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline =
                    RenderPipelines.register(
                            RenderPipeline.builder()
                                    .withLocation("pipeline/moduledlc_msdf")
                                    .withUniform("ScreenSize", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Range", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Thickness", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Smoothness", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Outline", UniformType.UNIFORM_BUFFER)
                                    .withUniform("OutlineThickness", UniformType.UNIFORM_BUFFER)
                                    .withUniform("OutlineColor", UniformType.UNIFORM_BUFFER)
                                    .withSampler("Sampler0")
                                    .withVertexShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/msdf_font"))
                                    .withFragmentShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/msdf_font"))
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

    private static MsdfFont font() {
        if (font == null) {
            font = MsdfFont.builder().name("Huninn").data("huninn").atlas("huninn").build();
        }
        return font;
    }

    public static float width(String text, float size) {
        return font().getWidth(text, size);
    }

    public static float verticalCenterOffset(String text, float size) {
        return font().getVerticalCenterOffset(text) * size;
    }

    public static void render(
            GuiGraphics graphics,
            String text,
            float x,
            float y,
            float size,
            int color,
            Render2D.Scissor scissor) {
        MsdfFont font = font();
        float thickness = 0.05f;
        float smoothness = 0.5f;
        int outlineColor = 0xFFFFFFFF;
        boolean outline = false;

        Render2D.draw(
                graphics,
                "msdf text",
                pipeline(),
                DefaultVertexFormat.POSITION_TEX_COLOR,
                builder ->
                        font.applyGlyphs(
                                builder,
                                text,
                                size,
                                thickness,
                                0,
                                x,
                                y + font.getMetrics().baselineHeight() * size,
                                0,
                                color),
                Map.of(
                        "Range", Render2D.floats(font.getAtlas().range()),
                        "Thickness", Render2D.floats(thickness),
                        "Smoothness", Render2D.floats(smoothness),
                        "Outline", Render2D.integer(outline ? 1 : 0),
                        "OutlineThickness", Render2D.floats(0),
                        "OutlineColor", colorUniform(outlineColor)),
                font.getAbstractTexture(),
                scissor);
    }

    private static ByteBuffer colorUniform(int color) {
        return Render2D.floats(
                ((color >>> 16) & 255) / 255f,
                ((color >>> 8) & 255) / 255f,
                (color & 255) / 255f,
                ((color >>> 24) & 255) / 255f);
    }
}
