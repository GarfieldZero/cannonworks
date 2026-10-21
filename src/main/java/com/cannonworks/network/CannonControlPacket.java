package com.cannonworks.network;

import com.cannonworks.block.CannonBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：调角度/装药，或开火。
 *
 * <p>所有校验都在服务端做（距离、冷却、火药数量），客户端只发「意图」。</p>
 */
public record CannonControlPacket(BlockPos pos, int pitchDelta, int yawDelta, int chargeDelta, boolean fire) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(pitchDelta);
        buf.writeByte(yawDelta);
        buf.writeByte(chargeDelta);
        buf.writeBoolean(fire);
    }

    public static CannonControlPacket decode(FriendlyByteBuf buf) {
        return new CannonControlPacket(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            BlockEntity blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity instanceof CannonBlockEntity cannon
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                cannon.adjust(pitchDelta, yawDelta, chargeDelta);
                if (fire) {
                    cannon.fire(player);
                }
            }
        }
        context.setPacketHandled(true);
    }
}
