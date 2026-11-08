package com.vehiclestudio.client.render;

import com.vehiclestudio.VehicleStudio;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleStore;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Texturas de pintura de los vehiculos del mundo (una por vehiculo, se rehace al cambiar). */
public final class PaintCache {
    private static final Map<String, Entry> CACHE = new HashMap<>();

    private static final class Entry {
        final PaintAtlas atlas;
        @Nullable
        VehicleDefinition def;
        int revision = Integer.MIN_VALUE;
        boolean painted;

        Entry(PaintAtlas atlas) {
            this.atlas = atlas;
        }
    }

    private PaintCache() {
    }

    /** Textura de la pintura del vehiculo, o null si no tiene nada pintado. */
    @Nullable
    public static PaintAtlas get(@Nullable VehicleDefinition def) {
        if (def == null || !VehicleDefinition.isValidId(def.id)) {
            return null;
        }
        Entry e = CACHE.get(def.id);
        if (e == null) {
            e = new Entry(new PaintAtlas(ResourceLocation.fromNamespaceAndPath(VehicleStudio.MODID, "paint/" + def.id)));
            CACHE.put(def.id, e);
        }
        if (e.def != def || e.revision != def.revision) {
            e.def = def;
            e.revision = def.revision;
            e.painted = e.atlas.rebuild(def);
        }
        return e.painted ? e.atlas : null;
    }

    /** Libera las texturas de vehiculos que ya no existen. */
    public static void prune() {
        Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> entry = it.next();
            if (VehicleStore.CLIENT.get(entry.getKey()) == null) {
                entry.getValue().atlas.close();
                it.remove();
            }
        }
    }

    public static void clear() {
        for (Entry e : CACHE.values()) {
            e.atlas.close();
        }
        CACHE.clear();
    }
}
