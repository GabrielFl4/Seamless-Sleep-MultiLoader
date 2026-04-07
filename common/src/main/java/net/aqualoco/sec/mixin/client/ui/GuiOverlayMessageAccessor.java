package net.aqualoco.sec.mixin.client.ui;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Accessor for the vanilla overlay message timer used by the custom bed leave hint.
@Mixin(Gui.class)
public interface GuiOverlayMessageAccessor {

    @Accessor("overlayMessageTime")
    void seamlesssleep$setOverlayMessageTime(int overlayMessageTime);
}
