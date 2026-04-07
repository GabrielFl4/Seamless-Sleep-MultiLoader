package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.BedCrosshairRenderer;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Replaces the vanilla crosshair with a mod texture while the player is in the managed bed flow.
@Mixin(Gui.class)
public abstract class GuiBedCrosshairMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$renderBedCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || !ClientBedWorkflow.shouldUseBedCrosshair(player)) {
            return;
        }

        ci.cancel();

        if (!this.minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR
                && !this.seamlesssleep$canRenderCrosshairForSpectator(this.minecraft.hitResult)) {
            return;
        }

        if (this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) {
            return;
        }

        BedCrosshairRenderer.render(graphics);
    }

    private boolean seamlesssleep$canRenderCrosshairForSpectator(@Nullable HitResult hitResult) {
        if (hitResult == null) {
            return false;
        }

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) hitResult).getEntity() instanceof MenuProvider;
        }

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            Level level = this.minecraft.level;
            return level != null && level.getBlockState(blockHitResult.getBlockPos()).getMenuProvider(level, blockHitResult.getBlockPos()) != null;
        }

        return false;
    }
}
