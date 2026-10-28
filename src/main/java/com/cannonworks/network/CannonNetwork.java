package com.cannonworks.network;

import com.cannonworks.CannonWorks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CannonNetwork {

    /**
     * 协议版本：加了「石碑改名」包（2）、给 Xaero 落点包加了名字字段（3），都要 +1。
     * 不 +1 的话旧客户端连新服务端会在注册包表时错位，报的错跟真正原因八竿子打不着。
     */
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(CannonWorks.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private CannonNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(CannonControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CannonControlPacket::encode)
                .decoder(CannonControlPacket::decode)
                .consumerMainThread(CannonControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(CannonAimPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CannonAimPacket::encode)
                .decoder(CannonAimPacket::decode)
                .consumerMainThread(CannonAimPacket::handle)
                .add();
        CHANNEL.messageBuilder(CannonRenameCargoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CannonRenameCargoPacket::encode)
                .decoder(CannonRenameCargoPacket::decode)
                .consumerMainThread(CannonRenameCargoPacket::handle)
                .add();
        CHANNEL.messageBuilder(XaeroMarkerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(XaeroMarkerPacket::encode)
                .decoder(XaeroMarkerPacket::decode)
                .consumerMainThread(XaeroMarkerPacket::handle)
                .add();
    }
}
