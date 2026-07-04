package net.aqualoco.sec.mixin.sleep;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Exposes vanilla "bright outside" sleep gating so the server animation can mirror the same conditions.
@Mixin(Level.class)
public interface LevelSleepBrightnessAccessor {

    @Invoker("isBrightOutside")
    boolean seamlesssleep$invokeIsBrightOutside();
}
