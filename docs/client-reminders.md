# 客户端检测与提醒

版本与模组检测提供独立的只读能力。客户端提示、主菜单和 Skript 从这些能力查询事实；检测器负责连接信号，提示负责建议清单、译文和展示策略。

## 能力与归属

| 位置 | 职责 |
| --- | --- |
| `feature/player/client/ClientVersions` | 主线程查询客户端版本、服务器版本和协议是否落后，返回当前快照 |
| `feature/player/client/ClientMods`、`ClientMod` | 主线程查询已检测到的模组，返回不可变的正向信号集合 |
| `feature/player/client/detection/` | ViaVersion 查询、PacketEvents 配置与游戏阶段信号、Bukkit 频道和语音安装信号 |
| `feature/player/client/tip/` | 推荐清单、下载链接、本地化内容及两条提示声明 |

`ClientVersions.version(player)` 在客户端协议未知或版本查询不可用时返回空值；服务器协议未知时仍可返回已知的客户端名称，但不会判断它落后。`ClientMods.detected(player)` 的空值表示当前连接不可观察，存在但为空的集合表示尚无已识别的模组信号。两者均不把未知状态断言为缺失。

`bootstrap` 先创建检测能力，再将查询接口注入 `ClientTips`。检测器独立管理网络监听及连接内存，提示作用域只管理规则与候选。提示停止后，版本查询仍可供主菜单和 Skript 使用；模组检测也有独立的启停。检测事实仅保存在当前连接内存，展示历史与冷却由[场景提示](guidance.md)统一保存。

## 入服提示

- 入服约 10 秒后，已知客户端协议早于服务器时，提供版本更新建议。同一协议覆盖的多个 Minecraft 补丁版本、较新的客户端和未知协议不产生此建议。
- 发送前重新检查未检测到的推荐模组，与版本建议合并为一次提示。模组名称可以点击，打开对应的 Modrinth 项目页；玩家应按自己的 Minecraft 版本和加载器选择下载。
- 相同的客户端与服务器版本组合、相同的推荐模组清单以 7 天为基础间隔，重复展示时逐步延长至 30 天。目标版本或推荐清单变化时重新评估；玩家安装部分模组不会绕过剩余建议的冷却，发送内容仍只包含当时未检测到的模组。所有建议均受全局提示间隔约束。
- 自动提示的 AFK 暂停、有效期和展示历史由[场景提示](guidance.md)管理。退出取消候选，断开连接清除该连接的检测记录，插件停用时注销监听并撤销候选。
- 基岩版玩家跳过 Java 版本对比和模组提醒。模组提醒独立于 ViaVersion，未安装 ViaVersion 时仍可工作。

## 检测依据

只观察客户端主动发送的频道注册和模组通信，不向客户端伪造 Axiom、Xaero 或模组加载器握手，也不宣称服务器支持未实现的模组协议。PacketEvents 同时监听配置阶段与游戏阶段，避免遗漏入服前的信号；每次查询快照时合并 Bukkit 的当前监听频道与语音安装信号，提示在发送前读取最新快照。

| 模组 | 正向信号 |
| --- | --- |
| Axiom | 客户端注册或发送 `axiom:*` 频道，例如 `axiom:enable`、`axiom:hello` |
| Plasmo Voice | `plasmo:voice`、`plasmo:voice/*` 频道（包含 `plasmo:voice/v2/installed` 安装标记），或现有 API 返回模组版本、语音连接成功 |
| Xaero's Minimap | `xaerominimap:*` 频道 |
| Xaero's World Map | `xaeroworldmap:*` 频道 |

小地图、世界地图分别识别。只有 `xaerolib:*` 时不能推断已安装任一地图模组。Plasmo Voice 静音、禁用语音或 UDP 未连接不作为“未安装”的依据；只要已收到模组信号，就不再提示缺失。

## Fabric、Forge、NeoForge 的边界

加载器名称不等于模组列表，`minecraft:brand` 不能用于判断具体模组是否安装。实现识别标准 `minecraft:register` 的 UTF-8/NUL 频道列表，以及公共网络协议 v1 的 `c:register`（版本、阶段、频道列表）。不支持的公共协议版本、畸形和超长注册消息被忽略。两种注册方式都只作为正向证据，不把其中未出现的模组断言为未安装。

Paper 插件无法强制枚举任意客户端本地模组。旧版模组、代理过滤、自定义客户端或加载器未上报相应频道时，只能说“未检测到”。因此提醒是安装建议，不拦截进入，也不作为权限或反作弊依据。完整模组清单需要客户端通过专用协议主动上报。

协议参考：[Fabric 网络文档](https://docs.fabricmc.net/develop/networking)、[NeoForge 公共频道格式](https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/network/payload/CommonRegisterPayload.java)。
