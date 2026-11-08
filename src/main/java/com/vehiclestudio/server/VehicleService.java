package com.vehiclestudio.server;

import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleStore;
import com.vehiclestudio.entity.CustomVehicle;
import com.vehiclestudio.item.VehicleItem;
import com.vehiclestudio.network.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Logica del servidor para las acciones que piden los jugadores desde el editor. */
public final class VehicleService {
    public static final int MAX_VEHICLES = 500;

    private VehicleService() {
    }

    public static boolean canEdit(ServerPlayer player, VehicleDefinition existing) {
        if (player.hasPermissions(2)) {
            return true;
        }
        MinecraftServer server = player.getServer();
        if (server != null && server.isSingleplayerOwner(player.getGameProfile())) {
            return true;
        }
        return !existing.owner.isEmpty() && existing.owner.equals(player.getUUID().toString());
    }

    public static void save(ServerPlayer player, VehicleDefinition incoming) {
        incoming.sanitize();
        if (incoming.id.isEmpty()) {
            incoming.id = VehicleDefinition.newId();
        }
        VehicleDefinition existing = VehicleStore.SERVER.get(incoming.id);
        if (existing != null) {
            if (!canEdit(player, existing)) {
                ModNetwork.notice(player, "message.vehiclestudio.no_permission", existing.name, false);
                return;
            }
            incoming.owner = existing.owner;
            incoming.revision = existing.revision + 1;
        } else {
            if (VehicleStore.SERVER.size() >= MAX_VEHICLES) {
                ModNetwork.notice(player, "message.vehiclestudio.too_many", String.valueOf(MAX_VEHICLES), false);
                return;
            }
            incoming.owner = player.getUUID().toString();
            incoming.revision = 1;
        }
        VehicleStore.SERVER.put(incoming);
        VehicleFiles.save(incoming);
        ModNetwork.broadcast(incoming);
        ModNetwork.notice(player, "message.vehiclestudio.saved", incoming.name, true);
    }

    public static void delete(ServerPlayer player, String id) {
        VehicleDefinition existing = VehicleStore.SERVER.get(id);
        if (existing == null) {
            ModNetwork.notice(player, "message.vehiclestudio.unknown_vehicle", "", false);
            return;
        }
        if (!canEdit(player, existing)) {
            ModNetwork.notice(player, "message.vehiclestudio.no_permission", existing.name, false);
            return;
        }
        VehicleStore.SERVER.remove(id);
        VehicleFiles.delete(id);
        ModNetwork.broadcastRemoval(id);
        ModNetwork.notice(player, "message.vehiclestudio.deleted", existing.name, true);
    }

    /** Da el vehiculo como objeto: gratis en creativo; en supervivencia cuesta hierro segun su tamano. */
    public static void giveVehicle(ServerPlayer player, String id) {
        VehicleDefinition d = VehicleStore.SERVER.get(id);
        if (d == null) {
            ModNetwork.notice(player, "message.vehiclestudio.unknown_vehicle", "", false);
            return;
        }
        if (!player.getAbilities().instabuild) {
            int cost = d.ironCost();
            if (count(player, Items.IRON_INGOT) < cost) {
                ModNetwork.notice(player, "message.vehiclestudio.need_iron", String.valueOf(cost), false);
                return;
            }
            consume(player, Items.IRON_INGOT, cost);
        }
        ItemStack stack = VehicleItem.create(d.id, d);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ModNetwork.notice(player, "message.vehiclestudio.vehicle_given", d.name, true);
    }

    /** Lo coloca delante del jugador (solo creativo u operadores). */
    public static void summon(ServerPlayer player, String id) {
        if (!player.getAbilities().instabuild && !player.hasPermissions(2)) {
            ModNetwork.notice(player, "message.vehiclestudio.need_creative", "", false);
            return;
        }
        VehicleDefinition d = VehicleStore.SERVER.get(id);
        if (d == null) {
            ModNetwork.notice(player, "message.vehiclestudio.unknown_vehicle", "", false);
            return;
        }
        Vec3 look = player.getLookAngle();
        float[] size = d.size();
        double distance = 1.5 + Math.max(size[0], size[2]) / 2.0;
        double x = player.getX() + look.x * distance;
        double z = player.getZ() + look.z * distance;
        CustomVehicle.spawn(player.serverLevel(), d, x, player.getY(), z, Mth.wrapDegrees(player.getYRot()), true);
        ModNetwork.notice(player, "message.vehiclestudio.summoned", d.name, true);
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void consume(ServerPlayer player, Item item, int amount) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
            }
        }
        inventory.setChanged();
    }
}
