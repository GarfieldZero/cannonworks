package com.cannonworks.entity;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Trajectory;
import com.cannonworks.payload.CannonPayload;
import com.cannonworks.payload.FireMode;
import com.cannonworks.payload.PayloadDelivery;
import com.cannonworks.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * 炮弹的权威飞行管理器。
 *
 * <p><b>核心设计：飞行状态和实体完全解耦。</b>
 * 弹道存在服务端内存里（{@link Flight}），每个服务端 tick 推进一次；
 * {@link CannonShellEntity} 只是一个“可抛弃的视觉载体”——区块卸载把它带走了，
 * 下一 tick 管理器在当前位置重新生成一个，飞行照常继续。</p>
 *
 * <p>这样解决了两个真实 bug：</p>
 * <ol>
 *   <li>0 人服务器上实体根本不 tick（vanilla 盔甲架都不会下落）；</li>
 *   <li>炮弹飞出已加载区块后，实体被区块卸载带走，整发炮弹冻在半空——玩家飞过去还会看到它挂在原地。</li>
 * </ol>
 *
 * <p>所有方法只在服务端主线程调用。</p>
 */
public final class ShellManager {

    private static final Map<UUID, Flight> FLIGHTS = new LinkedHashMap<>();

    /** 半径 2 → 中心区块 tick 等级 31（entity ticking），周围一圈只加载不 tick。 */
    private static final int TICKET_RADIUS = 2;
    private static final int TICKET_TIMEOUT_TICKS = 60;

    private static final TicketType<ChunkPos> SHELL_TICKET =
            TicketType.create("cannonworks:shell", Comparator.comparingLong(ChunkPos::toLong), TICKET_TIMEOUT_TICKS);

    /** 需要临时强制加载的落点（TNT 链式反应用）。 */
    private static final List<ForcedArea> FORCED_AREAS = new ArrayList<>();

    private ShellManager() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShellManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ShellManager::onServerStopped);
    }

    /** 发射一发炮弹，返回飞行 id；之后完全由本管理器驱动，调用方不需要再管实体。 */
    public static UUID launch(ServerLevel level, Trajectory trajectory, @Nullable UUID owner, CannonPayload payload,
                              FireMode mode) {
        Flight flight = new Flight(UUID.randomUUID(), level, trajectory, owner, payload, mode);
        FLIGHTS.put(flight.id, flight);
        spawnEntity(flight, trajectory.sampleAtTick(0));
        return flight.id;
    }

    public static boolean hasFlight(@Nullable UUID id) {
        return id != null && FLIGHTS.containsKey(id);
    }

    /** 供实体在区块重载后认领自己；已有存活实体时，多余的那个会被丢弃。 */
    public static void adopt(UUID flightId, CannonShellEntity entity) {
        Flight flight = FLIGHTS.get(flightId);
        if (flight == null) {
            return;
        }
        if (flight.entity == null || flight.entity.isRemoved() || flight.entity == entity) {
            flight.entity = entity;
        } else {
            // 同一发炮弹出现了两个载体（旧的那个已复活），丢掉后来的
            entity.discard();
        }
    }

    public static List<Flight> activeFlights() {
        return List.copyOf(FLIGHTS.values());
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        releaseExpiredForcedAreas(event.getServer());
        if (FLIGHTS.isEmpty()) {
            return;
        }
        for (Flight flight : List.copyOf(FLIGHTS.values())) {
            if (!FLIGHTS.containsKey(flight.id)) {
                continue; // 这一 tick 里已经被别的路径结束掉了
            }
            try {
                advance(flight);
            } catch (Exception exception) {
                CannonWorks.LOGGER.error("Cannon flight {} failed at {}", flight.id, flight.trajectory.sampleAtTick(flight.age), exception);
                end(flight);
            }
        }
    }

    private static void advance(Flight flight) {
        Trajectory trajectory = flight.trajectory;
        flight.age++;

        if (flight.age >= trajectory.flightTicks()) {
            impact(flight, trajectory.end(), "arrived");
            return;
        }

        Vec3 from = trajectory.sampleAtTick(flight.age - 1);
        Vec3 to = trajectory.sampleAtTick(flight.age);
        keepChunkLoaded(flight.level, to);

        // 载体没了（区块卸载 / 被杀）就在当前位置补一个，飞行本身不受影响
        if (flight.entity == null || flight.entity.isRemoved()) {
            spawnEntity(flight, to);
        }

        // 关键：Level.clip 读未加载区块会当成空气，炮弹会直接穿墙。
        // 先把这一段的两个端点所在区块强制加载出来，再判定碰撞。
        ensureChunkLoaded(flight.level, from);
        ensureChunkLoaded(flight.level, to);

        HitResult hit = flight.level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, flight.entity));
        if (hit.getType() != HitResult.Type.MISS) {
            impact(flight, hit.getLocation(), "hit " + hit.getType());
            return;
        }

        if (flight.entity != null && !flight.entity.isRemoved()) {
            flight.entity.setPos(to.x, to.y, to.z);
            flight.entity.setDeltaMovement(to.subtract(from));
        }
    }

    private static void ensureChunkLoaded(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
    }

    private static void spawnEntity(Flight flight, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        if (!flight.level.hasChunkAt(pos)) {
            return; // 区块还没加载好，下一 tick 再试；飞行逻辑不依赖它
        }
        CannonShellEntity shell = new CannonShellEntity(ModEntities.CANNON_SHELL.get(), flight.level);
        shell.setFlightId(flight.id);
        shell.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        flight.level.addFreshEntity(shell);
        flight.entity = shell;
    }

    /**
     * 让落点区块在接下来 200 tick 内保持 entity-ticking。
     *
     * <p>用 {@code setChunkForced} 而不是自定义 ticket：实测只有 forced 状态能保证区块里的
     * 实体真正 tick（普通 ticket 在 0 人服务器上不 tick，TNT 会永远不爆）。到期自动解禁。</p>
     */
    public static void keepAreaTicking(ServerLevel level, Vec3 at) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(at));
        level.setChunkForced(chunk.x, chunk.z, true);
        FORCED_AREAS.add(new ForcedArea(level, chunk, level.getGameTime() + 200));
    }

    private static void releaseExpiredForcedAreas(net.minecraft.server.MinecraftServer server) {
        if (FORCED_AREAS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        FORCED_AREAS.removeIf(area -> {
            if (area.until() > now) {
                return false;
            }
            area.level().setChunkForced(area.chunk().x, area.chunk().z, false);
            return true;
        });
    }

    private record ForcedArea(ServerLevel level, ChunkPos chunk, long until) {
    }

    private static void keepChunkLoaded(ServerLevel level, Vec3 at) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(at));
        level.getChunkSource().addRegionTicket(SHELL_TICKET, chunk, TICKET_RADIUS, chunk);
    }

    private static void impact(Flight flight, Vec3 at, String reason) {
        Vec3 incoming = flight.trajectory.sampleAtTick(flight.age)
                .subtract(flight.trajectory.sampleAtTick(Math.max(0, flight.age - 1)));

        ServerPlayer owner = flight.owner != null
                ? flight.level.getServer().getPlayerList().getPlayer(flight.owner)
                : null;
        String summary = PayloadDelivery.onImpact(flight.level, at, incoming, flight.payload, flight.mode, owner);

        flight.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        flight.level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.7f);
        CannonWorks.LOGGER.info("Cannon shell impact at {} ({}) after {} ticks, range {} blocks, payload [{}]: {}",
                String.format("%.1f/%.1f/%.1f", at.x, at.y, at.z), reason, flight.age,
                String.format("%.1f", flight.trajectory.range()), flight.payload.describe(), summary);

        notifyOwner(owner, at, reason, summary);
        end(flight);
    }

    private static void notifyOwner(@Nullable ServerPlayer player, Vec3 at, String reason, String summary) {
        if (player == null) {
            return;
        }
        String where = String.format("%d, %d, %d",
                (int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        boolean intercepted = reason.startsWith("hit");
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                (intercepted ? "炮弹被地形拦截于 " + where : "炮弹已抵达 " + where) + "：" + summary));
    }

    private static void end(Flight flight) {
        if (flight.entity != null && !flight.entity.isRemoved()) {
            flight.entity.discard();
        }
        FLIGHTS.remove(flight.id);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        for (ForcedArea area : FORCED_AREAS) {
            area.level().setChunkForced(area.chunk().x, area.chunk().z, false);
        }
        FORCED_AREAS.clear();
        for (Flight flight : FLIGHTS.values()) {
            if (flight.entity != null && !flight.entity.isRemoved()) {
                flight.entity.discard();
            }
        }
        FLIGHTS.clear();
    }

    /** 一发在飞的炮弹。除了 manager 自己，外部只读。 */
    public static final class Flight {
        private final UUID id;
        private final ServerLevel level;
        private final Trajectory trajectory;
        private final UUID owner;
        private final CannonPayload payload;
        private final FireMode mode;
        private int age;
        private CannonShellEntity entity;

        private Flight(UUID id, ServerLevel level, Trajectory trajectory, UUID owner, CannonPayload payload,
                        FireMode mode) {
            this.id = id;
            this.level = level;
            this.trajectory = trajectory;
            this.owner = owner;
            this.payload = payload;
            this.mode = mode;
        }

        public UUID id() {
            return id;
        }

        public int age() {
            return age;
        }

        public Trajectory trajectory() {
            return trajectory;
        }

        public CannonPayload payload() {
            return payload;
        }

        public boolean hasEntity() {
            return entity != null && !entity.isRemoved();
        }

        public Vec3 position() {
            return trajectory.sampleAtTick(age);
        }
    }
}
