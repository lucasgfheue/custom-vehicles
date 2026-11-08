package com.vehiclestudio.client.render;

import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehiclePart;
import com.vehiclestudio.data.PaintLayout;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Textura dinamica con la pintura a mano de todas las piezas de un vehiculo, colocadas por filas.
 * Solo se usa en el hilo de render.
 */
public final class PaintAtlas {
    private static final int MAX_SIZE = 4096;

    private final ResourceLocation location;
    @Nullable
    private DynamicTexture texture;
    private int width = 1;
    private int height = 1;
    private int[] slotX = new int[0];
    private int[] slotY = new int[0];

    public PaintAtlas(ResourceLocation location) {
        this.location = location;
    }

    public ResourceLocation location() {
        return location;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** true si la pieza tiene pintura dentro de esta textura. */
    public boolean has(int part) {
        return texture != null && part >= 0 && part < slotX.length && slotX[part] >= 0;
    }

    public int slotX(int part) {
        return slotX[part];
    }

    public int slotY(int part) {
        return slotY[part];
    }

    /** Vuelve a crear la imagen. Devuelve false (y libera la textura) si no hay nada pintado. */
    public boolean rebuild(VehicleDefinition def) {
        List<VehiclePart> parts = def.parts;
        int n = parts.size();
        int[] sx = new int[n];
        int[] sy = new int[n];
        List<Integer> order = new ArrayList<>();
        int maxW = 1;
        long area = 0;
        for (int i = 0; i < n; i++) {
            sx[i] = -1;
            sy[i] = -1;
            VehiclePart p = parts.get(i);
            if (p.hasPaint()) {
                order.add(i);
                maxW = Math.max(maxW, imageW(p));
                area += (long) imageW(p) * imageH(p);
            }
        }
        if (order.isEmpty()) {
            close();
            slotX = sx;
            slotY = sy;
            return false;
        }
        order.sort((a, b) -> Integer.compare(imageH(parts.get(b)), imageH(parts.get(a))));
        int atlasW = 64;
        while (atlasW < MAX_SIZE && (atlasW < maxW || (long) atlasW * atlasW < area * 2)) {
            atlasW *= 2;
        }
        int x = 0;
        int y = 0;
        int rowH = 0;
        for (int i : order) {
            VehiclePart p = parts.get(i);
            int w = imageW(p);
            int h = imageH(p);
            if (x > 0 && x + w > atlasW) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            if (y + h > MAX_SIZE) {
                continue; // no cabe: esta pieza se queda sin pintura visible
            }
            sx[i] = x;
            sy[i] = y;
            x += w;
            rowH = Math.max(rowH, h);
        }
        int atlasH = Math.max(1, y + rowH);
        NativeImage image = new NativeImage(atlasW, atlasH, true);
        for (int i : order) {
            if (sx[i] < 0) {
                continue;
            }
            VehiclePart p = parts.get(i);
            int w = imageW(p);
            int h = imageH(p);
            for (int ty = 0; ty < h; ty++) {
                for (int tx = 0; tx < w; tx++) {
                    int c = p.paint[ty * w + tx];
                    if ((c >>> 24) != 0) {
                        image.setPixelRGBA(sx[i] + tx, sy[i] + ty, toAbgr(c));
                    }
                }
            }
        }
        if (texture != null && width == atlasW && height == atlasH) {
            texture.setPixels(image);
            texture.upload();
        } else {
            close();
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(location, texture);
        }
        width = atlasW;
        height = atlasH;
        slotX = sx;
        slotY = sy;
        return true;
    }

    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(location);
            texture = null;
        }
    }

    private static int imageW(VehiclePart p) {
        return PaintLayout.imageW(p.pw, p.pd);
    }

    private static int imageH(VehiclePart p) {
        return PaintLayout.imageH(p.ph, p.pd);
    }

    /** NativeImage guarda los pixeles como ABGR. */
    static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    static int fromAbgr(int abgr) {
        return (abgr & 0xFF00FF00) | ((abgr >> 16) & 0xFF) | ((abgr & 0xFF) << 16);
    }
}
