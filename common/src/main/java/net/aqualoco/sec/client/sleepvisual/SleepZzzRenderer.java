package net.aqualoco.sec.client.sleepvisual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.aqualoco.sec.Constants;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

// Submits the Z sprite as a tiny world-space billboard through the 1.21 render queue.
public final class SleepZzzRenderer {

    private static final Identifier Z_TEXTURE = Identifier.fromNamespaceAndPath(
            Constants.MOD_ID,
            "textures/gui/sleep/z.png"
    );
    private static final float BASE_SIZE = 0.24F;

    private SleepZzzRenderer() {
    }

    public static void submit(PoseStack poseStack,
                              CameraRenderState cameraRenderState,
                              SubmitNodeCollector submitNodeCollector,
                              SleepZzzGlyph glyph,
                              float partialTick) {
        float alpha = glyph.alpha(partialTick);
        if (alpha <= 0.01F) {
            return;
        }

        Vec3 pos = glyph.renderPosition(partialTick);
        Vec3 cameraPos = cameraRenderState.pos;
        poseStack.pushPose();
        poseStack.translate(pos.x() - cameraPos.x(), pos.y() - cameraPos.y(), pos.z() - cameraPos.z());
        poseStack.mulPose(new Quaternionf(cameraRenderState.orientation));
        poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(glyph.rotationDegrees())));

        int alphaByte = (int) (clamp01(alpha) * 255.0F);
        float size = BASE_SIZE * glyph.scale(partialTick);
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(Z_TEXTURE),
                (pose, vertexConsumer) -> drawQuad(pose, vertexConsumer, size, alphaByte)
        );
        poseStack.popPose();
    }

    private static void drawQuad(PoseStack.Pose pose, VertexConsumer vertexConsumer, float size, int alpha) {
        float half = size * 0.5F;
        vertex(vertexConsumer, pose, -half, -half, 0.0F, 1.0F, alpha);
        vertex(vertexConsumer, pose, half, -half, 1.0F, 1.0F, alpha);
        vertex(vertexConsumer, pose, half, half, 1.0F, 0.0F, alpha);
        vertex(vertexConsumer, pose, -half, half, 0.0F, 0.0F, alpha);
    }

    private static void vertex(VertexConsumer vertexConsumer,
                               PoseStack.Pose pose,
                               float x,
                               float y,
                               float u,
                               float v,
                               int alpha) {
        vertexConsumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }
}
