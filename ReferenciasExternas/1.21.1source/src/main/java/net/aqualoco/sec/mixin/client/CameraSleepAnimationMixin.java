package net.aqualoco.sec.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraSleepAnimationMixin {


    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void aquasec$tiltSleepCamera(BlockView area,
                                         Entity focusedEntity,
                                         boolean thirdPerson,
                                         boolean inverseView,
                                         float tickDelta,
                                         CallbackInfo ci) {
        if (!(focusedEntity instanceof PlayerEntity player)) {
            return;
        }

        if (thirdPerson || !player.isSleeping()) {
            return;
        }

        Camera self = (Camera) (Object) this;
        float yaw = self.getYaw();
        float pitch = self.getPitch();

        float tilt = -10.0F;

        this.setRotation(yaw, pitch + tilt);
    }
}

