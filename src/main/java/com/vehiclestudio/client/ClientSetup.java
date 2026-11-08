package com.vehiclestudio.client;

import com.vehiclestudio.client.render.PaintCache;
import com.vehiclestudio.client.render.VehicleRenderer;
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
import net.minecraft.world.entity.Entity;
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
    private static Entity lastVehicle;

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerKeys);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onRenderGui);
        CustomVehicle.localControls = ClientSetup::controls;
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
        event.register(openStudio);
        event.register(horn);
        event.register(lights);
    }

    /** Los controles del jugador local si es quien conduce ese vehiculo (null si no). */
    @Nullable
    private static CustomVehicle.Controls controls(CustomVehicle v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || v.getControllingPassenger() != mc.player) {
            return null;
        }
        if (mc.screen != null) {
            return CustomVehicle.Controls.NONE;
        }
        Options o = mc.options;
        return new CustomVehicle.Controls(o.keyUp.isDown(), o.keyDown.isDown(), o.keyLeft.isDown(), o.keyRight.isDown(),
                o.keyJump.isDown());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
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
                mc.gui.setOverlayMessage(Component.translatable("message.vehiclestudio.how_to_drive",
                        move, mc.options.keyJump.getTranslatedKeyMessage(),
                        horn != null ? horn.getTranslatedKeyMessage() : Component.literal("H"),
                        lights != null ? lights.getTranslatedKeyMessage() : Component.literal("G"),
                        mc.options.keyShift.getTranslatedKeyMessage()), false);
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
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VehicleStore.CLIENT.clear();
        PaintCache.clear();
        VehicleEngineSound.clear();
    }
}
