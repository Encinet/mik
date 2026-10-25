# 协作约定

开始前阅读 [README](README.md) 和[设计准则](docs/design-principles.md)。目录与依赖遵循[项目架构](docs/architecture.md)，具体功能查阅[文档索引](docs/README.md)。

## 修改

- 保留工作区已有改动，围绕当前任务完成必要修改。
- 功能代码放在 `feature/<领域>/<功能>/`，保管自己的规则、数据、命令与菜单。跨功能通过能力接口或回调连接，由 `bootstrap` 装配。
- Bukkit 状态在主线程读写；网络、文件与耗时计算放后台。任务、线程和连接随功能关闭清理。
- 翻译变更同步 `Message`、全部 FTL 和调用方。数值按数值传入，复数、格与人称交给 Fluent。
- 重命名同步全部引用，删除旧别名与失效代码。
- 测试验证真实行为和关键边界，共性检查集中复用。

## 验证

- 代码变更：`./gradlew build`。
- 翻译变更：另执行 `python3 tools/i18n/audit.py`。
- 性能测量：按需执行 `./gradlew benchmark`，与默认测试分开。
- 文档变更：检查链接与内容一致性。
- 提交结果前：`git diff --check`。

交付时简述修改、实际验证和未完成事项。
