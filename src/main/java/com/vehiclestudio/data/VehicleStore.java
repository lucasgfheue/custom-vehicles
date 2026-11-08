package com.vehiclestudio.data;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de definiciones. Hay una instancia para el servidor (la fuente de verdad, guardada en el
 * mundo) y otra para el cliente (copia sincronizada por red). En un mundo de un jugador las dos
 * conviven en la misma JVM, por eso nunca se comparten objetos entre ellas.
 */
public final class VehicleStore {
    public static final VehicleStore SERVER = new VehicleStore();
    public static final VehicleStore CLIENT = new VehicleStore();

    private final Map<String, VehicleDefinition> map = new ConcurrentHashMap<>();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();
    private volatile boolean loaded;

    private VehicleStore() {
    }

    public static VehicleStore of(Level level) {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    @Nullable
    public VehicleDefinition get(@Nullable String id) {
        return id == null || id.isEmpty() ? null : map.get(id);
    }

    public void put(VehicleDefinition d) {
        map.put(d.id, d);
        deleted.remove(d.id);
    }

    public void remove(String id) {
        map.remove(id);
        deleted.add(id);
    }

    /** true si el vehiculo fue borrada durante esta sesion (sus entidades deben desaparecer). */
    public boolean wasDeleted(String id) {
        return deleted.contains(id);
    }

    public void clear() {
        map.clear();
        deleted.clear();
        loaded = false;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int size() {
        return map.size();
    }

    public Collection<VehicleDefinition> all() {
        return map.values();
    }

    public List<VehicleDefinition> sorted() {
        List<VehicleDefinition> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparing((VehicleDefinition d) -> d.name.toLowerCase(Locale.ROOT)).thenComparing(d -> d.id));
        return list;
    }
}
