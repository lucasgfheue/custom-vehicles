package com.vehiclestudio.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Campo numerico enlazado a un valor. Se puede escribir o, con el campo activo, usar la rueda del
 * raton o las flechas arriba/abajo (Shift = paso fino, Ctrl = paso grande).
 */
public class NumberField extends EditBox {
    private final Supplier<Float> getter;
    private final Consumer<Float> setter;
    private final float min;
    private final float max;
    private final float step;
    private final boolean integer;
    private final Runnable onChange;

    public NumberField(Font font, int x, int y, int width, int height, Supplier<Float> getter, Consumer<Float> setter,
                       float min, float max, float step, boolean integer, Runnable onChange) {
        super(font, x, y, width, height, Component.empty());
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
        this.integer = integer;
        this.onChange = onChange;
        this.setMaxLength(12);
        this.setFilter(s -> s.matches("-?[0-9]*([.,][0-9]*)?"));
        this.setValue(format(getter.get()));
        this.moveCursorToStart(false);
        this.setResponder(this::onText);
    }

    private void onText(String text) {
        Float parsed = parse(text);
        if (parsed == null) {
            return;
        }
        float v = clamp(parsed);
        if (v != getter.get()) {
            setter.accept(v);
            onChange.run();
        }
    }

    private Float parse(String text) {
        if (text == null || text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) {
            return null;
        }
        try {
            return Float.parseFloat(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private float clamp(float v) {
        if (integer) {
            v = Math.round(v);
        }
        return Math.max(min, Math.min(max, v));
    }

    /** Suma o resta un paso (direction = +1 / -1). */
    public void nudge(double direction) {
        float s = step;
        if (Screen.hasShiftDown()) {
            s = integer ? 1f : step * 0.1f;
        } else if (Screen.hasControlDown()) {
            s = step * 5f;
        }
        float v = clamp(getter.get() + (float) (direction * s));
        v = Math.round(v * 10000f) / 10000f;
        setter.accept(v);
        this.setValue(format(v));
        this.moveCursorToStart(false);
        onChange.run();
    }

    /** Con el campo activo, las flechas arriba/abajo suben o bajan el valor un paso. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isFocused() && (keyCode == 265 || keyCode == 264)) {
            nudge(keyCode == 265 ? 1 : -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Vuelve a leer el valor (si el usuario no esta escribiendo en este campo). */
    public void refresh() {
        if (!this.isFocused()) {
            String text = format(getter.get());
            if (!text.equals(this.getValue())) {
                this.setValue(text);
                this.moveCursorToStart(false);
            }
        }
    }

    private String format(float v) {
        if (integer) {
            return String.valueOf(Math.round(v));
        }
        return Ui.fmt(v);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "NumberField[%s]", getValue());
    }
}
