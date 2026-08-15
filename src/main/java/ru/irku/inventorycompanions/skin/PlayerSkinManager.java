package ru.irku.inventorycompanions.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.irku.inventorycompanions.AnimationIds;
import ru.irku.inventorycompanions.OverlayConfig;
import ru.irku.inventorycompanions.OverlayRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerSkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSkinManager.class);
    public static final String GENERATED_TEXTURE = "inventory_companions/generated/player_skin.png";
    private static final String RAW_SKIN = "inventory_companions/generated/player_skin_raw.png";

    private static final AtomicBoolean LOADING = new AtomicBoolean(false);
    private static final AtomicBoolean REGENERATION_QUEUED = new AtomicBoolean(false);
    private static final AtomicLong REQUEST_REVISION = new AtomicLong();
    private static final ExecutorService SKIN_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "inventory-companions-skin-worker");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Status status = Status.IDLE;
    private static volatile String pendingNickname;
    private static volatile boolean selectPlayerOnSuccess;

    private PlayerSkinManager() {
    }

    public enum Status {
        IDLE,
        EMPTY_NICKNAME,
        BUSY,
        LOADING,
        REGENERATING,
        NEED_SAVED_SKIN,
        NOT_FOUND,
        ERROR
    }

    public static Status status() {
        return status;
    }

    public static boolean isLoading() {
        return LOADING.get();
    }

    public static void requestUpdate(String nickname) {
        String cleaned = sanitizeNickname(nickname);
        if (cleaned.isBlank()) {
            status = Status.EMPTY_NICKNAME;
            return;
        }
        if (!LOADING.compareAndSet(false, true)) {
            status = Status.BUSY;
            return;
        }

        REQUEST_REVISION.incrementAndGet();
        REGENERATION_QUEUED.set(false);
        pendingNickname = cleaned;
        selectPlayerOnSuccess = true;
        status = Status.LOADING;
        CompletableFuture.runAsync(() -> downloadAndPrepare(cleaned), SKIN_WORKER);
    }

    private static void downloadAndPrepare(String nickname) {
        try {
            BufferedImage skin = downloadSkin(nickname);
            writeRawSkin(skin);
            scheduleGeneration(skin);
        } catch (PlayerNotFoundException exception) {
            finishTerminal(Status.NOT_FOUND, null, null);
        } catch (IOException | RuntimeException exception) {
            finishTerminal(Status.ERROR, "Failed to update player skin for " + nickname, exception);
        }
    }

    public static void regenerateFromSavedSkin() {
        REQUEST_REVISION.incrementAndGet();
        if (!LOADING.compareAndSet(false, true)) {
            REGENERATION_QUEUED.set(true);
            return;
        }

        status = Status.REGENERATING;
        startRegeneration();
    }

    private static void startRegeneration() {
        String nickname = pendingNickname;
        if (nickname == null || nickname.isBlank()) {
            nickname = OverlayConfig.get().customPlayerNickname;
        }
        String fallbackNickname = nickname;
        CompletableFuture.runAsync(() -> loadSkinForRegeneration(fallbackNickname), SKIN_WORKER);
    }

    private static void loadSkinForRegeneration(String nickname) {
        try {
            Path skinPath = rawSkinPath();
            BufferedImage skin;

            if (Files.exists(skinPath)) {
                skin = ImageIO.read(skinPath.toFile());
                if (skin == null) {
                    throw new IOException("Saved player skin is not a readable PNG");
                }
            } else if (nickname != null && !nickname.isBlank()) {
                skin = downloadSkin(nickname);
                writeRawSkin(skin);
            } else {
                finishTerminal(Status.NEED_SAVED_SKIN, null, null);
                return;
            }

            scheduleGeneration(skin);
        } catch (PlayerNotFoundException exception) {
            finishTerminal(Status.NOT_FOUND, null, null);
        } catch (IOException | RuntimeException exception) {
            finishTerminal(Status.ERROR, "Failed to prepare saved player skin", exception);
        }
    }

    private static void scheduleGeneration(BufferedImage skin) {
        runOnClient(() -> {
            long revision = REQUEST_REVISION.get();
            REGENERATION_QUEUED.set(false);
            OverlayConfig.Config config = OverlayConfig.get();
            RenderSettings settings = RenderSettings.from(config);
            Path finalPath = generatedTexturePath();
            Path temporaryPath = temporaryTexturePath(revision);

            CompletableFuture.runAsync(() -> {
                try {
                    SkinTemplateGenerator.generateTemplateToFile(
                            skin,
                            temporaryPath,
                            settings.animation(),
                            settings.bodyPatternVariant(),
                            settings.handsPatternVariant(),
                            settings.legsPatternVariant()
                    );
                    commitGeneratedTexture(temporaryPath, finalPath, revision);
                } catch (IOException | RuntimeException exception) {
                    deleteQuietly(temporaryPath);
                    finishTerminal(Status.ERROR, "Failed to generate player skin template", exception);
                }
            }, SKIN_WORKER);
        });
    }

    private static void commitGeneratedTexture(Path temporaryPath, Path finalPath, long revision) {
        runOnClient(() -> {
            if (revision != REQUEST_REVISION.get()) {
                deleteQuietly(temporaryPath);
                LOADING.set(false);
                if (REGENERATION_QUEUED.getAndSet(false)) {
                    startQueuedRegeneration();
                }
                return;
            }

            try {
                moveGeneratedTexture(temporaryPath, finalPath);
                OverlayConfig.Config config = OverlayConfig.get();
                String nickname = pendingNickname;
                if (nickname != null && !nickname.isBlank()) {
                    config.customPlayerNickname = nickname;
                }
                config.customPlayerTexture = GENERATED_TEXTURE;
                if (selectPlayerOnSuccess) {
                    config.selectedAnimation = AnimationIds.PLAYER_SKIN;
                }
                OverlayConfig.syncPlayerSkinAnimation(config);
                OverlayConfig.save();
                OverlayRenderer.clearCache();
                REGENERATION_QUEUED.set(false);
                clearPendingRequest();
                status = Status.IDLE;
            } catch (IOException | RuntimeException exception) {
                deleteQuietly(temporaryPath);
                LOGGER.warn("Failed to commit generated player skin", exception);
                clearPendingRequest();
                REGENERATION_QUEUED.set(false);
                status = Status.ERROR;
            } finally {
                LOADING.set(false);
            }
        });
    }

    private static void startQueuedRegeneration() {
        if (!LOADING.compareAndSet(false, true)) {
            REGENERATION_QUEUED.set(true);
            return;
        }
        status = Status.REGENERATING;
        startRegeneration();
    }

    private static void finishTerminal(Status finalStatus, String logMessage, Exception exception) {
        if (logMessage != null && exception != null) {
            LOGGER.warn(logMessage, exception);
        }
        runOnClient(() -> {
            clearPendingRequest();
            REGENERATION_QUEUED.set(false);
            status = finalStatus;
            LOADING.set(false);
        });
    }

    private static void clearPendingRequest() {
        pendingNickname = null;
        selectPlayerOnSuccess = false;
    }

    private static void runOnClient(Runnable action) {
        Minecraft.getInstance().execute(action);
    }

    public static String sanitizeNickname(String nickname) {
        if (nickname == null) {
            return "";
        }
        String cleaned = nickname.trim().replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    private static void writeRawSkin(BufferedImage skin) throws IOException {
        Path path = rawSkinPath();
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(skin, "png", path.toFile())) {
            throw new IOException("PNG writer is unavailable");
        }
    }

    private static Path rawSkinPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(RAW_SKIN);
    }

    private static Path generatedTexturePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(GENERATED_TEXTURE);
    }

    private static Path temporaryTexturePath(long revision) {
        Path finalPath = generatedTexturePath();
        return finalPath.resolveSibling("player_skin.tmp." + revision + ".png");
    }

    private static void moveGeneratedTexture(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailure) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.debug("Failed to delete temporary skin file {}", path, exception);
        }
    }

    private static BufferedImage downloadSkin(String nickname) throws IOException {
        String encoded = URLEncoder.encode(nickname, StandardCharsets.UTF_8);
        JsonObject profile;
        try {
            profile = readJson("https://api.mojang.com/users/profiles/minecraft/" + encoded);
        } catch (HttpStatusException exception) {
            if (exception.statusCode() == 404) {
                throw new PlayerNotFoundException();
            }
            throw exception;
        }
        if (profile == null || !profile.has("id")) {
            throw new PlayerNotFoundException();
        }

        String uuid = profile.get("id").getAsString();
        JsonObject session = readJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
        JsonArray properties = session == null ? null : session.getAsJsonArray("properties");
        if (properties == null) {
            throw new IOException("Skin properties are missing");
        }

        String texturesBase64 = findTexturesProperty(properties);
        if (texturesBase64 == null) {
            throw new IOException("Textures property is missing");
        }

        String decoded = new String(Base64.getDecoder().decode(texturesBase64), StandardCharsets.UTF_8);
        JsonObject decodedRoot = JsonParser.parseString(decoded).getAsJsonObject();
        JsonObject texturesRoot = decodedRoot.getAsJsonObject("textures");
        JsonObject skin = texturesRoot == null ? null : texturesRoot.getAsJsonObject("SKIN");
        if (skin == null || !skin.has("url")) {
            throw new IOException("Skin URL is missing");
        }

        BufferedImage image = readImage(skin.get("url").getAsString());
        if (image == null) {
            throw new IOException("Downloaded skin is not a readable image");
        }
        return image;
    }

    private static String findTexturesProperty(JsonArray properties) {
        for (JsonElement element : properties) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject property = element.getAsJsonObject();
            if (property.has("name") && "textures".equals(property.get("name").getAsString()) && property.has("value")) {
                return property.get("value").getAsString();
            }
        }
        return null;
    }

    private static JsonObject readJson(String url) throws IOException {
        try (InputStream input = openUrl(url)) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return null;
            }
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IOException("Expected a JSON object from " + url);
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JSON response from " + url, exception);
        }
    }

    private static BufferedImage readImage(String url) throws IOException {
        try (InputStream input = openUrl(url)) {
            byte[] bytes = input.readAllBytes();
            return ImageIO.read(new ByteArrayInputStream(bytes));
        }
    }

    private static InputStream openUrl(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("User-Agent", "InventoryCompanions/1.1");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new HttpStatusException(code);
        }
        try {
            return new DisconnectingInputStream(connection);
        } catch (IOException exception) {
            connection.disconnect();
            throw exception;
        }
    }

    private record RenderSettings(
            String animation,
            int bodyPatternVariant,
            int handsPatternVariant,
            int legsPatternVariant
    ) {
        private static RenderSettings from(OverlayConfig.Config config) {
            return new RenderSettings(
                    config.customPlayerAnimation,
                    config.customPlayerBodyPatternVariant,
                    config.customPlayerHandsPatternVariant,
                    config.customPlayerLegsPatternVariant
            );
        }
    }

    private static final class DisconnectingInputStream extends InputStream {
        private final HttpURLConnection connection;
        private final InputStream delegate;

        private DisconnectingInputStream(HttpURLConnection connection) throws IOException {
            this.connection = connection;
            this.delegate = connection.getInputStream();
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            return delegate.readAllBytes();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                connection.disconnect();
            }
        }
    }

    private static final class HttpStatusException extends IOException {
        private final int statusCode;

        private HttpStatusException(int statusCode) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }

    private static final class PlayerNotFoundException extends IOException {
        private PlayerNotFoundException() {
            super("Player profile was not found");
        }
    }
}
