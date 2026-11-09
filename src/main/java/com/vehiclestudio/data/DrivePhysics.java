package com.vehiclestudio.data;

/**
 * La parte de la conduccion que no depende del mundo: acelerar, frenar, marcha atras y girar como una bici (la
 * guinada sale de la velocidad, el giro de las ruedas y la distancia entre ejes), volar como un helicoptero o un
 * avion, y el turbo. Velocidades en bloques por tick.
 */
public final class DrivePhysics {

    /** Teclas del conductor en un tick. driven = hay alguien conduciendo (aunque no pulse nada). */
    public record Input(boolean forward, boolean back, boolean left, boolean right, boolean brake, boolean up,
                        boolean down, boolean turbo, boolean driven) {
        /** Nadie conduce. */
        public static final Input NONE = new Input(false, false, false, false, false, false, false, false, false);
        /** Alguien conduce pero no pulsa nada (por ejemplo, con el inventario abierto). */
        public static final Input IDLE = new Input(false, false, false, false, false, false, false, false, true);
    }

    /** Lo que cambia de un tick al siguiente. */
    public static final class State {
        /** Velocidad hacia delante (negativa = marcha atras), en bloques por tick. */
        public float speed;
        /** Volante: de -1 (derecha) a 1 (izquierda), con un poco de retardo. */
        public float steer;
        /** Velocidad vertical mientras vuela (bloques por tick). */
        public float vy;
        /** Deposito del turbo: de 0 (vacio) a 1 (lleno). */
        public float energy = 1f;
        /** Con el turbo encendido en este tick. */
        public boolean boosting;
        /** Volando (en el aire con el vuelo controlado). */
        public boolean flying;
    }

    /**
     * Resultado de un tick: cuanto gira (grados; negativo = a la izquierda, como las barcas), si esta frenando,
     * cuanto agarre tiene (1 = va justo hacia donde apunta; menos = derrapa o planea), la velocidad vertical si vuela
     * (NaN = la gravedad de siempre) y la inclinacion que deberia verse (grados; pitch positivo = morro arriba, roll
     * positivo = baja el lado izquierdo).
     */
    public record Step(float deltaYaw, boolean braking, float grip, float vy, boolean boosting, boolean flying,
                       float pitch, float roll) {
    }

    /**
     * Aceleracion lateral maxima (bloques/tick^2, unos 4 g): a mucha velocidad las curvas se abren y hay que frenar
     * para girar cerrado, como en un juego de coches.
     */
    public static final float MAX_LATERAL = 0.1f;
    /** Giro maximo por tick (grados), como una barca. */
    public static final float MAX_YAW_RATE = 12f;
    /** Lo rapido que baja un helicoptero sin conductor (bloques por tick): aterriza suave. */
    public static final float AUTOROTATION = 0.1f;
    /** Lo que mas se hunde un avion sin velocidad (bloques por tick). */
    public static final float STALL_SINK = 0.3f;

    private DrivePhysics() {
    }

    /** Velocidad maxima en tierra (bloques por tick). */
    public static float maxSpeed(VehicleDefinition d) {
        return d.maxSpeed / 3.6f / 20f;
    }

    /** Velocidad maxima en el aire (bloques por tick). */
    public static float airSpeed(VehicleDefinition d) {
        return d.airSpeed / 3.6f / 20f;
    }

    /** Velocidad para despegar un avion (bloques por tick). */
    public static float takeoffSpeed(VehicleDefinition d) {
        return d.takeoffSpeed() / 3.6f / 20f;
    }

    /** Subida maxima (bloques por tick). */
    public static float climbSpeed(VehicleDefinition d) {
        return d.climb / 20f;
    }

    /** Giro en el aire (grados por tick): 4 con el giro de las ruedas de serie (35). */
    public static float airTurnRate(VehicleDefinition d) {
        return 4f * d.steerAngle / 35f;
    }

    /** Lo que sostiene un avion segun su velocidad: 0 por debajo del 70 % de la de despegar, 1 desde ella. */
    public static float lift(VehicleDefinition d, float speed) {
        float t = takeoffSpeed(d);
        if (t <= 0f) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, (Math.abs(speed) - t * 0.7f) / (t * 0.3f)));
    }

    /** Version de antes (sin vuelo ni turbo), para el coche de siempre. */
    public static Step step(VehicleDefinition d, State s, boolean forward, boolean back, boolean left, boolean right,
                            boolean brake, boolean ground, boolean water) {
        return step(d, s, new Input(forward, back, left, right, brake, false, false, false, true), ground, water);
    }

    public static Step step(VehicleDefinition d, State s, Input in, boolean ground, boolean water) {
        boolean canFly = d.canFly();
        // --- turbo: se gasta mientras se pulsa y se llena solo ---
        // una vez vacio, no vuelve a encenderse hasta tener un poco (si no, parpadea con la tecla pulsada)
        boolean turbo = d.hasTurbo() && in.turbo() && in.driven() && (s.boosting ? s.energy > 0f : s.energy >= 0.15f);
        if (turbo) {
            s.energy = Math.max(0f, s.energy - 1f / (Math.max(0.5f, d.turboTime) * 20f));
        } else if (!in.turbo() || !in.driven()) {
            // se llena al soltar la tecla (con la tecla pulsada y el deposito vacio no hace nada)
            s.energy = d.turboRecharge <= 0f ? 1f : Math.min(1f, s.energy + 1f / (d.turboRecharge * 20f));
        }
        s.boosting = turbo;
        float boost = turbo ? 1f + d.turbo / 100f : 1f;

        // los que vuelan nunca caen a plomo: en el aire siempre van con el vuelo controlado
        boolean airborne = canFly && !ground && !water;
        boolean gas = in.forward() && !in.back();
        boolean rev = in.back() && !in.forward();
        float target = (in.left() ? 1f : 0f) - (in.right() ? 1f : 0f);
        s.steer = approach(s.steer, target, 0.12f);
        if (airborne) {
            return air(d, s, in, gas, rev, boost);
        }
        s.flying = false;

        // --- en tierra (o en el agua) ---
        float baseV = maxSpeed(d);
        float maxV = baseV * boost;
        float accel = baseV / (Math.max(0.05f, d.accelTime) * 20f) * (turbo ? 1.5f : 1f);
        float brakeStep = baseV / (Math.max(0.05f, d.brakeTime) * 20f);
        float reverse = baseV * d.reverseSpeed / 100f;
        if (ground) {
            if (gas) {
                if (s.speed < 0f) {
                    s.speed = Math.min(0f, s.speed + brakeStep);
                } else if (s.speed > maxV) {
                    s.speed = Math.max(maxV, s.speed - brakeStep * 0.25f); // al acabarse el turbo, poco a poco
                } else {
                    s.speed = Math.min(maxV, s.speed + accel);
                }
            } else if (rev) {
                s.speed = s.speed > 0f ? Math.max(0f, s.speed - brakeStep) : Math.max(-reverse, s.speed - accel);
            } else {
                s.speed = approach(s.speed, 0f, Math.max(brakeStep * 0.12f, 0.0015f));
            }
            // el freno de mano (los que vuelan usan Espacio para subir)
            if (in.brake() && !canFly) {
                s.speed = approach(s.speed, 0f, brakeStep * 1.5f);
            }
        }
        if (water) {
            s.speed *= 0.8f;
        }
        boolean handbrake = in.brake() && !canFly;
        boolean braking = handbrake || (rev && s.speed > 0.01f) || (gas && s.speed < -0.01f);
        float yawRate = 0f;
        float grip = 1f;
        if (ground && Math.abs(s.speed) > 1.0e-4f && s.steer != 0f) {
            // como una bici: la guinada sale de la velocidad, el giro de las ruedas y la distancia entre ejes
            double turn = Math.tan(Math.toRadians(d.steerAngle * s.steer));
            double rate = s.speed * turn / d.wheelbase();
            // sin pasar de la aceleracion lateral maxima (el freno de mano deja cerrar mas la curva, derrapando)
            double cap = MAX_LATERAL / Math.abs(s.speed) * (handbrake ? 1.6 : 1.0);
            cap = Math.min(cap, Math.toRadians(MAX_YAW_RATE));
            rate = Math.max(-cap, Math.min(cap, rate));
            yawRate = (float) Math.toDegrees(rate);
        }
        if (ground && handbrake && Math.abs(s.speed) > baseV * 0.15f) {
            grip = 0.35f;
        }

        // --- despegar ---
        float vy = Float.NaN;
        if (canFly && (ground || water) && in.driven() && in.up()) {
            boolean ready = d.flight == FlightMode.HELICOPTER || s.speed >= takeoffSpeed(d);
            if (ready) {
                s.vy = climbSpeed(d) * 0.6f;
                s.flying = true;
                vy = s.vy;
            }
        }
        if (Float.isNaN(vy)) {
            s.vy = Float.NaN;
        }
        return new Step(-yawRate, braking, grip, vy, turbo, s.flying, 0f, 0f);
    }

    /** En el aire: velocidad, giro y subida segun el modo de vuelo. */
    private static Step air(VehicleDefinition d, State s, Input in, boolean gas, boolean rev, float boost) {
        boolean plane = d.flight == FlightMode.PLANE;
        float baseV = airSpeed(d);
        float maxV = baseV * boost;
        float accel = baseV / (Math.max(0.05f, d.accelTime) * 20f) * (boost > 1f ? 1.5f : 1f);
        float brakeStep = baseV / (Math.max(0.05f, d.brakeTime) * 20f);
        float reverse = plane ? 0f : baseV * d.reverseSpeed / 100f;
        if (gas) {
            if (s.speed < 0f) {
                s.speed = Math.min(0f, s.speed + brakeStep);
            } else if (s.speed > maxV) {
                s.speed = Math.max(maxV, s.speed - brakeStep * 0.25f);
            } else {
                s.speed = Math.min(maxV, s.speed + accel);
            }
        } else if (rev) {
            s.speed = s.speed > 0f ? Math.max(0f, s.speed - brakeStep) : Math.max(-reverse, s.speed - accel);
        } else {
            // sin acelerar: un helicoptero se va parando; un avion planea y pierde velocidad despacio
            s.speed = approach(s.speed, 0f, Math.max(brakeStep * (plane ? 0.04f : 0.2f), 0.001f));
        }
        if (plane && s.speed < 0f) {
            s.speed = 0f;
        }

        // giro: un helicoptero gira en el sitio; un avion, como mucho lo que deja la aceleracion lateral
        double rate = Math.toRadians(airTurnRate(d) * s.steer);
        if (plane) {
            double cap = MAX_LATERAL * 1.5 / Math.max(0.05, Math.abs(s.speed));
            rate = Math.max(-cap, Math.min(cap, rate));
        }
        float yawRate = (float) Math.toDegrees(rate);

        // subir y bajar
        float climb = climbSpeed(d);
        float targetVy;
        if (plane) {
            float lift = lift(d, s.speed);
            float sink = -STALL_SINK * (1f - lift);
            if (in.driven() && in.up()) {
                targetVy = lift >= 1f ? climb : sink + climb * lift;
            } else if (in.driven() && in.down()) {
                targetVy = -climb * 1.2f;
            } else {
                targetVy = sink;
            }
        } else if (!in.driven()) {
            targetVy = -AUTOROTATION;
        } else if (in.up() && !in.down()) {
            targetVy = climb;
        } else if (in.down() && !in.up()) {
            targetVy = -climb;
        } else {
            targetVy = 0f;
        }
        float vy = Float.isNaN(s.vy) ? 0f : s.vy;
        s.vy = approach(vy, targetVy, 0.015f + climb * 0.12f);
        s.flying = true;

        // como se ve: el avion levanta el morro al subir y se inclina en las curvas; el helicoptero lo baja al avanzar
        float pitch;
        float roll;
        if (plane) {
            pitch = (float) Math.toDegrees(Math.atan2(s.vy, Math.max(0.05f, Math.abs(s.speed)))) * 0.9f;
            pitch = Math.max(-35f, Math.min(35f, pitch));
            roll = s.steer * 28f;
        } else {
            pitch = -12f * Math.max(-1f, Math.min(1f, s.speed / Math.max(1.0e-4f, baseV)));
            roll = s.steer * 8f;
        }
        boolean braking = rev && s.speed > 0.01f;
        float grip = plane ? 0.45f : 0.3f;
        return new Step(-yawRate, braking, grip, s.vy, s.boosting, true, pitch, roll);
    }

    public static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
