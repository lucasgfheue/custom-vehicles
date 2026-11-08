package com.vehiclestudio.data;

import java.util.Locale;

/** Como es la llanta de una rueda (lo que se ve en el lateral, dentro de la goma). */
public enum RimStyle {
    /** Disco liso con un tapacubos en el centro. */
    DISC(0),
    /** Tapacubos: disco con un aro marcado. */
    HUBCAP(0),
    /** Cinco radios gruesos. */
    SPOKES(5),
    /** Estrella deportiva de diez radios finos. */
    STAR(10),
    /** Bicicleta: muchos radios muy finos. */
    WIRE(16),
    /** Madera: ocho radios gruesos (carretas). */
    WOOD(8);

    private final int spokes;

    RimStyle(int spokes) {
        this.spokes = spokes;
    }

    /** Radios que tiene (0 = disco). */
    public int spokes() {
        return spokes;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RimStyle byKey(String key, RimStyle fallback) {
        for (RimStyle r : values()) {
            if (r.key().equals(key)) {
                return r;
            }
        }
        return fallback;
    }
}
