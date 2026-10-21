# Cannon Works · 大炮计划

Minecraft **1.20.1 / Forge 47.4.1** 的大炮主题模组，与「传送石碑（Waystones）」联动。

> 当前状态：**M1 炮体已落地**——大炮方块 + 装填界面 + 可俯仰炮管模型，已经能「放炮 → 装填 → 调角 → 点火」完整开火。
> M2 负载系统（石碑/草垛/水桶/填充物）此前已验证。下一步：按实机观感打磨模型与界面。

## 怎么打一炮

1. 创造模式物品栏（功能方块页）拿「大炮」，或工作台合成（8 铁锭 + 1 火药）。
2. 放下大炮，**右击**打开装填界面。
3. 左槽放**火药**（1 份 = 300 格射程，最多 40 份 = 12 km），右槽放**负载**：
   - 传送石碑物品 → 落点立碑并给你激活（物品带自定义名字就用那个名字）
   - 干草垛 / 水桶 → 软着陆，不炸坑
   - 其他方块 → 炸坑 + 空投该方块（一次消耗整叠）
4. 用 `俯仰 / 方位 / 装药` 三组 +/- 调好，点「点火」。
5. 炮弹飞 10 秒~4 分半，落地效果见上方表格。
> 设计文档见 [`docs/design-v1.md`](docs/design-v1.md)。

## 玩法速览

测距 → 定方位 → 算装药 → 调俯仰 → 点火 → 炮弹飞 10~30 秒 → 落地：弹坑 + 负载生效。
核心价值：**不用跑图，把传送石碑（或人）打到没去过的坐标。**

## 0. 环境自检结果（2026-10-21）

| 检查项 | 结果 |
| --- | --- |
| JDK 17 | ✅ `C:\Program Files\Java\jdk-17`（17.0.12） |
| Gradle 8.8 wrapper | ✅ 已配腾讯云镜像，首次构建自动下载 |
| `gradlew build` | ✅ 产出 `build/libs/cannonworks-0.1.0.jar` |
| `gradlew runServer` 冒烟测试 | ✅ `waystones` + `balm` + `cannonworks` 同时加载，`Done (4.287s)!` |
| 日志自检 | ✅ `Cannon Works loaded (Waystones present: true)` |
| `gradlew runClient` | ⏳ 未由我启动（会弹出游戏窗口），你随时可以自己跑 |

两个踩过的坑，已在工程里修好：

1. **Balm 的 Mixin 会让 dev 环境启动崩溃**（`m_129861_` 找不到目标方法）——
   已加 `mixin.env.remapRefMap=true` + `refMapRemappingFile`，见 `build.gradle` 的 `runs.configureEach`。删掉这两行必定崩。
2. **中文写入 `mods.toml` 会乱码**——`gradle.properties` 按 ISO-8859-1 读取，
   所以中文描述改用 `\uXXXX` 转义，并给 Gradle 加了 `-Dfile.encoding=UTF-8`。

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

## 4. 安装到整合包 / 本地测试

### 4.1 需要哪几个 jar

| jar | 作用 | 你机器上已有 |
| --- | --- | --- |
| `cannonworks-0.1.0.jar` | 本模组 | 本次构建产物 |
| `waystones-forge-1.20.1-14.1.x.jar` | 传送石碑 | ✅ 14.1.17 |
| `balm-forge-1.20.1-7.3.x-all.jar` | **Waystones 的硬前置**，缺了 Waystones 直接崩 | ✅ 7.3.34 |

> Balm 是 Waystones 的 mandatory 依赖，不是本模组的。本模组对 Waystones 是**软依赖**：
> 不装也能正常进游戏，只是 `/cannon waystones` 之类会提示「未检测到」。
> 已核对：本模组调用的 4 个 Waystones API 在 14.1.17 里全部存在，二进制兼容。

### 4.2 已经装好的位置（2026-10-21）

```
E:\.minecraft\versions\剑与王国\mods\cannonworks-0.1.0.jar
```

用 PCL 启动「剑与王国」即可。**卸载 = 删掉这个 jar**；目前没有自定义方块/物品，
只有实体和命令，不会污染存档。

### 4.3 测试注意

1. **新建一个世界并开启作弊**，别用现有存档——弹坑会炸地形，开发版也随时可能崩。
2. `/cannon` 需要权限等级 2（单人世界开作弊即可）。
3. 264 个 mod 的整合包首次加载较慢；如果崩了，把 `E:\.minecraft\versions\剑与王国\crash-reports\` 里的文件发我。

### 4.4 可用的测试命令

```
/cannon testfire <距离> [俯仰角]                        空包弹（只炸坑）
/cannon testfire waystone <距离> [俯仰角] [名字]       送一座传送石碑过去，落地自动为发射者激活
/cannon testfire buffer hay <距离> [俯仰角]             缓冲物·草垛：软着陆，不炸坑
/cannon testfire buffer water <距离> [俯仰角]           缓冲物·水桶：软着陆，水放在落点旁边
/cannon testfire filler <方块> <数量> <距离> [俯仰角]   填充物：炸坑 + 空投建材
/cannon testfire at <x> <y> <z> [俯仰角]                朝绝对坐标打
/cannon debug                                           列出在飞的炮弹（tick、坐标、负载、载体状态）
/cannon waystones / nearest / tp <名字>                  传送石碑联动
```

例子：

```
/cannon testfire waystone 800 45          # 800 格外落一座已激活的石碑，然后 /cannon tp <名字> 过去
/cannon testfire waystone 800 45 北境哨站  # 同上，但石碑直接叫「北境哨站」
/cannon testfire filler minecraft:obsidian 64 500 30     # 空投 64 块黑曜石
/cannon testfire buffer hay 300 20        # 给 300 格外的落点铺草垛，人/货物摔不坏
```

> 名字必须写在**最后**（greedy 参数），所以想命名时俯仰角不能省。
> 不写名字时自动叫「空投点 x, z」。

> `400` 是**距离**，不是坐标。旧写法 `testfire 400 ~ ~` 里的 400 是绝对 X 坐标，
> 会导致所有炮弹都飞向世界坐标 x=400 的那个点——已改成距离语义。

### 4.5 干净的备用环境

```powershell
cd "G:\我的世界大炮计划"
.\gradlew.bat runClient
```

`run/` 里只装了 Forge 47.4.1 + Waystones 14.1.21 + Balm 7.3.44 + 本模组，
不掺杂整合包里的 264 个 mod，排查「是不是本模组的问题」时用这个。

## 5. 联动的设计约定

- **软依赖**：`mods.toml` 里 `waystones` 是 `mandatory=false, ordering=AFTER`；没装 Waystones 时本模组照常加载。
- **类型隔离**：只有 `WaystoneTargets` 引用 `net.blay09.mods.waystones.*`。其他任何类调用前必须先 `ModList.get().isLoaded("waystones")`，否则会 `NoClassDefFoundError`。
- **可用的联动入口**（后续做「大炮」时会用到）：
  - 读取：`WaystonesAPI.getActivatedWaystones / getNearestWaystone / getWaystone(UUID) / getWaystoneAt(pos)`
  - 传送：`forceTeleportToWaystone`（无视消耗）、`createDefaultTeleportContext` + `tryTeleport`（走正常消耗/事件）
  - 落点：`IWaystone#resolveDestination(ServerLevel)` → `TeleportDestination(level, Vec3, Direction)`
  - 事件：`WaystoneTeleportEvent.Pre / Post`（Balm 事件总线）
- 详见 [`docs/waystones-integration-notes.md`](docs/waystones-integration-notes.md)。

## 6. 下一步（M2）

1. 负载槽：特殊负载（传送石碑）/ 缓冲物（草垛、水桶）/ 填充物（方块）三栏。
2. 落地：三维球形弹坑（已完成）+ 支撑位查找 + 石碑放置并激活发射者。
3. 出膛弹道净空检查：开火前预告「预计在 x 处被拦截」。
