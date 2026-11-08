package com.vehiclestudio.network;

import com.vehiclestudio.VehicleStudio;
import com.vehiclestudio.client.ClientAccess;
import com.vehiclestudio.entity.CustomVehicle;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleStore;
import com.vehiclestudio.data.DefCodec;
import com.vehiclestudio.server.VehicleService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registro de paquetes y manejadores. Los manejadores "ToClient" solo se ejecutan en el cliente,
 * por eso pueden llamar a {@link ClientAccess} sin romper los servidores dedicados.
 */
public final class ModNetwork {
    /** Los paquetes hacia el servidor deben pesar menos de 32 KiB: se envian trozos de este tamano. */
    public static final int MAX_UPLOAD_BYTES = 30000;
    /** Como mucho 64 trozos por vehiculo (unos 1,9 MB comprimidos). */
    public static final int MAX_CHUNKS = 64;
    public static final int MAX_TOTAL_UPLOAD = MAX_UPLOAD_BYTES * MAX_CHUNKS;

    /** Envios a medio recibir, uno por jugador (los manejadores corren en el hilo del servidor). */
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    private static final class Upload {
        final int transfer;
        final byte[][] chunks;
        int received;

        Upload(int transfer, int total) {
            this.transfer = transfer;
            this.chunks = new byte[total][];
        }
    }

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Payloads.SaveVehicle.TYPE, Payloads.SaveVehicle.STREAM_CODEC, ModNetwork::onSave);
        registrar.playToServer(Payloads.DeleteVehicle.TYPE, Payloads.DeleteVehicle.STREAM_CODEC, ModNetwork::onDelete);
        registrar.playToServer(Payloads.GiveVehicle.TYPE, Payloads.GiveVehicle.STREAM_CODEC, ModNetwork::onGiveVehicle);
        registrar.playToServer(Payloads.Summon.TYPE, Payloads.Summon.STREAM_CODEC, ModNetwork::onSummon);
        registrar.playToServer(Payloads.DriveInput.TYPE, Payloads.DriveInput.STREAM_CODEC, ModNetwork::onDriveInput);
        registrar.playToServer(Payloads.VehicleAction.TYPE, Payloads.VehicleAction.STREAM_CODEC, ModNetwork::onVehicleAction);

        registrar.playToClient(Payloads.SyncVehicle.TYPE, Payloads.SyncVehicle.STREAM_CODEC, ModNetwork::onSync);
        registrar.playToClient(Payloads.RemoveVehicle.TYPE, Payloads.RemoveVehicle.STREAM_CODEC, ModNetwork::onRemove);
        registrar.playToClient(Payloads.ClearVehicles.TYPE, Payloads.ClearVehicles.STREAM_CODEC, ModNetwork::onClear);
        registrar.playToClient(Payloads.Notice.TYPE, Payloads.Notice.STREAM_CODEC, ModNetwork::onNotice);
    }

    // ---------------------------------------------------------------------------------------
    // Servidor
    // ---------------------------------------------------------------------------------------

    private static void onSave(Payloads.SaveVehicle payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        int total = payload.total();
        int index = payload.index();
        if (payload.data().length > 32000 || total < 1 || total > MAX_CHUNKS || index < 0 || index >= total) {
            UPLOADS.remove(player.getUUID());
            notice(player, "message.vehiclestudio.too_big", "", false);
            return;
        }
        Upload upload = UPLOADS.get(player.getUUID());
        if (upload == null || upload.transfer != payload.transfer() || upload.chunks.length != total) {
            upload = new Upload(payload.transfer(), total);
            UPLOADS.put(player.getUUID(), upload);
        }
        if (upload.chunks[index] == null) {
            upload.chunks[index] = payload.data();
            upload.received++;
        }
        if (upload.received < total) {
            return;
        }
        UPLOADS.remove(player.getUUID());
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] chunk : upload.chunks) {
            joined.writeBytes(chunk);
        }
        VehicleDefinition def;
        try {
            def = DefCodec.decode(joined.toByteArray());
        } catch (IOException e) {
            VehicleStudio.LOGGER.warn("Definicion invalida recibida de {}: {}", player.getName().getString(), e.getMessage());
            notice(player, "message.vehiclestudio.invalid", "", false);
            return;
        }
        VehicleService.save(player, def);
    }

    private static void onDelete(Payloads.DeleteVehicle payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            VehicleService.delete(player, payload.id());
        }
    }

    private static void onGiveVehicle(Payloads.GiveVehicle payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            VehicleService.giveVehicle(player, payload.id());
        }
    }

    private static void onSummon(Payloads.Summon payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            VehicleService.summon(player, payload.id());
        }
    }

    private static void onDriveInput(Payloads.DriveInput payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof CustomVehicle v
                && v.getControllingPassenger() == player) {
            v.setDriverInput(payload.steer() / 100f, payload.braking());
        }
    }

    private static void onVehicleAction(Payloads.VehicleAction payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof CustomVehicle v) {
            switch (payload.action()) {
                case 0 -> v.honk();
                case 1 -> {
                    if (v.getControllingPassenger() == player) {
                        v.toggleLights();
                    }
                }
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Cliente
    // ---------------------------------------------------------------------------------------

    private static void onSync(Payloads.SyncVehicle payload, IPayloadContext context) {
        try {
            VehicleDefinition def = DefCodec.decode(payload.data());
            if (VehicleDefinition.isValidId(def.id)) {
                VehicleStore.CLIENT.put(def);
            }
        } catch (IOException e) {
            VehicleStudio.LOGGER.warn("No se pudo leer un vehiculo enviado por el servidor: {}", e.getMessage());
        }
        ClientAccess.onVehiclesChanged();
    }

    private static void onRemove(Payloads.RemoveVehicle payload, IPayloadContext context) {
        VehicleStore.CLIENT.remove(payload.id());
        ClientAccess.onVehiclesChanged();
    }

    private static void onClear(Payloads.ClearVehicles payload, IPayloadContext context) {
        VehicleStore.CLIENT.clear();
        VehicleStore.CLIENT.setLoaded(true);
        ClientAccess.onVehiclesChanged();
    }

    private static void onNotice(Payloads.Notice payload, IPayloadContext context) {
        ClientAccess.notice(payload.key(), payload.arg(), payload.success());
    }

    // ---------------------------------------------------------------------------------------
    // Envio
    // ---------------------------------------------------------------------------------------

    /** Envia un vehiculo al servidor (desde el cliente), partida en trozos si hace falta. */
    public static void sendSave(byte[] data) {
        int total = Math.max(1, (data.length + MAX_UPLOAD_BYTES - 1) / MAX_UPLOAD_BYTES);
        int transfer = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        for (int i = 0; i < total; i++) {
            int from = i * MAX_UPLOAD_BYTES;
            int to = Math.min(data.length, from + MAX_UPLOAD_BYTES);
            byte[] chunk = new byte[to - from];
            System.arraycopy(data, from, chunk, 0, chunk.length);
            PacketDistributor.sendToServer(new Payloads.SaveVehicle(transfer, i, total, chunk));
        }
    }

    public static void notice(ServerPlayer player, String key, String arg, boolean success) {
        PacketDistributor.sendToPlayer(player, new Payloads.Notice(key, arg == null ? "" : arg, success));
    }

    public static void syncAll(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Payloads.ClearVehicles(true));
        for (VehicleDefinition def : VehicleStore.SERVER.all()) {
            PacketDistributor.sendToPlayer(player, new Payloads.SyncVehicle(DefCodec.encode(def)));
        }
    }

    public static void broadcast(VehicleDefinition def) {
        PacketDistributor.sendToAllPlayers(new Payloads.SyncVehicle(DefCodec.encode(def)));
    }

    public static void broadcastRemoval(String id) {
        PacketDistributor.sendToAllPlayers(new Payloads.RemoveVehicle(id));
    }
}
