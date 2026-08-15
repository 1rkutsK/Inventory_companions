package ru.irku.inventorycompanions;

import java.util.List;

public final class AnimationIds {
    public static final String RANDOM = "random";
    public static final String PLAYER_SKIN = "player_skin";

    public static final String STATIC = "static";
    public static final String FISHING = "fishing";
    public static final String SWIMMING = "swimming";
    public static final String CAMPFIRE = "campfire";
    public static final String MINECART = "minecart";
    public static final String GRAVE = "grave";

    private static final List<String> PLAYER_ANIMATIONS = List.of(
            STATIC,
            FISHING,
            SWIMMING,
            CAMPFIRE,
            MINECART,
            GRAVE
    );

    private AnimationIds() {
    }

    public static List<String> playerAnimations() {
        return PLAYER_ANIMATIONS;
    }

    public static boolean isPlayerAnimation(String animation) {
        return PLAYER_ANIMATIONS.contains(animation);
    }

    public static String nextPlayerAnimation(String animation) {
        int index = PLAYER_ANIMATIONS.indexOf(animation);
        if (index < 0) {
            return STATIC;
        }
        return PLAYER_ANIMATIONS.get((index + 1) % PLAYER_ANIMATIONS.size());
    }
}
