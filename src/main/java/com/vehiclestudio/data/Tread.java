package com.vehiclestudio.data;

import java.util.Locale;

/** Dibujo de la goma de una rueda. */
public enum Tread {
    /** Lisa. */
    SMOOTH,
    /** Bloques: franjas mas oscuras y mas claras alrededor. */
    BLOCKS,
    /** Tacos de todoterreno que sobresalen. */
    KNOBBY,
    /** Tractor: tacos grandes en V. */
    CHEVRON;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Tread byKey(String key, Tread fallback) {
        for (Tread t : values()) {
            if (t.key().equals(key)) {
                return t;
            }
        }
        return fallback;
    }
}
