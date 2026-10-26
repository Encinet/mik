# Fluent 翻译维护

玩家可见文本由 `src/main/resources/lang/*.ftl` 维护，运行时入口是 `shared/i18n/LanguageService`。支持的语言以该目录为准，公开消息清单由 `Message` 枚举维护。所有文件和 `Message` 枚举按相同的功能分区与键顺序排列，对应共享能力、玩家、社区、世界、娱乐、服务器和入口层。普通消息和属性模板都注册在枚举中，调用方统一使用 `Message`，不传字符串消息 ID。

FTL 文件是维护源文件，可以直接编辑。语法形式由各语言决定：中文、日文、韩文、泰文使用合适的量词和语序；需要复数、格或配合变化的语言使用选择表达式。各语言不必拥有相同的分支数量。

## 命名与删除

消息键描述功能和用途，数量变化放在消息内部。隐身提醒统一使用 `invisibility-nearby-mm`，返回结果统一使用 `back-success-mm`；Java 传入数量，由 Fluent 选择形式。修改名称时同步枚举、全部语言、调用方与测试，并直接删除旧键。移除功能后删除无调用的消息，不保留旧名别名或兼容转发。

提醒使用明确的语义名称：`client-version-behind-mm` 接收 `$clientVersion` 和 `$serverVersion`，`client-mods-undetected-rich` 接收 `$mods`。返回历史使用 `$steps`、`$remaining`、`$max` 表达已消耗数量、剩余数量和上限。

`-mm` 标识需要 MiniMessage 解析的文本，`-rich` 标识需要组件参数的消息。二者说明当前渲染要求。可复用的内容使用术语、消息引用或属性；这些引用应服务于当前功能，不用于保留已替换的名字。

## 参数类型与调用方式

| 用途 | Java 入口 | FTL 参数 |
| --- | --- | --- |
| 普通文本和位置参数 | `t(language, message, count)` | `$arg0`、`$arg1` 等 |
| 具名文本或数值 | `format(language, message, TextArg.of("count", count))` | `$count` 等 |
| 保留颜色、悬停或点击事件的组件 | `rich(..., RichArg.component(...))` | 具名组件参数 |
| 保留组件样式并参与数值选择 | `rich(..., RichArg.number("count", component, count))` | `$count` 为显示组件，`$countValue` 为数值 |
| 与组件同时传递语义条件 | `rich(language, message, color, TextArg[] selectors, RichArg... values)` | 例如 `$person` |

计数直接传 `int`、`long`、`BigInteger`、`BigDecimal` 等数值。`FluentArguments` 保留其数值类型，Fluent 据此选择复数分支并按目标语言格式化。普通参数只绑定其指定名字；控制台共用富文本计数消息时，显式传入 `$count` 和 `$countValue`，不自动生成参数别名。

不要先调用 `Integer.toString`、`Long.toString`、`String.format` 或 `NumberFormat` 再传给计数消息。已经格式化的文本无法可靠参与数值选择。需要先按产品规则四舍五入时，传入舍入后的数值；治理时长保留最多一位小数，并移除无意义的末尾零。

字符串始终作为字符串处理，即使内容是 `"21"`。玩家名、平台名、模型 ID、命令和链接不靠内容推断类型，也不由翻译系统修改。审计记录中的奖品数量先按计数字段解析为数值，再传给翻译。

## 复数与整句配合

Fluent 按目标语言的 CLDR 规则选择 `one`、`few`、`many`、`other` 等类别。`one` 不等于数值恰好为 1；例如法语的 0 秒使用 `seconde`。每个选择表达式都要保留带 `*` 的默认分支。[Fluent 选择表达式](https://projectfluent.org/fluent/guide/selectors.html)、[CLDR 复数规则](https://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html)解释了分类与匹配规则。

俄语时长示例：

```fluent
governance-days = { $arg0 ->
    [one] { $arg0 } день
    [few] { $arg0 } дня
    [many] { $arg0 } дней
   *[other] { $arg0 } дня
}
```

这分别覆盖 1／21 天、2／22 天、0／5／11 天和小数。英语使用 `one/other`；没有此类复数变化的语言直接写数值和单位。

名词变化会连带影响形容词、分词或动词时，把相互配合的部分放进同一个分支。法语文案中的 `1 requête a échoué` 与 `2 requêtes ont échoué` 同时改变名词和动词，世界更新中的 `bloc modifié / blocs modifiés` 同时改变名词与分词。德语介词后的 `vor 1 Tag / vor 2 Tagen`、`von 1 Lied / von 2 Liedern` 另按句子所需的格书写。不要把英语的 `(s)` 或其他括号词尾翻译到目标语言。

韩语使用已知名词、量词或自然的标签句式确定助词，避免把 `을(를)`、`이(가)` 等候选形式直接展示给玩家。

## 私有术语、格与语法性别

以 `-` 开头的术语供 FTL 内部复用。`-plot` 与 `-vehicle-model` 保存名词及必要的语法属性；Java 不选择词尾，也不拼接名词。

乌克兰语中的宾格与属格由句子明确指定：

```fluent
plot-menu-create = Створити { -plot(case: "accusative") }
plot-role-owner = власник { -plot(case: "genitive") }
```

术语通过 `$case` 选择词形，默认主格。命名术语参数在引用处使用静态字面量，如 `case: "genitive"`。这是句法关系，不应从玩家输入或业务对象中猜测。[Fluent 术语文档](https://projectfluent.org/fluent/guide/terms.html)说明了参数化引用方式。

名词的 `.gender` 属性用于分词配合，例如俄语 `Модель сохранена` 和法语 `Parcelle … créée`。这里的性别是名词的语法属性。玩家身份没有可靠的语法性别信息，因此玩家广播采用中性句式，不按名字推断性别或强行对名字变格。

## 人称与属性列表

自踢消息接收 `$person`：`second` 面向执行者本人，`third` 面向其他玩家和控制台，默认第三人称。执行者看到直接称呼自己的基础消息，其他接收者看到包含玩家名的随机属性模板。这样人称取决于接收者，而不是整个服务器共用一句文本。

AFK、进退服彩蛋和自踢随机模板使用 `.item-*` 属性。各语言保留相同的属性名和必需参数；选择属性的代码只负责抽取与分发。普通共用文案使用消息引用，例如 AI 知识同步状态复用状态消息，帮助页复用操作提示。不要把完整句子拆成大量短片段后在 Java 中拼接。[Fluent 属性文档](https://projectfluent.org/fluent/guide/attributes.html)说明了属性用途。

## 富文本与 MiniMessage

富文本中的显示组件和语法条件分别传递：

```fluent
removeitems-done-rich = Removed { $countValue ->
    [one] { $count } dropped item
   *[other] { $count } dropped items
} in world { $world } { $location } { $radius }
```

`RichArg.number` 保留原始组件的颜色和事件，以数值副本选择单复数。用户输入通过组件标记替换原样插入，不再作为 Fluent 或 MiniMessage 解析，包含 `{ $name }` 或 `<red>` 的玩家文本也保持字面内容。

已有 `*-mm` 消息由调用方在 Fluent 格式化后交给 MiniMessage。数值条件在 Fluent 阶段处理，例如隐身提醒的玩家数与距离、返回历史的次数。动态用户内容继续使用组件或 MiniMessage 的安全占位符。修改这类消息时同时检查对应调用方，避免遗漏原有占位符、颜色或点击操作。

[场景提示](guidance.md#多语言)的普通文案通过 `define(id, Message)` 声明，消息使用 `-mm`，服务在发送前按接收者语言解析 MiniMessage。未指定颜色时回退为白色，模块可按内容自行选择明亮配色。原版物品可组合 `<lang:翻译键>` 和 `<hover:show_item:'物品ID'>`，显示客户端原生名称与物品悬浮框；同一功能重复使用时可提取为 FTL 术语。提示的长度、颜色和交互遵循[文案与呈现规范](guidance.md#文案与呈现)。

含动态参数时，模块的 `resolve(player, context, language)` 使用传入的 `Language` 调用以上入口，返回本地化的 `TipContent`。服务统一选定语言，使标签和正文保持一致，排队期间的语言切换也能生效；原版翻译组件由客户端按自身语言显示。情况标识与翻译文本独立，避免切换语言重置冷却。

## 检查与验证

在项目根目录运行：

```sh
python3 tools/i18n/audit.py
./gradlew build
```

静态审计无需第三方 Python 依赖，检查语言文件清单、重复键、分区顺序、枚举与资源对齐、生产调用、私有术语、属性、引用、参数，以及整条复制的多词英文文案和括号词尾。命令字、专有名词、单位符号及法语、荷兰语中确实相同的词有明确处理。它负责清单检查，Fluent 的真实语法与求值由 Java 测试验证。

`LanguageResourcesTest` 使用运行时相同的解析器加载所有语言，检查全部公开消息、属性及显示参数。`FluentGrammarTest` 覆盖 0、1、2、5、11、21、101 和 1.5，检查所有数值选择消息，并验证格、语法性别、人称、字符串类型及富文本组件事件。自踢资源测试另检查随机属性池。新增分支需要把触发条件补入测试；新增参数需要同步实际调用方与所有语言。

自动检查能发现语法、引用和参数退化；措辞是否自然、界面是否溢出仍需结合具体语言和 Java／基岩客户端检查。
