package com.vehiclestudio.data;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometria del vehiculo sin nada de Minecraft: pose de las piezas (ruedas que ruedan y giran, volante), luces
 * encendidas, puntos de los asientos y salidas de humo, y la matriz del modelo al mundo.
 */
public final class VehicleGeometry {
    private static final float DEG = PoseSolver.DEG;

    private VehicleGeometry() {
    }

    /** Lo que mueve las piezas: distancia rodada (bloques), giro del volante (-1 derecha a 1 izquierda) y luces. */
    public record Motion(float rolled, float steer, boolean lights, boolean braking) {
        public static final Motion REST = new Motion(0f, 0f, false, false);
    }

    /**
     * Matrices de cada pieza. Las ruedas giran sobre su eje segun lo rodado (y las que giran, hacia los lados); el
     * volante gira sobre su eje y el manillar como las ruedas. Las piezas hijas las siguen.
     */
    public static Matrix4f[] pose(VehicleDefinition def, Motion motion) {
        List<VehiclePart> parts = def.parts;
        int n = parts.size();
        // outer: hacia los lados, sobre el eje vertical; inner: rodar o girar sobre el eje de la propia pieza
        float[] outer = new float[n * 3];
        float[] inner = new float[n * 3];
        // Hacia la izquierda del vehiculo (+X del modelo) es un giro negativo en Y (y en Z para el volante).
        float steer = -motion.steer() * def.steerAngle * DEG;
        for (int i = 0; i < n; i++) {
            VehiclePart p = parts.get(i);
            switch (p.kind) {
                case WHEEL -> {
                    float radius = Math.max(0.5f, p.wheelRadius()) / 16f * def.scale;
                    // hacia delante (-Z) la parte de arriba de la rueda va hacia -Z: giro negativo en X
                    inner[i * 3] = -(motion.rolled() / radius);
                    if (p.steers) {
                        outer[i * 3 + 1] = steer;
                    }
                }
                case STEERING -> {
                    if (p.steeringStyle == SteeringStyle.HANDLEBAR) {
                        outer[i * 3 + 1] = steer;
                    } else {
                        inner[i * 3 + 2] = -motion.steer() * 1.6f;
                    }
                }
                default -> {
                }
            }
        }
        return PoseSolver.solve(parts, outer, null, inner);
    }

    /** true si una luz esta encendida con ese estado. */
    public static boolean lit(VehiclePart p, Motion motion) {
        if (p.kind != PartKind.LIGHT) {
            return p.glow;
        }
        return switch (p.lightMode) {
            case ALWAYS -> true;
            case HEAD -> motion.lights();
            case BRAKE -> motion.braking() || motion.lights();
        };
    }

    /**
     * {cx, minY, cz, escala}: el modelo se dibuja centrado en (cx, cz) y con su punto mas bajo en el suelo, en
     * bloques = pixeles * escala / 16 y reflejado en X (como Minecraft: +X del modelo es la izquierda).
     */
    public static float[] layout(VehicleDefinition d) {
        float[] s = d.size();
        return new float[]{s[3], s[4], s[5], d.scale};
    }

    /**
     * Matriz del espacio del modelo (pixeles) a desplazamientos en el mundo (bloques) desde la posicion del
     * vehiculo. Es la misma transformacion que usa el renderer.
     */
    public static Matrix4f modelToWorld(float[] layout, float yaw) {
        float k = layout[3] / 16f;
        return new Matrix4f().rotateY((180f - yaw) * DEG).scale(-k, k, k).translate(-layout[0], -layout[1], -layout[2]);
    }

    /** Puntos de los asientos en el espacio del modelo (pixeles), en el orden de {@link VehicleDefinition#seats()}. */
    public static float[][] seatPoints(VehicleDefinition d) {
        Matrix4f[] mats = PoseSolver.solve(d.parts, null, null);
        List<Integer> seats = d.seats();
        float[][] out = new float[seats.size()][];
        Vector3f v = new Vector3f();
        for (int s = 0; s < seats.size(); s++) {
            int i = seats.get(s);
            float[] p = d.parts.get(i).seatPoint();
            mats[i].transformPosition(p[0], p[1], p[2], v);
            out[s] = new float[]{v.x, v.y, v.z};
        }
        return out;
    }

    /** Salidas de humo: {x, y, z, dirX, dirY, dirZ} en el espacio del modelo. */
    public static float[][] exhausts(VehicleDefinition d) {
        Matrix4f[] mats = PoseSolver.solve(d.parts, null, null);
        List<float[]> out = new ArrayList<>();
        Vector3f v = new Vector3f();
        Vector3f o = new Vector3f();
        for (int i = 0; i < d.parts.size(); i++) {
            VehiclePart p = d.parts.get(i);
            if (p.kind != PartKind.EXHAUST) {
                continue;
            }
            float[] e = Shapes.exhaustOutlet(p);
            mats[i].transformPosition(e[0], e[1], e[2], v);
            mats[i].transformPosition(e[0] + e[3], e[1] + e[4], e[2] + e[5], o);
            out.add(new float[]{v.x, v.y, v.z, o.x - v.x, o.y - v.y, o.z - v.z});
        }
        return out.toArray(new float[0][]);
    }
}
