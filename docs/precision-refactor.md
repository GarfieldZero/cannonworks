# 精度改造（第一步：数据层）执行清单

> 目标：把装药从「整数份」改成「0.1 份精度」，为后续 GUI 小数输入打底。
> 当前约定：**内部一律用「十分之一」整数存储**（80 = 8.0 份），避免浮点传输与 NBT 兼容问题。

## 1. 方块实体 `CannonBlockEntity`

| 位置 | 改动 |
| --- | --- |
| `MAX_CHARGE = 64` | 改为 `640`（单位：0.1 份），注释写明 |
| 字段 `private int charge = 8` | 改为 `= 80` |
| `getRangeExact(int usedCharge)` | `usedCharge * RANGE_PER_CHARGE` → `usedCharge / 10.0 * RANGE_PER_CHARGE` |
| `fire()` 中的 `usedPowder` | 改为：`int needed = (int) Math.ceil(charge / 10.0);` `int usedItems = Math.min(needed, propellant.getCount());` `int usedChargeTenths = usedItems * 10;` |
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

- [ ] 装药 8.0 / 8.5 / 9.0 能打出不同距离，且 8.1 只扣 9 个火药（向上取整）；
- [ ] 界面显示一位小数，拖动手感不跳变；
- [ ] 铁砧改名的石碑打出去后，落点石碑保留该名字；
- [ ] 旧存档（`Charge: 8`）加载后按 0.8 份处理——**需要迁移**：若 `Charge <= 64` 视为旧单位并 ×10。
