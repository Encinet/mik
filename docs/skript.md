# MIK Skript API

MIK 在 Skript 已安装并启用时，以 addon 名称 `MIK` 注册玩家语言、客户端版本、身份、
AFK、PVP 和非欧空间 API。本文只描述 MIK 自己提供的 Skript 语法和行为。

## 通用规则

### 玩家与返回数量

所有 MIK 表达式接受 `%players%`，可传入一个或多个在线玩家。除
`mik pvp override ids` 外，表达式是否返回单值与输入玩家表达式一致。

所有属性表达式同时支持标准形式和所有格形式：

```applescript
set {_language} to mik language of player
set {_language} to player's mik language
```

可选属性不存在时，单玩家读取会得到无值；复数读取会省略该玩家的值。因此，可选属性
的返回列表不保证与输入玩家列表保持相同索引。需要区分玩家时，应逐个读取并先判断值
是否存在。

MIK API 只接受 Bukkit `Player`，不读取离线玩家。状态访问最终会在 Paper 主线程执行；
异步调用会同步切回主线程并等待结果。

### 返回类型

| 类型 | 用途 |
| --- | --- |
| `String` | 语言 ID、客户端版本、身份、AFK 来源和 override 标识 |
| `Boolean` | AFK、PVP、override 和战斗锁定状态 |
| `Timespan` | AFK 持续时间与剩余时间 |
| `Number` | PVP override 优先级 |

## 表达式

下表使用 `mik ... of %players%` 作为标准写法。每项也支持
`%players%'[s] mik ...` 所有格写法。

| 表达式 | 类型 | 行为与无值条件 |
| --- | --- | --- |
| `mik language of %players%` | `String` | MIK 最终生效的语言 ID，例如 `zh_cn`、`zh_tw`、`en_us`；考虑玩家设置和自动回退 |
| `mik client version of %players%` | `String` | ViaVersion 提供的可读客户端版本；ViaVersion 不可用或未知时返回 `unknown` |
| `mik role of %players%` | `String` | 按最高权限返回 `manager`、`helper`、`member`、`default` |
| `mik afk state of %players%` | `Boolean` | 当前是否处于 MIK AFK |
| `mik afk message of %players%` | `String` | 没有自定义消息或不在 AFK 时无值 |
| `mik afk source of %players%` | `String` | `manual`、`automatic`、`skript`；不在 AFK 时返回 `none` |
| `mik afk duration of %players%` | `Timespan` | 不在 AFK 时无值；从本次进入 AFK 的时间开始计算 |
| `mik pvp state of %players%` | `Boolean` | MIK 最终 PVP 状态，已合并持久偏好和临时 override |
| `mik pvp preference of %players%` | `Boolean` | 玩家持久保存的原始偏好，不受 override 影响 |
| `mik pvp overridden state of %players%` | `Boolean` | 是否存在至少一个尚未过期的 override |
| `mik pvp override value of %players%` | `Boolean` | 当前获胜 override 的值；没有 override 时无值 |
| `mik pvp override id of %players%` | `String` | 当前获胜 override 的 ID；没有 override 时无值 |
| `mik pvp override owner of %players%` | `String` | 当前获胜 override 所属脚本的名称/路径；没有 override 时无值 |
| `mik pvp override priority of %players%` | `Number` | 当前获胜 override 的优先级；没有 override 时无值 |
| `mik pvp override remaining time of %players%` | `Timespan` | 只有获胜 override 有时限时才有值；永久 override 或没有 override 时无值 |
| `mik pvp override ids of %players%` | `String` 列表 | 当前脚本为这些玩家创建的有效 ID，按文本排序；始终是复数表达式 |
| `mik combat tagged state of %players%` | `Boolean` | 是否处于 MIK 战斗锁定 |
| `mik combat tag remaining time of %players%` | `Timespan` | 未处于战斗锁定时无值 |

示例：

```applescript
set {_language} to mik language of player
set {_version} to mik client version of player
set {_role} to mik role of player

if mik afk message of player is set:
    set {_message} to mik afk message of player

loop all players:
    set {_effectivePvp} to mik pvp state of loop-player
```

## 条件

| 正向条件 | 否定条件 | 判断内容 |
| --- | --- | --- |
| `%players% is/are mik afk` | `%players% isn't/is not/aren't/are not mik afk` | MIK AFK 状态 |
| `%players% has/have mik pvp enabled` | `%players% doesn't/does not/do not/don't have mik pvp enabled` | 最终 MIK PVP 状态 |
| `%players% is/are mik pvp overridden` | `%players% isn't/is not/aren't/are not mik pvp overridden` | 是否存在有效 override |
| `%players% is/are mik combat tagged` | `%players% isn't/is not/aren't/are not mik combat tagged` | MIK 战斗锁定状态 |
| `%players% has/have [the] mik pvp override %string%` | `%players% doesn't/does not/do not/don't have [the] mik pvp override %string%` | 当前脚本是否拥有指定 ID 的 override |

override 条件按“当前脚本所有者 + ID”查找，不会匹配其他脚本的同名 ID。ID 为空或超过
长度限制属于无效输入，不应依赖无效 ID 的条件结果。

## AFK

### 设置与清理

```applescript
set %players% to mik afk
set %players% to mik afk with message %string%
set %players% to mik afk with message %string% silently

clear mik afk state of %players%
remove mik afk state from %players%
clear mik afk state of %players% silently
```

完整注册 pattern 为：

```text
set %players% to mik afk [with [the] message %-string%] [silently]
(clear|remove) [the] mik afk state (of|from) %players% [silently]
```

### 消息规则

- 自定义消息最多 20 个 Unicode code point。
- MIK 会折叠连续空白并去除首尾空白。
- 空字符串或只包含空白的消息等同于没有自定义消息。
- `mik afk message` 只在存在自定义消息时返回值。

### 状态与副作用

- 首次由脚本设为 AFK 时，来源变为 `skript` 并记录开始时间。
- MIK 会应用 AFK 碰撞和伤害保护、清理附近怪物目标、更新展示并通知状态监听器。
- 已经 AFK 的玩家再次被设置时，会更新消息和来源，保留原开始时间，不重复广播进入消息。
- `silently` 只关闭进入或退出 AFK 的全服聊天广播，不跳过其他状态更新。
- 清理后会恢复活动追踪、碰撞/保护状态和展示，并通知状态监听器。
- 清理一个并非 AFK 的玩家不会改变状态。
- AFK 状态不写入持久数据；30 分钟内短暂重连会恢复原状态及来源，超过保留时间或服务
  重启后不会恢复。

示例：

```applescript
if player is not mik afk:
    set player to mik afk with message "活动准备中" silently

set {_source} to mik afk source of player
set {_duration} to mik afk duration of player

clear mik afk state from player silently
```

## PVP 状态模型

MIK PVP 分为三层：

1. **偏好（preference）**：玩家持久保存的原始开关。
2. **override 集合**：脚本添加的内存状态，可设置优先级和时限。
3. **最终状态（effective state）**：获胜 override 的值；没有 override 时回退到偏好。

`mik pvp state` 返回最终状态；`mik pvp preference` 返回原始偏好。临时活动或区域规则
应使用 override，不应为了临时效果改写玩家偏好。

## PVP 偏好

```applescript
set mik pvp preference of %players% to %boolean%
```

此 effect 直接持久保存偏好并通知 MIK 战斗状态控制器。它不同于玩家手动执行 `/pvp`
的交互流程，不会替脚本执行“战斗中禁止关闭”等命令层检查。脚本必须自己保证修改符合
业务规则。

## PVP Override

### 创建或更新

```applescript
set mik pvp override %string% of %players% to %boolean%
set mik pvp override %string% of %players% to %boolean% with priority %number%
set mik pvp override %string% of %players% to %boolean% with priority %number% for %timespan%

force mik pvp on for %players% using id %string%
force mik pvp off for %players% with override id %string% with priority %number% for %timespan%
```

完整注册 pattern 为：

```text
set [the] mik pvp override %string% of %players% to %boolean% [with priority %-number%] [for %-timespan%]
force [the] mik pvp (on|off) for %players% (using|with) [override] [id] %string% [with priority %-number%] [for %-timespan%]
```

`using`/`with` 以及可选的 `override`、`id` 关键字属于同一语法。需要动态布尔值时，使用
`set mik pvp override ... to %boolean%`。

### 清理

```applescript
clear mik pvp override %string% from %players%
clear mik pvp override %string% of %players%
clear all mik pvp overrides from %players%
```

完整注册 pattern 为：

```text
clear [the] mik pvp override %string% (of|for|from) %players%
clear all [of the] mik pvp overrides (of|for|from) %players%
```

清理只能删除当前脚本所有者创建的 override，不会删除其他脚本的同名 ID。

### ID 与所有者

- ID 会去除首尾空白，不能为空，最长 80 个 Unicode code point。
- ID 区分大小写；推荐使用稳定的 ASCII 小写标识，例如 `event:arena`。
- 所有者由当前 Skript 文件的名称和路径自动生成，脚本不能指定或伪造。
- 同一玩家、同一所有者、同一 ID 再次设置时，会替换旧值并刷新先后顺序。
- `mik pvp override ids` 只列出当前脚本所有者的 ID。
- `mik pvp override owner` 返回当前获胜 override 的所有者，可能属于另一个脚本。

所有者隔离用于避免脚本间 ID 冲突，不是权限隔离。

### 优先级

- 默认优先级为 `0`，负数有效。
- 输入数值最终限制在 Java `int` 范围。
- 数字更大的优先级获胜。
- 优先级相同时，最后设置或更新的 override 获胜。

### 时限

- 不写 `for` 表示永久到生命周期清理。
- `for 0 seconds` 会立即过期。
- 有时限的 override 到期后，下一层 override 自动接管。
- 没有其他 override 时，最终状态回到玩家偏好。
- `mik pvp override remaining time` 只返回当前获胜且有时限的 override 剩余时间。

### 生命周期

MIK override 只保存在内存中，并在以下时机清理：

- 时限到期；
- 当前脚本显式执行 clear；
- 当前脚本 reload 或 unload；
- 玩家离线时清理该玩家的全部 override；
- 服务器停止时内存状态消失。

脚本 unload 时，MIK 按脚本所有者清理该脚本创建的全部 PVP override。此清理只覆盖
MIK PVP override，不会回滚脚本造成的其他外部状态。

### 示例

```applescript
# 活动区强制开启 10 分钟
force mik pvp on for player using id "event:arena" with priority 100 for 10 minutes

# 更高优先级的安全区强制关闭
force mik pvp off for player using id "zone:safe" with priority 1000

if player has mik pvp override "event:arena":
    set {_remaining} to mik pvp override remaining time of player

clear mik pvp override "event:arena" from player
```

### 与其他保护系统的边界

PVP override 只决定 MIK 的玩家最终 PVP 状态。它不会修改世界级 PVP 开关，也不会反
取消 WorldGuard、小游戏插件或其他保护插件已经取消的伤害事件。强制开启 MIK PVP
不是绕过其他保护；强制关闭会让 MIK 自己的伤害判断和自动开启逻辑按关闭状态处理。

## 战斗锁定

战斗锁定只提供读取能力：

```applescript
if player is mik combat tagged:
    set {_remaining} to mik combat tag remaining time of player
```

`mik combat tag remaining time` 在未锁定时无值。MIK 没有向 Skript 暴露创建、延长或
清除战斗锁定的 effect。

## 非欧空间

### 注册

Skript 可以用带名称的字段，一次性注册一条由两张矩形接缝组成的运行时空间连接：

```text
register [the] mik space %string%:
    first surface:
        corner a: %location%
        corner b: %location%
        through: north|south|east|west|up|down
        up: north|south|east|west|up|down  # 可选
    second surface:
        corner a: %location%
        corner b: %location%
        through: north|south|east|west|up|down
        up: north|south|east|west|up|down  # 可选
    entrances: first|both
```

例如下面的墙面与水平面连接会保持南北方向，同时令 `west` 与 `down` 对应：

```applescript
on load:
    set {_world} to world "world"
    set {_wall-a} to location at 329.5, 80, 1363 in {_world}
    set {_wall-b} to location at 329.5, 83, 1366 in {_world}
    set {_floor-a} to location at 324, 72.5, 1363 in {_world}
    set {_floor-b} to location at 321, 72.5, 1366 in {_world}

    register mik space "jump-door":
        first surface:
            corner a: {_wall-a}
            corner b: {_wall-b}
            through: west
            up: up
        second surface:
            corner a: {_floor-a}
            corner b: {_floor-b}
            through: down
            up: west
        entrances: both
```

注册使用与 `non-euclidean-spaces.yml` 相同的几何规则：两个角点必须形成非零矩形，
`through` 必须垂直于矩形，两张接缝必须位于同一世界且宽高相同。方向字段的含义是：

| 字段 | 含义 |
| --- | --- |
| `through` | 穿过该平面的方向，也是从另一张接缝出来后对应的前进方向。第一面的 `through` 会映射到第二面的 `through`。 |
| `up` | 接缝自身坐标系的上方。第一面的 `up` 会映射到第二面的 `up`，从而确定视角和速度如何旋转；它必须与 `through` 垂直。 |

方向直接填写 `north`、`south`、`east`、`west`、`up`、`down`，不加引号。`up` 可省略：
墙面默认采用世界的 `up`，地面或天花板默认采用 `north`，与 YAML 一致。Location 的 yaw
和 pitch 不参与区域定义。

因此示例中第一面与第二面的轴对应关系恰好为 `west → down`、`up → west`、
`north → north`；反向穿过则为严格逆变换 `down → west`，仍保持 `north → north`。

`entrances: first` 和 `entrances: both` 分别对应配置文件中的 `first` 与 `both`。注册是
原子的：任何字段无效或 ID 冲突时，整条连接都不会加入网络。ID 必须由 1–57 个小写
ASCII 字母、数字、点、下划线或连字符组成，并以字母或数字开头；它与 YAML 及其他
脚本注册的 link ID 共用全局命名空间。

### 查询与注销

注销 effect 的完整形式为 `unregister [the] mik space %string%`。

```applescript
if mik space "jump-door" is registered:
    unregister mik space "jump-door"
```

完整条件为：

```text
mik space %string% is registered
mik space %string% isn't registered
mik space %string% is not registered
```

条件查询全局活动网络，因此也能看到 YAML 注册的 link。`unregister` 只能删除当前
Skript 文件自己注册的同名 link，不能删除 YAML 或其他脚本的连接。脚本 reload、unload
或 MIK 停止时，其运行时连接会自动注销；这些连接不会写入 YAML，也不会跨服务器重启
保存。全局空间配置的 `enabled: false` 同样会停用 Skript 动态连接。
