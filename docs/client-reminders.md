# 客户端检测与提醒

版本与模组检测提供独立的只读能力。客户端提示、主菜单和 Skript 从这些能力查询事实；检测器负责连接信号，提示负责建议清单、译文和展示策略。

## 能力与归属

| 位置 | 职责 |
| --- | --- |
| `feature/player/client/ClientVersions` | 主线程查询客户端版本、服务器版本和协议是否落后，返回当前快照 |
| `feature/player/client/ClientMods`、`ClientMod` | 主线程查询已检测到的模组，返回不可变的正向信号集合 |
| `feature/player/client/detection/` | ViaVersion 查询、PacketEvents 配置与游戏阶段信号、Bukkit 频道和语音安装信号 |
| `feature/player/client/tip/` | 推荐清单、下载链接、本地化内容及入服、Axiom 与 Voxy 用途和飞行速度提示声明 |
| `feature/player/client/tip/ClientModTips` | 在模组条件下声明提示，统一过滤基岩版与未知检测，返回原有提示构建器 |

`ClientVersions.version(player)` 在客户端协议未知或版本查询不可用时返回空值；服务器协议未知时仍可返回已知的客户端名称，但不会判断它落后。`ClientMods.detected(player)` 的空值表示当前连接不可观察，存在但为空的集合表示尚无已识别的模组信号。两者均不把未知状态断言为缺失。

`bootstrap` 先创建检测能力，再将查询接口注入 `ClientTips`。检测器独立管理网络监听及连接内存，提示作用域只管理规则与候选。提示停止后，版本查询仍可供主菜单和 Skript 使用；模组检测也有独立的启停。检测事实仅保存在当前连接内存；客户端提示通过[场景提示](guidance.md)合并投递，其中入服提示不保存展示历史。

## 按模组条件声明提示

`ClientModTips` 是 `ClientMods` 与 `TipScope` 之间的声明适配。先描述需要满足的模组条件，
再通过 `define` 取得原有 `Tip.Builder`；事件源、内容、频率和生命周期仍由场景提示管理。
例如，`modTips` 由 `new ClientModTips(tips, mods, bedrockPlayer)` 创建，其中 `bedrockPlayer`
使用装配层提供的基岩版 UUID 判断能力：

```java
modTips.whenDetected(AXIOM).define("axiom-flight-speed", Message.CLIENT_TIP_AXIOM_FLIGHT_SPEED_MM)
        .on(TipTriggers.chatMentions(FLIGHT_SPEED_MENTION))
        .build();
```

其他条件使用相同的 `define(...).on(...).build()`，只调整声明入口：

| 条件 | 声明入口 |
| --- | --- |
| 未检测到 Axiom | `modTips.whenUndetected(AXIOM)` |
| Axiom 与 Plasmo Voice 都已检测到 | `modTips.whenDetected(AXIOM, PLASMO_VOICE)` |
| 已检测到 Axiom，同时未检测到 Plasmo Voice | `modTips.whenDetected(AXIOM).whenUndetected(PLASMO_VOICE)` |

模组常量来自 `ClientMod`。`whenDetected(A, B)` 要求全部检测到；`whenUndetected(A, B)`
要求全部未检测到，多次调用继续追加条件。这里的“未检测到”仍是快照中的正向信号缺失，
不能据此断言未安装；检测未知时不会匹配任何声明。

条件视图不可变，可从同一个 `modTips` 派生多个独立声明。注册时只保存规则，每次条件
求值读取一份最新快照，组合规则共用这份快照；基岩版在读取 Java 检测前即跳过，未知
快照始终不匹配。条件同时用于聊天抽签前和发送前的复核。适配不缓存玩家、不创建任务，
也不持有独立关闭动作；提示仍属于传入的作用域。

需要根据模组集合生成动态内容时，将快照和语言回调传给 `define`，例如入服推荐：

```java
modTips.define("client-mods", (detected, language) -> modAdvice(languages, language, detected))
        .group("client-setup").priority(30).withoutHistory()
        .lifetime(Duration.ofSeconds(45))
        .on(TipTrigger.event(PlayerJoinEvent.class))
        .build();
```

适配统一检查客户端类型和模组条件，在渲染时读取最新的匹配快照，再连同本次投递语言
交给回调。未知或不匹配的检测不进入内容回调，业务侧只负责生成建议，不再自行查询检测器。

## 入服提示

- 入服事件确认后，已知客户端协议早于服务器时，提供版本更新建议，不再统一等待 10 秒。同一协议覆盖的多个 Minecraft 补丁版本、较新的客户端和未知协议不产生此建议。
- 发送前读取当时已经收到的模组快照，将能够生成的建议与版本建议合并投递；某项检测未知时跳过该项，不拖住已经确定的版本结果。后续才到达的信号不代表投递时已知，建议仍只表述“未检测到”。模组名称可以点击，打开对应的 Modrinth 项目页；玩家应按自己的 Minecraft 版本和加载器选择下载。
- 每次入服都按当前检测结果判断，不根据之前是否展示过而跳过，不计算多天冷却或展示档位。版本仍落后、推荐模组仍未检测到时可再次建议；部分安装后仅展示剩余模组。已更新、全部检测到或检测未知时跳过相应建议。所有建议仍受共用的全局提示间隔约束。
- 自动提示的有效期和候选由[场景提示](guidance.md)管理，通过 `withoutHistory()` 声明不记录场景历史，旧版本留下的对应历史在加载或注册时移除，随后写回玩家 PDC。AFK、死亡时跳过本次候选，避免恢复后补发旧提醒。退出取消候选，断开连接清除该连接的检测记录，插件停用时注销监听并撤销候选。
- 基岩版玩家跳过 Java 版本对比和模组提醒。模组提醒独立于 ViaVersion，未安装 ViaVersion 时仍可工作。

## Axiom 用途介绍

未检测到 Axiom 的 Java 玩家可在日常随机提示中看到用途介绍；聊天提及飞行或调速时，
也可通过同一条提示介绍 Axiom。已检测到或检测未知时不展示，发送前再次复核。

文案按建筑编辑、放置能力、日常便利排序：蓝图复制粘贴、地形与展示实体编辑、无更新
放置、穿墙和多组快捷栏。快捷栏可保存多组，不表示增加原版背包的存储格数，见
[Axiom 菜单说明](https://axiomdocs.moulberry.com/contextmenu/intro.html)。Axiom 名称可点击
打开下载页，与入服推荐使用同一份链接和组件样式。

用途介绍通过 `modTips.whenUndetected(AXIOM)` 声明，和已检测时的飞行速度操作提示互斥；
使用普通日常间隔、聊天自适应概率及共用历史，不在每次入服时额外发送介绍。

## Axiom 飞行速度提示

已检测到 Axiom 的 Java 玩家在聊天中提及飞行、速度、快慢等常用说法时，介绍按住左 Alt
打开 Axiom 菜单、拖动右侧竖向滑条调速，也提供可点击填入的 `/fly speed` 命令。
直接 `/fly` 用于切换飞行，`/fly speed` 可查看当前速度并选择预设，也可附加数值设置速度。
Axiom 操作见[官方说明](https://axiomdocs.moulberry.com/contextmenu/intro.html#flight-speed)。

匹配覆盖当前全部 16 种界面语言，不依赖玩家选择的界面语言；拉丁与西里尔文字检查词边界，
排除 `butterfly`、`breakfast` 等单词内片段，中文单字“快”仅匹配独立出现的情况，链接中的
关键词由共享聊天来源排除。未知检测或尚未检测到 Axiom 时不触发，发送前再次复核连接信号。
这条情境提示沿用聊天自适应概率、全局间隔和历史，不使用入服建议的 `withoutHistory()`。

## Voxy 远景推荐

Voxy 加入入服推荐清单，并通过 `modTips.whenUndetected(VOXY)` 加入日常随机提示池：
介绍以较低开销呈现更远风景的用途，低配、高配都可按设备兼容性尝试。名称可点击打开
[官方下载页](https://modrinth.com/mod/voxy)，与入服推荐共用链接和样式；日常介绍沿用
普通提醒间隔，入服清单仍每次重新判断。已检测到或检测未知时跳过，发送前再次复核。

Voxy 本体是客户端远景渲染模组，目前未声明可供服务器识别的模组通信频道；本实现只
识别 VoxyServer 的兼容 LOD 信号，不能保证识别仅安装 Voxy 本体的玩家。没有信号表示
“未检测到”，不表示“未安装”，也不会主动伪造服务端 LOD 协议。项目要求显卡及驱动
支持 OpenGL 4.6，具体兼容性以下载页为准，不承诺所有低配置设备均可运行。

## 检测依据

只观察客户端主动发送的频道注册和模组通信，不向客户端伪造 Axiom、Xaero 或模组加载器握手，也不宣称服务器支持未实现的模组协议。PacketEvents 同时监听配置阶段与游戏阶段，避免遗漏入服前的信号；每次查询快照时合并 Bukkit 的当前监听频道与语音安装信号，提示在发送前读取最新快照。

| 模组 | 正向信号 |
| --- | --- |
| Axiom | 客户端注册或发送 `axiom:*` 频道，例如 `axiom:enable`、`axiom:hello` |
| Plasmo Voice | `plasmo:voice`、`plasmo:voice/*` 频道（包含 `plasmo:voice/v2/installed` 安装标记），或现有 API 返回模组版本、语音连接成功 |
| Voxy | VoxyServer 的 `voxyserver:lod_ready`、`voxyserver:lod_preferences`、`voxyserver:lod_server_settings`、`voxyserver:lod_preserialized`、`voxyserver:lod_clear` 兼容信号 |
| Xaero's Minimap | `xaerominimap:*` 频道 |
| Xaero's World Map | `xaeroworldmap:*` 频道 |

小地图、世界地图分别识别。只有 `xaerolib:*` 时不能推断已安装任一地图模组。Plasmo Voice 静音、禁用语音或 UDP 未连接不作为“未安装”的依据；只要已收到模组信号，就不再提示缺失。

VoxyServer 入服时发送就绪消息，并注册接收 LOD 的频道，见[客户端实现](https://github.com/PooSmacker/VoxyServer/blob/main/src/client/java/com/dripps/voxyserver/client/ClientLodReceiver.java)。
检测只接受上表中的具体频道，不把任意 `voxyserver:*`、Voxy 配置项或品牌字符串当成证据。

## Fabric、Forge、NeoForge 的边界

加载器名称不等于模组列表，`minecraft:brand` 不能用于判断具体模组是否安装。实现识别标准 `minecraft:register` 的 UTF-8/NUL 频道列表，以及公共网络协议 v1 的 `c:register`（版本、阶段、频道列表）。不支持的公共协议版本、畸形和超长注册消息被忽略。两种注册方式都只作为正向证据，不把其中未出现的模组断言为未安装。

Paper 插件无法强制枚举任意客户端本地模组。旧版模组、代理过滤、自定义客户端或加载器未上报相应频道时，只能说“未检测到”。因此提醒是安装建议，不拦截进入，也不作为权限或反作弊依据。完整模组清单需要客户端通过专用协议主动上报。

同一连接内，网络、Bukkit 频道和语音 API 已提供的正向信号会合并保留；频道随后注销、
语音暂时断开不撤销已确认的模组，避免重复建议安装。未增加新信号时复用不可变快照。
断开连接后不再接受该连接的迟到数据，重新连接从空记录开始。模块每次启用使用独立的
观察状态，停用前开始的查询或解析不能把旧信号写入重新启用后的状态。断开标记使用弱
连接键，不阻止旧连接回收。

两种注册格式均严格校验 UTF-8，编码错误或超长消息整包忽略；公共注册格式另检查截断
和多余尾部，不保留其中较早解析出的部分信号。标准注册列表按分隔位置扫描，未知
命名空间先跳过，避免为无关通信构造检测集合或执行频道正则校验。

协议参考：[Fabric 网络文档](https://docs.fabricmc.net/develop/networking)、[NeoForge 公共频道格式](https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/network/payload/CommonRegisterPayload.java)。
