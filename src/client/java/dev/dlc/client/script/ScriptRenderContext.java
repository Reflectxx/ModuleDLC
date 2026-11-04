package dev.dlc.client.script;

import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

public final class ScriptRenderContext {
    private static final RenderType LINES_NO_DEPTH;

    static {
        try {
            RenderPipeline base = RenderPipelines.LINES;
            Constructor<RenderPipeline> constructor = RenderPipeline.class.getDeclaredConstructor(
                    Identifier.class,
                    Identifier.class,
                    Identifier.class,
                    net.minecraft.client.renderer.ShaderDefines.class,
                    List.class,
                    List.class,
                    Optional.class,
                    DepthTestFunction.class,
                    com.mojang.blaze3d.platform.PolygonMode.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    com.mojang.blaze3d.platform.LogicOp.class,
                    com.mojang.blaze3d.vertex.VertexFormat.class,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.class,
                    float.class,
                    float.class,
                    int.class);
            constructor.setAccessible(true);
            RenderPipeline pipeline = constructor.newInstance(
                    base.getLocation(),
                    base.getVertexShader(),
                    base.getFragmentShader(),
                    base.getShaderDefines(),
                    base.getSamplers(),
                    base.getUniforms(),
                    base.getBlendFunction(),
                    DepthTestFunction.NO_DEPTH_TEST,
                    base.getPolygonMode(),
                    base.isCull(),
                    base.isWriteColor(),
                    base.isWriteAlpha(),
                    false,
                    base.getColorLogic(),
                    base.getVertexFormat(),
                    base.getVertexFormatMode(),
                    base.getDepthBiasScaleFactor(),
                    base.getDepthBiasConstant(),
                    base.getSortKey());
            RenderSetup setup = RenderSetup.builder(pipeline)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup();
            LINES_NO_DEPTH = RenderType.create("moduledlc_lines_no_depth", setup);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final WorldRenderContext context;
    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;

    public ScriptRenderContext(WorldRenderContext context) {
        this.context = java.util.Objects.requireNonNull(context);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        cameraX = camera.x;
        cameraY = camera.y;
        cameraZ = camera.z;
    }

    public void drawBlockOutline(BlockPos pos, int color) {
        java.util.Objects.requireNonNull(pos);
        drawBox(new AABB(pos), color);
    }

    public void drawBlockOutline(int x, int y, int z, int color) {
        drawBlockOutline(new BlockPos(x, y, z), color);
    }

    public void drawBox(AABB box, int color) {
        java.util.Objects.requireNonNull(box);
        PoseStack matrices = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(LINES_NO_DEPTH);
        ShapeRenderer.renderShape(
                matrices,
                lines,
                Shapes.create(box),
                -cameraX,
                -cameraY,
                -cameraZ,
                color,
                1.0f);
    }

    public double cameraX() {
        return cameraX;
    }

    public double cameraY() {
        return cameraY;
    }

    public double cameraZ() {
        return cameraZ;
    }
}