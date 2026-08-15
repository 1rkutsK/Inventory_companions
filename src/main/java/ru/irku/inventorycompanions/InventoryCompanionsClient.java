package ru.irku.inventorycompanions;

import net.fabricmc.api.ClientModInitializer;

public final class InventoryCompanionsClient implements ClientModInitializer {
    // Legacy id kept intentionally: changing it would break existing configs/resource references.
    public static final String MOD_ID = "animated_inventory_overlay";

    @Override
    public void onInitializeClient() {
        OverlayConfig.load();
    }
}
