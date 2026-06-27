package net.aqualoco.sec.client.sleepindicator;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

final class WristIndicatorGuiStateRenderer implements AutoCloseable {
    private static final Comparator<ScreenRectangle> SCISSOR_COMPARATOR = Comparator.nullsFirst(
            Comparator.comparing(ScreenRectangle::top)
                    .thenComparing(ScreenRectangle::bottom)
                    .thenComparing(ScreenRectangle::left)
                    .thenComparing(ScreenRectangle::right)
    );
    private static final Comparator<TextureSetup> TEXTURE_COMPARATOR =
            Comparator.nullsFirst(Comparator.comparing(TextureSetup::getSortKey));
    private static final Comparator<GuiElementRenderState> ELEMENT_SORT_COMPARATOR =
            Comparator.comparing(GuiElementRenderState::scissorArea, SCISSOR_COMPARATOR)
                    .thenComparing(GuiElementRenderState::pipeline, Comparator.comparing(RenderPipeline::getSortKey))
                    .thenComparing(GuiElementRenderState::textureSetup, TEXTURE_COMPARATOR);
    private static final int VERTEX_BUFFER_USAGE = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST;

    private final ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(131072);
    private final CachedOrthoProjectionMatrixBuffer projectionBuffer =
            new CachedOrthoProjectionMatrixBuffer("seamlesssleep-vivecraft-wrist-gui", 1000.0F, 11000.0F, true);

    void render(GuiRenderState renderState, RenderTarget target) {
        prepareText(renderState);
        renderState.sortElements(ELEMENT_SORT_COMPARATOR);

        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(
                    this.projectionBuffer.getBuffer(target.width, target.height),
                    ProjectionType.ORTHOGRAPHIC
            );
            drawElements(renderState, target);
        } finally {
            RenderSystem.restoreProjectionMatrix();
            renderState.reset();
        }
    }

    private static void prepareText(GuiRenderState renderState) {
        renderState.forEachText(textState -> {
            textState.ensurePrepared().visit(new Font.GlyphVisitor() {
                @Override
                public void acceptGlyph(TextRenderable textRenderable) {
                    accept(textRenderable);
                }

                @Override
                public void acceptEffect(TextRenderable textRenderable) {
                    accept(textRenderable);
                }

                private void accept(TextRenderable textRenderable) {
                    renderState.submitGlyphToCurrentLayer(new GlyphRenderState(textState.pose, textRenderable, textState.scissor));
                }
            });
        });
    }

    private void drawElements(GuiRenderState renderState, RenderTarget target) {
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
                new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F),
                new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                new Vector3f(),
                new Matrix4f(),
                0.0F
        );
        List<PreparedElement> preparedElements = new ArrayList<>();
        try {
            renderState.forEachElement(
                    element -> prepareElement(element, preparedElements),
                    GuiRenderState.TraverseRange.ALL
            );
            if (preparedElements.isEmpty()) {
                return;
            }

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                            () -> "Seamless Sleep wrist indicator",
                            target.getColorTextureView(),
                            OptionalInt.empty(),
                            target.useDepth ? target.getDepthTextureView() : null,
                            OptionalDouble.empty()
                    )) {
                RenderSystem.bindDefaultUniforms(renderPass);
                if (fog != null) {
                    renderPass.setUniform("Fog", fog);
                }
                renderPass.setUniform("DynamicTransforms", transform);
                for (PreparedElement preparedElement : preparedElements) {
                    drawElement(preparedElement, renderPass, target.height);
                }
            }
        } finally {
            for (PreparedElement preparedElement : preparedElements) {
                preparedElement.vertexBuffer().close();
            }
        }
    }

    private void prepareElement(GuiElementRenderState element, List<PreparedElement> preparedElements) {
        MeshData mesh = buildMesh(element);
        if (mesh == null) {
            return;
        }

        try (mesh) {
            MeshData.DrawState drawState = mesh.drawState();
            GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Seamless Sleep wrist indicator vertices",
                    VERTEX_BUFFER_USAGE,
                    mesh.vertexBuffer()
            );
            RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(drawState.mode());
            preparedElements.add(new PreparedElement(
                    element,
                    vertexBuffer,
                    indexBuffer.getBuffer(drawState.indexCount()),
                    indexBuffer.type(),
                    drawState.indexCount()
            ));
        }
    }

    private static void drawElement(PreparedElement preparedElement, RenderPass renderPass, int targetHeight) {
        GuiElementRenderState element = preparedElement.element();
        renderPass.setPipeline(element.pipeline());
        bindTextures(element.textureSetup(), renderPass);
        applyScissor(element.scissorArea(), renderPass, targetHeight);
        renderPass.setVertexBuffer(0, preparedElement.vertexBuffer());
        renderPass.setIndexBuffer(preparedElement.indexBuffer(), preparedElement.indexType());
        renderPass.drawIndexed(0, 0, preparedElement.indexCount(), 1);
    }

    private MeshData buildMesh(GuiElementRenderState element) {
        BufferBuilder builder = new BufferBuilder(
                this.byteBufferBuilder,
                element.pipeline().getVertexFormatMode(),
                element.pipeline().getVertexFormat()
        );
        element.buildVertices(builder);
        return builder.build();
    }

    private static void bindTextures(TextureSetup textureSetup, RenderPass renderPass) {
        bindTexture(renderPass, "Sampler0", textureSetup.texure0());
        bindTexture(renderPass, "Sampler1", textureSetup.texure1());
        bindTexture(renderPass, "Sampler2", textureSetup.texure2());
    }

    private static void bindTexture(RenderPass renderPass,
                                    String samplerName,
                                    GpuTextureView textureView) {
        if (textureView != null) {
            renderPass.bindSampler(samplerName, textureView);
        }
    }

    private static void applyScissor(ScreenRectangle scissor, RenderPass renderPass, int targetHeight) {
        if (scissor == null) {
            renderPass.disableScissor();
            return;
        }

        renderPass.enableScissor(
                scissor.left(),
                targetHeight - scissor.bottom(),
                Math.max(0, scissor.width()),
                Math.max(0, scissor.height())
        );
    }

    @Override
    public void close() {
        this.byteBufferBuilder.close();
        this.projectionBuffer.close();
    }

    private record PreparedElement(
            GuiElementRenderState element,
            GpuBuffer vertexBuffer,
            GpuBuffer indexBuffer,
            VertexFormat.IndexType indexType,
            int indexCount
    ) {
    }
}
