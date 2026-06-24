package ru.irku.inventorycompanions;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OverlayRenderer {
    private static final Map<String, SpriteSheet> CACHE = new ConcurrentHashMap<>();

    private OverlayRenderer() {
    }

    public static void drawSurvivalInventory(GuiGraphicsExtractor graphics, int left, int top) {
        OverlayConfig.Config config = OverlayConfig.get();
        if (!config.enabled) {
            return;
        }

        OverlayConfig.TextureConfig texture = config.selected().survivalInventory;
        drawConfigTexture(graphics, texture, left, top);
    }

    public static void drawCreativeInventory(GuiGraphicsExtractor graphics, int left, int top) {
        OverlayConfig.Config config = OverlayConfig.get();
        if (!config.enabled) {
            return;
        }

        OverlayConfig.TextureConfig texture = config.selected().creativeInventory;
        drawConfigTexture(graphics, texture, left, top);
    }

    public static void drawConfigTexture(GuiGraphicsExtractor graphics, OverlayConfig.TextureConfig texture, int x, int y) {
        if (texture == null) {
            return;
        }

        x += texture.offsetX;
        y += texture.offsetY;

        Pixel[] framePixels = getCurrentFramePixels(texture);
        if (framePixels.length == 0) {
            return;
        }

        for (Pixel pixel : framePixels) {
            graphics.fill(x + pixel.x, y + pixel.y, x + pixel.x + 1, y + pixel.y + 1, pixel.argb);
        }
    }

    public static void drawCharacterPreview(GuiGraphicsExtractor graphics, OverlayConfig.TextureConfig texture, int x, int y, int width, int height) {
        if (texture == null || width <= 0 || height <= 0) {
            return;
        }

        Pixel[] framePixels = getCurrentFramePixels(texture);
        if (framePixels.length == 0) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Pixel pixel : framePixels) {
            if (pixel.x < minX) minX = pixel.x;
            if (pixel.y < minY) minY = pixel.y;
            if (pixel.x > maxX) maxX = pixel.x;
            if (pixel.y > maxY) maxY = pixel.y;
        }

        if (minX > maxX || minY > maxY) {
            return;
        }

        int spriteWidth = maxX - minX + 1;
        int spriteHeight = maxY - minY + 1;
        int scaleX = Math.max(1, width / spriteWidth);
        int scaleY = Math.max(1, height / spriteHeight);
        int scale = Math.max(1, Math.min(scaleX, scaleY));

        int drawWidth = spriteWidth * scale;
        int drawHeight = spriteHeight * scale;
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;

        for (Pixel pixel : framePixels) {
            int px = drawX + (pixel.x - minX) * scale;
            int py = drawY + (pixel.y - minY) * scale;
            graphics.fill(px, py, px + scale, py + scale, pixel.argb);
        }
    }

    private static Pixel[] getCurrentFramePixels(OverlayConfig.TextureConfig texture) {
        int frameCount = Math.max(1, texture.frameCount);
        int frameWidth = Math.max(1, texture.frameWidth);
        int frameHeight = Math.max(1, texture.frameHeight);
        double speedFps = Math.max(0.01D, texture.speedFps);

        String key = texture.texture + "|" + frameWidth + "x" + frameHeight + "|" + frameCount;
        SpriteSheet sheet = CACHE.computeIfAbsent(key, ignored -> SpriteSheet.load(texture.texture, frameWidth, frameHeight, frameCount));
        if (sheet.frames.length == 0) {
            return new Pixel[0];
        }

        long frameNanos = Math.max(1L, (long) (1_000_000_000D / speedFps));
        int frame = (int) ((System.nanoTime() / frameNanos) % sheet.frames.length);
        return sheet.frames[frame];
    }

    private record Pixel(int x, int y, int argb) {
    }

    private record SpriteSheet(Pixel[][] frames) {
        static SpriteSheet load(String texturePath, int frameWidth, int frameHeight, int requestedFrames) {
            try (InputStream input = openTexture(texturePath)) {
                if (input == null) {
                    System.err.println("[Inventory Companions] Texture not found: " + texturePath);
                    return new SpriteSheet(new Pixel[0][]);
                }

                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    System.err.println("[Inventory Companions] Failed to decode texture: " + texturePath);
                    return new SpriteSheet(new Pixel[0][]);
                }

                int usableWidth = Math.min(frameWidth, image.getWidth());
                int availableFrames = Math.max(1, image.getHeight() / frameHeight);
                int frames = Math.max(1, Math.min(requestedFrames, availableFrames));

                Pixel[][] result = new Pixel[frames][];
                for (int frame = 0; frame < frames; frame++) {
                    List<Pixel> pixels = new ArrayList<>();
                    int yOffset = frame * frameHeight;
                    for (int py = 0; py < frameHeight; py++) {
                        int sourceY = yOffset + py;
                        if (sourceY >= image.getHeight()) {
                            break;
                        }

                        for (int px = 0; px < usableWidth; px++) {
                            int argb = image.getRGB(px, sourceY);
                            int alpha = (argb >>> 24) & 0xFF;
                            if (alpha != 0) {
                                pixels.add(new Pixel(px, py, argb));
                            }
                        }
                    }
                    result[frame] = pixels.toArray(Pixel[]::new);
                }

                return new SpriteSheet(result);
            } catch (Exception exception) {
                System.err.println("[Inventory Companions] Failed to load texture " + texturePath + ": " + exception.getMessage());
                return new SpriteSheet(new Pixel[0][]);
            }
        }

        private static InputStream openTexture(String texturePath) throws IOException {
            String normalized = texturePath == null ? "" : texturePath.trim().replace('\\', '/');
            if (normalized.isEmpty()) {
                return null;
            }

            ClassLoader loader = OverlayRenderer.class.getClassLoader();

            int separator = normalized.indexOf(':');
            boolean namespacedId = separator > 1;

            if (namespacedId) {
                String namespace = normalized.substring(0, separator);
                String path = normalized.substring(separator + 1);
                InputStream namespaced = loader.getResourceAsStream("assets/" + namespace + "/" + path);
                if (namespaced != null) {
                    return namespaced;
                }
            } else {
                try {
                    Path directPath = Path.of(normalized);
                    if (Files.exists(directPath)) {
                        return Files.newInputStream(directPath);
                    }
                } catch (RuntimeException ignored) {
                    
                }

                try {
                    Path configRelativePath = FabricLoader.getInstance().getConfigDir().resolve(normalized);
                    if (Files.exists(configRelativePath)) {
                        return Files.newInputStream(configRelativePath);
                    }
                } catch (RuntimeException ignored) {
                    
                }
            }

            InputStream raw = loader.getResourceAsStream(normalized);
            if (raw != null) {
                return raw;
            }

            return loader.getResourceAsStream("assets/" + InventoryCompanionsClient.MOD_ID + "/" + normalized);
        }
    }
}
