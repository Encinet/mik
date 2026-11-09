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

`ChatContent` 使用文本、链接、玩家提及、外部提及、广播提及和物品等语义节点。Minecraft、QQ、Matrix 各自渲染或降级，不在共享修饰器中拼平台标记。

每个平台在 `SocialPlatformDescriptor.sourceColor` 显式声明来源标签的 RGB 颜色。Host 将颜色随 `ChatOrigin.Social` 传递，游戏聊天统一读取该快照渲染标签；新增平台只需在自己的适配器中声明显示名与颜色。

## 平台 SPI 与生命周期

| 接口 | 契约 |
| --- | --- |
| `descriptor()` | 稳定平台 ID、显示名、来源标签颜色及可选的 `IdentityPlatform` |
| `prepare()` | 校验配置，禁用时返回空计划，异常只令该平台失败 |
| `SocialPlatformPlan.open(context)` | 固定本代运行策略并创建会话 |
| `SocialPlatformSession.close()` | 关闭传输与资源；状态由专属 sink 推送 `STARTING/READY/FAILED/STOPPED` |
| `SocialChatPlatformSession` | 提供聊天发送及目标会话提及解析 |
| `SocialChatPlatformSession.sendNotice` | 向指定会话发送服务端文档，QQ 与 Matrix 各自渲染，不套用普通聊天前缀 |
| `SocialChatRoute` | 会话的入站与出站各自使用 `ALWAYS` 或 `PREFIX`，可选择剥离前缀；入站同步调整可信提及范围 |

Host 为每个平台独立维护运行代次（generation）、会话、身份平台注册租约、有界虚拟线程执行器、去重与会话快照。同会话按提交顺序处理，不同会话可并发。并发额度持续到异步回复结束；队列满时返回 `RETRY_LATER` 并撤销该次去重标记。Minecraft 发布端获得包含 accepted、unavailable、backpressured 数量的报告。

重载只替换指定平台，旧代任务与结果失效。机器人消息、重复事件及生命周期拒绝不会作为普通聊天继续处理。

公告功能通过自己的 `AnnouncementPublisher` 发布新增时间戳与正文，`bootstrap` 将该能力连接到 Social；公告不依赖具体协议或 Social 运行时。Social 使用每个平台的默认语言构造文档，在同一有界执行器的独立有序队列中审核并投递到全部已配置路由。每个目的地分别处理发送错误，一个群或平台失败不会中止其他投递。公告使用 `REQUIRED` 审核策略，拦截时取消该平台的发送，不向全部群广播拦截占位提示。

## 命令与回复

每条 `SocialCommand<A, R>` 维护稳定 ID、文本别名、调用策略、描述、参数解析、业务处理、结果类型与回复布局。装配时显式创建不可变 dispatcher，帮助由已安装命令及当前平台前缀生成。平台 plan 只指定前缀和大小写规则；原生命令按稳定 ID 与结构化 options 调用。

`SocialDocument.of(moderation, title, tone, blocks...)` 支持段落、字段、列表和远程或内嵌图片，`plainText()` 提供纯文本回退。renderer 负责转义、格式转换和长度限制。

回复语言依次取绑定玩家的 Minecraft 偏好、命中的本地化别名、平台默认语言。所有语言别名同时有效；原生命令可用 `locale` option 指定标签。内容安全替换和截断保留已解析语言。

每份回复在创建时必须显式传入 `SocialDocument.Moderation`，构造器和工厂都没有默认策略，也没有创建后修改策略的接口；未声明策略的调用无法编译。回复辅助方法同样传递必填策略。

```java
SocialDocument.of(TRUSTED, "帮助", Tone.INFO, paragraph("固定命令说明"));
SocialDocument.of(REQUIRED, "AI", Tone.INFO, paragraph(answer));
```

`TRUSTED` 用于本地模板和可信统计，直接发送；`REQUIRED` 用于含用户输入、玩家名称或 AI 生成文本的回复，在平台开启内容安全时送审。检查覆盖截断后的完整文档，保留标题、字段与正文语境。语言转换和截断始终保留原策略。

| 回复内容 | 策略 |
| --- | --- |
| help、status、未知命令及固定说明、确认、错误提示 | `TRUSTED` |
| online | 有玩家名称时为 `REQUIRED`，空列表为 `TRUSTED` |
| profile 查询成功、含玩家名称的绑定或解绑结果 | `REQUIRED` |
| AI 实际回答 | `REQUIRED`；清空、繁忙、失败等固定提示为 `TRUSTED` |

聊天与社区公示提醒显式使用 `REQUIRED`，在路由选择和提及解析后检查出站语义文本，包含发送者、展开的可见提及和链接目标。同次发布的多个会话若产生相同文本，复用本次判定。社交入站到 Minecraft 的正文不执行出站审核。

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

健康入口等权轮询：未启用 Eden 时为 1:1，启用后为 1:1:1。OVH 端点为 `https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions`；Eden 使用 `https://api.edenai.run/v3/universal-ai`，模型标识为 `text/moderation/ovhcloud/Qwen3Guard-Gen-8B`。两家凭据独立，匿名 OVH 请求省略 `Authorization`，有密钥的入口发送各自的 `Bearer` 鉴权；禁止自动跟随重定向。

配置损坏时记录警告，平台继续启动，help、status 等可信回复仍可发送；审核客户端的可用性只在处理 `REQUIRED` 内容时检查，审核不可用时拒绝该次外发。修复配置后执行 `/social reload`，或重载对应平台。密钥和远端错误正文不写入日志。

[OVHcloud 官方限流说明](https://www.ovhcloud.com/en-gb/public-cloud/ai-endpoints/catalog/qwen-guard-gen-06b/)：匿名调用按 IP 和模型限制为每分钟 2 次；使用 API 密钥时按 Public Cloud 项目和模型限制为每分钟 400 次。超出额度返回 HTTP 429，并触发下述故障切换。Eden 的额度与鉴权由 Eden 账号管理，不能用 OVH 密钥代替。

OVH 按模型官方的文本审核用法，将待发送内容原样放入单条 `user` 消息，以 `temperature: 0`、非流式方式请求分类；Eden 按其 [Universal AI 文本审核协议](https://www.edenai.co/docs/v3/expert-models/features/text/moderation)将同一文本放入 `input.text`。不添加通用聊天助手的系统提示，也不附带入站命令、历史、验证码或传输凭据。命令审核包含准备向平台展示的文本资料；聊天使用去掉路由触发前缀并经 ChatModifier 展开的内容。图片只审核文字说明及文档中的远程链接，不上传图片二进制，也不检查图片像素。

OVH 仅完整的 `Safety: Safe` 且 `Categories: None` 结果放行，`Controversial` 和 `Unsafe` 均拦截。Eden 返回归一化风险结果，仅 `status: success`、提供方及任务正确、总风险与返回的每个分类的 likelihood 均为最低等级 `1` 时放行；`items` 按协议可省略，总风险及分数为必填项，数值必须类型及范围有效，缺失必填字段和未知格式不放行。明确的风险判定不向另一模型重审。命令回复被拦截时显示原语言的隐藏提示；聊天和社区提醒被拦截时取消本次外发。超过模型输入预算的内容直接拦截，不裁掉尾部后送审。

连接失败、超时、响应过大、结果无法解析、HTTP 408/425/429/5xx 会切换到尚未尝试的可用入口，每个入口每次最多尝试一次。429 默认冷却 60 秒，其余临时错误默认冷却 5 秒；HTTP 返回的有效 `Retry-After`（秒数或 HTTP 日期）优先使用，上限一天。冷却期间跳过该入口。401/402/403/404 将该入口停用到重载，并尝试其他入口；其他 HTTP 错误直接中止；无效结果对应的入口冷却 5 秒并继续尝试其他入口。所有入口不可用、重试耗尽或总超时后停止本次外发并记录错误。

请求在有界 Social worker 中执行，`request-timeout-millis` 是包含故障切换和完整响应体的总预算；每次尝试按剩余入口数分配剩余时间，确保卡住的响应也能切换。执行 `/social reload` 或 `/social reload <platform>` 会重新读取共享配置、清除入口故障状态并关闭旧审核客户端，正在等待旧客户端的审核可能取消；指定平台的重载仍只重建该平台会话。停用 Social 时关闭共享客户端并取消请求。旧词库不再读取，可删除服务器上遗留的词库文件。

## 身份、提及与游戏数据

平台必须从已认证事件构造 `ExternalIdentityKey`、提及范围和回复作者，不接受正文中自报的账号或伪造 `@名字`。玩家名查询可直接读取游戏资料；平台提及与回复目标须在当前作用域已绑定，多个不同目标会被拒绝。

出站提及先按 Minecraft UUID 查绑定，再由平台会话确认目标账号确实属于当前房间或群。多个有效账号全部保留；无目标时仅保留可读文本。被长度限制截断的提及不发送原生通知。显示约定为游戏到平台 `@游戏名(平台昵称)`，平台到游戏 `@平台昵称(游戏名)`。

共享 handler 运行于有界 worker，Bukkit 数据由 `SocialGameService` 经主线程网关捕获为不可变快照。查询自己只需绑定；查询他人还须具有 `member`、`moderator` 或 `custodian` 角色。资料包含 UUID、在线状态、游玩时间、首次加入、最近在线和皮肤头像，不暴露平台身份字段。

入站聊天经共享 gateway 补充绑定及 LuckPerms 身份，再交给共享聊天服务，统一投递游戏、网页和其他 Social 平台的公共会话。作者没有 Minecraft 绑定或绑定玩家游戏离线时仍可在网页显示。已绑定身份使用游戏名字与前后缀，未绑定时使用平台昵称。Minecraft 内提及输入语法为 `@玩家名` 或 `@ 玩家名`，统一显示为 `@玩家名`；中日韩正文无需用空格隔开，例如 `你好@Alex在吗`，`@all` 使用相同边界。自然聊天只在 Paper 事件最终未取消后提交提醒、重复状态及社交发布。

玩家提及使用不可变的名字哈希索引；在线玩家与绑定玩家分别维护，名字与候选集合不变时跨消息复用。每条消息只捕获频道可见的在线 UUID 并排除发送者，公共聊天额外查询无重名歧义的绑定玩家。解析按实际名字长度和完整边界查询，选择最长匹配；同名时优先使用当前频道内的在线身份。索引和玩家快照一起发布，异步聊天不读取 Bukkit 状态。

## 社区公示提醒

地段登记后选取附近已登记地段的主人，异议提醒公示作者，裁决和撤回提醒相关当事人；每人每小时最多提交 5 条异议，自动登记公示不占该额度。

Social 按 UUID 和语言偏好，只向已确认收件人绑定账号在场的配置会话发送原生提及，每个平台选择首个匹配会话。普通聊天的前缀条件不限制系统提醒；无绑定或无法确认成员关系时不群发。

在线玩家同时收到私有提示，离线玩家在 7 天内登录可收到一次。`plots/board-alerts.yml` 保存处理与游戏投递记录，启动只处理新事件。平台通知尽力发送，不代表收件、同意或正式裁决。

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

省略平台的 status 显示全部状态，reload 独立重载全部适配器。身份验证码使用 `/bind <platform>`。

新增平台时实现鉴权、可信身份映射、事件回复凭证、配置、`SocialCommandSyntax` 和 renderer，向 `context.events()` 提交事件。聊天接入另实现 `SocialChatPlatformSession` 并声明路由。共享命令、Bukkit 调度、身份仓储访问、去重和背压由 Host 与游戏网关负责。
