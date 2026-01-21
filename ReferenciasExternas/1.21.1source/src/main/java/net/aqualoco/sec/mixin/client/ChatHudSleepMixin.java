package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.config.AquaSecClientConfig;
import net.aqualoco.sec.config.AquaSecClientConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatHud.class)
public abstract class ChatHudSleepMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void aquasec$limitHeightWhenSleeping(CallbackInfoReturnable<Integer> cir) {
        if (!aquasec$isSleepingChat()) {
            return;
        }

        double chatHeightSetting = this.client.options.getChatHeightFocused().getValue();
        int vanillaHeight = ChatHud.getHeight(chatHeightSetting);

        double spacing = this.client.options.getChatLineSpacing().getValue();
        int lineHeight = (int) (9.0D * (1.0D + spacing));
        int maxLines = 4;
        int limitedHeight = Math.min(vanillaHeight, lineHeight * maxLines);

        cir.setReturnValue(limitedHeight);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
                    ordinal = 1
            )
    )
    private Object aquasec$dimBackgroundWhileSleeping(SimpleOption<Double> option) {
        double value = option.getValue();
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
                    target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object aquasec$dimTextWhileSleeping(SimpleOption<Double> option) {
        double value = option.getValue();
        if (!aquasec$isSleepingChat()) {
            return value;
        }

        double factor = aquasec$getConfig().sleepChatTextOpacityMultiplier;
        return value * factor;
    }

    private boolean aquasec$isSleepingChat() {
        return this.client != null
                && this.client.player != null
                && this.client.player.isSleeping()
                && this.client.currentScreen instanceof SleepingChatScreen;
    }

    private AquaSecClientConfig aquasec$getConfig() {
        return AquaSecClientConfigManager.get();
    }
}
