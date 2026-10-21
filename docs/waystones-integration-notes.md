# Waystones API 速查（1.20.1 / Forge / v14.1.21）

来源：`libs/waystones-14.1.21.jar` 反查（`javap -public`）。包根：`net.blay09.mods.waystones`。

## 入口 `api.WaystonesAPI`（全静态）

### 查询

| 方法 | 用途 |
| --- | --- |
| `getActivatedWaystones(Player)` → `Collection<IWaystone>` | 玩家已激活的石碑（做「目标选择」最常用） |
| `getNearestWaystone(Player)` → `Optional<IWaystone>` | 最近石碑 |
| `getWaystone(Level, UUID)` / `getWaystone(MinecraftServer, UUID)` | 按 UID 查 |
| `getWaystoneAt(ServerLevel, BlockPos)` | 某坐标是不是石碑 |
| `getAllWaystones(MinecraftServer)` → `Stream<IWaystone>` | 全服石碑（含未激活） |
| `getWaystonesByType(MinecraftServer, ResourceLocation)` | 按类型（waystone / sharestone / warp_plate） |
| `getBoundWaystone(ItemStack)` / `setBoundWaystone(ItemStack, IWaystone)` | 绑定卷轴类物品 |
| `isWaystoneActivated(Player, IWaystone)` | 是否已激活 |

### 传送

| 方法 | 说明 |
| --- | --- |
| `forceTeleportToWaystone(Entity, IWaystone)` | **无视经验/冷却/激活**，但会走 `WaystoneTeleportEvent`（事件里仍可取消） |
| `tryTeleportToWaystone(Entity, IWaystone, WarpMode, IWaystone from)` | 正常流程，会检查激活/经验/冷却，返回错误 |
| `createDefaultTeleportContext(...)` / `createCustomTeleportContext(Entity, IWaystone)` | 拿到 `IWaystoneTeleportContext`，可改落点、追加实体、改经验/冷却后再 `tryTeleport(ctx)` |
| `forceTeleport(ctx)` | 强制传送已构造的 context |

返回值统一是 `Either<List<Entity>, WaystoneTeleportError>`：
- `left()` 有值 → 成功（被传送的实体列表）
- `right()` 有值 → 失败，`WaystoneTeleportError#getTranslationKey()` 可直接 `Component.translatable`

### 放置 / 生成

| 方法 | 说明 |
| --- | --- |
| `placeWaystone(Level, BlockPos, WaystoneStyle)` | 放一座石碑（建炮台时「炮击落点生成石碑」可用） |
| `placeSharestone(Level, BlockPos, DyeColor)` | 共享石 |
| `placeWarpPlate(Level, BlockPos)` | 传送板 |
| `createAttunedShard(IWaystone)` / `createBoundScroll(IWaystone)` | 生成绑定道具 |

## 数据接口 `api.IWaystone`

```
UUID getWaystoneUid();
String getName();
ResourceKey<Level> getDimension();
WaystoneOrigin getOrigin();       // 玩家放置 / 自然生成 / 指令……
boolean isGlobal();
boolean isOwner(Player);
BlockPos getPos();
boolean isValid();
UUID getOwnerUid();
ResourceLocation getWaystoneType();
TeleportDestination resolveDestination(ServerLevel);   // 默认实现，算出精确落点
```

`TeleportDestination(ServerLevel level, Vec3 location, Direction direction)` —— 做「炮弹把玩家送到石碑门口」时，直接用它当落点。

## 上下文 `api.IWaystoneTeleportContext`

可读写：`entity`、`targetWaystone`、`destination`、`fromWaystone`、`warpItem`、
`xpCost`、`cooldown`、`warpMode`、`playsSound`、`playsEffect`、`consumesWarpItem`、
`leashedEntities`、`additionalEntities`（追加实体 → **一次传送一群实体**，适合「炮弹载人」）。

## 事件（Balm 事件总线）

- `WaystoneTeleportEvent.Pre`：可改 `xpCost` / `cooldown` / `destination`，可 `addAdditionalEntity`，可 `setDimensionalTeleportResult` 等。
- `WaystoneTeleportEvent.Post`：拿到实际被传送的实体列表。

注意这两个事件继承 `net.blay09.mods.balm.api.event.BalmEvent`，注册方式和 Forge 事件总线不同，
写联动代码时先确认 Balm 的注册方式（`Balm.getHooks()...` 或在 Forge 总线上监听桥接器）。

## 依赖坐标

- 运行期必需：`balm >= 7.3.28`（本仓库用 7.3.44）
- 本模组在 `mods.toml` 中声明：`waystones [14.0.0,) optional, ordering=AFTER`

## 踩坑：激活之后必须手动同步

`PlayerWaystoneManager.activateWaystone(player, waystone)` 只写**服务端**玩家数据并 fire `WaystoneActivatedEvent`，
**不发同步包**。真正的同步在 `WaystoneBlock.use()`（玩家右键石碑）里，调用 `WaystoneSyncManager.sendActivatedWaystones(player)`。

所以「代码激活」之后如果不补这一句，玩家客户端列表里看不到新石碑，必须亲自摸一下才出现。
正确写法：

```java
PlayerWaystoneManager.activateWaystone(owner, waystone);
WaystoneSyncManager.sendActivatedWaystones(owner);   // 关键
```

另外 `WaystonesAPI.placeWaystone` 只放置+注册，不会设置名字（玩家放的石碑显示为「未命名」），
需要 `((IMutableWaystone) waystone).setName(name)` + `WaystoneManager.get(server).updateWaystone(waystone)`。
