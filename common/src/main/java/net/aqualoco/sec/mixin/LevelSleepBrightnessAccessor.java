package net.aqualoco.sec.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface LevelSleepBrightnessAccessor {

    @Invoker("isBrightOutside")
    boolean seamlesssleep$invokeIsBrightOutside();
}
