package ru.irku.inventorycompanions.skin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.irku.inventorycompanions.AnimationIds;
import ru.irku.inventorycompanions.InventoryCompanionsClient;
import ru.irku.inventorycompanions.PlayerAnimationSpecs;
import ru.irku.inventorycompanions.PlayerPatternPart;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinTemplateGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkinTemplateGenerator.class);
    private static final String GUI = "/assets/" + InventoryCompanionsClient.MOD_ID + "/textures/gui/";
    private static final String STATIC_PATTERN = GUI + "pattern_companion.png";
    private static final String STATIC_SHADOW = GUI + "pattern_shadow.png";
    private static final String FISHING_PATTERN = GUI + "fishing_pattern.png";
    private static final String FISHING_SHADOW = GUI + "fishing_pattern_shadow.png";
    private static final String SWIMMING_PATTERN = GUI + "swimming_pattern.png";
    private static final String SWIMMING_SHADOW = GUI + "swimming_pattern_shadow.png";
    private static final String CAMPFIRE_PATTERN = GUI + "campfire_pattern.png";
    private static final String CAMPFIRE_SHADOW = GUI + "campfire_pattern_shadow.png";
    private static final String MINECART_PATTERN = GUI + "minecart_pattern.png";
    private static final String MINECART_SHADOW = GUI + "minecart_pattern_shadow.png";
    private static final String GRAVE_PATTERN = GUI + "grave_pattern.png";
    private static final String GRAVE_SHADOW = GUI + "grave_pattern_shadow.png";

    private static final Map<String, Optional<BufferedImage>> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, List<Point>>> SOURCE_POINT_CACHE = new ConcurrentHashMap<>();
    private static final Map<PatternKey, Optional<PatternData>> PATTERN_CACHE = new ConcurrentHashMap<>();

    private SkinTemplateGenerator() {
    }

    public static BufferedImage generateTemplate(BufferedImage inputSkin, String animation,
                                                 int bodyVariant, int handsVariant, int legsVariant) {
        if (inputSkin == null) {
            throw new IllegalArgumentException("inputSkin must not be null");
        }

        BufferedImage skin = normalizeTo64x64(inputSkin);
        return switch (animation) {
            case AnimationIds.FISHING -> generateFishing(skin, handsVariant, legsVariant);
            case AnimationIds.SWIMMING -> generateSwimming(skin, bodyVariant, handsVariant);
            case AnimationIds.CAMPFIRE -> generateCampfire(skin, handsVariant, legsVariant);
            case AnimationIds.MINECART -> generateMinecart(skin);
            case AnimationIds.GRAVE -> generateGrave(skin, bodyVariant);
            default -> generateStatic(skin, bodyVariant, handsVariant, legsVariant);
        };
    }

    public static void generateTemplateToFile(BufferedImage skin, Path output, String animation,
                                              int bodyVariant, int handsVariant, int legsVariant) throws IOException {
        BufferedImage image = generateTemplate(skin, animation, bodyVariant, handsVariant, legsVariant);
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("PNG writer is unavailable");
        }
    }

    private static BufferedImage generateStatic(BufferedImage skin, int bodyVariant, int handsVariant, int legsVariant) {
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.STATIC);
        PatternData pattern = patternData(STATIC_PATTERN, spec.frameHeight(), 1);
        if (pattern == null) {
            return new BufferedImage(spec.frameWidth(), spec.frameHeight(), BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage output = blankOutput(pattern);
        Map<Integer, List<Point>> destination = pattern.frame(0);
        applyComponent(skin, output, component(PlayerPatternPart.BODY, bodyVariant, false), destination, false);
        applyComponent(skin, output, component(PlayerPatternPart.BODY, bodyVariant, true), destination, true);
        applyComponent(skin, output, component(PlayerPatternPart.HANDS, handsVariant, false), destination, false);
        applyComponent(skin, output, component(PlayerPatternPart.HANDS, handsVariant, true), destination, true);
        applyComponent(skin, output, component(PlayerPatternPart.LEGS, legsVariant, false), destination, false);
        applyComponent(skin, output, component(PlayerPatternPart.LEGS, legsVariant, true), destination, true);
        applyShadow(output, STATIC_SHADOW);
        return output;
    }

    private static BufferedImage generateFishing(BufferedImage skin, int handsVariant, int legsVariant) {
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.FISHING);
        PatternData pattern = patternData(FISHING_PATTERN, spec.frameHeight(), spec.frameCount());
        if (pattern == null) {
            return generateStatic(skin, 1, handsVariant, legsVariant);
        }

        BufferedImage output = blankOutput(pattern);
        for (int frame = 0; frame < pattern.frameCount(); frame++) {
            Map<Integer, List<Point>> destination = pattern.frame(frame);
            applyComponent(skin, output, GUI + "fishing_body.png", destination, false);
            applyComponent(skin, output, GUI + "fishing_body_layer.png", destination, true);
            applyComponent(skin, output, fishingComponent(PlayerPatternPart.HANDS, handsVariant, false), destination, false);
            applyComponent(skin, output, fishingComponent(PlayerPatternPart.HANDS, handsVariant, true), destination, true);
            applyComponent(skin, output, fishingComponent(PlayerPatternPart.LEGS, legsVariant, false), destination, false);
            applyComponent(skin, output, fishingComponent(PlayerPatternPart.LEGS, legsVariant, true), destination, true);
        }
        applyShadow(output, FISHING_SHADOW);
        return output;
    }

    private static BufferedImage generateSwimming(BufferedImage skin, int bodyVariant, int handsVariant) {
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.SWIMMING);
        PatternData pattern = patternData(SWIMMING_PATTERN, spec.frameHeight(), spec.frameCount());
        if (pattern == null) {
            return generateStatic(skin, bodyVariant, handsVariant, 1);
        }

        BufferedImage output = blankOutput(pattern);
        for (int frame = 0; frame < pattern.frameCount(); frame++) {
            Map<Integer, List<Point>> destination = pattern.frame(frame);
            applyComponent(skin, output, swimmingComponent(PlayerPatternPart.BODY, bodyVariant, false), destination, false);
            applyComponent(skin, output, swimmingComponent(PlayerPatternPart.BODY, bodyVariant, true), destination, true);
            applyComponent(skin, output, swimmingComponent(PlayerPatternPart.HANDS, handsVariant, false), destination, false);
            applyComponent(skin, output, swimmingComponent(PlayerPatternPart.HANDS, handsVariant, true), destination, true);
        }
        applyShadow(output, SWIMMING_SHADOW);
        return output;
    }

    private static BufferedImage generateCampfire(BufferedImage skin, int handsVariant, int legsVariant) {
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.CAMPFIRE);
        PatternData pattern = patternData(CAMPFIRE_PATTERN, spec.frameHeight(), spec.frameCount());
        if (pattern == null) {
            return generateStatic(skin, 1, handsVariant, legsVariant);
        }

        BufferedImage output = blankOutput(pattern);
        for (int frame = 0; frame < pattern.frameCount(); frame++) {
            Map<Integer, List<Point>> destination = pattern.frame(frame);
            // Campfire body/legs use the same mapping images as fishing; keep one shared copy in the jar.
            applyComponent(skin, output, GUI + "fishing_body.png", destination, false);
            applyComponent(skin, output, GUI + "fishing_body_layer.png", destination, true);
            applyComponent(skin, output, campfireComponent(PlayerPatternPart.HANDS, handsVariant, false), destination, false);
            applyComponent(skin, output, campfireComponent(PlayerPatternPart.HANDS, handsVariant, true), destination, true);
            applyComponent(skin, output, fishingComponent(PlayerPatternPart.LEGS, legsVariant, false), destination, false);
            applyComponent(skin, output, fishingComponent(PlayerPatternPart.LEGS, legsVariant, true), destination, true);
        }
        applyShadow(output, CAMPFIRE_SHADOW);
        return output;
    }

    private static BufferedImage generateMinecart(BufferedImage skin) {
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.MINECART);
        PatternData pattern = patternData(MINECART_PATTERN, spec.frameHeight(), spec.frameCount());
        if (pattern == null) {
            return generateStatic(skin, 1, 1, 1);
        }

        BufferedImage output = blankOutput(pattern);
        for (int frame = 0; frame < pattern.frameCount(); frame++) {
            Map<Integer, List<Point>> destination = pattern.frame(frame);
            applyComponent(skin, output, GUI + "minecart_all.png", destination, false);
            applyComponent(skin, output, GUI + "minecart_all_layer.png", destination, true);
        }
        applyShadow(output, MINECART_SHADOW);
        return output;
    }

    private static BufferedImage generateGrave(BufferedImage skin, int bodyVariant) {
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.GRAVE);
        PatternData pattern = patternData(GRAVE_PATTERN, spec.frameHeight(), spec.frameCount());
        if (pattern == null) {
            return generateStatic(skin, bodyVariant, 1, 1);
        }

        BufferedImage output = blankOutput(pattern);
        for (int frame = 0; frame < pattern.frameCount(); frame++) {
            Map<Integer, List<Point>> destination = pattern.frame(frame);
            applyComponent(skin, output, graveBodyComponent(bodyVariant, false), destination, false);
            applyComponent(skin, output, graveBodyComponent(bodyVariant, true), destination, true);
            applyComponent(skin, output, GUI + "grave1_hands.png", destination, false);
            applyComponent(skin, output, GUI + "grave1_hands_layer.png", destination, true);
        }
        applyShadow(output, GRAVE_SHADOW);
        return output;
    }

    private static BufferedImage blankOutput(PatternData pattern) {
        return new BufferedImage(pattern.image().getWidth(), pattern.image().getHeight(), BufferedImage.TYPE_INT_ARGB);
    }

    private static String component(PlayerPatternPart part, int variant, boolean layer) {
        return GUI + clampVariant(variant) + "_pattern_" + part.resourceName() + layerSuffix(layer) + ".png";
    }

    private static String fishingComponent(PlayerPatternPart part, int variant, boolean layer) {
        return GUI + "fishing" + clampVariant(variant) + "_pattern_" + part.resourceName() + layerSuffix(layer) + ".png";
    }

    private static String swimmingComponent(PlayerPatternPart part, int variant, boolean layer) {
        int swimmingVariant = Math.max(1, Math.min(2, variant));
        return GUI + "swimming" + swimmingVariant + "_" + part.resourceName() + layerSuffix(layer) + ".png";
    }

    private static String campfireComponent(PlayerPatternPart part, int variant, boolean layer) {
        return GUI + "campfire" + clampVariant(variant) + "_pattern_" + part.resourceName() + layerSuffix(layer) + ".png";
    }

    private static String graveBodyComponent(int variant, boolean layer) {
        return GUI + "grave" + clampVariant(variant) + "_body" + layerSuffix(layer) + ".png";
    }

    private static String layerSuffix(boolean layer) {
        return layer ? "_layer" : "";
    }

    private static int clampVariant(int value) {
        return Math.max(1, Math.min(3, value));
    }

    private static void applyComponent(BufferedImage skin, BufferedImage output, String sourceResource,
                                       Map<Integer, List<Point>> destinations, boolean layer) {
        Map<Integer, List<Point>> sources = sourcePoints(sourceResource);
        if (sources.isEmpty() || destinations.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, List<Point>> entry : destinations.entrySet()) {
            List<Point> sourcePoints = sources.get(entry.getKey());
            if (sourcePoints == null || sourcePoints.isEmpty()) {
                continue;
            }

            List<Point> destinationPoints = entry.getValue();
            int count = Math.min(sourcePoints.size(), destinationPoints.size());
            for (int i = 0; i < count; i++) {
                Point source = sourcePoints.get(i);
                Point destination = destinationPoints.get(i);
                int color = skin.getRGB(source.x(), source.y());
                int alpha = color >>> 24;
                if (alpha == 0) {
                    continue;
                }

                int destinationColor = output.getRGB(destination.x(), destination.y());
                output.setRGB(
                        destination.x(),
                        destination.y(),
                        layer && alpha < 255 ? blend(color, destinationColor) : color
                );
            }
        }
    }

    private static Map<Integer, List<Point>> sourcePoints(String resource) {
        return SOURCE_POINT_CACHE.computeIfAbsent(resource, key -> {
            BufferedImage image = loadResource(key);
            return image == null ? Collections.emptyMap() : collect(image, 0, image.getHeight());
        });
    }

    private static PatternData patternData(String resource, int frameHeight, int requestedFrames) {
        PatternKey key = new PatternKey(resource, Math.max(1, frameHeight), Math.max(1, requestedFrames));
        return PATTERN_CACHE.computeIfAbsent(key, SkinTemplateGenerator::loadPatternData).orElse(null);
    }

    private static Optional<PatternData> loadPatternData(PatternKey key) {
        BufferedImage image = loadResource(key.resource());
        if (image == null) {
            return Optional.empty();
        }

        int availableFrames = Math.max(1, image.getHeight() / key.frameHeight());
        int frameCount = Math.min(key.requestedFrames(), availableFrames);
        List<Map<Integer, List<Point>>> frames = new ArrayList<>(frameCount);
        for (int frame = 0; frame < frameCount; frame++) {
            frames.add(collect(image, frame * key.frameHeight(), key.frameHeight()));
        }
        return Optional.of(new PatternData(image, List.copyOf(frames)));
    }

    private static Map<Integer, List<Point>> collect(BufferedImage image, int startY, int height) {
        Map<Integer, List<Point>> result = new HashMap<>();
        int endY = Math.min(image.getHeight(), Math.max(0, startY) + Math.max(0, height));
        for (int y = Math.max(0, startY); y < endY; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                if ((color >>> 24) == 0) {
                    continue;
                }
                result.computeIfAbsent(color, ignored -> new ArrayList<>()).add(new Point(x, y));
            }
        }
        return result;
    }

    private static void applyShadow(BufferedImage output, String resource) {
        BufferedImage shadow = loadResource(resource);
        if (shadow == null) {
            return;
        }

        int height = Math.min(output.getHeight(), shadow.getHeight());
        int width = Math.min(output.getWidth(), shadow.getWidth());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = shadow.getRGB(x, y);
                if ((color >>> 24) != 0) {
                    output.setRGB(x, y, blend(color, output.getRGB(x, y)));
                }
            }
        }
    }

    private static BufferedImage loadResource(String resource) {
        return IMAGE_CACHE.computeIfAbsent(resource, SkinTemplateGenerator::readResource).orElse(null);
    }

    private static Optional<BufferedImage> readResource(String resource) {
        try (InputStream stream = SkinTemplateGenerator.class.getResourceAsStream(resource)) {
            if (stream == null) {
                LOGGER.warn("Skin template resource is missing: {}", resource);
                return Optional.empty();
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                LOGGER.warn("Skin template resource is not a readable image: {}", resource);
                return Optional.empty();
            }
            return Optional.of(image);
        } catch (IOException exception) {
            LOGGER.warn("Failed to load skin template resource {}", resource, exception);
            return Optional.empty();
        }
    }

    private static BufferedImage normalizeTo64x64(BufferedImage input) {
        if (input.getWidth() == 64 && input.getHeight() == 64) {
            return input;
        }

        BufferedImage output = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int height = Math.min(64, input.getHeight());
        int width = Math.min(64, input.getWidth());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output.setRGB(x, y, input.getRGB(x, y));
            }
        }
        return output;
    }

    private static int blend(int top, int bottom) {
        int topAlpha = (top >>> 24) & 255;
        int bottomAlpha = (bottom >>> 24) & 255;
        int outputAlpha = topAlpha + (bottomAlpha * (255 - topAlpha) + 127) / 255;
        if (outputAlpha == 0) {
            return 0;
        }

        int topRed = (top >>> 16) & 255;
        int topGreen = (top >>> 8) & 255;
        int topBlue = top & 255;
        int bottomRed = (bottom >>> 16) & 255;
        int bottomGreen = (bottom >>> 8) & 255;
        int bottomBlue = bottom & 255;

        int red = (topRed * topAlpha * 255 + bottomRed * bottomAlpha * (255 - topAlpha)) / (outputAlpha * 255);
        int green = (topGreen * topAlpha * 255 + bottomGreen * bottomAlpha * (255 - topAlpha)) / (outputAlpha * 255);
        int blue = (topBlue * topAlpha * 255 + bottomBlue * bottomAlpha * (255 - topAlpha)) / (outputAlpha * 255);
        return (outputAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private record Point(int x, int y) {
    }

    private record PatternKey(String resource, int frameHeight, int requestedFrames) {
    }

    private record PatternData(BufferedImage image, List<Map<Integer, List<Point>>> frames) {
        private int frameCount() {
            return frames.size();
        }

        private Map<Integer, List<Point>> frame(int index) {
            return frames.get(index);
        }
    }
}
