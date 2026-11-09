### MIK · lzh
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = 地段
    .gender = other

-vehicle-model = 車模
    .gender = other

## Shared · General

back = 返
back-to-main = 還主簿
cancel = 罷
click-open = 擊以啟之
close = 閉
current-off = 今閉
current-on = 今啟
player-only = 此令惟戲者得用
unknown = 未明
usage = 令式

## Shared · Language, roles and time

language-command-description = 啟言文置設
language-menu-title = 言文置設
language-selected = 已擇
language-set = 言文既易爲 { $arg0 }
role-custodian = 維護者
role-member = 正員
role-moderator = 管理
role-new-player = 初來者
time-hours-minutes = { $arg0 } 時 { $arg1 } 分
time-minutes = { $arg0 } 分

## Shared · Menu presentation

interface-fov-apply = 施用 FOV
interface-fov-custom = 自定
interface-fov-description = 依遊戲所設填 FOV，以合選單之位
interface-fov-input = 客端 FOV：30 至 110 之整數
interface-fov-invalid = 請輸入 30 至 110 之整數。
interface-fov-title = 視野（FOV）
interface-reduced-motion = 減動效
interface-reduced-motion-hint = 減介面位移與搖動
interface-scale-adjust-hint = 指於比例項，滾輪以調
interface-scale-layout-title = 菜單尺度
interface-scale-menu-title = 介面與文字
interface-scale-preview = 例 Aa 123
interface-scale-reset = 復為 100%
interface-scale-text-title = 文字尺度
interface-theme-apply = 存配色
interface-theme-charcoal = 炭灰
interface-theme-color = 顏色（#RRGGBB）
interface-theme-custom = 自定
interface-theme-custom-hint = 取今主題諸色，另存為自定主題
interface-theme-edit = 編配色
interface-theme-forest = 林綠
interface-theme-hint = 擇之即效，唯易己之選單。
interface-theme-invalid = 請填 #RRGGBB 色碼及零至百之不透明度
interface-theme-opacity = 不透明度（0–100%）
interface-theme-role-accent = 強調色
interface-theme-role-button = 鈕底
interface-theme-role-button-disabled = 禁用之鈕
interface-theme-role-button-hovered = 指向之鈕
interface-theme-role-button-pressed = 按下之鈕
interface-theme-role-button-selected = 選中之鈕
interface-theme-role-input = 輸入底
interface-theme-role-sidebar = 側欄底
interface-theme-role-toolbar = 工具列底
interface-theme-role-tooltip = 提示底
interface-theme-role-window = 視窗底
interface-theme-title = 選單主題
interface-theme-warm = 暖褐
menu-collapse = 收
menu-expand = 展
menu-finish-editing = 請先畢其輸入。
menu-input-clear = 清空
menu-input-done = 畢
menu-input-empty = （空）
menu-input-single-line = 此欄僅容一行。
menu-input-too-long = 字數 { $arg0 } / { $arg1 }，刪短而後確認。
menu-query-cancel = 罷輸
menu-query-hint-rich = 按 { $chatKey } 以書…
menu-query-single-line = 限一行，點框重書。
menu-query-too-long = 字數 { $arg0 } / { $arg1 }，點框重書。
menu-rhythm-game-display = 節奏遊戲中
menu-search-empty = 無相合者
menu-search-failed = 檢索未成
menu-search-limit = 示 { $arg0 } / { $arg1 }，請細其索。
menu-search-loading = 檢索中…
menu-search-retry = 重試
menu-usage-display-mm = <aqua><bold>選單中</bold></aqua>
url-dialog-confirm = 啟之
url-dialog-hint = 確認後，客端仍須再允開連結。
url-dialog-question-rich = 確欲啟 { $label } 乎
url-dialog-title = 啟 { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = 薦用模組：{ $mods }，點其名可下載
client-tip-axiom-flight-speed-mm = <white>按住 <yellow>左 Alt</yellow> 啟 <aqua>Axiom</aqua> 選單，拖右側滑桿以調飛行之速；亦可用 <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click></white>
client-tip-axiom-intro-rich = { $mod } 可複製建築、修地形、編展示實體，點其名可下載
client-version-behind-mm = <white>客端 <yellow>{ $clientVersion }</yellow> 舊於伺服端 <green>{ $serverVersion }</green>，宜更新</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>近有 <gold>{ $count }</gold> 戲者 <gold>隱身</gold></yellow> <gray>·</gray> <gray>至近約去 { $distance } 方</gray>
player-bedrock-label = 基岩
player-web-label = 網頁
presence-join-templates =
    .item-1 = 叮咚，{ $player } 入服矣！
    .item-2 = 喜哉，{ $player } 至矣！
    .item-3 = 迎 { $player }，幸會幸會！
# Broadcast to remaining players; the departing player does not receive this message.
presence-quit-templates =
    .item-1 = { $player } 離服矣。
    .item-2 = 叮咚，{ $player } 已離服
    .item-3 = 在服減一人，{ $player } 離服矣
welcome-first-join-mm = <gold><bold>有朋自遠方來，不亦樂乎！</bold></gold><newline><gray>宜先覽 <click:open_url:'https://mcmik.top'><yellow>官網</yellow></click> 與 <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>；亦可行 <yellow>/menu</yellow> 以啟主簿。</gray>
welcome-return-mm = <gold><bold>歸去來兮！</bold></gold><newline><gray>君去此已 <yellow>{ $arg0 } 日</yellow>。行 <yellow>/menu</yellow> 可覽常用諸事。</gray>

## Player · Guidance

tip-label = 箴

## Player · AFK

afk-already-afk-mm = <gray>君已在<gold>暫離</gold>狀態</gray>
afk-command-description = 入暫離之狀
afk-default-statuses =
    .item-1 = 暫離鍵側
    .item-2 = 入省力之法
    .item-3 = 暫隱於後臺
    .item-4 = 神遊物外
afk-display-title-mm = <gold><bold>暫離</bold></gold>
afk-display-with-message-mm = <gold><bold>暫離</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>暫離曰：</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>留牘曰：</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>易其狀曰：</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>既歸鍵側</green>
    .item-2 = <gray>*</gray> <player> <green>既還</green>
afk-not-afk-mm = <gray>君今未 <gold>暫離</gold></gray>
afk-status-too-long-mm = <red>暫離之狀至多 <white>{ $arg0 }</white> 字</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · 相合 { $arg2 } 項 · 彼錄於 { $arg3 }
player-association-header = [入服關聯] { $arg0 } 與舊錄中 { $arg1 } 人之址相合
player-association-hover = 此惟言舊網錄相合，以供參酌；不示其址。
player-association-more-addresses = 另 { $arg0 } 則
player-association-more-players = 另 { $arg0 } 人
player-association-time-days = { $arg0 } 日前
player-association-time-occurrences = { $arg0 }（{ $arg1 } 項）
player-association-time-separator = 、
player-association-time-today = 今日


## Player · Account security
password-already-connected = 此號已入服。
password-already-set = 密碼已設，欲改請用 /password change。
password-busy = 屢試過密，請稍候。
password-change = 易密碼
password-change-done = 密碼已改。
password-checking = 正查帳號安防……
password-command-description = 掌帳號密碼
password-confirm = 再書新密碼
password-conflict = 帳號安防已易，請重試。
password-cooldown = 驗證暫緩，俟 { $arg0 } 秒再試。
password-current = 舊密碼
password-denied = 無權重置此號密碼
password-disabled = 密碼：未設
password-enabled = 密碼：已設
password-invalid = 密碼有誤，請再試。
password-keep-role = 管理與維護者不得除密
password-login = 驗密入服
password-login-expired = 登入驗證已失效，請重連。
password-login-hint = 請書本服密碼。遺忘者請管理重置。
password-mismatch = 兩書新密碼不合。
password-new = 新密碼
password-nomination-required = 欲申請管理，先設密碼：/password set
password-not-set = 尚未設密，可用 /password set。
password-remove = 除密碼
password-remove-done = 密碼已除。
password-remove-question = 除密後，登入不再驗密。請書現用密碼以確認。
password-required-application = 管理申請或任命未竟，暫不得除密
password-required-role = 管理與維護者之號須設密碼，請有權者重置後入服。
password-reset = 重置密碼
password-reset-console = 已重置 { $arg0 } 之密碼：{ $arg1 }。核實本人後，由可信之途交付。
password-reset-done = 已重置 { $arg0 } 之密碼。請即複製，核實本人後交付；關閉則不可復閱。
password-reset-question = 重置 { $arg0 } 之密碼乎？舊密將廢，其連線將斷。請書己密以確認。
password-retry = 再試
password-rules = 密碼須 { $arg0 } 至 { $arg1 } 字，勿用玩家名與常見弱密。
password-set = 設密碼
password-set-done = 密碼已設。
password-set-first = 先設密碼再申請
password-storage = 帳號安防暫不可用，請稍候或詢服主
password-timeout = 驗密已止或逾時，請重連再試。
password-tip-set-mm = 以 <gold><u>/password</u></gold> 設<yellow>登入密碼</yellow>，護己帳號
password-title = 帳號安防

## Player · Profile and tab list

nametag-cancel = 取消
nametag-clear = 清除
nametag-clear-hint = 除自設之飾，承繼之飾或仍顯
nametag-command-description = 自定名籤
nametag-confirm = 確定
nametag-conflict = 今值已變。請比對現貌，再按確定以易之。
nametag-copy-hint = 傳前後綴原文於私訊
nametag-copy-label = [鈔]
nametag-copy-sent = 已傳 { $arg0 } 之式原文於私訊。
nametag-current = 現貌
nametag-edit-ended = 編輯既止，欲續請再點其欄。
nametag-edit-prompt = 按 T 以 MiniMessage 編{ $arg0 }，惟己可見。
nametag-finish-field = 請先確定或取消此項
nametag-input-invalid = 格式有誤，請傳單行 MiniMessage，或按清除
nametag-leading-label = 名籤之首綴
nametag-mine = 吾之名牌
nametag-next = 次頁
nametag-no-changes = 內容未變
nametag-no-permission = 正式成員以上可改名牌；仍可觀覽樣式
nametag-no-players = 尚無玩家設名牌式樣
nametag-page = 第 { $arg0 } / { $arg1 } 頁
nametag-players = 玩家名式
nametag-preview-label = 預覽
nametag-previous = 前頁
nametag-refresh = 重載
nametag-saving = 正儲存…
nametag-set-error = 儲存未成：{ $arg0 }。請重試；上方為現貌。
nametag-set-success = ✔ { $arg0 } 既設
nametag-styles-failed = 玩家式樣未能載入，請重試。
nametag-styles-loading = 正載玩家式樣…
nametag-tip-customize-mm = 以 <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> 自訂<light_purple>名前後綴</light_purple>
nametag-title = 名籤
nametag-too-long = { $arg0 } 過長，至多 { $arg1 } 字
nametag-trailing-label = 名籤之尾綴
nametag-unset = （未設）
nametag-unsupported-placeholder-rich = 惟納 { $supported } 類之 PlaceholderAPI 變元，不納 { $unsupported }
nametag-user-data-load-failed = 載君之錄未成，請稍待復試
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = 在綫戲者：{ $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = 還前次移形或殞命之處
back-empty-mm = <gray>無可還之處</gray>
back-pending-mm = <gray>方移形，少待</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>既連還 { $steps } 次</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>移形未成，請復試</red>
back-tip-return-mm = 以 <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> 返上次傳送或身故前之處
back-tip-undo-mm = 以 <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> 撤前次 <yellow>/back</yellow>
back-undo-empty-mm = <gray>無 <white>/back</white> 可反</gray>
back-undo-success-mm = <green>既反前次 <white>/back</white></green>
back-undo-world-missing-mm = <red>所向之生界不可用，弗能反之</red>
back-world-missing-mm = <red>所向之生界不可用，弗能還</red>
boundary-near-mm = <yellow>君將近世疆</yellow>
boundary-reached-mm = <red>君既至世疆</red>
fly-command-description = 開閉己身翔行
fly-disabled = 翔行既閉
fly-enabled = 翔行既啟
fly-native-mode = /fly 之權既除；今之戲式猶可翔行。
fly-speed-click = 定翔行之速為 { $arg0 }x
fly-speed-current = 今翔行之速：{ $arg0 }x
fly-speed-limited = 開圖負重，飛行暫限 { $arg0 }x；原設仍為 { $arg1 }x。
fly-speed-presets = 速檔
fly-speed-reset = 翔行之速既復為 1x
fly-speed-reset-action = 復原
fly-speed-reset-click = 復翔行之速為 1x
fly-speed-set = 翔行之速既定為 { $arg0 }x
fly-state-disabled = 已閉
fly-state-enabled = 已啟
fly-status = 翔行：{ $arg0 } | 速：{ $arg1 }x
home-action-create = 設家
home-action-delete = 刪家
home-action-teleport = 傳送
home-action-update = 更新位置
home-already-exists = 已有同名之家
home-back = 罷
home-confirm-delete = 確除之乎
home-confirm-delete-detail-rich = 將除歸處 { $home }
home-confirm-delete-warning = 覆水難收，一除不可復得
home-confirm-update = 確遷之乎
home-confirm-update-detail-rich = 將歸處 { $home } 遷於此地
home-delete-command-description = 除一歸處
home-delete-usage-desc = 除一歸處
home-deleted-rich = 歸處 { $home } 既除
home-distance = 相去：{ $arg0 }
home-distance-blocks = { $arg0 } 方
home-distance-other-world = 異界
home-empty-description = 立於欲記之地
home-empty-title = 未有歸處
home-invalid-name = 歸處之名不可有 < > : 諸符
home-list-rich = 諸歸處：{ $homes }
home-location = 坐標：{ $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = 是歸處之方位有舛
home-max-reached = 君至多可定 { $arg0 } 歸處
home-menu-title = 吾之歸處
home-no-homes = 君未定歸處
home-not-found-rich = 無歸處 { $home }
home-search-placeholder = 尋家名…
home-search-empty = 未尋得相合之歸處
home-set-command-description = 定此地爲歸處
home-set-rich = 歸處 { $home } 既定
home-set-usage-desc = 定此地爲歸處
home-summary-count = 既定：{ $arg0 } / { $arg1 }
home-teleport-failed = 移形未成，請復試
home-teleported-rich = 既移形至歸處 { $home }
home-tip-create-mm = 以 <green><u><click:suggest_command:'/sethome '>/sethome 名</click></u></green> 存此處，再以 <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> 歸返
home-tip-return-mm = 以 <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> 開家園之目，擇家而按「{ home-action-teleport }」
home-updated-rich = 歸處 { $home } 之位既遷
home-usage-book = 歸處令式
home-usage-delhome = /delhome <名>  除歸處
home-usage-home = /home <名>  移形而往
home-usage-name-rule = 歸處之名不可有 < > :
home-usage-sethome = /sethome <名>  記此方位
home-world = 生界：{ $arg0 }
home-world-end = · 終界
home-world-nether = · 焱界
reback-command-description = 反前次 /back
teleport-accept-command-description = 許待決之移形請求
teleport-allow = 受他人移形
teleport-bare-entity-selector = 恐誤移，@e 須加方括號。請明書如 @e[type=…]。
teleport-block-afk = 暫離則拒之
teleport-denied = { $player } 今不納他人移形
teleport-deny-command-description = 拒待決之移形請求
teleport-force-command-description = 強移其形，無視所願（惟司事可用）
teleport-menu-title = 受移形之設
teleport-moved-here = { $arg0 } 既移君至此
teleport-policy-always-allow = 恆許之
teleport-policy-always-deny = 恆拒之
teleport-policy-require-consent = 俟我允之
teleport-request-accept = 許之
teleport-request-approved = { $player } 已允君之移形請求
teleport-request-deny-action = 拒之
teleport-request-expired = 移形請求已逾時
teleport-request-failed = 移形未成，請復試
teleport-request-none = 今無待決之移形請求
teleport-request-received = { $sender } 欲移君：{ $command }
teleport-request-rejected = 已拒移形請求
teleport-request-sent = 已告 { $player } 請移之意
tpany-command-description = 移形至戲者所在，不論在綫既去
tpany-done-offline-rich = 既移形至 { $player } 去服時所在 { $world } { $location }
tpany-done-online-rich = 既移形至 { $player } 今所在
tpany-not-found-rich = 尋 { $player } 之所在未得
tpany-usage = 令式
tpany-usage-command = /tpany <戲者名>
tpany-usage-desc = 移形至戲者所在，既去者取其留錄

## Player · PVP

pvp-admin-menu-title = 相搏掌簿
pvp-allow-mounted-damage = 所乘生靈可受擊
pvp-allow-mounted-damage-desc = 雙方皆啟相搏時，彼可擊君所乘之生靈
pvp-auto-enable-desc = 連擊已啟相搏之戲者二次
pvp-auto-enable-label = 擊敵自啟
pvp-auto-enable-warning-actionbar-mm = <yellow>復擊一次，君之相搏將自啟</yellow>
pvp-auto-enabled-actionbar-mm = <green>相搏既自啟</green>
pvp-combat-locked-mm = <red>方戰不可閉相搏</red> <dark_gray>|</dark_gray> <gray>餘時：<yellow>{ $arg0 }</yellow> 秒</gray>
pvp-combat-tag-label = 戰後之禁
pvp-combat-tag-value = { $arg0 } 秒
pvp-command-description = 易相搏之狀
pvp-enable = 啟相搏
pvp-enable-desc = 啟則君得與諸已啟相搏者相傷
pvp-enable-on-death = 身死則啟相搏
pvp-enable-on-death-desc = 啟此項，君身死則相搏自啟
pvp-enable-on-death-label = 身死自啟
pvp-enabled-on-death-mm = <yellow>君身死，相搏因而自啟</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>啟在綫戲者相搏掌簿</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>啟相搏置設</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin 戲者名 on|off|toggle|status|gui</aqua> <gray>掌所定之在綫戲者</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>覽己之相搏</gray>
pvp-help-title-mm = <gold><bold>相搏諸令</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>易己之相搏</gray>
pvp-menu-title = 相搏置設
pvp-mob-protected-actionbar-mm = <yellow>是生靈爲相搏之庇所護</yellow>
pvp-mob-protection-label = 生靈之庇
pvp-mounted-damage-label = 所乘生靈受擊
pvp-no-permission-mm = <red>君無掌他戲者相搏之權</red>
pvp-player-not-found-mm = <red>在綫戲者無所獲</red>
pvp-protect-mobs = 庇己之生靈
pvp-protect-mobs-desc = 庇君所馴抑所乘之生靈
pvp-quick-combat-cooldown = 戰歇尚餘 { $arg0 } 息
pvp-quick-override = 今從暫令；私好可於置設更易
pvp-set-by-staff-mm = <aqua>管理已設君之相搏爲</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = { $arg0 } 之相搏既設爲 { $arg1 }
pvp-set-other-rich = { $player } 之相搏既設爲 { $state }
pvp-state-label = 相搏
pvp-state-off = 閉
pvp-state-on = 啟
pvp-status-player = 戲者：{ $arg0 }
pvp-status-player-rich = 戲者：{ $player }
pvp-status-title-mm = <gold><bold>相搏之狀</bold></gold>
pvp-summary-disabled = 相搏既閉
pvp-summary-enabled = 相搏既啟
pvp-target-disabled-actionbar-mm = <yellow>彼未啟相搏</yellow>
pvp-target-menu-title = 相搏置設 · { $arg0 }
pvp-tip-protection-mm = 以 <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple> 啟閉己之對戰；<green>雙方皆啟</green>，乃可相攻
pvp-toggled-off-mm = <gray>相搏既閉</gray> <dark_gray>|</dark_gray> <gray>化干戈爲玉帛</gray>
pvp-toggled-on-mm = <green>相搏既啟</green> <dark_gray>|</dark_gray> <gray>今可與已啟者兵戎相見</gray>

## Player · Effects

effect-cleanup-all = [盡除諸效]
effect-cleanup-blocked = 須處創造或旁觀之態，且未與人交戰，方可除效。
effect-cleanup-expired = 此除效之請已失效。
effect-cleanup-negative = [除負面之效]
effect-cleanup-prompt-rich = 察君因 { $source } 而獲負面之效，欲除之乎？
effect-cleanup-result = 已除 { $count } 種效。
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] 滯留藥霧
        [ARROW] 矢
        [ATTACK] 遇襲
        [COMMAND] 指令
        [FOOD] 食物
        [PLUGIN] 插件
        [POTION_DRINK] 飲藥
        [POTION_SPLASH] 濺藥
        [WARDEN] 監守者
        [WITHER_ROSE] 凋靈玫瑰
       *[OTHER] 不明之因
    }

## Player · Trash

trash-clear = 盡棄
trash-clear-hint = 按之即盡棄匣中物；
    不按則閉匣 { $arg0 } 秒後自動盡棄。
trash-command-description = 啟棄物之匣
trash-sort = 整物
trash-sort-hint = 合同物之疊，依物類排列
trash-tip-dispose-mm = 以 <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> 啟<yellow>棄物之匣</yellow>，納所棄之物
trash-title = 棄物之匣

## Player · Utilities

hat-command-description = 冠以主手之物
hat-empty-hand-mm = <red>主手無物</red> <gray>先持一塊抑一物，而後用 /hat</gray>
hat-success-mm = <green>既冠以主手之物</green> <dark_gray>|</dark_gray> <gray>主手與兜鍪之格既易</gray>
hat-tip-wear-mm = 以 <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> 戴<yellow>主手之物</yellow>於首
removeitems-command-description = 清地上遺物
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = 已於 { $world } { $location } 清 { $count } 件遺物 { $radius }
removeitems-no-world = 所在之生界未明
removeitems-radius = 方圓 { $arg0 } 方
removeitems-tip-usage-mm = 以 <aqua><u><click:suggest_command:'/rmitems '>/rmitems [半徑]</click></u></aqua> 清近旁<yellow>遺物</yellow>
self-kick-command-description = 自逐出伺服器
self-kick-reason-too-long = 逐出之故不可逾 { $arg0 } 字
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] 君自請離服，所因：{ $reason }
       *[third] { $player } 自請離服，其故：{ $reason }
    }
    .item-1 = { $player } 自請離服，其故：{ $reason }
    .item-2 = { $player } 離服矣，留言：{ $reason }
    .item-3 = { $player } 辭服，留言曰：{ $reason }
    .item-4 = { $player } 已離服，附言：{ $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] 君已自請離服。
       *[third] { $player } 自請離服。
    }
    .item-1 = { $player } 自請離服。
    .item-2 = { $player } 離服矣。
    .item-3 = 叮咚，{ $player } 已離服
    .item-4 = 在服減一人，{ $player } 離服矣

## Community · Chat and mentions

chat-copy-hover = 擊左鍵以鈔其訊
chat-delay-3s = 3 秒
chat-delay-5s = 5 秒
chat-delay-7s = 7 秒
chat-delay-cancel-command-description = 罷待發之訊
chat-delay-cancelled = 待發之訊既罷
chat-delay-current = 今緩發：{ $arg0 }
chat-delay-nothing-to-cancel = 無待發之訊可罷
chat-delay-off = 閉
chat-delay-preview-marker = [待發]
chat-delay-queued = 訊將於 { $arg0 } 後發。書 /cancel 或 /c 可罷之
chat-item-empty-hover = 主手無物
chat-mention-all-hover = 告此議域中諸可見戲者
chat-msg-command-description = 寄私語而入私議
chat-player-hover = 擊左鍵以私語
chat-private-enter = 既與 { $arg0 } 私語
chat-private-label = 私語
chat-private-no-reply-target = 無可復語之在綫者
chat-private-self = 不可與己私語
chat-private-target-not-found = 在綫戲者 { $arg0 } 無所獲
chat-private-target-offline = 私語之人已離線，候其歸或易頻道
chat-private-tip-channel-mm = 以 <aqua><u><click:suggest_command:'/msg '>/msg 人</click></u></aqua> 入私語頻道，以 <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> 返公頻
chat-public-command-description = 易至公議
chat-public-enter = 既入公議
chat-reply-command-description = 復語於近所私語者
chat-settings-menu-title = 論議置設
chat-social-account-label = 臺帳
chat-social-bound-player-label = 所繫遊者
chat-social-name-label = 臺上之名
chat-social-unbound = 未繫 Minecraft 遊者
chat-staff-command-description = 易{ chat-staff-label }聊天之域
chat-staff-label = 管理
chat-staff-permission-missing = 君已無{ chat-staff-label }聊天之權，既還公議
mention-action-bar = 幕下之告
mention-action-bar-text = { $sender } 呼君之名
mention-alerts = 呼名之告
mention-menu-title = 呼名之告
mention-mute-afk = 暫離則默
mention-sound = 鳴音
staffchat-enter = 既入{ chat-staff-label }聊天

## Community · Announcements

announcement-count = 公告 { $arg0 } 則
announcement-empty = 暫無告示
announcement-first-page = 已至最新
announcement-last-page = 已至最早
announcement-latest = 最新之告
announcement-list-newer = 較新之列
announcement-list-older = 較舊之列
announcement-more-count = 尚有 { $arg0 } 則未示
announcement-new-count = 新告示 { $arg0 } 則
announcement-newer = 新告
announcement-next-page = 後頁
announcement-older = 舊告
announcement-page = 第 { $arg0 } / { $arg1 } 頁
announcement-previous-page = 前頁
announcement-reload = 重載告示
announcement-reload-command-description = 重載告示檔
announcement-reload-failed = 復載告示未成，舊告示仍存
announcement-reloaded = 告示已重載
announcement-unread-count = 未讀 { $arg0 } 則
announcement-updated = 告示已新
announcement-view-all = 覽諸告示
announcement-view-command-description = 覽伺服器告示

## Community · Identity and social platforms

identity-admin-binding-not-found = 未得獨一之玩家與臺繫。
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = 已解玩家 { $arg1 } 之 { $arg0 } 繫 { $arg2 }。
identity-admin-command-description = 察或解外臺身份之繫
identity-admin-help = /bindadmin lookup <玩家或UUID>
    /bindadmin unlink <玩家或UUID> <臺> confirm
identity-admin-lookup-empty = 未得此玩家之繫錄。
identity-admin-lookup-title = { $arg0 } 之繫：
identity-binding-details = · { $arg0 } · 域 { $arg1 } · { $arg2 }
identity-binding-not-owned = 君於此臺無繫。
identity-binding-unlinked = 已解 { $arg0 } 之繫 { $arg1 }。
identity-bindings-empty = 今未繫外臺之號。
identity-bindings-title = 已繫之外部身份：
identity-bindings-unlink-hint = 以 /bind unlink <臺> 解此臺諸繫。
identity-code-cancelled = 已廢未用之 { $arg0 } 驗碼。
identity-code-copy = 點此抄完整繫定之令
identity-code-expiry = 驗碼五分而廢，且僅可一用；再取則舊碼失效。
identity-code-issued = { $arg0 } 繫驗碼既成：
identity-code-not-pending = 無待用之 { $arg0 } 驗碼。
identity-code-redeem = 至所欲之 { $arg0 } 群，呼機關人而發：{ $arg1 }
identity-command-description = 繫 Minecraft 與外臺之身份
identity-help = 身份繫定之令：
    /bind qq — 速取 QQ 繫定之令
    /bind <臺> — 取一次驗碼
    /bind list — 察既有之繫
    /bind cancel <臺> — 廢待用驗碼
    /bind unlink <臺> — 解此臺諸繫
identity-notify-linked = { $arg0 } 身份已繫。可用 /bind list 察之，或用 /bind unlink { $arg0 } 解之。
identity-notify-unlinked = 已解君之 { $arg0 } 身份繫。
identity-platform-none = 無
identity-platforms = 所納之臺：{ $arg0 }
identity-scope-notice = 此臺身份以群為界；當於所欲之群繫之，異群須各繫。
identity-storage-error = 身份繫庫行事敗，請告司者。
identity-unavailable = 身份繫定之務今不可用，請告司者。
identity-unsupported-platform = 此臺未納：{ $arg0 }
social-admin-conversations-empty = 未見 { $arg0 } 之談。
social-admin-conversations-title = 所見 { $arg0 } 之談：
social-admin-help = /social platforms · /social status [臺] · /social reload [臺] · /social conversations <臺>
social-admin-platform = - { $arg0 }（{ $arg1 }）：{ $arg2 }
social-admin-reloaded = 社交臺 { $arg0 } 已重載。
social-admin-reloaded-all = 社交諸臺皆已重載。
social-admin-status = { $arg0 } · { $arg1 } · 世 { $arg2 } · 談 { $arg3 }
social-admin-title = 社交諸臺
social-admin-unknown = 未識之社交臺：{ $arg0 }
social-binding-already-linked = 此臺身分已繫 Minecraft 遊者 { $arg0 }。
social-binding-code-invalid = 驗碼無效或已過期。請於 Minecraft 復行 /bind { $arg0 }。
social-binding-external-in-use = 此臺身分已繫他 Minecraft 遊者。
social-binding-help = 於 Minecraft 行 /bind { $arg0 }。
    錄所得完整之令。
    發於此。
    以 /吾 驗之。
social-binding-identity-missing = 此訊無可信之臺員身分，故不可繫身分。
social-binding-linked = 已繫 Minecraft 遊者 { $arg0 }。
social-binding-not-bound = 此臺身分未繫 Minecraft 遊者。
social-binding-platform-mismatch = 此驗碼乃為他臺所發。
social-binding-player-scope-in-use = 此 Minecraft 遊者於本談已有他身分之繫。
social-binding-rate-limited = 試驗碼過頻，請約 { $arg0 } 分後復試。
social-binding-status-unbound = 此臺身分未繫。請用遊戲中所得驗碼行 /繫。
social-binding-unavailable = 身分之繫暫不可用。
social-binding-unlink-prompt = 解繫將去此臺身分與 Minecraft 之關聯。若決，請發 /解繫 確認。
social-binding-unlinked = 已解與 Minecraft 遊者 { $arg0 } 之繫。
social-command-description = 治社交諸臺之接器
social-duration-days = { $arg0 } 日 { $arg1 } 時 { $arg2 } 分
social-duration-hours = { $arg0 } 時 { $arg1 } 分
social-duration-minutes = { $arg0 } 分
social-help-command-description = 覽此令表
social-link-command-description = 以遊戲中驗碼繫 Minecraft 帳
social-online-command-description = 覽在綫諸玩家
social-profile-afk = 在綫 · 暫離
social-profile-avatar-alt = Minecraft 面像
social-profile-command-description = 覽己之錄；正員可以玩家名、提及或回覆詢他人
social-profile-first-joined-label = 初入
social-profile-language-label = 語言
social-profile-last-seen-label = 最近在綫
social-profile-member-required = 惟已繫之正員或管理、維護者可詢他人之錄。
social-profile-offline = 離綫
social-profile-online = 在綫 · 活躍
social-profile-play-time-label = 遊戲之時
social-profile-player-label = 遊者
social-profile-player-not-found = 未得此 Minecraft 玩家。
social-profile-presence-label = 狀況
social-profile-target-ambiguous = 惟可擇一：玩家名、提及一人、或覆一人。
social-profile-target-unbound = 所提及或所覆之身份未繫 Minecraft 玩家。
social-profile-uuid-label = UUID
social-query-active-label = 活躍
social-query-no-players = 今無遊者在綫。
social-query-online-label = 在綫
social-query-players-label = 遊者
social-query-unknown = 未識此令。請發 /助 以觀諸令。
social-query-uptime-label = 運行之時
social-reply-hidden = 回覆犯伺服器內容安全之則，原文已隱。
social-state-disabled = 已禁
social-state-failed = 敗
social-state-ready = 已備
social-state-starting = 方啟
social-state-stopped = 已止
social-status-command-description = 覽玩家、TPS、MSPT 與運行之時
social-title-command-help = 命令之助
social-title-content-hidden = 內容已隱
social-title-identity-linking = 帳號繫結
social-title-identity-unlinking = 解除繫結
social-title-online-players = 在線玩家
social-title-player-profile = 玩家之錄
social-title-server-status = 伺服器狀
social-title-unknown-command = 未知命令
social-unlink-command-description = 確認而後解此身份之繫

## Community · AI and knowledge

ai-busy = AI 事繁，請稍候再問
ai-cleared = AI 談錄已清
ai-command-description = 問遊戲中之 AI 助手
ai-disabled = AI 助手未啟或未設
ai-knowledge-curation-failed = 知識整理未成：{ $arg0 }
ai-knowledge-curation-finished = 整理既成：施行 { $arg0 } · 略過 { $arg1 } · 失敗 { $arg2 } · 隔離 { $arg3 } · 已處理 { $arg4 }
ai-knowledge-curation-started = 知識整理已始。
ai-knowledge-disabled = 知識庫未啟。
ai-knowledge-index-dirty = 索引待校，同步前暫閉私憶檢索。
ai-knowledge-last-failure = 前次失敗：{ $arg0 }
ai-knowledge-last-sync = 前次同步：{ $arg0 }
ai-knowledge-learning-disabled = 知識學習未啟。
ai-knowledge-operation-failed = 知識操作未成：{ $arg0 }
ai-knowledge-operation-started = 知識操作已始。
ai-knowledge-protected = 知識文 { $arg0 } 已護。
ai-knowledge-rollback = 知識文 { $arg0 } 已復為修訂 { $arg1 }。
ai-knowledge-status = 知識：{ $arg0 } 篇公文 / { $arg1 } 段文字 · { $arg2 } 篇私文 / { $arg3 } 段文字
ai-knowledge-sync-failed = 知識同步未成：{ $arg0 }
ai-knowledge-sync-started = 知識同步已始。
ai-knowledge-synchronized = { ai-knowledge-status } · 同步既成。
ai-knowledge-unprotected = 知識文 { $arg0 } 已解護。
ai-learning-failure = 學習未成：{ $arg0 }
ai-learning-status = 學習：候選 { $arg0 } · 擷取 { $arg1 } · 候列 { $arg2 } · 棄擷取 { $arg3 } · 棄候選 { $arg4 } · 隔離 { $arg5 }
ai-memory-empty = { $arg0 } 無私憶。
ai-memory-list = { $arg0 } 之私憶：{ $arg1 }
ai-memory-not-found = 未得私憶 { $arg0 }。
ai-memory-removed = 已永刪 { $arg1 } 之 { $arg0 } 則私憶。
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = 快取中無此玩家：{ $arg0 }
ai-prompt-too-long = 所問過長（至多 { $arg0 } 字）
ai-reload-failed = AI 設定重載不成：{ $arg0 }
ai-reloaded = AI 設定已重載（{ $arg0 }）
ai-request-failed = AI 求問不成，請稍候再試
ai-social-command-description = 問共用之 AI 助手
ai-social-question-required = 請於 AI 令後書所問
ai-status-disabled = AI 今已停
ai-status-enabled = AI 已啟 · 供者 { $arg0 } · 模型 { $arg1 } · 工具 { $arg2 }
ai-status-none = 無
ai-thinking = 思之中…
ai-usage = 用法：/ai <所問> · /ai clear · /ai status
ai-worker-status = 智助任務：運行 { $arg0 } · 候列 { $arg1 } · 已棄 { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>更上一層樓！</bold> 君今爲 <gold>正員</gold>，得用更多令與事，投影亦可一擊而印。</green>
governance-active-days-30 = 近三十日有效參與至少二日（今 { $arg0 }）
governance-active-days-60 = 近六十日有效參與至少六日（今 { $arg0 }）
governance-active-header = ─── 活躍成員之資格 ───
governance-active-member = 今為成員
governance-active-member-age = 為成員已滿三十日
governance-active-playtime = 近六十日有效遊玩滿六時（今 { $arg0 }）
governance-activity-action = { $arg0 ->
    [confirm] 確認聯署
    [vote] 詳閱投票
    *[details] 詳情
    }
governance-activity-all = 閱全部（{ $arg0 }）
governance-activity-closed = 已終
governance-activity-confirm-petition = 確認聯署？人數足則開罷免之票。
governance-activity-needed = 尚需 { $arg0 } 人
governance-activity-proposal = { $arg0 ->
    [appointment] 任 { $arg1 } 為管理
    [removal] 解 { $arg1 } 管理之職
    [petition] 罷免 { $arg1 } 之聯署
    *[ban] 封禁 { $arg1 } 之票
    }
governance-activity-remaining = { $arg1 ->
    [days] 尚餘：{ governance-days }
    [hours] 尚餘：{ governance-hours }
    *[minutes] 尚餘：{ governance-minutes }
    }
governance-activity-status = { $arg0 ->
    [available] 可投
    [voted] 已投
    [join] 可聯署
    [joined] 已聯署
    [closed] 已終
    *[view] 可閱
    }
governance-already-moderator = 君已為管理。
governance-application-status = { $arg0 ->
    [open] 任命之票議中
    [pending] 已可，待任命
    [blocked] 申請條件未備
   *[ready] 可申請管理
    }
governance-ballot-recorded = 匿名之票已記為「{ $arg0 }」；限前可更。
governance-ban-details = 倡者 { $arg0 } · 期限 { $arg1 } · 緣由：{ $arg2 }
governance-ban-suffix = · 期限 { $arg0 } · 緣由：{ $arg1 }
governance-choice-abstain = 棄權
governance-choice-no = 反對
governance-choice-yes = 贊成
governance-command-description = 覽治理資格與投票
governance-custodian-no-nomination = 維護者不與管理之任免。
governance-custodian-no-resign = 維護者不適用管理辭任之程。
governance-days = { $arg0 } 日
governance-error-ban-already-active = 此人已有現行封禁。
governance-error-ban-proposer-ineligible = 唯活躍成員得倡封禁之票。
governance-error-ban-self = 不得倡封禁己身之票。
governance-error-candidate-ineligible = 管理候選須為活躍成員
governance-error-nomination-not-open = 君無進行中之管理申請。
governance-error-not-voter = 君不在此票既定選民之籍。
governance-error-pause-already-active = 此人已有現行晉升暫停。
governance-error-pause-already-member = 此人已為成員，無須暫停晉升。
governance-error-pause-end-invalid = 晉升暫停之終期須在今後。
governance-error-player-unknown = 此人尚無治理紀錄。
governance-error-removal-petition-closed = 此聯署已終，或已另立新案。
governance-error-removal-requires-petition = 罷免之票須有足額聯署。
governance-error-removal-self = 不得聯署罷免己身。
governance-error-removal-sponsor-ineligible = 唯活躍成員得聯署罷免
governance-error-removal-sponsors-too-few = 被罷免者外須有活躍成員至少二人。
governance-error-removal-target-not-moderator = 此人已非可罷免之管理。
governance-error-removal-vote-open = 此管理已有進行中之罷免票。
governance-error-termination-reason-invalid = 此終止之由不適用於此票。
governance-error-vote-already-open = 此人已有同類進行中之票。
governance-error-vote-closed = 此票已截止或終止。
governance-error-vote-cooldown = 未達法定參與後三日之間隔尚未滿。
governance-error-vote-subject-unknown = 投票對象尚無治理紀錄。
governance-error-vote-termination-closed = 票期已過，不得撤之。
governance-error-vote-unknown = 無此投票。
governance-error-voters-too-few = 開票須有活躍成員至少二人。
governance-failure-majority = 贊成未逾全部票之半
governance-failure-minimum-yes = 贊成未達最低票數
governance-failure-none = 無所記之由
governance-failure-quorum = 未達法定參與數
governance-failure-two-thirds = 贊成未達非棄權票之三分之二
governance-history-header = ─── 近日投票之果 ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
governance-hours = { $arg0 } 時
governance-inactive-removal = { $arg0 } 連續一百八十日未入，故自動卸管理之任。
governance-invalid-choice = 請擇 yes、no 或 abstain。
governance-invalid-duration = 期限之式不合，請用 30m、12h、3d 等。
governance-kind-appointment = 任命
governance-kind-ban = 封禁
governance-kind-removal = 罷免
governance-legacy-ban-kick = 昔治理之票已通過。君受封禁 { $arg0 }。由：{ $arg1 }
governance-menu-already-sponsored = 已署
governance-menu-application = 申請近況
governance-menu-back = 返
governance-menu-cancel = 罷
governance-menu-completed = 已決
governance-menu-confirm = 確
governance-menu-confirm-nominate = 申請既呈，即啟管理任命之票。
governance-menu-confirm-resign = 欲辭管理之職乎？
governance-menu-confirm-sponsor = 欲贊此罷免之議乎？
governance-menu-confirm-withdraw = 欲撤管理之申請乎？
governance-menu-custodian-duties = 維護伺服器，理其庶務。
governance-menu-identity = 身分
governance-menu-identity-hint = 覽晉升之條件與活躍之錄
governance-menu-join-petition = 署名請罷
governance-menu-loading = 載入治理近況中…
governance-menu-matters = 公議
governance-menu-moderator-duties = 察違規，應玩家之求。
governance-menu-nominate = 申請管理
governance-menu-offline = 離線
governance-menu-ongoing = 議中
governance-menu-online = 在線
governance-menu-pause = 暫止新人晉升
governance-menu-pause-appeal = 申訴之途
governance-menu-pause-duration = 期限（如 2d）
governance-menu-pause-reason = 緣由
governance-menu-pause-until = 止期
governance-menu-pending-application = 已可 · 待行
governance-menu-petition-row = { $arg0 } · 贊者 { $arg1 }/{ $arg2 }
governance-menu-petitions = 罷免之議
governance-menu-player-name = 玩家之名
governance-menu-players = 玩家管理
governance-menu-refresh = 重載
governance-menu-related = 近事
governance-menu-related-matters = 閱相關公議
governance-menu-resign = 辭管理之職
governance-menu-resume = 解除暫止
governance-menu-resume-reason = 解除之由
governance-menu-staff-only = 惟管理與維護者得管理玩家
governance-menu-staff-search = 尋管理
governance-menu-subject-filter = { $arg0 } · 撤篩 ×
governance-menu-team = 管理名錄
governance-menu-team-empty = 未見相合之管理
governance-menu-title = 社群共治
governance-menu-withdraw = 撤申請
governance-minutes = { $arg0 } 分
governance-no-active-ban = 今無生效封禁
governance-no-active-pause = 今無生效晉升暫停
governance-nomination-withdrawn = 管理申請已撤；終止之果將自動告之。
governance-none = 無
governance-not-member = 尚非成員
governance-not-moderator = 君今非管理。
governance-operation-error = 治理之事未成，請復試。
governance-own-choice-closed = 君之擇：{ $arg0 }
governance-own-choice-open = 君之擇：{ $arg0 }；限前可更。
governance-own-not-voted = 君有票權而未投。
governance-own-not-voted-hint = 君尚未投票。入「治理」擇此票即可。
governance-own-not-voter = 君不在此票選民之籍。
governance-own-voted = 君已投票
governance-pause-cleared = { $arg0 } 之晉升暫停已除。
governance-pause-details = 暫停之由：{ $arg0 } · 至 { $arg1 } · 申訴：{ $arg2 }
governance-pause-fields-required = 請書暫停之由及申訴之途。
governance-pause-not-active = { $arg0 } 無生效中之晉升暫停。
governance-pause-notice = 君之晉升已暫停。由：{ $arg0 } · 至 { $arg1 } · 申訴：{ $arg2 }
governance-pause-set = { $arg0 } 之晉升已暫停至 { $arg1 }。
governance-petition-already-sponsored = 君已聯署此罷免（{ $arg0 }/{ $arg1 }）。
governance-petition-row = { $arg0 } · 聯署 { $arg1 }/{ $arg2 } 人 · 至 { $arg3 }
governance-petition-started = 罷免 { $arg0 } 之聯署：{ $arg1 }/{ $arg2 } 人，至 { $arg3 }。
governance-petitions-header = ─── 罷免聯署 ───
governance-player-first-join = 初入：{ $arg0 }
governance-player-header = ─── { $arg0 } · 治理紀錄 ───
governance-player-id = UUID：{ $arg0 }
governance-player-last-login = 前次入服：{ $arg0 }
governance-player-member-since = 為成員始於：{ $arg0 }
governance-player-never-joined = 玩家 { $arg0 } 未嘗入此服。
governance-promotion-account-age = 初入已滿三日
governance-promotion-header = ─── 晉升之要件 ───
governance-promotion-play-days = 有效遊玩跨至少二日（今 { $arg0 }）
governance-promotion-playtime = 有效遊玩滿八時（今 { $arg0 }）
governance-resign-failed = 辭任未成，請告維護者。
governance-resigned = { $arg0 } 已辭管理之任。
governance-result-announcement = 投票 #{ $arg0 } { $arg1 }（{ $arg2 }）：{ $arg3 }{ $arg4 }
governance-result-failed = 未通過（{ $arg0 }）
governance-result-open = 進行中
governance-result-passed = 通過
governance-result-tally = { $arg0 } · 選民 { $arg1 }，參與 { $arg2 }，贊成 { $arg3 }，反對 { $arg4 }，棄權 { $arg5 }
governance-result-terminated = 已終止（{ $arg0 }）
governance-resume-notice = 君之晉升暫停已除。由：{ $arg0 }
governance-resume-reason-required = 請書解除之由。
governance-role-lookup-error = 未能核管理之身分，請告維護者。
governance-seconds = { $arg0 } 秒
governance-storage-error = 治理資料之事未成，請告維護者。
governance-termination-inactive = 管理久未入服而卸任
governance-termination-ineligible = 候選人失活躍成員之資格
governance-termination-resigned = 管理已辭任
governance-termination-unknown = 未記其由
governance-termination-withdrew = 候選人撤申請
governance-vote-electorate = 選民 { $arg0 } 人 · 法定參與 { $arg1 } 人
governance-vote-finalized = 結算：{ $arg0 }
governance-vote-header = ─── 投票 #{ $arg0 } ───
governance-vote-in-progress = 進行中 · 已參與 { $arg0 } 人；分項票數終後公布。
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · 至 { $arg3 } · 選民 { $arg4 } · 已參與 { $arg5 } · 法定 { $arg6 }{ $arg7 }
governance-vote-period = 始 { $arg0 } · 止 { $arg1 }
governance-vote-started = { $arg1 }之票 #{ $arg0 } 已開：{ $arg2 }；選民 { $arg3 } 人，法定參與 { $arg4 } 人，止於 { $arg5 }。詳見「治理」之中。
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── 進行中之匿名投票 ───

## World · Plots

plot-access-removed-for-you = 汝於地段「{ $arg0 }」之權已除。
plot-admin-created = 已代 { $arg1 } 錄入地段 { $arg0 } · { $arg2 }
plot-allowed = 許
plot-area-changed = 確認之際地界已易，請刷新再試。
plot-area-confirm = 以今選域易「{ $arg0 }」之界（{ $arg1 } 單元）乎？不毀建築。
plot-area-hint = 木斧左右擊或 //pos1、//pos2 定角，返選單而存
plot-area-replace-save = 易界而存
plot-area-save = 存地界
plot-area-saved = 「{ $arg0 }」之界已存。
plot-atmosphere-saved = 地境已存。
plot-back = 返回
plot-board-auto-notify-result = 已擬告 { $arg0 } 人。
plot-board-auto-registered-text = 地段「{ $arg0 }」已立。已建單元外框：X { $arg1 }–{ $arg2 }，Y { $arg3 }–{ $arg4 }，Z { $arg5 }–{ $arg6 }。
plot-board-detail = { $arg0 } 之簿錄，處 { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }：{ $arg5 }
plot-board-empty = 社區尚無簿錄。
plot-board-header = 社區告知與異議（近 { $arg0 } 條；以 /plot board 號 覽詳）
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = 公示
plot-board-kind-objection = 異議
plot-board-loading = 正讀公示…
plot-board-menu = 社區簿錄
plot-board-notice-alert = { $arg0 } 布告：{ $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } 異議：{ $arg1 } · { $arg2 }
plot-board-rate-limited = 公示已逾次限，請稍候。
plot-board-record-invalid = 簿錄未存、已結，或此舉不許。
plot-board-registered-alert = { $arg0 } 立地段「{ $arg1 }」：{ $arg2 }
plot-board-ruling-alert = { $arg0 } 之異議已裁：{ $arg1 } · { $arg2 }
plot-board-ruling-detail = 裁決：{ $arg0 }
plot-board-saved = 簿錄已存：{ $arg0 }。
plot-board-status-elapsed = 刊布已逾三日
plot-board-status-open = 未決
plot-board-status-ruled = 已決
plot-board-status-withdrawn = 異議已撤
plot-board-text-invalid = 說明須 12–1000 字；裁決之由至少 8 字。
plot-board-unavailable = 簿錄暫未載；請稍候，或往 https://mcmik.top/community/notices 覽之。
plot-board-withdrawal-alert = { $arg0 } 撤異議：{ $arg1 } · { $arg2 }
plot-board-write-failed = 公示未得更新，請稍後再試。
plot-command-blocked = 地段保護阻此令；己地內可直用 /fill 或 /setblock，繁複 /execute 請管理審之。
plot-command-description = 管理地段
plot-created = 地段 { $arg0 } 既立 · { $arg1 }
plot-created-for-you = { $arg1 } 已代汝錄入地段 { $arg0 }。
plot-deleted = 地段已釋；築物未毀。
plot-denied = 禁
plot-error-admin-owner-only = 改有司理之能者，須兼具成員及權限司理之權。
plot-error-area-disconnected = 地段須為相連一域。
plot-error-args = 參數有誤或未備，請用 /plot help。
plot-error-busy = 地段資料方存，此事未提交，請稍候。
plot-error-flag = 權名不合，請於權限表擇其操作。
plot-error-id = 地段號未得或有歧義，請用 /plot list。
plot-error-member-missing = 此人已不在成員簿。
plot-error-member-required = 新客可閱地段、陳異議；欲立工程，須先為成員
plot-error-name = 工程名須一至四十八字。
plot-error-notice-text = 告示至多 { $arg0 } 字，行數與行長不限；勿含控制字元。
plot-error-overlap = 建築之域與他地段相疊。
plot-error-owner-member = 所有者使用所有者權限組，不作為普通成員邀請。
plot-error-owner-only = 你沒有執行此操作所需的管理權限
plot-error-permission-changed = 權限、成員或承襲之則已變，請重覽而後定。
plot-error-permission-grant = 不得授己所無之司理權。
plot-error-permission-owner-scope = 惟地主、母地主及伺服器司理得改地主組權。
plot-error-player-unknown = 未得曾入服之玩家；請核姓名或 UUID。
plot-error-release-owner-only = 你沒有刪除地段的權限。
plot-error-selection = 以 //pos1、//pos2 定兩隅
plot-error-selection-changed = 所選已易，請復確認。
plot-error-staff-only = 唯管理或維護者可管理或代錄他人地段。
plot-error-storage = 地段操作未成，稍候重試，或告管理。
plot-error-subplot-children = 先釋子段，後釋母段
plot-error-subplot-depth = 子段僅可一層。
plot-error-subplot-disconnected = 所選諸格不相連，宜分而劃之。
plot-error-subplot-overlap = 所選與他子段相疊。
plot-error-subplot-owner = 無權行此子地段之事
plot-error-subplot-parent = 兩端皆須在母段已築格內。
plot-error-subplot-supervisor-member = 母段之主本有監管之權，無須列為成員。
plot-error-time-format = 請以 HH:mm（00:00–23:59）書時。
plot-error-transfer-owner-only = 你沒有轉讓所有權的權限。
plot-error-transfer-self = 汝已為此地段之主。
plot-flag-set = 已設 { $arg0 } = { $arg1 }。
plot-help = 地段操作指引
plot-here = 此處：{ $arg0 } · { $arg1 }
plot-invited = 已授 { $arg0 } 通行之權。
plot-item-summary = 範圍 { $arg0 } 格² · 成員 { $arg1 } 人
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } 平方格
plot-marked = { $arg0 }：{ $arg1 }
plot-member-draft-hint = 尚未提交
plot-member-draft-reset = 清此類之設
plot-member-draft-role = 角色：{ $arg0 }
plot-member-draft-submit = 預覽並確認
plot-member-draft-title = 成員草案 · { $arg0 }
plot-member-permissions = 權限
plot-member-removed = 工程成員已移。
plot-menu-access = 同築者與權限
plot-menu-add-member = 添成員
plot-menu-atmosphere = 地境
plot-menu-clear = 晴
plot-menu-confirm-delete = 確欲除 { $arg0 } 之籍？護界、同築者權限及設置皆去，不可復；建築猶存。
plot-menu-confirm-subplot-transfer = 以「{ $arg0 }」轉予 { $arg1 }？母段之主仍可治。
plot-menu-confirm-transfer = 以「{ $arg0 }」地段之管理權讓於 { $arg1 } 乎？汝將失其權；舊有署名不改。
plot-menu-confirm-withdraw = 確欲撤回此意見？撤回之狀仍公示。
plot-menu-create = 立地段
plot-menu-create-for-player = 代此人錄入地段
plot-menu-custom-time = 自定時辰
plot-menu-delete = 除地段之籍
plot-menu-description = 說明
plot-menu-feedback = 異議此告示
plot-menu-find-record = 循號尋簿錄
plot-menu-flag-container = 開容器
plot-menu-flag-entity = 操作實體
plot-menu-follow-parent = 從母段
plot-menu-follow-world = 隨天地
plot-menu-group-admin = 地段管事
plot-menu-group-collaborator = 共作者
plot-menu-group-member = 成員預設權限
plot-menu-group-newcomer = 新人預設權限
plot-menu-group-owner = 地段之主
plot-menu-here = 所在之地
plot-menu-manage = 管理他人地段
plot-menu-managed-empty = 此人尚無地段。
plot-menu-managed-title = { $arg0 } 之地段
plot-menu-members = 成員（{ $arg0 }）
plot-menu-name = 工程名
plot-menu-owner = 地主：{ $arg0 }
plot-menu-parent-supervisor = 母段之主：{ $arg0 } · 可監
plot-menu-player = 玩家名或 UUID
plot-menu-rain = 雨雪
plot-menu-record-search = 編號或題
plot-menu-related-notice = 閱所關告知
plot-menu-remove-member = 移成員
plot-menu-rename = 更工程名
plot-menu-rule = 錄裁決
plot-menu-ruling = 書裁決之由（八至千字）
plot-menu-set-role = 改為{ $arg0 }
plot-menu-settings = 工程設置
plot-menu-submit = 施行
plot-menu-subplot-create = 建子地段
plot-menu-subplot-of = 隸於「{ $arg0 }」
plot-menu-subplot-parent = 返母段「{ $arg0 }」
plot-menu-subplots = 子地段（{ $arg0 }）
plot-menu-time = 時辰
plot-menu-time-input = 時辰（HH:mm）
plot-menu-transfer = 轉讓地段
plot-menu-weather = 天候
plot-menu-withdraw = 撤回意見
plot-no-here = 此處無受護地段。
plot-notice-board = 地段告示板
plot-notice-cleared = 地段告示已清除
plot-notice-edit = 修地段告示
plot-notice-input = 至多 { $arg0 } 字，可分行；空則清除
plot-notice-read = 閱全文
plot-notice-saved = 地段告示已更新
plot-permission-all-actions = 諸操作
plot-permission-animal = 照料、馴養與繫繩
plot-permission-break = 毀方塊
plot-permission-bucket = 以桶取放液體與生物
plot-permission-category-build = 營造修改
plot-permission-category-entity = 實體操作
plot-permission-category-interaction = 方塊交互
plot-permission-category-management = 管理之權
plot-permission-category-storage = 儲物與物品
plot-permission-change-children = 所涉子地：{ $arg0 }
plot-permission-change-confirm = 確認施行
plot-permission-change-granted = 新授：{ $arg0 }
plot-permission-change-members = 所涉成員：{ $arg0 }
plot-permission-change-more = 餘 { $arg0 } 項
plot-permission-change-no-effect = 實權未變；明設者將覆後續承襲。
plot-permission-change-none = 無
plot-permission-change-revoked = 撤權：{ $arg0 }
plot-permission-change-subject = 對象：{ $arg0 }
plot-permission-change-title = 確認權限之變
plot-permission-create-subplot = 建子地段
plot-permission-decoration = 置改飾物
plot-permission-default-status = 預設（{ $arg0 }）
plot-permission-delete = 刪地段
plot-permission-door = 啟閉門閘
plot-permission-entity-damage = 傷實體
plot-permission-entity-storage = 開實體藏物
plot-permission-harvest = 採漿果、藤蔓與蜂蜜
plot-permission-ignite = 舉火
plot-permission-item-drop = 棄物
plot-permission-item-pickup = 拾地上之物
plot-permission-manage-area = 更界
plot-permission-manage-members = 理成員
plot-permission-manage-permissions = 理權限
plot-permission-manage-settings = 更地段之設
plot-permission-modify = 改方塊施肥
plot-permission-operation = 操作
plot-permission-personal = 個人之權
plot-permission-personal-source = 個人之設
plot-permission-place = 置方塊與車舟
plot-permission-reset = 復常例
plot-permission-result = 狀態
plot-permission-ride = 乘車舟禽獸
plot-permission-select-state = 擇狀態：{ $arg0 }
plot-permission-sign = 改告示牌
plot-permission-source-group = 所屬組權
plot-permission-source-local = 本地所定
plot-permission-source-owner = 所有者保留復權之能
plot-permission-source-parent = 母地段
plot-permission-source-role = 角色常例
plot-permission-state-default = 預設
plot-permission-switch = 鈕桿與壓板
plot-permission-trade = 與村民交易
plot-permission-transfer = 轉所有權
plot-permission-workstation = 用設施與工作站
plot-pos1 = 首點
plot-pos2 = 次點
plot-private = 未公開
plot-public = 公開
plot-region-adjust = 調此矩形
plot-region-apply = 完成矩形調整
plot-region-cancel = 取消矩形調整
plot-region-changed = 所選已易，請重選矩形。
plot-region-delete = 刪此矩形
plot-region-delete-confirm = 自草稿去矩形 #{ $arg0 } 乎？不毀建築；存地段而後易界，亦可撤銷。
plot-region-edit-hint = 以 //pos1、//pos2 調此矩形，畢而存之
plot-region-finish-first = 請先畢或棄今矩形之調整。
plot-region-open = 矩形管理（{ $arg0 }）
plot-region-select-hint = 點編號矩形，沙盤聚焦而顯之。灰線為本頁之形；併域後序號或易。
plot-renamed = 工程名已更。
plot-role-admin = 地段管事
plot-role-changed-for-you = 汝於地段「{ $arg0 }」之位今為{ $arg1 }。
plot-role-collaborator = 協作者
plot-role-owner = 地段之主
plot-role-parent-owner = 母段之主
plot-role-server-staff = 管理
plot-selection-add = 併入此框
plot-selection-clear = 重定兩點
plot-selection-empty = 以 //pos1、//pos2 定兩隅。
plot-selection-fit = 復已存之界
plot-selection-incomplete = 已記一點，請記另一點；未呈交則不改地段。
plot-selection-redo = 重施前一步
plot-selection-replace = 以此框代之
plot-selection-session-hint = 潛行+F 或 /plot edit 返回；二十分鐘無操作則退出
plot-selection-size = 所選廣高深：{ $arg0 } × { $arg1 } × { $arg2 } 格（X/Y/Z）
plot-selection-stopped = 取界已畢，各地段之草稿仍存。
plot-selection-subtract = 扣除此框
plot-selection-undo = 復前一步
plot-subplot-created = 已立子段「{ $arg0 }」· { $arg1 } 格²
plot-subplot-created-for-you = 「{ $arg1 }」之子段「{ $arg0 }」今歸汝。
plot-subplot-owner-changed = 子段「{ $arg0 }」今歸 { $arg1 }。
plot-subplot-parent-transferred = 汝子段「{ $arg0 }」之母段已易主，新主為 { $arg1 }。
plot-subplot-released = 子段「{ $arg0 }」已釋，其地仍由母段護之。
plot-title = 地段
plot-transferred = 已以地段「{ $arg0 }」讓於 { $arg1 }。
plot-transferred-to-you = { $arg1 } 已以地段「{ $arg0 }」讓於汝。

## World · Vehicles

vehicle-command-description = 駕御及管理顯示實體之車
vehicle-menu-label =
    { $arg0 ->
        [vehicle] 御車面板
        [driving] 駕乘
        [maintenance] 維護與權限
        [public-status] 今公開
        [private-status] 今私有
        [home] 載具工作台
        [nearby] 附近載具
        [fleet] 所有可存取載具
        [selection] 模型擷取選區
        [origin] 將腳下設為原點
        [group] 選取展示實體組
        [radius] 選取半徑（0–16 公尺）
        [displays] 瀏覽附近展示實體
        [clear] 清空選區與原點
        [capture] 從選區建立模型
        [toggle-selection] 切換選取狀態
        [public] 設為公開
        [private] 設為私人
        [reload] 從檔案重新載入模型
        [leave] 離開載具
        [passenger] 乘客座位
        [fuel] 加油 · 主手持煤或木炭
        [repair] 維修 · 主手持鐵錠
        [access] 存取權限
        [remove] 移除載具
        [hud] 顯示駕駛 HUD
        [hide-preview] 隱藏 3D 預覽
        [service-hint] 請靠近並停車熄火，主手持所需材料。
        [nearby-hint] 請靠近至 32 格內，並先離開目前座位。
        [use-required] 需要 mik.vehicle.use 權限。
        [remove-hint] 請先停車並讓所有乘員下車。
        [reload-hint] 所有載具須停車、熄火且無人乘坐。草稿保留；外部已變更的模型須重新開啟。
        [pick-hint] 瞄準展示實體後右鍵選取整組；潛行右鍵取消，30 秒後到期。
        [spawn-hint] 世界座標預設位於前方四格；載具依生成時的水平視角定向。
        [zoom-in] 放大預覽
        [zoom-out] 縮小預覽
        [frame] 在面前重新擺放
        [offset] 相對移動
       *[other] { vehicle-command-description }
    }

vehicle-controls = { $arg0 }：W/S、A/D 駕駛；滾輪換檔或調油門；換手改模式；潛行下車。/vehicle
vehicle-dash-assisted = 穩定輔助
vehicle-dash-automatic = 自動
vehicle-dash-contact = 地面／水面承托：{ $arg0 }
vehicle-dash-details = 詳情
vehicle-dash-down = 降檔 −
vehicle-dash-drive = 駕駛
vehicle-dash-energy = 耗 { $arg0 } 單位／分 · 可行約 { $arg1 } 分／{ $arg2 } 千米
vehicle-dash-engine-start = 啟動引擎
vehicle-dash-engine-stop = 熄引擎
vehicle-dash-fuel = 燃料 { $arg0 } / { $arg1 } 單位 · { $arg2 }%
vehicle-dash-gear = 檔 { $arg0 }
vehicle-dash-gearbox = 變速
vehicle-dash-handbrake = 駐車／手剎：{ $arg0 }
vehicle-dash-heading = 方位 { $arg0 }°
vehicle-dash-health = 完好 { $arg0 }% · 乘員 { $arg1 } / { $arg2 }
vehicle-dash-input = 油門 { $arg0 }% · 制動 { $arg1 }% · 轉向 { $arg2 }%
vehicle-dash-manual = 手動
vehicle-dash-mode-switch = 改為 { $arg0 }
vehicle-dash-position = 原點 { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = 重置面板位置
vehicle-dash-rpm = 轉速 { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } 秒
vehicle-dash-speed = 速 { $arg0 } 千米／時 · 限 { $arg1 }
vehicle-dash-throttle-down = 油門 −5%
vehicle-dash-throttle-up = 油門 +5%
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = 無輔
vehicle-dash-unavailable = 操控未可用，請就駕座重開。
vehicle-dash-up = 升檔 +
vehicle-dash-vertical = 高 { $arg0 } 米 · 升降速 { $arg1 } 米／秒
vehicle-error = 車輛操作未成：{ $arg0 }
vehicle-group-changed = 組構已變，請重選而後匯入。
vehicle-group-invalid = 組中有不支援、受管、缺失或過遠之體；諸體須同世且距三十二格內。
vehicle-group-limit = 匯入至多一百二十八顯示體，組員總數至多二百五十六。
vehicle-group-not-found = 未得顯示組；請選組、瞄方塊或物品顯示體，或錄實體 UUID。
vehicle-group-selected = 已選組：{ $arg0 } 顯示體 · 根 { $arg1 }
vehicle-hud-boat = { $arg0 } 節 · 推力 { $arg1 }% · 向 { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } 千米／時 · 高 { $arg1 } 米 · 升降 { $arg2 } 米／秒 · 油門 { $arg3 }% · { $arg4 }
vehicle-hud-status = 燃料 { $arg0 }% · 完好 { $arg1 }% · { $arg2 }
vehicle-model-center = 重心
vehicle-model-choice = { $arg0 ->
    [CAR] 車
    [BOAT] 動力舟
    [PLANE] 定翼機
    [BODY] 車身
    [FRONT_WHEEL] 轉向輪
    [WHEEL] 輪
    [PROPELLER] 旋槳
    [RUDDER] 舵
    [FIXED] 固定
    [VERTICAL] 直立隨視
    [HORIZONTAL] 水平隨視
    [CENTER] 常向視角
    [NONE] 原姿
    [THIRDPERSON_LEFTHAND] 第三人稱·左手
    [THIRDPERSON_RIGHTHAND] 第三人稱·右手
    [FIRSTPERSON_LEFTHAND] 第一人稱·左手
    [FIRSTPERSON_RIGHTHAND] 第一人稱·右手
    [HEAD] 頭
    [GUI] 物品欄
    [GROUND] 地
    [LEFT] 左齊
    [RIGHT] 右齊
    [false] 關
    [true] 開
    [TEXT_CENTER] 中齊
   *[other] { $arg0 }
    }
vehicle-model-field = { $arg0 ->
    [kind] 車舟之類
    [mass] 質量
    [maximum-speed] 極速（km/h）
    [support-length] 支點行程（米）
    [grip] 附地力
    [center-of-mass] 重心
    [inertia] 三軸轉動慣量
    [engine-torque] 最大扭矩
    [engine-idle-rpm] 怠速（RPM）
    [engine-redline-rpm] 轉速上限（RPM）
    [engine-inertia] 機關慣量
    [engine-wheel-radius] 輪半徑（米）
    [engine-final-drive] 終傳比
    [engine-reverse-ratio] 倒行齒比
    [engine-ratios] 前行各檔齒比
    [engine-shift-seconds] 換檔時長（秒）
    [fuel-capacity] 燃料容量
    [fuel-consumption] 耗料（單位/秒）
    [buoyancy] 浮力
    [wing-area] 翼面積（平方米）
    [lift] 升力係數
    [animation] 動態職司
    [billboard] 隨視角轉向
    [item-transform] 物品姿態
    [alignment] 文字對齊
    [block-light] 方塊光
    [sky-light] 天光
    [background] 底色（ARGB）
    [line-width] 行寬
    [opacity] 不透明度（0–255）
    [shadow] 字影
    [see-through] 透物見字
    [x] X
    [y] Y
    [z] Z
    [size-x] 全寬 X（米）
    [size-y] 全高 Y（米）
    [size-z] 全長 Z（米）
   *[other] { $arg0 }
    }
vehicle-model-field-range = 值須大於零，且不逾 { $arg0 }
vehicle-model-front = 車首（+Z）
vehicle-model-front-choice = { $arg0 ->
    [negative-x] 今 −X 側
    [back] 今後方（−Z）
    [positive-x] 今 +X 側
    [look] 依水平視向（取近九十度）
   *[other] 以此側為首
    }
vehicle-model-front-hint = 於三維箭頭擇車首。車身不移，座、出口與物理諸點一同換軸。以九十度為節，可復原。
vehicle-model-handle = { $arg0 ->
    [ROTATE] { $arg1 } { $arg2 } { $arg3 }°
   *[other] { $arg1 } { $arg2 } { $arg3 }
    }
vehicle-model-marker = { $arg0 ->
    [driver] 駕位 #{ $arg1 }
    [passenger] 客位 #{ $arg1 }
    [exit] 出口 #{ $arg1 }
    [colliders] 碰撞盒 #{ $arg1 }
    [supports] 支點 #{ $arg1 }
    [center] 重心
    [parts] 展示件 #{ $arg1 }
   *[other] { $arg1 }
    }
vehicle-model-parameter-group = { $arg0 ->
    [body] 車身與附地
    [engine] 機關與齒輪
    [fuel] 燃料
    [boat] 舟之浮力
    [plane] 機之升力
    [power] 機關動力
   *[other] { $arg0 }
    }
vehicle-model-parameter-help = { $arg0 ->
    [body] 調質量、極速與附地。重心可於三維調整；未存則唯改草稿。
    [engine] 調轉速、扭矩、輪與齒比。前檔齒比須遞減，限轉須過怠速一倍有半。
    [fuel] 容量以遊戲燃料單位計，耗料以每秒計。
    [boat] 浮力於舟生效，餘類諸值仍存。
    [plane] 翼面積與升力於飛機生效，餘類諸值仍存。
    [power] 調此載具轉速與扭矩；車之齒輪諸值仍存。
   *[other] { $arg0 }
    }
vehicle-model-property-help = { $arg0 ->
    [block-light] −1 隨世光；欲自定，二光值皆須為 0–15。
    [sky-light] −1 隨世光；欲自定，二光值皆須為 0–15。
    [background] 填有符號 ARGB 整數，如 −16777216 為不透明黑。
    [line-width] 行寬可為 1–2048 像素。
    [opacity] 0 為透明，255 為不透明。
   *[other] { $arg0 }
    }
vehicle-model-spatial = 入三維點選
vehicle-model-spatial-hint = 右擊標記以選取或調整；潛行右擊則返設定。滾輪可易步長。
vehicle-model-spawn = 依此模型生載具
vehicle-model-add = 添部件
vehicle-model-add-here = 添承托於足下
vehicle-model-anchor = 在面前重新擺放
vehicle-model-animation = 動畫
vehicle-model-apply = 施行／確認
vehicle-model-auto = 重算碰撞界
vehicle-model-block = 方塊
vehicle-model-center-here = 置重心於足下
vehicle-model-center-look = 置重心於所視之面
vehicle-model-check = 驗構造與參數
vehicle-model-checked = 構造參數皆合；存時復驗顯示資料、既有車輛及世界碰撞。
vehicle-model-clone = 複製車模
vehicle-model-colliders = 碰撞箱
vehicle-model-copy = 複而編之（複座皆客座）
vehicle-model-corner-cancel = 撤未定之碰撞箱
vehicle-model-corner-first = 以足下為箱之首角
vehicle-model-corner-first-look = 以所視面為首角
vehicle-model-corner-hint = { vehicle-model-corner-first } → { vehicle-model-corner-second }
vehicle-model-corner-required = { vehicle-model-corner-first }
vehicle-model-corner-second = 以足下為對角而添箱
vehicle-model-corner-second-look = 以所視面為對角
vehicle-model-create = 新建車模
vehicle-model-delete = 刪車模
vehicle-model-delete-warning = 永刪此模乎？尚有車用之則不可刪。
vehicle-model-deleted = 車模已刪
vehicle-model-discard = 棄草稿
vehicle-model-discard-warning = 盡棄未存之改乎？此舉不可撤復。
vehicle-model-discarded = 草稿已棄
vehicle-model-draft = 未存草稿
vehicle-model-driver = 駕座
vehicle-model-driver-here = 添駕座於足下
vehicle-model-edit = 續編草稿
vehicle-model-overview = 稿之概覽
vehicle-model-panel = 返編輯面板
vehicle-model-editor-help = { $arg0 ->
   *[overview] 先擇類，再擇物與器；存檔、撤銷皆在下欄。
    [parts] 理方塊、物品與文字。擇部件而改其材、位、轉角與比例。
    [seats] 擇座而設坐處、離座之處；須留一駕駛座。
    [colliders] 擇碰撞盒而改其中心、全長寬高；亦可增盒或依模型包圍。
    [supports] 擇支點而移之，以承車身或測舟之浮力。
    [parameters] 先擇參數之類，再點欄以改值。
    [tools] 調預覽、點選場中之物，或驗草稿。棄改須先確認。
    }
vehicle-model-section-count = { $arg0 ->
   *[parts] { vehicle-model-parts } · { $arg1 }
    [seats] { vehicle-model-seats } · { $arg1 }
    [colliders] { vehicle-model-colliders } · { $arg1 }
    [supports] { vehicle-model-supports } · { $arg1 }
    }
vehicle-model-content-action = { $arg0 ->
   *[block] 設方塊之材
    [item] 易以主手之物
    [text] 設文字之文
    }
vehicle-model-exact-input = 精確輸值
vehicle-model-axis-value = { $arg0 } · { $arg1 }
vehicle-model-component-details = 構件之詳
vehicle-model-selection-unavailable = 此物已除或已變，請返而重選
vehicle-model-exit = 出口微調
vehicle-model-group = 添主手 Axiom 顯示組
vehicle-model-hand-invalid = Axiom 顯示物之資料無效，或含未支援之實體、屬性。
vehicle-model-hand-required = 請持 Axiom 顯示實體物於主手，再行匯入。
vehicle-model-here = 取足下之位
vehicle-model-history-empty = 此向無可撤復之改
vehicle-model-import = 匯入主手 Axiom 顯示組
vehicle-model-item = 物品
vehicle-model-library = 既存車模 · 草稿未存則惟留記憶中
vehicle-model-look = 瞄準置物（閉選單，右鍵乃定）
vehicle-model-look-missing = 未中其面；請視三十二格內方塊或私覽部件。
vehicle-model-manual = 錄局部座標
vehicle-model-options = 選項
vehicle-model-parameters = 物理與引擎參數
vehicle-model-parts = 顯示部件
vehicle-model-passenger-here = 添客座於足下
vehicle-model-pick = 編所視之私覽部件
vehicle-model-pick-hint = 先開私覽而視方塊或物品部件，亦可先於編輯器選之。
vehicle-model-place-cancelled = 置物已撤，或逾時、或編輯有變、或手動撤之。
vehicle-model-place-hint = 瞄準置物限三十秒；視方塊或私覽，以主手右鍵定之，潛行右鍵則撤。
vehicle-model-position = 位置微調
vehicle-model-position-hint = 局部座標：+Y 向上，+Z 向車首。按鍵依步長移之；精確輸值定其絕對位置。
vehicle-model-preview = 私覽
vehicle-model-preview-failed = 私覽未成，草稿尚存：{ $arg0 }
vehicle-model-preview-hint = 預覽已現。欲直接點選模型，請入三維點選。
vehicle-model-redo = 重做
vehicle-model-remove = 移部件
vehicle-model-remove-warning = 移此部件乎？後序號將移，尚可撤復。
vehicle-model-rotate = 轉部件
vehicle-model-rotate-hint = 局部相對旋轉：X 俯仰、Y 偏航、Z 翻滾；位置不移。
vehicle-model-save = 存之並畢編輯
vehicle-model-save-continue = 存而續編
vehicle-model-saved = 既存
vehicle-model-scale = 縮放部件
vehicle-model-scale-all = 各軸同縮放 { $arg0 } { $arg1 }
vehicle-model-scale-hint = 乘除局部軸長，非世界外框之尺寸。
vehicle-model-seats = 座位與出口
vehicle-model-selected = 匯入已選顯示物
vehicle-model-size = 改碰撞箱大小
vehicle-model-size-hint = 全寬、全高、全長；改尺寸而中心不移。
vehicle-model-snap-off = 網格吸附：停
vehicle-model-snap-on = 網格吸附：啟
vehicle-model-step = 改步長
vehicle-model-supports = 地面承托點
vehicle-model-text = 文字
vehicle-model-title = 車模管理
vehicle-model-tools = 預覽與工具
vehicle-model-transform-step = 改調整步長
vehicle-model-undo = 撤銷
vehicle-result = 車：{ $arg0 }
vehicle-selection-info = 所選：{ $arg0 } 顯示體 · 組 { $arg1 } · 原點 { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = 碰撞／受阻
vehicle-status-damage = 車損甚重
vehicle-status-disabled = 車已停用
vehicle-status-engine-off = 引擎已熄
vehicle-status-engine-on = 引擎運轉
vehicle-status-gear-limit = 已達操控之限
vehicle-status-grounded = 舟擱淺
vehicle-status-low-fuel = 燃料將盡
vehicle-status-manual-only = 先改手動
vehicle-status-no-fuel = 燃料已盡
vehicle-status-overrev = 降檔則引擎轉速過高
vehicle-status-shifting = 換檔中
vehicle-status-stall = 引擎失速
vehicle-status-stop-first = 先停車，乃可換向或駐車
vehicle-value-off = 閉
vehicle-value-on = 啟

## World · Elevators, regeneration and spawn

async-regen-already-running = 再生之務已行；可用 /regen status 察之，或 /regen cancel 止之
async-regen-apply-expired = 今無可施之策，請先行 /regen preview
async-regen-apply-plan = 施所存之策
async-regen-apply-started = 正以 { $arg0 } 施於所存選域：{ $arg1 }
async-regen-awaiting-apply = 有策待施：{ $arg0 } · { $arg1 } · { $arg2 } 秒後失效
async-regen-block-preview-progress = 方塊預察 · 已察 { $arg0 }/{ $arg1 } 區塊 · 方塊之變 { $arg2 } · 或損方塊實體 { $arg3 }
async-regen-block-preview-summary = 預察：合 { $arg2 } 者有方塊之變 { $arg0 }，或覆既有方塊實體 { $arg1 }
async-regen-cancelled = 再生已止。既施之 { $arg0 } 變仍存於世
async-regen-command-description = 異步再生 FastAsyncWorldEdit 所選之域
async-regen-completed = 再生畢：歷 { $arg1 }，易 { $arg0 } 方塊
async-regen-failed = 再生因內誤而止。既施之 { $arg0 } 變仍存於世
async-regen-filter-all = 諸方塊之類
async-regen-invalid-blocks = 未識或非方塊者：{ $arg0 }
async-regen-not-running = 今無再生之務
async-regen-nothing-to-apply = 所選已合此策，無變可施
async-regen-plan-cancel = 罷
async-regen-plan-dialog-title = 施再生之策
async-regen-preview-cancelled = 預察已止，世之資料未易
async-regen-preview-started = 預察始：所選 { $arg0 } 方塊，跨 { $arg1 } 區塊；其式 { $arg2 }
async-regen-progress = { $arg0 } · 已籌 { $arg1 }/{ $arg2 } 區塊 · 已施 { $arg3 } 變
async-regen-safety-note = 所存選域之外必不易；域內之築或覆，原版亦不識玩家所置之常方塊。施前宜察其象，備其世。
async-regen-scope-blocks = 再生合 { $arg0 } 之方塊
async-regen-selection-required = 請先完成 FastAsyncWorldEdit 選域
async-regen-skipped-ungenerated = 未嘗生之目標區塊 { $arg0 } 皆略之，亦未造未易
async-regen-start-failed = 再生未能始，詳見伺服器誌
async-regen-state-applying = 正施變更
async-regen-state-cancelled = 已止
async-regen-state-completed = 已畢
async-regen-state-failed = 已敗
async-regen-state-generating = 正生且較區塊
async-regen-state-preparing = 正備源世
async-regen-status = { $arg0 } · 已籌 { $arg1 }/{ $arg2 } 區塊 · 所見 { $arg4 } 變中已施 { $arg3 }
async-regen-structure-metadata-applied = 已移營造之始 { $arg0 }、區塊之引 { $arg1 }；/locate 與依營造而生者皆可識之
async-regen-upgrade-apply-progress = { $arg0 } · 已籌 { $arg1 }/{ $arg2 } 區塊 · 已施方塊 { $arg3 }、生境格 { $arg4 }
async-regen-upgrade-boundary-note = 臨所選之界者有 { $arg0 } 生境格，為不及界外，皆略之
async-regen-upgrade-completed = 升世畢：歷 { $arg2 }，易方塊 { $arg0 }、生境格 { $arg1 }
async-regen-upgrade-preview-progress = 升世預察 · 已察 { $arg0 }/{ $arg1 } 區塊 · 方塊之變 { $arg2 } · 生境格 { $arg3 } · 營造 { $arg4 }
async-regen-upgrade-preview-summary = 預察：方塊之變 { $arg0 }，生境格 { $arg1 }，營造 { $arg2 }（元據可全移者 { $arg3 }），或損既有方塊實體 { $arg4 }
async-regen-upgrade-scope-biomes = 惟生境
async-regen-upgrade-scope-terrain = 地勢、營造與生境
async-regen-usage = 用法：/regen preview blocks [類...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = 選域之象見於 { $arg0 }：青者界，橙者所易方塊之樣，赤者或覆之方塊實體；此粒惟汝可見
async-regen-visualization-selection = { $arg0 } 中青粒示選域之界，惟汝可見
async-regen-fawe-required = 欲讀所選之域，須有 FastAsyncWorldEdit
building-map-layer = 建築錄
building-map-open = 觀其詳
building-map-builders = 營造者：{ $arg0 }
building-map-original-author = 原作者：{ $arg0 }
building-map-type-original = 獨創
building-map-type-derivative = 衍作
building-map-type-replica = 摹建
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ 蹲
iron-elevator-tip-mm = 上下相對置{ -iron-elevator-block }，即可作升降臺；立其上，<green>躍則升</green>、<aqua>潛行則降</aqua>；達二層者，顯<gold>選層之板</gold>
iron-elevator-up = ↑ 躍
spawn-beacon-color-command-description = 易主城烽火臺流彩
spawn-beacon-color-disabled = 主城烽火臺流彩既閉
spawn-beacon-color-enabled = 主城烽火臺流彩既啟
spawn-command-description = 還主城
spawn-tip-return-mm = 以 <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> 歸<yellow>主城</yellow>
spawn-world-missing = 主城所在之生界不可用

## Entertainment · Music

music-album = 曲集：{ $arg0 }
music-artist = 樂人：{ $arg0 }
music-back = 還
music-command-description = 留聲機之本地、網上與 NBS 樂曲
music-disc-left = 擊左鍵：取盤
music-disc-right = 擊右鍵：奏於至近之留聲機
music-duration = 曲長：{ $arg0 }
music-format = 格式：{ $arg0 }
music-jukebox-audible-field-conflict = 他留聲機之顯聞域相重，而奏異曲
music-jukebox-tp-hover = 擊以移形至留聲機
music-jukebox-unavailable = 留聲機既除，或其區不可用
music-now-playing-rich = 方奏：{ $music }，留聲機在 { $location }
music-online-rate-limited = 求曲過繁，{ $arg0 } 秒後重試。
music-original-author = 原作者：{ $arg0 }
music-plasmo-voice-unavailable = 曲雖已奏，然 Plasmo Voice 未裝、未接或未啟，故不可聞
music-playback-failed = 「{ $arg0 }」不可奏。請察聲源或樂檔而復試。
music-reload-failed = 復載樂曲未成：{ $arg0 }
music-reload-started = 方復載樂檔與自定聲源⋯
music-rhythm-animation-offset = 動畫偏移 { $arg0 } 毫秒
music-rhythm-calibration-accumulating = 正驗其穩…請續隨擊
music-rhythm-calibration-adapting = 正合君拍…請續隨擊
music-rhythm-calibration-done = 延遲試畢 · 設定已存
music-rhythm-calibration-failed = 延遲試音忽斷，今已安止，請復啟之
music-rhythm-calibration-intro = Minecraft音符盒 → Plasmo Voice → 鍵盤視覺 → 鼠標視覺
music-rhythm-calibration-listen = 先聆所生節拍一巡 · 姑勿按鍵
music-rhythm-calibration-minecraft-instruction = 依著Minecraft音符盒節拍按一至四
music-rhythm-calibration-no-input = 未察按鍵；宜省鍵位聲量，而後續擊
music-rhythm-calibration-out-of-range = 所測之遲逾可用之域；宜省聲設而續擊
music-rhythm-calibration-plasmo-instruction = 今隨拍：依方纔 Plasmo Voice 節拍，每鼓按一至四任一鍵
music-rhythm-calibration-plasmo-ready = Minecraft音符盒試畢；先以Plasmo Voice聆同一節拍
music-rhythm-calibration-pointer-instruction = 方越線時擊鼠標左右鍵
music-rhythm-calibration-realigning = 聲流暫絕；今復演之，既得之樣不棄
music-rhythm-calibration-required = 戲節奏前須畢全延遲試
music-rhythm-calibration-reset-done = 延遲校準已復原
music-rhythm-calibration-result-hold = 諸試皆畢，請覽其果，頃刻自返
music-rhythm-calibration-stage-minecraft = Minecraft 音符盒
music-rhythm-calibration-stage-plasmo = Plasmo Voice 聲音與輸入試
music-rhythm-calibration-stage-pointer = 鼠標
music-rhythm-calibration-stage-visual = 畫面與輸入試
music-rhythm-calibration-visual-instruction = 方越線時按一至四
music-rhythm-calibration-visual-preview = 先觀方塊過線一巡而勿動 · 待辭令變乃始擊
music-rhythm-calibration-voice-required = 全延遲試須接通並啟用 Plasmo Voice
music-rhythm-chart-preparing = 正備全譜…
music-rhythm-combo = 連擊 ×{ $arg0 }
music-rhythm-difficulty-easy = 易
music-rhythm-difficulty-expert = 極
music-rhythm-difficulty-hard = 難
music-rhythm-difficulty-normal = 常
music-rhythm-exit = 離節奏戲
music-rhythm-get-ready = 備
music-rhythm-go = 始！
music-rhythm-good = 良
music-rhythm-great = 精準
music-rhythm-latency = 遲滯 { $arg0 } ms
music-rhythm-latency-test = 全延遲試
music-rhythm-miss = 失拍
music-rhythm-mode-falling = 垂落式
music-rhythm-mode-radial = 環向襲
music-rhythm-mode-spatial-aim = 星域擊
music-rhythm-network-too-high = 網路延遲過甚，判定難公（{ $arg0 } ms；上限 { $arg1 } ms）
music-rhythm-network-warning = 網路延遲不穩（{ $arg0 } ms）；若久逾 { $arg1 } ms，則安止此局
music-rhythm-no-beats = 此曲未得可戲之拍
music-rhythm-perfect = 盡善
music-rhythm-requires-playback = 請先令此留聲機奏樂
music-rhythm-results = 戰績
music-rhythm-score = 得分：{ $arg0 }
music-rhythm-spatial-no-room = 四周隘狹，不足以行星域擊，請移步曠處。
music-rhythm-spatial-slider-hint = 隨動星而轉視角，逐節點擊之 · 無須長按
music-rhythm-start = 始遊
music-sample-rate = 採聲之率：{ $arg0 }
music-size = 大小：{ $arg0 }
music-unavailable-disc = 不可用之盤

## Entertainment · Anniversary

anniversary-accepted = 已赴五載抽獎。非離席在線滿四時，可行首回抽獎。
anniversary-admin-audit-action-bonus = 發建築收錄獎機會
anniversary-admin-audit-action-gift = 贈活動獎：{ $arg0 }
anniversary-admin-audit-action-removal = 除管理者舊與錄，回補獎 { $arg0 } 件
anniversary-admin-audit-action-unknown = 未知操作：{ $arg0 }
anniversary-admin-audit-empty = 暫無管理稽核錄。
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = 操者：{ $arg0 } ({ $arg1 })；目標：{ $arg2 } ({ $arg3 })
anniversary-admin-audit-title = 五週年管理稽核（示 { $arg0 }/{ $arg1 } 條）
anniversary-admin-bonus-cancelled = 因大禮包而止
anniversary-admin-bonus-pending = 已發，未抽
anniversary-admin-bonus-used = 已抽
anniversary-admin-boss = 五週年活動管理 · 輸 { $arg0 } 觀進度
anniversary-admin-commands = 命：/anniversary winners [頁] | /anniversary audit [玩家] | /anniversary bonus <玩家>
anniversary-admin-control = 動態制御：已釋 { $arg0 }；今可發 { $arg1 }；本機率 { $arg2 }；近速 { $arg3 }
anniversary-admin-draw-bonus = 建築收錄獎
anniversary-admin-draw-detail = { $arg0 }：抽時 { $arg1 }；果 { $arg2 }
anniversary-admin-draw-metrics = 率 { $arg0 }；隨機數 { $arg1 }；算法版 { $arg2 }
anniversary-admin-draw-replaced = 已為大禮包所代並還庫
anniversary-admin-draw-round = 常規第 { $arg0 } 輪
anniversary-admin-event = 活動狀：{ $arg0 }；期：{ $arg1 }
anniversary-admin-excluded = 五週年活動之管理者不得與抽獎，已開管理概覽。
anniversary-admin-none = 無
anniversary-admin-opportunities = 已抽：{ $arg0 }；今可抽：{ $arg1 }；已得機會：{ $arg2 }；中/未中：{ $arg3 }
anniversary-admin-participants = 與者：{ $arg0 }
anniversary-admin-player-bag = 終活動囊：{ $arg0 }
anniversary-admin-player-bonus = 建築獎：{ $arg0 }；發時：{ $arg1 }；發者：{ $arg2 } ({ $arg3 })
anniversary-admin-player-history = 抽獎機會詳
anniversary-admin-player-not-found = 未得活動與者：{ $arg0 }
anniversary-admin-player-outcome = 中：{ $arg0 }；未中：{ $arg1 }；已抽次：{ $arg2 }；建築獎：{ $arg3 }
anniversary-admin-player-progress = 與時：{ $arg0 }；有效在線：{ $arg1 }；已抽常規輪：{ $arg2 }；今可抽常規機會：{ $arg3 }
anniversary-admin-player-title = 玩家稽核：{ $arg0 } ({ $arg1 })
anniversary-admin-state-active = 進行中
anniversary-admin-state-ended = 已竟
anniversary-admin-state-scheduled = 未始
anniversary-admin-stock = 餘庫：{ $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | 共 { $arg4 }
anniversary-admin-target-excluded = { $arg0 } 為五週年活動管理者，不得獲抽獎機會。
anniversary-admin-title = 五週年活動管理概覽
anniversary-admin-winners-empty = 暫無實抽之中獎錄。
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · 抽時 { $arg3 }
anniversary-admin-winners-title = 中獎發放錄（第 { $arg0 }/{ $arg1 } 頁，共 { $arg2 } 件）
anniversary-bag-empty = 尚無中獎之物
anniversary-bag-item-locked = 活動囊唯可觀，不可取
anniversary-bag-item-name = 五載獎品 · { $arg0 }
anniversary-bag-not-participating = 尚未參與，請先輸 { $arg0 }。
anniversary-bag-title = 五載活動囊
anniversary-bag-updated = 獎已納活動囊，輸 { $arg0 } 察之。
anniversary-bonus-already-granted = { $arg0 } 已得建築收錄之獎機，不可重授。
anniversary-bonus-boss-ready = 建築收錄獎抽獎已可用 · 輸 { $arg0 } 抽之
anniversary-bonus-broadcast-won = [五載抽獎] { $arg0 } 以建築收錄之獎得 { $arg1 }！
anniversary-bonus-draw-not-won = 建築收錄獎抽獎無所得。
anniversary-bonus-draw-sold-out = 建築收錄獎抽獎無所得：獎品已罄。
anniversary-bonus-draw-won = 建築收錄獎抽獎：賀汝得 { $arg0 }！
anniversary-bonus-gift-pack-final = { $arg0 } 已中大禮包，不得再授建築獎抽。
anniversary-bonus-grant-not-active = 今非活動期內，不得授建築收錄之獎機。
anniversary-bonus-grant-success = 已授 { $arg0 } 建築收錄之獎機。
anniversary-bonus-granted = 汝已得建築收錄之獎，一回額外抽獎可用；輸 /anniversary 抽之。
anniversary-bonus-participant-not-found = 未得已參活動之戲者：{ $arg0 }
anniversary-bonus-status-ready = 建築收錄獎：一回額外抽獎已可用。
anniversary-bonus-status-used = 建築收錄獎：額外抽獎已用。
anniversary-boss-completed = 五載抽獎 · 二回皆畢
anniversary-boss-gift-pack-final = 已得五載大禮包 · 輸 { $arg0 } 察活動囊
anniversary-boss-invite = 伺服器五載紀念抽獎 · 輸 { $arg0 } 參與
anniversary-boss-progress = 五載抽獎 { $arg0 }/{ $arg1 } · 距第 { $arg2 } 回 { $arg3 }
anniversary-boss-ready = 五載第 { $arg0 } 回已可抽 · 輸 { $arg1 } 抽之
anniversary-broadcast-won = [五載抽獎] { $arg0 } 於第 { $arg1 } 回得 { $arg2 }！
anniversary-cancel = 姑不參與
anniversary-command-description = 赴伺服器五載紀念抽獎
anniversary-confirm = 諾而參與
anniversary-confirm-hint = 諾後始計非離席在線之時
anniversary-delivery-note = 收件地若在中國大陸，請呈可供隱私寄件之有效順豐 ID；若在中國大陸以外，請呈收件人真名、完整地址及有效電話，並繳應付運費，否則視為棄領。
anniversary-dialog-prizes = 獎品：{ $arg0 } 二十、{ $arg1 } 十、{ $arg2 } 二十、{ $arg3 } 五
anniversary-dialog-time = 其期：{ $arg0 }
anniversary-dialog-title = 五載紀念物抽獎章程
anniversary-draw-not-won = 第 { $arg0 } 回無所得。
anniversary-draw-sold-out = 第 { $arg0 } 回無所得：獎品已罄。
anniversary-draw-won = 第 { $arg0 } 回：賀汝得 { $arg1 }！
anniversary-ended = 五載抽獎已畢
anniversary-ended-results = 汝之抽獎所得：{ $arg0 }
anniversary-gift-pack-final = 大禮包已為終獎，代他獎 { $arg0 } 件並還其庫；餘抽皆止。
anniversary-gift-prize-not-owned = 囊中無此獎：{ $arg0 }
anniversary-gift-received = { $arg0 } 贈汝 { $arg1 }，已納活動囊。
anniversary-gift-self = 不得自贈其獎。
anniversary-gift-success = 已以 { $arg0 } 贈 { $arg1 }。
anniversary-gift-usage = 用法：{ $arg0 }
anniversary-not-active = 今非五載之慶期內
anniversary-not-started = 五載之慶未始，其期：{ $arg0 }
anniversary-prize-badge = 五載合照徽章
anniversary-prize-gift-pack = 五載大禮包
anniversary-prize-keychain = Logo 鑰匙扣
anniversary-prize-mug = Logo 馬克杯
anniversary-prize-not-won = 無所得
anniversary-prize-sold-out = 獎品已罄
anniversary-rule-1 = 一、許諾參與後，每四時可抽一回，凡二回；中獎之物納活動虛囊，同類至多一件。若中大禮包，唯留此包，餘抽皆止。
anniversary-rule-2 = 二、離席之時不入抽獎計時；禁以任何法規避離席之判。
anniversary-rule-3 = 三、實物之運費，伺服器任其八成，然至多五十元；總費減此數後之餘額，悉由中獎者任之。
anniversary-rule-4 = 四、中獎後，收件地若在中國大陸，須呈可供隱私寄件之有效順豐 ID；若在中國大陸以外，須呈收件人真名、完整地址及有效電話。未依令呈資或繳應付運費者，視為棄領。
anniversary-rule-5 = 五、禁以多號重複參與，犯者奪其資格。
anniversary-rule-6 = 六、參與即視為允此諸條。
anniversary-rule-7 = 七、官網所錄建築，搬運計零點二，二創計零點五，原創計一分；活動前已錄者乘零點八，共建者按己之貢獻比例乘一點五。總分滿二，可增一回抽獎，由管理者手授。
anniversary-second-round-hint = 累計非離席在線滿八時，可行次回抽獎。
anniversary-status-completed = 汝已畢二回抽獎。
anniversary-status-gift-pack-final = 已中大禮包，終獎既定，餘抽皆止。
anniversary-status-progress = 已成：{ $arg0 }/{ $arg1 } 回；有效在線：{ $arg2 }
anniversary-status-remaining = 距第 { $arg0 } 回尚須非離席在線 { $arg1 }。
anniversary-status-results = 所得：{ $arg0 }
anniversary-status-title = 五載抽獎進度

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } 已禁
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [禁令公報]
ban-command-description = 依定等禁玩家
ban-database-error = 封禁資料庫操作未成，請察伺服器誌。
ban-menu-back = 返
ban-menu-confirm-action = 施禁
ban-menu-confirm-severity = 禁等
ban-menu-confirm-title = 定禁
ban-menu-never-joined-confirm-action = 仍禁之
ban-menu-never-joined-title = 此人未嘗入服
ban-menu-never-joined-warning = { $arg0 } 未嘗入此服。請復核其名，以免誤禁他人。
ban-menu-player-input = 手書玩家名
ban-menu-select-title = 擇玩家
ban-menu-severity-title = 擇禁等
ban-history-empty = 無 { $arg0 } 之封禁舊錄。
ban-history-title = 封禁舊錄：{ $arg0 }
ban-invalid-player = 玩家名不正
ban-invalid-severity = 禁等不正
ban-kick-appeal = 若以為誤，請於 QQ 群 1138627074 申訴
ban-kick-server = 米客
ban-kick-title = 君已為此服所禁
ban-not-banned = { $arg0 } 目下未受封禁。
ban-pardon-reason-required = 解禁必具由
ban-permanent = 永久
ban-reason-required = 封禁必具由
ban-self = 不可自禁
ban-severity-excusable = 情有可原（三十日）
ban-severity-extreme = 極重（一千五百日）
ban-severity-minor = 小過（一百五十日）
ban-severity-severe = 重過（三百六十日）
ban-severity-warning = 警告（三日）
ban-status-active = 現行
ban-status-expired = 已期滿
ban-status-revoked = 已赦
ban-success = 已禁 { $arg0 }，至 { $arg1 }
ban-sync-success = 封禁既同步：現行 { $arg0 } · 舊錄 { $arg1 }
ban-system-source = 系統
ban-unban-success = 已赦 { $arg0 }
ban-unknown-reason = 無由
ban-update-success = 已更 { $arg0 } 封禁欄：{ $arg1 }
banlist-command-description = 覽禁錄
banlist-copy = 點之以複玩家名
banlist-empty = 今無禁者
banlist-expires = 解禁
banlist-invalid-page = 頁碼有誤，凡 { $arg0 } 頁。
banlist-next = 後頁
banlist-page = 第 { $arg0 }/{ $arg1 } 頁 · 共 { $arg2 } 條
banlist-player = 玩家
banlist-previous = 前頁
banlist-reason = 由
banlist-source = { role-moderator }
banlist-title = 禁錄
maintenance-command-description = 啟閉維護之式，並察其態
maintenance-kick = 伺服維護中
maintenance-set = 維護之式已{ $arg0 }
maintenance-state-disabled = 閉
maintenance-state-enabled = 啟
maintenance-status = 維護之式：{ $arg0 }
maintenance-unchanged = 維護之式已{ $arg0 }，無所更
performance-kick-title = 伺服遲滯甚重
performance-kick-reason = 暫斷連線，以待恢復
restriction-entity-too-large-mm = <red>生靈之形過伺服器所容</red>
restriction-foreign-uuid-mm = <red>令中惟可用君所馴之生靈 UUID</red>
restriction-no-kill-e-mm = <red>不得用 <white>/kill @e</white> 令</red>
restriction-no-newline-mm = <red>訊中不得易行</red>
restriction-no-run-command-mm = <red>不得誘人行令</red>
restriction-no-selector-mm = <red>君無用目標擇器 <white>@e @a @r @p @n</white> 之權</red>
restriction-other-player-name-mm = <red>君無於令中提及他戲者之名之權</red>
tpsbar-command-description = 顯隱 TPS 之欄
tpsbar-hidden = TPS 之欄既隱
tpsbar-shown = TPS 之欄既顯
tpsbar-tip-status-mm = 以 <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> 啟閉<yellow>TPS、MSPT 與延遲</yellow>欄
unban-command-description = 赦玩家而留封禁之錄
whitelist-already-whitelisted = { $arg0 } 已在準簿中
whitelist-invalid-name = 戲者之名式有誤
whitelist-kick-apply-rich = 往 { $site } 求入準簿
whitelist-kick-title = 君未在準簿中
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = { $arg0 } 既納暫準簿，限 { $arg1 }
whitelist-temp-added-rich = { $player } 既納暫準簿，限 { $duration }
whitelist-temp-command-description = 納一時之暫準簿
whitelist-temp-duration = 1 時
whitelist-temp-usage-desc = 納一時之暫準簿

## Shell · Menus, server information and scripting

main-afk-status =
    { $arg0 ->
        [afk] AFK：暫離
       *[online] AFK：在綫
    }
main-pvp-status =
    { $arg0 ->
        [on] PVP：開
       *[off] PVP：關
    }
main-announcements = 告示
main-home = 歸處
main-language = 言文
main-language-auto = 自辨
main-links = 外鏈
main-map = 輿圖
main-menu-title = 主簿
main-music = 絲竹
main-name-tag = 名籤
main-settings = 設定
main-website = 官網
main-wiki = Wiki
skript-command-description = 觀伺服器腳本所能
skript-info-ai-option = 不欲自作？可持下詞授 AI：
skript-info-copy = [錄 AI 提示詞]
skript-info-copy-hover = 錄版本、文獻連結與安全之則
skript-info-documentation = 語法與 API 文獻
skript-info-review = 腳本必經管理者核驗試行，然後載入
skript-info-supported = 今可用：{ $arg0 }
skript-info-title = 伺服器腳本
skript-info-workflow = 循文獻作成並核 .sk 檔，乃呈管理者
stat-client-version = 客端版本
stat-first-joined = 初入
stat-location = 所在
stat-ping = 延遲
stat-ping-value = { $arg0 } 毫秒
stat-play-time = 嬉遊之時
stat-role = 身份
stat-world = 生界
web-account-title = 網頁帳戶與通行金鑰
web-account-confirm = 確認之
weblogin-question = 確認此網頁身分驗證乎？惟認己於 mcmik.top 所發之請，勿代他人輸碼。
weblogin-command-description = 確認 Mik 網頁身分驗證碼
weblogin-confirmed = 網頁身分已驗，請返瀏覽器續行
weblogin-invalid-code = 網頁身分驗證碼無效


# Music workspace
music-space-label =
    { $arg0 ->
        [title] 音乐空间
        [devices] 播放设备
        [all-devices] 諸樂器
        [use-device] 用此器
        [current-device] 現用之器
        [manage-device] 理器
        [back-devices] 返樂器目
        [group-with-devices] 與他器合奏
        [pick-world-member] 於世中擇一器
        [group-world-pick-hint] 空手潛行右擊唱片機以入選；/music 可返。
        [group-member-standalone] 請擇未入合奏組之唱片機。
        [group-selection-help] 可勾目中唱片機，亦可空手潛行右擊他器。提交乃合組。
        [group-source-help] 取所擇器之曲序與進度。建組則止原奏；建而奏之則續奏。
        [device-actions] 用事
        [device-status] 狀態
        [sync-group] 同步組
        [single-device] 唱片機
        [device-location] 所在
        [visit-device] 移至旁側
        [teleporting] 正移送…
        [recorded-device] 已誌
        [nearby-device] 近旁
        [group-select-members] 擇唱片機二具以上
        [group-name-required] 請題同步組名
        [direction-front] 前
        [direction-front-right] 右前
        [direction-right] 右
        [direction-back-right] 右後
        [direction-back] 後
        [direction-back-left] 左後
        [direction-left] 左
        [direction-front-left] 左前
        [direction-here] 近旁
        [direction-above] 上
        [direction-below] 下
        [no-devices] 尚無設備
        [choose-members] 擇成員設備
        [create-group] 建同步組
        [add-members] 添設備
        [create-play] 建組並奏
        [create-and-append] 建組並續添曲目
        [device-queue] 待奏
        [device-members] 成員
        [available] 可用
        [play] 奏樂
        [retry-outputs] 重試輸出
        [dissolve-group] 解散同步組
        [remove-member] 移出同步組
        [removed-stopped] 移出之設備停奏，保留所擇內容。
        [restore-previous] 復入組前內容
        [keep-group-content] 留組內內容
        [queue] 播放队列
        [back] 返回
        [close] 关闭
        [unavailable] 音乐存储不可用
        [saving] 正在保存…
        [saved] 已保存
        [apply] 应用
        [cancel] 取消
        [select-page] 选中本页
        [select-all] 选中全部结果
        [clear-tracks] 清除歌曲选择
        [add-playlist] 入曲單
        [description] 描述
        [make-private] 设为私有
        [publish] 设为公开
        [delete] 删除歌单
        [favorite] 收藏或取消收藏
        [copy] 复制歌单
        [share] 分享歌单命令
        [recent] 最近播放
        [favorites] 收藏歌单
        [public] 公开
        [private] 私有
        [new-playlist] 新建歌单
        [name] 名称
        [song-title] 曲名
        [track-count] 曲數
        [visibility] 可見
        [no-playlists] 尚無曲單
        [fetching-playlist] 讀取曲單中…
        [artist] 歌者
        [duration] 曲長
        [track-type] 類型
        [type-online] 線上
        [undo] 撤销上次编辑
        [remove] 移除所选条目
        [clear-recents] 清空我的播放记录
        [clear-entries] 清除条目选择
        [search-tracks] 尋曲、歌者…
        [search-playlists] 尋曲單…
        [public-playlists] 公开歌单
        [searching] 正在搜索…
        [insert] 插曲即奏
        [append] 續入待奏
        [choose-targets] 選奏曲之器
        [group-source] 所取播放佇列與進度
        [removed-targets] 有機已除，請重選
        [offline] 离线／区块未加载
        [device-discovery-hint] 空手潛行右擊唱片機以啟之。
        [empty] 無曲
        [queue-empty] 無待播曲
        [queue-current] 數位待播之當前曲
        [pause] 暂停
        [resume] 继续播放
        [stop] 停止，保留播放列表
        [previous] 上一首／从头播放
        [clear] 清空待播，当前播完停止
        [seek] 移時
        [more] 其他
        [volume-control] 音量
        [group-volume] 合奏總音量
        [timeline-help] 滾輪移五秒；右擊輸時
        [timeline-read-only] 此曲不可移時
        [seek-format] 輸 m:ss、h:mm:ss 或秒數
        [record] 錄以遠控
        [forget] 移除我的设备记录
        [alias] 个人设备别名
        [sound] 声音设置
        [rhythm] 音遊房
        [room-players] 玩家
        [room-player-status] 狀態
        [room-preparation] 吾之準備
        [room-ready] 準備
        [room-cancel-ready] 撤銷準備
        [room-playing] 遊戲中
        [room-score-ready] 譜面已備
        [room-select-one] 請於曲庫啟用多選，擇一曲。
        [room-end-confirmation] 此舉將結束全體參與者之音遊房。
        [create-room] 建房
        [no-rhythm-devices] 近處無唱片機，請空手潛行右擊唱片機。
        [calibration] 延迟校准
        [volume] 音量（0–100）
        [range] 范围（8–256 格）
        [mode] 奏式
        [save-queue] 存待播為曲單
        [sequential] 顺序播放
        [repeat-all] 列表循环
        [repeat-one] 单曲循环
        [shuffle] 随机播放，轮内不重复
        [import] 导入歌单
        [link] 曲單連結
        [partial-import] 仅获取部分歌曲，保存将只包含已获取部分
        [import-preview] 预览已获取信息，尚未下载音频
        [import-new] 新建並開啟
        [import-merge] 合入曲單
        [stopped] 已停止
        [loading] 正在准备音频
        [cancel-loading] 止備
        [playing] 正在播放
        [paused] 已暂停
        [finished] 播放结束
        [retry-failed] 重試奏樂
        [select-song] 勾選曲目，可合播或入歌單
        [select-toggle] 擇此項
        [deselect] 釋此項
        [selected] 已擇
        [place-before] 移所擇於此項前
        [eject] 弹出实体唱片
        [make-disc] 制作签名唱片
        [gain] 同步组总音量（0–100）
        [retry-import] 重新获取歌单
        [join-room] 入房
        [ready] 已備
        [waiting] 等待中
        [room-mode] 玩法
        [room-difficulty] 難度
        [late-join] 中途加入游戏
        [leave-room] 离开房间
        [select-room-track] 用所選曲
        [start-room] 開始遊戲
        [end-room] 結束房間
        [base-queue] 基础播放列表
        [priority-queue] 一次性待播
        [falling] 下落式
        [radial] 环向来袭
        [spatial-aim] 星域点击
        [easy] 轻松
        [normal] 标准
        [hard] 困难
        [expert] 专家
        [waiting-for-player] 等待准备
        [preparing] 正在准备音频和谱面
        [no-beats] 没有可玩节拍
        [catalog] 曲庫
        [my-playlists] 吾之曲單
        [playlist] 曲單
        [output] 奏曲之器
        [close-panel] 收起側欄
        [playlist-settings] 曲單設定
        [multi-select] 多選
        [move] 移曲
        [move-hint] 選目標曲，置於其前
        [cancel-move] 取消移曲
        [move-end] 置於末尾
        [cancel-selection] 罷多選
        [track-details] 曲目詳情
        [skip-next] 轉奏次曲
        [device-settings] 樂器設定
        [already-added] 已在曲單
        [current-track] 今曲
        [shuffle-candidates] 隨機候選曲
        [import-by-id] 以 ID 匯入
        [playlist-id] 曲單 ID
       *[unknown] 音乐
    }

music-space-error =
    { $arg0 ->
        [UNAVAILABLE] 音乐暂不可用，请稍后重新打开
        [INTERNAL] 樂務有誤，請告管理員查伺服器誌
        [NOT_FOUND] 所选对象已不存在
        [FORBIDDEN] 需要控制或编辑权限；请检查地段授权及设备记录
        [CONFLICT] 内容已变化，请刷新并重新选择
        [LIMIT] 已达到音乐资源上限
        [INVALID] 请检查名称、所选对象或数值范围
        [OFFLINE] 设备不可用，请到现场加载区块或弹出实体唱片
        [OVERLAP] 听闻范围重叠：请停止冲突设备、缩小范围或建立同步组
        [UNSUPPORTED] 此媒体不支持该操作
        [BUSY] 设备、请求或音游房间正在使用，请结束后重试
        [NO_SAFE_DESTINATION] 唱片機旁無可安立之處
        [TELEPORT_FAILED] 移送失敗或遭阻止
       *[UNKNOWN] 操作失败
    }

music-space-selected-tracks = 已擇 { $arg0 } 曲
music-space-track-count = { $arg0 } 曲
music-space-device-count = { $arg0 } 臺唱片機
music-space-playback-target = 奏於：{ $arg0 }
music-space-online-count = 在線：{ $arg0 }／{ $arg1 } 機
music-space-device-distance = { $arg0 } · { $arg1 } 格
music-space-device-height = { $arg0 ->
    [above] 高 { $arg1 } 格
   *[below] 低 { $arg1 } 格
    }
music-space-device-coordinates = { $arg0 } · X { $arg1 } · Y { $arg2 } · Z { $arg3 }
music-space-sound-value = { $arg0 ->
        [volume] { $arg1 ->
           *[other] { $arg1 }%
        }
       *[range] { $arg1 ->
           *[other] { $arg1 } 格
        }
    }
plot-permission-music-control = 控制音乐设备

music-partial-results = 今示部分之果，有 { $arg0 } 聲源請求未成

webchat-tab-label = { $arg0 ->
    [1] { player-web-label }
   *[other] { player-web-label } ×{ $arg0 }
    }
webchat-joined = { $arg0 } 入網頁言談
webchat-left = { $arg0 } 離網頁言談
chat-rate-limited = 言語過頻，稍候再試

plot-map-corner-a = 角 A
plot-map-corner-b = 角 B
plot-map-corner-hint = 按以聚焦此角；右按輸座標，滾輪升降四格
plot-map-coordinates = 座標 x,y,z
plot-map-zoom-in = 放大
plot-map-zoom-out = 縮小
plot-map-fit = 覽全域
plot-map-near = 返吾側
plot-map-layer = 層 Y { $arg0 }…{ $arg1 }
plot-map-layer-hint = 滾輪易所示之層，按則返所立層；底圖恆示地表
plot-map-north = 北移
plot-map-west = 西移
plot-map-east = 東移
plot-map-south = 南移
plot-map-scale = 每像素 { $arg0 } × { $arg0 } 方塊
plot-map-progress = 讀圖 { $arg0 }%…
plot-map-unknown = 未載地表之取樣處：{ $arg0 }
plot-map-legend = 青：留 · 紫：增 · 橙：減 · 綠：矩形 · A/B：角 · 箭：吾
plot-selection-validating = 後臺正算疆界、核其範圍，畢則自更。

plot-map-open = 覽原生輿圖

plot-map-handheld-hint = 副手持原生輿圖，主手以木斧定角；潛行＋F 返面板

plot-map-readonly-hint = 今惟覽圖；欲易角，返面板擇「調整此矩形」

plot-destinations = 傳送處
plot-destination-add = 增此處
plot-destination-confirm-delete = 刪傳送處「{ $arg0 }」乎？
plot-destination-delete = 刪傳送處
plot-destination-deleted = 傳送處已刪。
plot-destination-description = 簡介（可空）
plot-destination-edit = 改名與簡介
plot-destination-empty = 尚無可用傳送處。
plot-destination-error-changed = 傳送處已變，請重啟其頁。
plot-destination-error-description = 簡介至多五百一十二字，不得含控制字元。
plot-destination-error-location = 請立於此地段內之安全地面。
plot-destination-error-missing = 此傳送處已無。
plot-destination-error-name = 名須單行，一至四十八字。
plot-destination-error-private = 此處未公開，惟地段成員可用。
plot-destination-error-unsafe = 此處不可用，或落處不安。
plot-destination-loading = 正載入傳送處…
plot-destination-move = 改為此處
plot-destination-name = 名
plot-destination-public-empty = 尚無公開傳送處。
plot-destination-public-off = 公開：否
plot-destination-public-on = 公開：是
plot-destination-saved = 傳送處已存。
plot-destination-teleport = 傳送
plot-error-subplot-destination = 請先移去或刪除所選範圍內之母地段傳送處。
plot-error-subplot-own-destination = 請留傳送處於新界內，或先移去、刪除。

plot-destination-manage = 管理
plot-directory-owned = 吾所擁 · { $arg0 }
plot-directory-participating = 吾所參與 · { $arg0 }
plot-directory-search = 尋地段
plot-directory-search-value = 所尋：{ $arg0 }
plot-directory-search-field = 地段名或編號
plot-directory-clear-search = 清除所尋
plot-directory-empty = 此類尚無地段。
plot-directory-no-match = 無相符地段，請改所尋或清之。
plot-workspace-area = 編輯範圍
plot-workspace-area-hint = 調整此地段之保護範圍
plot-workspace-subplots = 子地段
plot-workspace-subplots-hint = 查閱、建立及管理內部分界
plot-workspace-access-hint = 管理成員、身分及操作權限
plot-workspace-settings-hint = 名稱、公告、環境及所有權
plot-workspace-destinations-hint = 查閱並前往此地段之傳送處
plot-workspace-role = 現身分：{ $arg0 }
plot-workspace-ownership = 所有權與刪除
plot-workspace-id = 地段編號：{ $arg0 }
plot-workspace-groups = 權限組
plot-workspace-permissions = 操作權限
plot-workspace-other-world = 請先至此地段所在之世界。

plot-destination-titles = 傳送題辭
plot-destination-title = 正題
plot-destination-subtitle = 副題
plot-destination-title-hint = 傳送既成乃顯。可用 MiniMessage 設色、字式與漸變；留白不顯，預覽不存。
plot-destination-title-preview = 全屏預覽
plot-destination-title-unset = 未設
plot-destination-title-save = 存題辭
plot-destination-title-unchanged = 尚無待存之改。
plot-destination-title-apply = 施於草稿
plot-destination-error-title = 正題限一行，所顯至多三十二字，MiniMessage 原文至多五百一十二字。
plot-destination-error-subtitle = 副題限一行，所顯至多六十四字，MiniMessage 原文至多五百一十二字。
