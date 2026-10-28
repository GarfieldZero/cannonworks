package com.cannonworks.client;

/**
 * 手输框的数值解析。
 *
 * <p>单独抽出来是为了能写单元测试：小数点/逗号、半截输入（"-"、"8..1"）、
 * 全是空格——这些边界在界面里点错一次就要重启游戏复现，放在纯函数里一条断言就够了。</p>
 */
public final class NumericInput {

    private NumericInput() {
    }

    /**
     * 解析玩家输入。
     *
     * <p>中文输入法和小键盘经常给出逗号，所以逗号也当小数点收；
     * 解析不了返回 {@code null}，由调用方决定「当没改过」。</p>
     */
    public static Double parse(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim().replace(',', '.');
        // 只输了一个减号或小数点，是还没打完，不能当成 0
        if (cleaned.isEmpty() || "-".equals(cleaned) || ".".equals(cleaned)) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
