# 文档

[安装与运行](../README.md#运行与安装) · [构建与验证](../README.md#构建与验证)

## 服务器

- [正版认证容灾](authentication-fallback.md)：官方认证故障时保留正版 UUID，按最近认证的 IP 与有效期回退。
- [网络出口与自适应视距](network-egress.md)：AFK 优先、剩余压力反馈、高速跑图区块预算与飞行限速。

## 玩家与界面

- [账号安全](account-security.md)：可选密码、管理账号强制保护、登录验证与管理员重置。
- [空间菜单](spatial-menus.md)：操作、界面大小、文字与 FOV。
- [名牌](name-tags.md)：聊天编辑、预览与玩家样式复制。
- [AFK](afk.md)：挂机判定、活动奖励与观影豁免。
- [客户端检测与提醒](client-reminders.md)：版本、模组信号查询与自动建议。
- [场景提示](guidance.md)：自动触发、展示策略与功能接入。
- [负面效果清除](effect-cleanup.md)：非战斗中的创造与旁观玩家获得负面效果时，一键清除。

## 社区

- [网页聊天](web-chat.md)：频道与私聊、Tunnel 路由、多网页连接、Tablist 和本机历史。

- [公告](announcements.md)：目录与阅读面、连续翻页、阅读位置和文件重载。
- [QQ](qq-bot.md)、[Matrix](matrix-bot.md)：机器人配置、命令与聊天互通。
- [身份绑定](identity-binding.md)：验证码、账号作用域与解绑。
- [AI 助手](ai.md)：提供商、工具、知识库与个人记忆。
- [社区治理](governance.md)：公开管理、治理事项、成员资格、投票与管理任免。

## 世界与建造

- [建筑地图](building-map.md)：已收录建筑的 Pl3xMap 兴趣点、自动同步与网站详情。
- [地段与公示板](plot-board.md)：创建、编辑、传送点、公告与存储。
- [地段权限](plot-permissions.md)：操作目录、继承、成员与授权。
- [选区与子地段](plot-subplots.md)：组合形状、手持原生地图、实地线框与矩形管理。
- [世界更新](world-upgrade.md)：选区预检、重生成、升级与取消。
- [非欧几里得空间](non-euclidean-spaces.md)：接缝配置、连接与穿越。
- [铁块电梯](iron-elevator.md)：平台、选层、同行与楼层编号。
- [载具](vehicles.md)：控件工作台、浮空 3D 编辑、驾驶、维护与保存。

## 音乐与娱乐

- [音乐与音游](music.md)：曲库、音源导入、唱片机、玩法与校准。
- [音乐文件与配置](music-storage.md)：目录层级、启动参数、备份与缓存清理。
- [NBS 参考](nbs-music.md)：文件格式、音色与播放边界。
- [LX 音源接口](lx-custom-source-extension.md)：脚本能力、请求与返回格式。

## 开发与扩展

- [设计准则](design-principles.md)：界面、内容、工程与验证。
- [项目架构](architecture.md)：目录归属、依赖与生命周期。
- [空间菜单架构](spatial-menu-design.md)：布局、输入、生命周期、能力边界与动画。
- [空间菜单图标](spatial-menu-icons.md)：样式、含义、动作与绘制预算。
- [空间菜单 API](spatial-menu-api.md)：组件声明、约束、布局、输入与浮层示例。
- [音乐架构](music-architecture.md)：曲库、唱片、播放运行、音游与校准的职责及资源归属。
- [Fluent 翻译维护](localization.md)：消息、数值、语法变化与[静态审计](../tools/i18n/audit.py)。
- [社交平台架构](social-platforms.md)：共享命令、聊天桥与平台 SPI。
- [Skript API](skript.md)：表达式、条件、状态控制与空间注册。
- [地理数据库](../tools/geo/README.md)：离线资源更新与校验。
