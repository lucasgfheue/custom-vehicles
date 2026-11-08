package com.vehiclestudio.data;

import java.util.Locale;

/** Cuando brilla una luz. */
public enum LightMode {
    /** Faro: con las luces encendidas (tecla de luces). */
    HEAD,
    /** Luz trasera: brilla al frenar y con las luces encendidas. */
    BRAKE,
    /** Siempre encendida. */
    ALWAYS;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LightMode byKey(String key, LightMode fallback) {
        for (LightMode m : values()) {
            if (m.key().equals(key)) {
                return m;
            }
        }
        return fallback;
    }
}
