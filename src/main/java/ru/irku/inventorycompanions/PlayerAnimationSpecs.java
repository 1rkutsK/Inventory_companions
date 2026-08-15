package ru.irku.inventorycompanions;

import java.util.Map;

public final class PlayerAnimationSpecs {
    private static final Map<String, Spec> SPECS = Map.of(
            AnimationIds.STATIC, new Spec(14, 26, 1, 1.0D, 134, 53, 137, 14, 3, 3, 3),
            AnimationIds.FISHING, new Spec(36, 31, 9, 10.0D, 125, 48, 128, 9, 0, 3, 3),
            AnimationIds.SWIMMING, new Spec(31, 30, 12, 10.0D, 130, 49, 133, 10, 2, 2, 0),
            AnimationIds.CAMPFIRE, new Spec(47, 32, 8, 10.0D, 123, 47, 126, 8, 0, 3, 3),
            AnimationIds.MINECART, new Spec(28, 30, 56, 10.0D, 131, 49, 134, 10, 0, 0, 0),
            AnimationIds.GRAVE, new Spec(30, 25, 70, 10.0D, 130, 55, 133, 16, 3, 1, 0)
    );

    private PlayerAnimationSpecs() {
    }

    public static Spec get(String animation) {
        return SPECS.getOrDefault(animation, SPECS.get(AnimationIds.STATIC));
    }

    public record Spec(
            int frameWidth,
            int frameHeight,
            int frameCount,
            double speedFps,
            int survivalOffsetX,
            int survivalOffsetY,
            int creativeOffsetX,
            int creativeOffsetY,
            int bodyVariants,
            int handsVariants,
            int legsVariants
    ) {
        public int maxVariant(PlayerPatternPart part) {
            return switch (part) {
                case BODY -> bodyVariants;
                case HANDS -> handsVariants;
                case LEGS -> legsVariants;
            };
        }

        public OverlayConfig.PlayerAnimationOffsets defaultOffsets() {
            OverlayConfig.PlayerAnimationOffsets offsets = new OverlayConfig.PlayerAnimationOffsets();
            offsets.survivalOffsetX = survivalOffsetX;
            offsets.survivalOffsetY = survivalOffsetY;
            offsets.creativeOffsetX = creativeOffsetX;
            offsets.creativeOffsetY = creativeOffsetY;
            return offsets;
        }
    }
}
