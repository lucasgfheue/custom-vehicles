package com.vehiclestudio.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Pasa una textura (skin) con el formato de caja de Minecraft a la pintura de las piezas, y al reves.
 * Cada pieza con sitio en la skin ocupa un rectangulo de 2*(d+w) x (d+h) texels desde (uvU, uvV), igual que
 * en Minecraft: la cara de abajo va volteada en vertical y las piezas reflejadas usan la zona del otro lado.
 */
public final class SkinLayout {
    /** Ancho minimo de las skins nuevas (el de las skins de Minecraft). */
    public static final int MIN_WIDTH = 64;

    private SkinLayout() {
    }

    /** Resolucion de la pintura de una pieza con sitio en la skin: la de la skin, con al menos 1 texel por eje. */
    public static int paintW(VehiclePart p) {
        return Math.max(1, p.uvW);
    }

    public static int paintH(VehiclePart p) {
        return Math.max(1, p.uvH);
    }

    public static int paintD(VehiclePart p) {
        return Math.max(1, p.uvD);
    }

    /**
     * Texel de la skin {x, y} que corresponde al texel (i, j) de la cara de la pieza, o null si esa cara no esta
     * en la skin (las caras de grosor 0 de las piezas planas).
     */
    public static int[] skinTexel(VehiclePart p, int face, int i, int j) {
        int src = p.uvMirror ? PaintLayout.mirrored(face) : face;
        int[] r = PaintLayout.region(src, p.uvW, p.uvH, p.uvD);
        if (i < 0 || j < 0 || i >= r[2] || j >= r[3]) {
            return null;
        }
        int si = p.uvMirror ? r[2] - 1 - i : i;
        int sj = face == PaintLayout.BOTTOM ? r[3] - 1 - j : j;
        return new int[]{p.uvU + r[0] + si, p.uvV + r[1] + sj};
    }

    /** Cuantas piezas tienen sitio en la skin. */
    public static int mapped(VehicleDefinition def) {
        if (def.texW <= 0 || def.texH <= 0) {
            return 0;
        }
        int n = 0;
        for (VehiclePart p : def.parts) {
            if (p.hasUv()) {
                n++;
            }
        }
        return n;
    }

    /** Piezas visibles (con tamano) que todavia no tienen sitio en la skin. */
    public static int unmapped(VehicleDefinition def) {
        int n = 0;
        for (VehiclePart p : def.parts) {
            if (!(p.hasUv() && def.texW > 0) && visible(p)) {
                n++;
            }
        }
        return n;
    }

    private static boolean visible(VehiclePart p) {
        int sides = (p.w > 0f ? 1 : 0) + (p.h > 0f ? 1 : 0) + (p.d > 0f ? 1 : 0);
        return sides >= 2 && !(MaterialIds.CLEAR.equals(p.material) && !p.hasPaint());
    }

    /**
     * Copia la imagen (ARGB, ancho x alto) en la pintura de las piezas con sitio en la skin. Si la imagen tiene
     * mas resolucion que la skin (por ejemplo 128x128 en vez de 64x64) se escala segun el ancho. Las zonas
     * transparentes de la imagen quedan sin pintar, y las piezas cuya zona no esta en la imagen (una skin antigua de
     * 64x32 no tiene brazo ni pierna izquierdos) se dejan como estaban. Devuelve las piezas pintadas.
     */
    public static int apply(VehicleDefinition def, int[] argb, int width, int height) {
        if (def.texW <= 0 || def.texH <= 0 || width <= 0 || height <= 0 || argb.length < width * height) {
            return 0;
        }
        float scale = width / (float) def.texW;
        int count = 0;
        for (VehiclePart p : def.parts) {
            if (!p.hasUv() || !insideImage(p, scale, width, height)) {
                continue;
            }
            p.pw = paintW(p);
            p.ph = paintH(p);
            p.pd = paintD(p);
            p.paint = new int[PaintLayout.imageW(p.pw, p.pd) * PaintLayout.imageH(p.ph, p.pd)];
            for (int f = 0; f < PaintLayout.FACES; f++) {
                int fw = PaintLayout.faceW(f, p.pw, p.pd);
                int fh = PaintLayout.faceH(f, p.ph, p.pd);
                for (int j = 0; j < fh; j++) {
                    for (int i = 0; i < fw; i++) {
                        int[] t = skinTexel(p, f, i, j);
                        if (t == null) {
                            continue;
                        }
                        int x = (int) Math.floor((t[0] + 0.5f) * scale);
                        int y = (int) Math.floor((t[1] + 0.5f) * scale);
                        if (x < 0 || y < 0 || x >= width || y >= height) {
                            continue;
                        }
                        int c = argb[y * width + x];
                        if ((c >>> 24) >= 128) {
                            p.setTexel(f, i, j, 0xFF000000 | (c & 0xFFFFFF));
                        }
                    }
                }
            }
            if (!p.hasPaint()) {
                p.clearPaint();
            }
            count++;
        }
        return count;
    }

    /** La zona de la pieza en la skin cabe entera en la imagen. */
    private static boolean insideImage(VehiclePart p, float scale, int width, int height) {
        int right = p.uvU + PaintLayout.imageW(p.uvW, p.uvD);
        int bottom = p.uvV + PaintLayout.imageH(p.uvH, p.uvD);
        return right * scale <= width + 0.01f && bottom * scale <= height + 0.01f;
    }

    /**
     * La skin del vehiculo (ARGB, def.texW x def.texH) con lo pintado. Lo que no esta pintado se rellena con el
     * color de la pieza (salvo en las piezas invisibles), para que se vea que zona es de cada pieza.
     */
    public static int[] export(VehicleDefinition def) {
        int[] out = new int[Math.max(0, def.texW * def.texH)];
        for (VehiclePart p : def.parts) {
            if (!p.hasUv()) {
                continue;
            }
            int tw = paintW(p);
            int th = paintH(p);
            int td = paintD(p);
            VehiclePart src = p;
            if (p.validPaint() && (p.pw != tw || p.ph != th || p.pd != td)) {
                src = p.copy();
                src.remapPaint(tw, th, td);
            }
            boolean paint = src.validPaint() && src.pw == tw && src.ph == th && src.pd == td;
            int base = MaterialIds.CLEAR.equals(p.material) ? 0 : 0xFF000000 | (p.color & 0xFFFFFF);
            for (int f = 0; f < PaintLayout.FACES; f++) {
                int fw = PaintLayout.faceW(f, tw, td);
                int fh = PaintLayout.faceH(f, th, td);
                for (int j = 0; j < fh; j++) {
                    for (int i = 0; i < fw; i++) {
                        int[] t = skinTexel(p, f, i, j);
                        if (t == null || t[0] < 0 || t[1] < 0 || t[0] >= def.texW || t[1] >= def.texH) {
                            continue;
                        }
                        int c = paint ? src.texel(f, i, j) : 0;
                        if ((c >>> 24) == 0) {
                            c = base;
                        }
                        int k = t[1] * def.texW + t[0];
                        if ((c >>> 24) != 0 || out[k] == 0) {
                            out[k] = c;
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Da sitio en la skin a las piezas visibles que no lo tienen (debajo de lo que ya esta ocupado) y agranda la
     * skin si hace falta. Devuelve cuantas piezas se colocaron.
     */
    public static int autoLayout(VehicleDefinition def) {
        List<VehiclePart> todo = new ArrayList<>();
        int used = 0;
        int maxW = 0;
        for (VehiclePart p : def.parts) {
            if (p.hasUv() && def.texW > 0) {
                used = Math.max(used, p.uvV + PaintLayout.imageH(p.uvH, p.uvD));
                maxW = Math.max(maxW, p.uvU + PaintLayout.imageW(p.uvW, p.uvD));
            } else if (visible(p)) {
                todo.add(p);
            }
        }
        if (todo.isEmpty()) {
            return 0;
        }
        int width = Math.max(MIN_WIDTH, Math.max(def.texW, maxW));
        for (VehiclePart p : todo) {
            p.uvW = p.validPaint() ? p.pw : VehiclePart.paintDim(p.w);
            p.uvH = p.validPaint() ? p.ph : VehiclePart.paintDim(p.h);
            p.uvD = p.validPaint() ? p.pd : VehiclePart.paintDim(p.d);
            width = Math.max(width, PaintLayout.imageW(p.uvW, p.uvD));
        }
        // las mas altas primero: las filas quedan mas compactas
        todo.sort((a, b) -> Integer.compare(PaintLayout.imageH(b.uvH, b.uvD), PaintLayout.imageH(a.uvH, a.uvD)));
        int x = 0;
        int y = used;
        int rowH = 0;
        for (VehiclePart p : todo) {
            int w = PaintLayout.imageW(p.uvW, p.uvD);
            int h = PaintLayout.imageH(p.uvH, p.uvD);
            if (x > 0 && x + w > width) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            p.uvU = x;
            p.uvV = y;
            p.uvMirror = false;
            x += w;
            rowH = Math.max(rowH, h);
        }
        int height = y + rowH;
        def.texW = width;
        def.texH = Math.max(Math.max(def.texH, 32), (height + 15) / 16 * 16);
        return todo.size();
    }

    /** Quita el sitio en la skin de todas las piezas (para volver a repartirlo desde cero). */
    public static void reset(VehicleDefinition def) {
        for (VehiclePart p : def.parts) {
            p.clearUv();
        }
        def.texW = 0;
        def.texH = 0;
    }
}
