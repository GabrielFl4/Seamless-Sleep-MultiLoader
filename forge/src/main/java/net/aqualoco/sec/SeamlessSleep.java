package net.aqualoco.sec;

import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class SeamlessSleep {

    public SeamlessSleep() {

        // Use Forge to bootstrap the Common mod.
        Constants.LOG.info("Hello Forge world!");
        SeamlessSleepCommon.init();
    }
}
