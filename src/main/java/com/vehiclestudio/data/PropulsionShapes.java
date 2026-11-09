package com.vehiclestudio.data;

/**
 * Formas de las helices y los propulsores, dentro del cubo de su pieza y sin nada de Minecraft (como {@link Shapes}).
 * Las helices se dibujan quietas: el giro lo pone la matriz de la pieza. La llama de los propulsores sale por la cara
 * de atras (+Z) y es lo unico que se sale del cubo.
 */
public final class PropulsionShapes {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private PropulsionShapes() {
    }

    /** Recibe las esquinas de la llama de una en una (cuatro por cuadrilatero), cada una con su color. */
    public interface FlameSink {
        void vertex(float x, float y, float z, int argb);
    }

    // ---------------------------------------------------------------------------------------
    // Helices
    // ---------------------------------------------------------------------------------------

    public static void propeller(VehiclePart p, Shapes.QuadSink out) {
        float[] c = {p.x + p.w / 2f, p.y + p.h / 2f, p.z + p.d / 2f};
        boolean top = p.propAxis == PropAxis.TOP;
        float r = p.propRadius();
        if (r <= 0.05f) {
            return;
        }
        float thick = top ? p.h : p.d;
        // n: eje de giro; u y v: el plano del disco
        float[] n = top ? new float[]{0, 1, 0} : new float[]{0, 0, -1};
        float[] u = {1, 0, 0};
        float[] v = top ? new float[]{0, 0, 1} : new float[]{0, 1, 0};
        float hub = clamp(r * 0.14f, 0.4f, 3f);
        float chord = clamp(r * 0.24f, 0.5f, 5f);
        float bladeT = Math.min(thick, Math.max(0.2f, chord * 0.18f));
        // inclinacion de las palas (como una helice de verdad), sin salirse del grosor del disco
        float room = (thick - bladeT) / 2f;
        float twist = room <= 0f ? 0f : (float) Math.asin(Math.min(1.0, room / (chord / 2f)));
        twist = Math.min(twist, 0.32f);
        // la punta de la pala, de esquina a esquina, llega justo al borde del disco
        float tip = (float) Math.sqrt(Math.max(0.0, r * r - chord * chord / 4f));
        float root = hub * 0.5f;
        if (tip <= root) {
            return;
        }
        int bladeColor = 0xFF000000 | p.color;
        int hubColor = 0xFF000000 | p.rimColor;
        int blades = Math.max(2, Math.min(8, p.blades));
        for (int k = 0; k < blades; k++) {
            float a = TWO_PI * k / blades;
            float ca = (float) Math.cos(a);
            float sa = (float) Math.sin(a);
            float[] dir = lin(u, ca, v, sa);
            float[] side = lin(u, -sa, v, ca);
            float[] chordDir = lin(side, (float) Math.cos(twist), n, (float) Math.sin(twist));
            float[] thickDir = lin(n, (float) Math.cos(twist), side, -(float) Math.sin(twist));
            float half = (tip - root) / 2f;
            float[] center = add(c, scale(dir, root + half));
            orientedBox(out, center, dir, chordDir, thickDir, half, chord / 2f, bladeT / 2f, bladeColor);
        }
        orientedBox(out, c, u, v, n, hub, hub, thick / 2f, hubColor);
    }

    // ---------------------------------------------------------------------------------------
    // Propulsores (tobera a lo largo de Z; sale por z1 = z + d)
    // ---------------------------------------------------------------------------------------

    /** Radio de la tobera (pixeles). */
    public static float nozzleRadius(VehiclePart p) {
        return Math.min(p.w, p.h) / 2f;
    }

    /** Radio por el que sale la llama. */
    public static float outletRadius(VehiclePart p) {
        float r = nozzleRadius(p);
        return switch (p.thrusterStyle) {
            case JET -> r * 0.78f;
            case ROCKET -> r * 0.9f;
            case NOZZLE -> r * 0.8f;
        };
    }

    /** Centro de la salida y direccion de la llama (+Z de la pieza): {x, y, z, 0, 0, 1}. */
    public static float[] outlet(VehiclePart p) {
        return new float[]{p.x + p.w / 2f, p.y + p.h / 2f, p.z + p.d, 0f, 0f, 1f};
    }

    public static void thruster(VehiclePart p, Shapes.QuadSink out) {
        float cx = p.x + p.w / 2f;
        float cy = p.y + p.h / 2f;
        float r = nozzleRadius(p);
        if (r <= 0.05f || p.d <= 0.05f) {
            return;
        }
        float z0 = p.z;
        float z1 = p.z + p.d;
        int seg = Shapes.wheelSegments(r);
        int body = 0xFF000000 | p.color;
        int dark = Shapes.shade(body, 0.32f);
        int deep = 0xFF141416;
        switch (p.thrusterStyle) {
            case JET -> {
                float ri = r * 0.78f;
                float zi = z1 - p.d * 0.6f;
                tube(out, cx, cy, z0, z1, r, r, seg, body, false);
                disk(out, cx, cy, z0, r, seg, -1f, Shapes.shade(body, 0.8f));
                annulus(out, cx, cy, z1, ri, r, seg, 1f, Shapes.shade(body, 0.7f));
                tube(out, cx, cy, zi, z1, ri, ri, seg, dark, true);
                disk(out, cx, cy, zi, ri, seg, 1f, deep);
                cone(out, cx, cy, zi, z1 - p.d * 0.08f, ri * 0.42f, seg, Shapes.shade(body, 0.55f));
            }
            case ROCKET -> {
                float zc = z0 + p.d * 0.35f;
                float rc = r * 0.55f;
                tube(out, cx, cy, z0, zc, rc, rc, seg, body, false);
                disk(out, cx, cy, z0, rc, seg, -1f, Shapes.shade(body, 0.8f));
                tube(out, cx, cy, zc, z1, r * 0.4f, r, seg, body, false);
                tube(out, cx, cy, zc, z1, r * 0.4f * 0.9f, r * 0.9f, seg, dark, true);
                disk(out, cx, cy, zc, r * 0.4f * 0.9f, seg, 1f, deep);
                annulus(out, cx, cy, z1, r * 0.9f, r, seg, 1f, Shapes.shade(body, 0.7f));
            }
            case NOZZLE -> {
                float ri = r * 0.8f;
                tube(out, cx, cy, z0, z1, r, r, seg, body, false);
                disk(out, cx, cy, z0, r, seg, -1f, Shapes.shade(body, 0.8f));
                annulus(out, cx, cy, z1, ri, r, seg, 1f, Shapes.shade(body, 0.7f));
                float zi = z1 - Math.min(p.d * 0.5f, 3f);
                tube(out, cx, cy, zi, z1, ri, ri, seg, dark, true);
                disk(out, cx, cy, zi, ri, seg, 1f, deep);
            }
        }
    }

    /**
     * Llama del propulsor: tres planos cruzados de fuego y tres mas pequenos y claros dentro, mas un disco
     * brillante en la salida. length en pixeles (0 = apagado).
     */
    public static void flame(VehiclePart p, float length, FlameSink out) {
        if (length <= 0.05f) {
            return;
        }
        float[] o = outlet(p);
        float ro = outletRadius(p);
        int base = 0xFF000000 | mix(p.flameColor, 0xFFFFFF, 0.35f);
        int tip = Shapes.shade(0xFF000000 | p.flameColor, 0.75f);
        int coreBase = 0xFF000000 | mix(p.flameColor, 0xFFFFFF, 0.8f);
        int coreTip = 0xFF000000 | mix(p.flameColor, 0xFFFFFF, 0.45f);
        float z = o[2] + 0.02f;
        for (int k = 0; k < 3; k++) {
            float a = (float) (Math.PI * k / 3.0);
            plane(out, o[0], o[1], z, a, ro, ro * 0.12f, length, base, tip);
            plane(out, o[0], o[1], z + 0.01f, a + (float) (Math.PI / 6.0), ro * 0.5f, ro * 0.05f, length * 0.55f,
                    coreBase, coreTip);
        }
        // disco caliente en la boca de la tobera
        int seg = Shapes.wheelSegments(ro);
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            out.vertex(o[0], o[1], z - 0.03f, coreBase);
            out.vertex(o[0] + ro * (float) Math.cos(a0), o[1] + ro * (float) Math.sin(a0), z - 0.03f, base);
            out.vertex(o[0] + ro * (float) Math.cos(a1), o[1] + ro * (float) Math.sin(a1), z - 0.03f, base);
            out.vertex(o[0] + ro * (float) Math.cos(a1), o[1] + ro * (float) Math.sin(a1), z - 0.03f, base);
        }
    }

    private static void plane(FlameSink out, float cx, float cy, float z, float angle, float r0, float r1, float len,
                              int c0, int c1) {
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        out.vertex(cx - dx * r0, cy - dy * r0, z, c0);
        out.vertex(cx + dx * r0, cy + dy * r0, z, c0);
        out.vertex(cx + dx * r1, cy + dy * r1, z + len, c1);
        out.vertex(cx - dx * r1, cy - dy * r1, z + len, c1);
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes
    // ---------------------------------------------------------------------------------------

    /** Tronco de cono de radio ra en za a rb en zb, alrededor del eje Z. inside = normales hacia dentro. */
    private static void tube(Shapes.QuadSink out, float cx, float cy, float za, float zb, float ra, float rb, int seg,
                             int color, boolean inside) {
        float slope = (ra - rb) / Math.max(1.0e-4f, zb - za);
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float am = (a0 + a1) / 2f;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float nx = (float) Math.cos(am);
            float ny = (float) Math.sin(am);
            float len = (float) Math.sqrt(1f + slope * slope);
            float sign = inside ? -1f : 1f;
            out.quad(cx + ra * c0, cy + ra * s0, za, cx + rb * c0, cy + rb * s0, zb,
                    cx + rb * c1, cy + rb * s1, zb, cx + ra * c1, cy + ra * s1, za,
                    sign * nx / len, sign * ny / len, sign * slope / len, color);
        }
    }

    private static void disk(Shapes.QuadSink out, float cx, float cy, float z, float r, int seg, float nz, int color) {
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float x1 = cx + r * (float) Math.cos(a1);
            float y1 = cy + r * (float) Math.sin(a1);
            out.quad(cx, cy, z, cx + r * (float) Math.cos(a0), cy + r * (float) Math.sin(a0), z, x1, y1, z, x1, y1, z,
                    0f, 0f, nz, color);
        }
    }

    private static void annulus(Shapes.QuadSink out, float cx, float cy, float z, float r0, float r1, int seg,
                                float nz, int color) {
        if (r1 <= r0) {
            return;
        }
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            out.quad(cx + r0 * c0, cy + r0 * s0, z, cx + r1 * c0, cy + r1 * s0, z,
                    cx + r1 * c1, cy + r1 * s1, z, cx + r0 * c1, cy + r0 * s1, z, 0f, 0f, nz, color);
        }
    }

    /** Cono de base r en za y punta en zb, sobre el eje Z. */
    private static void cone(Shapes.QuadSink out, float cx, float cy, float za, float zb, float r, int seg, int color) {
        float h = zb - za;
        float len = (float) Math.sqrt(h * h + r * r);
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg;
            float a1 = TWO_PI * (i + 1) / seg;
            float am = (a0 + a1) / 2f;
            float x0 = cx + r * (float) Math.cos(a0);
            float y0 = cy + r * (float) Math.sin(a0);
            float x1 = cx + r * (float) Math.cos(a1);
            float y1 = cy + r * (float) Math.sin(a1);
            out.quad(x0, y0, za, x1, y1, za, cx, cy, zb, cx, cy, zb,
                    h / len * (float) Math.cos(am), h / len * (float) Math.sin(am), r / len, color);
        }
    }

    /** Caja orientada: centro c, ejes unitarios a, b, n y medias medidas ha, hb, hn. */
    private static void orientedBox(Shapes.QuadSink out, float[] c, float[] a, float[] b, float[] n, float ha, float hb,
                                    float hn, int color) {
        float[][] corner = new float[8][];
        for (int i = 0; i < 8; i++) {
            float sa = (i & 1) == 0 ? -ha : ha;
            float sb = (i & 2) == 0 ? -hb : hb;
            float sn = (i & 4) == 0 ? -hn : hn;
            corner[i] = new float[]{c[0] + a[0] * sa + b[0] * sb + n[0] * sn, c[1] + a[1] * sa + b[1] * sb + n[1] * sn,
                    c[2] + a[2] * sa + b[2] * sb + n[2] * sn};
        }
        // caras: (+n, -n, +a, -a, +b, -b)
        face(out, corner, 4, 5, 7, 6, n, 1f, Shapes.shade(color, 1.0f));
        face(out, corner, 0, 2, 3, 1, n, -1f, Shapes.shade(color, 0.6f));
        face(out, corner, 1, 3, 7, 5, a, 1f, Shapes.shade(color, 0.85f));
        face(out, corner, 0, 4, 6, 2, a, -1f, Shapes.shade(color, 0.85f));
        face(out, corner, 2, 6, 7, 3, b, 1f, Shapes.shade(color, 0.75f));
        face(out, corner, 0, 1, 5, 4, b, -1f, Shapes.shade(color, 0.75f));
    }

    private static void face(Shapes.QuadSink out, float[][] k, int i0, int i1, int i2, int i3, float[] normal,
                             float sign, int color) {
        out.quad(k[i0][0], k[i0][1], k[i0][2], k[i1][0], k[i1][1], k[i1][2], k[i2][0], k[i2][1], k[i2][2],
                k[i3][0], k[i3][1], k[i3][2], normal[0] * sign, normal[1] * sign, normal[2] * sign, color);
    }

    private static float[] lin(float[] a, float ka, float[] b, float kb) {
        return new float[]{a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb};
    }

    private static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] scale(float[] a, float k) {
        return new float[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Mezcla dos colores RGB: t = 0 da a, t = 1 da b. */
    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
