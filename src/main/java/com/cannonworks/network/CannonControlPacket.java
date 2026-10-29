package com.cannonworks.network;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：调角度/装药，或开火。
 *
 * <p>所有校验都在服务端做（距离、冷却、火药数量），客户端只发「意图」。</p>
 * <p>{@code chargeDelta} 与装药字段同单位（十分之一份）：键盘一次 ±10 就是 ±1.0 份。</p>
 */
public record CannonControlPacket(BlockPos pos, int pitchDelta, int yawDelta, int chargeDelta, boolean fire)
        implements CustomPacketPayload {

    public static final Type<CannonControlPacket> TYPE =
            new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "control"));

    public static final StreamCodec<FriendlyByteBuf, CannonControlPacket> CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), CannonControlPacket::decode);

    @Override
    public Type<CannonControlPacket> type() {
        return TYPE;
    }

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

    public static void handle(CannonControlPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
            if (blockEntity instanceof CannonBlockEntity cannon
                    && player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) <= 64.0) {
                cannon.adjust(packet.pitchDelta(), packet.yawDelta(), packet.chargeDelta());
                if (packet.fire()) {
                    cannon.fire(player);
                }
            }
        });
    }
}
