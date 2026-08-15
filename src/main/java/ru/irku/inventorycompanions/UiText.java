package ru.irku.inventorycompanions;

import net.minecraft.client.gui.Font;

final class UiText {
    private UiText() {
    }

    static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        StringBuilder builder = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int previousLength = builder.length();
            builder.appendCodePoint(codePoint);
            if (font.width(builder.toString()) + ellipsisWidth > maxWidth) {
                builder.setLength(previousLength);
                break;
            }
            offset += Character.charCount(codePoint);
        }
        return builder + ellipsis;
    }
}
