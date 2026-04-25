package net.aqualoco.sec.mixin.sleep;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockEntity.class)
public interface BlockEntitySetChangedInvoker {

    @Invoker("setChanged")
    static void seamlesssleep$invokeSetChanged(Level level, BlockPos pos, BlockState state) {
        throw new AssertionError();
    }
}
