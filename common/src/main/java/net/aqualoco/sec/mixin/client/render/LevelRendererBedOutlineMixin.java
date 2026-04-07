package net.aqualoco.sec.mixin.client.render;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Drops the vanilla block-selection outline while the player is bed-bound, avoiding tilt-induced hitbox mismatch.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBedOutlineMixin {

    @ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean seamlesssleep$disableBlockOutlineWhileBedBound(boolean renderBlockOutline) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ClientBedWorkflow.isManagedBedState(player)) {
            return renderBlockOutline;
        }

        return false;
    }
}
