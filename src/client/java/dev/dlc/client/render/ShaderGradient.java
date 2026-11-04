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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ShaderGradient {
    private static RenderPipeline pipeline;

    private ShaderGradient() {}

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline =
                    RenderPipelines.register(
                            RenderPipeline.builder()
                                    .withLocation("pipeline/moduledlc_gradient")
                                    .withUniform("ScreenSize", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Size", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Radius", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Smoothness", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Color1", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Color2", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Horizontal", UniformType.UNIFORM_BUFFER)
                                    .withVertexShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/gradient"))
                                    .withFragmentShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/gradient"))
                                    .withBlend(BlendFunction.TRANSLUCENT)
                                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                    .withDepthWrite(false)
                                    .withCull(false)
                                    .withVertexFormat(
                                            DefaultVertexFormat.POSITION_COLOR,
                                            VertexFormat.Mode.QUADS)
                                    .build());
        }
        return pipeline;
    }

    public static void render(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            float radius,
            int firstColor,
            int secondColor,
            boolean horizontal,
            Render2D.Scissor scissor) {
        Render2D.draw(
                graphics,
                "gradient",
                pipeline(),
                DefaultVertexFormat.POSITION_COLOR,
                (BufferBuilder builder) -> {
                    builder.addVertex(x, y, 0).setColor(0xFFFFFFFF);
                    builder.addVertex(x, y + height, 0).setColor(0xFFFFFFFF);
                    builder.addVertex(x + width, y + height, 0).setColor(0xFFFFFFFF);
                    builder.addVertex(x + width, y, 0).setColor(0xFFFFFFFF);
                },
                Map.of(
                        "Size", Render2D.floats(width, height),
                        "Radius", Render2D.floats(radius, radius, radius, radius),
                        "Smoothness", Render2D.floats(0.75f),
                        "Color1", colorUniform(firstColor),
                        "Color2", colorUniform(secondColor),
                        "Horizontal", Render2D.integer(horizontal ? 1 : 0)),
                null,
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
