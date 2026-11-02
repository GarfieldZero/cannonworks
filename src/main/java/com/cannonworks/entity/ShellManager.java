package com.cannonworks.entity;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.ArmorPiercingRules;
import com.cannonworks.ballistics.Trajectory;
import com.cannonworks.config.CannonConfig;
import com.cannonworks.impact.ImpactEffects;
import com.cannonworks.impact.TunnelDigger;
import com.cannonworks.network.CannonNetwork;
import com.cannonworks.network.XaeroMarkerPacket;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.PacketDistributor;

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
 * <p><b>穿甲模式</b>在 v0.1.1 加进来：初始飞行仍然用参数化弹道；
 * 一旦撞到能钻的方块就沿线段挖隧道继续飞，撞到基岩则用“速度 + 重力”积分的
 * 反弹段打水漂。这样下界的地狱岩山体和基岩顶不再是一发就结束的墙。</p>
 *
 * <p>所有方法只在服务端主线程调用。</p>
 */
public final class ShellManager {

    private static final Map<UUID, Flight> FLIGHTS = new LinkedHashMap<>();

    /** 半径 2 → 中心区块 tick 等级 31（entity ticking），周围一圈只加载不 tick。 */
    private static final int TICKET_RADIUS = 2;
    private static final int TICKET_TIMEOUT_TICKS = 60;

    /** 穿甲弹单 tick 最多处理几次方块命中，防止在方块边界上死循环。 */
    private static final int MAX_COLLISION_STEPS_PER_TICK = 8;

    /** 反弹段最多持续多少 tick：够打水漂，又不会在两片基岩之间永动。 */
    private static final int MAX_BOUNCE_TICKS = 200;

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
        Vec3 at = trajectory.sampleAtTick(0);
        Vec3 next = trajectory.sampleAtTick(1);
        spawnEntity(flight, at, next.subtract(at));

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
                CannonWorks.LOGGER.error("Cannon flight {} failed at {}", flight.id, flight.position(), exception);
                end(flight);
            }
        }
    }

    private static void advance(Flight flight) {
        flight.age++;
        Vec3 from = flight.position;
        Vec3 to;
        Vec3 velocity;

        if (flight.bounceVelocity != null) {
            // 反弹段：简单重力积分，和参数化弹道分开——参数化曲线只能“先升后降”，
            // 撞天花板后需要往下掉，必须换成真正的速度积分。
            flight.bounceTicks++;
            if (flight.bounceTicks > MAX_BOUNCE_TICKS) {
                impact(flight, from, "bounce timeout");
                return;
            }
            velocity = flight.bounceVelocity.add(0.0, -ArmorPiercingRules.GRAVITY_PER_TICK, 0.0);
            flight.bounceVelocity = velocity;
            to = from.add(velocity);
        } else {
            if (flight.age >= flight.trajectory.flightTicks()) {
                impact(flight, flight.trajectory.end(), "arrived");
                return;
            }
            to = flight.trajectory.sampleAtTick(flight.age);
            velocity = to.subtract(from);
        }

        keepChunkLoaded(flight.level, to);

        // 出膛立刻标一个，之后每 5 秒补一个（客户端只保留最近 3 个）
        if (flight.age == 1 || flight.age % CannonConfig.TRAIL_INTERVAL_TICKS.get() == 0) {
            boolean sent = sendTrailPoint(flight, to);
            if (sent && flight.age == 1) {
                CannonWorks.LOGGER.info("Xaero trail started for flight {} at {}", flight.id, to);
            }
        }

        // 载体没了（区块卸载 / 被杀）就在当前位置补一个，飞行本身不受影响
        Vec3 direction = to.subtract(from);
        if (flight.entity == null || flight.entity.isRemoved()) {
            spawnEntity(flight, to, direction);
        } else {
            flight.entity.setFlightDirection(direction);
        }

        // 关键：Level.clip 读未加载区块会当成空气，炮弹会直接穿墙。
        // 先把这一段的两个端点所在区块强制加载出来，再判定碰撞。
        ensureChunkLoaded(flight.level, from);
        ensureChunkLoaded(flight.level, to);

        if (handleCollisions(flight, from, to, velocity)) {
            return;
        }

        flight.position = to;
        moveEntity(flight, to, direction);
    }

    /**
     * 处理这一 tick 线段上的碰撞。
     *
     * @return true 表示这一 tick 已经结束（命中/反弹/预算用完），调用方不要再把位置设成 to
     */
    private static boolean handleCollisions(Flight flight, Vec3 from, Vec3 to, Vec3 velocity) {
        if (flight.mode != FireMode.PIERCING) {
            HitResult hit = clip(flight, from, to);
            if (hit.getType() != HitResult.Type.MISS) {
                impact(flight, hit.getLocation(), "hit " + hit.getType());
                return true;
            }
            return false;
        }

        Vec3 segment = to.subtract(from);
        if (segment.lengthSqr() < 1.0E-8) {
            return false;
        }
        Vec3 step = segment.normalize();
        Vec3 cursor = from;
        int steps = 0;
        int drilled = 0;
        int budget = CannonConfig.ARMOR_PIERCING_BLOCKS_PER_TICK.get();

        while (steps < MAX_COLLISION_STEPS_PER_TICK) {
            HitResult hit = clip(flight, cursor, to);
            if (hit.getType() == HitResult.Type.MISS) {
                return false;
            }
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = flight.level.getBlockState(hitPos);
            float hardness = state.getDestroySpeed(flight.level, hitPos);

            if (canPierce(state, hardness)) {
                int remaining = budget - drilled;
                if (remaining <= 0) {
                    flight.position = cursor;
                    moveEntity(flight, cursor, segment);
                    return true;
                }
                int broken = TunnelDigger.digTunnel(flight.level, hitPos,
                        CannonConfig.ARMOR_PIERCING_TUNNEL_RADIUS.get(),
                        CannonConfig.ARMOR_PIERCING_MAX_HARDNESS.get(),
                        remaining,
                        CannonConfig.ARMOR_PIERCING_BREAK_BLOCK_ENTITIES.get());
                if (broken <= 0) {
                    // 配置或预算导致一块都没挖掉：把它当硬墙，否则 clip 会在原地无限循环
                    if (canBounce(state) && startBounce(flight, blockHit, velocity)) {
                        return true;
                    }
                    impact(flight, blockHit.getLocation(), "hit " + hit.getType());
                    return true;
                }
                drilled += broken;
                steps++;
                // 往线段方向推一点点，避免 clip 还停在刚挖掉的方块表面上
                cursor = blockHit.getLocation().add(step.scale(0.02));
                if (drilled >= budget) {
                    flight.position = cursor;
                    moveEntity(flight, cursor, segment);
                    return true;
                }
                continue;
            }

            if (canBounce(state) && startBounce(flight, blockHit, velocity)) {
                return true;
            }
            impact(flight, blockHit.getLocation(), "hit " + hit.getType());
            return true;
        }

        // 本 tick 命中次数用完：停在最后一个安全点，下一 tick 接着处理
        flight.position = cursor;
        moveEntity(flight, cursor, segment);
        return true;
    }

    /** 普通方块看硬度；方块实体被主人点名要炸掉，所以默认也按可穿透处理。 */
    private static boolean canPierce(BlockState state, float hardness) {
        if (state.hasBlockEntity() && !CannonConfig.ARMOR_PIERCING_BREAK_BLOCK_ENTITIES.get()) {
            return false;
        }
        return ArmorPiercingRules.canPierce(hardness, CannonConfig.ARMOR_PIERCING_MAX_HARDNESS.get());
    }

    /** 只有基岩会跳弹；屏障/命令方块这类隐形硬墙直接停，不弹。 */
    private static boolean canBounce(BlockState state) {
        return state.is(Blocks.BEDROCK) && CannonConfig.ARMOR_PIERCING_BOUNCE.get();
    }

    /**
     * 撞基岩后开始一段反弹。
     *
     * @return false 表示次数/速度不满足，调用方应该直接结算
     */
    private static boolean startBounce(Flight flight, BlockHitResult hit, Vec3 velocity) {
        int maxBounces = CannonConfig.ARMOR_PIERCING_MAX_BOUNCES.get();
        if (flight.bounces >= maxBounces) {
            return false;
        }

        Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(),
                hit.getDirection().getStepZ());
        ArmorPiercingRules.Velocity bounced = ArmorPiercingRules.reflect(
                new ArmorPiercingRules.Velocity(velocity.x, velocity.y, velocity.z),
                new ArmorPiercingRules.Velocity(normal.x, normal.y, normal.z),
                CannonConfig.ARMOR_PIERCING_RESTITUTION.get(),
                CannonConfig.ARMOR_PIERCING_FRICTION.get());

        if (ArmorPiercingRules.shouldStop(flight.bounces, maxBounces, bounced.length(),
                CannonConfig.ARMOR_PIERCING_MIN_BOUNCE_SPEED.get())) {
            return false;
        }

        flight.bounces++;
        flight.bounceTicks = 0;
        flight.bounceVelocity = new Vec3(bounced.x(), bounced.y(), bounced.z());
        // 沿法线挪出方块表面，否则下一 tick 一开始就还贴在基岩里
        flight.position = hit.getLocation().add(normal.scale(0.05));

        flight.level.playSound(null, flight.position.x, flight.position.y, flight.position.z,
                SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.4f, 0.55f);
        flight.level.sendParticles(ParticleTypes.CRIT,
                flight.position.x, flight.position.y, flight.position.z, 18, 0.35, 0.35, 0.35, 0.12);
        flight.level.sendParticles(ParticleTypes.CLOUD,
                flight.position.x, flight.position.y, flight.position.z, 10, 0.25, 0.25, 0.25, 0.06);
        moveEntity(flight, flight.position, flight.bounceVelocity);
        return true;
    }

    private static HitResult clip(Flight flight, Vec3 from, Vec3 to) {
        return flight.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, flight.entity));
    }

    private static void moveEntity(Flight flight, Vec3 at, Vec3 direction) {
        if (flight.entity == null || flight.entity.isRemoved()) {
            spawnEntity(flight, at, direction);
            return;
        }
        flight.entity.setFlightDirection(direction);
        flight.entity.setPos(at.x, at.y, at.z);
        flight.entity.setDeltaMovement(direction);
    }

    private static void ensureChunkLoaded(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
    }

    private static void spawnEntity(Flight flight, Vec3 at, Vec3 direction) {
        BlockPos pos = BlockPos.containing(at);
        if (!flight.level.hasChunkAt(pos)) {
            return; // 区块还没加载好，下一 tick 再试；飞行逻辑不依赖它
        }
        CannonShellEntity shell = new CannonShellEntity(ModEntities.CANNON_SHELL.get(), flight.level);
        shell.setFlightId(flight.id);
        shell.setFlightDirection(direction);
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
        if ("arrived".equals(reason)) {
            // 到达终点时通常还在空中：往下贴到水面/地面再生成结构
            at = ImpactEffects.findSurface(flight.level, at, 64);
        }
        Vec3 incoming = flight.incomingVelocity();

        ServerPlayer owner = flight.owner != null
                ? flight.level.getServer().getPlayerList().getPlayer(flight.owner)
                : null;
        String summary = PayloadDelivery.onImpact(flight.level, at, incoming, flight.payload, flight.mode, owner);

        flight.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        flight.level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.7f);
        CannonWorks.LOGGER.info(
                "Cannon shell impact at {} ({}) after {} ticks, range {} blocks, bounces {}, payload [{}]: {}",
                String.format("%.1f/%.1f/%.1f", at.x, at.y, at.z), reason, flight.age,
                String.format("%.1f", flight.trajectory.range()), flight.bounces,
                flight.payload.describe(), summary);

        if (owner != null) {
            // 打石碑时地图标记直接用石碑名字（没自定义名就留空，客户端回退到「落点 x, z」）
            String markerLabel = flight.payload.cargoName() != null ? flight.payload.cargoName() : "";
            CannonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner),
                    new XaeroMarkerPacket(true, flight.id.hashCode(), (int) at.x, (int) at.y, (int) at.z,
                            markerLabel));
        }
        notifyOwner(owner, at, reason, summary);
        end(flight);
    }

    private static boolean sendTrailPoint(Flight flight, Vec3 at) {
        if (flight.owner == null) {
            return false;
        }
        ServerPlayer player = flight.level.getServer().getPlayerList().getPlayer(flight.owner);
        if (player == null) {
            return false;
        }
        CannonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new XaeroMarkerPacket(false, flight.id.hashCode(), (int) at.x, (int) at.y, (int) at.z, ""));
        return true;
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
        private int bounces;
        private int bounceTicks;
        private Vec3 position;
        private Vec3 bounceVelocity;
        private CannonShellEntity entity;

        private Flight(UUID id, ServerLevel level, Trajectory trajectory, UUID owner, CannonPayload payload,
                        FireMode mode) {
            this.id = id;
            this.level = level;
            this.trajectory = trajectory;
            this.owner = owner;
            this.payload = payload;
            this.mode = mode;
            this.position = trajectory.sampleAtTick(0);
        }

        public UUID id() {
            return id;
        }

        public int age() {
            return age;
        }

        public int bounces() {
            return bounces;
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
            return position;
        }

        /** 落点特效/弹坑用的来向；反弹段用当前反弹速度，初始段用相邻采样点。 */
        public Vec3 incomingVelocity() {
            if (bounceVelocity != null) {
                return bounceVelocity;
            }
            int tick = Math.max(0, age);
            return trajectory.sampleAtTick(tick).subtract(trajectory.sampleAtTick(Math.max(0, tick - 1)));
        }
    }
}
