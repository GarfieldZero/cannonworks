package com.cannonworks.entity;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Trajectory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 炮弹实体：沿着一条预先算好的 {@link Trajectory} 飞行。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>服务端权威 + 全局 tick 驱动</b>：轨迹由 {@link ShellManager} 在每个服务端 tick 推进，
 *       <b>不依赖所在区块是否 entity-ticking</b>。这是踩坑换来的：实测 0 人在线的服务器上
 *       vanilla 连盔甲架都不会下落，靠 entity tick 驱动的投射物会直接冻在半空。</li>
 *   <li><b>只加载自身所在区块</b>：每 tick 给当前区块挂一张 40 tick 后自动过期的 ticket，
 *       半径 2（中心区块到 entity-ticking 等级），不写 forcedchunks、不需要手动清理。</li>
 *   <li><b>逐 tick 射线检测</b>：用 {@link Level#clip} 检查这一 tick 走过的线段，
 *       撞到方块就地引爆——低抛被山拦截就是在这里发生的。</li>
 * </ul>
 */
public class CannonShellEntity extends Entity {

    /** 半径 2 → 中心区块 tick 等级 31（entity ticking），周围一圈只加载不 tick。 */
    private static final int TICKET_RADIUS = 2;
    private static final int TICKET_TIMEOUT_TICKS = 40;

    private static final TicketType<ChunkPos> SHELL_TICKET =
            TicketType.create("cannonworks:shell", Comparator.comparingLong(ChunkPos::toLong), TICKET_TIMEOUT_TICKS);

    private Trajectory trajectory;
    private int age;
    private boolean impacted;

    public CannonShellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setTrajectory(Trajectory trajectory) {
        this.trajectory = trajectory;
        Vec3 start = trajectory.origin();
        this.moveTo(start.x, start.y, start.z, 0.0f, 0.0f);
        // 出生时先挂一张 ticket，保证起始区块至少被加载出来（碰撞检测需要区块数据）。
        kickstartChunkTicket();
    }

    public Trajectory getTrajectory() {
        return trajectory;
    }

    public int getAge() {
        return age;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        kickstartChunkTicket();
        ShellManager.track(this);
    }

    @Override
    public void onRemovedFromWorld() {
        ShellManager.untrack(this);
        super.onRemovedFromWorld();
    }

    private void kickstartChunkTicket() {
        if (level() instanceof ServerLevel serverLevel) {
            ChunkPos chunk = new ChunkPos(blockPosition());
            serverLevel.getChunkSource().addRegionTicket(SHELL_TICKET, chunk, TICKET_RADIUS, chunk);
        }
    }

    /**
     * 由 {@link ShellManager} 每个服务端 tick 调用一次。
     * 不要放进 {@link #tick()}——那样会依赖区块 tick 状态，见类注释。
     */
    public void advance() {
        if (level().isClientSide() || impacted || isRemoved()) {
            return;
        }
        if (trajectory == null) {
            CannonWorks.LOGGER.warn("Cannon shell without trajectory at {}, discarding", position());
            discard();
            return;
        }

        age++;
        if (age >= trajectory.flightTicks()) {
            impact(trajectory.end(), "arrived");
            return;
        }

        Vec3 from = position();
        Vec3 to = trajectory.sampleAtTick(age);

        keepChunkLoaded(to);

        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            impact(hit.getLocation(), "hit " + hit.getType());
            return;
        }

        setPos(to.x, to.y, to.z);
        setDeltaMovement(to.subtract(from));
    }

    /** 给下一段轨迹所在区块续命；到期的 ticket 由服务端自动回收。 */
    private void keepChunkLoaded(Vec3 next) {
        if (level() instanceof ServerLevel serverLevel) {
            ChunkPos chunk = new ChunkPos(BlockPos.containing(next));
            serverLevel.getChunkSource().addRegionTicket(SHELL_TICKET, chunk, TICKET_RADIUS, chunk);
        }
    }

    private void impact(Vec3 at, String reason) {
        impacted = true;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, 0.7f);
            CannonWorks.LOGGER.info("Cannon shell impact at {} ({}) after {} ticks, range {} blocks",
                    String.format("%.1f/%.1f/%.1f", at.x, at.y, at.z), reason, age,
                    String.format("%.1f", trajectory.range()));
        }
        discard();
    }

    @Override
    public void tick() {
        // 飞行推进在 ShellManager 里，这里只保留 vanilla 的基础 tick（着火、传送门等）。
        super.tick();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 512.0 * 512.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Trajectory")) {
            CompoundTag t = tag.getCompound("Trajectory");
            this.trajectory = new Trajectory(
                    new Vec3(t.getDouble("ox"), t.getDouble("oy"), t.getDouble("oz")),
                    new Vec3(t.getDouble("dx"), 0.0, t.getDouble("dz")),
                    t.getDouble("range"),
                    t.getDouble("apex"),
                    t.getDouble("deltaY"),
                    t.getInt("ticks"));
        }
        this.age = tag.getInt("Age");
        this.impacted = tag.getBoolean("Impacted");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (trajectory != null) {
            CompoundTag t = new CompoundTag();
            t.putDouble("ox", trajectory.origin().x);
            t.putDouble("oy", trajectory.origin().y);
            t.putDouble("oz", trajectory.origin().z);
            t.putDouble("dx", trajectory.direction().x);
            t.putDouble("dz", trajectory.direction().z);
            t.putDouble("range", trajectory.range());
            t.putDouble("apex", trajectory.apex());
            t.putDouble("deltaY", trajectory.deltaY());
            t.putInt("ticks", trajectory.flightTicks());
            tag.put("Trajectory", t);
        }
        tag.putInt("Age", age);
        tag.putBoolean("Impacted", impacted);
    }
}
