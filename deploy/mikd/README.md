# mikd Linux 部署

`mikd` 和 Paper 在游戏服务器上通过 Unix socket 通信。两者可独立重启，恢复后自动重连。

## 安装与启动

1. 按[发布构建](#发布构建)编译与游戏服务器兼容的 `mikd`，从同一份源码[构建 Paper 插件](../../README.md#构建与验证)。
2. 在游戏服务器创建服务账号 `mikd` 和组 `mik-ipc`，将 Paper 运行账号加入该组。`mikd` 默认按 Linux 账号 `paper` 校验对端 UID；已有不同账号时，只在单元的 `ExecStart` 中把账号追加到 `serve` 后。
3. 将发布构建的 `mikd` 安装到 `/usr/local/libexec/mikd`，插件 JAR 放入 Paper 的 `plugins/`，按[路径表](#配置与数据)安装服务单元。
4. 接收新玩家前配置[社交平台](#社交平台)。
5. 在游戏服务器执行以下命令，启动 Paper 后按[部署验收](#部署验收)检查。

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now mikd.service
```

## 发布构建

先在游戏服务器运行 `uname -m` 和 `getconf GNU_LIBC_VERSION`，确认架构与 glibc 版本。在游戏服务器本机编译时，可使用 `cargo build --release --locked -p mikd --bin mikd`，产物是 `target/release/mikd`。不要把在较新 glibc 上链接的原生产物直接复制到较旧系统。

跨主机构建 Linux x86_64 产物时，在构建机准备 Zig、`cargo-zigbuild` 和项目锁定的 `protoc`，然后从源码仓库根目录执行：

```sh
rustup target add x86_64-unknown-linux-gnu
cargo zigbuild --release --locked --target x86_64-unknown-linux-gnu.2.17 -p mikd --bin mikd
bin=target/x86_64-unknown-linux-gnu/release/mikd
readelf --version-info "$bin" | grep -oE 'GLIBC_[0-9]+(\.[0-9]+)+' | sort -Vu | tail -n 1
```

安装上面的 `$bin`，不要误用原生构建的 `target/release/mikd`。产物要求的最高 glibc 符号版本不能高于目标机。先在目标机对待安装文件执行 `./mikd admin --help`，确认能加载并显示控制台用法，再替换服务二进制；遇到 `GLIBC_* not found` 应重新构建，不修改系统 glibc。

## 配置与数据

| 用途 | 游戏服务器上的文件 | 模板与启用方式 |
| --- | --- | --- |
| 密钥与密码 | `/etc/mikd/secrets.env` | 有凭据时从 [secrets.env.example](secrets.env.example) 复制；归 `root:root` 所有，权限 `0600`。 |
| systemd 单元 | `/etc/systemd/system/mikd.service` | 安装 [mikd.service](mikd.service)。 |
| QQ／Matrix 群与房间规则 | `/etc/mikd/social.yml` | 启用时复制 [social.example.yml](social.example.yml)。 |
| AI 端点与模型 | `/etc/mikd/ai.yml` | 启用时复制 [ai.example.yml](ai.example.yml)。 |
| 持久数据 | `/var/lib/mikd/` | `chat-messages-v6.sqlite`、`community.db`、签名密钥与业务文件；需要备份。 |
| 缓存 | `/var/cache/mikd/` | 远端媒体字节与 Minecraft Wiki 索引；可重建。人工歌词属于持久数据。 |

`mikd` 启动时自动读取两份 YAML；缺少哪份就关闭对应功能。存在的 YAML 须对服务账号可读。Paper 直接使用 `/run/mik/mikd.sock`、后台账号 `mikd` 和实例名 `paper`，标准部署不需要 `plugins/mik/mikd.yml`。

## 更新

- **改后台配置**后，在 [控制台](#本机运维)的 Overview 页按 `r` 重启。修改单元时先执行 `sudo systemctl daemon-reload`。
- **更新程序：** 安装同一提交构建的 `mikd` 和 Paper 插件。重启两个进程后按[部署验收](#部署验收)检查。保留现有数据目录，不用空库覆盖存档。

预检时沿用待部署的二进制、环境和启动参数，将 `serve` 换成 `validate`，检查配置和公告文件。

## 本机运维

在运行 `mikd` 的主机上打开控制台。通过 SSH 执行时需分配终端。

```sh
sudo /usr/local/libexec/mikd admin
```

| 页签 | 操作 |
| --- | --- |
| `1` Overview | 查看 systemd 状态、管理连接和模块就绪情况。用 `a` 启动、`r` 重启、`x` 停止。 |
| `2` Logs | 查看 `mikd.service` 最近 200 条日志和读取告警。空格暂停或跟随，`/` 按文字筛选，方向键上下滚动或左右平移。 |
| `3` Music | 在同一工作区用 `3` 打开 Library、`4` 打开 Sources、`5` 打开 Cache。详情可直接访问关联的曲目、音源、缓存和歌词。操作见[音乐库与音源](#音乐库与音源)。 |
| `6` Console | 查看[插件控制台](#插件控制台)。用 `[`／`]` 切换控制台，`:` 输入命令，`p`／`n` 翻阅日志，空格暂停或跟随。 |

`Tab`／`Shift+Tab` 切换主页面，`F5` 刷新当前页面或详情，`?` 查看快捷键，`!` 展开上次操作结果，`q` 退出控制台。出现「结果待核实」（Result unconfirmed）时，先查对应对象的当前状态再决定是否重试。

管理 socket 默认是 `/run/mik/mikd.admin.sock`，权限 `0600`，只接受 root 和 `mikd` 服务账号。Paper 账号必须与 `mikd` 分开。Overview 中的启停和 Logs 中的日志由 systemd 提供，目标是 `mikd.service`。隔离实例用 `admin --socket <管理 socket>`，路径由实例的控制 socket 扩展名替换为 `.admin.sock` 得到。

预置在 `/var/lib/mikd/music/sources/local/` 的脚本随 `mikd` 启动加载。只导入可信脚本。后台配置位于 `/etc/mikd/`，用 `mikd validate` 预检。脚本可以继续使用 `mikd admin status` 等显式子命令，完整用法见 `mikd admin --help`。

### 插件控制台

插件与 `mikd` 通过 `console.v1` 协商控制台能力。Console 页显示提供方名称、`实例/提供方` 标识，以及命令和日志各自的连接状态。Paper 原生控制台默认是 `paper/native`。多台 Paper 共用本机 IPC 时，在各自的 `plugins/mik/mikd.yml` 中设置不同的 `game-instance`。

Paper 提供方读取服务器工作目录中的 `logs/latest.log` 及可读的 `logs/YYYY-MM-DD-N.log.gz`。空格暂停或跟随最新输出，`p`／`n` 可跨文件翻阅，`/` 筛选当前页，方向键滚动或平移。`Home` 显示当页开头，`End` 或 `F5` 回到最新输出，也可在日志轮转或清理导致游标过期后重新定位。超长行会截断，读取失败或跳过归档会显示在页面上。查询需要提供方在线。插件离线期间，可在服务器主机查原始日志文件，Paper 进程由主机服务管理器启动。

选中 Paper 控制台后，可用 `:` 提交原生命令，例如 `list` 或 `save-all`。「已提交」表示 Paper 的原生队列接收了命令，执行结果见控制台日志。回执丢失时先核实目标服务器状态，再决定是否提交新命令。

```sh
mikd admin console list
mikd admin console logs paper/native
mikd admin console run paper/native 'list'
```

脚本可将日志响应中的 `data.before` 传给 `console logs <实例/提供方> --before <游标>` 读取更早的一页，或将 `data.after` 传给 `--after <游标>` 读取后续日志。

## 网页接入

MikData 通过受限链路访问私有 HTTP API `127.0.0.1:35353`。网页聊天监听 `127.0.0.1:35354`，Cloudflare Tunnel 终止公网 HTTPS 后进行 WebSocket Upgrade。将以下规则放在 Tunnel 兜底规则之前。

```yaml
  - hostname: chat.mcmik.top
    path: ^/ws$
    service: http://127.0.0.1:35354
  - hostname: chat.mcmik.top
    service: http_status:404
```

1. 保留 `https://mcmik.top` 的网页 Origin 和代理转发的 `X-Forwarded-Proto: https`。将原有聊天域名配置为 Tunnel 的公开主机名。
2. mikd 从 `/var/lib/mikd/web-chat.key` 读取现有签名私钥，首次启动时生成权限为 `0600` 的 PKCS#8 文件。备份并保留此文件。读取启动日志的 `mikd Web chat public key`，把公钥设为 MikWeb 构建变量 `NEXT_PUBLIC_WEB_CHAT_PUBLIC_KEY`。公网 TLS 证书由 Cloudflare 管理，mikd 的签名私钥独立保护聊天正文。
3. 配套更新 mikd 和 MikWeb。管理界面的 Web chat 应显示端口 35354；用已登录浏览器验证发言、历史补读和跨标签页退出。协议版本见[网页授权与传输](../../docs/chat.md#网页授权与传输)。

mikd 需要出站 HTTPS 访问 `https://data.mcmik.top/chat/check` 核验租约。聊天页的同源 `/api/chat/session` 和 `/api/chat/connect` 由 CDN 重写给 MikData。

## 社交平台

在 `social.yml` 配置 QQ 群／Matrix 房间，凭据填入 `secrets.env`。接收新玩家需至少一个同时开启 `membership` 和 `commands` 的群或房间。会话状态位于 `/var/lib/mikd/social/`。配置字段见[社交平台配置](../../docs/chat.md#社交平台配置)。

配置 `qq:` 即为所有 QQ 群开启[出站内容审查](../../docs/chat.md#qq-内容审查)。

## 音乐库与音源

原件和编译后的表示保存在 `/var/lib/mikd/music/catalog-v2/library/`，不经过 Paper。导入普通音频前安装 `ffprobe`（FFmpeg）；NBS 导入和已入库文件的播放不需要它。将待导入文件放在服务账号可读的目录，例如 `/var/lib/mikd/music/inbox/`；标准 systemd 单元隔离家目录和 `/tmp`。导入后源文件不必留在该位置。不要直接写库目录。

Music 内用回车打开所选条目，`p`／`n` 翻页。从详情或关联列表按 `Esc` 返回上一层，保留搜索条件、页码和选中项。下架、恢复或替换后留在曲目详情。淘汰缓存后返回打开它的列表或详情。

Library 列出本地曲目、已登记的远端曲目和已下架项。用 `/` 按曲名、歌手或专辑查找，清空条件恢复浏览，或用 `f` 直接查曲目 ID。用 `d` 在线搜索，用 `o` 预览音源支持的曲单链接。在线结果按页登记曲目，部分结果会显示提示，`F5` 重新查询。

在 Library 页按 `a` 导入文件，详情中用 `r` 替换本地原件，`v` 查看该曲目的媒体版本。替换保留曲目 ID，已打开的租约继续使用原版本。用 `x` 下架，阻止新播放并保留曲目身份和原件。用 `u` 恢复同一曲目 ID。

在远端曲目详情按 `d` 准备缓存。服务复用可用版本，缺失时使用播放的解析、回退及下载流程。取得媒体后用 `b` 查看当前缓存，用 `y` 查看该版本的歌词。用 `s` 打开登记音源。当音频由备用音源取得时，用 `a` 打开实际下载音源。Paper 的播放告警提供曲目 ID。可在 Logs 页按 `/` 输入 ID 或媒体版本，核对 `mikd music open` 的 `source` 与 `acquisition`，以及 `LX playback` 和 `mikd music read` 的失败详情。

在 Sources 按 `a` 安装 LX 音源，支持 HTTPS 订阅和受限的本机文件。列表中用 `f` 查询音源 ID，`r` 刷新全部订阅。在音源详情用 `t` 查看由它登记的曲目，用 `b` 查看由它实际取得的缓存。即使音源已移除，从曲目或缓存中的历史引用打开该音源 ID，仍可查询这两份列表。

音源详情还列出各频道声明的播放、搜索、曲单和歌词能力。用 `r` 刷新单个订阅，用 `x` 移除。批量刷新失败时用 `!` 查看失败的订阅 ID。失败更新保留上一份可用脚本，具体错误见音源详情。见 [LX 配置](../../docs/lx-custom-source-extension.md#url-imports)。

脚本可使用 `music catalog list`、`search` 或 `collection`。音源关联查询用 `music sources tracks <音源 ID>` 和 `music sources cache <音源 ID>`。将响应的 `data.next` 传给同一查询的 `--after`。缓存查询的 `data.usage` 汇报整个缓存的配额和用量。查媒体版本用 `music track versions <曲目 ID>`，准备缓存用 `music track prepare <曲目 ID>`。原有 `music library list` 只列出已发布的本地曲目。

### 远端音频缓存

Cache 列出远端音频的缓存用量和条目。用 `f` 按媒体版本直达详情。详情包括曲目 ID、曲名、歌手、专辑、缓存大小、使用状态和最近访问时间。音源字段记录实际取得音频的 LX 音源，包含下载回退时使用的音源。音源移除后，缓存仍显示下载时记录的音源 ID。

在详情用 `t` 打开曲目，`s` 打开实际下载音源，`y` 查看该版本的歌词，`x` 淘汰选中的空闲版本。只在 Cache 主列表用 `c` 清理所有页面的空闲缓存。清理时会复核下载状态和播放租约，正在下载或被租约持有的版本会跳过。批量清理报告移除、跳过和失败数量，以及释放的缓存配额。依赖已淘汰版本的播放进度可能需要从头开始。

`mikd admin music cache list` 输出 JSON，下一页用 `--after` 传入响应的 `data.next`。脚本也可以用 `inspect <媒体版本>`、`evict <媒体版本>` 或 `clear-idle`。

### 歌词校订

在曲目或缓存详情按 `y`，或在版本列表选中版本后回车，查看该媒体版本的人工校订和内嵌轨道。版本列表中的远端媒体也可按 `b` 打开该版本的缓存。歌词详情显示角色、语种、是否定时和条目数。远端版本还显示播放时用于查歌词的音源。用 `l` 设置校订、`c` 清除。Library 的 `y`、`l`、`c` 可手动输入版本。脚本可用 `music lyrics inspect <媒体版本>` 查询同一份状态。

外部曲目须先取得媒体才能设置校订。缓存淘汰后，可按原媒体版本查询和清除保留的校订。把校订文件放到服务账号可读目录，例如 `/var/lib/mikd/music/inbox/song.json`。

`.txt` 为静态原文，`.lrc` 为管理员挂载到该版本的定时原文，`.json` 可一次提供多轨。使用 BCP 47 语种标签、纯文本和从媒体开始计算的毫秒；区间左闭右开。

```json
[
  {"role":"original","language_tag":"ja","kind":"timed","cues":[{"start_ms":1200,"end_ms":3100,"text":"原文"}]},
  {"role":"translation","language_tag":"zh-Hans","kind":"text","lines":["尚未对齐的译文"]}
]
```

角色为 `original`、`translation` 或 `pronunciation`，同一角色和语种只能出现一次。校订仅属于该媒体版本，替换后需显式重新挂载。清除后恢复内嵌歌词或外部候选；未证实对齐的外部文本不作为字幕。

## AI 配置

在 `ai.yml` 填写支持非流式 Chat Completions 和工具调用的端点与模型。供应商需要密钥时，在 `secrets.env` 设置 `MIKD_MODEL_API_KEY`。`provider.request-options` 不允许设置 `response_format`。

游戏工具随提问由 Paper 声明，复用助手 IPC，不需要额外端口。可在 `provider` 下设置 `reasoning-summaries: true`，为 Paper 和 Matrix 启用推理摘要。端点还须支持流式 Chat Completions，且模型须输出 `reasoning_details` 中类型为 `reasoning.summary` 的摘要。缺少摘要时继续显示「AI 思考中…」。并发额度覆盖每个已接收的生成回合（含工具往返），不占用在投递确认上；后台记忆处理在额度紧张时让位。

网页搜索默认关闭。启用时在 `/etc/mikd/ai.yml` 把 `web-search` 下的 `enabled` 改为 `true`（见[配置模板](ai.example.yml)），在 `/etc/mikd/secrets.env` 设置 `MIKD_TAVILY_API_KEY`，然后重启 `mikd.service`。Paper、QQ 和 Matrix 共用该密钥的额度。只想使用免费额度时，在 Tavily 保持按量付费关闭；`mikd` 只发基础搜索，不代管供应商账单。

Wiki 自动同步。补充的公开 Markdown 或文本放入 `/var/lib/mikd/knowledge/`。QQ／Matrix 入口在对应会话开启 `commands` 和 `ai`。详见 [AI 助手](../../docs/ai.md)。

Paper 玩家记忆保存在 `/var/lib/mikd/ai-player-memory.sqlite`。

## 部署验收

- 打开 `mikd admin`，在 Overview 查看后台就绪状态，在 Logs 检查启动日志。随后在 Paper 执行 `/mikapi status` 验证连接和协议兼容。
- 在 Console 页选中 Paper 的 `实例/native`，用 `:` 提交 `list`，确认输出出现在所选控制台的日志内。若有归档，用 `p` 翻阅早期日志。
- 分别重启 Paper 和 `mikd`，确认另一进程保持运行，两者恢复后自动重连。
- 按启用项验证：新玩家能绑定入服，QQ／Matrix／网页／游戏聊天可互通，治理权限同步到 LuckPerms，`/ai` 能回复。音乐检查目录、原版唱片、各输出能力、已配置的实际音源日志，以及歌词和同步组。
- 验收[离线跨平台提及](../../docs/chat.md#离线跨平台提及)：用同一个玩家绑定 QQ 和 Matrix，退出游戏并关闭网页聊天，再由其他玩家在公共频道发送 `@Minecraft账号名`，确认两个目标会话都产生原生提及。若同一玩家绑定了不同服务器的多个 Matrix 账号，确认在同一房间可提及这些账号；无本地成员记录不应阻止发送。
