# Cannon Works · 大炮计划

Minecraft **1.20.1 / Forge 47.4.1** 的大炮主题模组，与「传送石碑（Waystones）」联动。

> 当前状态：**开发环境已就绪**（骨架工程 + 可编译 + Waystones 软依赖已打通）。
> 大炮的具体玩法尚未实现，等待需求确认后继续。

---

## 1. 技术栈

| 项目 | 版本 | 备注 |
| --- | --- | --- |
| Minecraft | 1.20.1 | 与你现有的「剑与王国」整合包一致 |
| Forge | 47.4.1 | 同上 |
| Java | 17 (JDK 17.0.12) | 已装在 `C:\Program Files\Java\jdk-17` |
| Gradle | 8.8（wrapper 自带，走腾讯云镜像） | 首次构建会自动下载 |
| 构建插件 | ForgeGradle 6.0.x | |
| 映射 | official 1.20.1 | Mojang 官方名 |
| 传送石碑 | Waystones 14.1.21 (+ Balm 7.3.44) | 软依赖，jar 放在 `libs/` |

## 2. 目录结构

```
.
├─ build.gradle              # ForgeGradle 构建脚本（Waystones 依赖在此声明）
├─ gradle.properties         # 版本号 / mod 元信息（改这里即可）
├─ libs/                     # 本地联动模组 jar（不参与打进产物）
│  ├─ waystones-14.1.21.jar
│  └─ balm-7.3.44.jar
├─ src/main/java/com/cannonworks/
│  ├─ CannonWorks.java               # 主类 @Mod
│  ├─ command/CannonCommand.java     # 调试命令 /cannon ...
│  └─ integration/WaystoneTargets.java  # 唯一直接引用 Waystones API 的地方
├─ src/main/resources/
│  ├─ META-INF/mods.toml     # waystones 声明为 optional + AFTER
│  └─ pack.mcmeta
└─ docs/                     # 笔记（Waystones API 摘要等）
```

## 3. 常用命令

```powershell
# 编译 + 打包（产物：build/libs/cannonworks-0.1.0.jar）
.\gradlew.bat build

# 启动开发客户端（会自动加载 libs 里的 Waystones）
.\gradlew.bat runClient

# 启动开发服务端
.\gradlew.bat runServer
```

首次构建会下载 Forge/Minecraft 并反编译，约 5–15 分钟；之后增量构建很快。

## 4. 装进你自己的整合包测试

1. `.\gradlew.bat build`
2. 把 `build/libs/cannonworks-0.1.0.jar` 复制到整合包的 `mods/` 目录
3. 同目录还要有 **Waystones** 与它前置 **Balm**（`libs/` 里那两个 jar 就是）
4. 进入世界后执行：

```
/cannon waystones     # 列出你已激活的石碑
/cannon nearest       # 最近的一座
/cannon tp <名字>      # 强制传送（绕过冷却与经验消耗）
```

## 5. 联动的设计约定

- **软依赖**：`mods.toml` 里 `waystones` 是 `mandatory=false, ordering=AFTER`；没装 Waystones 时本模组照常加载。
- **类型隔离**：只有 `WaystoneTargets` 引用 `net.blay09.mods.waystones.*`。其他任何类调用前必须先 `ModList.get().isLoaded("waystones")`，否则会 `NoClassDefFoundError`。
- **可用的联动入口**（后续做「大炮」时会用到）：
  - 读取：`WaystonesAPI.getActivatedWaystones / getNearestWaystone / getWaystone(UUID) / getWaystoneAt(pos)`
  - 传送：`forceTeleportToWaystone`（无视消耗）、`createDefaultTeleportContext` + `tryTeleport`（走正常消耗/事件）
  - 落点：`IWaystone#resolveDestination(ServerLevel)` → `TeleportDestination(level, Vec3, Direction)`
  - 事件：`WaystoneTeleportEvent.Pre / Post`（Balm 事件总线）
- 详见 [`docs/waystones-integration-notes.md`](docs/waystones-integration-notes.md)。

## 6. 待确认（影响后续实现）

1. 大炮的**核心玩法**：固定炮台 / 手持发射器 / 载具？发射的是实体炮弹还是瞬时射线？
2. 「联动」具体指什么：炮弹落点把玩家传送到石碑？大炮本身作为石碑之间的传送装置？炮击可以作为跨维度投送？
3. 模组的正式名字与 modid（现在是占位的 `cannonworks` / `Cannon Works`）。
