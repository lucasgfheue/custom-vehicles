package com.vehiclestudio.item;

import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleStore;
import com.vehiclestudio.entity.CustomVehicle;
import com.vehiclestudio.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Un vehiculo guardado como objeto: al usarlo en un bloque, lo coloca ahi mirando hacia donde mira el jugador. */
public class VehicleItem extends Item {

    public VehicleItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(String id, @Nullable VehicleDefinition def) {
        ItemStack stack = new ItemStack(ModRegistry.VEHICLE_ITEM.get());
        stack.set(ModRegistry.VEHICLE_ID.get(), id == null ? "" : id);
        if (def != null) {
            stack.set(DataComponents.ITEM_NAME,
                    Component.translatable("item.vehiclestudio.vehicle.named", def.name));
        }
        return stack;
    }

    @Nullable
    public static String getVehicleId(ItemStack stack) {
        return stack.get(ModRegistry.VEHICLE_ID.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        VehicleDefinition def = VehicleStore.SERVER.get(getVehicleId(stack));
        if (def == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.vehiclestudio.unknown_vehicle")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);
        BlockPos pos = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
        float yaw = player != null ? Mth.wrapDegrees(player.getYRot()) : level.getRandom().nextFloat() * 360.0f;
        CustomVehicle vehicle = CustomVehicle.spawn(serverLevel, def, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                yaw, true);
        if (vehicle != null && stack.has(DataComponents.CUSTOM_NAME)) {
            vehicle.setCustomName(stack.getHoverName());
        }
        if (vehicle != null && (player == null || !player.getAbilities().instabuild)) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VehicleDefinition d = VehicleStore.CLIENT.get(getVehicleId(stack));
        if (d == null) {
            tooltip.add(Component.translatable("tooltip.vehiclestudio.unknown").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable("tooltip.vehiclestudio.stats", format(d.maxSpeed),
                d.seats().size(), format(d.health)).withStyle(ChatFormatting.GRAY));
        if (d.storage > 0) {
            tooltip.add(Component.translatable("tooltip.vehiclestudio.storage", d.storage).withStyle(ChatFormatting.GRAY));
        }
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("id: " + d.id).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String format(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
