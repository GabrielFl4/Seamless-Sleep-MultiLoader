package net.aqualoco.sec;

import net.aqualoco.sec.block.ModBlocks;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AquaSec implements ModInitializer {
	public static final String MOD_ID = "seamlesssleep";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final SleepAnimationState OVERWORLD_SLEEP_ANIMATION = new SleepAnimationState();

	@Override
	public void onInitialize() {
		SleepAnimationNetworking.initCommon();
		ModBlocks.registerModBlocks();
		LOGGER.info("[Seamless Sleep] inicializado. Animacao de sono e bloco sleep_barrier registrados.");
	}
}
