package net.aqualoco.sec.mixin.client.ui;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

// Gives the bed HUD a way to clear stale vanilla overlay/actionbar text when it takes over the same screen region.
@Mixin(Gui.class)
public interface GuiOverlayMessageAccessor {

    @Accessor("overlayMessageString")
    @Mutable
    void seamlesssleep$setOverlayMessageString(Component component);

    @Accessor("overlayMessageTime")
    @Mutable
    void seamlesssleep$setOverlayMessageTime(int ticks);
}
