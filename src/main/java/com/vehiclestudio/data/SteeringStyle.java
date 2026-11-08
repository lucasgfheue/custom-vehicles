package com.vehiclestudio.data;

import java.util.Locale;

/** Volante (aro que mira al conductor, gira sobre su eje Z) o manillar (barra que gira sobre Y). */
public enum SteeringStyle {
    WHEEL,
    HANDLEBAR;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SteeringStyle byKey(String key, SteeringStyle fallback) {
        for (SteeringStyle s : values()) {
            if (s.key().equals(key)) {
                return s;
            }
        }
        return fallback;
    }
}
