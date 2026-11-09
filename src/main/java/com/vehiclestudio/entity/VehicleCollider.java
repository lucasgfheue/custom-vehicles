package com.vehiclestudio.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Una caja solida del vehiculo (como las partes del dragon del End): los jugadores y los mobs chocan con ella y se
 * pueden subir encima. El vehiculo las coloca en cada tick alrededor de quien las puede tocar (en el cliente, de su
 * jugador; en el servidor, de los mobs, objetos y flechas); las que sobran se quedan apagadas. Con el raton no se eligen:
 * el cliente elige el vehiculo con un rayo contra sus piezas (ver VehiclePick).
 */
public class VehicleCollider extends PartEntity<CustomVehicle> {
    private boolean active;
    private AABB box;

    public VehicleCollider(CustomVehicle parent) {
        super(parent);
        this.blocksBuilding = true;
        this.box = new AABB(0, 0, 0, 0, 0, 0);
    }

    /** Pone la caja en el mundo (coordenadas absolutas). */
    void place(double x0, double y0, double z0, double x1, double y1, double z1) {
        double cx = (x0 + x1) / 2.0;
        double cz = (z0 + z1) / 2.0;
        this.xo = cx;
        this.yo = y0;
        this.zo = cz;
        this.xOld = cx;
        this.yOld = y0;
        this.zOld = cz;
        this.setPosRaw(cx, y0, cz);
        this.box = new AABB(x0, y0, z0, x1, y1, z1);
        this.setBoundingBox(this.box);
        this.active = true;
    }

    /** Apagada: ni choca ni se puede elegir (se queda en el vehiculo, sin tamano). */
    void deactivate() {
        if (!this.active && this.box.getXsize() == 0) {
            return;
        }
        this.active = false;
        CustomVehicle parent = getParent();
        this.setPosRaw(parent.getX(), parent.getY(), parent.getZ());
        this.box = new AABB(parent.getX(), parent.getY(), parent.getZ(), parent.getX(), parent.getY(), parent.getZ());
        this.setBoundingBox(this.box);
    }

    public boolean isActive() {
        return this.active;
    }

    @Override
    protected AABB makeBoundingBox() {
        return this.box != null ? this.box : super.makeBoundingBox();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.active;
    }

    @Override
    public boolean isPickable() {
        // solo en el servidor (para que las flechas den en el vehiculo); el cliente elige con el rayo exacto
        return this.active && !this.level().isClientSide();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return this.active && !this.isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    @Nullable
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }
}
