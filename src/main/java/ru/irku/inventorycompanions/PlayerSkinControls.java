package ru.irku.inventorycompanions;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import ru.irku.inventorycompanions.skin.PlayerSkinManager;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static ru.irku.inventorycompanions.ConfigScreenLayout.*;

final class PlayerSkinControls {
    private final Supplier<OverlayConfig.Config> configSupplier;
    private final Runnable onChanged;

    private EditBox nicknameBox;
    private Button updateButton;
    private Button animationButton;
    private Button bodyPatternButton;
    private Button handsPatternButton;
    private Button legsPatternButton;

    PlayerSkinControls(Supplier<OverlayConfig.Config> configSupplier, Runnable onChanged) {
        this.configSupplier = configSupplier;
        this.onChanged = onChanged;
    }

    void init(Font font, int screenWidth) {
        int leftX = playerLeftPanelX(screenWidth);
        int leftWidth = playerLeftPanelWidth(screenWidth);
        int y = playerPanelY() + 30;

        int updateWidth = Math.min(110, Math.max(80, leftWidth / 3));
        int gap = 8;
        int nicknameX = leftX + 16;
        int nicknameWidth = Math.max(60, leftWidth - 32 - updateWidth - gap);

        nicknameBox = new EditBox(font, nicknameX, y, nicknameWidth, 22, textComponent("settings.player_nickname"));
        nicknameBox.setMaxLength(16);
        String nickname = config().customPlayerNickname;
        nicknameBox.setValue(nickname == null ? "" : nickname);

        updateButton = transparentButton(
                nicknameX + nicknameWidth + gap,
                y,
                updateWidth,
                22,
                textComponent("settings.update_skin"),
                ignored -> {
                    OverlayConfig.Config config = config();
                    PlayerSkinManager.requestUpdate(nicknameBox == null ? config.customPlayerNickname : nicknameBox.getValue());
                    onChanged.run();
                });

        int patternY = y + 48;
        int patternHeight = 32;
        int patternGap = 48;
        int patternWidth = Math.max(100, leftWidth - 32);
        int buttonX = leftX + 16;

        animationButton = transparentButton(
                buttonX,
                patternY,
                patternWidth,
                patternHeight,
                textComponent("settings.player_animation"),
                ignored -> {
                    OverlayConfig.Config config = config();
                    config.customPlayerAnimation = AnimationIds.nextPlayerAnimation(config.customPlayerAnimation);
                    saveRegenerateAndRefresh();
                });
        bodyPatternButton = patternButton(buttonX, patternY + patternGap, patternWidth, patternHeight, PlayerPatternPart.BODY);
        handsPatternButton = patternButton(buttonX, patternY + patternGap * 2, patternWidth, patternHeight, PlayerPatternPart.HANDS);
        legsPatternButton = patternButton(buttonX, patternY + patternGap * 3, patternWidth, patternHeight, PlayerPatternPart.LEGS);
    }

    void addWidgets(Consumer<EditBox> editBoxAdder, Consumer<Button> buttonAdder) {
        editBoxAdder.accept(nicknameBox);
        for (Button button : List.of(updateButton, animationButton, bodyPatternButton, handsPatternButton, legsPatternButton)) {
            buttonAdder.accept(button);
        }
    }

    void clear() {
        nicknameBox = null;
        updateButton = null;
        animationButton = null;
        bodyPatternButton = null;
        handsPatternButton = null;
        legsPatternButton = null;
    }

    void updateStates() {
        OverlayConfig.Config config = config();
        if (updateButton != null) {
            updateButton.active = !PlayerSkinManager.isLoading();
            updateButton.setMessage(Component.literal(updateButtonText()));
        }
        if (animationButton != null) {
            animationButton.setMessage(Component.literal(
                    text("settings.player_animation") + ": " + animationName(config.customPlayerAnimation)));
        }
        updatePatternState(bodyPatternButton, config.customPlayerAnimation, PlayerPatternPart.BODY, config.customPlayerBodyPatternVariant);
        updatePatternState(handsPatternButton, config.customPlayerAnimation, PlayerPatternPart.HANDS, config.customPlayerHandsPatternVariant);
        updatePatternState(legsPatternButton, config.customPlayerAnimation, PlayerPatternPart.LEGS, config.customPlayerLegsPatternVariant);
    }

    void drawLayout(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight) {
        int leftX = playerLeftPanelX(screenWidth);
        int leftY = playerPanelY();
        int leftWidth = playerLeftPanelWidth(screenWidth);
        int panelHeight = playerPanelHeight(screenHeight);

        UiTheme.drawPanel(graphics, leftX, leftY, leftWidth, panelHeight, UiTheme.PANEL_BACKGROUND);
        int rightWidth = playerRightPanelWidth(screenWidth);
        if (rightWidth > 0) {
            UiTheme.drawPanel(graphics, playerRightPanelX(screenWidth), leftY, rightWidth, panelHeight, UiTheme.PANEL_BACKGROUND);
        }

        graphics.text(font, text("settings.player_nickname"), leftX + 16, leftY + 12, 0xFFDADADA, true);
        graphics.text(font, text("settings.player_animation"), leftX + 16, leftY + 66, 0xFFDADADA, true);
        graphics.text(font, text("settings.pattern_body"), leftX + 16, leftY + 114, 0xFFDADADA, true);
        graphics.text(font, text("settings.pattern_hands"), leftX + 16, leftY + 162, 0xFFDADADA, true);
        graphics.text(font, text("settings.pattern_legs"), leftX + 16, leftY + 210, 0xFFDADADA, true);
    }

    void drawPreview(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        int panelWidth = playerRightPanelWidth(screenWidth);
        if (panelWidth <= 0) {
            return;
        }

        int panelX = playerRightPanelX(screenWidth);
        int panelY = playerPanelY();
        int panelHeight = playerPanelHeight(screenHeight);

        int margin = 20;
        int squareSize = Math.min(panelWidth - margin * 2, panelHeight - margin * 2);
        if (squareSize <= 40) {
            return;
        }

        int squareX = panelX + (panelWidth - squareSize) / 2;
        int squareY = panelY + (panelHeight - squareSize) / 2;
        UiTheme.drawPanel(graphics, squareX, squareY, squareSize, squareSize, UiTheme.PANEL_BACKGROUND);

        int padding = Math.max(20, squareSize / 9);
        OverlayConfig.AnimationSet playerSkin = config().animations.get(AnimationIds.PLAYER_SKIN);
        OverlayConfig.AnimationSet animation = playerSkin == null ? config().selected() : playerSkin;
        OverlayRenderer.drawCharacterPreview(graphics, animation.survivalInventory,
                squareX + padding, squareY + padding,
                squareSize - padding * 2, squareSize - padding * 2);
    }

    void drawOverlays(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        updateStates();
        UiTheme.drawTextButton(graphics, font, updateButton, updateButtonText(), mouseX, mouseY);

        OverlayConfig.Config config = config();
        UiTheme.drawTextButton(graphics, font, animationButton,
                animationName(config.customPlayerAnimation) + "  ▾", mouseX, mouseY);
        drawPatternButton(graphics, font, bodyPatternButton, config.customPlayerAnimation,
                PlayerPatternPart.BODY, config.customPlayerBodyPatternVariant, mouseX, mouseY);
        drawPatternButton(graphics, font, handsPatternButton, config.customPlayerAnimation,
                PlayerPatternPart.HANDS, config.customPlayerHandsPatternVariant, mouseX, mouseY);
        drawPatternButton(graphics, font, legsPatternButton, config.customPlayerAnimation,
                PlayerPatternPart.LEGS, config.customPlayerLegsPatternVariant, mouseX, mouseY);
    }

    private Button patternButton(int x, int y, int width, int height, PlayerPatternPart part) {
        return transparentButton(x, y, width, height, textComponent(patternLabelKey(part)), ignored -> {
            OverlayConfig.Config config = config();
            int maxVariant = maxVariant(config.customPlayerAnimation, part);
            if (maxVariant <= 0) {
                return;
            }

            int next = nextVariant(patternVariant(config, part), maxVariant);
            setPatternVariant(config, part, next);
            saveRegenerateAndRefresh();
        });
    }

    private static Button transparentButton(int x, int y, int width, int height,
                                            Component narration, Consumer<Button> action) {
        Button button = Button.builder(narration, clicked -> action.accept(clicked)).bounds(x, y, width, height).build();
        button.setAlpha(0.0F);
        return button;
    }

    private void drawPatternButton(GuiGraphicsExtractor graphics, Font font, Button button, String animation,
                                   PlayerPatternPart part, int variant, int mouseX, int mouseY) {
        int maxVariant = maxVariant(animation, part);
        if (maxVariant <= 0) {
            UiTheme.drawTextButton(graphics, font, button, "—", mouseX, mouseY);
            return;
        }

        int clamped = Math.max(1, Math.min(maxVariant, variant));
        UiTheme.drawTextButton(graphics, font, button,
                text("settings.pattern_variant_value") + " " + clamped + "  ▾", mouseX, mouseY);
    }

    private void updatePatternState(Button button, String animation, PlayerPatternPart part, int variant) {
        if (button == null) {
            return;
        }
        int maxVariant = maxVariant(animation, part);
        button.active = maxVariant > 0;
        String value = maxVariant > 0 ? String.valueOf(Math.max(1, Math.min(maxVariant, variant))) : "—";
        button.setMessage(Component.literal(text(patternLabelKey(part)) + ": " + value));
    }

    private static String patternLabelKey(PlayerPatternPart part) {
        return switch (part) {
            case BODY -> "settings.pattern_body";
            case HANDS -> "settings.pattern_hands";
            case LEGS -> "settings.pattern_legs";
        };
    }

    private static int patternVariant(OverlayConfig.Config config, PlayerPatternPart part) {
        return switch (part) {
            case BODY -> config.customPlayerBodyPatternVariant;
            case HANDS -> config.customPlayerHandsPatternVariant;
            case LEGS -> config.customPlayerLegsPatternVariant;
        };
    }

    private static void setPatternVariant(OverlayConfig.Config config, PlayerPatternPart part, int value) {
        switch (part) {
            case BODY -> config.customPlayerBodyPatternVariant = value;
            case HANDS -> config.customPlayerHandsPatternVariant = value;
            case LEGS -> config.customPlayerLegsPatternVariant = value;
        }
    }

    private static int nextVariant(int value, int maxVariant) {
        return value >= maxVariant ? 1 : value + 1;
    }

    private static int maxVariant(String animation, PlayerPatternPart part) {
        return PlayerAnimationSpecs.get(animation).maxVariant(part);
    }

    private String updateButtonText() {
        return switch (PlayerSkinManager.status()) {
            case EMPTY_NICKNAME -> text("settings.empty_nickname");
            case BUSY -> text("settings.busy");
            case LOADING -> text("settings.loading");
            case REGENERATING -> text("settings.regenerating");
            case NEED_SAVED_SKIN -> text("settings.need_saved_skin");
            case NOT_FOUND -> text("settings.not_found");
            case ERROR -> text("settings.skin_error");
            case IDLE -> text("settings.update_skin");
        };
    }

    private static String animationName(String animation) {
        return switch (animation) {
            case AnimationIds.FISHING -> text("settings.player_animation.fishing");
            case AnimationIds.SWIMMING -> text("settings.player_animation.swimming");
            case AnimationIds.CAMPFIRE -> text("settings.player_animation.campfire");
            case AnimationIds.MINECART -> text("settings.player_animation.minecart");
            case AnimationIds.GRAVE -> text("settings.player_animation.grave");
            default -> text("settings.player_animation.static");
        };
    }

    private OverlayConfig.Config config() {
        return configSupplier.get();
    }

    private void saveRegenerateAndRefresh() {
        OverlayConfig.save();
        PlayerSkinManager.regenerateFromSavedSkin();
        onChanged.run();
    }

    private static String text(String key) {
        return textComponent(key).getString();
    }

    private static Component textComponent(String key) {
        return Component.translatable(InventoryCompanionsClient.MOD_ID + "." + key);
    }
}
