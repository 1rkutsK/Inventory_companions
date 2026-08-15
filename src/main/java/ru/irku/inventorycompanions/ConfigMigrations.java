package ru.irku.inventorycompanions;

final class ConfigMigrations {
    // Version 25 introduced the compact per-frame companion sheets.
    private static final int COMPACT_SPRITE_LAYOUT_VERSION = 25;
    // Version 26 only adjusts defaults when the user still has the old default value.
    private static final int DEFAULT_TUNING_VERSION = 26;

    private ConfigMigrations() {
    }

    static void apply(OverlayConfig.Config config, int sourceVersion) {
        migrateLololowka(config, sourceVersion);
        for (BuiltInCompanions.Companion companion : BuiltInCompanions.all()) {
            if (!"lololowka".equals(companion.key())) {
                migrateCompanion(config, companion, sourceVersion);
            }
        }

        if (sourceVersion < DEFAULT_TUNING_VERSION) {
            updateVersion26Defaults(config);
        }
        restoreBuiltInDisplayNames(config);
    }

    private static void updateVersion26Defaults(OverlayConfig.Config config) {
        updateDefaultSpeed(config, "alfedov", 6.0D, 9.0D);
        updateDefaultOffsets(config, "alfedov", 125, 53, 126, 14, 134, 53, 137, 14);

        updateDefaultSpeed(config, "jdh", 6.0D, 12.0D);
        updateDefaultSpeed(config, "pwgood", 6.0D, 9.0D);
        updateDefaultSpeed(config, "lololowka47", 6.0D, 9.0D);

        updateDefaultSpeed(config, "alcest_m", 6.0D, 10.0D);
        updateDefaultOffsets(config, "alcest_m", 109, 53, 112, 14, 124, 49, 132, 8);

        updateDefaultSpeed(config, "bezls", 9.0D, 10.0D);
        updateDefaultSpeed(config, "klashraick", 9.0D, 10.0D);
        updateDefaultSpeed(config, "kompotikivun", 9.0D, 10.0D);
    }

    private static void restoreBuiltInDisplayNames(OverlayConfig.Config config) {
        for (BuiltInCompanions.Companion companion : BuiltInCompanions.all()) {
            if (!companion.forceDisplayName()) {
                continue;
            }
            OverlayConfig.AnimationSet animation = config.animations.get(companion.key());
            if (animation != null) {
                animation.displayName = companion.displayName();
            }
        }
    }

    private static void migrateLololowka(OverlayConfig.Config config, int sourceVersion) {
        OverlayConfig.AnimationSet animation = config.animations.get("lololowka");
        if (animation == null) {
            return;
        }

        if (animation.survivalInventory == null) {
            animation.survivalInventory = OverlayConfig.TextureConfig.lololowkaCompanion("Survival Companion", 125, 53);
        }
        if (animation.creativeInventory == null) {
            animation.creativeInventory = OverlayConfig.TextureConfig.lololowkaCompanion("Creative Companion", 126, 14);
        }

        if (sourceVersion >= COMPACT_SPRITE_LAYOUT_VERSION) {
            return;
        }

        OverlayConfig.TextureConfig survival = animation.survivalInventory;
        OverlayConfig.TextureConfig creative = animation.creativeInventory;

        if (isLololowkaLegacySheet(survival, false)) {
            double speed = survival.speedFps > 0.0D ? survival.speedFps : 6.0D;
            applyLololowkaTexture(survival, "Survival Companion", speed,
                    migratedOffset(survival.offsetX, 125), migratedOffset(survival.offsetY, 53));
        }
        if (isLololowkaLegacySheet(creative, true)) {
            double speed = creative.speedFps > 0.0D ? creative.speedFps : 6.0D;
            applyLololowkaTexture(creative, "Creative Companion", speed,
                    migratedOffset(creative.offsetX, 126), migratedOffset(creative.offsetY, 14));
        }
    }

    private static boolean isLololowkaLegacySheet(OverlayConfig.TextureConfig texture, boolean creative) {
        String fileName = OverlayConfig.fileName(texture.texture);
        if (creative && "lololowka_inv_tab.png".equals(fileName)) {
            return true;
        }
        return "lololowka_inv.png".equals(fileName)
                && (texture.frameWidth == (creative ? 195 : 176)
                || texture.frameHeight == (creative ? 136 : 166));
    }

    private static void applyLololowkaTexture(OverlayConfig.TextureConfig texture, String displayName,
                                              double speed, int offsetX, int offsetY) {
        texture.texture = InventoryCompanionsClient.MOD_ID + ":textures/gui/lololowka_inv.png";
        texture.displayName = displayName;
        texture.frameWidth = 39;
        texture.frameHeight = 28;
        texture.frameCount = 9;
        texture.speedFps = speed;
        texture.offsetX = offsetX;
        texture.offsetY = offsetY;
    }

    private static void migrateCompanion(OverlayConfig.Config config, BuiltInCompanions.Companion companion,
                                         int sourceVersion) {
        OverlayConfig.AnimationSet animation = config.animations.get(companion.key());
        if (animation == null) {
            config.animations.put(companion.key(), companion.createAnimationSet());
            return;
        }

        if (animation.survivalInventory == null) {
            animation.survivalInventory = companion.createTexture("Survival Companion", false);
        }
        if (animation.creativeInventory == null) {
            animation.creativeInventory = companion.createTexture("Creative Companion", true);
        }

        if (sourceVersion >= COMPACT_SPRITE_LAYOUT_VERSION) {
            return;
        }

        OverlayConfig.TextureConfig survival = animation.survivalInventory;
        OverlayConfig.TextureConfig creative = animation.creativeInventory;
        String currentTexture = companion.textureFile();
        String legacyTabTexture = companion.key() + "_inv_tab.png";

        if (isLegacyInventorySheet(survival, currentTexture, legacyTabTexture, false)) {
            animation.survivalInventory = migratedTexture(survival, companion, false);
        }
        if (isLegacyInventorySheet(creative, currentTexture, legacyTabTexture, true)) {
            animation.creativeInventory = migratedTexture(creative, companion, true);
        }
    }

    private static boolean isLegacyInventorySheet(OverlayConfig.TextureConfig texture, String currentTexture,
                                                   String legacyTabTexture, boolean creative) {
        String fileName = OverlayConfig.fileName(texture.texture);
        if (legacyTabTexture.equals(fileName)) {
            return true;
        }
        if (!currentTexture.equals(fileName)) {
            return false; // Never rewrite a user's custom texture just because the config is old.
        }
        return texture.frameWidth == (creative ? 195 : 176) || texture.frameHeight > 64;
    }

    private static OverlayConfig.TextureConfig migratedTexture(OverlayConfig.TextureConfig oldTexture,
                                                               BuiltInCompanions.Companion companion,
                                                               boolean creative) {
        int defaultX = creative ? companion.creativeOffsetX() : companion.survivalOffsetX();
        int defaultY = creative ? companion.creativeOffsetY() : companion.survivalOffsetY();
        int offsetX = migratedOffset(oldTexture.offsetX, defaultX);
        int offsetY = migratedOffset(oldTexture.offsetY, defaultY);
        double speed = oldTexture.speedFps > 0.0D ? oldTexture.speedFps : companion.speedFps();
        String displayName = creative ? "Creative Companion" : "Survival Companion";
        return companion.createTexture(displayName, speed, offsetX, offsetY);
    }

    private static int migratedOffset(int oldValue, int defaultValue) {
        return oldValue == 0 ? defaultValue : oldValue;
    }

    private static void updateDefaultOffsets(OverlayConfig.Config config, String key,
                                             int oldSurvivalX, int oldSurvivalY,
                                             int oldCreativeX, int oldCreativeY,
                                             int newSurvivalX, int newSurvivalY,
                                             int newCreativeX, int newCreativeY) {
        OverlayConfig.AnimationSet animation = config.animations.get(key);
        if (animation == null) {
            return;
        }

        setOffsetsIfUnchanged(animation.survivalInventory, key,
                oldSurvivalX, oldSurvivalY, newSurvivalX, newSurvivalY);
        setOffsetsIfUnchanged(animation.creativeInventory, key,
                oldCreativeX, oldCreativeY, newCreativeX, newCreativeY);
    }

    private static void setOffsetsIfUnchanged(OverlayConfig.TextureConfig texture, String key,
                                              int oldX, int oldY, int newX, int newY) {
        if (isCurrentBuiltInTexture(texture, key)
                && texture.offsetX == oldX
                && texture.offsetY == oldY) {
            texture.offsetX = newX;
            texture.offsetY = newY;
        }
    }

    private static void updateDefaultSpeed(OverlayConfig.Config config, String key,
                                           double oldSpeed, double newSpeed) {
        OverlayConfig.AnimationSet animation = config.animations.get(key);
        if (animation == null) {
            return;
        }

        setSpeedIfUnchanged(animation.survivalInventory, key, oldSpeed, newSpeed);
        setSpeedIfUnchanged(animation.creativeInventory, key, oldSpeed, newSpeed);
    }

    private static void setSpeedIfUnchanged(OverlayConfig.TextureConfig texture, String key,
                                            double oldSpeed, double newSpeed) {
        if (isCurrentBuiltInTexture(texture, key)
                && Double.compare(texture.speedFps, oldSpeed) == 0) {
            texture.speedFps = newSpeed;
        }
    }

    private static boolean isCurrentBuiltInTexture(OverlayConfig.TextureConfig texture, String key) {
        if (texture == null) {
            return false;
        }
        String fileName = OverlayConfig.fileName(texture.texture);
        for (BuiltInCompanions.Companion companion : BuiltInCompanions.all()) {
            if (companion.key().equals(key)) {
                return companion.textureFile().equals(fileName);
            }
        }
        return false;
    }
}
