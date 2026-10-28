package com.cannonworks.network;

import com.cannonworks.block.CannonBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：给货物槽里的石碑改名。
 *
 * <p>名字直接写在物品自己的 {@code display.Name} 上，所以拿下来、换台炮、放进箱子都还在；
 * 传空字符串表示恢复默认（落点石碑会叫「空投点 x, z」）。</p>
 *
 * <p>之所以不把这个名字塞进开火包，是因为玩家常常先改名再装填，
 * 而且改完应该在物品提示里看得见。</p>
 */
public record CannonRenameCargoPacket(BlockPos pos, String name) {

    /** 和界面里输入框的 maxLength 保持一致；名字太长会把落点石碑的列表撑爆。 */
    public static final int MAX_NAME_LENGTH = 24;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(name, MAX_NAME_LENGTH);
    }

    public static CannonRenameCargoPacket decode(FriendlyByteBuf buf) {
        return new CannonRenameCargoPacket(buf.readBlockPos(), buf.readUtf(MAX_NAME_LENGTH));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            BlockEntity blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity instanceof CannonBlockEntity cannon
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                cannon.renameCargo(name);
            }
        }
        context.setPacketHandled(true);
    }
}
