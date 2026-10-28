# 精度改造（第一步：数据层）执行清单

> 目标：把装药从「整数份」改成「0.1 份精度」，为后续 GUI 小数输入打底。
> 当前约定：**内部一律用「十分之一」整数存储**（80 = 8.0 份），避免浮点传输与 NBT 兼容问题。

## 1. 方块实体 `CannonBlockEntity`

| 位置 | 改动 |
| --- | --- |
| `MAX_CHARGE = 64` | 改为 `640`（单位：0.1 份），注释写明 |
| 字段 `private int charge = 8` | 改为 `= 80` |
| `getRangeExact(int usedCharge)` | `usedCharge * RANGE_PER_CHARGE` → `usedCharge / 10.0 * RANGE_PER_CHARGE` |
| `fire()` 中的 `usedPowder` | 改为：`int needed = (int) Math.ceil(charge / 10.0);` `int usedItems = Math.min(needed, propellant.getCount());` `int usedChargeTenths = usedItems * 10;` ⚠ 实际实现见 §7「偏差」 |
| `propellant.shrink(usedPowder)` | 改为 `shrink(usedItems)`（1.1 份 → 扣 2 个） |
| NBT `Charge` | 不变（仍是 int，语义变为十分之一） |
| `adjust()` | 键盘微调步长要 ×10（原来 ±1 份 = ±10） |

## 2. 网络包 `CannonAimPacket`

- 字段保持 int，但语义为「十分之一」；
- GUI 发送时：`(int) Math.round(chargeDouble * 10)`；
- 服务端 `setCharge(value)` 直接接收十分之一。

## 3. 容器数据 `CannonMenu`

- `ContainerData` 传「十分之一」整数（`charge`），滑块最大值 640；
- 界面显示时 `/10.0` 保留一位小数。

## 4. 界面 `CannonScreen`

- 装药滑块范围 0..640，标签显示 `8.0`；
- 解算：`charge = ceil(distance / (300 * k) * 10)`（十分之一）；
- 后续（第二步）再加三个手动输入框，支持直接输入小数。

## 5. 传送石碑命名恢复

- `CannonBlockEntity.resolvePayload` 里，货物是石碑时读取：

```java
String custom = cargoStack.hasCustomHoverName()
        ? cargoStack.getHoverName().getString() : null;
```

- 传给 `CannonPayload.cargo(...)` 的 `cargoName`，`WaystoneDelivery.deliver` 会用它命名；
- 之后（第二步）再在 GUI 加命名输入框，免去跑铁砧。

## 6. 验收

- [x] 装药 8.0 / 8.5 / 9.0 能打出不同距离，且 8.1 只扣 9 个火药（向上取整）；
- [x] 界面显示一位小数，拖动手感不跳变；（2026-10-26 主人实机确认「瞅着没啥问题」）
- [x] 铁砧改名的石碑打出去后，落点石碑保留该名字；
- [x] 旧存档（`Charge: 8`）加载后按 0.8 份处理——**需要迁移**：若 `Charge <= 64` 视为旧单位并 ×10。

## 7. 执行记录（2026-10-26 00:30，数据层）

改动落在 5 个文件：`CannonBlockEntity`（含 NBT 迁移）、`CannonAimPacket` / `CannonControlPacket`
（只加单位注释）、`CannonMenu`（口径注释）、`CannonScreen`（滑条一位小数 + 解算 ×10 + 按键步长 ×10）。

### 7.1 与清单的一处偏差：`usedChargeTenths` 要再套一层 `min`

清单写的是 `usedChargeTenths = usedItems * 10`，但这样 8.5 份（需要 9 个火药）会算出 90，
和 9.0 份打一样远，正好违反验收第一条。正确写法：

```java
int usedChargeTenths = Math.min(charge, usedItems * 10);
```

- 火药够时：`usedItems * 10 >= charge`，所以就是请求的装药（8.1 份扣 9 个、打 8.1 份的射程）；
- 火药不够时：退回到实际到手的整数份（只有 29 个火药就最多 29.0 份）。

### 7.2 NBT 迁移多写了一个记号

只按 `Charge <= 64` 判断新旧会误伤新存档里的 6.4 份（64），所以新存档额外写 `ChargeScale=2`；
**没有**这个记号且 `Charge <= 64` 才 ×10。旧存档 `Charge:8` → 80（=8.0 份）。

### 7.3 无头验收（专用服务器 + RCON，`runServer`）

```
charge 8.1 (9 powder) range 300     # 64 个火药 → 打完剩 55
charge 8.0 (8 powder) range 296
charge 8.5 (9 powder) range 314
charge 9.0 (9 powder) range 333     # 三发都不同距离
charge 29.0 (29 powder) range 1072  # 装药 64.0 但只有 29 个火药：按 29.0 份封顶
```

- 迁移：`data remove block ... ChargeScale`（等价于旧存档只有 `Charge:8`）后读回 `Charge=80`；
- 石碑命名：带 `display.Name="北境哨站"` 的石碑打出后，落点日志为
  `石碑「北境哨站」已放置（无发射者）@ -1, 102, -50`，且 `run/world/data/waystones.dat` 里能读到该名字。

### 7.4 部署

`gradlew.bat build` → `build/libs/cannonworks-0.1.0.jar`（157153 字节）
覆盖到 `E:\LMQ\NEW\落幕曲1.5.3解压包（解压即可）\.minecraft\versions\测试环境\mods\cannonworks-0.1.0.jar`，
SHA256 与构建产物一致；旧 jar 备份在 `.cache/deploy-backup-测试环境-cannonworks-0.1.0.jar`。**需重启测试环境客户端**。

## 8. 第二步：GUI 小数手输 + 石碑命名（2026-10-26 21:05）

界面只有 176×238，滑条已经占满整行，所以没有新开一行控件，而是**点数值原地浮出输入框**：

- **手输**：点滑条右侧的数值文字 → 输入框浮在那一行上，Enter 提交、Esc 取消、点别处也当提交。
  角度按整数、装药按 0.1 份解析（`Math.round(x * 10)`），越界自动夹住；方位角绕圈（输 360 = 0）。
- **手输即最终值**：提交时会解掉「目标锁定」，否则解算逻辑会拿新装药反推俯仰、把刚打的数字改掉。
- **命名**：货物槽里放石碑时，底部键盘提示行换成命名框（最多 24 字），Enter 发
  `CannonRenameCargoPacket`；服务端 `renameCargo` 直接改物品自己的 `display.Name`，空名字 = 恢复默认
  （落点仍叫「空投点 x, z」）。拿下来、换台炮，名字都还在。
- **协议版本 1 → 2**：新增了一个包，客户端和服务端必须同版本，旧 jar 连新服务端会在握手时被拒。

新增的无头测试命令：`/cannon block name <pos> <名字>`、`/cannon block clearname <pos>`。

### 8.1 验证

- 改名：`data get block ... Inventory.Items[1]` → `{Slot: 2, id: "waystones:waystone", tag: {display: {Name: '{"text":"西岭驿站"}'}}}`；
- 清空：`clearname` 之后同一个槽只剩 `{Slot: 2, id: "waystones:waystone", Count: 1b}`，没有 `display`；
- 拒绝：往货物槽塞 TNT 再改名 → `货物槽里没有可命名的石碑`；
- 打出去：日志 `payload 传送石碑「南坡哨塔」` → `石碑「南坡哨塔」已放置（无发射者）@ 2, 102, -50`，
  并且 `run/world/data/waystones.dat` 里搜得到「南坡哨塔」；
- **没实机验证的**：点数值弹框、命名框显隐、焦点切换——这几样得在客户端里真点，代码只过了编译和逻辑审查。

### 8.2 部署

`build/libs/cannonworks-0.1.0.jar`（161938 字节）已覆盖到测试环境，SHA256 `3461…D541`；
上一步的 jar 备份在 `.cache/deploy-backup-step1-cannonworks-0.1.0.jar`。**同样要重启客户端**。

### 8.3 修复：输入框里按 E 会关界面（2026-10-28）

**现象**：命名框有焦点时按 E（原版背包键）仍然退出界面，名字里打不出字母 e。

**根因**：`AbstractContainerScreen#keyPressed` 的顺序是「先把按键交给焦点控件 → 再检查背包键、快捷栏数字键」。
`EditBox#keyPressed` 对字母键返回 false，于是 E 就落到「关闭界面」那一支；数字键还会顺手点快捷栏。
原版铁砧 `AnvilScreen` 专门重写了 `keyPressed`，用
`EditBox#keyPressed(...) || EditBox#canConsumeInput()` 就 `return true`，把按键整个吞掉。

**修法**：`CannonScreen.keyPressed` 里所有输入框（手输框、命名框、目标 X/Z）统一走 `swallowForTextBox`；
Esc 保留原意——手输/命名框里是「取消编辑」，其它输入框里还是「关界面」。

**验证**：编译通过；拿 `javap` 反编译原版 `AnvilScreen` 对照过实现顺序。
**E 键的实际手感还需要在客户端里试**（本鲸没有可自动点击的客户端）。

### 8.4 修复：命名框里的字没提交 / 地图落点显示坐标名（2026-10-28）

**现象**：在命名框里打了石碑名字，落地后石碑还是默认名；落点地图标记显示「Impact 4887, -3785」。

**原因一（真 bug）**：命名只在按 Enter 时提交。玩家打完字直接点「点火」，按钮把焦点抢走，
这一帧没有发送改名包；下一 tick `syncNameBox` 又用服务端旧值把输入框覆盖回去，
看起来就像「命名了但没生效」。
→ 现在失焦（点框外）、关界面、点火都会先走 `commitNameBox()`；提交后用一个 `pendingName`
挡住服务端回写之前的旧值，不会闪回。

**原因二（不是 bug，是显示）**：`XaeroBridge.onImpact` 一直用 `"Impact " + x + ", " + z` 起名，
那是**地图落点标记**，不是石碑名。主人看到坐标名就以为石碑没命名。
→ `XaeroMarkerPacket` 加了 `label` 字段（协议 2 → 3）：打石碑时地图标记直接用石碑名字；
没有名字时才回退到本地化的「落点 x, z」。

**证据**：服务端链路本来就是通的——21:11 那发日志是 `payload 传送石碑「sss」`，
`waystones.dat` 里也确实是 `sss`。所以这次修的是「GUI 提交」和「地图显示」两处。

**验证**：17 个单元测试全绿（语言键对齐覆盖新键）；构建通过并部署（169311 字节，SHA256 `E95C…2B51`）。
GUI 失焦提交和地图标记要实机点一下才算数。



