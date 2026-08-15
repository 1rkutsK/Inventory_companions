package ru.irku.inventorycompanions;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class OverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayRenderer.class);
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final long FILE_SIGNATURE_CHECK_NANOS = 1_000_000_000L;

    private static WeakReference<Object> currentInventoryScreen = new WeakReference<>(null);
    private static String randomAnimationKey = "";

    private OverlayRenderer() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static void beginInventoryScreen(Object screen) {
        OverlayConfig.Config config = OverlayConfig.get();
        if (!AnimationIds.RANDOM.equals(config.selectedAnimation)) {
            currentInventoryScreen = new WeakReference<>(screen);
            randomAnimationKey = "";
            return;
        }

        Object previous = currentInventoryScreen.get();
        if (previous != screen || !isValidRandomKey(config, randomAnimationKey)) {
            currentInventoryScreen = new WeakReference<>(screen);
            randomAnimationKey = chooseRandomAnimationKey(config);
        }
    }

    private static OverlayConfig.AnimationSet selectedAnimationSet(OverlayConfig.Config config) {
        if (!AnimationIds.RANDOM.equals(config.selectedAnimation)) {
            return config.selected();
        }

        if (!isValidRandomKey(config, randomAnimationKey)) {
            randomAnimationKey = chooseRandomAnimationKey(config);
        }

        OverlayConfig.AnimationSet selected = config.animations.get(randomAnimationKey);
        return selected == null ? config.selected() : selected;
    }

    private static boolean isValidRandomKey(OverlayConfig.Config config, String key) {
        return key != null
                && !key.isBlank()
                && !AnimationIds.PLAYER_SKIN.equals(key)
                && !AnimationIds.RANDOM.equals(key)
                && config.animations.containsKey(key);
    }

    private static String chooseRandomAnimationKey(OverlayConfig.Config config) {
        List<String> candidates = new ArrayList<>();
        for (String key : config.animations.keySet()) {
            if (!AnimationIds.PLAYER_SKIN.equals(key) && !AnimationIds.RANDOM.equals(key)) {
                candidates.add(key);
            }
        }

        if (candidates.isEmpty()) {
            return "";
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public static void drawSurvivalInventory(GuiGraphicsExtractor graphics, int left, int top) {
        OverlayConfig.Config config = OverlayConfig.get();
        if (!config.enabled) {
            return;
        }

        drawConfigTexture(graphics, selectedAnimationSet(config).survivalInventory, left, top);
    }

    public static void drawCreativeInventory(GuiGraphicsExtractor graphics, int left, int top) {
        OverlayConfig.Config config = OverlayConfig.get();
        if (!config.enabled) {
            return;
        }

        drawConfigTexture(graphics, selectedAnimationSet(config).creativeInventory, left, top);
    }

    public static void drawConfigTexture(GuiGraphicsExtractor graphics, OverlayConfig.TextureConfig texture, int x, int y) {
        if (texture == null) {
            return;
        }

        x += texture.offsetX;
        y += texture.offsetY;

        Identifier resource = namespacedIdentifier(texture.texture);
        if (resource != null) {
            drawNamespacedTexture(graphics, resource, texture, x, y);
            return;
        }

        Frame frame = getCurrentFrame(texture);
        if (frame.isEmpty()) {
            return;
        }

        for (RectRun run : frame.runs) {
            graphics.fill(
                    x + run.x,
                    y + run.y,
                    x + run.x + run.width,
                    y + run.y + run.height,
                    run.argb);
        }
    }

    private static void drawNamespacedTexture(GuiGraphicsExtractor graphics, Identifier resource,
                                              OverlayConfig.TextureConfig texture, int x, int y) {
        int frameWidth = Math.max(1, texture.frameWidth);
        int frameHeight = Math.max(1, texture.frameHeight);
        int frameCount = Math.max(1, texture.frameCount);
        int frame = currentFrameIndex(texture.speedFps, frameCount);
        int imageHeight = Math.max(frameHeight, frameHeight * frameCount);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                resource,
                x,
                y,
                0.0F,
                (float) (frame * frameHeight),
                frameWidth,
                frameHeight,
                frameWidth,
                frameHeight,
                frameWidth,
                imageHeight);
    }

    public static void drawCharacterPreview(GuiGraphicsExtractor graphics, OverlayConfig.TextureConfig texture,
                                            int x, int y, int width, int height) {
        if (texture == null || width <= 0 || height <= 0) {
            return;
        }

        Frame frame = getCurrentFrame(texture);
        if (frame.isEmpty()) {
            return;
        }

        int spriteWidth = frame.maxX - frame.minX + 1;
        int spriteHeight = frame.maxY - frame.minY + 1;
        if (spriteWidth <= 0 || spriteHeight <= 0) {
            return;
        }

        int scaleX = Math.max(1, width / spriteWidth);
        int scaleY = Math.max(1, height / spriteHeight);
        int scale = Math.max(1, Math.min(scaleX, scaleY));

        int drawWidth = spriteWidth * scale;
        int drawHeight = spriteHeight * scale;
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;

        for (RectRun run : frame.runs) {
            int px = drawX + (run.x - frame.minX) * scale;
            int py = drawY + (run.y - frame.minY) * scale;
            graphics.fill(
                    px,
                    py,
                    px + run.width * scale,
                    py + run.height * scale,
                    run.argb);
        }
    }

    private static Frame getCurrentFrame(OverlayConfig.TextureConfig texture) {
        int frameCount = Math.max(1, texture.frameCount);
        int frameWidth = Math.max(1, texture.frameWidth);
        int frameHeight = Math.max(1, texture.frameHeight);

        String cacheKey = normalizedTexturePath(texture.texture)
                + "|" + frameWidth + "x" + frameHeight
                + "|" + frameCount;
        long now = System.nanoTime();
        CacheEntry entry = CACHE.get(cacheKey);
        if (entry == null) {
            String signature = sourceSignature(texture.texture);
            entry = new CacheEntry(signature,
                    SpriteSheet.load(texture.texture, frameWidth, frameHeight, frameCount),
                    now);
            CACHE.put(cacheKey, entry);
        } else if (now - entry.checkedAtNanos >= FILE_SIGNATURE_CHECK_NANOS) {
            String signature = sourceSignature(texture.texture);
            if (!entry.signature.equals(signature)) {
                entry = new CacheEntry(signature,
                        SpriteSheet.load(texture.texture, frameWidth, frameHeight, frameCount),
                        now);
            } else {
                entry = new CacheEntry(entry.signature, entry.sheet, now);
            }
            CACHE.put(cacheKey, entry);
        }

        SpriteSheet sheet = entry.sheet;
        if (sheet.frames.length == 0) {
            return Frame.EMPTY;
        }

        int frame = currentFrameIndex(texture.speedFps, sheet.frames.length);
        return sheet.frames[frame];
    }

    private static int currentFrameIndex(double speedFps, int frameCount) {
        double safeSpeed = Math.max(0.01D, speedFps);
        int safeFrames = Math.max(1, frameCount);
        long frameNanos = Math.max(1L, (long) (1_000_000_000D / safeSpeed));
        return (int) ((System.nanoTime() / frameNanos) % safeFrames);
    }

    private static Identifier namespacedIdentifier(String texturePath) {
        String normalized = normalizedTexturePath(texturePath);
        if (normalized.indexOf(':') <= 0) {
            return null;
        }

        try {
            return Identifier.parse(normalized);
        } catch (RuntimeException exception) {
            LOGGER.warn("Invalid texture identifier: {}", texturePath);
            return null;
        }
    }

    private static String normalizedTexturePath(String texturePath) {
        return texturePath == null ? "" : texturePath.trim().replace('\\', '/');
    }

    private static String sourceSignature(String texturePath) {
        String normalized = normalizedTexturePath(texturePath);
        if (normalized.isEmpty() || normalized.indexOf(':') >= 0) {
            return "resource:" + normalized;
        }

        Path file = resolveFileTexture(normalized);
        if (file == null) {
            return "resource-or-missing:" + normalized;
        }

        try {
            return "file:" + file.toAbsolutePath().normalize()
                    + ":" + Files.size(file)
                    + ":" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            return "file-unreadable:" + file.toAbsolutePath().normalize();
        }
    }

    private static Path resolveFileTexture(String texturePath) {
        try {
            Path direct = Path.of(texturePath);
            if (Files.isRegularFile(direct)) {
                return direct;
            }

            Path config = FabricLoader.getInstance().getConfigDir().resolve(texturePath);
            if (Files.isRegularFile(config)) {
                return config;
            }
        } catch (InvalidPathException exception) {
            return null;
        }
        return null;
    }

    private record CacheEntry(String signature, SpriteSheet sheet, long checkedAtNanos) {
    }

    private record RectRun(int x, int y, int width, int height, int argb) {
    }

    private static final class Frame {
        private static final Frame EMPTY = new Frame(new RectRun[0], 0, 0, -1, -1);

        private final RectRun[] runs;
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private Frame(RectRun[] runs, int minX, int minY, int maxX, int maxY) {
            this.runs = runs;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        private boolean isEmpty() {
            return runs.length == 0;
        }
    }

    private static final class SpriteSheet {
        private final Frame[] frames;

        private SpriteSheet(Frame[] frames) {
            this.frames = frames;
        }

        private static SpriteSheet load(String texturePath, int frameWidth, int frameHeight, int requestedFrames) {
            try (InputStream input = openTexture(texturePath)) {
                if (input == null) {
                    LOGGER.warn("Texture not found: {}", texturePath);
                    return new SpriteSheet(new Frame[0]);
                }

                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    LOGGER.warn("Failed to decode texture: {}", texturePath);
                    return new SpriteSheet(new Frame[0]);
                }

                int usableWidth = Math.min(frameWidth, image.getWidth());
                int availableFrames = Math.max(1, image.getHeight() / frameHeight);
                int frameCount = Math.max(1, Math.min(requestedFrames, availableFrames));

                Frame[] result = new Frame[frameCount];
                for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    result[frameIndex] = readFrame(image, frameIndex * frameHeight, usableWidth, frameHeight);
                }

                return new SpriteSheet(result);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Failed to load texture {}", texturePath, exception);
                return new SpriteSheet(new Frame[0]);
            }
        }

        private static Frame readFrame(BufferedImage image, int yOffset, int usableWidth, int frameHeight) {
            List<RectRun> completed = new ArrayList<>();
            Map<RunKey, RectRun> active = new HashMap<>();

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (int py = 0; py < frameHeight; py++) {
                int sourceY = yOffset + py;
                if (sourceY >= image.getHeight()) {
                    break;
                }

                List<RowRun> rowRuns = readRowRuns(image, sourceY, usableWidth);
                Map<RunKey, RectRun> next = new HashMap<>();

                for (RowRun row : rowRuns) {
                    RunKey key = new RunKey(row.x, row.width, row.argb);
                    RectRun previous = active.remove(key);
                    RectRun merged = previous == null
                            ? new RectRun(row.x, py, row.width, 1, row.argb)
                            : new RectRun(previous.x, previous.y, previous.width, previous.height + 1, previous.argb);
                    next.put(key, merged);

                    minX = Math.min(minX, row.x);
                    minY = Math.min(minY, py);
                    maxX = Math.max(maxX, row.x + row.width - 1);
                    maxY = Math.max(maxY, py);
                }

                completed.addAll(active.values());
                active = next;
            }

            completed.addAll(active.values());
            if (completed.isEmpty()) {
                return Frame.EMPTY;
            }

            return new Frame(completed.toArray(RectRun[]::new), minX, minY, maxX, maxY);
        }

        private static List<RowRun> readRowRuns(BufferedImage image, int sourceY, int usableWidth) {
            List<RowRun> rows = new ArrayList<>();
            int runStart = -1;
            int runColor = 0;

            for (int px = 0; px <= usableWidth; px++) {
                int argb = px < usableWidth ? image.getRGB(px, sourceY) : 0;
                boolean visible = px < usableWidth && ((argb >>> 24) & 0xFF) != 0;

                if (visible && runStart >= 0 && argb == runColor) {
                    continue;
                }

                if (runStart >= 0) {
                    rows.add(new RowRun(runStart, px - runStart, runColor));
                    runStart = -1;
                }

                if (visible) {
                    runStart = px;
                    runColor = argb;
                }
            }

            return rows;
        }

        private static InputStream openTexture(String texturePath) throws IOException {
            String normalized = normalizedTexturePath(texturePath);
            if (normalized.isEmpty()) {
                return null;
            }

            ClassLoader loader = OverlayRenderer.class.getClassLoader();
            InputStream input = openNamespacedResource(loader, normalized);
            if (input != null) {
                return input;
            }

            if (normalized.indexOf(':') < 0) {
                Path file = resolveFileTexture(normalized);
                if (file != null) {
                    return Files.newInputStream(file);
                }
            }

            input = loader.getResourceAsStream(normalized);
            if (input != null) {
                return input;
            }
            return loader.getResourceAsStream("assets/" + InventoryCompanionsClient.MOD_ID + "/" + normalized);
        }

        private static InputStream openNamespacedResource(ClassLoader loader, String texturePath) {
            int separator = texturePath.indexOf(':');
            if (separator <= 0) {
                return null;
            }

            String namespace = texturePath.substring(0, separator);
            String path = texturePath.substring(separator + 1);
            return loader.getResourceAsStream("assets/" + namespace + "/" + path);
        }

        private record RowRun(int x, int width, int argb) {
        }

        private record RunKey(int x, int width, int argb) {
        }
    }
}
