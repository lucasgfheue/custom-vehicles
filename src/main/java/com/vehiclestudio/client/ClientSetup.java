package com.vehiclestudio.client;

import com.vehiclestudio.client.render.PaintCache;
import com.vehiclestudio.client.render.VehicleRenderer;
import com.vehiclestudio.data.DrivePhysics;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.entity.CustomVehicle;
import com.vehiclestudio.data.VehicleStore;
import com.vehiclestudio.network.Payloads;
import com.vehiclestudio.registry.ModRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Registro de todo lo que solo existe en el cliente: dibujado, teclas, controles y sonido del motor. */
public final class ClientSetup {
    /** Se crean al registrarlas (como recomienda NeoForge), no al cargar la clase. */
    @Nullable
    private static KeyMapping openStudio;
    @Nullable
    private static KeyMapping horn;
    @Nullable
    private static KeyMapping lights;
    @Nullable
    private static KeyMapping turbo;
    @Nullable
    private static Entity lastVehicle;

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerKeys);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onRenderGui);
        NeoForge.EVENT_BUS.addListener(VehiclePick::onClick);
        NeoForge.EVENT_BUS.addListener(VehiclePick::onHighlight);
        CustomVehicle.localControls = ClientSetup::controls;
        CustomVehicle.localPlayer = () -> Minecraft.getInstance().player;
        CustomVehicle.clientEffects = VehicleEngineSound::ensure;
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.VEHICLE.get(), VehicleRenderer::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        openStudio = new KeyMapping("key.vehiclestudio.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
                "key.categories.vehiclestudio");
        horn = new KeyMapping("key.vehiclestudio.horn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
                "key.categories.vehiclestudio");
        lights = new KeyMapping("key.vehiclestudio.lights", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
                "key.categories.vehiclestudio");
        turbo = new KeyMapping("key.vehiclestudio.turbo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
                "key.categories.vehiclestudio");
        event.register(openStudio);
        event.register(horn);
        event.register(lights);
        event.register(turbo);
    }

    /** Los controles del jugador local si es quien conduce ese vehiculo (null si no). */
    @Nullable
    private static DrivePhysics.Input controls(CustomVehicle v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || v.getControllingPassenger() != mc.player) {
            return null;
        }
        if (mc.screen != null) {
            // con una pantalla abierta sigue conduciendo sin tocar nada (un helicoptero se queda quieto en el aire)
            return DrivePhysics.Input.IDLE;
        }
        Options o = mc.options;
        boolean jump = o.keyJump.isDown();
        // Espacio: freno de mano en tierra o subir si vuela; Ctrl (correr): bajar
        return new DrivePhysics.Input(o.keyUp.isDown(), o.keyDown.isDown(), o.keyLeft.isDown(), o.keyRight.isDown(),
                jump, jump, o.keySprint.isDown(), turbo != null && turbo.isDown(), true);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        VehiclePick.tick(mc);
        if (openStudio != null) {
            while (openStudio.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    ClientAccess.openLibrary();
                }
            }
        }
        boolean riding = mc.player != null && mc.player.getVehicle() instanceof CustomVehicle;
        if (horn != null) {
            while (horn.consumeClick()) {
                if (riding) {
                    PacketDistributor.sendToServer(new Payloads.VehicleAction(0));
                }
            }
        }
        if (lights != null) {
            while (lights.consumeClick()) {
                if (riding) {
                    PacketDistributor.sendToServer(new Payloads.VehicleAction(1));
                }
            }
        }
        Entity vehicle = mc.player != null ? mc.player.getVehicle() : null;
        if (vehicle != lastVehicle) {
            lastVehicle = vehicle;
            if (vehicle instanceof CustomVehicle v && v.getControllingPassenger() == mc.player) {
                Component move = Component.empty().append(mc.options.keyUp.getTranslatedKeyMessage()).append("/")
                        .append(mc.options.keyLeft.getTranslatedKeyMessage()).append("/")
                        .append(mc.options.keyDown.getTranslatedKeyMessage()).append("/")
                        .append(mc.options.keyRight.getTranslatedKeyMessage());
                Component turboKey = turbo != null ? turbo.getTranslatedKeyMessage() : Component.literal("R");
                VehicleDefinition d = v.def();
                Component hint;
                if (d != null && d.canFly()) {
                    hint = Component.translatable(d.hasTurbo() ? "message.vehiclestudio.how_to_fly_turbo"
                                    : "message.vehiclestudio.how_to_fly", move, mc.options.keyJump.getTranslatedKeyMessage(),
                            mc.options.keySprint.getTranslatedKeyMessage(), turboKey,
                            mc.options.keyShift.getTranslatedKeyMessage());
                } else {
                    hint = Component.translatable(d != null && d.hasTurbo() ? "message.vehiclestudio.how_to_drive_turbo"
                                    : "message.vehiclestudio.how_to_drive",
                            move, mc.options.keyJump.getTranslatedKeyMessage(),
                            horn != null ? horn.getTranslatedKeyMessage() : Component.literal("H"),
                            lights != null ? lights.getTranslatedKeyMessage() : Component.literal("G"),
                            mc.options.keyShift.getTranslatedKeyMessage(), turboKey);
                }
                mc.gui.setOverlayMessage(hint, false);
            }
        }
    }

    /** Velocimetro abajo a la derecha mientras se conduce. */
    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof CustomVehicle v)
                || v.getControllingPassenger() != mc.player) {
            return;
        }
        VehicleDefinition d = v.def();
        if (d == null) {
            return;
        }
        int kmh = Math.round(v.getSpeedForSound() * 20f * 3.6f);
        GuiGraphics g = event.getGuiGraphics();
        Component text = Component.translatable("gui.vehiclestudio.speedometer", kmh);
        int x = g.guiWidth() - mc.font.width(text) - 8;
        int y = g.guiHeight() - 20;
        g.fill(x - 4, y - 3, g.guiWidth() - 4, y + 11, 0x90000000);
        g.drawString(mc.font, text, x, y, v.lightsOn() ? 0xFFFFE08A : 0xFFFFFFFF, false);
        int right = g.guiWidth() - 4;
        if (v.flying()) {
            // altura sobre el suelo que hay debajo
            int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(v.getX()), Mth.floor(v.getZ()));
            int height = Math.max(0, Mth.floor(v.getY()) - ground);
            Component alt = Component.translatable("gui.vehiclestudio.altitude", height);
            y -= 16;
            int ax = g.guiWidth() - mc.font.width(alt) - 8;
            g.fill(ax - 4, y - 3, right, y + 11, 0x90000000);
            g.drawString(mc.font, alt, ax, y, 0xFFB8E0FF, false);
        }
        if (d.hasTurbo()) {
            // deposito del turbo: barra naranja (roja si esta vacio)
            y -= 10;
            int w = 60;
            int bx = right - w - 4;
            float e = v.turboEnergy();
            g.fill(bx - 1, y - 1, bx + w + 1, y + 5, 0xA0000000);
            g.fill(bx, y, bx + Math.round(w * e), y + 4, v.boosting() ? 0xFFFFD040 : e < 0.15f ? 0xFFD03020 : 0xFFFF8A1E);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VehicleStore.CLIENT.clear();
        PaintCache.clear();
        VehicleEngineSound.clear();
    }
}
