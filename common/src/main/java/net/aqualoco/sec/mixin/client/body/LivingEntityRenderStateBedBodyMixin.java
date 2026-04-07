package net.aqualoco.sec.mixin.client.body;

import net.aqualoco.sec.client.BedCameraRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Adds a tiny flag to render states so the Seamless first-person body pass can be identified downstream.
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateBedBodyMixin implements BedCameraRenderState {

    @Unique
    private boolean seamlesssleep$cameraBody;

    @Override
    public void seamlesssleep$setCameraBody(boolean value) {
        this.seamlesssleep$cameraBody = value;
    }

    @Override
    public boolean seamlesssleep$isCameraBody() {
        return this.seamlesssleep$cameraBody;
    }
}
