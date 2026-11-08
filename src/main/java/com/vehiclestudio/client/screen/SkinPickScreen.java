package com.vehiclestudio.client.screen;

import com.vehiclestudio.VehicleStudio;
import com.vehiclestudio.client.ClientFiles;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Elegir una skin (PNG) de la carpeta .minecraft/vehiclestudio/ para ponerla en el vehiculo. Al pasar el
 * raton por un archivo se ve la imagen; con un clic se aplica y se vuelve al editor.
 */
public class SkinPickScreen extends Screen {
    private static final int ROW_H = 14;
    private static final ResourceLocation THUMB =
            ResourceLocation.fromNamespaceAndPath(VehicleStudio.MODID, "skin_pick/preview");

    private final Screen parent;
    /** Tamano de la skin que usal vehiculo (0 si aun no tiene). */
    private final int texW;
    private final int texH;
    private final BiConsumer<String, ClientFiles.Image> onPick;
    private List<Path> files = new ArrayList<>();
    private int scroll;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int thumbX;
    private int thumbY;
    private int thumbBox;
    private int infoY;
    @Nullable
    private Component error;

    @Nullable
    private Path thumbPath;
    @Nullable
    private DynamicTexture thumb;
    private int thumbW;
    private int thumbH;
    private boolean thumbFailed;

    public SkinPickScreen(Screen parent, int texW, int texH, BiConsumer<String, ClientFiles.Image> onPick) {
        super(Ui.tr("skinpick.title"));
        this.parent = parent;
        this.texW = texW;
        this.texH = texH;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        files = ClientFiles.listImages();
        int total = Math.min(520, width - 20);
        thumbBox = Mth.clamp(total * 2 / 5, 80, 180);
        listW = total - thumbBox - 10;
        listX = (width - total) / 2;
        int hintLines = font.split(Ui.tr("skinpick.hint"), total).size();
        int infoLines = font.split(info(), total).size();
        infoY = 22 + hintLines * 10 + 2;
        listY = infoY + infoLines * 10 + 6;
        listH = Math.max(40, height - listY - 34);
        thumbX = listX + listW + 10;
        thumbY = listY;
        thumbBox = Math.min(thumbBox, Math.max(40, listH - 24));
        int bw = 110;
        addRenderableWidget(Button.builder(Ui.tr("library.folder"), b -> ClientFiles.openFolder())
                .bounds(width / 2 - bw - 4, height - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Ui.tr("import.refresh"), b -> {
            releaseThumb();
            rebuildWidgets();
        }).bounds(width / 2 + 4, height - 26, bw / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Ui.tr("import.back"), b -> onClose())
                .bounds(width / 2 + 4 + bw / 2 + 2, height - 26, bw / 2 - 2, 20).build());
    }

    /** Que skin usal vehiculo (o que se hara si no tiene). */
    private Component info() {
        Component c = texW > 0 && texH > 0 ? Ui.tr("skinpick.current", texW, texH) : Ui.tr("skinpick.no_skin");
        return c.copy().withStyle(ChatFormatting.ITALIC);
    }

    private int visibleRows() {
        return Math.max(1, (listH - 4) / ROW_H);
    }

    private int rowAt(double mx, double my) {
        if (!Ui.inside(mx, my, listX, listY, listW, listH)) {
            return -1;
        }
        int row = (int) ((my - listY - 2) / ROW_H) + scroll;
        return row >= 0 && row < files.size() ? row : -1;
    }

    private String label(Path file) {
        Path dir = file.getParent();
        String name = file.getFileName().toString();
        return dir != null && dir.getFileName() != null && dir.getFileName().toString().equals("skins")
                ? "skins/" + name : name;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Ui.BG);
        Ui.panel(g, listX, listY, listW, listH);
        scroll = Mth.clamp(scroll, 0, Math.max(0, files.size() - visibleRows()));
        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0) {
            showThumb(files.get(hovered));
        }
        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        for (int r = 0; r < visibleRows() + 1 && r + scroll < files.size(); r++) {
            Path file = files.get(r + scroll);
            int y = listY + 2 + r * ROW_H;
            boolean shown = file.equals(thumbPath);
            if (r + scroll == hovered) {
                g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, 0x30FFFFFF);
            } else if (shown) {
                g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, 0x18FFFFFF);
            }
            String name = font.plainSubstrByWidth(label(file), listW - 10);
            g.drawString(font, name, listX + 5, y + 3, Ui.TEXT, false);
        }
        g.disableScissor();
        renderThumb(g);
    }

    private void renderThumb(GuiGraphics g) {
        Ui.panel(g, thumbX, thumbY, thumbBox, thumbBox);
        if (thumb == null || thumbW <= 0 || thumbH <= 0) {
            Component msg = thumbFailed ? Ui.tr("skinpick.unreadable") : Ui.tr("skinpick.hover");
            Ui.wrapped(g, font, msg, thumbX + 6, thumbY + 6, thumbBox - 12, thumbFailed ? Ui.ERROR : Ui.MUTED);
            return;
        }
        int inner = thumbBox - 8;
        float scale = Math.min(inner / (float) thumbW, inner / (float) thumbH);
        int dw = Math.max(1, Math.round(thumbW * scale));
        int dh = Math.max(1, Math.round(thumbH * scale));
        int dx = thumbX + (thumbBox - dw) / 2;
        int dy = thumbY + (thumbBox - dh) / 2;
        // cuadros de fondo para ver lo transparente
        int cell = Math.max(4, dw / 16);
        for (int y = 0; y < dh; y += cell) {
            for (int x = 0; x < dw; x += cell) {
                boolean dark = ((x / cell) + (y / cell)) % 2 == 0;
                g.fill(dx + x, dy + y, dx + Math.min(dw, x + cell), dy + Math.min(dh, y + cell),
                        dark ? 0xFF3A3D45 : 0xFF4A4E58);
            }
        }
        g.blit(THUMB, dx, dy, dw, dh, 0f, 0f, thumbW, thumbH, thumbW, thumbH);
        int ty = thumbY + thumbBox + 4;
        g.drawString(font, Ui.tr("skinpick.size", thumbW, thumbH), thumbX, ty, Ui.MUTED, false);
        if (texW > 0 && texH > 0 && !sameShape(thumbW, thumbH)) {
            Ui.wrapped(g, font, Ui.tr("skinpick.mismatch", texW, texH), thumbX, ty + 11, thumbBox, 0xFFE0B050);
        }
    }

    /** La imagen tiene la misma forma que la skin del vehiculo (o es un multiplo exacto, como 128x128). */
    private boolean sameShape(int w, int h) {
        return (long) w * texH == (long) h * texW && w % texW == 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, Ui.TEXT);
        Ui.wrapped(g, font, Ui.tr("skinpick.hint"), listX, 22, listW + thumbBox + 10, Ui.MUTED);
        Ui.wrapped(g, font, info(), listX, infoY, listW + thumbBox + 10, 0xFF7D8491);
        if (files.isEmpty()) {
            Ui.wrapped(g, font, Ui.tr("skinpick.empty"), listX + 8, listY + 8, listW - 16, Ui.MUTED);
        }
        if (error != null) {
            g.drawCenteredString(font, error, width / 2, height - 38, Ui.ERROR);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int row = rowAt(mouseX, mouseY);
        if (row >= 0 && button == 0) {
            Path file = files.get(row);
            try {
                ClientFiles.Image image = ClientFiles.readImage(file);
                minecraft.setScreen(parent);
                onPick.accept(file.getFileName().toString(), image);
            } catch (IOException e) {
                error = Ui.tr("skinpick.failed", file.getFileName().toString());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Ui.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---------------------------------------------------------------------------------------
    // Miniatura
    // ---------------------------------------------------------------------------------------

    private void showThumb(Path file) {
        if (file.equals(thumbPath)) {
            return;
        }
        releaseThumb();
        thumbPath = file;
        thumbFailed = false;
        NativeImage img = null;
        try (InputStream in = Files.newInputStream(file)) {
            img = NativeImage.read(in);
            if (img.getWidth() < 1 || img.getHeight() < 1
                    || img.getWidth() > ClientFiles.MAX_IMAGE || img.getHeight() > ClientFiles.MAX_IMAGE) {
                img.close();
                thumbFailed = true;
                return;
            }
            thumbW = img.getWidth();
            thumbH = img.getHeight();
            thumb = new DynamicTexture(img); // la textura se queda con la imagen y la cierra al liberarse
            minecraft.getTextureManager().register(THUMB, thumb);
        } catch (IOException | RuntimeException e) {
            if (img != null && thumb == null) {
                img.close();
            }
            thumb = null;
            thumbFailed = true;
        }
    }

    private void releaseThumb() {
        if (thumb != null) {
            minecraft.getTextureManager().release(THUMB);
            thumb = null;
        }
        thumbPath = null;
        thumbW = 0;
        thumbH = 0;
    }

    @Override
    public void removed() {
        releaseThumb();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
