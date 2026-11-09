package com.vehiclestudio.data;

import java.util.List;

/**
 * Mueve un grupo rigido de cajas (todas las piezas del vehiculo a la vez) contra cajas fijas (bloques y otras
 * entidades), como hace Minecraft con la caja de una entidad: primero en vertical, luego en horizontal (primero el eje
 * en el que mas se mueve) y, si choca de lado estando en el suelo, intenta subir el escalon. No usa nada de Minecraft.
 * Cajas: {minX, minY, minZ, maxX, maxY, maxZ}.
 */
public final class RigidSweep {
    public static final double EPS = 1.0e-7;

    private RigidSweep() {
    }

    /** Movimiento permitido y con que choco. */
    public record Result(double dx, double dy, double dz, boolean horizontal, boolean vertical, boolean onGround) {
    }

    public static Result move(double[][] boxes, List<double[]> obstacles, double dx, double dy, double dz, double step,
                              boolean onGround) {
        double[] plain = slide(boxes, obstacles, 0, 0, 0, dx, dy, dz);
        boolean horizontal = !same(plain[0], dx) || !same(plain[2], dz);
        boolean vertical = !same(plain[1], dy);
        boolean landed = vertical && dy < 0;
        double rx = plain[0];
        double ry = plain[1];
        double rz = plain[2];
        if (step > 0 && horizontal && (onGround || landed)) {
            // subir el escalon: arriba, de lado y otra vez abajo
            double up = collide(1, boxes, obstacles, 0, 0, 0, step);
            double[] side = slide(boxes, obstacles, 0, up, 0, dx, 0, dz);
            double wantDown = -up + Math.min(0, dy);
            double down = collide(1, boxes, obstacles, side[0], up, side[2], wantDown);
            double stepped = side[0] * side[0] + side[2] * side[2];
            double normal = rx * rx + rz * rz;
            if (stepped > normal + 1.0e-9) {
                rx = side[0];
                ry = up + down;
                rz = side[2];
                horizontal = !same(rx, dx) || !same(rz, dz);
                landed = wantDown < 0 && !same(down, wantDown);
                vertical = landed || !same(ry, dy);
            }
        }
        return new Result(rx, ry, rz, horizontal, vertical, landed);
    }

    /** Vertical y luego horizontal (primero el eje con mas movimiento), desde un desplazamiento inicial. */
    private static double[] slide(double[][] boxes, List<double[]> obstacles, double ox, double oy, double oz, double dx,
                                  double dy, double dz) {
        double ay = collide(1, boxes, obstacles, ox, oy, oz, dy);
        double ax;
        double az;
        if (Math.abs(dx) >= Math.abs(dz)) {
            ax = collide(0, boxes, obstacles, ox, oy + ay, oz, dx);
            az = collide(2, boxes, obstacles, ox + ax, oy + ay, oz, dz);
        } else {
            az = collide(2, boxes, obstacles, ox, oy + ay, oz, dz);
            ax = collide(0, boxes, obstacles, ox, oy + ay, oz + az, dx);
        }
        return new double[]{ax, ay, az};
    }

    /**
     * Cuanto puede moverse en un eje (0 = X, 1 = Y, 2 = Z) todo el grupo, ya desplazado (ox, oy, oz), sin meterse en
     * ninguna caja fija. Las cajas fijas en las que ya esta metido no lo frenan (asi puede salir).
     */
    public static double collide(int axis, double[][] boxes, List<double[]> obstacles, double ox, double oy, double oz,
                                 double d) {
        if (Math.abs(d) < EPS) {
            return 0;
        }
        for (double[] b : boxes) {
            double[] lo = {b[0] + ox, b[1] + oy, b[2] + oz};
            double[] hi = {b[3] + ox, b[4] + oy, b[5] + oz};
            for (double[] o : obstacles) {
                boolean overlap = true;
                for (int a = 0; a < 3 && overlap; a++) {
                    if (a != axis) {
                        overlap = hi[a] > o[a] + EPS && lo[a] < o[a + 3] - EPS;
                    }
                }
                if (!overlap) {
                    continue;
                }
                if (d > 0) {
                    if (o[axis] >= hi[axis] - EPS) {
                        d = Math.min(d, o[axis] - hi[axis]);
                    }
                } else if (o[axis + 3] <= lo[axis] + EPS) {
                    d = Math.max(d, o[axis + 3] - lo[axis]);
                }
            }
            if (Math.abs(d) < EPS) {
                return 0;
            }
        }
        return d;
    }

    /** true si alguna caja se mete en alguna caja fija mas de margin en los tres ejes. */
    public static boolean overlaps(double[][] boxes, List<double[]> obstacles, double margin) {
        for (double[] b : boxes) {
            for (double[] o : obstacles) {
                if (b[3] > o[0] + margin && b[0] < o[3] - margin && b[4] > o[1] + margin && b[1] < o[4] - margin
                        && b[5] > o[2] + margin && b[2] < o[5] - margin) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Caja que envuelve a todas: {minX, minY, minZ, maxX, maxY, maxZ} (null si no hay). */
    public static double[] union(double[][] boxes) {
        if (boxes.length == 0) {
            return null;
        }
        double[] u = boxes[0].clone();
        for (double[] b : boxes) {
            for (int a = 0; a < 3; a++) {
                u[a] = Math.min(u[a], b[a]);
                u[a + 3] = Math.max(u[a + 3], b[a + 3]);
            }
        }
        return u;
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }
}
