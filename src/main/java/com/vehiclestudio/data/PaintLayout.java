package com.vehiclestudio.data;

/**
 * Distribucion de la pintura a mano de una pieza: una imagen por pieza con el mismo esquema "de caja"
 * que usan las texturas de Minecraft (ancho = 2*(pd+pw), alto = pd+ph).
 *
 * <pre>
 *            pd     pw      pw     pd
 *        +------+-------+-------+
 *   pd   |      | ARRIBA| ABAJO |
 *        +------+-------+-------+------+-------+
 *   ph   | DCHA | FRENTE|  IZQ  | ATRAS|
 *        +------+-------+-------+------+-------+
 * </pre>
 *
 * Cada cara tiene un origen y dos ejes (s hacia la derecha, t hacia abajo vistos desde fuera),
 * los mismos que usa el renderizador para dibujar las texturas de bloque.
 * DCHA/IZQ son la derecha (-X) y la izquierda (+X) del vehiculo, que mira hacia -Z.
 */
public final class PaintLayout {
    public static final int FRONT = 0;   // -Z
    public static final int BACK = 1;    // +Z
    public static final int LEFT = 2;    // +X
    public static final int RIGHT = 3;   // -X
    public static final int TOP = 4;     // +Y
    public static final int BOTTOM = 5;  // -Y
    public static final int FACES = 6;

    /** Resolucion maxima de la pintura en cada eje (texels). */
    public static final int MAX_DIM = 64;

    private PaintLayout() {
    }

    public static int imageW(int pw, int pd) {
        return 2 * (pd + pw);
    }

    public static int imageH(int ph, int pd) {
        return pd + ph;
    }

    /** Rectangulo de la cara en la imagen: {x, y, ancho, alto}. */
    public static int[] region(int face, int pw, int ph, int pd) {
        return switch (face) {
            case TOP -> new int[]{pd, 0, pw, pd};
            case BOTTOM -> new int[]{pd + pw, 0, pw, pd};
            case RIGHT -> new int[]{0, pd, pd, ph};
            case FRONT -> new int[]{pd, pd, pw, ph};
            case LEFT -> new int[]{pd + pw, pd, pd, ph};
            default -> new int[]{2 * pd + pw, pd, pw, ph}; // BACK
        };
    }

    /**
     * Origen, eje s, eje t y normal de una cara de la caja [x0,x1]x[y0,y1]x[z0,z1]:
     * {ox, oy, oz, sx, sy, sz, tx, ty, tz, nx, ny, nz}, con s y t ya multiplicados por el tamano.
     */
    public static float[] frame(int face, float x0, float y0, float z0, float x1, float y1, float z1) {
        float w = x1 - x0;
        float h = y1 - y0;
        float d = z1 - z0;
        return switch (face) {
            case FRONT -> new float[]{x0, y1, z0, w, 0, 0, 0, -h, 0, 0, 0, -1};
            case BACK -> new float[]{x1, y1, z1, -w, 0, 0, 0, -h, 0, 0, 0, 1};
            case LEFT -> new float[]{x1, y1, z0, 0, 0, d, 0, -h, 0, 1, 0, 0};
            case RIGHT -> new float[]{x0, y1, z1, 0, 0, -d, 0, -h, 0, -1, 0, 0};
            case TOP -> new float[]{x0, y1, z1, w, 0, 0, 0, 0, -d, 0, 1, 0};
            default -> new float[]{x0, y0, z0, w, 0, 0, 0, 0, d, 0, -1, 0}; // BOTTOM
        };
    }

    /** Ancho y alto de la cara en texels. */
    public static int faceW(int face, int pw, int pd) {
        return face == LEFT || face == RIGHT ? pd : pw;
    }

    public static int faceH(int face, int ph, int pd) {
        return face == TOP || face == BOTTOM ? pd : ph;
    }

    /** La cara opuesta al reflejar en X (izquierda y derecha se intercambian). */
    public static int mirrored(int face) {
        return face == LEFT ? RIGHT : face == RIGHT ? LEFT : face;
    }

    /**
     * Cara de la caja sobre la que esta el punto (el plano mas cercano), o -1 si esta lejos de todas.
     */
    public static int faceAt(float px, float py, float pz,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] dist = {
                Math.abs(pz - z0), Math.abs(pz - z1), Math.abs(px - x1),
                Math.abs(px - x0), Math.abs(py - y1), Math.abs(py - y0)
        };
        int best = -1;
        float bestD = 0.3f;
        for (int f = 0; f < FACES; f++) {
            if (dist[f] < bestD) {
                bestD = dist[f];
                best = f;
            }
        }
        return best;
    }

    /** Coordenadas (s, t) en [0,1] de un punto sobre la cara. */
    public static float[] st(int face, float px, float py, float pz,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] fr = frame(face, x0, y0, z0, x1, y1, z1);
        float rx = px - fr[0];
        float ry = py - fr[1];
        float rz = pz - fr[2];
        float sl = fr[3] * fr[3] + fr[4] * fr[4] + fr[5] * fr[5];
        float tl = fr[6] * fr[6] + fr[7] * fr[7] + fr[8] * fr[8];
        float s = sl <= 1.0e-9f ? 0f : (rx * fr[3] + ry * fr[4] + rz * fr[5]) / sl;
        float t = tl <= 1.0e-9f ? 0f : (rx * fr[6] + ry * fr[7] + rz * fr[8]) / tl;
        return new float[]{s, t};
    }
}
