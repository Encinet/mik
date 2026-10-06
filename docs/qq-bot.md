# QQ 社交平台适配器

QQ 是通用 social Host 的协议适配器。它通过 QQ Bot Gateway WebSocket 接收事件，负责 Gateway 鉴权、心跳、断线恢复、Access Token、OpenAPI 被动回复、QQ 身份字段映射和 Markdown 渲染，不包含服务器查询或身份绑定业务。

该适配器不再启动 HTTP 服务，也不实现 Webhook、callback challenge 或 Ed25519 请求验签。服务器只需能够主动访问 QQ 的 HTTPS 与 WSS 服务；不需要公网域名、端口转发、反向代理或 Cloudflare Tunnel。若机器人管理后台仍保存了旧 callback 地址，应将事件接收方式切换为 WebSocket。

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

chat-bridge:
  routes: []

content-safety:
  enabled: true

reply:
  max-message-length: 1500
```

AppID 与 AppSecret 只从这个文件读取，不读取凭据环境变量。旧 `plugins/mik/qq-bot.yml` 不会自动迁移或加载；发现时日志会明确提示迁移。包含旧 `webhook:` 段的 `social/qq.yml` 会直接校验失败，必须删除该段后再重载。虽然 QQ 在 2026-08-10 的更新日志中宣布统一使用 `api.bot.qq.com`，但该域名的 Gateway discovery 在部分现有机器人上会返回 HTTP 401；当前暂时与 AstrBot 使用的 `qq-botpy 1.2.1` 保持一致，使用 `api.sgroup.qq.com` 与 `bots.qq.com`。配置文件中已写入统一域名的安装也会在运行时回退到这组兼容端点。

启动时适配器先申请 Access Token，再通过 `GET /gateway/bot` 动态发现当前 WSS 地址。Gateway discovery 和 OpenAPI 请求均携带 `Authorization: QQBot <access_token>` 与第三方 SDK 使用的 `X-Union-Appid: <app-id>`。连接收到 `HELLO` 后发送 `IDENTIFY`，订阅群聊与 C2C 事件；`READY` 后 `/social status qq` 显示 `READY`。连接期间按服务端下发的间隔发送心跳，收到重连指令或网络断开时使用 session ID 和最后 sequence 尝试恢复；无效 session 会重新鉴权建立新会话。

适配器同时处理需要 @ 的 `GROUP_AT_MESSAGE_CREATE` 与群聊全量消息 `GROUP_MESSAGE_CREATE`。若希望直接发送 `/在线` 等命令而不 @ 机器人，需要在手机 QQ 的群聊设置中打开机器人设置，将“机器人可获取的群聊消息范围”设为“获取群内全部消息”；平台未授予该范围时，Gateway 不会下发无 @ 消息，服务端代码无法自行绕过。

重连采用配置的指数退避。WebSocket 已建立但未在 `handshake-timeout-millis` 内完成 `READY/RESUMED` 时也会主动重连。连续失败超过 `max-reconnect-attempts` 后平台进入 `FAILED`，可在排除网络或凭据问题后执行 `/social reload qq`。

## 群内命令

QQ 使用 composition root 中显式安装的共享 `SocialCommand` 对象及不可变 dispatcher，不再维护 QQ 私有命令目录或别名表：

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

共享安全规则文件为 `plugins/mik/social/blocked-keywords.txt`。Host 只扫描文档显式标记的用户可控片段（目前是玩家名称），不会扫描固定回复、字段标签、TPS、MSPT、版本或管理状态；命中后用安全提示文档替换结果。旧 `plugins/mik/qq-blocked-keywords.txt` 只触发迁移警告，不会加载。

管理统一使用 `/social status qq`、`/social reload qq` 和 `/social conversations qq`。

## 聊天互通

`chat-bridge.routes` 把指定 QQ 群连接到 Minecraft 公共聊天，并通过共享 Host 继续转发到 Matrix 等其他社交平台。列表为空时不启用聊天互通。若配置了非空的 `allowed-group-openids`，每个桥接群也必须出现在该列表中。

QQ 群中的普通文本会进入 Minecraft 公共聊天；共享命令会优先解析，不会被重复当作聊天转发。从 Minecraft 发出的公共聊天通过 QQ OpenAPI 主动消息发送，staff 与私聊不会外发。消息沿用共享的内容安全、逐会话顺序、背压、去重与 `visitedPlatforms` 防回环机制。

完全互通会转发每条 Minecraft 公共消息：

```yaml
chat-bridge:
  routes:
    - group-openid: "your-group-openid"
      outbound:
        mode: "always"
```

也可以只转发带指定前缀的消息：

```yaml
chat-bridge:
  routes:
    - group-openid: "your-group-openid"
      outbound:
        mode: "prefix"
        prefix: "#"
        strip-prefix: true
```

上例会把 Minecraft 的 `#你好` 作为 `你好` 发到 QQ，Minecraft 内仍显示原始内容。QQ 出站使用 Markdown，并保留共享聊天节点中的基础样式、链接及可解析的玩家提及。QQ 身份按 AppID 和群 OpenID 隔离；只有在目标群绑定的账号才会生成 QQ 原生提及。

机器人需要在管理后台获准主动发言，并按需开启“获取群内全部消息”，否则只能收到 @ 机器人的事件，或主动消息被平台拒绝。修改配置后执行 `/social reload qq`。
