# 跨端契约与互通测试

此目录存共享夹具及互通测试的客户端入口。启动真实 `mikd` 的 Java 互通用例及测试后端在 [`src/test/java/org/encinet/mik/integration/mikd/`](../../src/test/java/org/encinet/mik/integration/mikd/)。

- `schema-v1.pb`：protoc 36.1 生成的首版 IPC 描述符，供后续字段和枚举检查。未部署的草案不进入基线。已独立版本化的聊天流不与首版聊天做兼容比较；共享玩家和外部身份类型仍在 v1 检查范围内。
- [chat/link-cases.json](../../shared/chat/link-cases.json) 由新版 Paper 和 Rust 链接行为测试共同读取。
- `hello-v1.textproto`／`hello-v1.bin`：v1 `PAPER_STATE` 握手样例，Java 和 Rust 各自验证 Protobuf 编解码。
- `schema-music-v2.pb` 和 `music-v2.textproto`／`music-v2.hex`：独立音乐契约和媒体打开请求。协议检查保留冻结的 v1 描述符，同时断言退役的音乐消息未重新进入运行时协议。
- `assistant-blocks-v6.textproto`／`assistant-blocks-v6.hex`：用 protoc 36.1 编码的富文本回复，包含标题、嵌套清单、混合样式链接、原位追问、表格与代码块。Java 和 Rust 验证布局、片段顺序、目标和编解码。
- `assistant-tools-ask-v9`／`assistant-tool-call-v9`（各有 `.textproto` 与 `.hex`）：验证随提问声明工具、按名称回调 Paper，以及实例内用于绑定玩家和回合的字段。
- 控制通道以四字节大端长度加 Envelope 分帧；零长度、超过 256 KiB 和无符号溢出前缀在分配正文前拒绝。目录、歌词及媒体租约走[独立音乐通道](../../docs/music-architecture.md#播放会话与媒体传输)，先协商 `MIKM` v2 和输出能力，错版时拒绝，不改变共享 v1。
- 音乐前缀 `4d 49 4b 4d 00 02` 后跟一个能力位图字节：位 0 为 `ENCODED_AUDIO` v1，位 1 为 `NOTE_EVENTS` v1。服务端回送支持的交集；交集为空则关闭连接。新表示或新表示版本分配新位，不改旧位含义。
- `./gradlew mikdInterop`：Java 客户端与真实 `mikd` 互通。控制台测试注册两个提供方，经管理 CLI 提交命令和请求历史页，检查命令与日志查询可并行。音乐测试用管理 CLI 在临时曲库入库和校订多轨歌词，不建立共享 IPC 会话；验证目录翻页、批量查 ID、音符租约，以及重启后精确读取同一媒体版本和歌词修订。
- `./gradlew chatInterop`：相邻 `mikweb` 的实际 Chromium WebSocket 客户端经 HTTPS 代理连接本地 `mikd`，验证授权、历史、发言回执、Paper 与网页之间的双向 Wiki 链接，以及网页提及到游戏提醒目标的投递。用 `-PwebChatProject=<路径>` 可指定客户端目录。

首次测试前在 MikWeb 目录执行 `bun install --frozen-lockfile` 和 `bunx playwright-core install chromium --only-shell`。也可用 `MIK_CHAT_CHROMIUM` 指定本地 Chromium 可执行文件。代理夹具用 OpenSSL 命令行生成临时证书，提供本地 HTTPS 代理，并经 HTTP Upgrade 回源到 mikd 的环回端口。Chromium 只信任夹具证书的公钥指纹。实测覆盖错误来源和版本拒绝、浏览器临时公钥和授权绑定、同一登录的两个标签页共享连接、关闭一页后保留私聊读取、退出撤权、历史分页和游标恢复、私聊隔离、公共和管理广播、历史解码暂停时实时收件、短暂重连只发布一次加入，以及断线后按发送者查询提交回执。用 `MIK_CHAT_NO_DEFLATE=true ./gradlew chatInterop` 验证原文记录。

冻结样例用于检查编解码、消息语义及退役字段是否被误用。Java 和 Rust 的 socket 测试验证：业务能力不匹配时拒绝对应请求，基础状态查询仍可用。进程测试以 `--instance` 启动隔离实例，使用临时数据目录和端口，不登录真实外部服务。真实平台、游戏权限及上线验收见[部署手册](../../deploy/mikd/README.md#部署验收)。
