# 生存模式适配核对（2026-10-28）

> 结论：**配方、掉落、拆炮、创造栏本来就有**；缺的是「镐子加速」和「配方书解锁」，本次已补。

## 1. 核对表

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 合成配方 | ✅ 原有 | 有序配方：8 铁锭 + 1 火药（中心），产物 `cannonworks:cannon` |
| 创造栏 | ✅ 原有 | 「功能方块」页（`BuildCreativeModeTabContentsEvent`） |
| 掉落（本体） | ✅ 原有 | `loot_tables/blocks/cannon.json` 掉自己，带 `survives_explosion` |
| 掉落（占位块） | ✅ 原有 | 空池子，不掉任何东西 |
| 拆任意底座块 | ✅ 原有 | `CannonPartBlock.onRemove` → `CannonMultiblock.breakFromPart` → `destroyBlock(anchor, true)`；控制器和里面的火药/负载一起掉 |
| 镐子加速 | ❌ → ✅ 本次补 | 之前没有 `mineable/pickaxe` 标签，镐和空手一样慢（硬度 5 ≈ 25 秒） |
| 配方书解锁 | ❌ → ✅ 本次补 | 新增 advancement：背包里同时有铁锭和火药时解锁配方 |
| 工具等级要求 | 刻意不做 | 任何工具/空手都能挖掉并拿到，理由见 §3 |

## 2. 这次补的文件

- `data/minecraft/tags/blocks/mineable/pickaxe.json`：`cannonworks:cannon` + `cannonworks:cannon_part`
- `data/cannonworks/advancements/recipes/cannon.json`：`inventory_changed` 双条件（铁锭 + 火药）→ 奖励配方
- `src/test/java/com/cannonworks/SurvivalDataTest.java`：5 个守门用例（配方 / 本体掉落 / 占位块空池 / 镐标签 / 配方书）

## 3. 为什么不做「必须用镐」的等级限制

拆任意占位块都会走 `destroyBlock(..., true)` 把控制器掉出来，而这条路径**不看玩家手里的工具**。
如果只给控制器加 `requiresCorrectToolForDrops`，就会出现「用木镐拆底座能拿到、直接挖本体反而拿不到」
的怪事。所以统一设计成：**任何工具都能拿，镐子只是更快**，和「整台炮是一个整体」的交互一致。

## 4. 验证

- 17 个单元测试全绿（新增 5 个生存数据用例）
- 专用服务器启动无 datapack 报错；jar 里标签和 advancement 都在
- 无头 RCON：
  - `loot spawn ... mine <炮位置>` → `Dropped 1 [Cannon] from loot table cannonworks:blocks/cannon`，
    落点物品实体 id = `cannonworks:cannon`
  - `setblock <某个占位块> air` → 控制器和其余占位块一起变空气，并在炮位掉落 `cannonworks:cannon`
- **没验证的**：拿镐子实挖的秒数（标签是原版标准行为，但没实机掐表）；配方书里点击合成
  （服务器没有玩家，点不了）。这两条要主人进游戏顺手试一下。

## 5. 部署

`build/libs/cannonworks-0.1.0.jar`（168766 字节，SHA256 `5B89…312A`）已覆盖测试环境；
上一版备份在 `.cache/deploy-backup-localization-cannonworks-0.1.0.jar`。
