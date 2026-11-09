package com.vehiclestudio.entity;

import com.vehiclestudio.data.Colliders;
import com.vehiclestudio.data.RigidSweep;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
    /** Lo pone el cliente: el jugador local (las cajas solidas del cliente solo hacen falta a su alrededor). */
    public static Supplier<Entity> localPlayer = () -> null;
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
    /** Como mucho, entidades a las que el servidor pone cajas alrededor (las mas cercanas). */
    private static final int MAX_ZONES = 24;
    /** Cajas con las que se mira si un jugador se ha quedado dentro de las piezas (para empujarle fuera). */
    private static final int PLAYER_PUSH_BOXES = 24;
    /** Lo lejos que puede quedar el centro de otro vehiculo de lo que toca este (vehiculos muy largos). */
    private static final double OTHER_REACH = 16.0;

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
    /** Cajas solidas para jugadores y mobs (siempre las mismas entidades: el juego las registra al aparecer). */
    private final VehicleCollider[] colliders = new VehicleCollider[Colliders.ENTITY_BOXES];
    /** Trozos del modelo para chocar con los bloques (cambian con la definicion). */
    @Nullable
    private List<float[]> terrainModel;
    /** Las piezas solidas (para las cajas) y todas (para hacer clic), y donde quedan con el giro de ahora. */
    @Nullable
    private Colliders.Body solidBody;
    @Nullable
    private Colliders.Body pickBody;
    @Nullable
    private Colliders.Frame solidFrame;
    @Nullable
    private Colliders.Frame pickFrame;
    private float frameYaw = Float.NaN;
    private float framePitch;
    private float frameRoll;
    /** Para no recibir varias veces el mismo golpe (una explosion toca todas las cajas a la vez). */
    private int lastHurtTick = Integer.MIN_VALUE;
    @Nullable
    private Object lastHurtType;
    @Nullable
    private Entity lastHurtBy;
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
        for (int i = 0; i < this.colliders.length; i++) {
            this.colliders[i] = new VehicleCollider(this);
        }
        assignColliderIds(this.getId());
    }

    // ---------------------------------------------------------------------------------------
    // Cajas solidas (como las partes del dragon del End)
    // ---------------------------------------------------------------------------------------

    /** Las cajas usan los ids siguientes al del vehiculo, igual en el servidor y en los clientes. */
    private void assignColliderIds(int id) {
        for (int i = 0; i < this.colliders.length; i++) {
            if (this.colliders[i] != null) {
                this.colliders[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (this.colliders != null) {
            assignColliderIds(id);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.colliders;
    }

    /** Las cajas de los vehiculos no le frenan: contra otro vehiculo choca con sus piezas (ver obstacles). */
    @Override
    public boolean canCollideWith(Entity other) {
        if (other instanceof VehicleCollider) {
            return false;
        }
        return super.canCollideWith(other);
    }

    private boolean anyColliderActive() {
        for (VehicleCollider c : this.colliders) {
            if (c.isActive()) {
                return true;
            }
        }
        return false;
    }

    /** true en el cliente si el jugador local va sentado en este vehiculo. */
    public boolean hasLocalPassenger() {
        if (!this.level().isClientSide()) {
            return false;
        }
        for (Entity e : getPassengers()) {
            if (e instanceof Player p && p.isLocalPlayer()) {
                return true;
            }
        }
        return false;
    }

    private List<float[]> terrainModel(VehicleDefinition d) {
        if (this.terrainModel == null) {
            this.terrainModel = Colliders.terrainBoxes(d);
        }
        return this.terrainModel;
    }

    /** Las piezas solidas colocadas con el giro de ahora (en el mundo, relativas al vehiculo). */
    private Colliders.Frame solidFrame(VehicleDefinition d) {
        checkFrames();
        if (this.solidFrame == null) {
            if (this.solidBody == null) {
                this.solidBody = Colliders.body(d, true);
            }
            this.solidFrame = Colliders.frame(this.solidBody,
                    VehicleGeometry.modelToWorld(layout(d), this.frameYaw, this.framePitch, this.frameRoll));
        }
        return this.solidFrame;
    }

    /** Todas las piezas (solidas o no) colocadas con el giro de ahora: lo que se puede elegir con el raton. */
    private Colliders.Frame pickFrame(VehicleDefinition d) {
        checkFrames();
        if (this.pickFrame == null) {
            if (this.pickBody == null) {
                this.pickBody = Colliders.body(d, false);
            }
            this.pickFrame = Colliders.frame(this.pickBody,
                    VehicleGeometry.modelToWorld(layout(d), this.frameYaw, this.framePitch, this.frameRoll));
        }
        return this.pickFrame;
    }

    private void checkFrames() {
        float yaw = this.getYRot();
        float pitch = this.getXRot();
        if (yaw != this.frameYaw || pitch != this.framePitch || this.roll != this.frameRoll) {
            this.frameYaw = yaw;
            this.framePitch = pitch;
            this.frameRoll = this.roll;
            this.solidFrame = null;
            this.pickFrame = null;
        }
    }

    /** Coordenadas relativas al vehiculo de una caja del mundo. */
    private double[] relative(AABB b) {
        return new double[]{b.minX - getX(), b.minY - getY(), b.minZ - getZ(), b.maxX - getX(), b.maxY - getY(),
                b.maxZ - getZ()};
    }

    /**
     * Donde puede estar alguien en el proximo tick: su caja, mas side a los lados, down por debajo y up por encima, y
     * mas lo que se mueve por tick (hasta 4 bloques).
     */
    private Colliders.Zone zoneOf(Entity e, double side, double down, double up, int rank) {
        double[] core = relative(e.getBoundingBox());
        Vec3 v = e.getDeltaMovement();
        double h = side + Math.min(4.0, Math.sqrt(v.x * v.x + v.z * v.z));
        double lo = down + Math.min(4.0, Math.max(0.0, -v.y));
        double hi = up + Math.min(4.0, Math.max(0.0, v.y));
        return new Colliders.Zone(new double[]{core[0] - h, core[1] - lo, core[2] - h, core[3] + h, core[4] + hi,
                core[5] + h}, core, rank);
    }

    /** Servidor: quien necesita cajas solidas (los jugadores no: su movimiento lo lleva su cliente). */
    private static boolean needsBoxes(Entity e) {
        return !(e instanceof VehicleCollider) && !(e instanceof CustomVehicle) && !(e instanceof Player)
                && !e.isPassenger() && !e.isSpectator() && !e.noPhysics && e.isAlive();
    }

    /** Las zonas en las que hacen falta cajas solidas en este lado. */
    private List<Colliders.Zone> zones(Colliders.Frame f) {
        List<Colliders.Zone> out = new ArrayList<>();
        double[] b = f.bounds();
        if (b == null) {
            return out;
        }
        if (this.level().isClientSide()) {
            // el cliente solo mueve a su jugador (o lo que este montando, como un caballo o una barca): las cajas, a
            // su alrededor
            Entity me = localPlayer.get();
            if (me != null && me.isPassenger()) {
                Entity root = me.getRootVehicle();
                me = root instanceof CustomVehicle ? null : root;
            }
            if (me != null && !me.isSpectator() && !me.noPhysics) {
                out.add(zoneOf(me, 0.35, 0.35, 0.65, 0));
            }
            return out;
        }
        AABB area = new AABB(getX() + b[0], getY() + b[1], getZ() + b[2], getX() + b[3], getY() + b[4], getZ() + b[5])
                .inflate(3.0);
        List<Entity> found = this.level().getEntities(this, area, CustomVehicle::needsBoxes);
        if (found.size() > MAX_ZONES) {
            // con muchos alrededor, los mas cercanos
            double cx = getX() + (b[0] + b[3]) / 2;
            double cy = getY() + (b[1] + b[4]) / 2;
            double cz = getZ() + (b[2] + b[5]) / 2;
            found = new ArrayList<>(found);
            found.sort((p, q) -> Double.compare(p.distanceToSqr(cx, cy, cz), q.distanceToSqr(cx, cy, cz)));
            found = found.subList(0, MAX_ZONES);
        }
        for (Entity e : found) {
            out.add(zoneOf(e, 0.35, 0.35, 0.65, e instanceof LivingEntity ? 0 : 1));
        }
        return out;
    }

    /** Trozos para chocar con los bloques, en el mundo relativo al vehiculo, con un giro dado (y la caja propia). */
    private double[][] terrainBoxes(VehicleDefinition d, float yaw, float pitch, float roll) {
        Matrix4f m = VehicleGeometry.modelToWorld(layout(d), yaw, pitch, roll);
        double[][] parts = Colliders.toWorld(terrainModel(d), m);
        // la caja de la entidad tambien: el servidor comprueba con ella que el vehiculo no se meta en los bloques
        AABB own = this.getBoundingBox().move(-this.getX(), -this.getY(), -this.getZ());
        double[][] out = Arrays.copyOf(parts, parts.length + 1);
        out[parts.length] = new double[]{own.minX, own.minY, own.minZ, own.maxX, own.maxY, own.maxZ};
        return out;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        for (VehicleCollider c : this.colliders) {
            c.deactivate();
        }
    }

    /**
     * Coloca las cajas solidas alrededor de quien las puede tocar: en el cliente, cajas que encierran las piezas para
     * su jugador; en el servidor, cajas que caben dentro de las piezas para los mobs, objetos y flechas (asi nunca frena
     * a un jugador en un sitio en el que su cliente le deja estar).
     */
    private void updateColliders(@Nullable VehicleDefinition d) {
        List<double[]> boxes = List.of();
        if (d != null && !this.isRemoved()) {
            Colliders.Frame f = solidFrame(d);
            if (f.size() > 0) {
                List<Colliders.Zone> zones = zones(f);
                if (!zones.isEmpty()) {
                    boxes = this.level().isClientSide()
                            ? Colliders.near(f, zones, this.colliders.length, Colliders.TOLERANCE)
                            : Colliders.inner(f, zones, this.colliders.length, Colliders.INNER_TOLERANCE);
                }
            }
        }
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        for (int i = 0; i < this.colliders.length; i++) {
            if (i < boxes.size()) {
                double[] b = boxes.get(i);
                this.colliders[i].place(x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5]);
            } else {
                this.colliders[i].deactivate();
            }
        }
    }

    /** Bloques y entidades solidas de una zona, como cajas relativas al vehiculo. */
    private List<double[]> obstacles(double[] area) {
        AABB zone = new AABB(this.getX() + area[0], this.getY() + area[1], this.getZ() + area[2],
                this.getX() + area[3], this.getY() + area[4], this.getZ() + area[5]);
        List<double[]> out = new ArrayList<>();
        for (VoxelShape shape : this.level().getBlockCollisions(this, zone)) {
            addShape(out, shape);
        }
        for (VoxelShape shape : this.level().getEntityCollisions(this, zone)) {
            addShape(out, shape);
        }
        // otros vehiculos: con todas sus piezas
        for (CustomVehicle o : this.level().getEntitiesOfClass(CustomVehicle.class, zone.inflate(OTHER_REACH),
                o -> o != this && !o.isRemoved())) {
            VehicleDefinition od = o.def();
            if (od == null) {
                continue;
            }
            double ox = o.getX() - this.getX();
            double oy = o.getY() - this.getY();
            double oz = o.getZ() - this.getZ();
            for (double[] b : o.terrainBoxes(od, o.getYRot(), o.getXRot(), o.roll)) {
                double[] r = {b[0] + ox, b[1] + oy, b[2] + oz, b[3] + ox, b[4] + oy, b[5] + oz};
                if (r[0] < area[3] && r[3] > area[0] && r[1] < area[4] && r[4] > area[1] && r[2] < area[5]
                        && r[5] > area[2]) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    private void addShape(List<double[]> out, VoxelShape shape) {
        for (AABB a : shape.toAabbs()) {
            out.add(new double[]{a.minX - this.getX(), a.minY - this.getY(), a.minZ - this.getZ(),
                    a.maxX - this.getX(), a.maxY - this.getY(), a.maxZ - this.getZ()});
        }
    }

    private static double[] grow(double[] u, double dx, double dy, double dz, double extra) {
        return new double[]{u[0] + Math.min(0, dx) - extra, u[1] + Math.min(0, dy) - extra, u[2] + Math.min(0, dz) - extra,
                u[3] + Math.max(0, dx) + extra, u[4] + Math.max(0, dy) + extra, u[5] + Math.max(0, dz) + extra};
    }

    /**
     * Se mueve con todas sus piezas: ninguna se mete en los bloques ni en otras entidades solidas, sube escalones y
     * se apoya en el suelo con la pieza que lo toque. Devuelve el movimiento hecho.
     */
    private Vec3 moveSolid(VehicleDefinition d, Vec3 wanted) {
        double[][] boxes = terrainBoxes(d, this.getYRot(), this.getXRot(), this.roll);
        double step = this.onGround() ? Math.max(0f, d.stepHeight) : 0.0;
        double[] u = RigidSweep.union(boxes);
        double[] area = grow(u, wanted.x, wanted.y, wanted.z, 0.01);
        area[4] += step;
        List<double[]> obstacles = obstacles(area);
        RigidSweep.Result r = RigidSweep.move(boxes, obstacles, wanted.x, wanted.y, wanted.z, step, this.onGround());
        boolean wasOnGround = this.onGround();
        this.setPos(this.getX() + r.dx(), this.getY() + r.dy(), this.getZ() + r.dz());
        this.horizontalCollision = r.horizontal();
        this.verticalCollision = r.vertical();
        this.verticalCollisionBelow = r.onGround();
        this.setOnGround(r.onGround());
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(Math.abs(r.dx() - wanted.x) > 1.0e-6 ? 0 : v.x,
                r.vertical() ? 0 : v.y, Math.abs(r.dz() - wanted.z) > 1.0e-6 ? 0 : v.z);
        if (r.onGround() && !wasOnGround && wanted.y < 0 && !this.level().isClientSide()) {
            landed(-wanted.y);
        }
        return new Vec3(r.dx(), r.dy(), r.dz());
    }

    /** Al tocar el suelo (lo mueve el servidor): dano de caida segun lo rapido que bajaba. */
    private void landed(double speed) {
        if (speed > 0.5) {
            float equivalent = (float) (speed * speed / 0.16);
            this.causeFallDamage(equivalent, 1.0f, this.damageSources().fall());
            if (equivalent > 3f) {
                this.hurt(this.damageSources().fall(), (equivalent - 3f) * 0.5f);
            }
        }
        this.resetFallDistance();
    }

    /** true si girar (o inclinarse) a esa posicion metiera alguna pieza en un bloque en el que no estaba. */
    private boolean rotationBlocked(VehicleDefinition d, float yaw, float pitch, float roll) {
        double[][] now = terrainBoxes(d, this.getYRot(), this.getXRot(), this.roll);
        double[][] next = terrainBoxes(d, yaw, pitch, roll);
        double[] u = RigidSweep.union(now);
        double[] v = RigidSweep.union(next);
        double[] area = {Math.min(u[0], v[0]) - 0.01, Math.min(u[1], v[1]) - 0.01, Math.min(u[2], v[2]) - 0.01,
                Math.max(u[3], v[3]) + 0.01, Math.max(u[4], v[4]) + 0.01, Math.max(u[5], v[5]) + 0.01};
        List<double[]> obstacles = obstacles(area);
        return RigidSweep.overlaps(next, obstacles, 1.0e-4) && !RigidSweep.overlaps(now, obstacles, 1.0e-4);
    }

    /** Coloca un vehiculo en el mundo mirando hacia yaw. */
    @Nullable
    public static CustomVehicle spawn(ServerLevel level, VehicleDefinition def, double x, double y, double z, float yaw,
                                      boolean checkSpace) {
        CustomVehicle v = new CustomVehicle(ModRegistry.VEHICLE.get(), level);
        v.setDefinitionId(def.id);
        v.moveTo(x, y, z, yaw, 0f);
        v.refreshDimensions();
        if (checkSpace) {
            // que ninguna pieza quede metida en un bloque: si no cabe, un poco mas arriba
            VehicleDefinition d = v.def();
            for (int up = 0; up <= 3 && d != null; up++) {
                v.setPos(x, y + up, z);
                double[][] boxes = v.terrainBoxes(d, yaw, 0f, 0f);
                double[] area = RigidSweep.union(boxes);
                if (area == null || !RigidSweep.overlaps(boxes, v.obstacles(area), 1.0e-4)) {
                    break;
                }
                if (up == 3) {
                    v.setPos(x, y, z);
                }
            }
        }
        v.updateColliders(v.def());
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
            this.terrainModel = null;
            this.solidBody = null;
            this.pickBody = null;
            this.solidFrame = null;
            this.pickFrame = null;
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
                    // ni dentro de un bloque ni dentro de las piezas del propio vehiculo
                    if (DismountHelper.canDismountTo(this.level(), spot, passenger, pose)
                            && !touchesSolid(d, passenger.getDimensions(pose).makeBoundingBox(spot))) {
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
        // el mismo golpe en el mismo tick (una explosion o un rayo tocan todas sus cajas a la vez) cuenta una vez
        if (this.tickCount == this.lastHurtTick && source.type() == this.lastHurtType
                && source.getDirectEntity() == this.lastHurtBy) {
            return false;
        }
        this.lastHurtTick = this.tickCount;
        this.lastHurtType = source.type();
        this.lastHurtBy = source.getDirectEntity();
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
        if (this.level().isClientSide()) {
            // En el cliente se elige con un rayo contra las piezas tal cual (VehiclePick); la caja de la entidad solo
            // si no hay piezas (sin definicion). Quien va dentro apunta a traves, como desde una barca.
            VehicleDefinition d = def();
            return (d == null || pickFrame(d).size() == 0) && !hasLocalPassenger();
        }
        // en el servidor (flechas y demas), sus cajas si las tiene puestas; si no, la de la entidad
        return !anyColliderActive();
    }

    /** Cliente: donde (de 0 a 1) entra el segmento de from a to en alguna pieza del vehiculo, o -1 si no toca ninguna. */
    public double rayHit(Vec3 from, Vec3 to) {
        VehicleDefinition d = def();
        if (d == null || this.isRemoved()) {
            return -1;
        }
        double x = getX();
        double y = getY();
        double z = getZ();
        return Colliders.raycast(pickFrame(d), new double[]{from.x - x, from.y - y, from.z - z},
                new double[]{to.x - x, to.y - y, to.z - z});
    }

    /** Lo lejos que queda un punto de la pieza mas cercana (para ver si un jugador llega a tocar el vehiculo). */
    public double reach(Vec3 p) {
        VehicleDefinition d = def();
        Colliders.Frame f = d == null ? null : pickFrame(d);
        if (f == null || f.size() == 0) {
            return Math.sqrt(this.getBoundingBox().distanceToSqr(p));
        }
        return Colliders.distance(f, new double[]{p.x - getX(), p.y - getY(), p.z - getZ()});
    }

    /** true si esa caja (del mundo) se mete en alguna pieza solida del vehiculo. */
    private boolean touchesSolid(VehicleDefinition d, AABB box) {
        Colliders.Frame f = solidFrame(d);
        return f.size() > 0 && Colliders.touches(f, relative(box), Colliders.TOLERANCE);
    }

    @Override
    public boolean canBeCollidedWith() {
        // lo solido son sus cajas (asi se puede entrar en un vehiculo hueco, como la caja de una camioneta)
        return false;
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
        updateColliders(d);
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
        // el morro sube o baja poco a poco (xRot viaja solo al servidor y a los demas)
        float newPitch = this.getXRot() + (pitchTarget - this.getXRot()) * 0.2f;
        if (d != null && (deltaYaw != 0f || newPitch != this.getXRot())
                && rotationBlocked(d, this.getYRot() + deltaYaw, newPitch, this.roll)) {
            // girar metera alguna pieza en una pared: no gira (como un coche pegado a un muro)
            deltaYaw = 0f;
            newPitch = this.getXRot();
        }
        this.deltaRotation = deltaYaw;
        this.setYRot(this.getYRot() + this.deltaRotation);
        this.setXRot(newPitch);
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
        if (d != null) {
            moveSolid(d, this.getDeltaMovement());
        } else {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
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
     * Lo que hay que mover una caja (en horizontal) para sacarla de unas cajas solidas, o null si no esta dentro de
     * ninguna. Sale por el lado mas corto, alejandose del centro de la caja en la que esta.
     */
    @Nullable
    private static Vec3 pushOut(AABB box, List<AABB> solids) {
        double bestX = 0;
        double bestZ = 0;
        boolean inside = false;
        for (AABB o : solids) {
            if (!box.intersects(o)) {
                continue;
            }
            double px = Math.min(box.maxX - o.minX, o.maxX - box.minX);
            double pz = Math.min(box.maxZ - o.minZ, o.maxZ - box.minZ);
            double cx = (box.minX + box.maxX) / 2 - (o.minX + o.maxX) / 2;
            double cz = (box.minZ + box.maxZ) / 2 - (o.minZ + o.maxZ) / 2;
            double mx = 0;
            double mz = 0;
            if (px < pz) {
                mx = Math.copySign(px + 0.01, cx);
            } else {
                mz = Math.copySign(pz + 0.01, cz);
            }
            if (!inside || mx * mx + mz * mz > bestX * bestX + bestZ * bestZ) {
                bestX = mx;
                bestZ = mz;
            }
            inside = true;
        }
        if (!inside) {
            return null;
        }
        // como mucho medio bloque por tick (si no, salen disparados)
        double len = Math.sqrt(bestX * bestX + bestZ * bestZ);
        double k = len > 0.5 ? 0.5 / len : 1.0;
        return new Vec3(bestX * k, 0.0, bestZ * k);
    }

    /** Cajas relativas al vehiculo pasadas al mundo. */
    private List<AABB> absolute(List<double[]> boxes) {
        List<AABB> out = new ArrayList<>(boxes.size());
        for (double[] b : boxes) {
            out.add(new AABB(getX() + b[0], getY() + b[1], getZ() + b[2], getX() + b[3], getY() + b[4], getZ() + b[5]));
        }
        return out;
    }

    /**
     * Servidor: saca hacia fuera a quien se meta en sus piezas (porque el vehiculo se le echa encima) y, si la opcion
     * esta activada, lo atropella. Lo recorrido se mide desde el tick anterior (con conductor, la posicion llega del
     * cliente entre ticks, no durante el tick). Se usan cajas que caben dentro de las piezas: solo empuja a quien de
     * verdad esta dentro, no a quien esta pegado a una pared en diagonal.
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
        Colliders.Frame f = solidFrame(d);
        double[] b = f.bounds();
        if (b == null) {
            return;
        }
        // las cajas que ya estan puestas (alrededor de mobs y objetos)
        List<AABB> placed = new ArrayList<>();
        for (VehicleCollider c : this.colliders) {
            if (c.isActive()) {
                placed.add(c.getBoundingBox());
            }
        }
        AABB area = new AABB(getX() + b[0], getY() + b[1], getZ() + b[2], getX() + b[3], getY() + b[4], getZ() + b[5])
                .inflate(0.05);
        List<Entity> list = this.level().getEntities(this, area,
                e -> !(e instanceof VehicleCollider) && e.isPushable() || e instanceof Player);
        LivingEntity driver = getControllingPassenger();
        for (Entity e : list) {
            if (e.isPassengerOfSameVehicle(this) || hasPassenger(e) || e.isSpectator() || e.noPhysics) {
                continue;
            }
            List<AABB> solids = placed;
            if (e instanceof Player) {
                // a los jugadores no se les ponen cajas en el servidor: las suyas, solo para esto
                double[] own = relative(e.getBoundingBox());
                solids = absolute(Colliders.inner(f, List.of(new Colliders.Zone(own, own, 0)), PLAYER_PUSH_BOXES,
                        Colliders.INNER_TOLERANCE));
            }
            Vec3 out = pushOut(e.getBoundingBox(), solids);
            if (out == null) {
                continue;
            }
            if (e instanceof Player player) {
                // el movimiento de los jugadores lo lleva su cliente: se le manda el empujon
                player.push(out.x, 0.0, out.z);
                player.hurtMarked = true;
            } else {
                e.move(MoverType.SHULKER, out);
            }
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
