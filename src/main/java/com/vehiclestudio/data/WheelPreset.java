package com.vehiclestudio.data;

import java.util.Locale;

/**
 * Ruedas de referencia: ponen de una vez el tamano, los colores, la llanta y el dibujo de la goma.
 * Despues se puede cambiar todo a mano.
 */
public enum WheelPreset {
    CAR(10, 4, 0x2B2B2B, 0xC8C8CC, RimStyle.SPOKES, Tread.BLOCKS, 0.58f),
    SPORT(11, 5, 0x1C1C1C, 0x2E2E33, RimStyle.STAR, Tread.SMOOTH, 0.72f),
    TRUCK(14, 6, 0x262626, 0xB4B4B8, RimStyle.DISC, Tread.BLOCKS, 0.5f),
    OFFROAD(14, 7, 0x232323, 0x8A8A90, RimStyle.SPOKES, Tread.KNOBBY, 0.48f),
    MOTO(12, 3, 0x202020, 0x9A9AA0, RimStyle.SPOKES, Tread.SMOOTH, 0.7f),
    BIKE(14, 1, 0x1A1A1A, 0xD8D8DC, RimStyle.WIRE, Tread.SMOOTH, 0.9f),
    TRACTOR(20, 7, 0x2A2A2A, 0xE2B227, RimStyle.DISC, Tread.CHEVRON, 0.52f),
    CART(14, 2, 0x5E4024, 0x8B6A40, RimStyle.WOOD, Tread.SMOOTH, 0.86f),
    KART(6, 4, 0x222222, 0xD84040, RimStyle.DISC, Tread.SMOOTH, 0.55f),
    MONSTER(28, 12, 0x1F1F1F, 0x707078, RimStyle.SPOKES, Tread.KNOBBY, 0.42f);

    /** Diametro y ancho en pixeles. */
    public final float diameter;
    public final float width;
    public final int tire;
    public final int rim;
    public final RimStyle rimStyle;
    public final Tread tread;
    /** Tamano de la llanta respecto al radio de la rueda (0,3 a 0,95). */
    public final float rimSize;

    WheelPreset(float diameter, float width, int tire, int rim, RimStyle rimStyle, Tread tread, float rimSize) {
        this.diameter = diameter;
        this.width = width;
        this.tire = tire;
        this.rim = rim;
        this.rimStyle = rimStyle;
        this.tread = tread;
        this.rimSize = rimSize;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Pone la rueda de referencia en una pieza. Si keepSize, solo cambia el aspecto (colores, llanta, dibujo);
     * si no, tambien el tamano, sin mover el centro de la rueda.
     */
    public void applyTo(VehiclePart p, boolean keepSize) {
        p.kind = PartKind.WHEEL;
        p.color = tire;
        p.rimColor = rim;
        p.rimStyle = rimStyle;
        p.tread = tread;
        p.rimSize = rimSize;
        p.material = MaterialIds.SOLID;
        if (!keepSize) {
            float cx = p.x + p.w / 2f;
            float cy = p.y + p.h / 2f;
            float cz = p.z + p.d / 2f;
            boolean grounded = Math.abs(p.y) < 0.01f;
            p.w = width;
            p.h = diameter;
            p.d = diameter;
            p.x = cx - width / 2f;
            // si tocaba el suelo, lo sigue tocando; si no, se queda con el mismo centro
            p.y = grounded ? 0f : cy - diameter / 2f;
            p.z = cz - diameter / 2f;
        }
        p.centerPivot();
    }
}
