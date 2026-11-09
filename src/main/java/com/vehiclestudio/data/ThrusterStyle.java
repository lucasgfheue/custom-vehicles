package com.vehiclestudio.data;

import java.util.Locale;

/** Forma de la tobera de un propulsor (la llama sale por la cara de atras, +Z, de la pieza). */
public enum ThrusterStyle {
    /** Turbina: tubo con un cono en el centro y aspas dentro. */
    JET,
    /** Cohete: campana que se abre hacia atras. */
    ROCKET,
    /** Tobera: tubo sencillo. */
    NOZZLE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ThrusterStyle byKey(String key, ThrusterStyle fallback) {
        for (ThrusterStyle s : values()) {
            if (s.key().equals(key)) {
                return s;
            }
        }
        return fallback;
    }
}
