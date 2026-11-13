# 社交平台架构

QQ、Matrix 共用命令、身份绑定、聊天桥、内容安全与运行时。平台适配器负责鉴权、协议、配置和最终渲染。配置与使用见 [QQ](qq-bot.md)、[Matrix](matrix-bot.md)、[身份绑定](identity-binding.md)及 [AI](ai.md)。

## 消息流程

```text
已验证的平台事件 → Adapter → SocialInboundMessage → Host
  → Command.decode → handle → present → SocialDocument → ReplyChannel

Minecraft 公共聊天 → ChatSubmission → ChatProcessor → ChatMessage
  → ChatContent → 会话路由 → 目标成员解析 → 平台 renderer

外部普通文本 → 同一入站入口 → 命令分类与路由 → Bukkit 主线程
  → ChatService → ChatProcessor → 游戏 / 网页 / 其他 Social 输出
```

命令优先解析，未命中时才进入已配置的聊天路由；同一事件只分类一次。公共聊天可桥接，staff 与私聊不外发。`ChatOrigin.visitedPlatforms` 防止消息返回来源平台。

`ChatContent` 使用文本、链接、玩家提及、外部提及、广播提及、物品和内容说明等语义节点。Minecraft、QQ、Matrix 各自渲染或降级，不在共享修饰器中拼平台标记。

每个平台在 `SocialPlatformDescriptor.sourceColor` 显式声明来源标签的 RGB 颜色。Host 将颜色随 `ChatOrigin.Social` 传递，游戏聊天统一读取该快照渲染标签；新增平台只需在自己的适配器中声明显示名与颜色。

## 多语言与展示边界

协议解码保留平台内容类型，投影层输出稳定正文、独立命令正文和生成说明的精确范围。QQ 与 Matrix 将图片、表情、聊天记录等映射到共享的 `ChatPlaceholder`；`ChatProcessor` 按可信范围构造 `ChatNode.Placeholder`。用户原文中手写的 `[image]` 等文字保持字面内容，不按文本匹配翻译。路由剥离前缀时同时调整提及和内容说明的位置；被截断的节点不产生残缺范围。

QQ 将显式聊天记录和官方引用元素中序列化的转发记录归一化为 `HISTORY`，投影时只产生聊天记录说明，不递归读取内部消息。游戏、网页和其他平台接收同一份折叠结果。普通正文中手写的类似分隔行仍作为文字；记录内部作者不进入引用身份解析。

`ChatContent.localized` 只解析内容说明节点，保留节点类型、样式、链接、提及与原始内容。游戏在渲染时采用接收玩家语言；网页采用绑定账号保存的语言，并按语言、发送者身份与提及状态复用消息帧；其他 Social 平台采用目标平台的 `default-language`，在内容审核之前解析展示文本。没有翻译上下文的日志或协议回退使用稳定的类型说明，例如 `[image]`。QQ 和 Matrix 的长度限制将内容说明作为整体保留或省略。

命令业务结果与语言资源分离。`SocialCommandLanguageResolver` 统一选择回复语言；帮助与动作提示从注册的命令别名和实际前缀生成，翻译只安排完整句子。计数、总体人数和指标以数值交给 Fluent，挂机状态以语义条件选择分支；`SocialTimeText` 统一日期与时长格式，公告由 `SocialAnnouncementPresenter` 按目标语言生成文档。全部公开文案同步维护 `Message` 和所有 FTL。

Matrix 加密管理返回 `MatrixCryptoResult`，分别区分自身验证、事务阶段和目标设备信任；预期操作错误使用 `MatrixCryptoException` 的原因类型。SDK 适配不返回已拼好的英文文案，`MatrixCryptoPresenter` 负责控制台展示和错误翻译。指纹、设备 ID 与 SAS 比对值按协议原样展示，故障详情记录到日志。

## 平台 SPI 与生命周期

| 接口 | 契约 |
| --- | --- |
| `descriptor()` | 稳定平台 ID、显示名、来源标签颜色及可选的 `IdentityPlatform` |
| `prepare()` | 校验配置，禁用时返回空计划，异常只令该平台失败 |
| `SocialPlatformPlan.open(context)` | 固定本代运行策略并创建会话 |
| `SocialPlatformSession.memberships()` | 暴露绑定入口时必须返回 `IdentityMemberships`，提供成员事实和退出监测；无法观测的状态返回 `UNKNOWN` |
| `SocialPlatformSession.close()` | 关闭传输与资源；状态由专属 sink 推送 `STARTING/READY/FAILED/STOPPED` |
| `SocialChatPlatformSession` | 提供聊天发送及目标会话提及解析 |
| `SocialChatPlatformSession.prepareNotice` | 向指定会话发送服务端文档，QQ 与 Matrix 各自渲染，不套用普通聊天前缀 |
| `SocialChatRoute` | 会话的入站与出站各自使用 `ALWAYS` 或 `PREFIX`，可选择剥离前缀；入站同步调整可信提及范围 |

Host 为每个平台独立维护运行代次（generation）、会话、身份平台注册租约、有界虚拟线程执行器、去重与会话快照。聊天按会话保持提交顺序，命令可并发执行；身份写入由 Identity 的事务队列串行维护。并发额度持续到异步回复结束；队列满时返回 `RETRY_LATER` 并撤销该次去重标记。Minecraft 发布端获得包含 accepted、unavailable、backpressured 数量的报告。

重载只替换指定平台，旧代任务与结果失效。机器人消息、重复事件及生命周期拒绝不会作为普通聊天继续处理。

公告功能通过自己的 `AnnouncementPublisher` 发布新增时间戳与正文，`bootstrap` 将该能力连接到 Social；公告不依赖具体协议或 Social 运行时。Social 使用每个平台的默认语言构造文档，在同一有界执行器的独立有序队列中审核并投递到全部已配置路由。每个目的地分别处理发送错误，一个群或平台失败不会中止其他投递。公告使用 `REQUIRED` 审核策略，拦截时取消该平台的发送，不向全部群广播拦截占位提示。

## 命令与回复

每条 `SocialCommand<A, R>` 只有定义、执行和呈现三个入口。`SocialCommandDefinition<A>` 将稳定 ID、文本别名、描述、参数解析与参数用法放在同一处；dispatcher 先解析并校验，参数错误作为数据生成用法指引，不进入业务执行。帮助和 AI 消费同一份不可变命令定义目录。平台 plan 只指定前缀和大小写规则；原生命令按稳定 ID 与结构化 options 调用。

业务执行返回 `SocialCommandExecution<R>`，携带具体结果、回复语言的选择依据，以及已完成身份操作的固定回执标识。dispatcher 保存这份结果后才调用呈现函数。语言偏好在呈现开始时解析一次，主回复和所有恢复路径共用 `SocialPresentationContext`；绑定、解绑使用操作结果中的玩家身份选择偏好，AI 使用请求时已确定的语言。呈现函数只读取结果与语言上下文，不重新查询绑定或游戏状态。资料中的目标玩家语言也在执行阶段捕获。

在线、状态与资料是单轮查询；绑定进度由 Identity 管理。Social 为解绑单独持有待确认操作：凭据绑定可信平台身份、会话、当前运行代次和目标 `bindingId`，有效期两分钟，只能消费一次。`/解绑` 返回说明和带确认码的完整指令。确认时 Identity 按外部身份和预期 `bindingId` 原子核验并删除；解绑后重新绑定、跨房间、换用户、平台重载、过期或已消费的确认均不能删除新关系。存储容量有界，功能关闭时清理。AI 历史由 AI 独立持有，不保存这些确认码。

`SocialDocument.message(moderation, tone, blocks...)` 生成无标题的短回复；状态、资料与公告使用 `of(moderation, title, tone, blocks...)` 保留标题。内容支持段落、字段、列表、操作指引与远程或内嵌图片，`plainText()` 提供语义文本。`instruction(explanation, invocation)` 将说明与可复制的命令组成不可拆分的完整节点，只展示指引，不执行动作。

绑定教程明确区分游戏内 `/bind <平台>` 和当前社交平台的资料命令。已经绑定时直接展示账号及资料入口；未绑定时不生成解绑确认。解绑确认说明账号对象，以及缺少其他有效社区绑定时的入服和在线连接影响。

无前缀快捷查询只匹配完整别名，附带其他正文时作为普通聊天继续路由。显式在线、状态和帮助命令遇到多余参数时显示正确用法，不忽略剩余正文执行查询。引用或转发内容仍不进入参数解析。

`SocialReplyChannel.prepare(document, maximumLength)` 在没有网络 I/O 的情况下生成 `SocialPreparedReply`。`SocialDocumentLayout` 根据平台各发送格式的实际编码长度选定同一份有界文档；操作指引整体保留或省略，字段和列表项不从中间截断，并保持原始节点顺序。QQ 的 `QqDocumentRenderer` 为回复和公告统一选择纯文本或 Markdown，并同时约束媒体说明和回退正文；`QqChatRenderer` 使用相同的字面文本规则。两者产出带格式的 `QqOutgoingMessage`，OpenAPI 客户端集中生成互斥的 `content` 或 `markdown` 字段。Matrix 的 HTML 与纯文本也共用该布局。运行时审核准备后的语义文本，审核后不再次裁剪或改写内容；公告同样先准备、再审核和发送。

回复语言依次取绑定玩家的 Minecraft 偏好、命中的本地化别名、平台默认语言。所有语言别名同时有效；原生命令可用 `locale` option 指定标签。内容安全替换和截断保留已解析语言。

每份回复及其辅助方法在创建时显式传入不可变的 `SocialDocument.Moderation` 策略。

```java
SocialDocument.of(TRUSTED, languages.t(language, Message.SOCIAL_TITLE_COMMAND_HELP),
        Tone.INFO, paragraph(languages.t(language, Message.SOCIAL_HELP_COMMAND_DESCRIPTION)));
SocialDocument.of(REQUIRED, "AI", Tone.INFO, paragraph(answer));
```

身份变更完成后，执行结果携带 `SocialCompletionReceipt` 中固定、不含玩家名的回执标识。详细呈现失败、完整回复被审核拦截或审核服务不可用时，运行时可以用同一语言生成这份 `TRUSTED` 回执；未完成的操作不能携带完成回执。其他处理故障在当前代次仍有效且能够发送时提供固定恢复提示。

`SocialPreparedReply.send()` 只启动一次投递，重复调用返回同一个 future。`SocialDeliveryResult` 区分平台已确认接受（`ACCEPTED`）、确定未发送（`NOT_SENT`）和结果未知（`UNKNOWN`）。媒体上传阶段失败尚未发送用户消息，可以回退为文字；发送阶段只有明确的拒绝才允许回退，超时、响应不完整和不确定的服务端故障保留为未知。取消传递到当前异步请求，不触发媒体回退。恢复沿用已完成的业务结果，结果未知的发送保持原状态。

`TRUSTED` 用于本地模板和可信统计，直接发送；`REQUIRED` 用于含用户输入、玩家名称或 AI 生成文本的回复，在平台开启内容安全时送审。检查覆盖截断后的完整文档，保留标题、字段与正文语境。语言转换和截断始终保留原策略。

| 回复内容 | 策略 |
| --- | --- |
| help、status、未知命令及固定说明、不含动态内容的错误提示 | `TRUSTED` |
| online | 有玩家名称时为 `REQUIRED`，空列表为 `TRUSTED` |
| profile 查询成功、含玩家名称的绑定教程、解绑确认和操作结果 | `REQUIRED` |
| AI 实际回答 | `REQUIRED`；清空、繁忙、失败等固定提示为 `TRUSTED` |

聊天显式使用 `REQUIRED`，在路由选择和提及解析后检查出站语义文本，包含发送者、展开的可见提及和链接目标。同次发布的多个会话若产生相同文本，复用本次判定。社交入站到 Minecraft 的正文不执行出站审核。

## 内容安全

QQ、Matrix 共用 Qwen3Guard 审核池，各平台的 `content-safety.enabled` 决定是否检查本平台出站内容。共享配置首次启动生成于 `plugins/mik/social/content-safety.yml`：

```yaml
api-key: ""
edenai-api-key: ""
connect-timeout-millis: 5000
request-timeout-millis: 10000
```

| 入口 | 模型 | 凭据 |
| --- | --- | --- |
| OVHcloud 直连 | [Qwen3Guard-Gen-0.6B](https://www.ovhcloud.com/en-gb/public-cloud/ai-endpoints/catalog/qwen-guard-gen-06b/) | `api-key`，留空或省略时匿名调用 |
| OVHcloud 直连 | [Qwen3Guard-Gen-8B](https://www.ovhcloud.com/en-gb/public-cloud/ai-endpoints/catalog/qwen-guard-gen-8b/) | 同上 |
| Eden AI | [OVHcloud Qwen3Guard-Gen-8B](https://www.edenai.co/expert-models/ovhcloud-qwen3guard-gen-8b) | `edenai-api-key`，留空或省略时不加入池 |

始终优先使用健康的 8B 入口：未启用 Eden 时使用 OVH 8B，启用后在 OVH 8B 与 Eden 8B 之间等权轮询。只有所有 8B 入口均不可用或本次尝试均失败时，才使用 OVH 0.6B；8B 入口冷却结束后自动恢复优先使用，0.6B 不参与日常轮询。OVH 端点为 `https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions`；Eden 使用 `https://api.edenai.run/v3/universal-ai`，模型标识为 `text/moderation/ovhcloud/Qwen3Guard-Gen-8B`。两家凭据独立，匿名 OVH 请求省略 `Authorization`，有密钥的入口发送各自的 `Bearer` 鉴权；禁止自动跟随重定向。

配置损坏时记录警告，平台继续启动，help、status 等可信回复仍可发送；审核客户端的可用性只在处理 `REQUIRED` 内容时检查，审核不可用时拒绝该次外发。修复配置后执行 `/social reload`，或重载对应平台。密钥和远端错误正文不写入日志。

[OVHcloud 官方限流说明](https://www.ovhcloud.com/en-gb/public-cloud/ai-endpoints/catalog/qwen-guard-gen-06b/)：匿名调用按 IP 和模型限制为每分钟 2 次；使用 API 密钥时按 Public Cloud 项目和模型限制为每分钟 400 次。超出额度返回 HTTP 429，并触发下述故障切换。Eden 的额度与鉴权由 Eden 账号管理，不能用 OVH 密钥代替。

OVH 按模型官方的文本审核用法，将待发送内容原样放入单条 `user` 消息，以 `temperature: 0`、非流式方式请求分类；Eden 按其 [Universal AI 文本审核协议](https://www.edenai.co/docs/v3/expert-models/features/text/moderation)将同一文本放入 `input.text`。不添加通用聊天助手的系统提示，也不附带入站命令、历史、验证码或传输凭据。命令审核包含准备向平台展示的文本资料；聊天使用去掉路由触发前缀并经 ChatModifier 展开的内容。图片只审核文字说明及文档中的远程链接，不上传图片二进制，也不检查图片像素。

OVH 仅完整的 `Safety: Safe` 且 `Categories: None` 结果放行，`Controversial` 和 `Unsafe` 均拦截。Eden 返回归一化风险结果，仅 `status: success`、提供方及任务正确、总风险与返回的每个分类的 likelihood 均为最低等级 `1` 时放行；`items` 按协议可省略，总风险及分数为必填项，数值必须类型及范围有效，缺失必填字段和未知格式不放行。明确的风险判定不向另一模型重审。命令回复被拦截时显示原语言的隐藏提示；聊天和社区提醒被拦截时取消本次外发。超过模型输入预算的内容直接拦截，不裁掉尾部后送审。

连接失败、超时、响应过大、结果无法解析、HTTP 408/425/429/5xx 会切换到尚未尝试的可用入口，每个入口每次最多尝试一次。429 默认冷却 60 秒，其余临时错误默认冷却 5 秒；HTTP 返回的有效 `Retry-After`（秒数或 HTTP 日期）优先使用，上限一天。冷却期间跳过该入口。401/402/403/404 将该入口停用到重载，并尝试其他入口；其他 HTTP 错误直接中止；无效结果对应的入口冷却 5 秒并继续尝试其他入口。所有入口不可用、重试耗尽或总超时后停止本次外发并记录错误。

请求在有界 Social worker 中执行，`request-timeout-millis` 是包含故障切换和完整响应体的总预算；每次尝试按剩余入口数分配剩余时间，确保卡住的响应也能切换。执行 `/social reload` 或 `/social reload <platform>` 会重新读取共享配置、清除入口故障状态并关闭旧审核客户端，正在等待旧客户端的审核可能取消；指定平台的重载仍只重建该平台会话。停用 Social 时关闭共享客户端并取消请求。

## 身份、提及与游戏数据

平台必须从已认证事件构造 `ExternalIdentityKey`、提及范围和回复作者，不接受正文中自报的账号或伪造 `@名字`。玩家名查询可直接读取游戏资料；平台提及与回复目标须在适配器声明的身份作用域内已绑定（QQ 为 AppID，群上下文不产生额外绑定），多个不同目标会被拒绝。

出站提及先按 Minecraft UUID 查绑定，再由平台会话确认目标账号确实属于当前房间或群。多个有效账号全部保留；无目标时仅保留可读文本。被长度限制截断的提及不发送原生通知。显示约定为游戏到平台 `@游戏名(平台昵称)`，平台到游戏 `@平台昵称(游戏名)`。

共享 handler 运行于有界 worker，Bukkit 数据由 `SocialGameService` 经主线程网关捕获为不可变快照。查询自己只需绑定；查询他人还须具有 `member`、`moderator` 或 `custodian` 角色。资料包含 UUID、在线状态、游玩时间、首次加入、最近在线和皮肤头像，不暴露平台身份字段。

入站 `SocialInboundMessage.Text` 分别保存桥接展示正文与可执行命令的原始正文，普通文字默认两者相同。平台添加附件、卡片、引用或聊天记录说明时，只扩展展示正文；命令解析只读取发送者自己的命令正文，防止引用内容触发命令。可信提及的位置仍相对于展示正文，路由剥离前缀时同步调整。

入站聊天经共享 gateway 补充绑定及 LuckPerms 身份，再交给共享聊天服务，统一投递游戏、网页和其他 Social 平台的公共会话。作者没有 Minecraft 绑定或绑定玩家游戏离线时仍可在网页显示。已绑定身份使用游戏名字与前后缀，未绑定时使用平台昵称。Minecraft 内提及输入语法为 `@玩家名` 或 `@ 玩家名`，统一显示为 `@玩家名`；中日韩正文无需用空格隔开，例如 `你好@Alex在吗`，`@all` 使用相同边界。自然聊天只在 Paper 事件最终未取消后提交提醒、重复状态及社交发布。

玩家提及使用不可变的名字哈希索引；在线玩家与绑定玩家分别维护，名字与候选集合不变时跨消息复用。每条消息只捕获频道可见的在线 UUID 并排除发送者，公共聊天额外查询无重名歧义的绑定玩家。解析按实际名字长度和完整边界查询，选择最长匹配；同名时优先使用当前频道内的在线身份。索引和玩家快照一起发布，异步聊天不读取 Bukkit 状态。

## 管理与扩展

以下命令需要 `group.custodian`：

```text
/social
/social help
/social platforms
/social status [platform]
/social reload [platform]
/social conversations <platform>
```

省略平台的 status 显示全部状态，reload 独立重载全部适配器。首次登录的拒绝界面会提供身份指令，已入服可用 `/bind <platform>`。

新增平台时实现鉴权、可信身份映射、事件回复凭证、配置、`SocialCommandSyntax` 和 renderer，向 `context.events()` 提交事件。暴露身份绑定时，必须提供成员证据和加入／退出监测；缺少该能力不能注册绑定入口。完整名单与事件记录分别由适配器维护；仅掌握部分社区状态时不能推断全部缺席。成员核验与入服规则见[绑定白名单](identity-binding.md#绑定白名单与成员退出)。聊天接入另实现 `SocialChatPlatformSession` 并声明路由。共享命令、Bukkit 调度、身份仓储访问、去重和背压由 Host 与游戏网关负责。
