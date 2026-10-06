# MIK

MIK 是 Paper 服务器插件。`MikLoader` 声明插件运行时库，`Mik` 创建模块、连接模块间的能力，并管理启停与命令注册。有停用方法的模块在启动后登记清理动作；正常停服或后续启动失败时，均按相反顺序清理。插件元数据和外部插件依赖见 `src/main/resources/paper-plugin.yml`。

## 代码位置

| 路径 | 职责 |
| --- | --- |
| `src/main/java/org/encinet/mik/module/` | 按游戏功能组织实现；地段、音乐、聊天、Social、AI 等各自保管状态和业务逻辑 |
| `src/main/java/org/encinet/mik/module/i18n/` 与 `src/main/resources/lang/` | 消息键、语言选择和译文 |
| `src/main/java/org/encinet/mik/module/menu/` | 空间菜单的定义、会话、虚拟实体渲染及基岩版呈现 |
| `src/main/java/org/encinet/mik/module/role/` | 玩家角色与权限组名称 |
| `src/main/java/org/encinet/mik/shell/` | 跨功能的主菜单和语言菜单 |
| `src/main/java/org/encinet/mik/integration/` | 不属于单个功能的外部插件集成 |
| `src/test/java/` | 与生产代码同包组织的行为和边界测试 |

较大的功能在自己的包内继续按职责分组。例如，`ai` 区分配置、会话、工具与知识库；`social` 区分共享命令、平台协议和运行时；`music` 区分曲库、唱片机、节奏游戏和界面。各功能的操作和存储约束记录在 `docs/` 对应文档中。

## 模块边界

- 新功能从自己的 `module/<feature>` 包进入，由 `Mik` 连接所需能力。功能包不引用 `Mik` 或 `shell`。
- 跨功能调用传入所需的能力，避免持有对方的整个模块。例如 Social 接收 `AiGateway`，地段接收 `PlotNoticePublisher`，MOTD 接收 `PlayerAddressIdentityLookup`；Skript 接入 AFK 和 PVP 时也只依赖各自的访问接口。
- 菜单页由创建它的功能持有：有玩家状态的页面使用 `FloatingMenuScreen`，按钮回调使用传入的 `FloatingMenuHandle`。关闭页面时只操作自己持有的会话；全局当前菜单查询只用于核对回调是否仍指向前台页面。
- 持有周期任务、网络监听器或外部资源的模块提供停用方法；仅注册 Bukkit 事件的模块由 `Mik.startListener` 登记注销动作。启动失败和正常停服都按依赖的相反顺序清理。资源所有者应完成其余独立清理并向 `Mik` 报告关闭失败；只有全部停用成功，启动标记才会被清除。
- `ApiModule` 管理 Bukkit 玩家事件和网页验证码确认命令，`LocalApiServer` 独占回环 HTTP 监听器及线程池。它通过只读能力访问公告和社区公示；数据仍由原功能模块维护。网页验证码的有效期和一次性消费由 API 自己管理。
- HTTP 请求在线程池中处理；共享快照可直接读取，Bukkit 玩家查询必须回到服务器主线程。玩家峰值由 `ApiPlayerSnapshot` 严格加载并原子保存，损坏的状态文件不会被空值覆盖。
- API 状态文件损坏或回环端口被占用时，API 会记录错误并保持停用，游戏功能继续启动；`/mikapi status` 可查看监听状态。
- 公告正文由运维文件提供；玩家已读位置由公告功能自己的 `AnnouncementSeenStore` 保存。损坏的状态文件会阻止加载，停用时不会用空数据覆盖它。
- 算法和持久化规则留在所属功能包。只有当职责与生命周期确实独立时才提取类型，不为缩短文件或消除依赖图数字增加转发层。
- 物品安全模块共用同一套物品与书页限值判定；网络包改写单独处理协议格式，事件模块负责拦截、清理和审计。

## 验证

运行 `./gradlew build` 编译插件并执行测试。修改语言键时一并检查 `src/main/resources/lang/` 的各语言文件；修改外部插件接入时核对 `paper-plugin.yml` 的依赖声明。
测试的临时文件写入 `build/test-tmp/`，避免系统临时目录的配额影响 SQLite 和音频测试。
