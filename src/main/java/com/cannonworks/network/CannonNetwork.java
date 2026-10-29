package com.cannonworks.network;

import com.cannonworks.CannonWorks;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册（NeoForge 1.21.1 的 payload 制）。
 *
 * <p>版本号仍然保留：客户端/服务端协议不一致时，NeoForge 会在握手阶段直接拒绝，
 * 比进入游戏后包表错位好查得多。</p>
 */
public final class CannonNetwork {

    /**
     * 协议版本：加了「石碑改名」包（2）、给 Xaero 落点包加了名字字段（3），都要 +1。
     */
    public static final String PROTOCOL = "3";

    private CannonNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(CannonControlPacket.TYPE, CannonControlPacket.CODEC,
                CannonControlPacket::handle);
        registrar.playToServer(CannonAimPacket.TYPE, CannonAimPacket.CODEC,
                CannonAimPacket::handle);
        registrar.playToServer(CannonRenameCargoPacket.TYPE, CannonRenameCargoPacket.CODEC,
                CannonRenameCargoPacket::handle);
        registrar.playToClient(XaeroMarkerPacket.TYPE, XaeroMarkerPacket.CODEC,
                XaeroMarkerPacket::handle);
    }
}
