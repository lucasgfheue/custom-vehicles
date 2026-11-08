package com.vehiclestudio.data;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minibloques: cubitos de un pixel pegados a una pieza (para cascos, armaduras, coronas...). Se guardan en
 * {@link VehiclePart#voxels} como pares de enteros {posicion, color}: la posicion son tres coordenadas de -128
 * a 127 relativas a la esquina minima de la pieza (en pixeles) y el color es RGB.
 * <p>
 * Los arrays no se modifican nunca: cada cambio devuelve uno nuevo (asi el dibujado puede guardar en cache que
 * caras se ven).
 */
public final class Voxels {
    /** Maximo de minibloques entre todas las piezas de un vehiculo. */
    public static final int MAX_TOTAL = 6000;
    public static final int MIN = -128;
    public static final int MAX = 127;

    /** Vecino en la direccion de cada cara de {@link PaintLayout} (frente -Z, atras +Z, izq +X, dcha -X...). */
    public static final int[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private Voxels() {
    }

    public static int pack(int x, int y, int z) {
        return (x & 0xFF) << 16 | (y & 0xFF) << 8 | (z & 0xFF);
    }

    public static int x(int pos) {
        return (byte) (pos >> 16);
    }

    public static int y(int pos) {
        return (byte) (pos >> 8);
    }

    public static int z(int pos) {
        return (byte) pos;
    }

    public static boolean inRange(int x, int y, int z) {
        return x >= MIN && x <= MAX && y >= MIN && y <= MAX && z >= MIN && z <= MAX;
    }

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

    /** Copia con un minibloque en esa posicion (si ya habia uno, cambia su color). */
    public static int[] with(@Nullable int[] v, int pos, int rgb) {
        int i = indexOf(v, pos);
        if (v != null && i >= 0) {
            if ((v[i * 2 + 1] & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
                return v;
            }
            int[] c = v.clone();
            c[i * 2 + 1] = rgb & 0xFFFFFF;
            return c;
        }
        int n = v == null ? 0 : v.length;
        int[] c = v == null ? new int[2] : Arrays.copyOf(v, n + 2);
        c[n] = pos;
        c[n + 1] = rgb & 0xFFFFFF;
        return c;
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
     * Arregla una lista leida de un archivo: pares completos, sin repetidos (gana el ultimo), colores RGB y como
     * mucho {@code budget} minibloques. null si queda vacia.
     */
    @Nullable
    public static int[] clean(@Nullable int[] v, int budget) {
        if (v == null || v.length < 2 || budget <= 0) {
            return null;
        }
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < v.length; i += 2) {
            int pos = v[i] & 0xFFFFFF;
            map.remove(pos);
            map.put(pos, v[i + 1] & 0xFFFFFF);
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
     * Caras que se ven de cada minibloque: bit f (cara f de {@link PaintLayout}) a 1 si no hay otro minibloque
     * pegado en esa direccion.
     */
    public static byte[] exposed(int[] v) {
        int n = v.length / 2;
        int[] sorted = new int[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = v[i * 2];
        }
        Arrays.sort(sorted);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int pos = v[i * 2];
            int x = x(pos);
            int y = y(pos);
            int z = z(pos);
            int mask = 0;
            for (int f = 0; f < 6; f++) {
                int nx = x + NORMALS[f][0];
                int ny = y + NORMALS[f][1];
                int nz = z + NORMALS[f][2];
                if (!inRange(nx, ny, nz) || Arrays.binarySearch(sorted, pack(nx, ny, nz)) < 0) {
                    mask |= 1 << f;
                }
            }
            out[i] = (byte) mask;
        }
        return out;
    }

    /**
     * Minibloques reflejados para la copia espejo de una pieza de ancho w (la copia empieza donde acaba el
     * reflejo, asi que cada minibloque pasa de x a w - 1 - x).
     */
    @Nullable
    public static int[] mirrorX(@Nullable int[] v, float w) {
        if (v == null) {
            return null;
        }
        int width = Math.round(w);
        int[] c = new int[v.length];
        int k = 0;
        for (int i = 0; i + 1 < v.length; i += 2) {
            int pos = v[i];
            int mx = width - 1 - x(pos);
            if (mx < MIN || mx > MAX) {
                continue;
            }
            c[k++] = pack(mx, y(pos), z(pos));
            c[k++] = v[i + 1];
        }
        return k == 0 ? null : clean(Arrays.copyOf(c, k), Integer.MAX_VALUE);
    }

    /** Mueve todos los minibloques (por ejemplo cuando la pieza cambia de tamano por el lado negativo). */
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

    // ---------------------------------------------------------------------------------------
    // Texto (para el JSON): 6 bytes por minibloque (x, y, z, rojo, verde, azul) en Base64
    // ---------------------------------------------------------------------------------------

    public static String encode(int[] v) {
        int n = v.length / 2;
        byte[] bytes = new byte[n * 6];
        for (int i = 0; i < n; i++) {
            int pos = v[i * 2];
            int c = v[i * 2 + 1];
            bytes[i * 6] = (byte) (pos >> 16);
            bytes[i * 6 + 1] = (byte) (pos >> 8);
            bytes[i * 6 + 2] = (byte) pos;
            bytes[i * 6 + 3] = (byte) (c >> 16);
            bytes[i * 6 + 4] = (byte) (c >> 8);
            bytes[i * 6 + 5] = (byte) c;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Nullable
    public static int[] decode(String text) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int n = bytes.length / 6;
        if (n == 0) {
            return null;
        }
        int[] v = new int[n * 2];
        for (int i = 0; i < n; i++) {
            v[i * 2] = (bytes[i * 6] & 0xFF) << 16 | (bytes[i * 6 + 1] & 0xFF) << 8 | (bytes[i * 6 + 2] & 0xFF);
            v[i * 2 + 1] = (bytes[i * 6 + 3] & 0xFF) << 16 | (bytes[i * 6 + 4] & 0xFF) << 8 | (bytes[i * 6 + 5] & 0xFF);
        }
        return v;
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
