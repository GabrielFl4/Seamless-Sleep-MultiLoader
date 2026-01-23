package net.aqualoco.sec.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraSleepAnimationMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void seamlesssleep$tiltSleepCamera(BlockGetter area,
                                               Entity focusedEntity,
                                               boolean thirdPerson,
                                               boolean inverseView,
                                               float tickDelta,
                                               CallbackInfo ci) {
        if (!(focusedEntity instanceof Player player)) {
            return;
        }

        if (thirdPerson || !player.isSleeping()) {
            return;
        }

        Camera self = (Camera) (Object) this;
        float yaw = self.getYRot();
        float pitch = self.getXRot();

        float tilt = -10.0F;

        this.setRotation(yaw, pitch + tilt);
    }
}
