package ru.irku.inventorycompanions.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.irku.inventorycompanions.OverlayRenderer;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void irku_$drawSurvivalOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        OverlayRenderer.drawSurvivalInventory(graphics, screen.irku_$getLeftPos(), screen.irku_$getTopPos());
    }
}
