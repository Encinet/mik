# 空间菜单 API

本文只描述现有接口。职责与约定见[菜单架构](spatial-menu-design.md)，玩家操作见[空间菜单](spatial-menus.md)，图形含义见[图标规范](spatial-menu-icons.md)。示例中的标签、数据和回调由功能提供，玩家可见文字通过 `LanguageService` 本地化。

## 声明、布局与呈现

功能准备状态快照，构造不可变 `UiNode`，再通过 `MenuScene` 打开或更新。构造与测量不执行业务动作、不读文件、不创建实体。框架按同一份最终几何呈现和命中；权限、存储与执行时复核归功能。

| 层次 | 类型 |
| --- | --- |
| 内容、语义与动作 | `UiContent`、`UiRole`、`UiInput` |
| 叶节点、容器与表面 | `UiElement`、`UiContainer`、`UiSurface` |
| 约束与安排 | `UiLayout`、`UiSizing`、`LinearSlot`、`GridSlot`、`UiTrack` |
| 局部测量结果 | `UiFrame` |
| 页面、嵌入与导航 | `MenuScene`、`MenuPanel`、`FloatingMenuHandle`、`FloatingMenuFlow` |

`MenuScene.Entry` 是组件投影后的索引。内容类型与交互语义分开：物品可以是按钮，只读文字不持有业务动作；`selected(false)` 和 `selected(true)` 都表示选择控件。禁用项保留原因和可指向区域。

## 直接组合

```java
UiNode card = Ui.surface(
        Ui.column().gap(UiLength.ui(8))
                .child(Ui.heading(title).key("title").build())
                .child(Ui.text(description).key("description").build())
                .child(Ui.button(UiIcons.PLAY, playLabel)
                        .key("play").primary(play).build())
                .build())
        .key("track-card").style(UiStyle.PANEL.withBackgroundAlpha(.78))
        .build();
FloatingMenus.present(player, MenuScene.screen("track-detail").content(card).build());
```

## 嵌入功能面板

`MenuPanel.render()` 返回功能场景，`attach(handle)` 接收宿主句柄，`close()` 释放本面板资源。功能完成操作后调用宿主传入的重绘回调；宿主切换或关闭时使旧回调失效并关闭面板，保存事务仍归功能。

完整页面可用 `scene.toBuilder()` 包装已解析内容，保留动作、提示、刷新和生命周期，并重新生成路径。`UiTemplates.withBack(content, label)` 增加独立打开时的返回工具。轻量浮窗只使用面板标题、正文和局部工具，见[详情浮层](#保留父页面的详情浮层)。

## 固定在世界位置的面板

在装配层调用 `floatingMenuService.worldPanels()` 创建 `WorldMenuScope`，通过生命周期托管后注入功能。相同作用域、玩家与 `UiKey` 更新原展示槽位，不替换该玩家的普通菜单；不同作用域的相同键互不影响。

```java
WorldMenuSurface surface = new WorldMenuSurface(
        center, UiRotation.degrees(yaw, pitch, roll),
        new FloatingMenuSize(1.76, 1.36), .25, 5);
MenuScene scene = MenuScene.screen("station")
        .appearance(FloatingMenuAppearance.NO_BACKGROUNDS)
        .animation(FloatingMenuAnimation.PLANAR)
        .content(Ui.column()
                .child(Ui.heading(title).build())
                .child(Ui.button(label).key("operate").primary(operation).build())
                .build())
        .build();
FloatingMenuHandle handle = panels.show(player, UiKey.named("controls"), surface, scene,
        viewer -> sourceStillAvailable(viewer));
```

`center` 是背板中心，局部 +X 向右、+Y 向上、+Z 指向面板正面；完整旋转支持地板、顶面和倾斜表面。`size` 按方块计，`scale` 是每个逻辑单位的显示倍率，逻辑视口为 `size / (0.02 * scale)`；`reach` 是射线操作距离。位置由面板提供，FOV 和菜单大小不参与调整，文字偏好仍参与有限范围内的排版。

背景与动画独立选择。`NO_BACKGROUNDS` 移除各交互状态的默认控件背景，节点显式声明的背景仍生效。`PLANAR` 保留透明度和布局过渡，进入、悬浮与按压均不沿表面法线位移；自定义 `FloatingMenuAnimation` 可分别配置 `entryOffset`、`hoverOffset` 和 `pressOffset`。固定宿主只为实际内容厚度与已配置的后退位移预留间距；平面文字贴于声明的背板，模型仍保留自身厚度。

场景使用 `IN_FRONT` 平面内容，标题放在内容树中，不声明场景外标题、世界锚定装饰或 Popover。`handle.openChild(scene)` 可打开普通详情／编辑页面；关闭前台后恢复固定面板。`handle.update`、`refresh`、`close` 与现有句柄共用语义，`reanchor` 重用固定表面；暂停显示时句柄状态为 `SUSPENDED`。可用条件用于输入复核，不负责业务提交验证。

`panels.clear(player, key)` 清除一个槽位，`clear(playerId)` 清除本作用域中该玩家的全部展示，之后仍可重新展示；`close()` 清除并永久关闭整个作用域。服务的 `closeAllImmediately()` 只清空当前展示，服务停用才撤销所有作用域。上述操作、`show` 和挂点状态检查在主线程执行；业务应在来源消失或不再需要展示时及时清理。共享层在玩家会话结束和服务关闭时释放剩余展示。

## 身份、尺寸与父级参数

动态子项使用实际对象的稳定键；排序只改变位置。兄弟键唯一，`UiKey.named` 接收非空、不含 `/`、最长 512 字符的路径段；`UiKey.item(id)` 编码 `%` 与 `/`，过长结果使用稳定摘要。动作继续使用原始业务 ID。

```java
Ui.row()
    .child(UiKey.item(firstId), reusableCard)
    .child(UiKey.item(secondId), reusableCard)
    .build();
```

| 声明 | 含义 |
| --- | --- |
| `UiLength`、`UiSize` | 逻辑界面单位；呈现边界按 1 UI = 0.02 方块换算 |
| `size(size)`、`UiSizing.fixed(size)` | 固定占位 |
| `UiSizing.CONTENT`、`atLeast(size)`、`width(min, max)` | 内容尺寸、最小占位、宽度范围 |
| `sizing.atLeastHeight(height)` | 只增加最小高度 |
| 默认 `LinearSlot` | 按内容测量，在交叉轴居中 |
| `LinearSlot.stretch()` | 参与自然测量，再拉齐交叉轴；遵守子项最大尺寸 |
| `LinearSlot.grow(weight)` | 分配主轴余量 |
| `LinearSlot.fit(alignment)` | 在普通兄弟确定的交叉轴内测量辅助内容，保留自然尺寸并按需换行 |
| `GridSlot.cell(row, column)` | 单元格；可加 `withRowSpan`、`withColumnSpan` |
| `MenuScene.viewport(size)` | 编译上限，默认 1200×600 UI，单边不超过 4096 UI |

`UiSizing` 只声明组件边界，父级分配由槽位表达。拉齐引起换行或列数变化时重测主轴需求。`START`、`CENTER`、`END` 控制位置，组件达到最大尺寸后仍按该位置对齐。

说明和搜索栏可跟随正文宽度，避免反向撑宽短列表：

```java
Ui.column()
    .child(body)
    .child(search, LinearSlot.fit())
    .child(feedback, LinearSlot.fit(LinearSlot.Alignment.START))
    .build();
```

### 阅读基准与内容尺寸

`screen(id)` 首次正式展示确定观看基准；同页更新保留位置、距离与名义比例，面板按当前内容重新计算宽高，不保留历史最大尺寸。普通前方页面先将声明视口限制到完整页面预算，正文、侧栏、标题和工具共同服从该约束。`UiLayouts.contentRow(contentKey, gap)` 与 `contentColumn(contentKey, gap)` 先测常驻区域，再将剩余宽度或高度作为正文的测量上限，最终按实际占用尺寸布局。`workspace` 和 `sheet` 分别复用横向与纵向规则；分类切换后未使用的预算不形成空槽，也不参与整页居中。需要留白时由 `UiSizing` 或画布明确声明，普通 `LinearSlot.grow` 仍保留填充分配。阅读焦点仅选择主内容和附着来源，不再绕过完整页面预算。

普通页面默认空间比例为 0.25，默认绘制范围至多 6×3 格，由 `MenuScene.DEFAULT_VIEWPORT` 的 1200×600 UI 推导；菜单倍率调整可用逻辑尺寸，字体倍率参与测量与分页。投影以完整布局居中。默认 70° 名义距离为 2.8 格，窄 FOV 按页面高度增加距离，高 FOV 不放大界面。最终完整包络继续约束阅读范围和避障；弧形与环绕使用各自投影。

`loading(true)` 的首屏占位不确定正式阅读基准；已有正式内容时保留它，完成后原位更新。`toBuilder()` 会保留标记，正式页须设置 `loading(false)`。错误页属于正式内容。

固定空间由组件约束声明，`UiSurface` 将多余高度留在内容下方。返回恢复父页阅读基准并按最新内容重排；修改菜单大小、文字、FOV 或主动定位会重新评估。专用场景可用 `anchorMode(FloatingMenuAnchorMode.ADAPTIVE)` 随内容重新适配。

普通前方阅读面按完整视口确定名义距离，与空间比例分别维护；地面与顶棚候选使用整页包络，主区下方的工具也参与放置。前方页与浮层通过同一视点收缩完整包络，物理尺寸、模型深度和视点距离同比变化，保持角大小及排版，不设置固定物理距离下限；恢复过程同样保持这一比例。只有视点处于不透明体内部或空间无法验证时才暂停显示和命中，会话保留并自动复核，不发送空间不足提示或关闭菜单。该规则不改变逻辑分页，也不替换环绕场景自己的径向布局。普通鼠标输入按当前发布表面射线选取，远处控件不依赖客户端能否直接点击其虚拟实体；重复输入仍通过同一会话输入门合并。

客户端拾取辅助实体沿视点到当前表面的射线投影到最多 2.5 格处，尺寸等比投影以保持角范围；按钮和浮层捕获区域使用同一规则。辅助实体没有独立业务动作，服务端继续依据真实表面、遮挡与当前会话重新选择对象。视点或表面变化时更新，静止时不重复发送，关闭时随原控件释放。

局部重排从当前呈现状态衔接最新目标，旋转和缩放也进入运动包络。路径与眼睛之间的完整体积使用地形、附近实体及已加载区块约束检查；世界快照变化时复核剩余路径。受阻或超出检查预算时直接重新发布已验证终态，保留阅读位置和业务状态。路径包络只用于避障，不参与组件测量与分页；减弱动态直接采用终态。

控件先采样当前布局与指向／按压偏移，浮层随后按同一时刻的来源中心、旋转和比例定位，再发布整帧。显示、命中与提示共享不可变的当前表面，尚未形成可交互表面的控件不接收直接实体点击。附着受阻时不发布部分控件帧，保留会话并按空间恢复规则重新采样。

## 文字与图文占位

- `.textWidth(pixels)` 指定软换行宽度，`.alignment(...)` 控制行内对齐。默认 `AUTO` 对交互控件使用父级最终可用宽度作为上限，按实际文字宽度排版；只读文字保留适合阅读的默认行宽。空间不足时再换行，`.singleLine()` 则在最终宽度内缩短并提供完整提示。
- `.singleLine()` 保持单行，超长或显式换行处省略；实际截断后可查看完整提示。
- `.noWrap()` 保留显式换行，禁止自动折行和省略，按实际字形建立最小宽度；短读数可搭配 `UiTrack.intrinsic()`。
- `.summaryLines(n)` 优先完整换行，整页放不下时才允许按实际宽度缩为最多 n 行；`UiElement.withSummaryLines(n)` 与 `UiPort.withSummaryLines(n)` 使用同一规则。截断后保留悬浮全文，已有操作说明接在全文之后。
- `.maxLines(n)` 为编辑预览或显式紧凑表示声明硬性行数上限；它不为未使用的行预留高度。普通标题、详情和状态使用 `.summaryLines(n)`。
- `UiText.abbreviate(component, codePoints)` 是保留样式的 Unicode 码点截取工具；普通界面内容不在构建时预先裁剪，摘要由测量决定。
- `Ui.text(Component.object(player))` 显示客户端玩家头像，按单个对象字形测量，无需下载皮肤或创建头颅物品。

物品和方块的图标、间距、标签作为同一占位测量；模型边距、最大悬停和浮动包络计入尺寸。标签保持文字阅读面，不随模型厚度前移；自定义浮动同时更新图文。

### 基线与短行内单元

`UiLayouts.baselineRow(gap, line, fallback)` 对齐首行或末行，`UiLayouts.inline(gap, lineGap, line, fallback)` 让完整短单元按顺序换行。两者按自然尺寸排列，不接受 flex 权重；长段落仍使用普通文字块及分页阅读。

```java
Ui.layout(UiLayouts.inline(UiLength.ui(6), UiLength.ui(4),
        UiBaseline.Line.FIRST, LinearSlot.Alignment.END))
    .child(Ui.icon(UiIcons.INFO, statusLabel).key("status").build())
    .child(Ui.text(summary).key("summary").noWrap().build())
    .build();
```

`UiBaseline` 使用距组件上沿的逻辑单位；文字测量提供可选首行与末行对齐线，嵌套容器传递内边距后的结果。图片、模型及未提供基线的内容使用声明的 `START`、`CENTER` 或 `END` 回退。默认字体标定为服务端估计，自定义字体的实际字形可能与估计不同。

自定义 `ElementMeasurer` 可用 `ElementMeasurement.withBaseline(new UiBaseline(first, last))` 提供数据。布局通过 `MeasureScope.measuredBaseline(index)` 查询；发布结果保存在 `UiFrame.Element.baseline()`，不可超出所测高度。

`ElementMeasurer.heightIndependent(element)` 默认返回 `false`。只有在缩小最大高度、且已测尺寸仍能放下时，尺寸与拟合标签都保证不变，才可声明 `true`；分页据此复用同一轮、同一宽度下的叶节点尺寸。内置测量器仅为单行或禁止自动换行的文字声明该契约，自适应组仍按页脚后的约束复测。

### 摘要与全文

正文预览与全文入口使用：

```java
UiNode preview = Ui.excerpt(
        Ui.text(body).key("body").summaryLines(8).build(),
        Ui.button(readFull).key("read-full").primary(openFull).build());
```

整页先用完整内容尝试换行、换向和分页，确实溢出时才启用声明的摘要行数。回退从放不下的局部区域开始，复用已放得下的全文测量；必要时才向共同容器收敛，避免一张卡片挤压其他卡片的内容。所有测量共用有界预算，失败轮次不发布背景、内容、页码或动作；尺寸恢复后重新优先全文，不保存折叠状态。无效结构不触发摘要回退。

只有正文实际被截断时才显示全文入口，并保留源全文提示；超长正文仍可进入阅读页分页查看。

## 容器与表格

行列、网格、换行、叠层与画布可以嵌套。`Ui.layout(UiLayouts.wrap(...))` 自动换行，`UiLayouts.canvas(viewport, centers)` 提供有界自由摆放。自定义 `UiLayout` 测量并安排每个子项，返回矩形须在声明边界内；`Result.focusing(childKey)` 指定主阅读子项。

`UiTemplates.focus(container, childKey)` 或 `UiFocusLayout` 声明主阅读内容；显式焦点沿单子树包装层传播，`UiFrame.readingRegion()` 发布该区域。`Result.focusing(...)` 也可指定测量后的焦点。焦点不分配尺寸预算，所有子树服从完整页面的父级约束。`UiTemplates.reading(contentKey, sections...)` 同时声明正文纵列与焦点；已有容器可用 `reading(container, contentKey, gap)` 指定同一规则。

解析保留槽位和键。零尺寸容器不增加内边距、背景或间距，固定占位与画布继续保留。普通网格按均分的初测宽度建立单格基础，再按跨度和位置分配跨格需求；显式 `GridSlot` 不变时，声明顺序不改变结果。最终列宽确定后重测行高。没有单元格的网格不保留列间距。

| 组合 | 行为 |
| --- | --- |
| `UiLayouts.firstFit(...)` | 依次尝试完整布局；仅尺寸溢出触发替代，身份与跨度错误继续报告 |
| `UiLayouts.adaptiveWrap(maximumRows, gap, rowGap, fallback)` | 先按内容宽度换行；自然行数超过上限或真实溢出时使用完整回退布局 |
| `UiTemplates.split(navigation, content, preferredAxis)` | 声明优先上下或左右排布；首选方向放不下时整体换向，保持主阅读区和控件身份 |
| `UiLayouts.headerBody(gap)` | 标题在自身宽度边界内拉齐，正文使用最终剩余高度；空区域无分隔间距 |
| `UiTemplates.titleBar(title, actions)` | 标题相对整栏居中，操作靠右；放不下时操作整组换行 |
| `UiTemplates.centeredBar(center, leading, trailing)` | 两侧对称避让，中心不随缺少某侧操作偏移 |
| `UiTemplates.actionRow(content, actions)` | 内容靠起点、操作靠末端，窄处整组换行 |
| `UiTemplates.disclosure(key, summary, toggle, expanded, body)` | 受控展开组；标题与开关持续显示，只有展开时构建正文 |
| `UiLayouts.preferredWidth(width, layout)` | 与文字长度无关的自然宽度，仍服从宿主约束 |
| `UiSurface.stretchingContent()` | 将已分配的面板内宽度传给正文，适合独立搜索工具栏；默认仍按正文自然宽度排版 |

零尺寸可选工具组与未提供工具组的间距一致，不保留空按钮或空操作行。

展开组接收普通摘要节点、带语义说明的开关控件、功能持有的展开状态，以及只构建节点的正文工厂。工厂在构建展开快照时调用一次，测量与逐帧过渡不再调用；折叠快照没有正文节点、动作或占位。`header`、`body` 是稳定子作用域，开关的选中、禁用和回调有效性由功能维护。正文继续遵守宿主约束，可组合表单、文档或分页；超大正文使用这些既有机制适配。展开后相邻控件复用布局过渡，反向从当前姿态衔接；当前输入立即随快照更新。来源控件折叠后，所属托管浮层先释放再停止读取内容。编辑中的草稿如何处理须由功能明确，例如音乐设备暂时禁止折叠，直到结束当前输入。

### 分页

```java
UiNode cards = Ui.pagedFlow(3).key("results")
        .items(items).page(pageIndex, selectPage).build();
UiNode paragraphs = Ui.pagedList().key("reading")
        .header(header).items(lines).page(pageIndex, selectPage).build();
```

- `pagedFlow(rows)` 从左到右装入原尺寸子项，行数为上限，实际容量受宽高及页脚约束；`.gaps(horizontal, vertical)` 配置间距。
- `pagedList()` 按高度排条目，`.gap(...)` 配置间距；标题保留自然高度，空正文不额外留间距。
- 两者默认生成分页和滚轮，`.pager(page -> controls)` 可定制；工厂只读取页码快照。总页数由 `page.count()` 取得，越界页码钳制，空集合保留一页。
- 同一集合各页保留最大实际正文尺寸，单行结果不预留多行。条目在扣除页脚后重新测量，正文高度固定为已打包结果；单项连同必要操作不能高于一页，长文须分段。

`UiTemplates.pagedActions(region, rows, pageIndex, selectPage)` 将既有内容位解析为有界流式分页，按自然宽度排成最多指定行数；它使用流式布局，不读取传入内容位的普通网格布局。功能维护页码和重绘回调；`.withSizing(...)` 限制集合宽度，`.withCompactModels()` 明确允许模型入口在空间不足时使用同键、同动作的两行文字表示，截断后保留完整标签提示。

`UiLayouts.balancedGrid(maximumColumns, targetAspectRatio, bias, columnGap, rowGap)` 是普通的 `UiLayout`，可直接传入 `Ui.layout(...)`。它复用网格轨道、跨度及最终宽度测量，在可用范围内比较各列数的实际宽高比；目标比例按宽／高传入，`GridBias.NONE`、`TALLER`、`WIDER` 在形状同样接近目标时决定方向。均衡网格默认采用 `GridSizing.UNIFORM` 和双轴 `GridAlignment.STRETCH`，入口共享矩形宽高并填满单元格；内容按原字号、原比例呈现。宽度拉齐后重新测量换行高度，选择后恢复对应测量结果；方向偏好不限制可用的排版形状。

```java
UiLayout layout = UiLayouts.balancedGrid(3, 1, UiLayouts.GridBias.TALLER,
        UiLength.ui(10), UiLength.ui(8));
UiNode actions = UiTemplates.pagedActions("destinations", layout, pageIndex, selectPage)
        .withCompactModels();
```

`pagedActions(region, layout, pageIndex, selectPage)` 解析为 `UiPagedLayout`，可使用均衡网格、普通纵列、换行或自定义纯布局。它与流式分页共用页脚测量、越界钳制和导航规则，支持 `.withSizing(...)`、`.withCompactModels()`；按完整连续前缀试排，不假定自适应布局的容量单调变化。各页保留最大实际正文尺寸，尾页使用原布局重新排列，不强制单元格等边。直接构造 `UiPagedLayout` 时提供包含条目的 `UiContainer`、正文与页脚间距、页码、页脚工厂和选择回调。该适配用于有界集合，大型数据使用窗口集合。

### 文档阅读与位置保持

长正文使用 `document`，由阅读会话持有源位置：

```java
UiNode reading = Ui.document().key("notice")
        .blocks(List.of(
                Ui.text(introduction).key("introduction").build(),
                Ui.text(body).key("body").textWidth(320).build(),
                figureWithCaption))
        .position(state.position(), position -> updateReadingPosition(position))
        .build();
```

各块必须声明稳定键。只读、允许自动换行且未限制行数的纯文字组件可按实际宽高拆分；颜色、字体、装饰和 Unicode 字素保持完整。其他节点与包含交互的组整体分页，超大组须显式提供尺寸回退。普通块拉齐当前正文宽度，文字仍使用自身对齐方式；空的可选块不保留间距或背景宽度。

`UiReadingPosition` 保存源块键、按 Unicode 码点计数的偏移和序号回退。字号、FOV 或约束变化后，重新选择包含该位置的页面；正文缩短时钳制到该块最后一段，块被移除时寻找邻近序号。分页边界消费的空白仍计入源偏移，不在页末额外绘制空行。`UiFrame.Pagination.readingPosition()` 发布当前实际页首，测量不修改功能持有的位置。

`.readingBody()` 将非空正文声明为阅读焦点，标题与页脚先在完整页面内预留高度；正文必须能显示可读内容，空正文在该模式下报告溢出，由宿主提供空状态或回退。可通过 `.pager((page, select) -> ...)` 根据实际页码组合导航和局部操作，例如只在读完提案的末页提供投票。辅助操作出现时保持正文容量及源位置；组件仍同时核对父容器的完整高度。页脚工厂只构建不可变节点，不读写业务状态或执行操作。

既有声明内容可通过 `UiPort.document(position, read)` 接入同一阅读器，也支持 `.readingBody()`。解析时将该内容位拥有的条目转换为源块，保留条目身份、样式、提示与操作；纯文字按上述规则切分，交互条目整体分页。此适配使用文档流替代内容位原来的排列算法，仍由功能持有阅读位置。

`.position(snapshot, changed)` 必须明确提供所有者回调。翻页请求新位置，所属会话更新状态并重绘；同一面板用于独立页或浮窗时，各宿主分别持有自己的位置和生命周期。`.header(...)` 保留标题，`.gap(...)` 配置块间距；标题与页脚均在正文切分前占用高度，多页保留最大实际正文范围，短末页不会使正文或导航横向跳动。

`.pager((page, select) -> controls)` 定制分页区域，工厂在测量中可能执行多次，只构造快照内容。`select` 由编译器将页码映射回源位置；定制 `UiPager` 可以声明与相邻文档衔接的导航作用域。公告阅读器采用此方式，目录滚轮与正文滚轮独立，跨文档边界不循环。

每份文档最多 256 个块、65,536 个纯文字码点及 2,048 个切分片段，同一尺寸协调最多 8 轮。单次编译内复用相同高度的切分结果，位置、动作与页面快照不进入跨会话缓存。自定义 `ElementMeasurer.documentPages(...)` 可提供按源码点计数的快速分页边界；未实现时由通用测量器在字素边界试排，仍受总测量预算限制。

运行时按视图复用纯文字的全文换行结果，页脚或页高变化直接重新分组，源内容、字体或换行宽度变化重新计算。全文换行与文字叶节点尺寸共用下方预算；点击事件、悬停负载和动态对象组件不进入缓存，关闭视图统一释放。

### 分页组与尺寸回退

插图与图注、字段与错误说明可以作为同一个普通子树条目分页。超大组使用显式回退：

```java
UiNode figure = Ui.alternative(
        fullFigure,      // 例如含插图和图注的同键组件
        readFullButton); // 打开同一对象的完整阅读页
```

两个根必须使用同一个对象键。编译器先尝试首选表示，只在尺寸溢出时选择回退；身份、跨度和无效布局继续报告。仅所选分支发布内容与动作，声明索引不展开候选分支；调用方以实际 `UiFrame` 和测量后的场景为准。候选优先使用类型化内容；包含声明内容位时，两种表示分别解析并须拥有同一声明集合。仅有文字摘要差异时直接使用 `withSummaryLines(lines)`，无需复制两套页面。长正文仍须提供阅读入口。两种表示均计入结构节点与深度预算。

已由功能分页的表格与 `UiTemplates.pager(page, selectPage)` 放在同一容器；该容器即滚轮归属。文字版本使用 `pager(page, previousLabel, summary, nextLabel, selectPage)`。页码保持在整栏中心；单行摘要在窄处省略并提供完整提示，为两侧操作保留完整命中空间。内容位对应 `MenuScene.Builder.pagination(...)` 与 `UiTemplates.pagination(region)`。默认单页隐藏控件但保留输入归属，定制摘要版本保留摘要。

多个集合按元素归属和实际平面命中翻页，嵌套优先内层；首末页停止并消费滚轮。数值控件的滚轮调整优先；只有一个集合时保留整页翻页回退。浮层与父页分别处理，功能不重复绑定整页滚轮。

### 长集合窗口

变高段落使用 `pagedList`，它会测量完整的有界条目集合。大量等高条目使用 `windowedList`，先由行模板、标题和页脚计算容量，再创建当前窗口：

```java
var snapshot = List.copyOf(results);
var rowTemplate = Ui.button(Component.text("M")).singleLine()
        .primary((player, handle) -> {}).build();
UiNode catalogue = Ui.windowedList(snapshot.size(), rowTemplate, index -> {
    var value = snapshot.get(index);
    return Ui.button(label(value)).key(key(value)).singleLine()
            .primary((player, handle) -> select(value)).build();
}).key("catalogue").header(header).page(pageIndex, selectPage).build();
```

模板只参与测量，不发布内容、动作、背景或输入目标；模板样式须与条目一致，文字倍率与继承样式会参与行高测量。已有固定尺寸时可用 `windowedList(count, UiLength.ui(height), factory)`。条目仍按自身完整尺寸测量，超过声明行高会报告溢出；变高正文应改用有界段落分页，不进行裁剪。

窗口列表和表格可在 `.page(...)` 后调用 `.containing(itemIndex)`，按扣除页脚后的实际容量显示包含该源条目的页面。功能先在当前快照中按对象身份查找索引，不保存旧排序下的索引；筛选缩减时超出集合的索引收敛到末项，空集合不创建行。再次调用 `.page(...)` 清除定位请求，恢复普通翻页。队列移动使用播放出现的 UUID 查找当前位置，字号与布局变化不再依赖固定行数计算结果页。

工厂只读取已加载的不可变快照，不做 I/O、不更新业务状态；一次布局可能因父级约束变化调用多次。每个测量窗口最多创建 256 项，无预取；不会为计算页数扫描其他项。声明遍历不创建行，实际条目与动作以发布的 `UiFrame`、测量后 `MenuScene` 为准。单页使用自然宽度；多页占用分配宽度并保留完整正文高度，末页不随条目减少跳动。标题持续显示，页码越界钳制；空集合只展示标题及需要的页脚。

需要按显示范围准备数据时，可在窗口列表声明 `.observeWindow(dataIdentity, page -> requestVisible(page.fromIndex(), page.toIndex()))`。通知由运行时在有效布局发布后的 tick 阶段交付，测量期间不调用；同一数据身份与窗口去重，暂停后恢复重新通知。数据身份随数据集变化，普通可用性或样式刷新保留身份。回调只提交功能任务，查询结果作为新快照更新；功能仍须校验自己的访问与数据身份。失败布局、关闭会话及重入替换后的剩余通知均不交付。

每行须声明稳定对象键，不能使用默认键或以当前位置替代对象身份。离开窗口的动作、焦点及所附浮层随新帧失效；浮层在读取详情之前检查已实现的来源，旧回调不能重开已关闭内容。查询、选择和页码仍由功能维护。默认纵向搜索已使用此路径，候选总量限制和重新查询规则不变。

自定义窗口页脚返回 `UiPager` 时，其导航范围与回调同时用于按钮和滚轮。宿主可据最终页容量把显示页映射为数据源索引，例如治理历史记录的有限读取批次；页脚工厂只构造快照导航，后台读取由用户触发的功能回调执行。

### 表格

等高卡片可通过 `Ui.windowedGrid(windowedList, maximumColumns, minimumColumnWidth, columnGap)` 复用同一窗口与源位置。最小列宽决定增加列数的阈值，窄宿主至少保留一列；每页最多 256 项，按完整行计算容量，末页保留正文高度。`.centered(true)` 将卡片在列内居中、短行水平居中，并将末页内容在预留正文高度内垂直居中。模板在实际列宽下测量，只有可见卡片被创建；卡片键仍使用对象身份，行列序号只用于摆放。地段和成员目录采用此方式，并提供双行卡片到单行卡片的同键回退。

单元格是普通子树，可组合文字、图片、状态和按钮。表格默认沿行垂直居中，可用 `.verticalAlignment(START/END)` 改变；普通网格用 `.horizontalAlignment(...)` 控制列内位置。

```java
UiNode table = Ui.<RecordView>table()
        .column(new UiTable.Column<>(UiKey.named("cover"), coverHeading,
                UiTrack.fixed(UiLength.ui(64)), RecordView::coverCell))
        .column(new UiTable.Column<>(UiKey.named("title"), titleHeading,
                UiTrack.content(UiLength.ui(80), UiLength.ui(180)), RecordView::titleCell))
        .column(new UiTable.Column<>(UiKey.named("actions"), actionsHeading,
                UiTrack.fraction(1), RecordView::actionsCell))
        .rows(rows, RecordView::key).gaps(UiLength.ui(12), UiLength.ui(8)).build();
```

普通网格的 `GridAlignment.STRETCH` 将子项分配到完整列宽或行高；达到组件最大尺寸时在该范围内居中。`grid(columns, columnGap, rowGap, horizontalAlignment, verticalAlignment, GridSizing.UNIFORM)` 让全部列等宽、全部行等高；`CONTENT` 按各列、各行的内容分别测量。宽度与高度独立，跨行／跨列组件包含对应间距。普通网格默认仍采用 `CONTENT`。

`UiLayouts.grid(List<UiTrack>, columnGap, rowGap)` 可声明没有数据表头的共享列，例如标签和值。`fixed` 固定宽度，`intrinsic` 保留测得宽度，`content` 可在边界内收缩，`fraction` 先保留控件下限再按权重分余量，达到上限后继续分给其他比例列。

表头和各行共用列宽，跨列内容使用已分配轨道，不再重新分配。内容或比例列不足时有界发现单元格实际下限，再分配余量；单层控件与嵌套子树规则相同。硬性下限仍放不下时报告溢出，交给完整替代布局。表格可用 `.sizing(bounds)` 限制自身尺寸；分页时先保留完整表头，再计算正文容量。排序、筛选与写入归功能；有界表格最多 256 行数据、256 列，另受网格总预算约束。

需要实际行高分页时，将末尾 `.build()` 换成 `.paged(pageIndex, selectPage)`，返回 `UiPagedTable`。功能持有页码；组件持续显示表头，正文与分页条使用扣除表头后的剩余空间。所有有界数据行共同确定列宽，表头与当前页使用同一组轨道，翻页不重新分列；正文高度按最大页保持，末页不会收缩。测量后只发布当前页的行与动作，行键沿用业务对象身份。

分页表格会测量完整的有界行集合以计算列需求与页面，不适用于无界数据源。大量等高数据可用 `.windowed(count, rowTemplate, itemAt, rowKey).page(pageIndex, selectPage)`，返回 `UiWindowedTable`。默认只用表头和一行模板确定列宽，再根据可读高度创建当前窗口，最多 256 行；模板不发布动作。表头持续显示，翻页保持列宽和正文高度。单元格应采用与模板一致的单行文字或固定尺寸，完整标签通过提示读取。

名称长度变化较大的目录可接 `.fitVisibleColumns()`：模板只建立等高行的高度，列宽由当前窗口的表头和实际单元格测量，翻页后可随内容扩宽或收窄；使用 `intrinsic` 列保留自然宽度，使用 `content` 列允许收缩。分页栏独立保留所需宽度，单元格控件仍保持自然尺寸。窗口与列宽在有界次数内共同收敛，不扫描完整数据源；工厂可能读取候选窗口并重复测量。后台快照由功能加载，工厂不查询存储、不写状态。

窗口表格不扫描屏外数据来决定列宽，屏外较长文字不能改变当前列宽。变高正文使用有界分页。单行自身超过可用空间或列下限无法满足时报告溢出，宿主用同键 `UiAlternative` 提供紧凑卡片、详情入口等完整替代呈现。

窗口表格的 `.pager(controls)` 与文档流共用 `UiPageControls`。工厂接收实际页码、总数、窗口起止与翻页回调，可按最终窗口构造「选择本页」等操作；它可能被重复测量，只生成控件，不写回状态或执行查询。窄处的紧凑卡片可使用比例列，将可用宽度留给行内容和页控件。

替代表示共用相同的宿主约束；例如完整表格与紧凑表格保留正文焦点，同时在完整页面内预留表头和工具。焦点查找遵守树深和遍历预算，不展开无关侧栏。

## 内容位与页面配方

`UiPort` 在构建时解析为同一组件树，声明的每项恰好归属一个内容位：

```java
var menu = MenuScene.screen("library").content(UiTemplates.menu(
        UiTemplates.heading("summary"), UiTemplates.actions("tracks", 3),
        UiTemplates.navigation("navigation")));
menu.information("title", title).region("summary");
menu.control("play", playLabel).region("tracks").primary(play);
menu.back(backLabel).region("navigation");
```

| 配方 | 用途 |
| --- | --- |
| `title(id)`、`heading(id)` | 1.25、1.12 倍字号的只读内容位 |
| `list(id)`、`actions(id, columns)`、`navigation(id)` | 拉齐列表、居中按钮网格、换行导航 |
| `contentActions(id, compactColumns, maximumNaturalRows)` | 按完整文案测量动作宽度，先自然换行，超过行数预算再用紧凑网格 |
| `window(id, content)`、`sidebar(...)`、`toolbar(...)` | 阅读面、侧栏、沿法线前移 4 UI 的工具面 |
| `sheet(content, tools)` | 正文与下方工具，焦点在正文 |
| `split(sidebar, content)` | 主从分栏，放不下时纵向排列 |
| `workspace(navigation, content)` | 分类侧栏与内容自带的阅读面和工具 |
| `workspace(navigation, content, tools)` | 额外生成阅读面和底部工具 |
| `panoramicPanels(centerKey, spacing, ...)` | 指定中心区域，其他面板并列 |
| `workbench(modelSize/modelNode, inspector, header, controls, footer)` | 有界模型空间和检查器，空间不足时纵向组合 |

`UiTemplates.actions(id, columns)` 保留紧凑等列网格语义，沿用标准动作行宽，标签放不下时在格内换行；需要按钮按完整文案自然扩宽时使用 `contentActions(...)`。`navigation(...)` 与流式 `pagedActions(...)` 默认按自然文案宽度换行。自然宽度始终受父级最终约束，明确设置的 `.textWidth(...)` 优先于内容位策略。自定义内容位可通过 `.withItemTextWidth(FloatingMenuTextWidth.CONTENT)` 继承自然宽度策略。内容位可用 `.withSummaryLines(...)` 声明整页确实放不下时的行数预算。`UiTemplates.focus(container, childKey)` 传播主阅读焦点，空阅读区不接管可见工具的焦点。`UiPort.withItemSlot(...)` 设置解析条目的父级分配。模型的草稿与预览状态仍归功能。

## 表面、透明度与空间层级

`Ui.surface(content)` 为刚性表面。`withBackgroundAlpha` 只改背景；`withContentAlpha` 沿父级相乘，作用于文字和图片。物品、方块使用占位内缩放显隐，不支持材质透明。透明面板仍参与包络和输入。

### 主阅读区与面板朝向

| `UiSurfaceFacing` | 行为 |
| --- | --- |
| `INHERIT` | 默认；内部卡片继承最近面板平面 |
| `VIEWER` | 自由面板按相对阅读区域的位置自动选择单轴转向；窗口配方采用 |
| `HORIZONTAL` | 显式水平内收；侧栏配方采用 |
| `VERTICAL` | 显式垂直倾斜；工具栏配方采用 |
| `FORWARD` | 独立面板，与主阅读面平行 |

侧面板只水平内收，上下面板只垂直倾斜。`split` 在同一内容树上切换横排／竖排并发布对应朝向，动作身份保持；默认优先横排，传入 `UiLayouts.Axis.VERTICAL` 可优先纵向阅读。纵向组合先预留导航，再把剩余高度交给正文分页。`UiLayout.Result.facing(childKey, facing)` 可声明直接子表面的响应式朝向，显式 `FORWARD` 和 `INHERIT` 保持原有平面。以靠近主区的边为支点，水平最多 28°、垂直最多 22°，不自动滚转。朝向参考稳定阅读点，普通转头不追逐准星；图文、背景与命中共同变换。

### 样式与变换

`withTextScale(...)` 沿父级相乘，局部累计 0.25–4，再叠加玩家文字偏好。文字和图标共同测量缩放，图片与物品本体不随字号放大。`elevation` 沿表面法线前移，嵌套累计最多 64 UI，不改二维占位。

`UiTheme` 提供组件样式，`UiColor.themed(role)` 从玩家主题解析，`UiColor.literal(rgb, alpha)` 使用固定颜色；显式 `withAlpha` 优先，包括零透明度。按钮的 `controlSurface` 覆盖分配矩形并表达交互状态，`withControlSurface(false)` 可关闭。自定义主题是 `UiCustomTheme` 的完整快照，新增主题或角色须同步存储、名称和翻译；玩家配置见[主题设置](spatial-menus.md#菜单主题大小与文字)。

默认前方阅读，`frontArc()` 将独立面板排成前方弧面，`aroundViewer()` 环绕玩家。子项保持所在刚性平面。`Ui.group(content)` 在排版后施加层级变换，保持节点身份与二维分配：

```java
UiNode transformed = Ui.group(content).key("preview")
        .pivot(new UiPivot(.5, .5, 0))
        .transform(UiTransform.IDENTITY.translated(0, 0, 8).rotated(-12, 6, 4).scaled(.9))
        .build();
```

位移为局部右、上、朝向阅读者的 UI 单位；旋转为 yaw、pitch、roll 角度。先统一缩放、再旋转、最后位移，层级先应用子级；`UiRotation` 使用单位四元数。`UiPivot` 前两项相对于矩形，第三项为深度 UI。累计缩放范围 1/64–64，绘制包络在 8192 UI 内。

## 平面图标

```java
Ui.button(UiIcons.CROSS, clearLabel).key("clear").primary(clearSearch).build();
Ui.icon(UiIcons.ACTIVITY_BARS, playingLabel).key("status").build();
```

标签用于语义和 Tooltip，不影响图形占位。图形、尺寸、帧预算及扩充规则集中在[图标规范](spatial-menu-icons.md)。

## 图片资源

```java
UiImage cover = UiImage.remote("https://cdn.example.org/cover.png", 1024, 1024);
UiNode image = Ui.image(cover, coverLabel).key("cover")
        .size(new UiSize(64, 64)).style(UiStyle.PLAIN.withContentAlpha(.9)).build();
```

每组件是一张发送给当前玩家的临时 Axiom image annotation；需要支持该通道且开启注解显示的客户端，无需 Axiom 服务端插件。不支持时显示替代文字；下载失败由 Axiom 显示检查格，服务器不知道下载结果。

URL 为客户端可访问的 HTTP(S) ASCII 地址，最长 8192 字节，不支持本地路径或 `data:`。声明原图宽高须与实际资源一致。图片在占位内等比居中；协议不提供覆盖裁剪、UV 或非等比拉伸，资源方需预先裁剪。原图 alpha 与 `contentAlpha` 相乘，背景独立。

稳定 UUID 支持移动、旋转差量更新。URL、宽度或透明度变化会重建注解和纹理，避免逐帧缩放或淡入；单精度坐标在远离原点处可能与实体文字有偏差。每包最多 32,000 字节，发送失败保留待同步状态并重试。注解按所有者在移除、挂起、关闭、换世界、退出或停用时清理，不清空世界注解或写入存档。图片动作通过组合语义控件提供。

## 实时范围与进度

`UiRange.live(min, max, supplier)` 或 `fixed(min, max, value)` 声明有限且递增的边界；当前值钳制在范围内。`step` 为滚轮正步长，`format` 返回数值与单位，`withLabels()` 在轨道两侧显示当前值与上限。中间值须能放入按端点格式预留的宽度。

```java
UiRange range = UiRange.live(0, 100, model::level).step(5).format(this::formatLevel);
UiNode progress = Ui.progress(label, range).key("progress").build();
UiNode slider = Ui.slider(label, range, changeLevel).key("slider").build();
```

供值是主线程轻量内存读取。实时数值只更新绘制；点击按不含标签的轨道比例设绝对值，滚轮按步长增减，`step` 不量化点击。指向轨道显示目标值，`.secondary(...)` 可增加精确输入。权限、整数取整和异步保存合并归功能。

### 滑条与精确数值

`Ui.numberField(label, range, change)` 组合滑条和紧凑数值框，共享范围与格式。`.edit(command)` 打开宿主精确输入，未声明时数值框只接受滚轮；`.disabled(reason)` 同时禁用两处。显式键也标识数值入口，可作浮窗来源。

默认横排，窄处数值框整组换行；标签由外层 `Ui.form()` 或标题提供。数值框随宿主重绘更新，轨道继续采样。组件不创建输入会话或保存队列，功能须保留最新调整并反馈实际保存结果。

## 提示、工具条与动态偏好

玩家偏好变更先重测当前页面，再采用新的显示参数；重测失败时保留上一份完整布局。运行时按实体比较有效缩放、字号、旋转和主题，逻辑背景尺寸不变时也会同步新的父级缩放。调整菜单大小或字号、避障收缩与恢复均无需重新打开菜单；世界锚定标记保留自己的单位。

`.tooltip(Component)` 默认稳定指向 10 tick 后显示；`UiTooltip` 可配置 80–240 像素换行宽度与延迟。说明放在准星旁的只读浮框，优先显示完整源文字，不设固定八行或字符数上限；只有视野实际放不下时才拟合。相同标签和已可见原因被过滤，超长正文通过详情页继续阅读。

提示在光标周围及障碍边缘进行有界避让，覆盖尽量少，必要时前移并保持视觉字号；不移动父菜单。显示后保持来源锚点，几何变化才校验，范围值预览随指向更新。准星有 4 tick 间隙可移入阅读；左击关闭，其他输入不执行后方操作。离开、来源失效、页面切换或原生输入清理提示。

`taskToolbar()` 启用任务所属侧边工具条：小幅转头保留死区，普通移动阻尼跟随，快速转头或触达时保持，离开后恢复；仍须显式点击。`FloatingMenuAnimation` 提供进入退出、指向按压和有限装饰。按压反馈按会话时钟到期，同一 tick 重绘不消耗持续时间，遮挡期间继续计时，关闭或来源失效时清理。关闭途中及普通布局更新从当前值衔接最新目标，同目标刷新不重启动画；减弱动态即时采用稳定姿态。

同页移除内容的绘制副本最多保留八 tick、128 个显示实体，与当前页面共用实体预算；旧动作与点击实体立即撤销。障碍靠近、预算不足、关闭、暂停或开启减弱动态时提前清理，不延长面板及输入资源的生命周期。

### 共享元素导航

通过 `MenuScene.Builder.sharedElement(对象身份, 节点键)` 声明跨页配对。对象身份包含业务领域和实际对象标识；节点键指向当前页内唯一的可见控件，标签与排序不建立配对。例如：

```java
UiKey object = UiKey.named("plot:" + plotId);
directory.sharedElement(object, UiKey.named("plot:" + plotId));
details.sharedElement(object, UiKey.named("heading"));
```

导航从上一页面已发布的姿态衔接当前目标，快速返回重新取当前姿态；旧页面的动作、输入与实体不转交。一次最多配对八个主页面的文字、物品或方块控件；节点有歧义、类型不兼容、来源未发布、处于加载／减弱动态或路径不安全时使用普通页面更新。图标、范围控件、图片与浮层内容使用普通过渡。空间检查和资源归属见[菜单架构](spatial-menu-design.md#命中包络与共享元素)。

## 世界标记与界面遮挡

`worldTextDecoration` 的 `seeThrough` 保留世界穿墙行为，但与菜单、Popover 或 Tooltip 投影重叠时整条隐藏，移开后恢复。透明面板也保留阅读区域。

粒子每次更新可重新取得 `FloatingMenus.worldOverlayVisibility(player)`，用其 `Predicate<Location>` 过滤被界面遮盖的位置。快照不查方块，已发送粒子由原版生命周期消退。

## 输入、返回与异步回调

指针能力由 `UiInput.pointerBehavior()`／`MenuScene.Entry.pointerBehavior()` 根据当前绑定推导：无动作和说明时为 `NONE`，只读提示、焦点观察或禁用动作为 `INSPECT`，已绑定且可用的动作为 `OPERATE`。只有 `OPERATE` 获得操作高亮、悬浮音、指向／按压位移与点击辅助实体。`tooltip()`、`singleLine()` 和省略文字的完整内容提示保持只读；按钮角色和外观本身不授予操作能力。`interactive()` 只表达布局与组合所需的控件语义。

`openRoot` 建立新根，`open` 进入子页，`present` 在同 `screenId` 更新或返回已有层级；`handle.back()` 返回，`close()` 关闭。输入回调中的更新消费本次剩余数据包，后台刷新不阻断新的输入。

### 保留父页面的详情浮层

```java
var source = UiKey.named("selected-person");
MenuPopover details = MenuPopover.attached(source, personName, personDetails, closeLabel, dismissed);
MenuScene scene = MenuScene.screen("directory").content(directory).popover(details).build();
// 或由宿主托管面板实例：
handle.openPopover(source, redraw -> governance.identityPanel(player, redraw));
handle.closePopover();
```

来源须是父页中唯一可见元素的 `UiKey`。每场景一个 Popover，来源离开可见页时不发布；正文、标题、关闭标签归功能，宿主生成窗口和关闭。`.withActions(tools)` 增加局部操作，`.exclusive()` 锁住背景用于确认。浮层独立测量，不扩大父页包络，阅读期间跟随来源。

普通详情允许操作未覆盖的父控件：先关闭详情，再执行一次；点来源只关闭，点外部空白关闭并消费输入，浮层空白不穿透。`back()`、`close()` 优先关闭浮层，进入子页先清理它；不设超时或移开视线自动关闭。

托管 `MenuPanel` 从 `render()` 取标题和正文，`headerActions()` 提供局部工具，打开时 `attach(handle)`。父页重绘保留实例，来源消失、替换或结束时 `close()`；旧重绘与 `closePopover(expected)` 不能影响替换后的浮窗。先测父页并确定实际阅读参数，再测浮层，宽窄页切换和世界避障不各自推算比例；浮层布局失败不替换原阅读基准。附着后的完整浮层参与地形和附近实体检查，沿视线同步收近与缩放以保持阅读角度，同次放置复用父页方块缓存和采样预算。没有合法位置时暂停整页显示与输入，保留导航、草稿和业务任务，后续复核恢复。父页拥有刷新与导航，不复制面板独立装饰或场景生命周期。需要完整页面能力的任务进入子页。

### 表单与字段

`FloatingMenus.input(player, MenuInputForm)` 托管保存类草稿，字段声明初值、码点上限、单行／多行和预览函数。点击预览进入 Dialog 编辑完整原文；「完成」只更新草稿，最终确认才保存一次。取消或 Escape 保留进入编辑前的值。超限和非法换行带候选与错误重开，正式草稿保持有效值；CRLF/CR 统一为 LF，保留空格、空行和尾部换行。

```java
Ui.form().key("playlist")
    .field("name", nameLabel, Ui.textField(name)
        .placeholder(emptyLabel).primary(editName).build())
    .field("description", descriptionLabel, Ui.textField(description).preview(renderDescription)
        .maxLines(4).primary(editDescription).build())
    .feedback(status).actions(buttons).build();
```

`Ui.form()` 共享标签列，窄处将标签移到控件上方；`field(key, control)` 省略已有标题说明的标签。底栏提示靠左、动作靠右，均可省略。`Ui.textField(String)` 接收原文快照，默认按字面显示；可用 `.preview(Function<String, Component>)` 提供富文本效果。占位只依据原文是否为空，非空原文即使预览没有字形也保留其真实状态；空原文直接使用占位，不调用预览函数。尾部 `actions(node)` 放清空等局部操作；默认宽度 240 UI、最小 160 UI，仍受宿主约束。

`editing(...)` 只呈现宿主提供的编辑态，点击后的输入会话仍由宿主开启和结束，原值、草稿和保存规则也由宿主管理。`disabled(reason)` 禁用值的编辑入口，复制、清空等辅助操作由宿主分别设置可用性。

较长表单可用 `.viewedField(fieldKey, chooseField)` 声明字段视口，由宿主保存当前浏览的字段键。`.navigationAxis(UiLayouts.Axis.VERTICAL)` 让字段目录优先放在当前控件上方；默认 `HORIZONTAL` 优先并排，两者复用 `split` 的完整换向规则。导航方向不决定选项集合如何排版：长文字设置使用 `Ui.pagedList()`，短数值可使用 `Ui.pagedFlow(rows)`；`rows` 是最多占用的行数，流式集合会利用横向空间。多字段目录必须提供可见标签。目录按实际行高有界分页，只发布当前目录窗口与一个完整控件；当前控件、标签、字段目录和表单级提示／操作共同使用宿主分配的有限空间。控件优先使用类型化内容；旧声明位只为当前字段提供数据。字段键消失时宿主须重新选择有效键，组件不会将旧控件动作换绑到相邻字段。

共享 `MenuInputForm` 采用字段视口：目录只请求浏览，不打开 Dialog，也不修改值；点击当前预览才获得该字段的输入会话。浏览字段与正在编辑的字段分别记录，切换浏览结束已有 Dialog，保留全部已接受草稿。清空回调捕获明确字段，确认和取消始终位于当前控件之外。短标题完整显示，超长标题按实际宽度省略并保留完整提示。

`inlineInput(player, form, changed, finished)` 返回 `MenuFormSession`，用 `content()` 嵌入现有阅读面；`focus(fieldKey)` 打开字段编辑，`changed` 重绘，`finished` 移除表单。宿主离开、暂停或替换时 `close()`，拒绝迟到响应。浮窗关闭与取消等价时，可用 `content(MenuFormSession.Actions.SUBMIT)` 只保留提交；字段叉号始终只清空该字段。

原生输入、确认和外链由 `MenuDialogs` 管理；复杂 Dialog 用 `begin(plugin, player)` 取得 `MenuDialogSession`，按钮通过会话 `action` 注册并单次消费，取消和 Escape 也需退出动作。服务端仍须复核业务权限与版本。

### 搜索

```java
MenuQuerySession search = FloatingMenus.queryInput(player, placeholder, 128,
        value -> context.update(state -> state.search(value)), context::redraw);
UiNode searchBar = search.content(context.state().query());
```

会话只维护编辑和校验，关键词由功能保存。查询栏在正文中使用 `LinearSlot.fit()` 居中，框内文字左对齐，尾部操作优先同排。空时显示占位，有值只显示关键词；长值省略后提供全文提示，错误说明跟随控件宽度。

点击后接收首条聊天提交，去除首尾空白后更新查询并结束捕获；随后消息恢复普通聊天。编辑中叉号取消并保留旧查询，提交后仅有清除，空白未编辑时无叉号。非法输入同样结束接收并保留原查询，等待提交没有超时；取消、清除或关闭丢弃尚未交付的查询。

独立对象查找用 `FloatingMenus.search(player, MenuSearch<T>)`，查询与结果同页，单条精确匹配也须点击进入：

```java
FloatingMenus.search(player, MenuSearch.<Entry>builder(title, placeholder)
        .find(query -> findEntries(query)) // CompletionStage<List<Entry>>
        .key(entry -> UiKey.item(entry.id())).label(Entry::label)
        .selected((viewer, entry) -> openDetail(viewer, entry.id())).build());
```

默认纵向分页，`.flowResults(rows, maximumWidth)` 使用横向卡片。最多展示 256 项，超出时提示缩小范围，重搜仍查询完整数据源。首次请求使用整页加载，后续请求移除旧候选并显示局部状态；旧请求不能覆盖新结果，返回保留查询和页码。

数据源在主线程发起，先取 Bukkit 快照，再将文件或仓库工作交给功能后台执行器。`MenuPlayerSearch.builder(...)` 复用头像、姓名、UUID 和名称匹配；`cached(viewer, query)` 提供可见在线及精确缓存候选，`matching(...)` 可合并功能自己的离线目录。权限与选中复核归功能。

### 异步与清理

定制即时预览使用 `beginChatInput(player, onText, onEnded)` 返回的 `MenuInputLease`，在主线程接收最新候选；同页预览重绘不结束接收。`consumeChatInput(UUID, String)` 由装配接到聊天入口，在频道、延迟发送和外部转发前消费。

查询是单次提交，即时预览持续接收；两者及 Dialog 均不设超时。所属输入在确定、取消、离开页面、关闭、换世界、退出或停用时释放，尚待交付的旧消息丢弃，不进入新会话。空间菜单不能可靠获知客户端未报告的全部前台界面。

## 预算

| 项目 | 上限 |
| --- | --- |
| 树深度／节点 | 64／4096 |
| 单次编译测量 | 16384 |
| 网格占用单元 | 65536 |
| 单场景展示与交互实体 | 2048，包含图标与提示等展开成本 |
| 等高长集合窗口 | 每次测量最多创建 256 行，无预取；行模板不发布 |
| 单份文档 | 256 块、65,536 个纯文字码点、2,048 个切分片段；页脚协调最多 8 轮 |
| 单次放置的方块采样缓存 | 32768 个方格，包含空方格；各候选、阅读面、布局运动及附着浮层共用 |
| 局部布局运动包络 | 至多 128 个体积单元；超出时直接采用已验证终态 |
| 避障射线长度／附近普通实体查询半径 | 至多 64 方块；展示模型另按实际变换范围过滤 |
| 每视图文字测量缓存 | 至多 2048 项、131072 权重单位；权重包含文本、字体键及节点开销 |

父页与浮层共用实际实体计数，分别计算文字、模型、控件表面、装饰及可操作节点的点击辅助实体；只读提示不预留点击实体。结构或实体超预算在创建实体前报告，功能通过分页、分组或替代布局缩减当前内容。遇到未加载区块、采样预算不足或没有可用空间时暂停显示与命中，保留会话并在空间恢复后重测；检查只访问已加载区块。
