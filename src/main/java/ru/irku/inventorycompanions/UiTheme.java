package ru.irku.inventorycompanions;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;

final class UiTheme {
    static final int PANEL_BACKGROUND = 0x72000000;
    static final int PANEL_BACKGROUND_STRONG = 0x86000000;
    static final int BORDER_LIGHT = 0xFF5E5E6D;
    static final int BORDER_DARK = 0xFF272735;
    static final int BORDER_HOVER_LIGHT = 0xFF8A8A9A;
    static final int BORDER_HOVER_DARK = 0xFF424255;

    private UiTheme() {
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill) {
        drawBox(graphics, x, y, width, height, fill, BORDER_LIGHT, BORDER_DARK, true, true);
    }

    static void drawButton(GuiGraphicsExtractor graphics, Button button, int mouseX, int mouseY) {
        if (button == null) {
            return;
        }
        boolean hovered = isHovered(button, mouseX, mouseY);
        int fill = hovered ? 0x9B000000 : 0x82000000;
        int top = hovered ? BORDER_HOVER_LIGHT : BORDER_LIGHT;
        int bottom = hovered ? BORDER_HOVER_DARK : BORDER_DARK;
        drawBox(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(), fill, top, bottom, true, true);
    }

    static void drawSegment(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            boolean hovered, boolean drawLeftBorder, boolean drawRightBorder) {
        int fill = hovered ? 0x9B000000 : 0x82000000;
        int top = hovered ? BORDER_HOVER_LIGHT : BORDER_LIGHT;
        int bottom = hovered ? BORDER_HOVER_DARK : BORDER_DARK;
        drawBox(graphics, x, y, width, height, fill, top, bottom, drawLeftBorder, drawRightBorder);
    }

    static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                        int fill, int top, int bottom, boolean drawLeftBorder, boolean drawRightBorder) {
        if (width <= 0 || height <= 0) {
            return;
        }
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

    static int crispBoldTextWidth(Font font, String text) {
        int width = 0;
        boolean hasGlyph = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            width += font.width(glyph) + 1;
            hasGlyph = true;
            offset += Character.charCount(codePoint);
        }
        return hasGlyph ? Math.max(0, width - 1) : 0;
    }

    static void drawCrispBoldText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        int drawX = x;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            int glyphWidth = font.width(glyph);
            graphics.text(font, glyph, drawX, y, color, false);
            if (!Character.isWhitespace(codePoint) && glyphWidth > 0) {
                graphics.text(font, glyph, drawX + 1, y, color, false);
            }
            drawX += glyphWidth + 1;
            offset += Character.charCount(codePoint);
        }
    }

    static void drawTab(GuiGraphicsExtractor graphics, Font font, Button button,
                        int x, int y, int width, int height, boolean selected, String text,
                        int mouseX, int mouseY) {
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

        if (selected && width > 8) {
            int underlineWidth = Math.max(4, Math.min(width - 6, Math.min(132, Math.max(24, width / 2))));
            int underlineX = x + (width - underlineWidth) / 2;
            graphics.fill(underlineX, y + height - 2, underlineX + underlineWidth, y + height, 0xFFF2F2F2);
        }

        int color = selected ? 0xFFFFFFFF : hovered ? 0xFFF1F1F1 : 0xFFE4E4E4;
        String visibleText = UiText.ellipsize(font, text, Math.max(1, width - 16));
        int textWidth = crispBoldTextWidth(font, visibleText);
        drawCrispBoldText(graphics, font, visibleText, x + (width - textWidth) / 2, y + 8, color);
    }

    static void drawTextButton(GuiGraphicsExtractor graphics, Font font, Button button,
                               String text, int mouseX, int mouseY) {
        if (button == null) {
            return;
        }
        drawButton(graphics, button, mouseX, mouseY);
        String visibleText = UiText.ellipsize(font, text, Math.max(1, button.getWidth() - 12));
        int textX = button.getX() + (button.getWidth() - font.width(visibleText)) / 2;
        int textY = button.getY() + (button.getHeight() - 8) / 2;
        graphics.text(font, visibleText, textX, textY, 0xFFFFFFFF, true);
    }

    static void drawIconButton(GuiGraphicsExtractor graphics, Font font, Button button, String text,
                               int normalColor, int hoverColor, int mouseX, int mouseY) {
        if (button == null) {
            return;
        }
        drawButton(graphics, button, mouseX, mouseY);
        int color = isHovered(button, mouseX, mouseY) ? hoverColor : normalColor;
        int textX = button.getX() + (button.getWidth() - font.width(text)) / 2;
        int textY = button.getY() + (button.getHeight() - 8) / 2;
        graphics.text(font, text, textX, textY, color, true);
    }

    static int brighten(int color, int amount) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static boolean isHovered(Button button, int mouseX, int mouseY) {
        return button != null && button.active && contains(button, mouseX, mouseY);
    }

    static boolean contains(Button button, int mouseX, int mouseY) {
        return button != null
                && mouseX >= button.getX()
                && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY()
                && mouseY < button.getY() + button.getHeight();
    }
}
