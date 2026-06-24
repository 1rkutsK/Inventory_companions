package ru.irku.inventorycompanions;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class OverlayConfigScreen extends Screen {
    private static final int OFFSET_MIN = -300;
    private static final int OFFSET_MAX = 300;

    private static final int MAIN_ROW_MARGIN = 18;
    private static final int MAIN_ROW_TOP = 40;
    private static final int MAIN_ROW_HEIGHT = 24;
    private static final int MAIN_ROW_GAP = 30;
    private static final int MAIN_VALUE_BUTTON_HEIGHT = MAIN_ROW_HEIGHT;
    private static final int TOP_TAB_MARGIN = 20;
    private static final int TOP_TAB_GAP = 6;
    private static final int TOP_TAB_HEIGHT = 26;

    private static final Identifier SURVIVAL_PREVIEW_TEXTURE = Identifier.parse("minecraft:textures/gui/container/inventory.png");
    private static final Identifier CREATIVE_PREVIEW_TEXTURE = Identifier.parse("minecraft:textures/gui/container/creative_inventory/tab_inventory.png");

    private final Screen parent;
    private Tab activeTab = Tab.MAIN;

    private Button mainTabButton;
    private Button survivalTabButton;
    private Button creativeTabButton;
    private Button enabledButton;
    private Button animationButton;
    private Button animationArrowButton;
    private final List<Button> characterListButtons = new ArrayList<>();
    private final List<String> visibleCharacterKeys = new ArrayList<>();
    private boolean characterListOpen = false;
    private int characterListScroll = 0;
    private OffsetSlider offsetXSlider;
    private OffsetSlider offsetYSlider;
    private Button offsetXResetButton;
    private Button offsetYResetButton;
    private Button doneButton;

    public OverlayConfigScreen(Screen parent) {
        super(Component.translatable("inventory_companions.settings.title"));
        this.parent = parent;
    }

    private static String text(String key) {
        return Component.translatable("inventory_companions." + key).getString();
    }

    @Override
    protected void init() {
        rebuildConfigWidgets();
    }

    private void rebuildConfigWidgets() {
        this.clearWidgets();

        this.mainTabButton = null;
        this.survivalTabButton = null;
        this.creativeTabButton = null;
        this.enabledButton = null;
        this.animationButton = null;
        this.animationArrowButton = null;
        this.characterListButtons.clear();
        this.visibleCharacterKeys.clear();
        this.offsetXSlider = null;
        this.offsetYSlider = null;
        this.offsetXResetButton = null;
        this.offsetYResetButton = null;
        this.doneButton = null;

        addTabButtons();

        if (this.activeTab == Tab.MAIN) {
            initMainTab();
            if (this.characterListOpen) {
                addCharacterListButtons();
            }
        } else {
            initDisplayTab();
        }

        addBottomButtons();
        updateButtonLabels();
    }

    private void addTabButtons() {
        int tabGap = TOP_TAB_GAP;
        int tabX = TOP_TAB_MARGIN;
        int tabY = 6;
        int totalWidth = Math.max(300, this.width - TOP_TAB_MARGIN * 2);
        int tabWidth = Math.max(120, (totalWidth - tabGap * 2) / 3);

        this.mainTabButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> switchTab(Tab.MAIN))
                .bounds(tabX, tabY, tabWidth, TOP_TAB_HEIGHT)
                .build());
        this.mainTabButton.setAlpha(0.0F);

        this.survivalTabButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> switchTab(Tab.SURVIVAL))
                .bounds(tabX + tabWidth + tabGap, tabY, tabWidth, TOP_TAB_HEIGHT)
                .build());
        this.survivalTabButton.setAlpha(0.0F);

        this.creativeTabButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> switchTab(Tab.CREATIVE))
                .bounds(tabX + (tabWidth + tabGap) * 2, tabY, tabWidth, TOP_TAB_HEIGHT)
                .build());
        this.creativeTabButton.setAlpha(0.0F);
    }

    private void initMainTab() {
        int rowLeft = getMainRowLeft();
        int rowWidth = getMainRowWidth();
        int rightPadding = 10;
        int y = MAIN_ROW_TOP;
        int buttonY = y;

        int enabledWidth = computeMainValueButtonWidth(currentEnabledText(), 180, 240);
        int enabledX = rowLeft + rowWidth - enabledWidth - rightPadding;
        this.enabledButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            OverlayConfig.get().enabled = !OverlayConfig.get().enabled;
            OverlayConfig.save();
            rebuildConfigWidgets();
        }).bounds(enabledX, buttonY, enabledWidth, MAIN_VALUE_BUTTON_HEIGHT).build());
        this.enabledButton.setAlpha(0.0F);

        y += MAIN_ROW_GAP;
        buttonY = y;
        int arrowWidth = 34;
        int animationWidth = computeMainValueButtonWidth(currentAnimationText(), 120, 190);
        int selectorTotalWidth = animationWidth + arrowWidth;
        int selectorRightPadding = 0;
        int selectorX = rowLeft + rowWidth - selectorRightPadding - selectorTotalWidth;
        selectorX = Math.max(selectorX, rowLeft + 150);
        this.animationButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            cycleAnimation();
            updateButtonLabels();
        }).bounds(selectorX, buttonY, animationWidth, MAIN_VALUE_BUTTON_HEIGHT).build());
        this.animationButton.setAlpha(0.0F);

        this.animationArrowButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.characterListOpen = !this.characterListOpen;
            rebuildConfigWidgets();
        }).bounds(selectorX + animationWidth, buttonY, arrowWidth, MAIN_VALUE_BUTTON_HEIGHT).build());
        this.animationArrowButton.setAlpha(0.0F);
    }

    private void addCharacterListButtons() {
        List<String> keys = getAnimationKeys();
        int maxScroll = Math.max(0, keys.size() - getCharacterListVisibleRows());
        this.characterListScroll = clamp(this.characterListScroll, 0, maxScroll);

        int panelX = getCharacterListPanelX();
        int panelY = getCharacterListPanelY() + 8;
        int panelWidth = getCharacterListPanelWidth();
        int rowHeight = 25;
        int innerWidth = panelWidth - 16;

        int end = Math.min(keys.size(), this.characterListScroll + getCharacterListVisibleRows());
        for (int i = this.characterListScroll; i < end; i++) {
            final String key = keys.get(i);
            int rowY = panelY + (i - this.characterListScroll) * rowHeight;
            Button entry = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                OverlayConfig.get().selectedAnimation = key;
                OverlayConfig.save();
                this.characterListOpen = false;
                rebuildConfigWidgets();
            }).bounds(panelX + 8, rowY, innerWidth, 18).build());
            entry.setAlpha(0.0F);
            this.characterListButtons.add(entry);
            this.visibleCharacterKeys.add(key);
        }
    }

    private void initDisplayTab() {
        int rowLeft = 8;
        int rowWidth = Math.min(840, this.width - 280);
        int resetWidth = 28;
        int gap = 6;
        int labelWidth = 140;
        int sliderWidth = rowWidth - labelWidth - resetWidth - gap;
        int sliderX = rowLeft + labelWidth;
        int resetX = sliderX + sliderWidth + gap;
        int y = 56;

        this.offsetXSlider = this.addRenderableWidget(new OffsetSlider(sliderX, y, sliderWidth, 22,
                text("settings.offset_x"),
                rowLeft,
                labelWidth,
                () -> activeTexture().offsetX,
                value -> activeTexture().offsetX = value));
        this.offsetXSlider.setAlpha(0.0F);

        this.offsetXResetButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            activeTexture().offsetX = defaultTexture().offsetX;
            OverlayConfig.save();
            updateButtonLabels();
        }).bounds(resetX, y, resetWidth, 22).build());
        this.offsetXResetButton.setAlpha(0.0F);

        y += 34;
        this.offsetYSlider = this.addRenderableWidget(new OffsetSlider(sliderX, y, sliderWidth, 22,
                text("settings.offset_y"),
                rowLeft,
                labelWidth,
                () -> activeTexture().offsetY,
                value -> activeTexture().offsetY = value));
        this.offsetYSlider.setAlpha(0.0F);

        this.offsetYResetButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            activeTexture().offsetY = defaultTexture().offsetY;
            OverlayConfig.save();
            updateButtonLabels();
        }).bounds(resetX, y, resetWidth, 22).build());
        this.offsetYResetButton.setAlpha(0.0F);
    }

    private void addBottomButtons() {
        int bottomY = this.height - 32;
        int doneWidth = 220;
        int doneX = (this.width - doneWidth) / 2;

        this.doneButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            OverlayConfig.save();
            openParentScreen();
        }).bounds(doneX, bottomY, doneWidth, 22).build());
        this.doneButton.setAlpha(0.0F);
    }

    private void openParentScreen() {
        setScreenCompat(this.parent);
    }

    private void setScreenCompat(Screen target) {
        if (this.minecraft == null) {
            return;
        }

        if (setScreenViaGui(target)) {
            return;
        }

        if (invokeScreenMethod(this.minecraft,
                "net.minecraft.client.Minecraft",
                "setScreen",
                "(Lnet/minecraft/client/gui/screens/Screen;)V",
                target)) {
            return;
        }

        throw new IllegalStateException("Unable to switch Minecraft screen on this game version");
    }

    private boolean setScreenViaGui(Screen target) {
        Object gui = getFieldValue(this.minecraft,
                "net.minecraft.client.Minecraft",
                "gui",
                "Lnet/minecraft/client/gui/Gui;");

        if (gui != null && invokeScreenMethod(gui,
                "net.minecraft.client.gui.Gui",
                "setScreen",
                "(Lnet/minecraft/client/gui/screens/Screen;)V",
                target)) {
            return true;
        }

        return tryAnyScreenHolderField(this.minecraft, target);
    }

    private static boolean tryAnyScreenHolderField(Object owner, Screen target) {
        Class<?> type = owner.getClass();
        while (type != null) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value != null && invokeAnyScreenMethod(value, target)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static Object getFieldValue(Object owner, String namedOwner, String namedField, String namedDescriptor) {
        for (String fieldName : runtimeFieldNames(namedOwner, namedField, namedDescriptor)) {
            Class<?> type = owner.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(owner);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        return null;
    }

    private static boolean invokeScreenMethod(Object owner, String namedOwner, String namedMethod, String namedDescriptor, Screen target) {
        for (String methodName : runtimeMethodNames(namedOwner, namedMethod, namedDescriptor)) {
            if (invokeMethodByName(owner, methodName, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean invokeAnyScreenMethod(Object owner, Screen target) {
        for (String methodName : List.of("setScreen", "method_1507")) {
            if (invokeMethodByName(owner, methodName, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean invokeMethodByName(Object owner, String methodName, Screen target) {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName, Screen.class);
                method.setAccessible(true);
                method.invoke(owner, target);
                return true;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static List<String> runtimeFieldNames(String namedOwner, String namedField, String namedDescriptor) {
        List<String> names = new ArrayList<>();
        addUnique(names, mapFieldName(namedOwner, namedField, namedDescriptor));
        addUnique(names, namedField);
        return names;
    }

    private static List<String> runtimeMethodNames(String namedOwner, String namedMethod, String namedDescriptor) {
        List<String> names = new ArrayList<>();
        addUnique(names, mapMethodName(namedOwner, namedMethod, namedDescriptor));
        addUnique(names, namedMethod);
        return names;
    }

    private static String mapFieldName(String namedOwner, String namedField, String namedDescriptor) {
        try {
            return FabricLoader.getInstance().getMappingResolver().mapFieldName("named", namedOwner, namedField, namedDescriptor);
        } catch (Throwable ignored) {
            return namedField;
        }
    }

    private static String mapMethodName(String namedOwner, String namedMethod, String namedDescriptor) {
        try {
            return FabricLoader.getInstance().getMappingResolver().mapMethodName("named", namedOwner, namedMethod, namedDescriptor);
        } catch (Throwable ignored) {
            return namedMethod;
        }
    }

    private static void addUnique(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    private void switchTab(Tab tab) {
        if (this.activeTab != tab) {
            this.activeTab = tab;
            this.characterListOpen = false;
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
            drawCharacterListPanel(graphics, mouseX, mouseY);
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
        } else {
            drawDisplayLayout(graphics);
        }
    }

    private void drawTabBackgrounds(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int tabGap = TOP_TAB_GAP;
        int tabX = TOP_TAB_MARGIN;
        int tabY = 6;
        int totalWidth = Math.max(300, this.width - TOP_TAB_MARGIN * 2);
        int tabWidth = Math.max(120, (totalWidth - tabGap * 2) / 3);

        drawTabBackground(graphics, this.mainTabButton, tabX, tabY, tabWidth, TOP_TAB_HEIGHT, this.activeTab == Tab.MAIN, text("settings.tab.main"), mouseX, mouseY);
        drawTabBackground(graphics, this.survivalTabButton, tabX + tabWidth + tabGap, tabY, tabWidth, TOP_TAB_HEIGHT, this.activeTab == Tab.SURVIVAL, text("settings.tab.survival"), mouseX, mouseY);
        drawTabBackground(graphics, this.creativeTabButton, tabX + (tabWidth + tabGap) * 2, tabY, tabWidth, TOP_TAB_HEIGHT, this.activeTab == Tab.CREATIVE, text("settings.tab.creative_full"), mouseX, mouseY);
    }

    private void drawTabBackground(GuiGraphicsExtractor graphics, Button button, int x, int y, int width, int height, boolean selected, String text, int mouseX, int mouseY) {
        boolean hovered = isHovered(button, mouseX, mouseY);
        int fill = selected ? 0x82000000 : hovered ? 0x70000000 : 0x5E000000;
        int top = selected ? 0xFFD4D4D4 : hovered ? 0xFFB9B9B9 : 0xFF6A6A6A;
        int bottom = selected ? 0xFFB8B8B8 : hovered ? 0xFF8A8A8A : 0xFF4B4B4B;
        int side = selected ? 0xFFC8C8C8 : hovered ? 0xFFA6A6A6 : 0xFF5C5C5C;

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, top);
        graphics.fill(x, y + height - 1, x + width, y + height, bottom);
        graphics.fill(x, y, x + 1, y + height, side);
        graphics.fill(x + width - 1, y, x + width, y + height, side);

        if (selected) {
            int underlineWidth = Math.min(132, Math.max(96, width / 3));
            int underlineX = x + (width - underlineWidth) / 2;
            graphics.fill(underlineX, y + height - 2, underlineX + underlineWidth, y + height, 0xFFF2F2F2);
        }

        int color = selected ? 0xFFFFFFFF : hovered ? 0xFFF1F1F1 : 0xFFE4E4E4;
        int textWidth = getCrispBoldTextWidth(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + 8;
        drawCrispBoldText(graphics, text, textX, textY, color);
    }

    private int getCrispBoldTextWidth(String text) {
        int width = 0;
        boolean hasGlyph = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            width += this.font.width(glyph) + 1;
            hasGlyph = true;
            offset += Character.charCount(codePoint);
        }
        return hasGlyph ? Math.max(0, width - 1) : 0;
    }

    private void drawCrispBoldText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        int drawX = x;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            int glyphWidth = this.font.width(glyph);

            graphics.text(this.font, glyph, drawX, y, color, false);
            if (!Character.isWhitespace(codePoint) && glyphWidth > 0) {
                graphics.text(this.font, glyph, drawX + 1, y, color, false);
            }

            drawX += glyphWidth + 1;
            offset += Character.charCount(codePoint);
        }
    }

    private void drawMainLayout(GuiGraphicsExtractor graphics) {
        int rowLeft = getMainRowLeft();
        int rowWidth = getMainRowWidth();
        int y = MAIN_ROW_TOP;

        drawMainRow(graphics, rowLeft, y, rowWidth, text("settings.enabled"));
        y += MAIN_ROW_GAP;
        drawMainRow(graphics, rowLeft, y, rowWidth, text("settings.character"));
    }

    private int getMainRowLeft() {
        return Math.max(8, MAIN_ROW_MARGIN);
    }

    private int getMainRowWidth() {
        return Math.max(180, Math.min(980, this.width - getMainRowLeft() * 2));
    }

    private void drawMainCharacterPreview(GuiGraphicsExtractor graphics) {
        int panelY = 98;
        int panelLeft = getMainRowLeft() + 36;
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

        graphics.fill(panelX, drawY, panelX + panelSize, drawY + panelSize, 0x72000000);
        graphics.fill(panelX, drawY, panelX + panelSize, drawY + 1, 0xFF5E5E6D);
        graphics.fill(panelX, drawY + panelSize - 1, panelX + panelSize, drawY + panelSize, 0xFF272735);
        graphics.fill(panelX, drawY, panelX + 1, drawY + panelSize, 0xFF5E5E6D);
        graphics.fill(panelX + panelSize - 1, drawY, panelX + panelSize, drawY + panelSize, 0xFF272735);

        int padding = Math.max(18, panelSize / 16);
        OverlayRenderer.drawCharacterPreview(graphics, selected().survivalInventory,
                panelX + padding, drawY + padding,
                panelSize - padding * 2, panelSize - padding * 2);
    }

    private int getCharacterListPanelX() {
        int rowLeft = getMainRowLeft();
        int rowWidth = getMainRowWidth();
        int panelWidth = getCharacterListPanelWidth();
        int panelRightPadding = 0;
        int rightAlignedX = rowLeft + rowWidth - panelRightPadding - panelWidth;
        return Math.max(rowLeft, rightAlignedX);
    }

    private int getCharacterListPanelY() {
        return 108;
    }

    private int getCharacterListPanelWidth() {
        return 240;
    }

    private int getCharacterListVisibleRows() {
        return Math.max(1, Math.min(10, OverlayConfig.get().animations.size()));
    }

    private int getCharacterListPanelHeight() {
        return 16 + getCharacterListVisibleRows() * 25;
    }

    private List<String> getAnimationKeys() {
        List<String> keys = new ArrayList<>(OverlayConfig.get().animations.keySet());
        keys.sort((left, right) -> {
            OverlayConfig.AnimationSet leftSet = OverlayConfig.get().animations.get(left);
            OverlayConfig.AnimationSet rightSet = OverlayConfig.get().animations.get(right);
            String leftName = leftSet == null ? left : leftSet.displayName;
            String rightName = rightSet == null ? right : rightSet.displayName;
            return leftName.compareToIgnoreCase(rightName);
        });
        return keys;
    }

    private boolean isMouseOverCharacterList(double mouseX, double mouseY) {
        int x = getCharacterListPanelX();
        int y = getCharacterListPanelY();
        int width = getCharacterListPanelWidth();
        int height = getCharacterListPanelHeight();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawCharacterListPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.characterListOpen) {
            return;
        }

        int panelX = getCharacterListPanelX();
        int panelY = getCharacterListPanelY();
        int panelWidth = getCharacterListPanelWidth();
        int panelHeight = getCharacterListPanelHeight();

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x86000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF5E5E6D);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF272735);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF5E5E6D);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF272735);

        for (int i = 0; i < this.characterListButtons.size() && i < this.visibleCharacterKeys.size(); i++) {
            Button button = this.characterListButtons.get(i);
            String key = this.visibleCharacterKeys.get(i);
            OverlayConfig.AnimationSet animationSet = OverlayConfig.get().animations.get(key);
            String text = animationSet == null ? key : animationSet.displayName;
            boolean hovered = isHovered(button, mouseX, mouseY);
            boolean selected = key.equals(OverlayConfig.get().selectedAnimation);

            int x = button.getX();
            int y = button.getY();
            int w = button.getWidth();
            int h = button.getHeight();
            int fill = selected ? 0x5AFFFFFF : hovered ? 0x34000000 : 0x22000000;
            int edge = selected ? 0xFFD0D0D0 : hovered ? 0xFF7A7A8A : 0x00000000;
            if (fill != 0) {
                graphics.fill(x, y, x + w, y + h, fill);
            }
            if (edge != 0) {
                graphics.fill(x, y, x + w, y + 1, edge);
                graphics.fill(x, y + h - 1, x + w, y + h, edge);
            }

            int color = selected ? 0xFFFFFFFF : hovered ? 0xFFEAEAEA : 0xFFD0D0D0;
            int textY = y + (h - 8) / 2;
            graphics.text(this.font, ellipsize(this.font, text, w - 12), x + 6, textY, color, true);
        }
    }

    private void drawDisplayLayout(GuiGraphicsExtractor graphics) {
        int rowLeft = 8;
        int rowWidth = Math.min(840, this.width - 280);
        int y = 56;

        drawConfigRow(graphics, rowLeft, y, rowWidth);
        drawConfigRow(graphics, rowLeft, y + 34, rowWidth);
        drawPreviewPanel(graphics);
    }

    private void drawMainRow(GuiGraphicsExtractor graphics, int x, int y, int width, String label) {
        drawConfigRow(graphics, x, y, width, MAIN_ROW_HEIGHT);
        graphics.text(this.font, label, x + 14, y + (MAIN_ROW_HEIGHT - 8) / 2, 0xFFFFFFFF, true);
    }

    private void drawConfigRow(GuiGraphicsExtractor graphics, int x, int y, int width) {
        drawConfigRow(graphics, x, y, width, 22);
    }

    private void drawConfigRow(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int fill = 0x86000000;
        int top = 0xFF5E5E6D;
        int bottom = 0xFF272735;

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, top);
        graphics.fill(x, y + height - 1, x + width, y + height, bottom);
        graphics.fill(x, y, x + 1, y + height, top);
        graphics.fill(x + width - 1, y, x + width, y + height, bottom);
    }

    private void drawPreviewPanel(GuiGraphicsExtractor graphics) {
        int rowLeft = 8;
        int rowWidth = Math.min(840, this.width - 280);
        int panelX = rowLeft + rowWidth + 18;
        int panelY = 56;
        int panelWidth = this.width - (rowLeft + rowWidth + 26);
        int panelHeight = 210;

        if (panelWidth <= 40) {
            return;
        }

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x72000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF5E5E6D);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF272735);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF5E5E6D);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF272735);
    }

    private void drawMainValueOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.enabledButton != null) {
            drawMainValueText(graphics, this.enabledButton, currentEnabledText(), currentEnabledColor(), false, mouseX, mouseY);
        }
        if (this.animationButton != null && this.animationArrowButton != null) {
            drawCharacterSelector(graphics, this.animationButton, this.animationArrowButton, currentAnimationText(), mouseX, mouseY);
        } else if (this.animationButton != null) {
            drawCharacterValueButton(graphics, this.animationButton, currentAnimationText(), mouseX, mouseY);
        } else if (this.animationArrowButton != null) {
            drawCharacterDropdownButton(graphics, this.animationArrowButton, mouseX, mouseY);
        }
    }

    private void drawMainValueText(GuiGraphicsExtractor graphics, Button button, String text, int textColor, boolean showArrow, int mouseX, int mouseY) {
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        boolean hovered = isHovered(button, mouseX, mouseY);

        int arrowPadding = showArrow ? 14 : 0;
        int arrowWidth = showArrow ? 12 : 0;
        int textX = x + width - this.font.width(text) - 8 - arrowPadding;
        int textY = y + (height - 8) / 2;
        int drawColor = hovered ? brightenColor(textColor, 24) : textColor;
        graphics.text(this.font, text, textX, textY, drawColor, true);

        if (showArrow) {
            graphics.text(this.font, "›", x + width - arrowWidth, textY, hovered ? 0xFFFFFFFF : 0xFFD6D6D6, true);
        }
    }

    private void drawCharacterDropdownButton(GuiGraphicsExtractor graphics, Button button, int mouseX, int mouseY) {
        drawButtonBox(graphics, button, mouseX, mouseY);
        String arrowText = this.characterListOpen ? "∨" : "›";
        int color = isHovered(button, mouseX, mouseY) ? 0xFFFFFFFF : 0xFFD6D6D6;
        graphics.text(this.font, arrowText,
                button.getX() + (button.getWidth() - this.font.width(arrowText)) / 2,
                button.getY() + (button.getHeight() - 8) / 2,
                color,
                true);
    }

    private void drawCharacterSelector(GuiGraphicsExtractor graphics, Button valueButton, Button arrowButton, String text, int mouseX, int mouseY) {
        int x = valueButton.getX();
        int y = valueButton.getY();
        int valueWidth = valueButton.getWidth();
        int arrowWidth = arrowButton.getWidth();
        int height = Math.max(valueButton.getHeight(), arrowButton.getHeight());
        int separatorX = arrowButton.getX();

        boolean valueHovered = isHovered(valueButton, mouseX, mouseY);
        boolean arrowHovered = isHovered(arrowButton, mouseX, mouseY);

        drawSegmentButtonBox(graphics, x, y, valueWidth, height, valueHovered, true, false);
        drawSegmentButtonBox(graphics, separatorX, y, arrowWidth, height, arrowHovered, false, true);

        int separatorColor = (valueHovered || arrowHovered) ? 0xFF8A8A9A : 0xFF5E5E6D;
        graphics.fill(separatorX, y, separatorX + 1, y + height, separatorColor);

        int maxTextWidth = Math.max(20, valueWidth - 16);
        String displayText = ellipsize(this.font, text, maxTextWidth);
        int valueColor = valueHovered ? 0xFFFFFFFF : 0xFFE8E8E8;
        graphics.text(this.font, displayText,
                x + (valueWidth - this.font.width(displayText)) / 2,
                y + (height - 8) / 2,
                valueColor,
                true);

        String arrowText = this.characterListOpen ? "∨" : "›";
        int arrowColor = arrowHovered ? 0xFFFFFFFF : 0xFFD6D6D6;
        graphics.text(this.font, arrowText,
                separatorX + (arrowWidth - this.font.width(arrowText)) / 2,
                y + (height - 8) / 2,
                arrowColor,
                true);
    }

    private void drawCharacterValueButton(GuiGraphicsExtractor graphics, Button button, String text, int mouseX, int mouseY) {
        drawButtonBox(graphics, button, mouseX, mouseY);
        int maxTextWidth = Math.max(20, button.getWidth() - 16);
        String displayText = ellipsize(this.font, text, maxTextWidth);
        int color = isHovered(button, mouseX, mouseY) ? 0xFFFFFFFF : 0xFFE8E8E8;
        graphics.text(this.font, displayText,
                button.getX() + (button.getWidth() - this.font.width(displayText)) / 2,
                button.getY() + (button.getHeight() - 8) / 2,
                color,
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

    private int brightenColor(int color, int amount) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String currentEnabledText() {
        return OverlayConfig.get().enabled ? text("settings.enabled_on") : text("settings.enabled_off");
    }

    private int currentEnabledColor() {
        return OverlayConfig.get().enabled ? 0xFF55FF55 : 0xFFFF5555;
    }

    private String currentAnimationText() {
        return OverlayConfig.get().selected().displayName;
    }

    private void drawOffsetValueOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.offsetXSlider != null) {
            this.offsetXSlider.drawOverlay(graphics, this.font, mouseX, mouseY);
        }
        if (this.offsetYSlider != null) {
            this.offsetYSlider.drawOverlay(graphics, this.font, mouseX, mouseY);
        }
        if (this.offsetXResetButton != null) {
            drawIconButton(graphics, this.offsetXResetButton, "×", 0xFFFF5555, 0xFFFF7777, mouseX, mouseY);
        }
        if (this.offsetYResetButton != null) {
            drawIconButton(graphics, this.offsetYResetButton, "×", 0xFFFF5555, 0xFFFF7777, mouseX, mouseY);
        }
    }

    private void drawBottomButtonOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.doneButton != null) {
            drawTextButton(graphics, this.doneButton, text("settings.done"), mouseX, mouseY);
        }
    }

    private void drawTextButton(GuiGraphicsExtractor graphics, Button button, String text, int mouseX, int mouseY) {
        drawButtonBox(graphics, button, mouseX, mouseY);
        graphics.text(this.font, text, button.getX() + (button.getWidth() - this.font.width(text)) / 2, button.getY() + (button.getHeight() - 8) / 2, 0xFFFFFFFF, true);
    }

    private void drawIconButton(GuiGraphicsExtractor graphics, Button button, String text, int mouseX, int mouseY) {
        drawIconButton(graphics, button, text, 0xFFE8E8E8, 0xFFFFFFFF, mouseX, mouseY);
    }

    private void drawIconButton(GuiGraphicsExtractor graphics, Button button, String text, int normalColor, int hoverColor, int mouseX, int mouseY) {
        drawButtonBox(graphics, button, mouseX, mouseY);
        int color = isHovered(button, mouseX, mouseY) ? hoverColor : normalColor;
        graphics.text(this.font, text, button.getX() + (button.getWidth() - this.font.width(text)) / 2, button.getY() + (button.getHeight() - 8) / 2, color, true);
    }

    private void drawButtonBox(GuiGraphicsExtractor graphics, Button button, int mouseX, int mouseY) {
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        boolean hovered = isHovered(button, mouseX, mouseY);

        int fill = hovered ? 0x9B000000 : 0x82000000;
        int top = hovered ? 0xFF8A8A9A : 0xFF5E5E6D;
        int bottom = hovered ? 0xFF424255 : 0xFF272735;

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, top);
        graphics.fill(x, y + height - 1, x + width, y + height, bottom);
        graphics.fill(x, y, x + 1, y + height, top);
        graphics.fill(x + width - 1, y, x + width, y + height, bottom);
    }

    private void drawSegmentButtonBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean hovered, boolean drawLeftBorder, boolean drawRightBorder) {
        int fill = hovered ? 0x9B000000 : 0x82000000;
        int top = hovered ? 0xFF8A8A9A : 0xFF5E5E6D;
        int bottom = hovered ? 0xFF424255 : 0xFF272735;

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, top);
        graphics.fill(x, y + height - 1, x + width, y + height, bottom);
        if (drawLeftBorder) {
            graphics.fill(x, y, x + 1, y + height, top);
        }
        if (drawRightBorder) {
            graphics.fill(x + width - 1, y, x + width, y + height, bottom);
        }
    }

    private boolean isHovered(Button button, int mouseX, int mouseY) {
        return button != null
                && button.active
                && mouseX >= button.getX()
                && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY()
                && mouseY < button.getY() + button.getHeight();
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        int rowLeft = 8;
        int rowWidth = Math.min(840, this.width - 280);
        int panelX = rowLeft + rowWidth + 18;
        int panelY = 78;
        int panelWidth = this.width - (rowLeft + rowWidth + 26);
        int panelHeight = 170;

        if (panelWidth <= 40) {
            return;
        }

        boolean survival = this.activeTab == Tab.SURVIVAL;
        int previewWidth = survival ? 176 : 195;
        int previewHeight = survival ? 166 : 136;
        int previewX = panelX + (panelWidth - previewWidth) / 2;
        int previewY = panelY + (panelHeight - previewHeight) / 2;

        if (survival) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SURVIVAL_PREVIEW_TEXTURE,
                    previewX, previewY,
                    0.0F, 0.0F,
                    176, 166,
                    176, 166,
                    256, 256);
            OverlayRenderer.drawConfigTexture(graphics, selected().survivalInventory, previewX, previewY);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, CREATIVE_PREVIEW_TEXTURE,
                    previewX, previewY,
                    0.0F, 0.0F,
                    195, 136,
                    195, 136,
                    256, 256);
            OverlayRenderer.drawConfigTexture(graphics, selected().creativeInventory, previewX, previewY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeTab == Tab.MAIN && this.characterListOpen && isMouseOverCharacterList(mouseX, mouseY)) {
            int maxScroll = Math.max(0, OverlayConfig.get().animations.size() - getCharacterListVisibleRows());
            if (verticalAmount < 0.0D) {
                this.characterListScroll = clamp(this.characterListScroll + 1, 0, maxScroll);
                rebuildConfigWidgets();
                return true;
            }
            if (verticalAmount > 0.0D) {
                this.characterListScroll = clamp(this.characterListScroll - 1, 0, maxScroll);
                rebuildConfigWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        OverlayConfig.save();
        openParentScreen();
    }

    private OverlayConfig.AnimationSet selected() {
        return OverlayConfig.get().selected();
    }

    private OverlayConfig.TextureConfig activeTexture() {
        return this.activeTab == Tab.CREATIVE ? selected().creativeInventory : selected().survivalInventory;
    }

    private OverlayConfig.TextureConfig defaultTexture() {
        OverlayConfig.Config defaults = OverlayConfig.Config.defaults();
        OverlayConfig.AnimationSet animationSet = defaults.animations.get(OverlayConfig.get().selectedAnimation);
        if (animationSet == null) {
            animationSet = defaults.selected();
        }
        return this.activeTab == Tab.CREATIVE ? animationSet.creativeInventory : animationSet.survivalInventory;
    }

    private void cycleAnimation() {
        OverlayConfig.Config config = OverlayConfig.get();
        List<String> keys = getAnimationKeys();
        if (keys.isEmpty()) {
            return;
        }

        int currentIndex = keys.indexOf(config.selectedAnimation);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % keys.size();
        config.selectedAnimation = keys.get(nextIndex);
        OverlayConfig.save();
        rebuildConfigWidgets();
    }

    private void resetToDefaults() {
        OverlayConfig.Config defaults = OverlayConfig.Config.defaults();
        OverlayConfig.Config current = OverlayConfig.get();
        current.enabled = defaults.enabled;
        current.selectedAnimation = defaults.selectedAnimation;
        current.animations = defaults.animations;
        OverlayConfig.save();
    }

    private void updateButtonLabels() {
        OverlayConfig.Config config = OverlayConfig.get();
        OverlayConfig.AnimationSet set = config.selected();

        if (this.mainTabButton != null) {
            this.mainTabButton.active = this.activeTab != Tab.MAIN;
        }
        if (this.survivalTabButton != null) {
            this.survivalTabButton.active = this.activeTab != Tab.SURVIVAL;
        }
        if (this.creativeTabButton != null) {
            this.creativeTabButton.active = this.activeTab != Tab.CREATIVE;
        }
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(Component.empty());
        }
        if (this.animationButton != null) {
            this.animationButton.setMessage(Component.empty());
        }
        if (this.animationArrowButton != null) {
            this.animationArrowButton.setMessage(Component.empty());
        }
        if (this.offsetXResetButton != null) {
            this.offsetXResetButton.setMessage(Component.empty());
        }
        if (this.offsetYResetButton != null) {
            this.offsetYResetButton.setMessage(Component.empty());
        }
        if (this.doneButton != null) {
            this.doneButton.setMessage(Component.empty());
        }
        if (this.offsetXSlider != null) {
            this.offsetXSlider.refreshFromConfig();
        }
        if (this.offsetYSlider != null) {
            this.offsetYSlider.refreshFromConfig();
        }
    }

    private void centeredText(GuiGraphicsExtractor graphics, String text, int y) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, 0xFFFFFFFF, true);
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double toSliderValue(int value) {
        int clamped = clamp(value, OFFSET_MIN, OFFSET_MAX);
        return (double) (clamped - OFFSET_MIN) / (double) (OFFSET_MAX - OFFSET_MIN);
    }

    private static int fromSliderValue(double value) {
        return clamp((int) Math.round(OFFSET_MIN + value * (OFFSET_MAX - OFFSET_MIN)), OFFSET_MIN, OFFSET_MAX);
    }

    private static String ellipsize(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String next = builder.toString() + c;
            if (font.width(next) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(c);
        }

        return builder + ellipsis;
    }

    private enum Tab {
        MAIN,
        SURVIVAL,
        CREATIVE
    }

    private static final class OffsetSlider extends AbstractSliderButton {
        private final String label;
        private final int labelX;
        private final int labelWidth;
        private final IntGetter getter;
        private final IntSetter setter;

        private OffsetSlider(int x, int y, int width, int height, String label, int labelX, int labelWidth, IntGetter getter, IntSetter setter) {
            super(x, y, width, height, Component.empty(), toSliderValue(getter.get()));
            this.label = label;
            this.labelX = labelX;
            this.labelWidth = labelWidth;
            this.getter = getter;
            this.setter = setter;
            updateMessage();
        }

        private void refreshFromConfig() {
            this.value = toSliderValue(getter.get());
            updateMessage();
        }

        private int getValueSlotLeft() {
            return this.getX() + 6;
        }

        private int getValueSlotWidth() {
            return 34;
        }

        private int getTrackLeft() {
            return getValueSlotLeft() + getValueSlotWidth() + 2;
        }

        private int getTrackRight() {
            return this.getX() + this.getWidth() - 4;
        }

        private void setSliderValueFromMouse(double mouseX) {
            int trackLeft = getTrackLeft();
            int trackRight = getTrackRight();
            int trackWidth = trackRight - trackLeft;
            if (trackWidth <= 0) {
                return;
            }

            double newValue = (mouseX - (double) trackLeft) / (double) trackWidth;
            this.value = Math.max(0.0D, Math.min(1.0D, newValue));
            applyValue();
            updateMessage();
        }

        void drawOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
            String valueText = String.valueOf(getter.get());
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            boolean rowHovered = mouseX >= this.labelX
                    && mouseX < x + width
                    && mouseY >= y
                    && mouseY < y + height;
            boolean expanded = rowHovered || this.isFocused();

            int labelColor = 0xFFFFFFFF;
            int valueColor = 0xFFFFFFFF;
            int textY = y + 6;

            if (!expanded) {
                graphics.text(font, this.label, this.labelX + 10, textY, labelColor, true);
                graphics.text(font, valueText, x + width - font.width(valueText) - 14, textY, valueColor, true);
                return;
            }

            int labelMaxWidth = Math.max(50, this.labelWidth - 26);
            String labelText = ellipsize(font, this.label, labelMaxWidth);
            graphics.text(font, labelText, this.labelX + 10, textY, labelColor, true);

            int valueSlotLeft = getValueSlotLeft();
            int valueSlotWidth = getValueSlotWidth();
            int valueTextX = valueSlotLeft + (valueSlotWidth - font.width(valueText)) / 2;
            graphics.text(font, valueText, valueTextX, textY, valueColor, true);

            int trackLeft = getTrackLeft();
            int trackRight = getTrackRight();
            if (trackRight <= trackLeft + 8) {
                return;
            }

            int trackY = y + height / 2;
            int knobX = trackLeft + (int) Math.round((trackRight - trackLeft) * this.value);

            graphics.fill(trackLeft, trackY, trackRight, trackY + 1, 0xFFE6E6E6);
            graphics.fill(knobX - 2, y + 3, knobX + 3, y + height - 3, 0xFFE6E6E6);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            if (event.button() == 0
                    && this.active
                    && mouseX >= this.getX()
                    && mouseX < this.getX() + this.getWidth()
                    && mouseY >= this.getY()
                    && mouseY < this.getY() + this.getHeight()) {
                setFocused(true);
                setSliderValueFromMouse(mouseX);
                return true;
            }
            return super.mouseClicked(event, isDoubleClick);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (event.button() == 0 && this.active && this.isFocused()) {
                setSliderValueFromMouse(event.x());
                return true;
            }
            return super.mouseDragged(event, dragX, dragY);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.empty());
        }

        @Override
        protected void applyValue() {
            setter.set(fromSliderValue(this.value));
            OverlayConfig.save();
            updateMessage();
        }
    }
}

