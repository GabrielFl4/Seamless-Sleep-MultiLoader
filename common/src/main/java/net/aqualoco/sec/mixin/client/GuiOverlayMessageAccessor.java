package net.aqualoco.sec.mixin.client;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiOverlayMessageAccessor {

    @Accessor("overlayMessageTime")
    void seamlesssleep$setOverlayMessageTime(int overlayMessageTime);
}
