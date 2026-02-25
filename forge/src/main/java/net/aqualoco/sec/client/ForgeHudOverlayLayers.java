package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.bus.BusGroup;

// Registers Forge 58+ HUD layers so the custom sleep text renders in the layered overlay pipeline.
@OnlyIn(Dist.CLIENT)
public final class ForgeHudOverlayLayers {
    private static final Identifier SLEEP_STATUS_TEXT_LAYER =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sleep_status_text");

    private ForgeHudOverlayLayers() {
    }

    public static void register(BusGroup modBusGroup) {
        AddGuiOverlayLayersEvent.getBus(modBusGroup).addListener(ForgeHudOverlayLayers::onAddGuiOverlayLayers);
    }

    private static void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(
                ForgeLayeredDraw.VANILLA_ROOT,
                SLEEP_STATUS_TEXT_LAYER,
                ForgeLayeredDraw.POST_SLEEP_STACK,
                (graphics, deltaTracker) -> SleepStatusOverlay.render(graphics, SeamlessSleepClientState.SLEEP_ANIMATION)
        );
    }
}
