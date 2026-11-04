package dev.dlc.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class ShaderRect {
    private static RenderPipeline pipeline;

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = RenderPipelines.register(
                RenderPipeline.builder()
                    .withLocation("pipeline/moduledlc_rectangle")
                    .withUniform("ScreenSize", UniformType.UNIFORM_BUFFER)
                    .withUniform("Size", UniformType.UNIFORM_BUFFER)
                    .withUniform("Radius", UniformType.UNIFORM_BUFFER)
                    .withUniform("Smoothness", UniformType.UNIFORM_BUFFER)
                    .withVertexShader(Identifier.fromNamespaceAndPath("mre", "core/rectangle"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("mre", "core/rectangle"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .build()
            );
        }
        return pipeline;
    }

    public static void render(
            GuiGraphics graphics,
            float x,
            float y,
            float w,
            float h,
            float r1,
            float r2,
            float r3,
            float r4,
            float smoothness,
            int color,
            Render2D.Scissor scissor) {
        float[] xs = new float[]{x, x, x + w, x + w};
        float[] ys = new float[]{y, y + h, y + h, y};
        renderQuad(graphics, xs, ys, w, h, r1, r2, r3, r4, smoothness, color, scissor);
    }

    public static void renderQuad(
            GuiGraphics graphics,
            float[] xs,
            float[] ys,
            float w,
            float h,
            float r1,
            float r2,
            float r3,
            float r4,
            float smoothness,
            int color,
            Render2D.Scissor scissor) {
        renderQuad(
                graphics, xs, ys, w, h,
                new int[] {color, color, color, color},
                r1, r2, r3, r4, smoothness, scissor);
    }

    public static void renderQuad(
            GuiGraphics graphics,
            float[] xs,
            float[] ys,
            float w,
            float h,
            int[] colors,
            float r1,
            float r2,
            float r3,
            float r4,
            float smoothness,
            Render2D.Scissor scissor) {
        Render2D.draw(
                graphics,
                "rectangle",
                pipeline(),
                DefaultVertexFormat.POSITION_COLOR,
                builder -> {
                    for (int i = 0; i < 4; i++) {
                        builder.addVertex(xs[i], ys[i], 0).setColor(colors[i]);
                    }
                },
                Map.of(
                        "Size", Render2D.floats(w, h),
                        "Radius", Render2D.floats(r1, r2, r3, r4),
                        "Smoothness", Render2D.floats(smoothness)),
                null,
                scissor);
    }
}
