package ru.irku.inventorycompanions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;


import static ru.irku.inventorycompanions.ConfigScreenLayout.*;

public final class OverlayConfigScreen extends Screen {
    private final Screen parent;
    private OverlayConfig.Config config;
    private Tab activeTab = Tab.MAIN;

    private Button mainTabButton;
    private Button playerSkinTabButton;
    private Button survivalTabButton;
    private Button creativeTabButton;
    private Button enabledButton;
    private Button animationButton;
    private Button animationArrowButton;
    private final CharacterListWidget characterList;
    private final PlayerSkinControls playerSkinControls;
    private OffsetSlider offsetXSlider;
    private OffsetSlider offsetYSlider;
    private Button offsetXResetButton;
    private Button offsetYResetButton;
    private Button doneButton;

    public OverlayConfigScreen(Screen parent) {
        super(Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.title"));
        this.parent = parent;
        this.characterList = new CharacterListWidget(
                this::getCharacterListPanelX,
                this::config,
                this::localizedAnimationName,
                key -> {
                    OverlayConfig.Config config = config();
                    config.selectedAnimation = key;
                    OverlayConfig.save();
                },
                this::rebuildConfigWidgets
        );
        this.playerSkinControls = new PlayerSkinControls(this::config, this::updateButtonLabels);
    }

    private static String text(String key) {
        return Component.translatable(InventoryCompanionsClient.MOD_ID + "." + key).getString();
    }

    @Override
    protected void init() {
        rebuildConfigWidgets();
    }

    private void rebuildConfigWidgets() {
        this.config = OverlayConfig.get();
        this.clearWidgets();
        clearWidgetReferences();

        this.characterList.clearButtons();
        this.characterList.refreshEntries();
        addTabButtons();

        switch (this.activeTab) {
            case MAIN -> {
                initMainTab();
                if (this.characterList.isOpen()) {
                    this.characterList.addButtons(button -> this.addRenderableWidget(button));
                }
            }
            case PLAYER_SKIN -> {
                this.playerSkinControls.init(this.font, this.width);
                this.playerSkinControls.addWidgets(
                        box -> this.addRenderableWidget(box),
                        button -> this.addRenderableWidget(button));
            }
            case SURVIVAL, CREATIVE -> initDisplayTab();
        }

        addBottomButtons();
        updateButtonLabels();
    }

    private void clearWidgetReferences() {
        this.mainTabButton = null;
        this.playerSkinTabButton = null;
        this.survivalTabButton = null;
        this.creativeTabButton = null;
        this.enabledButton = null;
        this.animationButton = null;
        this.animationArrowButton = null;
        this.offsetXSlider = null;
        this.offsetYSlider = null;
        this.offsetXResetButton = null;
        this.offsetYResetButton = null;
        this.doneButton = null;
        this.playerSkinControls.clear();
    }

    private void addTabButtons() {
        ConfigScreenLayout.TabStrip tabs = ConfigScreenLayout.tabs(this.width);

        this.mainTabButton = addTabButton(tabs.xFor(0), tabs, Tab.MAIN);
        this.playerSkinTabButton = addTabButton(tabs.xFor(1), tabs, Tab.PLAYER_SKIN);
        this.survivalTabButton = addTabButton(tabs.xFor(2), tabs, Tab.SURVIVAL);
        this.creativeTabButton = addTabButton(tabs.xFor(3), tabs, Tab.CREATIVE);
    }

    private Button addTabButton(int x, ConfigScreenLayout.TabStrip tabs, Tab tab) {
        Button button = this.addRenderableWidget(Button.builder(tabMessage(tab), ignored -> switchTab(tab))
                .bounds(x, tabs.y(), tabs.tabWidth(), tabs.height())
                .build());
        button.setAlpha(0.0F);
        return button;
    }

    private static Component tabMessage(Tab tab) {
        return Component.translatable(InventoryCompanionsClient.MOD_ID + switch (tab) {
            case MAIN -> ".settings.tab.main";
            case PLAYER_SKIN -> ".settings.tab.player_skin";
            case SURVIVAL -> ".settings.tab.survival";
            case CREATIVE -> ".settings.tab.creative_full";
        });
    }

    private void initMainTab() {
        int rowLeft = mainRowLeft();
        int rowWidth = mainRowWidth(this.width);
        int y = MAIN_ROW_TOP;

        int enabledWidth = Math.min(rowWidth, computeMainValueButtonWidth(currentEnabledText(), 120, 240));
        int enabledX = Math.max(rowLeft, rowLeft + rowWidth - enabledWidth);
        this.enabledButton = this.addRenderableWidget(Button.builder(
                Component.literal(text("settings.enabled") + ": " + currentEnabledText()),
                button -> {
                    OverlayConfig.Config config = config();
                    config.enabled = !config.enabled;
                    OverlayConfig.save();
                    rebuildConfigWidgets();
                }).bounds(enabledX, y, enabledWidth, MAIN_ROW_HEIGHT).build());
        this.enabledButton.setAlpha(0.0F);

        y += MAIN_ROW_GAP;
        int arrowWidth = Math.min(34, Math.max(24, rowWidth / 5));
        int animationWidth = Math.min(
                Math.max(1, rowWidth - arrowWidth),
                computeMainValueButtonWidth(currentAnimationText(), 100, 190));
        int selectorTotalWidth = animationWidth + arrowWidth;
        int selectorX = Math.max(rowLeft, rowLeft + rowWidth - selectorTotalWidth);

        this.animationButton = this.addRenderableWidget(Button.builder(
                Component.literal(text("settings.character") + ": " + currentAnimationText()),
                button -> {
                    cycleAnimation();
                    updateButtonLabels();
                }).bounds(selectorX, y, animationWidth, MAIN_ROW_HEIGHT).build());
        this.animationButton.setAlpha(0.0F);

        this.animationArrowButton = this.addRenderableWidget(Button.builder(
                Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.character"),
                button -> {
                    this.characterList.toggle();
                    rebuildConfigWidgets();
                }).bounds(selectorX + animationWidth, y, arrowWidth, MAIN_ROW_HEIGHT).build());
        this.animationArrowButton.setAlpha(0.0F);
    }

    private void initDisplayTab() {
        int rowLeft = DISPLAY_ROW_LEFT;
        int rowWidth = displayRowWidth(this.width);
        int labelWidth = displayLabelWidth(this.width);
        int sliderWidth = Math.max(30, rowWidth - labelWidth - DISPLAY_RESET_WIDTH - DISPLAY_CONTROL_GAP);
        int sliderX = rowLeft + labelWidth;
        int resetX = sliderX + sliderWidth + DISPLAY_CONTROL_GAP;
        int y = DISPLAY_ROW_TOP;

        this.offsetXSlider = this.addRenderableWidget(new OffsetSlider(sliderX, y, sliderWidth, DISPLAY_CONTROL_HEIGHT,
                text("settings.offset_x"),
                rowLeft,
                labelWidth,
                () -> activeTexture().offsetX,
                value -> activeTexture().offsetX = value));
        this.offsetXSlider.setAlpha(0.0F);

        this.offsetXResetButton = this.addRenderableWidget(Button.builder(
                Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.reset"),
                button -> {
                    activeTexture().offsetX = defaultTexture().offsetX;
                    OverlayConfig.save();
                    updateButtonLabels();
                }).bounds(resetX, y, DISPLAY_RESET_WIDTH, DISPLAY_CONTROL_HEIGHT).build());
        this.offsetXResetButton.setAlpha(0.0F);

        y += DISPLAY_ROW_GAP;
        this.offsetYSlider = this.addRenderableWidget(new OffsetSlider(sliderX, y, sliderWidth, DISPLAY_CONTROL_HEIGHT,
                text("settings.offset_y"),
                rowLeft,
                labelWidth,
                () -> activeTexture().offsetY,
                value -> activeTexture().offsetY = value));
        this.offsetYSlider.setAlpha(0.0F);

        this.offsetYResetButton = this.addRenderableWidget(Button.builder(
                Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.reset"),
                button -> {
                    activeTexture().offsetY = defaultTexture().offsetY;
                    OverlayConfig.save();
                    updateButtonLabels();
                }).bounds(resetX, y, DISPLAY_RESET_WIDTH, DISPLAY_CONTROL_HEIGHT).build());
        this.offsetYResetButton.setAlpha(0.0F);
    }

    private void addBottomButtons() {
        int bottomY = Math.max(TOP_TAB_Y + TOP_TAB_HEIGHT + 4, this.height - 32);
        int doneWidth = Math.max(80, Math.min(220, this.width - 16));
        int doneX = Math.max(0, (this.width - doneWidth) / 2);

        this.doneButton = this.addRenderableWidget(Button.builder(
                Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.done"),
                button -> {
                    OverlayConfig.save();
                    openParentScreen();
                }).bounds(doneX, bottomY, doneWidth, 22).build());
        this.doneButton.setAlpha(0.0F);
    }

    private void openParentScreen() {
        ScreenNavigationCompat.open(this.minecraft, this.parent);
    }

    private void switchTab(Tab tab) {
        if (this.activeTab != tab) {
            this.activeTab = tab;
            this.characterList.close();
            rebuildConfigWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        drawPageLayout(graphics);
        if (this.activeTab == Tab.SURVIVAL || this.activeTab == Tab.CREATIVE) {
            drawPreview(graphics);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (this.activeTab == Tab.MAIN) {
            drawMainValueOverlays(graphics, mouseX, mouseY);
            drawMainCharacterPreview(graphics);
            this.characterList.render(graphics, this.font, mouseX, mouseY);
        }

        if (this.activeTab == Tab.PLAYER_SKIN) {
            this.playerSkinControls.drawPreview(graphics, this.width, this.height);
            this.playerSkinControls.drawOverlays(graphics, this.font, mouseX, mouseY);
        }

        if (this.activeTab == Tab.SURVIVAL || this.activeTab == Tab.CREATIVE) {
            drawOffsetValueOverlays(graphics, mouseX, mouseY);
        }

        drawBottomButtonOverlay(graphics, mouseX, mouseY);
        drawTabBackgrounds(graphics, mouseX, mouseY);
    }

    private void drawPageLayout(GuiGraphicsExtractor graphics) {
        if (this.activeTab == Tab.MAIN) {
            drawMainLayout(graphics);
        } else if (this.activeTab == Tab.PLAYER_SKIN) {
            this.playerSkinControls.drawLayout(graphics, this.font, this.width, this.height);
        } else {
            drawDisplayLayout(graphics);
        }
    }

    private void drawTabBackgrounds(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ConfigScreenLayout.TabStrip tabs = ConfigScreenLayout.tabs(this.width);
        drawTab(graphics, tabs, 0, this.mainTabButton, Tab.MAIN, text("settings.tab.main"), mouseX, mouseY);
        drawTab(graphics, tabs, 1, this.playerSkinTabButton, Tab.PLAYER_SKIN, text("settings.tab.player_skin"), mouseX, mouseY);
        drawTab(graphics, tabs, 2, this.survivalTabButton, Tab.SURVIVAL, text("settings.tab.survival"), mouseX, mouseY);
        drawTab(graphics, tabs, 3, this.creativeTabButton, Tab.CREATIVE, text("settings.tab.creative_full"), mouseX, mouseY);
    }

    private void drawTab(GuiGraphicsExtractor graphics, ConfigScreenLayout.TabStrip tabs, int index,
                         Button button, Tab tab, String label, int mouseX, int mouseY) {
        UiTheme.drawTab(graphics, this.font, button, tabs.xFor(index), tabs.y(), tabs.tabWidth(), tabs.height(),
                this.activeTab == tab, label, mouseX, mouseY);
    }

    private void drawMainLayout(GuiGraphicsExtractor graphics) {
        int rowLeft = mainRowLeft();
        int rowWidth = mainRowWidth(this.width);
        int y = MAIN_ROW_TOP;

        drawMainRow(graphics, rowLeft, y, rowWidth, text("settings.enabled"));
        y += MAIN_ROW_GAP;
        drawMainRow(graphics, rowLeft, y, rowWidth, text("settings.character"));
    }

    private void drawMainCharacterPreview(GuiGraphicsExtractor graphics) {
        drawCharacterPreviewPanel(graphics, selected().survivalInventory);
    }

    private void drawCharacterPreviewPanel(GuiGraphicsExtractor graphics, OverlayConfig.TextureConfig textureConfig) {
        int panelY = 98;
        int panelLeft = mainRowLeft() + 36;
        int panelRight = getCharacterListPanelX() - 32;
        int panelBottom = this.height - 72;

        int availableWidth = panelRight - panelLeft;
        int availableHeight = panelBottom - panelY;
        if (availableWidth <= 40 || availableHeight <= 40) {
            return;
        }

        int panelSize = Math.min(availableWidth, availableHeight);
        panelSize = Math.max(180, panelSize);

        if (panelSize > availableWidth) {
            panelSize = availableWidth;
        }
        if (panelSize > availableHeight) {
            panelSize = availableHeight;
        }

        int panelX = panelLeft;
        int previewAreaHeight = panelBottom - panelY;
        int drawY = panelY + Math.max(0, (previewAreaHeight - panelSize) / 2);

        if (this.activeTab == Tab.PLAYER_SKIN) {
            OverlayConfig.Config config = config();
            String nickname = config.customPlayerNickname == null ? "" : config.customPlayerNickname;
            if (!nickname.isBlank()) {
                int nameWidth = this.font.width(nickname);
                int nameX = panelX + (panelSize - nameWidth) / 2;
                int nameY = drawY + 10;
                graphics.text(this.font, nickname, nameX, nameY, 0xFFFFFFFF, true);
            }
        }

        UiTheme.drawPanel(graphics, panelX, drawY, panelSize, panelSize, UiTheme.PANEL_BACKGROUND);

        int padding = Math.max(18, panelSize / 16);
        OverlayRenderer.drawCharacterPreview(graphics, textureConfig,
                panelX + padding, drawY + padding,
                panelSize - padding * 2, panelSize - padding * 2);
    }

    private int getCharacterListPanelX() {
        int rowLeft = mainRowLeft();
        int rowWidth = mainRowWidth(this.width);
        int panelWidth = this.characterList.panelWidth();
        int panelRightPadding = 0;
        int rightAlignedX = rowLeft + rowWidth - panelRightPadding - panelWidth;
        return Math.max(rowLeft, rightAlignedX);
    }

    private void drawDisplayLayout(GuiGraphicsExtractor graphics) {
        int rowLeft = DISPLAY_ROW_LEFT;
        int rowWidth = displayRowWidth(this.width);
        int y = DISPLAY_ROW_TOP;

        drawConfigRow(graphics, rowLeft, y, rowWidth);
        drawConfigRow(graphics, rowLeft, y + DISPLAY_ROW_GAP, rowWidth);
        drawPreviewPanel(graphics);
    }

    private void drawMainRow(GuiGraphicsExtractor graphics, int x, int y, int width, String label) {
        drawConfigRow(graphics, x, y, width, MAIN_ROW_HEIGHT);
        graphics.text(this.font, label, x + 14, y + (MAIN_ROW_HEIGHT - 8) / 2, 0xFFFFFFFF, true);
    }

    private void drawConfigRow(GuiGraphicsExtractor graphics, int x, int y, int width) {
        drawConfigRow(graphics, x, y, width, DISPLAY_CONTROL_HEIGHT);
    }

    private void drawConfigRow(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        UiTheme.drawPanel(graphics, x, y, width, height, UiTheme.PANEL_BACKGROUND_STRONG);
    }

    private void drawPreviewPanel(GuiGraphicsExtractor graphics) {
        ConfigScreenLayout.Rect panel = displayPreviewPanel(this.width);
        if (panel.width() > 40) {
            UiTheme.drawPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL_BACKGROUND);
        }
    }

    private void drawMainValueOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.enabledButton != null) {
            drawMainValueText(graphics, this.enabledButton, currentEnabledText(), currentEnabledColor(), mouseX, mouseY);
        }
        if (this.animationButton != null && this.animationArrowButton != null) {
            drawCharacterSelector(graphics, this.animationButton, this.animationArrowButton, currentAnimationText(), mouseX, mouseY);
        }
    }

    private void drawMainValueText(GuiGraphicsExtractor graphics, Button button, String text, int textColor, int mouseX, int mouseY) {
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        boolean hovered = UiTheme.isHovered(button, mouseX, mouseY);

        String displayText = UiText.ellipsize(this.font, text, Math.max(1, width - 16));
        int textX = x + width - this.font.width(displayText) - 8;
        int textY = y + (height - 8) / 2;
        int drawColor = hovered ? UiTheme.brighten(textColor, 24) : textColor;
        graphics.text(this.font, displayText, textX, textY, drawColor, true);
    }

    private void drawCharacterSelector(GuiGraphicsExtractor graphics, Button valueButton, Button arrowButton, String text, int mouseX, int mouseY) {
        int x = valueButton.getX();
        int y = valueButton.getY();
        int valueWidth = valueButton.getWidth();
        int arrowWidth = arrowButton.getWidth();
        int height = Math.max(valueButton.getHeight(), arrowButton.getHeight());
        int separatorX = arrowButton.getX();

        boolean valueHovered = UiTheme.isHovered(valueButton, mouseX, mouseY);
        boolean arrowHovered = UiTheme.isHovered(arrowButton, mouseX, mouseY);

        UiTheme.drawSegment(graphics, x, y, valueWidth, height, valueHovered, true, false);
        UiTheme.drawSegment(graphics, separatorX, y, arrowWidth, height, arrowHovered, false, true);

        int separatorColor = (valueHovered || arrowHovered) ? 0xFF8A8A9A : UiTheme.BORDER_LIGHT;
        graphics.fill(separatorX, y, separatorX + 1, y + height, separatorColor);

        int maxTextWidth = Math.max(20, valueWidth - 16);
        String displayText = UiText.ellipsize(this.font, text, maxTextWidth);
        int valueColor = valueHovered ? 0xFFFFFFFF : 0xFFE8E8E8;
        graphics.text(this.font, displayText,
                x + (valueWidth - this.font.width(displayText)) / 2,
                y + (height - 8) / 2,
                valueColor,
                true);

        String arrowText = this.characterList.isOpen() ? "∨" : "›";
        int arrowColor = arrowHovered ? 0xFFFFFFFF : 0xFFD6D6D6;
        graphics.text(this.font, arrowText,
                separatorX + (arrowWidth - this.font.width(arrowText)) / 2,
                y + (height - 8) / 2,
                arrowColor,
                true);
    }

    private int computeMainValueButtonWidth(String text, int minWidth, int maxWidth) {
        int width = this.font.width(text) + 40;
        if (width < minWidth) {
            width = minWidth;
        }
        if (width > maxWidth) {
            width = maxWidth;
        }
        return width;
    }

    private String localizedAnimationName(String key) {
        if (BuiltInCompanions.contains(key)
                || AnimationIds.PLAYER_SKIN.equals(key)
                || AnimationIds.RANDOM.equals(key)) {
            return text("animation." + key);
        }

        OverlayConfig.AnimationSet animation = config().animations.get(key);
        return animation == null ? key : animation.displayName;
    }

    private String currentEnabledText() {
        return config().enabled ? text("settings.enabled_on") : text("settings.enabled_off");
    }

    private int currentEnabledColor() {
        return config().enabled ? 0xFF55FF55 : 0xFFFF5555;
    }

    private String currentAnimationText() {
        return localizedAnimationName(config().selectedAnimation);
    }

    private void drawOffsetValueOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.offsetXSlider != null) {
            this.offsetXSlider.drawOverlay(graphics, this.font, mouseX, mouseY);
        }
        if (this.offsetYSlider != null) {
            this.offsetYSlider.drawOverlay(graphics, this.font, mouseX, mouseY);
        }
        if (this.offsetXResetButton != null) {
            UiTheme.drawIconButton(graphics, this.font, this.offsetXResetButton, "×", 0xFFFF5555, 0xFFFF7777, mouseX, mouseY);
        }
        if (this.offsetYResetButton != null) {
            UiTheme.drawIconButton(graphics, this.font, this.offsetYResetButton, "×", 0xFFFF5555, 0xFFFF7777, mouseX, mouseY);
        }
    }

    private void drawBottomButtonOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.doneButton != null) {
            UiTheme.drawTextButton(graphics, this.font, this.doneButton, text("settings.done"), mouseX, mouseY);
        }
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        boolean survival = this.activeTab == Tab.SURVIVAL;
        OverlayConfig.TextureConfig texture = survival ? selected().survivalInventory : selected().creativeInventory;
        ConfigPreviewRenderer.drawInventory(graphics, survival, texture, displayPreviewContent(this.width));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (this.activeTab == Tab.MAIN && this.characterList.mouseClicked(event)) {
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.activeTab == Tab.MAIN && this.characterList.mouseDragged(event)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.characterList.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeTab == Tab.MAIN && this.characterList.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        OverlayConfig.save();
        openParentScreen();
    }

    private OverlayConfig.Config config() {
        if (this.config == null) {
            this.config = OverlayConfig.get();
        }
        return this.config;
    }

    private OverlayConfig.AnimationSet selected() {
        return config().selected();
    }

    private OverlayConfig.TextureConfig activeTexture() {
        return this.activeTab == Tab.CREATIVE ? selected().creativeInventory : selected().survivalInventory;
    }

    private OverlayConfig.TextureConfig defaultTexture() {
        OverlayConfig.Config current = config();
        OverlayConfig.Config defaults = OverlayConfig.Config.defaults();
        if (AnimationIds.PLAYER_SKIN.equals(current.selectedAnimation)) {
            defaults.customPlayerAnimation = current.customPlayerAnimation;
            OverlayConfig.syncPlayerSkinAnimation(defaults);
        }
        OverlayConfig.AnimationSet animationSet = defaults.animations.get(current.selectedAnimation);
        if (animationSet == null) {
            animationSet = defaults.selected();
        }
        return this.activeTab == Tab.CREATIVE ? animationSet.creativeInventory : animationSet.survivalInventory;
    }

    private void cycleAnimation() {
        OverlayConfig.Config config = config();
        List<String> keys = this.characterList.animationKeys();
        if (keys.isEmpty()) {
            return;
        }

        int currentIndex = keys.indexOf(config.selectedAnimation);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % keys.size();
        config.selectedAnimation = keys.get(nextIndex);
        OverlayConfig.save();
        rebuildConfigWidgets();
    }

    private void updateButtonLabels() {
        updateTabButtonStates();
        updateMainLabels();
        this.playerSkinControls.updateStates();
        updateDisplayLabels();
    }

    private void updateTabButtonStates() {
        if (this.mainTabButton != null) {
            this.mainTabButton.active = this.activeTab != Tab.MAIN;
        }
        if (this.playerSkinTabButton != null) {
            this.playerSkinTabButton.active = this.activeTab != Tab.PLAYER_SKIN;
        }
        if (this.survivalTabButton != null) {
            this.survivalTabButton.active = this.activeTab != Tab.SURVIVAL;
        }
        if (this.creativeTabButton != null) {
            this.creativeTabButton.active = this.activeTab != Tab.CREATIVE;
        }
    }

    private void updateMainLabels() {
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(Component.literal(text("settings.enabled") + ": " + currentEnabledText()));
        }
        if (this.animationButton != null) {
            this.animationButton.setMessage(Component.literal(text("settings.character") + ": " + currentAnimationText()));
        }
        if (this.animationArrowButton != null) {
            this.animationArrowButton.setMessage(Component.literal(text("settings.character") + ": " + currentAnimationText()));
        }
    }

    private void updateDisplayLabels() {
        if (this.offsetXResetButton != null) {
            this.offsetXResetButton.setMessage(Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.reset"));
        }
        if (this.offsetYResetButton != null) {
            this.offsetYResetButton.setMessage(Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.reset"));
        }
        if (this.doneButton != null) {
            this.doneButton.setMessage(Component.translatable(InventoryCompanionsClient.MOD_ID + ".settings.done"));
        }
        if (this.offsetXSlider != null) {
            this.offsetXSlider.refreshFromConfig();
        }
        if (this.offsetYSlider != null) {
            this.offsetYSlider.refreshFromConfig();
        }
    }

    private enum Tab {
        MAIN,
        PLAYER_SKIN,
        SURVIVAL,
        CREATIVE
    }

}

