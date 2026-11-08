package com.vehiclestudio.registry;

import com.vehiclestudio.VehicleStudio;
import com.vehiclestudio.block.VehicleStudioBlock;
import com.vehiclestudio.entity.CustomVehicle;
import com.vehiclestudio.item.VehicleItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VehicleStudio.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VehicleStudio.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, VehicleStudio.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, VehicleStudio.MODID);

    /** Identificador del vehiculo guardado dentro del objeto. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> VEHICLE_ID =
            COMPONENTS.register("vehicle_id", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static final DeferredBlock<VehicleStudioBlock> STUDIO_BLOCK = BLOCKS.register("vehicle_studio",
            () -> new VehicleStudioBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)));

    public static final DeferredItem<BlockItem> STUDIO_ITEM = ITEMS.register("vehicle_studio",
            () -> new BlockItem(STUDIO_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<VehicleItem> VEHICLE_ITEM = ITEMS.register("vehicle",
            () -> new VehicleItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<EntityType<?>, EntityType<CustomVehicle>> VEHICLE =
            ENTITIES.register("vehicle", () -> EntityType.Builder.<CustomVehicle>of(CustomVehicle::new, MobCategory.MISC)
                    .sized(1.5f, 1.0f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("vehicle"));

    private ModRegistry() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
    }
}
