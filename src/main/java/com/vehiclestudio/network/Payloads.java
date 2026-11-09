package com.vehiclestudio.network;

import com.vehiclestudio.VehicleStudio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Todos los paquetes del mod. C2S = cliente a servidor, S2C = servidor a cliente. */
public final class Payloads {

    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VehicleStudio.MODID, path));
    }

    /**
     * Guardar (crear o actualizar) un vehiculo: el JSON comprimido se envia en trozos de menos de 32 KiB
     * (index de 0 a total-1, todos con el mismo numero de envio).
     */
    public record SaveVehicle(int transfer, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<SaveVehicle> TYPE = typeOf("save_vehicle");
        public static final StreamCodec<ByteBuf, SaveVehicle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SaveVehicle::transfer,
                ByteBufCodecs.VAR_INT, SaveVehicle::index,
                ByteBufCodecs.VAR_INT, SaveVehicle::total,
                ByteBufCodecs.BYTE_ARRAY, SaveVehicle::data,
                SaveVehicle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DeleteVehicle(String id) implements CustomPacketPayload {
        public static final Type<DeleteVehicle> TYPE = typeOf("delete_vehicle");
        public static final StreamCodec<ByteBuf, DeleteVehicle> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DeleteVehicle::id, DeleteVehicle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GiveVehicle(String id) implements CustomPacketPayload {
        public static final Type<GiveVehicle> TYPE = typeOf("give_egg");
        public static final StreamCodec<ByteBuf, GiveVehicle> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, GiveVehicle::id, GiveVehicle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Summon(String id) implements CustomPacketPayload {
        public static final Type<Summon> TYPE = typeOf("summon");
        public static final StreamCodec<ByteBuf, Summon> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Summon::id, Summon::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Una definicion completa enviada a los clientes. */
    public record SyncVehicle(byte[] data) implements CustomPacketPayload {
        public static final Type<SyncVehicle> TYPE = typeOf("sync_vehicle");
        public static final StreamCodec<ByteBuf, SyncVehicle> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, SyncVehicle::data, SyncVehicle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RemoveVehicle(String id) implements CustomPacketPayload {
        public static final Type<RemoveVehicle> TYPE = typeOf("remove_vehicle");
        public static final StreamCodec<ByteBuf, RemoveVehicle> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RemoveVehicle::id, RemoveVehicle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Borra la cache del cliente antes de una sincronizacion completa. */
    public record ClearVehicles(boolean full) implements CustomPacketPayload {
        public static final Type<ClearVehicles> TYPE = typeOf("clear_vehicles");
        public static final StreamCodec<ByteBuf, ClearVehicles> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, ClearVehicles::full, ClearVehicles::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Mensaje para mostrar en la pantalla abierta (o en el chat). */
    public record Notice(String key, String arg, boolean success) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = typeOf("notice");
        public static final StreamCodec<ByteBuf, Notice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Notice::key,
                ByteBufCodecs.STRING_UTF8, Notice::arg,
                ByteBufCodecs.BOOL, Notice::success,
                Notice::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Lo que hace el conductor (para que los demas vean girar las ruedas, las luces de freno, las llamas y las
     * helices): steer de -100 (derecha) a 100 (izquierda); flags = CustomVehicle.FLAG_* (freno, turbo, gas, vuelo).
     */
    public record DriveInput(int steer, int flags) implements CustomPacketPayload {
        public static final Type<DriveInput> TYPE = typeOf("drive_input");
        public static final StreamCodec<ByteBuf, DriveInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DriveInput::steer,
                ByteBufCodecs.VAR_INT, DriveInput::flags,
                DriveInput::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Golpe o clic derecho en un vehiculo, elegido en el cliente con un rayo contra sus piezas (action: 0 = golpe,
     * 1 = usar con la mano principal, 2 = con la otra mano).
     */
    public record VehicleHit(int entityId, int action) implements CustomPacketPayload {
        public static final Type<VehicleHit> TYPE = typeOf("vehicle_hit");
        public static final StreamCodec<ByteBuf, VehicleHit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, VehicleHit::entityId,
                ByteBufCodecs.VAR_INT, VehicleHit::action,
                VehicleHit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Tocar la bocina o encender/apagar las luces (action: 0 = bocina, 1 = luces). */
    public record VehicleAction(int action) implements CustomPacketPayload {
        public static final Type<VehicleAction> TYPE = typeOf("vehicle_action");
        public static final StreamCodec<ByteBuf, VehicleAction> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, VehicleAction::action, VehicleAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
