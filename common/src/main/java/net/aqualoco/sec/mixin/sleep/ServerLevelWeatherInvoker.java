package net.aqualoco.sec.mixin.sleep;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelWeatherInvoker {

    @Invoker("resetWeatherCycle")
    void seamlesssleep$invokeResetWeatherCycle();
}
