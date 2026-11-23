# MIK 文档

初次安装见[项目 README](../README.md#运行与安装)和 [mikd Linux 部署](../deploy/mikd/README.md)。查 Paper、后台和网站的分工，从[项目架构](architecture.md#进程边界)开始。

## 聊天与社区

| 主题 | 内容 |
| --- | --- |
| [聊天与社交互通](chat.md) | Paper、Web、QQ、Matrix 与 `mikd` 的消息路径、频道权限、消息模型、历史和平台配置 |
| [账号、身份绑定与登录](account-security.md) | 游戏密码、绑定准入、正版认证容灾、网页登录和 Passkey |
| [社区治理](governance.md) | 成员资格、名册、投票、任免和游戏权限交付 |
| [公告](announcements.md) | 最近更新日志、文件格式和自动生效 |
| [AI 助手](ai.md) | 玩家命令、Wiki 与网页搜索、只读游戏查询和自动记忆 |

## 游戏功能

- [玩家界面](spatial-menus.md)、[名牌](name-tags.md)、[客户端检测](client-reminders.md)、[场景提示与效果清除](guidance.md)、[AFK 与活动奖励](afk.md)。
- [建筑地图](building-map.md)。
- [世界更新](world-upgrade.md)、[非欧空间](non-euclidean-spaces.md)、[铁块电梯](iron-elevator.md)、[载具与骑乘](vehicles.md)。
- [音乐与音游](music.md)：曲库、唱片机、同步组、播放和校准。

## 部署与运维

- [mikd 部署](../deploy/mikd/README.md)：systemd、私有 Web API、Tunnel、后台数据初始化与[配置](../deploy/mikd/README.md#配置与数据)。
- [网络出口与视距](network-egress.md)、[世界安全保护](server-safety.md)、[管理员监控](server-monitor.md)、[监狱管理](server-prison.md)。
- [音乐文件与配置](music-architecture.md#文件与配置)、[地理数据库更新](../tools/geo/README.md)。

## 开发与扩展

- [项目架构](architecture.md)、[设计准则](design-principles.md)、[构建与验证](../README.md#构建与验证)、[跨端互通与协议夹具](../tests/interop/README.md)。
- [空间菜单架构](spatial-menu-design.md)、[组件 API](spatial-menu-api.md)、[图标规范](spatial-menu-icons.md)。
- [音乐架构](music-architecture.md)、[LX 音源接口](lx-custom-source-extension.md)、[NBS 参考](nbs-music.md)。
- [Fluent 翻译维护](localization.md)、[Skript API](skript.md)。
