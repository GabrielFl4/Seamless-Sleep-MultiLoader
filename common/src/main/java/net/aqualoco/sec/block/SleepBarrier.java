package net.aqualoco.sec.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

// Invisible helper block used during sleep flow and safety checks.
public class SleepBarrier extends Block {


    public SleepBarrier(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
