# 装饰头颅

从 `/menu` 选择「装饰头颅」，或执行 `/heads`（也可用 `/hdb`、`/headdb`）。玩家权限 `mik.heads.use` 默认开启。

- 公共目录可按名称、分类、标签、合集或编号搜索。输入 `#123` 可按编号精确检索。「筛选与排序」可叠加标签及合集、选择多个分类，并按相关度、名称、编号或分类排序。
- 「玩家头颅」展示当前可见的在线玩家，也可按完整 Minecraft 玩家名或 UUID 查找其他账号。账号查询由 Paper 在后台完成。
- 画廊由中央展墙和朝向玩家的两侧展墙组成，每页最多 8 列 × 5 行，实际容量随菜单大小、字号和 FOV 调整。上方提供返回、目录切换、搜索与筛选，下方提供分页按钮，指向任意展墙也可滚动翻页。空间紧时改用较小的模型。
- 左击模型或名称进入详情，右击卡片切换收藏。已收藏的卡片带星标，指向卡片可查看完整名称、编号和右键操作。
- 详情显示放大的预览。点击 1、16 或 64 可快速选量，也可用滑杆或滚轮逐个调整。领取免费，背包容不下所选数量时可先腾出空间或减少数量。
- 收藏按玩家保存，暂时无法领取的条目仍可从收藏移除。从详情或筛选页返回会恢复当前查询和浏览位置。

`/heads search <关键词>`、`/heads player [玩家名或 UUID]` 和 `/heads favorites` 可直接打开对应页面。搜索用法与[其他空间菜单](spatial-menus.md#操作)一致。

## 公共目录与缓存

公共内容来自 [HeadDB](https://github.com/SilentDevelopment/HeadDB) 使用的 [HeadsDB 数据源](https://data.headsdb.com/manifest.json)。Paper 插件启动后在后台读取 `plugins/mik/heads/catalog.zip`，再从上游校验并更新目录。缓存与下载都校验资源的大小和 SHA-256，并应用撤回清单。完整校验后才发布新快照。网络暂不可用时保留最近一次有效目录，每两分钟重试。同步成功后每六小时检查更新。

`plugins/mik/heads/favorites/` 按玩家 UUID 保存收藏。损坏的文件保留原样，由服务端日志报告。

## 预置本地内容

若服务器随部署提供 `plugins/mik/heads/custom.json`，启动时会读取并展示「本地头颅」。文件使用如下结构。`texture` 是 `textures.minecraft.net` 上纹理路径的十六进制标识。分类可省略，标签和合集可用空数组。引用的分类、标签和合集需要在同文件中定义。头颅编号由 `custom:` 和文件中的 `id` 组成。

```json
{
  "schema": 1,
  "categories": [{"id": "garden", "name": "花园"}],
  "tags": [{"id": "fruit", "name": "水果"}],
  "collections": [],
  "heads": [{
    "id": "garden-melon",
    "name": "西瓜",
    "texture": "9636dee806ba47a2c40e95b57a12f37de6c2e677f2160132a07e24eeffa6",
    "category": "garden",
    "tags": ["fruit"],
    "collections": []
  }]
}
```
