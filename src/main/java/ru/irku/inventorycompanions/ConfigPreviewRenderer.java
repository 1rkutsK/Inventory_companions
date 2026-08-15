package ru.irku.inventorycompanions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class ConfigPreviewRenderer {
    private static final Identifier SURVIVAL_INVENTORY = Identifier.parse("minecraft:textures/gui/container/inventory.png");
    private static final Identifier CREATIVE_INVENTORY = Identifier.parse("minecraft:textures/gui/container/creative_inventory/tab_inventory.png");

    private ConfigPreviewRenderer() {
    }

    static void drawInventory(GuiGraphicsExtractor graphics, boolean survival,
                              OverlayConfig.TextureConfig companion, ConfigScreenLayout.Rect area) {
        if (area.width() <= 40) {
            return;
        }

        int previewWidth = survival ? 176 : 195;
        int previewHeight = survival ? 166 : 136;
        int x = area.x() + (area.width() - previewWidth) / 2;
        int y = area.y() + (area.height() - previewHeight) / 2;

        Identifier texture = survival ? SURVIVAL_INVENTORY : CREATIVE_INVENTORY;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y,
                0.0F, 0.0F,
                previewWidth, previewHeight,
                previewWidth, previewHeight,
                256, 256);
        OverlayRenderer.drawConfigTexture(graphics, companion, x, y);
    }
}
