package net.aqualoco.sec.client.sleepindicator;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;

final class WristIndicatorTargetTexture extends AbstractTexture {
    WristIndicatorTargetTexture(RenderTarget target) {
        updateTarget(target);
    }

    void updateTarget(RenderTarget target) {
        this.texture = target.getColorTexture();
        this.textureView = target.getColorTextureView();
    }

    @Override
    public void close() {
        this.texture = null;
        this.textureView = null;
    }
}
