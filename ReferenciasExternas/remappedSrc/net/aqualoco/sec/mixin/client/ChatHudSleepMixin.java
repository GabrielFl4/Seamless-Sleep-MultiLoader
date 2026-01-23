package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.config.AquaSecClientConfig;
import net.aqualoco.sec.config.AquaSecClientConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.InBedChatScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatHudSleepMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void aquasec$limitHeightWhenSleeping(CallbackInfoReturnable<Integer> cir) {
        if (!aquasec$isSleepingChat()) {
            return;
        }

        double chatHeightSetting = this.minecraft.options.chatHeightFocused().get();
        int vanillaHeight = ChatComponent.getHeight(chatHeightSetting);

        double spacing = this.minecraft.options.chatLineSpacing().get();
        int lineHeight = (int) (9.0D * (1.0D + spacing));
        int maxLines = 4;
        int limitedHeight = Math.min(vanillaHeight, lineHeight * maxLines);

        cir.setReturnValue(limitedHeight);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 1
            )
    )
    private Object aquasec$dimBackgroundWhileSleeping(OptionInstance<Double> option) {
        double value = option.get();
        if (!aquasec$isSleepingChat()) {
            return value;
        }

        double factor = aquasec$getConfig().sleepChatBackgroundOpacityMultiplier;
        return value * factor;
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object aquasec$dimTextWhileSleeping(OptionInstance<Double> option) {
        double value = option.get();
        if (!aquasec$isSleepingChat()) {
            return value;
        }

        double factor = aquasec$getConfig().sleepChatTextOpacityMultiplier;
        return value * factor;
    }

    private boolean aquasec$isSleepingChat() {
        return this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.isSleeping()
                && this.minecraft.screen instanceof InBedChatScreen;
    }

    private AquaSecClientConfig aquasec$getConfig() {
        return AquaSecClientConfigManager.get();
    }
}
