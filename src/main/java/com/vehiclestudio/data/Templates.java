package com.vehiclestudio.data;

import java.util.List;
import java.util.function.Supplier;

/**
 * Vehiculos de partida para el editor. Medidas en pixeles (16 = 1 bloque). El frente mira hacia -Z y la izquierda
 * del vehiculo (donde va el conductor) es +X; las piezas de la derecha son el reflejo de las de la izquierda.
 */
public final class Templates {

    public record Template(String key, Supplier<VehicleDefinition> factory) {
        public VehicleDefinition create() {
            return factory.get();
        }
    }

    public static final List<Template> ALL = List.of(
            new Template("car", Templates::car),
            new Template("sports", Templates::sports),
            new Template("pickup", Templates::pickup),
            new Template("bus", Templates::bus),
            new Template("motorcycle", Templates::motorcycle),
            new Template("kart", Templates::kart),
            new Template("tractor", Templates::tractor),
            new Template("buggy", Templates::buggy),
            new Template("cart", Templates::cart),
            new Template("empty", Templates::empty)
    );

    private static final int DARK = 0x2A2A2A;
    private static final int TRIM = 0x3A3A3A;
    private static final int GLASS_TINT = 0xFFFFFF;
    private static final int HEADLIGHT = 0xFFF6C8;
    private static final int BRAKELIGHT = 0xD01818;
    private static final int SEAT_DARK = 0x303030;
    private static final String GLASS = "minecraft:block/glass";
    private static final String PLANKS = "minecraft:block/oak_planks";

    private Templates() {
    }

    /** Ayudante para encadenar piezas y sus reflejos. */
    private static final class Builder {
        final VehicleDefinition def = new VehicleDefinition();

        int add(VehiclePart p) {
            def.parts.add(p);
            return def.parts.size() - 1;
        }

        /** Anade la pieza (de la izquierda) y su reflejo; devuelve el indice de la original. */
        int pair(VehiclePart left) {
            int i = add(left);
            add(left.mirroredX());
            return i;
        }

        /** Dos ruedas iguales a los lados, con el centro a cx del medio. */
        void wheels(String where, WheelPreset preset, float cx, float cz, boolean steers) {
            pair(VehiclePart.wheel("rueda " + where + " izquierda", preset, cx, -1, cz, steers));
        }

        VehiclePart glass(String name, float x, float y, float z, float w, float h, float d) {
            return VehiclePart.box(name, x, y, z, w, h, d).textured(GLASS).colored(GLASS_TINT).stretched();
        }

        void stats(float speed, float accel, float brake, float steer, int drift, float step, float health) {
            def.maxSpeed = speed;
            def.accelTime = accel;
            def.brakeTime = brake;
            def.steerAngle = steer;
            def.drift = drift;
            def.stepHeight = step;
            def.health = health;
        }

        VehicleDefinition named(String name) {
            def.name = name;
            return def;
        }
    }

    // ---------------------------------------------------------------------------------------

    public static VehicleDefinition car() {
        Builder b = new Builder();
        int red = 0xC0392B;
        b.add(VehiclePart.box("chasis", -11, 3, -30, 22, 3, 60).colored(DARK));
        b.add(VehiclePart.box("carroceria", -12, 6, -32, 24, 8, 64).textured(MaterialIds.SMOOTH).colored(red));
        b.add(VehiclePart.box("techo", -11, 29, -9, 22, 2, 26).textured(MaterialIds.SMOOTH).colored(red));
        b.pair(VehiclePart.box("pilar delantero izquierdo", 9, 14, -10, 2, 15, 2).textured(MaterialIds.SMOOTH).colored(red));
        b.pair(VehiclePart.box("pilar trasero izquierdo", 9, 14, 16, 2, 15, 2).textured(MaterialIds.SMOOTH).colored(red));
        b.add(b.glass("parabrisas", -9, 14, -10, 18, 15, 1));
        b.add(b.glass("luneta", -9, 14, 17, 18, 15, 1));
        b.pair(b.glass("ventanilla izquierda", 10, 14, -8, 1, 15, 24));
        b.add(VehiclePart.box("salpicadero", -9, 14, -9, 18, 4, 3).colored(TRIM));
        b.add(VehiclePart.box("volante", 2.5f, 15, -6.5f, 6, 6, 1).steering(SteeringStyle.WHEEL).colored(0x1E1E1E)
                .rotated(20, 0, 0));
        b.add(VehiclePart.seat("asiento del conductor", SeatStyle.CAR, 2, 6, -2, 7, 14, 8, SEAT_DARK, true));
        b.add(VehiclePart.seat("asiento del copiloto", SeatStyle.CAR, -9, 6, -2, 7, 14, 8, SEAT_DARK, false));
        b.pair(VehiclePart.seat("asiento trasero izquierdo", SeatStyle.CAR, 2, 6, 8, 7, 12, 8, SEAT_DARK, false));
        b.pair(VehiclePart.box("faro izquierdo", 7, 9, -32.5f, 4, 3, 0.5f).light(LightMode.HEAD).colored(HEADLIGHT));
        b.pair(VehiclePart.box("luz de freno izquierda", 7, 9, 32, 4, 3, 0.5f).light(LightMode.BRAKE).colored(BRAKELIGHT));
        b.add(VehiclePart.box("parachoques delantero", -12, 5, -33, 24, 3, 1).colored(TRIM));
        b.add(VehiclePart.box("parachoques trasero", -12, 5, 32, 24, 3, 1).colored(TRIM));
        b.add(VehiclePart.box("escape", -9, 4, 32, 2, 2, 3).as(PartKind.EXHAUST).colored(0x555555));
        b.wheels("delantera", WheelPreset.CAR, 14, -20, true);
        b.wheels("trasera", WheelPreset.CAR, 14, 20, false);
        b.stats(60, 4, 1.5f, 35, 10, 1, 40);
        b.def.storage = 9;
        return b.named("Coche");
    }

    public static VehicleDefinition sports() {
        Builder b = new Builder();
        int yellow = 0xF1C40F;
        b.add(VehiclePart.box("chasis", -11, 3, -32, 22, 3, 64).colored(DARK));
        b.add(VehiclePart.box("carroceria", -12, 5, -34, 24, 7, 68).textured(MaterialIds.SMOOTH).colored(yellow));
        b.add(VehiclePart.box("morro", -12, 5, -38, 24, 4, 4).textured(MaterialIds.SMOOTH).colored(yellow));
        b.add(VehiclePart.box("franja", -3, 12, -34, 6, 0.25f, 68).colored(0x111111));
        b.add(b.glass("parabrisas", -10, 12, -6, 20, 7, 1).pivotAt(0, 12, -5.5f).rotated(25, 0, 0));
        b.add(VehiclePart.box("salpicadero", -10, 10, -6, 20, 3, 2).colored(DARK));
        b.add(VehiclePart.box("volante", 3, 11, -4.5f, 6, 6, 1).steering(SteeringStyle.WHEEL).colored(0x151515)
                .rotated(20, 0, 0));
        b.add(VehiclePart.seat("asiento del conductor", SeatStyle.RACING, 2, 5, -2, 8, 12, 9, 0x111111, true));
        b.add(VehiclePart.seat("asiento del copiloto", SeatStyle.RACING, -10, 5, -2, 8, 12, 9, 0x111111, false));
        b.pair(VehiclePart.box("soporte del aleron izquierdo", 6, 12, 26, 2, 4, 2).colored(0x1A1A1A));
        b.add(VehiclePart.box("aleron", -11, 16, 24, 22, 1.5f, 6).colored(0x1A1A1A));
        b.pair(VehiclePart.box("faro izquierdo", 7, 7, -38.5f, 4, 2, 0.5f).light(LightMode.HEAD).colored(HEADLIGHT));
        b.pair(VehiclePart.box("luz de freno izquierda", 7, 8, 34, 4, 2, 0.5f).light(LightMode.BRAKE).colored(BRAKELIGHT));
        b.pair(VehiclePart.box("escape izquierdo", 3, 4, 34, 2, 2, 3).as(PartKind.EXHAUST).colored(0x8A8A8A));
        b.wheels("delantera", WheelPreset.SPORT, 14, -22, true);
        b.wheels("trasera", WheelPreset.SPORT, 14, 22, false);
        b.stats(110, 3, 1.2f, 30, 25, 1, 35);
        return b.named("Deportivo");
    }

    public static VehicleDefinition pickup() {
        Builder b = new Builder();
        int blue = 0x2E6FD8;
        b.add(VehiclePart.box("chasis", -13, 5, -36, 26, 3, 72).colored(DARK));
        b.add(VehiclePart.box("capo", -14, 8, -38, 28, 10, 20).textured(MaterialIds.SMOOTH).colored(blue));
        b.add(VehiclePart.box("cabina", -14, 8, -18, 28, 10, 22).textured(MaterialIds.SMOOTH).colored(blue));
        b.add(VehiclePart.box("suelo de la caja", -14, 8, 4, 28, 3, 34).textured(MaterialIds.PLATES).colored(blue));
        b.pair(VehiclePart.box("lateral de la caja izquierdo", 12, 11, 4, 2, 7, 32).textured(MaterialIds.SMOOTH).colored(blue));
        b.add(VehiclePart.box("compuerta", -14, 11, 36, 28, 7, 2).textured(MaterialIds.SMOOTH).colored(blue));
        b.pair(VehiclePart.box("pilar delantero izquierdo", 11, 18, -18, 3, 14, 2).textured(MaterialIds.SMOOTH).colored(blue));
        b.pair(VehiclePart.box("pilar trasero izquierdo", 11, 18, 2, 3, 14, 2).textured(MaterialIds.SMOOTH).colored(blue));
        b.add(VehiclePart.box("techo", -14, 32, -18, 28, 2, 22).textured(MaterialIds.SMOOTH).colored(blue));
        b.add(b.glass("parabrisas", -11, 18, -18, 22, 14, 1));
        b.add(b.glass("ventana trasera", -11, 18, 3, 22, 14, 1));
        b.pair(b.glass("ventanilla izquierda", 13, 18, -16, 1, 14, 18));
        b.add(VehiclePart.box("salpicadero", -11, 18, -17, 22, 4, 3).colored(TRIM));
        b.add(VehiclePart.box("volante", 3.5f, 19, -14, 7, 7, 1).steering(SteeringStyle.WHEEL).colored(0x1E1E1E)
                .rotated(20, 0, 0));
        b.add(VehiclePart.seat("asiento del conductor", SeatStyle.CAR, 3, 8, -10, 8, 16, 9, SEAT_DARK, true));
        b.add(VehiclePart.seat("asiento del copiloto", SeatStyle.CAR, -11, 8, -10, 8, 16, 9, SEAT_DARK, false));
        b.add(VehiclePart.box("parrilla", -6, 10, -38.5f, 12, 6, 0.5f).textured(MaterialIds.GRILLE).colored(0x404040));
        b.pair(VehiclePart.box("faro izquierdo", 9, 12, -38.5f, 4, 3, 0.5f).light(LightMode.HEAD).colored(HEADLIGHT));
        b.pair(VehiclePart.box("luz de freno izquierda", 10, 12, 38, 3, 4, 0.5f).light(LightMode.BRAKE).colored(BRAKELIGHT));
        b.add(VehiclePart.box("parachoques delantero", -14, 6, -39, 28, 3, 1).colored(0x777777));
        b.add(VehiclePart.box("parachoques trasero", -14, 6, 38, 28, 3, 1).colored(0x777777));
        b.add(VehiclePart.box("escape", -11, 5, 38, 2, 2, 3).as(PartKind.EXHAUST).colored(0x555555));
        b.wheels("delantera", WheelPreset.TRUCK, 15, -24, true);
        b.wheels("trasera", WheelPreset.TRUCK, 15, 24, false);
        b.stats(55, 5, 2, 32, 8, 1, 60);
        b.def.horn = HornSound.TRUCK;
        b.def.storage = 27;
        return b.named("Camioneta");
    }

    public static VehicleDefinition bus() {
        Builder b = new Builder();
        int yellow = 0xF4B400;
        b.add(VehiclePart.box("chasis", -14, 5, -54, 28, 3, 108).colored(DARK));
        b.add(VehiclePart.box("carroceria", -15, 8, -56, 30, 10, 112).textured(MaterialIds.SMOOTH).colored(yellow));
        b.add(VehiclePart.box("franja", -15.25f, 12, -56.25f, 30.5f, 1.5f, 112.5f).colored(0x222222));
        b.add(VehiclePart.box("techo", -15, 34, -56, 30, 2, 112).textured(MaterialIds.SMOOTH).colored(yellow));
        b.add(b.glass("parabrisas", -13, 18, -56, 26, 16, 1));
        b.add(b.glass("luna trasera", -13, 18, 55, 26, 16, 1));
        b.pair(b.glass("ventanas izquierdas", 14, 18, -54, 1, 16, 108));
        b.pair(VehiclePart.box("pilar delantero izquierdo", 13, 18, -56, 2, 16, 2).textured(MaterialIds.SMOOTH).colored(yellow));
        b.pair(VehiclePart.box("pilar trasero izquierdo", 13, 18, 54, 2, 16, 2).textured(MaterialIds.SMOOTH).colored(yellow));
        b.pair(VehiclePart.box("pilar central izquierdo", 13, 18, -20, 2, 16, 2).textured(MaterialIds.SMOOTH).colored(yellow));
        b.pair(VehiclePart.box("pilar de atras izquierdo", 13, 18, 16, 2, 16, 2).textured(MaterialIds.SMOOTH).colored(yellow));
        b.add(VehiclePart.box("salpicadero", -13, 18, -55, 26, 4, 3).colored(TRIM));
        b.add(VehiclePart.box("volante", 5, 19, -51, 7, 7, 1).steering(SteeringStyle.WHEEL).colored(0x1E1E1E)
                .rotated(25, 0, 0));
        b.add(VehiclePart.seat("asiento del conductor", SeatStyle.CAR, 4, 8, -48, 8, 16, 8, SEAT_DARK, true));
        float[] rows = {-34, -18, -2, 14, 30};
        for (int r = 0; r < rows.length; r++) {
            b.pair(VehiclePart.seat("asiento " + (r + 1) + " izquierdo", SeatStyle.CAR, 4, 8, rows[r], 8, 14, 8,
                    0x2F5D8A, false));
        }
        b.pair(VehiclePart.box("faro izquierdo", 9, 12, -56.5f, 4, 4, 0.5f).light(LightMode.HEAD).colored(HEADLIGHT));
        b.pair(VehiclePart.box("luz de freno izquierda", 10, 12, 56, 3, 4, 0.5f).light(LightMode.BRAKE).colored(BRAKELIGHT));
        b.add(VehiclePart.box("parachoques delantero", -15, 6, -57, 30, 3, 1).colored(DARK));
        b.add(VehiclePart.box("parachoques trasero", -15, 6, 56, 30, 3, 1).colored(DARK));
        b.add(VehiclePart.box("escape", -12, 5, 56, 2, 2, 3).as(PartKind.EXHAUST).colored(0x555555));
        b.wheels("delantera", WheelPreset.TRUCK, 16, -38, true);
        b.wheels("trasera", WheelPreset.TRUCK, 16, 34, false);
        b.stats(45, 8, 2.5f, 30, 5, 1, 80);
        b.def.horn = HornSound.TRUCK;
        b.def.storage = 27;
        return b.named("Autobus");
    }

    public static VehicleDefinition motorcycle() {
        Builder b = new Builder();
        int red = 0xC0392B;
        int chrome = 0xB0B0B4;
        b.add(VehiclePart.wheel("rueda delantera", WheelPreset.MOTO, 0, -1, -18, true));
        b.add(VehiclePart.wheel("rueda trasera", WheelPreset.MOTO, 0, -1, 16, false));
        int bar = b.add(VehiclePart.box("manillar", -6, 18, -19, 12, 1.5f, 2).steering(SteeringStyle.HANDLEBAR)
                .colored(0x1A1A1A).pivotAt(0, 18.75f, -18));
        b.pair(VehiclePart.box("horquilla izquierda", 2, 6, -19, 1, 12, 2).colored(chrome).childOf(bar));
        b.add(VehiclePart.box("faro", -1.5f, 14, -21, 3, 3, 1).light(LightMode.HEAD).colored(HEADLIGHT).childOf(bar));
        b.add(VehiclePart.box("chasis", -1.5f, 8, -16, 3, 3, 30).colored(DARK));
        b.add(VehiclePart.box("motor", -3.5f, 5, -8, 7, 6, 10).textured(MaterialIds.METAL).colored(0x4A4A4A));
        b.add(VehiclePart.box("deposito", -3, 12, -12, 6, 5, 12).textured(MaterialIds.SMOOTH).colored(red));
        b.add(VehiclePart.seat("sillin", SeatStyle.SADDLE, -3, 13, 0, 6, 3, 12, 0x1A1A1A, true));
        b.add(VehiclePart.seat("sillin trasero", SeatStyle.SADDLE, -3, 13.5f, 12, 6, 2.5f, 7, 0x1A1A1A, false));
        b.add(VehiclePart.box("guardabarros", -2, 12.5f, 13, 4, 1, 9).textured(MaterialIds.SMOOTH).colored(red));
        b.add(VehiclePart.box("luz de freno", -1.5f, 11, 21.5f, 3, 2, 0.5f).light(LightMode.BRAKE).colored(BRAKELIGHT));
        b.add(VehiclePart.box("escape", 2.5f, 5, 4, 2, 2, 14).as(PartKind.EXHAUST).colored(0x9A9AA0));
        b.stats(90, 2.5f, 1.2f, 30, 15, 1, 25);
        b.def.horn = HornSound.BIKE;
        return b.named("Moto");
    }

    public static VehicleDefinition kart() {
        Builder b = new Builder();
        int red = 0xE53935;
        b.add(VehiclePart.box("chasis", -9, 2, -18, 18, 2, 36).colored(DARK));
        b.add(VehiclePart.box("morro", -7, 3, -22, 14, 3, 6).textured(MaterialIds.SMOOTH).colored(red));
        b.pair(VehiclePart.box("pont\u00F3n izquierdo", 7, 3, -6, 4, 3, 12).textured(MaterialIds.SMOOTH).colored(red));
        b.add(VehiclePart.seat("asiento", SeatStyle.RACING, -4, 4, 2, 8, 10, 8, 0x111111, true));
        b.add(VehiclePart.box("volante", -2.5f, 8, -5, 5, 5, 1).steering(SteeringStyle.WHEEL).colored(0x111111)
                .rotated(30, 0, 0));
        b.add(VehiclePart.box("columna", -0.5f, 4, -8, 1, 5, 1).colored(DARK));
        b.add(VehiclePart.box("motor", -4, 4, 10, 8, 5, 6).textured(MaterialIds.METAL).colored(0x7A7A7A));
        b.add(VehiclePart.box("escape", 4, 6, 15, 2, 2, 3).as(PartKind.EXHAUST).colored(0x555555));
        b.wheels("delantera", WheelPreset.KART, 11, -14, true);
        b.wheels("trasera", WheelPreset.KART, 11, 13, false);
        b.stats(50, 1.5f, 1, 40, 30, 0.5f, 20);
        b.def.horn = HornSound.CLOWN;
        return b.named("Kart");
    }

    public static VehicleDefinition tractor() {
        Builder b = new Builder();
        int green = 0x2E8B3A;
        b.add(VehiclePart.box("chasis", -5, 6, -30, 10, 4, 44).colored(DARK));
        b.add(VehiclePart.box("capo", -6, 10, -30, 12, 10, 26).textured(MaterialIds.SMOOTH).colored(green));
        b.add(VehiclePart.box("parrilla", -5, 11, -30.5f, 10, 8, 0.5f).textured(MaterialIds.GRILLE).colored(0x303030));
        b.add(VehiclePart.box("plataforma", -9, 12, -4, 18, 2, 20).textured(MaterialIds.PLATES).colored(0x3A3A3A));
        b.add(VehiclePart.seat("asiento", SeatStyle.CAR, -4, 14, 4, 8, 12, 8, 0x222222, true));
        b.add(VehiclePart.box("columna", -0.5f, 14, -3, 1, 7, 1).colored(DARK));
        b.add(VehiclePart.box("volante", -3, 20, -2.5f, 6, 6, 1).steering(SteeringStyle.WHEEL).colored(0x1A1A1A)
                .rotated(35, 0, 0));
        b.pair(VehiclePart.box("guardabarros izquierdo", 9, 20, 1, 8, 1.5f, 18).textured(MaterialIds.SMOOTH).colored(green));
        b.pair(VehiclePart.box("poste del arco izquierdo", 8, 21.5f, 14, 1.5f, 18, 1.5f).colored(DARK));
        b.add(VehiclePart.box("arco", -9.5f, 39.5f, 14, 19, 1.5f, 1.5f).colored(DARK));
        b.add(VehiclePart.box("tubo de escape", 3, 20, -14, 2, 10, 2).as(PartKind.EXHAUST).colored(0x2A2A2A));
        b.pair(VehiclePart.box("faro izquierdo", 2, 20, -30, 3, 2, 1).light(LightMode.HEAD).colored(HEADLIGHT));
        b.wheels("trasera", WheelPreset.TRACTOR, 13, 10, false);
        int front = b.def.parts.size();
        b.wheels("delantera", WheelPreset.CAR, 10, -22, true);
        for (int i = front; i < b.def.parts.size(); i++) {
            VehiclePart w = b.def.parts.get(i);
            w.rimColor = 0xE2B227;
            w.rimStyle = RimStyle.DISC;
        }
        b.stats(25, 5, 2, 40, 0, 1.5f, 60);
        b.def.horn = HornSound.TRUCK;
        b.def.storage = 9;
        return b.named("Tractor");
    }

    public static VehicleDefinition buggy() {
        Builder b = new Builder();
        int orange = 0xE67E22;
        int cage = 0x222222;
        b.add(VehiclePart.box("chasis", -10, 6, -26, 20, 3, 52).colored(DARK));
        b.add(VehiclePart.box("frente", -10, 9, -30, 20, 5, 10).textured(MaterialIds.SMOOTH).colored(orange));
        b.add(VehiclePart.box("motor", -10, 9, 14, 20, 7, 14).textured(MaterialIds.SMOOTH).colored(orange));
        b.pair(VehiclePart.box("barra delantera izquierda", 9, 9, -14, 1.5f, 22, 1.5f).colored(cage));
        b.pair(VehiclePart.box("barra trasera izquierda", 9, 9, 12, 1.5f, 22, 1.5f).colored(cage));
        b.pair(VehiclePart.box("barra de arriba izquierda", 9, 31, -14, 1.5f, 1.5f, 27.5f).colored(cage));
        b.add(VehiclePart.box("travesano delantero", -10.5f, 31, -14, 21, 1.5f, 1.5f).colored(cage));
        b.add(VehiclePart.box("travesano trasero", -10.5f, 31, 12, 21, 1.5f, 1.5f).colored(cage));
        b.add(VehiclePart.seat("asiento del conductor", SeatStyle.RACING, 1, 9, -4, 8, 12, 9, 0x111111, true));
        b.add(VehiclePart.seat("asiento del copiloto", SeatStyle.RACING, -9, 9, -4, 8, 12, 9, 0x111111, false));
        b.add(VehiclePart.box("volante", 2, 15, -9, 6, 6, 1).steering(SteeringStyle.WHEEL).colored(0x111111)
                .rotated(25, 0, 0));
        b.pair(VehiclePart.box("faro izquierdo", 5, 12, -30.5f, 3, 3, 0.5f).light(LightMode.HEAD).colored(HEADLIGHT));
        b.add(VehiclePart.box("escape", -6, 12, 28, 2, 2, 3).as(PartKind.EXHAUST).colored(0x555555));
        b.wheels("delantera", WheelPreset.OFFROAD, 15, -20, true);
        b.wheels("trasera", WheelPreset.OFFROAD, 15, 20, false);
        b.stats(70, 3, 1.5f, 38, 35, 1.5f, 35);
        return b.named("Buggy");
    }

    public static VehicleDefinition cart() {
        Builder b = new Builder();
        int wood = 0xFFFFFF;
        b.add(VehiclePart.box("suelo", -10, 8, -14, 20, 2, 30).textured(PLANKS).colored(wood));
        b.pair(VehiclePart.box("lateral izquierdo", 8.5f, 10, -14, 1.5f, 6, 30).textured(PLANKS).colored(wood));
        b.add(VehiclePart.box("frente", -8.5f, 10, -14, 17, 6, 1.5f).textured(PLANKS).colored(wood));
        b.add(VehiclePart.box("trasera", -8.5f, 10, 14.5f, 17, 6, 1.5f).textured(PLANKS).colored(wood));
        b.add(VehiclePart.box("lanza", -1, 8, -30, 2, 2, 16).textured(PLANKS).colored(wood));
        b.add(VehiclePart.box("eje", -12, 6, 1, 24, 2, 2).colored(0x5E4024));
        b.add(VehiclePart.seat("banco", SeatStyle.BENCH, -8, 10, -12, 16, 8, 8, 0x8B6A40, true));
        b.wheels("", WheelPreset.CART, 12, 2, false);
        b.def.parts.get(b.def.parts.size() - 2).name = "rueda izquierda";
        b.def.parts.get(b.def.parts.size() - 1).name = "rueda derecha";
        b.stats(15, 3, 1.5f, 30, 0, 1, 20);
        b.def.engine = EngineSound.NONE;
        b.def.horn = HornSound.BELL;
        b.def.storage = 18;
        return b.named("Carreta");
    }

    public static VehicleDefinition empty() {
        Builder b = new Builder();
        b.add(VehiclePart.box("chasis", -10, 4, -20, 20, 4, 40).textured(MaterialIds.SMOOTH).colored(0x8A8A8A));
        b.add(VehiclePart.seat("asiento", SeatStyle.CAR, -4, 8, -2, 8, 14, 8, SEAT_DARK, true));
        b.wheels("delantera", WheelPreset.CAR, 12, -14, true);
        b.wheels("trasera", WheelPreset.CAR, 12, 14, false);
        return b.named("Nuevo vehiculo");
    }
}
