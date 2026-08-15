package ru.irku.inventorycompanions;

import java.util.List;
import java.util.Set;

final class BuiltInCompanions {
    private static final List<Companion> ALL = List.of(
            companion("lololowka", "Lololowka (ПР)", "lololowka_inv.png", sprite(39, 28, 9, 6.0D), offsets(125, 53, 126, 14), true),
            companion("alfedov", "Alfedov", "alfedov_inv.png", sprite(27, 26, 29, 9.0D), offsets(134, 53, 137, 14), false),
            companion("secb", "SecB", "secb_inv.png", sprite(36, 31, 9, 6.0D), offsets(125, 53, 126, 14), false),
            companion("jdh", "JDH", "jdh_inv.png", sprite(34, 27, 100, 12.0D), offsets(124, 54, 127, 15), false),
            companion("pwgood", "PWGood", "pwgood_inv.png", sprite(33, 27, 46, 9.0D), offsets(125, 53, 126, 14), false),
            companion("lololowka47", "Lololowka (М47)", "lololowka47_inv.png", sprite(29, 30, 100, 9.0D), offsets(132, 48, 139, 12), true),
            companion("alcest_m", "Alcest_M", "alcest_m_inv.png", sprite(48, 32, 70, 10.0D), offsets(124, 49, 132, 8), true),
            companion("bezls", "BezLS", "bezls_inv.png", sprite(27, 26, 180, 10.0D), offsets(134, 53, 137, 14), true),
            companion("klashraick", "KlashRaick", "klashraick_inv.png", sprite(21, 29, 84, 10.0D), offsets(140, 50, 143, 11), true),
            companion("kompotikivun", "KompotikIvun", "kompotikivun_inv.png", sprite(40, 35, 169, 10.0D), offsets(121, 44, 124, 5), true)
    );

    private static final Set<String> LEGACY_TEXTURES = Set.of(
            "lololowka_inv_tab.png",
            "secb_inv_tab.png",
            "jdh_inv_tab.png",
            "pwgood_inv_tab.png",
            "lololowka47_inv_tab.png"
    );

    private BuiltInCompanions() {
    }

    static List<Companion> all() {
        return ALL;
    }

    static void addDefaults(OverlayConfig.Config config) {
        for (Companion companion : ALL) {
            config.animations.put(companion.key(), companion.createAnimationSet());
        }
    }

    static boolean contains(String key) {
        for (Companion companion : ALL) {
            if (companion.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    static boolean isBuiltInTexture(String fileName) {
        if (LEGACY_TEXTURES.contains(fileName)) {
            return true;
        }
        for (Companion companion : ALL) {
            if (companion.textureFile().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private static Companion companion(String key, String displayName, String textureFile,
                                       SpriteSpec sprite, InventoryOffsets offsets, boolean forceDisplayName) {
        return new Companion(key, displayName, textureFile, sprite, offsets, forceDisplayName);
    }

    private static SpriteSpec sprite(int width, int height, int frameCount, double speedFps) {
        return new SpriteSpec(width, height, frameCount, speedFps);
    }

    private static InventoryOffsets offsets(int survivalX, int survivalY, int creativeX, int creativeY) {
        return new InventoryOffsets(survivalX, survivalY, creativeX, creativeY);
    }

    record Companion(
            String key,
            String displayName,
            String textureFile,
            SpriteSpec sprite,
            InventoryOffsets offsets,
            boolean forceDisplayName
    ) {
        String texturePath() {
            return InventoryCompanionsClient.MOD_ID + ":textures/gui/" + textureFile;
        }

        double speedFps() {
            return sprite.speedFps();
        }

        int survivalOffsetX() {
            return offsets.survivalX();
        }

        int survivalOffsetY() {
            return offsets.survivalY();
        }

        int creativeOffsetX() {
            return offsets.creativeX();
        }

        int creativeOffsetY() {
            return offsets.creativeY();
        }

        OverlayConfig.TextureConfig createTexture(String displayName, boolean creative) {
            int offsetX = creative ? offsets.creativeX() : offsets.survivalX();
            int offsetY = creative ? offsets.creativeY() : offsets.survivalY();
            return createTexture(displayName, sprite.speedFps(), offsetX, offsetY);
        }

        OverlayConfig.TextureConfig createTexture(String displayName, double speed, int offsetX, int offsetY) {
            return OverlayConfig.TextureConfig.companion(
                    displayName,
                    texturePath(),
                    sprite.width(),
                    sprite.height(),
                    sprite.frameCount(),
                    speed,
                    offsetX,
                    offsetY
            );
        }

        OverlayConfig.AnimationSet createAnimationSet() {
            return OverlayConfig.AnimationSet.of(
                    displayName,
                    createTexture("Survival Companion", false),
                    createTexture("Creative Companion", true)
            );
        }
    }

    record SpriteSpec(int width, int height, int frameCount, double speedFps) {
    }

    record InventoryOffsets(int survivalX, int survivalY, int creativeX, int creativeY) {
    }
}
