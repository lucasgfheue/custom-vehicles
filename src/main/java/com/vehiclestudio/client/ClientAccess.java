package com.vehiclestudio.client;

import com.vehiclestudio.client.render.PaintCache;
import com.vehiclestudio.client.screen.EditorScreen;
import com.vehiclestudio.client.screen.LibraryScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Puente entre el codigo comun y el del cliente. Solo debe llamarse desde codigo que ya sabe que
 * se esta ejecutando en el cliente (por ejemplo, dentro de {@code if (level.isClientSide())}).
 */
public final class ClientAccess {

    private ClientAccess() {
    }

    public static void openLibrary() {
        Minecraft.getInstance().setScreen(new LibraryScreen());
    }

    public static void onVehiclesChanged() {
        PaintCache.prune();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LibraryScreen library) {
            library.refresh();
        }
    }

    public static void notice(String key, String arg, boolean success) {
        Minecraft mc = Minecraft.getInstance();
        Component message = Component.translatable(key, arg);
        if (mc.screen instanceof EditorScreen editor) {
            editor.showNotice(message, success);
        } else if (mc.screen instanceof LibraryScreen library) {
            library.showNotice(message, success);
        } else if (mc.player != null) {
            mc.player.displayClientMessage(message.copy().withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED),
                    false);
        }
    }
}
