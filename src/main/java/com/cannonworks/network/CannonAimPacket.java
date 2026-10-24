package com.cannonworks.network;

import com.cannonworks.block.CannonBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：把俯仰/方位/装药<b>直接设成某个值</b>（滑条用）。
 *
 * <p>和 {@link CannonControlPacket}（增量，按键用）分开，避免滑条拖动时把增量叠加成灾难。</p>
 */
public record CannonAimPacket(BlockPos pos, int pitch, int yaw, int charge) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(pitch);
        buf.writeVarInt(yaw);
        buf.writeVarInt(charge);
    }

    public static CannonAimPacket decode(FriendlyByteBuf buf) {
        return new CannonAimPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
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
            }
        }
        context.setPacketHandled(true);
    }
}
