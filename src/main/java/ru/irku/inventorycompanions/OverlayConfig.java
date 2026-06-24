package ru.irku.inventorycompanions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OverlayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "inventory_companions.json";
    private static final int CONFIG_VERSION = 1;

    private static Config config = Config.defaults();
    private static Path configPath;
    private static long lastModified = -1L;

    private OverlayConfig() {
    }

    public static synchronized Config get() {
        reloadIfChanged();
        return config;
    }

    public static synchronized void load() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        try {
            if (!Files.exists(configPath)) {
                writeDefaults();
            }

            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                config = validate(GSON.fromJson(reader, Config.class));
            }

            save();
            lastModified = Files.getLastModifiedTime(configPath).toMillis();
        } catch (IOException | JsonSyntaxException exception) {
            System.err.println("[" + InventoryCompanionsClient.MOD_NAME + "] Config load failed: " + exception.getMessage());
            config = Config.defaults();
        }
    }

    public static synchronized void save() {
        if (configPath == null) {
            configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        }

        try {
            config = validate(config);
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            lastModified = Files.getLastModifiedTime(configPath).toMillis();
        } catch (IOException exception) {
            System.err.println("[" + InventoryCompanionsClient.MOD_NAME + "] Config save failed: " + exception.getMessage());
        }
    }

    private static void reloadIfChanged() {
        if (configPath == null) {
            load();
            return;
        }

        try {
            if (Files.exists(configPath)) {
                long modified = Files.getLastModifiedTime(configPath).toMillis();
                if (modified != lastModified) {
                    load();
                }
            }
        } catch (IOException exception) {
            System.err.println("[" + InventoryCompanionsClient.MOD_NAME + "] Config timestamp check failed: " + exception.getMessage());
        }
    }

    private static void writeDefaults() throws IOException {
        Files.createDirectories(configPath.getParent());
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(Config.defaults(), writer);
        }
    }

    private static Config validate(Config value) {
        Config defaults = Config.defaults();
        if (value == null) {
            return defaults;
        }

        value.configVersion = CONFIG_VERSION;
        if (value.animations == null) {
            value.animations = new LinkedHashMap<>();
        }

        for (Map.Entry<String, AnimationSet> entry : defaults.animations.entrySet()) {
            value.animations.putIfAbsent(entry.getKey(), entry.getValue());
        }

        AnimationSet fallback = defaults.animations.get(defaults.selectedAnimation);
        for (Map.Entry<String, AnimationSet> entry : value.animations.entrySet()) {
            AnimationSet defaultAnimation = defaults.animations.getOrDefault(entry.getKey(), fallback);
            entry.setValue(validateAnimation(entry.getValue(), defaultAnimation));
        }

        if (!value.animations.containsKey(value.selectedAnimation)) {
            value.selectedAnimation = defaults.selectedAnimation;
        }

        return value;
    }

    private static AnimationSet validateAnimation(AnimationSet current, AnimationSet fallback) {
        if (current == null) {
            return fallback;
        }

        if (current.displayName == null || current.displayName.isBlank()) {
            current.displayName = fallback.displayName;
        }
        current.survivalInventory = validateTexture(current.survivalInventory, fallback.survivalInventory);
        current.creativeInventory = validateTexture(current.creativeInventory, fallback.creativeInventory);
        return current;
    }

    private static TextureConfig validateTexture(TextureConfig current, TextureConfig fallback) {
        if (current == null) {
            return fallback;
        }

        if (current.displayName == null || current.displayName.isBlank()) {
            current.displayName = fallback.displayName;
        }
        if (current.texture == null || current.texture.isBlank()) {
            current.texture = fallback.texture;
        }

        current.texture = normalizeTexture(current.texture);
        current.frameWidth = positiveOrFallback(current.frameWidth, fallback.frameWidth);
        current.frameHeight = positiveOrFallback(current.frameHeight, fallback.frameHeight);
        current.frameCount = positiveOrFallback(current.frameCount, fallback.frameCount);
        current.speedFps = current.speedFps > 0.0D ? current.speedFps : fallback.speedFps;
        return current;
    }

    private static String normalizeTexture(String texture) {
        String value = texture.trim().replace('\\', '/');
        String fileName = fileName(value);
        if (isBuiltInTexture(fileName)) {
            return texture(fileName);
        }
        return value.indexOf(':') < 0 ? InventoryCompanionsClient.MOD_ID + ":" + value : value;
    }

    private static boolean isBuiltInTexture(String fileName) {
        return fileName.equals("lololowka_inv.png")
                || fileName.equals("alfedov_inv.png")
                || fileName.equals("secb_inv.png")
                || fileName.equals("jdh_inv.png")
                || fileName.equals("pwgood_inv.png")
                || fileName.equals("lololowka47_inv.png");
    }

    private static String texture(String fileName) {
        return InventoryCompanionsClient.MOD_ID + ":textures/gui/" + fileName;
    }

    private static String fileName(String path) {
        String value = path == null ? "" : path.trim().replace('\\', '/');
        return value.substring(value.lastIndexOf('/') + 1);
    }

    private static int positiveOrFallback(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    public static final class Config {
        public int configVersion = CONFIG_VERSION;
        public boolean enabled = true;
        public String selectedAnimation = "lololowka";
        public Map<String, AnimationSet> animations = new LinkedHashMap<>();

        public static Config defaults() {
            Config config = new Config();
            config.animations.put("lololowka", AnimationSet.of(
                    "Lololowka (ПР)",
                    TextureConfig.companion("Survival Companion", texture("lololowka_inv.png"), 39, 28, 9, 6.0D, 125, 53),
                    TextureConfig.companion("Creative Companion", texture("lololowka_inv.png"), 39, 28, 9, 6.0D, 126, 14)
            ));
            config.animations.put("alfedov", AnimationSet.of(
                    "Alfedov",
                    TextureConfig.companion("Survival Companion", texture("alfedov_inv.png"), 27, 26, 29, 9.0D, 134, 53),
                    TextureConfig.companion("Creative Companion", texture("alfedov_inv.png"), 27, 26, 29, 9.0D, 137, 14)
            ));
            config.animations.put("secb", AnimationSet.of(
                    "SecB",
                    TextureConfig.companion("Survival Companion", texture("secb_inv.png"), 36, 31, 9, 6.0D, 125, 53),
                    TextureConfig.companion("Creative Companion", texture("secb_inv.png"), 36, 31, 9, 6.0D, 126, 14)
            ));
            config.animations.put("jdh", AnimationSet.of(
                    "JDH",
                    TextureConfig.companion("Survival Companion", texture("jdh_inv.png"), 34, 27, 100, 12.0D, 124, 54),
                    TextureConfig.companion("Creative Companion", texture("jdh_inv.png"), 34, 27, 100, 12.0D, 127, 15)
            ));
            config.animations.put("pwgood", AnimationSet.of(
                    "PWGood",
                    TextureConfig.companion("Survival Companion", texture("pwgood_inv.png"), 33, 27, 46, 9.0D, 125, 53),
                    TextureConfig.companion("Creative Companion", texture("pwgood_inv.png"), 33, 27, 46, 9.0D, 126, 14)
            ));
            config.animations.put("lololowka47", AnimationSet.of(
                    "Lololowka (М47)",
                    TextureConfig.companion("Survival Companion", texture("lololowka47_inv.png"), 29, 30, 100, 9.0D, 132, 48),
                    TextureConfig.companion("Creative Companion", texture("lololowka47_inv.png"), 29, 30, 100, 9.0D, 139, 12)
            ));
            return config;
        }

        public AnimationSet selected() {
            AnimationSet selected = animations.get(selectedAnimation);
            return selected != null ? selected : Config.defaults().animations.get("lololowka");
        }
    }

    public static final class AnimationSet {
        public String displayName;
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
