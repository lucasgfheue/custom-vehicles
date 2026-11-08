package com.vehiclestudio.data;

import java.util.Locale;

/** Sonido de la bocina (sonidos de Minecraft). */
public enum HornSound {
    NONE("", 1.0f),
    CAR("minecraft:block.note_block.didgeridoo", 1.4f),
    TRUCK("minecraft:item.goat_horn.sound.0", 1.0f),
    BELL("minecraft:block.bell.use", 1.6f),
    BIKE("minecraft:block.note_block.bell", 1.5f),
    CLOWN("minecraft:block.note_block.bit", 1.2f),
    COW("minecraft:entity.cow.ambient", 1.0f);

    private final String sound;
    private final float pitch;

    HornSound(String sound, float pitch) {
        this.sound = sound;
        this.pitch = pitch;
    }

    public String sound() {
        return sound;
    }

    public float pitch() {
        return pitch;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HornSound byKey(String key, HornSound fallback) {
        for (HornSound h : values()) {
            if (h.key().equals(key)) {
                return h;
            }
        }
        return fallback;
    }
}
