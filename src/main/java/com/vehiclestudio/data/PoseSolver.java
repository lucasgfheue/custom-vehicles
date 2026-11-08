package com.vehiclestudio.data;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Calcula la matriz de transformacion (en pixeles del modelo) de cada pieza teniendo en cuenta
 * la jerarquia padre/hijo, el pivote, la rotacion y los desplazamientos extra de la animacion.
 */
public final class PoseSolver {
    public static final float DEG = (float) (Math.PI / 180.0);

    private PoseSolver() {
    }

    /** Indices de padre validos y sin ciclos. */
    public static int[] safeParents(List<VehiclePart> parts) {
        int n = parts.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            int p = parts.get(i).parent;
            parent[i] = (p < 0 || p >= n || p == i) ? -1 : p;
        }
        for (int i = 0; i < n; i++) {
            int cur = parent[i];
            int steps = 0;
            while (cur >= 0 && steps <= n) {
                if (cur == i) {
                    parent[i] = -1; // rompe el ciclo
                    break;
                }
                cur = parent[cur];
                steps++;
            }
        }
        return parent;
    }

    /**
     * @param outerRot rotaciones extra en radianes (3 por pieza) o null, alrededor del pivote y en los ejes del
     *                 padre (por ejemplo, girar una rueda hacia los lados siempre sobre el eje vertical)
     * @param extraOff desplazamientos extra en pixeles (3 por pieza) o null
     */
    public static Matrix4f[] solve(List<VehiclePart> parts, @Nullable float[] outerRot, @Nullable float[] extraOff) {
        return solve(parts, outerRot, extraOff, null);
    }

    /**
     * @param innerRot rotaciones extra en radianes (3 por pieza) o null, en los ejes de la propia pieza ya girada
     *                 (por ejemplo, una rueda inclinada rueda sobre su propio eje y un volante inclinado gira sobre
     *                 su columna)
     */
    public static Matrix4f[] solve(List<VehiclePart> parts, @Nullable float[] outerRot, @Nullable float[] extraOff,
                                   @Nullable float[] innerRot) {
        int n = parts.size();
        int[] parent = safeParents(parts);
        Matrix4f[] out = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            compute(i, parts, parent, outerRot, extraOff, innerRot, out);
        }
        return out;
    }

    private static Matrix4f compute(int i, List<VehiclePart> parts, int[] parent, @Nullable float[] outerRot,
                                    @Nullable float[] extraOff, @Nullable float[] innerRot, Matrix4f[] out) {
        if (out[i] != null) {
            return out[i];
        }
        Matrix4f m = parent[i] >= 0
                ? new Matrix4f(compute(parent[i], parts, parent, outerRot, extraOff, innerRot, out))
                : new Matrix4f();
        VehiclePart p = parts.get(i);
        float ox = 0f, oy = 0f, oz = 0f;
        if (extraOff != null) {
            ox = extraOff[i * 3];
            oy = extraOff[i * 3 + 1];
            oz = extraOff[i * 3 + 2];
        }
        m.translate(p.px + ox, p.py + oy, p.pz + oz);
        rotate(m, outerRot, i);
        if (p.rx != 0f || p.ry != 0f || p.rz != 0f) {
            m.rotate(new Quaternionf().rotationZYX(p.rz * DEG, p.ry * DEG, p.rx * DEG));
        }
        rotate(m, innerRot, i);
        m.translate(-p.px, -p.py, -p.pz);
        out[i] = m;
        return m;
    }

    private static void rotate(Matrix4f m, @Nullable float[] rot, int i) {
        if (rot == null) {
            return;
        }
        float rx = rot[i * 3];
        float ry = rot[i * 3 + 1];
        float rz = rot[i * 3 + 2];
        if (rx != 0f || ry != 0f || rz != 0f) {
            m.rotate(new Quaternionf().rotationZYX(rz, ry, rx));
        }
    }

    /**
     * Cuelga la pieza {@code child} de {@code newParent} (-1 = de ninguna) sin que cambie como se ve en reposo: se
     * recalcula su giro, se mueve su pivote y se desplaza su rama entera lo necesario. Asi, a partir de ahora la pieza
     * sigue a su nuevo padre cuando este se mueve o se anima. No hace nada si se crearia un ciclo.
     */
    public static void reparentKeepingPose(List<VehiclePart> parts, int child, int newParent) {
        int n = parts.size();
        if (child < 0 || child >= n || newParent >= n || newParent == child
                || (newParent >= 0 && isDescendant(parts, newParent, child))) {
            return;
        }
        Matrix4f[] rest = solve(parts, null, null);
        Matrix4f local = new Matrix4f();
        if (newParent >= 0) {
            Matrix4f inverse = new Matrix4f(rest[newParent]).invert(new Matrix4f());
            inverse.mul(rest[child], local);
        } else {
            local = new Matrix4f(rest[child]);
        }
        VehiclePart c = parts.get(child);
        // Giro: las columnas de la matriz son los ejes X, Y y Z ya girados.
        Vector3f o = local.transformPosition(0f, 0f, 0f, new Vector3f());
        Vector3f ex = local.transformPosition(1f, 0f, 0f, new Vector3f());
        Vector3f ey = local.transformPosition(0f, 1f, 0f, new Vector3f());
        Vector3f ez = local.transformPosition(0f, 0f, 1f, new Vector3f());
        float r00 = ex.x - o.x;
        float r10 = ex.y - o.y;
        float r20 = ex.z - o.z;
        float r01 = ey.x - o.x;
        float r11 = ey.y - o.y;
        float r21 = ey.z - o.z;
        float r22 = ez.z - o.z;
        float[] angles = eulerZYX(r00, r10, r20, r01, r11, r21, r22);
        Vector3f pivot = local.transformPosition(c.px, c.py, c.pz, new Vector3f());
        float dx = pivot.x - c.px;
        float dy = pivot.y - c.py;
        float dz = pivot.z - c.pz;
        int[] parent = safeParents(parts);
        for (int i = 0; i < n; i++) {
            if (i == child || isDescendantOf(parent, i, child)) {
                parts.get(i).translate(dx, dy, dz);
            }
        }
        c.rx = angles[0];
        c.ry = angles[1];
        c.rz = angles[2];
        c.parent = newParent;
    }

    /**
     * Angulos en grados {x, y, z} tales que Rz(z) * Ry(y) * Rx(x) es la matriz de giro dada (por filas:
     * r00 r01 / r10 r11 / r20 r21 r22, lo que hace falta de ella).
     */
    static float[] eulerZYX(float r00, float r10, float r20, float r01, float r11, float r21, float r22) {
        double sy = Math.max(-1.0, Math.min(1.0, -r20));
        double y = Math.asin(sy);
        double x;
        double z;
        if (Math.abs(sy) < 0.99999) {
            x = Math.atan2(r21, r22);
            z = Math.atan2(r10, r00);
        } else {
            // bloqueo del cardan: basta con un solo giro alrededor de X o Z
            x = 0.0;
            z = Math.atan2(-r01, r11);
        }
        return new float[]{clean(Math.toDegrees(x)), clean(Math.toDegrees(y)), clean(Math.toDegrees(z))};
    }

    // ---------------------------------------------------------------------------------------
    // Giros como matrices 3x3 guardadas por filas (para posar las piezas con el raton)
    // ---------------------------------------------------------------------------------------

    /** Angulos en grados {x, y, z} de una matriz de giro (por filas, 9 numeros). */
    public static float[] eulerOf(float[] r) {
        return eulerZYX(r[0], r[3], r[6], r[1], r[4], r[7], r[8]);
    }

    /** Matriz (por filas) de Rz(z) * Ry(y) * Rx(x), con los angulos en grados: el giro de una pieza. */
    public static float[] rotationZYX(float xDeg, float yDeg, float zDeg) {
        double x = Math.toRadians(xDeg);
        double y = Math.toRadians(yDeg);
        double z = Math.toRadians(zDeg);
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x);
        float cy = (float) Math.cos(y), sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        float[] rz = {cz, -sz, 0f, sz, cz, 0f, 0f, 0f, 1f};
        float[] ry = {cy, 0f, sy, 0f, 1f, 0f, -sy, 0f, cy};
        float[] rx = {1f, 0f, 0f, 0f, cx, -sx, 0f, sx, cx};
        return mul3(mul3(rz, ry), rx);
    }

    /** Matriz (por filas) de un giro de "angle" radianes alrededor del eje (x, y, z), que no hace falta que mida 1. */
    public static float[] axisAngle(float x, float y, float z, float angle) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0e-6f) {
            return new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
        }
        x /= l;
        y /= l;
        z /= l;
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float t = 1f - c;
        return new float[]{
                t * x * x + c, t * x * y - s * z, t * x * z + s * y,
                t * x * y + s * z, t * y * y + c, t * y * z - s * x,
                t * x * z - s * y, t * y * z + s * x, t * z * z + c};
    }

    public static float[] mul3(float[] a, float[] b) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    public static float[] transpose3(float[] a) {
        return new float[]{a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]};
    }

    /**
     * Animaciones propias: el giro extra (grados {x, y, z}, que se suma al de reposo de la pieza) que deja la pieza
     * girada "angle" radianes alrededor del eje (ax, ay, az) del espacio del modelo, partiendo de como estaba con el
     * giro extra "start". parentRot es el giro de su padre en ese momento (por filas; la identidad si no tiene).
     * Cada angulo queda lo mas cerca posible de su valor en start (sin saltos de 360).
     */
    public static float[] rotateDelta(VehiclePart p, float[] start, float[] parentRot, float ax, float ay, float az,
                                      float angle) {
        float[] r0 = rotationZYX(p.rx + start[0], p.ry + start[1], p.rz + start[2]);
        float[] q = axisAngle(ax, ay, az, angle);
        float[] r1 = mul3(mul3(mul3(transpose3(parentRot), q), parentRot), r0);
        float[] e = eulerOf(r1);
        float[] base = {p.rx, p.ry, p.rz};
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            float d = e[k] - base[k];
            while (d - start[k] > 180f) {
                d -= 360f;
            }
            while (d - start[k] < -180f) {
                d += 360f;
            }
            // dentro de lo que se guarda (de -360 a 360)
            while (d > 360f) {
                d -= 360f;
            }
            while (d < -360f) {
                d += 360f;
            }
            out[k] = Math.round(d * 10f) / 10f;
        }
        return out;
    }

    /** Quita el ruido de coma flotante (89.99998 -> 90). */
    private static float clean(double degrees) {
        double r = Math.rint(degrees * 1000.0) / 1000.0;
        return (float) (Math.abs(r) < 1.0e-3 ? 0.0 : r);
    }

    private static boolean isDescendantOf(int[] parent, int candidate, int ancestor) {
        int cur = candidate >= 0 && candidate < parent.length ? parent[candidate] : -1;
        int steps = 0;
        while (cur >= 0 && steps <= parent.length) {
            if (cur == ancestor) {
                return true;
            }
            cur = parent[cur];
            steps++;
        }
        return false;
    }

    /** true si {@code candidate} es descendiente de {@code ancestor}. */
    public static boolean isDescendant(List<VehiclePart> parts, int candidate, int ancestor) {
        int[] parent = safeParents(parts);
        int cur = candidate >= 0 && candidate < parent.length ? parent[candidate] : -1;
        int steps = 0;
        while (cur >= 0 && steps <= parent.length) {
            if (cur == ancestor) {
                return true;
            }
            cur = parent[cur];
            steps++;
        }
        return false;
    }
}
