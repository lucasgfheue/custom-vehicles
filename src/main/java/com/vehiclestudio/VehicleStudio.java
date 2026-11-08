package com.vehiclestudio;

import com.vehiclestudio.client.ClientSetup;
import com.vehiclestudio.network.ModNetwork;
import com.vehiclestudio.registry.ModRegistry;
import com.vehiclestudio.server.ServerEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(VehicleStudio.MODID)
public class VehicleStudio {
    public static final String MODID = "vehiclestudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VehicleStudio(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(VehicleStudio::onCreativeTabs);
        modBus.addListener(ModNetwork::register);
        ServerEvents.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.STUDIO_ITEM.get());
        }
    }
}
