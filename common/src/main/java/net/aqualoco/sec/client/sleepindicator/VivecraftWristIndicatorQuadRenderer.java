package net.aqualoco.sec.client.sleepindicator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.aqualoco.sec.client.VivecraftSleepWristPanel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

final class VivecraftWristIndicatorQuadRenderer {
    // Moves the final textured quad above the wrist plane to avoid z-fighting.
    private static final float SURFACE_FORWARD_OFFSET = 0.0015F;
    // Clockwise quarter turns applied to the final texture: 0, 1, 2, or 3.
    private static final int TEXTURE_ROTATION_QUARTER_TURNS = 2;
    // Keep both faces matched because the quad is submitted double-sided.
    private static final boolean TEXTURE_FRONT_FLIP_X = true;
    private static final boolean TEXTURE_FRONT_FLIP_Y = false;
    private static final boolean TEXTURE_BACK_FLIP_X = true;
    private static final boolean TEXTURE_BACK_FLIP_Y = false;

    private VivecraftWristIndicatorQuadRenderer() {
    }

    static void submit(PoseStack poseStack,
                       Vec3 cameraPos,
                       SubmitNodeCollector submitNodeCollector,
                       VivecraftSleepWristPanel.PanelPose panel,
                       ResourceLocation texture,
                       float physicalSize) {
        float halfSize = Math.max(0.001F, physicalSize * 0.5F);
        Quad quad = quad(panel, halfSize, halfSize);
        poseStack.pushPose();
        Vec3 center = panel.center().add(panel.normal().scale(SURFACE_FORWARD_OFFSET));
        poseStack.translate(center.x() - cameraPos.x(), center.y() - cameraPos.y(), center.z() - cameraPos.z());
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderType.entityTranslucent(texture),
                (pose, vertexConsumer) -> {
                    drawFront(pose, vertexConsumer, quad, panel.normal());
                    drawBack(pose, vertexConsumer, quad, panel.normal().scale(-1.0D));
                }
        );
        poseStack.popPose();
    }

    private static Quad quad(VivecraftSleepWristPanel.PanelPose panel, float halfWidth, float halfHeight) {
        Vec3 horizontal = panel.horizontal().scale(halfWidth);
        Vec3 vertical = panel.vertical().scale(halfHeight);
        return new Quad(
                horizontal.scale(-1.0D).subtract(vertical),
                horizontal.scale(-1.0D).add(vertical),
                horizontal.add(vertical),
                horizontal.subtract(vertical)
        );
    }

    private static void drawFront(PoseStack.Pose pose, VertexConsumer vertexConsumer, Quad quad, Vec3 normal) {
        entityVertex(vertexConsumer, pose, quad.bottomLeft(), uv(0.0F, 1.0F, false), normal);
        entityVertex(vertexConsumer, pose, quad.bottomRight(), uv(1.0F, 1.0F, false), normal);
        entityVertex(vertexConsumer, pose, quad.topRight(), uv(1.0F, 0.0F, false), normal);
        entityVertex(vertexConsumer, pose, quad.topLeft(), uv(0.0F, 0.0F, false), normal);
    }

    private static void drawBack(PoseStack.Pose pose, VertexConsumer vertexConsumer, Quad quad, Vec3 normal) {
        entityVertex(vertexConsumer, pose, quad.topLeft(), uv(0.0F, 0.0F, true), normal);
        entityVertex(vertexConsumer, pose, quad.topRight(), uv(1.0F, 0.0F, true), normal);
        entityVertex(vertexConsumer, pose, quad.bottomRight(), uv(1.0F, 1.0F, true), normal);
        entityVertex(vertexConsumer, pose, quad.bottomLeft(), uv(0.0F, 1.0F, true), normal);
    }

    private static Uv uv(float u, float v, boolean backFace) {
        float mappedU = u;
        float mappedV = v;
        int rotations = Math.floorMod(TEXTURE_ROTATION_QUARTER_TURNS, 4);
        for (int i = 0; i < rotations; i++) {
            float nextU = 1.0F - mappedV;
            mappedV = mappedU;
            mappedU = nextU;
        }

        if (backFace ? TEXTURE_BACK_FLIP_X : TEXTURE_FRONT_FLIP_X) {
            mappedU = 1.0F - mappedU;
        }
        if (backFace ? TEXTURE_BACK_FLIP_Y : TEXTURE_FRONT_FLIP_Y) {
            mappedV = 1.0F - mappedV;
        }
        return new Uv(mappedU, mappedV);
    }

    private static void entityVertex(VertexConsumer vertexConsumer,
                                     PoseStack.Pose pose,
                                     Vec3 point,
                                     Uv uv,
                                     Vec3 normal) {
        vertexConsumer.addVertex(pose, (float) point.x(), (float) point.y(), (float) point.z())
                .setColor(255, 255, 255, 255)
                .setUv(uv.u(), uv.v())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x(), (float) normal.y(), (float) normal.z());
    }

    private record Quad(Vec3 topLeft, Vec3 topRight, Vec3 bottomRight, Vec3 bottomLeft) {
    }

    private record Uv(float u, float v) {
    }
}
