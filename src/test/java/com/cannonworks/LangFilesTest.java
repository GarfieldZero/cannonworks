package com.cannonworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 语言文件的守门测试。
 *
 * <p>游戏里缺一个键不会崩，只会把 {@code gui.cannonworks...} 原样显示出来，
 * 而且只有切到那个语言、打开那个界面才看得见——正是那种能漏到发布之后的问题。
 * 这里在构建期就把「中英键不一致」「重复键」挡住。</p>
 *
 * <p>没引 Gson：语言文件是扁平的 key/value，正则足够，也免得测试依赖
 * 跟着 Minecraft 的传递依赖走。</p>
 */
class LangFilesTest {

    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    @Test
    @DisplayName("中英文语言文件的键一一对应")
    void zhAndEnDeclareSameKeys() throws IOException {
        Set<String> zh = keys("assets/cannonworks/lang/zh_cn.json");
        Set<String> en = keys("assets/cannonworks/lang/en_us.json");
        assertFalse(zh.isEmpty(), "中文语言文件不该是空的");
        assertEquals(en, zh, "缺一边的键会直接显示原始 key");
    }

    @Test
    @DisplayName("同一个语言文件里没有重复键（JSON 会静默覆盖）")
    void noDuplicateKeys() throws IOException {
        assertEquals(uniqueKeys("assets/cannonworks/lang/zh_cn.json").size(),
                countKeys("assets/cannonworks/lang/zh_cn.json"), "中文语言文件有重复键");
        assertEquals(uniqueKeys("assets/cannonworks/lang/en_us.json").size(),
                countKeys("assets/cannonworks/lang/en_us.json"), "英文语言文件有重复键");
    }

    private static Set<String> keys(String resource) throws IOException {
        return uniqueKeys(resource);
    }

    private static Set<String> uniqueKeys(String resource) throws IOException {
        Matcher matcher = KEY.matcher(read(resource));
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static int countKeys(String resource) throws IOException {
        Matcher matcher = KEY.matcher(read(resource));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String read(String resource) throws IOException {
        InputStream stream = LangFilesTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, "找不到语言文件: " + resource);
        try (InputStream in = Objects.requireNonNull(stream)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
