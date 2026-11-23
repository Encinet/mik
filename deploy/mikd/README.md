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

安装上面的 `$bin`，不要误用原生构建的 `target/release/mikd`。产物要求的最高 glibc 符号版本不能高于目标机。先在目标机对待安装文件执行 `./mikd admin --help`，确认能加载且包含 `music library`，再替换服务二进制；遇到 `GLIBC_* not found` 应重新构建，不修改系统 glibc。

## 配置与数据

| 用途 | 游戏服务器上的文件 | 模板与启用方式 |
| --- | --- | --- |
| 密钥与密码 | `/etc/mikd/secrets.env` | 有凭据时从 [secrets.env.example](secrets.env.example) 复制；归 `root:root` 所有，权限 `0600`。 |
| systemd 单元 | `/etc/systemd/system/mikd.service` | 安装 [mikd.service](mikd.service)。 |
| QQ／Matrix 群与房间规则 | `/etc/mikd/social.yml` | 启用时复制 [social.example.yml](social.example.yml)。 |
| AI 端点与模型 | `/etc/mikd/ai.yml` | 启用时复制 [ai.example.yml](ai.example.yml)。 |
| 持久数据 | `/var/lib/mikd/` | `chat-messages.sqlite`、`community.db`、签名密钥与业务文件；需要备份。 |
| 缓存 | `/var/cache/mikd/` | 远端媒体字节与 Minecraft Wiki 索引；可重建。人工歌词属于持久数据。 |

`mikd` 启动时自动读取两份 YAML；缺少哪份就关闭对应功能。存在的 YAML 须对服务账号可读。Paper 直接使用 `/run/mik/mikd.sock`、后台账号 `mikd` 和实例名 `paper`，标准部署不需要 `plugins/mik/mikd.yml`。

## 更新

- **改后台配置：** 执行 `sudo systemctl restart mikd.service`。修改单元时先执行 `sudo systemctl daemon-reload`。
- **更新程序：** 安装同一提交构建的 `mikd` 和 Paper 插件。重启两个进程后按[部署验收](#部署验收)检查。保留现有数据目录，不用空库覆盖存档。

预检时沿用待部署的二进制、环境和启动参数，将 `serve` 换成 `validate`，检查配置和公告文件。

## 本机运维

在运行 `mikd` 的主机上操作（下例沿用默认安装路径）：

```sh
sudo /usr/local/libexec/mikd admin status
sudo /usr/local/libexec/mikd admin music sources list
sudo /usr/local/libexec/mikd admin music sources inspect <ID>
sudo /usr/local/libexec/mikd admin music sources import <URL>
sudo /usr/local/libexec/mikd admin music sources refresh
sudo /usr/local/libexec/mikd admin music sources remove <ID>
```

管理 socket 默认是 `/run/mik/mikd.admin.sock`，权限 `0600`，只接受 root 和 `mikd` 服务账号。Paper 账号必须与 `mikd` 分开；不开放管理端口，也不从游戏端授权这些操作。CLI 连接现有进程，不直接读写其数据库和订阅文件；用 `mikd admin --help` 查看用法。隔离实例用 `admin --socket <管理 socket>`，路径由实例的控制 socket 扩展名替换为 `.admin.sock` 得到。

`refresh`、`remove` 只操作订阅；本地脚本由主机文件管理，变更后重启 `mikd`。只导入可信脚本。后台配置仍在 `/etc/mikd/`，用 `validate` 预检、用 `systemctl` 重启，不用 CLI 改写配置文件。

## 网页接入

MikData 通过受限链路访问私有 API `127.0.0.1:35353`。公开的 Tunnel 只转发聊天域名的 `/ws` 到 `127.0.0.1:35354`。将这两条加入 Cloudflare Tunnel 的 `ingress`，放在兜底规则前：

```yaml
  - hostname: chat.mcmik.top
    path: ^/ws$
    service: http://127.0.0.1:35354
  - hostname: chat.mcmik.top
    service: http_status:404
```

1. 网页 Origin 固定为 `https://mcmik.top`。代理转发 `X-Forwarded-Proto: https`。
2. 从 `mikd` 日志读取 `Web chat public key`，在 MikWeb 的部署环境设置 `NEXT_PUBLIC_WEB_CHAT_PUBLIC_KEY` 后部署网页。要沿用既有公钥，启动前将原 PKCS#8 私钥放到 `/var/lib/mikd/web-chat.key`（归 `mikd` 所有，权限 `0600`）。

不接入网页时，保持回环监听即可，无需配置 Tunnel。网页登录和聊天流程见[聊天文档](../../docs/chat.md#网页授权与传输)。

## 社交平台

在 `social.yml` 配置 QQ 群／Matrix 房间，凭据填入 `secrets.env`。接收新玩家需至少一个同时开启 `membership` 和 `commands` 的群或房间。会话状态位于 `/var/lib/mikd/social/`。配置字段见[社交平台配置](../../docs/chat.md#社交平台配置)。

配置 `qq:` 即为所有 QQ 群开启[出站内容审查](../../docs/chat.md#qq-内容审查)。

## 音乐库与音源

原件和编译后的表示保存在 `/var/lib/mikd/music/catalog-v2/library/`，不经过 Paper。导入普通音频前安装 `ffprobe`（FFmpeg）；NBS 导入和已入库文件的播放不需要它。将待导入文件放在服务账号可读的目录，例如 `/var/lib/mikd/music/inbox/`；标准 systemd 单元隔离家目录和 `/tmp`。导入后源文件不必留在该位置。不要直接写库目录。

```sh
sudo /usr/local/libexec/mikd admin music library import /var/lib/mikd/music/inbox/song.flac
sudo /usr/local/libexec/mikd admin music library list
sudo /usr/local/libexec/mikd admin music library replace '<曲目 ID>' /var/lib/mikd/music/inbox/new.flac
sudo /usr/local/libexec/mikd admin music track inspect '<曲目 ID>'
sudo /usr/local/libexec/mikd admin music track retire '<曲目 ID>'
```

`list --after '<末条 ID>'` 继续翻页。`replace` 保留 ID 并发布新媒体版本，不替换正在使用的版本；精确恢复仍需原版本可用。`retire` 下架曲目，保留身份和原件，不中断已打开的租约；该 ID 不再接受新的播放。

**查看实际来源。** Paper 的播放告警提供 `TrackId` 和错误类别。执行 `journalctl -u mikd.service -f -g 'mikd music open'`，按相同 ID 查媒体版本、`source` 和 `acquisition`。库内媒体显示 `source=managed_library`；`cache_hit` 的 `source` 是最初取得该缓存的音源，`download` 是当次下载使用的音源。按版本可在 `LX playback` 和 `mikd music read` 中查解析、请求和读取故障。日志不记录上游曲目标识、播放 URL 或凭据。用 `admin music sources inspect <音源 ID>` 查音源状态，用 `admin music track inspect <曲目 ID>` 查曲目当前的媒体与最近一次打开失败。

LX 音源用上节的 `music sources import` 安装，支持 HTTPS 订阅和受限的本机文件，见 [LX 配置](../../docs/lx-custom-source-extension.md#url-imports)。该能力不在 Paper 命令或音乐协议中。

### 歌词校订

先用 `music track inspect <曲目 ID>` 读取 `data.media.version`。对于外部曲目，须先取得媒体，才有版本可校订。把校订文件放到服务账号可读目录：

```sh
sudo /usr/local/libexec/mikd admin music lyrics set '<媒体版本>' /var/lib/mikd/music/inbox/song.json
sudo /usr/local/libexec/mikd admin music lyrics clear '<媒体版本>'
```

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

游戏工具随提问由 Paper 声明，复用助手 IPC，不需要额外端口或配置。Paper 静默轮询，不展示模型阶段。并发额度覆盖每个已接收的生成回合（含工具往返），不占用在投递确认上；后台记忆处理在额度紧张时让位。

网页搜索默认关闭。启用时在 `/etc/mikd/ai.yml` 把 `web-search` 下的 `enabled` 改为 `true`（见[配置模板](ai.example.yml)），在 `/etc/mikd/secrets.env` 设置 `MIKD_TAVILY_API_KEY`，然后重启 `mikd.service`。Paper、QQ 和 Matrix 共用该密钥的额度。只想使用免费额度时，在 Tavily 保持按量付费关闭；`mikd` 只发基础搜索，不代管供应商账单。

Wiki 自动同步。补充的公开 Markdown 或文本放入 `/var/lib/mikd/knowledge/`。QQ／Matrix 入口在对应会话开启 `commands` 和 `ai`。详见 [AI 助手](../../docs/ai.md)。

Paper 玩家记忆保存在 `/var/lib/mikd/ai-player-memory.sqlite`。

## 部署验收

- 用 `journalctl -u mikd.service -n 50` 看启动日志，用 `mikd admin status` 查后台就绪状态，在 Paper 执行 `/mikapi status` 验证连接和协议兼容。
- 分别重启 Paper 和 `mikd`，确认另一进程保持运行，两者恢复后自动重连。
- 按启用项验证：新玩家能绑定入服，QQ／Matrix／网页／游戏聊天可互通，治理权限同步到 LuckPerms，`/ai` 能回复。音乐检查目录、原版唱片、各输出能力、已配置的实际音源日志，以及歌词和同步组。
