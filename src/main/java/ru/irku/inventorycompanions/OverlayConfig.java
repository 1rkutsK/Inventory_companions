package ru.irku.inventorycompanions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.irku.inventorycompanions.skin.PlayerSkinManager;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OverlayConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayConfig.class);
    private static final int CURRENT_CONFIG_VERSION = 26;
    private static final ConfigStorage STORAGE = new ConfigStorage(
            "inventory_companions.json", "animated_companions.json", LOGGER);

    private static Config config = Config.defaults();

    private OverlayConfig() {
    }

    public static synchronized Config get() {
        reloadIfChanged();
        return config;
    }

    public static synchronized void load() {
        config = validate(STORAGE.load(Config.class, Config::defaults));
        STORAGE.save(config);
    }

    private static void reloadIfChanged() {
        if (STORAGE.shouldReload()) {
            load();
        }
    }

    public static synchronized void save() {
        config = validate(config == null ? Config.defaults() : config);
        STORAGE.save(config);
    }

    static Config validate(Config value) {
        if (value == null) {
            value = Config.defaults();
        }

        int sourceVersion = Math.max(0, value.configVersion);

        LinkedHashMap<String, AnimationSet> cleanedAnimations = new LinkedHashMap<>();
        if (value.animations != null) {
            for (Map.Entry<String, AnimationSet> entry : value.animations.entrySet()) {
                String key = entry.getKey();
                AnimationSet animation = entry.getValue();
                if (key != null && !key.isBlank() && animation != null) {
                    cleanedAnimations.put(key, animation);
                }
            }
        }
        value.animations = cleanedAnimations;

        Config defaults = Config.defaults();
        for (Map.Entry<String, AnimationSet> entry : defaults.animations.entrySet()) {
            value.animations.putIfAbsent(entry.getKey(), entry.getValue());
        }

        if (value.animations.isEmpty()) {
            return defaults;
        }

        if (value.selectedAnimation == null
                || (!AnimationIds.RANDOM.equals(value.selectedAnimation) && !value.animations.containsKey(value.selectedAnimation))) {
            value.selectedAnimation = value.animations.keySet().iterator().next();
        }

        for (AnimationSet set : value.animations.values()) {
            if (set.survivalInventory == null) {
                set.survivalInventory = TextureConfig.survival(InventoryCompanionsClient.MOD_ID + ":textures/gui/lololowka_inv.png", 9);
            }
            if (set.creativeInventory == null) {
                set.creativeInventory = TextureConfig.lololowkaCompanion("Creative Companion", 126, 14);
            }

            sanitizeTexture(set.survivalInventory, 176, 166);
            sanitizeTexture(set.creativeInventory, 195, 136);
        }

        ConfigMigrations.apply(value, sourceVersion);
        value.configVersion = CURRENT_CONFIG_VERSION;
        initCustomPlayerAnimationOffsets(value);
        syncPlayerSkinAnimation(value);

        return value;
    }

    private static void initCustomPlayerAnimationOffsets(Config value) {
        if (value.customPlayerAnimationOffsets == null) {
            value.customPlayerAnimationOffsets = new LinkedHashMap<>();
        }
        for (String animation : AnimationIds.playerAnimations()) {
            value.customPlayerAnimationOffsets.putIfAbsent(animation, defaultPlayerAnimationOffsets(animation));
        }
        if (!isValidCustomPlayerAnimation(value.customPlayerAnimationSynced)) {
            value.customPlayerAnimationSynced = value.customPlayerAnimation;
        }
    }

    public static PlayerAnimationOffsets defaultPlayerAnimationOffsets(String animation) {
        return PlayerAnimationSpecs.get(animation).defaultOffsets();
    }

    public static void syncPlayerSkinAnimation(Config value) {
        if (value == null) {
            return;
        }

        sanitizePlayerSkinSettings(value);
        rememberCurrentPlayerSkinOffsets(value);
        buildPlayerSkinAnimation(value);
    }

    private static void sanitizePlayerSkinSettings(Config value) {
        if (value.customPlayerNickname == null) {
            value.customPlayerNickname = "";
        }
        if (value.customPlayerTexture == null || value.customPlayerTexture.isBlank()) {
            value.customPlayerTexture = PlayerSkinManager.GENERATED_TEXTURE;
        }
        if (!isValidCustomPlayerAnimation(value.customPlayerAnimation)) {
            value.customPlayerAnimation = AnimationIds.STATIC;
        }

        initCustomPlayerAnimationOffsets(value);
        value.customPlayerPatternVariant = clamp(value.customPlayerPatternVariant, 1, 3);
        if (value.customPlayerBodyPatternVariant <= 0) {
            value.customPlayerBodyPatternVariant = value.customPlayerPatternVariant;
        }
        if (value.customPlayerHandsPatternVariant <= 0) {
            value.customPlayerHandsPatternVariant = value.customPlayerPatternVariant;
        }
        if (value.customPlayerLegsPatternVariant <= 0) {
            value.customPlayerLegsPatternVariant = value.customPlayerPatternVariant;
        }
        value.customPlayerBodyPatternVariant = clamp(value.customPlayerBodyPatternVariant, 1, 3);
        value.customPlayerHandsPatternVariant = clamp(value.customPlayerHandsPatternVariant, 1, 3);
        value.customPlayerLegsPatternVariant = clamp(value.customPlayerLegsPatternVariant, 1, 3);
    }

    private static void rememberCurrentPlayerSkinOffsets(Config value) {
        AnimationSet existing = value.animations.get(AnimationIds.PLAYER_SKIN);
        if (existing == null) {
            return;
        }

        String previousAnimation = isValidCustomPlayerAnimation(value.customPlayerAnimationSynced)
                ? value.customPlayerAnimationSynced
                : value.customPlayerAnimation;
        PlayerAnimationOffsets previousOffsets = value.customPlayerAnimationOffsets.computeIfAbsent(
                previousAnimation, OverlayConfig::defaultPlayerAnimationOffsets);

        if (existing.survivalInventory != null) {
            previousOffsets.survivalOffsetX = existing.survivalInventory.offsetX;
            previousOffsets.survivalOffsetY = existing.survivalInventory.offsetY;
        }
        if (existing.creativeInventory != null) {
            previousOffsets.creativeOffsetX = existing.creativeInventory.offsetX;
            previousOffsets.creativeOffsetY = existing.creativeInventory.offsetY;
        }
    }

    private static void buildPlayerSkinAnimation(Config value) {
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(value.customPlayerAnimation);
        PlayerAnimationOffsets offsets = value.customPlayerAnimationOffsets.computeIfAbsent(
                value.customPlayerAnimation, OverlayConfig::defaultPlayerAnimationOffsets);

        TextureConfig survival = TextureConfig.companion(
                "Player Skin Survival", value.customPlayerTexture, spec.frameWidth(), spec.frameHeight(),
                spec.frameCount(), spec.speedFps(), offsets.survivalOffsetX, offsets.survivalOffsetY);
        TextureConfig creative = TextureConfig.companion(
                "Player Skin Creative", value.customPlayerTexture, spec.frameWidth(), spec.frameHeight(),
                spec.frameCount(), spec.speedFps(), offsets.creativeOffsetX, offsets.creativeOffsetY);

        value.customPlayerAnimationSynced = value.customPlayerAnimation;
        value.animations.put(AnimationIds.PLAYER_SKIN, AnimationSet.of("Player Skin", survival, creative));
    }

    private static boolean isValidCustomPlayerAnimation(String animation) {
        return AnimationIds.isPlayerAnimation(animation);
    }


    private static void sanitizeTexture(TextureConfig texture, int defaultWidth, int defaultHeight) {
        if (texture.displayName == null || texture.displayName.isBlank()) {
            texture.displayName = defaultWidth == 176 ? "Survival Inventory" : "Creative Inventory";
        }
        if (texture.texture == null || texture.texture.isBlank()) {
            texture.texture = InventoryCompanionsClient.MOD_ID + ":textures/gui/lololowka_inv.png";
        }
        texture.texture = normalizeTexturePath(texture.texture);
        if (texture.frameWidth <= 0) {
            texture.frameWidth = defaultWidth;
        }
        if (texture.frameHeight <= 0) {
            texture.frameHeight = defaultHeight;
        }
        if (texture.frameCount <= 0) {
            texture.frameCount = 1;
        }
        if (texture.speedFps <= 0.0D) {
            texture.speedFps = 6.0D;
        }
        texture.offsetX = clamp(texture.offsetX, -2000, 2000);
        texture.offsetY = clamp(texture.offsetY, -2000, 2000);
    }

    private static String normalizeTexturePath(String texturePath) {
        String result = texturePath.trim().replace('\\', '/');

        if (!result.endsWith(".png")) {
            result = result + ".png";
        }

        String fileName = fileName(result);
        if (BuiltInCompanions.isBuiltInTexture(fileName)) {
            return InventoryCompanionsClient.MOD_ID + ":textures/gui/" + fileName;
        }

        if (result.startsWith("inventory_companions/") || result.startsWith("generated/") || result.startsWith("skins/")) {
            return result;
        }

        if (result.indexOf(':') < 0) {
            result = InventoryCompanionsClient.MOD_ID + ":" + result;
        }

        return result;
    }

    static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int namespaceSeparator = fileName.lastIndexOf(':');
        return namespaceSeparator >= 0 ? fileName.substring(namespaceSeparator + 1) : fileName;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Config {
        public int configVersion = CURRENT_CONFIG_VERSION;
        public boolean enabled = true;
        public String selectedAnimation = "lololowka";
        public Map<String, AnimationSet> animations = new LinkedHashMap<>();
        public String customPlayerNickname = "";
        public String customPlayerTexture = PlayerSkinManager.GENERATED_TEXTURE;
        public String customPlayerAnimation = AnimationIds.STATIC;
        public String customPlayerAnimationSynced = AnimationIds.STATIC;
        public Map<String, PlayerAnimationOffsets> customPlayerAnimationOffsets = new LinkedHashMap<>();
        public int customPlayerPatternVariant = 1;
        public int customPlayerBodyPatternVariant = 1;
        public int customPlayerHandsPatternVariant = 1;
        public int customPlayerLegsPatternVariant = 1;

        public static Config defaults() {
            Config config = new Config();

            BuiltInCompanions.addDefaults(config);

            initCustomPlayerAnimationOffsets(config);
            syncPlayerSkinAnimation(config);
            return config;
        }

        public AnimationSet selected() {
            AnimationSet selected = animations.get(selectedAnimation);
            if (selected != null) {
                return selected;
            }

            for (Map.Entry<String, AnimationSet> entry : animations.entrySet()) {
                if (!AnimationIds.PLAYER_SKIN.equals(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return animations.values().iterator().next();
        }
    }

    public static final class PlayerAnimationOffsets {
        public int survivalOffsetX;
        public int survivalOffsetY;
        public int creativeOffsetX;
        public int creativeOffsetY;
    }

    public static final class AnimationSet {
        public String displayName = "Animation";
        public TextureConfig survivalInventory;
        public TextureConfig creativeInventory;

        public static AnimationSet of(String displayName, TextureConfig survivalInventory, TextureConfig creativeInventory) {
            AnimationSet set = new AnimationSet();
            set.displayName = displayName;
            set.survivalInventory = survivalInventory;
            set.creativeInventory = creativeInventory;
            return set;
        }
    }

    public static final class TextureConfig {
        public String displayName;
        public String texture;
        public int frameWidth;
        public int frameHeight;
        public int frameCount;
        public double speedFps;
        public int offsetX;
        public int offsetY;

        public static TextureConfig survival(String texture, int frameCount) {
            return survival(texture, frameCount, 6.0D);
        }

        public static TextureConfig survival(String texture, int frameCount, double speedFps) {
            TextureConfig config = new TextureConfig();
            config.displayName = "Survival Inventory";
            config.texture = texture;
            config.frameWidth = 176;
            config.frameHeight = 166;
            config.frameCount = frameCount;
            config.speedFps = speedFps;
            config.offsetX = 0;
            config.offsetY = 0;
            return config;
        }

        public static TextureConfig lololowkaCompanion(String displayName, int offsetX, int offsetY) {
            TextureConfig config = new TextureConfig();
            config.displayName = displayName;
            config.texture = InventoryCompanionsClient.MOD_ID + ":textures/gui/lololowka_inv.png";
            config.frameWidth = 39;
            config.frameHeight = 28;
            config.frameCount = 9;
            config.speedFps = 6.0D;
            config.offsetX = offsetX;
            config.offsetY = offsetY;
            return config;
        }

        public static TextureConfig companion(String displayName, String texture, int frameWidth, int frameHeight,
                                              int frameCount, double speedFps, int offsetX, int offsetY) {
            TextureConfig config = new TextureConfig();
            config.displayName = displayName;
            config.texture = texture;
            config.frameWidth = frameWidth;
            config.frameHeight = frameHeight;
            config.frameCount = frameCount;
            config.speedFps = speedFps;
            config.offsetX = offsetX;
            config.offsetY = offsetY;
            return config;
        }

    }
}
