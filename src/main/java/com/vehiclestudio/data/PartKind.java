package com.vehiclestudio.data;

import java.util.Locale;

/** Que es cada pieza del vehiculo. */
public enum PartKind {
    /** Carroceria: un cubo normal (se puede pintar y llevar minibloques). */
    BODY,
    /** Rueda: un cilindro dentro de su cubo con el eje en X (ancho = W, diametro = H y D). Gira al avanzar. */
    WHEEL,
    /** Asiento: donde se sientan el conductor y los pasajeros. */
    SEAT,
    /** Volante o manillar: gira cuando el conductor gira. */
    STEERING,
    /** Faro o luz: brilla (siempre, con las luces encendidas o al frenar). */
    LIGHT,
    /** Tubo de escape: echa humo mientras el motor esta en marcha. */
    EXHAUST,
    /** Helice: palas que giran (hacia delante como un avion o hacia arriba como un helicoptero). Hace volar. */
    PROPELLER,
    /** Propulsor: tobera que echa fuego por detras. Da el turbo y tambien hace volar. */
    THRUSTER;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PartKind byKey(String key, PartKind fallback) {
        for (PartKind k : values()) {
            if (k.key().equals(key)) {
                return k;
            }
        }
        return fallback;
    }

    /** Las ruedas, asientos, volantes, escapes, helices y propulsores tienen su propio dibujo; los demas son cubos. */
    public boolean isBox() {
        return this == BODY || this == LIGHT;
    }

    /** Helices y propulsores: lo que hace volar. */
    public boolean propels() {
        return this == PROPELLER || this == THRUSTER;
    }
}
