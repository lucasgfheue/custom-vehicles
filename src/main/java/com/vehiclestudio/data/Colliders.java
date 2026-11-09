package com.vehiclestudio.data;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Cajas de choque del vehiculo a partir de sus piezas. No usa nada de Minecraft.
 * <ul>
 *     <li>Para el suelo y las paredes (el vehiculo contra los bloques): cada pieza solida partida en trozos pequenos,
 *     para que al girar el vehiculo sus cajas (siempre rectas en el mundo) se ajusten a la forma.</li>
 *     <li>Para los jugadores y los mobs: cajas finas solo alrededor de quien las puede tocar. Los trozos de pieza se
 *     parten donde mas falta hace, asi un vehiculo largo y hueco girado en diagonal sigue siendo hueco por dentro. El
 *     cliente pone para su jugador cajas que encierran las piezas ({@link #near}); el servidor, para los mobs, cajas
 *     que caben dentro ({@link #inner}), para no frenar nunca a un jugador donde su cliente le deja estar.</li>
 *     <li>Para hacer clic: el rayo contra las piezas tal cual son ({@link #raycast}).</li>
 * </ul>
 * Cajas del modelo en pixeles {x0, y0, z0, x1, y1, z1}; cajas del mundo en bloques, relativas a la posicion del
 * vehiculo.
 */
public final class Colliders {
    /** Lado maximo (pixeles) de los trozos para chocar con los bloques. */
    public static final float TERRAIN_SEGMENT = 12f;
    /** Como mucho, trozos para el suelo y las paredes. */
    public static final int MAX_TERRAIN = 320;
    /** Cajas para jugadores y mobs (cada una es una entidad, asi que pocas: se ponen cerca de quien las toca). */
    public static final int ENTITY_BOXES = 40;
    /** Lo que, como mucho, una caja para jugadores sobresale de su pieza cerca de quien la toca (bloques). */
    public static final double TOLERANCE = 0.09;
    /** Lo que, como mucho, un mob se puede meter en una pieza girada (con las cajas de dentro del servidor). */
    public static final double INNER_TOLERANCE = 0.15;
    /** Como mucho, trozos por calculo (piezas enormes con mucha gente alrededor). */
    private static final int MAX_CELLS = 6000;
    /** Trozo mas pequeno que merece la pena partir (bloques). */
    private static final double MIN_CELL = 0.02;
    /** Un eje del modelo que se desvia menos que esto de un eje del mundo cuenta como recto. */
    private static final double STRAIGHT = 1.0e-4;

    private Colliders() {
    }

    // ---------------------------------------------------------------------------------------
    // Contra los bloques: trozos en el espacio del modelo
    // ---------------------------------------------------------------------------------------

    /** Una caja por pieza solida (la de la pieza ya girada en reposo), sin las que quedan dentro de otras. */
    public static List<float[]> modelBoxes(VehicleDefinition d) {
        return build(d, Float.MAX_VALUE);
    }

    /** Las piezas solidas en trozos de como mucho TERRAIN_SEGMENT pixeles de lado (en ancho y fondo). */
    public static List<float[]> terrainBoxes(VehicleDefinition d) {
        float seg = TERRAIN_SEGMENT;
        List<float[]> out = build(d, seg);
        while (out.size() > MAX_TERRAIN && seg < 256f) {
            seg *= 1.5f;
            out = build(d, seg);
        }
        return out;
    }

    private static List<float[]> build(VehicleDefinition d, float seg) {
        Matrix4f[] mats = PoseSolver.solve(d.parts, null, null);
        List<float[]> out = new ArrayList<>();
        Vector3f v = new Vector3f();
        for (int i = 0; i < d.parts.size(); i++) {
            VehiclePart p = d.parts.get(i);
            if (!p.solid || p.w < 0.01f || p.h < 0.01f || p.d < 0.01f) {
                continue;
            }
            int nx = Math.max(1, (int) Math.ceil(p.w / seg - 1.0e-4f));
            int nz = Math.max(1, (int) Math.ceil(p.d / seg - 1.0e-4f));
            boolean tilted = Math.abs(p.rx) > 1f || Math.abs(p.rz) > 1f;
            int ny = tilted ? Math.max(1, (int) Math.ceil(p.h / seg - 1.0e-4f)) : 1;
            for (int ix = 0; ix < nx; ix++) {
                for (int iy = 0; iy < ny; iy++) {
                    for (int iz = 0; iz < nz; iz++) {
                        float x0 = p.x + p.w * ix / nx;
                        float x1 = p.x + p.w * (ix + 1) / nx;
                        float y0 = p.y + p.h * iy / ny;
                        float y1 = p.y + p.h * (iy + 1) / ny;
                        float z0 = p.z + p.d * iz / nz;
                        float z1 = p.z + p.d * (iz + 1) / nz;
                        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                        for (int c = 0; c < 8; c++) {
                            mats[i].transformPosition((c & 1) == 0 ? x0 : x1, (c & 2) == 0 ? y0 : y1,
                                    (c & 4) == 0 ? z0 : z1, v);
                            box[0] = Math.min(box[0], v.x);
                            box[1] = Math.min(box[1], v.y);
                            box[2] = Math.min(box[2], v.z);
                            box[3] = Math.max(box[3], v.x);
                            box[4] = Math.max(box[4], v.y);
                            box[5] = Math.max(box[5], v.z);
                        }
                        out.add(box);
                    }
                }
            }
        }
        return prune(out);
    }

    /** Quita las cajas que quedan del todo dentro de otra. */
    private static List<float[]> prune(List<float[]> boxes) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            float[] a = boxes.get(i);
            boolean inside = false;
            for (int j = 0; j < boxes.size() && !inside; j++) {
                if (i == j) {
                    continue;
                }
                float[] b = boxes.get(j);
                boolean contains = b[0] <= a[0] && b[1] <= a[1] && b[2] <= a[2] && b[3] >= a[3] && b[4] >= a[4]
                        && b[5] >= a[5];
                // dos iguales: se queda la primera
                boolean equal = contains && b[0] == a[0] && b[1] == a[1] && b[2] == a[2] && b[3] == a[3]
                        && b[4] == a[4] && b[5] == a[5];
                inside = contains && (!equal || j < i);
            }
            if (!inside) {
                out.add(a);
            }
        }
        return out;
    }

    /** Las cajas del modelo en el mundo (bloques, relativas al vehiculo) con la matriz del modelo al mundo. */
    public static double[][] toWorld(List<float[]> boxes, Matrix4f m) {
        double[][] out = new double[boxes.size()][];
        Vector3f v = new Vector3f();
        for (int i = 0; i < boxes.size(); i++) {
            float[] b = boxes.get(i);
            double[] w = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                    -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int c = 0; c < 8; c++) {
                m.transformPosition((c & 1) == 0 ? b[0] : b[3], (c & 2) == 0 ? b[1] : b[4], (c & 4) == 0 ? b[2] : b[5],
                        v);
                w[0] = Math.min(w[0], v.x);
                w[1] = Math.min(w[1], v.y);
                w[2] = Math.min(w[2], v.z);
                w[3] = Math.max(w[3], v.x);
                w[4] = Math.max(w[4], v.y);
                w[5] = Math.max(w[5], v.z);
            }
            out[i] = w;
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // Las piezas tal cual (cajas giradas) en el mundo
    // ---------------------------------------------------------------------------------------

    /** Las piezas (solo las solidas o todas) listas para ponerlas en el mundo. Cambia solo con la definicion. */
    public static final class Body {
        final float[][] boxes;
        final Matrix4f[] mats;

        Body(float[][] boxes, Matrix4f[] mats) {
            this.boxes = boxes;
            this.mats = mats;
        }

        public int size() {
            return this.boxes.length;
        }
    }

    public static Body body(VehicleDefinition d, boolean solidOnly) {
        Matrix4f[] all = PoseSolver.solve(d.parts, null, null);
        List<float[]> boxes = new ArrayList<>();
        List<Matrix4f> mats = new ArrayList<>();
        for (int i = 0; i < d.parts.size(); i++) {
            VehiclePart p = d.parts.get(i);
            if ((solidOnly && !p.solid) || p.w < 0.01f || p.h < 0.01f || p.d < 0.01f) {
                continue;
            }
            boxes.add(new float[]{p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d});
            mats.add(all[i]);
        }
        return new Body(boxes.toArray(new float[0][]), mats.toArray(new Matrix4f[0]));
    }

    /**
     * Un {@link Body} colocado con una matriz del modelo al mundo (bloques, relativo al vehiculo). Cada pieza es una
     * caja girada: su esquina de origen y lo que avanza en el mundo un pixel a lo largo de cada uno de sus ejes.
     */
    public static final class Frame {
        final int n;
        final float[][] boxes;
        final double[] origin;
        final double[] axes;
        final double[] inv2;
        final double[] scale;
        final double[] crooked;
        final boolean[] straight;
        final double[] bounds;

        Frame(Body b, Matrix4f m) {
            this.n = b.size();
            this.boxes = b.boxes;
            this.origin = new double[3 * this.n];
            this.axes = new double[9 * this.n];
            this.inv2 = new double[this.n];
            this.scale = new double[this.n];
            this.crooked = new double[3 * this.n];
            this.straight = new boolean[this.n];
            this.bounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                    -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            Vector3f v = new Vector3f();
            for (int i = 0; i < this.n; i++) {
                b.mats[i].transformPosition(0f, 0f, 0f, v);
                m.transformPosition(v.x, v.y, v.z, v);
                this.origin[3 * i] = v.x;
                this.origin[3 * i + 1] = v.y;
                this.origin[3 * i + 2] = v.z;
                double len = 0;
                boolean allStraight = true;
                for (int j = 0; j < 3; j++) {
                    b.mats[i].transformDirection(j == 0 ? 1f : 0f, j == 1 ? 1f : 0f, j == 2 ? 1f : 0f, v);
                    m.transformDirection(v.x, v.y, v.z, v);
                    int k = 9 * i + 3 * j;
                    this.axes[k] = v.x;
                    this.axes[k + 1] = v.y;
                    this.axes[k + 2] = v.z;
                    double l = Math.sqrt((double) v.x * v.x + (double) v.y * v.y + (double) v.z * v.z);
                    len += l;
                    double big = Math.max(Math.abs(v.x), Math.max(Math.abs(v.y), Math.abs(v.z)));
                    double c = l > 0 ? 1.0 - big / l : 0.0;
                    if (c < STRAIGHT) {
                        c = 0.0;
                    } else {
                        allStraight = false;
                    }
                    this.crooked[3 * i + j] = c;
                }
                this.scale[i] = len / 3.0;
                this.inv2[i] = this.scale[i] > 0 ? 1.0 / (this.scale[i] * this.scale[i]) : 0.0;
                this.straight[i] = allStraight;
                float[] box = this.boxes[i];
                double[] w = cell(i, box[0], box[1], box[2], box[3], box[4], box[5]);
                for (int k = 0; k < 3; k++) {
                    this.bounds[k] = Math.min(this.bounds[k], w[k]);
                    this.bounds[k + 3] = Math.max(this.bounds[k + 3], w[k + 3]);
                }
            }
        }

        public int size() {
            return this.n;
        }

        /** Caja del mundo que encierra todas las piezas (null si no hay ninguna). */
        public double[] bounds() {
            return this.n == 0 ? null : this.bounds.clone();
        }

        /** Caja del mundo de un trozo de la pieza i (trozo en pixeles del espacio de la pieza). */
        double[] cell(int i, double x0, double y0, double z0, double x1, double y1, double z1) {
            double cx = (x0 + x1) / 2;
            double cy = (y0 + y1) / 2;
            double cz = (z0 + z1) / 2;
            double hx = (x1 - x0) / 2;
            double hy = (y1 - y0) / 2;
            double hz = (z1 - z0) / 2;
            int a = 9 * i;
            double[] w = new double[6];
            for (int k = 0; k < 3; k++) {
                double ax = this.axes[a + k];
                double ay = this.axes[a + 3 + k];
                double az = this.axes[a + 6 + k];
                double c = this.origin[3 * i + k] + ax * cx + ay * cy + az * cz;
                double h = Math.abs(ax) * hx + Math.abs(ay) * hy + Math.abs(az) * hz;
                w[k] = c - h;
                w[k + 3] = c + h;
            }
            return w;
        }

        /** Coordenada j (pixeles, en el espacio de la pieza i) de un punto del mundo. */
        double local(int i, int j, double x, double y, double z) {
            int a = 9 * i + 3 * j;
            double rx = x - this.origin[3 * i];
            double ry = y - this.origin[3 * i + 1];
            double rz = z - this.origin[3 * i + 2];
            return (rx * this.axes[a] + ry * this.axes[a + 1] + rz * this.axes[a + 2]) * this.inv2[i];
        }

        /** Distancia (bloques) de un punto del mundo a la pieza i (0 si esta dentro). */
        double outside(int i, double x, double y, double z) {
            float[] box = this.boxes[i];
            double sum = 0;
            for (int j = 0; j < 3; j++) {
                double l = local(i, j, x, y, z);
                double e = l < box[j] ? box[j] - l : l > box[j + 3] ? l - box[j + 3] : 0.0;
                sum += e * e;
            }
            return Math.sqrt(sum) * this.scale[i];
        }

        /** Lo que mas sobresale de la pieza i una caja del mundo (la distancia de su esquina mas alejada). */
        double sticksOut(int i, double[] w) {
            double most = 0;
            for (int c = 0; c < 8; c++) {
                double d = outside(i, (c & 1) == 0 ? w[0] : w[3], (c & 2) == 0 ? w[1] : w[4], (c & 4) == 0 ? w[2] : w[5]);
                most = Math.max(most, d);
            }
            return most;
        }

        /**
         * La caja recta mas grande centrada en el trozo c de la pieza i que cabe dentro de la pieza y de la caja del
         * trozo w: se encoge, en cada eje de la pieza, lo justo en los ejes del mundo que van por ese eje.
         */
        double[] inside(int i, double[] c, double[] w) {
            float[] box = this.boxes[i];
            double s = this.scale[i];
            double[] h = {(w[3] - w[0]) / 2, (w[4] - w[1]) / 2, (w[5] - w[2]) / 2};
            for (int j = 0; j < 3; j++) {
                double half = (box[j + 3] - box[j]) / 2.0;
                double off = Math.abs((c[j] + c[j + 3]) / 2.0 - (box[j] + box[j + 3]) / 2.0);
                double room = Math.max(0.0, (half - off) * s);
                int a = 9 * i + 3 * j;
                double ux = s > 0 ? Math.abs(this.axes[a]) / s : 0;
                double uy = s > 0 ? Math.abs(this.axes[a + 1]) / s : 0;
                double uz = s > 0 ? Math.abs(this.axes[a + 2]) / s : 0;
                double sum = h[0] * ux + h[1] * uy + h[2] * uz;
                if (sum > room) {
                    double k = sum > 0 ? room / sum : 0.0;
                    if (ux > STRAIGHT) {
                        h[0] *= k;
                    }
                    if (uy > STRAIGHT) {
                        h[1] *= k;
                    }
                    if (uz > STRAIGHT) {
                        h[2] *= k;
                    }
                }
            }
            double cx = (w[0] + w[3]) / 2;
            double cy = (w[1] + w[4]) / 2;
            double cz = (w[2] + w[5]) / 2;
            return new double[]{cx - h[0], cy - h[1], cz - h[2], cx + h[0], cy + h[1], cz + h[2]};
        }

        /** Lo mas lejos que queda una esquina del trozo c de la pieza i de la caja b (lo que b no cubre). */
        double uncovered(int i, double[] c, double[] b) {
            double most = 0;
            int a = 9 * i;
            for (int k = 0; k < 8; k++) {
                double lx = (k & 1) == 0 ? c[0] : c[3];
                double ly = (k & 2) == 0 ? c[1] : c[4];
                double lz = (k & 4) == 0 ? c[2] : c[5];
                double sum = 0;
                for (int d = 0; d < 3; d++) {
                    double p = this.origin[3 * i + d] + this.axes[a + d] * lx + this.axes[a + 3 + d] * ly
                            + this.axes[a + 6 + d] * lz;
                    double e = p < b[d] ? b[d] - p : p > b[d + 3] ? p - b[d + 3] : 0.0;
                    sum += e * e;
                }
                most = Math.max(most, Math.sqrt(sum));
            }
            return most;
        }

        /** El eje (de la pieza) por el que conviene partir un trozo para que su caja se ajuste mejor, o -1. */
        int splitAxis(int i, double[] c) {
            int best = -1;
            double score = 0;
            for (int j = 0; j < 3; j++) {
                double s = (c[j + 3] - c[j]) * this.scale[i] * this.crooked[3 * i + j];
                if (s > score) {
                    score = s;
                    best = j;
                }
            }
            if (best < 0 || (c[best + 3] - c[best]) * this.scale[i] < MIN_CELL) {
                return -1;
            }
            return best;
        }
    }

    public static Frame frame(Body b, Matrix4f m) {
        return new Frame(b, m);
    }

    // ---------------------------------------------------------------------------------------
    // Cajas para jugadores y mobs
    // ---------------------------------------------------------------------------------------

    /**
     * Donde hacen falta cajas: query es la zona que puede tocar alguien en el proximo tick, core su propia caja (para
     * dar antes las mas cercanas) y rank su prioridad (0 = primero).
     */
    public record Zone(double[] query, double[] core, int rank) {
    }

    /**
     * Cajas (del mundo, relativas al vehiculo) que cubren las piezas solidas que tocan alguna zona, como mucho budget.
     * Cada caja encierra su trozo de pieza: nadie se mete en el vehiculo, pero sobresalen un poco cuando la pieza va
     * girada. Se parten primero los trozos que mas sobresalen cerca de alguien, hasta que ninguno sobresale mas de tol
     * dentro de las zonas o no quedan cajas. Es lo del cliente para su jugador.
     */
    public static List<double[]> near(Frame f, List<Zone> zones, int budget, double tol) {
        return place(f, zones, budget, tol, false);
    }

    /**
     * Como {@link #near}, pero cada caja cabe dentro de su pieza (la mas grande centrada en su trozo): nunca tapa un
     * sitio libre, aunque deja meterse un poco en el vehiculo (como mucho tol). Es lo del servidor: asi nunca frena a
     * un jugador en un sitio en el que su cliente le deja estar.
     */
    public static List<double[]> inner(Frame f, List<Zone> zones, int budget, double tol) {
        return place(f, zones, budget, tol, true);
    }

    // trozo: {pieza, caja local x6, caja del mundo x6, rank, distancia, prioridad, eje}
    private static final int LOCAL = 1;
    private static final int WORLD = 7;
    private static final int RANK = 13;
    private static final int DIST = 14;
    private static final int PRIORITY = 15;
    private static final int AXIS = 16;

    private static List<double[]> place(Frame f, List<Zone> zones, int budget, double tol, boolean inner) {
        List<double[]> result = new ArrayList<>();
        if (zones.isEmpty() || budget <= 0 || f.n == 0) {
            return result;
        }
        List<Zone> live = new ArrayList<>();
        for (Zone z : zones) {
            if (intersects(f.bounds, z.query())) {
                live.add(z);
            }
        }
        if (live.isEmpty()) {
            return result;
        }
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(b[PRIORITY], a[PRIORITY]));
        List<double[]> done = new ArrayList<>();
        for (int i = 0; i < f.n; i++) {
            float[] b = f.boxes[i];
            add(evaluate(f, live, i, new double[]{b[0], b[1], b[2], b[3], b[4], b[5]}, tol, inner), open, done);
        }
        int count = open.size() + done.size();
        int splits = 0;
        while (!open.isEmpty() && count < budget && splits < MAX_CELLS) {
            double[] c = open.poll();
            int i = (int) c[0];
            int axis = (int) c[AXIS];
            double[] lo = Arrays.copyOfRange(c, LOCAL, LOCAL + 6);
            double[] hi = lo.clone();
            double mid = (lo[axis] + lo[axis + 3]) / 2;
            lo[axis + 3] = mid;
            hi[axis] = mid;
            count--;
            splits++;
            count += add(evaluate(f, live, i, lo, tol, inner), open, done);
            count += add(evaluate(f, live, i, hi, tol, inner), open, done);
        }
        done.addAll(open);
        done.sort((a, b) -> a[RANK] != b[RANK] ? Double.compare(a[RANK], b[RANK]) : Double.compare(a[DIST], b[DIST]));
        for (int k = 0; k < done.size() && result.size() < budget; k++) {
            result.add(Arrays.copyOfRange(done.get(k), WORLD, WORLD + 6));
        }
        return result;
    }

    private static int add(double[] cell, PriorityQueue<double[]> open, List<double[]> done) {
        if (cell == null) {
            return 0;
        }
        if (cell[AXIS] >= 0) {
            open.add(cell);
        } else {
            done.add(cell);
        }
        return 1;
    }

    /** Un trozo (caja local c de la pieza i) listo para la cola, o null si no toca ninguna zona. */
    private static double[] evaluate(Frame f, List<Zone> live, int i, double[] c, double tol, boolean inner) {
        double[] w = f.cell(i, c[0], c[1], c[2], c[3], c[4], c[5]);
        boolean any = false;
        double rank = Double.MAX_VALUE;
        double dist = Double.MAX_VALUE;
        double nearest = Double.MAX_VALUE;
        double out = 0;
        for (Zone z : live) {
            if (!intersects(w, z.query())) {
                continue;
            }
            any = true;
            double g = gap(w, z.core());
            nearest = Math.min(nearest, g);
            if (z.rank() < rank || z.rank() == rank && g < dist) {
                rank = z.rank();
                dist = g;
            }
            if (!inner && !f.straight[i] && out <= tol) {
                out = Math.max(out, f.sticksOut(i, clip(w, z.query())));
            }
        }
        if (!any) {
            return null;
        }
        double[] box = w;
        if (inner && !f.straight[i]) {
            box = f.inside(i, c, w);
            out = f.uncovered(i, c, box);
        }
        int axis = out > tol ? f.splitAxis(i, c) : -1;
        double[] cell = new double[17];
        cell[0] = i;
        System.arraycopy(c, 0, cell, LOCAL, 6);
        System.arraycopy(box, 0, cell, WORLD, 6);
        cell[RANK] = rank;
        cell[DIST] = dist;
        // lo que mas sobresale (o lo que mas queda sin cubrir), mas cuanto mas cerca de alguien
        cell[PRIORITY] = out / (1.0 + 2.0 * nearest);
        cell[AXIS] = axis;
        return cell;
    }

    /** true si alguna pieza se mete en la caja (del mundo, relativa al vehiculo) mas de tol. */
    public static boolean touches(Frame f, double[] box, double tol) {
        for (double[] l : near(f, List.of(new Zone(box, box, 0)), 4096, tol)) {
            if (l[0] < box[3] - tol && l[3] > box[0] + tol && l[1] < box[4] - tol && l[4] > box[1] + tol
                    && l[2] < box[5] - tol && l[5] > box[2] + tol) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------
    // Clic y distancia, contra las piezas tal cual
    // ---------------------------------------------------------------------------------------

    /**
     * Donde (de 0 a 1 a lo largo del segmento de from a to, relativos al vehiculo) entra el segmento en la primera
     * pieza, o -1 si no toca ninguna. Las piezas en las que ya esta from no cuentan (como desde dentro de una barca).
     */
    public static double raycast(Frame f, double[] from, double[] to) {
        double best = -1;
        for (int i = 0; i < f.n; i++) {
            float[] box = f.boxes[i];
            double t0 = 0.0;
            double t1 = 1.0;
            boolean inside = true;
            boolean miss = false;
            for (int j = 0; j < 3 && !miss; j++) {
                double s = f.local(i, j, from[0], from[1], from[2]);
                double e = f.local(i, j, to[0], to[1], to[2]);
                double lo = box[j];
                double hi = box[j + 3];
                if (s < lo || s > hi) {
                    inside = false;
                }
                double d = e - s;
                if (Math.abs(d) < 1.0e-12) {
                    miss = s < lo || s > hi;
                    continue;
                }
                double ta = (lo - s) / d;
                double tb = (hi - s) / d;
                if (ta > tb) {
                    double t = ta;
                    ta = tb;
                    tb = t;
                }
                t0 = Math.max(t0, ta);
                t1 = Math.min(t1, tb);
                miss = t0 > t1;
            }
            if (miss || inside) {
                continue;
            }
            if (best < 0 || t0 < best) {
                best = t0;
            }
        }
        return best;
    }

    /** Distancia (bloques) de un punto del mundo (relativo al vehiculo) a la pieza mas cercana. */
    public static double distance(Frame f, double[] p) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < f.n; i++) {
            best = Math.min(best, f.outside(i, p[0], p[1], p[2]));
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------
    // Cajas
    // ---------------------------------------------------------------------------------------

    static boolean intersects(double[] a, double[] b) {
        return a[0] < b[3] && a[3] > b[0] && a[1] < b[4] && a[4] > b[1] && a[2] < b[5] && a[5] > b[2];
    }

    private static double[] clip(double[] a, double[] b) {
        return new double[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]),
                Math.min(a[3], b[3]), Math.min(a[4], b[4]), Math.min(a[5], b[5])};
    }

    /** Hueco entre dos cajas (0 si se tocan). */
    private static double gap(double[] a, double[] b) {
        double dx = Math.max(0, Math.max(a[0] - b[3], b[0] - a[3]));
        double dy = Math.max(0, Math.max(a[1] - b[4], b[1] - a[4]));
        double dz = Math.max(0, Math.max(a[2] - b[5], b[2] - a[5]));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
