### MIK · ja-jp
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = 区画
    .gender = other

-vehicle-model = モデル
    .gender = other

## Shared · General

back = 戻る
back-to-main = メインメニューに戻る
cancel = キャンセル
click-open = クリックして開く
close = 閉じる
current-off = 現在: オフ
current-on = 現在: オン
player-only = このコマンドはプレイヤーのみ使用できます
unknown = 不明
usage = 使用法

## Shared · Language, roles and time

language-command-description = 言語設定を開く
language-menu-title = 言語設定
language-selected = 選択中
language-set = 言語を{ $arg0 }に設定しました
role-custodian = 保守担当者
role-member = メンバー
role-moderator = モデレーター
role-new-player = 新規プレイヤー
time-hours-minutes = { $arg0 }時間{ $arg1 }分
time-minutes = { $arg0 }分

## Shared · Menu presentation

interface-fov-apply = FOVを適用
interface-fov-custom = カスタム
interface-fov-description = ゲーム設定の FOV を入力し、メニューの位置を合わせます。
interface-fov-input = クライアントFOV：30～110の整数
interface-fov-invalid = 30～110の整数を入力してください。
interface-fov-title = 視野（FOV）
interface-reduced-motion = 動きを減らす
interface-reduced-motion-hint = メニューの移動と揺れを減らします。
interface-scale-adjust-hint = 割合の項目に合わせてスクロールすると調整できます。
interface-scale-layout-title = メニューサイズ
interface-scale-menu-title = メニューと文字
interface-scale-preview = 例 Aa 123
interface-scale-reset = 100%に戻す
interface-scale-text-title = 文字サイズ
interface-theme-apply = 配色を保存
interface-theme-charcoal = チャコール
interface-theme-color = 色（#RRGGBB）
interface-theme-custom = カスタム
interface-theme-custom-hint = 現在のテーマの全設定を独立したカスタムテーマにコピーします。
interface-theme-edit = 配色を編集
interface-theme-forest = フォレスト
interface-theme-hint = 自分のメニューにすぐ反映されます。
interface-theme-invalid = #RRGGBB 形式の色と 0～100 の不透明度を入力してください。
interface-theme-opacity = 不透明度（0～100%）
interface-theme-role-accent = アクセント色
interface-theme-role-button = ボタン背景
interface-theme-role-button-disabled = 無効なボタン
interface-theme-role-button-hovered = 指しているボタン
interface-theme-role-button-pressed = 押しているボタン
interface-theme-role-button-selected = 選択中のボタン
interface-theme-role-input = 入力欄の背景
interface-theme-role-sidebar = サイドバー背景
interface-theme-role-toolbar = ツールバー背景
interface-theme-role-tooltip = ヒント背景
interface-theme-role-window = ウィンドウ背景
interface-theme-title = メニューテーマ
interface-theme-warm = ウォーム
menu-collapse = 折りたたむ
menu-expand = 展開
menu-finish-editing = 先に入力を完了してください。
menu-input-clear = クリア
menu-input-done = 完了
menu-input-empty = （空）
menu-input-single-line = この欄は一行のみ入力できます。
menu-input-too-long = 長さ: { $arg0 } / { $arg1 }。短くしてから確定してください。
menu-query-cancel = 入力を中止
menu-query-hint-rich = { $chatKey } で入力…
menu-query-single-line = 1行のみ入力できます。入力欄をクリックして再入力してください。
menu-query-too-long = 長さ { $arg0 } / { $arg1 }。入力欄をクリックして再入力してください。
menu-rhythm-game-display = リズムゲーム中
menu-search-empty = 一致する結果はありません
menu-search-failed = 検索に失敗しました
menu-search-limit = { $arg0 } / { $arg1 } を表示。検索条件を絞り込んでください。
menu-search-loading = 検索中…
menu-search-retry = 再試行
menu-usage-display-mm = <aqua><bold>メニュー操作中</bold></aqua>
url-dialog-confirm = 開く
url-dialog-hint = この後、クライアントでリンクを開く確認がもう一度表示されます。
url-dialog-question-rich = { $label }を開きますか？
url-dialog-title = { $arg0 }を開く

## Player · Client and presence

client-mods-undetected-rich = おすすめのMOD：{ $mods }。名前をクリックしてダウンロードできます。
client-tip-axiom-flight-speed-mm = <white><yellow>左 Alt</yellow> を長押しして <aqua>Axiom</aqua> のメニューを開き、右側の縦スライダーで飛行速度を調整できます。<click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click> も使えます。</white>
client-tip-axiom-intro-rich = { $mod } で建築のコピー・貼り付けや地形・ディスプレイエンティティの編集ができます。名前をクリックするとダウンロードできます。
client-version-behind-mm = <white>クライアント <yellow>{ $clientVersion }</yellow> はサーバー <green>{ $serverVersion }</green> より古いバージョンです。更新をおすすめします。</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>近くに<gold>{ $count }人</gold>の<gold>透明化</gold>したプレイヤーがいます</yellow> <gray>·</gray> <gray>最寄りまで約{ $distance }ブロック</gray>
player-afk-label = 離席
player-bedrock-label = 統合版
player-monitor-label = 監視
player-web-label = ウェブ
presence-join-templates =
    .item-1 = ピンポーン、{ $player } がログインしたよ！
    .item-2 = わーい、{ $player } だ！
    .item-3 = { $player }、いらっしゃい！
# Broadcast to remaining players; the departing player does not receive this message.
presence-quit-templates =
    .item-1 = { $player } がログアウトしたよ～
    .item-2 = ピンポーン、{ $player } がオフラインになったよ
    .item-3 = オンライン -1、{ $player } がログアウトしたよ
welcome-first-join-mm = <gold><bold>MIKへようこそ！</bold></gold><newline><gray>まずは<click:open_url:'https://mcmik.top'><yellow>ウェブサイト</yellow></click>と<click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>をご覧ください。<yellow>/menu</yellow>でメインメニューを開くこともできます。</gray>
welcome-return-mm = <gold><bold>おかえりなさい！</bold></gold><newline><gray>前回の参加から<yellow>{ $arg0 }日</yellow>経過しています。よく使う機能は<yellow>/menu</yellow>から確認できます。</gray>

## Player · Guidance

tip-label = ヒント

## Player · AFK

afk-already-afk-mm = <gray>すでに <gold>AFK</gold> です</gray>
afk-command-description = AFK状態にする
afk-default-statuses =
    .item-1 = キーボードから離れました
    .item-2 = 省電力モードになりました
    .item-3 = バックグラウンドに移動しました
    .item-4 = ぼんやりしています
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>がAFKになりました:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>からのメモ:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>がステータスを変更しました:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>がキーボードの前に戻りました</green>
    .item-2 = <gray>*</gray> <player> <green>が戻ってきました</green>
afk-not-afk-mm = <gray>現在<gold>AFK</gold>ではありません</gray>
afk-status-too-long-mm = <red>AFKステータスは最大<white>{ $arg0 }</white>文字までです</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · 一致 { $arg2 } 件 · 相手の記録: { $arg3 }
player-association-header = [ログイン関連] { $arg0 } は過去の { $arg1 } 人とアドレス履歴が一致しています
player-association-hover = 過去のネットワーク記録が一致したことだけを示す調査補助情報です。アドレスは表示されません。
player-association-more-addresses = ほか{ $arg0 }件の記録
player-association-more-players = ほか{ $arg0 }人のプレイヤー
player-association-time-days = { $arg0 }日前
player-association-time-occurrences = { $arg0 }（{ $arg1 } 件）
player-association-time-separator = 、
player-association-time-today = 今日


## Player · Account security
password-already-connected = このアカウントは接続済みです。
password-already-set = パスワードは設定済みです。変更には /password change を使用してください。
password-busy = 試行回数が多すぎます。しばらくしてから再試行してください。
password-change = パスワードを変更
password-change-done = パスワードを変更しました。
password-checking = アカウント保護を確認中…
password-command-description = アカウントのパスワード保護を管理
password-confirm = 新しいパスワードの確認
password-conflict = アカウントのセキュリティ状態が変更されました。操作をやり直してください。
password-cooldown = アカウント認証は一時的に制限されています。{ $arg0 } 秒後に再試行してください。
password-current = 現在のパスワード
password-denied = このアカウントのパスワードをリセットする権限がありません。
password-disabled = パスワード：未設定
password-enabled = パスワード：設定済み
password-invalid = パスワードが違います。再試行してください。
password-keep-role = 管理・保守担当者はパスワードを削除できません。
password-login = ログイン
password-login-expired = ログイン認証が無効になりました。再接続してください。
password-login-hint = このサーバーで設定したアカウントのパスワードを入力してください。忘れた場合は管理者にリセットを依頼してください。
password-mismatch = 新しいパスワードが一致しません。
password-new = 新しいパスワード
password-nomination-required = 管理者への立候補前にパスワードを設定してください：/password set
password-not-set = パスワードが未設定です。/password set で設定できます。
password-remove = パスワードを削除
password-remove-done = パスワードを削除しました。
password-remove-question = 削除後はログイン時にパスワードを確認しなくなります。現在のパスワードを入力して確定してください。
password-required-application = 管理者への立候補または任命が処理中のため、パスワードを削除できません。
password-required-role = 管理・保守担当者のアカウントにはパスワードが必要です。権限のある担当者にリセットを依頼してからログインしてください。
password-reset = パスワードをリセット
password-reset-console = { $arg0 } のパスワードをリセットしました：{ $arg1 }。本人確認後、信頼できる経路で渡してください。
password-reset-done = { $arg0 } のパスワードをリセットしました。今すぐコピーし、本人確認後に渡してください。再表示はできません。
password-reset-question = { $arg0 } のパスワードをリセットしますか？古いパスワードは無効になり、接続は切断されます。自分のパスワードで確認してください。
password-retry = 再試行
password-rules = { $arg0 }～{ $arg1 } 文字で入力してください。プレイヤー名や一般的なパスワードは使用できません。
password-set = パスワードを設定
password-set-done = パスワードを設定しました。
password-set-first = パスワードを設定してから立候補
password-storage = アカウント保護を利用できません。後で再試行するかサーバー所有者に連絡してください。
password-timeout = パスワード認証がキャンセルされたか時間切れになりました。再接続してください。
password-tip-set-mm = <gold><u>/password</u></gold> で<yellow>ログインパスワード</yellow>を設定し、アカウントを保護しましょう。
password-title = アカウント保護

## Player · Profile and tab list

nametag-clear = 消去
nametag-clear-hint = カスタム設定のみ削除します。継承された装飾は残る場合があります。
nametag-command-description = ネームタグをカスタマイズ
nametag-confirm = 確定
nametag-conflict = 現在の値が変更されました。現在の表示を確認し、置き換える場合はもう一度確定してください。
nametag-copy-hint = 接頭辞・接尾辞の原文をチャットに送信
nametag-copy-label = [コピー]
nametag-copy-sent = { $arg0 } のスタイル原文をチャットに送りました。
nametag-current = 現在の表示
nametag-edit-ended = 編集を終了しました。続けるには入力欄をもう一度クリックしてください。
nametag-edit-prompt = T で{ $arg0 }を入力（MiniMessage、自分だけに表示）。
nametag-finish-field = 先に編集中の項目を確定またはキャンセルしてください。
nametag-input-invalid = 形式が無効です。1 行の MiniMessage を送信するか、消去を選んでください。
nametag-leading-label = 名前の前
nametag-mine = 自分のネームタグ
nametag-next = 次へ
nametag-no-changes = 変更なし
nametag-no-permission = メンバー以上がネームタグを編集できます。スタイルの確認は引き続き可能です。
nametag-no-players = ネームタグのスタイルを設定したプレイヤーはいません
nametag-page = { $arg0 } / { $arg1 } ページ
nametag-players = プレイヤーのスタイル
nametag-preview-label = プレビュー
nametag-previous = 前へ
nametag-refresh = 更新
nametag-saving = 保存中…
nametag-set-error = 保存できませんでした：{ $arg0 }。再試行してください。現在の表示は上にあります。
nametag-set-success = ✔ { $arg0 }を設定しました
nametag-styles-failed = スタイルを読み込めませんでした。更新して再試行してください。
nametag-styles-loading = プレイヤーのスタイルを読み込み中…
nametag-tip-customize-mm = <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> で<light_purple>名前の接頭辞と接尾辞</light_purple>をカスタマイズできます。
nametag-title = ネームタグ
nametag-too-long = { $arg0 }は長すぎます。最大{ $arg1 }文字までです
nametag-trailing-label = 名前の後
nametag-unset = （未設定）
nametag-unsupported-placeholder-rich = 使用できるPlaceholderAPI変数は{ $supported }のみです。{ $unsupported }は使用できません
nametag-user-data-load-failed = ユーザーデータを読み込めませんでした。しばらくしてからもう一度お試しください
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = オンライン: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = 直前のテレポート先または死亡地点へ戻る
back-empty-mm = <gray>戻れる場所がありません</gray>
back-pending-mm = <gray>テレポートしています。しばらくお待ちください</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>{ $steps }回前の場所へ戻りました</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>テレポートに失敗しました。もう一度お試しください</red>
back-tip-return-mm = <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> で前回のテレポートや死亡の前にいた場所へ戻れます。
back-tip-undo-mm = <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> で直前の <yellow>/back</yellow> を取り消せます。
back-undo-empty-mm = <gray>取り消せる<white>/back</white>がありません</gray>
back-undo-success-mm = <green>直前の<white>/back</white>を取り消しました</green>
back-undo-world-missing-mm = <red>移動先のワールドを利用できないため、取り消せません</red>
back-world-missing-mm = <red>移動先のワールドを利用できないため、戻れません</red>
boundary-near-mm = <yellow>ワールドボーダーに近づいています</yellow>
boundary-reached-mm = <red>ワールドボーダーに到達しました</red>
fly-command-description = 個人飛行を切り替え
fly-disabled = 飛行を無効にしました
fly-enabled = 飛行を有効にしました
fly-native-mode = /fly の権限を無効にしました。このゲームモードでは引き続き飛行できます。
fly-speed-click = 飛行速度を { $arg0 }x に設定
fly-speed-current = 飛行速度: { $arg0 }x
fly-speed-limited = 探索による負荷のため飛行速度を一時的に { $arg0 }x に制限しています。設定値は { $arg1 }x のままです。
fly-speed-presets = プリセット
fly-speed-reset = 飛行速度を 1x に戻しました
fly-speed-reset-action = リセット
fly-speed-reset-click = 飛行速度を 1x に戻す
fly-speed-set = 飛行速度を { $arg0 }x に設定しました
fly-state-disabled = 無効
fly-state-enabled = 有効
fly-status = 飛行: { $arg0 } | 速度: { $arg1 }x
home-action-create = ホームを作成
home-action-delete = ホームを削除
home-action-teleport = テレポート
home-action-update = 場所を更新
home-already-exists = 同じ名前のホームが既にあります
home-confirm-delete = 削除の確認
home-confirm-delete-detail-rich = ホーム「{ $home }」を削除
home-confirm-delete-warning = この操作は元に戻せません
home-confirm-update = 更新を確認
home-confirm-update-detail-rich = ホーム「{ $home }」を次の場所に更新
home-delete-command-description = ホームを削除
home-deleted-rich = ホーム「{ $home }」を削除しました
home-distance = 距離: { $arg0 }
home-distance-blocks = { $arg0 }ブロック
home-distance-other-world = 別のワールド
home-empty-description = ホームに設定したい場所に立ってください
home-empty-title = ホームがありません
home-invalid-name = ホーム名には < > : を使用できません
home-list-rich = ホーム: { $homes }
home-location = 座標: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = このホームの位置データは無効です
home-max-reached = 設定できるホームは最大{ $arg0 }件です
home-menu-title = ホーム
home-no-homes = ホームがまだ設定されていません
home-not-found-rich = ホーム「{ $home }」は存在しません
home-search-placeholder = 家の名前を検索…
home-search-empty = 該当するホームがありません
home-set-command-description = 現在地をホームに設定
home-set-rich = ホーム「{ $home }」を設定しました
home-summary-count = 設定数: { $arg0 } / { $arg1 }
home-teleport-failed = テレポートに失敗しました。もう一度お試しください
home-teleported-rich = ホーム「{ $home }」へテレポートしました
home-tip-create-mm = <green><u><click:suggest_command:'/sethome '>/sethome 名前</click></u></green> で現在地を保存し、<aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> で戻れます。
home-tip-return-mm = <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> でホームメニューを開き、行き先を選んで「{ home-action-teleport }」を押してください。
home-updated-rich = ホーム「{ $home }」の位置を更新しました
home-usage-book = コマンドヘルプ
home-usage-delhome = /delhome <name>  ホームを削除
home-usage-home = /home <name>  ホームへ直接テレポート
home-usage-name-rule = ホーム名には < > : を使用できません
home-usage-sethome = /sethome <name>  現在地を保存
home-world = ワールド: { $arg0 }
home-world-end = · ジ・エンド
home-world-nether = · ネザー
reback-command-description = 直前の/backを取り消す
teleport-accept-command-description = 保留中のテレポート申請を承認
teleport-allow = 他のプレイヤーによるテレポート
teleport-bare-entity-selector = 誤ったテレポートを防ぐため、角括弧のない @e は使用できません。@e[type=…] のように明示してください。
teleport-block-afk = AFK中は拒否
teleport-denied = { $player }は現在、他のプレイヤーによるテレポートを拒否しています
teleport-deny-command-description = 保留中のテレポート申請を拒否
teleport-force-command-description = プレイヤーの設定を無視して強制テレポート（管理者専用）
teleport-menu-title = テレポート設定
teleport-moved-here = { $arg0 }によってここへテレポートされました
teleport-policy-always-allow = 常に許可
teleport-policy-always-deny = 常に拒否
teleport-policy-require-consent = 自分の承認が必要
teleport-request-accept = 承認
teleport-request-approved = { $player }がテレポート申請を承認しました
teleport-request-deny-action = 拒否
teleport-request-expired = テレポート申請の期限が切れました
teleport-request-failed = テレポートを実行できませんでした。もう一度お試しください
teleport-request-none = 保留中のテレポート申請はありません
teleport-request-received = { $sender }があなたをテレポートしようとしています：{ $command }
teleport-request-rejected = テレポート申請を拒否しました
teleport-request-sent = { $player }にテレポート申請を送信しました
tpany-command-description = オンラインまたはオフラインのプレイヤーの場所へテレポート
tpany-done-offline-rich = { $player }の最終確認地点へテレポートしました { $world } { $location }
tpany-done-online-rich = { $player }の現在地へテレポートしました
tpany-not-found-rich = { $player }の場所が見つかりませんでした
tpany-usage-command = /tpany <player>
tpany-usage-desc = オフライン時の記録を含むプレイヤーの場所へテレポート

## Player · PVP

pvp-admin-menu-title = PvP管理
pvp-allow-mounted-damage = 騎乗中のMobへの攻撃を許可
pvp-allow-mounted-damage-desc = お互いのPvPが有効な場合、他のプレイヤーが騎乗中のMobを攻撃できます
pvp-auto-enable-desc = PvPが有効なプレイヤーを2回連続で攻撃すると有効化
pvp-auto-enable-label = 自動有効化
pvp-auto-enable-warning-actionbar-mm = <yellow>もう一度攻撃するとPvPが自動で有効になります</yellow>
pvp-auto-enabled-actionbar-mm = <green>PvPが自動で有効になりました</green>
pvp-combat-locked-mm = <red>戦闘中はPvPを無効にできません</red> <dark_gray>|</dark_gray> <gray>残り時間：<yellow>{ $arg0 }</yellow>秒</gray>
pvp-combat-tag-label = 戦闘クールダウン
pvp-combat-tag-value = { $arg0 }秒
pvp-command-description = PvPの有効状態を切り替え
pvp-enable = PvPを有効化
pvp-enable-desc = 有効にすると、PvPが有効なプレイヤー同士で攻撃できます
pvp-enable-on-death = 死亡時にPvPを有効化
pvp-enable-on-death-desc = 有効にすると、死亡時にPvPが自動で有効になります
pvp-enable-on-death-label = 死亡時に有効化
pvp-enabled-on-death-mm = <yellow>死亡したためPvPが有効になりました</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>オンラインプレイヤーのPvP管理を開く</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>PvP設定を開く</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>オンラインプレイヤーを管理</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>自分のPvP状態を表示</gray>
pvp-help-title-mm = <gold><bold>PvPコマンド</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>自分のPvPを切り替え</gray>
pvp-menu-title = PvP設定
pvp-mob-protected-actionbar-mm = <yellow>このMobはPvPダメージから保護されています</yellow>
pvp-mob-protection-label = Mobの保護
pvp-mounted-damage-label = 騎乗中のMobへのダメージ
pvp-no-permission-mm = <red>他のプレイヤーのPvP設定を管理する権限がありません</red>
pvp-player-not-found-mm = <red>オンラインのプレイヤーが見つかりません</red>
pvp-protect-mobs = 自分のMobを保護
pvp-protect-mobs-desc = 自分が手懐けた、または騎乗中のMobを保護
pvp-quick-combat-cooldown = 戦闘クールダウン：残り{ $arg0 }秒
pvp-quick-override = 一時的なルールによりPvPが制御されています。個人設定は設定画面で変更できます。
pvp-set-by-staff-mm = <aqua>スタッフがあなたのPvPを次の状態に設定しました:</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = { $arg0 }のPvPを{ $arg1 }に設定しました
pvp-set-other-rich = { $player }のPvPを{ $state }に設定しました
pvp-state-label = PvP
pvp-state-off = オフ
pvp-state-on = オン
pvp-status-player = プレイヤー: { $arg0 }
pvp-status-player-rich = プレイヤー: { $player }
pvp-status-title-mm = <gold><bold>PvPステータス</bold></gold>
pvp-summary-disabled = PvP: オフ
pvp-summary-enabled = PvP: オン
pvp-target-disabled-actionbar-mm = <yellow>そのプレイヤーはPvPを有効にしていません</yellow>
pvp-target-menu-title = PvP設定 - { $arg0 }
pvp-tip-protection-mm = <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple> で自分のPVPを切り替えられます。<green>両方のプレイヤーが有効にすると</green>、互いに攻撃できます。
pvp-toggled-off-mm = <gray>PvPを無効にしました</gray> <dark_gray>|</dark_gray> <gray>他のプレイヤーから攻撃されません</gray>
pvp-toggled-on-mm = <green>PvPを有効にしました</green> <dark_gray>|</dark_gray> <gray>PvPが有効なプレイヤーと戦闘できます</gray>

## Player · Effects

effect-cleanup-all = [すべての効果を解除]
effect-cleanup-blocked = PVP 戦闘中でないクリエイティブまたはスペクテイターモードでのみ解除できます。
effect-cleanup-expired = この解除リクエストは期限切れです。
effect-cleanup-negative = [マイナス効果を解除]
effect-cleanup-prompt-rich = { $source } によりマイナス効果が付与されました。解除しますか？
effect-cleanup-result = { $count } 種類の効果を解除しました。
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] 残留ポーションの雲
        [ARROW] 矢
        [ATTACK] 攻撃
        [COMMAND] コマンド
        [FOOD] 食べ物
        [PLUGIN] プラグイン
        [POTION_DRINK] ポーションの使用
        [POTION_SPLASH] スプラッシュポーション
        [WARDEN] ウォーデン
        [WITHER_ROSE] ウィザーローズ
       *[OTHER] 不明な原因
    }

## Player · Trash

trash-clear = 空にする
trash-clear-hint = クリックすると中のアイテムをすべて削除します。
    クリックしなくても、閉じてから { $arg0 } 秒後に自動削除されます。
trash-command-description = ゴミ箱を開く
trash-sort = 整理
trash-sort-hint = 同じアイテムをまとめて種類順に並べる
trash-tip-dispose-mm = <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> で<yellow>ゴミ箱</yellow>を開き、不要なアイテムを入れられます。
trash-title = ゴミ箱

## Player · Utilities

hat-command-description = 利き手に持っているアイテムを頭に装備
hat-empty-hand-mm = <red>利き手にアイテムを持っていません</red> <gray>ブロックまたはアイテムを持ってから/hatを使用してください</gray>
hat-success-mm = <green>利き手のアイテムを頭に装備しました</green> <dark_gray>|</dark_gray> <gray>利き手と頭の装備スロットを入れ替えました</gray>
hat-tip-wear-mm = <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> で<yellow>メインハンドのアイテム</yellow>を頭にかぶれます。
removeitems-command-description = ドロップアイテムを削除
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = ワールド{ $world }の{ $count }個のドロップアイテムを削除しました { $location } { $radius }
removeitems-no-world = ワールドを特定できませんでした
removeitems-radius = 半径{ $arg0 }ブロック
removeitems-tip-usage-mm = <aqua><u><click:suggest_command:'/rmitems '>/rmitems [半径]</click></u></aqua> で近くの<yellow>ドロップアイテム</yellow>を削除できます。
self-kick-command-description = 自分をサーバーからキックする
self-kick-reason-too-long = キック理由は{ $arg0 }文字以内にしてください
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] 自分の操作でサーバーから退出しました。理由：{ $reason }
       *[third] { $player } が自分の操作でサーバーから退出しました。理由：{ $reason }
    }
    .item-1 = { $player } が自分の操作でサーバーから退出しました。理由：{ $reason }
    .item-2 = { $player } がログアウトしました。メッセージ：{ $reason }
    .item-3 = { $player } がサーバーから退出しました。メッセージ：{ $reason }
    .item-4 = { $player } が退出時にメモを残しました：{ $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] 自分の操作でサーバーから退出しました。
       *[third] { $player } が自分の操作でサーバーから退出しました。
    }
    .item-1 = { $player } が自分の操作でサーバーから退出しました。
    .item-2 = { $player } がログアウトしたよ～
    .item-3 = ピンポーン、{ $player } がオフラインになったよ
    .item-4 = オンライン -1、{ $player } がログアウトしたよ

## Community · Chat and mentions

chat-content-audio = [音声]
chat-content-card = [カード]
chat-content-contact = [連絡先]
chat-content-emote = [アクション]
chat-content-face = [絵文字]
chat-content-file = [ファイル]
chat-content-history = [チャット履歴]
chat-content-image = [画像]
chat-content-location = [位置情報]
chat-content-message = [メッセージ]
chat-content-multiple = [複数のメッセージ]
chat-content-music = [音楽]
chat-content-notice = [通知]
chat-content-reply = [引用メッセージ]
chat-content-sticker = [ステッカー]
chat-content-video = [動画]
chat-copy-hover = 左クリックしてメッセージをコピー
chat-delay-3s = 3秒
chat-delay-5s = 5秒
chat-delay-7s = 7秒
chat-delay-cancel-command-description = 送信待ちの遅延メッセージをキャンセル
chat-delay-cancelled = 送信待ちのメッセージをキャンセルしました
chat-delay-current = 現在の送信遅延: { $arg0 }
chat-delay-nothing-to-cancel = 送信待ちのメッセージはありません
chat-delay-off = オフ
chat-delay-preview-marker = [送信待ち]
chat-delay-queued = { $arg0 }後に送信します。キャンセルするには/cancelまたは/cを使用してください
chat-item-empty-hover = 利き手にアイテムを持っていません
chat-mention-all-hover = このチャンネルで表示中の全プレイヤーに通知
chat-msg-command-description = プライベートメッセージを送信し、プライベートチャットに切り替え
chat-player-hover = 左クリックしてプライベートメッセージを開始
chat-private-enter = { $arg0 }とのプライベートチャットに切り替えました
chat-private-label = MSG
chat-private-no-reply-target = 直前のプライベートチャット相手はオンラインではありません
chat-private-self = 自分とはプライベートチャットを開始できません
chat-private-target-not-found = オンラインのプレイヤーが見つかりません: { $arg0 }
chat-private-target-offline = 相手はオフラインです。接続を待つか別のチャンネルを選んでください
chat-private-tip-channel-mm = <aqua><u><click:suggest_command:'/msg '>/msg プレイヤー</click></u></aqua> で個人チャットに切り替え、<yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> で公開チャットに戻れます。
chat-public-command-description = パブリックチャットに切り替え
chat-public-enter = パブリックチャットに切り替えました
chat-reply-command-description = 直前のプライベートチャット相手に返信
chat-settings-menu-title = チャット設定
chat-social-account-label = プラットフォームアカウント
chat-social-bound-player-label = 連携プレイヤー
chat-social-name-label = ソーシャル名
chat-social-unbound = Minecraftプレイヤーと未連携
chat-staff-command-description = スタッフチャットに切り替え
chat-staff-label = スタッフ
chat-staff-permission-missing = スタッフチャットの権限がないため、パブリックチャットに戻りました
mention-action-bar = アクションバー通知
mention-action-bar-text = { $sender }からメンションされました
mention-alerts = メンション通知
mention-menu-title = メンション通知
mention-mute-afk = AFK中はミュート
mention-sound = 通知音
staffchat-enter = スタッフチャットに参加しました

## Community · Announcements

announcement-count = 公告 { $arg0 } 件
announcement-empty = お知らせはありません
announcement-first-page = 最新のお知らせです
announcement-last-page = 最も古いお知らせです
announcement-latest = 最新の公告
announcement-list-newer = 新しい一覧
announcement-list-older = 古い一覧
announcement-more-count = ほか { $arg0 } 件は未表示
announcement-new-count = 新着 { $arg0 } 件
announcement-newer = 新しいお知らせ
announcement-next-page = 次のページ
announcement-older = 以前のお知らせ
announcement-page = { $arg0 } / { $arg1 } ページ
announcement-previous-page = 前のページ
announcement-reload = お知らせを再読み込み
announcement-reload-command-description = お知らせファイルを再読み込み
announcement-reload-failed = お知らせを再読み込みできませんでした。以前のお知らせは保持されています
announcement-reloaded = お知らせを再読み込みしました
announcement-unread-count = 未読 { $arg0 } 件
announcement-updated = お知らせを更新しました
announcement-view-all = すべてのお知らせを見る
announcement-view-command-description = サーバーのお知らせを見る

## Community · Identity and social platforms

identity-admission-bind-rich = { $platform }：{ $command }
identity-admission-instructions = プラットフォームを選び、対応するコマンドを送信：
identity-admission-pending = 現在の連携はまだ確認できません。後で再試行するか、別のプラットフォームのアカウントを連携してください
identity-admission-reconnect = 連携後、サーバーに再接続してください
identity-admission-required = コミュニティのアカウントを連携すると参加できます
identity-admission-revoked = 有効なコミュニティ連携がなくなりました。再接続して連携手順を確認してください
identity-admin-binding-not-found = 選択した連携は存在しません。
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = { $arg1 } の選択した { $arg0 } の連携を解除しました。
identity-admin-command-description = 外部 ID の連携を確認または解除する
identity-admin-help = /bindadmin lookup <プレイヤーまたはUUID>
    /bindadmin unlink <binding-id> confirm
identity-admin-lookup-empty = このプレイヤーの連携は見つかりませんでした。
identity-admin-lookup-title = { $arg0 } の連携：
identity-binding-community = コミュニティ #{ $arg0 }
identity-binding-info = 連携日：{ $arg0 }
    連携 ID：{ $arg1 }
identity-binding-label-rich = { $kind ->
    [community] { $platform } · { $account } · { $community }
   *[account] { $platform } · { $account }
    }
identity-binding-not-owned = 選択した連携は存在しないか、あなたのものではありません。
identity-binding-reference-rich = { $label } · #{ $reference }
identity-binding-unlinked = 選択した { $arg0 } の連携を解除しました。
identity-binding-unnamed = ニックネーム未設定
identity-bindings-empty = 外部プラットフォームのアカウントは連携されていません。
identity-bindings-title = 連携済みアカウント
identity-code-cancelled = 未使用の { $arg0 } 連携コードを取り消しました。
identity-code-copy = クリックして完全な連携コマンドをコピー
identity-code-expiry = コードの有効期間：{ $arg0 } 分
identity-code-not-pending = 未使用の { $arg0 } 連携コードはありません。
identity-code-redeem-rich = { $platform } のコミュニティで Bot に送信：{ $command }
identity-command-description = Minecraft を外部プラットフォームの ID と連携する
identity-community-join-rich = コミュニティに参加：{ $link }
identity-help = ID 連携コマンド：
    /bind qq — QQ 連携コマンドをすぐに発行
    /bind <プラットフォーム> — 使い捨てコードを発行
    /bind list — 既存の連携を表示
    /bind cancel <プラットフォーム> — 未使用コードを取り消す
    /bind unlink — 解除する連携を1つ選ぶ
identity-notify-linked = { $arg0 } のアカウントを連携しました。/bind list で連携の確認と管理ができます。
identity-notify-unlinked = { $arg0 } の ID 連携を解除しました。
identity-platform-none = なし
identity-platforms = 対応プラットフォーム：{ $arg0 }
identity-storage-error = ID 連携データベースの処理に失敗しました。管理者に連絡してください。
identity-unavailable = ID 連携は現在利用できません。管理者に連絡してください。
identity-unlink-action = [連携解除]
identity-unlink-hint = 選択したこの連携だけを解除します。
identity-unsupported-platform = 未対応のプラットフォーム：{ $arg0 }
social-admin-conversations-empty = { $arg0 } で確認された会話はありません。
social-admin-conversations-title = { $arg0 } で確認された会話：
social-admin-help = /social platforms · /social status [プラットフォーム] · /social reload [プラットフォーム] · /social conversations <プラットフォーム>
social-admin-metrics = 受信 { $arg0 }／再試行 { $arg1 } · 送信 { $arg2 }／バックプレッシャー { $arg3 } · 失敗 { $arg4 }
social-admin-platform = - { $arg0 }（{ $arg1 }）：{ $arg2 }
social-admin-reloaded = ソーシャルプラットフォーム { $arg0 } を再読み込みしました。
social-admin-reloaded-all = すべてのソーシャルプラットフォームを再読み込みしました。
social-admin-status = { $arg0 } · { $arg1 } · 世代 { $arg2 } · 会話 { $arg3 }
social-admin-title = ソーシャルプラットフォーム
social-admin-unknown = 不明なソーシャルプラットフォーム：{ $arg0 }
social-binding-already-linked = すでに { $arg0 } と連携しています。{ $arg1 } でプロフィールを確認できます。
social-binding-confirmation-expired = 確認の有効期限が切れたか、連携アカウントが変更されました。もう一度解除を要求してください：
social-binding-code-invalid = このコードは無効か期限切れです。再接続するか、ゲーム内で { $arg0 } を実行して新しいコードを取得してください。
social-binding-external-in-use = この識別情報はすでに別の Minecraft プレイヤーと連携されています。
social-binding-help = Minecraft サーバーに接続し、ログイン画面の連携コマンドをそのままここに送ってください。すでにゲーム内にいる場合は { $arg0 } で取得できます。
social-binding-identity-missing = このメッセージには信頼できるメンバー識別情報がないため、連携できません。
social-binding-link-completed = アカウントを連携しました。再接続するとログインを続けられます。
social-binding-linked = ゲームアカウント { $arg0 } と連携しました。再接続するとログインを続けられます。
social-binding-membership-required = このプラットフォームの連携コミュニティに参加してから、もう一度連携してください
social-binding-membership-unavailable = コミュニティへの参加状況を確認できません。しばらくしてから再試行するか、サーバー管理者に連絡してください
social-binding-not-bound = まだゲームアカウントと連携していません。
social-binding-platform-mismatch = この確認コードは別のプラットフォーム用です。
social-binding-player-scope-in-use = この Minecraft プレイヤーには、同じプラットフォームアカウントのスコープで別の ID が連携されています。
social-binding-rate-limited = 失敗回数が多すぎます。約 { $arg0 } 分後に再試行してください。
social-binding-unavailable = 現在、識別情報の連携を利用できません。
social-binding-unlink-completed = 連携を解除しました。他に有効なコミュニティ連携がない場合、参加前に再度連携してください。
social-binding-unlink-prompt = このプラットフォームのアカウントと { $arg0 } の連携を解除します。他に有効なコミュニティ連携がない場合、サーバーに参加できなくなり、接続中の場合は切断されます。確認するには次を送ってください：
social-binding-unlinked = Minecraft プレイヤー { $arg0 } との連携を解除しました。
social-command-description = ソーシャルプラットフォームアダプターを管理します
social-command-invalid-arguments = 引数が正しくありません。次の形式で送信してください：
social-command-usage = このコマンドに引数は不要です。次を送ってください：
social-crypto-command-description = Matrix 暗号化デバイスの検証を管理
social-crypto-comparison-not-ready = SAS の比較はまだ準備できていません。
social-crypto-device = { $arg0 } / { $arg1 }
    Ed25519: { $arg2 }
social-crypto-failed = デバイスの検証に失敗しました。サーバーログを確認してください。
social-crypto-no-active = 進行中のデバイス検証はありません。
social-crypto-not-accepting = 現在の検証は承認待ちではありません。
social-crypto-not-running = Matrix は起動していません。
social-crypto-requested = 検証を要求しました：{ $arg0 }
social-crypto-state = { $arg0 ->
    [initializing] 初期化中
    [verified] 検証済み
    [unverified] 未検証
    [no_cross_signing] クロス署名無効
    [requested] 要求済み
    [ready] 準備完了
    [started] 開始済み
    [comparison] 比較待ち
    [waiting] 完了待ち
    [done] 完了
    [cancelled] キャンセル済み
   *[unknown] 不明
    [valid] 有効な鍵
    [cross_signed] クロス署名済み
    [not_cross_signed] クロス署名なし
    [blocked] ブロック済み
    [invalid] 無効な鍵
    }
social-crypto-status = 検証：{ $arg0 }
    鍵バックアップのバージョン：{ $arg1 }
social-crypto-target = { $arg0 } / { $arg1 }: { $arg2 } · { $arg3 }
social-crypto-transaction = { $arg0 }: { $arg1 } / { $arg2 } · { $arg3 }
social-crypto-transaction-required = 現在の検証トランザクションの完全な ID を指定してください。
social-crypto-unavailable = 利用不可
social-crypto-updated = 検証を更新しました。matrixcrypto status で確認してください。
social-crypto-usage = matrixcrypto status [ユーザーID デバイスID]
    matrixcrypto verify <ユーザーID> <デバイスID>
    matrixcrypto accept|confirm|cancel <トランザクションID>
social-duration-days = { $arg0 } 日 { $arg1 } 時間 { $arg2 } 分
social-duration-hours = { $arg0 } 時間 { $arg1 } 分
social-duration-minutes = { $arg0 } 分
social-help-command-description = このコマンド一覧を表示
social-link-command-description = ゲーム内コードで Minecraft アカウントを連携
social-link-command-parameters = <認証コード>
social-online-command-description = オンラインプレイヤーを表示
social-profile-afk = オンライン · AFK
social-profile-avatar-alt = Minecraft の顔
social-profile-command-description = 自分を表示；正式メンバーは名前、メンション、返信で他のプレイヤーを検索可能
social-profile-command-parameters = [プレイヤー名]
social-profile-first-joined-label = 初参加
social-profile-language-label = 言語
social-profile-last-seen-label = 最終オンライン
social-profile-member-required = 連携済みの正式メンバー、モデレーター、保守担当者だけが他のプレイヤーを検索できます。
social-profile-offline = オフライン
social-profile-online = オンライン · アクティブ
social-profile-play-time-label = プレイ時間
social-profile-player-label = プレイヤー
social-profile-player-not-found = その Minecraft プレイヤーは見つかりませんでした。
social-profile-presence-label = 状態
social-profile-target-ambiguous = プレイヤー名、1人へのメンション、または1人への返信のいずれか1つを指定してください。
social-profile-target-unbound = メンションまたは返信先のユーザーは Minecraft と連携されていません。
social-profile-uuid-label = UUID
social-query-metric = { $arg0 }
social-query-no-players = 現在オンラインのプレイヤーはいません。
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (離席中)
   *[active] { $arg0 }
    }
social-query-online-summary = 現在 { $arg0 } 人がオンラインです。{ $arg1 ->
    [0] { "" }
   *[other] うち { $arg1 } 人が離席中です。
    }
social-query-players-label = プレイヤー
social-query-population = オンライン { $arg0 }/{ $arg1 } · アクティブ { $arg2 } · 離席中 { $arg3 }
social-query-unknown = 不明なコマンドです。使用可能なコマンドは { $arg0 } で確認できます。
social-query-uptime-label = 稼働時間
social-query-version-label = バージョン
social-reply-hidden = この返信はサーバーのコンテンツ安全ポリシーにより非表示になりました。
social-reply-unavailable = 現在、結果を表示できません。アカウントの変更はプロフィールコマンドで確認してから再実行してください。
social-state-disabled = 無効
social-state-failed = 失敗
social-state-ready = 準備完了
social-state-starting = 起動中
social-state-stopped = 停止
social-status-command-description = プレイヤー数、TPS、MSPT、稼働時間を表示
social-title-command-help = コマンドヘルプ
social-title-content-hidden = 内容を非表示
social-title-player-profile = プレイヤープロフィール
social-title-server-status = サーバー状態
social-unlink-command-description = 確認後にこの連携を解除
social-unlink-command-parameters = [{ social-unlink-confirmation } <確認コード>]
social-unlink-confirmation = 確認

## Community · AI and knowledge

ai-busy = AI のリクエストが混み合っています。しばらくしてから再試行してください
ai-cleared = AI の会話履歴を消去しました
ai-command-description = ゲーム内 AI アシスタントに質問する
ai-disabled = AI アシスタントは無効か、設定されていません
ai-knowledge-curation-failed = ナレッジの整理に失敗しました：{ $arg0 }
ai-knowledge-curation-finished = 整理完了：適用 { $arg0 } · スキップ { $arg1 } · 失敗 { $arg2 } · 隔離 { $arg3 } · 処理 { $arg4 }
ai-knowledge-curation-started = ナレッジの整理を開始しました。
ai-knowledge-disabled = ナレッジ機能は無効です。
ai-knowledge-index-dirty = インデックスの整合が必要です。同期完了まで個人メモリーの検索を停止します。
ai-knowledge-last-failure = 前回の失敗：{ $arg0 }
ai-knowledge-last-sync = 最終同期：{ $arg0 }
ai-knowledge-learning-disabled = ナレッジの学習は無効です。
ai-knowledge-operation-failed = ナレッジの操作に失敗しました：{ $arg0 }
ai-knowledge-operation-started = ナレッジの操作を開始しました。
ai-knowledge-protected = 文書{ $arg0 }を保護しました。
ai-knowledge-rollback = 文書{ $arg0 }をリビジョン{ $arg1 }として復元しました。
ai-knowledge-status = ナレッジ：{ $arg0 }件の公開文書 / { $arg1 }個のテキスト断片 · { $arg2 }件の個人文書 / { $arg3 }個のテキスト断片
ai-knowledge-sync-failed = ナレッジの同期に失敗しました：{ $arg0 }
ai-knowledge-sync-started = ナレッジの同期を開始しました。
ai-knowledge-synchronized = { ai-knowledge-status } · 同期が完了しました。
ai-knowledge-unprotected = 文書{ $arg0 }の保護を解除しました。
ai-learning-failure = 学習エラー：{ $arg0 }
ai-learning-status = 学習：候補 { $arg0 } · 抽出中 { $arg1 } · 待機 { $arg2 } · 抽出破棄 { $arg3 } · 候補破棄 { $arg4 } · 隔離 { $arg5 }
ai-memory-empty = { $arg0 }の個人メモリーはありません。
ai-memory-list = { $arg0 }の個人メモリー：{ $arg1 }
ai-memory-not-found = 個人メモリー{ $arg0 }が見つかりません。
ai-memory-removed = { $arg1 }の{ $arg0 }件の個人メモリーを完全に削除しました。
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = キャッシュにないプレイヤー：{ $arg0 }
ai-prompt-too-long = 質問が長すぎます（最大 { $arg0 } 文字）
ai-reload-failed = AI 設定を再読み込みできませんでした: { $arg0 }
ai-reloaded = AI 設定を再読み込みしました（{ $arg0 }）
ai-request-failed = AI リクエストに失敗しました。後でもう一度お試しください
ai-social-command-description = 共通 AI アシスタントに質問する
ai-social-command-parameters = <質問>
ai-social-question-required = AI コマンドの後に質問を入力してください
ai-status-disabled = AI は無効です
ai-status-enabled = AI 有効 · プロバイダー { $arg0 } · モデル { $arg1 } · ツール { $arg2 }
ai-status-none = なし
ai-thinking = 考えています…
ai-usage = 使用法: /ai <質問> · /ai clear · /ai status
ai-worker-status = AIタスク：実行中 { $arg0 } · 待機中 { $arg1 } · 破棄 { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>おめでとうございます！</bold> <gold>メンバー</gold>に昇格しました。schematicの簡易プリントなど、より多くのコマンドや機能を利用できるようになりました。</green>
governance-active-days-30 = 過去30日で有効な参加日が2日以上（現在 { $arg0 } 日）
governance-active-days-60 = 過去60日で有効な参加日が6日以上（現在 { $arg0 } 日）
governance-active-header = ─── 活動中のメンバー資格 ───
governance-active-member = 現在メンバーである
governance-active-member-age = メンバーになってから30日以上
governance-active-playtime = 過去60日の有効プレイ時間が{ $arg1 }以上（現在 { $arg0 }）
governance-activity-action = { $arg0 ->
    [confirm] 賛同を確定
    [vote] 内容を読んで投票
    *[details] 詳細を見る
    }
governance-activity-all = すべて表示（{ $arg0 }）
governance-activity-closed = 終了
governance-activity-confirm-petition = この請願に賛同しますか？必要人数に達すると解任投票が始まります。
governance-activity-needed = あと { $arg0 } 人必要
governance-activity-proposal = { $arg0 ->
    [appointment] { $arg1 } を管理者に任命
    [removal] { $arg1 } の管理者職を解任
    [petition] { $arg1 } の解任請願
    *[ban] { $arg1 } のBANに関する投票
    }
governance-activity-remaining = { $arg1 ->
    [days] 残り：{ governance-days }
    [hours] 残り：{ governance-hours }
    *[minutes] 残り：{ governance-minutes }
    }
governance-activity-status = { $arg0 ->
    [available] 投票可能
    [voted] 投票済み
    [join] 賛同可能
    [joined] 賛同済み
    [closed] 終了
    *[view] 閲覧のみ
    }
governance-already-moderator = すでに管理者です。
governance-application-status = { $arg0 ->
    [open] 任命投票が進行中です
    [pending] 承認済み・任命待ち
    [blocked] 申請条件を満たしていません
   *[ready] 申請できます
    }
governance-ballot-recorded = 匿名投票を「{ $arg0 }」として記録しました。締切前なら変更できます。
governance-ban-details = 提案者 { $arg0 } · 期間 { $arg1 } · 理由：{ $arg2 }
governance-ban-suffix = · 期間 { $arg0 } · 理由：{ $arg1 }
governance-choice-abstain = 棄権
governance-choice-no = 反対
governance-choice-yes = 賛成
governance-command-description = 運営の資格と投票を確認
governance-custodian-no-nomination = 保守担当者は管理者の任免に参加しません。
governance-custodian-no-resign = 保守担当者には管理者の辞任手続きは適用されません。
governance-days = { $arg0 }日
governance-error-ban-already-active = このプレイヤーには有効なBANがあります。
governance-error-ban-proposer-ineligible = BAN投票を提案できるのは活動中のメンバーだけです。
governance-error-ban-self = 自分へのBAN投票は提案できません。
governance-error-candidate-ineligible = 管理者候補は活動中のメンバーである必要があります。
governance-error-nomination-not-open = 進行中の管理者立候補はありません。
governance-error-not-voter = 今回の確定済み有権者名簿に入っていません。
governance-error-pause-already-active = このプレイヤーの昇格はすでに一時停止されています。
governance-error-pause-already-member = このプレイヤーはすでにメンバーです。昇格を停止する必要はありません。
governance-error-pause-end-invalid = 昇格停止の終了時刻は現在より後にしてください。
governance-error-player-unknown = このプレイヤーの運営記録はまだありません。
governance-error-removal-petition-closed = この請願は終了したか、新しい申請に置き換わりました。
governance-error-removal-requires-petition = 解任投票には必要人数の賛同が必要です。
governance-error-removal-self = 自分の解任には賛同できません。
governance-error-removal-sponsor-ineligible = 解任に賛同できるのは活動中のメンバーだけです。
governance-error-removal-sponsors-too-few = 対象者以外に活動中のメンバーが2人以上必要です。
governance-error-removal-target-not-moderator = このプレイヤーは解任対象の管理者ではなくなりました。
governance-error-removal-vote-open = この管理者の解任投票はすでに進行中です。
governance-error-termination-reason-invalid = この終了理由は投票の種類に適用できません。
governance-error-vote-already-open = このプレイヤーには同種の進行中の投票があります。
governance-error-vote-closed = 投票は締め切られたか終了しました。
governance-error-vote-cooldown = 定足数不足による不成立から3日間の再試行期間が終わっていません。
governance-error-vote-subject-unknown = 対象者の運営記録はまだありません。
governance-error-vote-termination-closed = 締切後のため投票を取り消せません。
governance-error-vote-unknown = 投票が見つかりません。
governance-error-voters-too-few = 投票開始には活動中のメンバーが2人以上必要です。
governance-failure-majority = 賛成票が全投票の半数を超えなかった
governance-failure-minimum-yes = 賛成票が最低票数に届かなかった
governance-failure-none = 理由の記録なし
governance-failure-quorum = 定足数に達しなかった
governance-failure-two-thirds = 賛成票が棄権を除く票の3分の2に届かなかった
governance-history-header = ─── 最近の投票結果 ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
governance-hours = { $arg0 }時間
governance-inactive-removal = { $arg0 } は180日間ログインしなかったため、管理者から自動的に退任しました。
governance-invalid-choice = yes、no、abstain から選んでください。
governance-invalid-duration = 期間が無効です。30m、12h、3d のように入力してください。
governance-kind-appointment = 任命
governance-kind-ban = BAN
governance-kind-removal = 解任
governance-legacy-ban-kick = 以前の運営投票が可決されました。{ $arg0 } のBANです。理由：{ $arg1 }
governance-menu-already-sponsored = 署名済み
governance-menu-application = 申請状況
governance-menu-completed = 終了済み
governance-menu-confirm = 確認
governance-menu-confirm-nominate = 申請すると、あなたのモデレーター任命投票が始まります。
governance-menu-confirm-resign = モデレーターを辞任しますか？
governance-menu-confirm-sponsor = この解任請願を支持しますか？
governance-menu-confirm-withdraw = モデレーターへの立候補を取り下げますか？
governance-menu-custodian-duties = サーバーの保守と管理。
governance-menu-identity = 身分
governance-menu-identity-hint = 昇格条件と活動記録。
governance-menu-join-petition = 解任請願を支持
governance-menu-loading = 運営状況を読み込み中…
governance-menu-matters = 運営の議題
governance-menu-moderator-duties = 違反への対応とプレイヤー支援。
governance-menu-nominate = モデレーターに立候補
governance-menu-offline = オフライン
governance-menu-ongoing = 進行中
governance-menu-online = オンライン
governance-menu-pause = 新人昇格を一時停止
governance-menu-pause-appeal = 異議申し立て先
governance-menu-pause-duration = 期間（例: 2d）
governance-menu-pause-reason = 理由
governance-menu-pause-until = 終了日時
governance-menu-pending-application = 可決・適用待ち
governance-menu-petition-row = { $arg0 } · 支持 { $arg1 }/{ $arg2 } 人
governance-menu-petitions = 解任請願
governance-menu-player-name = プレイヤー名
governance-menu-players = プレイヤー管理
governance-menu-refresh = 更新
governance-menu-related = 最近の関連記録
governance-menu-related-matters = 関連する議題を見る
governance-menu-resign = モデレーターを辞任
governance-menu-resume = 昇格停止を解除
governance-menu-resume-reason = 解除理由
governance-menu-staff-only = モデレーターと管理責任者のみ操作できます。
governance-menu-staff-search = 管理者を検索
governance-menu-subject-filter = { $arg0 } · 絞り込みを解除 ×
governance-menu-team = 公開管理者一覧
governance-menu-team-empty = 該当する管理者はいません
governance-menu-title = コミュニティ運営
governance-menu-withdraw = 立候補を取り下げる
governance-minutes = { $arg0 }分
governance-no-active-ban = 有効なBANがない
governance-no-active-pause = 有効な昇格停止がない
governance-nomination-withdrawn = 立候補を取り下げました。投票終了の結果は自動で告知されます。
governance-none = なし
governance-not-member = メンバーではありません
governance-not-moderator = 現在、管理者ではありません。
governance-operation-error = 運営操作に失敗しました。もう一度お試しください。
governance-own-choice-closed = あなたの選択：{ $arg0 }
governance-own-choice-open = あなたの選択：{ $arg0 }。締切前なら変更できます。
governance-own-not-voted = 有権者でしたが投票していません。
governance-own-not-voted-hint = まだ投票していません。「運営」を開いてこの投票を選んでください。
governance-own-not-voter = 今回の有権者名簿に入っていません。
governance-own-voted = 投票済み
governance-pause-cleared = { $arg0 } の昇格停止を解除しました。
governance-pause-details = 停止理由：{ $arg0 } · 期限：{ $arg1 } · 異議申立て：{ $arg2 }
governance-pause-fields-required = 停止理由と異議申立て先を入力してください。
governance-pause-not-active = { $arg0 } に有効な昇格停止はありません。
governance-pause-notice = 昇格が一時停止されました。理由：{ $arg0 } · 期限：{ $arg1 } · 異議申立て：{ $arg2 }
governance-pause-set = { $arg0 } の昇格を { $arg1 } まで停止しました。
governance-petition-already-sponsored = すでにこの解任に賛同しています（{ $arg0 }/{ $arg1 }）。
governance-petition-row = { $arg0 } · 賛同 { $arg1 }/{ $arg2 } 人 · 期限 { $arg3 }
governance-petition-started = { $arg0 } の解任賛同：{ $arg1 }/{ $arg2 } 人、期限 { $arg3 }。
governance-petitions-header = ─── 解任の賛同募集 ───
governance-player-first-join = 初参加：{ $arg0 }
governance-player-header = ─── { $arg0 } · 運営記録 ───
governance-player-id = UUID：{ $arg0 }
governance-player-last-login = 最終ログイン：{ $arg0 }
governance-player-member-since = メンバー開始：{ $arg0 }
governance-player-never-joined = プレイヤー { $arg0 } はまだこのサーバーに参加していません。
governance-promotion-account-age = 初参加から3日以上
governance-promotion-header = ─── 昇格条件 ───
governance-promotion-play-days = 有効プレイ日が2日以上（現在 { $arg0 } 日）
governance-promotion-playtime = 有効プレイ時間8時間以上（現在 { $arg0 }）
governance-resign-failed = 辞任に失敗しました。保守担当者に連絡してください。
governance-resigned = { $arg0 } が管理者を辞任しました。
governance-result-announcement = 投票 #{ $arg0 } { $arg1 }（{ $arg2 }）：{ $arg3 }{ $arg4 }
governance-result-failed = 不成立（{ $arg0 }）
governance-result-open = 進行中
governance-result-passed = 可決
governance-result-tally = { $arg0 } · 有権者 { $arg1 }、参加 { $arg2 }、賛成 { $arg3 }、反対 { $arg4 }、棄権 { $arg5 }
governance-result-terminated = 終了（{ $arg0 }）
governance-resume-notice = 昇格停止が解除されました。理由：{ $arg0 }
governance-resume-reason-required = 解除理由を入力してください。
governance-role-lookup-error = 管理者資格を確認できません。保守担当者に連絡してください。
governance-seconds = { $arg0 }秒
governance-storage-error = 運営データの操作に失敗しました。保守担当者に連絡してください。
governance-termination-inactive = 長期不在により管理者が退任
governance-termination-ineligible = 候補者が活動中のメンバー資格を喪失
governance-termination-resigned = 管理者が辞任
governance-termination-unknown = 理由の記録なし
governance-termination-withdrew = 候補者が立候補を撤回
governance-vote-electorate = 有権者 { $arg0 } 人 · 定足数 { $arg1 } 人
governance-vote-finalized = 確定：{ $arg0 }
governance-vote-header = ─── 投票 #{ $arg0 } ───
governance-vote-in-progress = 進行中 · 参加 { $arg0 } 人。内訳は終了後に公開されます。
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · 期限 { $arg3 } · 有権者 { $arg4 } · 参加 { $arg5 } · 定足数 { $arg6 }{ $arg7 }
governance-vote-period = 開始 { $arg0 } · 締切 { $arg1 }
governance-vote-started = { $arg1 }投票 #{ $arg0 } を開始：{ $arg2 }。有権者 { $arg3 } 人、定足数 { $arg4 } 人、締切 { $arg5 }。詳細は「運営」で確認できます。
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── 進行中の匿名投票 ───

## World · Plots

plot-admin-created = { $arg1 } の区画 { $arg0 } を登録しました · { $arg2 }
plot-allowed = 有効
plot-area-changed = 確認中に範囲が変更されました。更新して再確認してください。
plot-area-confirm = 「{ $arg0 }」の範囲を選択範囲（{ $arg1 } セル）に置き換えますか？建物は削除されません。
plot-area-hint = 木の斧の左右クリックか //pos1、//pos2 で角を指定し、戻って保存。
plot-area-replace-save = 範囲を置き換えて保存
plot-area-save = 範囲を保存
plot-area-saved = 「{ $arg0 }」の範囲を保存しました。
plot-atmosphere-saved = 環境設定を保存しました。
plot-command-blocked = 区画保護によりこのコマンドをブロックしました。自分の区画内で/fillや/setblockを直接使ってください。複雑な/executeは管理者に確認を依頼してください。
plot-command-description = 建築区画を管理
plot-created = 区画{ $arg0 }を作成しました · { $arg1 }
plot-deleted = 区画を解放しました。建物は残ります。
plot-denied = 無効
plot-error-admin-owner-only = 管理権限を持つメンバーの変更には、メンバー管理と権限管理の両方が必要です。
plot-error-area-disconnected = 土地はつながった一つの範囲である必要があります。
plot-error-args = 引数が無効か不足しています。/plot helpを使ってください。
plot-error-busy = 区画データを保存中です。この操作は送信されていません。後で再試行してください。
plot-error-flag = 不明な権限です。権限一覧から操作を選んでください。
plot-error-id = 区画IDが見つからないか、複数に一致します。/plot listを使ってください。
plot-error-member-missing = このプレイヤーは既にメンバーではありません。
plot-error-member-required = 区画の作成にはメンバー権限が必要です。
plot-error-name = プロジェクト名は1～48文字で入力してください。
plot-error-notice-text = 公告は最大 { $arg0 } 文字です。行数・1行の長さに制限はありません。制御文字は使用できません。
plot-error-overlap = 建築範囲が別の区画と重なっています。
plot-error-owner-member = 区画の所有者 · グループ権限
plot-error-owner-only = 無効 · 管理権限
plot-error-permission-changed = 権限、メンバーまたは継承規則が変更されました。再度プレビューしてください。
plot-error-permission-grant = 自分が持っていない管理権限は付与できません。
plot-error-permission-owner-scope = 所有者グループの権限は、土地所有者、親土地所有者またはサーバー管理者のみ変更できます。
plot-error-player-unknown = サーバーに参加したことのあるプレイヤーが見つかりません。名前または UUID を確認してください。
plot-error-release-owner-only = 無効 · 区画の削除
plot-error-selection = //pos1 と //pos2 で2つの角を指定してください。
plot-error-selection-changed = 選択範囲が変わりました。もう一度確認してください。
plot-error-staff-only = 他のプレイヤーの区画を管理・登録できるのはモデレーターまたは保守担当者だけです。
plot-error-storage = 区画の操作に失敗しました。時間をおいて再試行するか、管理者に連絡してください。
plot-error-subplot-children = 親区画を解放する前に子区画を解放してください。
plot-error-subplot-depth = 子区画は一階層のみ作成できます。
plot-error-subplot-disconnected = 選択した建築セルがつながっていません。部分ごとに分けてください。
plot-error-subplot-overlap = 選択範囲が別の子区画と重なっています。
plot-error-subplot-owner = この子区画操作の権限がありません。
plot-error-subplot-parent = 両端を親区画の登録済み建築セル内に指定してください。
plot-error-subplot-supervisor-member = 親区画の所有者はすでに監督権を持つため、メンバー登録は不要です。
plot-error-time-format = 時刻を HH:mm（00:00–23:59）で入力してください。
plot-error-transfer-owner-only = 無効 · 所有権の譲渡
plot-error-transfer-self = あなたはすでにこの区画の所有者です。
plot-flag-set = { $arg0 }：{ $arg1 }。
plot-help = 区画の操作ガイド
plot-here = 現在地：{ $arg0 } · { $arg1 }
plot-invited = { $arg0 }にアクセスを許可しました。
plot-item-summary = 建築範囲 { $arg0 } ブロック² · メンバー { $arg1 } 人
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 }ブロック²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = 未送信
plot-member-draft-reset = 分類の設定を消去
plot-member-draft-role = 役割：{ $arg0 }
plot-member-draft-submit = プレビューして確定
plot-member-draft-title = メンバー草案 · { $arg0 }
plot-member-permissions = 権限
plot-member-removed = プロジェクトのメンバーを削除しました。
plot-menu-access = メンバーと権限
plot-menu-add-member = メンバーを追加
plot-menu-atmosphere = 環境
plot-menu-clear = 晴れ
plot-menu-confirm-delete = { $arg0 } の登録を削除しますか？保護、メンバー権限、設定が削除され、元に戻せません。建物は残ります。
plot-menu-confirm-subplot-transfer = { $arg0 } を { $arg1 } に譲渡しますか？親区画の所有者は管理権を保ちます。
plot-menu-confirm-transfer = 「{ $arg0 }」の管理権を { $arg1 } に移しますか？あなたはアクセス権を失います。作品の既存の著作者表示は変わりません。
plot-menu-create = 区画を作成
plot-menu-create-for-player = このプレイヤーの区画を登録
plot-menu-custom-time = 時刻を指定
plot-menu-delete = 区画の登録を削除
plot-menu-flag-container = コンテナを開く
plot-menu-flag-entity = エンティティ操作
plot-menu-follow-parent = 親区画に合わせる
plot-menu-follow-world = ワールドに合わせる
plot-menu-group-collaborator = 共同作業者
plot-menu-group-member = メンバーの初期権限
plot-menu-group-newcomer = 新人の初期権限
plot-menu-here = 現在地
plot-menu-manage = メンバーの区画を管理
plot-menu-managed-empty = このプレイヤーの区画はまだありません。
plot-menu-managed-title = { $arg0 } の区画
plot-menu-members = メンバー（{ $arg0 } 人）
plot-menu-name = プロジェクト名
plot-menu-owner = 所有者：{ $arg0 }
plot-menu-parent-supervisor = 親区画の所有者: { $arg0 } · 監督権
plot-menu-player = プレイヤー名またはUUID
plot-menu-rain = 降水
plot-menu-remove-member = メンバーを削除
plot-menu-rename = プロジェクト名を変更
plot-menu-set-role = { $arg0 }に変更
plot-menu-settings = プロジェクト設定
plot-menu-submit = 適用
plot-menu-subplot-of = { $arg0 } の一部
plot-menu-subplot-parent = 親区画に戻る: { $arg0 }
plot-menu-subplots = 子区画 ({ $arg0 })
plot-menu-time = 時刻
plot-menu-time-input = 時刻（HH:mm）
plot-menu-transfer = 区画の所有権を移転
plot-menu-weather = 天候
plot-no-here = ここには保護された区画がありません。
plot-notice-board = 区画の掲示板
plot-notice-cleared = 掲示板を消去しました
plot-notice-edit = 掲示板を編集
plot-notice-input = 最大 { $arg0 } 文字、複数行可。空欄で削除
plot-notice-read = 全文を読む
plot-notice-saved = 掲示板を更新しました
plot-permission-all-actions = すべての操作
plot-permission-animal = 動物の世話・手なずけ・リード
plot-permission-break = ブロックの破壊
plot-permission-bucket = バケツで液体・生物を出し入れ
plot-permission-category-build = 建築と変更
plot-permission-category-entity = エンティティ操作
plot-permission-category-interaction = ブロック操作
plot-permission-category-management = 管理権限
plot-permission-category-storage = 収納とアイテム
plot-permission-change-children = 影響する子土地：{ $arg0 }
plot-permission-change-confirm = 変更を適用
plot-permission-change-granted = 付与：{ $arg0 }
plot-permission-change-members = 影響するメンバー：{ $arg0 }
plot-permission-change-more = ほか { $arg0 } 件
plot-permission-change-no-effect = 実効権限は変わりません。明示設定は今後の継承より優先されます。
plot-permission-change-none = なし
plot-permission-change-revoked = 取り消し：{ $arg0 }
plot-permission-change-subject = 対象：{ $arg0 }
plot-permission-change-title = 権限変更の確認
plot-permission-create-subplot = 子区画を作成
plot-permission-decoration = 装飾の設置と編集
plot-permission-default-status = 既定（{ $arg0 }）
plot-permission-delete = 区画の削除
plot-permission-door = ドアとゲート
plot-permission-entity-damage = エンティティへの攻撃
plot-permission-entity-storage = エンティティ収納を開く
plot-permission-harvest = ベリー・ツタ・蜂蜜の採取
plot-permission-ignite = 着火
plot-permission-item-drop = アイテムを捨てる
plot-permission-item-pickup = 地面のアイテムを拾う
plot-permission-manage-area = 範囲の変更
plot-permission-manage-members = メンバーの管理
plot-permission-manage-permissions = 権限の管理
plot-permission-manage-settings = 区画設定の変更
plot-permission-modify = ブロック変更と施肥
plot-permission-operation = 操作
plot-permission-personal = 個人の権限
plot-permission-personal-source = 個別設定
plot-permission-place = ブロックと乗り物の設置
plot-permission-reset = 既定に戻す
plot-permission-result = 状態
plot-permission-ride = 乗り物と動物に乗る
plot-permission-select-state = 状態を選択：{ $arg0 }
plot-permission-sign = 看板の編集
plot-permission-source-group = グループ権限
plot-permission-source-local = 個別設定
plot-permission-source-owner = 所有者の権限復旧保証
plot-permission-source-parent = 親区画
plot-permission-source-role = 役割の既定値
plot-permission-state-default = 既定
plot-permission-switch = ボタン・レバー・感圧板
plot-permission-trade = 村人との取引
plot-permission-transfer = 所有権の譲渡
plot-permission-workstation = 設備と作業台を使う
plot-pos1 = 始点
plot-pos2 = 終点
plot-private = 非公開
plot-public = 公開
plot-region-adjust = この矩形を調整
plot-region-apply = 矩形の調整を完了
plot-region-cancel = 矩形の調整をキャンセル
plot-region-changed = 選択範囲が変わりました。矩形を選び直してください。
plot-region-delete = この矩形を削除
plot-region-delete-confirm = 矩形 #{ $arg0 } を下書きから削除しますか？建物は残ります。区画を保存するまで境界は変わりません。元に戻すこともできます。
plot-region-edit-hint = //pos1、//pos2 でこの長方形を調整して保存。
plot-region-finish-first = 先に現在の矩形調整を完了するかキャンセルしてください。
plot-region-open = 矩形を管理（{ $arg0 }）
plot-region-select-hint = 番号付き矩形を選ぶと拡大して強調表示します。灰色の線はこのページの矩形です。結合後に番号が変わる場合があります。
plot-renamed = プロジェクト名を変更しました。
plot-role-admin = 区画管理者
plot-role-collaborator = 共同制作者
plot-role-owner = 区画の所有者
plot-role-parent-owner = 親区画の所有者
plot-role-server-staff = サーバー管理者
plot-selection-add = 現在の範囲を追加
plot-selection-clear = 2点を選び直す
plot-selection-empty = //pos1 と //pos2 で2つの角を指定してください。
plot-selection-fit = 保存済みの範囲に戻す
plot-selection-incomplete = 1点を記録済みです。もう1点を記録してください。送信前に区画は変更されません。
plot-selection-redo = 直前の操作をやり直す
plot-selection-replace = 現在の範囲で置換
plot-selection-session-hint = スニーク+F または /plot edit で戻る。20分間操作がないと終了。
plot-selection-size = 選択範囲：{ $arg0 } × { $arg1 } × { $arg2 } ブロック（X/Y/Z）
plot-selection-stopped = 範囲選択を終了しました。下書きは区画ごとに保持されます。
plot-selection-subtract = 現在の範囲を削除
plot-selection-undo = 直前の操作を戻す
plot-subplot-created = 子区画 { $arg0 } を作成 · { $arg1 } ブロック²
plot-title = 土地
plot-transferred = 区画「{ $arg0 }」を { $arg1 } に移転しました。
vehicle-command-description = 表示エンティティの車両を運転・管理
vehicle-menu-label =
    { $arg0 ->
        [vehicle] 乗り物の操作
        [driving] 運転と乗車
        [maintenance] 整備とアクセス
        [public-status] 公開アクセス
        [private-status] 非公開アクセス
        [home] 乗り物ワークスペース
        [nearby] 近くの乗り物
        [fleet] 利用可能な乗り物
        [selection] モデルの選択範囲
        [origin] 足元を原点にする
        [group] 表示グループを選択
        [radius] 選択半径（0–16 m）
        [displays] 近くの表示を探す
        [clear] 選択と原点を解除
        [capture] 選択からモデルを作成
        [toggle-selection] 選択を切り替え
        [public] 公開にする
        [private] 非公開にする
        [reload] ファイルから再読込
        [leave] 降りる
        [passenger] 助手席・客席
        [fuel] 給油 · 石炭か木炭を持つ
        [repair] 修理 · 鉄インゴットを持つ
        [access] アクセス
        [remove] 乗り物を削除
        [hud] 運転 HUD を表示
        [hide-preview] 3D プレビューを隠す
        [service-hint] 近くで停車してエンジンを切り、必要な材料を持ってください。
        [nearby-hint] 32 ブロック以内に近づき、今の座席から降りてください。
        [use-required] mik.vehicle.use 権限が必要です。
        [remove-hint] 停車して全員降りてください。
        [reload-hint] 全車両を停車・停止し、全員降りてください。下書きは保持されます。変更されたモデルは開き直してください。
        [pick-hint] 表示を狙って右クリックでグループ選択。スニーク右クリックで取消、30 秒で終了。
        [spawn-hint] ワールド座標。初期値は前方 4 ブロック。生成時の水平視線方向を向きます。
        [zoom-in] 拡大
        [zoom-out] 縮小
        [frame] 目の前に再配置
        [offset] 相対移動
       *[other] { vehicle-command-description }
    }

vehicle-controls = { $arg0 }：W/S・A/Dで操作、ホイールで変速またはスロットル調整、持ち手交換でモード変更、スニークで降車。/vehicle
vehicle-dash-assisted = 安定化アシスト
vehicle-dash-automatic = オートマチック
vehicle-dash-contact = 地面 / 水面の支持：{ $arg0 }
vehicle-dash-details = 詳細
vehicle-dash-down = シフトダウン −
vehicle-dash-drive = 運転
vehicle-dash-energy = 消費 { $arg0 } 単位/分 · 推定航続時間 { $arg1 } 分 / 距離 { $arg2 } km
vehicle-dash-engine-start = エンジン始動
vehicle-dash-engine-stop = エンジン停止
vehicle-dash-fuel = 燃料 { $arg0 } / { $arg1 } 単位 · { $arg2 }%
vehicle-dash-gear = ギア { $arg0 }
vehicle-dash-gearbox = 変速機
vehicle-dash-handbrake = 駐車 / ハンドブレーキ：{ $arg0 }
vehicle-dash-heading = 方位 { $arg0 }°
vehicle-dash-health = 状態 { $arg0 }% · 乗員 { $arg1 } / { $arg2 }
vehicle-dash-input = スロットル { $arg0 }% · ブレーキ { $arg1 }% · 操舵 { $arg2 }%
vehicle-dash-manual = マニュアル
vehicle-dash-mode-switch = { $arg0 }に切り替える
vehicle-dash-position = 原点 { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = パネルを中央に戻す
vehicle-dash-rpm = 回転数 { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = 速度 { $arg0 } km/h · 上限 { $arg1 }
vehicle-dash-throttle-down = スロットル −5%
vehicle-dash-throttle-up = スロットル +5%
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = アシストなし
vehicle-dash-unavailable = 運転操作を利用できません。運転席から開き直してください。
vehicle-dash-up = シフトアップ +
vehicle-dash-vertical = 高度 { $arg0 } m · 垂直速度 { $arg1 } m/s
vehicle-error = 車両の操作に失敗しました：{ $arg0 }
vehicle-group-changed = グループの構造が変わりました。読み込み前に選び直してください。
vehicle-group-invalid = 非対応・管理対象・不在・範囲外のエンティティが含まれています。全メンバーが同じワールドの32ブロック以内に必要です。
vehicle-group-limit = 読み込み上限は表示エンティティ128体、グループ全体256体です。
vehicle-group-not-found = 表示グループが見つかりません。グループを選ぶか、ブロック・アイテム表示に照準を合わせるか、エンティティのUUIDを指定してください。
vehicle-group-selected = 選択グループ：{ $arg0 }体の表示エンティティ · ルート { $arg1 }
vehicle-hud-boat = { $arg0 } kn · 推力 { $arg1 }% · 方位 { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · 高度 { $arg1 } m · 垂直速度 { $arg2 } m/s · スロットル { $arg3 }% · { $arg4 }
vehicle-hud-status = 燃料 { $arg0 }% · 状態 { $arg1 }% · { $arg2 }
vehicle-model-center = 重心
vehicle-model-choice = { $arg0 ->
    [CAR] 自動車
    [BOAT] 動力船
    [PLANE] 固定翼機
    [BODY] 車体
    [FRONT_WHEEL] 操舵輪
    [WHEEL] 車輪
    [PROPELLER] プロペラ
    [RUDDER] 方向舵
    [FIXED] 固定
    [VERTICAL] 垂直を維持
    [HORIZONTAL] 水平に追従
    [CENTER] 常に視点へ向く
    [NONE] 元の姿勢
    [THIRDPERSON_LEFTHAND] 三人称・左手
    [THIRDPERSON_RIGHTHAND] 三人称・右手
    [FIRSTPERSON_LEFTHAND] 一人称・左手
    [FIRSTPERSON_RIGHTHAND] 一人称・右手
    [HEAD] 頭
    [GUI] インベントリ
    [GROUND] 地面
    [LEFT] 左揃え
    [RIGHT] 右揃え
    [false] 無効
    [true] 有効
    [TEXT_CENTER] 中央揃え
   *[other] { $arg0 }
    }
vehicle-model-field = { $arg0 ->
    [kind] 乗り物の種類
    [mass] 質量
    [maximum-speed] 最高速度（km/h）
    [support-length] 接地判定の長さ（m）
    [grip] グリップ
    [center-of-mass] 重心
    [inertia] 各軸の慣性モーメント
    [engine-torque] 最大トルク
    [engine-idle-rpm] アイドリング（RPM）
    [engine-redline-rpm] 上限回転数（RPM）
    [engine-inertia] エンジン慣性
    [engine-wheel-radius] 車輪半径（m）
    [engine-final-drive] 最終減速比
    [engine-reverse-ratio] 後退ギア比
    [engine-ratios] 前進ギア比
    [engine-shift-seconds] 変速時間（秒）
    [fuel-capacity] 燃料容量
    [fuel-consumption] 燃料消費（単位/秒）
    [buoyancy] 浮力
    [wing-area] 翼面積（m²）
    [lift] 揚力係数
    [animation] アニメーションの役割
    [billboard] 視点への追従
    [item-transform] アイテムの姿勢
    [alignment] 文字の配置
    [block-light] ブロック光
    [sky-light] 天空光
    [background] 背景色（ARGB）
    [line-width] 文字の行幅
    [opacity] 不透明度（0–255）
    [shadow] 文字の影
    [see-through] 文字の透過表示
    [x] X
    [y] Y
    [z] Z
    [size-x] 全幅 X（m）
    [size-y] 全高 Y（m）
    [size-z] 全長 Z（m）
   *[other] { $arg0 }
    }
vehicle-model-field-range = 入力範囲：0 < 値 ≤ { $arg0 }
vehicle-model-front = 前方（+Z）
vehicle-model-front-choice = { $arg0 ->
    [negative-x] 現在の −X 側
    [back] 現在の後方（−Z）
    [positive-x] 現在の +X 側
    [look] 水平の視線方向を使う（90°単位）
   *[other] この側を前方にする
    }
vehicle-model-front-hint = 3D の矢印で前方を選びます。車体はその場に留まり、座席・出口・物理点の座標をまとめて変換します。90°単位で変更でき、元に戻せます。
vehicle-model-handle = { $arg0 ->
    [ROTATE] { $arg1 } { $arg2 } { $arg3 }°
   *[other] { $arg1 } { $arg2 } { $arg3 }
    }
vehicle-model-marker = { $arg0 ->
    [driver] 運転席 #{ $arg1 }
    [passenger] 助手席 #{ $arg1 }
    [exit] 出口 #{ $arg1 }
    [colliders] 衝突ボックス #{ $arg1 }
    [supports] 接地点 #{ $arg1 }
    [center] 重心
    [parts] 表示パーツ #{ $arg1 }
   *[other] { $arg1 }
    }
vehicle-model-parameter-group = { $arg0 ->
    [body] 車体と接地
    [engine] エンジンと変速機
    [fuel] 燃料
    [boat] 船の浮力
    [plane] 飛行機の揚力
    [power] エンジン出力
   *[other] { $arg0 }
    }
vehicle-model-parameter-help = { $arg0 ->
    [body] 質量・速度・接地を調整します。重心は 3D でも調整できます。保存までは下書きだけを変更します。
    [engine] 回転数・トルク・車輪・ギア比を調整します。前進ギア比は降順、上限回転数はアイドリングの1.5倍超が必要です。
    [fuel] 容量はゲーム内の燃料単位、消費量は毎秒の値です。
    [boat] 浮力は船に適用されます。他の種類の設定も保持します。
    [plane] 翼面積と揚力は飛行機に適用されます。他の種類の設定も保持します。
    [power] 現在の種類の回転数とトルクを調整します。自動車用の変速設定も保持します。
   *[other] { $arg0 }
    }
vehicle-model-property-help = { $arg0 ->
    [block-light] −1 はワールドの明るさです。上書きには両方を 0–15 にします。
    [sky-light] −1 はワールドの明るさです。上書きには両方を 0–15 にします。
    [background] 符号付き ARGB 整数。例：−16777216 は不透明な黒です。
    [line-width] 行幅は 1–2048 ピクセルです。
    [opacity] 0 は透明、255 は不透明です。
   *[other] { $arg0 }
    }
vehicle-model-spatial = 3D選択モードへ
vehicle-model-spatial-hint = マーカーを右クリックして選択・調整します。スニーク＋右クリックで設定に戻ります。スクロールで調整幅を切り替えます。
vehicle-model-spawn = このモデルで生成
vehicle-model-add = 部品を追加
vehicle-model-add-here = 足元に支持点を追加
vehicle-model-anchor = 目の前に再配置
vehicle-model-animation = アニメーション
vehicle-model-apply = 適用 / 確認
vehicle-model-auto = 当たり判定を再計算
vehicle-model-block = ブロック
vehicle-model-center-here = 重心を足元に設定
vehicle-model-center-look = 重心を見ている面に設定
vehicle-model-check = 構造とパラメーターを検証
vehicle-model-checked = 構造と設定は有効です。保存時には表示データ、既存の車両、ワールドとの衝突も検証します。
vehicle-model-clone = モデルを複製
vehicle-model-colliders = 当たり判定ボックス
vehicle-model-copy = 複製して編集（座席の複製は助手席になります）
vehicle-model-corner-cancel = 未確定の当たり判定を取り消す
vehicle-model-corner-first = 足元をボックスの最初の角にする
vehicle-model-corner-first-look = 見ている面を最初の角にする
vehicle-model-corner-hint = { vehicle-model-corner-first } → { vehicle-model-corner-second }
vehicle-model-corner-second = 足元を対角にしてボックスを追加
vehicle-model-corner-second-look = 見ている面を対角にする
vehicle-model-create = モデルを作成
vehicle-model-delete = モデルを削除
vehicle-model-delete-warning = このモデルを完全に削除しますか？使用中の車両があるモデルは削除できません。
vehicle-model-deleted = モデルを削除しました
vehicle-model-discard = 下書きを破棄
vehicle-model-discard-warning = 未保存の変更をすべて破棄しますか？この操作は元に戻せません。
vehicle-model-discarded = 下書きを破棄しました
vehicle-model-draft = 未保存の下書き
vehicle-model-driver = 運転席
vehicle-model-driver-here = 足元に運転席を追加
vehicle-model-edit = 下書きを編集 / 再開
vehicle-model-overview = 下書きの概要
vehicle-model-panel = 編集パネルに戻る
vehicle-model-editor-help = { $arg0 ->
   *[overview] 下から編集項目を選び、対象とツールを選択します。保存と取り消しは下部にあります。
    [parts] 表示するブロック、アイテム、テキストを管理します。部品を選んで内容や変形を編集します。
    [seats] 座席を選び、乗車位置と降車位置を設定します。運転席は必ず1つ必要です。
    [colliders] 衝突ボックスを選び、中心と全寸法を編集します。追加やモデル全体への自動調整もできます。
    [supports] 接地点を選んで移動します。地面の支持や船の浮力計算に使われます。
    [parameters] パラメーターのグループを選び、項目をクリックして値を編集します。
    [tools] プレビューの調整、ワールド内の対象選択、下書きの検証ができます。破棄には確認が必要です。
    }
vehicle-model-section-count = { $arg0 ->
   *[parts] { vehicle-model-parts } · { $arg1 }
    [seats] { vehicle-model-seats } · { $arg1 }
    [colliders] { vehicle-model-colliders } · { $arg1 }
    [supports] { vehicle-model-supports } · { $arg1 }
    }
vehicle-model-content-action = { $arg0 ->
   *[block] ブロック内容を設定
    [item] メインハンドのアイテムに置換
    [text] テキスト内容を設定
    }
vehicle-model-exact-input = 正確な値を入力
vehicle-model-axis-value = { $arg0 } · { $arg1 }
vehicle-model-component-details = 部品の詳細
vehicle-model-selection-unavailable = 対象が変更または削除されました。戻って選び直してください
vehicle-model-exit = 降車位置 / 微調整
vehicle-model-group = メインハンドのAxiom表示グループを追加
vehicle-model-hand-invalid = Axiom表示アイテムが無効か、未対応のエンティティや表示データが含まれています。
vehicle-model-hand-required = Axiom表示エンティティのアイテムをメインハンドに持って、再度読み込んでください。
vehicle-model-here = 現在の足元の位置を使う
vehicle-model-history-empty = この方向の編集履歴はありません。
vehicle-model-import = メインハンドのAxiom表示グループを読み込む
vehicle-model-item = アイテム
vehicle-model-library = 保存済みモデル · 下書きは保存するまでメモリー内のみ
vehicle-model-look = 照準で配置（メニューを閉じ、右クリックで確定）
vehicle-model-look-missing = 面が見つかりません。32ブロック以内のワールドのブロックか、自分のプレビューの部品を見てください。
vehicle-model-manual = ローカル座標を入力
vehicle-model-options = オプション
vehicle-model-parameters = 物理・エンジン設定
vehicle-model-parts = 表示パーツ
vehicle-model-passenger-here = 足元に助手席を追加
vehicle-model-pick = 見ているプレビューの部品を編集
vehicle-model-pick-hint = プレビューを開いてブロックまたはアイテムの部品を見るか、先にエディターで部品を選んでください。
vehicle-model-place-cancelled = 配置を取り消しました（時間切れ、編集状態の変化、または手動での取り消し）。
vehicle-model-place-hint = 照準による配置は30秒間有効です。ブロックかプレビューを見て、メインハンドの右クリックで確定、スニーク＋右クリックで取り消します。
vehicle-model-position = 位置 / 微調整
vehicle-model-position-hint = ローカル座標：+Yは上、+Zは前です。ボタンはステップ単位で移動し、正確な値の入力は絶対位置を設定します。
vehicle-model-preview = 自分専用プレビュー
vehicle-model-preview-failed = プレビューを表示できません。下書きは保持されています：{ $arg0 }
vehicle-model-preview-hint = プレビューを表示しました。モデルを直接選択・調整するには3D選択モードに入ってください。
vehicle-model-redo = やり直し
vehicle-model-remove = 部品を削除
vehicle-model-remove-warning = この部品を削除しますか？以降の番号がずれます。元に戻すことは可能です。
vehicle-model-rotate = 部品を回転
vehicle-model-rotate-hint = 相対的なローカル回転：Xはピッチ、Yはヨー、Zはロール。位置は変わりません。
vehicle-model-save = 保存して編集を終了
vehicle-model-save-continue = 保存して編集を続ける
vehicle-model-saved = 保存しました
vehicle-model-scale = 部品を拡大・縮小
vehicle-model-scale-all = 全軸の倍率 { $arg0 } { $arg1 }
vehicle-model-scale-hint = ローカル軸の長さを乗除します。ワールドでの外接寸法ではありません。
vehicle-model-seats = 座席と降車位置
vehicle-model-selected = 選択した表示エンティティを読み込む
vehicle-model-size = 当たり判定の寸法を変更
vehicle-model-size-hint = 幅・高さ・長さの全寸法。中心を動かさずに変更します。
vehicle-model-snap-off = グリッド吸着：オフ
vehicle-model-snap-on = グリッド吸着：オン
vehicle-model-step = 調整幅を切り替える
vehicle-model-supports = 接地支持点
vehicle-model-text = テキスト
vehicle-model-title = 車両モデル管理
vehicle-model-tools = プレビューとツール
vehicle-model-transform-step = 調整幅を変更
vehicle-model-undo = 取り消し
vehicle-result = 車両：{ $arg0 }
vehicle-selection-info = 選択：{ $arg0 }体の表示エンティティ · グループ { $arg1 } · 原点 { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = 衝突 / 障害物
vehicle-status-damage = 車両に重大な損傷
vehicle-status-disabled = 車両使用不能
vehicle-status-engine-off = エンジン停止中
vehicle-status-engine-on = エンジン稼働中
vehicle-status-gear-limit = 操作上限に達しました
vehicle-status-grounded = 座礁
vehicle-status-low-fuel = 燃料残量低下
vehicle-status-manual-only = 先にマニュアルモードに切り替えてください
vehicle-status-no-fuel = 燃料切れ
vehicle-status-overrev = シフトダウンすると過回転になります
vehicle-status-shifting = 変速中
vehicle-status-stall = エンスト
vehicle-status-stop-first = 前後進の切り替え・駐車の前に停止してください
vehicle-value-off = オフ
vehicle-value-on = オン

## World · Elevators, regeneration and spawn

async-regen-already-running = 再生成タスクはすでに実行中です。/regen status で確認、/regen cancel で中止できます
async-regen-apply-expired = 適用できる有効なプランがありません。先に /regen preview を実行してください
async-regen-apply-plan = 保存済みプランを適用
async-regen-apply-started = 保存された選択範囲に { $arg0 } を適用中：{ $arg1 }
async-regen-awaiting-apply = 適用待ちプラン：{ $arg0 } · { $arg1 } · あと { $arg2 } 秒で期限切れ
async-regen-block-preview-progress = ブロックプレビュー · { $arg0 }/{ $arg1 } チャンク確認済み · ブロック変更 { $arg2 } · 危険なブロックエンティティ { $arg3 }
async-regen-block-preview-summary = プレビュー：{ $arg2 } に一致するブロック変更 { $arg0 } 件、上書きされる可能性のある既存ブロックエンティティ { $arg1 } 件
async-regen-cancelled = 再生成を中止しました。すでに適用された { $arg0 } 件の変更はワールドに残ります
async-regen-command-description = FastAsyncWorldEdit の選択範囲を非同期で再生成する
async-regen-completed = 再生成が完了しました：{ $arg1 } で { $arg0 } ブロックを変更
async-regen-failed = 内部エラーにより再生成を停止しました。すでに適用された { $arg0 } 件の変更はワールドに残ります
async-regen-filter-all = すべてのブロック種類
async-regen-invalid-blocks = 不明、またはブロックではない種類：{ $arg0 }
async-regen-not-running = 実行中の再生成タスクはありません
async-regen-nothing-to-apply = 選択範囲はこのプランと一致しています。適用する変更はありません
async-regen-plan-dialog-title = 再生成プランを適用
async-regen-preview-cancelled = プレビューを中止しました。ワールドデータは変更されていません
async-regen-preview-started = プレビュー開始：選択 { $arg0 } ブロック、{ $arg1 } チャンク、モード { $arg2 }
async-regen-progress = { $arg0 } · 計画済み { $arg1 }/{ $arg2 } チャンク · { $arg3 } 件を適用
async-regen-safety-note = 保存された選択範囲の外は変更されません。範囲内の建築物は上書きされる可能性があり、バニラはプレイヤー設置ブロックを識別できません。適用前に可視化を確認し、バックアップしてください。
async-regen-scope-blocks = { $arg0 } に一致するブロックを再生成
async-regen-selection-required = 先に FastAsyncWorldEdit の選択範囲を完成させてください
async-regen-skipped-ungenerated = 未生成の対象チャンク { $arg0 } 個をスキップしました。新規生成も変更もしていません
async-regen-start-failed = 再生成を開始できませんでした。詳細はサーバーログを確認してください
async-regen-state-applying = 変更を適用中
async-regen-state-cancelled = 中止
async-regen-state-completed = 完了
async-regen-state-failed = 失敗
async-regen-state-generating = チャンクを生成・比較中
async-regen-state-preparing = 生成元ワールドを準備中
async-regen-status = { $arg0 } · 計画済み { $arg1 }/{ $arg2 } チャンク · 検出 { $arg4 } 件中 { $arg3 } 件を適用
async-regen-structure-metadata-applied = 構造物の開始点 { $arg0 } 件とチャンク参照 { $arg1 } 件を移行しました。/locate と構造物依存のスポーンから認識できます
async-regen-upgrade-apply-progress = { $arg0 } · 計画済み { $arg1 }/{ $arg2 } チャンク · ブロック { $arg3 }、バイオームセル { $arg4 } を適用
async-regen-upgrade-boundary-note = 選択外を変更しないため、境界に接する { $arg0 } 個のバイオームセルはスキップします
async-regen-upgrade-completed = ワールドアップグレード完了：{ $arg2 } でブロック { $arg0 }、バイオームセル { $arg1 } を変更
async-regen-upgrade-preview-progress = プレビュー · { $arg0 }/{ $arg1 } チャンク確認済み · ブロック変更 { $arg2 } · バイオームセル { $arg3 } · 構造物 { $arg4 }
async-regen-upgrade-preview-summary = プレビュー：ブロック変更 { $arg0 }、バイオームセル { $arg1 }、構造物 { $arg2 }（メタデータを完全移行できるもの { $arg3 }）、上書きされる可能性のある既存ブロックエンティティ { $arg4 }
async-regen-upgrade-scope-biomes = バイオームのみ
async-regen-upgrade-scope-terrain = 地形・構造物・バイオーム
async-regen-usage = 使用法：/regen preview blocks [種類...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = { $arg0 } の可視化：水色 = 選択範囲、オレンジ = 変更ブロックのサンプル、赤 = 上書きの危険があるブロックエンティティ。このパーティクルは自分にだけ見えます。
async-regen-visualization-selection = { $arg0 } の水色パーティクルが選択範囲の境界を示します。自分にだけ見えます。
async-regen-fawe-required = 選択範囲の取得には FastAsyncWorldEdit が必要です
building-map-layer = 建築アーカイブ
building-map-open = 建築を見る
building-map-builders = 建築者：{ $arg0 }
building-map-original-author = 原作者：{ $arg0 }
building-map-type-original = オリジナル
building-map-type-derivative = 派生作品
building-map-type-replica = 再現建築
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ スニーク
iron-elevator-tip-mm = { -iron-elevator-block }を上下にそろえて置くとエレベーターになります。ブロックの上で<green>ジャンプすると上へ</green>、<aqua>スニークすると下へ</aqua>。2階以上では<gold>階数パネル</gold>が表示されます。
iron-elevator-up = ↑ ジャンプ
spawn-beacon-color-command-description = スポーンビーコンの色の切り替えを設定
spawn-beacon-color-disabled = スポーンビーコンの色の切り替えを無効にしました
spawn-beacon-color-enabled = スポーンビーコンの色の切り替えを有効にしました
spawn-command-description = スポーンへ戻る
spawn-tip-return-mm = <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> で<yellow>サーバーの初期スポーン地点</yellow>に戻れます。
spawn-world-missing = スポーンのワールドを利用できません

## Entertainment · Music

music-album = アルバム: { $arg0 }
music-artist = アーティスト: { $arg0 }
music-command-description = ジュークボックス用のローカル、オンライン、NBS音楽
music-disc-left = 左クリック: レコードを入手
music-disc-right = 右クリック: 最寄りのジュークボックスで再生
music-duration = 再生時間: { $arg0 }
music-format = 形式: { $arg0 }
music-jukebox-audible-field-conflict = はっきり聞こえる範囲が重なる別のジュークボックスで異なる曲が再生されています
music-jukebox-tp-hover = クリックしてジュークボックスへテレポート
music-jukebox-unavailable = ジュークボックスが撤去されたか、そのチャンクを利用できません
music-now-playing-rich = 再生中: { $music }（ジュークボックス: { $location }）
music-online-rate-limited = 音楽のリクエストが多すぎます。{ $arg0 }秒後に再試行してください。
music-original-author = 原作者: { $arg0 }
music-plasmo-voice-unavailable = 曲は再生されましたが、Plasmo Voice がインストール、接続、または有効化されていないため聴くことができません
music-playback-failed = 「{ $arg0 }」を再生できませんでした。音源または音楽ファイルを確認して、もう一度お試しください。
music-reload-failed = 音楽の再読み込みに失敗しました: { $arg0 }
music-reload-started = 音楽ファイルとカスタム音源を再読み込みしています...
music-rhythm-animation-offset = アニメーションのずれ { $arg0 } ms
music-rhythm-calibration-accumulating = 安定したタイミングを確認中…そのまま続けてください
music-rhythm-calibration-adapting = リズムに合わせています…そのまま続けてください
music-rhythm-calibration-audio-instruction = リズムに合わせて、1～4のいずれかのキーを押してください。
music-rhythm-calibration-done = 遅延テスト完了・設定を保存しました
music-rhythm-calibration-failed = 遅延テストの音声が中断されました。テストを安全に終了したため、もう一度開始してください
music-rhythm-calibration-intro = { music-rhythm-calibration-stage-minecraft } → { music-rhythm-calibration-stage-plasmo } → { music-rhythm-calibration-stage-visual } → { music-rhythm-calibration-stage-pointer }
music-rhythm-calibration-listen = まずはリズムを一通り聴いてください。まだキーを押さないでください。
music-rhythm-calibration-no-input = 有効な入力がありません。この段階で案内されている操作を、合図に合わせて行ってください。
music-rhythm-calibration-out-of-range = 測定した遅延が対応範囲外です。音声設定を確認してください
music-rhythm-calibration-plasmo-ready = Minecraft Note Block テスト完了。まず同じリズムを Plasmo Voice で聴いてください
music-rhythm-calibration-pointer-instruction = ノートがヒットラインを通過したら左または右クリック
music-rhythm-calibration-realigning = 音声が中断しました。実演を再生し、完了済みの測定は保持します
music-rhythm-calibration-required = リズムゲームを開始する前に完全な遅延テストを完了してください
music-rhythm-calibration-reset-done = 遅延キャリブレーションをリセットしました
music-rhythm-calibration-result-hold = テストが完了しました。結果を確認してください。まもなく自動で戻ります。
music-rhythm-calibration-stage-minecraft = Minecraftの音符ブロック
music-rhythm-calibration-stage-plasmo = Plasmo Voice
music-rhythm-calibration-stage-pointer = 視覚・マウス
music-rhythm-calibration-stage-visual = 視覚・キーボード
music-rhythm-calibration-visual-instruction = ノートがヒットラインを通過したら1～4を押す
music-rhythm-calibration-visual-preview = 入力せずにキューを1周観察してください · 指示が変わってから開始します
music-rhythm-calibration-voice-required = 完全な遅延テストには Plasmo Voice の接続と有効化が必要です
music-rhythm-chart-preparing = 完全な譜面を準備中…
music-rhythm-combo = コンボ ×{ $arg0 }
music-rhythm-difficulty-easy = イージー
music-rhythm-difficulty-expert = エキスパート
music-rhythm-difficulty-hard = ハード
music-rhythm-difficulty-normal = ノーマル
music-rhythm-exit = リズムゲームを終了
music-rhythm-get-ready = 準備
music-rhythm-go = スタート！
music-rhythm-good = グッド
music-rhythm-great = グレート
music-rhythm-latency = 遅延 { $arg0 } ms
music-rhythm-latency-test = 完全な遅延テスト
music-rhythm-miss = ミス
music-rhythm-mode-falling = 落下式
music-rhythm-mode-radial = 全方向ラッシュ
music-rhythm-mode-spatial-aim = 空間エイム
music-rhythm-network-too-high = 公平な判定にはネットワーク遅延が高すぎます（{ $arg0 } ms、上限 { $arg1 } ms）
music-rhythm-network-warning = ネットワーク遅延が不安定です（{ $arg0 } ms）。{ $arg1 } ms を超え続けるとセッションを安全に終了します
music-rhythm-no-beats = この曲からプレイ可能なビートを検出できませんでした
music-rhythm-perfect = パーフェクト
music-rhythm-requires-playback = 先にこのジュークボックスで曲を再生してください
music-rhythm-results = リザルト
music-rhythm-score = スコア: { $arg0 }
music-rhythm-spatial-no-room = 空間エイムに必要な空きがありません。より開けた場所へ移動してください。
music-rhythm-spatial-slider-hint = 動く星を追い各チェックポイントでクリック · 長押し不要
music-rhythm-start = ゲームを開始
music-sample-rate = サンプルレート: { $arg0 }
music-size = サイズ: { $arg0 }
music-unavailable-disc = 利用できないレコード

## Entertainment · Anniversary

anniversary-accepted = 5周年抽選に参加しました。AFK以外で4時間オンラインになると第1ラウンドを抽選できます。
anniversary-admin-audit-action-bonus = 建築採用ボーナス機会を付与
anniversary-admin-audit-action-gift = イベント賞品を贈呈：{ $arg0 }
anniversary-admin-audit-action-removal = 管理者の過去参加記録を削除し、賞品 { $arg0 } 件を復元
anniversary-admin-audit-action-unknown = 不明な操作：{ $arg0 }
anniversary-admin-audit-empty = 管理監査記録はまだありません。
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = 実行者：{ $arg0 } ({ $arg1 })；対象：{ $arg2 } ({ $arg3 })
anniversary-admin-audit-title = 周年管理監査（{ $arg0 }/{ $arg1 } 件を表示）
anniversary-admin-bonus-cancelled = ギフトパックにより終了
anniversary-admin-bonus-pending = 付与済み、未抽選
anniversary-admin-bonus-used = 抽選済み
anniversary-admin-boss = 周年イベント管理 · { $arg0 }で進捗を確認
anniversary-admin-commands = コマンド：/anniversary winners [ページ] | /anniversary audit [プレイヤー] | /anniversary bonus <プレイヤー>
anniversary-admin-control = 動的制御：解放済み { $arg0 }；現在利用可 { $arg1 }；基本確率 { $arg2 }；直近速度 { $arg3 }
anniversary-admin-draw-bonus = 建築採用ボーナス
anniversary-admin-draw-detail = { $arg0 }：抽選日時 { $arg1 }；結果 { $arg2 }
anniversary-admin-draw-metrics = 確率 { $arg0 }；乱数 { $arg1 }；アルゴリズム版 { $arg2 }
anniversary-admin-draw-replaced = ギフトパックにより置換、在庫へ返却済み
anniversary-admin-draw-round = 通常第 { $arg0 } 回
anniversary-admin-event = イベント状態：{ $arg0 }；期間：{ $arg1 }
anniversary-admin-excluded = 周年イベント管理者は抽選に参加できません。管理概要を開きました。
anniversary-admin-none = なし
anniversary-admin-opportunities = 実行済み抽選：{ $arg0 }；現在抽選可能：{ $arg1 }；獲得済み機会：{ $arg2 }；当選/落選：{ $arg3 }
anniversary-admin-participants = 参加者：{ $arg0 }
anniversary-admin-player-bag = 最終イベントバッグ：{ $arg0 }
anniversary-admin-player-bonus = 建築ボーナス：{ $arg0 }；付与日時：{ $arg1 }；付与者：{ $arg2 } ({ $arg3 })
anniversary-admin-player-history = 抽選機会の詳細
anniversary-admin-player-not-found = イベント参加者が見つかりません：{ $arg0 }
anniversary-admin-player-outcome = 当選：{ $arg0 }；落選：{ $arg1 }；実行済み抽選：{ $arg2 }；建築ボーナス：{ $arg3 }
anniversary-admin-player-progress = 参加日時：{ $arg0 }；有効オンライン時間：{ $arg1 }；実行済み通常抽選：{ $arg2 }；現在抽選可能な通常機会：{ $arg3 }
anniversary-admin-player-title = 参加者監査：{ $arg0 } ({ $arg1 })
anniversary-admin-state-active = 開催中
anniversary-admin-state-ended = 終了
anniversary-admin-state-scheduled = 開始前
anniversary-admin-stock = 残り在庫：{ $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | 合計 { $arg4 }
anniversary-admin-target-excluded = { $arg0 } は周年イベント管理者のため、抽選機会を受け取れません。
anniversary-admin-title = 五周年イベント管理概要
anniversary-admin-winners-empty = 実際に抽選された当選記録はまだありません。
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · 抽選日時 { $arg3 }
anniversary-admin-winners-title = 賞品発送リスト（{ $arg0 }/{ $arg1 } ページ、全 { $arg2 } 件）
anniversary-bag-empty = 当選賞品はまだありません
anniversary-bag-item-locked = イベントバッグは閲覧専用です
anniversary-bag-item-name = 5周年賞品 · { $arg0 }
anniversary-bag-not-participating = まだイベントに参加していません。先に{ $arg0 }を使用してください。
anniversary-bag-title = 5周年イベントバッグ
anniversary-bag-updated = 賞品をイベントバッグに保存しました。{ $arg0 }で確認できます。
anniversary-bonus-already-granted = { $arg0 }には建築採用ボーナスがすでに付与されており、再付与できません。
anniversary-bonus-boss-ready = 建築採用ボーナス抽選可能 · { $arg0 }で抽選
anniversary-bonus-broadcast-won = [5周年抽選] { $arg0 }さんが建築採用ボーナスで{ $arg1 }を獲得しました！
anniversary-bonus-draw-not-won = 建築採用ボーナス抽選：今回は当選なしです。
anniversary-bonus-draw-sold-out = 建築採用ボーナス抽選：賞品がすべてなくなったため当選なしです。
anniversary-bonus-draw-won = 建築採用ボーナス抽選：おめでとうございます！{ $arg0 }が当たりました！
anniversary-bonus-gift-pack-final = { $arg0 }はすでにギフトパックに当選しているため、建築ボーナス抽選を付与できません。
anniversary-bonus-grant-not-active = イベント期間外は建築採用ボーナスを付与できません。
anniversary-bonus-grant-success = { $arg0 }に建築採用ボーナスを付与しました。
anniversary-bonus-granted = 建築採用ボーナスとして追加抽選1回を獲得しました。/anniversary で抽選できます。
anniversary-bonus-participant-not-found = イベント参加者「{ $arg0 }」が見つかりません
anniversary-bonus-status-ready = 建築採用ボーナス：追加抽選1回が利用可能です。
anniversary-bonus-status-used = 建築採用ボーナス：追加抽選は使用済みです。
anniversary-boss-completed = 5周年抽選 · 2ラウンド完了
anniversary-boss-gift-pack-final = 5周年ギフトパック獲得 · { $arg0 }でイベントバッグを表示
anniversary-boss-invite = サーバー5周年記念グッズ抽選 · { $arg0 }で参加
anniversary-boss-progress = 5周年抽選 { $arg0 }/{ $arg1 } · 第{ $arg2 }ラウンドまで { $arg3 }
anniversary-boss-ready = 第{ $arg0 }ラウンド抽選可能 · { $arg1 }で抽選
anniversary-broadcast-won = [5周年抽選] { $arg0 }さんが第{ $arg1 }ラウンドで{ $arg2 }を獲得しました！
anniversary-cancel = 今は参加しない
anniversary-command-description = サーバー5周年記念グッズ抽選に参加する
anniversary-confirm = 同意して参加
anniversary-confirm-hint = 確定後、AFK以外のオンライン時間を計測します
anniversary-delivery-note = 中国本土宛てはプライバシー保護配送に使える有効な順豊 ID を、中国本土以外宛ては受取人の実名、完全な住所、有効な電話番号を提出してください。指定送料を支払わない場合は辞退扱いです。
anniversary-dialog-prizes = 賞品：{ $arg0 } 20個、{ $arg1 } 10個、{ $arg2 } 20個、{ $arg3 } 5個
anniversary-dialog-time = 開催期間：{ $arg0 }
anniversary-dialog-title = 5周年記念グッズ抽選のルール
anniversary-draw-not-won = 第{ $arg0 }ラウンド：今回は当選なしです。
anniversary-draw-sold-out = 第{ $arg0 }ラウンド：賞品がすべてなくなったため当選なしです。
anniversary-draw-won = 第{ $arg0 }ラウンド：おめでとうございます！{ $arg1 }が当たりました！
anniversary-ended = 5周年記念抽選は終了しました
anniversary-ended-results = あなたの抽選結果：{ $arg0 }
anniversary-gift-pack-final = ギフトパックが最終賞品になりました。他の賞品{ $arg0 }個を置き換えて在庫へ戻し、残りの抽選はすべて終了しました。
anniversary-gift-prize-not-owned = このイベント賞品を所持していません：{ $arg0 }
anniversary-gift-received = { $arg0 }から{ $arg1 }が贈られました。イベントバッグに保存されています。
anniversary-gift-self = 自分自身に賞品を贈ることはできません。
anniversary-gift-success = { $arg0 }を{ $arg1 }に贈りました。
anniversary-gift-usage = 使用方法：{ $arg0 }
anniversary-not-active = 現在は5周年イベントの開催期間外です
anniversary-not-started = 5周年イベントはまだ始まっていません。開催期間：{ $arg0 }
anniversary-prize-badge = 5周年集合写真缶バッジ
anniversary-prize-gift-pack = 5周年ギフトパック
anniversary-prize-keychain = ロゴキーホルダー
anniversary-prize-mug = ロゴマグカップ
anniversary-prize-not-won = 当選なし
anniversary-prize-sold-out = 賞品配布終了
anniversary-rule-1 = 1. 参加後は4時間ごとに合計2回抽選できます。当選品はイベント専用の仮想バッグに入り、同じ種類は1個までです。ギフトパック当選後はそれだけを残し、以降の抽選は終了します。
anniversary-rule-2 = 2. AFK中の時間は抽選時間に加算されません。いかなる方法でも AFK 判定を回避することは禁止です。
anniversary-rule-3 = 3. 実物記念品の送料は、サーバーが総額の80%（上限50元）を負担し、その負担額を差し引いた残額は当選者が全額負担します。
anniversary-rule-4 = 4. 中国本土宛ては、プライバシー保護配送に使える有効な順豊 ID を提出してください。中国本土以外宛ては、受取人の実名、完全な住所、有効な電話番号を提出してください。必要情報の未提出または送料未払いは辞退扱いです。
anniversary-rule-5 = 5. 複数アカウントなどによる重複参加は禁止です。違反者は参加資格を失います。
anniversary-rule-6 = 6. 参加した時点で上記すべてのルールに同意したものとみなします。
anniversary-rule-7 = 7. 公式サイトで採用された建築は、転載 0.2、二次創作 0.5、オリジナル 1 点。イベント前の採用は 0.8 倍、共同建築は個人の貢献割合の 1.5 倍で計算します。合計 2 点以上で追加抽選 1 回を管理者が手動付与します。
anniversary-second-round-hint = AFK以外のオンライン時間が合計8時間になると第2ラウンドを抽選できます。
anniversary-status-completed = 2ラウンドすべて完了しました。
anniversary-status-gift-pack-final = ギフトパックに当選しました。最終賞品が確定し、残りの抽選はすべて終了しました。
anniversary-status-progress = 完了：{ $arg0 }/{ $arg1 }ラウンド、有効オンライン時間：{ $arg2 }
anniversary-status-remaining = 第{ $arg0 }ラウンドまで、AFK以外であと{ $arg1 }オンラインが必要です。
anniversary-status-results = 抽選結果：{ $arg0 }
anniversary-status-title = 5周年抽選の進行状況

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } は既にBANされています
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [BAN公報]
ban-command-description = 固定の重大度でプレイヤーをBAN
ban-database-error = BANデータベースの操作に失敗しました。サーバーログを確認してください。
ban-menu-confirm-action = プレイヤーをBAN
ban-menu-confirm-severity = 重大度
ban-menu-confirm-title = BANの確認
ban-menu-never-joined-confirm-action = それでもBANする
ban-menu-never-joined-title = このプレイヤーは参加したことがありません
ban-menu-never-joined-warning = { $arg0 } はこのサーバーに参加したことがありません。別のプレイヤーを誤ってBANしないよう、名前を確認してください。
ban-menu-player-input = プレイヤー名を手動入力
ban-menu-select-title = プレイヤーを選択
ban-menu-severity-title = BANの重大度を選択
ban-history-empty = { $arg0 }のBAN履歴はありません。
ban-history-title = BAN履歴：{ $arg0 }
ban-invalid-player = プレイヤー名が無効です
ban-invalid-severity = BANの重大度が無効です
ban-kick-appeal = 誤りだと思われる場合はQQグループ1138627074で異議を申し立ててください
ban-kick-server = MIK
ban-kick-title = このサーバーからBANされています
ban-not-banned = { $arg0 }は現在BANされていません。
ban-pardon-reason-required = BAN解除理由が必要です
ban-permanent = 永久
ban-reason-required = BAN理由が必要です
ban-self = 自分自身をBANすることはできません
ban-severity-excusable = 情状酌量（30日）
ban-severity-extreme = 極めて重大（1500日）
ban-severity-minor = 軽微な違反（150日）
ban-severity-severe = 重大な違反（360日）
ban-severity-warning = 警告（3日）
ban-status-active = 有効
ban-status-expired = 期限切れ
ban-status-revoked = 解除済み
ban-success = { $arg0 } を { $arg1 } までBANしました
ban-sync-success = BANを同期しました：有効 { $arg0 } · 履歴 { $arg1 }
ban-system-source = システム
ban-unban-success = { $arg0 }のBANを解除しました
ban-unknown-reason = 理由なし
ban-update-success = { $arg0 }のBAN項目を更新しました：{ $arg1 }
banlist-command-description = BAN一覧を表示
banlist-copy = クリックしてプレイヤー名をコピー
banlist-empty = BANされているプレイヤーはいません
banlist-expires = 解除日時
banlist-invalid-page = ページが範囲外です。全{ $arg0 }ページ。
banlist-next = 次へ
banlist-page = { $arg0 }/{ $arg1 }ページ · { $arg2 }件
banlist-player = プレイヤー
banlist-previous = 前へ
banlist-reason = 理由
banlist-source = { role-moderator }
banlist-title = BAN一覧
maintenance-command-description = メンテナンスモードの切り替えまたは状態確認
maintenance-kick = サーバーはメンテナンス中です
maintenance-set = メンテナンスモードを{ $arg0 }にしました
maintenance-state-disabled = 無効
maintenance-state-enabled = 有効
maintenance-status = メンテナンスモード: { $arg0 }
maintenance-unchanged = メンテナンスモードはすでに{ $arg0 }です
monitor-command-description = ランダム巡回または指定プレイヤーを監視。stop で終了
monitor-return-failed = 帰還に失敗しました。監視は継続中です。/monitor stop で再試行してください。
monitor-returning = 元の場所に戻っています…
monitor-started = 監視を開始しました。/monitor で終了します。
monitor-stopped = 監視を終了し、元の場所に戻りました。
monitor-unavailable = 監視を開始できません。乗り物から降り、スペクテイターモードに切り替えられるか確認してください。
monitor-waiting = 監視：対象のプレイヤーを待っています…
monitor-watching = 監視：{ $arg0 }
performance-kick-title = サーバーに深刻な遅延が発生しています
performance-kick-reason = サーバーの復旧のため、一時的に接続を切断しました
restriction-entity-too-large-mm = <red>エンティティの大きさがサーバーの安全上限を超えています</red>
restriction-foreign-uuid-mm = <red>UUIDは自分が手懐けたMobにのみ使用できます</red>
restriction-no-kill-e-mm = <red><white>/kill @e</white>コマンドは使用できません</red>
restriction-no-newline-mm = <red>メッセージに改行は使用できません</red>
restriction-no-run-command-mm = <red>プレイヤーにコマンドを実行させるようなメッセージは使用できません</red>
restriction-no-selector-mm = <red>ターゲットセレクター<white>@e @a @r @p @n</white>を使用する権限がありません</red>
restriction-other-player-name-mm = <red>コマンドに他のプレイヤー名を指定する権限がありません</red>
tpsbar-command-description = TPSバーの表示を切り替え
tpsbar-hidden = TPSバーを非表示にしました
tpsbar-shown = TPSバーを表示しました
tpsbar-tip-status-mm = <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> で<yellow>TPS・MSPT・Ping</yellow>バーの表示を切り替えられます。
unban-command-description = BANを解除して履歴を保持

## Shell · Menus, server information and scripting

main-afk-status =
    { $arg0 ->
        [afk] AFK：離席中
       *[online] AFK：オンライン
    }
main-pvp-status =
    { $arg0 ->
        [on] PVP：オン
       *[off] PVP：オフ
    }
main-announcements = お知らせ
main-home = ホーム
main-language = 言語
main-language-auto = 自動
main-links = リンク
main-map = ウェブマップ
main-menu-title = メインメニュー
main-music = 音楽
main-name-tag = ネームタグ
main-settings = 設定
main-website = ウェブサイト
main-wiki = Wiki
skript-command-description = サーバーのスクリプト対応状況を表示
skript-info-ai-option = 自分で書かない場合は、このプロンプトを AI に渡してください:
skript-info-copy = [AI プロンプトをコピー]
skript-info-copy-hover = バージョン、ドキュメントリンク、安全要件を含むプロンプトをコピー
skript-info-documentation = 構文と API のドキュメント
skript-info-review = スクリプトは管理者による確認とテストの後にのみ読み込まれます
skript-info-supported = 利用可能: { $arg0 }
skript-info-title = サーバースクリプト
skript-info-workflow = ドキュメントを参照して完全な .sk ファイルを作成・確認し、管理者へ送ってください
stat-client-version = クライアントのバージョン
stat-first-joined = 初回参加日
stat-location = 座標
stat-ping = 応答時間
stat-ping-value = { $arg0 } ミリ秒
stat-play-time = プレイ時間
stat-role = ロール
stat-world = ワールド
web-account-title = ウェブアカウントとパスキー
web-account-confirm = 確認する
weblogin-question = 今回のウェブ本人確認を承認しますか？mcmik.top で自分が開始した操作だけを承認してください。他人のためにコードを入力しないでください。
weblogin-command-description = Mik のウェブ本人確認コードを承認
weblogin-confirmed = ウェブ本人確認が完了しました。ブラウザーに戻って続けてください。
weblogin-invalid-code = 無効なウェブ本人確認コードです


# Music workspace
music-space-label =
    { $arg0 ->
        [title] 音楽空間
        [devices] 再生デバイス
        [all-devices] すべてのデバイス
        [use-device] このデバイスを使う
        [current-device] 現在のデバイス
        [manage-device] 管理
        [back-devices] デバイス一覧へ
        [group-with-devices] 他のデバイスとグループ化
        [pick-world-member] ワールドで1台選ぶ
        [group-world-pick-hint] 空の手でスニークしながらジュークボックスを右クリックして追加。/music でメニューに戻れます。
        [group-member-standalone] まだ同期グループに入っていないジュークボックスを選んでください。
        [group-selection-help] 下のジュークボックスを選ぶか、ワールドで空の手でスニークしながら別の機器を右クリックします。確定後にグループ化されます。
        [group-source-help] 選んだデバイスのキューと再生位置を引き継ぎます。作成すると元の再生は停止し、「作成して再生」で再開します。
        [device-actions] 操作
        [device-status] 状態
        [sync-group] 同期グループ
        [single-device] ジュークボックス
        [device-location] 場所
        [visit-device] そばにテレポート
        [teleporting] テレポート中…
        [recorded-device] 登録済み
        [nearby-device] 近く
        [group-select-members] ジュークボックスを2台以上選択
        [group-name-required] グループ名を入力
        [direction-front] 前方
        [direction-front-right] 右前方
        [direction-right] 右
        [direction-back-right] 右後方
        [direction-back] 後方
        [direction-back-left] 左後方
        [direction-left] 左
        [direction-front-left] 左前方
        [direction-here] すぐそば
        [direction-above] 上方
        [direction-below] 下方
        [no-devices] デバイスがありません
        [choose-members] メンバーのデバイスを選択
        [create-group] 同期グループを作成
        [add-members] デバイスを追加
        [create-play] 作成して再生
        [create-and-append] 作成して曲を末尾に追加
        [device-queue] キュー
        [device-members] メンバー
        [available] 利用可能
        [play] 再生
        [retry-outputs] 出力を再試行
        [dissolve-group] グループを解散
        [remove-member] グループから外す
        [removed-stopped] 外したデバイスは再生を停止し、選んだ内容を保持します。
        [restore-previous] 参加前の内容を復元
        [keep-group-content] グループの内容を保持
        [queue] 再生キュー
        [back] { back }
        [close] { close }
        [unavailable] 音楽ストレージを利用できません
        [saving] 保存中…
        [saved] 保存しました
        [apply] { plot-menu-submit }
        [cancel] { cancel }
        [select-page] このページをすべて選択
        [select-all] 検索結果をすべて選択
        [clear-tracks] 曲の選択を解除
        [add-playlist] リストに追加
        [description] 説明
        [make-private] 非公開にする
        [publish] 公開する
        [delete] プレイリストを削除
        [favorite] お気に入りを切り替え
        [copy] プレイリストをコピー
        [share] プレイリストの共有コマンド
        [recent] 最近の再生
        [favorites] お気に入りのプレイリスト
        [public] 公開
        [private] 非公開
        [new-playlist] プレイリストを作成
        [name] 名前
        [song-title] 曲名
        [track-count] 曲数
        [visibility] 公開範囲
        [no-playlists] プレイリストがありません
        [fetching-playlist] プレイリストを読み込み中…
        [artist] アーティスト
        [duration] 再生時間
        [track-type] 種類
        [type-online] オンライン
        [undo] { vehicle-model-undo }
        [remove] 選択した項目を削除
        [clear-recents] 自分の再生履歴を消去
        [clear-entries] 項目の選択を解除
        [search-tracks] 曲・アーティストを検索…
        [search-playlists] プレイリストを検索…
        [public-playlists] 公開プレイリスト
        [searching] 検索中…
        [insert] 割り込んですぐ再生
        [append] キューに追加
        [choose-targets] 出力先を選択
        [group-source] キューと再生位置の引き継ぎ元
        [removed-targets] 削除された再生先があります。選び直してください
        [offline] オフライン
        [device-discovery-hint] 素手でスニークしながらジュークボックスを右クリックすると開けます。
        [empty] 曲がありません
        [queue-empty] 再生待ちの曲はありません
        [queue-current] デジタルキューの現在の曲
        [pause] 一時停止
        [resume] 再開
        [stop] 停止して再生リストを保持
        [previous] 前の曲 / 最初から再生
        [clear] 再生待ちを消去し、現在の曲の後に停止
        [seek] 再生位置
        [more] その他
        [volume-control] 音量
        [group-volume] グループ音量
        [timeline-help] ホイールで ±5 秒・右クリックで時間入力
        [timeline-read-only] 再生位置を変更できません
        [seek-format] m:ss、h:mm:ss または秒数を入力
        [record] 遠隔操作用に登録
        [forget] 自分の登録を解除
        [alias] 自分用のデバイス名
        [sound] サウンド設定
        [rhythm] 音楽ゲームルーム
        [room-players] プレイヤー
        [room-player-status] 状態
        [room-preparation] 自分の準備
        [room-ready] 準備する
        [room-cancel-ready] 準備を取り消す
        [room-playing] プレイ中
        [room-score-ready] 譜面の準備完了
        [room-select-one] 曲庫の複数選択を有効にして、曲を1つ選択してください。
        [room-end-confirmation] 参加者全員の音楽ゲームルームを終了します。
        [create-room] ルームを作成
        [no-rhythm-devices] 近くにジュークボックスがありません。まず空の手でスニークしながらジュークボックスを右クリックしてください。
        [calibration] 遅延キャリブレーション
        [volume] 音量（0～100）
        [range] 範囲（8～256ブロック）
        [mode] 再生モード
        [save-queue] キューをプレイリストに保存
        [sequential] 順番に再生
        [repeat-all] リストをリピート
        [repeat-one] 1曲リピート
        [shuffle] 重複なしのシャッフル
        [import] プレイリストをインポート
        [link] プレイリストのリンク
        [partial-import] プレイリストの一部のみ取得できました。取得済みの部分を残すには保存してください。
        [import-preview] 取得したメタデータのプレビューです。音声はダウンロードしていません。
        [import-new] 作成して開く
        [import-merge] リストに統合
        [stopped] 停止中
        [loading] 音声を準備中
        [cancel-loading] 準備をキャンセル
        [playing] 再生中
        [paused] 一時停止中
        [finished] 再生終了
        [retry-failed] 再生を再試行
        [select-song] 選択した曲をまとめて再生、またはプレイリストに追加。
        [select-toggle] この項目を選択
        [deselect] この項目の選択を解除
        [selected] 選択済み
        [place-before] 選択した項目をこの項目の前に移動
        [eject] 挿入したレコードを取り出す
        [make-disc] 署名付き音楽ディスクを作成
        [gain] グループ音量（0～100）
        [retry-import] プレイリストを再取得
        [join-room] ルームに参加
        [ready] 準備完了
        [waiting] 待機中
        [room-mode] ゲームモード
        [room-difficulty] 難易度
        [late-join] 進行中のゲームに参加
        [leave-room] ルームから退出
        [select-room-track] 選択した曲を使用
        [start-room] ゲーム開始
        [end-room] ルームを終了
        [base-queue] 基本の再生リスト
        [priority-queue] 1回限りのリクエスト
        [falling] { music-rhythm-mode-falling }
        [radial] { music-rhythm-mode-radial }
        [spatial-aim] { music-rhythm-mode-spatial-aim }
        [easy] { music-rhythm-difficulty-easy }
        [normal] { music-rhythm-difficulty-normal }
        [hard] { music-rhythm-difficulty-hard }
        [expert] { music-rhythm-difficulty-expert }
        [waiting-for-player] プレイヤーの準備待ち
        [preparing] 音声と譜面を準備中
        [no-beats] プレイ可能なビートがありません
        [catalog] 楽曲一覧
        [my-playlists] 自分のプレイリスト
        [playlist] プレイリスト
        [output] 出力先
        [close-panel] パネルを閉じる
        [playlist-settings] リスト設定
        [multi-select] 複数選択
        [move] 移動
        [move-hint] 選んだ曲の前に移動します
        [cancel-move] 移動をキャンセル
        [move-end] 末尾に移動
        [cancel-selection] 複数選択をキャンセル
        [track-details] 曲の詳細
        [skip-next] 次の曲へ
        [device-settings] 機器設定
        [already-added] 追加済み
        [current-track] 現在の曲
        [shuffle-candidates] シャッフル候補
        [import-by-id] IDでインポート
        [playlist-id] プレイリストID
       *[unknown] 音楽空間
    }

music-space-error =
    { $arg0 ->
        [UNAVAILABLE] 音楽機能を利用できません。後でもう一度開いてください。
        [INTERNAL] 音楽機能で内部エラーが発生しました。管理者にサーバーログの確認を依頼してください
        [NOT_FOUND] 選択した項目は存在しません。
        [FORBIDDEN] 操作または編集の権限が必要です。
        [CONFLICT] 内容が変更されました。更新して選び直してください。
        [LIMIT] 音楽リソースの上限に達しました。
        [INVALID] 名前、選択内容、数値の範囲を確認してください。
        [OFFLINE] デバイスを利用できません。チャンクを読み込むか、挿入したレコードを取り出してください。
        [OVERLAP] 音が聞こえる範囲が重なっています。別のデバイスを停止するか、範囲を狭めるか、同期グループを作成してください。
        [UNSUPPORTED] このメディアはこの操作に対応していません。
        [BUSY] デバイス、リクエスト、または音楽ゲームルームが使用中です。処理の完了後に再試行してください。
        [NO_SAFE_DESTINATION] ジュークボックスのそばに安全な移動先がありません
        [TELEPORT_FAILED] テレポートに失敗したか、移動が拒否されました
       *[UNKNOWN] 操作に失敗しました
    }

music-space-selected-tracks = { $arg0 } 曲を選択中
music-space-track-count = { $arg0 } 曲
music-space-device-count = ジュークボックス { $arg0 } 台
music-space-playback-target = 出力先：{ $arg0 }
music-space-online-count = オンライン：{ $arg0 }/{ $arg1 } 台
music-space-device-distance = { $arg0 } · { $arg1 } ブロック
music-space-device-height = { $arg0 ->
    [above] { $arg1 } ブロック上
   *[below] { $arg1 } ブロック下
    }
music-space-device-coordinates = { $arg0 } · X { $arg1 } · Y { $arg2 } · Z { $arg3 }
music-space-sound-value = { $arg0 ->
        [volume] { $arg1 ->
           *[other] { $arg1 }%
        }
       *[range] { $arg1 ->
           *[other] { $arg1 } ブロック
        }
    }
plot-permission-music-control = 音楽デバイスの操作

music-partial-results = 一部の結果を表示します。{ $arg0 }件の音源リクエストに失敗しました

webchat-tab-label = { $arg0 ->
    [1] { player-web-label }
   *[other] { player-web-label } ×{ $arg0 }
    }
webchat-joined = { $arg0 } がウェブチャットに参加しました
webchat-left = { $arg0 } がウェブチャットから退出しました
chat-rate-limited = 送信が速すぎます。少し待ってください。

plot-map-corner-a = 角 A
plot-map-corner-b = 角 B
plot-map-corner-hint = クリックでこの角に移動。右クリックで座標入力、ホイールで高さを4変更。
plot-map-coordinates = 座標 x,y,z
plot-map-zoom-in = 拡大
plot-map-zoom-out = 縮小
plot-map-fit = 範囲全体
plot-map-near = 現在地付近
plot-map-layer = 高さ Y { $arg0 }…{ $arg1 }
plot-map-layer-hint = ホイールで表示階層を変更、クリックで現在の高さへ。地形は常に地表です。
plot-map-north = 北へ
plot-map-west = 西へ
plot-map-east = 東へ
plot-map-south = 南へ
plot-map-scale = 1ピクセル = { $arg0 } × { $arg0 } ブロック
plot-map-progress = 地図読込 { $arg0 }%…
plot-map-unknown = 未読込の地表サンプル：{ $arg0 }
plot-map-legend = 水色：維持 · 紫：追加 · 橙：削除 · 緑：直方体 · A/B：角 · 矢印：自分
plot-selection-validating = バックグラウンドで境界の計算と範囲の検証を実行中… 完了すると自動更新されます。

plot-map-open = Minecraftの地図を見る

plot-map-handheld-hint = オフハンドに地図、木の斧で選択。スニーク＋Fでパネルに戻ります。

plot-map-readonly-hint = 地図の閲覧のみです。角を変更するにはパネルに戻り、直方体の調整を選んでください。

plot-destinations = テレポート地点
plot-destination-add = 現在地を追加
plot-destination-confirm-delete = テレポート地点「{ $arg0 }」を削除しますか？
plot-destination-delete = 地点を削除
plot-destination-deleted = テレポート地点を削除しました。
plot-destination-description = 紹介（省略可）
plot-destination-edit = 名前と紹介を編集
plot-destination-empty = 利用できる地点はありません。
plot-destination-error-changed = 地点が変更されました。開き直してください。
plot-destination-error-description = 紹介は512文字以内で、制御文字は使用できません。
plot-destination-error-location = この区画内の安全な地面に立ってください。
plot-destination-error-missing = この地点は削除されています。
plot-destination-error-name = 名前は1行、1〜48文字で入力してください。
plot-destination-error-private = この地点は非公開で、区画のメンバーのみ利用できます。
plot-destination-error-unsafe = この地点は利用できないか、安全ではありません。
plot-destination-loading = 地点を読み込み中…
plot-destination-move = 現在地に更新
plot-destination-name = 名前
plot-destination-public-empty = 公開されている地点はありません。
plot-destination-public-off = 公開：オフ
plot-destination-public-on = 公開：オン
plot-destination-saved = テレポート地点を保存しました。
plot-destination-teleport = テレポート
plot-error-subplot-destination = 選択範囲内にある親区画の地点を先に移動または削除してください。
plot-error-subplot-own-destination = 地点を新しい範囲内に残すか、先に移動または削除してください。

plot-destination-manage = 管理
plot-directory-owned = 所有 · { $arg0 }
plot-directory-participating = 参加 · { $arg0 }
plot-directory-search = 区画を検索
plot-directory-search-value = 検索：{ $arg0 }
plot-directory-search-field = 区画名またはID
plot-directory-clear-search = 検索をクリア
plot-directory-empty = この分類にはまだ区画がありません。
plot-directory-no-match = 一致する区画がありません。検索を変更またはクリアしてください。
plot-workspace-area = 範囲を編集
plot-workspace-area-hint = この区画の保護範囲を調整
plot-workspace-subplots = 子区画
plot-workspace-subplots-hint = 内部の区分を確認・作成・管理
plot-workspace-access-hint = メンバー、役割、操作権限を管理
plot-workspace-settings-hint = 名前、告知、環境、所有権
plot-workspace-destinations-hint = この区画のテレポート地点を確認して移動
plot-workspace-role = あなたの役割：{ $arg0 }
plot-workspace-ownership = 所有権と削除
plot-workspace-id = 区画ID：{ $arg0 }
plot-workspace-groups = 権限グループ
plot-workspace-permissions = 操作権限
plot-workspace-other-world = 先にこの区画があるワールドへ移動してください。

plot-destination-titles = 到着時のタイトル
plot-destination-title = タイトル
plot-destination-subtitle = サブタイトル
plot-destination-title-hint = テレポート成功後に表示します。MiniMessage の色・装飾・グラデーションに対応。空欄で非表示になり、プレビューでは保存しません。
plot-destination-title-preview = 全画面プレビュー
plot-destination-title-unset = 未設定
plot-destination-title-save = タイトルを保存
plot-destination-title-unchanged = 未保存の変更はありません。
plot-destination-title-apply = 下書きに適用
plot-destination-error-title = タイトルは1行で、表示文字数は32文字、MiniMessage 原文は512文字までです。
plot-destination-error-subtitle = サブタイトルは1行で、表示文字数は64文字、MiniMessage 原文は512文字までです。
