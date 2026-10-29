package com.cannonworks;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 生存模式数据守门。
 *
 * <p>配方、掉落表、工具标签、配方书解锁全是 JSON——删掉或改错一个文件不会让编译失败，
 * 只会让玩家在生存里「合不出来 / 挖不动 / 挖了不掉」。这里在构建期把它们钉住。</p>
 *
 * <p>断言用压缩后的子串而不是解析 JSON：这些文件结构简单，这样写不引额外依赖，
 * 也能容忍缩进调整。</p>
 */
class SurvivalDataTest {

    @Test
    @DisplayName("合成配方仍是 8 铁锭 + 1 火药，产物是大炮")
    void recipeCostsIronAndGunpowder() throws IOException {
        // 1.21 的数据包目录改单数：recipe / advancement / loot_table / tags/block
        String json = compact(read("data/cannonworks/recipe/cannon.json"));
        assertTrue(json.contains("\"pattern\":[\"III\",\"IGI\",\"III\"]"), "配方形状变了");
        assertTrue(json.contains("\"minecraft:iron_ingot\""), "配方少了铁锭");
        assertTrue(json.contains("\"minecraft:gunpowder\""), "配方少了火药");
        assertTrue(json.contains("\"result\":{\"count\":1,\"id\":\"cannonworks:cannon\"}"), "产物不是大炮");
    }

    @Test
    @DisplayName("大炮方块掉自己，而且不被爆炸吞掉")
    void cannonBlockDropsItself() throws IOException {
        String json = compact(read("data/cannonworks/loot_table/blocks/cannon.json"));
        assertTrue(json.contains("\"name\":\"cannonworks:cannon\""), "打掉大炮不掉大炮");
        assertTrue(json.contains("\"minecraft:survives_explosion\""), "少了爆炸保护条件");
    }

    @Test
    @DisplayName("占位块不掉任何东西（避免刷物品）")
    void placeholderDropsNothing() throws IOException {
        String json = compact(read("data/cannonworks/loot_table/blocks/cannon_part.json"));
        assertTrue(json.contains("\"pools\":[]"), "占位块掉落表不该有池子");
    }

    @Test
    @DisplayName("镐子能加速挖大炮和占位块")
    void pickaxeTagCoversBothBlocks() throws IOException {
        String json = compact(read("data/minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(json.contains("\"cannonworks:cannon\""), "大炮不在镐子标签里");
        assertTrue(json.contains("\"cannonworks:cannon_part\""), "占位块不在镐子标签里");
    }

    @Test
    @DisplayName("拿到铁锭和火药后，配方书会解锁大炮")
    void recipeUnlocksFromIngredients() throws IOException {
        String json = compact(read("data/cannonworks/advancement/recipes/cannon.json"));
        assertTrue(json.contains("\"minecraft:iron_ingot\""), "解锁条件里没有铁锭");
        assertTrue(json.contains("\"minecraft:gunpowder\""), "解锁条件里没有火药");
        assertTrue(json.contains("\"recipes\":[\"cannonworks:cannon\"]"), "没有把配方奖励出去");
    }

    private static String compact(String json) {
        return json.replaceAll("\\s+", "");
    }

    private static String read(String resource) throws IOException {
        InputStream stream = SurvivalDataTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, "找不到数据文件: " + resource);
        try (InputStream in = Objects.requireNonNull(stream)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
