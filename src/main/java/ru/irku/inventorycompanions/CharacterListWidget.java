package ru.irku.inventorycompanions;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class CharacterListWidget {
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 25;
    private static final int SCROLLBAR_WIDTH = 10;
    private static final int PANEL_Y = 108;
    private static final int PANEL_WIDTH = 240;
    private final IntSupplier panelX;
    private final Supplier<OverlayConfig.Config> configSupplier;
    private final Function<String, String> labelProvider;
    private final Consumer<String> selectionHandler;
    private final Runnable rebuild;
    private final List<Button> buttons = new ArrayList<>();
    private final List<String> visibleKeys = new ArrayList<>();
    private List<String> animationKeys = List.of();

    private boolean open;
    private int scroll;
    private boolean scrollbarDragging;
    private double scrollbarDragOffset;

    CharacterListWidget(IntSupplier panelX,
                        Supplier<OverlayConfig.Config> configSupplier,
                        Function<String, String> labelProvider,
                        Consumer<String> selectionHandler,
                        Runnable rebuild) {
        this.panelX = panelX;
        this.configSupplier = configSupplier;
        this.labelProvider = labelProvider;
        this.selectionHandler = selectionHandler;
        this.rebuild = rebuild;
    }

    boolean isOpen() {
        return open;
    }

    int panelWidth() {
        return PANEL_WIDTH;
    }

    void toggle() {
        open = !open;
        scrollbarDragging = false;
        if (open) {
            ensureSelectedVisible();
        }
    }

    void close() {
        open = false;
        scrollbarDragging = false;
    }

    void clearButtons() {
        buttons.clear();
        visibleKeys.clear();
    }

    void refreshEntries() {
        OverlayConfig.Config config = configSupplier.get();
        List<String> keys = new ArrayList<>(config.animations.keySet());
        keys.remove(AnimationIds.RANDOM);
        keys.sort((left, right) -> labelProvider.apply(left).compareToIgnoreCase(labelProvider.apply(right)));
        keys.remove(AnimationIds.PLAYER_SKIN);
        keys.add(0, AnimationIds.RANDOM);
        keys.add(0, AnimationIds.PLAYER_SKIN);
        animationKeys = List.copyOf(keys);
    }

    List<String> animationKeys() {
        return animationKeys;
    }

    void addButtons(Function<Button, Button> addWidget) {
        List<String> keys = animationKeys;
        int visibleRows = visibleRows(keys.size());
        int maxScroll = Math.max(0, keys.size() - visibleRows);
        scroll = clamp(scroll, 0, maxScroll);

        int x = panelX.getAsInt();
        int y = PANEL_Y + 8;
        int innerWidth = PANEL_WIDTH - 16 - SCROLLBAR_WIDTH - 4;
        int end = Math.min(keys.size(), scroll + visibleRows);

        for (int i = scroll; i < end; i++) {
            String key = keys.get(i);
            int rowY = y + (i - scroll) * ROW_HEIGHT;
            Button entry = addWidget.apply(Button.builder(Component.literal(labelProvider.apply(key)), button -> {
                selectionHandler.accept(key);
                close();
                rebuild.run();
            }).bounds(x + 8, rowY, innerWidth, 18).build());
            entry.setAlpha(0.0F);
            buttons.add(entry);
            visibleKeys.add(key);
        }
    }

    void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (!open) {
            return;
        }

        int x = panelX.getAsInt();
        int height = panelHeight();
        UiTheme.drawPanel(graphics, x, PANEL_Y, PANEL_WIDTH, height, UiTheme.PANEL_BACKGROUND_STRONG);

        OverlayConfig.Config config = configSupplier.get();
        int count = Math.min(buttons.size(), visibleKeys.size());
        for (int i = 0; i < count; i++) {
            Button button = buttons.get(i);
            String key = visibleKeys.get(i);
            boolean hovered = UiTheme.contains(button, mouseX, mouseY);
            boolean selected = key.equals(config.selectedAnimation);

            int buttonX = button.getX();
            int buttonY = button.getY();
            int width = button.getWidth();
            int buttonHeight = button.getHeight();
            int fill = selected ? 0x5AFFFFFF : hovered ? 0x34000000 : 0x22000000;
            int edge = selected ? 0xFFD0D0D0 : hovered ? 0xFF7A7A8A : 0x00000000;
            if (fill != 0) {
                graphics.fill(buttonX, buttonY, buttonX + width, buttonY + buttonHeight, fill);
            }
            if (edge != 0) {
                graphics.fill(buttonX, buttonY, buttonX + width, buttonY + 1, edge);
                graphics.fill(buttonX, buttonY + buttonHeight - 1, buttonX + width, buttonY + buttonHeight, edge);
            }

            int color = selected ? 0xFFFFFFFF : hovered ? 0xFFEAEAEA : 0xFFD0D0D0;
            int textY = buttonY + (buttonHeight - 8) / 2;
            String text = UiText.ellipsize(font, labelProvider.apply(key), width - 12);
            graphics.text(font, text, buttonX + 6, textY, color, true);
        }

        drawScrollbar(graphics, mouseX, mouseY);
    }

    boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != 0 || !open || !isMouseOverScrollbar(event.x(), event.y())) {
            return false;
        }

        int thumbY = scrollbarThumbY();
        int thumbHeight = scrollbarThumbHeight();
        if (event.y() >= thumbY && event.y() < thumbY + thumbHeight) {
            scrollbarDragOffset = event.y() - thumbY;
        } else {
            scrollbarDragOffset = thumbHeight / 2.0D;
        }
        scrollbarDragging = true;
        setScrollFromScrollbar(event.y());
        return true;
    }

    boolean mouseDragged(MouseButtonEvent event) {
        if (event.button() != 0 || !scrollbarDragging) {
            return false;
        }
        setScrollFromScrollbar(event.y());
        return true;
    }

    boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != 0 || !scrollbarDragging) {
            return false;
        }
        scrollbarDragging = false;
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!open || !isMouseOverPanel(mouseX, mouseY) || verticalAmount == 0.0D) {
            return false;
        }

        int maxScroll = Math.max(0, animationKeys.size() - visibleRows());
        int direction = verticalAmount < 0.0D ? 1 : -1;
        int newScroll = clamp(scroll + direction, 0, maxScroll);
        if (newScroll != scroll) {
            scroll = newScroll;
            rebuild.run();
        }
        return true;
    }

    private void ensureSelectedVisible() {
        OverlayConfig.Config config = configSupplier.get();
        List<String> keys = animationKeys;
        int selectedIndex = keys.indexOf(config.selectedAnimation);
        int visibleRows = visibleRows(keys.size());
        int maxScroll = Math.max(0, keys.size() - visibleRows);

        if (selectedIndex < 0) {
            scroll = clamp(scroll, 0, maxScroll);
            return;
        }
        if (selectedIndex < scroll) {
            scroll = selectedIndex;
        } else if (selectedIndex >= scroll + visibleRows) {
            scroll = selectedIndex - visibleRows + 1;
        }
        scroll = clamp(scroll, 0, maxScroll);
    }

    private int panelHeight() {
        return 16 + visibleRows() * ROW_HEIGHT;
    }

    private int visibleRows() {
        return visibleRows(animationKeys.size());
    }

    private static int visibleRows(int total) {
        return Math.max(1, Math.min(VISIBLE_ROWS, total));
    }

    private int scrollbarTrackX() {
        return panelX.getAsInt() + PANEL_WIDTH - SCROLLBAR_WIDTH - 5;
    }

    private static int scrollbarTrackY() {
        return PANEL_Y + 8;
    }

    private int scrollbarTrackHeight() {
        return panelHeight() - 16;
    }

    private int scrollbarThumbHeight() {
        int total = animationKeys.size();
        int visible = visibleRows(total);
        int trackHeight = scrollbarTrackHeight();
        if (total <= visible) {
            return trackHeight;
        }
        return Math.max(18, (int) Math.round((double) trackHeight * visible / total));
    }

    private int scrollbarThumbY() {
        int maxScroll = Math.max(0, animationKeys.size() - visibleRows());
        int trackY = scrollbarTrackY();
        int travel = scrollbarTrackHeight() - scrollbarThumbHeight();
        if (maxScroll <= 0 || travel <= 0) {
            return trackY;
        }
        return trackY + (int) Math.round((double) travel * scroll / maxScroll);
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        int x = scrollbarTrackX() - 3;
        int y = scrollbarTrackY();
        int width = SCROLLBAR_WIDTH + 6;
        int height = scrollbarTrackHeight();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isMouseOverPanel(double mouseX, double mouseY) {
        int x = panelX.getAsInt();
        return mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= PANEL_Y && mouseY < PANEL_Y + panelHeight();
    }

    private void setScrollFromScrollbar(double mouseY) {
        int maxScroll = Math.max(0, animationKeys.size() - visibleRows());
        int travel = scrollbarTrackHeight() - scrollbarThumbHeight();
        if (maxScroll <= 0 || travel <= 0) {
            return;
        }

        double thumbTop = mouseY - scrollbarDragOffset;
        double fraction = (thumbTop - scrollbarTrackY()) / travel;
        int newScroll = clamp((int) Math.round(fraction * maxScroll), 0, maxScroll);
        if (newScroll != scroll) {
            scroll = newScroll;
            rebuild.run();
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int total = animationKeys.size();
        int visible = visibleRows(total);
        if (total <= visible) {
            return;
        }

        int trackX = scrollbarTrackX();
        int trackY = scrollbarTrackY();
        int trackHeight = scrollbarTrackHeight();
        int thumbY = scrollbarThumbY();
        int thumbHeight = scrollbarThumbHeight();
        boolean hovered = isMouseOverScrollbar(mouseX, mouseY);

        graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, 0x48000000);
        graphics.fill(trackX, trackY, trackX + 1, trackY + trackHeight, 0xFF353542);
        graphics.fill(trackX + SCROLLBAR_WIDTH - 1, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, 0xFF181820);

        int thumbColor = scrollbarDragging ? 0xFFF0F0F0 : hovered ? 0xFFD8D8D8 : 0xFF9A9AA4;
        graphics.fill(trackX + 2, thumbY, trackX + SCROLLBAR_WIDTH - 2, thumbY + thumbHeight, thumbColor);
        graphics.fill(trackX + 2, thumbY, trackX + SCROLLBAR_WIDTH - 2, thumbY + 1, 0xFFFFFFFF);
        graphics.fill(trackX + 2, thumbY + thumbHeight - 1, trackX + SCROLLBAR_WIDTH - 2, thumbY + thumbHeight, 0xFF5A5A64);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
