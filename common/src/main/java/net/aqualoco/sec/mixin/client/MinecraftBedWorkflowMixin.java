package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MinecraftBedWorkflowMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;openScreen(Lnet/minecraft/client/gui/components/ChatComponent$ChatMethod;Lnet/minecraft/client/gui/screens/ChatScreen$ChatConstructor;)V"
            )
    )
    private void seamlesssleep$suppressAutoBedChat(ChatComponent chatComponent, ChatComponent.ChatMethod chatMethod, ChatScreen.ChatConstructor<?> chatConstructor) {
        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player != null && ClientBedWorkflow.shouldSuppressBedScreen(player)) {
            return;
        }

        chatComponent.openScreen(chatMethod, chatConstructor);
    }
}
