# 1.21.1 + NeoForge 适配评估（2026-10-29）

> **结论：可行，工作量中等偏大。**
> 只求核心可玩（不含 Waystones）约 **2–3 个工作日**；完整功能（含石碑联动）约 **4–6 个工作日**。
> 不建议直接改现有代码，单开 `1.21.1-neoforge` 分支，版本号起 0.2.0。

## 1. 现状

- 当前发布：**v0.1.0**，Minecraft **1.20.1 + Forge 47.x**（Release 已发；截图里「没有发布 Release」的信息已过时）
- 代码量：**39 个 Java 文件 / 4319 行**；**没有 Mixin、没有 Access Transformer**（这点对迁移很有利）
- 对旧 API 的依赖面：Forge 包 56 处、`CompoundTag` 10 处、`FriendlyByteBuf` 12 处、
  `SimpleChannel` 3 处、`NetworkHooks` 4 处、`ForgeConfigSpec` 5 处、`DeferredRegister` 14 处
- 本机只有 **JDK 17**；NeoForge 21.1 需要 **Java 21**，这是第一个前置条件

## 2. 生态可用性（已核查，非猜）

| 依赖 | 1.21.1 + NeoForge | 结论 |
| --- | --- | --- |
| Waystones | 有，21.1.x 共 41 个版本（最新 21.1.46） | **API 大改**，集成代码要重写 |
| Xaero 小地图/世界地图 | 有（26.6.0） | 我们用的反射链原样存在，基本不用改 |
| NeoForge | 21.1.x（最新 21.1.265） | 需要 Java 21 |

Waystones 21.1 API 与 14.x 的差异（比对 jar 内方法表得出）：

- `IWaystone` → `Waystone`，`IMutableWaystone` → `MutableWaystone`
- `getName()` 从 `String` 变成 `Component`；`setName(Component)`
- `WaystoneStyles.DEFAULT` 被移除（只剩 `getStyle(...)`），默认样式要换获取方式
- `WaystonesAPI.forceTeleportToWaystone` 被移除，传送要走 TeleportContext API
- `WaystoneManager.get(server).updateWaystone(...)` 没了，`WaystoneSyncManager.sendActivatedWaystones` 也没了
- 好消息：`placeWaystone / placeSharestone / placeWarpPlate / activateWaystone /
  getActivatedWaystones / getNearestWaystone / sendWaystoneUpdateToAll` 都还在

Xaero 反射链核对结果（26.6.0 里全部存在）：

```
XaeroMinimapSession.getCurrentSession()
  → getWaypointsManager()
  → getCurrentWorld()
  → getCurrentSet()
  → getList()
Waypoint(int,int,int,String,String,int)   // 6 参构造仍在
```

## 3. 迁移清单与估时

| 模块 | 1.21.1 要做的事 | 估时 |
| --- | --- | --- |
| 构建环境 | JDK 21、ModDevGradle/NeoGradle、`neoforge.mods.toml`、去掉 reobf | 0.5 天 |
| 注册与事件 | `DeferredRegister`/`DeferredHolder`/`NeoForgeRegistries`、`NeoForge.EVENT_BUS`、`EventBusSubscriber`、`ModList` | 0.5 天（39 个文件机械改） |
| 网络 | `SimpleChannel` → `RegisterPayloadHandlersEvent` + `CustomPacketPayload`/`StreamCodec`（4 个包） | 0.5–1 天 |
| NBT → 组件 | 物品自定义名改 `DataComponents.CUSTOM_NAME`；`BlockEntity.saveAdditional/loadAdditional` 加 `HolderLookup.Provider`；`ItemStackHandler` 序列化签名 | 0.5 天 |
| 菜单/界面 | `IForgeMenuType` → NeoForge 对应 API、`NetworkHooks.openScreen` → `player.openMenu`、`GuiGraphics.blit` 签名 | 0.5 天 |
| 实体 | `defineSynchedData` → `SynchedEntityData.Builder` + `defineId` | 0.25 天 |
| 配置 | `ForgeConfigSpec` → `ModConfigSpec` | 0.25 天 |
| 数据包 JSON | `recipes`→`recipe`、`advancements`→`advancement`、`loot_tables`→`loot_table`、`tags/blocks`→`tags/block`；配方产物改 `{"id": ...}` | 0.25 天 |
| Waystones 集成 | 2 个类按新 API 重写（见 §2） | 0.5–1 天 |
| Xaero | 反射链已核对，预计 0 改动 | 0.1 天 |
| 回归测试 | 四向/俯仰/开火/石碑命名/生存/专用服务器 | 1–2 天 |
| **合计** | 核心（不含 Waystones）**2–3 天**；完整 **4–6 天** | |

## 4. 主要风险

1. **Waystones API 重写**是最大不确定点：接口和传送模型都变了，但目标方法都还在，工作量可控。
2. **NeoForge 版本迭代快**：要锁定 21.1.x 具体版本（玩家包也得对齐）。
3. **双分支维护**：1.20.1 Forge 和 1.21.1 NeoForge 的 API 不兼容，**不能一个 jar 通吃**；
   要么双分支，要么上 Architectury 多层（成本更高，不建议现在做）。
4. 1.21.1 的资源包格式（item model 仍是 `item/generated`）变化不大，但数据包目录/配方格式要逐项验证。

## 5. 建议路径

1. 开 `1.21.1-neoforge` 分支，版本号 **0.2.0**，`main` 保持 1.20.1 v0.1.0 不动。
2. 先做**不含 Waystones 的核心版**（方块/实体/弹道/命令/GUI），确认能打炮再补联动——
   软依赖的设计允许这样做，风险最低。
3. 补 Waystones 21.1 集成，再核对 Xaero（预计不用改）。
4. 用专用服务器 + 客户端各跑一轮回归，Release 标注 `Minecraft 1.21.1 / NeoForge 21.1.x / Java 21`。

> 给那位玩家的回复要点：v0.1.0 已经发了 Release（1.20.1 Forge）；1.21.1 NeoForge 目前确实不支持，
> 但生态（Waystones/Xaero/NeoForge）都齐，属于「可移植、工程量中等」，不是「没有替代品」。
