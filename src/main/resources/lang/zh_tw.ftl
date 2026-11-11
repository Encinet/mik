### MIK · zh-tw
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = 地段
    .gender = other

-vehicle-model = 模型
    .gender = other

## Shared · General

back = 返回
back-to-main = 返回主選單
cancel = 取消
click-open = 點擊開啟
close = 關閉
current-off = 目前: 關閉
current-on = 目前: 開啟
player-only = 該指令只能由玩家執行
unknown = 未知
usage = 用法

## Shared · Language, roles and time

language-command-description = 開啟語言設定
language-menu-title = 語言設定
language-selected = 已選擇
language-set = 語言已設定為 { $arg0 }
role-custodian = 維護者
role-member = 正式成員
role-moderator = 管理
role-new-player = 新玩家
time-hours-minutes = { $arg0 } 小時 { $arg1 } 分鐘
time-minutes = { $arg0 } 分鐘

## Shared · Menu presentation

interface-fov-apply = 套用 FOV
interface-fov-custom = 自訂
interface-fov-description = 填入遊戲設定中的 FOV，讓選單位置配合你的視野
interface-fov-input = 用戶端 FOV：30 至 110 的整數
interface-fov-invalid = 請輸入 30 至 110 的整數。
interface-fov-title = 視野（FOV）
interface-reduced-motion = 減弱動態效果
interface-reduced-motion-hint = 減少選單移動和晃動
interface-scale-adjust-hint = 指向比例選項，用滾輪調整
interface-scale-layout-title = 選單大小
interface-scale-menu-title = 介面與文字
interface-scale-preview = 範例 Aa 123
interface-scale-reset = 還原 100%
interface-scale-text-title = 文字大小
interface-theme-apply = 儲存配色
interface-theme-charcoal = 炭灰
interface-theme-color = 顏色（#RRGGBB）
interface-theme-custom = 自訂
interface-theme-custom-hint = 將目前主題的全部配色複製為獨立的自訂主題
interface-theme-edit = 編輯配色
interface-theme-forest = 森林
interface-theme-hint = 選擇後立即生效，僅影響自己的選單。
interface-theme-invalid = 請輸入 #RRGGBB 顏色與 0–100 的不透明度
interface-theme-opacity = 不透明度（0–100%）
interface-theme-role-accent = 強調色
interface-theme-role-button = 按鈕背景
interface-theme-role-button-disabled = 停用按鈕
interface-theme-role-button-hovered = 懸停按鈕
interface-theme-role-button-pressed = 按下按鈕
interface-theme-role-button-selected = 選取按鈕
interface-theme-role-input = 輸入框背景
interface-theme-role-sidebar = 側欄背景
interface-theme-role-toolbar = 工具列背景
interface-theme-role-tooltip = 提示背景
interface-theme-role-window = 視窗背景
interface-theme-title = 介面主題
interface-theme-warm = 暖棕
menu-collapse = 收合
menu-expand = 展開
menu-finish-editing = 請先結束目前的輸入。
menu-input-clear = 清空
menu-input-done = 完成
menu-input-empty = （空）
menu-input-single-line = 此欄位僅支援一行。
menu-input-too-long = 長度 { $arg0 } / { $arg1 }，請縮短後確認。
menu-query-cancel = 取消輸入
menu-query-hint-rich = 按 { $chatKey } 輸入…
menu-query-single-line = 僅支援單行，點選輸入框重試。
menu-query-too-long = 長度 { $arg0 } / { $arg1 }，點選輸入框重試。
menu-rhythm-game-display = 節奏遊戲中
menu-search-empty = 沒有符合的結果
menu-search-failed = 搜尋失敗
menu-search-limit = 顯示 { $arg0 } / { $arg1 }，請縮小搜尋範圍。
menu-search-loading = 正在搜尋…
menu-search-retry = 重試
menu-usage-display-mm = <aqua><bold>選單中</bold></aqua>
url-dialog-confirm = 確認開啟
url-dialog-hint = 確認後仍須在用戶端確認開啟連結。
url-dialog-question-rich = 確定要開啟 { $label } 嗎
url-dialog-title = 開啟 { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = 推薦模組：{ $mods }，點擊名稱下載
client-tip-axiom-flight-speed-mm = <white>按住 <yellow>左 Alt</yellow> 開啟 <aqua>Axiom</aqua> 選單，拖曳右側滑桿調整飛行速度，也可以用 <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click></white>
client-tip-axiom-intro-rich = { $mod } 可以複製貼上建築、修改地形和編輯展示實體，點擊名稱下載
client-version-behind-mm = <white>你的用戶端版本 <yellow>{ $clientVersion }</yellow> 比伺服器版本 <green>{ $serverVersion }</green> 舊，建議更新</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>附近有 <gold>{ $count }</gold> 名玩家處於 <gold>隱身</gold> 狀態</yellow> <gray>·</gray> <gray>最近約 { $distance } 格</gray>
player-bedrock-label = 基岩版
player-web-label = 網頁
presence-join-templates =
    .item-1 = 叮咚，{ $player } 上線啦！
    .item-2 = 好耶，是 { $player }！
    .item-3 = { $player } 來啦，歡迎歡迎～
# Broadcast to remaining players; the departing player does not receive this message.
presence-quit-templates =
    .item-1 = { $player } 下線啦～
    .item-2 = 叮咚，{ $player } 已離線
    .item-3 = 線上 -1，{ $player } 下線了
welcome-first-join-mm = <gold><bold>歡迎來到 MIK！</bold></gold><newline><gray>建議先看看 <click:open_url:'https://mcmik.top'><yellow>官網</yellow></click> 和 <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>；輸入 <yellow>/menu</yellow> 也能開啟主選單。</gray>
welcome-return-mm = <gold><bold>歡迎回來！</bold></gold><newline><gray>你上次進服是 <yellow>{ $arg0 } 天前</yellow>。輸入 <yellow>/menu</yellow> 可以快速檢視常用功能。</gray>

## Player · Guidance

tip-label = 提示

## Player · AFK

afk-already-afk-mm = <gray>你已經處於 <gold>AFK</gold> 狀態</gray>
afk-command-description = 進入暫離狀態
afk-default-statuses =
    .item-1 = 雙手離開了鍵盤
    .item-2 = 進入省電模式
    .item-3 = 暫時切到背景執行
    .item-4 = 正在原地發呆
afk-display-title-mm = <gold><bold>暫離中</bold></gold>
afk-display-with-message-mm = <gold><bold>暫離中</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>暫時離開：</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>留下了一張紙條：</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>把狀態改成了：</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>回到了鍵盤前</green>
    .item-2 = <gray>*</gray> <player> <green>回來了</green>
afk-not-afk-mm = <gray>你目前並非處於 <gold>暫離</gold> 狀態</gray>
afk-status-too-long-mm = <red>暫離狀態最多 <white>{ $arg0 }</white> 個字</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · 關聯 { $arg2 } 項 · 對方紀錄於 { $arg3 }
player-association-header = [登入關聯] { $arg0 } 與 { $arg1 } 名歷史玩家存在地址紀錄重合
player-association-hover = 僅表示歷史網路紀錄重合，供輔助判斷；不會顯示地址。
player-association-more-addresses = 另有{ $arg0 }筆記錄
player-association-more-players = 另有{ $arg0 }名玩家
player-association-time-days = { $arg0 }天前
player-association-time-occurrences = { $arg0 }（{ $arg1 } 項）
player-association-time-separator = 、
player-association-time-today = 今天


## Player · Account security
password-already-connected = 此帳號已連線至伺服器。
password-already-set = 已設定密碼，請用 /password change 修改。
password-busy = 嘗試過於頻繁，請稍後再試。
password-change = 修改密碼
password-change-done = 密碼已修改。
password-checking = 正在檢查帳號安全狀態……
password-command-description = 管理帳號密碼保護
password-confirm = 確認新密碼
password-conflict = 帳號安全狀態已變更，請重新操作。
password-cooldown = 帳號驗證暫時受限，請於 { $arg0 } 秒後重試。
password-current = 目前密碼
password-denied = 你無權重設此帳號的密碼
password-disabled = 密碼：未設定
password-enabled = 密碼：已設定
password-invalid = 密碼錯誤，請重試。
password-keep-role = 管理員與維護者不能移除密碼
password-login = 登入
password-login-expired = 登入驗證已失效，請重新連線。
password-login-hint = 請輸入本服密碼。忘記密碼請聯絡管理重設。
password-mismatch = 兩次輸入的新密碼不一致。
password-new = 新密碼
password-nomination-required = 申請管理前請先設定帳號密碼：/password set
password-not-set = 尚未設定密碼，可用 /password set 設定。
password-remove = 移除密碼
password-remove-done = 密碼已移除。
password-remove-question = 移除後，登入將不再驗證密碼。請輸入目前密碼確認。
password-required-application = 管理申請或任命尚未結束，暫不能移除密碼
password-required-role = 管理與維護者帳號必須設定密碼，請聯絡有權限的管理者重設後登入。
password-reset = 重設密碼
password-reset-console = 已重設 { $arg0 } 的密碼：{ $arg1 }。請確認本人後透過可信管道交付。
password-reset-done = 已重設 { $arg0 } 的密碼。請立即複製並確認本人後交付；關閉後無法再次查看。
password-reset-question = 重設 { $arg0 } 的密碼？舊密碼將失效，目標連線將中斷。請輸入你自己的密碼確認。
password-retry = 重試
password-rules = 密碼須為 { $arg0 }～{ $arg1 } 個字元，不能使用玩家名稱或常見弱密碼。
password-set = 設定密碼
password-set-done = 密碼已設定。
password-set-first = 先設定密碼再申請
password-storage = 帳號安全服務無法使用，請稍後重試或聯絡服主
password-timeout = 密碼驗證已取消或逾時，請重新連線後重試。
password-tip-set-mm = 用 <gold><u>/password</u></gold> 設定<yellow>登入密碼</yellow>，保護你的帳號
password-title = 帳號安全

## Player · Profile and tab list

nametag-cancel = 取消
nametag-clear = 清空
nametag-clear-hint = 清空自訂內容後，繼承的樣式仍可能顯示
nametag-command-description = 自訂名稱標籤
nametag-confirm = 確定
nametag-conflict = 目前內容已被修改。請比較目前效果，再次確定以取代。
nametag-copy-hint = 傳送前後綴原文到聊天
nametag-copy-label = [複製]
nametag-copy-sent = 已將 { $arg0 } 的樣式原文傳送到聊天。
nametag-current = 目前效果
nametag-edit-ended = 編輯已結束，請重新點擊欄位以繼續。
nametag-edit-prompt = 按 T 輸入{ $arg0 }（MiniMessage，僅自己可見）。
nametag-finish-field = 請先確定或取消目前欄位的編輯
nametag-input-invalid = 格式無效，請傳送單行 MiniMessage，或點選清空
nametag-leading-label = 名稱字首
nametag-mine = 我的名牌
nametag-next = 下一頁
nametag-no-changes = 內容未變更
nametag-no-permission = 正式成員以上可編輯名牌；你仍可查看樣式
nametag-no-players = 尚無玩家設定名牌樣式
nametag-page = 第 { $arg0 } / { $arg1 } 頁
nametag-players = 玩家樣式
nametag-preview-label = 預覽
nametag-previous = 上一頁
nametag-refresh = 重新整理
nametag-saving = 正在儲存…
nametag-set-error = 儲存失敗：{ $arg0 }。請重試；上方顯示目前實際效果。
nametag-set-success = ✔ { $arg0 } 已設定
nametag-styles-failed = 無法載入玩家樣式，請重新整理後重試。
nametag-styles-loading = 正在載入玩家樣式…
nametag-tip-customize-mm = 用 <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> 自訂<light_purple>名稱前綴和後綴</light_purple>
nametag-title = 名稱標籤
nametag-too-long = { $arg0 } 過長，最多 { $arg1 } 個字元
nametag-trailing-label = 名稱字尾
nametag-unset = (未設定)
nametag-unsupported-placeholder-rich = 只支援 { $supported } 這類 PlaceholderAPI 變數，不支援 { $unsupported }
nametag-user-data-load-failed = 無法載入玩家資料，請稍後再試
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = 線上玩家: { $online } / { $max }
tablist-footer-ping-rich = Ping: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = 回到上一次傳送/死亡前的位置
back-empty-mm = <gray>沒有可返回的位置</gray>
back-pending-mm = <gray>正在傳送，請稍等</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>已連續返回 { $steps } 次</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>傳送失敗，請重試</red>
back-tip-return-mm = 用 <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> 返回上次傳送或死亡前的位置
back-tip-undo-mm = 用 <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> 撤銷上一次 <yellow>/back</yellow>
back-undo-empty-mm = <gray>沒有可撤銷的 <white>/back</white></gray>
back-undo-success-mm = <green>已撤銷上一次 <white>/back</white></green>
back-undo-world-missing-mm = <red>目標世界不可用，無法撤銷</red>
back-world-missing-mm = <red>目標世界不可用，無法返回</red>
boundary-near-mm = <yellow>你正在接近世界邊界</yellow>
boundary-reached-mm = <red>你已到達世界邊界</red>
fly-command-description = 開關個人飛行
fly-disabled = 已關閉飛行
fly-enabled = 已開啟飛行
fly-native-mode = 已關閉 /fly 授權；目前遊戲模式仍允許飛行。
fly-speed-click = 設定飛行速度為 { $arg0 }x
fly-speed-current = 目前飛行速度：{ $arg0 }x
fly-speed-limited = 跑圖負載較高，飛行暫限 { $arg0 }x；你的速度設定仍為 { $arg1 }x。
fly-speed-presets = 快捷速度
fly-speed-reset = 飛行速度已重設為 1x
fly-speed-reset-action = 重設
fly-speed-reset-click = 重設飛行速度為 1x
fly-speed-set = 飛行速度已設為 { $arg0 }x
fly-state-disabled = 已關閉
fly-state-enabled = 已開啟
fly-status = 飛行：{ $arg0 } | 速度：{ $arg1 }x
home-action-create = 建立家
home-action-delete = 刪除家
home-action-teleport = 傳送
home-action-update = 更新位置
home-already-exists = 已有同名的家
home-back = 返回
home-confirm-delete = 確認刪除
home-confirm-delete-detail-rich = 將刪除傳送點 { $home }
home-confirm-delete-warning = 這個操作不能撤銷
home-confirm-update = 確認更新
home-confirm-update-detail-rich = 將傳送點 { $home } 更新至以下位置
home-delete-command-description = 刪除一個傳送點
home-delete-usage-desc = 刪除一個傳送點
home-deleted-rich = 已刪除傳送點 { $home }
home-distance = 距離: { $arg0 }
home-distance-blocks = { $arg0 } 格
home-distance-other-world = 不同世界
home-empty-description = 站在想儲存的位置
home-empty-title = 尚未設定傳送點
home-invalid-name = 傳送點名稱不能包含 < > : 這些符號
home-list-rich = 已有傳送點 { $homes }
home-location = 座標: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = 這個傳送點的位置資料有誤
home-max-reached = 你最多只能設定 { $arg0 } 個傳送點
home-menu-title = 我的傳送點
home-no-homes = 你尚未設定傳送點
home-not-found-rich = 找不到傳送點 { $home }
home-search-placeholder = 搜尋家的名稱…
home-search-empty = 沒有符合的傳送點
home-set-command-description = 將目前位置設為傳送點
home-set-rich = 已設定傳送點 { $home }
home-set-usage-desc = 將目前位置設為傳送點
home-summary-count = 已設定: { $arg0 } / { $arg1 }
home-teleport-failed = 傳送失敗，請重試
home-teleported-rich = 已傳送至 { $home }
home-tip-create-mm = 用 <green><u><click:suggest_command:'/sethome '>/sethome 名稱</click></u></green> 儲存目前位置，再用 <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> 返回
home-tip-return-mm = 用 <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> 開啟家園選單，選好要去的家，再按「{ home-action-teleport }」
home-updated-rich = 傳送點 { $home } 的位置已更新
home-usage-book = 指令幫助
home-usage-delhome = /delhome <名稱>  刪除傳送點
home-usage-home = /home <名稱>  直接傳送
home-usage-name-rule = 傳送點名稱不能包含 < > :
home-usage-sethome = /sethome <名稱>  儲存目前位置
home-world = 世界: { $arg0 }
home-world-end = · 終界
home-world-nether = · 地獄
reback-command-description = 撤銷上一次 /back
teleport-accept-command-description = 同意待處理的傳送請求
teleport-allow = 被其他玩家傳送
teleport-bare-entity-selector = 為防止誤傳，禁止使用未加方括號的 @e。請使用 @e[type=…] 等明確寫法。
teleport-block-afk = 暫離時拒絕傳送
teleport-denied = { $player } 目前拒絕被其他玩家傳送
teleport-deny-command-description = 拒絕待處理的傳送請求
teleport-force-command-description = 強制傳送，忽略玩家傳送偏好（僅管理員）
teleport-menu-title = 被傳送設定
teleport-moved-here = 你已被 { $arg0 } 傳送到這裡
teleport-policy-always-allow = 永遠允許
teleport-policy-always-deny = 永遠拒絕
teleport-policy-require-consent = 經過我允許
teleport-request-accept = 同意
teleport-request-approved = { $player } 已同意你的傳送請求
teleport-request-deny-action = 拒絕
teleport-request-expired = 傳送請求已逾時
teleport-request-failed = 無法執行傳送，請再試一次
teleport-request-none = 目前沒有可處理的傳送請求
teleport-request-received = { $sender } 想要傳送你：{ $command }
teleport-request-rejected = 已拒絕傳送請求
teleport-request-sent = 已向 { $player } 傳送傳送請求
tpany-command-description = 傳送到玩家位置（線上/離線均可）
tpany-done-offline-rich = 已傳送至 { $player } 離線前的位置 { $world } { $location }
tpany-done-online-rich = 已傳送到 { $player } 目前位置
tpany-not-found-rich = 找不到 { $player } 的位置
tpany-usage = 用法
tpany-usage-command = /tpany <玩家名>
tpany-usage-desc = 傳送到玩家位置，支援離線快取

## Player · PVP

pvp-admin-menu-title = 玩家對戰管理
pvp-allow-mounted-damage = 騎乘生物可被攻擊
pvp-allow-mounted-damage-desc = 開啟後，當你和對方都開啟玩家對戰時，對方可以攻擊你正在騎乘的生物
pvp-auto-enable-desc = 連續攻擊已開啟玩家對戰的玩家兩次
pvp-auto-enable-label = 自動開啟
pvp-auto-enable-warning-actionbar-mm = <yellow>再攻擊一次將自動開啟玩家對戰</yellow>
pvp-auto-enabled-actionbar-mm = <green>玩家對戰已自動開啟</green>
pvp-combat-locked-mm = <red>戰鬥中無法關閉玩家對戰</red> <dark_gray>|</dark_gray> <gray>剩餘時間：<yellow>{ $arg0 }</yellow>秒</gray>
pvp-combat-tag-label = 戰鬥冷卻
pvp-combat-tag-value = { $arg0 } 秒
pvp-command-description = 切換玩家對戰狀態
pvp-enable = 啟用玩家對戰
pvp-enable-desc = 開啟後，你可以攻擊其他已開啟玩家對戰的玩家，也會受到他們攻擊
pvp-enable-on-death = 死亡後自動開啟玩家對戰
pvp-enable-on-death-desc = 開啟後，你死亡時會自動開啟玩家對戰
pvp-enable-on-death-label = 死亡後自動開啟
pvp-enabled-on-death-mm = <yellow>你已因死亡自動開啟玩家對戰</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>開啟線上玩家對戰管理選單</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>開啟玩家對戰設定選單</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin 玩家名 on|off|toggle|status|gui</aqua> <gray>管理指定線上玩家</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>檢視自己的玩家對戰狀態</gray>
pvp-help-title-mm = <gold><bold>玩家對戰指令</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>切換自己的玩家對戰狀態</gray>
pvp-menu-title = 玩家對戰設定
pvp-mob-protected-actionbar-mm = <yellow>該生物受到玩家對戰保護</yellow>
pvp-mob-protection-label = 生物保護
pvp-mounted-damage-label = 騎乘生物可被攻擊
pvp-no-permission-mm = <red>你沒有權限管理其他玩家的玩家對戰狀態</red>
pvp-player-not-found-mm = <red>找不到線上玩家</red>
pvp-protect-mobs = 保護自己的生物
pvp-protect-mobs-desc = 保護你馴服或正在騎乘的生物
pvp-quick-combat-cooldown = 戰鬥冷卻剩餘 { $arg0 } 秒
pvp-quick-override = 目前由臨時規則控制，可在設定中調整個人偏好
pvp-set-by-staff-mm = <aqua>管理已將你的玩家對戰狀態設定為</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = 已將 { $arg0 } 的玩家對戰狀態設定為 { $arg1 }
pvp-set-other-rich = 已將 { $player } 的玩家對戰狀態設定為 { $state }
pvp-state-label = 玩家對戰
pvp-state-off = 關閉
pvp-state-on = 開啟
pvp-status-player = 玩家: { $arg0 }
pvp-status-player-rich = 玩家: { $player }
pvp-status-title-mm = <gold><bold>玩家對戰狀態</bold></gold>
pvp-summary-disabled = 玩家對戰已關閉
pvp-summary-enabled = 玩家對戰已開啟
pvp-target-disabled-actionbar-mm = <yellow>對方未開啟玩家對戰</yellow>
pvp-target-menu-title = 玩家對戰設定 - { $arg0 }
pvp-tip-protection-mm = 用 <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple> 開啟或關閉 PVP，<green>雙方都開啟</green>後才能互相攻擊
pvp-toggled-off-mm = <gray>玩家對戰已關閉</gray> <dark_gray>|</dark_gray> <gray>其他玩家不能主動攻擊你</gray>
pvp-toggled-on-mm = <green>玩家對戰已開啟</green> <dark_gray>|</dark_gray> <gray>你現在可以攻擊其他已開啟玩家對戰的玩家</gray>

## Player · Effects

effect-cleanup-all = [清除全部效果]
effect-cleanup-blocked = 僅可在未處於 PVP 戰鬥的創造或旁觀模式下清除效果。
effect-cleanup-expired = 這項清除請求已失效。
effect-cleanup-negative = [清除負面效果]
effect-cleanup-prompt-rich = 偵測到你因 { $source } 獲得了一些負面效果，是否清除？
effect-cleanup-result = 已清除 { $count } 種效果。
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] 滯留藥水雲
        [ARROW] 箭矢
        [ATTACK] 受到攻擊
        [COMMAND] 指令
        [FOOD] 食用物品
        [PLUGIN] 插件
        [POTION_DRINK] 飲用藥水
        [POTION_SPLASH] 飛濺藥水
        [WARDEN] 伏守者
        [WITHER_ROSE] 凋零玫瑰
       *[OTHER] 未知來源
    }

## Player · Trash

trash-clear = 清空
trash-clear-hint = 點擊立即清空箱內物品；
    不點擊則在關閉選單 { $arg0 } 秒後自動清空。
trash-command-description = 開啟垃圾桶
trash-sort = 整理
trash-sort-hint = 合併同類堆疊並依物品類型排列
trash-tip-dispose-mm = 用 <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> 開啟<yellow>垃圾桶</yellow>，放入不需要的物品
trash-title = 垃圾桶

## Player · Utilities

hat-command-description = 把主手物品戴到頭上
hat-empty-hand-mm = <red>主手沒有物品</red> <gray>先拿起一個方塊或物品再使用 /hat</gray>
hat-success-mm = <green>已把主手物品戴到頭上</green> <dark_gray>|</dark_gray> <gray>主手與頭盔欄已交換</gray>
hat-tip-wear-mm = 用 <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> 把<yellow>主手物品</yellow>戴到頭上
removeitems-command-description = 清除掉落物
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = 已在世界 { $world } { $location } 清除{ $count }個掉落物品 { $radius }
removeitems-no-world = 無法確定世界
removeitems-radius = 半徑 { $arg0 } 格
removeitems-tip-usage-mm = 用 <aqua><u><click:suggest_command:'/rmitems '>/rmitems [半徑]</click></u></aqua> 清理附近的<yellow>掉落物</yellow>
self-kick-command-description = 把自己踢出伺服器
self-kick-reason-too-long = 踢出原因不可超過 { $arg0 } 個字元
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] 你已主動離開伺服器。原因：{ $reason }
       *[third] { $player } 主動離開了伺服器。原因：{ $reason }
    }
    .item-1 = { $player } 主動離開了伺服器。原因：{ $reason }
    .item-2 = { $player } 下線了。留言：{ $reason }
    .item-3 = { $player } 離開了伺服器，留言：{ $reason }
    .item-4 = { $player } 已退出。附言：{ $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] 你已主動離開伺服器。
       *[third] { $player } 主動離開了伺服器。
    }
    .item-1 = { $player } 主動離開了伺服器。
    .item-2 = { $player } 下線啦～
    .item-3 = 叮咚，{ $player } 已離線
    .item-4 = 線上 -1，{ $player } 下線了

## Community · Chat and mentions

chat-content-audio = [音訊]
chat-content-card = [卡片]
chat-content-contact = [名片]
chat-content-emote = [動作]
chat-content-face = [表情]
chat-content-file = [檔案]
chat-content-history = [聊天紀錄]
chat-content-image = [圖片]
chat-content-location = [位置]
chat-content-message = [訊息]
chat-content-multiple = [多則訊息]
chat-content-music = [音樂]
chat-content-notice = [通知]
chat-content-reply = [引用訊息]
chat-content-sticker = [貼圖]
chat-content-video = [影片]
chat-copy-hover = 左鍵點擊複製訊息
chat-delay-3s = 3 秒
chat-delay-5s = 5 秒
chat-delay-7s = 7 秒
chat-delay-cancel-command-description = 取消待傳送的延遲訊息
chat-delay-cancelled = 已取消等待傳送的訊息
chat-delay-current = 目前聊天延遲：{ $arg0 }
chat-delay-nothing-to-cancel = 沒有待取消的訊息
chat-delay-off = 關閉
chat-delay-preview-marker = [等待傳送]
chat-delay-queued = 訊息將於 { $arg0 } 後傳送，輸入 /cancel 或 /c 可取消
chat-item-empty-hover = 主手沒有物品
chat-mention-all-hover = 提醒目前頻道內所有可見玩家
chat-msg-command-description = 傳送私人訊息並切換至私人聊天頻道
chat-player-hover = 左鍵點擊發起私人聊天
chat-private-enter = 已切換至與 { $arg0 } 的私人聊天頻道
chat-private-label = 私人訊息
chat-private-no-reply-target = 沒有可回覆的線上私人聊天對象
chat-private-self = 無法與自己開始私人聊天
chat-private-target-not-found = 找不到線上玩家 { $arg0 }
chat-private-target-offline = 私訊對象已離線，請等待對方上線或選擇其他頻道
chat-private-tip-channel-mm = 用 <aqua><u><click:suggest_command:'/msg '>/msg 玩家</click></u></aqua> 進入私聊頻道，用 <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> 返回公共頻道
chat-public-command-description = 切換公共聊天頻道
chat-public-enter = 已切換到公共聊天頻道
chat-reply-command-description = 回覆最近的私人聊天對象
chat-settings-menu-title = 聊天設定
chat-social-account-label = 平台帳號
chat-social-bound-player-label = 已綁定玩家
chat-social-name-label = 社交暱稱
chat-social-unbound = 未綁定 Minecraft 玩家
chat-staff-command-description = 切換{ chat-staff-label }聊天頻道
chat-staff-label = 管理
chat-staff-permission-missing = 你已失去{ chat-staff-label }聊天權限，已回到公共聊天頻道
mention-action-bar = 螢幕提示
mention-action-bar-text = { $sender } 提及了你
mention-alerts = 提及提醒總開關
mention-menu-title = 提及提醒
mention-mute-afk = 暫離時靜音
mention-sound = 提醒聲音
staffchat-enter = 已進入{ chat-staff-label }聊天頻道

## Community · Announcements

announcement-count = { $arg0 } 則公告
announcement-empty = 暫無公告
announcement-first-page = 已到最新公告
announcement-last-page = 已到最早公告
announcement-latest = 最新公告
announcement-list-newer = 較新一組
announcement-list-older = 較早一組
announcement-more-count = 還有 { $arg0 } 則未顯示
announcement-new-count = 新增 { $arg0 } 則
announcement-newer = 較新公告
announcement-next-page = 下一頁
announcement-older = 較早公告
announcement-page = { $arg0 } / { $arg1 } 頁
announcement-previous-page = 上一頁
announcement-reload = 重新載入公告
announcement-reload-command-description = 重新載入公告檔案
announcement-reload-failed = 公告重新載入失敗，原有公告已保留
announcement-reloaded = 公告已重新載入
announcement-unread-count = { $arg0 } 則未讀
announcement-updated = 伺服器公告已更新
announcement-view-all = 查看所有公告
announcement-view-command-description = 查看伺服器公告

## Community · Identity and social platforms

identity-admission-bind-rich = { $platform }：{ $command }
identity-admission-instructions = 選擇一個平台，向社群機器人傳送對應指令：
identity-admission-pending = 現有綁定暫時無法驗證，請稍後重試或綁定其他平台帳號
identity-admission-reconnect = 綁定後重新連線伺服器
identity-admission-required = 綁定社群帳號後即可入服
identity-admission-revoked = 你已沒有有效的社群平台綁定，請重新連線取得綁定指令
identity-admin-binding-not-found = 所選綁定已不存在。
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = 已解除玩家 { $arg1 } 所選的 { $arg0 } 綁定。
identity-admin-command-description = 管理員檢視或撤銷外部身分綁定
identity-admin-help = /bindadmin lookup <玩家或UUID>
    /bindadmin unlink <binding-id> confirm
identity-admin-lookup-empty = 找不到該玩家的綁定紀錄。
identity-admin-lookup-title = { $arg0 } 的綁定：
identity-binding-community = 社群 #{ $arg0 }
identity-binding-info = 綁定時間：{ $arg0 }
    綁定編號：{ $arg1 }
identity-binding-label-rich = { $kind ->
    [community] { $platform } · { $account } · { $community }
   *[account] { $platform } · { $account }
    }
identity-binding-not-owned = 所選綁定不存在或不屬於你。
identity-binding-reference-rich = { $label } · #{ $reference }
identity-binding-unlinked = 已解除所選的 { $arg0 } 綁定。
identity-binding-unnamed = 未提供暱稱
identity-bindings-empty = 目前沒有綁定任何外部平台帳號。
identity-bindings-title = 已綁定的帳號
identity-code-cancelled = 已取消尚未使用的 { $arg0 } 綁定驗證碼。
identity-code-copy = 點擊複製完整綁定指令
identity-code-expiry = 驗證碼有效期：{ $arg0 } 分鐘
identity-code-not-pending = 沒有待使用的 { $arg0 } 綁定驗證碼。
identity-code-redeem-rich = 在 { $platform } 社群向機器人傳送：{ $command }
identity-command-description = 將 Minecraft 與外部平台身分綁定
identity-community-join-rich = 加入社群：{ $link }
identity-help = 身分綁定指令：
    /bind qq — 快速產生 QQ 綁定指令
    /bind <平台> — 產生一次性驗證碼
    /bind list — 檢視已有綁定
    /bind cancel <平台> — 取消待用驗證碼
    /bind unlink — 選擇並解除一條綁定
identity-notify-linked = { $arg0 } 身分綁定成功。使用 /bind list 檢視及管理綁定。
identity-notify-unlinked = 已解除你的 { $arg0 } 身分綁定。
identity-platform-none = 無
identity-platforms = 支援的平台：{ $arg0 }
identity-storage-error = 身分綁定資料庫操作失敗，請聯絡管理員。
identity-unavailable = 身分綁定服務目前無法使用，請聯絡管理員。
identity-unlink-action = [解除綁定]
identity-unlink-hint = 只解除所選的這一條綁定。
identity-unsupported-platform = 不支援的平台：{ $arg0 }
social-admin-conversations-empty = 尚未觀察到 { $arg0 } 的對話。
social-admin-conversations-title = { $arg0 } 已觀察到的對話：
social-admin-help = /social platforms · /social status [平台] · /social reload [平台] · /social conversations <平台>
social-admin-metrics = 入站 { $arg0 }／重試 { $arg1 } · 出站 { $arg2 }／背壓 { $arg3 } · 失敗 { $arg4 }
social-admin-platform = - { $arg0 }（{ $arg1 }）：{ $arg2 }
social-admin-reloaded = 已重新載入社群平台 { $arg0 }。
social-admin-reloaded-all = 已重新載入所有社群平台。
social-admin-status = { $arg0 } · { $arg1 } · 世代 { $arg2 } · 對話 { $arg3 }
social-admin-title = 社群平台
social-admin-unknown = 未知社群平台：{ $arg0 }
social-binding-already-linked = 目前平台身分已綁定 Minecraft 玩家 { $arg0 }。
social-binding-code-invalid = 驗證碼無效或已過期。請重新連線伺服器取得新碼，或在遊戲內執行 /bind { $arg0 }。
social-binding-external-in-use = 目前平台身分已綁定其他 Minecraft 玩家。
social-binding-help = 嘗試連線伺服器，在登入提示中取得驗證碼；已入服可執行 /bind { $arg0 }。
    複製產生的完整指令。
    在此傳送該指令。
    使用 /我的帳號 驗證綁定。
social-binding-identity-missing = 此訊息沒有可靠的平台成員身分，無法執行身分綁定。
social-binding-linked = 已綁定 Minecraft 玩家 { $arg0 }。
social-binding-membership-required = 請先加入此平台的接入社群，再重試綁定
social-binding-membership-unavailable = 社群成員資格暫時無法核驗，請稍後重試或聯絡伺服器管理員
social-binding-not-bound = 目前平台身分尚未綁定 Minecraft 玩家。
social-binding-platform-mismatch = 此驗證碼是為其他平台產生的。
social-binding-player-scope-in-use = 此 Minecraft 玩家已在相同的平台帳號範圍內綁定其他身分。
social-binding-rate-limited = 驗證碼嘗試次數過多，請約 { $arg0 } 分鐘後再試。
social-binding-unavailable = 身分綁定暫時無法使用，請聯絡伺服器管理員。
social-binding-unlink-prompt = 解綁會移除目前平台身分與 Minecraft 的關聯。如確定，請傳送 { $arg0 }。
social-binding-unlinked = 已解除與 Minecraft 玩家 { $arg0 } 的綁定。
social-command-description = 管理社群平台介接器
social-crypto-command-description = 管理 Matrix 加密裝置驗證
social-crypto-comparison-not-ready = SAS 比對尚未就緒。
social-crypto-device = { $arg0 } / { $arg1 }
    Ed25519: { $arg2 }
social-crypto-failed = 裝置驗證失敗，請查看伺服器記錄。
social-crypto-no-active = 目前沒有進行中的裝置驗證。
social-crypto-not-accepting = 目前驗證階段無法接受請求。
social-crypto-not-running = Matrix 尚未執行。
social-crypto-requested = 已發起驗證：{ $arg0 }
social-crypto-state = { $arg0 ->
    [initializing] 正在初始化
    [verified] 已驗證
    [unverified] 未驗證
    [no_cross_signing] 未啟用交叉簽章
    [requested] 已請求
    [ready] 就緒
    [started] 已開始
    [comparison] 等待比對
    [waiting] 等待完成
    [done] 已完成
    [cancelled] 已取消
   *[unknown] 未知
    [valid] 金鑰有效
    [cross_signed] 已交叉簽章
    [not_cross_signed] 未交叉簽章
    [blocked] 已封鎖
    [invalid] 金鑰無效
    }
social-crypto-status = 驗證：{ $arg0 }
    金鑰備份版本：{ $arg1 }
social-crypto-target = { $arg0 } / { $arg1 }: { $arg2 } · { $arg3 }
social-crypto-transaction = { $arg0 }: { $arg1 } / { $arg2 } · { $arg3 }
social-crypto-transaction-required = 請指定目前驗證交易的完整 ID。
social-crypto-unavailable = 無法使用
social-crypto-updated = 驗證已更新；使用 matrixcrypto status 查看狀態。
social-crypto-usage = matrixcrypto status [使用者ID 裝置ID]
    matrixcrypto verify <使用者ID> <裝置ID>
    matrixcrypto accept|confirm|cancel <交易ID>
social-duration-days = { $arg0 } 天 { $arg1 } 小時 { $arg2 } 分鐘
social-duration-hours = { $arg0 } 小時 { $arg1 } 分鐘
social-duration-minutes = { $arg0 } 分鐘
social-help-command-description = 顯示此命令清單
social-link-command-description = 使用遊戲內驗證碼綁定 Minecraft 帳號
social-online-command-description = 查看在線玩家
social-profile-afk = 線上 · AFK
social-profile-avatar-alt = Minecraft 頭像
social-profile-command-description = 查看自己；正式成員可按玩家名稱、@提及或回覆查詢他人
social-profile-first-joined-label = 首次加入
social-profile-language-label = 語言
social-profile-last-seen-label = 最近上線
social-profile-member-required = 只有已綁定的正式成員或管理、維護者可以查詢其他玩家。
social-profile-offline = 離線
social-profile-online = 線上 · 活躍
social-profile-play-time-label = 遊玩時間
social-profile-player-label = 玩家
social-profile-player-not-found = 找不到該 Minecraft 玩家。
social-profile-presence-label = 狀態
social-profile-target-ambiguous = 請只指定一個玩家名稱、@一名使用者，或回覆一名使用者。
social-profile-target-unbound = 被提及或回覆的平台身分尚未綁定 Minecraft 玩家。
social-profile-uuid-label = UUID
social-query-metric = { $arg0 }
social-query-no-players = 目前沒有玩家在線上。
social-query-online-label = 線上
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (暫離)
   *[active] { $arg0 }
    }
social-query-players-label = 玩家
social-query-population = 線上 { $arg0 }/{ $arg1 } · 活躍 { $arg2 } · 暫離 { $arg3 }
social-query-unknown = 未知指令。傳送 { $arg0 } 查看可用指令。
social-query-uptime-label = 執行時間
social-reply-hidden = 回覆命中伺服器內容安全規則，已隱藏原內容。
social-state-disabled = 已停用
social-state-failed = 失敗
social-state-ready = 就緒
social-state-starting = 正在啟動
social-state-stopped = 已停止
social-status-command-description = 查看玩家、TPS、MSPT 與運行時間
social-title-command-help = 指令說明
social-title-content-hidden = 內容已隱藏
social-title-identity-linking = 帳號綁定
social-title-identity-unlinking = 解除綁定
social-title-online-players = 線上玩家
social-title-player-profile = 玩家資料
social-title-server-status = 伺服器狀態
social-title-unknown-command = 未知指令
social-unlink-command-description = 確認後解除目前身分綁定

## Community · AI and knowledge

ai-busy = AI 目前請求較多，請稍後再試
ai-cleared = 已清除 AI 對話記錄
ai-command-description = 向遊戲內 AI 助手提問
ai-disabled = AI 助手尚未啟用或設定
ai-knowledge-curation-failed = 知識整理失敗：{ $arg0 }
ai-knowledge-curation-finished = 知識整理完成：應用 { $arg0 }、跳過 { $arg1 }、失敗 { $arg2 }、隔離 { $arg3 }、處理 { $arg4 }。
ai-knowledge-curation-started = 已開始整理知識候選。
ai-knowledge-disabled = 知識庫已停用。
ai-knowledge-index-dirty = 索引需要修復；同步完成前已暫停私人記憶檢索。
ai-knowledge-last-failure = 上次失敗：{ $arg0 }
ai-knowledge-last-sync = 上次同步：{ $arg0 }
ai-knowledge-learning-disabled = 知識學習已停用。
ai-knowledge-operation-failed = 知識庫操作失敗：{ $arg0 }
ai-knowledge-operation-started = 知識庫操作已開始。
ai-knowledge-protected = 知識文檔 { $arg0 } 已設為受保護。
ai-knowledge-rollback = 知識文檔 { $arg0 } 已恢復為版本 { $arg1 }。
ai-knowledge-status = 知識：{ $arg0 }份公開文件 / { $arg1 }個文字片段 · { $arg2 }份私人文件 / { $arg3 }個文字片段
ai-knowledge-sync-failed = 知識庫同步失敗：{ $arg0 }
ai-knowledge-sync-started = 已開始同步知識庫。
ai-knowledge-synchronized = { ai-knowledge-status } · 同步完成。
ai-knowledge-unprotected = 知識文檔 { $arg0 } 已取消保護。
ai-learning-failure = 學習失敗：{ $arg0 }
ai-learning-status = 學習狀態：候選 { $arg0 }、正在提取 { $arg1 }、排隊 { $arg2 }、丟棄的提取任務 { $arg3 }、丟棄的候選 { $arg4 }、隔離 { $arg5 }
ai-memory-empty = { $arg0 } 沒有私人記憶。
ai-memory-list = { $arg0 } 的私人記憶：{ $arg1 }
ai-memory-not-found = 未找到私人記憶 { $arg0 }。
ai-memory-removed = 已永久刪除 { $arg1 } 的{ $arg0 }筆私人記憶。
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = 未找到已緩存的玩家：{ $arg0 }
ai-prompt-too-long = 問題過長（最多 { $arg0 } 個字元）
ai-reload-failed = AI 設定重新載入失敗：{ $arg0 }
ai-reloaded = 已重新載入 AI 設定（{ $arg0 }）
ai-request-failed = AI 請求失敗，請稍後再試
ai-social-command-description = 向共用 AI 助手提問
ai-social-question-required = 請在 AI 指令後輸入問題
ai-status-disabled = AI 目前已停用
ai-status-enabled = AI 已啟用 · 提供者 { $arg0 } · 模型 { $arg1 } · 工具 { $arg2 }
ai-status-none = 無
ai-thinking = 正在思考…
ai-usage = 用法：/ai <問題> · /ai clear · /ai status
ai-worker-status = AI 工作線程：運行 { $arg0 }、排隊 { $arg1 }、丟棄 { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>恭喜！</bold> 你已成為 <gold>正式成員</gold>，現在可以使用更多指令和功能，例如投影一鍵列印！</green>
governance-active-days-30 = 近 30 天至少 2 個有效參與日（當前 { $arg0 }）
governance-active-days-60 = 近 60 天至少 6 個有效參與日（當前 { $arg0 }）
governance-active-header = ─── 活躍成員資格 ───
governance-active-member = 當前具有成員身份
governance-active-member-age = 取得成員身份滿 30 天
governance-active-playtime = 近 60 天有效遊玩 6 小時（當前 { $arg0 }）
governance-activity-action = { $arg0 ->
    [confirm] 確認聯署
    [vote] 查看並投票
    *[details] 查看詳情
    }
governance-activity-all = 查看全部（{ $arg0 }）
governance-activity-closed = 已結束
governance-activity-confirm-petition = 確認參與聯署？達到人數後將發起罷免投票。
governance-activity-needed = 還需 { $arg0 } 人
governance-activity-proposal = { $arg0 ->
    [appointment] 任命 { $arg1 } 為管理員
    [removal] 免去 { $arg1 } 的管理職務
    [petition] 罷免 { $arg1 } 的聯署
    *[ban] 封禁 { $arg1 } 的投票
    }
governance-activity-remaining = { $arg1 ->
    [days] 剩餘：{ governance-days }
    [hours] 剩餘：{ governance-hours }
    *[minutes] 剩餘：{ governance-minutes }
    }
governance-activity-status = { $arg0 ->
    [available] 可投票
    [voted] 已投票
    [join] 可聯署
    [joined] 已聯署
    [closed] 已結束
    *[view] 僅查看
    }
governance-already-moderator = 你已經是管理。
governance-application-status = { $arg0 ->
    [open] 任命投票進行中
    [pending] 已通過，等待任命
    [blocked] 尚未符合申請條件
   *[ready] 可以申請管理
    }
governance-ballot-recorded = 匿名投票已記錄為“{ $arg0 }”；截止前可修改選擇。
governance-ban-details = 發起人 { $arg0 } · 封禁期限 { $arg1 } · 理由：{ $arg2 }
governance-ban-suffix = · 期限 { $arg0 } · 理由：{ $arg1 }
governance-choice-abstain = 棄權
governance-choice-no = 反對
governance-choice-yes = 贊成
governance-command-description = 檢視治理資格與投票
governance-custodian-no-nomination = 維護者不參加管理任免。
governance-custodian-no-resign = 維護者不適用管理辭任流程。
governance-days = { $arg0 }天
governance-error-ban-already-active = 該玩家已有生效中的封禁。
governance-error-ban-proposer-ineligible = 只有活躍成員可以發起封禁投票。
governance-error-ban-self = 不能發起封禁自己的投票。
governance-error-candidate-ineligible = 管理候選人必須是活躍成員
governance-error-nomination-not-open = 你沒有進行中的管理申請。
governance-error-not-voter = 你不在本次凍結選民名單中。
governance-error-pause-already-active = 該玩家已有生效中的晉升暫停。
governance-error-pause-already-member = 該玩家已是成員，無需暫停晉升。
governance-error-pause-end-invalid = 晉升暫停期限必須晚於當前時間。
governance-error-player-unknown = 該玩家尚無治理記錄。
governance-error-removal-petition-closed = 這項聯署已結束或已由新申請取代。
governance-error-removal-requires-petition = 罷免投票必須由達到人數的聯署啟動。
governance-error-removal-self = 被罷免者不能聯署自己的罷免。
governance-error-removal-sponsor-ineligible = 只有活躍成員可以聯署罷免
governance-error-removal-sponsors-too-few = 除被罷免者外至少需要兩名活躍成員。
governance-error-removal-target-not-moderator = 對方已不再是可罷免的管理員。
governance-error-removal-vote-open = 該管理已有進行中的罷免投票。
governance-error-termination-reason-invalid = 終止原因不適用於該類投票。
governance-error-vote-already-open = 該玩家已有進行中的同類投票。
governance-error-vote-closed = 投票已經截止或終止。
governance-error-vote-cooldown = 未達到法定參與人數後的三天重試間隔尚未結束。
governance-error-vote-subject-unknown = 投票物件尚無治理記錄。
governance-error-vote-termination-closed = 投票截止時間已過，不能再終止或撤回。
governance-error-vote-unknown = 投票不存在。
governance-error-voters-too-few = 至少需要兩名活躍成員才能發起投票。
governance-failure-majority = 贊成票未超過全部參與票的一半
governance-failure-minimum-yes = 贊成票未達到最低票數
governance-failure-none = 無
governance-failure-quorum = 未達到法定參與人數
governance-failure-two-thirds = 贊成票未達到非棄權票的三分之二
governance-history-header = ─── 最近投票結果 ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
governance-hours = { $arg0 }小時
governance-inactive-removal = { $arg0 } 已因連續 180 天未登入自動卸任管理。
governance-invalid-choice = 請選擇 yes、no 或 abstain。
governance-invalid-duration = 期限格式無效，請使用 30m、12h、3d 等格式。
governance-kind-appointment = 任命
governance-kind-ban = 封禁
governance-kind-removal = 罷免
governance-legacy-ban-kick = 此前的治理投票已通過，你被封禁 { $arg0 }。理由：{ $arg1 }
governance-menu-already-sponsored = 已連署
governance-menu-application = 申請狀態
governance-menu-back = 返回
governance-menu-cancel = 取消
governance-menu-completed = 已結束
governance-menu-confirm = 確認
governance-menu-confirm-nominate = 提交申請將發起管理任命投票。
governance-menu-confirm-resign = 確認辭任管理？
governance-menu-confirm-sponsor = 支持這項罷免連署？
governance-menu-confirm-withdraw = 撤回管理申請？
governance-menu-custodian-duties = 伺服器維護與管理。
governance-menu-identity = 身分
governance-menu-identity-hint = 查看晉升條件和活躍紀錄
governance-menu-join-petition = 支持罷免連署
governance-menu-loading = 正在讀取治理狀態…
governance-menu-matters = 治理事項
governance-menu-moderator-duties = 處理違規與玩家求助。
governance-menu-nominate = 申請管理
governance-menu-offline = 離線
governance-menu-ongoing = 進行中
governance-menu-online = 線上
governance-menu-pause = 暫停新人晉升
governance-menu-pause-appeal = 申訴途徑
governance-menu-pause-duration = 期限（例如 2d）
governance-menu-pause-reason = 原因
governance-menu-pause-until = 截止時間
governance-menu-pending-application = 已通過 · 待執行
governance-menu-petition-row = { $arg0 } · 已連署 { $arg1 }/{ $arg2 }
governance-menu-petitions = 罷免連署
governance-menu-player-name = 玩家名稱
governance-menu-players = 玩家管理
governance-menu-refresh = 重新整理
governance-menu-related = 近期相關紀錄
governance-menu-related-matters = 查看相關事項
governance-menu-resign = 辭任管理
governance-menu-resume = 解除晉升暫停
governance-menu-resume-reason = 解除原因
governance-menu-staff-only = 只有管理與維護者可以管理玩家
governance-menu-staff-search = 尋找管理
governance-menu-subject-filter = { $arg0 } · 清除篩選 ×
governance-menu-team = 公開管理
governance-menu-team-empty = 沒有符合條件的管理
governance-menu-title = 社群治理
governance-menu-withdraw = 撤回申請
governance-minutes = { $arg0 }分鐘
governance-no-active-ban = 當前沒有生效中的封禁
governance-no-active-pause = 當前沒有生效中的晉升暫停
governance-nomination-withdrawn = 已撤回管理申請；投票終止結果將自動公告。
governance-none = 暫無
governance-not-member = 尚未成為成員
governance-not-moderator = 你當前不是管理。
governance-operation-error = 治理操作失敗，請重試。
governance-own-choice-closed = 你的選擇：{ $arg0 }
governance-own-choice-open = 你的選擇：{ $arg0 }；截止前可修改。
governance-own-not-voted = 你在選民名單中，但尚未投票。
governance-own-not-voted-hint = 你尚未投票；開啟「治理」並選擇這項投票。
governance-own-not-voter = 你不在本次選民名單中。
governance-own-voted = 你已投票
governance-pause-cleared = 已解除 { $arg0 } 的晉升暫停。
governance-pause-details = 暫停理由：{ $arg0 }；期限：{ $arg1 }；申訴：{ $arg2 }
governance-pause-fields-required = 請填寫暫停理由和申訴途徑。
governance-pause-not-active = { $arg0 } 沒有生效中的晉升暫停。
governance-pause-notice = 你的晉升已暫停。理由：{ $arg0 }；至 { $arg1 }；申訴：{ $arg2 }
governance-pause-set = 已暫停 { $arg0 } 的晉升至 { $arg1 }。
governance-petition-already-sponsored = 你已聯署過該罷免（{ $arg0 }/{ $arg1 }）。
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } 人 · 截止 { $arg3 }
governance-petition-started = 針對 { $arg0 } 的罷免聯署：{ $arg1 }/{ $arg2 } 人；截止 { $arg3 }。
governance-petitions-header = ─── 罷免聯署 ───
governance-player-first-join = 首次加入：{ $arg0 }
governance-player-header = ─── { $arg0 } · 治理檔案 ───
governance-player-id = UUID：{ $arg0 }
governance-player-last-login = 上次登入：{ $arg0 }
governance-player-member-since = 成員身份起始：{ $arg0 }
governance-player-never-joined = 玩家 { $arg0 } 從未進入過伺服器。
governance-promotion-account-age = 首次加入已滿 3 天
governance-promotion-header = ─── 新人晉升條件 ───
governance-promotion-play-days = 有效遊玩跨至少 2 個自然日（當前 { $arg0 }）
governance-promotion-playtime = 有效遊玩滿 8 小時（當前 { $arg0 }）
governance-resign-failed = 辭任失敗，請聯絡維護者。
governance-resigned = { $arg0 } 已辭任管理。
governance-result-announcement = 投票 #{ $arg0 } { $arg1 }（{ $arg2 }）：{ $arg3 }{ $arg4 }
governance-result-failed = 未通過（{ $arg0 }）
governance-result-open = 進行中
governance-result-passed = 通過
governance-result-tally = { $arg0 } · 選民 { $arg1 }，參與 { $arg2 }，贊成 { $arg3 }，反對 { $arg4 }，棄權 { $arg5 }
governance-result-terminated = 已終止（{ $arg0 }）
governance-resume-notice = 你的晉升暫停已解除。理由：{ $arg0 }
governance-resume-reason-required = 請填寫解除暫停的理由。
governance-role-lookup-error = 無法核對管理身份，請聯絡維護者。
governance-seconds = { $arg0 }秒
governance-storage-error = 治理資料操作失敗，請聯絡維護者。
governance-termination-inactive = 被罷免者因長期離線自動卸任
governance-termination-ineligible = 候選人失去活躍成員資格
governance-termination-resigned = 被罷免者已經辭任
governance-termination-unknown = 未記錄原因
governance-termination-withdrew = 候選人撤回申請
governance-vote-electorate = 選民 { $arg0 } 人 · 法定參與 { $arg1 } 人
governance-vote-finalized = 結算：{ $arg0 }
governance-vote-header = ─── 投票 #{ $arg0 } ───
governance-vote-in-progress = 進行中 · 已參與 { $arg0 } 人；分項票數在結束後公佈。
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · 截止 { $arg3 } · 選民 { $arg4 } · 已參與 { $arg5 } · 法定 { $arg6 }{ $arg7 }
governance-vote-period = 開始 { $arg0 } · 截止 { $arg1 }
governance-vote-started = { $arg1 }投票 #{ $arg0 } 已開始：{ $arg2 }；選民 { $arg3 } 人，法定參與 { $arg4 } 人，截止 { $arg5 }。開啟「治理」查看詳情。
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── 進行中的匿名投票 ───

## World · Plots

plot-access-removed-for-you = 你在地段「{ $arg0 }」的權限已被移除。
plot-admin-created = 已為 { $arg1 } 登錄地段 { $arg0 } · { $arg2 }
plot-allowed = 開啟
plot-area-changed = 確認期間地段範圍已改變，請重新整理後重試。
plot-area-confirm = 將「{ $arg0 }」的範圍替換為目前選區（{ $arg1 } 個單元）？不會刪除建築。
plot-area-hint = 用木斧左、右鍵或 //pos1、//pos2 選點，返回選單後儲存
plot-area-replace-save = 替換範圍並儲存
plot-area-save = 儲存地段範圍
plot-area-saved = 已儲存「{ $arg0 }」的地段範圍。
plot-atmosphere-saved = 已儲存環境設定。
plot-back = 返回
plot-board-auto-notify-result = 已安排提醒 { $arg0 } 位玩家。
plot-board-auto-registered-text = 地段「{ $arg0 }」已登記。已建單元的座標外框：X { $arg1 }–{ $arg2 }，Y { $arg3 }–{ $arg4 }，Z { $arg5 }–{ $arg6 }。
plot-board-detail = { $arg0 } 的紀錄，位置 { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }：{ $arg5 }
plot-board-empty = 目前沒有社區紀錄。
plot-board-header = 社區公示與回饋（最近 { $arg0 } 條；/plot board 編號 查看詳情）
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = 公示
plot-board-kind-objection = 異議
plot-board-loading = 正在讀取公示紀錄…
plot-board-menu = 社區公示
plot-board-notice-alert = { $arg0 } 發布了公示：{ $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } 提出了異議：{ $arg1 } · { $arg2 }
plot-board-rate-limited = 已達公示頻率限制，請稍後再試。
plot-board-record-invalid = 紀錄不存在、已處理，或你無權執行此操作。
plot-board-registered-alert = { $arg0 } 登記了地段「{ $arg1 }」：{ $arg2 }
plot-board-ruling-alert = { $arg0 } 的異議已有裁決：{ $arg1 } · { $arg2 }
plot-board-ruling-detail = 裁決：{ $arg0 }
plot-board-saved = 紀錄已儲存：{ $arg0 }。
plot-board-status-elapsed = 發布已滿 72 小時
plot-board-status-open = 待處理
plot-board-status-ruled = 已裁決
plot-board-status-withdrawn = 異議已撤回
plot-board-text-invalid = 說明需 12–1000 字；裁決理由至少 8 字。
plot-board-unavailable = 公示紀錄暫未載入，請稍後重試，或查看 https://mcmik.top/community/notices 。
plot-board-withdrawal-alert = { $arg0 } 已撤回異議：{ $arg1 } · { $arg2 }
plot-board-write-failed = 更新公示失敗，請稍後重試。
plot-command-blocked = 地段保護阻止了這條命令。請在自己的地段內直接使用 /fill、/setblock 等命令；複雜 /execute 請由管理員確認。
plot-command-description = 管理地段
plot-created = 已建立地段 { $arg0 } · { $arg1 }
plot-created-for-you = { $arg1 } 已為你登錄地段 { $arg0 }。
plot-deleted = 已釋放地段；建築本身沒有被刪除。
plot-denied = 關閉
plot-error-admin-owner-only = 調整具備管理能力的成員，需要同時具備管理成員和管理權限能力。
plot-error-area-disconnected = 地段必須是一個相連的區域。
plot-error-args = 參數無效或不足。用 /plot help 查看用法。
plot-error-busy = 地段資料正在儲存，請稍後重試；本次操作未提交。
plot-error-flag = 不支援此權限名稱，請從權限列表選擇具體操作。
plot-error-id = 地段 ID 不存在或不唯一。用 /plot list 查看。
plot-error-member-missing = 該玩家已不在成員列表。
plot-error-member-required = 新人可查看地段和提出異議；成為 正式成員 後可建立項目
plot-error-name = 地段名稱需為 1–48 個字元。
plot-error-notice-text = 公告板最多 { $arg0 } 字，不限制行數或單行長度；不能包含控制字元。
plot-error-overlap = 選取範圍與已有地段重疊。
plot-error-owner-member = 所有者使用所有者權限組，不作為普通成員邀請。
plot-error-owner-only = 你沒有執行此操作所需的管理權限
plot-error-permission-changed = 權限、成員或繼承規則已變更，請重新預覽後確認。
plot-error-permission-grant = 不能授予自己不具備的管理權限。
plot-error-permission-owner-scope = 只有地段所有者、母地段所有者或伺服器管理者可以修改所有者組權限。
plot-error-player-unknown = 找不到這名已加入伺服器的玩家；請核對名稱或 UUID。
plot-error-release-owner-only = 你沒有刪除地段的權限。
plot-error-selection = 用 //pos1、//pos2 標記兩個角點
plot-error-selection-changed = 選區已變更，請重新確認。
plot-error-staff-only = 只有管理或維護者可以管理或代登錄他人的地段。
plot-error-storage = 地段操作失敗，請稍後重試或聯繫管理者。
plot-error-subplot-children = 請先釋放子地段，再釋放母專案
plot-error-subplot-depth = 子地段僅支援一層。
plot-error-subplot-disconnected = 所選建築單元沒有連成一片；請分別劃分各部分。
plot-error-subplot-overlap = 所選範圍與其他子地段重疊。
plot-error-subplot-owner = 你沒有執行此子地段操作所需的權限
plot-error-subplot-parent = 請將兩個選區角點標在母專案已有的建築單元內。
plot-error-subplot-supervisor-member = 母專案所有者已擁有此處的監管權限，無須再加入成員。
plot-error-time-format = 請輸入 HH:mm 時間（00:00–23:59）。
plot-error-transfer-owner-only = 你沒有轉讓所有權的權限。
plot-error-transfer-self = 你已經是此地段的所有者。
plot-flag-set = 已設定 { $arg0 } = { $arg1 }。
plot-help = 說明
plot-here = 目前位置：{ $arg0 } · { $arg1 }
plot-invited = 已授予 { $arg0 } 權限。
plot-item-summary = { $arg0 } 格² · { $arg1 } 人
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } 格²
plot-marked = { $arg0 }：{ $arg1 }
plot-member-draft-hint = 尚未提交
plot-member-draft-reset = 清除此分類設定
plot-member-draft-role = 角色：{ $arg0 }
plot-member-draft-submit = 預覽並確認
plot-member-draft-title = 成員草案 · { $arg0 }
plot-member-permissions = 權限
plot-member-removed = 已移除項目成員。
plot-menu-access = 成員與權限
plot-menu-add-member = 新增成員
plot-menu-atmosphere = 環境
plot-menu-clear = 晴朗
plot-menu-confirm-delete = 確定刪除 { $arg0 } 的登記？保護、成員權限與設定將被移除，無法復原；建築會保留。
plot-menu-confirm-subplot-transfer = 將「{ $arg0 }」轉給 { $arg1 }？母專案所有者仍可管理。
plot-menu-confirm-transfer = 將「{ $arg0 }」的地段管理權轉給 { $arg1 }？你將失去存取權；既有作品署名不變。
plot-menu-confirm-withdraw = 確定撤回這則回饋？撤回狀態仍會公開保留。
plot-menu-create = 建立地段
plot-menu-create-for-player = 為此玩家登錄地段
plot-menu-custom-time = 自訂時間
plot-menu-delete = 刪除地段登記
plot-menu-description = 說明
plot-menu-feedback = 對本公示提出異議
plot-menu-find-record = 依編號尋找紀錄
plot-menu-flag-container = 開啟容器
plot-menu-flag-entity = 操作實體
plot-menu-follow-parent = 跟隨母專案
plot-menu-follow-world = 跟隨世界
plot-menu-group-admin = 地段管理員
plot-menu-group-collaborator = 協作者
plot-menu-group-member = 正式成員預設權限
plot-menu-group-newcomer = 新人預設權限
plot-menu-group-owner = 地段所有者
plot-menu-here = 目前位置
plot-menu-manage = 管理成員地段
plot-menu-managed-empty = 這名玩家尚無地段。
plot-menu-managed-title = { $arg0 } 的地段
plot-menu-members = 成員（{ $arg0 }）
plot-menu-name = 項目名稱
plot-menu-owner = 所有者：{ $arg0 }
plot-menu-parent-supervisor = 母專案所有者：{ $arg0 } · 可監管
plot-menu-player = 玩家名稱或 UUID
plot-menu-rain = 降水
plot-menu-record-search = 編號或標題
plot-menu-related-notice = 查看關聯告知
plot-menu-remove-member = 移除成員
plot-menu-rename = 重新命名項目
plot-menu-rule = 記錄裁決
plot-menu-ruling = 說明裁決理由（8–1000 字）
plot-menu-set-role = 設為{ $arg0 }
plot-menu-settings = 設定
plot-menu-submit = 提交
plot-menu-subplot-create = 建立子地段
plot-menu-subplot-of = 隸屬「{ $arg0 }」
plot-menu-subplot-parent = 返回母專案「{ $arg0 }」
plot-menu-subplots = 子地段（{ $arg0 }）
plot-menu-time = 時間
plot-menu-time-input = 時間（HH:mm）
plot-menu-transfer = 轉讓地段所有權
plot-menu-weather = 天氣
plot-menu-withdraw = 撤回回饋
plot-no-here = 目前位置沒有受保護地段。
plot-notice-board = 地段公告板
plot-notice-cleared = 地段公告板已清除
plot-notice-edit = 編輯地段公告板
plot-notice-input = 最多 { $arg0 } 字，支援多行；留空清除
plot-notice-read = 查看全文
plot-notice-saved = 地段公告板已更新
plot-permission-all-actions = 全部操作
plot-permission-animal = 照料、馴養與拴繩
plot-permission-break = 破壞方塊
plot-permission-bucket = 用桶取放液體與生物
plot-permission-category-build = 建造與修改
plot-permission-category-entity = 實體操作
plot-permission-category-interaction = 方塊互動
plot-permission-category-management = 管理權限
plot-permission-category-storage = 儲物與物品
plot-permission-change-children = 受影響子地段：{ $arg0 }
plot-permission-change-confirm = 確認套用變更
plot-permission-change-granted = 新增授權：{ $arg0 }
plot-permission-change-members = 受影響成員：{ $arg0 }
plot-permission-change-more = 另有 { $arg0 } 項
plot-permission-change-no-effect = 實際權限不變；明確設定將覆蓋後續繼承變更。
plot-permission-change-none = 無
plot-permission-change-revoked = 撤銷授權：{ $arg0 }
plot-permission-change-subject = 對象：{ $arg0 }
plot-permission-change-title = 確認權限變更
plot-permission-create-subplot = 建立子地段
plot-permission-decoration = 放置與修改裝飾實體
plot-permission-default-status = 預設（{ $arg0 }）
plot-permission-delete = 刪除地段
plot-permission-door = 門與柵欄門
plot-permission-entity-damage = 傷害實體
plot-permission-entity-storage = 開啟實體儲物
plot-permission-harvest = 採收漿果、藤蔓與蜂蜜
plot-permission-ignite = 點火
plot-permission-item-drop = 丟棄物品
plot-permission-item-pickup = 拾取地面物品
plot-permission-manage-area = 修改範圍
plot-permission-manage-members = 管理成員
plot-permission-manage-permissions = 管理權限
plot-permission-manage-settings = 修改地段設定
plot-permission-modify = 修改方塊與施肥
plot-permission-operation = 操作
plot-permission-personal = 個人權限
plot-permission-personal-source = 個人設定
plot-permission-place = 放置方塊與載具
plot-permission-reset = 恢復預設
plot-permission-result = 狀態
plot-permission-ride = 乘坐載具與動物
plot-permission-select-state = 選擇狀態：{ $arg0 }
plot-permission-sign = 編輯告示牌
plot-permission-source-group = 所屬組權限
plot-permission-source-local = 本地設定
plot-permission-source-owner = 所有者權限恢復保障
plot-permission-source-parent = 母地段
plot-permission-source-role = 角色預設
plot-permission-state-default = 預設
plot-permission-switch = 按鈕、拉桿與壓力板
plot-permission-trade = 村民交易
plot-permission-transfer = 轉讓所有權
plot-permission-workstation = 使用設施與工作站
plot-pos1 = 第一點
plot-pos2 = 第二點
plot-private = 未公開
plot-public = 公開
plot-region-adjust = 調整矩形
plot-region-apply = 完成矩形調整
plot-region-cancel = 取消矩形調整
plot-region-changed = 選區已改變，請重新選擇矩形。
plot-region-delete = 刪除矩形
plot-region-delete-confirm = 從草稿移除矩形 #{ $arg0 }？不刪除建築；儲存地段後才修改邊界，可復原。
plot-region-edit-hint = 用 //pos1、//pos2 調整目前矩形，完成後儲存
plot-region-finish-first = 請先完成或取消目前的矩形調整。
plot-region-open = 矩形管理（{ $arg0 }）
plot-region-select-hint = 選擇一個矩形進行編輯。
plot-renamed = 已改名。
plot-role-admin = 地段管理員
plot-role-changed-for-you = 你在地段「{ $arg0 }」的身分已變更為{ $arg1 }。
plot-role-collaborator = 協作者
plot-role-owner = 地段所有者
plot-role-parent-owner = 母專案所有者
plot-role-server-staff = 伺服器管理
plot-selection-add = 累加目前框選
plot-selection-clear = 重選兩點
plot-selection-empty = 尚未選點
plot-selection-fit = 恢復已儲存範圍
plot-selection-incomplete = 需選齊兩個角點
plot-selection-redo = 重做上一步
plot-selection-replace = 用目前框選取代
plot-selection-session-hint = 潛行 + F 或 /plot edit 返回選單；20 分鐘無操作後自動退出
plot-selection-size = { $arg0 } × { $arg1 } × { $arg2 } 格（X/Y/Z）
plot-selection-stopped = 實地框選已結束，選區草稿仍按地段保留。
plot-selection-subtract = 扣除目前框選
plot-selection-undo = 復原上一步
plot-subplot-created = 已建立子地段「{ $arg0 }」· { $arg1 } 格²
plot-subplot-created-for-you = 你已成為「{ $arg1 }」內子地段「{ $arg0 }」的所有者。
plot-subplot-owner-changed = 子地段「{ $arg0 }」的所有者已變更為 { $arg1 }。
plot-subplot-parent-transferred = 你的子地段「{ $arg0 }」所屬母專案已轉讓，新母專案所有者為 { $arg1 }。
plot-subplot-released = 子地段「{ $arg0 }」已釋放，原範圍仍由母專案保護。
plot-title = 地段
plot-transferred = 已將地段「{ $arg0 }」轉讓給 { $arg1 }。
plot-transferred-to-you = { $arg1 } 已將地段「{ $arg0 }」轉讓給你。

## World · Vehicles

vehicle-command-description = 駕駛与管理展示實體載具
vehicle-menu-label =
    { $arg0 ->
        [vehicle] 載具控制
        [driving] 駕駛與乘坐
        [maintenance] 維護與權限
        [public-status] 目前公開
        [private-status] 目前私人
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

vehicle-controls = { $arg0 }：W/S、A/D 駕駛；滾輪換檔或調節油门；換手切換駕駛模式；潛行退出。/vehicle
vehicle-dash-assisted = 穩定輔助
vehicle-dash-automatic = 自動檔
vehicle-dash-contact = 接地 / 浮力支撐：{ $arg0 }
vehicle-dash-details = 詳情
vehicle-dash-down = 降檔 −
vehicle-dash-drive = 駕駛
vehicle-dash-energy = 消耗 { $arg0 } 單位/分鐘 · 預計續航 { $arg1 } 分鐘 / { $arg2 } km
vehicle-dash-engine-start = 啟動引擎
vehicle-dash-engine-stop = 關閉引擎
vehicle-dash-fuel = 燃料 { $arg0 } / { $arg1 } 單位 · { $arg2 }%
vehicle-dash-gear = 檔位 { $arg0 }
vehicle-dash-gearbox = 變速箱
vehicle-dash-handbrake = 駐車 / 手煞：{ $arg0 }
vehicle-dash-heading = 航向 { $arg0 }°
vehicle-dash-health = 車況 { $arg0 }% · 乘員 { $arg1 } / { $arg2 }
vehicle-dash-input = 油門 { $arg0 }% · 制動 { $arg1 }% · 轉向 { $arg2 }%
vehicle-dash-manual = 手動檔
vehicle-dash-mode-switch = 切換至{ $arg0 }
vehicle-dash-position = 原點 { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = 面板歸位
vehicle-dash-rpm = 轉速 { $arg0 } / { $arg1 } RPM
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } 秒
vehicle-dash-speed = 速度 { $arg0 } km/h · 上限 { $arg1 }
vehicle-dash-throttle-down = 油門 −5%
vehicle-dash-throttle-up = 油門 +5%
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = 手動操縱
vehicle-dash-unavailable = 駕駛控制不可用，請進入駕駛位後重新開啟。
vehicle-dash-up = 升檔 +
vehicle-dash-vertical = 高度 { $arg0 } m · 垂速 { $arg1 } m/s
vehicle-error = 載具操作失敗：{ $arg0 }
vehicle-group-changed = 分組結構已變更，請重新選擇後再匯入。
vehicle-group-invalid = 分組含不支援、受管、已失效或超出範圍的實體；所有成員須在目前世界的 32 格內。
vehicle-group-limit = 最多匯入 128 個展示實體，分組總成員不得超過 256 個。
vehicle-group-not-found = 未找到展示分組。請先選擇分組、瞄準方塊/物品展示，或指定實體 UUID。
vehicle-group-selected = 已選擇群組：{ $arg0 }個顯示實體 · 根實體 { $arg1 }
vehicle-hud-boat = { $arg0 } 節  ·  推進 { $arg1 }%  ·  航向 { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h  ·  高度 { $arg1 } m  ·  垂速 { $arg2 } m/s  ·  油门 { $arg3 }%  ·  { $arg4 }
vehicle-hud-status = 燃料 { $arg0 }%  ·  車況 { $arg1 }%  ·  { $arg2 }
vehicle-model-center = 重心
vehicle-model-choice = { $arg0 ->
    [CAR] 汽車
    [BOAT] 動力船
    [PLANE] 固定翼飛機
    [BODY] 車身
    [FRONT_WHEEL] 轉向車輪
    [WHEEL] 車輪
    [PROPELLER] 螺旋槳
    [RUDDER] 方向舵
    [FIXED] 固定
    [VERTICAL] 保持直立面向視角
    [HORIZONTAL] 水平面向視角
    [CENTER] 始終面向視角
    [NONE] 原始姿態
    [THIRDPERSON_LEFTHAND] 第三人稱·左手
    [THIRDPERSON_RIGHTHAND] 第三人稱·右手
    [FIRSTPERSON_LEFTHAND] 第一人稱·左手
    [FIRSTPERSON_RIGHTHAND] 第一人稱·右手
    [HEAD] 頭部
    [GUI] 物品欄
    [GROUND] 地面
    [LEFT] 靠左
    [RIGHT] 靠右
    [false] 關閉
    [true] 開啟
    [TEXT_CENTER] 置中
   *[other] { $arg0 }
    }
vehicle-model-field = { $arg0 ->
    [kind] 載具類型
    [mass] 質量
    [maximum-speed] 最高速度（km/h）
    [support-length] 支撐行程（公尺）
    [grip] 抓地力
    [center-of-mass] 重心
    [inertia] 三軸轉動慣量
    [engine-torque] 峰值扭矩
    [engine-idle-rpm] 怠速（RPM）
    [engine-redline-rpm] 限轉（RPM）
    [engine-inertia] 引擎慣量
    [engine-wheel-radius] 車輪半徑（公尺）
    [engine-final-drive] 終傳比
    [engine-reverse-ratio] 倒檔齒比
    [engine-ratios] 前進檔齒比
    [engine-shift-seconds] 換檔時間（秒）
    [fuel-capacity] 燃料容量
    [fuel-consumption] 燃料消耗（單位/秒）
    [buoyancy] 浮力
    [wing-area] 機翼面積（平方公尺）
    [lift] 升力係數
    [animation] 動畫角色
    [billboard] 面向視角
    [item-transform] 物品展示姿態
    [alignment] 文字對齊
    [block-light] 方塊光照
    [sky-light] 天空光照
    [background] 背景色（ARGB）
    [line-width] 文字行寬
    [opacity] 不透明度（0–255）
    [shadow] 文字陰影
    [see-through] 文字穿透顯示
    [x] X
    [y] Y
    [z] Z
    [size-x] 完整寬度 X（公尺）
    [size-y] 完整高度 Y（公尺）
    [size-z] 完整長度 Z（公尺）
   *[other] { $arg0 }
    }
vehicle-model-field-range = 數值範圍：0 < 數值 ≤ { $arg0 }
vehicle-model-front = 車頭方向（+Z）
vehicle-model-front-choice = { $arg0 ->
    [negative-x] 目前 −X 一側
    [back] 目前後方（−Z）
    [positive-x] 目前 +X 一側
    [look] 使用我的水平視線方向（吸附至 90°）
   *[other] 將這一側設為車頭
    }
vehicle-model-front-hint = 在 3D 中選擇車頭箭頭。車身維持原位，座位、出口和物理點同步換算；以 90° 為單位轉向，可整體復原。
vehicle-model-handle = { $arg0 ->
    [ROTATE] { $arg1 } { $arg2 } { $arg3 }°
   *[other] { $arg1 } { $arg2 } { $arg3 }
    }
vehicle-model-marker = { $arg0 ->
    [driver] 駕駛座 #{ $arg1 }
    [passenger] 乘客座 #{ $arg1 }
    [exit] 出口 #{ $arg1 }
    [colliders] 碰撞盒 #{ $arg1 }
    [supports] 支撐點 #{ $arg1 }
    [center] 重心
    [parts] 展示元件 #{ $arg1 }
   *[other] { $arg1 }
    }
vehicle-model-parameter-group = { $arg0 ->
    [body] 車身與地面接觸
    [engine] 引擎與變速箱
    [fuel] 燃料
    [boat] 船隻浮力
    [plane] 飛機升力
    [power] 引擎動力
   *[other] { $arg0 }
    }
vehicle-model-parameter-help = { $arg0 ->
    [body] 調整質量、速度和地面接觸；重心可在 3D 中調整。儲存前只修改草稿。
    [engine] 調整轉速、扭矩、車輪和齒比。前進檔齒比須遞減，限轉須高於怠速的 1.5 倍。
    [fuel] 容量使用遊戲燃料單位；消耗以每秒計。
    [boat] 浮力在船隻類型生效；其他類型的參數繼續保留。
    [plane] 機翼面積和升力在飛機類型生效；其他類型的參數繼續保留。
    [power] 調整目前載具的轉速與扭矩；汽車的變速箱參數繼續保留。
   *[other] { $arg0 }
    }
vehicle-model-property-help = { $arg0 ->
    [block-light] −1 使用世界光照；覆寫亮度時，兩個光照值皆須設為 0–15。
    [sky-light] −1 使用世界光照；覆寫亮度時，兩個光照值皆須設為 0–15。
    [background] 輸入帶正負號的 ARGB 整數，例如 −16777216 表示不透明黑色。
    [line-width] 文字行寬範圍為 1–2048 像素。
    [opacity] 0 表示透明，255 表示不透明。
   *[other] { $arg0 }
    }
vehicle-model-spatial = 進入 3D 點選模式
vehicle-model-spatial-hint = 右鍵點擊標記選取物件或調整數值；蹲下按右鍵返回設定。滾輪切換調整步長。
vehicle-model-spawn = 生成此模型的載具
vehicle-model-add = 新增組件
vehicle-model-add-here = 在腳下新增支撐點
vehicle-model-anchor = 在面前重新擺放
vehicle-model-animation = 動畫
vehicle-model-apply = 套用 / 確認
vehicle-model-auto = 重建包圍碰撞盒
vehicle-model-block = 方塊
vehicle-model-center-here = 將重心設定在腳下
vehicle-model-center-look = 將重心設定在視線命中處
vehicle-model-check = 檢查結構與參數
vehicle-model-checked = 結構與參數校驗通過；儲存時還會檢查展示資料、現有載具相容性與世界碰撞。
vehicle-model-clone = 複製模型
vehicle-model-colliders = 碰撞盒
vehicle-model-copy = 複製並編輯（複製的座位為乘客位）
vehicle-model-corner-cancel = 取消正在製作的碰撞盒
vehicle-model-corner-first = 將腳下標記為碰撞盒第一角
vehicle-model-corner-first-look = 將視線命中處標記為第一角
vehicle-model-corner-hint = { vehicle-model-corner-first } → { vehicle-model-corner-second }
vehicle-model-corner-required = { vehicle-model-corner-first }
vehicle-model-corner-second = 以腳下為對角，新增碰撞盒
vehicle-model-corner-second-look = 以視線命中處為對角，新增盒子
vehicle-model-create = 建立模型
vehicle-model-delete = 刪除模型
vehicle-model-delete-warning = 永久刪除此模型？仍有載具引用的模型不能刪除。
vehicle-model-deleted = 模型已刪除
vehicle-model-discard = 放棄草稿
vehicle-model-discard-warning = 放棄所有未儲存修改？此操作不可復原。
vehicle-model-discarded = 草稿已放棄
vehicle-model-draft = 未儲存草稿
vehicle-model-driver = 駕駛位
vehicle-model-driver-here = 在腳下新增駕駛座位
vehicle-model-edit = 編輯 / 繼續草稿
vehicle-model-overview = 草稿概覽
vehicle-model-panel = 返回編輯面板
vehicle-model-editor-help = { $arg0 ->
   *[overview] 先選下方的編輯分類，再選物件與工具；儲存與復原始終位於底欄。
    [parts] 管理模型可見的方塊、物品與文字。選擇部件後修改內容、位置、旋轉或縮放。
    [seats] 選擇座位，分別設定乘坐位置與下車出口；模型必須保留一個駕駛位。
    [colliders] 選擇碰撞盒，編輯中心與完整尺寸；也可新增盒子或依模型自動包圍。
    [supports] 選擇接地點並調整位置，用於地面支撐或船體浮力取樣。
    [parameters] 先選參數分組，再點選欄位修改數值。
    [tools] 調整預覽、在場景中點選物件或檢查草稿。放棄修改前會確認。
    }
vehicle-model-section-count = { $arg0 ->
   *[parts] { vehicle-model-parts } · { $arg1 }
    [seats] { vehicle-model-seats } · { $arg1 }
    [colliders] { vehicle-model-colliders } · { $arg1 }
    [supports] { vehicle-model-supports } · { $arg1 }
    }
vehicle-model-content-action = { $arg0 ->
   *[block] 設定方塊內容
    [item] 替換為主手物品
    [text] 設定文字內容
    }
vehicle-model-exact-input = 精確輸入數值
vehicle-model-axis-value = { $arg0 } · { $arg1 }
vehicle-model-component-details = 元件詳情
vehicle-model-selection-unavailable = 物件已移除或變更，請返回重新選擇
vehicle-model-exit = 出口位置 / 軸向微調
vehicle-model-group = 加入主手 Axiom 展示組
vehicle-model-hand-invalid = Axiom 展示物品資料無效，或包含不支援的實體或展示屬性。
vehicle-model-hand-required = 請將 Axiom 展示實體物品拿在主手後再匯入。
vehicle-model-here = 使用我目前的腳下位置
vehicle-model-history-empty = 目前沒有可復原或重做的編輯
vehicle-model-import = 匯入主手 Axiom 展示組
vehicle-model-item = 物品
vehicle-model-library = 已儲存模型 · 草稿儲存前僅在記憶體中
vehicle-model-look = 視線放置（關閉選單後右擊確認）
vehicle-model-look-missing = 請看向 32 格內的世界方塊或個人預覽組件；目前沒有命中表面。
vehicle-model-manual = 手動輸入局部座標
vehicle-model-options = 選項
vehicle-model-parameters = 物理與引擎參數
vehicle-model-parts = 展示組件
vehicle-model-passenger-here = 在腳下新增乘客座位
vehicle-model-pick = 編輯正在看向的預覽組件
vehicle-model-pick-hint = 請開啟預覽並看向方塊或物品組件，或先在編輯器中選中組件。
vehicle-model-place-cancelled = 視線放置已取消（逾時、編輯狀態變化或主動取消）。
vehicle-model-place-hint = 視線放置已就緒：30 秒內瞄準世界方塊或個人預覽表面，主手右擊確認；潛行右擊取消。
vehicle-model-position = 位置 / 軸向微調
vehicle-model-position-hint = 局部座標：+Y 向上，+Z 朝車頭。按鈕依步長移動；精確輸入設定絕對位置。
vehicle-model-preview = 個人預覽
vehicle-model-preview-failed = 預覽暫不可用，草稿已保留：{ $arg0 }
vehicle-model-preview-hint = 預覽已顯示。需要直接點選模型時，請進入 3D 點選模式。
vehicle-model-redo = 重做
vehicle-model-remove = 移除組件
vehicle-model-remove-warning = 移除此組件？後續索引會前移，可透過復原恢復。
vehicle-model-rotate = 旋轉部件
vehicle-model-rotate-hint = 相對局部旋轉：X 俯仰、Y 偏航、Z 橫滾；位置保持不變。
vehicle-model-save = 儲存並結束編輯
vehicle-model-save-continue = 儲存並繼續編輯
vehicle-model-saved = 已儲存
vehicle-model-scale = 縮放部件
vehicle-model-scale-all = 所有軸統一縮放 { $arg0 } { $arg1 }
vehicle-model-scale-hint = 按比例乘除局部各軸長度；顯示值不是世界包圍盒尺寸。
vehicle-model-seats = 座位與出口
vehicle-model-selected = 匯入已選展示實體
vehicle-model-size = 調整碰撞盒尺寸
vehicle-model-size-hint = 完整寬、高、長；以不變的中心向兩側調整。
vehicle-model-snap-off = 網格吸附：關閉
vehicle-model-snap-on = 網格吸附：開啟
vehicle-model-step = 切換微調步長
vehicle-model-supports = 地面支撐點
vehicle-model-text = 文字
vehicle-model-title = 載具模型管理
vehicle-model-tools = 預覽與工具
vehicle-model-transform-step = 切換調整步長
vehicle-model-undo = 復原
vehicle-result = 載具：{ $arg0 }
vehicle-selection-info = 已選擇：{ $arg0 }個顯示實體 · 群組 { $arg1 } · 原點 { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = 接觸碰撞 / 受阻
vehicle-status-damage = 車況低於 20%
vehicle-status-disabled = 載具已損壞
vehicle-status-engine-off = 引擎已關閉
vehicle-status-engine-on = 引擎運行中
vehicle-status-gear-limit = 已達控制範圍限制
vehicle-status-grounded = 船體擱淺
vehicle-status-low-fuel = 燃料不足 10%
vehicle-status-manual-only = 請先切換手動檔
vehicle-status-no-fuel = 燃料耗盡
vehicle-status-overrev = 降檔會超過轉速限制
vehicle-status-shifting = 正在換檔
vehicle-status-stall = 失速
vehicle-status-stop-first = 請停穩後切換方向或駐車
vehicle-value-off = 關閉
vehicle-value-on = 開啟

## World · Elevators, regeneration and spawn

async-regen-already-running = 你已有重新生成工作在執行；可用 /regen status 查看或 /regen cancel 取消
async-regen-apply-expired = 目前沒有可套用的有效計畫，請先執行 /regen preview
async-regen-apply-plan = 套用已儲存計畫
async-regen-apply-started = 正在向已儲存的選區套用{ $arg0 }：{ $arg1 }
async-regen-awaiting-apply = 有計畫等待套用：{ $arg0 } · { $arg1 } · { $arg2 } 秒後過期
async-regen-block-preview-progress = 方塊預檢 · 已掃描 { $arg0 }/{ $arg1 } 個區塊 · { $arg2 } 個方塊變更 · { $arg3 } 個方塊實體有風險
async-regen-block-preview-summary = 預檢結果：符合{ $arg2 }的方塊將有 { $arg0 } 項變更，可能覆蓋 { $arg1 } 個現有方塊實體
async-regen-cancelled = 已取消重新生成。已套用的 { $arg0 } 項變更會保留在世界中
async-regen-command-description = 非同步重新生成 FastAsyncWorldEdit 選區
async-regen-completed = 重新生成完成：在 { $arg1 } 內修改了 { $arg0 } 個方塊
async-regen-failed = 重新生成因內部錯誤而停止。已套用的 { $arg0 } 項變更會保留在世界中
async-regen-filter-all = 所有方塊類型
async-regen-invalid-blocks = 無法識別或不是方塊的類型：{ $arg0 }
async-regen-not-running = 你目前沒有正在執行的重新生成工作
async-regen-nothing-to-apply = 選區已符合這份計畫，沒有需要套用的變更
async-regen-plan-cancel = 取消
async-regen-plan-dialog-title = 套用重新生成計畫
async-regen-preview-cancelled = 已取消預檢，世界資料沒有改動
async-regen-preview-started = 已開始預檢：選區共 { $arg0 } 個方塊、{ $arg1 } 個區塊；模式為{ $arg2 }
async-regen-progress = { $arg0 } · 已規劃 { $arg1 }/{ $arg2 } 個區塊 · 已套用 { $arg3 } 項變更
async-regen-safety-note = 已儲存的選區外不會改動；選區內建築仍可能被覆蓋，原版也無法識別玩家放置的普通方塊。套用前請檢查視覺化並備份世界。
async-regen-scope-blocks = 重新生成符合{ $arg0 }的方塊
async-regen-selection-required = 請先完成一個 FastAsyncWorldEdit 選區
async-regen-skipped-ungenerated = 已略過 { $arg0 } 個從未生成的目標區塊，沒有建立或修改這些區塊
async-regen-start-failed = 無法啟動重新生成，請查看伺服器紀錄以瞭解詳情
async-regen-state-applying = 正在套用變更
async-regen-state-cancelled = 已取消
async-regen-state-completed = 已完成
async-regen-state-failed = 已失敗
async-regen-state-generating = 正在生成並比較區塊
async-regen-state-preparing = 正在準備來源世界
async-regen-status = { $arg0 } · 已規劃 { $arg1 }/{ $arg2 } 個區塊 · 已套用 { $arg3 }/{ $arg4 } 項已發現變更
async-regen-structure-metadata-applied = 已遷移 { $arg0 } 個結構起點及 { $arg1 } 個區塊引用；/locate 與依賴結構的生物生成可以識別它們
async-regen-upgrade-apply-progress = { $arg0 } · 已規劃 { $arg1 }/{ $arg2 } 個區塊 · 已套用 { $arg3 } 個方塊及 { $arg4 } 個生物群系單元
async-regen-upgrade-boundary-note = 有 { $arg0 } 個生物群系單元接觸選區邊界，將略過這些單元以免修改選區外區域
async-regen-upgrade-completed = 世界升級完成：在 { $arg2 } 內修改了 { $arg0 } 個方塊及 { $arg1 } 個生物群系單元
async-regen-upgrade-preview-progress = 升級預檢 · 已掃描 { $arg0 }/{ $arg1 } 個區塊 · { $arg2 } 個方塊變更 · { $arg3 } 個生物群系單元 · { $arg4 } 個結構
async-regen-upgrade-preview-summary = 預檢結果：{ $arg0 } 個方塊變更、{ $arg1 } 個生物群系單元、{ $arg2 } 個結構（{ $arg3 } 個可完整遷移結構中繼資料），可能覆蓋 { $arg4 } 個現有方塊實體
async-regen-upgrade-scope-biomes = 僅生物群系
async-regen-upgrade-scope-terrain = 地形、結構與生物群系
async-regen-usage = 用法：/regen preview blocks [方塊類型...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = 選區視覺化位於 { $arg0 }：青色 = 選區邊界，橙色 = 將變更方塊抽樣，紅色 = 可能被覆蓋的方塊實體；粒子只有你可見
async-regen-visualization-selection = { $arg0 } 中的青色粒子表示選區邊界，只有你可見
async-regen-fawe-required = 需要安裝 FastAsyncWorldEdit 才能讀取選區
building-map-layer = 建築收錄
building-map-open = 查看建築
building-map-builders = 建造者：{ $arg0 }
building-map-original-author = 原作者：{ $arg0 }
building-map-type-original = 原創
building-map-type-derivative = 衍生
building-map-type-replica = 復刻
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ 蹲
iron-elevator-tip-mm = 把{ -iron-elevator-block }上下對齊放置就能搭建電梯，站在上面，<green>跳躍上樓</green>、<aqua>潛行下樓</aqua>；兩層起會顯示<gold>樓層面板</gold>
iron-elevator-up = ↑ 跳
spawn-beacon-color-command-description = 開關主城信標顏色切換
spawn-beacon-color-disabled = 已停用主城信標顏色切換
spawn-beacon-color-enabled = 已啟用主城信標顏色切換
spawn-command-description = 返回主城
spawn-tip-return-mm = 用 <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> 返回<yellow>主城</yellow>
spawn-world-missing = 主城世界不可用

## Entertainment · Music

music-album = 專輯: { $arg0 }
music-artist = 歌手: { $arg0 }
music-back = 返回
music-command-description = 本機、線上與 NBS 唱片機音樂
music-disc-left = 左鍵: 拿取唱片
music-disc-right = 右鍵: 在最近的唱片機播放
music-duration = 長度: { $arg0 }
music-format = 格式: { $arg0 }
music-jukebox-audible-field-conflict = 另一台唱片機正在重疊的明顯聽聞範圍內播放不同歌曲
music-jukebox-tp-hover = 點擊傳送到唱片機
music-jukebox-unavailable = 唱片機已被移除或所在區塊無法使用
music-now-playing-rich = 正在播放: { $music } 於唱片機 { $location }
music-online-rate-limited = 音樂請求過多，請在{ $arg0 }秒後重試。
music-original-author = 原作者: { $arg0 }
music-plasmo-voice-unavailable = 歌曲已開始播放，但你未安裝、連線或啟用 Plasmo Voice，因此無法收聽
music-playback-failed = 無法播放「{ $arg0 }」，請檢查音源或音樂檔案後重試
music-reload-failed = 音樂模組重新載入失敗: { $arg0 }
music-reload-started = 正在重新載入音樂檔案和自訂音源...
music-rhythm-animation-offset = 動畫 { $arg0 } ms
music-rhythm-calibration-accumulating = 取樣進行中，請繼續按目前階段的操作方式穩定輸入
music-rhythm-calibration-adapting = 正在對齊節奏，繼續保持連續輸入
music-rhythm-calibration-done = 延遲測試完成
music-rhythm-calibration-failed = 延遲測試音訊意外中斷，測試已安全結束，請重新開始
music-rhythm-calibration-intro = 流程：Minecraft音符盒 → Plasmo Voice → 鍵盤視覺 → 滑鼠視覺
music-rhythm-calibration-listen = 先聽一遍節奏後，對每個 Cue 持續同節奏按鍵
music-rhythm-calibration-minecraft-instruction = 跟著 Minecraft音符盒節拍按 1–4
music-rhythm-calibration-no-input = 未偵測到有效輸入，請按目前階段提示逐拍操作
music-rhythm-calibration-out-of-range = 測得延遲超出支援範圍，請檢查音訊裝置，或退出後重新測試
music-rhythm-calibration-plasmo-instruction = 繼續相同節奏完成 Plasmo Voice，維持固定按鍵節奏，會自動排除異常值
music-rhythm-calibration-plasmo-ready = Minecraft音符盒階段完成，保持同樣節奏繼續Plasmo Voice
music-rhythm-calibration-pointer-instruction = 方塊過線時點擊滑鼠左鍵或右鍵
music-rhythm-calibration-realigning = 聲音串流掉線，重播中
music-rhythm-calibration-required = 先完成延遲測試才能開始
music-rhythm-calibration-reset-done = 延遲校準已重設
music-rhythm-calibration-result-hold = 測試完成，等待結果套用
music-rhythm-calibration-stage-minecraft = Minecraft音符盒
music-rhythm-calibration-stage-plasmo = Plasmo Voice
music-rhythm-calibration-stage-pointer = 滑鼠
music-rhythm-calibration-stage-visual = 視覺
music-rhythm-calibration-visual-instruction = 過線時按 1–4
music-rhythm-calibration-visual-preview = 先完整觀察一遍方塊過線，此時不要操作；提示切換後再逐拍輸入
music-rhythm-calibration-voice-required = 完整延遲測試需要已連線並啟用 Plasmo Voice
music-rhythm-chart-preparing = 正在準備完整譜面…
music-rhythm-combo = 連擊 ×{ $arg0 }
music-rhythm-difficulty-easy = 輕鬆
music-rhythm-difficulty-expert = 專家
music-rhythm-difficulty-hard = 困難
music-rhythm-difficulty-normal = 標準
music-rhythm-exit = 離開節奏遊戲
music-rhythm-get-ready = 準備
music-rhythm-go = 開始！
music-rhythm-good = 良好
music-rhythm-great = 精準
music-rhythm-latency = 延遲 { $arg0 } ms
music-rhythm-latency-test = 延遲測試
music-rhythm-miss = 未命中
music-rhythm-mode-falling = 下落式
music-rhythm-mode-radial = 環向來襲
music-rhythm-mode-spatial-aim = 星域點擊
music-rhythm-network-too-high = 目前網路延遲過高，無法保證公平判定（{ $arg0 } ms；上限 { $arg1 } ms）
music-rhythm-network-warning = 網路延遲不穩定（{ $arg0 } ms）；若持續高於 { $arg1 } ms，本次工作階段將安全結束
music-rhythm-no-beats = 這首歌曲中沒有偵測到可用節拍
music-rhythm-perfect = 完美
music-rhythm-requires-playback = 請先讓這台唱片機播放一首歌曲
music-rhythm-results = 遊玩結算
music-rhythm-score = 分數: { $arg0 }
music-rhythm-spatial-no-room = 周圍空間不足以遊玩星域點擊，請前往更開闊的位置。
music-rhythm-spatial-slider-hint = 跟隨移動星體，逐個點擊軌跡檢查點 · 無需長按
music-rhythm-start = 開始遊戲
music-sample-rate = 取樣率: { $arg0 }
music-size = 大小: { $arg0 }
music-unavailable-disc = 無法使用的唱片

## Entertainment · Anniversary

anniversary-accepted = 已確認參加五週年抽獎活動。非 AFK 上線滿 4 小時後可進行第一輪抽獎。
anniversary-admin-audit-action-bonus = 發放建築收錄獎勵機會
anniversary-admin-audit-action-gift = 贈送活動獎品：{ $arg0 }
anniversary-admin-audit-action-removal = 清除管理員的歷史參加紀錄，並回補 { $arg0 } 件獎品
anniversary-admin-audit-action-unknown = 未知操作：{ $arg0 }
anniversary-admin-audit-empty = 暫無管理稽核紀錄。
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = 操作者：{ $arg0 } ({ $arg1 })；目標：{ $arg2 } ({ $arg3 })
anniversary-admin-audit-title = 五週年管理稽核（顯示 { $arg0 }/{ $arg1 } 筆）
anniversary-admin-bonus-cancelled = 已因大禮包終止
anniversary-admin-bonus-pending = 已發放，尚未抽獎
anniversary-admin-bonus-used = 已抽獎
anniversary-admin-boss = 五週年活動管理 · 輸入 { $arg0 } 查看進度
anniversary-admin-commands = 指令：/anniversary winners [頁碼] | /anniversary audit [玩家] | /anniversary bonus <玩家>
anniversary-admin-control = 動態控制：已釋出 { $arg0 }；目前可發 { $arg1 }；基礎機率 { $arg2 }；近期速度 { $arg3 }
anniversary-admin-draw-bonus = 建築收錄獎勵
anniversary-admin-draw-detail = { $arg0 }：開獎時間 { $arg1 }；結果 { $arg2 }
anniversary-admin-draw-metrics = 機率 { $arg0 }；隨機數 { $arg1 }；演算法版本 { $arg2 }
anniversary-admin-draw-replaced = 已由大禮包取代並回補庫存
anniversary-admin-draw-round = 一般第 { $arg0 } 輪
anniversary-admin-event = 活動狀態：{ $arg0 }；時間：{ $arg1 }
anniversary-admin-excluded = 五週年活動管理員不可參加抽獎，已為你開啟管理概覽。
anniversary-admin-none = 無
anniversary-admin-opportunities = 已實際開獎：{ $arg0 }；目前可抽：{ $arg1 }；已獲得機會：{ $arg2 }；中獎/未中獎：{ $arg3 }
anniversary-admin-participants = 參加玩家：{ $arg0 }
anniversary-admin-player-bag = 最終活動背包：{ $arg0 }
anniversary-admin-player-bonus = 建築獎勵：{ $arg0 }；發放時間：{ $arg1 }；發放人：{ $arg2 } ({ $arg3 })
anniversary-admin-player-history = 抽獎機會明細
anniversary-admin-player-not-found = 找不到活動參加者：{ $arg0 }
anniversary-admin-player-outcome = 中獎：{ $arg0 }；未中獎：{ $arg1 }；已開獎次數：{ $arg2 }；建築獎勵：{ $arg3 }
anniversary-admin-player-progress = 參加時間：{ $arg0 }；有效在線：{ $arg1 }；已開獎一般輪次：{ $arg2 }；目前可抽一般機會：{ $arg3 }
anniversary-admin-player-title = 玩家稽核：{ $arg0 } ({ $arg1 })
anniversary-admin-state-active = 進行中
anniversary-admin-state-ended = 已結束
anniversary-admin-state-scheduled = 未開始
anniversary-admin-stock = 剩餘庫存：{ $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | 合計 { $arg4 }
anniversary-admin-target-excluded = { $arg0 } 是五週年活動管理員，不可獲得抽獎機會。
anniversary-admin-title = 五週年活動管理概覽
anniversary-admin-winners-empty = 目前還沒有實際開獎的中獎紀錄。
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · 開獎時間 { $arg3 }
anniversary-admin-winners-title = 中獎發放清單（第 { $arg0 }/{ $arg1 } 頁，共 { $arg2 } 件）
anniversary-bag-empty = 暫無中獎獎品
anniversary-bag-item-locked = 活動背包為唯讀，無法取出物品
anniversary-bag-item-name = 五週年獎品 · { $arg0 }
anniversary-bag-not-participating = 你尚未參加五週年活動，請先輸入 { $arg0 }。
anniversary-bag-title = 五週年活動背包
anniversary-bag-updated = 獎品已存入活動背包，輸入 { $arg0 } 查看。
anniversary-bonus-already-granted = { $arg0 } 已獲得建築收錄獎勵機會，不能重複發放。
anniversary-bonus-boss-ready = 建築收錄獎勵抽獎已就緒 · 輸入 { $arg0 } 抽獎
anniversary-bonus-broadcast-won = [五週年抽獎] { $arg0 } 透過建築收錄獎勵抽中了 { $arg1 }！
anniversary-bonus-draw-not-won = 建築收錄獎勵抽獎未中獎。
anniversary-bonus-draw-sold-out = 建築收錄獎勵抽獎未中獎：獎品已全數抽完。
anniversary-bonus-draw-won = 建築收錄獎勵抽獎結果：恭喜你獲得 { $arg0 }！
anniversary-bonus-gift-pack-final = { $arg0 } 已抽中大禮包，不能再獲得建築收錄抽獎機會。
anniversary-bonus-grant-not-active = 目前不在活動時間內，無法發放建築收錄獎勵機會。
anniversary-bonus-grant-success = 已向 { $arg0 } 發放建築收錄獎勵機會。
anniversary-bonus-granted = 你已獲得建築收錄獎勵的一次額外抽獎機會，輸入 /anniversary 抽獎。
anniversary-bonus-participant-not-found = 找不到已參加活動的玩家：{ $arg0 }
anniversary-bonus-status-ready = 建築收錄獎勵：1 次額外抽獎機會已就緒。
anniversary-bonus-status-used = 建築收錄獎勵：額外抽獎機會已使用。
anniversary-boss-completed = 伺服器五週年抽獎 · 已完成全部兩輪
anniversary-boss-gift-pack-final = 已獲得五週年大禮包 · 輸入 { $arg0 } 查看活動背包
anniversary-boss-invite = 伺服器五週年紀念品抽獎 · 輸入 { $arg0 } 參加
anniversary-boss-progress = 五週年抽獎 { $arg0 }/{ $arg1 } · 距第 { $arg2 } 輪 { $arg3 }
anniversary-boss-ready = 五週年第 { $arg0 } 輪抽獎已就緒 · 輸入 { $arg1 } 抽獎
anniversary-broadcast-won = [五週年抽獎] { $arg0 } 在第 { $arg1 } 輪抽中了 { $arg2 }！
anniversary-cancel = 暫不參加
anniversary-command-description = 參加伺服器五週年紀念品抽獎活動
anniversary-confirm = 同意並參加
anniversary-confirm-hint = 確認後開始累計非 AFK 上線時間
anniversary-delivery-note = 收件目的地為中國大陸的，請提供可用於隱私寄件的有效順豐 ID；目的地為中國大陸以外地區的，請提供真實有效的收件人姓名、完整地址和聯絡電話，並完成應付運費，否則視為放棄領取。
anniversary-dialog-prizes = 獎品：20 個 { $arg0 }、10 個 { $arg1 }、20 個 { $arg2 }、5 個 { $arg3 }
anniversary-dialog-time = 活動時間：{ $arg0 }
anniversary-dialog-title = 五週年紀念品抽獎活動說明
anniversary-draw-not-won = 第 { $arg0 } 輪抽獎未中獎。
anniversary-draw-sold-out = 第 { $arg0 } 輪抽獎未中獎：獎品已全數抽完。
anniversary-draw-won = 第 { $arg0 } 輪抽獎結果：恭喜你獲得 { $arg1 }！
anniversary-ended = 五週年抽獎活動已結束
anniversary-ended-results = 你的抽獎結果：{ $arg0 }
anniversary-gift-pack-final = 大禮包已成為最終獎品，已取代並回補 { $arg0 } 件其他獎品；後續抽獎機會全部終止。
anniversary-gift-prize-not-owned = 你的活動背包中沒有該獎品：{ $arg0 }
anniversary-gift-received = { $arg0 } 向你贈送了 { $arg1 }，獎品已存入你的活動背包。
anniversary-gift-self = 不能把獎品贈送給自己。
anniversary-gift-success = 已將 { $arg0 } 贈送給 { $arg1 }。
anniversary-gift-usage = 用法：{ $arg0 }
anniversary-not-active = 目前不在五週年活動時間內
anniversary-not-started = 五週年活動尚未開始，活動時間：{ $arg0 }
anniversary-prize-badge = 五週年合照徽章
anniversary-prize-gift-pack = 五週年大禮包
anniversary-prize-keychain = Logo 鑰匙圈
anniversary-prize-mug = Logo 馬克杯
anniversary-prize-not-won = 未中獎
anniversary-prize-sold-out = 獎品已全數抽完
anniversary-rule-1 = 1. 確認參加後，每隔 4 小時可抽獎 1 次，共 2 輪；中獎獎品存入活動專用虛擬背包，同類最多 1 件。抽中大禮包後只保留大禮包，並終止後續抽獎機會。
anniversary-rule-2 = 2. AFK期間不計入抽獎計時，禁止以任何方式繞過 AFK 判定。
anniversary-rule-3 = 3. 實體紀念品的總運費由伺服器補助 80%，補助金額最高為 50 元；中獎者負擔扣除伺服器補助後的全部剩餘運費。
anniversary-rule-4 = 4. 中獎後，收件目的地為中國大陸的，須提供可用於隱私寄件的有效順豐 ID；目的地為中國大陸以外地區的，須提供真實有效的收件人姓名、完整地址和聯絡電話。未依要求提供資料或完成應付運費，視為放棄領取。
anniversary-rule-5 = 5. 禁止使用多個帳號等方式重複參加，違規者將取消活動資格。
anniversary-rule-6 = 6. 參加活動即視為同意以上規則。
anniversary-rule-7 = 7. 官網成功收錄的建築按搬運 0.2、二創 0.5、原創 1 分計；活動前已收錄的建築乘 0.8，共同建造按個人貢獻佔比的 1.5 倍計分。累計積分滿 2 分，可獲 1 次額外抽獎機會，由管理員手動發放。
anniversary-second-round-hint = 累計非 AFK 上線滿 8 小時後可進行第二輪抽獎。
anniversary-status-completed = 你已完成全部兩輪抽獎。
anniversary-status-gift-pack-final = 已抽中大禮包，最終獎品已確定，後續抽獎機會全部終止。
anniversary-status-progress = 已完成：{ $arg0 }/{ $arg1 } 輪；有效上線時間：{ $arg2 }
anniversary-status-remaining = 距離第 { $arg0 } 輪還需非 AFK 上線 { $arg1 }。
anniversary-status-results = 抽獎結果：{ $arg0 }
anniversary-status-title = 五週年抽獎進度

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } 已被封禁
ban-broadcast-operator = 執行者
ban-broadcast-prefix = [封禁公告]
ban-command-description = 按固定嚴重程度封禁玩家
ban-database-error = 封禁資料庫操作失敗，請檢查伺服器日誌
ban-menu-back = 返回
ban-menu-confirm-action = 確認封禁
ban-menu-confirm-severity = 嚴重程度
ban-menu-confirm-title = 確認封禁
ban-menu-never-joined-confirm-action = 仍然封禁
ban-menu-never-joined-title = 玩家從未加入
ban-menu-never-joined-warning = { $arg0 } 從未加入過伺服器。請再次確認玩家名稱無誤，以免封禁錯誤的玩家。
ban-menu-player-input = 手動輸入玩家名稱
ban-menu-select-title = 選擇玩家
ban-menu-severity-title = 選擇封禁程度
ban-history-empty = 找不到 { $arg0 } 的封禁歷史
ban-history-title = 封禁歷史：{ $arg0 }
ban-invalid-player = 玩家名稱格式無效
ban-invalid-severity = 封禁嚴重程度無效
ban-kick-appeal = 如有異議，請在 QQ 群 1138627074 向管理員申訴
ban-kick-server = 米客
ban-kick-title = 你已被伺服器封禁
ban-not-banned = { $arg0 } 目前沒有被封禁
ban-pardon-reason-required = 必須填寫解封原因
ban-permanent = 永久
ban-reason-required = 必須填寫封禁原因
ban-self = 不能封禁自己
ban-severity-excusable = 情有可原（30 天）
ban-severity-extreme = 極其嚴重（1500 天）
ban-severity-minor = 一般違規（150 天）
ban-severity-severe = 嚴重違規（360 天）
ban-severity-warning = 警告（3 天）
ban-status-active = 生效中
ban-status-expired = 已到期
ban-status-revoked = 已解封
ban-success = 已封禁 { $arg0 }，期限：{ $arg1 }
ban-sync-success = 封禁同步完成：{ $arg0 } 筆有效記錄，{ $arg1 } 筆歷史記錄
ban-system-source = 系統
ban-unban-success = 已解除 { $arg0 } 的封禁
ban-unknown-reason = 未提供原因
ban-update-success = 已修改 { $arg0 } 的封禁欄位：{ $arg1 }
banlist-command-description = 查看玩家封禁列表
banlist-copy = 點擊複製玩家名稱
banlist-empty = 目前沒有被封禁的玩家
banlist-expires = 解封時間
banlist-invalid-page = 頁碼無效，共{ $arg0 }頁。
banlist-next = 下一頁
banlist-page = 第 { $arg0 }/{ $arg1 } 頁，共 { $arg2 } 筆記錄
banlist-player = 玩家
banlist-previous = 上一頁
banlist-reason = 原因
banlist-source = 執行者
banlist-title = 封禁列表
maintenance-command-description = 切換或查看維護模式
maintenance-kick = 伺服器維護中
maintenance-set = 已將維護模式{ $arg0 }
maintenance-state-disabled = 關閉
maintenance-state-enabled = 開啟
maintenance-status = 維護模式：{ $arg0 }
maintenance-unchanged = 維護模式已處於{ $arg0 }狀態
performance-kick-title = 伺服器出現嚴重延遲
performance-kick-reason = 為協助伺服器恢復，暫時中斷連線
restriction-entity-too-large-mm = <red>實體尺寸超過伺服器安全限制</red>
restriction-foreign-uuid-mm = <red>你只能在指令中使用自己馴服生物的 UUID</red>
restriction-no-kill-e-mm = <red>禁止使用 <white>/kill @e</white> 指令</red>
restriction-no-newline-mm = <red>訊息中不可換行</red>
restriction-no-run-command-mm = <red>禁止誘導執行指令</red>
restriction-no-selector-mm = <red>你沒有權限使用目標選取器 <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>你沒有權限在指令中提及其他玩家的名字</red>
tpsbar-command-description = 顯示/隱藏 TPS 資訊欄
tpsbar-hidden = 已隱藏 TPS 資訊欄
tpsbar-shown = 已顯示 TPS 資訊欄
tpsbar-tip-status-mm = 用 <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> 顯示或隱藏<yellow>TPS、MSPT 和延遲</yellow>資訊列
unban-command-description = 解除玩家封禁並保留封禁記錄

## Shell · Menus, server information and scripting

main-afk-status =
    { $arg0 ->
        [afk] AFK：暫離中
       *[online] AFK：在線
    }
main-pvp-status =
    { $arg0 ->
        [on] PVP：開啟
       *[off] PVP：關閉
    }
main-announcements = 公告
main-home = 我的家
main-language = 語言
main-language-auto = 自動
main-links = 站外連結
main-map = 網頁地圖
main-menu-title = 主選單
main-music = 音樂
main-name-tag = 名稱標籤
main-settings = 設定
main-website = 官網
main-wiki = Wiki
skript-command-description = 查看伺服器腳本支援
skript-info-ai-option = 不想自行編寫？可把以下提示詞交給 AI：
skript-info-copy = [複製 AI 提示詞]
skript-info-copy-hover = 複製包含版本、文件連結與安全要求的提示詞
skript-info-documentation = 語法與 API 文件
skript-info-review = 腳本經管理員審核與測試後才會載入
skript-info-supported = 目前可用：{ $arg0 }
skript-info-title = 伺服器腳本支援
skript-info-workflow = 依照文件編寫並檢查完整的 .sk 檔案，完成後傳給管理員
stat-client-version = 遊戲版本
stat-first-joined = 首次加入
stat-location = 座標
stat-ping = Ping
stat-ping-value = { $arg0 } 毫秒
stat-play-time = 遊玩時間
stat-role = 目前身份
stat-world = 世界
web-account-title = 網頁帳號與 Passkey
web-account-confirm = 確認
weblogin-question = 確認此次網頁身分驗證？請只確認你在 mcmik.top 上親自發起的操作，不要替他人輸入驗證碼。
weblogin-command-description = 確認 Mik 網頁身分驗證碼
weblogin-confirmed = 已確認網頁身分，請回到瀏覽器繼續
weblogin-invalid-code = 無效的網頁身分驗證碼


# Music workspace
music-space-label =
    { $arg0 ->
        [title] 音乐空间
        [devices] 播放设备
        [all-devices] 全部裝置
        [use-device] 使用此裝置
        [current-device] 目前裝置
        [manage-device] 管理
        [back-devices] 返回裝置清單
        [group-with-devices] 與其他裝置合為群組
        [pick-world-member] 在遊戲中選一台
        [group-world-pick-hint] 空手潛行右鍵目標唱片機，加入目前選擇；/music 返回。
        [group-member-standalone] 請選擇尚未加入同步群組的唱片機。
        [group-selection-help] 勾選清單中的唱片機，或在遊戲中空手潛行右鍵另一台。提交後才會合為群組。
        [group-source-help] 採用所選裝置的佇列與進度。建立會停止原播放；「建立並播放」會繼續播放。
        [device-actions] 操作
        [device-status] 狀態
        [sync-group] 同步組
        [single-device] 唱片機
        [device-location] 位置
        [visit-device] 傳送到旁邊
        [teleporting] 正在傳送…
        [recorded-device] 已記錄
        [nearby-device] 附近
        [group-select-members] 勾選至少兩台唱片機
        [group-name-required] 填寫同步組名稱
        [direction-front] 前方
        [direction-front-right] 右前方
        [direction-right] 右側
        [direction-back-right] 右後方
        [direction-back] 後方
        [direction-back-left] 左後方
        [direction-left] 左側
        [direction-front-left] 左前方
        [direction-here] 旁邊
        [direction-above] 上方
        [direction-below] 下方
        [no-devices] 暫無裝置
        [choose-members] 選擇成員裝置
        [create-group] 建立同步組
        [add-members] 新增裝置
        [create-play] 建立並播放
        [create-and-append] 建立並加入歌曲
        [device-queue] 佇列
        [device-members] 成員
        [available] 可用
        [play] 播放
        [retry-outputs] 重試輸出
        [dissolve-group] 解散同步組
        [remove-member] 移出同步組
        [removed-stopped] 移出的裝置將停止播放，保留所選內容。
        [restore-previous] 恢復加入前內容
        [keep-group-content] 保留同步組內容
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
        [add-playlist] 加入歌單
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
        [song-title] 歌曲名稱
        [track-count] 歌曲數
        [visibility] 可見性
        [no-playlists] 沒有歌單
        [fetching-playlist] 正在讀取歌單…
        [artist] 歌手
        [duration] 時長
        [track-type] 類型
        [type-online] 線上
        [undo] 撤销上次编辑
        [remove] 移除所选条目
        [clear-recents] 清空我的播放记录
        [clear-entries] 清除条目选择
        [search-tracks] 搜尋歌曲、歌手…
        [search-playlists] 搜尋歌單…
        [public-playlists] 公开歌单
        [searching] 正在搜索…
        [insert] 插播並立即播放
        [append] 加入播放佇列
        [choose-targets] 選擇播放裝置
        [group-source] 採用哪台的佇列與進度
        [removed-targets] 部分目標已移除，請重新選擇
        [offline] 离线／区块未加载
        [device-discovery-hint] 空手潛行右鍵唱片機即可開啟裝置。
        [empty] 沒有歌曲
        [queue-empty] 沒有待播歌曲
        [queue-current] 數位佇列目前曲目
        [pause] 暂停
        [resume] 继续播放
        [stop] 停止，保留播放列表
        [previous] 上一首／从头播放
        [clear] 清空待播，当前播完停止
        [seek] 定位
        [more] 更多
        [volume-control] 音量
        [group-volume] 同步群組總音量
        [timeline-help] 滾輪 ±5 秒；右鍵輸入時間
        [timeline-read-only] 此歌曲無法定位
        [seek-format] 輸入 m:ss、h:mm:ss 或秒數
        [record] 記錄以遠端控制
        [forget] 移除我的设备记录
        [alias] 个人设备别名
        [sound] 声音设置
        [rhythm] 音遊房間
        [room-players] 玩家
        [room-player-status] 狀態
        [room-preparation] 我的準備
        [room-ready] 準備
        [room-cancel-ready] 取消準備
        [room-playing] 遊戲中
        [room-score-ready] 譜面就緒
        [room-select-one] 在曲庫開啟多選，選取一首歌曲。
        [room-end-confirmation] 這會結束所有參與者的音遊房間。
        [create-room] 建立房間
        [no-rhythm-devices] 附近沒有唱片機，請先空手蹲下並右鍵點擊唱片機。
        [calibration] 延迟校准
        [volume] 音量（0–100）
        [range] 范围（8–256 格）
        [mode] 播放模式
        [save-queue] 將佇列儲存為歌單
        [sequential] 顺序播放
        [repeat-all] 列表循环
        [repeat-one] 单曲循环
        [shuffle] 随机播放，轮内不重复
        [import] 导入歌单
        [link] 歌單連結
        [partial-import] 仅获取部分歌曲，保存将只包含已获取部分
        [import-preview] 预览已获取信息，尚未下载音频
        [import-new] 新增並開啟
        [import-merge] 合併至歌單
        [stopped] 已停止
        [loading] 正在准备音频
        [cancel-loading] 取消準備
        [playing] 正在播放
        [paused] 已暂停
        [finished] 播放结束
        [retry-failed] 重試播放
        [select-song] 勾選歌曲，批次播放或加入歌單
        [select-toggle] 選擇此項
        [deselect] 取消選擇此項
        [selected] 已選取
        [place-before] 將所選內容放到此項前
        [eject] 弹出实体唱片
        [make-disc] 制作签名唱片
        [gain] 同步组总音量（0–100）
        [retry-import] 重新获取歌单
        [join-room] 加入房間
        [ready] 已準備
        [waiting] 等待中
        [room-mode] 玩法
        [room-difficulty] 難度
        [late-join] 中途加入游戏
        [leave-room] 离开房间
        [select-room-track] 使用所選歌曲
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
        [my-playlists] 我的歌單
        [playlist] 歌單
        [output] 播放裝置
        [close-panel] 收起面板
        [playlist-settings] 歌單設定
        [multi-select] 多選
        [move] 移動
        [move-hint] 點選目標歌曲，放到它之前
        [cancel-move] 取消移動
        [move-end] 放到末尾
        [cancel-selection] 取消多選
        [track-details] 歌曲詳情
        [skip-next] 切到下一首
        [device-settings] 裝置設定
        [already-added] 已在歌單中
        [current-track] 目前歌曲
        [shuffle-candidates] 隨機候選歌曲
        [import-by-id] 透過 ID 匯入
        [playlist-id] 歌單 ID
       *[unknown] 音乐
    }

music-space-error =
    { $arg0 ->
        [UNAVAILABLE] 音乐暂不可用，请稍后重新打开
        [INTERNAL] 音樂發生內部錯誤，請聯絡管理員查看伺服器日誌
        [NOT_FOUND] 所选对象已不存在
        [FORBIDDEN] 需要控制或编辑权限；请检查地段授权及设备记录
        [CONFLICT] 内容已变化，请刷新并重新选择
        [LIMIT] 已达到音乐资源上限
        [INVALID] 请检查名称、所选对象或数值范围
        [OFFLINE] 设备不可用，请到现场加载区块或弹出实体唱片
        [OVERLAP] 听闻范围重叠：请停止冲突设备、缩小范围或建立同步组
        [UNSUPPORTED] 此媒体不支持该操作
        [BUSY] 设备、请求或音游房间正在使用，请结束后重试
        [NO_SAFE_DESTINATION] 唱片機旁沒有安全落點
        [TELEPORT_FAILED] 傳送失敗或被阻止
       *[UNKNOWN] 操作失败
    }

music-space-selected-tracks = 已選 { $arg0 } 首歌曲
music-space-track-count = { $arg0 } 首
music-space-device-count = { $arg0 } 台唱片機
music-space-playback-target = 播放至：{ $arg0 }
music-space-online-count = 在線：{ $arg0 }/{ $arg1 } 台
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

music-partial-results = 部分結果可用，{ $arg0 } 個音源請求失敗

webchat-tab-label = { $arg0 ->
    [1] { player-web-label }
   *[other] { player-web-label } ×{ $arg0 }
    }
webchat-joined = { $arg0 } 加入了網頁聊天
webchat-left = { $arg0 } 離開了網頁聊天
chat-rate-limited = 發送過於頻繁，請稍後再試

plot-map-corner-a = 角點 A
plot-map-corner-b = 角點 B
plot-map-corner-hint = 點擊聚焦此角；右鍵輸入座標，滾輪將此角高度增減 4 格
plot-map-coordinates = 座標 x,y,z
plot-map-zoom-in = 放大
plot-map-zoom-out = 縮小
plot-map-fit = 範圍總覽
plot-map-near = 回到我附近
plot-map-layer = 高度層 Y { $arg0 }…{ $arg1 }
plot-map-layer-hint = 滾輪切換顯示層，點擊回到所在層；地形底圖始終顯示地表
plot-map-north = 向北
plot-map-west = 向西
plot-map-east = 向東
plot-map-south = 向南
plot-map-scale = 每像素 { $arg0 } × { $arg0 } 方塊
plot-map-progress = 讀取地圖 { $arg0 }%…
plot-map-unknown = 未載入的地表取樣點：{ $arg0 }
plot-map-legend = 青：保留 · 紫：新增 · 橙：移除 · 綠：矩形 · A/B：角點 · 箭頭：我
plot-selection-validating = 範圍校驗中…

plot-map-open = 查看原生地圖

plot-map-handheld-hint = 副手顯示 Minecraft 地圖，主手用木斧選點；潛行＋F 返回面板

plot-map-readonly-hint = 目前僅查看地圖；要修改角點，請返回面板選擇「調整此矩形」

plot-destinations = 傳送點
plot-destination-add = 新增目前位置
plot-destination-confirm-delete = 刪除傳送點「{ $arg0 }」？
plot-destination-delete = 刪除傳送點
plot-destination-deleted = 已刪除傳送點。
plot-destination-description = 介紹（可留空）
plot-destination-edit = 編輯名稱與介紹
plot-destination-empty = 暫無可用的傳送點。
plot-destination-error-changed = 傳送點已變更，請重新開啟後操作。
plot-destination-error-description = 介紹最多 512 個字元，請勿使用控制字元。
plot-destination-error-location = 請站在此地段內的安全地面上。
plot-destination-error-missing = 傳送點已不存在。
plot-destination-error-name = 名稱須為一行，包含 1 至 48 個字元。
plot-destination-error-private = 此傳送點尚未公開，僅供地段成員使用。
plot-destination-error-unsafe = 傳送點無法使用或落點不安全。
plot-destination-loading = 正在載入傳送點…
plot-destination-move = 更新為目前位置
plot-destination-name = 名稱
plot-destination-public-empty = 暫無公開傳送點。
plot-destination-public-off = 公開：關
plot-destination-public-on = 公開：開
plot-destination-saved = 已儲存傳送點。
plot-destination-teleport = 傳送
plot-error-subplot-destination = 請先移動或刪除所選範圍內的母地段傳送點。
plot-error-subplot-own-destination = 請將傳送點保留在新範圍內，或先移動或刪除。

plot-destination-manage = 管理
plot-directory-owned = 我擁有的 · { $arg0 }
plot-directory-participating = 我參與的 · { $arg0 }
plot-directory-search = 搜尋地段
plot-directory-search-value = 搜尋：{ $arg0 }
plot-directory-search-field = 地段名稱或編號
plot-directory-clear-search = 清除搜尋
plot-directory-empty = 此分類還沒有地段。
plot-directory-no-match = 沒有符合的地段，請修改或清除搜尋。
plot-workspace-area = 範圍編輯
plot-workspace-area-hint = 調整目前地段的保護範圍
plot-workspace-subplots = 子地段
plot-workspace-subplots-hint = 查看、建立和管理內部劃分
plot-workspace-access-hint = 管理成員、角色與操作權限
plot-workspace-settings-hint = 名稱、公告、環境與所有權
plot-workspace-destinations-hint = 查看並前往此地段的傳送點
plot-workspace-role = 目前身分：{ $arg0 }
plot-workspace-ownership = 所有權與刪除
plot-workspace-id = 地段編號：{ $arg0 }
plot-workspace-groups = 權限群組
plot-workspace-permissions = 操作權限
plot-workspace-other-world = 請先前往此地段所在的世界。

plot-destination-titles = 傳送標題
plot-destination-title = 標題
plot-destination-subtitle = 副標題
plot-destination-title-hint = 傳送成功後顯示。支援 MiniMessage 色彩、樣式與漸層；留空不顯示，預覽不會儲存。
plot-destination-title-preview = 全螢幕預覽
plot-destination-title-unset = 未設定
plot-destination-title-save = 儲存標題
plot-destination-title-unchanged = 沒有尚未儲存的修改。
plot-destination-title-apply = 套用至草稿
plot-destination-error-title = 標題須為單行，顯示文字最多 32 個字元，MiniMessage 原文最多 512 個字元。
plot-destination-error-subtitle = 副標題須為單行，顯示文字最多 64 個字元，MiniMessage 原文最多 512 個字元。
