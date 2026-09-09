package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.CloudAccelerationController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Cloud phase boost while the sleep transition is running.
//
// ******************** VERIFY BEFORE BUILDING FOR 26.2 ********************
// This mixin targets a *local variable slot by bytecode index* (index 15), which is
// extremely sensitive to any recompilation of `CloudRenderer.render(...)` - even changes
// unrelated to clouds can shift local variable slots. 26.2 is a large rendering overhaul
// (Blaze3d/Vulkan rewrite, GpuFormat, RenderPipeline changes - see
// https://docs.neoforged.net/primer/docs/26.2/), so `CloudRenderer` may well have been
// recompiled even if its cloud logic itself is untouched. Before building:
//   1. Regenerate sources against the 26.2 Minecraft jar.
//   2. Decompile/inspect the bytecode of `CloudRenderer.render(...)` (e.g. via
//      `javap -c -p` on the intermediary/named class, or a bytecode viewer in your IDE)
//      to find the new local slot for the cloud phase/offset value before `cloudX` is
//      derived, and update the `index = 15` below accordingly.
// ***************************************************************************
@Mixin(CloudRenderer.class)
public abstract class CloudRendererSleepAccelerationMixin {

    @Unique
    private static final CloudAccelerationController seamlesssleep$cloudController = new CloudAccelerationController("Vanilla");

    @Unique
    private static boolean seamlesssleep$loggedHookOnce;

    @ModifyVariable(
            method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("STORE"),
            index = 15
    )
    // 26.1 bytecode: local slot 15 is the cloudOffset/phase accumulator before cloudX is derived.
    private float seamlesssleep$injectExtraPhaseIntoF2(float vanillaPhase) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        long now = System.currentTimeMillis();

        if (!seamlesssleep$loggedHookOnce) {
            seamlesssleep$loggedHookOnce = true;
            Constants.debug("Cloud acceleration hook active: CloudRenderer.render -> cloudOffset STORE (local index 15).");
        }

        var sample = seamlesssleep$cloudController.sample(vanillaPhase, level, now);
        seamlesssleep$cloudController.logApplied(now, vanillaPhase, sample.adjustedValue());
        return sample.adjustedValue();
    }
}
