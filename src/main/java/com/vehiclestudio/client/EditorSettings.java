package com.vehiclestudio.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Preferencias del editor que se recuerdan entre partidas (modo facil, ancho y estado del panel), en
 * .minecraft/vehiclestudio/editor.properties.
 */
public final class EditorSettings {
    /** Modo facil: solo las pestanas basicas (partes del cuerpo, plantillas y pintura). */
    /** Panel de la derecha plegado (solo se ve la columna de iconos). */
    public static boolean panelCollapsed;
    /** Ancho del panel elegido arrastrando su borde; 0 = automatico. */
    public static int panelWidth;

    private static boolean loaded;

    private EditorSettings() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Properties p = new Properties();
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException ignored) {
                // archivo roto: se usan los valores por defecto
            }
        }
        
        panelCollapsed = Boolean.parseBoolean(p.getProperty("panelCollapsed", "false"));
        try {
            panelWidth = Math.max(0, Math.min(1000, Integer.parseInt(p.getProperty("panelWidth", "0").trim())));
        } catch (NumberFormatException e) {
            panelWidth = 0;
        }
    }

    public static void save() {
        Properties p = new Properties();

        p.setProperty("panelCollapsed", String.valueOf(panelCollapsed));
        p.setProperty("panelWidth", String.valueOf(panelWidth));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "Vehicle Studio - editor");
        } catch (IOException ignored) {
            // no es importante si no se puede guardar
        }
    }

    private static Path file() {
        return ClientFiles.folder().resolve("editor.properties");
    }
}
