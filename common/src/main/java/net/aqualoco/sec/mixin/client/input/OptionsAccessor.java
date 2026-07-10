package net.aqualoco.sec.mixin.client.input;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface OptionsAccessor {

    @Accessor("invertYMouse")
    OptionInstance<Boolean> seamlesssleep$getInvertYMouse();
}
