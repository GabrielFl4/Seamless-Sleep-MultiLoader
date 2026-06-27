package net.aqualoco.sec.mixin.forge.compat.comforts;

import net.aqualoco.sec.compat.ComfortsCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class ForgeComfortsAvatarRenderOffsetMixin {

    @Inject(
            method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void seamlesssleep$lowerRemoteComfortsSleepingBody(AvatarRenderState renderState,
                                                               CallbackInfoReturnable<Vec3> cir) {
        if (!renderState.hasPose(Pose.SLEEPING)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || renderState.id == client.player.getId()) {
            return;
        }

        Entity entity = client.level.getEntity(renderState.id);
        if (!(entity instanceof Player player)) {
            return;
        }

        BlockPos sleepingPos = player.getSleepingPos().orElse(null);
        double yOffset = ComfortsCompat.getSleepingBodyYOffset(client.level, sleepingPos);
        if (yOffset == 0.0D) {
            return;
        }

        Vec3 baseOffset = cir.getReturnValue();
        if (baseOffset != null) {
            cir.setReturnValue(baseOffset.add(0.0D, yOffset, 0.0D));
        }
    }
}
