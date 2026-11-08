package com.vehiclestudio.client.screen;

import com.vehiclestudio.client.ClientFiles;
import com.vehiclestudio.data.VehicleDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lista los JSON de .minecraft/vehiclestudio/ para importarlos como vehiculos nuevos. */
public class ImportScreen extends Screen {
    private static final int ROW_H = 14;
    private final Screen parent;
    private List<Path> files = new ArrayList<>();
    private int scroll;
    private int listX, listY, listW, listH;
    @Nullable
    private Component error;

    public ImportScreen(Screen parent) {
        super(Ui.tr("import.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        files = ClientFiles.list();
        listW = Math.min(320, width - 20);
        listX = (width - listW) / 2;
        listY = 40;
        listH = height - listY - 34;
        int bw = 110;
        addRenderableWidget(Button.builder(Ui.tr("library.folder"), b -> ClientFiles.openFolder())
                .bounds(width / 2 - bw - 4, height - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Ui.tr("import.refresh"), b -> rebuildWidgets())
                .bounds(width / 2 + 4, height - 26, bw / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Ui.tr("import.back"), b -> onClose())
                .bounds(width / 2 + 4 + bw / 2 + 2, height - 26, bw / 2 - 2, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Ui.BG);
        Ui.panel(g, listX, listY, listW, listH);
        int visible = Math.max(1, (listH - 4) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, files.size() - visible));
        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        for (int r = 0; r < visible + 1 && r + scroll < files.size(); r++) {
            int y = listY + 2 + r * ROW_H;
            boolean hover = Ui.inside(mouseX, mouseY, listX, y, listW, ROW_H);
            if (hover) {
                g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, 0x30FFFFFF);
            }
            String name = font.plainSubstrByWidth(files.get(r + scroll).getFileName().toString(), listW - 10);
            g.drawString(font, name, listX + 5, y + 3, Ui.TEXT, false);
        }
        g.disableScissor();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, Ui.TEXT);
        g.drawCenteredString(font, Ui.tr("import.hint"), width / 2, 24, Ui.MUTED);
        if (files.isEmpty()) {
            Ui.wrapped(g, font, Ui.tr("import.empty"), listX + 8, listY + 8, listW - 16, Ui.MUTED);
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
        if (Ui.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int row = (int) ((mouseY - listY - 2) / ROW_H) + scroll;
            if (row >= 0 && row < files.size()) {
                Path file = files.get(row);
                try {
                    VehicleDefinition def = ClientFiles.load(file);
                    minecraft.setScreen(new EditorScreen(parent, def, true, false));
                } catch (IOException e) {
                    error = Ui.tr("import.failed", file.getFileName().toString());
                }
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

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
