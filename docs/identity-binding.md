# 外部身份绑定

MIK 的身份绑定是独立于 QQ 的通用基础设施。Minecraft 账号以 UUID 为主键，外部账号使用四段式身份键：

社交平台的模块边界、命令执行流程和适配清单见 [社交平台适配架构](social-platforms.md)。

| 字段 | 含义 | QQ 适配值 |
| --- | --- | --- |
| `platform` | 平台类型 | `qq` |
| `issuer` | 签发用户标识的机器人、应用或身份提供方 | QQ 机器人 `AppID` |
| `scope` | 标识生效的租户、群组或会话；全局标识可留空 | `group_openid` |
| `subject` | 平台在该作用域内认证出的用户标识 | `member_openid` |

这个模型也能容纳 Discord、Telegram、Matrix、网页账号或 OIDC：适配器根据平台的真实标识语义选择 `issuer` 和 `scope`，核心绑定服务不需要知道平台协议。除受控的 `platform` 会转为小写外，其他三段是 opaque identifier，核心不会裁剪、改写大小写或做 Unicode 归一化；适配器必须原样使用认证提供方给出的稳定标识。

## 绑定不变量

- 一个完整外部身份键最多绑定一个 Minecraft UUID，不能用另一个验证码抢绑。
- 同一 Minecraft UUID 在同一个 `platform + issuer + scope` 内最多绑定一个外部身份。
- 同一 Minecraft UUID 可以绑定多个平台，也可以绑定同一平台的多个独立作用域。
- 不同 issuer 或 scope 的标识不会被自动推断为同一个人；跨作用域合并必须由平台提供可靠的全局标识后显式迁移。
- 玩家改名不会改变绑定，重新上线时只更新用于展示的最近玩家名。

## 玩家流程

```text
/bind <平台>              生成新的单次验证码
/bind qq                  生成 QQ 绑定指令
/bind list                查看自己的平台绑定与作用域短指纹
/bind cancel <平台>       作废尚未使用的验证码
/bind unlink <平台>       解除自己在该平台的全部绑定
```

验证码由密码学安全随机源生成，共 10 个不易混淆的 Base32 字符，显示为 `XXXXX-XXXXX`。验证码 5 分钟后过期，只能成功兑换一次；同一玩家重新生成同一平台验证码时，旧码立即失效。数据库只保存验证码的 SHA-256 摘要，不保存明文。

拥有 `group.manager` 权限的管理员可以使用：

```text
/bindadmin lookup <玩家或UUID>
/bindadmin unlink <玩家或UUID> <平台> confirm
```

查询输出不会展示完整外部用户 ID、群 ID 或应用 ID，只显示不可逆的作用域短指纹。绑定没有公开的递增编号；领域身份直接由 Minecraft UUID 与外部四段式身份键表达。

## QQ 流程

1. 玩家在 Minecraft 内执行 `/bind qq`。
2. 玩家点击游戏中的验证码或兑换提示，复制完整的 `/绑定 XXXXX-XXXXX` 指令。
3. 玩家在目标 QQ 群 @机器人并发送复制的指令。
4. 机器人与在线的游戏玩家都会收到成功确认；QQ 侧发送 `/我的`、游戏内执行 `/bind list` 可以复查。
5. QQ 侧发送 `/解绑 确认` 可以解除当前群身份；游戏内执行 `/bind unlink qq` 会解除该玩家在所有 QQ 作用域中的绑定。

QQ 官方的 `member_openid` 对群隔离：同一个机器人在不同群看到的同一个用户也会取得不同值。因此 QQ 适配器把 `AppID + group_openid + member_openid` 作为完整身份，用户需要在每个将来会使用身份权限的群分别绑定。这避免把不同群里的成员错误合并。参见 [QQ 官方唯一身份机制](https://bot.q.qq.com/wiki/develop/api-v2/dev-prepare/unique-id.html)。

这一作用域规则也适用于 `/我的` 的 `@用户` 与回复查询：目标只会用当前群 Gateway 事件中认证的 `member_openid` 查找当前群绑定，不会按昵称猜测，也不会把另一个群的绑定复用到当前群。

游戏端生成的验证码和兑换提示都可点击复制，复制内容是完整 QQ 指令而非裸验证码。验证码仍只在 5 分钟内有效并只能成功兑换一次；再次执行 `/bind qq` 会替换同一玩家尚未使用的 QQ 验证码。

若当前群身份已有绑定，机器人会从对应 Minecraft UUID 读取手动语言设置或最近一次客户端语言。未绑定时使用本次命中的本地化命令别名所声明的语言；别名也无法提供语言时才回退到简体中文。验证码兑换成功后已经取得目标 UUID，因此成功回复会立即使用该玩家的语言。此查找不会根据 QQ 用户、群名或 IP 推测语言，也不会跨群复用身份。

## 安全与存储

- 数据保存在 `plugins/mik/identity-bindings.db`，使用 SQLite WAL 和事务维护唯一性。绑定表以 `(platform, issuer, scope, subject)` 为复合主键，并以 `(player_uuid, platform, issuer, scope)` 作为玩家侧唯一约束。
- 只支持当前数据库格式，不包含版本号或旧格式迁移。若目录中存在旧的身份数据库，需要先删除 `identity-bindings.db` 后再启动。
- 外部平台 ID 是敏感的账号关联数据，应随服务器数据加密备份并限制文件访问权限。
- 连续 5 次无效验证码会对该外部身份锁定 15 分钟；失败窗口为 10 分钟。
- 平台冲突不会自动覆盖既有绑定，也不会消耗仍可用的验证码。
- 应用日志不记录验证码、外部用户 ID、群 ID 或消息原文；数据库只保存当前绑定和待兑换验证码。
- 平台适配器必须从已完成平台鉴权与完整性校验的可信事件构造 `subject`，绝不能接受聊天文本中自报的用户 ID。

## 接入新平台

通用接口是 `IdentityBindingManager`，并通过 Bukkit `ServicesManager` 发布。适配器注册平台描述后，游戏内会自动接受 `/bind <platform>`：

```java
IdentityBindingManager bindings = Bukkit.getServicesManager()
        .load(IdentityBindingManager.class);
IdentityPlatformRegistration registration = bindings.registerPlatform(new IdentityPlatform(
        "discord", "Discord", "/bind {code}"));

ExternalIdentity identity = new ExternalIdentity(
        new ExternalIdentityKey("discord", applicationId, guildId, authenticatedUserId),
        displayName);
IdentityLinkResult result = bindings.redeem(code, identity);

List<IdentityBinding> playerBindings = bindings.findByPlayer(minecraftUuid);

// 适配器停用时释放自己的注册租约。
registration.close();
```

第三个字段必须包含 `{code}`，并应提供可直接执行且尽量不依赖自然语言的兑换动作模板；核心模块会在游戏内用玩家语言补充“在该平台使用”的说明。

平台注册采用租约而不是按 ID 全局删除：多个适配器可以注册完全相同的平台描述，只有最后一个租约关闭后入口才会隐藏；同一平台 ID 的冲突描述会被拒绝。关闭租约只影响新的验证码入口，不会删除已有绑定；恢复适配器后绑定仍然有效。
