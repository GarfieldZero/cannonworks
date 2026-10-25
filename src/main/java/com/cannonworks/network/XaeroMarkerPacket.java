package com.cannonworks.network;

import com.cannonworks.client.XaeroBridge;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -> 客户端：告诉客户端在 Xaero 地图上加一个标记。
 *
 * @param impact true = 落点（同时清掉沿途的弹道点），false = 弹道路径点
 */
public record XaeroMarkerPacket(boolean impact, int x, int y, int z) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(impact);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public static XaeroMarkerPacket decode(FriendlyByteBuf buf) {
        return new XaeroMarkerPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> XaeroBridge.onMarker(impact, x, y, z));
        context.setPacketHandled(true);
    }
}
