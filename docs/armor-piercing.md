# 穿甲模式（v0.1.1）

> 目标：让大炮在**下界**也能正常用——低角度钻透地狱岩/黑石/黑曜石，撞基岩会跳弹，箱子/刷怪笼直接炸掉。

## 1. 玩法

- 装填界面右下角的模式按钮现在是三档：**接触 → 空爆 → 穿甲**。
- 穿甲模式飞行中：
  - 硬度 **0 ≤ h ≤ 50**（黑曜石及以下）的方块会被钻掉，挖出 3×3 截面的隧道；
  - **基岩**（硬度 -1）不会破；默认会跳弹（打水漂），最多 **3 次**，然后停在落点交付货物；
  - **箱子/刷怪笼**这类方块实体：直接炸掉，容器内容物按原版掉落；
  - 普通方块**不掉落**（防自动刷矿）；空气/流体/可替换方块跳过；
  - 停下来的地方**不再炸坑**（隧道本身就是效果），填充/石碑/TNT/末影珍珠/传送板照常生效。
- 低角度（5°）弹道也有 `MIN_APEX = 10` 的最低顶点，会先爬升约 10 格——不是贴地平飞，测试墙要建够高。
- 流体（岩浆/水）不参与碰撞，隧道可能灌进岩浆；这是下界挖隧道的真实代价。

## 2. 配置（`config/cannonworks-common.toml`）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `armorPiercing.maxHardness` | 50 | 能钻透的最大硬度；50 = 黑曜石 |
| `armorPiercing.tunnelRadius` | 1 | 隧道半径；1 = 3×3，0 = 只钻中心 |
| `armorPiercing.blocksPerTick` | 48 | 每 tick 最多钻多少块，防卡服 |
| `armorPiercing.bounce` | true | 撞基岩是否跳弹；false = 直接停 |
| `armorPiercing.maxBounces` | 3 | 最多跳几次 |
| `armorPiercing.restitution` | 0.55 | 法向恢复系数 |
| `armorPiercing.friction` | 0.85 | 切向摩擦（水平掉速） |
| `armorPiercing.minBounceSpeed` | 0.08 | 反弹后低于这个速度就停（格/tick） |
| `armorPiercing.breakBlockEntities` | true | 箱子/刷怪笼是否直接炸掉；false = 当硬墙停下 |

## 3. 实现

| 文件 | 职责 |
| --- | --- |
| `ballistics/ArmorPiercingRules.java` | 纯规则：硬度门槛、法线反射、停止条件；不依赖 Minecraft，方便单测 |
| `impact/TunnelDigger.java` | 在命中方块周围挖 (2r+1)³ 的立方体；普通方块无掉落；容器先 `Containers.dropContents` 再炸 |
| `entity/ShellManager.java` | 穿甲分支：沿 tick 线段循环 `level.clip` → 能穿就挖、撞基岩就反射/结算；反弹段用 `速度 + 重力` 积分 |
| `payload/PayloadDelivery.java` | 穿甲不炸坑；填充按贴面逻辑放；货物逻辑不变 |
| `payload/FireMode.java` | 新增 `PIERCING("穿甲", ...)`，三档循环 |
| `network/CannonNetwork.java` | 协议 `3 → 4`，防止旧客户端把穿甲当空爆 |
| `client/CannonScreen.java` | 模式按钮自动按 `FireMode.values()` 显示第三档 |
| `test/.../ArmorPiercingTest.java` | 硬度阈值、地面/墙面反弹、停止条件，4 个用例 |

## 4. 验证记录（2026-11-02，无头 Forge 47.4.1 服务端，下界实测）

| 场景 | 结果 |
| --- | --- |
| 地狱岩墙 3×3 截面 | ✅ 钻透，隧道方块为空气；日志 `drilled 18/12/21... blocks` |
| 黑曜石墙（硬度 50） | ✅ 两段命中：18 块 + 12 块，继续飞 |
| 基岩墙 | ✅ `pierce=false`，跳弹；最终 `bounces 3`，停在底部基岩 y=3 |
| 基岩天花板 | ✅ 命中 y=95 的基岩后向下跳弹；后续又撞自然下界顶 y=123；最终 `bounces 3` |
| 箱子（5 钻石） | ✅ 箱子方块变空气，钻石 ×5 + 箱子物品掉出（`Containers.dropContents`） |
| 普通接触模式回归 | ✅ 撞基岩，`bounces 0`，`contact` 弹坑 18 格，没有穿甲挖掘 |

JUnit：`ArmorPiercingTest` 4 + `ChargeMathTest` 7 + `NumericInputTest` 3 + `LangFilesTest` 2 + `SurvivalDataTest` 5 = **21/21 通过**。

## 5. 已知边界

- 模式是**弹种/引信**而不是新货物：这样穿甲可以和石碑/TNT/填充自由组合；以后要独立的“穿甲弹芯”物品可以再加。
- 基岩反射是**物理跳弹**：撞天花板会向下弹，不是贴顶滑行；地板场景才是字面意义的打水漂。
- 无掉落是默认值；要改就改 `blocksPerTick` 之外还得加掉落开关（当前版本写死无掉落）。
- 客户端/服务端协议必须是 **4**；旧 jar 会被拒绝连接。
- 界面交互（模式按钮第三档、跳弹视觉）本版只做了编译与服务端验证，客户端手感需要进游戏确认。
