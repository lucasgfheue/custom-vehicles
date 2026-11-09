package com.vehiclestudio.data;

import java.util.Locale;

/** Hacia donde mira una helice (el eje sobre el que gira). */
public enum PropAxis {
    /** Mirando hacia delante, como la de una avioneta (gira sobre el eje Z de la pieza). */
    FRONT,
    /** Mirando hacia arriba, como la de un helicoptero o un dron (gira sobre el eje Y de la pieza). */
    TOP;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PropAxis byKey(String key, PropAxis fallback) {
        for (PropAxis a : values()) {
            if (a.key().equals(key)) {
                return a;
            }
        }
        return fallback;
    }
}
