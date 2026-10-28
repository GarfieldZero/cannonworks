# M6 打磨 · 本地化与单元测试（2026-10-28）

## 1. 本地化

### 做了什么

- 界面里所有硬编码中文抽成语言键：`zh_cn.json` / `en_us.json` 各 29 条（原 4 条 + 新增 25 条）。
- 覆盖 `CannonScreen`：滑条标签、槽位标签、按钮、tooltip、状态行、键盘提示、命名框与手输框提示。
- `FireMode` 新增 `translationKey()`：GUI 走语言键；`label()` 保留给服务端日志和命令反馈
  ——服务端没有「玩家语言」，硬翻译反而会让中文服主的日志变成英文。

### 约定（以后加界面文字照这个来）

- 键名格式：`gui.cannonworks.cannon.<区域>.<名称>`，例如 `gui.cannonworks.cannon.status.ready`。
- 带变量的用 `%s`：`Component.translatable("...", arg)`。
- 服务端日志/命令反馈**不要**用 `translatable`，那是按服务端默认语言渲染的。
- `LangFilesTest` 会在构建期检查「中英键一一对应」和「同一文件无重复键」。
- 英文比中文宽：这次为了放下 `Solve` / `Airburst`，把解算按钮 20→30、模式按钮 46→50、
  目标 X/Z 输入框 38→33。以后改布局先按英文宽度量一遍。

### 刻意没做的

`CannonCommand` 的命令反馈、`PayloadDelivery` 的落点报告、控制台日志仍是中文：
它们面向服主和日志，不属于「玩家语言」环境；真要翻译得把返回字符串改成 `Component` 传参，
等有英文玩家服再说。

## 2. 单元测试

### 怎么跑

```powershell
.\gradlew.bat test     # 只跑测试
.\gradlew.bat build    # 构建；check 依赖 test，所以会先跑测试
```

当前 12 个用例：

| 测试类 | 覆盖 |
| --- | --- |
| `ChargeMathTest`（7） | 火药向上取整（8.1→9）、8.5/9.0 射程必须不同、火药不够按整数份封顶、旧存档 8→80、新存档 6.4 份不被误乘、结果夹在 0..640 |
| `NumericInputTest`（3） | 小数、逗号当小数点、半截输入（`-`、`.`、空）不提交 |
| `LangFilesTest`（2） | 中英键一致、无重复键 |

### 为什么测试不需要 Minecraft

`ChargeMath` / `NumericInput` 是纯函数，`LangFilesTest` 只读 JSON。
`CannonBlockEntity` 现在调用 `ChargeMath`，所以「8.1 扣 9 个火药」这类规则由测试守着，
而方块实体本身不需要构造。

### 未覆盖

- `Ballistics`（用了 `Mth.clamp` 等 MC 工具类）：要单测就得把 MC 类放进测试 classpath，
  目前判断不值得；数值行为靠无头 RCON 验收。
- GUI 交互（点数值弹框、命名框）只能实机点，没有自动化。

## 3. 构建环境踩坑（下次别重踩）

1. **Maven Central 的 TLS 在这台机器上过不去**（curl 正常，JVM 握手被断）。
   → `build.gradle` 里 JUnit 走阿里云镜像，并且用 `content { includeGroupByRegex ... }`
   只放 JUnit 相关 group。不加过滤的话，ForgeGradle 的编译配置会顺带去镜像重拉 netty。
2. **中文项目路径 + Gradle 测试 worker 的 argfile**：
   Gradle 用守护进程的默认字符集写 `@gradle-worker-classpath...txt`，而 `java.exe`
   用系统代码页读。以前给 daemon 加了 `-Dfile.encoding=UTF-8`，于是 argfile 是 UTF-8、
   读取按 GBK，路径里的中文全变乱码，测试类报 `ClassNotFoundException`
   ——看起来像「测试没编译」，其实是 classpath 没传进去。
   → 去掉 daemon 的 `file.encoding`，让它跟系统一致；需要 UTF-8 的地方显式钉住：
   `compileJava.options.encoding = 'UTF-8'` 和 `processResources.filteringCharset = 'UTF-8'`。

## 4. 部署

`build/libs/cannonworks-0.1.0.jar`（167052 字节，SHA256 `129D…3FB3`）已覆盖到测试环境；
上一版备份在 `.cache/deploy-backup-ekey-cannonworks-0.1.0.jar`。
中文客户端的界面文案与原来基本一致；英文客户端不再中英混排。
