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

api-base-url: "https://api.sgroup.qq.com"
access-token-url: "https://bots.qq.com/app/getAppAccessToken"

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

AppID 与 AppSecret 只从此文件读取。使用示例中的 API 端点；配置与凭据错误可通过 `/social status qq` 检查，修改后执行 `/social reload qq`。

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
  groups:
    - group-openid: "first-group-openid"
      totp-secret: "YOUR_FIRST_BASE32_SECRET"
    - group-openid: "second-group-openid"
      totp-secret: "YOUR_SECOND_BASE32_SECRET"
```

将示例值替换为实际群 OpenID 与外部页面或验证器使用的 Base32 密钥。群 OpenID 不是 QQ 群号；两个群的密钥必须不同，解码后各至少 16 字节。验证码由外部生成，双方使用相同的 [RFC 6238 TOTP](https://www.rfc-editor.org/rfc/rfc6238) 参数：**HMAC-SHA1、6 位数字、30 秒周期、Unix 时间起点**。服务端与生成端须保持时钟同步；密钥只保存在配置中，不向群聊或内容安全服务发送。

申请人在验证消息中填写完整的 6 位验证码，保留开头的零；也支持“管理员审核问答”，任一答案完整匹配即可。允许验证码两侧留空白，正文中夹带的数字不提取为验证码。

以服务器收到 Gateway 申请事件的时间为准，只接受该时间所在的 30 秒周期或紧前一个周期，不接受下一周期或更早的验证码。例如收到申请时为 `12:00:45`，接受 `12:00:30–12:00:59` 和 `12:00:00–12:00:29` 生成的验证码。后台排队不会改变本次校验时间，事件中的 `apply_at` 不用于延长有效期。

匹配时调用官方[入群申请审批接口](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_groups_group_openid_approval_join_request_member_openid.post.html)通过对应申请；缺失、错误或过期则拒绝，不拉黑。未配置的群、机器人账号和已被 QQ 自动审批通过的事件不处理。相同申请会去重；网络或平台审批失败时记录警告，不视为已通过，可能需要管理员手动处理。重载或停用会关闭本次审批工作线程和连接。

修改后执行 `/social reload qq`。不需要新增游戏内命令，也不改变 `/bind qq` 的身份绑定验证码。

## 聊天互通

`chat-bridge.routes` 把指定 QQ 群连接到 Minecraft 公共聊天，并通过共享 Host 继续转发到 Matrix 等其他社交平台。列表为空时不启用聊天互通。若配置了非空的 `allowed-group-openids`，每个桥接群也必须出现在该列表中。

每个路由分别配置 `inbound`（**QQ群 → Minecraft**）与 `outbound`（**Minecraft → QQ群**）。每个方向支持 `always` 和 `prefix`，省略该方向时默认为 `always`。`outbound.prefix` 只控制发往 QQ 的消息；限制群消息进入游戏需配置 `inbound`。

QQ 群中的普通文本按 `inbound` 规则进入 Minecraft 公共聊天；共享命令会优先解析，不受聊天前缀限制，也不会被重复当作聊天转发。从 Minecraft 发出的公共聊天按 `outbound` 规则通过 QQ OpenAPI 主动消息发送，staff 与私聊不会外发。消息沿用共享的内容安全、逐会话顺序、背压、去重与 `visitedPlatforms` 防回环机制。

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

上例会把QQ群里的 `#你好` 作为 `你好` 发到游戏，普通 `你好` 不转发，只有 `#` 而无正文也不转发。`strip-prefix: false` 会保留前缀；设为 `true` 时会同步调整正文中可信 @提及的位置。

游戏发往 QQ 同样可在 `outbound` 下设置 `mode: "prefix"`、`prefix: "#"` 和 `strip-prefix: true`，此时游戏里的 `#你好` 会作为 `你好` 发到 QQ，游戏内仍显示原始内容。两个方向可使用不同前缀，也可同时设为 `prefix`。

QQ 出站使用 Markdown，并保留共享聊天节点中的基础样式、链接及可解析的玩家提及。QQ 身份按 AppID 和群 OpenID 隔离；只有在目标群绑定的账号才会生成 QQ 原生提及。

机器人需要在管理后台获准主动发言，并按需开启“获取群内全部消息”，否则只能收到 @ 机器人的事件，或主动消息被平台拒绝。修改配置后执行 `/social reload qq`。

重载后发现的[新公告](announcements.md#新公告同步)也会主动发送到上述路由配置的全部群，独立于聊天前缀规则；即使 `outbound.mode` 为 `prefix`，公告也无需添加前缀。公告使用群对应平台的默认语言标题，并沿用内容审核和消息长度上限。
