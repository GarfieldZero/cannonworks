package com.cannonworks.network;

import com.cannonworks.CannonWorks;
import com.cannonworks.client.XaeroBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：Xaero 地图标记。
 *
 * @param impact   true = 落点（并清掉这条弹道留下的所有路径点），false = 飞行中的路径点
 * @param flightId 用来区分同一条弹道的路径点
 * @param label    落点标记的名字；空串表示用默认的「落点 x, z」。
 *                 打传送石碑时会带货物名——否则地图上永远显示坐标名，
 *                 看起来就像「石碑命名没生效」。
 */
public record XaeroMarkerPacket(boolean impact, int flightId, int x, int y, int z, String label)
        implements CustomPacketPayload {

    /** 和石碑命名框同级的长度上限，别让地图上的名字撑爆。 */
    public static final int MAX_LABEL_LENGTH = 32;

    public static final Type<XaeroMarkerPacket> TYPE =
            new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "xaero_marker"));

    public static final StreamCodec<FriendlyByteBuf, XaeroMarkerPacket> CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), XaeroMarkerPacket::decode);

    @Override
    public Type<XaeroMarkerPacket> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(impact);
        buf.writeVarInt(flightId);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        buf.writeUtf(label, MAX_LABEL_LENGTH);
    }

    public static XaeroMarkerPacket decode(FriendlyByteBuf buf) {
        return new XaeroMarkerPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readUtf(MAX_LABEL_LENGTH));
    }

    public static void handle(XaeroMarkerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.impact()) {
                XaeroBridge.onImpact(packet.flightId(), packet.x(), packet.y(), packet.z(), packet.label());
            } else {
                XaeroBridge.onTrail(packet.flightId(), packet.x(), packet.y(), packet.z());
            }
        });
    }
}
