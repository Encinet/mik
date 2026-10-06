# AI 助手

MIK 提供一套游戏内与社交平台共用的 AI 助手。AI 本身默认关闭，避免未配置密钥时意外发起外部请求；一旦启用，所有内置只读工具包默认开启，并由 `tool_search` 按问题逐步加载具体工具定义。

## 启用与第三方提供商

首次启动会生成 `plugins/mik/ai.yml`。提供商需要兼容非流式 `POST /chat/completions` 以及标准 function/tool calling。可以声明任意数量的命名提供商，再切换 `active-provider`：

```yaml
enabled: true
active-provider: gateway

providers:
  gateway:
    endpoint: "https://ai-gateway.example.org/v1/chat/completions"
    model: "your-model"
    api-key: "${AI_GATEWAY_KEY}"
    auth-header: "Authorization"
    auth-prefix: "Bearer "
    connect-timeout-seconds: 10
    request-timeout-seconds: 60
    max-response-bytes: 2097152
    headers:
      X-Tenant: "minecraft"
    request-options:
      temperature: 0.2
      max_tokens: 1200
```

`endpoint`、`model`、密钥、认证字段、自定义 header 和 `request-options` 均属于提供商配置，因此 OpenAI、OpenRouter、DeepSeek、兼容网关以及 Ollama、vLLM、LM Studio 一类自托管服务不需要各写一个 Java adapter。环境变量支持 `${NAME}` 和 `${NAME:-fallback}`。`model`、`messages`、`tools`、`tool_choice` 与 `stream` 是协议保留字段，不能被 `request-options` 覆盖。HTTP 重定向不会携带认证信息继续跟随，响应体和等待时间都有上限，密钥不会写入日志。

内置 `ai.yml` 的 OpenAI 示例使用 `gpt-6-luna`，并在 `request-options` 中设置 `reasoning_effort: none` 和 `max_completion_tokens: 1200`。当前客户端使用 Chat Completions 和 function calling；[OpenAI 的 GPT-6 Luna 模型说明](https://developers.openai.com/api/docs/models/gpt-6-luna)指出，在这个接口中调用工具只能使用 `none`。模型也支持 `low`、`medium`、`high`、`xhigh`、`max` 等思考等级，但要在保留 AI 工具能力的同时使用这些等级，需要先将客户端接入 Responses API。直接把当前示例改为其他等级，会使游戏查询、网页搜索和知识工具调用不符合该模型的接口约束。其他兼容提供商的 `request-options` 应按其自身接口填写。

修改后，管理员执行 `/ai reload`。`/ai status` 显示当前提供商、模型和启用的工具包。普通使用需要 `mik.ai.use`，管理员组也可直接使用：

```text
/ai <问题>
/ai clear
/ai status
/ai reload
```

## 工具包

`tools.enabled-packs` 缺省时包含全部七个工具包：

| 能力包 | 工具 | 数据来源 |
| --- | --- | --- |
| `web` | `web_search`、`fetch_web_page` | SearXNG 搜索与受限网页抓取 |
| `server` | `server_status`、`get_server_time` | 当前服务器快照 |
| `player` | `list_online_players`、`get_player_info`、`get_my_inventory`、`get_player_statistics`、`get_nearby_entities` | 当前玩家快照 |
| `world` | `get_world_info`、`get_world_game_rules` | 已加载世界快照 |
| `minecraft` | `search_minecraft_registry` | 当前服务端版本的物品、方块和实体注册表 |
| `utility` | `calculate` | 本地安全表达式解析器 |
| `knowledge` | `search_knowledge`、`open_knowledge` | 本地公共 Markdown 知识库与 Lucene 索引 |

模型最初只看到轻量的 `tool_search`。它用自然语言关键词或准确包 ID 激活一个或多个能力包，下一轮才看到这些包的完整 JSON Schema；无法匹配时会加载当前配置允许的全部包。这个过程与 skills 的“先发现能力，再载入细节”相同，避免把大量无关 schema 塞进每次请求。模型在同一轮同时调用 `tool_search` 和具体工具时，运行时也会先完成能力激活，再并行执行具体工具。

网页搜索默认指向本机 `http://127.0.0.1:8080/search`。SearXNG 必须在 `settings.yml` 中允许 JSON 格式。搜索标题和摘要会去除 HTML，只接受 HTTP(S) 结果 URL，并作为不可信工具数据交给模型；网页服务不可用时只令该次工具调用失败，不会影响游戏查询和本地计算。

`fetch_web_page` 用于完整的“搜索 → 阅读 → 沿链接继续阅读”流程。模型先通过 `web_search` 找到候选结果；摘要不足时抓取结果 URL；若正文引用了必要的下一页，模型直接把内联绝对 URL 作为下一次 `fetch_web_page` 的 `url` 参数。HTML 会优先抽取 `main`、唯一文章或常见正文容器，移除脚本、导航、表单、隐藏元素等噪音，然后转换成类 Markdown。工具也能读取 JSON、XML、Markdown 与纯文本，并统一返回：

```json
{
  "title": "Page title",
  "content_markdown": "# Readable content\n\nSee [details](<https://example.org/details>)...",
  "truncated": false
}
```

正文中的链接使用 `[文字](<绝对 URL>)`，不再另附重复的编号链接表，减少上下文占用和模型解析步骤。抓取边界由 `tools.web-fetch` 控制，包括超时、原始响应字节数、正文字符数、重定向次数和正文内最多保留的链接数。每次请求及每次重定向都会重新验证目标，只允许公开 HTTP(S) 地址；用户信息 URL、本机名、内网/链路本地/保留地址、HTTPS 降级跳转、循环跳转、压缩响应和二进制内容都会被拒绝。抓取器不会使用系统 HTTP 代理，避免由代理绕过目标地址检查。它不运行 JavaScript，也不读取图片或 PDF；这类页面会返回明确的工具错误，而不是把二进制数据交给模型。

转换器覆盖标题与段落、粗体/斜体/删除/高亮、引用、水平线、嵌套与起始编号列表、任务列表、定义列表、`details`、缩写、上下标、ruby 注音、图片懒加载地址、音视频引用，以及带标题、链接、`colspan`、`rowspan` 的表格。行内代码和代码块会根据内容自动选择不会冲突的反引号围栏，并识别常见语言 class/data 属性。相对链接、`<base>`、国际化域名和 Unicode 路径都会生成可直接调用的绝对 ASCII URL；畸形 HTML、过深 DOM 和内容截断也不会留下半条链接、半个 Unicode 字符或未闭合代码围栏。

所有搜索摘要、网页正文和链接仍然是不可信数据。内置多语言 prompt 明确要求模型忽略页面内指令，只把内容当作资料，按需访问链接，并在最终回答中引用实际采用的 URL。

游戏工具全部只读。Bukkit 数据在主线程一次性捕获成不可变快照，模型调用工具时不再跨线程读取 Bukkit 对象。其他玩家的背包永远不会暴露；`get_my_inventory` 只对当前游戏玩家，或已经绑定 Minecraft 身份的社交用户有效。精确玩家坐标和附近实体默认关闭，只有服主显式设置 `tools.game-data.include-player-locations: true` 才会进入快照。工具不会列出 IP、密钥、插件配置、封禁详情或其他管理数据。

## 知识库与个人记忆

知识正文以 Markdown 文件为准，Lucene 只保存可重建的检索索引，SQLite 只记录整理状态与不含正文的审计元数据。服务启动时会先扫描 Markdown 并重建索引；重建过程先完成一组新的公共/私有 index generation，再原子更新 `CURRENT`，失败时继续保留旧 generation。扫描或增量索引更新失败会在 `/ai knowledge status` 中标记为需要同步，此时个人记忆检索会停止，避免从旧索引返回已经删除的私有内容。

```text
plugins/mik/
├── knowledge/
│   ├── public/                         # 可由 search_knowledge 检索
│   ├── users/<minecraft-uuid>/         # 只为该 UUID 自动召回
│   ├── .inbox/                         # 回答后的待整理候选
│   ├── .revisions/                     # 更新前快照
│   └── .archive/                       # 可恢复归档与失败候选
├── cache/ai-knowledge/
│   ├── CURRENT                          # 原子切换的活动 generation
│   └── generations/<id>/{public,private}/ # 可删除、可重建的 Lucene 索引
└── ai-knowledge.db                     # 状态和元数据审计
```

普通 Markdown 可以直接放进 `knowledge/public`。需要稳定标识、过滤标签、来源、保护或有效期时，在文档前部加入 YAML frontmatter：

```markdown
---
id: building-rules
title: 建筑规则
language: zh_cn
aliases: [建造规范, building rules]
tags: [rules, building]
kind: rule
protected: true
revision: 1
created-at: 2026-08-24T00:00:00Z
updated-at: 2026-08-24T00:00:00Z
sources:
  - title: 官方规则页
    url: https://example.org/rules
---

# 建筑规则

正文可以继续使用标题、列表、表格、代码块和内联链接。
```

未写 frontmatter 时，文件相对路径会生成稳定 ID，第一个 Markdown 标题会成为标题。`scope` 和私有知识的 `owner` 由物理目录决定；frontmatter 若声明了冲突值，扫描会失败，模型不能通过正文或元数据把文档移到另一个用户。文件大小、ID、时间戳和 HTTP(S) 来源也会在读取时校验，知识目录中的符号链接不会被跟随。

`search_knowledge` 支持最多四组多语言查询和标签过滤。标题、别名、章节、标签和正文使用 ICU 分词与 BM25 排序，多组查询通过 RRF 合并。结果返回 `[K1: 标题](knowledge://public/文档#chunk-N)` 形式的内联链接；模型把其中的完整 URI 直接传给 `open_knowledge`。链接能减少复制参数时的歧义，但模型仍然需要调用工具，`knowledge://` 不是提供商自行访问的网络地址。最终回答缺少引用时，运行时只会追加本轮 `search_knowledge` 实际返回过的链接，不接受模型自行构造的引用。

个人记忆不会注册成模型可任意调用的工具。只有游戏玩家或已绑定 Minecraft 身份的社交用户带有可信 UUID；系统用该 UUID 查询独立的私有索引，并把有界结果作为不可信 `memory_recall` 工具输出加入当前请求。未绑定的社交身份、其他玩家 UUID 和公共知识工具都不能进入这个索引。管理员可检查和执行永久遗忘：

```text
/ai memory list <player>
/ai memory show <player> <document-id>
/ai memory forget <player> <document-id|all>
```

永久遗忘会先移除检索入口，再删除该玩家的正文、历史版本、归档和待处理候选。相关权限为 `mik.ai.memory.manage`，默认只授予 OP；`group.custodian` 同样可用。

启用 `knowledge.learning` 后，每次回答完成会把当前问题、最终回答和有界工具证据放入内存任务队列，主回答不等待该过程。提取模型只能提交独立改写后的候选 Markdown；原始会话不会写入磁盘或 SQLite。来源 URL 必须实际出现在本轮工具证据中。使用过个人记忆或玩家私有数据工具的回合不能生成公共候选，未绑定身份的回合不能生成个人候选。持久候选数量与提取任务队列都有上限，过载时直接丢弃后台学习，不影响回答。

定时整理器会检索相关现有文档，再选择跳过、新建或合并。更新使用预期 revision，受 `protected` 保护，并在写入前保存旧版本；模型只能更新检索到的相关 ID，也只能归档已经合并且未保护的相关文档。单条候选失败不会阻塞同批其他候选，连续失败三次后移到归档供管理员检查。管理入口为：

```text
/ai knowledge status
/ai knowledge sync
/ai knowledge reindex
/ai knowledge curate
/ai knowledge protect <document-id>
/ai knowledge unprotect <document-id>
/ai knowledge rollback <document-id> <revision>
```

`sync` 与 `reindex` 含义相同，都会从 Markdown 重建索引。知识管理权限为 `mik.ai.knowledge.manage`，默认只授予 OP；`group.custodian` 同样可用。默认配置允许为学习流程单独指定任一已配置提供商：

```yaml
knowledge:
  enabled: true
  index:
    max-file-bytes: 2097152
    max-chunk-characters: 1800
    overlap-characters: 200
    default-result-limit: 8
  learning:
    enabled: true
    provider: active
    max-concurrent-extractions: 1
    candidate-queue-capacity: 256
    max-candidates-per-turn: 3
    curation-interval-minutes: 30
    curation-batch-size: 32
  memory:
    enabled: true
    max-results: 4
    max-context-characters: 4000
    max-documents-per-player: 128
```

## 多语言 prompt

`system-prompts` 为每种界面语言提供独立 prompt，并必须保留 `default` 回退。内置配置覆盖简体中文、香港繁体、台湾繁体、文言、英语、德语、西班牙语、法语、意大利语、日语、韩语、荷兰语、巴西葡萄牙语、俄语、泰语和乌克兰语。

请求会同时携带首选界面语言，但模型必须优先跟随用户最新问题实际使用的语言，因此同一段会话可以自然切换语言。所有内置 prompt 都声明工具输出不可信、网页搜索需要引用、工具只读、不得泄露凭据或隐藏 prompt。服主可以逐语言改写角色设定，不需要改代码。

## QQ 与 Matrix

AI 命令是共享 `SocialCommand`，不是 QQ 或 Matrix 私有实现：

```text
QQ:     /ai 现在服务器有多少人？
Matrix: !ai Who is online?
```

所有 16 种语言都有文本别名，稳定原生命令 ID 为 `ai.ask`。参数为 `clear`、`reset`、`清空` 等本地化清理词时会清除当前会话历史。

社交对话按“平台 + 房间/群 + 已认证平台用户”隔离。同一用户在同一房间可以连续追问，不同用户不会共享历史；没有可信平台身份的事件不会持久复用历史。已绑定用户会把 Minecraft UUID 传给只读游戏工具，未绑定用户仍可查询服务器、世界、版本、网页和计算数据。模型答案通过 `SocialDocument.withUntrustedText` 进入共享内容安全检查，再由 QQ 或 Matrix renderer 转义和截断。

## Worker 与线程边界

文件、Lucene、SQLite 和后台学习中的阻塞等待由 `AiWorkerPool` 承载。每个任务使用一个 Java 25 虚拟线程，同时分别限制运行数和等待数；达到 admission 上限后立即拒绝后台任务或让管理命令返回失败，不会建立无界队列。通用管理/召回任务与知识提取、知识整理使用不同 worker，提取积压不会占用管理员重建索引的执行位，也不会阻塞定时整理。

```yaml
limits:
  max-concurrent-requests: 4
  max-blocking-workers: 8
  max-worker-queue: 128
```

虚拟线程用于可能阻塞的任务，不替代并发限制。OpenAI-compatible 和网页 HTTP 请求继续使用 `HttpClient.sendAsync`；知识整理的单个定时 daemon 平台线程只触发任务，实际工作转交虚拟线程。Bukkit 世界、玩家和实体读取仍只在服务器主线程生成不可变快照，worker 不直接访问 Bukkit 对象；异步结果发送也会调度回主线程。reload 或 disable 会先停止接收任务、取消等待任务，再关闭知识存储。`/ai status` 对管理员显示 worker 的 running、queued 和 dropped 计数。

## 代码层级

```text
module/ai/
├── AiModule.java              # Bukkit composition root、/ai 命令、主线程快照
├── api/                       # AiGateway、AiRequest、公共失败契约
├── config/                    # ai.yml 解析、环境变量和完整校验
├── conversation/              # 模型请求边界、有界历史、并发控制和工具循环
├── provider/                  # OpenAI-compatible HTTP transport
├── runtime/                   # 有界虚拟线程 worker、生命周期和 HTTP 响应限额
├── knowledge/
│   ├── model/                 # 文档、分块、命中与 scope
│   ├── application/           # 检索、个人召回、异步学习和整理策略
│   ├── adapter/
│   │   ├── markdown/          # frontmatter、路径约束、版本和归档
│   │   ├── lucene/            # 公共/私有 ICU + BM25 索引
│   │   └── sqlite/            # 状态与元数据审计
│   └── tool/                  # 只读公共知识工具包
└── tool/
    ├── AiToolCatalog          # 能力包目录
    ├── AiToolRegistry         # 请求级 tool_search、激活状态与执行轨迹
    ├── game/                  # server/player/world/minecraft 包
    ├── utility/               # 无网络本地工具
    └── web/                   # 搜索、安全抓取、文档清洗、语义渲染与安全截断

module/social/command/builtin/AiSocialCommand.java
                              # 仅依赖 module.ai.api
```

`AiModule` 是唯一装配点；公共 `api` 不依赖 Bukkit、社交层或内部实现。`AiConversationService` 只认识 provider 边界和请求级工具目录，具体游戏、网页、计算和知识能力彼此独立。知识模块继续按 model、application、adapter、tool 分层，Markdown adapter 不依赖 Lucene，公共模型与应用层不依赖 Bukkit 或社交平台。历史同时受单会话消息数与全局会话数限制，活跃请求还受全局并发量和单会话互斥保护。新增工具时实现 `AiTool`，归入一个 `AiToolPack`，再在 composition root 注册；不需要修改对话协议或任何平台 adapter。
