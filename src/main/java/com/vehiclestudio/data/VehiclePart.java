package com.vehiclestudio.data;

/**
 * Una pieza del vehiculo. Todas las coordenadas estan en "pixeles" (1/16 de bloque) y son absolutas
 * en el espacio del modelo: Y hacia arriba, el suelo en Y=0 y el frente del vehiculo hacia -Z.
 * La rotacion se aplica alrededor del pivote y se hereda a las piezas hijas.
 * Las ruedas, asientos, volantes y escapes se dibujan dentro de su cubo con su propia forma ({@link #kind}).
 */
public class VehiclePart {
    /** Largo maximo del nombre de una pieza. */
    public static final int NAME_MAX = 40;

    public String name = "cubo";
    public int parent = -1;
    public PartKind kind = PartKind.BODY;

    /** Esquina minima del cubo. */
    public float x = -2, y = 0, z = -2;
    /** Tamano del cubo. */
    public float w = 4, h = 4, d = 4;
    /** Punto de giro. */
    public float px = 0, py = 2, pz = 0;
    /** Rotacion en grados. */
    public float rx, ry, rz;

    /** Color RGB que tine la textura. */
    public int color = 0xFFFFFF;
    /** Sprite del atlas de bloques, p. ej. "minecraft:block/white_wool". */
    public String material = MaterialIds.SOLID;
    /** true = la textura entera se estira sobre cada cara; false = se repite cada 16 pixeles. */
    public boolean stretch;
    /** true = se dibuja con brillo maximo (ojos que brillan en la oscuridad). */
    public boolean glow;
    /** true = se choca con ella (jugadores, mobs, bloques); false = adorno que se puede atravesar. */
    public boolean solid = true;

    // --- Rueda (el color de la pieza es el de la goma) ---
    public int rimColor = 0xC8C8CC;
    public RimStyle rimStyle = RimStyle.SPOKES;
    public Tread tread = Tread.BLOCKS;
    /** Tamano de la llanta respecto al radio (0,3 a 0,95). */
    public float rimSize = 0.58f;
    /** true = gira hacia los lados al doblar (las delanteras). */
    public boolean steers;

    // --- Asiento ---
    public SeatStyle seatStyle = SeatStyle.CAR;
    /** El asiento del conductor (si no hay ninguno, conduce el primero). */
    public boolean driver;

    // --- Volante ---
    public SteeringStyle steeringStyle = SteeringStyle.WHEEL;

    // --- Luz ---
    public LightMode lightMode = LightMode.HEAD;

    // --- Helice (el color de la pieza es el de las palas y rimColor el del centro) ---
    /** Numero de palas (2 a 8). */
    public int blades = 3;
    public PropAxis propAxis = PropAxis.FRONT;

    // --- Propulsor ---
    public ThrusterStyle thrusterStyle = ThrusterStyle.JET;
    /** Color de la llama. */
    public int flameColor = 0xFF8A1E;
    /** Largo de la llama respecto al tamano de la tobera (0,3 a 3). */
    public float flameSize = 1f;

    /**
     * Pintura a mano: un color ARGB por texel (alfa 0 = sin pintar, se ve la textura de debajo),
     * con el esquema de {@link PaintLayout}. Puede ser null.
     */
    public int[] paint;
    /** Resolucion de la pintura en texels (ancho, alto y fondo). */
    public int pw;
    public int ph;
    public int pd;

    /**
     * Sitio de la pieza en una textura (skin) con el formato de caja de Minecraft: esquina (uvU, uvV), o -1
     * si no tiene. uvMirror: la pieza usa esa zona reflejada (como las patas del otro lado en Minecraft).
     */
    public int uvU = -1;
    public int uvV = -1;
    public boolean uvMirror;
    public int uvW;
    public int uvH;
    public int uvD;

    /**
     * Minibloques pegados a la pieza (ver {@link Voxels}): pares {posicion, color}, o null si no tiene.
     * Se mueven y se animan con la pieza.
     */
    public int[] voxels;

    /** Tamano de la pieza en la skin (texels de ancho, alto y fondo; 0 en piezas planas como las aletas). */
    public boolean hasUv() {
        if (uvU < 0 || uvV < 0 || uvW < 0 || uvH < 0 || uvD < 0
                || uvW > PaintLayout.MAX_DIM || uvH > PaintLayout.MAX_DIM || uvD > PaintLayout.MAX_DIM) {
            return false;
        }
        return (uvW > 0 ? 1 : 0) + (uvH > 0 ? 1 : 0) + (uvD > 0 ? 1 : 0) >= 2;
    }

    /** Quita el sitio en la skin. */
    public void clearUv() {
        uvU = -1;
        uvV = -1;
        uvMirror = false;
        uvW = 0;
        uvH = 0;
        uvD = 0;
    }

    public VehiclePart() {
    }

    public VehiclePart copy() {
        VehiclePart c = new VehiclePart();
        c.name = name;
        c.parent = parent;
        c.x = x;
        c.y = y;
        c.z = z;
        c.w = w;
        c.h = h;
        c.d = d;
        c.px = px;
        c.py = py;
        c.pz = pz;
        c.rx = rx;
        c.ry = ry;
        c.rz = rz;
        c.color = color;
        c.material = material;
        c.stretch = stretch;
        c.glow = glow;
        c.solid = solid;
        c.kind = kind;
        c.rimColor = rimColor;
        c.rimStyle = rimStyle;
        c.tread = tread;
        c.rimSize = rimSize;
        c.steers = steers;
        c.seatStyle = seatStyle;
        c.driver = driver;
        c.steeringStyle = steeringStyle;
        c.lightMode = lightMode;
        c.blades = blades;
        c.propAxis = propAxis;
        c.thrusterStyle = thrusterStyle;
        c.flameColor = flameColor;
        c.flameSize = flameSize;
        c.paint = paint == null ? null : paint.clone();
        c.pw = pw;
        c.ph = ph;
        c.pd = pd;
        c.uvU = uvU;
        c.uvV = uvV;
        c.uvMirror = uvMirror;
        c.uvW = uvW;
        c.uvH = uvH;
        c.uvD = uvD;
        c.voxels = voxels == null ? null : voxels.clone();
        return c;
    }

    /** Copia reflejada respecto al plano X=0 (ruedas, faros, retrovisores...). El conductor no se copia. */
    public VehiclePart mirroredX() {
        VehiclePart c = copy();
        c.x = -(x + w);
        c.px = -px;
        c.ry = -ry;
        c.rz = -rz;
        c.name = mirrorName(name);
        c.driver = false;
        if (validPaint()) {
            c.paint = mirroredPaint();
        }
        c.uvMirror = !uvMirror;
        c.voxels = Voxels.mirrorX(voxels, w);
        return c;
    }

    public void translate(float dx, float dy, float dz) {
        x += dx;
        y += dy;
        z += dz;
        px += dx;
        py += dy;
        pz += dz;
    }

    public void centerPivot() {
        px = x + w / 2f;
        py = y + h / 2f;
        pz = z + d / 2f;
    }

    // ---------------------------------------------------------------------------------------
    // Pintura a mano
    // ---------------------------------------------------------------------------------------

    /** Texels de pintura para un tamano dado (uno por pixel del modelo, entre 1 y el maximo). */
    public static int paintDim(float size) {
        return Math.max(1, Math.min(PaintLayout.MAX_DIM, (int) Math.ceil(size - 1.0e-4f)));
    }

    public boolean validPaint() {
        return paint != null && pw >= 1 && ph >= 1 && pd >= 1
                && pw <= PaintLayout.MAX_DIM && ph <= PaintLayout.MAX_DIM && pd <= PaintLayout.MAX_DIM
                && paint.length == PaintLayout.imageW(pw, pd) * PaintLayout.imageH(ph, pd);
    }

    /** true si hay al menos un texel pintado. */
    public boolean hasPaint() {
        if (!validPaint()) {
            return false;
        }
        for (int c : paint) {
            if ((c >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Crea la capa de pintura vacia si no existe. */
    public void ensurePaint() {
        if (validPaint()) {
            return;
        }
        pw = paintDim(w);
        ph = paintDim(h);
        pd = paintDim(d);
        paint = new int[PaintLayout.imageW(pw, pd) * PaintLayout.imageH(ph, pd)];
    }

    public void clearPaint() {
        paint = null;
        pw = 0;
        ph = 0;
        pd = 0;
    }

    public int texel(int face, int i, int j) {
        if (!validPaint()) {
            return 0;
        }
        int[] r = PaintLayout.region(face, pw, ph, pd);
        if (i < 0 || j < 0 || i >= r[2] || j >= r[3]) {
            return 0;
        }
        return paint[(r[1] + j) * PaintLayout.imageW(pw, pd) + r[0] + i];
    }

    public void setTexel(int face, int i, int j, int argb) {
        if (!validPaint()) {
            return;
        }
        int[] r = PaintLayout.region(face, pw, ph, pd);
        if (i >= 0 && j >= 0 && i < r[2] && j < r[3]) {
            paint[(r[1] + j) * PaintLayout.imageW(pw, pd) + r[0] + i] = argb;
        }
    }

    /** Cambia la resolucion de la pintura conservando lo pintado (cada cara se recorta o crece desde su esquina). */
    public void remapPaint(int npw, int nph, int npd) {
        if (!validPaint()) {
            return;
        }
        int oldW = PaintLayout.imageW(pw, pd);
        int newW = PaintLayout.imageW(npw, npd);
        int[] out = new int[newW * PaintLayout.imageH(nph, npd)];
        for (int f = 0; f < PaintLayout.FACES; f++) {
            int[] ro = PaintLayout.region(f, pw, ph, pd);
            int[] rn = PaintLayout.region(f, npw, nph, npd);
            int cw = Math.min(ro[2], rn[2]);
            int ch = Math.min(ro[3], rn[3]);
            for (int j = 0; j < ch; j++) {
                for (int i = 0; i < cw; i++) {
                    out[(rn[1] + j) * newW + rn[0] + i] = paint[(ro[1] + j) * oldW + ro[0] + i];
                }
            }
        }
        paint = out;
        pw = npw;
        ph = nph;
        pd = npd;
    }

    /**
     * Llamar despues de cambiar el tamano. Si la pintura iba a un texel por pixel, se adapta al tamano nuevo;
     * si no (por ejemplo, la textura de un modelo de Minecraft), simplemente se estira.
     */
    public void onResized(float oldW, float oldH, float oldD) {
        if (!validPaint()) {
            return;
        }
        if (pw == paintDim(oldW) && ph == paintDim(oldH) && pd == paintDim(oldD)) {
            int npw = paintDim(w);
            int nph = paintDim(h);
            int npd = paintDim(d);
            if (npw != pw || nph != ph || npd != pd) {
                remapPaint(npw, nph, npd);
            }
        }
    }

    /** La pintura reflejada en X: izquierda y derecha se intercambian y cada cara se voltea. */
    private int[] mirroredPaint() {
        int width = PaintLayout.imageW(pw, pd);
        int[] out = new int[paint.length];
        for (int f = 0; f < PaintLayout.FACES; f++) {
            int[] dst = PaintLayout.region(f, pw, ph, pd);
            int[] src = PaintLayout.region(PaintLayout.mirrored(f), pw, ph, pd);
            for (int j = 0; j < dst[3]; j++) {
                for (int i = 0; i < dst[2]; i++) {
                    out[(dst[1] + j) * width + dst[0] + i] = paint[(src[1] + j) * width + src[0] + (src[2] - 1 - i)];
                }
            }
        }
        return out;
    }

    static String mirrorName(String n) {
        String[][] pairs = {
                {"izquierda", "derecha"},
                {"izquierdo", "derecho"},
                {"left", "right"}
        };
        for (String[] p : pairs) {
            if (n.contains(p[0])) {
                return n.replace(p[0], p[1]);
            }
            if (n.contains(p[1])) {
                return n.replace(p[1], p[0]);
            }
        }
        String result = n + " (espejo)";
        return result.length() > NAME_MAX ? result.substring(0, NAME_MAX) : result;
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes para construir plantillas de forma compacta
    // ---------------------------------------------------------------------------------------

    public static VehiclePart box(String name, float x, float y, float z, float w, float h, float d) {
        VehiclePart p = new VehiclePart();
        p.name = name;
        p.x = x;
        p.y = y;
        p.z = z;
        p.w = w;
        p.h = h;
        p.d = d;
        p.centerPivot();
        return p;
    }

    public VehiclePart pivotAt(float px, float py, float pz) {
        this.px = px;
        this.py = py;
        this.pz = pz;
        return this;
    }

    public VehiclePart rotated(float rx, float ry, float rz) {
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        return this;
    }

    public VehiclePart colored(int rgb) {
        this.color = rgb & 0xFFFFFF;
        return this;
    }

    public VehiclePart textured(String material) {
        this.material = material;
        return this;
    }

    /** Rueda de referencia con el centro en (cx, cy, cz); cy = -1 la apoya en el suelo. */
    public static VehiclePart wheel(String name, WheelPreset preset, float cx, float cy, float cz, boolean steers) {
        float r = preset.diameter / 2f;
        float y = cy < 0 ? 0f : cy - r;
        VehiclePart p = box(name, cx - preset.width / 2f, y, cz - r, preset.width, preset.diameter, preset.diameter);
        preset.applyTo(p, true);
        p.steers = steers;
        return p;
    }

    public static VehiclePart seat(String name, SeatStyle style, float x, float y, float z, float w, float h, float d,
                                   int color, boolean driver) {
        VehiclePart p = box(name, x, y, z, w, h, d);
        p.kind = PartKind.SEAT;
        p.seatStyle = style;
        p.color = color & 0xFFFFFF;
        p.driver = driver;
        return p;
    }

    public VehiclePart as(PartKind kind) {
        this.kind = kind;
        return this;
    }

    public VehiclePart light(LightMode mode) {
        this.kind = PartKind.LIGHT;
        this.lightMode = mode;
        return this;
    }

    public VehiclePart steering(SteeringStyle style) {
        this.kind = PartKind.STEERING;
        this.steeringStyle = style;
        return this;
    }

    /**
     * Helice con el centro en (cx, cy, cz). FRONT: disco de frente (ancho y alto = diametro, fondo = grosor);
     * TOP: disco tumbado (ancho y fondo = diametro, alto = grosor).
     */
    public static VehiclePart propeller(String name, PropAxis axis, float cx, float cy, float cz, float diameter,
                                        float thickness, int blades, int color) {
        float r = diameter / 2f;
        float t = thickness / 2f;
        VehiclePart p = axis == PropAxis.TOP
                ? box(name, cx - r, cy - t, cz - r, diameter, thickness, diameter)
                : box(name, cx - r, cy - r, cz - t, diameter, diameter, thickness);
        p.kind = PartKind.PROPELLER;
        p.propAxis = axis;
        p.blades = blades;
        p.color = color & 0xFFFFFF;
        p.rimColor = 0x3A3A3E;
        p.material = MaterialIds.METAL;
        return p;
    }

    /** Propulsor de seccion size x size y largo length, con el centro en (cx, cy, cz); la llama sale por +Z. */
    public static VehiclePart thruster(String name, ThrusterStyle style, float cx, float cy, float cz, float size,
                                       float length, int flameColor) {
        VehiclePart p = box(name, cx - size / 2f, cy - size / 2f, cz - length / 2f, size, size, length);
        p.kind = PartKind.THRUSTER;
        p.thrusterStyle = style;
        p.flameColor = flameColor & 0xFFFFFF;
        p.color = 0x8E9096;
        p.material = MaterialIds.METAL;
        return p;
    }

    /** Radio de la rueda (en pixeles). */
    public float wheelRadius() {
        return Math.min(h, d) / 2f;
    }

    /** Radio de la helice (en pixeles): la mitad del lado mas corto de su disco. */
    public float propRadius() {
        return (propAxis == PropAxis.TOP ? Math.min(w, d) : Math.min(w, h)) / 2f;
    }

    /** Punto donde se sienta quien va en este asiento (pixeles, espacio del modelo, sin girar la pieza). */
    public float[] seatPoint() {
        return new float[]{x + w / 2f, y + h * seatStyle.cushion(), z + d * 0.55f};
    }

    public VehiclePart childOf(int parent) {
        this.parent = parent;
        return this;
    }

    public VehiclePart stretched() {
        this.stretch = true;
        return this;
    }

    public VehiclePart glowing() {
        this.glow = true;
        return this;
    }

    /** Adorno que se puede atravesar. */
    public VehiclePart ghost() {
        this.solid = false;
        return this;
    }
}
