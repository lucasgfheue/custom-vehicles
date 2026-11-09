package com.vehiclestudio.data;

import java.util.Locale;

/** Si el vehiculo vuela y como (necesita al menos una helice o un propulsor). */
public enum FlightMode {
    /** No vuela. */
    NONE,
    /** Como un helicoptero o un dron: sube y baja en el sitio y se queda quieto en el aire. */
    HELICOPTER,
    /** Como un avion: necesita coger velocidad para despegar y para no caer. */
    PLANE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FlightMode byKey(String key, FlightMode fallback) {
        for (FlightMode m : values()) {
            if (m.key().equals(key)) {
                return m;
            }
        }
        return fallback;
    }
}
