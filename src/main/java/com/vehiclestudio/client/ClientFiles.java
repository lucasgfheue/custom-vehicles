package com.vehiclestudio.client;

import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleJson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exportar e importar vehiculos (JSON) y skins (PNG) en .minecraft/vehiclestudio/ */
public final class ClientFiles {
    /** Lado maximo de las imagenes que se leen. */
    public static final int MAX_IMAGE = 4096;

    /** Una imagen en memoria: un color ARGB por pixel, fila a fila. */
    public record Image(int width, int height, int[] argb) {
    }

    private ClientFiles() {
    }

    public static Path folder() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("vehiclestudio");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    public static Path export(VehicleDefinition def) throws IOException {
        Path dir = folder();
        String base = safeName(def.name);
        Path target = dir.resolve(base + ".json");
        int n = 2;
        while (Files.exists(target)) {
            target = dir.resolve(base + "_" + n++ + ".json");
        }
        VehicleDefinition copy = def.copy();
        copy.owner = "";
        Files.writeString(target, VehicleJson.write(copy, true), StandardCharsets.UTF_8);
        return target;
    }

    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder(), "*.json")) {
            for (Path p : stream) {
                out.add(p);
            }
        } catch (IOException ignored) {
        }
        out.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return out;
    }

    /** Lee un archivo y lo prepara como vehiculo nuevo (con identificador nuevo). */
    public static VehicleDefinition load(Path file) throws IOException {
        VehicleDefinition def;
        try {
            def = VehicleJson.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
        def.id = VehicleDefinition.newId();
        def.owner = "";
        def.revision = 0;
        def.sanitize();
        if (def.id.isEmpty()) {
            def.id = VehicleDefinition.newId();
        }
        return def;
    }

    public static void openFolder() {
        Util.getPlatform().openFile(folder().toFile());
    }

    // ---------------------------------------------------------------------------------------
    // Skins (PNG)
    // ---------------------------------------------------------------------------------------

    /** Las imagenes PNG de la carpeta y de su subcarpeta "skins", ordenadas por nombre. */
    public static List<Path> listImages() {
        List<Path> out = new ArrayList<>();
        Path dir = folder();
        collectImages(dir, out);
        Path skins = dir.resolve("skins");
        if (Files.isDirectory(skins)) {
            collectImages(skins, out);
        }
        out.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return out;
    }

    private static void collectImages(Path dir, List<Path> out) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.endsWith(".png") && Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** Lee un PNG. Lanza IOException si no es un PNG valido o es demasiado grande. */
    public static Image readImage(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth();
            int h = img.getHeight();
            if (w < 1 || h < 1 || w > MAX_IMAGE || h > MAX_IMAGE) {
                throw new IOException(w + "x" + h);
            }
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = abgrToArgb(img.getPixelRGBA(x, y));
                }
            }
            return new Image(w, h, px);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Guarda la skin como &lt;nombre&gt;_skin.png (sin pisar archivos que ya existan). */
    public static Path writeSkin(String vehicleName, int[] argb, int w, int h) throws IOException {
        if (w < 1 || h < 1 || argb.length < w * h) {
            throw new IOException("empty image");
        }
        Path dir = folder();
        String base = safeName(vehicleName) + "_skin";
        Path target = dir.resolve(base + ".png");
        int n = 2;
        while (Files.exists(target)) {
            target = dir.resolve(base + "_" + n++ + ".png");
        }
        try (NativeImage img = new NativeImage(w, h, false)) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    img.setPixelRGBA(x, y, abgrToArgb(argb[y * w + x]));
                }
            }
            img.writeToFile(target);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
        return target;
    }

    /** De ABGR (formato de NativeImage) a ARGB y al reves: se intercambian el rojo y el azul. */
    public static int abgrToArgb(int c) {
        return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
    }

    private static String safeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
            if (sb.length() >= 40) {
                break;
            }
        }
        return sb.length() == 0 ? "vehiculo" : sb.toString();
    }
}
