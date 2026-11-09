package com.vehiclestudio.data;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Definicion completa de un vehiculo creado por el usuario: sus piezas (carroceria, ruedas, asientos, volante,
 * luces, escape) y como se conduce. Se guarda como JSON en la carpeta del mundo y se sincroniza con los clientes.
 */
public class VehicleDefinition {
    public static final int MAX_PARTS = 160;
    public static final int MAX_WHEELS = 16;
    public static final int MAX_SEATS = 12;
    /** Maximo de texels pintados a mano por vehiculo (sumando todas las piezas). */
    public static final int MAX_PAINT_TEXELS = 160_000;
    public static final int NAME_MAX = 32;
    /** Huecos que puede tener el maletero (filas de un cofre). */
    public static final int[] STORAGE_SIZES = {0, 9, 18, 27, 36, 45, 54};
    public static final float MAX_SPEED = 150f;
    /** Velocidad maxima en el aire (km/h). */
    public static final float MAX_AIR_SPEED = 200f;
    /** Turbo maximo (% extra sobre la velocidad maxima). */
    public static final float MAX_TURBO = 150f;
    /** Helices y propulsores (juntos) por vehiculo. */
    public static final int MAX_PROPULSION = 16;

    /** Tamano de la textura (skin) en la que estan las piezas con {@code uvU >= 0}; 0 si no hay. */
    public int texW;
    public int texH;

    public String id = "";
    public String name = "Nuevo vehiculo";
    public String owner = "";
    public int revision = 0;

    /** Escala de todo el vehiculo (modelo, caja de colision y asientos). */
    public float scale = 1f;

    // --- Conduccion ---
    /** Velocidad maxima en km/h (1 bloque = 1 metro). */
    public float maxSpeed = 60f;
    /** Segundos para pasar de 0 a la velocidad maxima. */
    public float accelTime = 4f;
    /** Segundos para frenar desde la velocidad maxima. */
    public float brakeTime = 1.5f;
    /** Velocidad marcha atras, en % de la maxima. */
    public int reverseSpeed = 40;
    /** Cuanto giran las ruedas delanteras (grados): mas = curvas mas cerradas. */
    public float steerAngle = 35f;
    /** Derrape: 0 = va siempre hacia donde apunta, 100 = como sobre hielo. */
    public int drift = 10;
    /** Bloques de altura que sube sin saltar (1 = un bloque entero). */
    public float stepHeight = 1f;
    public float health = 40f;
    /** true = hace dano a los mobs con los que choca (segun la velocidad). */
    public boolean runOver = false;

    // --- Extras ---
    /** Huecos del maletero (0 = sin maletero). Se abre con Mayus + clic derecho. */
    public int storage = 0;
    public HornSound horn = HornSound.CAR;
    public EngineSound engine = EngineSound.MOTOR;

    // --- Vuelo (necesita al menos una helice o un propulsor) ---
    public FlightMode flight = FlightMode.NONE;
    /** Velocidad maxima en el aire (km/h). */
    public float airSpeed = 80f;
    /** Lo rapido que sube y baja (bloques por segundo). */
    public float climb = 5f;

    // --- Turbo (necesita al menos un propulsor; tecla R) ---
    /** Velocidad extra con el turbo (% de la maxima); 0 = sin turbo. */
    public float turbo = 0f;
    /** Segundos que dura el turbo con el deposito lleno. */
    public float turboTime = 3f;
    /** Segundos que tarda en llenarse otra vez. */
    public float turboRecharge = 6f;

    // --- Modelo ---
    public List<VehiclePart> parts = new ArrayList<>();

    public VehicleDefinition() {
    }

    public VehicleDefinition copy() {
        return VehicleJson.fromJson(VehicleJson.toJson(this));
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static boolean isValidId(String id) {
        return id != null && !id.isEmpty() && id.length() <= 40 && id.matches("[a-z0-9_]+");
    }

    // ---------------------------------------------------------------------------------------
    // Asientos y ruedas
    // ---------------------------------------------------------------------------------------

    /** Indices (en {@link #parts}) de los asientos, en orden. */
    public List<Integer> seats() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).kind == PartKind.SEAT) {
                out.add(i);
            }
        }
        return out;
    }

    /** Cual de los asientos (posicion en {@link #seats()}) es el del conductor: el marcado o, si no, el primero. */
    public int driverSeat() {
        List<Integer> seats = seats();
        for (int s = 0; s < seats.size(); s++) {
            if (parts.get(seats.get(s)).driver) {
                return s;
            }
        }
        return 0;
    }

    public int countKind(PartKind kind) {
        int n = 0;
        for (VehiclePart p : parts) {
            if (p.kind == kind) {
                n++;
            }
        }
        return n;
    }

    /**
     * Distancia (en bloques, con la escala) entre las ruedas que giran y las que no: cuanto mas larga, mas abiertas
     * las curvas. Sin ruedas que giren (o sin las otras) se usa algo parecido segun el largo del vehiculo.
     */
    public float wheelbase() {
        float front = 0f;
        int nf = 0;
        float rear = 0f;
        int nr = 0;
        for (VehiclePart p : parts) {
            if (p.kind != PartKind.WHEEL) {
                continue;
            }
            float cz = p.z + p.d / 2f;
            if (p.steers) {
                front += cz;
                nf++;
            } else {
                rear += cz;
                nr++;
            }
        }
        float wb;
        if (nf > 0 && nr > 0) {
            wb = Math.abs(rear / nr - front / nf);
        } else {
            float[] b = bounds();
            wb = Math.max(b[3] - b[0], b[5] - b[2]) * 0.6f;
        }
        return Math.max(0.5f, wb / 16f * scale);
    }

    /**
     * Lingotes de hierro que cuesta en supervivencia: uno por cada bloque de carroceria (16x16x16 pixeles), uno por
     * rueda y uno por asiento, de 1 a 64.
     */
    public int ironCost() {
        double volume = 0;
        int extra = 0;
        for (VehiclePart p : parts) {
            switch (p.kind) {
                case WHEEL, SEAT -> extra++;
                case PROPELLER, THRUSTER -> extra += 2;
                default -> volume += (double) p.w * p.h * p.d;
            }
            volume += Voxels.volume(p.voxels);
        }
        if (canFly()) {
            extra += 4;
        }
        double blocks = volume / 4096.0 * scale * scale * scale;
        long cost = (long) Math.ceil(blocks) + extra;
        return (int) Math.max(1, Math.min(64, cost));
    }

    /** true si vuela: tiene modo de vuelo y al menos una helice o un propulsor. */
    public boolean canFly() {
        return flight != FlightMode.NONE && (countKind(PartKind.PROPELLER) + countKind(PartKind.THRUSTER)) > 0;
    }

    /** true si tiene turbo: algo de turbo y al menos un propulsor. */
    public boolean hasTurbo() {
        return turbo > 0f && countKind(PartKind.THRUSTER) > 0;
    }

    /** Velocidad a la que despega un avion (km/h): el 40 % de la del aire, sin pasar del 90 % de la de tierra. */
    public float takeoffSpeed() {
        return Math.min(airSpeed * 0.4f, maxSpeed * 0.9f);
    }

    // ---------------------------------------------------------------------------------------
    // Tamano
    // ---------------------------------------------------------------------------------------

    /**
     * Caja envolvente del modelo en reposo, en pixeles: {minX, minY, minZ, maxX, maxY, maxZ}.
     * Tiene en cuenta las rotaciones, la jerarquia y los minibloques.
     */
    public float[] bounds() {
        if (parts.isEmpty()) {
            return new float[]{-8, 0, -8, 8, 8, 8};
        }
        Matrix4f[] mats = PoseSolver.solve(parts, null, null);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        Vector3f v = new Vector3f();
        for (int i = 0; i < parts.size(); i++) {
            VehiclePart p = parts.get(i);
            float bx0 = p.x;
            float by0 = p.y;
            float bz0 = p.z;
            float bx1 = p.x + p.w;
            float by1 = p.y + p.h;
            float bz1 = p.z + p.d;
            if (Voxels.count(p.voxels) > 0) {
                for (int k = 0; k + 1 < p.voxels.length; k += 2) {
                    int pos = p.voxels[k];
                    float s = Voxels.pixels(Voxels.sizeOf(p.voxels[k + 1]));
                    bx0 = Math.min(bx0, p.x + Voxels.px(pos));
                    by0 = Math.min(by0, p.y + Voxels.py(pos));
                    bz0 = Math.min(bz0, p.z + Voxels.pz(pos));
                    bx1 = Math.max(bx1, p.x + Voxels.px(pos) + s);
                    by1 = Math.max(by1, p.y + Voxels.py(pos) + s);
                    bz1 = Math.max(bz1, p.z + Voxels.pz(pos) + s);
                }
            }
            for (int c = 0; c < 8; c++) {
                float cx = (c & 1) == 0 ? bx0 : bx1;
                float cy = (c & 2) == 0 ? by0 : by1;
                float cz = (c & 4) == 0 ? bz0 : bz1;
                mats[i].transformPosition(cx, cy, cz, v);
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);
            }
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * Medidas en el mundo, en bloques y con la escala: {ancho, alto, largo, centroX, sueloY, centroZ}. Las tres
     * ultimas (en pixeles del modelo) dicen donde queda el centro del vehiculo: el juego lo dibuja centrado ahi.
     */
    public float[] size() {
        float[] b = bounds();
        float k = scale / 16f;
        float width = Math.min(b[3] - b[0], b[5] - b[2]);
        float length = Math.max(b[3] - b[0], b[5] - b[2]);
        return new float[]{clamp(width * k, 0.3f, 8f), clamp((b[4] - b[1]) * k, 0.3f, 8f), clamp(length * k, 0.3f, 16f),
                (b[0] + b[3]) / 2f, b[1], (b[2] + b[5]) / 2f};
    }

    /** Desplaza todas las piezas para que el modelo quede centrado en X/Z y apoyado en el suelo. */
    public void centerOnGround() {
        if (parts.isEmpty()) {
            return;
        }
        float[] b = bounds();
        float dx = -(b[0] + b[3]) / 2f;
        float dz = -(b[2] + b[5]) / 2f;
        float dy = -b[1];
        dx = Math.round(dx * 4f) / 4f;
        dy = Math.round(dy * 4f) / 4f;
        dz = Math.round(dz * 4f) / 4f;
        for (VehiclePart p : parts) {
            p.translate(dx, dy, dz);
        }
    }

    // ---------------------------------------------------------------------------------------

    /** Limpia y limita todos los valores (se usa en el servidor antes de guardar). */
    public void sanitize() {
        name = cleanText(name, NAME_MAX);
        if (name.isEmpty()) {
            name = "Vehiculo";
        }
        if (!isValidId(id)) {
            id = "";
        }
        if (owner == null) {
            owner = "";
        }
        scale = clamp(scale, 0.25f, 4f);
        maxSpeed = clamp(maxSpeed, 1f, MAX_SPEED);
        accelTime = clamp(accelTime, 0.3f, 30f);
        brakeTime = clamp(brakeTime, 0.2f, 15f);
        reverseSpeed = clampInt(reverseSpeed, 0, 100);
        steerAngle = clamp(steerAngle, 5f, 70f);
        drift = clampInt(drift, 0, 100);
        stepHeight = clamp(stepHeight, 0f, 2f);
        health = clamp(health, 1f, 1000f);
        int best = 0;
        for (int size : STORAGE_SIZES) {
            if (Math.abs(size - storage) < Math.abs(best - storage)) {
                best = size;
            }
        }
        storage = best;
        if (horn == null) horn = HornSound.CAR;
        if (engine == null) engine = EngineSound.MOTOR;
        if (flight == null) flight = FlightMode.NONE;
        airSpeed = clamp(airSpeed, 5f, MAX_AIR_SPEED);
        climb = clamp(climb, 0.5f, 20f);
        turbo = clamp(turbo, 0f, MAX_TURBO);
        turboTime = clamp(turboTime, 0.5f, 20f);
        turboRecharge = clamp(turboRecharge, 0f, 60f);

        if (parts == null) {
            parts = new ArrayList<>();
        }
        parts.removeIf(Objects::isNull);
        while (parts.size() > MAX_PARTS) {
            parts.remove(parts.size() - 1);
        }
        int n = parts.size();
        int wheels = 0;
        int seats = 0;
        int propulsion = 0;
        boolean driver = false;
        for (int i = 0; i < n; i++) {
            VehiclePart p = parts.get(i);
            p.name = cleanText(p.name, VehiclePart.NAME_MAX);
            if (p.name.isEmpty()) {
                p.name = "cubo";
            }
            if (p.parent < -1 || p.parent >= n || p.parent == i) {
                p.parent = -1;
            }
            p.x = clamp(p.x, -512f, 512f);
            p.y = clamp(p.y, -512f, 512f);
            p.z = clamp(p.z, -512f, 512f);
            p.w = clamp(p.w, 0f, 256f);
            p.h = clamp(p.h, 0f, 256f);
            p.d = clamp(p.d, 0f, 256f);
            p.px = clamp(p.px, -512f, 512f);
            p.py = clamp(p.py, -512f, 512f);
            p.pz = clamp(p.pz, -512f, 512f);
            p.rx = clamp(p.rx, -360f, 360f);
            p.ry = clamp(p.ry, -360f, 360f);
            p.rz = clamp(p.rz, -360f, 360f);
            p.color &= 0xFFFFFF;
            p.rimColor &= 0xFFFFFF;
            p.material = cleanMaterial(p.material);
            if (p.kind == null) p.kind = PartKind.BODY;
            if (p.rimStyle == null) p.rimStyle = RimStyle.SPOKES;
            if (p.tread == null) p.tread = Tread.SMOOTH;
            if (p.seatStyle == null) p.seatStyle = SeatStyle.CAR;
            if (p.steeringStyle == null) p.steeringStyle = SteeringStyle.WHEEL;
            if (p.lightMode == null) p.lightMode = LightMode.HEAD;
            if (p.propAxis == null) p.propAxis = PropAxis.FRONT;
            if (p.thrusterStyle == null) p.thrusterStyle = ThrusterStyle.JET;
            p.blades = clampInt(p.blades, 2, 8);
            p.flameColor &= 0xFFFFFF;
            p.flameSize = clamp(p.flameSize, 0.3f, 3f);
            p.rimSize = clamp(p.rimSize, 0.3f, 0.95f);
            if (p.kind.propels() && ++propulsion > MAX_PROPULSION) {
                p.kind = PartKind.BODY;
            }
            if (p.kind == PartKind.WHEEL && ++wheels > MAX_WHEELS) {
                p.kind = PartKind.BODY;
            }
            if (p.kind == PartKind.SEAT && ++seats > MAX_SEATS) {
                p.kind = PartKind.BODY;
            }
            if (p.kind == PartKind.WHEEL || p.kind == PartKind.PROPELLER) {
                p.centerPivot(); // las ruedas y las helices giran sobre su centro
            }
            if (p.kind != PartKind.WHEEL) {
                p.steers = false;
            }
            if (p.kind == PartKind.SEAT && p.driver) {
                p.driver = !driver; // solo un conductor
                driver = true;
            } else {
                p.driver = false;
            }
        }
        sanitizePaint();
        int voxelBudget = Voxels.MAX_TOTAL;
        for (VehiclePart p : parts) {
            p.voxels = Voxels.clean(p.voxels, voxelBudget);
            voxelBudget -= Voxels.count(p.voxels);
        }
        if (texW < 8 || texH < 8 || texW > 4096 || texH > 4096) {
            texW = 0;
            texH = 0;
        }
        for (VehiclePart p : parts) {
            if (texW == 0 || !p.hasUv() || p.uvU >= texW || p.uvV >= texH) {
                p.clearUv();
            }
        }
    }

    /** Pintura: alfa 0 o 255 (como las texturas de recorte), sin capas vacias y con un limite total. */
    private void sanitizePaint() {
        int budget = MAX_PAINT_TEXELS;
        for (VehiclePart p : parts) {
            if (!p.validPaint()) {
                p.clearPaint();
                continue;
            }
            boolean any = false;
            for (int i = 0; i < p.paint.length; i++) {
                int c = p.paint[i];
                if ((c >>> 24) < 128) {
                    p.paint[i] = 0;
                } else {
                    p.paint[i] = 0xFF000000 | (c & 0xFFFFFF);
                    any = true;
                }
            }
            if (!any || p.paint.length > budget) {
                p.clearPaint();
            } else {
                budget -= p.paint.length;
            }
        }
    }

    /** true si alguna pieza tiene pintura a mano. */
    public boolean hasPaint() {
        for (VehiclePart p : parts) {
            if (p.hasPaint()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------

    public static float clamp(float v, float min, float max) {
        if (!(v >= min)) {
            return min; // tambien atrapa NaN
        }
        return Math.min(v, max);
    }

    public static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static String cleanText(String s, int max) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c != '\u00A7' && c != 127) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /** Devuelve un identificador "espacio:ruta" valido o "" si no lo es. */
    public static String cleanResource(String s) {
        if (s == null) {
            return "";
        }
        // "Diamond Sword" -> "minecraft:diamond_sword"
        s = s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (s.isEmpty()) {
            return "";
        }
        if (!s.contains(":")) {
            s = "minecraft:" + s;
        }
        return ResourceLocation.tryParse(s) == null ? "" : s;
    }

    /** Normaliza una textura: "stone" -> "minecraft:block/stone". */
    public static String cleanMaterial(String s) {
        if (s == null) {
            return MaterialIds.SOLID;
        }
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return MaterialIds.SOLID;
        }
        String ns = "minecraft";
        String path = s;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            ns = s.substring(0, colon);
            path = s.substring(colon + 1);
        }
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        if (!path.contains("/")) {
            path = "block/" + path;
        }
        String full = ns + ":" + path;
        return full.length() > 128 || ResourceLocation.tryParse(full) == null ? MaterialIds.SOLID : full;
    }
}
