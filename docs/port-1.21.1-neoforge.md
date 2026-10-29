# 1.21.1 + NeoForge 适配记录（分支 `1.21.1-neoforge`）

> 目标：**保持 1.20.1 / Forge 的主线不动**，在单独分支上做出同功能的 1.21.1 / NeoForge 版本。
> 主线：`main`（Minecraft 1.20.1 / Forge 47.4.1 / Java 17 / ForgeGradle）。
> 本分支：`1.21.1-neoforge`（Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 / ModDevGradle 2.0.148）。

## 1. 环境

| 项目 | 版本 | 说明 |
| --- | --- | --- |
| Minecraft | 1.21.1 | |
| NeoForge | 21.1.265 | `gradle.properties` 的 `neoforge_version` |
| Java | 21（Temurin 21.0.12） | `JAVA_HOME` 指向 JDK 21；Gradle 本身也用 21 跑 |
| Gradle | 8.8（wrapper） | |
| 构建插件 | ModDevGradle 2.0.148 | NeoForm 管线，无 reobf |
| 传送石碑 | Waystones 21.1.46 + Balm 21.0.66 | 软依赖；jar 放 `libs-1.21.1/`（不入库） |

## 2. 构建与运行

```powershell
# 1) 准备联动 jar（版本见 gradle.properties），放到 libs-1.21.1/
#    waystones-neoforge-1.21.1-21.1.46.jar
#    balm-neoforge-1.21.1-21.0.66.jar

# 2) 用 JDK 21 跑
$env:JAVA_HOME = "C:\Users\<你>\.jdks\temurin-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

cmd /c "gradlew.bat build --console=plain"     # 产物 build/libs/cannonworks-0.2.0.jar
cmd /c "gradlew.bat test --console=plain"      # 17 个纯逻辑/资源测试
cmd /c "gradlew.bat runServer"                 # 开发服，目录 run-1.21.1/
cmd /c "gradlew.bat runClient"                 # 开发客户端，目录 run-1.21.1-client/
```

运行期联动：把 Waystones/Balm 的 jar 放进 `run-1.21.1/mods/`（服务端）和 `run-1.21.1-client/mods/`（客户端）。
开发服默认端口 25566、RCON 25576（见 `run-1.21.1/server.properties`）。

### 两个构建坑

- **Gradle daemon 必须钉 `-Dfile.encoding=GBK`**：JDK 18+ 默认 UTF-8，而 Gradle 写测试 worker 的 classpath argfile 用 daemon 默认字符集、`java.exe` 读 argfile 用系统代码页（GBK）。项目路径含中文时不一致会 `ClassNotFoundException`。已写进 `gradle.properties`；UTF-8 只在 `compileJava.options.encoding` / `processResources.filteringCharset` 上钉。
- 首次解析 NeoForged 仓库偶尔会被本机代理打断 TLS 握手（`Remote host terminated the handshake`）。用 curl 预热/重试即可，Gradle 缓存会逐步补齐；不是工程配置问题。

## 3. 主要 API 迁移

| 1.20.1 / Forge | 1.21.1 / NeoForge |
| --- | --- |
| `net.minecraftforge.*` 事件/注册/网络 | `net.neoforged.neoforge.*`（`IEventBus`、`ModConfigSpec`、`NeoForge.EVENT_BUS`） |
| `RegistryObject<T>` | `DeferredHolder<R, T>`；`DeferredRegister.create(Registries.BLOCK, id)` |
| `SimpleChannel` + 手工 id | `RegisterPayloadHandlersEvent` + `CustomPacketPayload` + `StreamCodec`（`playToServer` / `playToClient`） |
| `NetworkHooks.openScreen` | `ServerPlayer.openMenu(menuProvider, buf -> buf.writeBlockPos(pos))` |
| `BlockEntity#saveAdditional(tag)` / `load(tag)` | `saveAdditional(tag, HolderLookup.Provider)` / `loadAdditional(tag, HolderLookup.Provider)` |
| `ItemStack#setHoverName/resetHoverName/hasCustomHoverName` | `DataComponents.CUSTOM_NAME`（`set/remove/has`） |
| `Block#use(...)` | `Block#useWithoutItem(...)`（1.20.5 起拆分 `useItemOn` / `useWithoutItem`） |
| `MenuScreens.register(...)` in `FMLClientSetupEvent` | `RegisterMenuScreensEvent#register(...)` |
| `BaseEntityBlock` 无 codec | 实现 `codec()`（`simpleCodec(CannonBlock::new)`） |
| `SynchedEntityData#defineSynchedData()` | `defineSynchedData(SynchedEntityData.Builder)` |
| `TickEvent.ServerTickEvent` | `ServerTickEvent.Post` |
| `ChunkStatus`（`world.level.chunk`） | `world.level.chunk.status.ChunkStatus` |
| `IForgeMenuType.create` | `IMenuTypeExtension.create` |
| `mods.toml` | `META-INF/neoforge.mods.toml`（`type="required"` 取代 `mandatory`） |
| 数据包目录 `recipes/ advancements/ loot_tables/ tags/blocks/` | `recipe/ advancement/ loot_table/ tags/block/` |
| 配方结果 `{"item": "..."}` | `{"id": "...", "count": n}` |

`pack.mcmeta` 的 `pack_format` 升到 34（1.21.1 资源包格式）。

## 4. Waystones 21.1 API 重写

Waystones 21.1 的 API 变化最大，全部收敛在 `integration/` 两个类里：

- `IWaystone` → `Waystone`，`IMutableWaystone` → `MutableWaystone`；`getName()` 现在返回 `Component`，用 `hasName()` 判断。
- `WaystoneStyles.DEFAULT` 删除：改为按默认方块 `waystones:waystone` 反查 `WaystoneStyles.getStyle(Block)`。
- `forceTeleportToWaystone` 删除：改为两段式
  `createDefaultTeleportContext(player, waystone, ctx -> {})`（`Either<WaystoneTeleportContext, WaystoneTeleportError>`，左成功右失败）
  → `forceTeleport(context)`（`Either<List<Entity>, WaystoneTeleportError>`）。
- `WaystoneManager.updateWaystone` / `PlayerWaystoneManager.sendActivatedWaystones` 删除：改名后用 `WaystoneSyncManager.sendWaystoneUpdateToAll` 同步；激活用 `WaystonesAPI.activateWaystone` + `isWaystoneActivated`。
- 错误显示从 `getTranslationKey()` 改为 `error.getComponent()`。

## 5. 验证记录（2026-10-29）

| 项目 | 结果 |
| --- | --- |
| `gradlew compileJava` | ✅ 0 error（仅 `EventBusSubscriber.bus` 的 `[removal]` 警告，1.21.1 仍生效） |
| `gradlew test` | ✅ 17/17：ChargeMath 7、NumericInput 3、LangFiles 2、SurvivalData 5 |
| 无头服务端启动 | ✅ `Done (1.312s)!`，Waystones/Balm 同时加载，无异常 |
| 点火链路（RCON） | ✅ `/cannon block fire`：`charge 8.1 (9 powder) range 300`，与 1.20.1 口径一致 |
| 落点/占位块/掉落 | ✅ 底座 `cannon_part` 生成；大炮掉落自身；`ChargeScale=2` |
| 石碑空投 | ✅ 命名「南坡哨塔」→ 落点 `石碑「南坡哨塔」已放置 @ -1, 78, -50`（Waystones 21.1 真 API） |
| 客户端启动 + 进服 | ✅ quickPlay 连入开发服；无类加载/渲染注册异常 |
| 大炮模型渲染 | ✅ 后台窗口截图确认模型正常显示 |
| 装填界面 | ✅ 通过开发命令 `/cannon block open <pos>` 打开，标题/装药/角度/X-Z/模式/FIRE 均渲染 |

### 未覆盖

- **Xaero 小地图**：本机 1.21.1 环境没装 Xaero，`XaeroBridge` 仍是反射软依赖，未实机验证（1.21.1 的 Xaero 反射链在评估阶段确认过签名一致）。
- **GUI 交互**：小数输入、命名框、滑条拖动这些鼠标/键盘路径没有做自动化点击（Windows 前台锁会拦截后台窗口的模拟输入，没去抢主人正在玩的窗口），但对应的服务端逻辑（`setCharge`/`renameCargo`）已用命令验证；界面本身能正常打开渲染。
- **玩家开火路径**：`fire(player)` 的发射者参数走 GUI 发包；无头测试用控制台/命令开火（`fire(null)`），链路其余部分一致。

## 6. 与主线的关系

- `main` 继续维护 1.20.1 / Forge 47。
- 本分支独立构建，**不共用一个 jar**；切换分支后需重新 `gradlew build`。
- 1.21.1 版本号 0.2.0，Release 资产：`cannonworks-0.2.0.jar`。
- 如需把两版放进同一个 Release，请分别上传两个 jar 并在正文写明适用的 MC/加载器/Java 版本（不要混用）。
