package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.BedCameraRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

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
