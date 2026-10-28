package com.cannonworks.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 手输框的解析规则：小数、逗号、半截输入。 */
class NumericInputTest {

    @Test
    @DisplayName("正常小数和整数")
    void parsesNumbers() {
        assertEquals(8.1, NumericInput.parse("8.1"), 1e-9);
        assertEquals(45.0, NumericInput.parse("45"), 1e-9);
        assertEquals(360.0, NumericInput.parse(" 360 "), 1e-9);
    }

    @Test
    @DisplayName("逗号当小数点（中文输入法/小键盘常见）")
    void acceptsComma() {
        assertEquals(8.5, NumericInput.parse("8,5"), 1e-9);
    }

    @Test
    @DisplayName("半截输入不提交，避免把 8. 当成 8")
    void rejectsIncompleteInput() {
        assertNull(NumericInput.parse(""));
        assertNull(NumericInput.parse("   "));
        assertNull(NumericInput.parse("-"));
        assertNull(NumericInput.parse("."));
        assertNull(NumericInput.parse("abc"));
        assertNull(NumericInput.parse(null));
    }
}
