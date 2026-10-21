package com.cannonworks.network;

import com.cannonworks.CannonWorks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CannonNetwork {

    private static final String PROTOCOL = "1";

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
    }
}
