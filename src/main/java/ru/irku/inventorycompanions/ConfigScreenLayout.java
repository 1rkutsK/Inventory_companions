package ru.irku.inventorycompanions;

final class ConfigScreenLayout {
    static final int MAIN_ROW_TOP = 40;
    static final int MAIN_ROW_HEIGHT = 24;
    static final int MAIN_ROW_GAP = 30;

    static final int TOP_TAB_Y = 6;
    static final int TOP_TAB_HEIGHT = 26;

    static final int DISPLAY_ROW_LEFT = 8;
    static final int DISPLAY_ROW_TOP = 56;
    static final int DISPLAY_ROW_GAP = 34;
    static final int DISPLAY_RESET_WIDTH = 28;
    static final int DISPLAY_CONTROL_GAP = 6;
    static final int DISPLAY_CONTROL_HEIGHT = 22;

    private static final int MAIN_ROW_MARGIN = 18;
    private static final int TOP_TAB_GAP = 6;
    private static final int DISPLAY_ROW_MAX_WIDTH = 840;
    private static final int DISPLAY_RIGHT_RESERVE = 280;
    private static final int DISPLAY_PREVIEW_GAP = 18;
    private static final int DISPLAY_PREVIEW_RIGHT_MARGIN = 26;
    private static final int DISPLAY_PREVIEW_HEIGHT = 210;
    private static final int DISPLAY_CONTENT_PREVIEW_TOP = 78;
    private static final int DISPLAY_CONTENT_PREVIEW_HEIGHT = 170;
    private static final int PLAYER_COLUMN_GAP = 10;

    private ConfigScreenLayout() {
    }

    static TabStrip tabs(int screenWidth) {
        int margin = responsiveMargin(screenWidth);
        int availableWidth = Math.max(4, screenWidth - margin * 2);
        int totalGap = TOP_TAB_GAP * 3;
        int tabWidth = Math.max(1, (availableWidth - totalGap) / 4);
        return new TabStrip(margin, TOP_TAB_Y, tabWidth, TOP_TAB_HEIGHT, TOP_TAB_GAP);
    }

    static int mainRowLeft() {
        return MAIN_ROW_MARGIN;
    }

    static int mainRowWidth(int screenWidth) {
        int left = mainRowLeft();
        return Math.max(80, Math.min(980, screenWidth - left * 2));
    }

    static int playerLeftPanelX(int screenWidth) {
        return responsiveMargin(screenWidth);
    }

    static int playerPanelY() {
        return 40;
    }

    static boolean playerPreviewVisible(int screenWidth) {
        return screenWidth >= 560;
    }

    static int playerLeftPanelWidth(int screenWidth) {
        int margin = responsiveMargin(screenWidth);
        int contentWidth = Math.max(100, screenWidth - margin * 2);
        if (!playerPreviewVisible(screenWidth)) {
            return contentWidth;
        }
        return Math.max(260, (contentWidth - PLAYER_COLUMN_GAP) * 56 / 100);
    }

    static int playerRightPanelX(int screenWidth) {
        return playerLeftPanelX(screenWidth) + playerLeftPanelWidth(screenWidth) + PLAYER_COLUMN_GAP;
    }

    static int playerRightPanelWidth(int screenWidth) {
        if (!playerPreviewVisible(screenWidth)) {
            return 0;
        }
        int margin = responsiveMargin(screenWidth);
        return Math.max(0, screenWidth - playerRightPanelX(screenWidth) - margin);
    }

    static int playerPanelHeight(int screenHeight) {
        return Math.max(160, screenHeight - 78);
    }

    static int displayRowWidth(int screenWidth) {
        int available = Math.max(120, screenWidth - DISPLAY_ROW_LEFT * 2);
        if (screenWidth < 620) {
            return available;
        }
        return Math.max(220, Math.min(DISPLAY_ROW_MAX_WIDTH, screenWidth - DISPLAY_RIGHT_RESERVE));
    }

    static int displayLabelWidth(int screenWidth) {
        int rowWidth = displayRowWidth(screenWidth);
        return Math.max(80, Math.min(140, rowWidth / 3));
    }

    static Rect displayPreviewPanel(int screenWidth) {
        if (screenWidth < 620) {
            return new Rect(screenWidth, DISPLAY_ROW_TOP, 0, DISPLAY_PREVIEW_HEIGHT);
        }
        int rowWidth = displayRowWidth(screenWidth);
        int x = DISPLAY_ROW_LEFT + rowWidth + DISPLAY_PREVIEW_GAP;
        int width = Math.max(0, screenWidth - x - DISPLAY_PREVIEW_RIGHT_MARGIN);
        return new Rect(x, DISPLAY_ROW_TOP, width, DISPLAY_PREVIEW_HEIGHT);
    }

    static Rect displayPreviewContent(int screenWidth) {
        Rect panel = displayPreviewPanel(screenWidth);
        return new Rect(panel.x(), DISPLAY_CONTENT_PREVIEW_TOP, panel.width(), DISPLAY_CONTENT_PREVIEW_HEIGHT);
    }

    private static int responsiveMargin(int screenWidth) {
        return screenWidth < 420 ? 8 : 20;
    }

    record TabStrip(int x, int y, int tabWidth, int height, int gap) {
        int xFor(int index) {
            return x + (tabWidth + gap) * index;
        }
    }

    record Rect(int x, int y, int width, int height) {
    }
}
