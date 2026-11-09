package com.vehiclestudio.entity;

import com.vehiclestudio.data.DrivePhysics;
import com.vehiclestudio.data.FlightMode;
import com.vehiclestudio.data.HornSound;
import com.vehiclestudio.data.PartKind;
import com.vehiclestudio.data.PoseSolver;
import com.vehiclestudio.data.VehicleDefinition;
import com.vehiclestudio.data.VehicleGeometry;
import com.vehiclestudio.data.VehiclePart;
import com.vehiclestudio.data.VehicleStore;
import com.vehiclestudio.item.VehicleItem;
import com.vehiclestudio.network.Payloads;
import com.vehiclestudio.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Un vehiculo en el mundo. Como las barcas: lo mueve el cliente del conductor (y el servidor cuando no hay
 * conductor), y los demas lo ven con interpolacion. Los asientos y las ruedas salen de su definicion.
 */
public class CustomVehicle extends Entity {
    private static final EntityDataAccessor<String> DATA_DEF =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_HURT_TIME =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HURT_DIR =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.INT);
    /** Volante del conductor (de -1 derecha a 1 izquierda), para que los demas vean girar las ruedas. */
    private static final EntityDataAccessor<Float> DATA_STEER =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.FLOAT);
    /** Lo que hace el conductor (FLAG_*), para que los demas vean las luces de freno, las llamas y las helices. */
    private static final EntityDataAccessor<Integer> DATA_INPUT =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LIGHTS =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.BOOLEAN);
    /** Quien va en cada asiento: ids de red separados por comas (-1 = libre). */
    private static final EntityDataAccessor<String> DATA_SEATS =
            SynchedEntityData.defineId(CustomVehicle.class, EntityDataSerializers.STRING);

    /** Frenando (luces de freno). */
    public static final int FLAG_BRAKE = 1;
    /** Con el turbo encendido. */
    public static final int FLAG_TURBO = 2;
    /** Acelerando (llamas mas grandes). */
    public static final int FLAG_GAS = 4;
    /** Volando. */
    public static final int FLAG_FLYING = 8;

    /** Lo pone el cliente: los controles si el conductor es el jugador local (null si no). */
    public static Function<CustomVehicle, DrivePhysics.Input> localControls = v -> null;
    /** Lo pone el cliente: sonido del motor y demas efectos. */
    public static Consumer<CustomVehicle> clientEffects = v -> {
    };

    private static final float DEG = PoseSolver.DEG;
    /**
     * El punto de enganche de un jugador sentado queda a la altura de la parte de abajo de sus muslos (0,6 sobre los
     * pies); se baja un poquito para que los muslos apoyen en el cojin en lugar de flotar.
     */
    private static final double THIGH = 0.03;
    private static final int MAX_STORAGE = 54;

    // Fisica (en el lado que controla el vehiculo)
    private final DrivePhysics.State physics = new DrivePhysics.State();
    private float deltaRotation;
    // Cliente: ruedas y volante para dibujar
    private float rolled;
    private float rolledO;
    private float steerShown;
    private float steerShownO;
    private int smokeTimer;
    // Envio de controles al servidor
    private int sentSteer = Integer.MIN_VALUE;
    private int sentFlags = -1;
    private int localFlags;
    // Inclinacion que se ve (pitch va en el xRot, que viaja solo por la red; roll sale del volante)
    private float roll;
    private float rollO;
    // Cliente: helices y llamas
    private float spin;
    private float spinO;
    private float spinRate;
    private float thrust;
    private float thrustO;
    private boolean wasBoosting;
    private int flameTimer;
    // Para el dano de caida: lo rapido que bajaba antes de tocar el suelo
    private double fallSpeed;
    // Interpolacion para los que no conducen
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    // Servidor
    private int hornCooldown;
    @Nullable
    private SimpleContainer storage;
    private final NonNullList<ItemStack> storedItems = NonNullList.withSize(MAX_STORAGE, ItemStack.EMPTY);
    // Cache de la definicion
    @Nullable
    private VehicleDefinition cached;
    private int cachedRevision = Integer.MIN_VALUE;
    private String seatsText = "";
    private int[] seatIds = new int[0];
    @Nullable
    private float[][] seatPoints;
    @Nullable
    private float[][] exhausts;
    @Nullable
    private float[] layout;
    /** Donde estaba en el tick anterior (servidor), para saber cuanto avanzo al atropellar. */
    private double pushX = Double.NaN;
    private double pushZ = Double.NaN;
    /** Medidas del vehiculo ({@link VehicleDefinition#size()}), que no cambian hasta que cambia la definicion. */
    @Nullable
    private float[] sizeCache;

    public CustomVehicle(EntityType<? extends CustomVehicle> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        // La gravedad la pone drive(); asi el servidor no echa al conductor por "volar" con el vehiculo
        this.setNoGravity(true);
    }

    /** Coloca un vehiculo en el mundo mirando hacia yaw. */
    @Nullable
    public static CustomVehicle spawn(ServerLevel level, VehicleDefinition def, double x, double y, double z, float yaw,
                                      boolean checkSpace) {
        CustomVehicle v = new CustomVehicle(ModRegistry.VEHICLE.get(), level);
        v.setDefinitionId(def.id);
        v.moveTo(x, y, z, yaw, 0f);
        v.refreshDimensions();
        if (checkSpace && !level.noCollision(v, v.getBoundingBox().deflate(0.05))) {
            v.setPos(x, y + 1.0, z);
        }
        level.addFreshEntity(v);
        return v;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DEF, "");
        builder.define(DATA_DAMAGE, 0f);
        builder.define(DATA_HURT_TIME, 0);
        builder.define(DATA_HURT_DIR, 1);
        builder.define(DATA_STEER, 0f);
        builder.define(DATA_INPUT, 0);
        builder.define(DATA_LIGHTS, false);
        builder.define(DATA_SEATS, "");
    }

    // ---------------------------------------------------------------------------------------
    // Definicion
    // ---------------------------------------------------------------------------------------

    public String getDefinitionId() {
        return this.entityData.get(DATA_DEF);
    }

    public void setDefinitionId(String id) {
        this.entityData.set(DATA_DEF, id == null ? "" : id);
    }

    @Nullable
    public VehicleDefinition def() {
        VehicleDefinition d = VehicleStore.of(this.level()).get(getDefinitionId());
        if (d != this.cached || (d != null && d.revision != this.cachedRevision)) {
            this.cached = d;
            this.cachedRevision = d == null ? Integer.MIN_VALUE : d.revision;
            this.seatPoints = null;
            this.exhausts = null;
            this.layout = null;
            this.sizeCache = null;
            this.refreshDimensions();
        }
        return d;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DEF.equals(key)) {
            this.cached = null;
            this.cachedRevision = Integer.MIN_VALUE;
            this.sizeCache = null;
            this.refreshDimensions();
        }
    }

    /** Medidas en bloques: {ancho, alto, largo, ...} (ver {@link VehicleDefinition#size()}). */
    public float[] size(VehicleDefinition d) {
        if (this.sizeCache == null || d != this.cached) {
            float[] s = d.size();
            if (d != this.cached) {
                return s;
            }
            this.sizeCache = s;
        }
        return this.sizeCache;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        VehicleDefinition d = this.cached != null ? this.cached : VehicleStore.of(this.level()).get(getDefinitionId());
        if (d == null) {
            return super.getDimensions(pose);
        }
        float[] size = size(d);
        return EntityDimensions.scalable(size[0], size[1]);
    }

    @Override
    public float maxUpStep() {
        VehicleDefinition d = def();
        return d == null ? 0.6f : d.stepHeight;
    }

    @Override
    public float getPickRadius() {
        VehicleDefinition d = def();
        if (d == null) {
            return 0f;
        }
        // la caja es cuadrada (el ancho): se agranda para poder hacer clic en el morro y la cola, pero poco, para no
        // tapar los bloques de alrededor
        float[] size = size(d);
        return Math.min(1.0f, Math.max(0f, (size[2] - size[0]) / 2f));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        VehicleDefinition d = def();
        if (d == null) {
            return super.getBoundingBoxForCulling();
        }
        float[] size = size(d);
        double r = Math.max(size[0], size[2]) / 2.0 + 0.5;
        return new AABB(getX() - r, getY() - 0.5, getZ() - r, getX() + r, getY() + size[1] + 0.5, getZ() + r);
    }

    @Override
    protected Component getTypeName() {
        VehicleDefinition d = def();
        return d != null ? Component.literal(d.name) : super.getTypeName();
    }

    // ---------------------------------------------------------------------------------------
    // Del modelo al mundo
    // ---------------------------------------------------------------------------------------

    /**
     * {cx, minY, cz, escala}: el modelo se dibuja centrado en (cx, cz) y con su punto mas bajo en el suelo, en
     * bloques = pixeles * escala / 16 y reflejado en X (como Minecraft: +X del modelo es la izquierda).
     */
    public float[] layout(VehicleDefinition d) {
        if (this.layout == null) {
            this.layout = VehicleGeometry.layout(d);
        }
        return this.layout;
    }

    /** Matriz del espacio del modelo (pixeles) a desplazamientos en el mundo desde la posicion del vehiculo. */
    public Matrix4f modelToWorld(VehicleDefinition d, float yaw) {
        return VehicleGeometry.modelToWorld(layout(d), yaw, this.getXRot(), this.roll);
    }

    /** Puntos de los asientos en el espacio del modelo (pixeles), en el orden de {@link VehicleDefinition#seats()}. */
    private float[][] seatPoints(VehicleDefinition d) {
        if (this.seatPoints == null) {
            this.seatPoints = VehicleGeometry.seatPoints(d);
        }
        return this.seatPoints;
    }

    /** Salidas de humo: {x, y, z, dirX, dirY, dirZ} en el espacio del modelo. */
    private float[][] exhausts(VehicleDefinition d) {
        if (this.exhausts == null) {
            this.exhausts = VehicleGeometry.exhausts(d);
        }
        return this.exhausts;
    }

    // ---------------------------------------------------------------------------------------
    // Asientos y pasajeros
    // ---------------------------------------------------------------------------------------

    public int seatCount() {
        VehicleDefinition d = def();
        return d == null ? 0 : d.seats().size();
    }

    private int[] seatIds() {
        String text = this.entityData.get(DATA_SEATS);
        if (!text.equals(this.seatsText)) {
            this.seatsText = text;
            if (text.isEmpty()) {
                this.seatIds = new int[0];
            } else {
                String[] parts = text.split(",");
                int[] ids = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try {
                        ids[i] = Integer.parseInt(parts[i]);
                    } catch (NumberFormatException e) {
                        ids[i] = -1;
                    }
                }
                this.seatIds = ids;
            }
        }
        return this.seatIds;
    }

    private void writeSeats(int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids[i]);
        }
        this.entityData.set(DATA_SEATS, sb.toString());
    }

    /** El asiento de un pasajero (si aun no se sabe, el primero libre en el orden de los pasajeros). */
    public int seatOf(Entity passenger) {
        int count = seatCount();
        int[] ids = seatIds();
        for (int s = 0; s < ids.length && s < count; s++) {
            if (ids[s] == passenger.getId()) {
                return s;
            }
        }
        int index = getPassengers().indexOf(passenger);
        boolean[] used = new boolean[count];
        for (int s = 0; s < ids.length && s < count; s++) {
            used[s] = ids[s] >= 0;
        }
        // aun no llego la lista de asientos (llega despues que el pasajero): lo mismo que hara el servidor
        VehicleDefinition d = def();
        int driver = d == null ? 0 : d.driverSeat();
        if (passenger instanceof Player && driver < count && !used[driver] && index == firstUnlistedPlayer(ids)) {
            return driver;
        }
        for (int s = 0, seen = 0; s < count; s++) {
            if (!used[s] && seen++ == Math.max(0, index)) {
                return s;
            }
        }
        return Math.max(0, Math.min(count - 1, index));
    }

    /** Posicion (en la lista de pasajeros) del primer jugador que aun no tiene asiento en la lista. */
    private int firstUnlistedPlayer(int[] ids) {
        List<Entity> passengers = getPassengers();
        for (int i = 0; i < passengers.size(); i++) {
            Entity e = passengers.get(i);
            if (!(e instanceof Player)) {
                continue;
            }
            boolean listed = false;
            for (int id : ids) {
                listed |= id == e.getId();
            }
            if (!listed) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seatCount();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (this.level().isClientSide()) {
            return;
        }
        VehicleDefinition d = def();
        int count = seatCount();
        if (d == null || count == 0) {
            return;
        }
        int[] ids = Arrays.copyOf(seatIds(), count);
        for (int s = seatIds().length; s < count; s++) {
            ids[s] = -1;
        }
        for (int s = 0; s < count; s++) {
            Entity seated = ids[s] >= 0 ? this.level().getEntity(ids[s]) : null;
            if (seated == null || !hasPassenger(seated)) {
                ids[s] = -1;
            }
        }
        int driver = d.driverSeat();
        int seat = -1;
        if (passenger instanceof Player && driver < count && ids[driver] < 0) {
            seat = driver;
        } else {
            // el asiento libre mas cercano a donde estaba (sin quitarle el del conductor a un jugador)
            float[][] points = seatPoints(d);
            Matrix4f m = modelToWorld(d, getYRot());
            Vector3f v = new Vector3f();
            double best = Double.MAX_VALUE;
            for (int s = 0; s < count; s++) {
                if (ids[s] >= 0 || (s == driver && !(passenger instanceof Player) && count > 1)) {
                    continue;
                }
                m.transformPosition(points[s][0], points[s][1], points[s][2], v);
                double dist = passenger.distanceToSqr(getX() + v.x, getY() + v.y, getZ() + v.z);
                if (dist < best) {
                    best = dist;
                    seat = s;
                }
            }
            if (seat < 0) {
                for (int s = 0; s < count && seat < 0; s++) {
                    if (ids[s] < 0) {
                        seat = s;
                    }
                }
            }
        }
        if (seat >= 0) {
            ids[seat] = passenger.getId();
        }
        writeSeats(ids);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (this.level().isClientSide()) {
            return;
        }
        int[] ids = seatIds().clone();
        for (int s = 0; s < ids.length; s++) {
            if (ids[s] == passenger.getId()) {
                ids[s] = -1;
            }
        }
        writeSeats(ids);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        VehicleDefinition d = def();
        if (d == null) {
            return null;
        }
        int driver = d.driverSeat();
        for (Entity e : getPassengers()) {
            if (e instanceof Player player && seatOf(e) == driver) {
                return player;
            }
        }
        return null;
    }

    /** Desplazamiento (bloques) desde la posicion del vehiculo hasta un asiento, ya girado. */
    public Vec3 seatOffset(int seat, float yaw) {
        VehicleDefinition d = def();
        if (d == null) {
            return Vec3.ZERO;
        }
        float[][] points = seatPoints(d);
        if (points.length == 0) {
            return new Vec3(0, getBbHeight(), 0);
        }
        float[] p = points[Mth.clamp(seat, 0, points.length - 1)];
        Vector3f v = modelToWorld(d, yaw).transformPosition(p[0], p[1], p[2], new Vector3f());
        return new Vec3(v.x, v.y, v.z);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return seatOffset(seatOf(entity), getYRot()).add(0, -THIGH, 0);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        passenger.setYRot(passenger.getYRot() + this.deltaRotation);
        passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
        clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampRotation(passenger);
    }

    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float g = Mth.clamp(f, -105.0f, 105.0f);
        passenger.yRotO += g - f;
        passenger.setYRot(passenger.getYRot() + g - f);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        VehicleDefinition d = def();
        if (d == null) {
            return super.getDismountLocationForPassenger(passenger);
        }
        Vec3 seat = seatOffset(seatOf(passenger), getYRot());
        float yaw = getYRot() * DEG;
        // hacia fuera por el lado del asiento, luego el otro lado, detras y delante
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        double side = seat.dot(left) >= 0 ? 1 : -1;
        float[] size = size(d);
        double half = size[0] / 2.0 + passenger.getBbWidth() / 2.0 + 0.3;
        double halfLength = size[2] / 2.0 + passenger.getBbWidth() / 2.0 + 0.3;
        Vec3 flat = new Vec3(seat.x, 0, seat.z);
        Vec3[] tries = {
                flat.add(left.scale(side * half - flat.dot(left))),
                flat.add(left.scale(-side * half - flat.dot(left))),
                forward.scale(-halfLength),
                forward.scale(halfLength)
        };
        for (Vec3 t : tries) {
            double x = getX() + t.x;
            double z = getZ() + t.z;
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos pos = BlockPos.containing(x, getY() + dy, z);
                double floor = this.level().getBlockFloorHeight(pos);
                if (!DismountHelper.isBlockFloorValid(floor)) {
                    continue;
                }
                Vec3 spot = new Vec3(x, pos.getY() + floor, z);
                for (Pose pose : passenger.getDismountPoses()) {
                    if (DismountHelper.canDismountTo(this.level(), spot, passenger, pose)) {
                        passenger.setPose(pose);
                        return spot;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ---------------------------------------------------------------------------------------
    // Interaccion
    // ---------------------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        VehicleDefinition d = def();
        if (d == null) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (d.storage <= 0) {
                return InteractionResult.PASS;
            }
            if (!this.level().isClientSide()) {
                openStorage(player, d);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (hasPassenger(player)) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    private SimpleContainer storage() {
        if (this.storage == null) {
            this.storage = new SimpleContainer(MAX_STORAGE) {
                @Override
                public boolean stillValid(Player player) {
                    return CustomVehicle.this.isAlive() && player.distanceToSqr(CustomVehicle.this) < 100.0;
                }
            };
            for (int i = 0; i < MAX_STORAGE; i++) {
                this.storage.setItem(i, this.storedItems.get(i));
            }
        }
        return this.storage;
    }

    private void openStorage(Player player, VehicleDefinition d) {
        int rows = Mth.clamp(d.storage / 9, 1, 6);
        SimpleContainer container = storage();
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(type, id, inventory, container, rows),
                getDisplayName()));
    }

    /** Servidor: la bocina (la oyen todos). */
    public void honk() {
        VehicleDefinition d = def();
        if (d == null || d.horn == HornSound.NONE || this.hornCooldown > 0) {
            return;
        }
        this.hornCooldown = 8;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse(d.horn.sound()));
        if (sound != null) {
            this.level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 2.0f, d.horn.pitch());
        }
    }

    /** Servidor: enciende o apaga las luces. */
    public void toggleLights() {
        this.entityData.set(DATA_LIGHTS, !lightsOn());
        this.level().playSound(null, getX(), getY(), getZ(),
                BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace("block.lever.click")),
                SoundSource.NEUTRAL, 0.5f, lightsOn() ? 1.2f : 0.9f);
    }

    /** Servidor: lo que hace el conductor, para los demas jugadores. */
    public void setDriverInput(float steer, int flags) {
        this.entityData.set(DATA_STEER, Mth.clamp(steer, -1f, 1f));
        this.entityData.set(DATA_INPUT, flags & (FLAG_BRAKE | FLAG_TURBO | FLAG_GAS | FLAG_FLYING));
    }

    private int inputFlags() {
        return isLocallyDriven() ? this.localFlags : this.entityData.get(DATA_INPUT);
    }

    /** Con el turbo encendido (llamas grandes). */
    public boolean boosting() {
        return (inputFlags() & FLAG_TURBO) != 0;
    }

    /** Volando. */
    public boolean flying() {
        return (inputFlags() & FLAG_FLYING) != 0;
    }

    /** Deposito del turbo del conductor local (0 a 1). */
    public float turboEnergy() {
        return this.physics.energy;
    }

    public boolean lightsOn() {
        return this.entityData.get(DATA_LIGHTS);
    }

    /** Frenando (para las luces de freno). */
    public boolean braking() {
        return (inputFlags() & FLAG_BRAKE) != 0;
    }

    private boolean isLocallyDriven() {
        return this.level().isClientSide() && isControlledByLocalInstance();
    }

    // ---------------------------------------------------------------------------------------
    // Dano
    // ---------------------------------------------------------------------------------------

    public float getDamage() {
        return this.entityData.get(DATA_DAMAGE);
    }

    public void setDamage(float damage) {
        this.entityData.set(DATA_DAMAGE, damage);
    }

    public int getHurtTime() {
        return this.entityData.get(DATA_HURT_TIME);
    }

    public int getHurtDir() {
        return this.entityData.get(DATA_HURT_DIR);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (this.level().isClientSide() || this.isRemoved()) {
            return true;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && this.hasPassenger(attacker)) {
            // los que van dentro no rompen su propio vehiculo
            return false;
        }
        VehicleDefinition d = def();
        this.entityData.set(DATA_HURT_DIR, -getHurtDir());
        this.entityData.set(DATA_HURT_TIME, 10);
        this.markHurt();
        setDamage(getDamage() + amount);
        boolean creative = attacker instanceof Player p && p.getAbilities().instabuild;
        if (creative || getDamage() > (d == null ? 10f : d.health)) {
            destroy(!creative);
        }
        return true;
    }

    /** Se rompe: suelta el maletero y (si dropItem) el propio vehiculo como objeto. */
    public void destroy(boolean dropItem) {
        if (this.level() instanceof ServerLevel server) {
            Containers.dropContents(server, this, storage());
            this.storage().clearContent();
            VehicleDefinition d = def();
            if (dropItem && d != null && server.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                ItemStack stack = VehicleItem.create(d.id, d);
                if (hasCustomName()) {
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
                }
                spawnAtLocation(stack);
            }
        }
        this.ejectPassengers();
        this.discard();
    }

    @Override
    public ItemStack getPickResult() {
        VehicleDefinition d = def();
        return d == null ? ItemStack.EMPTY : VehicleItem.create(d.id, d);
    }

    @Override
    public boolean isPickable() {
        if (this.isRemoved()) {
            return false;
        }
        // Quien va dentro tiene los ojos dentro de la caja del vehiculo: el rayo del cursor lo elegiria siempre a el
        // (y un clic lo romperia). Asi apunta a traves, como desde una barca.
        if (this.level().isClientSide()) {
            for (Entity e : getPassengers()) {
                if (e instanceof Player p && p.isLocalPlayer()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        // sin definicion (aun no llego al cliente) no hay nada que ver: que no sea un obstaculo invisible
        return def() != null;
    }

    /** Servidor: la definicion ya no existe (se borro en esta sesion o no se cargo con el mundo). */
    private boolean isOrphan() {
        VehicleStore store = VehicleStore.SERVER;
        return store.wasDeleted(getDefinitionId()) || (store.isLoaded() && this.tickCount > 40);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ---------------------------------------------------------------------------------------
    // Movimiento
    // ---------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (getHurtTime() > 0) {
            this.entityData.set(DATA_HURT_TIME, getHurtTime() - 1);
        }
        if (getDamage() > 0f && !this.level().isClientSide()) {
            setDamage(Math.max(0f, getDamage() - 0.05f));
        }
        if (this.hornCooldown > 0) {
            this.hornCooldown--;
        }
        super.tick();
        VehicleDefinition d = def();
        if (d == null && !this.level().isClientSide() && isOrphan()) {
            // su definicion se borro (o no existe): desaparece, soltando lo que llevaba en el maletero
            destroy(false);
            return;
        }
        tickLerp();
        if (!this.isNoGravity()) {
            this.setNoGravity(true);
        }
        if (isControlledByLocalInstance()) {
            DrivePhysics.Input c = DrivePhysics.Input.NONE;
            if (this.level().isClientSide()) {
                DrivePhysics.Input local = localControls.apply(this);
                if (local != null) {
                    c = local;
                }
            }
            drive(d, c);
            if (this.level().isClientSide()) {
                sendControls();
            }
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            this.deltaRotation = 0f;
        }
        tickRoll(d);
        this.checkInsideBlocks();
        if (this.level().isClientSide()) {
            clientTick(d);
        } else if (d != null) {
            pushEntities(d);
        }
    }

    /** La fisica: acelerar, frenar, girar como una bici (segun la distancia entre ruedas) y derrapar. */
    private void drive(@Nullable VehicleDefinition d, DrivePhysics.Input c) {
        boolean ground = this.onGround();
        boolean water = this.isInWater();
        float deltaYaw = 0f;
        float gripMul = 1f;
        float flyVy = Float.NaN;
        boolean flying = false;
        float pitchTarget = 0f;
        if (d != null) {
            DrivePhysics.Step step = DrivePhysics.step(d, this.physics, c, ground, water);
            deltaYaw = step.deltaYaw();
            gripMul = step.grip();
            flyVy = step.vy();
            flying = step.flying();
            pitchTarget = step.pitch();
            int flags = 0;
            if (step.braking()) {
                flags |= FLAG_BRAKE;
            }
            if (step.boosting()) {
                flags |= FLAG_TURBO;
            }
            if (c.forward() || (flying && (c.up() || this.physics.speed > 0.02f))) {
                flags |= FLAG_GAS;
            }
            if (flying) {
                flags |= FLAG_FLYING;
            }
            this.localFlags = flags;
            if (!this.level().isClientSide()) {
                // sin conductor lo mueve el servidor: que los demas tambien lo vean
                this.entityData.set(DATA_INPUT, flags);
                this.entityData.set(DATA_STEER, this.physics.steer);
            }
        } else {
            this.physics.speed = 0f;
            this.localFlags = 0;
        }
        this.deltaRotation = deltaYaw;
        this.setYRot(this.getYRot() + this.deltaRotation);
        // el morro sube o baja poco a poco (xRot viaja solo al servidor y a los demas)
        this.setXRot(this.getXRot() + (pitchTarget - this.getXRot()) * 0.2f);
        float speed = this.physics.speed;
        Vec3 v = this.getDeltaMovement();
        double yaw = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double grip = flying ? gripMul : ground ? (1.0 - (d == null ? 0 : d.drift) * 0.0085) * gripMul : 0.0;
        double hx = v.x + (fx * speed - v.x) * grip;
        double hz = v.z + (fz * speed - v.z) * grip;
        if (!ground && !flying) {
            hx *= 0.99;
            hz *= 0.99;
        }
        double vy;
        if (!Float.isNaN(flyVy)) {
            vy = flyVy;
        } else {
            vy = (v.y - 0.08) * 0.98;
            if (water) {
                vy = Math.max(vy * 0.5, -0.06);
                hx *= 0.9;
                hz *= 0.9;
            }
        }
        this.setDeltaMovement(hx, vy, hz);
        double beforeX = this.getX();
        double beforeZ = this.getZ();
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.verticalCollision && !Float.isNaN(this.physics.vy)) {
            // tocando techo o suelo: deja de subir o bajar
            this.physics.vy = 0f;
        }
        if (this.horizontalCollision) {
            // choque: se queda con lo que avanzo de verdad
            double moved = (this.getX() - beforeX) * fx + (this.getZ() - beforeZ) * fz;
            this.physics.speed = (float) (Math.signum(speed) * Math.min(Math.abs(speed), Math.abs(moved)) * 0.5);
        }
    }

    /** Cliente del conductor: manda el volante, el freno, el turbo... cuando cambian. */
    private void sendControls() {
        int s = Math.round(this.physics.steer * 100f);
        if (s != this.sentSteer || this.localFlags != this.sentFlags) {
            this.sentSteer = s;
            this.sentFlags = this.localFlags;
            PacketDistributor.sendToServer(new Payloads.DriveInput(s, this.localFlags));
        }
    }

    /** Inclinacion hacia un lado (todos los lados): sale del volante cuando vuela. */
    private void tickRoll(@Nullable VehicleDefinition d) {
        this.rollO = this.roll;
        float target = 0f;
        if (d != null && flying()) {
            float steer = isLocallyDriven() || !this.level().isClientSide() && getControllingPassenger() == null
                    ? this.physics.steer : this.entityData.get(DATA_STEER);
            target = steer * (d.flight == FlightMode.PLANE ? 28f : 8f);
        }
        this.roll += (target - this.roll) * 0.25f;
        if (Math.abs(this.roll) < 1.0e-3f) {
            this.roll = 0f;
        }
    }

    /** Inclinacion del morro (grados, positivo = arriba) para dibujar. */
    public float getPitchShown(float partialTick) {
        return Mth.lerp(partialTick, this.xRotO, this.getXRot());
    }

    /** Inclinacion hacia un lado (grados, positivo = baja el lado izquierdo) para dibujar. */
    public float getRollShown(float partialTick) {
        return Mth.lerp(partialTick, this.rollO, this.roll);
    }

    /** Giro de las helices (radianes) para dibujar. */
    public float getSpin(float partialTick) {
        return Mth.lerp(partialTick, this.spinO, this.spin);
    }

    /** Fuerza de las llamas (0 apagadas, 1 normal, mas con el turbo) para dibujar. */
    public float getThrust(float partialTick) {
        return Mth.lerp(partialTick, this.thrustO, this.thrust);
    }

    // ---------------------------------------------------------------------------------------
    // Dano de caida: segun lo rapido que llega al suelo (los que vuelan bajan despacio y no se hacen dano)
    // ---------------------------------------------------------------------------------------

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (onGround) {
            if (this.fallSpeed > 0.5) {
                // altura desde la que se cae en Minecraft para llegar con esa velocidad
                float equivalent = (float) (this.fallSpeed * this.fallSpeed / 0.16);
                this.causeFallDamage(equivalent, 1.0f, this.damageSources().fall());
                if (equivalent > 3f && !this.level().isClientSide()) {
                    // un golpe fuerte tambien dana el vehiculo
                    this.hurt(this.damageSources().fall(), (equivalent - 3f) * 0.5f);
                }
            }
            this.fallSpeed = 0.0;
            this.resetFallDistance();
        } else {
            this.fallSpeed = y < 0.0 ? -y : 0.0;
            this.resetFallDistance();
        }
    }

    /**
     * Servidor: empuja a los que estan delante y, si la opcion esta activada, los atropella. Lo recorrido se mide
     * desde el tick anterior (con conductor, la posicion llega del cliente entre ticks, no durante el tick).
     */
    private void pushEntities(VehicleDefinition d) {
        double dx = Double.isNaN(this.pushX) ? 0.0 : this.getX() - this.pushX;
        double dz = Double.isNaN(this.pushX) ? 0.0 : this.getZ() - this.pushZ;
        this.pushX = this.getX();
        this.pushZ = this.getZ();
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 4.0) {
            // teletransporte, no un choque
            dx = 0.0;
            dz = 0.0;
            moved = 0.0;
        }
        List<Entity> list = this.level().getEntities(this, this.getBoundingBox().inflate(0.2, -0.01, 0.2),
                EntitySelector.pushableBy(this));
        LivingEntity driver = getControllingPassenger();
        for (Entity e : list) {
            if (e.isPassengerOfSameVehicle(this) || hasPassenger(e)) {
                continue;
            }
            this.push(e);
            if (d.runOver && moved > 0.15 && e instanceof LivingEntity living && e.invulnerableTime == 0) {
                float damage = (float) (moved * 20.0 * 0.6);
                DamageSource source = driver instanceof Player p ? this.damageSources().playerAttack(p)
                        : this.damageSources().generic();
                if (living.hurt(source, damage)) {
                    living.knockback(Math.min(2.0, moved * 3.0), -dx / Math.max(moved, 1e-6), -dz / Math.max(moved, 1e-6));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Interpolacion (como las barcas)
    // ---------------------------------------------------------------------------------------

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = 3;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float) this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float) this.lerpYRot : this.getYRot();
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot,
                    this.lerpXRot);
            this.lerpSteps--;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Cliente: ruedas, volante y humo
    // ---------------------------------------------------------------------------------------

    private void clientTick(@Nullable VehicleDefinition d) {
        this.rolledO = this.rolled;
        double yaw = Math.toRadians(this.getYRot());
        double forward = (this.getX() - this.xo) * -Math.sin(yaw) + (this.getZ() - this.zo) * Math.cos(yaw);
        this.rolled += (float) forward;
        if (Math.abs(this.rolled) > 4096f) {
            // que no crezca sin fin (perderia precision): un salto cada 4 km no se nota
            float shift = Math.copySign(4096f, this.rolled);
            this.rolled -= shift;
            this.rolledO -= shift;
        }
        this.steerShownO = this.steerShown;
        float target = isLocallyDriven() ? this.physics.steer : this.entityData.get(DATA_STEER);
        this.steerShown += (target - this.steerShown) * 0.5f;
        if (d == null) {
            return;
        }
        boolean driven = getControllingPassenger() != null;
        int flags = inputFlags();
        // helices: despacio con el motor en marcha, rapido al acelerar o volar
        float rate = !driven ? 0f : (flags & (FLAG_GAS | FLAG_FLYING)) != 0 ? 1.9f : 0.6f;
        if ((flags & FLAG_TURBO) != 0) {
            rate = 2.4f;
        }
        this.spinRate += (rate - this.spinRate) * (rate > this.spinRate ? 0.15f : 0.05f);
        this.spinO = this.spin;
        this.spin += this.spinRate;
        if (this.spin > (float) (Math.PI * 2000.0)) {
            this.spin -= (float) (Math.PI * 2000.0);
            this.spinO -= (float) (Math.PI * 2000.0);
        }
        // llamas: pequenas en marcha, normales acelerando, grandes con el turbo (con un poco de parpadeo)
        float flame = !driven ? 0f : (flags & FLAG_TURBO) != 0 ? 1.9f : (flags & FLAG_GAS) != 0 ? 1f : 0.45f;
        this.thrustO = this.thrust;
        this.thrust += (flame - this.thrust) * 0.35f;
        if (this.thrust > 0.05f) {
            this.thrust *= 0.94f + this.random.nextFloat() * 0.12f;
        }
        boolean boost = (flags & FLAG_TURBO) != 0;
        if (boost && !this.wasBoosting) {
            this.level().playLocalSound(getX(), getY(), getZ(),
                    BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace("entity.firework_rocket.launch")),
                    SoundSource.NEUTRAL, 1.2f, 0.8f, false);
        }
        this.wasBoosting = boost;
        if (driven) {
            clientEffects.accept(this);
            smoke(d, Math.abs(forward));
            flames(d, boost);
        }
    }

    /** Chispas de fuego de los propulsores con el turbo o volando. */
    private void flames(VehicleDefinition d, boolean boost) {
        if (!boost && !flying()) {
            return;
        }
        if (--this.flameTimer > 0) {
            return;
        }
        this.flameTimer = boost ? 1 : 3;
        float[][] outlets = VehicleGeometry.thrusters(d);
        if (outlets.length == 0) {
            return;
        }
        Matrix4f m = modelToWorld(d, this.getYRot());
        Vector3f v = new Vector3f();
        Vector3f dir = new Vector3f();
        for (int i = 0; i < outlets.length; i++) {
            float[] o = outlets[i];
            VehiclePart part = thrusterPart(d, i);
            m.transformPosition(o[0], o[1], o[2], v);
            m.transformDirection(o[3], o[4], o[5], dir);
            if (dir.lengthSquared() > 1.0e-8f) {
                dir.normalize();
            }
            boolean blue = part != null && (part.flameColor & 0xFF) > ((part.flameColor >> 16) & 0xFF);
            this.level().addParticle(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    getX() + v.x, getY() + v.y, getZ() + v.z, dir.x * 0.15, dir.y * 0.15, dir.z * 0.15);
        }
    }

    @Nullable
    private static VehiclePart thrusterPart(VehicleDefinition d, int index) {
        int n = 0;
        for (VehiclePart p : d.parts) {
            if (p.kind == PartKind.THRUSTER && n++ == index) {
                return p;
            }
        }
        return null;
    }

    private void smoke(VehicleDefinition d, double moved) {
        float[][] outlets = exhausts(d);
        if (outlets.length == 0 || --this.smokeTimer > 0) {
            return;
        }
        this.smokeTimer = moved > 0.05 ? 2 : 6;
        Matrix4f m = modelToWorld(d, this.getYRot());
        Vector3f v = new Vector3f();
        Vector3f dir = new Vector3f();
        for (float[] o : outlets) {
            m.transformPosition(o[0], o[1], o[2], v);
            m.transformDirection(o[3], o[4], o[5], dir);
            if (dir.lengthSquared() > 1.0e-8f) {
                dir.normalize();
            }
            this.level().addParticle(ParticleTypes.SMOKE, getX() + v.x, getY() + v.y, getZ() + v.z,
                    dir.x * 0.05, dir.y * 0.05 + 0.01, dir.z * 0.05);
        }
    }

    /** Distancia rodada, para girar las ruedas al dibujar. */
    public float getRolled(float partialTick) {
        return Mth.lerp(partialTick, this.rolledO, this.rolled);
    }

    /** Volante (de -1 derecha a 1 izquierda) para dibujar. */
    public float getSteerShown(float partialTick) {
        return Mth.lerp(partialTick, this.steerShownO, this.steerShown);
    }

    /** Velocidad aproximada en bloques por tick (para el sonido). */
    public float getSpeedForSound() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------------------------------
    // Guardado
    // ---------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Vehicle", getDefinitionId());
        tag.putBoolean("Lights", lightsOn());
        tag.putFloat("Damage", getDamage());
        if (this.storage != null) {
            for (int i = 0; i < MAX_STORAGE; i++) {
                this.storedItems.set(i, this.storage.getItem(i));
            }
        }
        CompoundTag items = new CompoundTag();
        ContainerHelper.saveAllItems(items, this.storedItems, this.registryAccess());
        tag.put("Storage", items);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setDefinitionId(tag.getString("Vehicle"));
        this.entityData.set(DATA_LIGHTS, tag.getBoolean("Lights"));
        setDamage(tag.getFloat("Damage"));
        if (tag.contains("Storage")) {
            this.storedItems.clear();
            ContainerHelper.loadAllItems(tag.getCompound("Storage"), this.storedItems, this.registryAccess());
            this.storage = null;
        }
    }
}
