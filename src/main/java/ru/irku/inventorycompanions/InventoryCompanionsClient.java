package ru.irku.inventorycompanions;

import net.fabricmc.api.ClientModInitializer;

public final class InventoryCompanionsClient implements ClientModInitializer {
    public static final String MOD_ID = "inventory_companions";
    public static final String MOD_NAME = "Inventory Companions";

    @Override
    public void onInitializeClient() {
        OverlayConfig.load();
    }
}
