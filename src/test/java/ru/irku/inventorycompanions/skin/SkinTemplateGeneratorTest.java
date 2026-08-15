package ru.irku.inventorycompanions.skin;

import org.junit.jupiter.api.Test;
import ru.irku.inventorycompanions.AnimationIds;
import ru.irku.inventorycompanions.PlayerAnimationSpecs;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class SkinTemplateGeneratorTest {
    @Test
    void generatesStaticTemplateWithExpectedSize() {
        BufferedImage skin = solidSkin();

        BufferedImage output = SkinTemplateGenerator.generateTemplate(skin, AnimationIds.STATIC, 1, 1, 1);
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.STATIC);

        assertEquals(spec.frameWidth(), output.getWidth());
        assertEquals(spec.frameHeight() * spec.frameCount(), output.getHeight());
        assertTrue(hasVisiblePixel(output));
    }

    @Test
    void generatesFishingSheetWithAllFrames() {
        BufferedImage skin = solidSkin();

        BufferedImage output = SkinTemplateGenerator.generateTemplate(skin, AnimationIds.FISHING, 1, 1, 1);
        PlayerAnimationSpecs.Spec spec = PlayerAnimationSpecs.get(AnimationIds.FISHING);

        assertEquals(spec.frameWidth(), output.getWidth());
        assertEquals(spec.frameHeight() * spec.frameCount(), output.getHeight());
        assertTrue(hasVisiblePixel(output));
    }

    private static BufferedImage solidSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < skin.getHeight(); y++) {
            for (int x = 0; x < skin.getWidth(); x++) {
                skin.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        return skin;
    }

    private static boolean hasVisiblePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
