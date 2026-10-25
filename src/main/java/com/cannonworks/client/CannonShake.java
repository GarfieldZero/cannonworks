package com.cannonworks.client;

import com.cannonworks.CannonWorks;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 重炮开火时的短促屏幕震动。
 *
 * <p>只影响相机角度，不改玩家朝向；强度按距离衰减，持续时间约 650ms。
 * 由客户端渲染器在收到 BE 的 {@code fireGameTime} 后触发。</p>
 */
@Mod.EventBusSubscriber(modid = CannonWorks.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CannonShake {

    private static final long DURATION_MS = 650L;
    private static long startMs;
    private static long endMs;
    private static float amplitude;

    private CannonShake() {
    }

    public static void trigger(float strength) {
        long now = Util.getMillis();
        if (now > endMs) {
            amplitude = 0.0f;
        }
        amplitude = Math.max(amplitude, Mth.clamp(strength, 0.0f, 2.0f));
        startMs = now;
        endMs = now + DURATION_MS;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = Util.getMillis();
        if (now >= endMs) {
            return;
        }
        float progress = Mth.clamp((now - startMs) / (float) DURATION_MS, 0.0f, 1.0f);
        float fade = (1.0f - progress) * (1.0f - progress);
        float a = amplitude * fade;
        if (a <= 0.001f) {
            return;
        }
        float t = progress * 42.0f;
        event.setYaw(event.getYaw() + Mth.sin(t * 1.7f) * a * 1.4f);
        event.setPitch(event.getPitch() + Mth.cos(t * 2.1f) * a * 0.8f);
        event.setRoll(event.getRoll() + Mth.sin(t * 2.6f) * a * 0.9f);
    }
}
