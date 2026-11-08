package com.vehiclestudio.data;

/** Ayudantes del editor para las piezas: piezas nuevas de cada tipo y la pareja del otro lado. */
public final class VehicleParts {

    private VehicleParts() {
    }

    /**
     * La pieza del otro lado: la que se llama igual cambiando izquierda por derecha o, si no hay, la que esta en el
     * sitio reflejado con el mismo tamano. -1 si esta en el centro o no tiene pareja.
     */
    public static int partner(VehicleDefinition def, int i) {
        if (i < 0 || i >= def.parts.size()) {
            return -1;
        }
        VehiclePart p = def.parts.get(i);
        String mirrored = VehiclePart.mirrorName(p.name);
        if (!mirrored.equals(p.name) && !mirrored.endsWith("(espejo)")) {
            for (int j = 0; j < def.parts.size(); j++) {
                if (j != i && def.parts.get(j).name.equals(mirrored)) {
                    return j;
                }
            }
        }
        float cx = p.x + p.w / 2f;
        if (Math.abs(cx) < 0.25f) {
            return -1;
        }
        int best = -1;
        float bestErr = 1.01f;
        for (int j = 0; j < def.parts.size(); j++) {
            if (j == i) {
                continue;
            }
            VehiclePart q = def.parts.get(j);
            if (q.kind != p.kind) {
                continue;
            }
            float err = Math.abs(q.x + q.w / 2f + cx) + Math.abs(q.y - p.y) + Math.abs(q.z - p.z)
                    + Math.abs(q.w - p.w) + Math.abs(q.h - p.h) + Math.abs(q.d - p.d);
            if (err < bestErr) {
                bestErr = err;
                best = j;
            }
        }
        return best;
    }

    /**
     * Una pieza nueva de ese tipo, en un sitio razonable: las ruedas en una esquina libre, los asientos en el centro,
     * las luces delante (o detras, las de freno), el escape detras...
     */
    public static VehiclePart create(VehicleDefinition def, PartKind kind) {
        float[] b = def.parts.isEmpty() ? new float[]{-12, 0, -24, 12, 16, 24} : def.bounds();
        float halfW = Math.max(6f, (b[3] - b[0]) / 2f);
        float front = Math.min(-8f, b[2]);
        float back = Math.max(8f, b[5]);
        switch (kind) {
            case WHEEL -> {
                // la primera esquina sin rueda: delante izquierda, delante derecha, detras izquierda, detras derecha
                float[][] corners = {{halfW, front * 0.6f}, {-halfW, front * 0.6f}, {halfW, back * 0.6f}, {-halfW, back * 0.6f}};
                for (float[] c : corners) {
                    if (!wheelNear(def, c[0], c[1])) {
                        boolean steers = c[1] < 0;
                        String name = "rueda " + (steers ? "delantera " : "trasera ") + (c[0] > 0 ? "izquierda" : "derecha");
                        return VehiclePart.wheel(name, WheelPreset.CAR, c[0], -1, c[1], steers);
                    }
                }
                return VehiclePart.wheel("rueda", WheelPreset.CAR, halfW, -1, 0, false);
            }
            case SEAT -> {
                boolean driver = def.countKind(PartKind.SEAT) == 0;
                return VehiclePart.seat(driver ? "asiento del conductor" : "asiento", SeatStyle.CAR, -4, 6, -4, 8, 14, 8,
                        0x303030, driver);
            }
            case STEERING -> {
                return VehiclePart.box("volante", -3, 14, -8, 6, 6, 1).steering(SteeringStyle.WHEEL)
                        .colored(0x1E1E1E).rotated(20, 0, 0);
            }
            case LIGHT -> {
                return VehiclePart.box("faro", -2, 8, front - 0.5f, 4, 3, 0.5f).light(LightMode.HEAD).colored(0xFFF6C8);
            }
            case EXHAUST -> {
                return VehiclePart.box("escape", -1, 4, back, 2, 2, 3).as(PartKind.EXHAUST).colored(0x555555);
            }
            default -> {
                return VehiclePart.box("cubo", -4, 8, -4, 8, 8, 8).textured(MaterialIds.SMOOTH).colored(0xE6E6E6);
            }
        }
    }

    private static boolean wheelNear(VehicleDefinition def, float cx, float cz) {
        for (VehiclePart p : def.parts) {
            if (p.kind == PartKind.WHEEL && Math.abs(p.x + p.w / 2f - cx) < 4f && Math.abs(p.z + p.d / 2f - cz) < 6f) {
                return true;
            }
        }
        return false;
    }
}
