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

公共聊天互通走同一个 Host，但使用独立、平台无关的 chat capability：

```text
Minecraft 公共聊天 -> ChatSubmission -> ChatProcessor -> ChatMessage
  -> ChatContent 语义节点 -> READY generation -> 会话路由
  -> 绑定账号候选 -> 目标会话成员解析 -> 平台 renderer

外部普通文本 -> adapter -> SocialInboundMessage -> generation 一次分类
  -> 命令或 ChatSubmission -> 去重/有序背压 -> Bukkit 主线程
  -> ChatProcessor -> Minecraft renderer -> ChatModule 公共聊天格式链路
```

平台适配器只实现协议。服务器查询、身份绑定、资料查询、并发、去重、生命周期、内容安全和本地化结果均属于共享层。当前生产适配器有 QQ 和 Matrix；测试使用内存适配器验证多个平台可以同时运行。

## 社区公示定向提醒

Plot 登记地段或预留施工范围时，自动选取附近所有已登记地段主人作为提醒对象。针对具体公示的异议会提醒公示作者；裁决和撤回也会提醒相关当事人。登记与预留自动公示，不占用异议频率限制；每位玩家每小时最多提交 5 条异议。单条记录不限制提醒人数。收件人以 UUID 保存在插件本地；公开 API 仅展示玩家名。

地段模块在公示写入本地数据库后立即安排提醒。Social 按收件人 UUID 读取语言偏好，只向确认该绑定账号已在目标 QQ 群或 Matrix 房间内的会话发送原生 mention；没有可确认成员关系就不向该会话群发。在线玩家同时收到游戏内私有提示；离线玩家在 7 天内登录后可收到一次提示。提醒链接按收件人语言打开官网中文版或英文版。插件数据目录中的 `plots/board-alerts.yml` 保存已处理的提醒事件和游戏内投递记录，服务器重启不会重复尝试同一事件。首次安装只处理启动后产生的记录，避免向群组回放全部旧公示。平台发送是尽力而为，公示记录仍是正式依据；通知不代表收件或同意。

## 平台 SPI

`SocialPlatformAdapter` 分两阶段启动：

- `descriptor()` 返回稳定平台 ID、显示名及可选的 `IdentityPlatform`。
- `prepare()` 读取并验证配置。配置禁用时返回空 plan；异常只令该平台进入 `FAILED`。
- `SocialPlatformPlan` 固定本 generation 的运行策略和命令语法，并通过 `open(context)` 创建 session。
- `SocialPlatformSession` 只暴露 `close()`；transport 通过 generation 专属的 status sink 主动推送 `STARTING`、`READY`、`FAILED` 或 `STOPPED`。
- 需要聊天互通的平台 session 额外实现 `SocialChatPlatformSession`。共享的 `SocialChatRoute` 与 `SocialChatOutboundPolicy` 支持每个会话独立选择 `ALWAYS` 或 `PREFIX`，以及是否移除触发前缀；adapter 只把平台房间配置转换成这些通用模型。
- 支持原生提及的平台通过 `resolveMentions(conversation, requests)` 从候选绑定身份中选择确实属于目标会话的账号，再由三参数 `sendChat` 消费会话专属解析结果。解析结果不能跨路由缓存；不支持主动发消息或原生提及的平台可保留默认空实现。

Host 为每个平台独立持有 generation、session、身份平台注册租约、有界虚拟线程执行器、去重集合和已观察会话。并发额度覆盖 handler、内容安全处理以及异步平台回复的完整生命周期，不会在 HTTP 回复仍未完成时提前释放。同一会话的命令、入站聊天和出站聊天保持提交顺序，不同会话仍可并发；队列满时 sink 返回 `RETRY_LATER` 并撤销去重记录，Minecraft 发布端则返回包含 accepted/unavailable/backpressured 计数的 `SocialChatPublishReport`。重载只替换指定平台，旧 generation 的任务和结果不会发送。命令解析和聊天路由由 generation 对同一个 `SocialInboundMessage` 只判定一次；重复事件、机器人消息与生命周期拒绝不会被误判成聊天。

## 命令与文档

每条共享命令由一个 `SocialCommand<A, R>` 实现类完整拥有稳定 ID、全部文本别名、调用策略、本地化描述键、类型化 decoder、handler、命令专属结果类型，以及生成 `SocialDocument` 的完整回复布局。标题、字段、语气、权限判断和结果分支都写在对应的 `command.builtin` 类内，不再拆到 feature service、result、help catalog 或 presenter。共用层只保留 dispatcher、语言选择、身份仓储、`social.game` 游戏快照边界、文档模型和平台运行时。`/help` 直接遍历实际安装的命令，根据当前语言选择主别名，并结合当前平台前缀自动生成 `命令 — 描述` 清单；内部 fallback 没有描述，因此不会暴露。新增、删除或重命名命令时不再维护第二份帮助菜单。composition root 显式列出这些对象并创建不可变 `SocialCommandDispatcher`；没有反射扫描，也没有 Manager/Registry/Router 或平台私有命令目录。平台 plan 只声明自己的文本前缀与大小写规则。因此两个平台仍可用不同前缀调用同一命令；原生命令则直接按稳定 ID 调用，并保留结构化 options，无需伪造 `/command` 文本。

共享命令还安装了平台无关的 AI 助手：QQ 使用 `/ai <问题>`，Matrix 使用 `!ai <问题>`，稳定原生命令 ID 为 `ai.ask`。它只依赖 `module.ai.api.AiGateway`，不引用提供商、工具或 Bukkit 实现；完整配置、工具包、多语言 prompt、身份隔离与安全边界见 [AI 助手](ai.md)。

Handler 返回 `SocialDocument(title, tone, blocks)`。block 支持段落、字段、有序列表、无序列表和远程或内嵌图片；`plainText()` 提供完整的无格式表示。内容安全扫描命令通过 `withUntrustedText(...)` 显式标记的用户可控片段，以及聊天桥接两端的消息正文；不扫描标题、本地化文案、字段标签、TPS、版本等可信内容。平台 renderer 负责最终转义与格式转换。

共享命令及其回复完整支持简体中文、香港繁体、台湾繁体、文言、英语、德语、西班牙语、法语、意大利语、日语、韩语、荷兰语、巴西葡萄牙语、俄语、泰语和乌克兰语。所有语言的别名始终同时注册，当前回复语言不会过滤可执行的命令；例如中文用户仍可使用 `/profile` 或 `/プロフィール`。命令注册同时保存“别名 → 语言”元数据：已绑定身份优先采用对应 Minecraft 玩家的语言设置；未绑定身份采用本次命中的本地化别名语言；两者都无法确定时才采用平台 plan 在 `SocialCommandSyntax` 中声明的默认语言。QQ 的默认语言为简体中文，共享层不替其他平台决定默认值。原生命令适配器可通过结构化 option `locale` 传入语言标签，例如 `ja-JP` 或 `pt_BR`。结果文档携带已解析语言，因此内容安全替换、长度截断和平台渲染不会丢失语言上下文。玩家资料中的“语言”属于被查询玩家，与整条回复采用的查询者语言相互独立；目标没有保存的语言时显示当前平台的默认语言。

## Bukkit 边界

共享 handler 在每个平台 generation 自己的有界虚拟线程执行器中运行。所有 Bukkit 状态读取集中在 `social.game.SocialGameService`，并经 `SocialMainThreadGateway` 生成不可变的 `ServerSnapshot` 或玩家资料；这个边界只提供游戏数据，不包含任何命令分支或回复。`/我的` 展示 Minecraft UUID、在线状态、游玩时间、首次加入和最近在线，并从 Paper profile 取得官方皮肤纹理，在工作线程本地裁剪正面脸部，将 9×9 的皮肤外层居中覆盖到 8×8 的基础头部后生成 256×256 PNG。同一纹理的并发下载会合并，成功结果按不可变纹理 URL 缓存，失败结果短暂缓存 30 秒；下载本身有 2 秒连接/读取超时和 1 MiB 上限。回复不会显示平台昵称或平台身份字段，平台 adapter 也不调度 Bukkit 线程。不带目标时查询当前绑定玩家；查询其他玩家时，调用者必须已经绑定，且其游戏账号拥有 `member`、`moderator` 或 `custodian` 角色。普通已绑定玩家仍可查询自己。

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

这些管理命令需要 `group.custodian` 权限。`/social` 默认显示平台概览；`status`、`reload` 和 `conversations` 的平台参数会动态补全所有已安装适配器。省略 `status` 的平台参数会显示全部状态；省略 `reload` 的平台参数会独立重载全部已安装适配器。旧 `/qqbot`、`/qqbind`、`bindqq` 命令不再注册；游戏内身份验证码统一使用 `/bind <platform>`，例如 `/bind qq` 或 `/bind matrix`。

## 接入新平台

新适配器需要完成平台鉴权，把稳定且可信的身份构造成 `ExternalIdentityKey`，捕获一次入站事件的回复凭证，然后向 sink 提交 `SocialInboundMessage`。adapter 只提供 transport、配置、`SocialCommandSyntax` 与渲染；不要在 adapter 内访问 Bukkit、身份仓储、声明共享别名或实现 server/identity handler。

若平台支持聊天互通，adapter 将普通文本和命令都作为 `SocialInboundMessage` 交给唯一的 `context.events()`，并让 session 实现 `SocialChatPlatformSession`。generation 先解析命令，未命中时只在已配置的 `SocialChatRoute` 中构造通用 `ChatSubmission`。全量互通使用 `SocialChatOutboundPolicy.always()`，触发式互通使用 `prefixed(prefix, stripPrefix)`。Minecraft 线程、消息展示、generation 去重、有序背压与重载失效仍由共享层负责。

入站 bridge 不直接拼 Adventure 组件或自行广播。`BukkitSocialChatGateway` 在社交工作线程解析身份绑定及 LuckPerms 前后缀快照，再切换到主线程交给 `ChatModule`；ChatModule 使用与玩家公共消息相同的安全链接解析和 `ChatMessageFormatter.channelMessage` 路径。平台来源只增加 `[平台]` 身份标记：已绑定身份显示 Minecraft 玩家身份，在线或离线都复用前后缀，未绑定身份才显示平台昵称。只有名称节点悬浮显示平台昵称、平台账号、绑定玩家和 UUID；LuckPerms 前缀、后缀及基岩标记不继承该悬浮。原生 Minecraft 名称则显示本地化玩家资料、UUID 和私聊操作提示。名称之后仍使用相同的金色 `»` 分隔符，正文继续具有本地化发送时间悬浮、点击复制及链接子组件自己的打开动作。社交平台进入游戏的聊天不经过关键词过滤；向其他社交平台继续转发时才执行出站检查。`ChatOrigin.visitedPlatforms` 会阻止消息回送来源平台，因此增加更多支持聊天的 adapter 时不需要平台之间互相硬编码。

## Matrix

Matrix 适配器使用 Client-Server API：启动时通过 `/account/whoami` 验证 access token，首次 `/sync` 只建立 `next_batch` 游标而不回放历史命令，之后长轮询未加密房间中的 `m.room.message`。共享命令使用 `!` 前缀，例如 `!帮助`、`!状态` 和 `!绑定 CODE`。文本回复使用带 `m.in_reply_to` 的 `m.text`，避免客户端把命令结果按 notice 显示为斜体或弱化样式；线程内命令保留 `m.thread` 关系，玩家头像先上传到 Matrix 媒体仓库再作为带说明文字的 `m.image` 回复。

Matrix 用户 ID 是全局身份：`subject` 保存完整 MXID，`issuer` 保存 MXID 的 server name，`scope` 为空，因此同一用户在不同房间不需要重复绑定。结构化 `m.mentions.user_ids` 必须再与 HTML `matrix.to` 用户链接或明确的纯文本 mention 范围对应，随后才作为带起止位置的可信引用进入共享模型；已观察到的回复事件作者只进入 reply 引用。正文中的伪造 `@user:server` 不算认证提及，回复通知里的用户也不会凭空变成正文 mention。适配器忽略编辑事件、非文本事件、机器人自身消息和 `m.room.encrypted`；它不实现端到端加密。

Matrix 的 `chat-bridge.routes` 按房间启用普通聊天互通，且每个房间可独立选择 `always` 或 `prefix` 出站策略。Matrix 入站普通文本转入 Minecraft；Minecraft 只外发公共频道，staff 和私聊不会进入桥接。Minecraft 出站消息同时携带用于路由与安全检查的原始正文，以及 ChatModifier 处理后的平台中立 `ChatContent` 节点；Matrix 将安全 HTTP(S)、`mailto:` 链接和基础文字样式转换为带纯文本回退的 `org.matrix.custom.html`。邮箱、Matrix ID、`matrix.to` 永久链接及 `matrix:` URI 均由共享 ChatModifier 识别；其中 `matrix:` 会转换为规范的 `matrix.to` URL，以便 Minecraft 与 Matrix 客户端都能打开。Minecraft 的打开链接动作只允许 HTTP(S)，所以邮箱节点在游戏中提供左键复制地址，Matrix 中仍提供 `mailto:`。前缀路由剥离触发器时会同步裁剪语义节点，而不是重新退化为纯文本。

聊天内核的共同模型是 `ChatSubmission -> ChatProcessor -> ChatMessage`。修饰器只产生 `Text`、`Link`、`PlayerMention`、`ExternalMention`、`BroadcastMention`、`Item` 等语义节点，不拼接 Matrix HTML、QQ Markdown 或 Adventure 组件；Minecraft、Matrix 与 QQ renderer 各自降级不支持的能力。平台 adapter 负责把协议认证的 mention 映射为精确正文范围，共享 gateway 再把已绑定身份补成 Minecraft UUID；普通文本修饰器不得覆盖这些可信范围。Minecraft 玩家 mention 的输入语法固定为 `玩家名@`，语义节点的可见形式始终是 `@玩家名`。自然 Paper 聊天先建立待提交事务，只有事件最终未取消才提交重复状态、重复按钮、提醒和社交发布。异步聊天从主线程维护的不可变玩家、绑定玩家与物品快照读取上下文，不同步等待 Bukkit 主线程。

出站 mention 先按 Minecraft UUID 查询绑定快照，再按目标平台过滤候选，最后由 session 按确切目标会话成员关系解析。当前 Matrix session 启动时读取完整 `/joined_members`，并以 `m.room.member` 事件维护快照；只有 `join` 状态的账号会进入结果。QQ 身份本身带有 AppID issuer 和群 OpenID scope，因此 QQ session 只选择当前机器人、当前目标群内绑定的账号。一个玩家在目标会话中有多个绑定账号时全部保留。renderer 的通用显示约定是 Minecraft → 社交平台使用 `@游戏名(目标平台昵称)`，社交平台 → Minecraft 使用 `@来源平台昵称(游戏名)`，社交平台 A → B 使用 `@目标平台昵称(游戏名)`。找不到目标身份时保留来源侧可读文本且不发送原生通知；Matrix renderer 只为完整写入消息的 mention 生成链接和 `m.mentions.user_ids`，QQ renderer 只为完整写入消息的 mention 生成 `<@!member_openid>`，避免截断消息造成不可见通知。

社区公示由 Plot 在地段登记或预留成功时自动生成，选出附近已登记地段主人，再交给 Social 定向提醒。Social 仅在已配置的聊天会话中尝试发送，每个平台选择首个能解析该玩家绑定身份的会话；普通聊天的 `always` 或 `prefix` 触发条件不限制这类系统提醒。正文按收件人语言生成，未绑定或未匹配会话时不向群组广播，也不视为送达。
