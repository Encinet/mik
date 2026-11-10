# QQ 社交平台适配器

QQ 是通用 social Host 的协议适配器。它通过 QQ Bot Gateway WebSocket 接收事件，负责 Gateway 鉴权、心跳、断线恢复、Access Token、OpenAPI 被动回复、QQ 身份字段映射和 Markdown 渲染，不包含服务器查询或身份绑定业务。

服务器通过出站 HTTPS 与 WSS 连接 QQ，无需公网回调地址或入站端口；机器人后台选择 WebSocket 接收事件。

QQ 群聊可以通过共享聊天桥与 Minecraft、Matrix 等其他已启用平台双向互通。加入/离开通知仍不在支持范围内。

## 配置

首次启动生成 `plugins/mik/social/qq.yml`：

```yaml
enabled: false
app-id: ""
app-secret: ""

gateway:
  debug: false
  reconnect-base-delay-millis: 1000
  reconnect-max-delay-millis: 30000
  max-reconnect-attempts: 10
  max-frame-bytes: 1048576
  handshake-timeout-millis: 30000

worker:
  max-concurrent-tasks: 64

allowed-group-openids: []

group-join:
  groups: []

chat-bridge:
  routes: []

content-safety:
  enabled: true

reply:
  max-message-length: 1500
```

AppID 与 AppSecret 只从此文件读取。OpenAPI 与令牌获取统一使用代码中固定的官方域名 `api.bot.qq.com`，无需配置接口地址；Gateway 的 WSS 地址仍通过官方接口动态发现。配置与凭据错误可通过 `/social status qq` 检查，修改后执行 `/social reload qq`。

启动时适配器先申请 Access Token，再通过 `GET /gateway/bot` 动态发现当前 WSS 地址。Gateway discovery 和 OpenAPI 请求均携带 `Authorization: QQBot <access_token>` 与第三方 SDK 使用的 `X-Union-Appid: <app-id>`。连接收到 `HELLO` 后发送 `IDENTIFY`，订阅群聊与 C2C 事件；`READY` 后 `/social status qq` 显示 `READY`。连接期间按服务端下发的间隔发送心跳，收到重连指令或网络断开时使用 session ID 和最后 sequence 尝试恢复；无效 session 会重新鉴权建立新会话。

适配器同时处理需要 @ 的 `GROUP_AT_MESSAGE_CREATE` 与群聊全量消息 `GROUP_MESSAGE_CREATE`。若希望直接发送 `/在线` 等命令而不 @ 机器人，需要在手机 QQ 的群聊设置中打开机器人设置，将“机器人可获取的群聊消息范围”设为“获取群内全部消息”；平台未授予该范围时，Gateway 不会下发无 @ 消息，服务端代码无法自行绕过。

重连采用配置的指数退避。WebSocket 已建立但未在 `handshake-timeout-millis` 内完成 `READY/RESUMED` 时也会主动重连。连续失败超过 `max-reconnect-attempts` 后平台进入 `FAILED`，可在排除网络或凭据问题后执行 `/social reload qq`。

## 群内命令

QQ 使用共享社交命令，常用写法如下：

| 命令 | 作用 |
| --- | --- |
| `/ai <问题>`、`/问ai <问题>` | 使用与游戏、Matrix 共用的 AI 助手；`/ai clear` 或 `/ai 清空` 清除当前用户在本群的历史 |
| `/在线`、`/在線`、`/online` | 在线玩家 |
| `/状态`、`/status` | 完整服务器状态，包含在线人数、TPS、MSPT、运行时间和版本 |
| `/帮助`、`/help` | 共享帮助 |
| `/绑定 <验证码>`、`/bind <验证码>` | 绑定当前群身份 |
| `/我的 [玩家名]`、`/profile [player]` | Minecraft 脸部头像、UUID、在线状态、游玩时间、首次加入和最近在线；查询他人要求调用者已绑定且为正式成员、协管或管理员 |
| `/解绑 确认`、`/unbind confirm` | 解除绑定 |

Minecraft 端统一执行 `/bind qq` 生成验证码。

上述表格仅列常用中英文写法；每条共享命令还注册了香港繁体、台湾繁体、文言、德语、西班牙语、法语、意大利语、日语、韩语、荷兰语、巴西葡萄牙语、俄语、泰语和乌克兰语别名。未绑定用户的回复语言取自实际命中的本地化别名；绑定后则优先跟随 Minecraft 语言设置。例如发送 `/プロフィール` 会得到日语回复，发送 `/profil` 会得到德语回复。

`/我的` 也支持 QQ 原生目标选择：在命令中 `@一名用户`，或者回复该用户的一条消息后发送 `/我的`。调用者仍必须已绑定且是正式成员；被提及或被回复的 QQ 身份必须已在当前群绑定 Minecraft。适配器只接受 Gateway 的 `mentions[].member_openid` 与引用消息作者 `member_openid`，并忽略机器人自身提及；reply 与 mention 指向同一人时会自动去重，出现多个不同目标时要求重新选择。聊天正文里手写的 `@昵称` 或伪造 `<@id>` 不会作为可信身份。

## 被动回复与安全

每个回复固定携带当前入站消息的 `msg_id` 和 `msg_seq=1`。Gateway 连接与 OpenAPI 回复共用 Access Token 缓存；OpenAPI 返回 401 时会失效缓存并重试一次。平台 renderer 对所有动态字段做 Markdown 转义，并在配置上限内截断。

内容安全由共享的 Qwen3Guard 服务提供。QQ 默认启用 `content-safety.enabled`，共享配置 `plugins/mik/social/content-safety.yml` 的 `api-key` 可留空使用匿名调用，也可填写 OVHcloud API 密钥以使用更高额度。配置损坏时，help、status 等可信回复仍可使用，需要审核的输出停止外发。含用户可控内容的命令回复按完整文档审核，拦截时显示安全提示；聊天和社区提醒在外发前审核。配置、判定与失败处理见[内容安全](social-platforms.md#内容安全)。

管理统一使用 `/social status qq`、`/social reload qq` 和 `/social conversations qq`。

## 入群审批

`group-join.groups` 最多配置两个群，各自使用不同的 TOTP 密钥；空列表关闭自动审批。机器人必须在这些群中拥有管理员身份，配置非空时会额外订阅官方 `GROUP_MEMBER_EVENT (1<<24)` 的 [`GROUP_JOIN_REQUEST` 事件](https://bot.q.qq.com/wiki/develop/api-v2/autogen/event/group_join_request.html)。如果设置了非空的 `allowed-group-openids`，审批群也必须在该列表中。

```yaml
group-join:
  reconcile-interval-seconds: 30
  groups:
    - group-openid: "first-group-openid"
      totp-secret: "YOUR_FIRST_BASE32_SECRET"
    - group-openid: "second-group-openid"
      totp-secret: "YOUR_SECOND_BASE32_SECRET"
```

将示例值替换为实际群 OpenID 与外部页面或验证器使用的 Base32 密钥。群 OpenID 不是 QQ 群号；两个群的密钥必须不同，解码后各至少 16 字节。验证码由外部生成，双方使用相同的 [RFC 6238 TOTP](https://www.rfc-editor.org/rfc/rfc6238) 参数：**HMAC-SHA1、6 位数字、30 秒周期、Unix 时间起点**。服务端与生成端须保持时钟同步；密钥只保存在配置中，不向群聊或内容安全服务发送。

申请人在验证消息中填写完整的 6 位验证码，保留开头的零；也支持“管理员审核问答”，任一答案完整匹配即可。允许验证码两侧留空白，正文中夹带的数字不提取为验证码。

以 QQ 提供的申请时间 `apply_at` 为准，只接受该时间所在的 30 秒周期或紧前一个周期，不接受下一周期或更早的验证码。例如申请提交于 `12:00:45`，接受 `12:00:30–12:00:59` 和 `12:00:00–12:00:29` 生成的验证码。即使服务器到 `12:05` 才恢复连接并处理，该申请仍按 `12:00:45` 校验。申请时间缺失、格式错误、早于 Unix 起点或超出服务器当前时间 30 秒时，不自动审批，留待人工处理。

实时事件之外，还通过官方[待审批申请列表接口](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_groups_group_openid_join_request_list.get.html)补扫。启动及 Gateway 连接就绪、恢复会话后立即触发；平时每个群完成一轮扫描后等待 `reconcile-interval-seconds` 再扫描，默认 30 秒，可设为 10–3600 秒。每页最多 50 条，两群交替推进分页，列表请求之间至少间隔 2.1 秒，积压与接口退避会使完整扫描耗时增加。断线期间的申请，只要仍在 QQ 待审批列表中，就能在恢复后补审。

匹配时调用官方[入群申请审批接口](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_groups_group_openid_approval_join_request_member_openid.post.html)通过对应申请；验证码缺失、错误或在申请时已过期则拒绝，不拉黑。未配置的群、机器人账号和已被 QQ 自动审批通过的事件不处理。实时事件和列表补扫共用审批入口，按群及申请 ID 去重；正在处理的申请不会重复提交，成功结果最多缓存 4096 条、保留 10 分钟。审批请求串行执行，至少间隔 1.1 秒。

网络或平台失败不会记为审批成功，后续补扫可重新提交；列表失败及审批的暂时性故障会退避重试，并遵守接口的 `Retry-After`。申请已被其他管理员处理等 4xx 错误不会让全部审批进入退避。某群列表失败不阻塞另一个群；工作队列满时保留当前分页稍后再扫，分页循环或超过 64 页时在下一轮从头扫描。日志不包含验证码、密钥或完整远端响应。重载或停用会取消补扫、审批任务和未完成的网络请求，并关闭连接。

QQ 的令牌获取、Gateway 地址发现、消息发送、图片上传和入群审批共用 HTTP 错误格式，保留操作、HTTP 状态、`code`、`err_code`、脱敏后的 `message` 和 `trace_id`，以及有效的 `Retry-After`。两个错误码分别保留，链路 ID 优先取响应正文，缺失时取 `X-Tps-trace-ID` 响应头。错误说明会清理已知凭据、被回显的请求内容、6 位验证码和控制字符，并限制长度；不输出完整响应或申请列表。网络超时、DNS、连接和 TLS 故障使用统一摘要，未知异常只记录类型。

补扫和审批失败的警告另带群 OpenID 与实际退避时长。重复补扫失败最长退避至 5 分钟，有效的 `Retry-After` 可以要求更长等待。例如 `HTTP 400, code=11703, err_code=40011030`、`机器人不是群管理员` 表示 QQ 接口判定机器人未拥有该群的管理员权限；若群内显示已授权，应核对配置 AppID 对应的机器人与目标群，并携链路 ID 向 QQ 平台排查。其他错误按[官方错误码说明](https://bot.q.qq.com/wiki/develop/api-v2/dev-prepare/api-call-guide.html#openapi-错误码)和日志中的错误说明处理。

修改后执行 `/social reload qq`。不需要新增游戏内命令，也不改变 `/bind qq` 的身份绑定验证码。

## 聊天互通

`chat-bridge.routes` 把指定 QQ 群连接到 Minecraft 公共聊天，并通过共享 Host 继续转发到 Matrix 等其他社交平台。列表为空时不启用聊天互通。若配置了非空的 `allowed-group-openids`，每个桥接群也必须出现在该列表中。

每个路由分别配置 `inbound`（**QQ群 → Minecraft**）与 `outbound`（**Minecraft → QQ群**）。每个方向支持 `always` 和 `prefix`，省略该方向时默认为 `always`。`outbound.prefix` 只控制发往 QQ 的消息；限制群消息进入游戏需配置 `inbound`。

QQ 群消息转换后按 `inbound` 规则进入 Minecraft 公共聊天；共享命令会优先解析，不受聊天前缀限制，也不会被重复当作聊天转发。从 Minecraft 发出的公共聊天按 `outbound` 规则通过 QQ OpenAPI 主动消息发送，staff 与私聊不会外发。消息沿用共享的内容安全、逐会话顺序、背压、去重与 `visitedPlatforms` 防回环机制。

QQ 入站转换覆盖官方群消息事件实际下发的以下内容，`GROUP_AT_MESSAGE_CREATE` 与 `GROUP_MESSAGE_CREATE` 使用同一转换流程：

| 内容 | 转换结果 |
| --- | --- |
| 普通文字、Unicode 表情、@提及 | 保留正文及表情；按可信成员字段转换 @提及，`@all` 仅显示文字 |
| 图片、GIF、表情图片 | `🖼️`、文件名、尺寸、文件大小及下载链接 |
| 视频 | `🎞️`、文件名、大小及视频链接 |
| 语音、音频 | `🎙️`、文件信息及音频链接；语音优先使用 QQ 提供的 WAV 转换地址，并保留 ASR 参考文本 |
| 群文件及其他附件 | `📎`、文件名、大小及下载链接 |
| 结构化卡片 | 图文、小程序、位置、名片、视频分享、一起听歌、图片等卡片保留标题、描述、来源、地址或昵称、提示及可用的跳转／预览链接 |
| 引用、并行消息、聊天记录 | 保留 `↪` / `💬` 标记、原发送者名字和嵌套正文、媒体与卡片 |
| Markdown 正文 | 保留正文和链接，按文字转换；不将其中的格式控制当作游戏格式执行 |
| 未知类型 | 尽量保留已知正文、附件和嵌套内容；完全缺少可读内容时显示 `📨` |

媒体与卡片以文字信息和可点击链接进入游戏、网页和 Matrix；网页当前也使用链接形式。链接有效期由 QQ 决定，不下载或缓存附件，也不会把消息场景的鉴权字段转发出去。只保留安全的 HTTP(S) 链接；QQ 提供的 `//` 下载地址补为 HTTPS。小程序等专用跳转协议无法通用打开时保留卡片的可读信息。

自己的正文与卡片、引用和聊天记录的展示内容分别保存。共享命令只解析自己的文字正文，附件说明不混入命令参数；引用或转发记录里的 `/绑定`、`/ai` 等不会执行。纯附件在 `inbound.mode: always` 下可转发；前缀模式仍需在自己的正文中写前缀，例如 `#` 加图片。

自己的正文最多归一化为 2000 字符，附加信息预算为 1999 字符，合计不超过共享聊天的 4000 字符上限。嵌套展开最多 4 层，每条消息最多处理 64 个元素；耗尽总预算时显示省略号，生成的媒体链接完整保留或整体省略。字段异常时跳过异常字段，保留其余可读内容。转换范围以[官方群消息事件](https://bot.q.qq.com/wiki/develop/api-v2/autogen/event/group_message_create.html)和[消息类型说明](https://bot.q.qq.com/wiki/develop/api-v2/server-inter/message/type/overview.html)为准，事件没有下发的原始内容无法恢复。

完全互通会转发每条 Minecraft 公共消息：

```yaml
chat-bridge:
  routes:
    - group-openid: "your-group-openid"
      inbound:
        mode: "always"
      outbound:
        mode: "always"
```

若只希望QQ群里带 `#` 的消息进入游戏，同时将游戏公共聊天全部发往 QQ：

```yaml
chat-bridge:
  routes:
    - group-openid: "your-group-openid"
      inbound:
        mode: "prefix"
        prefix: "#"
        strip-prefix: true
      outbound:
        mode: "always"
```

上例会把QQ群里的 `#你好` 作为 `你好` 发到游戏，普通 `你好` 不转发，只有 `#` 且没有其他文字或附件时也不转发。`strip-prefix: false` 会保留前缀；设为 `true` 时会同步调整正文中可信 @提及的位置。

游戏发往 QQ 同样可在 `outbound` 下设置 `mode: "prefix"`、`prefix: "#"` 和 `strip-prefix: true`，此时游戏里的 `#你好` 会作为 `你好` 发到 QQ，游戏内仍显示原始内容。两个方向可使用不同前缀，也可同时设为 `prefix`。

QQ 出站使用 Markdown，并保留共享聊天节点中的基础样式、链接及可解析的玩家提及。QQ 身份按 AppID 和群 OpenID 隔离；只有在目标群绑定的账号才会生成 QQ 原生提及。

机器人需要在管理后台获准主动发言，并按需开启“获取群内全部消息”，否则只能收到 @ 机器人的事件，或主动消息被平台拒绝。修改配置后执行 `/social reload qq`。

重载后发现的[新公告](announcements.md#新公告同步)也会主动发送到上述路由配置的全部群，独立于聊天前缀规则；即使 `outbound.mode` 为 `prefix`，公告也无需添加前缀。公告使用群对应平台的默认语言标题，并沿用内容审核和消息长度上限。
