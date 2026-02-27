package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.InBedChatScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Adjusts chat height and opacity while the player is typing from bed.
@Mixin(ChatComponent.class)
public abstract class ChatHudSleepMixin {

    @Unique
    private static final long SEAMLESSSLEEP_BED_CHAT_GRACE_NANOS = 200_000_000L;

    @Shadow @Final private Minecraft minecraft;
    @Unique
    private long seamlesssleep$bedChatGraceUntilNanos;

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$limitHeightWhenSleeping(CallbackInfoReturnable<Integer> cir) {
        if (!seamlesssleep$isSleepingChat()) {
            return;
        }

        double chatHeightSetting = this.minecraft.options.chatHeightFocused().get();
        int vanillaHeight = ChatComponent.getHeight(chatHeightSetting);

        double spacing = this.minecraft.options.chatLineSpacing().get();
        int lineHeight = (int) (9.0D * (1.0D + spacing));
        int maxLines = seamlesssleep$getConfig().sleepChatMaxLines;
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
    private Object seamlesssleep$dimBackgroundWhileSleeping(OptionInstance<Double> option) {
        double value = option.get();
        if (!seamlesssleep$isSleepingChat()) {
            return value;
        }

        SeamlessSleepClientConfig cfg = seamlesssleep$getConfig();
        double factor = cfg.sleepChatBackgroundOpacityMultiplier * cfg.sleepChatOpacityMultiplier;
        if (factor < 0.0D) {
            factor = 0.0D;
        } else if (factor > 1.0D) {
            factor = 1.0D;
        }
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
    private Object seamlesssleep$dimTextWhileSleeping(OptionInstance<Double> option) {
        double value = option.get();
        if (!seamlesssleep$isSleepingChat()) {
            return value;
        }

        SeamlessSleepClientConfig cfg = seamlesssleep$getConfig();
        double factor = cfg.sleepChatTextOpacityMultiplier * cfg.sleepChatOpacityMultiplier;
        if (factor < 0.0D) {
            factor = 0.0D;
        } else if (factor > 1.0D) {
            factor = 1.0D;
        }
        return value * factor;
    }

    private boolean seamlesssleep$isSleepingChat() {
        if (this.minecraft == null || this.minecraft.player == null || !this.minecraft.player.isSleeping()) {
            this.seamlesssleep$bedChatGraceUntilNanos = 0L;
            return false;
        }

        if (this.minecraft.screen instanceof InBedChatScreen) {
            this.seamlesssleep$bedChatGraceUntilNanos = System.nanoTime() + SEAMLESSSLEEP_BED_CHAT_GRACE_NANOS;
            return true;
        }

        return this.minecraft.screen == null && System.nanoTime() < this.seamlesssleep$bedChatGraceUntilNanos;
    }

    private SeamlessSleepClientConfig seamlesssleep$getConfig() {
        return SeamlessSleepClientConfigManager.get();
    }
}
