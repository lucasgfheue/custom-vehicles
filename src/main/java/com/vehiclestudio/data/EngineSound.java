package com.vehiclestudio.data;

import java.util.Locale;

/** Sonido del motor mientras alguien conduce (se repite; el tono sube con la velocidad). */
public enum EngineSound {
    NONE(""),
    /** Motor: el traqueteo de las vagonetas, mas agudo cuanto mas rapido. */
    MOTOR("minecraft:entity.minecart.riding"),
    /** Electrico: zumbido suave. */
    ELECTRIC("minecraft:block.beacon.ambient"),
    /** Dron: zumbido de abeja, como unas helices pequenas. */
    DRONE("minecraft:entity.bee.loop"),
    /** Reactor: el viento de volar con elitros. */
    JET("minecraft:item.elytra.flying");

    private final String sound;

    EngineSound(String sound) {
        this.sound = sound;
    }

    public String sound() {
        return sound;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EngineSound byKey(String key, EngineSound fallback) {
        for (EngineSound e : values()) {
            if (e.key().equals(key)) {
                return e;
            }
        }
        return fallback;
    }
}
