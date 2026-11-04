package dev.dlc.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class Render2D {
    private static final List<PendingDraw> PENDING_DRAWS = new ArrayList<>();

    private record PendingDraw(
            String name,
            RenderPipeline pipeline,
            VertexFormat format,
            MeshData mesh,
            Map<String, ByteBuffer> uniforms,
            AbstractTexture texture,
            int screenWidth,
            int screenHeight,
            Scissor scissor) {}

    public record Scissor(float x, float y, float width, float height) {
        public Scissor intersect(Scissor other) {
            float left = Math.max(x, other.x);
            float top = Math.max(y, other.y);
            float right = Math.min(x + width, other.x + other.width);
            float bottom = Math.min(y + height, other.y + other.height);
            return new Scissor(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }

    private Render2D() {}

    public static ByteBuffer floats(float... values) {
        ByteBuffer buffer =
                ByteBuffer.allocateDirect(Math.max(16, ((values.length + 3) / 4) * 16))
                        .order(ByteOrder.nativeOrder());
        for (float value : values) buffer.putFloat(value);
        while (buffer.hasRemaining()) buffer.put((byte) 0);
        buffer.flip();
        return buffer;
    }

    public static ByteBuffer integer(int value) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        buffer.putInt(value);
        while (buffer.hasRemaining()) buffer.put((byte) 0);
        buffer.flip();
        return buffer;
    }

    public static void draw(
            GuiGraphics graphics,
            String name,
            RenderPipeline pipeline,
            VertexFormat format,
            Consumer<BufferBuilder> writeVertices,
            Map<String, ByteBuffer> uniforms,
            AbstractTexture texture,
            Scissor scissor) {
        ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(8192);
        BufferBuilder builder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, format);
        MeshData mesh;
        try {
            writeVertices.accept(builder);
            mesh = builder.build();
        } catch (RuntimeException | Error failure) {
            byteBufferBuilder.close();
            throw failure;
        }
        if (mesh == null) {
            byteBufferBuilder.close();
            return;
        }

        PENDING_DRAWS.add(
                new PendingDraw(
                        name,
                        pipeline,
                        format,
                        mesh,
                        Map.copyOf(uniforms),
                        texture,
                        graphics.guiWidth(),
                        graphics.guiHeight(),
                        scissor));
    }

    public static void flush() {
        if (PENDING_DRAWS.isEmpty()) return;
        List<PendingDraw> pending = List.copyOf(PENDING_DRAWS);
        PENDING_DRAWS.clear();
        try {
            for (PendingDraw draw : pending) render(draw);
        } finally {
            pending.forEach(draw -> draw.mesh().close());
        }
    }

    public static void discardPending() {
        PENDING_DRAWS.forEach(draw -> draw.mesh().close());
        PENDING_DRAWS.clear();
    }

    private static void render(PendingDraw draw) {
        List<GpuBuffer> uniformBuffers = new ArrayList<>();
        GpuBuffer vertexBuffer = null;
        RenderPass renderPass = null;
        try {
            ByteBuffer screenSize = floats(draw.screenWidth(), draw.screenHeight());
            uniformBuffers.add(
                    RenderSystem.getDevice()
                            .createBuffer(
                                    () -> draw.name() + " screen size",
                                    GpuBuffer.USAGE_UNIFORM,
                                    screenSize));

            List<String> uniformNames = new ArrayList<>(draw.uniforms().size() + 1);
            uniformNames.add("ScreenSize");
            for (Map.Entry<String, ByteBuffer> uniform : draw.uniforms().entrySet()) {
                uniformNames.add(uniform.getKey());
                uniformBuffers.add(
                        RenderSystem.getDevice()
                                .createBuffer(
                                        () -> draw.name() + " " + uniform.getKey(),
                                        GpuBuffer.USAGE_UNIFORM,
                                        uniform.getValue()));
            }

            MeshData mesh = draw.mesh();
            MeshData.DrawState drawState = mesh.drawState();
            if (drawState.indexCount() == 0) return;
            vertexBuffer = draw.format().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            RenderSystem.AutoStorageIndexBuffer indexBuffer =
                    RenderSystem.getSequentialBuffer(drawState.mode());
            RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
            renderPass =
                    RenderSystem.getDevice()
                            .createCommandEncoder()
                            .createRenderPass(
                                    () -> draw.name(),
                                    target.getColorTextureView(),
                                    OptionalInt.empty());
            renderPass.setPipeline(draw.pipeline());
            renderPass.setVertexBuffer(0, vertexBuffer);
            renderPass.setIndexBuffer(indexBuffer.getBuffer(drawState.indexCount()), indexBuffer.type());

            for (int i = 0; i < uniformBuffers.size(); i++) {
                renderPass.setUniform(uniformNames.get(i), uniformBuffers.get(i).slice());
            }
            if (draw.texture() != null) {
                renderPass.bindTexture(
                        "Sampler0", draw.texture().getTextureView(), draw.texture().getSampler());
            }
            if (draw.scissor() != null) {
                enableScissor(renderPass, draw.screenWidth(), draw.screenHeight(), draw.scissor());
            }
            renderPass.drawIndexed(0, 0, drawState.indexCount(), 1);
        } finally {
            if (renderPass != null) renderPass.close();
            for (GpuBuffer uniformBuffer : uniformBuffers) uniformBuffer.close();
        }
    }

    private static void enableScissor(
            RenderPass renderPass, int screenWidth, int screenHeight, Scissor scissor) {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        var window = Minecraft.getInstance().getWindow();
        float scaleX = (float) window.getWidth() / screenWidth;
        float scaleY = (float) window.getHeight() / screenHeight;
        int x = Math.round(scissor.x() * scaleX);
        int y = Math.round(window.getHeight() - (scissor.y() + scissor.height()) * scaleY);
        int width = Math.max(0, Math.round(scissor.width() * scaleX));
        int height = Math.max(0, Math.round(scissor.height() * scaleY));
        renderPass.enableScissor(x, y, width, height);
    }
}
