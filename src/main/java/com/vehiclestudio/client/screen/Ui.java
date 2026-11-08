package com.vehiclestudio.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.Locale;

/** Colores y pequenas utilidades de dibujo compartidas por las pantallas del mod. */
public final class Ui {
    public static final int BG = 0xFF1B1D22;
    public static final int PANEL = 0xFF252830;
    public static final int PANEL_DARK = 0xFF1F2128;
    public static final int BORDER = 0xFF3A3F4B;
    public static final int TEXT = 0xFFE8E8E8;
    public static final int MUTED = 0xFF9AA1AD;
    public static final int ACCENT = 0xFF6FB7FF;
    public static final int SELECT = 0xFF33507D;
    public static final int ERROR = 0xFFFF6B6B;
    public static final int OK = 0xFF7CDB7C;
    public static final int PREVIEW_TOP = 0xFF2C3240;
    public static final int PREVIEW_BOTTOM = 0xFF121418;

    private Ui() {
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.vehiclestudio." + key, args);
    }

    public static MutableComponent onOff(String key, boolean value) {
        return tr(key).append(": ").append(tr(value ? "on" : "off"));
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, BORDER);
    }

    /** Panel flotante con sombra (para los selectores de color, textura y plantilla). */
    public static void popup(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x80000000);
        g.fill(x, y, x + w, y + h, 0xFF2B2F38);
        g.renderOutline(x, y, w, h, 0xFF5A6070);
    }

    public static void swatch(GuiGraphics g, int x, int y, int size, int rgb, boolean highlighted) {
        g.fill(x, y, x + size, y + size, 0xFF000000 | rgb);
        g.renderOutline(x, y, size, size, highlighted ? 0xFFFFFFFF : 0xFF000000);
    }

    /** Boton dibujado a mano (para paneles flotantes). */
    public static void flatButton(GuiGraphics g, Font font, int x, int y, int w, int h, Component text, boolean hover) {
        g.fill(x, y, x + w, y + h, hover ? 0xFF4A5468 : 0xFF3A4150);
        g.renderOutline(x, y, w, h, hover ? 0xFFFFFFFF : 0xFF5A6070);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Dibuja texto partido en lineas; devuelve la Y siguiente. */
    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, Math.max(10, width))) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    public static String fmt(float v) {
        if (Math.abs(v - Math.round(v)) < 1.0e-4f) {
            return String.valueOf(Math.round(v));
        }
        String s = String.format(Locale.ROOT, "%.3f", v);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // ---------------------------------------------------------------------------------------
    // Color HSV
    // ---------------------------------------------------------------------------------------

    /** h, s, v en [0,1] -> RGB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        float c = v * s;
        float hp = h * 6f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r = 0, gg = 0, b = 0;
        if (hp < 1) {
            r = c;
            gg = x;
        } else if (hp < 2) {
            r = x;
            gg = c;
        } else if (hp < 3) {
            gg = c;
            b = x;
        } else if (hp < 4) {
            gg = x;
            b = c;
        } else if (hp < 5) {
            r = x;
            b = c;
        } else {
            r = c;
            b = x;
        }
        float m = v - c;
        int ri = Math.round((r + m) * 255f);
        int gi = Math.round((gg + m) * 255f);
        int bi = Math.round((b + m) * 255f);
        return (clamp255(ri) << 16) | (clamp255(gi) << 8) | clamp255(bi);
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d < 1.0e-6f) {
            h = 0f;
        } else if (max == r) {
            h = ((g - b) / d) / 6f;
        } else if (max == g) {
            h = ((b - r) / d + 2f) / 6f;
        } else {
            h = ((r - g) / d + 4f) / 6f;
        }
        h = (h % 1f + 1f) % 1f;
        float s = max <= 0f ? 0f : d / max;
        return new float[]{h, s, max};
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
