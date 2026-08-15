package ru.irku.inventorycompanions;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

final class OffsetSlider extends AbstractSliderButton {
    private static final int MIN_VALUE = -2000;
    private static final int MAX_VALUE = 2000;

    private final String label;
    private final int labelX;
    private final int labelWidth;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private boolean dirty;

    OffsetSlider(int x, int y, int width, int height, String label, int labelX, int labelWidth,
                 IntSupplier getter, IntConsumer setter) {
        super(x, y, width, height, Component.empty(), toSliderValue(getter.getAsInt()));
        this.label = label;
        this.labelX = labelX;
        this.labelWidth = labelWidth;
        this.getter = getter;
        this.setter = setter;
        updateMessage();
    }

    void refreshFromConfig() {
        this.value = toSliderValue(getter.getAsInt());
        updateMessage();
    }

    void drawOverlay(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        String valueText = String.valueOf(getter.getAsInt());
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        boolean rowHovered = mouseX >= this.labelX
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
        boolean expanded = rowHovered || isFocused();
        int textY = y + 6;

        if (!expanded) {
            graphics.text(font, this.label, this.labelX + 10, textY, 0xFFFFFFFF, true);
            graphics.text(font, valueText, x + width - font.width(valueText) - 14, textY, 0xFFFFFFFF, true);
            return;
        }

        int labelMaxWidth = Math.max(50, this.labelWidth - 26);
        String labelText = UiText.ellipsize(font, this.label, labelMaxWidth);
        graphics.text(font, labelText, this.labelX + 10, textY, 0xFFFFFFFF, true);

        int valueSlotLeft = valueSlotLeft();
        int valueSlotWidth = valueSlotWidth();
        int valueTextX = valueSlotLeft + (valueSlotWidth - font.width(valueText)) / 2;
        graphics.text(font, valueText, valueTextX, textY, 0xFFFFFFFF, true);

        int trackLeft = trackLeft();
        int trackRight = trackRight();
        if (trackRight <= trackLeft + 8) {
            return;
        }

        int trackY = y + height / 2;
        int knobX = trackLeft + (int) Math.round((trackRight - trackLeft) * this.value);
        graphics.fill(trackLeft, trackY, trackRight, trackY + 1, 0xFFE6E6E6);
        graphics.fill(knobX - 2, y + 3, knobX + 3, y + height - 3, 0xFFE6E6E6);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0
                && this.active
                && mouseX >= getX()
                && mouseX < getX() + getWidth()
                && mouseY >= getY()
                && mouseY < getY() + getHeight()) {
            setFocused(true);
            setValueFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && this.active && isFocused()) {
            setValueFromMouse(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    protected void updateMessage() {
        if (this.label == null || this.getter == null) {
            setMessage(Component.empty());
            return;
        }
        setMessage(Component.literal(this.label + ": " + this.getter.getAsInt()));
    }

    @Override
    protected void applyValue() {
        setter.accept(fromSliderValue(this.value));
        dirty = true;
        updateMessage();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dirty) {
            OverlayConfig.save();
            dirty = false;
        }
        return super.mouseReleased(event);
    }

    private int valueSlotLeft() {
        return getX() + 6;
    }

    private static int valueSlotWidth() {
        return 34;
    }

    private int trackLeft() {
        return valueSlotLeft() + valueSlotWidth() + 2;
    }

    private int trackRight() {
        return getX() + getWidth() - 4;
    }

    private void setValueFromMouse(double mouseX) {
        int trackLeft = trackLeft();
        int trackRight = trackRight();
        int trackWidth = trackRight - trackLeft;
        if (trackWidth <= 0) {
            return;
        }

        double newValue = (mouseX - (double) trackLeft) / (double) trackWidth;
        this.value = Math.max(0.0D, Math.min(1.0D, newValue));
        applyValue();
        updateMessage();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double toSliderValue(int value) {
        int clamped = clamp(value, MIN_VALUE, MAX_VALUE);
        return (double) (clamped - MIN_VALUE) / (double) (MAX_VALUE - MIN_VALUE);
    }

    private static int fromSliderValue(double value) {
        return clamp((int) Math.round(MIN_VALUE + value * (MAX_VALUE - MIN_VALUE)), MIN_VALUE, MAX_VALUE);
    }

}
