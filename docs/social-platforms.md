# 社交平台架构

QQ、Matrix 共用命令、身份绑定、聊天桥、内容安全与运行时。平台适配器负责鉴权、协议、配置和最终渲染。配置与使用见 [QQ](qq-bot.md)、[Matrix](matrix-bot.md)、[身份绑定](identity-binding.md)及 [AI](ai.md)。

## 消息流程

```text
已验证的平台事件 → Adapter → SocialInboundMessage → Host
  → Command.decode → handle → present → SocialDocument → ReplyChannel

Minecraft 公共聊天 → ChatSubmission → ChatProcessor → ChatMessage
  → ChatContent → 会话路由 → 目标成员解析 → 平台 renderer

外部普通文本 → 同一入站入口 → 命令分类与路由 → Bukkit 主线程
  → ChatProcessor → Minecraft renderer
```

命令优先解析，未命中时才进入已配置的聊天路由；同一事件只分类一次。公共聊天可桥接，staff 与私聊不外发。`ChatOrigin.visitedPlatforms` 防止消息返回来源平台。

`ChatContent` 使用文本、链接、玩家提及、外部提及、广播提及和物品等语义节点。Minecraft、QQ、Matrix 各自渲染或降级，不在共享修饰器中拼平台标记。

## 平台 SPI 与生命周期

| 接口 | 契约 |
| --- | --- |
| `descriptor()` | 稳定平台 ID、显示名及可选的 `IdentityPlatform` |
| `prepare()` | 校验配置，禁用时返回空计划，异常只令该平台失败 |
| `SocialPlatformPlan.open(context)` | 固定本代运行策略并创建会话 |
| `SocialPlatformSession.close()` | 关闭传输与资源；状态由专属 sink 推送 `STARTING/READY/FAILED/STOPPED` |
| `SocialChatPlatformSession` | 提供聊天发送及目标会话提及解析 |
| `SocialChatRoute` | 会话级 `ALWAYS` 或 `PREFIX` 路由，可选择剥离前缀 |

Host 为每个平台独立维护运行代次（generation）、会话、身份平台注册租约、有界虚拟线程执行器、去重与会话快照。同会话按提交顺序处理，不同会话可并发。并发额度持续到异步回复结束；队列满时返回 `RETRY_LATER` 并撤销该次去重标记。Minecraft 发布端获得包含 accepted、unavailable、backpressured 数量的报告。

重载只替换指定平台，旧代任务与结果失效。机器人消息、重复事件及生命周期拒绝不会作为普通聊天继续处理。

## 命令与回复

每条 `SocialCommand<A, R>` 维护稳定 ID、文本别名、调用策略、描述、参数解析、业务处理、结果类型与回复布局。装配时显式创建不可变 dispatcher，帮助由已安装命令及当前平台前缀生成。平台 plan 只指定前缀和大小写规则；原生命令按稳定 ID 与结构化 options 调用。

`SocialDocument(title, tone, blocks)` 支持段落、字段、列表和远程或内嵌图片，`plainText()` 提供纯文本回退。renderer 负责转义、格式转换和长度限制。

回复语言依次取绑定玩家的 Minecraft 偏好、命中的本地化别名、平台默认语言。所有语言别名同时有效；原生命令可用 `locale` option 指定标签。内容安全替换和截断保留已解析语言。

命令通过 `withUntrustedText(...)` 标记用户可控片段供安全检查；聊天执行平台出站检查。社交入站到 Minecraft 的正文不经过关键词过滤。固定标签、版本、TPS 等可信字段不参与关键词扫描。

## 身份、提及与游戏数据

平台必须从已认证事件构造 `ExternalIdentityKey`、提及范围和回复作者，不接受正文中自报的账号或伪造 `@名字`。玩家名查询可直接读取游戏资料；平台提及与回复目标须在当前作用域已绑定，多个不同目标会被拒绝。

出站提及先按 Minecraft UUID 查绑定，再由平台会话确认目标账号确实属于当前房间或群。多个有效账号全部保留；无目标时仅保留可读文本。被长度限制截断的提及不发送原生通知。显示约定为游戏到平台 `@游戏名(平台昵称)`，平台到游戏 `@平台昵称(游戏名)`。

共享 handler 运行于有界 worker，Bukkit 数据由 `SocialGameService` 经主线程网关捕获为不可变快照。查询自己只需绑定；查询他人还须具有 `member`、`moderator` 或 `custodian` 角色。资料包含 UUID、在线状态、游玩时间、首次加入、最近在线和皮肤头像，不暴露平台身份字段。

入站聊天经共享 gateway 补充绑定及 LuckPerms 身份，再交给游戏公共聊天链路。已绑定身份使用游戏名字与前后缀，未绑定时使用平台昵称。提及输入语法为 `玩家名@`，显示为 `@玩家名`。自然聊天只在 Paper 事件最终未取消后提交提醒、重复状态及社交发布。

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
