package com.vehiclestudio.data;

/**
 * Formas de las piezas que no son un simple cubo (ruedas, asientos, volantes, escapes). Todo en pixeles y en el
 * espacio de la pieza (el mismo que su cubo); quien dibuja aplica la matriz de la pieza. Sin nada de Minecraft,
 * para poder probarlo fuera del juego.
 */
public final class Shapes {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /** Recibe cuadrilateros (4 esquinas, normal y color ARGB). Se dibujan sin quitar caras traseras. */
    public interface QuadSink {
        void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                  float x2, float y2, float z2, float x3, float y3, float z3,
                  float nx, float ny, float nz, int argb);
    }

    /** Recibe cubos alineados con la pieza (se dibujan con la textura de la pieza); shade aclara u oscurece. */
    public interface BoxSink {
        void box(float x0, float y0, float z0, float x1, float y1, float z1, float shade);
    }

    private Shapes() {
    }

    // ---------------------------------------------------------------------------------------
    // Ruedas: cilindro con el eje en X, inscrito en el cubo
    // ---------------------------------------------------------------------------------------

    /** Lados del cilindro segun el tamano (siempre par, para alternar el dibujo de la goma). */
    public static int wheelSegments(float radius) {
        return radius > 9f ? 32 : (radius > 4.5f ? 24 : 16);
    }

    public static void wheel(VehiclePart p, QuadSink out) {
        float x0 = p.x;
        float x1 = p.x + p.w;
        float cy = p.y + p.h / 2f;
        float cz = p.z + p.d / 2f;
        float r = Math.min(p.h, p.d) / 2f;
        if (r <= 0.01f || p.w <= 0.01f) {
            return;
        }
        int seg = wheelSegments(r);
        float rim = r * Math.max(0.3f, Math.min(0.95f, p.rimSize));
        int tire = 0xFF000000 | p.color;
        int tireDark = shade(tire, 0.72f);
        int tireSide = shade(tire, 0.9f);
        int rimC = 0xFF000000 | p.rimColor;
        int rimDark = shade(rimC, 0.62f);
        int hole = 0xFF121212;
        boolean lugs = p.tread == Tread.KNOBBY || p.tread == Tread.CHEVRON;
        float low = lugs ? r * 0.9f : r;
        float xm = (x0 + x1) / 2f;

        // Banda de rodadura (y tacos)
        float[] outer = new float[seg];
        for (int i = 0; i < seg; i++) {
            outer[i] = lugs ? low : r;
        }
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float am = (a0 + a1) / 2f;
            float ny = (float) Math.sin(am);
            float nz = (float) Math.cos(am);
            int band = p.tread == Tread.BLOCKS ? (i % 2 == 0 ? tire : tireDark) : (lugs ? tireDark : tire);
            ring(out, x0, x1, outer[i], a0, a1, cy, cz, ny, nz, band);
            if (lugs) {
                // taco: toda la banda (todoterreno) o media banda alternando lados (tractor)
                float lx0 = x0;
                float lx1 = x1;
                boolean has = i % 2 == 0;
                if (p.tread == Tread.CHEVRON) {
                    has = i % 4 == 0 || i % 4 == 2;
                    if (i % 4 == 0) {
                        lx1 = xm + (x1 - x0) * 0.1f;
                    } else {
                        lx0 = xm - (x1 - x0) * 0.1f;
                    }
                }
                if (has) {
                    lug(out, lx0, lx1, low, r, a0, a1, cy, cz, tire);
                }
            }
        }
        // Flancos de la goma (de la llanta al borde) a los dos lados
        float inset = Math.min(0.6f, p.w * 0.25f);
        annulus(out, x0, rim, low, seg, cy, cz, -1f, tireSide);
        annulus(out, x1, rim, low, seg, cy, cz, 1f, tireSide);
        // Labio interior de la goma, hasta la llanta hundida
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float am = (a0 + a1) / 2f;
            float ny = -(float) Math.sin(am);
            float nz = -(float) Math.cos(am);
            ring(out, x0, x0 + inset, rim, a0, a1, cy, cz, ny, nz, tireDark);
            ring(out, x1 - inset, x1, rim, a0, a1, cy, cz, ny, nz, tireDark);
        }
        // Llantas
        rimFace(out, p, x0 + inset, 1f, rim, seg, cy, cz, rimC, rimDark, hole);
        rimFace(out, p, x1 - inset, -1f, rim, seg, cy, cz, rimC, rimDark, hole);
    }

    /**
     * Una llanta en el plano x. inward: hacia donde queda el centro de la rueda (+1 o -1): lo "hundido" se mueve
     * hacia alli y lo que sobresale hacia fuera.
     */
    private static void rimFace(QuadSink out, VehiclePart p, float x, float inward, float rim, int seg, float cy,
                                float cz, int rimC, int rimDark, int hole) {
        float nx = -inward;
        float deep = 0.25f * inward;
        float raised = -0.08f * inward;
        float hubRaised = -0.16f * inward;
        int spokes = p.rimStyle.spokes();
        if (spokes == 0) {
            disk(out, x, rim, seg, cy, cz, nx, rimC);
            if (p.rimStyle == RimStyle.HUBCAP) {
                annulus(out, x + raised, rim * 0.62f, rim * 0.74f, seg, cy, cz, nx, rimDark);
                disk(out, x + hubRaised, rim * 0.24f, seg, cy, cz, nx, shade(rimC, 1.15f));
            } else {
                disk(out, x + hubRaised, rim * 0.3f, seg, cy, cz, nx, rimDark);
            }
            return;
        }
        float width = switch (p.rimStyle) {
            case SPOKES -> 0.34f;
            case STAR -> 0.14f;
            case WIRE -> 0.05f;
            default -> 0.22f;
        } * rim;
        width = Math.max(width, 0.3f);
        disk(out, x + deep, rim, seg, cy, cz, nx, hole);
        annulus(out, x, rim * 0.82f, rim, seg, cy, cz, nx, rimC);
        for (int k = 0; k < spokes; k++) {
            float a = TWO_PI * k / spokes;
            spoke(out, x + raised * 0.5f, a, width, rim * 0.18f, rim * 0.9f, cy, cz, nx, rimC);
        }
        disk(out, x + hubRaised, rim * 0.28f, seg, cy, cz, nx, p.rimStyle == RimStyle.WOOD ? rimDark : rimC);
    }

    /** Banda del cilindro de radio r entre dos angulos, de xa a xb. */
    private static void ring(QuadSink out, float xa, float xb, float r, float a0, float a1, float cy, float cz,
                             float ny, float nz, int color) {
        float y0 = cy + r * (float) Math.sin(a0);
        float z0 = cz + r * (float) Math.cos(a0);
        float y1 = cy + r * (float) Math.sin(a1);
        float z1 = cz + r * (float) Math.cos(a1);
        out.quad(xa, y0, z0, xb, y0, z0, xb, y1, z1, xa, y1, z1, 0f, ny, nz, color);
    }

    /** Taco que sobresale de la banda: la cara de arriba y sus dos lados. */
    private static void lug(QuadSink out, float xa, float xb, float r0, float r1, float a0, float a1, float cy,
                            float cz, int color) {
        float am = (a0 + a1) / 2f;
        ring(out, xa, xb, r1, a0, a1, cy, cz, (float) Math.sin(am), (float) Math.cos(am), color);
        for (float a : new float[]{a0, a1}) {
            float s = (float) Math.sin(a);
            float c = (float) Math.cos(a);
            float ty = a == a0 ? -c : c;
            float tz = a == a0 ? s : -s;
            out.quad(xa, cy + r0 * s, cz + r0 * c, xb, cy + r0 * s, cz + r0 * c,
                    xb, cy + r1 * s, cz + r1 * c, xa, cy + r1 * s, cz + r1 * c, 0f, ty, tz, color);
        }
        // tapas de los lados del taco
        for (float x : new float[]{xa, xb}) {
            float nx = x == xa ? -1f : 1f;
            float y0 = cy + r0 * (float) Math.sin(a0);
            float z0 = cz + r0 * (float) Math.cos(a0);
            float y1 = cy + r0 * (float) Math.sin(a1);
            float z1 = cz + r0 * (float) Math.cos(a1);
            float y2 = cy + r1 * (float) Math.sin(a1);
            float z2 = cz + r1 * (float) Math.cos(a1);
            float y3 = cy + r1 * (float) Math.sin(a0);
            float z3 = cz + r1 * (float) Math.cos(a0);
            out.quad(x, y0, z0, x, y1, z1, x, y2, z2, x, y3, z3, nx, 0f, 0f, color);
        }
    }

    /** Corona circular en el plano x. */
    private static void annulus(QuadSink out, float x, float r0, float r1, int seg, float cy, float cz, float nx,
                                int color) {
        if (r1 <= r0) {
            return;
        }
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float s0 = (float) Math.sin(a0);
            float c0 = (float) Math.cos(a0);
            float s1 = (float) Math.sin(a1);
            float c1 = (float) Math.cos(a1);
            out.quad(x, cy + r0 * s0, cz + r0 * c0, x, cy + r1 * s0, cz + r1 * c0,
                    x, cy + r1 * s1, cz + r1 * c1, x, cy + r0 * s1, cz + r0 * c1, nx, 0f, 0f, color);
        }
    }

    /** Disco en el plano x (triangulos hechos con cuadrilateros de dos esquinas iguales). */
    private static void disk(QuadSink out, float x, float r, int seg, float cy, float cz, float nx, int color) {
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float y0 = cy + r * (float) Math.sin(a0);
            float z0 = cz + r * (float) Math.cos(a0);
            float y1 = cy + r * (float) Math.sin(a1);
            float z1 = cz + r * (float) Math.cos(a1);
            out.quad(x, cy, cz, x, y0, z0, x, y1, z1, x, y1, z1, nx, 0f, 0f, color);
        }
    }

    /** Radio de la llanta: un rectangulo del angulo a, de r0 a r1. */
    private static void spoke(QuadSink out, float x, float a, float width, float r0, float r1, float cy, float cz,
                              float nx, int color) {
        float dy = (float) Math.sin(a);
        float dz = (float) Math.cos(a);
        float py = dz * width / 2f;
        float pz = -dy * width / 2f;
        out.quad(x, cy + dy * r0 + py, cz + dz * r0 + pz, x, cy + dy * r1 + py, cz + dz * r1 + pz,
                x, cy + dy * r1 - py, cz + dz * r1 - pz, x, cy + dy * r0 - py, cz + dz * r0 - pz, nx, 0f, 0f, color);
    }

    // ---------------------------------------------------------------------------------------
    // Asientos (cubos dentro del cubo de la pieza; el frente mira a -Z)
    // ---------------------------------------------------------------------------------------

    /** editor: los asientos invisibles se dibujan como una placa fina para poder verlos y elegirlos. */
    public static void seat(VehiclePart p, BoxSink out, boolean editor) {
        float x0 = p.x;
        float y0 = p.y;
        float z0 = p.z;
        float w = p.w;
        float h = p.h;
        float d = p.d;
        float x1 = x0 + w;
        float y1 = y0 + h;
        float z1 = z0 + d;
        switch (p.seatStyle) {
            case CAR -> {
                float top = y0 + h * 0.35f;
                out.box(x0, y0, z0, x1, top, z1, 1.0f);
                out.box(x0, top, z1 - d * 0.25f, x1, y1 - h * 0.17f, z1, 0.88f);
                out.box(x0 + w * 0.22f, y1 - h * 0.17f, z1 - d * 0.22f, x1 - w * 0.22f, y1, z1 - d * 0.03f, 0.8f);
            }
            case BENCH -> {
                float top = y0 + h * 0.4f;
                out.box(x0, y0, z0, x1, top, z1, 1.0f);
                out.box(x0, top, z1 - d * 0.2f, x1, y1, z1, 0.88f);
            }
            case SADDLE -> {
                out.box(x0 + w * 0.2f, y0, z0, x1 - w * 0.2f, y1 - h * 0.15f, z0 + d * 0.4f, 0.95f);
                out.box(x0, y0, z0 + d * 0.4f, x1, y1, z1, 1.0f);
            }
            case RACING -> {
                float bottom = y0 + h * 0.25f;
                out.box(x0, y0, z0, x1, bottom, z1, 1.0f);
                out.box(x0, y0, z1 - d * 0.25f, x1, y1, z1, 0.85f);
                out.box(x0, bottom, z0, x0 + w * 0.15f, y0 + h * 0.6f, z1 - d * 0.25f, 0.9f);
                out.box(x1 - w * 0.15f, bottom, z0, x1, y0 + h * 0.6f, z1 - d * 0.25f, 0.9f);
            }
            case CHAIR -> {
                float seat0 = y0 + h * 0.37f;
                float seat1 = y0 + h * 0.45f;
                float leg = Math.min(w, d) * 0.12f;
                out.box(x0, seat0, z0, x1, seat1, z1, 1.0f);
                out.box(x0, y0, z0, x0 + leg, seat0, z0 + leg, 0.8f);
                out.box(x1 - leg, y0, z0, x1, seat0, z0 + leg, 0.8f);
                out.box(x0, y0, z1 - leg, x0 + leg, seat0, z1, 0.8f);
                out.box(x1 - leg, y0, z1 - leg, x1, seat0, z1, 0.8f);
                out.box(x0, seat1, z1 - d * 0.1f, x1, y1, z1, 0.9f);
            }
            case INVISIBLE -> {
                if (editor) {
                    out.box(x0, y0, z0, x1, y0 + Math.min(0.5f, h), z1, 0.6f);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Volante y manillar
    // ---------------------------------------------------------------------------------------

    /** Volante: aro en el plano XY (mira hacia el conductor, en +Z) con tres radios. Manillar: barra en X. */
    public static void steering(VehiclePart p, QuadSink quads, BoxSink boxes, int argb) {
        float cx = p.x + p.w / 2f;
        float cy = p.y + p.h / 2f;
        float cz = p.z + p.d / 2f;
        if (p.steeringStyle == SteeringStyle.HANDLEBAR) {
            float t = Math.max(0.3f, Math.min(p.h, p.d));
            float grip = p.w * 0.22f;
            boxes.box(p.x + grip, cy - t * 0.4f, cz - t * 0.4f, p.x + p.w - grip, cy + t * 0.4f, cz + t * 0.4f, 1.0f);
            boxes.box(p.x, cy - t / 2f, cz - t / 2f, p.x + grip, cy + t / 2f, cz + t / 2f, 0.55f);
            boxes.box(p.x + p.w - grip, cy - t / 2f, cz - t / 2f, p.x + p.w, cy + t / 2f, cz + t / 2f, 0.55f);
            float ch = Math.min(t * 0.6f, p.h / 2f);
            float cd = Math.min(t * 0.6f, p.d / 2f);
            boxes.box(cx - t * 0.6f, cy - ch, cz - cd, cx + t * 0.6f, cy + ch, cz + cd, 0.8f);
            return;
        }
        float size = Math.min(p.w, p.h);
        float t = Math.max(0.5f, Math.min(2f, size * 0.14f));
        float tz = Math.max(0.3f, Math.min(p.d, t));
        float ring = size / 2f - t / 2f;
        if (ring <= 0.1f) {
            boxes.box(p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d, 1.0f);
            return;
        }
        int seg = 20;
        float zf = cz - tz / 2f;
        float zb = cz + tz / 2f;
        float r0 = ring - t / 2f;
        float r1 = ring + t / 2f;
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float cm = (float) Math.cos((a0 + a1) / 2f);
            float sm = (float) Math.sin((a0 + a1) / 2f);
            // fuera y dentro del aro
            quads.quad(cx + r1 * c0, cy + r1 * s0, zf, cx + r1 * c0, cy + r1 * s0, zb,
                    cx + r1 * c1, cy + r1 * s1, zb, cx + r1 * c1, cy + r1 * s1, zf, cm, sm, 0f, argb);
            quads.quad(cx + r0 * c0, cy + r0 * s0, zf, cx + r0 * c1, cy + r0 * s1, zf,
                    cx + r0 * c1, cy + r0 * s1, zb, cx + r0 * c0, cy + r0 * s0, zb, -cm, -sm, 0f, argb);
            // caras delantera y trasera
            quads.quad(cx + r0 * c0, cy + r0 * s0, zf, cx + r1 * c0, cy + r1 * s0, zf,
                    cx + r1 * c1, cy + r1 * s1, zf, cx + r0 * c1, cy + r0 * s1, zf, 0f, 0f, -1f, argb);
            quads.quad(cx + r0 * c0, cy + r0 * s0, zb, cx + r0 * c1, cy + r0 * s1, zb,
                    cx + r1 * c1, cy + r1 * s1, zb, cx + r1 * c0, cy + r1 * s0, zb, 0f, 0f, 1f, argb);
        }
        // tres radios (abajo y a los lados, un poco hacia arriba) y el centro
        int dark = shade(argb, 0.8f);
        float hub = t * 1.3f;
        for (float deg : new float[]{270f, 20f, 160f}) {
            float a = (float) Math.toRadians(deg);
            float dx = (float) Math.cos(a);
            float dy = (float) Math.sin(a);
            float px = -dy * t * 0.35f;
            float py = dx * t * 0.35f;
            float ra = hub * 0.5f;
            float rb = ring;
            for (float z : new float[]{cz - tz * 0.3f, cz + tz * 0.3f}) {
                quads.quad(cx + dx * ra + px, cy + dy * ra + py, z, cx + dx * rb + px, cy + dy * rb + py, z,
                        cx + dx * rb - px, cy + dy * rb - py, z, cx + dx * ra - px, cy + dy * ra - py, z,
                        0f, 0f, z < cz ? -1f : 1f, dark);
            }
        }
        float hz = Math.min(tz * 0.6f, p.d / 2f);
        boxes.box(cx - hub / 2f, cy - hub / 2f, cz - hz, cx + hub / 2f, cy + hub / 2f, cz + hz, 0.7f);
    }

    // ---------------------------------------------------------------------------------------
    // Escape
    // ---------------------------------------------------------------------------------------

    /**
     * Por donde sale el humo de un escape: la cara de arriba si es mas alto que largo (chimenea de tractor) o la de
     * atras (+Z). Devuelve {x, y, z, dirX, dirY, dirZ} en el espacio de la pieza.
     */
    public static float[] exhaustOutlet(VehiclePart p) {
        float cx = p.x + p.w / 2f;
        float cy = p.y + p.h / 2f;
        float cz = p.z + p.d / 2f;
        if (p.h > p.d) {
            return new float[]{cx, p.y + p.h, cz, 0f, 1f, 0f};
        }
        return new float[]{cx, cy, p.z + p.d, 0f, 0f, 1f};
    }

    /** El agujero oscuro del escape, un poco por fuera de su cara de salida. */
    public static void exhaustHole(VehiclePart p, QuadSink out) {
        float[] o = exhaustOutlet(p);
        int hole = 0xFF101010;
        float e = 0.02f;
        if (o[4] > 0.5f) {
            float hw = p.w * 0.3f;
            float hd = p.d * 0.3f;
            float y = o[1] + e;
            out.quad(o[0] - hw, y, o[2] - hd, o[0] - hw, y, o[2] + hd, o[0] + hw, y, o[2] + hd, o[0] + hw, y, o[2] - hd,
                    0f, 1f, 0f, hole);
        } else {
            float hw = p.w * 0.3f;
            float hh = p.h * 0.3f;
            float z = o[2] + e;
            out.quad(o[0] - hw, o[1] - hh, z, o[0] + hw, o[1] - hh, z, o[0] + hw, o[1] + hh, z, o[0] - hw, o[1] + hh, z,
                    0f, 0f, 1f, hole);
        }
    }

    /** Multiplica el brillo de un color ARGB (mas de 1 aclara). */
    public static int shade(int argb, float k) {
        int a = argb >>> 24;
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * k));
        int g = Math.min(255, Math.round(((argb >> 8) & 255) * k));
        int b = Math.min(255, Math.round((argb & 255) * k));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
