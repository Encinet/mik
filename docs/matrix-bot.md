# Matrix 机器人

首次启动会生成 `plugins/mik/social/matrix.yml`。创建一个专用 Matrix 账号，将它加入需要使用机器人的未加密房间，然后填写该账号的 Client-Server API access token：

```yaml
enabled: true
homeserver-url: "https://matrix.example.org"
access-token: "syt_your_access_token"
allowed-room-ids:
  - "!roomId:example.org"
```

`homeserver-url` 必须是实际提供 Client-Server API 的地址，不是 Element 等网页客户端地址。access token 只从此文件读取，并通过 `Authorization: Bearer` 请求头发送。建议用专用账号和最小房间权限保护 token，同时用 `allowed-room-ids` 限制可接收命令的房间；留空会接受机器人已加入的所有未加密房间。

配置完成后执行 `/social reload matrix`，再用 `/social status matrix` 检查状态。机器人命令前缀固定为 `!`：

```text
!帮助
!在线
!状态
!绑定 ABCDEFGH
!我的
!解绑 确认
```

身份验证码也可以先在游戏内用 `/bind matrix` 生成，再到 Matrix 房间发送 `!绑定 <验证码>`。Matrix 身份不按房间隔离，绑定一次后可在所有允许房间使用。

当前适配器不处理端到端加密事件。若房间启用了加密，机器人看到的只会是 `m.room.encrypted`，命令不会执行；请使用专门的未加密机器人房间。

## 聊天互通

`chat-bridge.routes` 把 Matrix 房间连接到 Minecraft 公共聊天。列表为空时不启用聊天互通；每个房间必须同时包含在非空的 `allowed-room-ids` 中。Matrix 身份已绑定时显示 `[Matrix] 游戏内身份 » 内容`，并且玩家离线时仍会复用 LuckPerms 前后缀；未绑定时才显示 `[Matrix] Matrix昵称 » 内容`。只有名称悬浮显示平台昵称、平台账号、绑定玩家和 UUID，前缀、后缀和基岩标记不会继承该悬浮；分隔符、颜色、发送时间悬浮和点击复制都复用游戏公共聊天的渲染链路。命令仍由共享命令系统处理，不会作为聊天重复转发。

Matrix 发往游戏的普通聊天不经过 `blocked-keywords.txt`。Minecraft 发往 Matrix 时仍执行共享内容安全检查，并同时检查原始消息与 ChatModifier 展开的可见文本。HTTP(S) URL、邮箱地址、`matrix.to`/`matrix:` 链接、颜色、粗体、斜体、下划线和删除线会转换为 Matrix 的 `org.matrix.custom.html`，同时保留纯文本 `body` 作为客户端回退；邮箱在 Matrix 中使用安全的 `mailto:`，在只接受 HTTP(S) 打开动作的 Minecraft 客户端中降级为左键复制地址。Matrix URI 会规范化成标准 `https://matrix.to/#/...` 永久链接。不导出游戏内命令、文件打开等 Minecraft 专用点击动作。

Minecraft 内提及玩家统一使用后缀输入，例如 `Alex@`，最终在所有界面显示为前缀形式 `@Alex`；裸写 `Alex` 或写成 `@Alex` 都不会触发玩家 mention。公共聊天的候选集合同时包含在线玩家和所有已绑定玩家，因此目标离线但正在 Matrix 房间里时仍能收到提及。Matrix 入站只信任事件的 `m.mentions.user_ids` 以及与之对应的 Matrix HTML 用户链接或明确的纯文本 mention，不会把手写或伪造的 `@用户` 当成认证身份。

mention 在每个出站房间分别解析，显示规则如下：

- Minecraft → Matrix：`@游戏名(Matrix昵称)`。
- Matrix → Minecraft：`@Matrix昵称(游戏名)`。
- 其他社交平台 → Matrix：`@目标Matrix昵称(游戏名)`。

若一个 Minecraft 玩家绑定了多个 Matrix 账号，MIK 会只选择当前目标房间内处于 `join` 状态的账号，并全部显示、全部写入 Matrix 原生 `m.mentions.user_ids`。例如 `@Alex(Alex One, Alex Two)`。未加入该房间的绑定账号不会收到通知；没有可用目标账号时仍保留可读的 mention 文本，但不会生成原生通知。消息被长度限制截断到 mention 中间时同样不会误发通知。

连接使用 Matrix Client-Server API 的增量 `/sync` 长轮询：首次请求只取得 `next_batch`，之后请求携带 `since`，不是固定间隔轮询，因此房间事件通常会由 homeserver 立即返回。启动时会通过每个桥接房间的 `/joined_members` 建立完整成员快照，随后用 `m.room.member` 增量事件维护；这既避免依赖 `/sync` 的 lazy-loaded 局部成员列表，也保证 mention 按确切目标房间判断。MIK 在每个 Matrix 房间内按顺序处理事件，不同房间可并发；出站也使用异步 HTTP，不占用 Minecraft 主线程。`sync.timeout-millis` 只是空闲长轮询保持时间，不是消息等待时间。

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

可以同时配置多个路由，并让不同房间分别使用 `always` 或 `prefix`。`strip-prefix: false` 会保留触发前缀。延迟发送、`/public <消息>` 和公开消息重复发送同样经过这套共享规则；staff 与私聊不会外发。`content-safety.enabled: true` 时，共享关键词规则只检查机器人输出及 Minecraft 发往 Matrix 的聊天，不检查 Matrix 发往游戏的聊天。修改后执行 `/social reload matrix`。
