# MIK

Paper 服务器插件，提供玩家工具、地段保护、载具、音乐与音游、社交互通和社区治理。

## 运行与安装

当前构建目标为 **Java 25、Paper 26.3**，必需插件为 **PacketEvents、LuckPerms、Plasmo Voice**。其他集成按功能选装，依赖声明见 [paper-plugin.yml](src/main/resources/paper-plugin.yml)。

1. 按下方[构建命令](#构建与验证)生成插件，将 `build/libs/mik-1.0.jar` 与必需插件放入服务器的 `plugins/` 目录。
2. 启动服务器，检查 MIK 是否正常启用。配置与存档位于 `plugins/mik/`。
3. 游戏内执行 `/menu`，进入功能与个人设置。

AI、QQ、Matrix 默认关闭，启用前需填写各自配置；操作与重载方式见对应手册。

## 常用入口

| 功能 | 入口与说明 |
| --- | --- |
| 菜单与个人设置 | `/menu`；[界面、文字与 FOV](docs/spatial-menus.md) |
| 地段与建造 | `/plot`；[地段操作](docs/plot-board.md)、[权限](docs/plot-permissions.md)、[选区与子地段](docs/plot-subplots.md) |
| 音乐与音游 | `/music`；[曲库、唱片机与玩法](docs/music.md) |
| 社交与账号 | `/bind <平台>`；[身份绑定](docs/identity-binding.md)、[QQ](docs/qq-bot.md)、[Matrix](docs/matrix-bot.md) |
| AI 与治理 | `/ai`、`/gov`；[AI 配置](docs/ai.md)、[社区治理](docs/governance.md) |

载具、电梯、空间连接、世界更新及其他功能见[完整文档](docs/README.md)。

## 构建与验证

在项目根目录执行，构建使用 Gradle Wrapper：

| 命令 | 用途 |
| --- | --- |
| `./gradlew build` | 编译并运行默认测试，产物位于 `build/libs/` |
| `./gradlew test --tests org.encinet.mik.shared.i18n.FluentGrammarTest` | 只运行指定测试类 |
| `./gradlew benchmark` | 单独执行性能测量，不进入默认构建 |
| `python3 tools/i18n/audit.py` | 检查翻译清单、引用、参数与生产调用，无第三方 Python 依赖 |

测试报告位于 `build/reports/tests/{test,benchmark}/index.html`，临时文件位于 `build/test-tmp/`。比较性能结果时保持环境和数据规模一致。

通用取舍见[设计准则](docs/design-principles.md)，工程约定见[项目架构](docs/architecture.md)与[翻译维护](docs/localization.md)。构建依赖见 [build.gradle](build.gradle)，动态加载的运行时库见 [MikLoader.java](src/main/java/org/encinet/mik/MikLoader.java)。
