# 通用社交平台内核

MIK 通过 Host + Command + Adapter 处理外部社交平台消息：

```text
已验证的平台事件
  -> SocialPlatformAdapter
  -> SocialInboundMessage
  -> SocialPlatformHost
  -> SocialCommandDispatcher
  -> SocialCommand.decode(...)
  -> SocialCommand.handle(...)
  -> SocialCommand.present(...)
  -> SocialDocument
  -> 事件捕获的 SocialReplyChannel
```

平台适配器只实现协议。服务器查询、身份绑定、资料查询、并发、去重、生命周期、内容安全和本地化结果均属于共享层。当前生产适配器只有 QQ；测试使用内存适配器验证多个平台可以同时运行。

## 平台 SPI

`SocialPlatformAdapter` 分两阶段启动：

- `descriptor()` 返回稳定平台 ID、显示名及可选的 `IdentityPlatform`。
- `prepare()` 读取并验证配置。配置禁用时返回空 plan；异常只令该平台进入 `FAILED`。
- `SocialPlatformPlan` 固定本 generation 的运行策略和命令语法，并通过 `open(context)` 创建 session。
- `SocialPlatformSession` 只暴露 `close()`；transport 通过 generation 专属的 status sink 主动推送 `STARTING`、`READY`、`FAILED` 或 `STOPPED`。

Host 为每个平台独立持有 generation、session、身份平台注册租约、有界虚拟线程执行器、去重集合和已观察会话。并发额度覆盖 handler、内容安全处理以及异步平台回复的完整生命周期，不会在 HTTP 回复仍未完成时提前释放。重载只替换指定平台；旧 generation 的任务和结果不会发送。队列满时 sink 返回 `RETRY_LATER` 并撤销去重记录，使支持重投或断线恢复的平台可以再次提交事件。

## 命令与文档

每条共享命令由一个 `SocialCommand<A, R>` 实现类完整拥有稳定 ID、全部文本别名、调用策略、本地化描述键、类型化 decoder、handler、命令专属结果类型，以及生成 `SocialDocument` 的完整回复布局。标题、字段、语气、权限判断和结果分支都写在对应的 `command.builtin` 类内，不再拆到 feature service、result、help catalog 或 presenter。共用层只保留 dispatcher、语言选择、身份仓储、`social.game` 游戏快照边界、文档模型和平台运行时。`/help` 直接遍历实际安装的命令，根据当前语言选择主别名，并结合当前平台前缀自动生成 `命令 — 描述` 清单；内部 fallback 没有描述，因此不会暴露。新增、删除或重命名命令时不再维护第二份帮助菜单。composition root 显式列出这些对象并创建不可变 `SocialCommandDispatcher`；没有反射扫描，也没有 Manager/Registry/Router 或平台私有命令目录。平台 plan 只声明自己的文本前缀与大小写规则。因此两个平台仍可用不同前缀调用同一命令；原生命令则直接按稳定 ID 调用，并保留结构化 options，无需伪造 `/command` 文本。

Handler 返回 `SocialDocument(title, tone, blocks)`。block 支持段落、字段、有序列表、无序列表和远程或内嵌图片；`plainText()` 提供完整的无格式表示。内容安全只扫描命令通过 `withUntrustedText(...)` 显式标记的用户可控片段，不扫描标题、本地化文案、字段标签、TPS、版本等可信内容。平台 renderer 负责最终转义与格式转换。

共享命令及其回复完整支持简体中文、香港繁体、台湾繁体、文言、英语、德语、西班牙语、法语、意大利语、日语、韩语、荷兰语、巴西葡萄牙语、俄语、泰语和乌克兰语。所有语言的别名始终同时注册，当前回复语言不会过滤可执行的命令；例如中文用户仍可使用 `/profile` 或 `/プロフィール`。命令注册同时保存“别名 → 语言”元数据：已绑定身份优先采用对应 Minecraft 玩家的语言设置；未绑定身份采用本次命中的本地化别名语言；两者都无法确定时才采用平台 plan 在 `SocialCommandSyntax` 中声明的默认语言。QQ 的默认语言为简体中文，共享层不替其他平台决定默认值。原生命令适配器可通过结构化 option `locale` 传入语言标签，例如 `ja-JP` 或 `pt_BR`。结果文档携带已解析语言，因此内容安全替换、长度截断和平台渲染不会丢失语言上下文。玩家资料中的“语言”属于被查询玩家，与整条回复采用的查询者语言相互独立；目标没有保存的语言时显示当前平台的默认语言。

## Bukkit 边界

共享 handler 在每个平台 generation 自己的有界虚拟线程执行器中运行。所有 Bukkit 状态读取集中在 `social.game.SocialGameService`，并经 `SocialMainThreadGateway` 生成不可变的 `ServerSnapshot` 或玩家资料；这个边界只提供游戏数据，不包含任何命令分支或回复。`/我的` 展示 Minecraft UUID、在线状态、游玩时间、首次加入和最近在线，并从 Paper profile 取得官方皮肤纹理，在工作线程本地裁剪正面脸部，将 9×9 的皮肤外层居中覆盖到 8×8 的基础头部后生成 256×256 PNG。同一纹理的并发下载会合并，成功结果按不可变纹理 URL 缓存，失败结果短暂缓存 30 秒；下载本身有 2 秒连接/读取超时和 1 MiB 上限。回复不会显示平台昵称或平台身份字段，平台 adapter 也不调度 Bukkit 线程。不带目标时查询当前绑定玩家；查询其他玩家时，调用者必须已经绑定，且其游戏账号拥有 `member`、`helper` 或 `manager` 角色。普通已绑定玩家仍可查询自己。

其他玩家可以通过精确 Minecraft 玩家名、平台认证后的单个提及，或被回复消息的作者来选择。玩家名直接查询游戏资料；提及和回复则要求目标平台身份已在当前作用域绑定。多个不同目标会被拒绝，文本中伪造的 `@名字` 不会被当作身份。共享层只接收 adapter 构造的 `SocialMessageReferences`，不解析 QQ 私有字段。

## 管理命令

```text
/social
/social help
/social platforms
/social status [platform]
/social reload [platform]
/social conversations <platform>
```

这些管理命令需要 `group.manager` 权限。`/social` 默认显示平台概览；`status`、`reload` 和 `conversations` 的平台参数会动态补全所有已安装适配器。省略 `status` 的平台参数会显示全部状态；省略 `reload` 的平台参数会独立重载全部已安装适配器。旧 `/qqbot`、`/qqbind`、`bindqq` 命令不再注册；游戏内身份验证码统一使用 `/bind qq`。

## 接入新平台

新适配器需要完成平台鉴权，把稳定且可信的身份构造成 `ExternalIdentityKey`，捕获一次入站事件的回复凭证，然后向 sink 提交 `SocialInboundMessage`。adapter 只提供 transport、配置、`SocialCommandSyntax` 与渲染；不要在 adapter 内访问 Bukkit、身份仓储、声明共享别名或实现 server/identity handler。
