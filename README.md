# Cannon Works · 大炮计划

Minecraft **1.20.1 / Forge 47.4.1** 的大炮主题模组，与「传送石碑（Waystones）」联动。

> 当前状态：**M1 炮体已落地**——大炮方块 + 装填界面 + 可俯仰炮管模型，已经能「放炮 → 装填 → 调角 → 点火」完整开火。
> M2 负载系统（石碑/草垛/水桶/填充物）此前已验证。下一步：按实机观感打磨模型与界面。
>
> **2026-10-28 更新：** 碰撞改为按炮台 yaw 动态裁切的旋转形状（5×5×2 查询网格），解决“转到侧面就穿模 / 空心处空气墙”；底部石碑命名框改成无边框内嵌样式。模型仍为 4×2.5 底座 + 6 格炮管。详见 [`docs/cannon-v3-implementation.md`](docs/cannon-v3-implementation.md)。

## 怎么打一炮

> **生存模式**：工作台 8 铁锭 + 1 火药合成（拿到材料后配方书自动解锁）；
> 用镐子挖更快（硬度 5，空手约 25 秒）；拆**任意**底座方块都会整台掉出来，里面的火药和负载一起掉。

1. 创造模式物品栏（功能方块页）拿「大炮」，或工作台合成（8 铁锭 + 1 火药）。
2. 放下大炮，**右击**打开装填界面。
3. 左槽放**火药**（**0.1 份精度**：1 份 = 300 格射程，最多 64.0 份；装药按整份扣火药，8.1 份扣 9 个），右槽放**负载**：
   - 传送石碑物品 → 落点立碑并给你激活（物品带自定义名字就用那个名字）
   - 干草垛 / 水桶 → 软着陆，不炸坑
   - **TNT → 大爆破**（半径 6，约 220~230 格方块；消耗 1 个）
   - **传送板 → 落地部署一块传送板**（软着陆，不炸坑）
   - **末影珍珠 → 落地瞬间把你传送到落点**（消耗 1 个；你就是「人肉炮弹」）
   - 其他方块 → 炸坑 + 空投该方块（一次消耗整叠）
4. 瞄准：**滑条**大范围拖（装药显示到 0.1 份），**↑↓ ←→** 微调俯仰/方位（每次 1°，Shift 5°），**PgUp/PgDn** 调装药（每次 1.0 份）。
   **想输精确值就点滑条右边的数值**，会在原地弹出输入框：Enter 提交、Esc 取消；装药可以打小数（如 `8.1`）。
   **货物槽放石碑时，最下面那行会变成命名框**，打完名字按 Enter 即可，不用再抱去铁砧。最后点「点火」。
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
├─ libs/                     # 本地联动模组 jar（不入库，需自行下载；不参与打进产物）
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

> **构建前准备**：`libs/` 不入库（不随仓库分发第三方模组）。从 CurseForge / Modrinth 下载
> **Waystones 14.1.21** 和 **Balm 7.3.44**（Forge 1.20.1），按上面的文件名放进 `libs/`，
> 再跑 `gradlew.bat build`。只跑单元测试（`gradlew.bat test`）不需要这两个 jar。

## 3. 常用命令

```powershell
# 编译 + 打包（产物：build/libs/cannonworks-0.1.0.jar；会先跑单元测试）
.\gradlew.bat build

# 只跑单元测试（装药规则 / 手输解析 / 语言键对齐，不需要启动游戏）
.\gradlew.bat test

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
/cannon testfire tnt <距离> [俯仰角]                    TNT：半径 6 大爆破
/cannon testfire pearl <距离> [俯仰角]                  末影珍珠：落地把人传送过去
/cannon testfire warpplate <距离> [俯仰角]              传送板：落地部署一块传送板
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

## 6. 路线图状态（2026-10-28）

1. 负载槽：特殊负载（传送石碑）/ 缓冲物（草垛、水桶）/ 填充物（方块）三栏 —— ✅
2. 落地：三维球形弹坑 + 支撑位查找 + 石碑放置并激活发射者 —— ✅（发射者激活待实机复验）
3. ~~出膛弹道净空检查~~ —— **不做**（2026-10-28 决定）：打中山体就地落点是预期行为，
   不需要为每一发找无遮挡航线。
4. 载人：用末影珍珠实现「人肉炮弹」，不做真正的乘客模式 —— ✅（替代方案）
5. 火控：输入目标 X/Z 反解方位与装药，俯仰由玩家定 —— ✅（够用）
6. 地图：Xaero 小地图/世界地图的弹道点与落点标记 —— ✅（原计划 JourneyMap，整合包适配太少）

剩下只有 **M6 打磨**：美术、配置、平衡、多人测试。里程碑细节见 [`docs/design-v1.md`](docs/design-v1.md) §9。

## 7. 常见问题

- **构建报 `Failed to validate certificate for host 'https://maven.minecraftforge.net/'`**：
  本机网络环境（代理 / 杀软）让 ForgeGradle 的证书校验失败，在命令后加
  `-Dnet.minecraftforge.gradle.check.certs=false` 再构建即可。这是环境问题，不是代码问题。
- **构建报找不到 `local:waystones`**：`libs/` 不入库，按 §2 说明下载 Waystones 14.1.21
  与 Balm 7.3.44 放进 `libs/`。
- **只想跑单元测试**：`gradlew.bat test`，不需要 `libs/` 里的联动模组。
