package com.cannonworks.network;

import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.payload.FireMode;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：把俯仰/方位/装药/模式<b>直接设成某个值</b>（滑条与模式按钮用）。
 *
 * <p>和 {@link CannonControlPacket}（增量，键盘用）分开，避免滑条拖动时把增量叠加成灾难。</p>
 */
public record CannonAimPacket(BlockPos pos, int pitch, int yaw, int charge, FireMode mode) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(pitch);
        buf.writeVarInt(yaw);
        buf.writeVarInt(charge);
        buf.writeByte(mode.ordinal());
    }

    public static CannonAimPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int pitch = buf.readVarInt();
        int yaw = buf.readVarInt();
        int charge = buf.readVarInt();
        FireMode[] modes = FireMode.values();
        int ordinal = Math.max(0, Math.min(modes.length - 1, buf.readByte()));
        return new CannonAimPacket(pos, pitch, yaw, charge, modes[ordinal]);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            BlockEntity blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity instanceof CannonBlockEntity cannon
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                cannon.setPitch(pitch);
                cannon.setYaw(yaw);
                cannon.setCharge(charge);
                cannon.setMode(mode);
            }
        }
        context.setPacketHandled(true);
    }
}
