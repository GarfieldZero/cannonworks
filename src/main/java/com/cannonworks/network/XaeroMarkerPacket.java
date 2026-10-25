package com.cannonworks.network;

import com.cannonworks.client.XaeroBridge;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -> 客户端：Xaero 地图标记。
 *
 * @param impact   true = 落点（并清掉这条弹道留下的所有路径点），false = 飞行中的路径点
 * @param flightId 用来区分同一条弹道的路径点
 */
public record XaeroMarkerPacket(boolean impact, int flightId, int x, int y, int z) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(impact);
        buf.writeVarInt(flightId);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public static XaeroMarkerPacket decode(FriendlyByteBuf buf) {
        return new XaeroMarkerPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (impact) {
                XaeroBridge.onImpact(flightId, x, y, z);
            } else {
                XaeroBridge.onTrail(flightId, x, y, z);
            }
        });
        context.setPacketHandled(true);
    }
}
