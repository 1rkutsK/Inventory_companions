package ru.irku.inventorycompanions;

public enum PlayerPatternPart {
    BODY("body"),
    HANDS("hands"),
    LEGS("legs");

    private final String resourceName;

    PlayerPatternPart(String resourceName) {
        this.resourceName = resourceName;
    }

    public String resourceName() {
        return resourceName;
    }
}
