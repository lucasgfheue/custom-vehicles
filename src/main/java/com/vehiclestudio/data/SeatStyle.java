package com.vehiclestudio.data;

import java.util.Locale;

/** Como es un asiento (se dibuja dentro de su cubo, mirando hacia delante, -Z). */
public enum SeatStyle {
    /** De coche: cojin, respaldo y reposacabezas. */
    CAR(0.35f),
    /** Banco corrido: cojin ancho y respaldo bajo. */
    BENCH(0.4f),
    /** Sillin de moto: solo un cojin alargado. */
    SADDLE(1.0f),
    /** De carreras (karts): bajo y con laterales. */
    RACING(0.25f),
    /** Silla de madera: asiento fino y respaldo alto. */
    CHAIR(0.45f),
    /** Invisible: solo el sitio donde se sienta (cuando la carroceria ya parece un asiento). */
    INVISIBLE(0.0f);

    private final float cushion;

    SeatStyle(float cushion) {
        this.cushion = cushion;
    }

    /** Altura de la superficie donde se sienta, en fraccion de la altura del cubo. */
    public float cushion() {
        return cushion;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SeatStyle byKey(String key, SeatStyle fallback) {
        for (SeatStyle s : values()) {
            if (s.key().equals(key)) {
                return s;
            }
        }
        return fallback;
    }
}
