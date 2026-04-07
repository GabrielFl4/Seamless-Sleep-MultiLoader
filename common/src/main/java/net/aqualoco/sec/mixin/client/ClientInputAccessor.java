package net.aqualoco.sec.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("keyPresses")
    Input seamlesssleep$getKeyPresses();

    @Accessor("keyPresses")
    void seamlesssleep$setKeyPresses(Input keyPresses);

    @Accessor("moveVector")
    void seamlesssleep$setMoveVector(Vec2 moveVector);
}
