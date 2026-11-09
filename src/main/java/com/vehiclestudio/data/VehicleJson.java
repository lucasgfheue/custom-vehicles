package com.vehiclestudio.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Base64;
import java.util.Locale;

/** Conversion de definiciones de vehiculos a JSON y viceversa (tolerante a campos que falten). */
public final class VehicleJson {
    public static final int FORMAT = 1;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private VehicleJson() {
    }

    public static String write(VehicleDefinition d, boolean pretty) {
        return (pretty ? PRETTY : COMPACT).toJson(toJson(d));
    }

    public static VehicleDefinition read(String json) {
        JsonElement e = JsonParser.parseString(json);
        if (!e.isJsonObject()) {
            throw new JsonParseException("Se esperaba un objeto JSON");
        }
        return fromJson(e.getAsJsonObject());
    }

    public static JsonObject toJson(VehicleDefinition d) {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.addProperty("id", d.id);
        o.addProperty("name", d.name);
        o.addProperty("owner", d.owner);
        o.addProperty("revision", d.revision);
        o.addProperty("scale", d.scale);

        JsonObject drive = new JsonObject();
        drive.addProperty("maxSpeed", d.maxSpeed);
        drive.addProperty("accelTime", d.accelTime);
        drive.addProperty("brakeTime", d.brakeTime);
        drive.addProperty("reverseSpeed", d.reverseSpeed);
        drive.addProperty("steerAngle", d.steerAngle);
        drive.addProperty("drift", d.drift);
        drive.addProperty("stepHeight", d.stepHeight);
        drive.addProperty("health", d.health);
        drive.addProperty("runOver", d.runOver);
        o.add("driving", drive);

        JsonObject extras = new JsonObject();
        extras.addProperty("storage", d.storage);
        extras.addProperty("horn", d.horn.key());
        extras.addProperty("engine", d.engine.key());
        o.add("extras", extras);

        JsonObject flight = new JsonObject();
        flight.addProperty("mode", d.flight.key());
        flight.addProperty("airSpeed", d.airSpeed);
        flight.addProperty("climb", d.climb);
        o.add("flight", flight);

        JsonObject turbo = new JsonObject();
        turbo.addProperty("boost", d.turbo);
        turbo.addProperty("time", d.turboTime);
        turbo.addProperty("recharge", d.turboRecharge);
        o.add("turbo", turbo);

        JsonArray parts = new JsonArray();
        for (VehiclePart p : d.parts) {
            JsonObject j = new JsonObject();
            j.addProperty("name", p.name);
            j.addProperty("kind", p.kind.key());
            j.addProperty("parent", p.parent);
            j.add("from", vec(p.x, p.y, p.z));
            j.add("size", vec(p.w, p.h, p.d));
            j.add("pivot", vec(p.px, p.py, p.pz));
            j.add("rotation", vec(p.rx, p.ry, p.rz));
            j.addProperty("color", hex(p.color));
            j.addProperty("material", p.material);
            j.addProperty("stretch", p.stretch);
            j.addProperty("glow", p.glow);
            switch (p.kind) {
                case WHEEL -> {
                    JsonObject w = new JsonObject();
                    w.addProperty("rimColor", hex(p.rimColor));
                    w.addProperty("rim", p.rimStyle.key());
                    w.addProperty("tread", p.tread.key());
                    w.addProperty("rimSize", round(p.rimSize));
                    w.addProperty("steers", p.steers);
                    j.add("wheel", w);
                }
                case SEAT -> {
                    JsonObject w = new JsonObject();
                    w.addProperty("style", p.seatStyle.key());
                    w.addProperty("driver", p.driver);
                    j.add("seat", w);
                }
                case STEERING -> j.addProperty("steeringStyle", p.steeringStyle.key());
                case LIGHT -> j.addProperty("lightMode", p.lightMode.key());
                case PROPELLER -> {
                    JsonObject w = new JsonObject();
                    w.addProperty("blades", p.blades);
                    w.addProperty("axis", p.propAxis.key());
                    w.addProperty("hub", hex(p.rimColor));
                    j.add("propeller", w);
                }
                case THRUSTER -> {
                    JsonObject w = new JsonObject();
                    w.addProperty("style", p.thrusterStyle.key());
                    w.addProperty("flame", hex(p.flameColor));
                    w.addProperty("flameSize", round(p.flameSize));
                    j.add("thruster", w);
                }
                default -> {
                }
            }
            if (p.hasUv()) {
                JsonArray uv = new JsonArray();
                uv.add(p.uvU);
                uv.add(p.uvV);
                uv.add(p.uvMirror ? 1 : 0);
                uv.add(p.uvW);
                uv.add(p.uvH);
                uv.add(p.uvD);
                j.add("uv", uv);
            }
            if (p.validPaint()) {
                JsonArray size = new JsonArray();
                size.add(p.pw);
                size.add(p.ph);
                size.add(p.pd);
                j.add("paintSize", size);
                j.addProperty("paint", encodePaint(p.paint));
            }
            if (Voxels.count(p.voxels) > 0) {
                j.addProperty("voxels", Voxels.encode(p.voxels));
            }
            parts.add(j);
        }
        o.add("parts", parts);
        if (d.texW > 0 && d.texH > 0) {
            JsonArray tex = new JsonArray();
            tex.add(d.texW);
            tex.add(d.texH);
            o.add("textureSize", tex);
        }
        return o;
    }

    public static VehicleDefinition fromJson(JsonObject o) {
        VehicleDefinition d = new VehicleDefinition();
        d.id = str(o, "id", "");
        d.name = str(o, "name", d.name);
        d.owner = str(o, "owner", "");
        d.revision = integer(o, "revision", 0);
        d.scale = num(o, "scale", d.scale);

        JsonObject drive = obj(o, "driving");
        if (drive != null) {
            d.maxSpeed = num(drive, "maxSpeed", d.maxSpeed);
            d.accelTime = num(drive, "accelTime", d.accelTime);
            d.brakeTime = num(drive, "brakeTime", d.brakeTime);
            d.reverseSpeed = integer(drive, "reverseSpeed", d.reverseSpeed);
            d.steerAngle = num(drive, "steerAngle", d.steerAngle);
            d.drift = integer(drive, "drift", d.drift);
            d.stepHeight = num(drive, "stepHeight", d.stepHeight);
            d.health = num(drive, "health", d.health);
            d.runOver = bool(drive, "runOver", d.runOver);
        }
        JsonObject extras = obj(o, "extras");
        if (extras != null) {
            d.storage = integer(extras, "storage", d.storage);
            d.horn = HornSound.byKey(str(extras, "horn", d.horn.key()), d.horn);
            d.engine = EngineSound.byKey(str(extras, "engine", d.engine.key()), d.engine);
        }
        JsonObject flight = obj(o, "flight");
        if (flight != null) {
            d.flight = FlightMode.byKey(str(flight, "mode", d.flight.key()), d.flight);
            d.airSpeed = num(flight, "airSpeed", d.airSpeed);
            d.climb = num(flight, "climb", d.climb);
        }
        JsonObject turbo = obj(o, "turbo");
        if (turbo != null) {
            d.turbo = num(turbo, "boost", d.turbo);
            d.turboTime = num(turbo, "time", d.turboTime);
            d.turboRecharge = num(turbo, "recharge", d.turboRecharge);
        }

        if (o.has("textureSize") && o.get("textureSize").isJsonArray() && o.getAsJsonArray("textureSize").size() == 2) {
            try {
                d.texW = o.getAsJsonArray("textureSize").get(0).getAsInt();
                d.texH = o.getAsJsonArray("textureSize").get(1).getAsInt();
            } catch (RuntimeException e) {
                d.texW = 0;
                d.texH = 0;
            }
        }

        d.parts.clear();
        if (o.has("parts") && o.get("parts").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("parts")) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject j = e.getAsJsonObject();
                VehiclePart p = new VehiclePart();
                p.name = str(j, "name", p.name);
                p.kind = PartKind.byKey(str(j, "kind", "body"), PartKind.BODY);
                p.parent = integer(j, "parent", -1);
                float[] from = vec(j, "from", new float[]{p.x, p.y, p.z});
                float[] size = vec(j, "size", new float[]{p.w, p.h, p.d});
                float[] pivot = vec(j, "pivot", new float[]{p.px, p.py, p.pz});
                float[] rot = vec(j, "rotation", new float[]{0, 0, 0});
                p.x = from[0];
                p.y = from[1];
                p.z = from[2];
                p.w = size[0];
                p.h = size[1];
                p.d = size[2];
                p.px = pivot[0];
                p.py = pivot[1];
                p.pz = pivot[2];
                p.rx = rot[0];
                p.ry = rot[1];
                p.rz = rot[2];
                p.color = j.has("color") ? parseColor(j.get("color"), p.color) : p.color;
                p.material = str(j, "material", p.material);
                p.stretch = bool(j, "stretch", false);
                p.glow = bool(j, "glow", false);
                JsonObject wheel = obj(j, "wheel");
                if (wheel != null) {
                    p.rimColor = wheel.has("rimColor") ? parseColor(wheel.get("rimColor"), p.rimColor) : p.rimColor;
                    p.rimStyle = RimStyle.byKey(str(wheel, "rim", p.rimStyle.key()), p.rimStyle);
                    p.tread = Tread.byKey(str(wheel, "tread", p.tread.key()), p.tread);
                    p.rimSize = num(wheel, "rimSize", p.rimSize);
                    p.steers = bool(wheel, "steers", false);
                }
                JsonObject seat = obj(j, "seat");
                if (seat != null) {
                    p.seatStyle = SeatStyle.byKey(str(seat, "style", p.seatStyle.key()), p.seatStyle);
                    p.driver = bool(seat, "driver", false);
                }
                p.steeringStyle = SteeringStyle.byKey(str(j, "steeringStyle", p.steeringStyle.key()), p.steeringStyle);
                p.lightMode = LightMode.byKey(str(j, "lightMode", p.lightMode.key()), p.lightMode);
                JsonObject prop = obj(j, "propeller");
                if (prop != null) {
                    p.blades = integer(prop, "blades", p.blades);
                    p.propAxis = PropAxis.byKey(str(prop, "axis", p.propAxis.key()), p.propAxis);
                    p.rimColor = prop.has("hub") ? parseColor(prop.get("hub"), p.rimColor) : p.rimColor;
                }
                JsonObject thr = obj(j, "thruster");
                if (thr != null) {
                    p.thrusterStyle = ThrusterStyle.byKey(str(thr, "style", p.thrusterStyle.key()), p.thrusterStyle);
                    p.flameColor = thr.has("flame") ? parseColor(thr.get("flame"), p.flameColor) : p.flameColor;
                    p.flameSize = num(thr, "flameSize", p.flameSize);
                }
                readPaint(j, p);
                readUv(j, p);
                if (j.has("voxels") && j.get("voxels").isJsonPrimitive()) {
                    p.voxels = Voxels.decode(j.get("voxels").getAsString());
                }
                d.parts.add(p);
            }
        }
        return d;
    }

    // ---------------------------------------------------------------------------------------
    // Pintura: RGBA en Base64
    // ---------------------------------------------------------------------------------------

    static String encodePaint(int[] argb) {
        byte[] bytes = new byte[argb.length * 4];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            bytes[i * 4] = (byte) (c >> 16);
            bytes[i * 4 + 1] = (byte) (c >> 8);
            bytes[i * 4 + 2] = (byte) c;
            bytes[i * 4 + 3] = (byte) (c >>> 24);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    static int[] decodePaint(String text) {
        byte[] bytes = Base64.getDecoder().decode(text);
        int[] out = new int[bytes.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = (bytes[i * 4 + 3] & 255) << 24 | (bytes[i * 4] & 255) << 16
                    | (bytes[i * 4 + 1] & 255) << 8 | (bytes[i * 4 + 2] & 255);
        }
        return out;
    }

    private static void readUv(JsonObject j, VehiclePart p) {
        try {
            if (j.has("uv") && j.get("uv").isJsonArray() && j.getAsJsonArray("uv").size() == 6) {
                JsonArray uv = j.getAsJsonArray("uv");
                p.uvU = uv.get(0).getAsInt();
                p.uvV = uv.get(1).getAsInt();
                p.uvMirror = uv.get(2).getAsInt() != 0;
                p.uvW = uv.get(3).getAsInt();
                p.uvH = uv.get(4).getAsInt();
                p.uvD = uv.get(5).getAsInt();
            }
        } catch (RuntimeException e) {
            p.uvU = -1;
            p.uvV = -1;
        }
    }

    private static void readPaint(JsonObject j, VehiclePart p) {
        if (!j.has("paint") || !j.has("paintSize") || !j.get("paintSize").isJsonArray()) {
            return;
        }
        try {
            JsonArray size = j.getAsJsonArray("paintSize");
            if (size.size() != 3) {
                return;
            }
            p.pw = size.get(0).getAsInt();
            p.ph = size.get(1).getAsInt();
            p.pd = size.get(2).getAsInt();
            p.paint = decodePaint(j.get("paint").getAsString());
            if (!p.validPaint()) {
                p.clearPaint();
            }
        } catch (RuntimeException e) {
            p.clearPaint();
        }
    }

    // ---------------------------------------------------------------------------------------

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Acepta "#RRGGBB", "RRGGBB", "0xRRGGBB" o un numero. Devuelve -1 si no es valido. */
    public static int parseHex(String s) {
        if (s == null) {
            return -1;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        } else if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (t.length() == 3) {
            t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        }
        if (t.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseColor(JsonElement e, int def) {
        try {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                return e.getAsInt() & 0xFFFFFF;
            }
            int c = parseHex(e.getAsString());
            return c < 0 ? def : c;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static JsonArray vec(float a, float b, float c) {
        JsonArray arr = new JsonArray();
        arr.add(round(a));
        arr.add(round(b));
        arr.add(round(c));
        return arr;
    }

    private static float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    private static float[] vec(JsonObject o, String key, float[] def) {
        try {
            if (o.has(key) && o.get(key).isJsonArray()) {
                JsonArray arr = o.getAsJsonArray(key);
                float[] out = def.clone();
                for (int i = 0; i < Math.min(3, arr.size()); i++) {
                    out[i] = arr.get(i).getAsFloat();
                }
                return out;
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static String str(JsonObject o, String key, String def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static float num(JsonObject o, String key, float def) {
        try {
            return o.has(key) ? o.get(key).getAsFloat() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int integer(JsonObject o, String key, int def) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return o.has(key) ? o.get(key).getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static <E extends Enum<E>> E enumValue(JsonObject o, String key, Class<E> type, E def) {
        try {
            if (o.has(key)) {
                return Enum.valueOf(type, o.get(key).getAsString().trim().toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }
}
