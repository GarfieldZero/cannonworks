package com.cannonworks.ballistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 装药规则的回归测试。
 *
 * <p>这些数字不是随便挑的，全部来自 `docs/precision-refactor.md` §6/§7 的验收记录：
 * 8.1 份扣 9 个火药、8.5 和 9.0 必须打不同距离、旧存档 8 要变 80。
 * 以后谁改 ChargeMath，先看这里的断言能不能过。</p>
 */
class ChargeMathTest {

    @Test
    @DisplayName("火药按整份向上取整：8.0→8、8.1→9、9.0→9")
    void neededItemsRoundsUp() {
        assertEquals(8, ChargeMath.neededItems(80));
        assertEquals(9, ChargeMath.neededItems(81));
        assertEquals(9, ChargeMath.neededItems(85));
        assertEquals(9, ChargeMath.neededItems(90));
        assertEquals(64, ChargeMath.neededItems(640));
        assertEquals(0, ChargeMath.neededItems(0));
    }

    @Test
    @DisplayName("火药够时射程按请求值算，不能因为多凑一个就多打")
    void effectiveChargeKeepsRequestedValue() {
        // 8.5 份要 9 个火药，但只能算 8.5 份（当时清单写 usedItems*10 会变成 9.0，是错的）
        assertEquals(85, ChargeMath.effectiveChargeTenths(85, 64));
        assertEquals(81, ChargeMath.effectiveChargeTenths(81, 64));
        assertEquals(90, ChargeMath.effectiveChargeTenths(90, 64));
    }

    @Test
    @DisplayName("火药不够时按到手的整数份封顶")
    void effectiveChargeCappedByStock() {
        // 无头验收里那条：装药 64.0 份、只有 29 个火药 → 打 29.0 份
        assertEquals(290, ChargeMath.effectiveChargeTenths(640, 29));
        assertEquals(290, ChargeMath.consumedItems(640, 29) * 10);
        assertEquals(9, ChargeMath.consumedItems(81, 64));
        assertEquals(0, ChargeMath.effectiveChargeTenths(85, 0));
    }

    @Test
    @DisplayName("旧存档迁移：没有记号且 ≤64 的按整数份 ×10")
    void migratesLegacyCharge() {
        assertEquals(80, ChargeMath.migrateCharge(8, false));
        assertEquals(640, ChargeMath.migrateCharge(64, false));
        assertEquals(0, ChargeMath.migrateCharge(0, false));
    }

    @Test
    @DisplayName("新存档的 6.4 份（64）不能被误乘成 64.0 份")
    void doesNotMigrateNewFormat() {
        assertEquals(64, ChargeMath.migrateCharge(64, true));
        assertEquals(600, ChargeMath.migrateCharge(600, true));
    }

    @Test
    @DisplayName("迁移结果仍要夹在 0..640")
    void migrationClamps() {
        assertEquals(640, ChargeMath.migrateCharge(1000, true));
        assertEquals(0, ChargeMath.migrateCharge(-5, false));
        assertEquals(640, ChargeMath.clamp(9999));
        assertEquals(0, ChargeMath.clamp(-1));
    }

    @Test
    @DisplayName("内部单位换「份」只在一个地方做")
    void convertsToCharges() {
        assertEquals(8.0, ChargeMath.toCharges(80), 1e-9);
        assertEquals(8.1, ChargeMath.toCharges(81), 1e-9);
        assertEquals(64.0, ChargeMath.toCharges(640), 1e-9);
    }
}
