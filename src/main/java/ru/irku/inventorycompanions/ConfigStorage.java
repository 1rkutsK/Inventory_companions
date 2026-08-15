package ru.irku.inventorycompanions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

final class ConfigStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long RELOAD_CHECK_INTERVAL_NANOS = 750_000_000L;

    private final String fileName;
    private final String legacyFileName;
    private final Logger logger;

    private Path path;
    private long lastModified = -1L;
    private long nextReloadCheckNanos;

    ConfigStorage(String fileName, String legacyFileName, Logger logger) {
        this.fileName = fileName;
        this.legacyFileName = legacyFileName;
        this.logger = logger;
    }

    <T> T load(Class<T> type, Supplier<T> defaults) {
        Path configPath = path();
        try {
            prepareFile(configPath, defaults);
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                T value = GSON.fromJson(reader, type);
                updateFileState();
                return value == null ? defaults.get() : value;
            }
        } catch (JsonParseException exception) {
            return recoverBrokenConfig(defaults, exception);
        } catch (IOException exception) {
            logger.error("Failed to load config, using in-memory defaults", exception);
            return defaults.get();
        }
    }

    void save(Object value) {
        try {
            writeAtomically(path(), value);
            updateFileState();
        } catch (IOException exception) {
            logger.error("Failed to save config", exception);
        }
    }

    boolean shouldReload() {
        Path configPath = path();
        long now = System.nanoTime();
        if (now < nextReloadCheckNanos) {
            return false;
        }
        nextReloadCheckNanos = now + RELOAD_CHECK_INTERVAL_NANOS;

        try {
            if (!Files.exists(configPath)) {
                return true;
            }
            return Files.getLastModifiedTime(configPath).toMillis() != lastModified;
        } catch (IOException exception) {
            logger.warn("Failed to check config timestamp", exception);
            return false;
        }
    }

    private Path path() {
        if (path == null) {
            path = FabricLoader.getInstance().getConfigDir().resolve(fileName);
        }
        return path;
    }

    private void prepareFile(Path configPath, Supplier<?> defaults) throws IOException {
        Files.createDirectories(configPath.getParent());
        if (Files.exists(configPath)) {
            return;
        }

        Path legacyPath = configPath.resolveSibling(legacyFileName);
        if (Files.exists(legacyPath)) {
            Files.copy(legacyPath, configPath);
            updateFileState();
            return;
        }

        writeAtomically(configPath, defaults.get());
        updateFileState();
    }

    private <T> T recoverBrokenConfig(Supplier<T> defaults, JsonParseException parseException) {
        logger.error("Config is invalid; backing it up and restoring defaults", parseException);
        T fallback = defaults.get();
        Path configPath = path();

        try {
            if (Files.exists(configPath)) {
                Path backup = configPath.resolveSibling(configPath.getFileName() + ".broken-" + System.currentTimeMillis());
                Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
                logger.warn("Broken config backed up to {}", backup);
            }
            writeAtomically(configPath, fallback);
            updateFileState();
        } catch (IOException exception) {
            logger.error("Failed to recover broken config; continuing with in-memory defaults", exception);
        }
        return fallback;
    }

    private void writeAtomically(Path configPath, Object value) throws IOException {
        Files.createDirectories(configPath.getParent());
        Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");

        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }

            try {
                Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void updateFileState() throws IOException {
        lastModified = Files.getLastModifiedTime(path()).toMillis();
        nextReloadCheckNanos = System.nanoTime() + RELOAD_CHECK_INTERVAL_NANOS;
    }
}
