package com.cannonworks.network;

import com.cannonworks.CannonWorks;
import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.payload.FireMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：把俯仰/方位/装药/模式<b>直接设成某个值</b>（滑条与模式按钮用）。
 *
 * <p>和 {@link CannonControlPacket}（增量，键盘用）分开，避免滑条拖动时把增量叠加成灾难。</p>
 *
 * <p>单位约定：{@code charge} 是<b>十分之一份</b>的整数（80 = 8.0 份），不是火药个数。</p>
 */
public record CannonAimPacket(BlockPos pos, int pitch, int yaw, int charge, FireMode mode)
        implements CustomPacketPayload {

    public static final Type<CannonAimPacket> TYPE =
            new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "aim"));

    public static final StreamCodec<FriendlyByteBuf, CannonAimPacket> CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), CannonAimPacket::decode);

    @Override
    public Type<CannonAimPacket> type() {
        return TYPE;
    }

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

    public static void handle(CannonAimPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
            if (blockEntity instanceof CannonBlockEntity cannon
                    && player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) <= 64.0) {
                cannon.setPitch(packet.pitch());
                cannon.setYaw(packet.yaw());
                cannon.setCharge(packet.charge());
                cannon.setMode(packet.mode());
            }
        });
    }
}
