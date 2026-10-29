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
 * 客户端 → 服务端：给货物槽里的石碑改名。
 *
 * <p>名字写在物品的 {@code DataComponents.CUSTOM_NAME} 上，所以拿下来、换台炮、放进箱子都还在；
 * 传空字符串表示恢复默认（落点石碑会叫「空投点 x, z」）。</p>
 */
public record CannonRenameCargoPacket(BlockPos pos, String name) implements CustomPacketPayload {

    /** 和界面里输入框的 maxLength 保持一致；名字太长会把落点石碑的列表撑爆。 */
    public static final int MAX_NAME_LENGTH = 24;

    public static final Type<CannonRenameCargoPacket> TYPE =
            new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "rename_cargo"));

    public static final StreamCodec<FriendlyByteBuf, CannonRenameCargoPacket> CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), CannonRenameCargoPacket::decode);

    @Override
    public Type<CannonRenameCargoPacket> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(name, MAX_NAME_LENGTH);
    }

    public static CannonRenameCargoPacket decode(FriendlyByteBuf buf) {
        return new CannonRenameCargoPacket(buf.readBlockPos(), buf.readUtf(MAX_NAME_LENGTH));
    }

    public static void handle(CannonRenameCargoPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
            if (blockEntity instanceof CannonBlockEntity cannon
                    && player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) <= 64.0) {
                cannon.renameCargo(packet.name());
            }
        });
    }
}
