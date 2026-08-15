package ru.irku.inventorycompanions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverlayConfigValidationTest {
    @Test
    void removesNullCustomAnimationsInsteadOfCrashing() {
        OverlayConfig.Config config = OverlayConfig.Config.defaults();
        config.animations.put("broken_custom_entry", null);

        OverlayConfig.Config validated = OverlayConfig.validate(config);

        assertNotNull(validated);
        assertFalse(validated.animations.containsKey("broken_custom_entry"));
        assertNotNull(validated.animations.get("lololowka"));
    }

    @Test
    void oldConfigDoesNotOverwriteCustomBuiltInTextureSettings() {
        OverlayConfig.Config config = OverlayConfig.Config.defaults();
        config.configVersion = 24;

        OverlayConfig.AnimationSet alfedov = config.animations.get("alfedov");
        alfedov.survivalInventory.texture = "inventory_companions/custom_alfedov.png";
        alfedov.survivalInventory.frameWidth = 41;
        alfedov.survivalInventory.frameHeight = 43;
        alfedov.survivalInventory.frameCount = 7;
        alfedov.survivalInventory.speedFps = 6.0D;
        alfedov.survivalInventory.offsetX = 125;
        alfedov.survivalInventory.offsetY = 53;

        OverlayConfig.Config validated = OverlayConfig.validate(config);
        OverlayConfig.TextureConfig texture = validated.animations.get("alfedov").survivalInventory;

        assertEquals("inventory_companions/custom_alfedov.png", texture.texture);
        assertEquals(41, texture.frameWidth);
        assertEquals(43, texture.frameHeight);
        assertEquals(7, texture.frameCount);
        assertEquals(6.0D, texture.speedFps);
        assertEquals(125, texture.offsetX);
        assertEquals(53, texture.offsetY);
    }

    @Test
    void legacyBuiltInInventorySheetStillMigrates() {
        OverlayConfig.Config config = OverlayConfig.Config.defaults();
        config.configVersion = 24;

        OverlayConfig.AnimationSet alfedov = config.animations.get("alfedov");
        alfedov.survivalInventory.texture = "alfedov_inv_tab.png";
        alfedov.survivalInventory.frameWidth = 176;
        alfedov.survivalInventory.frameHeight = 166;
        alfedov.survivalInventory.frameCount = 29;
        alfedov.survivalInventory.speedFps = 6.0D;
        alfedov.survivalInventory.offsetX = 125;
        alfedov.survivalInventory.offsetY = 53;

        OverlayConfig.Config validated = OverlayConfig.validate(config);
        OverlayConfig.TextureConfig texture = validated.animations.get("alfedov").survivalInventory;
        BuiltInCompanions.Companion companion = BuiltInCompanions.all().stream()
                .filter(candidate -> candidate.key().equals("alfedov"))
                .findFirst()
                .orElseThrow();

        assertEquals(companion.texturePath(), texture.texture);
        assertEquals(companion.sprite().width(), texture.frameWidth);
        assertEquals(companion.sprite().height(), texture.frameHeight);
        assertEquals(companion.sprite().frameCount(), texture.frameCount);
    }
}
