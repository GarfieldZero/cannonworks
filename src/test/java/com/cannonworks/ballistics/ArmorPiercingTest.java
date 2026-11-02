package com.cannonworks.ballistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 穿甲弹的纯逻辑规则（不加载 Minecraft，跟 ChargeMathTest 一个套路）。
 *
 * <p>这里先把「穿不穿、怎么弹、什么时候停」钉死，ShellManager 那边只负责把
 * Minecraft 的 BlockState/法线翻译成这些数字，避免飞行代码里混进一堆魔法数。</p>
 */
class ArmorPiercingTest {

    private static final double EPS = 1.0E-9;

    @Test
    @DisplayName("硬度阈值：黑曜石 50 可穿，基岩 -1 与 50.1 不可穿")
    void hardnessThreshold() {
        assertTrue(ArmorPiercingRules.canPierce(0.4f, 50.0), "地狱岩应该能穿");
        assertTrue(ArmorPiercingRules.canPierce(30.0f, 50.0), "远古残骸应该能穿");
        assertTrue(ArmorPiercingRules.canPierce(50.0f, 50.0), "黑曜石正好在上限上，应该能穿");
        assertFalse(ArmorPiercingRules.canPierce(-1.0f, 50.0), "基岩这类硬度 -1 的方块不能穿");
        assertFalse(ArmorPiercingRules.canPierce(50.1f, 50.0), "超过黑曜石硬度就不能穿");
    }

    @Test
    @DisplayName("地面反弹：法向速度翻向，切向按摩擦衰减")
    void floorBounce() {
        ArmorPiercingRules.Velocity incoming =
                new ArmorPiercingRules.Velocity(2.0, -1.0, 0.0);
        ArmorPiercingRules.Velocity floorNormal =
                new ArmorPiercingRules.Velocity(0.0, 1.0, 0.0);

        ArmorPiercingRules.Velocity bounced =
                ArmorPiercingRules.reflect(incoming, floorNormal, 0.5, 0.85);

        assertEquals(1.7, bounced.x(), EPS, "切向 2.0 × 0.85");
        assertEquals(0.5, bounced.y(), EPS, "法向 1.0 × 恢复系数 0.5");
        assertEquals(0.0, bounced.z(), EPS);
    }

    @Test
    @DisplayName("墙面反弹：法向朝反方向，切向保留并摩擦")
    void wallBounce() {
        ArmorPiercingRules.Velocity incoming =
                new ArmorPiercingRules.Velocity(2.0, 0.0, 1.0);
        ArmorPiercingRules.Velocity wallNormal =
                new ArmorPiercingRules.Velocity(-1.0, 0.0, 0.0);

        ArmorPiercingRules.Velocity bounced =
                ArmorPiercingRules.reflect(incoming, wallNormal, 0.5, 0.85);

        assertEquals(-1.0, bounced.x(), EPS, "法向 2.0 × 0.5 后朝 -X");
        assertEquals(0.0, bounced.y(), EPS);
        assertEquals(0.85, bounced.z(), EPS, "切向 1.0 × 0.85");
    }

    @Test
    @DisplayName("弹跳次数用完或速度太低就停")
    void stopWhenSlowOrOutOfBounces() {
        assertTrue(ArmorPiercingRules.shouldStop(3, 3, 1.0, 0.08), "弹满 3 次必须停");
        assertTrue(ArmorPiercingRules.shouldStop(0, 3, 0.07, 0.08), "低于最小速度必须停");
        assertFalse(ArmorPiercingRules.shouldStop(2, 3, 0.5, 0.08), "还有次数且速度够，继续弹");
    }
}
