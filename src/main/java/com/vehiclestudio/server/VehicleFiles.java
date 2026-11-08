package com.vehiclestudio.server;

import com.vehiclestudio.VehicleStudio;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleJson;
import com.vehiclestudio.data.VehicleStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Guarda cada vehiculo como un archivo JSON en <mundo>/vehiclestudio/. */
public final class VehicleFiles {
    @Nullable
    private static Path dir;

    private VehicleFiles() {
    }

    public static void load(MinecraftServer server) {
        VehicleStore.SERVER.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("vehiclestudio");
        int count = 0;
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    try {
                        VehicleDefinition d = VehicleJson.read(Files.readString(file, StandardCharsets.UTF_8));
                        String fileName = file.getFileName().toString();
                        String fileId = fileName.substring(0, fileName.length() - ".json".length());
                        if (!VehicleDefinition.isValidId(d.id)) {
                            d.id = VehicleDefinition.isValidId(fileId) ? fileId : VehicleDefinition.newId();
                        }
                        d.sanitize();
                        if (d.id.isEmpty()) {
                            continue;
                        }
                        if (d.revision <= 0) {
                            d.revision = 1;
                        }
                        VehicleStore.SERVER.put(d);
                        count++;
                    } catch (Exception e) {
                        VehicleStudio.LOGGER.warn("Vehicle Studio: no se pudo leer {}: {}", file, e.toString());
                    }
                }
            }
        } catch (IOException e) {
            VehicleStudio.LOGGER.error("Vehicle Studio: no se pudo abrir la carpeta {}", dir, e);
        }
        VehicleStore.SERVER.setLoaded(true);
        VehicleStudio.LOGGER.info("Vehicle Studio: {} vehiculos cargados desde {}", count, dir);
    }

    public static void unload() {
        dir = null;
        VehicleStore.SERVER.clear();
    }

    public static void save(VehicleDefinition d) {
        Path folder = dir;
        if (folder == null) {
            return;
        }
        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(d.id + ".json");
            Path tmp = folder.resolve(d.id + ".json.tmp");
            Files.writeString(tmp, VehicleJson.write(d, true), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            VehicleStudio.LOGGER.error("Vehicle Studio: no se pudo guardar {}", d.id, e);
        }
    }

    public static void delete(String id) {
        Path folder = dir;
        if (folder == null || !VehicleDefinition.isValidId(id)) {
            return;
        }
        try {
            Files.deleteIfExists(folder.resolve(id + ".json"));
        } catch (IOException e) {
            VehicleStudio.LOGGER.error("Vehicle Studio: no se pudo borrar {}", id, e);
        }
    }
}
