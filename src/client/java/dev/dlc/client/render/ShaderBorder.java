package dev.dlc.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ShaderBorder {
    private static RenderPipeline pipeline;

    private ShaderBorder() {}

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline =
                    RenderPipelines.register(
                            RenderPipeline.builder()
                                    .withLocation("pipeline/moduledlc_border")
                                    .withUniform("ScreenSize", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Size", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Radius", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Thickness", UniformType.UNIFORM_BUFFER)
                                    .withUniform("Smoothness", UniformType.UNIFORM_BUFFER)
                                    .withVertexShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/border"))
                                    .withFragmentShader(
                                            Identifier.fromNamespaceAndPath("mre", "core/border"))
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
            float thickness,
            int color,
            Render2D.Scissor scissor) {
        Render2D.draw(
                graphics,
                "border",
                pipeline(),
                DefaultVertexFormat.POSITION_COLOR,
                (BufferBuilder builder) -> {
                    builder.addVertex(x, y, 0).setColor(color);
                    builder.addVertex(x, y + height, 0).setColor(color);
                    builder.addVertex(x + width, y + height, 0).setColor(color);
                    builder.addVertex(x + width, y, 0).setColor(color);
                },
                Map.of(
                        "Size", Render2D.floats(width, height),
                        "Radius", Render2D.floats(radius, radius, radius, radius),
                        "Thickness", Render2D.floats(thickness),
                        "Smoothness", Render2D.floats(0.75f, 0.75f)),
                null,
                scissor);
    }
}
