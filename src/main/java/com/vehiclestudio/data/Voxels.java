package com.vehiclestudio.data;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minibloques: cubitos pegados a una pieza (para cascos, armaduras, coronas...), de 1/4 a 4 pixeles de lado. Se
 * guardan en {@link VehiclePart#voxels} como pares de enteros {posicion, color}:
 * <ul>
 *     <li>la posicion es la esquina minima del minibloque, relativa a la esquina minima de la pieza, en cuartos de
 *     pixel: tres coordenadas de -512 a 511 (de -128 a casi 128 pixeles), ver {@link #pack};</li>
 *     <li>el color es RGB y, en el byte de arriba, el tamano (ver {@link #SIZE_KEYS}; 0 = 1 pixel, el de antes de que
 *     hubiera tamanos).</li>
 * </ul>
 * Dos minibloques no pueden tener la misma esquina; los de distinto tamano se pueden mezclar en una pieza.
 * <p>
 * Los arrays no se modifican nunca: cada cambio devuelve uno nuevo (asi el dibujado puede guardar en cache que
 * caras se ven).
 */
public final class Voxels {
    /** Maximo de minibloques entre todas las piezas de un vehiculo. */
    public static final int MAX_TOTAL = 6000;
    /** Cuartos de pixel por pixel: la rejilla de las posiciones. */
    public static final int UNIT = 4;
    public static final int MIN = -512;
    public static final int MAX = 511;
    /** Lado (en cuartos de pixel) de cada tamano: 0 = 1 px, 1 = 1/2 px, 2 = 1/4 px, 3 = 2 px, 4 = 4 px. */
    private static final int[] SIDE = {4, 2, 1, 8, 16};
    /** Nombre de cada tamano (para los textos), por codigo. */
    public static final String[] SIZE_KEYS = {"one", "half", "quarter", "two", "four"};
    /** Los tamanos de menor a mayor (como se eligen en el editor). */
    public static final Integer[] SIZE_ORDER = {2, 1, 0, 3, 4};
    /** 1 pixel. */
    public static final int DEFAULT_SIZE = 0;

    /** Vecino en la direccion de cada cara de {@link PaintLayout} (frente -Z, atras +Z, izq +X, dcha -X...). */
    public static final int[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private Voxels() {
    }

    // ---------------------------------------------------------------------------------------
    // Posicion y tamano
    // ---------------------------------------------------------------------------------------

    /** Posicion empaquetada (cuartos de pixel, 10 bits por eje): nunca negativa. */
    public static int pack(int x, int y, int z) {
        return (x & 0x3FF) << 20 | (y & 0x3FF) << 10 | (z & 0x3FF);
    }

    /** Coordenadas en cuartos de pixel. */
    public static int x(int pos) {
        return (pos << 2) >> 22;
    }

    public static int y(int pos) {
        return (pos << 12) >> 22;
    }

    public static int z(int pos) {
        return (pos << 22) >> 22;
    }

    /** Coordenadas en pixeles (relativas a la esquina de la pieza). */
    public static float px(int pos) {
        return x(pos) / (float) UNIT;
    }

    public static float py(int pos) {
        return y(pos) / (float) UNIT;
    }

    public static float pz(int pos) {
        return z(pos) / (float) UNIT;
    }

    /** Cuartos de pixel (redondeado) de una medida en pixeles. */
    public static int units(float pixels) {
        return Math.round(pixels * UNIT);
    }

    public static boolean inRange(int x, int y, int z) {
        return x >= MIN && x <= MAX && y >= MIN && y <= MAX && z >= MIN && z <= MAX;
    }

    public static boolean validSize(int size) {
        return size >= 0 && size < SIDE.length;
    }

    /** Lado en cuartos de pixel de un tamano. */
    public static int side(int size) {
        return SIDE[validSize(size) ? size : DEFAULT_SIZE];
    }

    /** Lado en pixeles de un tamano. */
    public static float pixels(int size) {
        return side(size) / (float) UNIT;
    }

    /** El tamano guardado en el entero del color. */
    public static int sizeOf(int word) {
        int s = (word >>> 24) & 0x7;
        return validSize(s) ? s : DEFAULT_SIZE;
    }

    /** El entero del color de un minibloque: RGB y, en el byte de arriba, el tamano. */
    public static int word(int rgb, int size) {
        return (rgb & 0xFFFFFF) | (validSize(size) ? size : DEFAULT_SIZE) << 24;
    }

    // ---------------------------------------------------------------------------------------
    // Listas
    // ---------------------------------------------------------------------------------------

    public static int count(@Nullable int[] v) {
        return v == null ? 0 : v.length / 2;
    }

    /** Indice del minibloque en esa posicion, o -1. */
    public static int indexOf(@Nullable int[] v, int pos) {
        if (v == null) {
            return -1;
        }
        for (int i = 0; i + 1 < v.length; i += 2) {
            if (v[i] == pos) {
                return i / 2;
            }
        }
        return -1;
    }

    public static int posAt(int[] v, int index) {
        return v[index * 2];
    }

    public static int colorAt(int[] v, int index) {
        return v[index * 2 + 1] & 0xFFFFFF;
    }

    public static int sizeAt(int[] v, int index) {
        return sizeOf(v[index * 2 + 1]);
    }

    /**
     * Donde va (posicion empaquetada) un minibloque del tamano size pegado por fuera a la cara face (ver
     * {@link #NORMALS}) de la caja de la pieza (de w x h x d pixeles, si hitPos es -1) o del minibloque hitPos (de
     * tamano hitSize) en el punto tocado (hx, hy, hz, en pixeles desde la esquina de la pieza): de lo tocado hacia fuera
     * y, a lo largo de la cara, en la casilla de su misma rejilla que hay bajo el punto. -1 si queda fuera de lo que se
     * puede guardar.
     */
    public static int beside(float w, float h, float d, int face, float hx, float hy, float hz, int hitPos, int hitSize,
                             int size) {
        if (face < 0 || face >= NORMALS.length) {
            return -1;
        }
        int[] n = NORMALS[face];
        int s = side(size);
        int[] lo = {0, 0, 0};
        int[] len;
        if (hitPos >= 0) {
            int hs = side(hitSize);
            lo = new int[]{x(hitPos), y(hitPos), z(hitPos)};
            len = new int[]{hs, hs, hs};
        } else {
            len = new int[]{Math.max(1, units(w)), Math.max(1, units(h)), Math.max(1, units(d))};
        }
        float[] at = {hx * UNIT, hy * UNIT, hz * UNIT};
        int[] q = new int[3];
        for (int a = 0; a < 3; a++) {
            if (n[a] > 0) {
                q[a] = lo[a] + len[a];
            } else if (n[a] < 0) {
                q[a] = lo[a] - s;
            } else {
                int cells = Math.max(1, (len[a] + s - 1) / s);
                int c = Math.max(0, Math.min(cells - 1, (int) Math.floor((at[a] - lo[a]) / s)));
                q[a] = lo[a] + c * s;
            }
        }
        return inRange(q[0], q[1], q[2]) ? pack(q[0], q[1], q[2]) : -1;
    }

    /** Copia con un minibloque en esa posicion (si ya habia uno, cambia su color y su tamano). */
    public static int[] with(@Nullable int[] v, int pos, int rgb, int size) {
        int w = word(rgb, size);
        int i = indexOf(v, pos);
        if (v != null && i >= 0) {
            if (v[i * 2 + 1] == w) {
                return v;
            }
            int[] c = v.clone();
            c[i * 2 + 1] = w;
            return c;
        }
        int n = v == null ? 0 : v.length;
        int[] c = v == null ? new int[2] : Arrays.copyOf(v, n + 2);
        c[n] = pos;
        c[n + 1] = w;
        return c;
    }

    /** Copia con otro color para el minibloque de esa posicion (con el mismo tamano); la misma si no hay ninguno. */
    @Nullable
    public static int[] recolor(@Nullable int[] v, int pos, int rgb) {
        int i = indexOf(v, pos);
        if (v == null || i < 0) {
            return v;
        }
        return with(v, pos, rgb, sizeAt(v, i));
    }

    /** Copia sin el minibloque de esa posicion (null si no queda ninguno). */
    @Nullable
    public static int[] without(@Nullable int[] v, int pos) {
        int i = indexOf(v, pos);
        if (v == null || i < 0) {
            return v;
        }
        if (v.length == 2) {
            return null;
        }
        int[] c = new int[v.length - 2];
        System.arraycopy(v, 0, c, 0, i * 2);
        System.arraycopy(v, i * 2 + 2, c, i * 2, v.length - i * 2 - 2);
        return c;
    }

    /**
     * Arregla una lista leida de un archivo: pares completos, sin repetidos (gana el ultimo), colores RGB, tamanos
     * validos y como mucho {@code budget} minibloques. null si queda vacia.
     */
    @Nullable
    public static int[] clean(@Nullable int[] v, int budget) {
        if (v == null || v.length < 2 || budget <= 0) {
            return null;
        }
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < v.length; i += 2) {
            int pos = v[i] & 0x3FFFFFFF;
            map.remove(pos);
            map.put(pos, word(v[i + 1], sizeOf(v[i + 1])));
        }
        int n = Math.min(map.size(), budget);
        if (n == 0) {
            return null;
        }
        int[] out = new int[n * 2];
        int k = 0;
        for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            if (k >= n * 2) {
                break;
            }
            out[k++] = e.getKey();
            out[k++] = e.getValue();
        }
        return out;
    }

    /**
     * Caras que se ven de cada minibloque: bit f (cara f de {@link PaintLayout}) a 1 si no hay otro minibloque del
     * mismo tamano pegado en esa direccion.
     */
    public static byte[] exposed(int[] v) {
        int n = v.length / 2;
        long[] sorted = new long[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = (long) v[i * 2] << 3 | sizeOf(v[i * 2 + 1]);
        }
        Arrays.sort(sorted);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int pos = v[i * 2];
            int size = sizeOf(v[i * 2 + 1]);
            int s = side(size);
            int x = x(pos);
            int y = y(pos);
            int z = z(pos);
            int mask = 0;
            for (int f = 0; f < 6; f++) {
                int nx = x + NORMALS[f][0] * s;
                int ny = y + NORMALS[f][1] * s;
                int nz = z + NORMALS[f][2] * s;
                if (!inRange(nx, ny, nz) || Arrays.binarySearch(sorted, (long) pack(nx, ny, nz) << 3 | size) < 0) {
                    mask |= 1 << f;
                }
            }
            out[i] = (byte) mask;
        }
        return out;
    }

    /**
     * Minibloques reflejados para la copia espejo de una pieza de ancho w (la copia empieza donde acaba el
     * reflejo, asi que un minibloque de x a x + lado pasa a ir de w - x - lado a w - x).
     */
    @Nullable
    public static int[] mirrorX(@Nullable int[] v, float w) {
        if (v == null) {
            return null;
        }
        int width = units(w);
        int[] c = new int[v.length];
        int k = 0;
        for (int i = 0; i + 1 < v.length; i += 2) {
            int pos = v[i];
            int mx = width - x(pos) - side(sizeOf(v[i + 1]));
            if (mx < MIN || mx > MAX) {
                continue;
            }
            c[k++] = pack(mx, y(pos), z(pos));
            c[k++] = v[i + 1];
        }
        return k == 0 ? null : clean(Arrays.copyOf(c, k), Integer.MAX_VALUE);
    }

    /** Mueve todos los minibloques (en cuartos de pixel). */
    @Nullable
    public static int[] shift(@Nullable int[] v, int dx, int dy, int dz) {
        if (v == null || (dx == 0 && dy == 0 && dz == 0)) {
            return v;
        }
        int[] c = new int[v.length];
        int k = 0;
        for (int i = 0; i + 1 < v.length; i += 2) {
            int pos = v[i];
            int x = x(pos) + dx;
            int y = y(pos) + dy;
            int z = z(pos) + dz;
            if (!inRange(x, y, z)) {
                continue;
            }
            c[k++] = pack(x, y, z);
            c[k++] = v[i + 1];
        }
        return k == 0 ? null : Arrays.copyOf(c, k);
    }

    /** Une dos listas (los de b ganan si coinciden). */
    @Nullable
    public static int[] merge(@Nullable int[] a, @Nullable int[] b) {
        if (a == null) {
            return b == null ? null : b.clone();
        }
        if (b == null) {
            return a;
        }
        int[] all = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return clean(all, Integer.MAX_VALUE);
    }

    /** Volumen de una lista en pixeles cubicos. */
    public static double volume(@Nullable int[] v) {
        if (v == null) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i + 1 < v.length; i += 2) {
            double s = pixels(sizeOf(v[i + 1]));
            sum += s * s * s;
        }
        return sum;
    }

    // ---------------------------------------------------------------------------------------
    // Texto (para el JSON), en Base64
    // ---------------------------------------------------------------------------------------

    /**
     * true si la lista se puede guardar como antes de que hubiera tamanos (todos de 1 pixel y en pixeles enteros):
     * asi lo que no usa tamanos se sigue abriendo con versiones anteriores del mod.
     */
    public static boolean legacy(int[] v) {
        for (int i = 0; i + 1 < v.length; i += 2) {
            int pos = v[i];
            if (sizeOf(v[i + 1]) != DEFAULT_SIZE || x(pos) % UNIT != 0 || y(pos) % UNIT != 0 || z(pos) % UNIT != 0) {
                return false;
            }
        }
        return true;
    }

    /** Como antes de los tamanos ("voxels"): 6 bytes por minibloque (x, y, z en pixeles, rojo, verde, azul). */
    public static String encodeLegacy(int[] v) {
        int n = v.length / 2;
        byte[] bytes = new byte[n * 6];
        for (int i = 0; i < n; i++) {
            int pos = v[i * 2];
            int c = v[i * 2 + 1];
            bytes[i * 6] = (byte) (x(pos) / UNIT);
            bytes[i * 6 + 1] = (byte) (y(pos) / UNIT);
            bytes[i * 6 + 2] = (byte) (z(pos) / UNIT);
            bytes[i * 6 + 3] = (byte) (c >> 16);
            bytes[i * 6 + 4] = (byte) (c >> 8);
            bytes[i * 6 + 5] = (byte) c;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Nullable
    public static int[] decodeLegacy(String text) {
        byte[] bytes = bytes(text);
        int n = bytes == null ? 0 : bytes.length / 6;
        if (n == 0) {
            return null;
        }
        int[] v = new int[n * 2];
        for (int i = 0; i < n; i++) {
            v[i * 2] = pack(bytes[i * 6] * UNIT, bytes[i * 6 + 1] * UNIT, bytes[i * 6 + 2] * UNIT);
            v[i * 2 + 1] = (bytes[i * 6 + 3] & 0xFF) << 16 | (bytes[i * 6 + 4] & 0xFF) << 8 | (bytes[i * 6 + 5] & 0xFF);
        }
        return v;
    }

    /** Con tamanos ("voxels2"): 8 bytes por minibloque (posicion empaquetada, tamano, rojo, verde, azul). */
    public static String encode(int[] v) {
        int n = v.length / 2;
        byte[] bytes = new byte[n * 8];
        for (int i = 0; i < n; i++) {
            int pos = v[i * 2];
            int c = v[i * 2 + 1];
            bytes[i * 8] = (byte) (pos >> 24);
            bytes[i * 8 + 1] = (byte) (pos >> 16);
            bytes[i * 8 + 2] = (byte) (pos >> 8);
            bytes[i * 8 + 3] = (byte) pos;
            bytes[i * 8 + 4] = (byte) sizeOf(c);
            bytes[i * 8 + 5] = (byte) (c >> 16);
            bytes[i * 8 + 6] = (byte) (c >> 8);
            bytes[i * 8 + 7] = (byte) c;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Nullable
    public static int[] decode(String text) {
        byte[] bytes = bytes(text);
        int n = bytes == null ? 0 : bytes.length / 8;
        if (n == 0) {
            return null;
        }
        int[] v = new int[n * 2];
        for (int i = 0; i < n; i++) {
            int pos = (bytes[i * 8] & 0xFF) << 24 | (bytes[i * 8 + 1] & 0xFF) << 16 | (bytes[i * 8 + 2] & 0xFF) << 8
                    | (bytes[i * 8 + 3] & 0xFF);
            int rgb = (bytes[i * 8 + 5] & 0xFF) << 16 | (bytes[i * 8 + 6] & 0xFF) << 8 | (bytes[i * 8 + 7] & 0xFF);
            v[i * 2] = pos & 0x3FFFFFFF;
            v[i * 2 + 1] = word(rgb, bytes[i * 8 + 4]);
        }
        return v;
    }

    @Nullable
    private static byte[] bytes(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Total de minibloques del vehiculo. */
    public static int total(VehicleDefinition d) {
        int n = 0;
        for (VehiclePart p : d.parts) {
            n += count(p.voxels);
        }
        return n;
    }

    /** Cuantos minibloques nuevos (que no estaban ya) traeria una lista. */
    public static int added(@Nullable int[] base, int[] extra) {
        Map<Integer, Boolean> have = new HashMap<>();
        if (base != null) {
            for (int i = 0; i + 1 < base.length; i += 2) {
                have.put(base[i], Boolean.TRUE);
            }
        }
        int n = 0;
        for (int i = 0; i + 1 < extra.length; i += 2) {
            if (have.put(extra[i], Boolean.TRUE) == null) {
                n++;
            }
        }
        return n;
    }
}
