# MIK

Paper 服务器插件，提供玩家工具、地段保护、载具、音乐与音游、社交互通和社区治理。

## 运行与安装

当前构建目标为 **Java 25、Paper 26.3**，必需插件为 **PacketEvents、LuckPerms、Plasmo Voice**。其他集成按功能选装，依赖声明见 [paper-plugin.yml](src/main/resources/paper-plugin.yml)。

1. 按下方[构建命令](#构建与验证)生成插件，将 `build/libs/mik-1.0.jar` 与必需插件放入服务器的 `plugins/` 目录。
2. 启动服务器，检查 MIK 是否正常启用。配置与存档位于 `plugins/mik/`。
3. 游戏内执行 `/menu`，进入功能与个人设置。

地段批量编辑保护与 `/regen` 选区读取使用可选插件 **[FastAsyncWorldEdit 2.16.1](https://github.com/IntellectualSites/FastAsyncWorldEdit/releases/tag/2.16.1)**；安装时用它替换 WorldEdit。

AI、QQ、Matrix 默认关闭，启用前需填写各自配置；操作与重载方式见对应手册。

直连正版服务器自动启用[认证容灾](docs/authentication-fallback.md)：官方认证故障时，近期验证成功且 IP 相同的玩家可使用原正版 UUID 重连。

## 常用入口

| 功能 | 入口与说明 |
| --- | --- |
| 菜单与个人设置 | `/menu`；[界面、文字与 FOV](docs/spatial-menus.md) |
| 建筑地图 | 安装 Pl3xMap 后自动展示已收录建筑；[同步与图层](docs/building-map.md) |
| 地段与建造 | `/plot`；[地段操作](docs/plot-board.md)、[权限](docs/plot-permissions.md)、[选区与子地段](docs/plot-subplots.md) |
| 音乐与音游 | `/music`；[曲库、唱片机与玩法](docs/music.md) |
| 网页聊天 | `/chat`、游戏 `/msg`；[接入与安全规则](docs/web-chat.md) |
| 社交与账号 | `/password`、`/bind <平台>`；[账号安全](docs/account-security.md)、[身份绑定](docs/identity-binding.md)、[QQ](docs/qq-bot.md)、[Matrix](docs/matrix-bot.md) |
| AI 与治理 | `/ai`、`/gov`；[AI 配置](docs/ai.md)、[社区治理](docs/governance.md) |

载具、电梯、空间连接、世界更新及其他功能见[完整文档](docs/README.md)。

## 构建与验证

在项目根目录执行，构建使用 Gradle Wrapper：

| 命令 | 用途 |
| --- | --- |
| `./gradlew build` | 编译并运行默认测试，产物位于 `build/libs/` |
| `./gradlew checkJavaWarnings` | 单独检查生产与测试源码警告，已包含在默认构建中 |
| `./gradlew test --tests org.encinet.mik.shared.i18n.FluentGrammarTest` | 只运行指定测试类 |
| `./gradlew benchmark` | 单独执行性能测量，不进入默认构建 |
| `python3 tools/i18n/audit.py` | 检查翻译清单、引用、参数与生产调用，无第三方 Python 依赖 |

测试报告位于 `build/reports/tests/{test,benchmark}/index.html`，临时文件位于 `build/test-tmp/`。比较性能结果时保持环境和数据规模一致。

生产与测试源码共用警告检查，弃用 API 和未解决的警告会使构建失败。必要的局部豁免在代码旁说明。

通用取舍见[设计准则](docs/design-principles.md)，工程约定见[项目架构](docs/architecture.md)与[翻译维护](docs/localization.md)。构建依赖见 [build.gradle](build.gradle)，动态加载的运行时库见 [MikLoader.java](src/main/java/org/encinet/mik/MikLoader.java)。
