package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;

// Shared config fallback used when a full YACL config screen is unavailable.
public final class ConfigFallbackScreen extends Screen {
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private final Screen parent;
    private final String noticeKey;
    private final String hintKey;
    private final ChatFormatting noticeFormatting;

    public ConfigFallbackScreen(Screen parent, String noticeKey, String hintKey, ChatFormatting noticeFormatting) {
        super(Component.literal(Constants.MOD_NAME));
        this.parent = parent;
        this.noticeKey = noticeKey;
        this.hintKey = hintKey;
        this.noticeFormatting = noticeFormatting;
    }

    public static ConfigFallbackScreen forgeFileOnly(Screen parent) {
        return new ConfigFallbackScreen(
                parent,
                "screen.seamlesssleep.forge_config.notice",
                "screen.seamlesssleep.forge_config.hint",
                ChatFormatting.YELLOW
        );
    }

    public static ConfigFallbackScreen missingYacl(Screen parent) {
        return new ConfigFallbackScreen(
                parent,
                "screen.seamlesssleep.missing_yacl.notice",
                "screen.seamlesssleep.missing_yacl.hint",
                ChatFormatting.RED
        );
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    protected void init() {
        if (minecraft == null) {
            return;
        }

        int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int x = this.width / 2 - rowWidth / 2;
        int y = this.height / 2 + 10;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.seamlesssleep.config.open_folder"),
                        button -> this.openConfigFolder())
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.back"),
                        button -> this.onClose())
                .bounds(x + BUTTON_WIDTH + BUTTON_GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int x = this.width / 2;
        int y = this.height / 2 - 20;
        graphics.drawCenteredString(this.font,
                Component.translatable(this.noticeKey).withStyle(this.noticeFormatting),
                x,
                y,
                0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable(this.hintKey),
                x,
                y + 12,
                0xFFFFFFFF);
    }

    private void openConfigFolder() {
        try {
            Path configDir = Services.PLATFORM.getConfigDir();
            Files.createDirectories(configDir);
            Util.getPlatform().openPath(configDir);
        } catch (Exception e) {
            Constants.warn("Failed to open config folder: {}", e.getMessage());
        }
    }
}
