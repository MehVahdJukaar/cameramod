package net.mehvahdjukaar.vista.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class ScrollBarWidget extends AbstractWidget {

    public enum Orientation {HORIZONTAL, VERTICAL}

    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;

    private final Orientation orientation;
    private final ResourceLocation handleSprite;
    private final int handleWidth;
    private final int handleHeight;

    @Nullable
    private ResourceLocation disabledSprite;
    @Nullable
    private BooleanSupplier usableCheck;
    @Nullable
    private DoubleConsumer onChanged;

    // when bound, the handle position lives outside this widget (e.g. a scrolling content view);
    // otherwise it is kept in internalValue
    @Nullable
    private DoubleSupplier externalGetter;
    @Nullable
    private DoubleConsumer externalSetter;
    private double internalValue;

    // optional number drawn on the handle, mapped from the [0,1] position onto [minValue, maxValue]
    private boolean showValue;
    private int minValue;
    private int maxValue;
    private int textColor = DEFAULT_TEXT_COLOR;

    private boolean dragging;

    public ScrollBarWidget(Orientation orientation, int x, int y, int width, int height,
                           ResourceLocation handleSprite, int handleWidth, int handleHeight) {
        super(x, y, width, height, Component.empty());
        this.orientation = orientation;
        this.handleSprite = handleSprite;
        this.handleWidth = handleWidth;
        this.handleHeight = handleHeight;
    }

    public ScrollBarWidget disabledSprite(ResourceLocation sprite) {
        this.disabledSprite = sprite;
        return this;
    }

    public ScrollBarWidget usableWhen(BooleanSupplier check) {
        this.usableCheck = check;
        return this;
    }

    public ScrollBarWidget onChanged(DoubleConsumer callback) {
        this.onChanged = callback;
        return this;
    }

    // back the handle position with an external offset instead of this widget's own field
    public ScrollBarWidget bind(DoubleSupplier getter, DoubleConsumer setter) {
        this.externalGetter = getter;
        this.externalSetter = setter;
        return this;
    }

    public ScrollBarWidget showValue(int min, int max) {
        this.showValue = true;
        this.minValue = min;
        this.maxValue = max;
        return this;
    }

    public ScrollBarWidget textColor(int color) {
        this.textColor = color;
        return this;
    }

    public ScrollBarWidget value(double fraction) {
        setValue(fraction);
        return this;
    }

    public double getValue() {
        return externalGetter != null ? externalGetter.getAsDouble() : internalValue;
    }

    // programmatic update: moves the handle without firing onChanged
    public void setValue(double fraction) {
        store(Mth.clamp(fraction, 0, 1));
    }

    public int getMappedValue() {
        return (int) Math.round(Mth.lerp(getValue(), minValue, maxValue));
    }

    public boolean isDragging() {
        return dragging;
    }

    void stopDragging() {
        this.dragging = false;
    }

    private void store(double fraction) {
        if (externalSetter != null) externalSetter.accept(fraction);
        else internalValue = fraction;
    }

    private boolean usable() {
        return usableCheck == null || usableCheck.getAsBoolean();
    }

    private int travel() {
        return orientation == Orientation.HORIZONTAL ? width - handleWidth : height - handleHeight;
    }

    private int handleX() {
        double value = getValue();
        return orientation == Orientation.HORIZONTAL
                ? getX() + (int) (value * travel())
                : getX() + (width - handleWidth) / 2;
    }

    private int handleY() {
        double value = getValue();
        return orientation == Orientation.VERTICAL
                ? getY() + (int) ((1 - value) * travel())
                : getY() + (height - handleHeight) / 2;
    }

    private void seek(double mouseX, double mouseY) {
        double v = orientation == Orientation.HORIZONTAL
                ? (mouseX - getX() - handleWidth / 2.0) / travel()
                : 1 - (mouseY - getY() - handleHeight / 2.0) / travel();
        v = Mth.clamp(v, 0, 1);
        store(v);
        if (onChanged != null) onChanged.accept(v);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ResourceLocation sprite = usable() || disabledSprite == null ? handleSprite : disabledSprite;
        int hx = handleX();
        int hy = handleY();
        g.blitSprite(sprite, hx, hy, handleWidth, handleHeight);

        if (showValue) {
            Font font = Minecraft.getInstance().font;
            String text = String.valueOf(getMappedValue());
            int tx = hx + (handleWidth - font.width(text)) / 2;
            int ty = hy + (handleHeight - font.lineHeight) / 2 + 1;
            g.drawString(font, text, tx, ty, textColor, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.dragging = false;
        if (button == 0 && usable() && isMouseOver(mouseX, mouseY)) {
            this.dragging = true;
            seek(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (this.dragging) seek(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!usable() || scrollY == 0 || !isMouseOver(mouseX, mouseY)) return false;
        double step = maxValue > minValue ? 1.0 / (maxValue - minValue) : 0.1;
        double v = Mth.clamp(getValue() + scrollY * step, 0, 1);
        store(v);
        if (onChanged != null) onChanged.accept(v);
        return true;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // handled in mouseClicked so the seek runs with the real cursor position
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }
}
