package ru.irku.inventorycompanions.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.irku.inventorycompanions.OverlayRenderer;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void irku_$drawCreativeOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CreativeModeInventoryScreen creativeScreen = (CreativeModeInventoryScreen) (Object) this;
        if (!creativeScreen.isInventoryOpen()) {
            return;
        }

        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        OverlayRenderer.drawCreativeInventory(graphics, screen.irku_$getLeftPos(), screen.irku_$getTopPos());
    }
}
