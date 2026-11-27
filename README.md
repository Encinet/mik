# MIK

由 Paper 插件和 Rust 后台 `mikd` 组成，提供服务器玩法、音乐、跨端聊天和社区治理。

## 运行与安装

当前构建目标为 **Java 25、Paper 26.3**，必需插件为 **PacketEvents 2.14.0 或更高版本、LuckPerms、Plasmo Voice**。其他集成按功能选装，依赖声明见 [paper-plugin.yml](src/main/resources/paper-plugin.yml)。

先按 [mikd 部署手册](deploy/mikd/README.md)安装后台，Paper 与后台同步更新。

1. 按下方[构建命令](#构建与验证)生成插件，将 `build/libs/mik-1.0.jar` 与必需插件放入服务器的 `plugins/` 目录。
2. 启动服务器，检查 MIK 是否正常启用。配置与存档位于 `plugins/mik/`。
3. 游戏内执行 `/menu`，进入功能与个人设置。

存储只接受当前格式，不自动改写不兼容的存档。

地段批量编辑保护与 `/regen` 选区读取使用可选插件 **[FastAsyncWorldEdit 2.16.1](https://github.com/IntellectualSites/FastAsyncWorldEdit/releases/tag/2.16.1)**；安装时用它替换 WorldEdit。

AI、QQ、Matrix 默认关闭。要接受新玩家，至少配置一个可[绑定入服](docs/account-security.md#外部身份绑定)的 QQ 群或 Matrix 房间。首次连接的拒绝界面提供可用绑定指令，不通过原版白名单或 OP 绕过。

直连正版服务器自动启用[认证容灾](docs/account-security.md#正版认证容灾)：Paper 请求官方验证时遇到故障或限流，符合条件的玩家仍可使用原正版 UUID 重连。

## 常用入口

| 功能 | 入口与说明 |
| --- | --- |
| 菜单与个人设置 | `/menu`；[界面、文字与 FOV](docs/spatial-menus.md) |
| 装饰头颅 | `/heads` 或主菜单；[搜索、收藏与领取](docs/decorative-heads.md) |
| 建筑地图 | 安装 Pl3xMap 后自动展示已收录建筑；[同步与图层](docs/building-map.md) |
| 地段与建造 | `/plot`；地段管理、权限、公告板与选区编辑 |
| 封禁管理 | `/ban`；封禁玩家、查看在押账号；[监狱配置与封禁准入](docs/server-prison.md) |
| 音乐与音游 | `/music`；[曲库、唱片机与玩法](docs/music.md) |
| 聊天与社交 | 网页 `/chat`、游戏 `/msg`；[互通、历史与 QQ／Matrix 配置](docs/chat.md) |
| 账号与身份 | `/password`、`/bind <平台>`；[游戏和网页登录](docs/account-security.md) |
| 玩家 AI 助手 | `/ai <问题>`；[游戏用法与配置](docs/ai.md) |
| 社区治理 | `/gov`；[规则、名册与投票](docs/governance.md) |
| 后台运维 | `mikd admin` 提供 [插件控制台、后台日志和音乐管理](deploy/mikd/README.md#本机运维) |

载具、电梯、空间连接、世界更新及其他功能见[完整文档](docs/README.md)。

## 构建与验证

在项目根目录执行。完整构建依赖 Linux、Java 25、`rust-toolchain.toml` 锁定的 Rust 工具链、protoc 36.1 和 Python 3；协议检查的 Python 依赖见 [requirements.txt](tools/mikd/requirements.txt)。

| 命令 | 用途 |
| --- | --- |
| `./gradlew build` | 编译并运行默认测试，产物位于 `build/libs/` |
| `cargo test --locked --workspace` | 执行 Rust 工作区测试 |
| `cargo clippy --locked --workspace --all-targets --all-features -- -D warnings` | 执行与 CI 相同的 Rust 静态检查 |
| `./gradlew checkJavaWarnings` | 单独检查生产与测试源码警告，已包含在默认构建中 |
| `./gradlew test --tests org.encinet.mik.shared.i18n.FluentGrammarTest` | 只运行指定测试类 |
| `./gradlew benchmark` | 单独执行性能测量，不进入默认构建 |
| `python3 tools/i18n/audit.py` | 检查翻译清单、引用、参数与生产调用，无第三方 Python 依赖 |

测试报告位于 `build/reports/tests/{test,benchmark}/index.html`，临时文件位于 `build/test-tmp/`。比较性能结果时保持环境和数据规模一致。

生产与测试源码共用警告检查，弃用 API 和未解决的警告会使构建失败。必要的局部豁免在代码旁说明。

`mikd` 和协议库在 Clippy 的 `all`、`pedantic` 之外，限制显式 panic、未完成代码、调试输出和绕过析构的操作，检查被隐式延长的锁守卫生命周期，并要求明确处理或丢弃 `#[must_use]` 结果。测试允许用 `unwrap`、`expect` 和 `panic!` 暴露断言或夹具错误；生产代码优先返回错误，必要的 lint 豁免须就地注明理由。

通用取舍见[设计准则](docs/design-principles.md)，工程约定见[项目架构](docs/architecture.md)与[翻译维护](docs/localization.md)。构建依赖见 [build.gradle](build.gradle)，动态加载的运行时库见 [MikLoader.java](src/main/java/org/encinet/mik/MikLoader.java)。

跨进程测试和协议样例见[互通测试](tests/interop/README.md)。
