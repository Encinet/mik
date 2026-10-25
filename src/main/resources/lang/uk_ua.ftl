### MIK · uk-ua
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = { $case ->
       *[nominative] Ділянка
        [genitive] ділянки
        [dative] ділянці
        [accusative] ділянку
        [instrumental] ділянкою
        [locative] ділянці
    }
    .gender = feminine

-vehicle-model = { $case ->
       *[nominative] Модель
        [genitive] моделі
        [dative] моделі
        [accusative] модель
        [instrumental] моделлю
        [locative] моделі
    }
    .gender = feminine

## Shared · General

back = Назад
back-to-main = До головного меню
click-open = Натисніть, щоб відкрити
close = Закрити
current-off = Зараз: вимкнено
current-on = Зараз: увімкнено
player-only = Ця команда доступна лише гравцям
unknown = Невідомо
usage = Використання

## Shared · Language, roles and time

language-command-description = Відкрити налаштування мови
language-menu-title = Налаштування мови
language-selected = Вибрано
language-set = Вибрано мову: { $arg0 }
role-custodian = Хранитель
role-member = Учасник
role-moderator = Модератор
role-new-player = Новий гравець
time-hours-minutes = { $arg0 } год { $arg1 } хв
time-minutes = { $arg0 } хв

## Shared · Menu presentation

bedrock-menu-back = Назад
bedrock-menu-hotkey = Швидка дія (F)
bedrock-menu-primary = Основна дія
bedrock-menu-scroll-down = Вниз
bedrock-menu-scroll-up = Вгору
bedrock-menu-secondary = Додаткова дія
interface-fov-apply = Застосувати FOV
interface-fov-custom = Ввести точне значення
interface-fov-description = Укажіть FOV клієнта; змінюється лише позиція меню. Типово: 70°.
interface-fov-input = FOV клієнта: ціле число від 30 до 110
interface-fov-invalid = Введіть ціле число від 30 до 110.
interface-fov-title = Адаптація поля зору (FOV)
interface-scale-current = Зараз: { $arg0 }
interface-scale-description = Розмір тексту множиться на розмір меню; обидві групи налаштовуються коліщатком.
interface-scale-layout-title = Розмір меню
interface-scale-menu-title = Інтерфейс і текст
interface-scale-native-hint = Ці налаштування стосуються просторових меню Java. Текст нативних форм залежить від налаштувань клієнта.
interface-scale-preview = Зразок тексту Aa 123
interface-scale-reset = Повернути обидва до 100%
interface-scale-text-title = Розмір тексту
menu-rhythm-game-display = Грає в ритм-гру
menu-usage-display-mm = <aqua><bold>У меню</bold></aqua>
url-dialog-confirm = Відкрити
url-dialog-hint = Клієнт ще раз попросить підтвердити відкриття посилання.
url-dialog-question-rich = Відкрити { $label }?
url-dialog-title = Відкрити { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Рекомендовані моди не виявлено: { $mods }. Натисніть назву для завантаження; якщо вже встановлено, перевірте їх увімкнення та сумісність.
client-version-mismatch-mm = <gray>Версія сервера <white>{ $serverVersion }</white>, поточна версія клієнта <white>{ $clientVersion }</white>.</gray>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>Поблизу: { $count ->
        [one] <gold>{ $count }</gold> <gold>невидимий гравець</gold>
        [few] <gold>{ $count }</gold> <gold>невидимі гравці</gold>
        [many] <gold>{ $count }</gold> <gold>невидимих гравців</gold>
       *[other] <gold>{ $count }</gold> <gold>невидимого гравця</gold>
    }</yellow> <gray>·</gray> <gray>до найближчого: приблизно { $distance ->
        [one] { $distance } блок
        [few] { $distance } блоки
        [many] { $distance } блоків
       *[other] { $distance } блока
    }</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = На сервер непомітно пробирається { $player }
    .item-2 = На таємній стежці з’являється { $player }
    .item-3 = З кущів з’являється { $player }
presence-quit-templates =
    .item-1 = { $player } тихо зникає
    .item-2 = { $player } вирушає зберігатися
    .item-3 = Реальність кличе: { $player } залишає сервер
welcome-first-join-mm = <gold><bold>Вітаємо на MIK!</bold></gold><newline><gray>Спершу відвідайте <click:open_url:'https://mcmik.top'><yellow>вебсайт</yellow></click> і <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>. Головне меню також можна відкрити командою <yellow>/menu</yellow>.</gray>
welcome-return-mm = <gold><bold>З поверненням!</bold></gold><newline><gray>Ви востаннє заходили <yellow>{ $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дні
        [many] { $arg0 } днів
       *[other] { $arg0 } дня
    } тому</yellow>. Скористайтеся <yellow>/menu</yellow>, щоб швидко знайти основні функції.</gray>

## Player · AFK

afk-already-afk-mm = <gray>Ви вже <gold>AFK</gold></gray>
    <gray>Ви вже <gold>AFK</gray></gray>
afk-command-description = Увійти в режим AFK
afk-default-statuses =
    .item-1 = поза клавіатурою
    .item-2 = у режимі енергозбереження
    .item-3 = у фоновому режимі
    .item-4 = у роздумах про вічне
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>тепер AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>залишає повідомлення:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>змінює статус:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>повертається до клавіатури</green>
    .item-2 = <gray>*</gray> <player> <green>повертається</green>
afk-not-afk-mm = <gray>Ви зараз не <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>Статус AFK може містити щонайбільше { $arg0 ->
        [one] <white>{ $arg0 }</white> символ
        [few] <white>{ $arg0 }</white> символи
        [many] <white>{ $arg0 }</white> символів
       *[other] <white>{ $arg0 }</white> символа
    }</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · збіги: { $arg2 ->
        [one] { $arg2 } запис
        [few] { $arg2 } записи
        [many] { $arg2 } записів
       *[other] { $arg2 } запису
    } · історія: { $arg3 }
player-association-header = [Зв’язок входу] { $arg0 } · збіги історії адрес: { $arg1 ->
        [one] { $arg1 } гравець
        [few] { $arg1 } гравці
        [many] { $arg1 } гравців
       *[other] { $arg1 } гравця
    }.
player-association-hover = Збіглися історичні мережеві записи. Це лише допоміжна інформація для перевірки; адреси не показуються.
player-association-more-addresses = Ще: { $arg0 ->
        [one] { $arg0 } запис
        [few] { $arg0 } записи
        [many] { $arg0 } записів
       *[other] { $arg0 } запису
    }
player-association-more-players = Ще: { $arg0 ->
        [one] { $arg0 } гравець
        [few] { $arg0 } гравці
        [many] { $arg0 } гравців
       *[other] { $arg0 } гравця
    }
player-association-time-days = { $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дні
        [many] { $arg0 } днів
       *[other] { $arg0 } дня
    } тому
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } запис
        [few] { $arg1 } записи
        [many] { $arg1 } записів
       *[other] { $arg1 } запису
    })
player-association-time-separator = ,
player-association-time-today = сьогодні

## Player · Profile and tab list

nametag-clear-all-error = Не вдалося очистити теги імені. Спробуйте пізніше
nametag-clear-all-success = ✔ Усі теги імені очищено
nametag-clear-error = Не вдалося очистити { $arg0 }
nametag-clear-success = ✔ Очищено: { $arg0 }
nametag-click-fill-command = Натисніть, щоб вставити команду
nametag-command-description = Налаштувати теги імені
nametag-copy-hover = Скопіювати код MiniMessage до буфера обміну
nametag-copy-label = [копіювати]
nametag-effect-label = Результат
nametag-empty-content-rich = Вміст не може бути порожнім. Щоб очистити його, скористайтеся { $command }
nametag-format-docs = Довідка з форматування
nametag-leading-label = Префікс імені
nametag-no-permission = Ви не маєте дозволу на цю команду
nametag-online-editor = Онлайн-редактор
nametag-preview-label = Попередній перегляд
nametag-sample-message = приклад повідомлення
nametag-set-error = Не вдалося задати { $arg0 }. Спробуйте пізніше
nametag-set-success = ✔ Задано: { $arg0 }
nametag-title = Теги імені
nametag-too-long = Значення { $arg0 } задовге. Максимальна довжина: { $arg1 ->
        [one] { $arg1 } символ
        [few] { $arg1 } символи
        [many] { $arg1 } символів
       *[other] { $arg1 } символа
    }
nametag-trailing-label = Суфікс імені
nametag-unset = (не задано)
nametag-unsupported-placeholder-rich = Підтримуються лише змінні PlaceholderAPI: { $supported }; не підтримуються: { $unsupported }
nametag-user-data-load-failed = Не вдалося завантажити дані користувача. Спробуйте пізніше
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Гравців у мережі: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Повернутися до попереднього місця телепортації або смерті
back-empty-mm = <gray>Немає місця для повернення</gray>
back-pending-mm = <gray>Телепортація, зачекайте</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>Повернення на { $steps ->
        [one] { $steps } крок
        [few] { $steps } кроки
        [many] { $steps } кроків
       *[other] { $steps } кроку
    } назад</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>Не вдалося телепортуватися. Спробуйте ще раз</red>
back-undo-empty-mm = <gray>Немає команди <white>/back</white> для скасування</gray>
back-undo-success-mm = <green>Останню команду <white>/back</white> скасовано</green>
back-undo-world-missing-mm = <red>Цільовий світ недоступний. Скасування неможливе</red>
back-world-missing-mm = <red>Цільовий світ недоступний. Повернення неможливе</red>
boundary-near-mm = <yellow>Ви наближаєтеся до межі світу</yellow>
boundary-reached-mm = <red>Ви досягли межі світу</red>
fly-command-description = Перемкнути особистий політ
fly-disabled = Політ вимкнено
fly-enabled = Політ увімкнено
fly-native-mode = Ваш доступ /fly вимкнено. Цей режим гри все ще дозволяє літати.
fly-speed-click = Встановити швидкість польоту { $arg0 }x
fly-speed-current = Швидкість польоту: { $arg0 }x
fly-speed-presets = Передналаштування
fly-speed-reset = Швидкість польоту скинуто до 1x
fly-speed-reset-action = Скинути
fly-speed-reset-click = Скинути швидкість польоту до 1x
fly-speed-set = Швидкість польоту встановлено на { $arg0 }x
fly-state-disabled = Вимкнено
fly-state-enabled = Увімкнено
fly-status = Політ: { $arg0 } | Швидкість: { $arg1 }x
flyspeed-command-description = Переглянути або налаштувати особисту швидкість польоту
home-action-create = Створити дім
home-action-delete = Видалити дім
home-action-icon = Встановити значок із руки
home-action-teleport = Телепортуватися
home-action-update = Оновити місце
home-already-exists = Дім із такою назвою вже існує
home-back = Назад
home-confirm-delete = Підтвердити видалення
home-confirm-delete-detail-rich = Видалити домівку { $home }
home-confirm-delete-warning = Цю дію неможливо скасувати
home-confirm-update = Підтвердити оновлення
home-confirm-update-detail-rich = Оновити домівку { $home } до цієї позиції
home-delete-command-description = Видалити домівку
home-delete-usage-desc = Видалити домівку
home-deleted-rich = Домівку { $home } видалено
home-distance = Відстань: { $arg0 }
home-distance-blocks = { $arg0 } бл.
home-distance-other-world = інший світ
home-empty-description = Станьте там, де хочете створити домівку
home-empty-title = Домівок ще немає
home-icon = Значок: { $arg0 }{ $arg1 }
home-icon-default-note = · типовий
home-icon-hand-invalid = Предмет в основній руці не можна використати як значок домівки
home-icon-updated-rich = Значок домівки { $home } оновлено
home-invalid-icon = Цей матеріал не можна використати як значок домівки
home-invalid-name = Назва домівки не може містити символи < > :
home-list-rich = Домівки: { $homes }
home-location = Координати: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = Дані про розташування цієї домівки некоректні
home-max-reached = Можна створити щонайбільше домівок: { $arg0 }
home-menu-title = Домівки
home-no-homes = Ви ще не створили жодної домівки
home-not-found-rich = Домівки { $home } не існує
home-set-command-description = Створити домівку в поточному місці
home-set-rich = Домівку { $home } створено
home-set-usage-desc = Створити домівку в поточному місці
home-summary-count = Створено: { $arg0 } / { $arg1 }
home-teleport-command-description = Телепортуватися до домівки
home-teleport-failed = Не вдалося телепортуватися. Спробуйте ще раз
home-teleported-rich = Ви телепортувалися до домівки { $home }
home-updated-rich = Розташування домівки { $home } оновлено
home-usage-book = Довідка команд
home-usage-delhome = /delhome <name>  Видалити домівку
home-usage-home = /home tp <name>  Телепортуватися до домівки
home-usage-icon = /home icon <material> <name>  Вибрати значок
home-usage-name-rule = Назва домівки не може містити < > :
home-usage-sethome = /sethome <name>  Зберегти поточне розташування
home-world = Світ: { $arg0 }
home-world-end = · Енд
home-world-nether = · Незер
reback-command-description = Скасувати останню команду /back
teleport-accept-command-description = Прийняти запит на телепортацію, що очікує відповіді
teleport-allow = Телепортації іншими гравцями
teleport-block-afk = Забороняти під час AFK
teleport-denied = { $player } забороняє телепортувати себе звичайним гравцям
teleport-deny-command-description = Відхилити запит на телепортацію, що очікує відповіді
teleport-menu-title = Дозволи на телепортацію
teleport-moved-here = Гравець { $arg0 } телепортував вас сюди
teleport-policy-always-allow = Завжди дозволяти
teleport-policy-always-deny = Завжди забороняти
teleport-policy-require-consent = Вимагати моєї згоди
teleport-request-accept = Прийняти
teleport-request-approved = Гравець { $player } прийняв ваш запит на телепортацію
teleport-request-deny-action = Відхилити
teleport-request-expired = Термін дії запиту на телепортацію минув
teleport-request-failed = Не вдалося виконати телепортацію. Спробуйте ще раз
teleport-request-none = У вас немає запиту на телепортацію, що очікує відповіді
teleport-request-received = Гравець { $sender } хоче телепортувати вас: { $command }
teleport-request-rejected = Запит на телепортацію відхилено
teleport-request-sent = Запит на телепортацію надіслано гравцю { $player }
teleport-summary-afk = Завжди дозволено, крім статусу AFK
teleport-summary-allow = Телепортації звичайними гравцями завжди дозволено
teleport-summary-consent = Телепортації звичайними гравцями вимагають моєї згоди
teleport-summary-deny = Телепортації звичайними гравцями завжди заборонено
tpany-command-description = Телепортуватися до гравця в мережі або поза нею
tpany-done-offline-rich = Ви телепортувалися до останнього відомого розташування гравця { $player }: { $world } { $location }
tpany-done-online-rich = Ви телепортувалися до поточного розташування гравця { $player }
tpany-not-found-rich = Не вдалося знайти розташування гравця { $player }
tpany-usage = Використання
tpany-usage-command = /tpany <player>
tpany-usage-desc = Телепортуватися до гравця, використовуючи також збережене розташування

## Player · PVP

pvp-admin-menu-title = Керування PvP - сторінка { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = Дозволити атакувати їздових мобів
pvp-allow-mounted-damage-desc = Інший гравець зможе атакувати моба, на якому ви їдете, якщо у вас обох увімкнено PvP
pvp-auto-enable-desc = Двічі поспіль атакувати гравця з увімкненим PvP
pvp-auto-enable-label = Автоматичне ввімкнення
pvp-auto-enable-warning-actionbar-mm = <yellow>Атакуйте ще раз, щоб автоматично ввімкнути PvP</yellow>
pvp-auto-enabled-actionbar-mm = <green>PvP увімкнено автоматично</green>
pvp-back-admin = До керування PvP
pvp-combat-locked-mm = <red>Не можна вимкнути PvP під час бою</red> <dark_gray>|</dark_gray> <gray>Залишок часу: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> секунда
        [few] <yellow>{ $arg0 }</yellow> секунди
        [many] <yellow>{ $arg0 }</yellow> секунд
       *[other] <yellow>{ $arg0 }</yellow> секунди
    }</gray>
pvp-combat-tag-label = Бойова затримка
pvp-combat-tag-value = { $arg0 } с
pvp-command-description = Перемкнути стан PvP
pvp-enable = Увімкнути PvP
pvp-enable-desc = Ви зможете атакувати інших гравців з увімкненим PvP, а вони зможуть атакувати вас
pvp-enable-on-death = Увімкнути PvP після смерті
pvp-enable-on-death-desc = PvP автоматично вмикатиметься після вашої смерті
pvp-enable-on-death-label = Увімкнення після смерті
pvp-enabled-on-death-mm = <yellow>PvP увімкнено через вашу смерть</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Відкрити керування PvP гравців у мережі</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Відкрити налаштування PvP</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Керувати гравцем у мережі</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Переглянути свій стан PvP</gray>
pvp-help-title-mm = <gold><bold>Команди PvP</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Перемкнути свій стан PvP</gray>
pvp-menu-title = Налаштування PvP
pvp-mob-protected-actionbar-mm = <yellow>Цього моба захищено від шкоди PvP</yellow>
pvp-mob-protection-label = Захист мобів
pvp-mounted-damage-label = Шкода мобам із вершником
pvp-next-page = Наступна сторінка
pvp-no-permission-mm = <red>Ви не маєте дозволу керувати станом PvP іншого гравця</red>
pvp-player-not-found-mm = <red>Гравця немає в мережі</red>
pvp-prev-page = Попередня сторінка
pvp-protect-mobs = Захищати своїх мобів
pvp-protect-mobs-desc = Захищати приручених вами мобів і мобів, на яких ви їдете
pvp-set-by-staff-mm = <aqua>Персонал установив ваш стан PvP:</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = Для { $arg0 } встановлено стан PvP: { $arg1 }
pvp-set-other-rich = Для { $player } встановлено стан PvP: { $state }
pvp-state-label = PvP
pvp-state-off = Вимкнено
pvp-state-on = Увімкнено
pvp-status-player = Гравець: { $arg0 }
pvp-status-player-rich = Гравець: { $player }
pvp-status-title-mm = <gold><bold>Стан PvP</bold></gold>
pvp-summary-disabled = PvP вимкнено
pvp-summary-enabled = PvP увімкнено
pvp-target-disabled-actionbar-mm = <gray>У цього гравця PvP вимкнено</gray>
pvp-target-menu-title = Налаштування PvP - { $arg0 }
pvp-toggled-off-mm = <gray>PvP вимкнено</gray> <dark_gray>|</dark_gray> <gray>Інші гравці не можуть вас атакувати</gray>
pvp-toggled-on-mm = <green>PvP увімкнено</green> <dark_gray>|</dark_gray> <gray>Тепер ви можете битися з іншими гравцями з увімкненим PvP</gray>

## Player · Utilities

hat-command-description = Надіти предмет з основної руки
hat-empty-hand-mm = <red>В основній руці немає предмета</red> <gray>Візьміть блок або предмет і введіть /hat</gray>
hat-success-mm = <green>Предмет з основної руки надіто</green> <dark_gray>|</dark_gray> <gray>Предмети в руці та комірці шолома поміняно місцями</gray>
removeitems-command-description = Видалити викинуті предмети
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Видалено: { $countValue ->
        [one] { $count } викинутий предмет
        [few] { $count } викинуті предмети
        [many] { $count } викинутих предметів
       *[other] { $count } викинутого предмета
    } · світ { $world } { $location } { $radius }
removeitems-no-world = Не вдалося визначити світ
removeitems-radius = радіус: { $arg0 } бл.
self-kick-command-description = Кікнути себе із сервера
self-kick-reason-too-long = Причина кіка не може перевищувати { $arg0 ->
        [one] { $arg0 } символ
        [few] { $arg0 } символи
        [many] { $arg0 } символів
       *[other] { $arg0 } символа
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Ви залишили сервер за власним запитом. Причина: { $reason }
       *[third] { $player }: добровільний вихід із сервера. Причина: { $reason }
    }
    .item-1 = { $player }: несподіваний самокік. Причина: { $reason }
    .item-2 = { $player }: самокік через { $reason }!
    .item-3 = { $player }: добровільний вихід із поясненням: { $reason }
    .item-4 = { $player }: самостійний вихід із причини: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Ви залишили сервер за власним запитом.
       *[third] { $player }: добровільний вихід із сервера.
    }
    .item-1 = { $player }: оце так самокік!
    .item-2 = { $player }: вихід без зайвих слів.
    .item-3 = { $player }: бездоганний самокік.
    .item-4 = { $player }: вихід за власним бажанням.
trash-command-description = Відкрити смітник
trash-title = Смітник
trash-title-hint = Наведіться на предмет і натисніть F, щоб видалити всю стопку

## Community · Chat and mentions

chat-copy-hover = ЛКМ: скопіювати повідомлення
chat-delay-3s = 3 секунди
chat-delay-5s = 5 секунд
chat-delay-7s = 7 секунд
chat-delay-cancel-command-description = Скасувати відкладені повідомлення
chat-delay-cancelled = Відкладене повідомлення скасовано
chat-delay-current = Поточна затримка чату: { $arg0 }
chat-delay-nothing-to-cancel = Немає відкладених повідомлень
chat-delay-off = Вимкнено
chat-delay-preview-marker = [Очікує]
chat-delay-queued = Повідомлення буде надіслано через { $arg0 }; для скасування введіть /cancel або /c
chat-item-empty-hover = В основній руці немає предмета
chat-mention-all-hover = Сповістити всіх видимих гравців у цьому каналі
chat-msg-command-description = Надіслати приватне повідомлення й перейти до приватного чату
chat-player-hover = ЛКМ: почати приватне повідомлення
chat-private-enter = Увімкнено приватний чат із { $arg0 }
chat-private-label = ПП
chat-private-no-reply-target = Жоден із недавніх співрозмовників приватного чату не в мережі
chat-private-self = Не можна почати приватний чат із собою
chat-private-target-not-found = Гравця немає в мережі: { $arg0 }
chat-private-target-offline = Співрозмовник вийшов із мережі. Увімкнено загальний чат
chat-public-command-description = Перейти до загального чату
chat-public-enter = Увімкнено загальний чат
chat-reply-command-description = Відповісти останньому співрозмовнику приватного чату
chat-settings-menu-title = Налаштування чату
chat-social-account-label = Обліковий запис платформи
chat-social-bound-player-label = Прив’язаний гравець
chat-social-name-label = Ім’я в соцмережі
chat-social-unbound = Не прив’язано до гравця Minecraft
chat-staff-command-description = Перейти до чату персоналу
chat-staff-permission-missing = Немає дозволу на чат персоналу. Увімкнено загальний чат
mention-action-bar = Напис над інвентарем
mention-action-bar-text = { $sender } згадує вас
mention-alerts = Сповіщення про згадки
mention-menu-title = Сповіщення про згадки
mention-mute-afk = Вимикати під час AFK
mention-sound = Звук сповіщення
mention-summary-action-bar = напис над інвентарем
mention-summary-disabled = Сповіщення вимкнено
mention-summary-enabled = Сповіщення ввімкнено
mention-summary-enabled-afk = Сповіщення ввімкнено, але вимкнено під час AFK
mention-summary-modes-afk = { $arg0 }, вимкнено під час AFK
mention-summary-sound = звук
staffchat-enter = Ви приєдналися до чату персоналу

## Community · Announcements and tips

announcement-empty = Оголошень немає
announcement-first-page = Найновіші оголошення
announcement-last-page = Найдавніші оголошення
announcement-more-count = Ще { $arg0 } не показано
announcement-new-count = Нових: { $arg0 }
announcement-newer = Новіші оголошення
announcement-older = Старіші оголошення
announcement-reload = Оновити оголошення
announcement-reload-command-description = Перезавантажити файл оголошень
announcement-reload-failed = Не вдалося перезавантажити оголошення; попередні збережено
announcement-reloaded = Оголошення оновлено
announcement-scroll-hint = Колесо для перегляду по горизонталі
announcement-unread-count = Непрочитаних: { $arg0 }
announcement-updated = Оголошення оновлено
announcement-view-all = Усі оголошення
announcement-view-command-description = Переглянути оголошення сервера
tip-hover = Підказка сервера
tip-label = ПІДКАЗКА
tip-reload-command-description = Перезавантажити tips.txt
tip-reload-count = { $arg0 ->
        [one] { $arg0 } запис
        [few] { $arg0 } записи
        [many] { $arg0 } записів
       *[other] { $arg0 } запису
    }
tip-reload-done = Підказки перезавантажено

## Community · Identity and social platforms

identity-admin-binding-not-found = Однозначну прив’язку гравця й платформи не знайдено.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Для { $arg1 } видалено: { $arg2 ->
        [one] { $arg2 } прив’язка
        [few] { $arg2 } прив’язки
        [many] { $arg2 } прив’язок
       *[other] { $arg2 } прив’язки
    } ({ $arg0 }).
identity-admin-command-description = Переглянути або відкликати зовнішні прив’язки
identity-admin-help = /bindadmin lookup <гравець-або-UUID>
    /bindadmin unlink <гравець-або-UUID> <платформа> confirm
identity-admin-lookup-empty = Прив’язок цього гравця не знайдено.
identity-admin-lookup-title = Прив’язки гравця { $arg0 }:
identity-binding-details = · { $arg0 } · область { $arg1 } · { $arg2 }
identity-binding-not-owned = У вас немає прив’язок для цієї платформи.
identity-binding-unlinked = Видалено: { $arg1 ->
        [one] { $arg1 } прив’язка
        [few] { $arg1 } прив’язки
        [many] { $arg1 } прив’язок
       *[other] { $arg1 } прив’язки
    } ({ $arg0 }).
identity-bindings-empty = Ви не прив’язали жодного облікового запису зовнішньої платформи.
identity-bindings-title = Прив’язані зовнішні облікові записи:
identity-bindings-unlink-hint = Використайте /bind unlink <платформа>, щоб видалити всі прив’язки платформи.
identity-code-cancelled = Невикористаний код прив’язки { $arg0 } скасовано.
identity-code-copy = Натисніть, щоб скопіювати повну команду прив’язки
identity-code-expiry = Код дійсний 5 хвилин і використовується один раз. Створення нового коду скасовує старий.
identity-code-issued = Код прив’язки для { $arg0 } створено:
identity-code-not-pending = Невикористаного коду прив’язки { $arg0 } немає.
identity-code-redeem = У потрібній групі { $arg0 } згадайте бота й надішліть: { $arg1 }
identity-command-description = Прив’язати Minecraft до облікового запису зовнішньої платформи
identity-help = Команди прив’язки облікового запису:
    /bind qq — швидко створити команду прив’язки QQ
    /bind <платформа> — створити одноразовий код
    /bind list — показати наявні прив’язки
    /bind cancel <платформа> — скасувати невикористаний код
    /bind unlink <платформа> — видалити всі прив’язки платформи
identity-notify-linked = Вашу особу { $arg0 } підтверджено. Перегляд: /bind list; видалення: /bind unlink { $arg0 }.
identity-notify-unlinked = Вашу прив’язку { $arg0 } видалено.
identity-platform-none = немає
identity-platforms = Підтримувані платформи: { $arg0 }
identity-scope-notice = Ця платформа розділяє облікові записи за групами. Виконайте прив’язку в потрібній групі; кожну групу треба прив’язати окремо.
identity-storage-error = Помилка операції з базою прив’язок. Зверніться до адміністратора.
identity-unavailable = Прив’язка облікових записів зараз недоступна. Зверніться до адміністратора.
identity-unsupported-platform = Непідтримувана платформа: { $arg0 }
social-admin-conversations-empty = Для { $arg0 } не виявлено розмов.
social-admin-conversations-title = Виявлені розмови для { $arg0 }:
social-admin-help = /social platforms · /social status [платформа] · /social reload [платформа] · /social conversations <платформа>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Соціальну платформу { $arg0 } перезавантажено.
social-admin-reloaded-all = Усі соціальні платформи перезавантажено.
social-admin-status = { $arg0 } · { $arg1 } · покоління { $arg2 } · розмови { $arg3 }
social-admin-title = Соціальні платформи
social-admin-unknown = Невідома соціальна платформа: { $arg0 }
social-binding-already-linked = Цю особу вже прив’язано до гравця Minecraft { $arg0 }.
social-binding-code-invalid = Код підтвердження недійсний або прострочений. Створіть новий командою /bind { $arg0 } у Minecraft.
social-binding-external-in-use = Цю особу вже прив’язано до іншого гравця Minecraft.
social-binding-help = Виконайте /bind { $arg0 } у Minecraft.
    Скопіюйте створену повну команду.
    Надішліть цю команду сюди.
    Перевірте прив’язку командою /профіль.
social-binding-identity-missing = У цьому повідомленні немає надійного ідентифікатора учасника, тому прив’язування недоступне.
social-binding-linked = Прив’язано до гравця Minecraft { $arg0 }.
social-binding-not-bound = Цю особу платформи не прив’язано.
social-binding-platform-mismatch = Цей код підтвердження видано для іншої платформи.
social-binding-player-scope-in-use = Цей гравець Minecraft уже має іншу прив’язану особу в цій розмові.
social-binding-rate-limited = Забагато невдалих спроб. Повторіть приблизно через { $arg0 ->
        [one] { $arg0 } хвилину
        [few] { $arg0 } хвилини
        [many] { $arg0 } хвилин
       *[other] { $arg0 } хвилини
    }.
social-binding-status-unbound = Цю особу не прив’язано. Використайте /прив’язати з кодом підтвердження.
social-binding-unavailable = Прив’язування особи тимчасово недоступне.
social-binding-unlink-prompt = Відв’язування видалить зв’язок цієї особи з Minecraft. Щоб продовжити, надішліть /відв’язати підтвердити.
social-binding-unlinked = Відв’язано від гравця Minecraft { $arg0 }.
social-command-description = Керування адаптерами соціальних платформ
social-duration-days = { $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дні
        [many] { $arg0 } днів
       *[other] { $arg0 } дня
    } { $arg1 } год. { $arg2 } хв.
social-duration-hours = { $arg0 } год. { $arg1 } хв.
social-duration-minutes = { $arg0 } хв.
social-help-command-description = Показати цей список команд
social-link-command-description = Прив’язати обліковий запис Minecraft кодом із гри
social-online-command-description = Показати гравців онлайн
social-profile-afk = Онлайн · AFK
social-profile-avatar-alt = Обличчя Minecraft
social-profile-command-description = Показати свій профіль; повні учасники шукають за ім’ям, згадкою або відповіддю
social-profile-first-joined-label = Перший вхід
social-profile-language-label = Мова
social-profile-last-seen-label = Останній вхід
social-profile-member-required = Лише прив’язані учасники, модератори або хранителі можуть переглядати профілі інших гравців.
social-profile-offline = Офлайн
social-profile-online = Онлайн · активний
social-profile-play-time-label = Час гри
social-profile-player-label = Гравець
social-profile-player-not-found = Цього гравця Minecraft не знайдено.
social-profile-presence-label = Стан
social-profile-target-ambiguous = Виберіть одну ціль: ім’я гравця, одну згадку або одного автора відповіді.
social-profile-target-unbound = Згадану особу або автора повідомлення, на яке відповіли, не прив’язано до Minecraft.
social-profile-uuid-label = UUID
social-query-active-label = Активні
social-query-no-players = Зараз немає гравців онлайн.
social-query-online-label = Онлайн
social-query-players-label = Гравці
social-query-unknown = Невідома команда. Надішліть /допомога, щоб переглянути доступні команди.
social-query-uptime-label = Час роботи
social-reply-hidden = Відповідь приховано політикою безпеки вмісту сервера.
social-state-disabled = вимкнено
social-state-failed = помилка
social-state-ready = готово
social-state-starting = запускається
social-state-stopped = зупинено
social-status-command-description = Показати гравців, TPS, MSPT та час роботи
social-title-command-help = Довідка команд
social-title-content-hidden = Вміст приховано
social-title-identity-linking = Прив’язування облікового запису
social-title-identity-unlinking = Відв’язування облікового запису
social-title-online-players = Гравці онлайн
social-title-player-profile = Профіль гравця
social-title-server-status = Стан сервера
social-title-unknown-command = Невідома команда
social-unlink-command-description = Видалити це прив’язування після підтвердження

## Community · AI and knowledge

ai-busy = ШІ обробляє забагато запитів; спробуйте ще раз трохи згодом
ai-cleared = Історію розмови з ШІ очищено
ai-command-description = Поставити запитання ігровому ШІ-помічнику
ai-disabled = ШІ-помічник вимкнений або не налаштований
ai-knowledge-curation-failed = Помилка впорядкування знань: { $arg0 }
ai-knowledge-curation-finished = Упорядкування завершено: застосовано { $arg0 } · пропущено { $arg1 } · помилок { $arg2 } · карантин { $arg3 } · опрацьовано { $arg4 }
ai-knowledge-curation-started = Упорядкування знань розпочато.
ai-knowledge-disabled = Базу знань вимкнено.
ai-knowledge-index-dirty = Потрібно узгодити індекс; пошук особистих спогадів закрито до синхронізації.
ai-knowledge-last-failure = Остання помилка: { $arg0 }
ai-knowledge-last-sync = Остання синхронізація: { $arg0 }
ai-knowledge-learning-disabled = Навчання бази знань вимкнено.
ai-knowledge-operation-failed = Помилка операції з базою знань: { $arg0 }
ai-knowledge-operation-started = Операцію з базою знань розпочато.
ai-knowledge-protected = Документ { $arg0 } тепер захищено.
ai-knowledge-rollback = Документ { $arg0 } відновлено як редакцію { $arg1 }.
ai-knowledge-status = Знання: { $arg0 ->
        [one] { $arg0 } загальний документ
        [few] { $arg0 } загальні документи
        [many] { $arg0 } загальних документів
       *[other] { $arg0 } загального документа
    } / { $arg1 ->
        [one] { $arg1 } фрагмент тексту
        [few] { $arg1 } фрагменти тексту
        [many] { $arg1 } фрагментів тексту
       *[other] { $arg1 } фрагмента тексту
    } · { $arg2 ->
        [one] { $arg2 } особистий документ
        [few] { $arg2 } особисті документи
        [many] { $arg2 } особистих документів
       *[other] { $arg2 } особистого документа
    } / { $arg3 ->
        [one] { $arg3 } фрагмент тексту
        [few] { $arg3 } фрагменти тексту
        [many] { $arg3 } фрагментів тексту
       *[other] { $arg3 } фрагмента тексту
    }
ai-knowledge-sync-failed = Помилка синхронізації знань: { $arg0 }
ai-knowledge-sync-started = Синхронізацію знань розпочато.
ai-knowledge-synchronized = { ai-knowledge-status } · Синхронізацію завершено.
ai-knowledge-unprotected = Захист документа { $arg0 } знято.
ai-learning-failure = Помилка навчання: { $arg0 }
ai-learning-status = Навчання: кандидати { $arg0 } · видобування { $arg1 } · черга { $arg2 } · видобувань відкинуто { $arg3 } · кандидатів відкинуто { $arg4 } · карантин { $arg5 }
ai-memory-empty = Особистих спогадів для { $arg0 } немає.
ai-memory-list = Особисті спогади для { $arg0 }: { $arg1 }
ai-memory-not-found = Особистий спогад { $arg0 } не знайдено.
ai-memory-removed = Для { $arg1 } видалено назавжди: { $arg0 ->
        [one] { $arg0 } особистий спогад
        [few] { $arg0 } особисті спогади
        [many] { $arg0 } особистих спогадів
       *[other] { $arg0 } особистого спогаду
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Невідомий гравець у кеші: { $arg0 }
ai-prompt-too-long = Запитання задовге (щонайбільше { $arg0 ->
        [one] { $arg0 } символу
        [few] { $arg0 } символів
        [many] { $arg0 } символів
       *[other] { $arg0 } символу
    })
ai-reload-failed = Не вдалося перезавантажити налаштування ШІ: { $arg0 }
ai-reloaded = Налаштування ШІ перезавантажено ({ $arg0 })
ai-request-failed = Запит до ШІ завершився помилкою; спробуйте пізніше
ai-social-command-description = Поставити запитання спільному ШІ-помічнику
ai-social-question-required = Додайте запитання після команди ШІ
ai-status-disabled = ШІ вимкнено
ai-status-enabled = ШІ увімкнено · провайдер { $arg0 } · модель { $arg1 } · інструменти { $arg2 }
ai-status-none = немає
ai-thinking = Міркую…
ai-usage = Використання: /ai <запитання> · /ai clear · /ai status
ai-worker-status = Завдання ШІ: виконується { $arg0 } · у черзі { $arg1 } · відкинуто { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Вітаємо!</bold> Тепер ваша роль: <gold>Учасник</gold>. Вам доступно більше команд і функцій, зокрема спрощений друк схем.</green>
governance-active-days-30 = Щонайменше 2 активні дні за 30 днів (зараз { $arg0 })
governance-active-days-60 = Щонайменше 6 активних днів за 60 днів (зараз { $arg0 })
governance-active-header = ─── Умови активного учасника ───
governance-active-member = Зараз учасник
governance-active-member-age = Учасник щонайменше 30 днів
governance-active-playtime = Щонайменше 6 годин корисної гри за 60 днів (зараз { $arg0 })
governance-already-moderator = Ви вже модератор.
governance-ballot-recorded = Ваш таємний голос записано як «{ $arg0 }». До завершення його можна змінити.
governance-ban-details = Запропонував { $arg0 } · Тривалість { $arg1 } · Причина: { $arg2 }
governance-ban-suffix = · Тривалість { $arg0 } · Причина: { $arg1 }
governance-choice-abstain = Утримався
governance-choice-no = Проти
governance-choice-yes = За
governance-command-description = Переглянути статус управління та голосування
governance-custodian-no-nomination = Опікуни не беруть участі у виборах модераторів.
governance-custodian-no-resign = Порядок відставки модераторів не стосується опікунів.
# $arg0 (Number): duration; locale decides the unit form.
governance-days = { $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дні
        [many] { $arg0 } днів
       *[other] { $arg0 } дня
    }
governance-error-ban-already-active = Цього гравця вже заблоковано.
governance-error-ban-proposer-ineligible = Лише активні учасники можуть пропонувати голосування про блокування.
governance-error-ban-self = Не можна пропонувати голосування про власне блокування.
governance-error-candidate-ineligible = Кандидат у модератори має бути активним учасником.
governance-error-nomination-not-open = У вас немає відкритої заявки в модератори.
governance-error-not-voter = Вас немає у зафіксованому списку виборців.
governance-error-pause-already-active = Підвищення цього гравця вже призупинено.
governance-error-pause-already-member = Цей гравець уже учасник; призупиняти підвищення не потрібно.
governance-error-pause-end-invalid = Призупинення підвищення має закінчитися в майбутньому.
governance-error-player-unknown = Запису про цього гравця ще немає.
governance-error-removal-requires-petition = Для голосування про усунення потрібно достатньо підтримки.
governance-error-removal-self = Не можна підтримувати власне усунення.
governance-error-removal-sponsor-ineligible = Лише активні учасники можуть підтримувати усунення.
governance-error-removal-sponsors-too-few = Потрібні щонайменше два інші активні учасники.
governance-error-removal-vote-open = Голосування про усунення цього модератора вже триває.
governance-error-termination-reason-invalid = Ця причина завершення не підходить до голосування.
governance-error-vote-already-open = Щодо цього гравця вже триває голосування такого типу.
governance-error-vote-closed = Голосування вже закрито або завершено.
governance-error-vote-cooldown = Триденний строк після нестачі кворуму ще не минув.
governance-error-vote-subject-unknown = Запису про об'єкт голосування ще немає.
governance-error-vote-termination-closed = Після завершення терміну голосування не можна відкликати.
governance-error-vote-unknown = Голосування не знайдено.
governance-error-voters-too-few = Для початку голосування потрібні щонайменше два активні учасники.
governance-failure-majority = Голосів «за» не більше половини всіх бюлетенів
governance-failure-minimum-yes = Недостатньо голосів «за»
governance-failure-none = Причину не вказано
governance-failure-quorum = Кворум не досягнуто
governance-failure-two-thirds = Голосів «за» менше двох третин голосів без утримань
governance-history-header = ─── Останні результати ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
# $arg0 (Number): duration; locale decides the unit form.
governance-hours = { $arg0 ->
        [one] { $arg0 } година
        [few] { $arg0 } години
        [many] { $arg0 } годин
       *[other] { $arg0 } години
    }
governance-inactive-removal = { $arg0 } автоматично залишив посаду модератора після 180 днів без входу.
governance-invalid-choice = Виберіть yes, no або abstain.
governance-invalid-duration = Неправильний термін. Укажіть, наприклад, 30m, 12h або 3d.
governance-kind-appointment = Призначення
governance-kind-ban = Блокування
governance-kind-removal = Усунення
governance-legacy-ban-kick = Попереднє рішення спільноти забороняє вам вхід. Строк: { $arg0 }. Причина: { $arg1 }
governance-menu-actions = Дії
governance-menu-back = Назад
governance-menu-cancel = Скасувати
governance-menu-confirm = Підтвердити
governance-menu-confirm-nominate = Почати голосування за ваше призначення модератором?
governance-menu-confirm-resign = Піти з посади модератора?
governance-menu-confirm-sponsor = Підтримати це подання про усунення?
governance-menu-confirm-withdraw = Відкликати свою заявку в модератори?
governance-menu-find-player = Знайти іншого гравця
governance-menu-history = Історія голосувань
governance-menu-loading = Завантаження стану врядування…
governance-menu-new-petition = Почати подання про усунення
governance-menu-nominate = Подати заявку в модератори
governance-menu-nomination-open = Голосування за ваше призначення триває
governance-menu-pause = Призупинити підвищення новачка
governance-menu-pause-appeal = Шлях оскарження
governance-menu-pause-duration = Термін (наприклад, 2d)
governance-menu-pause-reason = Причина
governance-menu-petition-row = { $arg0 } · підтримка { $arg1 }/{ $arg2 }
governance-menu-petitions = Подання про усунення
governance-menu-player-name = Ім’я гравця
governance-menu-players = Керування гравцями
governance-menu-recent-results = Останні результати голосувань
governance-menu-refresh = Оновити
governance-menu-resign = Піти з посади модератора
governance-menu-resume = Зняти паузу підвищення
governance-menu-resume-reason = Причина зняття
governance-menu-staff-only = Керувати гравцями можуть лише модератори й куратори.
governance-menu-status = Ваш стан
governance-menu-target = Ім’я модератора
governance-menu-title = Управління спільнотою
governance-menu-votes = Відкриті голосування
governance-menu-withdraw = Відкликати заявку
# $arg0 (Number): duration; locale decides the unit form.
governance-minutes = { $arg0 ->
        [one] { $arg0 } хвилина
        [few] { $arg0 } хвилини
        [many] { $arg0 } хвилин
       *[other] { $arg0 } хвилини
    }
governance-no-active-ban = Немає чинного блокування
governance-no-active-pause = Немає чинного призупинення підвищення
governance-nomination-withdrawn = Заявку відкликано. Результат буде оголошено автоматично.
governance-none = Немає
governance-not-member = Не учасник
governance-not-moderator = Зараз ви не модератор.
governance-operation-error = Не вдалося виконати дію. Спробуйте ще раз.
governance-own-choice-closed = Ваш вибір: { $arg0 }
governance-own-choice-open = Ваш вибір: { $arg0 }; до завершення його можна змінити.
governance-own-not-voted = Ви мали право голосу, але не голосували.
governance-own-not-voted-hint = Ви ще не голосували. Відкрийте «Врядування» й оберіть це голосування.
governance-own-not-voted-open = Ви ще не проголосували.
governance-own-not-voter = Вас немає у списку виборців цього голосування.
governance-own-voted = Ви проголосували
governance-pause-cleared = Призупинення підвищення гравця { $arg0 } знято.
governance-pause-details = Призупинення: { $arg0 } · До { $arg1 } · Оскарження: { $arg2 }
governance-pause-fields-required = Укажіть причину та спосіб оскарження.
governance-pause-not-active = У гравця { $arg0 } немає чинного призупинення підвищення.
governance-pause-notice = Ваше підвищення призупинено. Причина: { $arg0 } · До { $arg1 } · Оскарження: { $arg2 }
governance-pause-set = Підвищення гравця { $arg0 } призупинено до { $arg1 }.
governance-petition-already-sponsored = Ви вже підтримали це усунення ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · Підтримка { $arg1 }/{ $arg2 } · До { $arg3 }
governance-petition-started = Запит на усунення { $arg0 }: підтримка { $arg1 }/{ $arg2 }; до { $arg3 }.
governance-petitions-header = ─── Запити на усунення ───
governance-player-first-join = Перший вхід: { $arg0 }
governance-player-header = ─── { $arg0 } · Профіль управління ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Останній вхід: { $arg0 }
governance-player-member-since = Учасник з: { $arg0 }
governance-player-never-joined = Гравець { $arg0 } ще не заходив на цей сервер.
governance-promotion-account-age = Від першого входу минуло щонайменше 3 дні
governance-promotion-header = ─── Умови підвищення ───
governance-promotion-play-days = Корисна гра щонайменше у 2 дні (зараз { $arg0 })
governance-promotion-playtime = Щонайменше 8 годин корисної гри (зараз { $arg0 })
governance-resign-failed = Не вдалося піти у відставку. Зверніться до опікуна.
governance-resigned = { $arg0 } пішов з посади модератора.
governance-result-announcement = Голосування #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = Не схвалено ({ $arg0 })
governance-result-open = Триває
governance-result-passed = Схвалено
governance-result-tally = { $arg0 } · Виборців { $arg1 }, взяли участь { $arg2 }, за { $arg3 }, проти { $arg4 }, утримались { $arg5 }
governance-result-terminated = Завершено ({ $arg0 })
governance-resume-notice = Призупинення вашого підвищення знято. Причина: { $arg0 }
governance-resume-reason-required = Укажіть причину зняття призупинення.
governance-role-lookup-error = Не вдалося перевірити статус модератора. Зверніться до опікуна.
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } секунда
        [few] { $arg0 } секунди
        [many] { $arg0 } секунд
       *[other] { $arg0 } секунди
    }
governance-storage-error = Помилка даних управління. Зверніться до опікуна.
governance-target-not-moderator = { $arg0 } зараз не модератор.
governance-termination-inactive = Модератор пішов після тривалої відсутності
governance-termination-ineligible = Кандидат утратив статус активного учасника
governance-termination-resigned = Модератор пішов у відставку
governance-termination-unknown = Причину не вказано
governance-termination-withdrew = Кандидат відкликав заявку
governance-vote-electorate = Виборців { $arg0 } · Кворум { $arg1 }
governance-vote-finalized = Завершено: { $arg0 }
governance-vote-header = ─── Голосування #{ $arg0 } ───
governance-vote-in-progress = Триває · Взяли участь { $arg0 }; докладні підсумки після закриття.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · До { $arg3 } · Виборців { $arg4 } · Участь { $arg5 } · Кворум { $arg6 }{ $arg7 }
governance-vote-period = Початок { $arg0 } · Кінець { $arg1 }
governance-vote-started = Голосування «{ $arg1 }» #{ $arg0 } почалося: { $arg2 }; виборців { $arg3 }, кворум { $arg4 }, до { $arg5 }. Докладніше в меню «Врядування».
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Поточні таємні голосування ───

## World · Plots

plot-access-removed-for-you = Ваш доступ до ділянки { $arg0 } вилучено.
plot-admin-created = Ділянку { $arg0 } зареєстровано для { $arg1 } · { $arg2 }
plot-allowed = увімкнено
plot-area-changed = Межі змінилися. Оновіть і підтвердьте знову.
plot-area-confirm = Замінити межі ділянки «{ $arg0 }» виділенням ({ $arg1 ->
        [one] { $arg1 } будівельна комірка
        [few] { $arg1 } будівельні комірки
        [many] { $arg1 } будівельних комірок
       *[other] { $arg1 } будівельної комірки
    })? Споруди залишаться.
plot-area-hint = //pos1 → //pos2 → зберегти. Кути можна вибрати дерев’яною сокирою; додати/відняти для кількох областей.
plot-area-replace-save = Замінити межі та зберегти
plot-area-save = Зберегти межі
plot-area-saved = Межі «{ $arg0 }» збережено.
plot-arrival-removed = Точку прибуття видалено.
plot-arrival-set = Точку прибуття збережено.
plot-arrival-text-saved = Заголовок прибуття збережено.
plot-atmosphere-saved = Налаштування оточення збережено.
plot-back = Назад
plot-board-auto-notify-result = Сповіщення заплановано для { $arg0 ->
        [one] { $arg0 } гравця
        [few] { $arg0 } гравців
        [many] { $arg0 } гравців
       *[other] { $arg0 } гравця
    }.
plot-board-auto-registered-text = Ділянку «{ $arg0 }» зареєстровано. Зовнішні координати забудованих клітин: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Запис { $arg0 }: { $arg1 } { $arg2 }, { $arg3 }, { $arg4 } — { $arg5 }
plot-board-empty = Записів спільноти поки немає.
plot-board-header = Оголошення та відгуки (останні { $arg0 }; докладніше: /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Оголошення
plot-board-kind-objection = Заперечення
plot-board-menu = Дошка спільноти
plot-board-notice-alert = { $arg0 } опублікував оголошення: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } подав заперечення: { $arg1 } · { $arg2 }
plot-board-rate-limited = Ліміт публікацій досягнуто. Спробуйте пізніше.
plot-board-record-invalid = Запис не знайдено, уже вирішено або дія недоступна.
plot-board-registered-alert = { $arg0 }: реєстрація ділянки «{ $arg1 }»: { $arg2 }
plot-board-ruling-alert = Щодо заперечення { $arg0 } ухвалено рішення: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Рішення: { $arg0 }
plot-board-saved = Запис { $arg0 } збережено.
plot-board-status-elapsed = Опубліковано щонайменше 72 години тому
plot-board-status-open = Відкрито
plot-board-status-ruled = Рішення ухвалено
plot-board-status-withdrawn = Відкликано
plot-board-text-invalid = Опис: 12–1000 символів; рішення: щонайменше 8.
plot-board-unavailable = Записи не завантажилися. Спробуйте пізніше або відкрийте https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } відкликав заперечення: { $arg1 } · { $arg2 }
plot-board-write-failed = Не вдалося оновити дошку. Спробуйте пізніше.
plot-command-blocked = Захист ділянки заблокував команду. Використовуйте /fill або /setblock безпосередньо на своїй ділянці; складні команди /execute має перевірити адміністрація.
plot-command-description = Керувати ділянками й громадськими проєктами
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } створений · { $arg1 }
        [feminine] { -plot } { $arg0 } створена · { $arg1 }
       *[other] { -plot } { $arg0 } створене · { $arg1 }
    }
plot-created-for-you = Для вас зареєстровано ділянку { $arg0 }. Реєстрація: { $arg1 }.
plot-deleted = Ділянку звільнено; споруда залишилася.
plot-denied = вимкнено
plot-empty-summary = Тут немає ділянки.
plot-error-admin-owner-only = Зміна учасників із повноваженнями керування вимагає керування учасниками та правами.
plot-error-area-disconnected = Ділянка має бути однією зв’язною областю.
plot-error-args = Неправильні або неповні аргументи. Використайте /plot help.
plot-error-arrival-location = Станьте на безпечну підлогу в забудованій частині проєкту.
plot-error-arrival-missing = Точку прибуття не задано.
plot-error-arrival-private = Ви не можете відвідати цей приватний проєкт.
plot-error-arrival-text = Недійсний заголовок. Один рядок; до 32 видимих символів у заголовку та 64 у підзаголовку.
plot-error-arrival-unsafe = Точка прибуття недоступна або більше не є безпечною.
plot-error-busy = Дані ділянки зберігаються. Повторіть пізніше; операцію не надіслано.
plot-error-flag = Невідоме право. Оберіть дію зі списку прав.
plot-error-id = ID ділянки не знайдено або він неоднозначний. Використайте /plot list.
plot-error-member-missing = Цей гравець уже не учасник.
plot-error-member-required = Нові гравці можуть переглядати ділянки та подавати заперечення. Для створення проєкту потрібен статус учасника.
plot-error-name = Назва проєкту має містити від 1 до 48 символів.
plot-error-notice-text = Оголошення допускають до { $arg0 ->
        [one] { $arg0 } символу
        [few] { $arg0 } символів
        [many] { $arg0 } символів
       *[other] { $arg0 } символу
    } без обмеження кількості чи довжини рядків; керувальні символи заборонені.
plot-error-overlap = Область будівництва перетинається з іншою ділянкою.
plot-error-owner-member = власник ділянки · Права групи
plot-error-owner-only = вимкнено · Керування
plot-error-permission-changed = Права, учасники або успадковані правила змінилися. Перегляньте зміни знову.
plot-error-permission-grant = Не можна надати повноваження керування, яких у вас немає.
plot-error-permission-owner-scope = Права власника можуть змінювати лише власник ділянки, власник батьківської ділянки або персонал сервера.
plot-error-player-unknown = Гравця не знайдено. Перевірте ім'я або UUID.
plot-error-release-owner-only = вимкнено · Видаляти ділянку
plot-error-selection = Позначте два кути командами //pos1 і //pos2.
plot-error-selection-changed = Виділення змінилося. Підтвердьте знову.
plot-error-staff-only = Лише модератори або хранителі можуть керувати ділянками інших гравців або реєструвати їх.
plot-error-storage = Помилка операції з ділянкою. Повторіть пізніше або зверніться до адміністрації.
plot-error-subplot-arrival = Спочатку перенесіть точку прибуття батьківської ділянки за межі вибору.
plot-error-subplot-children = Звільніть дочірні ділянки перед звільненням батьківської.
plot-error-subplot-depth = Дозволено лише один рівень дочірніх ділянок.
plot-error-subplot-disconnected = Вибрані будівельні комірки не з’єднані. Розділіть кожну частину окремо.
plot-error-subplot-overlap = Вибрана область перетинається з іншою дочірньою ділянкою.
plot-error-subplot-own-arrival = Залиште точку прибуття в новій області або спочатку перенесіть її.
plot-error-subplot-owner = У вас немає прав на цю операцію з підділянкою.
plot-error-subplot-parent = Позначте обидва кути в наявних будівельних комірках батьківської ділянки.
plot-error-subplot-supervisor-member = Власник батьківської ділянки вже має тут нагляд і не потребує ролі учасника.
plot-error-time-format = Введіть час у форматі HH:mm (00:00–23:59).
plot-error-transfer-owner-only = вимкнено · Передавати володіння
plot-error-transfer-self = Ви вже володієте цією ділянкою.
plot-flag-set = { $arg0 }: { $arg1 }.
plot-help = Довідка щодо ділянок
plot-here = Тут: { $arg0 } · { $arg1 }
plot-invited = Доступ для { $arg0 } надано.
plot-item-summary = Забудована площа { $arg0 } блоків² · учасників: { $arg1 }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } блок²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Не надіслано
plot-member-draft-reset = Очистити налаштування категорії
plot-member-draft-role = Роль: { $arg0 }
plot-member-draft-submit = Переглянути й підтвердити
plot-member-draft-title = Чернетка учасника · { $arg0 }
plot-member-permissions = Права
plot-member-removed = Учасника проєкту видалено.
plot-menu-access = Доступ і учасники
plot-menu-access-by-settings = за налаштуваннями доступу
plot-menu-admin = Додати керівника ділянки
plot-menu-arrival = Точка прибуття
plot-menu-arrival-subtitle = Підзаголовок (MiniMessage; необов'язково)
plot-menu-arrival-text = Заголовок прибуття
plot-menu-arrival-title = Заголовок (MiniMessage; порожньо — назва проєкту)
plot-menu-atmosphere = Оточення
plot-menu-clear = Ясно
plot-menu-collaborator = Додати співавтора
plot-menu-confirm-delete = Видалити реєстрацію { $arg0 }? Захист, права учасників і налаштування буде видалено без можливості скасування. Споруди залишаться.
plot-menu-confirm-subplot-transfer = Передати { $arg0 } гравцеві { $arg1 }? Власник батьківської ділянки збереже нагляд.
plot-menu-confirm-transfer = Передати керування ділянкою { $arg0 } гравцеві { $arg1 }? Ви втратите доступ; авторство робіт не зміниться.
plot-menu-confirm-withdraw = Відкликати цей відгук? Позначка про відкликання залишиться публічною.
plot-menu-create = Створити { -plot(case: "accusative") }
plot-menu-create-for-player = Зареєструвати ділянку гравцю
plot-menu-custom-time = Власний час
plot-menu-delete = Видалити реєстрацію ділянки
plot-menu-description = Опис
plot-menu-feedback = Заперечити проти цього оголошення
plot-menu-find-record = Знайти запис
plot-menu-flag-container = Відкривати контейнери
plot-menu-flag-entity = Взаємодіяти із сутностями
plot-menu-follow-parent = Успадковувати від батьківської
plot-menu-follow-world = Як у світі
plot-menu-group-admin = керівник ділянки
plot-menu-group-collaborator = Співавтори
plot-menu-group-member = Доступ учасників
plot-menu-group-newcomer = Доступ новачків
plot-menu-group-owner = власник ділянки
plot-menu-manage = Керувати ділянками гравців
plot-menu-managed-empty = Цей гравець ще не має ділянок.
plot-menu-managed-title = Ділянки гравця { $arg0 }
plot-menu-members = Учасники ({ $arg0 })
plot-menu-my-access = Ваш доступ: { $arg0 } · { $arg1 }
plot-menu-name = Назва проєкту
plot-menu-owner = Власник: { $arg0 }
plot-menu-parent-supervisor = Власник батьківської ділянки: { $arg0 } · нагляд
plot-menu-player = Ім’я гравця або UUID
plot-menu-project-kind = Тип проєкту
plot-menu-public-destinations = Публічні місця
plot-menu-public-destinations-empty = Публічних проєктів із точкою прибуття поки немає.
plot-menu-rain = Опади
plot-menu-record-id = ID запису; для старих записів введіть повний ID
plot-menu-related-notice = Відкрити пов’язане повідомлення
plot-menu-remove-arrival = Видалити точку прибуття
plot-menu-remove-member = Видалити учасника
plot-menu-rename = Перейменувати проєкт
plot-menu-rule = Записати рішення
plot-menu-ruling = Поясніть рішення (8–1000 символів)
plot-menu-set-arrival = Установити точку прибуття тут
plot-menu-set-role = Призначити роль { $arg0 }
plot-menu-settings = Налаштування проєкту
plot-menu-submit = Застосувати
plot-menu-subplot-create = Створити підділянку
plot-menu-subplot-of = Частина проєкту { $arg0 }
plot-menu-subplot-parent = До батьківської ділянки: { $arg0 }
plot-menu-subplots = Дочірні ділянки ({ $arg0 })
plot-menu-time = Час
plot-menu-time-input = Час (HH:mm)
plot-menu-transfer = Передати ділянку
plot-menu-visit = Відвідати проєкт
plot-menu-weather = Погода
plot-menu-withdraw = Відкликати відгук
plot-model-fit = Показати всі межі
plot-model-focus = Показати околиці
plot-model-layer-down = Опустити зріз на 1 блок
plot-model-layer-up = Підняти зріз на 1 блок
plot-model-legend = Блакитний: збережено · Зелений/золотий: кути 1/2 · Фіолетовий: межі для збереження · Білий/червоний: ви/напрямок
plot-model-level = Висота зрізу Y ≤ { $arg0 } (лише перегляд)
plot-model-limited = Деталізацію моделі знижено. Сфокусуйтеся на ближній області чи прямокутнику; режим меж дає змогу перевірити краї.
plot-model-north-up = На вигляді згори північ угорі. Для повороту поверніться до тривимірного вигляду.
plot-model-perspective = Тривимірний вигляд
plot-model-preparing = Обчислення меж і перевірка ділянки у фоні… Автоматичне оновлення після завершення.
plot-model-progress = Читання моделі: { $arg0 } %…
plot-model-rotate-left = ↶ Обернути ліворуч
plot-model-rotate-right = ↷ Обернути праворуч
plot-model-show-all = Показати повну висоту
plot-model-slice = Зріз вище мого рівня
plot-model-terrain = Показати споруди та рельєф
plot-model-top = Вигляд згори
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } незавантажений зразок
        [few] { $arg0 } незавантажені зразки
        [many] { $arg0 } незавантажених зразків
       *[other] { $arg0 } незавантаженого зразка
    } (сірий колір). Чанки не завантажуються примусово.
plot-model-wireframe = Лише межі
plot-no-here = Тут немає захищеної { -plot(case: "genitive") }.
plot-notice-board = Дошка ділянки
plot-notice-cleared = Дошку очищено
plot-notice-edit = Змінити дошку
plot-notice-input = До { $arg0 ->
        [one] { $arg0 } символу
        [few] { $arg0 } символів
        [many] { $arg0 } символів
       *[other] { $arg0 } символу
    }; дозволено кілька рядків; залиште порожнім для видалення
plot-notice-read = Прочитати оголошення повністю
plot-notice-saved = Дошку оновлено
plot-permission-all-actions = Усі дії
plot-permission-animal = Догляд, приручення та прив’язування тварин
plot-permission-break = Ламати блоки
plot-permission-bucket = Використовувати відра для рідин та істот
plot-permission-category-build = Будівництво та зміни
plot-permission-category-entity = Сутності
plot-permission-category-interaction = Взаємодія з блоками
plot-permission-category-management = Керування
plot-permission-category-storage = Сховища та предмети
plot-permission-change-children = Зачеплені підділянки: { $arg0 }
plot-permission-change-confirm = Застосувати зміни
plot-permission-change-granted = Надано: { $arg0 }
plot-permission-change-members = Зачеплені учасники: { $arg0 }
plot-permission-change-more = Ще { $arg0 }
plot-permission-change-no-effect = Чинні права не змінюються. Явні налаштування замінять майбутні успадковані правила.
plot-permission-change-none = Немає
plot-permission-change-revoked = Відкликано: { $arg0 }
plot-permission-change-subject = Об’єкт: { $arg0 }
plot-permission-change-title = Підтвердження зміни прав
plot-permission-create-subplot = Створити підділянку
plot-permission-decoration = Розміщувати й змінювати декорації
plot-permission-default-status = Типово ({ $arg0 })
plot-permission-delete = Видаляти ділянку
plot-permission-door = Двері та хвіртки
plot-permission-entity-damage = Завдавати шкоди сутностям
plot-permission-entity-storage = Відкривати сховища сутностей
plot-permission-harvest = Збирати ягоди, плоди ліан і мед
plot-permission-ignite = Запалювати вогонь
plot-permission-item-drop = Викидати предмети
plot-permission-item-pickup = Підбирати предмети із землі
plot-permission-manage-area = Змінювати межі
plot-permission-manage-members = Керувати учасниками
plot-permission-manage-permissions = Керувати дозволами
plot-permission-manage-settings = Змінювати налаштування ділянки
plot-permission-management = Налаштування й власність ділянки
plot-permission-modify = Змінювати блоки та удобрювати
plot-permission-operation = Дія
plot-permission-personal = Особисті права
plot-permission-personal-source = Особисте налаштування
plot-permission-place = Розміщувати блоки й транспорт
plot-permission-reset = Відновити правила
plot-permission-result = Стан
plot-permission-ride = Їздити на транспорті й тваринах
plot-permission-select-state = Виберіть стан: { $arg0 }
plot-permission-sign = Редагувати таблички
plot-permission-source-group = Права групи
plot-permission-source-local = Локальне налаштування
plot-permission-source-owner = Право власника на відновлення
plot-permission-source-parent = Батьківська ділянка
plot-permission-source-role = Правило ролі
plot-permission-state-default = Типово
plot-permission-switch = Кнопки, важелі та плити
plot-permission-trade = Торгувати з селянами
plot-permission-transfer = Передавати володіння
plot-permission-workstation = Використовувати об’єкти й робочі блоки
plot-pos1 = Перша точка
plot-pos2 = Друга точка
plot-private = Приватний проєкт
plot-public = Громадський проєкт
plot-public-set = Тип проєкту: { $arg0 }.
plot-region-adjust = Змінити цей прямокутник
plot-region-apply = Завершити зміну прямокутника
plot-region-cancel = Скасувати зміну прямокутника
plot-region-changed = Виділення змінилося. Виберіть прямокутник знову.
plot-region-delete = Видалити цей прямокутник
plot-region-delete-confirm = Видалити прямокутник №{ $arg0 } із чернетки? Будівлі залишаться. Межі зміняться лише після збереження; дію можна скасувати.
plot-region-edit-hint = Змініть два кути цього прямокутника через //pos1 і //pos2. Інші не зміняться. Завершіть зміну, потім збережіть ділянку.
plot-region-finish-first = Спочатку завершіть або скасуйте зміну поточного прямокутника.
plot-region-open = Керування прямокутниками ({ $arg0 })
plot-region-select-hint = Виберіть прямокутник за номером для фокуса й виділення. Сірі лінії показують поточну сторінку; після об’єднання номери можуть змінитися.
plot-renamed = Проєкт перейменовано.
plot-role-admin = керівник ділянки
plot-role-changed-for-you = Ваша роль на ділянці { $arg0 } тепер: { $arg1 }.
plot-role-collaborator = співавтор
plot-role-owner = власник { -plot(case: "genitive") }
plot-role-parent-owner = власник батьківської ділянки
plot-role-server-staff = адміністрація сервера
plot-selection-add = Додати поточний паралелепіпед
plot-selection-clear = Повторно вибрати дві точки
plot-selection-empty = Позначте два кути командами //pos1 і //pos2.
plot-selection-exit = Завершити редагування / перегляд
plot-selection-fit = Відновити збережені межі
plot-selection-incomplete = Одну точку позначено. Позначте другу; до надсилання ділянка не зміниться.
plot-selection-mode = Вибрати точки у світі
plot-selection-no-redo = Немає зміни виділення для повторення.
plot-selection-no-undo = Немає попередньої зміни виділення для скасування.
plot-selection-open = Змінити межі
plot-selection-redo = Повторити останній крок
plot-selection-replace = Замінити поточним паралелепіпедом
plot-selection-session-hint = Присісти+F або /plot edit для повернення. Вихід через 20 хвилин бездіяльності.
plot-selection-size = Виділення: { $arg0 } × { $arg1 } × { $arg2 } блоків (X/Y/Z)
plot-selection-stopped = Виділення завершено. Чернетки зберігаються окремо для кожної ділянки.
plot-selection-subtract = Відняти поточний паралелепіпед
plot-selection-undo = Скасувати останній крок
plot-subplot-created = Створено дочірню ділянку { $arg0 } · { $arg1 } блоків²
plot-subplot-created-for-you = Тепер ви власник ділянки { $arg0 } у { $arg1 }.
plot-subplot-owner-changed = Ділянка { $arg0 } тепер належить { $arg1 }.
plot-subplot-parent-transferred = Власником батьківської ділянки для вашої ділянки { $arg0 } тепер є { $arg1 }.
plot-subplot-released = Дочірню ділянку { $arg0 } звільнено; її територію й надалі захищає батьківська ділянка.
plot-title = Ділянки
plot-transferred = Ділянку { $arg0 } передано гравцеві { $arg1 }.
plot-transferred-to-you = Ділянку { $arg0 } передано вам. Відправник: { $arg1 }.

## World · Vehicles

vehicle-admin-help = /vehicle — { vehicle-model-title }
    Моделі: model | model help
    Захоплення: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Транспорт: spawn <model> [x y z] | reload
vehicle-command-description = Керувати транспортом із сутностей відображення
vehicle-controls = { $arg0 }: W/S та A/D — керування; колесо — передачі або газ; зміна рук — режим; присідання — вихід. /vehicle help · /vehicle dashboard — панель керування.
vehicle-dash-assisted = Допомога зі стабілізацією
vehicle-dash-automatic = Автоматичний режим
vehicle-dash-contact = Опора на ґрунт / воду: { $arg0 }
vehicle-dash-details = Подробиці
vehicle-dash-down = Знизити передачу −
vehicle-dash-drive = Керування
vehicle-dash-energy = Витрата { $arg0 } од./хв · запас ходу ≈ { $arg1 } хв / { $arg2 } км
vehicle-dash-engine-start = Запустити двигун
vehicle-dash-engine-stop = Зупинити двигун
vehicle-dash-fuel = Пальне { $arg0 } / { $arg1 } од. · { $arg2 } %
vehicle-dash-gear = Передача { $arg0 }
vehicle-dash-gearbox = Коробка передач
vehicle-dash-handbrake = Паркування / ручне гальмо: { $arg0 }
vehicle-dash-heading = Курс { $arg0 }°
vehicle-dash-health = Стан { $arg0 } % · пасажири { $arg1 } / { $arg2 }
vehicle-dash-input = Газ { $arg0 } % · гальмо { $arg1 } % · кермо { $arg2 } %
vehicle-dash-manual = Ручний режим
vehicle-dash-mode-switch = Змінити режим: { $arg0 }
vehicle-dash-position = Початок координат { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Центрувати панель
vehicle-dash-rpm = Оберти { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Швидкість { $arg0 } км/год · межа { $arg1 }
vehicle-dash-throttle-down = Газ −5 %
vehicle-dash-throttle-up = Газ +5 %
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Без допомоги
vehicle-dash-unavailable = Керування недоступне; відкрийте панель із місця водія.
vehicle-dash-up = Підвищити передачу +
vehicle-dash-vertical = Висота { $arg0 } м · вертикальна швидкість { $arg1 } м/с
vehicle-error = Помилка операції з транспортом: { $arg0 }
vehicle-group-changed = Структура групи змінилася. Виберіть її заново перед імпортом.
vehicle-group-invalid = У групі є непідтримувані, керовані, відсутні чи віддалені сутності. Усі мають бути в цьому світі в межах 32 блоків.
vehicle-group-limit = Можна імпортувати не більш як 128 елементів відображення та 256 учасників групи.
vehicle-group-not-found = Групу не знайдено. Виберіть групу, наведіться на сутність відображення блока чи предмета або вкажіть UUID сутності.
vehicle-group-selected = Вибрано групу: { $arg0 ->
        [one] { $arg0 } сутність відображення
        [few] { $arg0 } сутності відображення
        [many] { $arg0 } сутностей відображення
       *[other] { $arg0 } сутності відображення
    } · корінь { $arg1 }
vehicle-help = /vehicle — Керування транспортом
    Посадка: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Рух: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    Панель: hud [on/off] | dashboard [drive/gearbox/details/off]
    Обслуговування: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } вуз. · тяга { $arg1 } % · курс { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } км/год · висота { $arg1 } м · підйом { $arg2 } м/с · газ { $arg3 } % · { $arg4 }
vehicle-hud-status = Пальне { $arg0 } % · стан { $arg1 } % · { $arg2 }
vehicle-model-add = Додати компонент
vehicle-model-add-here = Додати опору біля моїх ніг
vehicle-model-anchor = Розмістити початок перегляду тут, за напрямком погляду
vehicle-model-animation = Анімація
vehicle-model-apply = Застосувати / підтвердити
vehicle-model-auto = Перерахувати межі зіткнень
vehicle-model-block = Блок
vehicle-model-center-here = Установити центр мас біля моїх ніг
vehicle-model-center-look = Установити центр мас на поверхню під прицілом
vehicle-model-check = Перевірити структуру та параметри
vehicle-model-checked = Структура й параметри правильні. Під час збереження також перевіряються дані відображення, наявний транспорт і зіткнення зі світом.
vehicle-model-clone = Клонувати модель
vehicle-model-colliders = Області зіткнень
vehicle-model-copy = Копіювати й змінити (копії сидінь — пасажирські)
vehicle-model-corner-cancel = Скасувати незавершену область зіткнень
vehicle-model-corner-first = Позначити перший кут біля моїх ніг
vehicle-model-corner-first-look = Позначити перший кут на поверхні під прицілом
vehicle-model-corner-hint = Перший кут позначено. Перейдіть до протилежного кута з урахуванням висоти та введіть /vehicle model collider corner2. Область вирівняна за осями моделі.
vehicle-model-corner-required = Спочатку позначте перший кут: /vehicle model collider corner1.
vehicle-model-corner-second = Використати ноги як протилежний кут і додати область
vehicle-model-corner-second-look = Використати поверхню під прицілом як протилежний кут
vehicle-model-create = Створити { -vehicle-model(case: "accusative") }
vehicle-model-delete = Видалити модель
vehicle-model-delete-warning = Видалити модель назавжди? Не можна, доки існує транспорт, що її використовує.
vehicle-model-deleted = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } видалений
        [feminine] { -vehicle-model } видалена
       *[other] { -vehicle-model } видалене
    }
vehicle-model-discard = Відкинути чернетку
vehicle-model-discard-warning = Відкинути всі незбережені зміни? Цю дію не можна скасувати.
vehicle-model-discarded = Чернетку відкинуто
vehicle-model-draft = Незбережена чернетка
vehicle-model-driver = Місце водія
vehicle-model-driver-here = Додати місце водія біля моїх ніг
vehicle-model-edit = Змінити / продовжити чернетку
vehicle-model-exit = Місце виходу / точне налаштування
vehicle-model-group = Додати групу відображення Axiom
vehicle-model-help = /vehicle model — { vehicle-model-title }
    Моделі: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Зміни: set <field> <value...> | part/seat/collider/support — Tab — операції та аргументи
    Чернетка: preview [off] | undo | redo | save | cancel
    У світі: anchor | pick | here | look | snap [on/off] | move <index> <dx dy dz>
    { vehicle-model-position-hint }
    seat position/exit | part position | support add/set | collider position: here / look
    collider corner1/corner2 [look] | collider cancel — { vehicle-model-corner-hint }
    Інші дії: check | apply — { vehicle-model-save-continue }
vehicle-model-here = Використати положення біля моїх ніг
vehicle-model-history-empty = У цьому напрямку історії немає змін.
vehicle-model-import = Імпортувати групу відображення Axiom
vehicle-model-item = Предмет
vehicle-model-library = Збережені моделі · чернетки залишаються в пам’яті до збереження
vehicle-model-look = Розмістити прицілюванням (меню закриється; ПКМ — підтвердити)
vehicle-model-look-missing = Поверхню не знайдено. Наведіться на блок світу або елемент особистого перегляду в межах 32 блоків.
vehicle-model-manual = Увести локальні координати
vehicle-model-options = Параметри
vehicle-model-parameters = Параметри фізики та двигуна
vehicle-model-parts = Елементи відображення
vehicle-model-passenger-here = Додати пасажирське місце біля моїх ніг
vehicle-model-pick = Змінити елемент перегляду під прицілом
vehicle-model-pick-hint = Відкрийте попередній перегляд і наведіться на блок чи предмет або спочатку виберіть компонент у редакторі.
vehicle-model-place-cancelled = Розміщення скасовано: минув час, змінився стан редактора або виконано скасування.
vehicle-model-place-hint = Розміщення активне 30 секунд. Наведіться на блок чи перегляд; ПКМ основною рукою — підтвердити, присідання + ПКМ — скасувати.
vehicle-model-position = Положення / точне налаштування
vehicle-model-position-hint = Локальні X/Y/Z · here — біля ніг, look — на поверхні під прицілом · сітка використовує крок налаштування.
vehicle-model-preview = Особистий попередній перегляд
vehicle-model-preview-failed = Попередній перегляд недоступний; чернетку збережено: { $arg0 }
vehicle-model-preview-hint = Особистий перегляд увімкнено. Видаляється через п’ять хвилин без редагування; /vehicle model preview off для закриття.
vehicle-model-redo = Повторити скасовану зміну
vehicle-model-remove = Видалити компонент
vehicle-model-remove-warning = Видалити компонент? Наступні індекси зсунуться; дію можна скасувати.
vehicle-model-rotate = Повернути компонент
vehicle-model-rotate-hint = Відносний поворот: X — тангаж, Y — рискання, Z — крен. Положення не змінюється.
vehicle-model-save = Перевірити та зберегти
vehicle-model-save-continue = Зберегти та продовжити
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } збережений
        [feminine] { -vehicle-model } збережена
       *[other] { -vehicle-model } збережене
    }
vehicle-model-scale = Масштабувати компонент
vehicle-model-scale-all = Масштаб усіх осей × { $arg0 }
vehicle-model-scale-hint = Множення або ділення довжин локальних осей, а не розмірів габаритів у світі.
vehicle-model-seats = Сидіння та виходи
vehicle-model-selected = Імпортувати вибрані елементи
vehicle-model-size = Змінити розмір області зіткнень
vehicle-model-size-hint = Повні ширина, висота й довжина; центр залишається на місці.
vehicle-model-snap-off = Прив’язка до сітки: вимкнена
vehicle-model-snap-on = Прив’язка до сітки: увімкнена
vehicle-model-step = Змінити крок налаштування
vehicle-model-supports = Точки опори на ґрунт
vehicle-model-text = Текст
vehicle-model-title = Керування моделями транспорту
vehicle-model-tools = Інструменти у світі
vehicle-model-transform-step = Змінити крок налаштування
vehicle-model-undo = Скасувати останню зміну
vehicle-result = Транспорт: { $arg0 }
vehicle-selection-info = Вибрано: { $arg0 ->
        [one] { $arg0 } сутність відображення
        [few] { $arg0 } сутності відображення
        [many] { $arg0 } сутностей відображення
       *[other] { $arg0 } сутності відображення
    } · група { $arg1 } · початок { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Зіткнення / перешкода
vehicle-status-damage = Критичне пошкодження транспорту
vehicle-status-disabled = Транспорт виведено з ладу
vehicle-status-engine-off = Двигун вимкнено
vehicle-status-engine-on = Двигун працює
vehicle-status-gear-limit = Досягнуто межі керування
vehicle-status-grounded = Судно на мілині
vehicle-status-low-fuel = Мало пального
vehicle-status-manual-only = Спочатку ввімкніть ручний режим
vehicle-status-no-fuel = Немає пального
vehicle-status-overrev = Зниження передачі перевищить допустимі оберти
vehicle-status-shifting = Перемикання передачі
vehicle-status-stall = Двигун заглух
vehicle-status-stop-first = Зупиніться перед зміною напрямку або паркуванням
vehicle-value-off = Вимкнено
vehicle-value-on = Увімкнено

## World · Elevators, regeneration and spawn

async-regen-already-running = Відновлення вже виконується; використайте /regen status або /regen cancel
async-regen-apply-expired = Немає дійсного плану; спочатку виконайте /regen preview
async-regen-apply-plan = Застосувати збережений план
async-regen-apply-started = Застосовується { $arg0 } до збереженого виділення: { $arg1 }
async-regen-awaiting-apply = План очікує застосування: { $arg0 } · { $arg1 } · спливає через { $arg2 ->
        [one] { $arg2 } секунду
        [few] { $arg2 } секунди
        [many] { $arg2 } секунд
       *[other] { $arg2 } секунди
    }
async-regen-block-preview-progress = перегляд блоків · перевірено чанків: { $arg0 }/{ $arg1 } · змін: { $arg2 } · блок-сутностей під ризиком: { $arg3 }
async-regen-block-preview-summary = Попередній перегляд: { $arg0 ->
        [one] { $arg0 } зміна блока
        [few] { $arg0 } зміни блоків
        [many] { $arg0 } змін блоків
       *[other] { $arg0 } зміни блока
    } для { $arg2 }; ризик перезапису: { $arg1 ->
        [one] { $arg1 } наявна блок-сутність
        [few] { $arg1 } наявні блок-сутності
        [many] { $arg1 } наявних блок-сутностей
       *[other] { $arg1 } наявної блок-сутності
    }
async-regen-cancelled = Відновлення скасовано. Уже застосовані зміни ({ $arg0 }) залишаться у світі
async-regen-command-description = Асинхронно відновити виділення WorldEdit
async-regen-completed = Відновлення завершено: змінено блоків: { $arg0 }; час: { $arg1 }
async-regen-failed = Відновлення зупинено через внутрішню помилку. Уже застосовані зміни ({ $arg0 }) залишаться у світі
async-regen-filter-all = усі типи блоків
async-regen-invalid-blocks = Невідомі типи або не блоки: { $arg0 }
async-regen-not-running = У вас немає активного відновлення
async-regen-nothing-to-apply = Виділення вже відповідає цьому плану; застосовувати нічого
async-regen-plan-cancel = Скасувати
async-regen-plan-dialog-title = Застосувати план регенерації
async-regen-preview-cancelled = Попередній перегляд скасовано; дані світу не змінювалися
async-regen-preview-started = Перегляд розпочато: виділення { $arg0 ->
        [one] { $arg0 } блок
        [few] { $arg0 } блоки
        [many] { $arg0 } блоків
       *[other] { $arg0 } блока
    } · область { $arg1 ->
        [one] { $arg1 } чанк
        [few] { $arg1 } чанки
        [many] { $arg1 } чанків
       *[other] { $arg1 } чанка
    } · режим { $arg2 }
async-regen-progress = { $arg0 } · заплановано чанків: { $arg1 }/{ $arg2 } · застосовано змін: { $arg3 }
async-regen-safety-note = Поза збереженим виділенням нічого не зміниться. Будівлі всередині можуть бути перезаписані, а Vanilla не розрізняє блоки гравців. Перевірте візуалізацію та зробіть резервну копію перед застосуванням.
async-regen-scope-blocks = перегенерувати блоки, що відповідають { $arg0 }
async-regen-selection-required = Спочатку завершіть виділення WorldEdit
async-regen-skipped-ungenerated = Пропущено ще не створені цільові чанки: { $arg0 ->
        [one] { $arg0 } чанк
        [few] { $arg0 } чанки
        [many] { $arg0 } чанків
       *[other] { $arg0 } чанка
    }. Їх не створювали й не змінювали.
async-regen-start-failed = Не вдалося запустити відновлення; подробиці дивіться в журналі сервера
async-regen-state-applying = застосування змін
async-regen-state-cancelled = скасовано
async-regen-state-completed = завершено
async-regen-state-failed = помилка
async-regen-state-generating = генерація та порівняння чанків
async-regen-state-preparing = підготовка вихідного світу
async-regen-status = { $arg0 } · заплановано чанків: { $arg1 }/{ $arg2 } · застосовано змін: { $arg3 } із { $arg4 }
async-regen-structure-metadata-applied = Перенесено початків структур: { $arg0 }, посилань чанків: { $arg1 }; /locate і залежна від структур поява зможуть їх розпізнати
async-regen-upgrade-apply-progress = { $arg0 } · заплановано чанків: { $arg1 }/{ $arg2 } · застосовано блоків: { $arg3 }, комірок біомів: { $arg4 }
async-regen-upgrade-boundary-note = Комірки біомів на межі ({ $arg0 }) буде пропущено, щоб не змінювати область поза виділенням
async-regen-upgrade-completed = Оновлення світу завершено: змінено блоків — { $arg0 }, комірок біомів — { $arg1 }; час: { $arg2 }
async-regen-upgrade-preview-progress = перегляд · перевірено чанків: { $arg0 }/{ $arg1 } · змін блоків: { $arg2 } · комірок біомів: { $arg3 } · структур: { $arg4 }
async-regen-upgrade-preview-summary = Попередній перегляд: { $arg0 ->
        [one] { $arg0 } зміна блока
        [few] { $arg0 } зміни блоків
        [many] { $arg0 } змін блоків
       *[other] { $arg0 } зміни блока
    }, { $arg1 ->
        [one] { $arg1 } комірка біому
        [few] { $arg1 } комірки біому
        [many] { $arg1 } комірок біомів
       *[other] { $arg1 } комірки біому
    }, { $arg2 ->
        [one] { $arg2 } структура
        [few] { $arg2 } структури
        [many] { $arg2 } структур
       *[other] { $arg2 } структури
    } (повне перенесення метаданих можливе: { $arg3 }); ризик перезапису: { $arg4 ->
        [one] { $arg4 } наявна блок-сутність
        [few] { $arg4 } наявні блок-сутності
        [many] { $arg4 } наявних блок-сутностей
       *[other] { $arg4 } наявної блок-сутності
    }
async-regen-upgrade-scope-biomes = лише біоми
async-regen-upgrade-scope-terrain = рельєф, структури та біоми
async-regen-usage = Використання: /regen preview blocks [типи...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Візуалізація у { $arg0 }: блакитний — межа виділення, помаранчевий — вибірка змінюваних блоків, червоний — блок-сутність під ризиком. Ці частинки бачите лише ви.
async-regen-visualization-selection = Виділення у { $arg0 }: блакитні частинки позначають межу й видимі лише вам.
async-regen-world-edit-required = Для читання виділення потрібен WorldEdit
iron-elevator-down = ↓ Присісти
iron-elevator-up = ↑ Стрибок
spawn-beacon-color-command-description = Перемкнути зміну кольору маяка точки відродження
spawn-beacon-color-disabled = Зміну кольору маяка точки відродження вимкнено
spawn-beacon-color-enabled = Зміну кольору маяка точки відродження ввімкнено
spawn-command-description = Повернутися до точки відродження
spawn-world-missing = Світ точки відродження недоступний

## Entertainment · Music

music-action-add-queue = додати до черги
music-action-play-nearby = відтворити на найближчому програвачі
music-action-play-now = відтворити зараз на цьому програвачі
music-action-take-disc = отримати платівку
music-added-to-queue-rich = Додано до черги: { $music }
music-album = Альбом: { $arg0 }
music-all-in-queue = Усі композиції вже є в черзі
music-artist = Виконавець: { $arg0 }
music-back = Назад
music-browser-left-click = Ліва кнопка
music-browser-right-click = Права кнопка
music-cache-clear-done = Кеш онлайн-музики очищено
music-cache-clear-started = Очищення кешу онлайн-музики...
music-cache-operation-failed = Помилка операції з кешем музики: { $arg0 }
music-cache-status = Кеш онлайн-музики: файлів — { $arg0 }, { $arg1 } / { $arg2 }
music-cached = У кеші - готово до відтворення
music-clear-queue = Очистити чергу
music-clear-queue-done = Чергу очищено ({ $arg0 ->
        [one] { $arg0 } композиція
        [few] { $arg0 } композиції
        [many] { $arg0 } композицій
       *[other] { $arg0 } композиції
    })
music-command-description = Локальна, онлайн- і NBS-музика для програвача
music-disc-left = ЛКМ: узяти платівку
music-disc-right = ПКМ: відтворити на найближчому програвачі
music-duplicate-in-queue = Ця композиція вже є в черзі
music-duration = Тривалість: { $arg0 }
music-empty-library = Музична бібліотека порожня
music-empty-library-description = Немає доступних локальних аудіофайлів або композицій NBS
music-empty-nbs = Композицій NBS не знайдено
music-empty-nbs-description = Перейдіть до всієї музики або додайте файл NBS
music-empty-playlist = У цьому плейлісті немає доступних композицій
music-empty-playlist-description = Переконайтеся, що плейліст публічний і вибрано правильну платформу
music-empty-search = Відповідних композицій не знайдено
music-empty-search-description = Спробуйте інший запит або перевірте стан користувацьких джерел
music-finished = Завершено
music-format = Формат: { $arg0 }
music-inventory-full = Інвентар заповнений. Не вдалося видати платівку
music-jukebox-audible-field-conflict = Інший програвач відтворює іншу пісню в зоні чіткої чутності, що перетинається
music-jukebox-music-mode = Режим музики
music-jukebox-play-order = Порядок відтворення
music-jukebox-range = Радіус відтворення
music-jukebox-range-value = Зараз: { $arg0 ->
        [one] { $arg0 } блок
        [few] { $arg0 } блоки
        [many] { $arg0 } блоків
       *[other] { $arg0 } блока
    }
music-jukebox-rhythm-mode = Режим ритму
music-jukebox-too-far = Ви надто далеко від програвача (не більше { $arg0 ->
        [one] { $arg0 } блока
        [few] { $arg0 } блоків
        [many] { $arg0 } блоків
       *[other] { $arg0 } блока
    })
music-jukebox-tp-hover = Натисніть, щоб телепортуватися до програвача
music-jukebox-unavailable = Програвач видалено або його чанк недоступний
music-jukebox-usage-mode = Режим програвача
music-jukebox-volume = Гучність програвача
music-jukebox-volume-value = Зараз: { $arg0 }%
music-library-button = Музична бібліотека
music-library-random-mode = Випадково з медіатеки
music-library-random-mode-desc = Відтворювати випадкові композиції з усієї медіатеки без негайного повтору
music-loading-title = Завантаження
music-menu-nbs-title = Музика NBS - сторінка { $arg0 }/{ $arg1 }
music-menu-playlist-title = Список відтворення: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = Пошук NBS: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-title = Пошук: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-title = Платівки - сторінка { $arg0 }/{ $arg1 }
music-mode-switched = Вибрано { $arg0 }
music-nearest-jukebox-missing = У радіусі { $arg0 ->
        [one] { $arg0 } блока
        [few] { $arg0 } блоків
        [many] { $arg0 } блоків
       *[other] { $arg0 } блока
    } немає програвача
music-next-page = Наступна сторінка
music-no-disc = Немає платівки
music-no-files = Немає доступних музичних файлів
music-no-search-results-rich = Композицій зі словами «{ $keyword }» не знайдено
music-now-playing-rich = Зараз відтворюється: { $music }; програвач: { $location }
music-online-partial-failure = Помилки джерел: { $arg0 ->
        [one] { $arg0 } запит
        [few] { $arg0 } запити
        [many] { $arg0 } запитів
       *[other] { $arg0 } запиту
    }; показано доступні результати
music-online-rate-limited = Забагато запитів музики. Повторіть через { $arg0 ->
        [one] { $arg0 } секунду
        [few] { $arg0 } секунди
        [many] { $arg0 } секунд
       *[other] { $arg0 } секунди
    }.
music-online-searching = Пошук «{ $arg0 }» у локальних і онлайн-джерелах...
music-original-author = Автор оригіналу: { $arg0 }
music-page-out-of-range = Такої сторінки немає. Остання сторінка: { $arg0 }
music-page-total = Усього: { $arg0 ->
        [one] { $arg0 } композиція
        [few] { $arg0 } композиції
        [many] { $arg0 } композицій
       *[other] { $arg0 } композиції
    }
music-page-usage-desc = Перейти до сторінки
music-partial-results = Помилки джерел: { $arg0 ->
        [one] { $arg0 } запит
        [few] { $arg0 } запити
        [many] { $arg0 } запитів
       *[other] { $arg0 } запиту
    }; показано доступні результати
music-paused = Призупинено
music-plasmo-voice-unavailable = Трек запущено, але ви не можете його почути, оскільки Plasmo Voice не встановлено, не підключено або вимкнено
music-play-next = Відтворити наступну
music-play-random = Відтворити випадкову
music-playback-failed = Не вдалося відтворити «{ $arg0 }». Перевірте джерело або музичний файл і спробуйте ще раз.
music-playback-status = Стан відтворення: { $arg0 }
music-playing = Відтворюється
music-playing-now-rich = Зараз відтворюється: { $music }
music-playlist-import-button = Імпортувати плейліст
music-playlist-import-done = «{ $arg0 }» імпортовано: { $arg1 } з { $arg2 ->
        [one] { $arg2 } композиції
        [few] { $arg2 } композицій
        [many] { $arg2 } композицій
       *[other] { $arg2 } композиції
    }.
music-playlist-import-failed = Не вдалося імпортувати плейліст: { $arg0 }
music-playlist-import-prompt = Виберіть платформу, потім вставте ID або посилання публічного плейліста:
music-playlist-import-usage-desc = Імпортувати публічний плейліст з іншої музичної платформи
music-playlist-importing = Імпортування плейліста...
music-playlist-reference-prompt = Вставте ID або посилання на публічний плейлист
music-prev-page = Попередня сторінка
music-queue-empty = Черга порожня
music-queue-move-down = Перемістити нижче
music-queue-move-up = Перемістити вище
music-queue-remove = Вилучити композицію
music-queue-summary = Черга: { $arg0 ->
        [one] { $arg0 } композиція
        [few] { $arg0 } композиції
        [many] { $arg0 } композицій
       *[other] { $arg0 } композиції
    }
music-queue-summary-description = Сторінка { $arg0 }/{ $arg1 }
music-random-add = Додати випадкову композицію
music-random-added-to-queue-rich = До черги додано випадкову композицію: { $music }
music-random-disc = Випадкова платівка
music-random-disc-got-rich = Отримано випадкову платівку: { $music }
music-random-mode = Випадковий список
music-random-mode-desc = Відтворювати список випадково без негайного повтору тієї самої композиції
music-rank-down = Переміщено нижче
music-rank-up = Переміщено вище
music-reload-done = Перезавантаження завершено: { $arg0 ->
        [one] { $arg0 } локальна композиція
        [few] { $arg0 } локальні композиції
        [many] { $arg0 } локальних композицій
       *[other] { $arg0 } локальної композиції
    }; джерела LX { $arg1 }/{ $arg2 }; підписок перевірено { $arg3 }, оновлено { $arg4 }
music-reload-failed = Не вдалося перезавантажити музику: { $arg0 }
music-reload-issues = Проблеми під час перезавантаження: { $arg0 }
music-reload-partial = Перезавантаження завершено зі збереженням попереднього стану: { $arg0 ->
        [one] { $arg0 } локальна композиція
        [few] { $arg0 } локальні композиції
        [many] { $arg0 } локальних композицій
       *[other] { $arg0 } локальної композиції
    }; джерела LX { $arg1 }/{ $arg2 }; підписок перевірено { $arg3 }, оновлено { $arg4 }
music-reload-started = Перезавантаження музичних файлів і користувацьких джерел...
music-remote-source-error = помилка оновлення: { $arg0 }
music-remote-source-import-done = Підписку на джерело LX імпортовано: { $arg0 }
music-remote-source-import-failed = Не вдалося імпортувати джерело LX: { $arg0 }
music-remote-source-import-started = Імпортування підписки на джерело LX...
music-remote-source-remove-done = Підписку на джерело LX видалено: { $arg0 }
music-remote-source-remove-failed = Не вдалося видалити підписку на джерело LX: { $arg0 }
music-remote-source-remove-not-found = Підписку на джерело LX не знайдено: { $arg0 }
music-remote-source-update-done = Перевірка: { $arg0 ->
        [one] { $arg0 } підписка
        [few] { $arg0 } підписки
        [many] { $arg0 } підписок
       *[other] { $arg0 } підписки
    }; оновлено: { $arg1 }; помилок: { $arg2 }.
music-remote-source-update-failed = Не вдалося оновити джерело LX: { $arg0 }
music-remote-source-update-started = Перевірка імпортованих підписок на джерела LX...
music-remote-source-updated-at = оновлено: { $arg0 }
music-remote-sources-title = Імпортовані підписки на джерела LX: { $arg0 }
music-removed-from-queue-rich = Вилучено з черги: { $music }
music-repeat-one-mode = Повтор однієї композиції
music-repeat-one-mode-desc = Повторювати поточну композицію до ручного перемикання
music-request-error = Помилка запиту: { $arg0 }
music-rhythm-animation-offset = Зсув анімації { $arg0 } мс
music-rhythm-calibration = Калібрування затримки
music-rhythm-calibration-accumulating = Перевіряємо стабільність… продовжуйте грати
music-rhythm-calibration-adapting = Підлаштовуємося під ритм… продовжуйте грати
music-rhythm-calibration-complete = Тест затримки завершено · ритм-гра готова
music-rhythm-calibration-done = Тест затримки завершено · налаштування збережено
music-rhythm-calibration-failed = Звук тесту перервався. Тест безпечно завершено; запустіть його знову
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Клавіатура візуально → Миша візуально
music-rhythm-calibration-listen = Спочатку прослухайте створений ритм один раз · поки не натискайте
music-rhythm-calibration-minecraft-instruction = Дотримуйтеся ритму Minecraft Note Block і натискайте 1–4
music-rhythm-calibration-no-input = Ввід не виявлено. Перевірте клавіші й гучність та продовжуйте грати
music-rhythm-calibration-out-of-range = Виміряна затримка поза підтримуваним діапазоном. Перевірте налаштування звуку
music-rhythm-calibration-plasmo-instruction = Тепер повторюйте: дотримуйтеся ритму Plasmo Voice і натискайте 1–4 на кожен удар
music-rhythm-calibration-plasmo-ready = Тест Minecraft Note Block завершено; спочатку прослухайте той самий ритм через Plasmo Voice
music-rhythm-calibration-pointer-instruction = Натискайте ліву або праву кнопку миші, коли нота перетинає лінію.
music-rhythm-calibration-prompt-confirm = Почати повний тест
music-rhythm-calibration-prompt-confirm-hint = Авто повернення після завершення.
music-rhythm-calibration-prompt-later = Не зараз
music-rhythm-calibration-prompt-reason = Спочатку пройдіть тест затримки.
music-rhythm-calibration-prompt-title = Перед грою потрібен тест затримки
music-rhythm-calibration-realigning = Звуковий потік перервався. Демонстрацію буде повторено, завершені вимірювання збережено
music-rhythm-calibration-required = Перед запуском ритм-гри пройдіть повний тест затримки
music-rhythm-calibration-reset-done = Калібрування затримки скинуто
music-rhythm-calibration-result-hold = Усі тести завершено. Перевірте результат перед автоматичним поверненням
music-rhythm-calibration-stage-minecraft = Нотний блок Minecraft
music-rhythm-calibration-stage-plasmo = Тест звуку Plasmo Voice та вводу
music-rhythm-calibration-stage-pointer = Миша
music-rhythm-calibration-stage-visual = Тест зображення та вводу
music-rhythm-calibration-visual-instruction = Натисніть 1–4, коли нота перетинає лінію.
music-rhythm-calibration-visual-preview = Перегляньте один повний цикл без вводу · почніть після зміни інструкції
music-rhythm-calibration-voice-required = Для повного тесту Plasmo Voice має бути підключено й увімкнено
music-rhythm-chart-preparing = Підготовка повної мапи…
music-rhythm-combo = Комбо ×{ $arg0 }
music-rhythm-difficulty-easy = Легко
music-rhythm-difficulty-expert = Експерт
music-rhythm-difficulty-hard = Складно
music-rhythm-difficulty-normal = Нормально
music-rhythm-exit = Вийти з ритм-гри
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
music-rhythm-get-ready = Приготуйтеся
music-rhythm-go = СТАРТ!
music-rhythm-good = Добре
music-rhythm-great = Чудово
music-rhythm-latency = Затримка { $arg0 } мс
music-rhythm-latency-test = Повний тест затримки
music-rhythm-miss = Промах
music-rhythm-mode-falling = Ноти, що падають
music-rhythm-mode-radial = Радіальний наступ
music-rhythm-mode-spatial-aim = Просторове прицілювання
music-rhythm-network-too-high = Мережева затримка надто висока для чесної оцінки ({ $arg0 } мс; межа { $arg1 } мс)
music-rhythm-network-warning = Мережева затримка нестабільна ({ $arg0 } мс). Сеанс завершиться, якщо вона залишиться вище { $arg1 } мс
music-rhythm-no-beats = У цій пісні не знайдено придатних ритмів
music-rhythm-perfect = Ідеально
music-rhythm-preparing = Зачекайте, поки пісня повністю кешується та буде проаналізовано її ритм
music-rhythm-ready = Повна карта готова — початок із першого удару
music-rhythm-requires-playback = Спочатку увімкніть пісню на цьому програвачі
music-rhythm-reset-calibration = Скинути калібрування затримки
music-rhythm-results = Результати
music-rhythm-score = Рахунок: { $arg0 }
music-rhythm-select-track = Вибрати трек для ритм-гри
music-rhythm-spatial-no-room = Недостатньо вільного місця. Перейдіть у відкритішу зону.
music-rhythm-spatial-slider-hint = Стежте за рухомою зіркою й натискайте кожну точку · не утримуйте
music-rhythm-start-game = Почати гру
music-rhythm-waiting-for-player = Очікування гравця для запуску ритм-гри
music-sample-rate = Частота дискретизації: { $arg0 }
music-search-button = Знайти композицію
music-search-keyword-required = Пошуковий запит не може бути порожнім
music-search-keyword-too-long = Задовгий запит. Ліміт: { $arg0 ->
        [one] { $arg0 } символ
        [few] { $arg0 } символи
        [many] { $arg0 } символів
       *[other] { $arg0 } символа
    }.
music-search-usage-desc = Знайти композиції
music-section-all = Уся музика
music-section-nbs = Музика NBS
music-select-music = Вибрати музику
music-sequential-mode = Повтор списку
music-sequential-mode-desc = Відтворювати список по черзі та починати з початку
music-size = Розмір: { $arg0 }
music-sort-button = Сортування
music-sort-current = Поточне: { $arg0 }
music-sort-default = Типовий порядок
music-sort-most-played = Найчастіше відтворювані
music-sort-recently-played = Нещодавно відтворені
music-sound-settings = Налаштування звуку
music-source-actions = дії: { $arg0 }
music-source-available = доступне
music-source-capabilities = можливості: { $arg0 }
music-source-error = помилка: { $arg0 }
music-source-failures = помилок поспіль: { $arg0 }
music-source-retry = повтор через { $arg0 ->
        [one] { $arg0 } секунду
        [few] { $arg0 } секунди
        [many] { $arg0 } секунд
       *[other] { $arg0 } секунди
    }
music-source-unavailable = недоступне
music-sources-empty = Користувацькі джерела музики LX не знайдено
music-sources-title = Користувацькі джерела музики LX: { $arg0 }
music-stop-eject = Зупинити й вилучити
music-stop-eject-done = Відтворення зупинено, платівку вилучено
music-stopped = Зупинено
music-track-details = Відомості про трек
music-unavailable-disc = Недоступна платівка
music-unavailable-disc-description = Цієї платівки MIK немає в поточному каталозі, але відтворення можна зупинити, а платівку вилучити

## Entertainment · Anniversary

anniversary-accepted = Ви приєдналися до розіграшу. Перший раунд відкриється після 4 годин онлайн поза AFK.
anniversary-admin-audit-action-bonus = Видано додаткову спробу за публікацію споруд
anniversary-admin-audit-action-gift = Подаровано приз події: { $arg0 }
anniversary-admin-audit-action-removal = Попередню участь адміністратора видалено; повернуто: { $arg0 ->
        [one] { $arg0 } приз
        [few] { $arg0 } призи
        [many] { $arg0 } призів
       *[other] { $arg0 } призу
    }.
anniversary-admin-audit-action-unknown = Невідома дія: { $arg0 }
anniversary-admin-audit-empty = Записів аудиту керування ще немає.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Виконавець: { $arg0 } ({ $arg1 }); ціль: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Аудит керування ювілеєм (показано { $arg0 }/{ $arg1 })
anniversary-admin-bonus-cancelled = Завершено подарунковим набором
anniversary-admin-bonus-pending = Видано, ще не розіграно
anniversary-admin-bonus-used = Розіграно
anniversary-admin-boss = Керування ювілеєм · Введіть { $arg0 }, щоб переглянути прогрес
anniversary-admin-commands = Команди: /anniversary winners [сторінка] | /anniversary audit [гравець] | /anniversary bonus <гравець>
anniversary-admin-control = Динамічне керування: випущено { $arg0 }; доступно зараз { $arg1 }; базова ймовірність { $arg2 }; нещодавня швидкість { $arg3 }
anniversary-admin-draw-bonus = Бонус за публікацію споруд
anniversary-admin-draw-detail = { $arg0 }: проведено { $arg1 }; результат { $arg2 }
anniversary-admin-draw-metrics = Ймовірність { $arg0 }; випадкове число { $arg1 }; версія алгоритму { $arg2 }
anniversary-admin-draw-replaced = Замінено подарунковим набором; запас відновлено
anniversary-admin-draw-round = Звичайний раунд { $arg0 }
anniversary-admin-event = Стан події: { $arg0 }; період: { $arg1 }
anniversary-admin-excluded = Адміністратори ювілейної події не можуть брати участь у розіграші. Відкрито огляд керування.
anniversary-admin-none = Немає
anniversary-admin-opportunities = Проведено розіграшів: { $arg0 }; готово зараз: { $arg1 }; отримано спроб: { $arg2 }; перемог/поразок: { $arg3 }
anniversary-admin-participants = Учасників: { $arg0 }
anniversary-admin-player-bag = Остаточна сумка події: { $arg0 }
anniversary-admin-player-bonus = Бонус за споруди: { $arg0 }; видано: { $arg1 }; ким: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Подробиці спроб розіграшу
anniversary-admin-player-not-found = Учасника події не знайдено: { $arg0 }
anniversary-admin-player-outcome = Перемог: { $arg0 }; поразок: { $arg1 }; проведено розіграшів: { $arg2 }; бонус за споруди: { $arg3 }
anniversary-admin-player-progress = Приєднався: { $arg0 }; зарахований онлайн-час: { $arg1 }; проведено звичайних розіграшів: { $arg2 }; готово звичайних спроб: { $arg3 }
anniversary-admin-player-title = Аудит учасника: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Триває
anniversary-admin-state-ended = Завершено
anniversary-admin-state-scheduled = Заплановано
anniversary-admin-stock = Залишок: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | усього { $arg4 }
anniversary-admin-target-excluded = { $arg0 } є адміністратором ювілейної події та не може отримати спробу розіграшу.
anniversary-admin-title = Огляд керування п'ятим ювілеєм
anniversary-admin-winners-empty = Фактично розіграних виграшних результатів ще немає.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · розіграно { $arg3 }
anniversary-admin-winners-title = Список видачі призів (сторінка { $arg0 }/{ $arg1 }, призів: { $arg2 })
anniversary-bag-empty = Виграних призів ще немає
anniversary-bag-item-locked = Сумка події доступна лише для перегляду
anniversary-bag-item-name = Приз до п'ятиріччя · { $arg0 }
anniversary-bag-not-participating = Ви ще не берете участь у події. Спочатку введіть { $arg0 }.
anniversary-bag-title = Сумка події до п'ятиріччя
anniversary-bag-updated = Ваш приз у сумці події. Введіть { $arg0 }, щоб переглянути його.
anniversary-bonus-already-granted = Для { $arg0 } бонус за публікацію споруд уже видано; повторна видача неможлива.
anniversary-bonus-boss-ready = Додатковий розіграш за публікацію споруд готовий · Введіть { $arg0 } для розіграшу
anniversary-bonus-broadcast-won = [Ювілейний розіграш] Бонус за споруди приніс приз: { $arg0 } · { $arg1 }!
anniversary-bonus-draw-not-won = Додатковий розіграш за публікацію споруд: цього разу без призу.
anniversary-bonus-draw-sold-out = Додатковий розіграш за публікацію споруд: приз не видано, бо призовий фонд вичерпано.
anniversary-bonus-draw-won = Додатковий розіграш за публікацію споруд: вітаємо, ви виграли { $arg0 }!
anniversary-bonus-gift-pack-final = Для { $arg0 } остаточним призом уже став подарунковий набір; бонусний розіграш недоступний.
anniversary-bonus-grant-not-active = Нагороду за публікацію споруд не можна видати поза періодом події.
anniversary-bonus-grant-success = Нагороду за публікацію споруд видано гравцеві { $arg0 }.
anniversary-bonus-granted = Ви отримали одну додаткову спробу за публікацію споруд. Введіть /anniversary, щоб розіграти її.
anniversary-bonus-participant-not-found = Учасника події з іменем { $arg0 } не знайдено
anniversary-bonus-status-ready = Нагорода за публікацію споруд: одна додаткова спроба готова.
anniversary-bonus-status-used = Нагорода за публікацію споруд: додаткову спробу використано.
anniversary-boss-completed = Ювілейний розіграш · Обидва раунди завершені
anniversary-boss-gift-pack-final = Подарунковий набір отримано · Введіть { $arg0 }, щоб відкрити сумку події
anniversary-boss-invite = Розіграш до п'ятиріччя · Введіть { $arg0 } для участі
anniversary-boss-progress = Ювілейний розіграш { $arg0 }/{ $arg1 } · Раунд { $arg2 } через { $arg3 }
anniversary-boss-ready = Раунд { $arg0 } готовий · Введіть { $arg1 } для розіграшу
anniversary-broadcast-won = [Ювілейний розіграш] Перемога в раунді { $arg1 }: { $arg0 } · приз: { $arg2 }!
anniversary-cancel = Не зараз
anniversary-command-description = Узяти участь у розіграші до п'ятиріччя сервера
anniversary-confirm = Погодитися й узяти участь
anniversary-confirm-hint = Починає рахувати час онлайн поза AFK
anniversary-delivery-note = Для доставки до материкового Китаю надайте дійсний SF ID, придатний для відправлення із захистом персональних даних. Для доставки за межі материкового Китаю надайте справжнє ім’я одержувача, повну адресу та дійсний номер телефону. Сплатіть належну частку вартості доставки, інакше право на приз буде втрачено.
anniversary-dialog-prizes = Призи: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } та 5 { $arg3 }
anniversary-dialog-time = Період події: { $arg0 }
anniversary-dialog-title = Правила розіграшу до п'ятиріччя
anniversary-draw-not-won = Раунд { $arg0 }: цього разу без призу.
anniversary-draw-sold-out = Раунд { $arg0 }: приз не видано, бо всі призи закінчилися.
anniversary-draw-won = Раунд { $arg0 }: вітаємо, ви виграли { $arg1 }!
anniversary-ended = Розіграш до п'ятиріччя завершено
anniversary-ended-results = Ваші результати розіграшу: { $arg0 }
anniversary-gift-pack-final = Подарунковий набір став вашим остаточним призом. Він замінив і повернув у запас { $arg0 ->
        [one] { $arg0 } інший приз
        [few] { $arg0 } інші призи
        [many] { $arg0 } інших призів
       *[other] { $arg0 } іншого призу
    }; усі подальші розіграші завершено.
anniversary-gift-prize-not-owned = У вас немає цього призу події: { $arg0 }
anniversary-gift-received = { $arg0 } подарував вам { $arg1 }. Приз знаходиться у вашій сумці події.
anniversary-gift-self = Не можна подарувати приз самому собі.
anniversary-gift-success = Приз { $arg0 } подаровано гравцю { $arg1 }.
anniversary-gift-usage = Використання: { $arg0 }
anniversary-not-active = Подія до п'ятиріччя зараз не активна
anniversary-not-started = Подія до п'ятиріччя ще не почалася. Період: { $arg0 }
anniversary-prize-badge = значок із груповим фото до п'ятиріччя
anniversary-prize-gift-pack = подарунковий набір до п'ятиріччя
anniversary-prize-keychain = брелок із логотипом
anniversary-prize-mug = кухоль із логотипом
anniversary-prize-not-won = без призу
anniversary-prize-sold-out = призовий фонд вичерпано
anniversary-rule-1 = 1. Після участі можна розігрувати раз на 4 години, усього двічі. Призи потрапляють у віртуальну сумку події, не більше одного кожного виду. Подарунковий набір лишається єдиним призом і завершує подальші розіграші.
anniversary-rule-2 = 2. Час AFK не зараховується для розіграшу. Заборонено будь-яким способом обходити визначення AFK.
anniversary-rule-3 = 3. Сервер сплачує 80% загальної вартості доставки фізичних призів, але не більше 50 CNY; переможець повністю сплачує залишок після вирахування цієї суми.
anniversary-rule-4 = 4. Для доставки до материкового Китаю необхідно надати дійсний SF ID, придатний для відправлення із захистом персональних даних. Для доставки за межі материкового Китаю необхідні справжнє ім’я одержувача, повна адреса та дійсний номер телефону. Якщо потрібні дані не надано або належну частку вартості доставки не сплачено, переможець втрачає право на приз.
anniversary-rule-5 = 5. Повторна участь через альтернативні акаунти заборонена та призводить до дискваліфікації.
anniversary-rule-6 = 6. Участь означає згоду з усіма правилами вище.
anniversary-rule-7 = 7. Прийняті сайтом споруди дають 0,2 бала за перенесення, 0,5 за переробку та 1 за оригінал; прийняті до події множаться на 0,8, а спільні — на частку вашого внеску та 1,5. За суми щонайменше 2 бали адміністратор вручну видає одну додаткову спробу.
anniversary-second-round-hint = Другий раунд відкриється після 8 годин онлайн поза AFK загалом.
anniversary-status-completed = Ви завершили обидва раунди.
anniversary-status-gift-pack-final = Ви виграли подарунковий набір. Остаточний приз визначено, усі подальші розіграші завершено.
anniversary-status-progress = Завершено: { $arg0 }/{ $arg1 } раундів; дійсний час онлайн: { $arg2 }
anniversary-status-remaining = До раунду { $arg0 } потрібно ще { $arg1 } онлайн поза AFK.
anniversary-status-results = Результати розіграшу: { $arg0 }
anniversary-status-title = Прогрес ювілейного розіграшу

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } уже заблоковано
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Повідомлення про блокування]
ban-command-description = Заблокувати гравця за фіксованим рівнем порушення
ban-database-error = Помилка бази блокувань. Перевірте журнал сервера.
ban-dialog-back = Назад
ban-dialog-cancel = Скасувати
ban-dialog-confirm-action = Заблокувати гравця
ban-dialog-confirm-severity = Рівень
ban-dialog-confirm-title = Підтвердження блокування
ban-dialog-never-joined-confirm-action = Усе одно заблокувати
ban-dialog-never-joined-title = Гравець ніколи не заходив
ban-dialog-never-joined-warning = { $arg0 } ніколи не заходив на цей сервер. Перевірте ім’я, щоб не заблокувати іншого гравця.
ban-dialog-next = Далі
ban-dialog-player-input = Ввести ім’я гравця вручну
ban-dialog-player-manual = Ручне введення
ban-dialog-player-online = Гравці зараз на сервері
ban-dialog-select-title = Вибір гравця
ban-dialog-severity-title = Вибір рівня блокування
ban-history-empty = Історія блокувань для { $arg0 } відсутня.
ban-history-title = Історія блокувань: { $arg0 }
ban-invalid-player = Некоректне ім’я гравця
ban-invalid-severity = Некоректний рівень блокування
ban-kick-appeal = Якщо це помилка, подайте апеляцію в групі QQ 1138627074
ban-kick-server = MIK
ban-kick-title = Вас заблоковано на цьому сервері
ban-not-banned = Для { $arg0 } немає чинного блокування.
ban-pardon-reason-required = Потрібно вказати причину розблокування
ban-permanent = Назавжди
ban-reason-required = Потрібно вказати причину блокування
ban-self = Не можна заблокувати себе
ban-severity-excusable = Можна пробачити (30 днів)
ban-severity-extreme = Надзвичайно серйозне порушення (1500 днів)
ban-severity-minor = Незначне порушення (150 днів)
ban-severity-severe = Серйозне порушення (360 днів)
ban-severity-warning = Попередження (3 дні)
ban-status-active = Діє
ban-status-expired = Строк минув
ban-status-revoked = Знято
ban-success = { $arg0 } заблоковано до { $arg1 }
ban-sync-success = Блокування синхронізовано: чинних { $arg0 } · історичних записів { $arg1 }
ban-system-source = Система
ban-unban-success = Блокування для { $arg0 } знято
ban-unknown-reason = Причину не вказано
ban-update-success = Поле блокування для { $arg0 } оновлено: { $arg1 }
banlist-command-description = Переглянути список блокувань
banlist-copy = Натисніть, щоб скопіювати ім’я гравця
banlist-empty = Заблокованих гравців немає
banlist-expires = Завершення
banlist-invalid-page = Неправильна сторінка. Усього: { $arg0 ->
        [one] { $arg0 } сторінка
        [few] { $arg0 } сторінки
        [many] { $arg0 } сторінок
       *[other] { $arg0 } сторінки
    }.
banlist-next = Далі
banlist-page = Сторінка { $arg0 }/{ $arg1 } · записів: { $arg2 }
banlist-player = Гравець
banlist-previous = Назад
banlist-reason = Причина
banlist-source = { role-moderator }
banlist-title = Список блокувань
maintenance-command-description = Увімкнути, вимкнути або перевірити режим технічного обслуговування
maintenance-set = Режим технічного обслуговування { $arg0 }
maintenance-state-disabled = вимкнено
maintenance-state-enabled = увімкнено
maintenance-status = Режим технічного обслуговування: { $arg0 }
maintenance-unchanged = Режим технічного обслуговування вже { $arg0 }
restriction-entity-too-large-mm = <red>Розмір сутності перевищує безпечне обмеження сервера</red>
restriction-foreign-uuid-mm = <red>Можна використовувати UUID лише приручених вами мобів</red>
restriction-no-kill-e-mm = <red>Команду <white>/kill @e</white> заборонено</red>
restriction-no-newline-mm = <red>Повідомлення не можуть містити переноси рядків</red>
restriction-no-run-command-mm = <red>Повідомлення не можуть спонукати гравців оманою виконувати команди</red>
restriction-no-selector-mm = <red>Ви не маєте дозволу використовувати селектори цілей <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>Ви не маєте дозволу згадувати імена інших гравців у командах</red>
tpsbar-command-description = Показати або приховати панель TPS
tpsbar-hidden = Панель TPS прихована
tpsbar-shown = Панель TPS показана
unban-command-description = Зняти блокування зі збереженням історії
whitelist-already-whitelisted = { $arg0 } уже в білому списку
whitelist-invalid-name = Неприпустимий формат імені гравця
whitelist-kick-apply-rich = Подайте заявку до білого списку на { $site }
whitelist-kick-title = Вас немає в білому списку
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = Гравця { $arg0 } додано до білого списку на { $arg1 }
whitelist-temp-added-rich = Гравця { $player } додано до білого списку на { $duration }
whitelist-temp-command-description = Тимчасово додати гравця до білого списку на 1 годину
whitelist-temp-duration = 1 година
whitelist-temp-usage-desc = Тимчасово додати гравця до білого списку на 1 годину

## Shell · Menus, server information and scripting

main-afk-current-afk = Зараз AFK
main-afk-current-online = Зараз у мережі
main-afk-status = Статус AFK
main-announcements = Оголошення
main-home = Домівки
main-language = Мова
main-language-auto = Автоматично
main-links = Посилання
main-map = Вебмапа
main-menu-title = Головне меню
main-music = Музика
main-name-tag = Теги імені
main-profile = Профіль
main-pvp = PvP
main-settings = Налаштування
main-website = Сайт
main-wiki = Wiki
skript-command-description = Показати підтримку серверних скриптів
skript-info-ai-option = Не хочете писати самі? Передайте цей запит ШІ:
skript-info-copy = [Копіювати запит для ШІ]
skript-info-copy-hover = Копіювати запит із версіями, посиланнями на документацію та правилами безпеки
skript-info-documentation = Документація синтаксису й API
skript-info-review = Скрипти завантажуються лише після перевірки й тестування адміністратором
skript-info-supported = Доступно: { $arg0 }
skript-info-title = Серверні скрипти
skript-info-workflow = Напишіть і перевірте повний файл .sk за документацією, потім надішліть його адміністратору
stat-client-version = Версія клієнта
stat-first-joined = Перше приєднання
stat-location = Розташування
stat-ping = Затримка
stat-play-time = Час гри
stat-role = Роль
stat-world = Світ
weblogin-command-description = Підтвердити код входу в Mik Web
weblogin-confirmed = Вхід у Mik Web підтверджено. Поверніться до браузера, щоб продовжити.
weblogin-invalid-code = Неправильний код входу в Mik Web
