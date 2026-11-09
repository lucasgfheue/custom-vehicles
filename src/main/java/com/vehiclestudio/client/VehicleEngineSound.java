package com.vehiclestudio.client;

import com.vehiclestudio.data.EngineSound;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.entity.CustomVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** Sonido del motor de un vehiculo mientras alguien lo conduce: mas fuerte y mas agudo cuanto mas rapido va. */
public final class VehicleEngineSound extends AbstractTickableSoundInstance {
    private static final Map<Integer, VehicleEngineSound> PLAYING = new HashMap<>();

    private final CustomVehicle vehicle;
    private final EngineSound kind;

    private VehicleEngineSound(CustomVehicle vehicle, SoundEvent sound, EngineSound kind) {
        super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.kind = kind;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.x = vehicle.getX();
        this.y = vehicle.getY();
        this.z = vehicle.getZ();
    }

    /** Se llama cada tick mientras alguien conduce: empieza el sonido si no estaba sonando. */
    public static void ensure(CustomVehicle v) {
        VehicleDefinition d = v.def();
        if (d == null || d.engine == EngineSound.NONE) {
            return;
        }
        VehicleEngineSound playing = PLAYING.get(v.getId());
        if (playing != null && !playing.isStopped() && playing.kind == d.engine) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse(d.engine.sound()));
        if (sound == null) {
            return;
        }
        VehicleEngineSound instance = new VehicleEngineSound(v, sound, d.engine);
        PLAYING.put(v.getId(), instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    public static void clear() {
        PLAYING.clear();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        VehicleDefinition d = this.vehicle.def();
        if (this.vehicle.isRemoved() || this.vehicle.getControllingPassenger() == null || d == null || d.engine != this.kind) {
            PLAYING.remove(this.vehicle.getId(), this);
            this.stop();
            return;
        }
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY();
        this.z = this.vehicle.getZ();
        float speed = this.vehicle.getSpeedForSound();
        boolean flying = this.vehicle.flying();
        float top = Math.max(0.05f, (flying ? d.airSpeed : d.maxSpeed) / 3.6f / 20f);
        float f = Mth.clamp(speed / top, 0f, 1f);
        if (flying) {
            // en el aire suena siempre fuerte (las helices no paran aunque este quieto)
            f = Math.max(f, 0.55f);
        }
        if (this.vehicle.boosting()) {
            f = Math.min(1.2f, f + 0.3f);
        }
        switch (this.kind) {
            case ELECTRIC -> {
                this.volume = 0.35f + f * 0.5f;
                this.pitch = 0.8f + f * 1.0f;
            }
            case DRONE -> {
                this.volume = 0.4f + f * 0.6f;
                this.pitch = 0.7f + f * 0.9f;
            }
            case JET -> {
                this.volume = 0.25f + f * 0.75f;
                this.pitch = 0.6f + f * 0.7f;
            }
            default -> {
                this.volume = 0.3f + f * 0.6f;
                this.pitch = 0.5f + f * 1.1f;
            }
        }
    }
}
