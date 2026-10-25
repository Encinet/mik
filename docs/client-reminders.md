# 入服客户端提醒

- 入服约 2 秒后，已知客户端协议与服务器协议不同时，显示一行服务器版本和当前客户端版本。同一协议覆盖的多个 Minecraft 补丁版本不会重复提示。
- 入服约 10 秒后，将未检测到的推荐模组合并为一条消息。模组名称可以点击，打开对应的 Modrinth 项目页；玩家应按自己的 Minecraft 版本和加载器选择下载。
- 每次入服最多一条模组提醒；全部检测到时不发送。退出取消待发送任务，断开连接清除该连接的检测记录，插件停用时注销监听并取消任务。
- 基岩版玩家跳过 Java 版本对比和模组提醒。模组提醒独立于 ViaVersion，未安装 ViaVersion 时仍可工作。

## 检测依据

只观察客户端主动发送的频道注册和模组通信，不向客户端伪造 Axiom、Xaero 或模组加载器握手，也不宣称服务器支持未实现的模组协议。PacketEvents 同时监听配置阶段与游戏阶段，避免遗漏入服前的信号；发送提醒前再次读取 Bukkit 的客户端监听频道。

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
