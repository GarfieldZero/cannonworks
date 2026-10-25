package com.cannonworks.entity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 炮弹的<b>视觉载体</b>，不负责飞行。
 *
 * <p>飞行状态由 {@link ShellManager} 在服务端内存里持有，实体随时可以被区块卸载、
 * 被重新加载、甚至被销毁重建，都不会影响弹道。这是踩坑后的架构：</p>
 * <ul>
 *   <li>实体自己 tick 不可靠（0 人服务器上根本不 tick）；</li>
 *   <li>实体所在区块一旦卸载，实体被存档移出世界，飞行就“冻”在半空。</li>
 * </ul>
 *
 * <p>现在实体只做三件事：渲染、被 manager 摆位置、发现自己是“孤儿”就自杀。</p>
 */
public class CannonShellEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(CannonShellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(CannonShellEntity.class, EntityDataSerializers.FLOAT);

    private UUID flightId;

    public CannonShellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setFlightId(UUID flightId) {
        this.flightId = flightId;
    }

    /** 由 ShellManager 每个 tick 写入，客户端用这个朝向渲染弹体和尾焰。 */
    public void setFlightDirection(Vec3 direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) Math.toDegrees(Math.atan2(direction.y, horizontal));
        entityData.set(DATA_YAW, yaw);
        entityData.set(DATA_PITCH, pitch);
    }

    public float getFlightYaw() {
        return entityData.get(DATA_YAW);
    }

    public float getFlightPitch() {
        return entityData.get(DATA_PITCH);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        // 区块重载后实体可能“复活”，此时向 manager 认领自己：
        // 飞行还在 → 接管；飞行已结束 → 这是存档里的孤儿，直接清掉。
        if (!ShellManager.hasFlight(flightId)) {
            discard();
            return;
        }
        ShellManager.adopt(flightId, this);
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
        entityData.define(DATA_YAW, 0.0f);
        entityData.define(DATA_PITCH, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("FlightId")) {
            this.flightId = tag.getUUID("FlightId");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (flightId != null) {
            tag.putUUID("FlightId", flightId);
        }
    }
}
