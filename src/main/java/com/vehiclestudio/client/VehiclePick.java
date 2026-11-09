package com.vehiclestudio.client;

import com.vehiclestudio.entity.CustomVehicle;
import com.vehiclestudio.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Elegir un vehiculo con el raton: un rayo contra sus piezas tal cual (giradas), no contra cajas. Asi se hace clic en
 * cualquier parte de un vehiculo largo, una pieza tapa lo que tiene detras y, desde dentro de uno hueco, se puede
 * usar lo que hay dentro. El servidor comprueba con las mismas piezas que el jugador llega (paquete VehicleHit).
 */
public final class VehiclePick {
    /** Hasta donde puede quedar el centro de un vehiculo de lo que se mira (vehiculos largos). */
    private static final double REACH = 16.0;
    /** Atacar seguia pulsado en el tick anterior: picando un bloque, no un clic nuevo. */
    private static boolean attackHeld;

    private VehiclePick() {
    }

    /** La pieza de vehiculo mas cercana en la linea de la vista. */
    record Hit(CustomVehicle vehicle, Vec3 location, double distance) {
    }

    @Nullable
    static Hit pick(Minecraft mc, float partialTick) {
        Entity cam = mc.getCameraEntity();
        LocalPlayer player = mc.player;
        if (cam == null || player == null || mc.level == null || player.isSpectator()) {
            return null;
        }
        double range = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        Vec3 eye = cam.getEyePosition(partialTick);
        Vec3 look = cam.getViewVector(partialTick);
        Vec3 end = eye.add(look.x * range, look.y * range, look.z * range);
        Hit best = null;
        for (CustomVehicle v : mc.level.getEntitiesOfClass(CustomVehicle.class, new AABB(eye, end).inflate(REACH))) {
            // el vehiculo en el que se va sentado no se elige (se apunta a traves, como desde una barca)
            if (v.isRemoved() || v.hasPassenger(player)) {
                continue;
            }
            double t = v.rayHit(eye, end);
            if (t < 0) {
                continue;
            }
            double dist = t * range;
            if (best == null || dist < best.distance()) {
                best = new Hit(v, eye.add(look.x * dist, look.y * dist, look.z * dist), dist);
            }
        }
        return best;
    }

    /** Al final de cada tick del cliente. */
    static void tick(Minecraft mc) {
        attackHeld = mc.options != null && mc.options.keyAttack != null && mc.options.keyAttack.isDown();
    }

    /** Lo lejos que esta lo que el juego cree que se mira (infinito si es el aire). */
    private static double distanceOf(@Nullable HitResult hit, Vec3 eye) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(hit.getLocation().distanceToSqr(eye));
    }

    /** Golpear, usar o coger el objeto: si lo primero que hay delante es una pieza de un vehiculo, va al vehiculo. */
    static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Entity cam = mc.getCameraEntity();
        if (player == null || cam == null || mc.gameMode == null) {
            return;
        }
        Hit hit = pick(mc, 1.0f);
        if (hit == null) {
            return;
        }
        HitResult current = mc.hitResult;
        if (hit.distance() >= distanceOf(current, cam.getEyePosition(1.0f))) {
            // hay algo delante del vehiculo
            return;
        }
        boolean held = event.isAttack() && attackHeld && current != null && current.getType() == HitResult.Type.BLOCK;
        if (held) {
            // seguia picando un bloque que ahora queda detras de una pieza: deja de picar
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (hit.distance() > player.entityInteractionRange()) {
            // el vehiculo tapa lo de detras pero queda lejos para tocarlo: como mirar al aire
            mc.hitResult = BlockHitResult.miss(hit.location(), Direction.UP,
                    BlockPos.containing(hit.location().x, hit.location().y, hit.location().z));
            mc.crosshairPickEntity = null;
            return;
        }
        if (event.isPickBlock()) {
            // coger el objeto del vehiculo (creativo): lo hace el juego con el vehiculo como objetivo
            mc.hitResult = new EntityHitResult(hit.vehicle(), hit.location());
            mc.crosshairPickEntity = hit.vehicle();
            return;
        }
        event.setCanceled(true);
        int id = hit.vehicle().getId();
        if (event.isAttack()) {
            event.setSwingHand(true);
            PacketDistributor.sendToServer(new Payloads.VehicleHit(id, 0));
            player.attack(hit.vehicle());
            player.resetAttackStrengthTicker();
            // en este mismo tick el juego sigue con "picar" si se mantiene pulsado: eso ya no es un golpe nuevo
            attackHeld = true;
            return;
        }
        // usar: como el juego con cualquier entidad, primero con la mano principal y luego con la otra
        event.setSwingHand(false);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            PacketDistributor.sendToServer(new Payloads.VehicleHit(id, hand == InteractionHand.MAIN_HAND ? 1 : 2));
            InteractionResult result = player.interactOn(hit.vehicle(), hand);
            if (result.consumesAction()) {
                if (result.shouldSwing()) {
                    player.swing(hand);
                }
                return;
            }
            if (!stack.isEmpty()) {
                InteractionResult used = mc.gameMode.useItem(player, hand);
                if (used.consumesAction()) {
                    if (used.shouldSwing()) {
                        player.swing(hand);
                    }
                    return;
                }
            }
        }
    }

    /** No dibuja el borde del bloque que se mira si una pieza de un vehiculo lo tapa. */
    static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (cam == null) {
            return;
        }
        Hit hit = pick(mc, 1.0f);
        if (hit != null && hit.distance() < distanceOf(event.getTarget(), cam.getEyePosition(1.0f))) {
            event.setCanceled(true);
        }
    }
}
