# Matrix 机器人

首次启动会生成 `plugins/mik/social/matrix.yml`。创建专用 Matrix 账号，加入用于成员核验的 Space／房间和需要使用机器人的聊天房间，然后填写该账号的 Client-Server API access token：

```yaml
enabled: true
homeserver-url: "https://matrix.example.org"
access-token: "syt_your_access_token"
membership:
  room-ids:
    - "!communitySpace:example.org"
commands:
  room-ids:
    - "!botChat:example.org"
```

`homeserver-url` 必须是实际提供 Client-Server API 的地址，不是 Element 等网页客户端地址。access token 只从此文件读取，并通过 `Authorization: Bearer` 请求头发送。

| 配置 | 授予的能力 |
| --- | --- |
| `membership.room-ids` | 成员资格来源，可填写 Space 或普通房间；启用时必须非空 |
| `commands.room-ids` | 接受绑定等机器人命令的聊天房间，支持加密；启用时必须非空 |
| `chat-bridge.routes` | 聊天互通与公告投递；空列表关闭桥接 |

三者独立配置，适配器同步其并集。房间 ID 使用 `!` 开头的不透明标识，可在客户端的房间／空间设置中查看；`#mikcasual:matrix.org` 这样的名称是别名，不能填作房间 ID。机器人须分别加入资格来源和消息房间。

配置完成后执行 `/social reload matrix`，再用 `/social status matrix` 检查状态。机器人命令前缀固定为 `!`：

```text
!帮助
!ai Who is online?
!在线
!状态
!绑定 123456
!我的
!解绑 确认
```

`!ai <问题>` 使用与游戏和 QQ 共用的 AI 助手；`!ai clear` 清除当前 Matrix 用户在本房间的对话历史。AI 提供商、网页搜索、游戏查询、能力包和多语言 prompt 统一由 `plugins/mik/ai.yml` 配置，详见 [AI 助手](ai.md)。

首次登录从 Minecraft 拒绝界面取得验证码；已在游戏内也可用 `/bind matrix` 生成，再到命令房间发送 `!绑定 <验证码>`。兑换时同时核验该 Matrix 账号在资格来源中的成员状态。Matrix 身份不按房间隔离，绑定一次后可在所有接入房间使用。

## 端到端加密

加密由 [Trixnity 5.10.1](https://trixnity.connect2x.de/docs/client/features/) 与 vodozemac 管理，包括 Olm 设备会话、Megolm 群会话、签名验证、一次性密钥、密钥轮换、设备验证和房间密钥备份。发现 `m.room.encryption` 后，聊天、命令回复、公告和图片消息自动使用加密；同一房间中的明文聊天不会作为命令执行或转发。Space、未同步、断线或机器人已退出的房间不能发送消息。

access token 必须属于机器人专用的独立设备会话。首次启用时，该设备须尚未注册加密密钥；已有加密设备必须保留对应的 MIK 密钥库。适配器会拒绝用空数据库覆盖已有设备身份，不能直接复用 Element 等客户端正在使用的设备 token。

密钥、会话、重放记录和同步游标保存在 `plugins/mik/social/matrix-crypto/`，按 homeserver、账号和设备分别存储。数据库使用 AES 加密，`database.key` 和数据库必须一起备份；该目录在支持 POSIX 权限的系统中仅允许服务账号访问。重载与重启复用设备身份和密钥，不重新生成设备密钥。重启后沿用已提交的游标，并获取完整房间状态重建社区名单；首批离线消息不作为新命令执行。

机器人账号已在可信客户端设置恢复密钥时，可配置：

```yaml
encryption:
  recovery-key: "你的 Matrix 恢复密钥"
```

该密钥用于恢复交叉签名秘密、验证机器人设备，并接入账号已有的加密密钥备份。留空时可通过设备验证建立信任。适配器不会重置账号已有的交叉签名或恢复设置。

### 设备验证

以下命令仅能在服务器控制台使用：

| 命令 | 用途 |
| --- | --- |
| `matrixcrypto status` | 查看账号、设备、Ed25519 指纹、验证状态及当前 SAS 数字／表情 |
| `matrixcrypto status <用户ID> <设备ID>` | 查看指定设备的信任状态 |
| `matrixcrypto verify <用户ID> <设备ID>` | 向确切设备发起验证 |
| `matrixcrypto accept <事务ID>` | 推进当前请求、SAS 启动或接受步骤 |
| `matrixcrypto confirm <事务ID>` | 两端的三组数字或七个表情一致后确认 |
| `matrixcrypto cancel <事务ID>` | 取消验证，包括两端比较结果不一致时 |

也可在 Matrix 客户端向机器人设备发起验证，然后通过 `status` 查看事务和当前步骤，再使用 `accept` 推进。每一步都核对事务 ID；最终确认始终需要管理员比较两端结果。

### 收发与故障

解密后的文本、提及、回复、线程和媒体说明进入同一消息投影与绑定链路。图片先按 Matrix v2 附件格式使用 AES-256-CTR 加密，再上传密文；附件密钥、哈希、文件名和说明随 Megolm 消息发送。启用聊天桥接后，插件会在服务器内解密，再将所配置的聊天内容转发到 Minecraft 或其他平台。

消息按房间顺序等待密钥，最多缓存 512 条待解密事件，单条最多等待两分钟。等待密钥不阻塞 `/sync`、社区成员退出检测或其他房间；达到容量上限时通过背压重试。离开房间、关闭或重载会取消对应的等待。解密失败、设备签名无效、来源无法认证或检测到重放时，不执行命令、不转发正文，也不降级发送明文。只有原始发送设备直接提供，或机器人自己的已验证设备可信转交的密钥可以认证入站消息；未认证的备份密钥不用于执行社交命令。

一次性密钥和设备列表变化交给 SDK 处理。群会话遵循房间配置的轮换周期；未配置时使用七天或 100 条消息。成员退出后轮换群会话，后续密钥不发给已退出设备。出站遇到同步状态更新时异步等待更新完成；退出、加密状态变化与发送失败都重新检查房间状态。

## 成员资格

成员名单与实时变化统一来自 `/sync`，关闭成员懒加载。首次同步（不带 `since`）和 `full_state=true` 响应直接建立完整名单，无需额外调用 `/members`，也不依赖可缺省的 `prev_batch`。完整状态整体替换旧名单，增量状态只更新对应成员；时间线被裁剪时仍应用缺口中的成员状态变化。

增量同步首次发现一个房间或重新加入时，先记录成员变化并将该来源的资格保持为未知，下一轮携带当前游标请求完整状态。补全复用同一条同步链路，不等待独立的名单网络请求；聊天、密钥分发和退出检测持续处理。完整状态必须包含机器人自身的 `join` 成员记录，非法成员状态会使本次同步失败，沿用连接重试流程，不据此推断用户缺席。完整状态中消失的房间撤回成员证明，按未知处理，不会推断该房间的所有用户退出。

只有 `membership.room-ids` 授予入服资格。账号仍在任意一个资格来源中就保留绑定，确认离开最后一个才自动解绑；只有命令或聊天路由的房间不参与资格判断。若以 `#mikcasual:matrix.org` 空间为社区边界，将该 Space 的实际房间 ID 放入资格列表，子聊天房间放入命令列表或聊天路由。

Space 与子房间各自维护成员资格，[加入空间不会自动加入其中的房间](https://www.matrix.org/docs/chat_basics/public-rooms/#spaces)。适配器不展开空间层级，也不把子房间成员推断为空间成员。子房间可使用 [`restricted` 加入规则](https://spec.matrix.org/v1.19/client-server-api/#restricted-rooms)允许空间成员加入，但该规则只在加入时检查，退出 Space 不会自动退出子房间；资格判断仍直接核验 Space。

读取失败或机器人退出资格来源不代表所有用户退出。未知来源不能证明缺席；仍有其他已确认来源时保留准入，否则保留绑定并暂停依赖该平台的新登录。同步断线时暂时撤回成员证明，恢复同一游标的增量后重新发布；游标失效则重建完整名单。已有在线玩家不因核验故障被误踢。入服与单条解绑规则见[外部身份绑定](identity-binding.md#绑定白名单与成员退出)。

当前不自动审批加入申请，也不会自动加入收到的房间邀请；Space 的 `knock` 验码审批需另外接入验证与邀请流程。

## 协议范围

2026-11-12 对照最新稳定版 [Matrix 1.19](https://matrix.org/blog/2026/07/08/matrix-v1.19-release/) 和 [Client-Server API](https://spec.matrix.org/v1.19/client-server-api/) 核对。适配器使用稳定的 `/client/v3` 接口和 `/media/v3/upload`；API 路径版本与规范发布版本分别演进。

| 入站内容 | 跨平台显示与命令行为 |
| --- | --- |
| `m.text` | 优先读取安全的 HTML 文本，回退到 `body`；仅未截断的本条正文可执行命令 |
| `m.notice`、`m.emote` | 保留正文并标注 `[通知]`、`[动作]`；不执行命令，避免自动回复通知形成循环 |
| `m.image`、`m.audio`、`m.video`、`m.file` | 显示 `[图片]`、`[音频]`、`[视频]`、`[文件]`，保留用户填写的说明；不把媒体说明作为命令，不附加文件名、尺寸、大小或媒体地址 |
| `m.location`、`m.sticker` | 保留文本说明并标注 `[位置]`、`[贴纸]` |
| 未知 `msgtype` | 使用规范要求的纯文本 `body` 回退并标注 `[消息]`；不执行命令 |
| 自定义表情与内联图片 | 读取 `<img>` 的 `alt`；没有替代文本时，按 `data-mx-emoticon` 显示 `[表情]` 或 `[图片]`，不请求图片地址 |

媒体的 `body` 在 `filename` 缺失或两者相同时代表文件名，只显示类型占位；两者不同时才将 `body` 和可选的 HTML 作为说明保留，遵循规范的[媒体说明规则](https://spec.matrix.org/v1.19/client-server-api/#media-captions)。正文和类型标签合计最多 4,000 个 UTF-16 单元，截断保留完整 Unicode 字符、内联表情和可信提及。正文投影保留提及和图片各自的来源。命令资格独立于显示占位符判断，图片的 `alt` 可读文本也不参与命令执行；媒体说明、通知、嵌套回退和截断片段同样不会成为命令。

回复通过 `m.relates_to.m.in_reply_to` 识别；仅有回复关系时才剥离引用回退，普通引用文本保留。线程保留 `m.thread` 根事件；`is_falling_back: true` 表示线程连续性回退，不作为用户主动选择的回复对象。出站回复直接发送关系元数据。详见[富回复](https://spec.matrix.org/v1.19/client-server-api/#rich-replies)与[线程](https://spec.matrix.org/v1.19/client-server-api/#threading)。

适配器承担实时聊天桥接：不同步反应与编辑／撤回操作，也不回填 `/sync` 中被 homeserver 裁剪的历史时间线。编辑事件不会重新执行命令；缺少正文的已撤回事件不会转发。每个房间首次同步或机器人重新加入时，只建立状态，不执行或转发该批历史消息；背压重试也不会把这批历史变成新命令。

## 聊天互通

`chat-bridge.routes` 把 Matrix 房间连接到 Minecraft 公共聊天。列表为空时不启用聊天互通；路由不需要重复填入资格来源，只有同时列入 `commands.room-ids` 的房间才执行机器人命令。Matrix 身份已绑定时显示 `[Matrix] 游戏内身份 » 内容`，并且玩家离线时仍会复用 LuckPerms 前后缀；未绑定时才显示 `[Matrix] Matrix昵称 » 内容`。只有名称悬浮显示平台昵称、平台账号、绑定玩家和 UUID，前缀、后缀和基岩标记不会继承该悬浮；分隔符、颜色、发送时间悬浮和点击复制都复用游戏公共聊天的渲染链路。在命令房间识别出的指令由共享命令系统处理，不会作为聊天重复转发。

每个路由的 `inbound` 控制 **Matrix → Minecraft**，`outbound` 控制 **Minecraft → Matrix**。两个方向独立支持 `always` 或 `prefix`，省略该方向时默认为 `always`。机器人命令不受聊天前缀限制。

Matrix 发往游戏的普通聊天不执行出站审核。启用 `content-safety.enabled` 后，Minecraft 发往 Matrix 的聊天由共享 Qwen3Guard 检查路由处理及 ChatModifier 展开后的语义正文，配置见[内容安全](social-platforms.md#内容安全)。HTTP(S) URL、邮箱地址、`matrix.to`/`matrix:` 链接、颜色、粗体、斜体、下划线和删除线会转换为 Matrix 的 `org.matrix.custom.html`，同时保留纯文本 `body` 作为客户端回退；邮箱在 Matrix 中使用安全的 `mailto:`，在只接受 HTTP(S) 打开动作的 Minecraft 客户端中降级为左键复制地址。Matrix URI 会规范化成标准 `https://matrix.to/#/...` 永久链接。不导出游戏内命令、文件打开等 Minecraft 专用点击动作。

Minecraft 内提及玩家使用 `@Alex` 或 `@ Alex`，最终在所有界面统一显示为 `@Alex`；裸写 `Alex` 或写成 `Alex@` 都不会触发玩家 mention。公共聊天的候选集合同时包含在线玩家和所有已绑定玩家，因此目标离线但正在 Matrix 房间里时仍能收到提及。Matrix 入站只信任事件的 `m.mentions.user_ids` 以及与之对应的 Matrix HTML 用户链接或明确的纯文本 mention，不会把手写或伪造的 `@用户` 当成认证身份。

mention 在每个出站房间分别解析，显示规则如下：

- Minecraft → Matrix：`@游戏名(Matrix昵称)`。
- Matrix → Minecraft：`@Matrix昵称(游戏名)`。
- 其他社交平台 → Matrix：`@目标Matrix昵称(游戏名)`。

若一个 Minecraft 玩家绑定了多个 Matrix 账号，MIK 会只选择当前目标房间内处于 `join` 状态的账号，并全部显示、全部写入 Matrix 原生 `m.mentions.user_ids`。例如 `@Alex(Alex One, Alex Two)`。未加入该房间的绑定账号不会收到通知；没有可用目标账号时仍保留可读的 mention 文本，但不会生成原生通知。消息被长度限制截断到 mention 中间时同样不会误发通知。

连接使用 Matrix Client-Server API 的增量 `/sync` 长轮询：首次请求建立房间状态并保存 `next_batch`，之后请求携带 `since`，不是固定间隔轮询，因此房间事件通常会由 homeserver 立即返回。成员名单按[成员资格](#成员资格)中的流程建立，mention 始终按确切目标房间判断。MIK 在每个 Matrix 房间内按顺序处理事件，不同房间可并发；出站也使用异步 HTTP，不占用 Minecraft 主线程。`sync.timeout-millis` 只是空闲长轮询保持时间，不是消息等待时间。

同步请求使用 `use_state_after=true`。响应包含 `state_after` 时，只使用这份最终状态更新成员和房间属性，空列表也具有同样语义；普通 `state` 响应则按状态和时间线的顺序应用。`MatrixSyncRequest` 将请求游标与完整状态标志一起传给状态处理，重试不会把新房间的历史消息视为新命令。

HTTP 响应均受 `sync.max-response-bytes` 限制，包括分块响应。普通请求的 20 秒时限覆盖整个响应体，长轮询额外加上请求的等待时间。取消回复或关闭平台会取消底层 HTTP 交换；图片上传与消息发送共用取消链，取消不会触发文字补发。关闭后不能重新发布成员证明或恢复就绪状态。

同步遇到限流时优先遵循 HTTP `Retry-After`（秒数或 HTTP 日期），没有有效响应头时读取错误响应的 `retry_after_ms`；服务器要求的等待时间不会被重连退避上限缩短。出站 HTML 使用 `span data-mx-color` 表达颜色，Matrix 用户永久链接按 RFC 3986 编码，保留 MXID 中的 `+`、`/` 等字符。详见[限流](https://spec.matrix.org/v1.19/client-server-api/#rate-limiting)与[消息格式](https://spec.matrix.org/v1.19/client-server-api/#mroommessage-msgtypes)。

完全互通会把每条 Minecraft 公共消息发到该房间：

```yaml
chat-bridge:
  routes:
    - room-id: "!public:example.org"
      outbound:
        mode: "always"
```

前缀触发只转发匹配的消息。例如玩家发送 `#你好` 时，下面的路由会向 Matrix 发送 `玩家名: 你好`；Minecraft 内仍显示玩家原本输入的 `#你好`：

```yaml
chat-bridge:
  routes:
    - room-id: "!relay:example.org"
      outbound:
        mode: "prefix"
        prefix: "#"
        strip-prefix: true
```

可以同时配置多个路由，并让不同房间分别使用 `always` 或 `prefix`。`strip-prefix: false` 会保留触发前缀。延迟发送、`/public <消息>` 和公开消息重复发送同样经过这套共享规则；staff 与私聊不会外发。`content-safety.enabled: true` 时，共享 Qwen3Guard 检查含用户可控内容的机器人回复、Minecraft 发往 Matrix 的聊天及社区提醒；共享配置 `social/content-safety.yml` 中的 `api-key` 可留空使用匿名调用，额度见[内容安全](social-platforms.md#内容安全)。修改后执行 `/social reload matrix`。

如需限制 Matrix 消息进入游戏，在对应路由下配置同样结构的 `inbound`：

```yaml
chat-bridge:
  routes:
    - room-id: "!relay:example.org"
      inbound:
        mode: "prefix"
        prefix: "#"
        strip-prefix: true
      outbound:
        mode: "always"
```

此时 Matrix 的 `#你好` 转发为游戏中的 `你好`，无前缀或只有前缀而无正文的消息不转发；入站去除前缀会同步调整可信提及的位置。游戏发往 Matrix 继续按独立的 `outbound` 规则处理。

重载后发现的[新公告](announcements.md#新公告同步)会发送到 `chat-bridge.routes` 的全部房间，使用独立的服务端通知入口，保持 HTML 转义和纯文本回退。公告不受聊天前缀限制，仍按平台的内容安全配置和消息长度上限处理。
