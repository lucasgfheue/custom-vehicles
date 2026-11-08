package com.vehiclestudio.data;

/**
 * La parte de la conduccion que no depende del mundo: acelerar, frenar, marcha atras y girar como una bici (la
 * guinada sale de la velocidad, el giro de las ruedas y la distancia entre ejes). Velocidades en bloques por tick.
 */
public final class DrivePhysics {

    /** Lo que cambia de un tick al siguiente. */
    public static final class State {
        /** Velocidad hacia delante (negativa = marcha atras), en bloques por tick. */
        public float speed;
        /** Volante: de -1 (derecha) a 1 (izquierda), con un poco de retardo. */
        public float steer;
    }

    /**
     * Resultado de un tick: cuanto gira (grados; negativo = a la izquierda, como las barcas), si esta frenando y
     * cuanto agarre le queda a las ruedas (1 = normal; menos con el freno de mano, para derrapar).
     */
    public record Step(float deltaYaw, boolean braking, float grip) {
    }

    /**
     * Aceleracion lateral maxima (bloques/tick^2, unos 4 g): a mucha velocidad las curvas se abren y hay que frenar
     * para girar cerrado, como en un juego de coches.
     */
    public static final float MAX_LATERAL = 0.1f;
    /** Giro maximo por tick (grados), como una barca. */
    public static final float MAX_YAW_RATE = 12f;

    private DrivePhysics() {
    }

    public static float maxSpeed(VehicleDefinition d) {
        return d.maxSpeed / 3.6f / 20f;
    }

    public static Step step(VehicleDefinition d, State s, boolean forward, boolean back, boolean left, boolean right,
                            boolean brake, boolean ground, boolean water) {
        float maxV = maxSpeed(d);
        float accel = maxV / (Math.max(0.05f, d.accelTime) * 20f);
        float brakeStep = maxV / (Math.max(0.05f, d.brakeTime) * 20f);
        float reverse = maxV * d.reverseSpeed / 100f;
        boolean gas = forward && !back;
        boolean rev = back && !forward;
        if (ground) {
            if (gas) {
                s.speed = s.speed < 0f ? Math.min(0f, s.speed + brakeStep) : Math.min(maxV, s.speed + accel);
            } else if (rev) {
                s.speed = s.speed > 0f ? Math.max(0f, s.speed - brakeStep) : Math.max(-reverse, s.speed - accel);
            } else {
                s.speed = approach(s.speed, 0f, Math.max(brakeStep * 0.12f, 0.0015f));
            }
            if (brake) {
                s.speed = approach(s.speed, 0f, brakeStep * 1.5f);
            }
        }
        if (water) {
            s.speed *= 0.8f;
        }
        boolean braking = brake || (rev && s.speed > 0.01f) || (gas && s.speed < -0.01f);
        float target = (left ? 1f : 0f) - (right ? 1f : 0f);
        s.steer = approach(s.steer, target, 0.12f);
        float yawRate = 0f;
        float grip = 1f;
        if (ground && Math.abs(s.speed) > 1.0e-4f && s.steer != 0f) {
            // como una bici: la guinada sale de la velocidad, el giro de las ruedas y la distancia entre ejes
            double turn = Math.tan(Math.toRadians(d.steerAngle * s.steer));
            double rate = s.speed * turn / d.wheelbase();
            // sin pasar de la aceleracion lateral maxima (el freno de mano deja cerrar mas la curva, derrapando)
            double cap = MAX_LATERAL / Math.abs(s.speed) * (brake ? 1.6 : 1.0);
            cap = Math.min(cap, Math.toRadians(MAX_YAW_RATE));
            rate = Math.max(-cap, Math.min(cap, rate));
            yawRate = (float) Math.toDegrees(rate);
        }
        if (ground && brake && Math.abs(s.speed) > maxV * 0.15f) {
            grip = 0.35f;
        }
        return new Step(-yawRate, braking, grip);
    }

    public static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
