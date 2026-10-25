### MIK · ru-ru
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = { $case ->
       *[nominative] Участок
        [genitive] участка
        [dative] участку
        [accusative] участок
        [instrumental] участком
        [locative] участке
    }
    .gender = masculine

-vehicle-model = { $case ->
       *[nominative] Модель
        [genitive] модели
        [dative] модели
        [accusative] модель
        [instrumental] моделью
        [locative] модели
    }
    .gender = feminine

## Shared · General

back = Назад
back-to-main = В главное меню
click-open = Нажмите, чтобы открыть
close = Закрыть
current-off = Сейчас: выкл.
current-on = Сейчас: вкл.
player-only = Эту команду могут использовать только игроки
unknown = Неизвестно
usage = Использование

## Shared · Language, roles and time

language-command-description = Открыть настройки языка
language-menu-title = Настройки языка
language-selected = Выбран
language-set = Выбран язык: { $arg0 }
role-custodian = Хранитель
role-member = Участник
role-moderator = Модератор
role-new-player = Новый игрок
time-hours-minutes = { $arg0 } ч { $arg1 } мин
time-minutes = { $arg0 } мин

## Shared · Menu presentation

bedrock-menu-back = Назад
bedrock-menu-hotkey = Быстрое действие (F)
bedrock-menu-primary = Основное действие
bedrock-menu-scroll-down = Вниз
bedrock-menu-scroll-up = Вверх
bedrock-menu-secondary = Дополнительное действие
interface-fov-apply = Применить FOV
interface-fov-custom = Ввести точное значение
interface-fov-description = Укажите FOV клиента; меняется только положение меню. По умолчанию: 70°.
interface-fov-input = FOV клиента: целое число от 30 до 110
interface-fov-invalid = Введите целое число от 30 до 110.
interface-fov-title = Адаптация поля зрения (FOV)
interface-scale-current = Сейчас: { $arg0 }
interface-scale-description = Размер текста умножается на размер меню; обе группы настраиваются колёсиком.
interface-scale-layout-title = Размер меню
interface-scale-menu-title = Интерфейс и текст
interface-scale-native-hint = Эти настройки применяются к пространственным меню Java. Текст нативных форм зависит от настроек клиента.
interface-scale-preview = Пример текста Aa 123
interface-scale-reset = Вернуть оба к 100%
interface-scale-text-title = Размер текста
menu-rhythm-game-display = Играет в ритм-игру
menu-usage-display-mm = <aqua><bold>В меню</bold></aqua>
url-dialog-confirm = Открыть
url-dialog-hint = Клиент ещё раз попросит подтвердить открытие ссылки.
url-dialog-question-rich = Открыть { $label }?
url-dialog-title = Открыть { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Рекомендуемые моды не обнаружены: { $mods }. Нажмите на название для скачивания; если моды установлены, проверьте их включение и совместимость.
client-version-mismatch-mm = <gray>Версия сервера <white>{ $serverVersion }</white>, текущая версия клиента <white>{ $clientVersion }</white>.</gray>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>Рядом: { $count ->
        [one] <gold>{ $count }</gold> <gold>невидимый игрок</gold>
        [few] <gold>{ $count }</gold> <gold>невидимых игрока</gold>
        [many] <gold>{ $count }</gold> <gold>невидимых игроков</gold>
       *[other] <gold>{ $count }</gold> <gold>невидимого игрока</gold>
    }</yellow> <gray>·</gray> <gray>до ближайшего: примерно { $distance ->
        [one] { $distance } блок
        [few] { $distance } блока
        [many] { $distance } блоков
       *[other] { $distance } блока
    }</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = На сервер незаметно пробирается { $player }
    .item-2 = На тайной тропе появляется { $player }
    .item-3 = Из кустов появляется { $player }
presence-quit-templates =
    .item-1 = { $player } тихо исчезает
    .item-2 = { $player } отправляется сохраняться
    .item-3 = Реальность зовёт: { $player } покидает сервер
welcome-first-join-mm = <gold><bold>Добро пожаловать на MIK!</bold></gold><newline><gray>Для начала загляните на <click:open_url:'https://mcmik.top'><yellow>сайт</yellow></click> и в <click:open_url:'https://mcmik.top/wiki'><yellow>Вики</yellow></click>. Главное меню также можно открыть командой <yellow>/menu</yellow>.</gray>
welcome-return-mm = <gold><bold>С возвращением!</bold></gold><newline><gray>Вы заходили <yellow>{ $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дня
        [many] { $arg0 } дней
       *[other] { $arg0 } дня
    } назад</yellow>. Основные возможности можно быстро найти через <yellow>/menu</yellow>.</gray>

## Player · AFK

afk-already-afk-mm = <gray>Вы уже в режиме <gold>AFK</gold></gray>
afk-command-description = Перейти в режим AFK
afk-default-statuses =
    .item-1 = отошёл от клавиатуры
    .item-2 = перешёл в режим энергосбережения
    .item-3 = свернул игру
    .item-4 = смотрит в пустоту
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>теперь AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>оставляет сообщение:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>меняет статус:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>снова у клавиатуры</green>
    .item-2 = <gray>*</gray> <player> <green>снова с нами</green>
afk-not-afk-mm = <gray>Сейчас вы не <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>Статус AFK не может быть длиннее { $arg0 ->
        [one] <white>{ $arg0 }</white> символа
        [few] <white>{ $arg0 }</white> символов
        [many] <white>{ $arg0 }</white> символов
       *[other] <white>{ $arg0 }</white> символа
    }</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · совпадения: { $arg2 ->
        [one] { $arg2 } запись
        [few] { $arg2 } записи
        [many] { $arg2 } записей
       *[other] { $arg2 } записи
    } · история: { $arg3 }
player-association-header = [Связь входа] { $arg0 } · совпадения истории адресов: { $arg1 ->
        [one] { $arg1 } игрок
        [few] { $arg1 } игрока
        [many] { $arg1 } игроков
       *[other] { $arg1 } игрока
    }.
player-association-hover = Совпали исторические сетевые записи. Это только вспомогательная информация для проверки; адреса не показываются.
player-association-more-addresses = Ещё: { $arg0 ->
        [one] { $arg0 } запись
        [few] { $arg0 } записи
        [many] { $arg0 } записей
       *[other] { $arg0 } записи
    }
player-association-more-players = Ещё: { $arg0 ->
        [one] { $arg0 } игрок
        [few] { $arg0 } игрока
        [many] { $arg0 } игроков
       *[other] { $arg0 } игрока
    }
player-association-time-days = { $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дня
        [many] { $arg0 } дней
       *[other] { $arg0 } дня
    } назад
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } запись
        [few] { $arg1 } записи
        [many] { $arg1 } записей
       *[other] { $arg1 } записи
    })
player-association-time-separator = ,
player-association-time-today = сегодня

## Player · Profile and tab list

nametag-clear-all-error = Не удалось очистить оформление имени. Попробуйте позже
nametag-clear-all-success = ✔ Оформление имени полностью очищено
nametag-clear-error = Не удалось очистить { $arg0 }
nametag-clear-success = ✔ { $arg0 } очищен
nametag-click-fill-command = Нажмите, чтобы вставить команду
nametag-command-description = Настроить оформление имени
nametag-copy-hover = Скопировать исходный код MiniMessage в буфер обмена
nametag-copy-label = [копировать]
nametag-effect-label = Результат
nametag-empty-content-rich = Значение не может быть пустым. Для очистки используйте { $command }
nametag-format-docs = Справка по форматированию
nametag-leading-label = Префикс имени
nametag-no-permission = У вас нет прав на эту команду
nametag-online-editor = Онлайн-редактор
nametag-preview-label = Предпросмотр
nametag-sample-message = пример сообщения
nametag-set-error = Не удалось задать { $arg0 }. Попробуйте позже
nametag-set-success = ✔ { $arg0 } задан
nametag-title = Оформление имени
nametag-too-long = Значение { $arg0 } слишком длинное. Не более { $arg1 ->
        [one] { $arg1 } символа
        [few] { $arg1 } символов
        [many] { $arg1 } символов
       *[other] { $arg1 } символа
    }
nametag-trailing-label = Суффикс имени
nametag-unset = (не задано)
nametag-unsupported-placeholder-rich = Поддерживаются только переменные PlaceholderAPI из списка { $supported }, но не { $unsupported }
nametag-user-data-load-failed = Не удалось загрузить данные игрока. Попробуйте позже
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Игроков в сети: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Вернуться к предыдущему месту телепортации или смерти
back-empty-mm = <gray>Нет места для возврата</gray>
back-pending-mm = <gray>Телепортация, подождите</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>Возврат на { $steps ->
        [one] { $steps } шаг
        [few] { $steps } шага
        [many] { $steps } шагов
       *[other] { $steps } шага
    } назад</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>Не удалось телепортироваться. Попробуйте ещё раз</red>
back-undo-empty-mm = <gray>Нет команды <white>/back</white>, которую можно отменить</gray>
back-undo-success-mm = <green>Последняя команда <white>/back</white> отменена</green>
back-undo-world-missing-mm = <red>Целевой мир недоступен. Отменить возврат нельзя</red>
back-world-missing-mm = <red>Целевой мир недоступен. Вернуться нельзя</red>
boundary-near-mm = <yellow>Вы приближаетесь к границе мира</yellow>
boundary-reached-mm = <red>Вы достигли границы мира</red>
fly-command-description = Переключить личный полёт
fly-disabled = Полёт выключен
fly-enabled = Полёт включён
fly-native-mode = Доступ /fly отключён. Этот игровой режим всё ещё позволяет летать.
fly-speed-click = Установить скорость полёта { $arg0 }x
fly-speed-current = Скорость полёта: { $arg0 }x
fly-speed-presets = Предустановки
fly-speed-reset = Скорость полёта сброшена до 1x
fly-speed-reset-action = Сбросить
fly-speed-reset-click = Сбросить скорость полёта до 1x
fly-speed-set = Скорость полёта установлена на { $arg0 }x
fly-state-disabled = Выключен
fly-state-enabled = Включён
fly-status = Полёт: { $arg0 } | Скорость: { $arg1 }x
flyspeed-command-description = Посмотреть или установить личную скорость полёта
home-action-create = Создать дом
home-action-delete = Удалить дом
home-action-icon = Задать значок из руки
home-action-teleport = Телепортироваться
home-action-update = Обновить место
home-already-exists = Дом с таким названием уже существует
home-back = Назад
home-confirm-delete = Подтверждение удаления
home-confirm-delete-detail-rich = Удалить точку дома { $home }
home-confirm-delete-warning = Это действие нельзя отменить
home-confirm-update = Подтвердить обновление
home-confirm-update-detail-rich = Обновить точку дома { $home } до этой позиции
home-delete-command-description = Удалить точку дома
home-delete-usage-desc = Удалить точку дома
home-deleted-rich = Точка дома { $home } удалена
home-distance = Расстояние: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } блок
        [few] { $arg0 } блока
        [many] { $arg0 } блоков
       *[other] { $arg0 } блока
    }
home-distance-other-world = другой мир
home-empty-description = Встаньте там, где хотите сохранить точку дома
home-empty-title = Точек дома пока нет
home-icon = Значок: { $arg0 }{ $arg1 }
home-icon-default-note = · по умолчанию
home-icon-hand-invalid = Предмет в ведущей руке нельзя использовать как значок точки дома
home-icon-updated-rich = Значок точки дома { $home } обновлён
home-invalid-icon = Этот материал нельзя использовать как значок точки дома
home-invalid-name = Имя точки дома не может содержать символы < > :
home-list-rich = Точки дома: { $homes }
home-location = Координаты: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = Данные о расположении этой точки дома повреждены
home-max-reached = Можно установить не более { $arg0 } точек дома
home-menu-title = Точки дома
home-no-homes = У вас пока нет точек дома
home-not-found-rich = Точка дома { $home } не существует
home-set-command-description = Установить точку дома в текущем месте
home-set-rich = Точка дома { $home } установлена
home-set-usage-desc = Установить точку дома в текущем месте
home-summary-count = Установлено: { $arg0 } / { $arg1 }
home-teleport-command-description = Телепортироваться к точке дома
home-teleport-failed = Не удалось телепортироваться. Попробуйте ещё раз
home-teleported-rich = Вы телепортированы к точке дома { $home }
home-updated-rich = Позиция точки дома { $home } обновлена
home-usage-book = Справка по командам
home-usage-delhome = /delhome <name>  Удалить точку дома
home-usage-home = /home tp <name>  Телепортироваться к точке
home-usage-icon = /home icon <material> <name>  Выбрать значок
home-usage-name-rule = Имя точки дома не может содержать < > :
home-usage-sethome = /sethome <name>  Сохранить текущее место
home-world = Мир: { $arg0 }
home-world-end = · Энд
home-world-nether = · Незер
reback-command-description = Отменить последнюю команду /back
teleport-accept-command-description = Принять ожидающий запрос на телепортацию
teleport-allow = Телепортации другими игроками
teleport-block-afk = Запрещать в AFK
teleport-denied = Игрок { $player } запретил обычную телепортацию к себе
teleport-deny-command-description = Отклонить ожидающий запрос на телепортацию
teleport-menu-title = Приватность телепортации
teleport-moved-here = Игрок { $arg0 } телепортировал вас сюда
teleport-policy-always-allow = Всегда разрешать
teleport-policy-always-deny = Всегда запрещать
teleport-policy-require-consent = Требовать моего согласия
teleport-request-accept = Принять
teleport-request-approved = Игрок { $player } принял ваш запрос на телепортацию
teleport-request-deny-action = Отклонить
teleport-request-expired = Срок действия запроса на телепортацию истёк
teleport-request-failed = Не удалось выполнить телепортацию. Попробуйте ещё раз
teleport-request-none = У вас нет ожидающего запроса на телепортацию
teleport-request-received = Игрок { $sender } хочет телепортировать вас: { $command }
teleport-request-rejected = Запрос на телепортацию отклонён
teleport-request-sent = Запрос на телепортацию отправлен игроку { $player }
teleport-summary-afk = Всегда разрешены, но запрещены в AFK
teleport-summary-allow = Телепортации обычными игроками всегда разрешены
teleport-summary-consent = Телепортации обычными игроками требуют моего согласия
teleport-summary-deny = Телепортации обычными игроками всегда запрещены
tpany-command-description = Телепортироваться к игроку в сети или не в сети
tpany-done-offline-rich = Вы телепортированы к последнему известному расположению игрока { $player }: { $world } { $location }
tpany-done-online-rich = Вы телепортированы к текущему расположению игрока { $player }
tpany-not-found-rich = Не удалось найти расположение игрока { $player }
tpany-usage = Использование
tpany-usage-command = /tpany <player>
tpany-usage-desc = Телепортироваться к текущему или сохранённому расположению игрока

## Player · PVP

pvp-admin-menu-title = Управление PvP · Страница { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = Разрешить атаковать ездового моба
pvp-allow-mounted-damage-desc = Другой игрок сможет атаковать моба, на котором вы едете, если у вас обоих включён PvP
pvp-auto-enable-desc = Дважды подряд атаковать игрока с включённым PvP
pvp-auto-enable-label = Автовключение
pvp-auto-enable-warning-actionbar-mm = <yellow>Атакуйте ещё раз, чтобы автоматически включить PvP</yellow>
pvp-auto-enabled-actionbar-mm = <green>PvP включён автоматически</green>
pvp-back-admin = К управлению PvP
pvp-combat-locked-mm = <red>Нельзя выключить PvP во время боя</red> <dark_gray>|</dark_gray> <gray>Осталось времени: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> секунда
        [few] <yellow>{ $arg0 }</yellow> секунды
        [many] <yellow>{ $arg0 }</yellow> секунд
       *[other] <yellow>{ $arg0 }</yellow> секунды
    }</gray>
pvp-combat-tag-label = Боевая блокировка
pvp-combat-tag-value = { $arg0 } с
pvp-command-description = Переключить статус PvP
pvp-enable = Включить PvP
pvp-enable-desc = Вы сможете атаковать игроков с включённым PvP, а они смогут атаковать вас
pvp-enable-on-death = Включать PvP после смерти
pvp-enable-on-death-desc = Автоматически включать PvP после вашей смерти
pvp-enable-on-death-label = Включать после смерти
pvp-enabled-on-death-mm = <yellow>PvP включён после вашей смерти</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Открыть управление PvP игроков в сети</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Открыть настройки PvP</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Управлять игроком в сети</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Посмотреть свой статус PvP</gray>
pvp-help-title-mm = <gold><bold>Команды PvP</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Переключить свой статус PvP</gray>
pvp-menu-title = Настройки PvP
pvp-mob-protected-actionbar-mm = <yellow>Этот моб защищён от урона в PvP</yellow>
pvp-mob-protection-label = Защита мобов
pvp-mounted-damage-label = Урон ездовому мобу
pvp-next-page = Следующая страница
pvp-no-permission-mm = <red>У вас нет прав управлять статусом PvP другого игрока</red>
pvp-player-not-found-mm = <red>Игрок не в сети или не найден</red>
pvp-prev-page = Предыдущая страница
pvp-protect-mobs = Защищать своих мобов
pvp-protect-mobs-desc = Защищать приручённых вами мобов и тех, на которых вы едете
pvp-set-by-staff-mm = <aqua>Персонал установил вам статус PvP:</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = Статус PvP игрока { $arg0 }: { $arg1 }
pvp-set-other-rich = Статус PvP игрока { $player }: { $state }
pvp-state-label = PvP
pvp-state-off = Выкл.
pvp-state-on = Вкл.
pvp-status-player = Игрок: { $arg0 }
pvp-status-player-rich = Игрок: { $player }
pvp-status-title-mm = <gold><bold>Статус PvP</bold></gold>
pvp-summary-disabled = PvP выключен
pvp-summary-enabled = PvP включён
pvp-target-disabled-actionbar-mm = <gray>У этого игрока выключен PvP</gray>
pvp-target-menu-title = Настройки PvP · { $arg0 }
pvp-toggled-off-mm = <gray>PvP выключен</gray> <dark_gray>|</dark_gray> <gray>Другие игроки не могут вас атаковать</gray>
pvp-toggled-on-mm = <green>PvP включён</green> <dark_gray>|</dark_gray> <gray>Теперь вы можете сражаться с другими игроками с включённым PvP</gray>

## Player · Utilities

hat-command-description = Надеть предмет из ведущей руки
hat-empty-hand-mm = <red>В ведущей руке нет предмета</red> <gray>Возьмите блок или предмет и используйте /hat</gray>
hat-success-mm = <green>Предмет из ведущей руки надет</green> <dark_gray>|</dark_gray> <gray>Предметы в руке и слоте шлема поменялись местами</gray>
removeitems-command-description = Удалить выброшенные предметы
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Удалено: { $countValue ->
        [one] { $count } выброшенный предмет
        [few] { $count } выброшенных предмета
        [many] { $count } выброшенных предметов
       *[other] { $count } выброшенного предмета
    } · мир { $world } { $location } { $radius }
removeitems-no-world = Не удалось определить мир
removeitems-radius = радиус { $arg0 ->
        [one] { $arg0 } блок
        [few] { $arg0 } блока
        [many] { $arg0 } блоков
       *[other] { $arg0 } блока
    }
self-kick-command-description = Кикнуть себя с сервера
self-kick-reason-too-long = Причина кика не может превышать { $arg0 ->
        [one] { $arg0 } символ
        [few] { $arg0 } символа
        [many] { $arg0 } символов
       *[other] { $arg0 } символа
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Вы покинули сервер по собственному запросу. Причина: { $reason }
       *[third] { $player }: добровольный выход с сервера. Причина: { $reason }
    }
    .item-1 = { $player }: неожиданный самокик. Причина: { $reason }
    .item-2 = { $player }: самокик из-за { $reason }!
    .item-3 = { $player }: добровольный выход с объяснением: { $reason }
    .item-4 = { $player }: самостоятельный выход по причине: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Вы покинули сервер по собственному запросу.
       *[third] { $player }: добровольный выход с сервера.
    }
    .item-1 = { $player }: вот это самокик!
    .item-2 = { $player }: выход без лишних слов.
    .item-3 = { $player }: безупречный самокик.
    .item-4 = { $player }: выход по собственной кнопке.
trash-command-description = Открыть корзину
trash-title = Корзина
trash-title-hint = Наведитесь на предмет и нажмите F, чтобы удалить всю стопку

## Community · Chat and mentions

chat-copy-hover = ЛКМ: скопировать сообщение
chat-delay-3s = 3 секунды
chat-delay-5s = 5 секунд
chat-delay-7s = 7 секунд
chat-delay-cancel-command-description = Отменить отложенные сообщения
chat-delay-cancelled = Отложенное сообщение отменено
chat-delay-current = Текущая задержка чата: { $arg0 }
chat-delay-nothing-to-cancel = Нет отложенного сообщения для отмены
chat-delay-off = Выкл.
chat-delay-preview-marker = [Ожидает отправки]
chat-delay-queued = Сообщение будет отправлено через { $arg0 }; для отмены используйте /cancel или /c
chat-item-empty-hover = В ведущей руке нет предмета
chat-mention-all-hover = Оповестить всех видимых игроков в этом канале
chat-msg-command-description = Отправить личное сообщение и перейти в личный чат
chat-player-hover = ЛКМ: начать личное сообщение
chat-private-enter = Вы перешли в личный чат с { $arg0 }
chat-private-label = ЛС
chat-private-no-reply-target = Среди недавних собеседников никого нет в сети
chat-private-self = Нельзя начать личный чат с собой
chat-private-target-not-found = Игрок не в сети или не найден: { $arg0 }
chat-private-target-offline = Собеседник вышел из сети. Вы вернулись в общий чат
chat-public-command-description = Переключиться в общий чат
chat-public-enter = Вы перешли в общий чат
chat-reply-command-description = Ответить последнему собеседнику в личном чате
chat-settings-menu-title = Настройки чата
chat-social-account-label = Аккаунт платформы
chat-social-bound-player-label = Привязанный игрок
chat-social-name-label = Имя в соцсети
chat-social-unbound = Не привязано к игроку Minecraft
chat-staff-command-description = Переключиться в чат персонала
chat-staff-permission-missing = Нет доступа к чату персонала. Вы вернулись в общий чат
mention-action-bar = Надпись над инвентарём
mention-action-bar-text = { $sender } упоминает вас
mention-alerts = Оповещения об упоминаниях
mention-menu-title = Оповещения об упоминаниях
mention-mute-afk = Отключать в AFK
mention-sound = Звук оповещения
mention-summary-action-bar = надпись над инвентарём
mention-summary-disabled = Оповещения отключены
mention-summary-enabled = Оповещения включены
mention-summary-enabled-afk = Оповещения включены, но отключаются в AFK
mention-summary-modes-afk = { $arg0 }, отключаются в AFK
mention-summary-sound = звук
staffchat-enter = Вы вошли в чат персонала

## Community · Announcements and tips

announcement-empty = Объявлений нет
announcement-first-page = Самые новые объявления
announcement-last-page = Самые ранние объявления
announcement-more-count = Ещё { $arg0 } не показано
announcement-new-count = Новых: { $arg0 }
announcement-newer = Новые объявления
announcement-older = Ранние объявления
announcement-reload = Обновить объявления
announcement-reload-command-description = Перезагрузить файл объявлений
announcement-reload-failed = Не удалось перезагрузить объявления; прежние объявления сохранены
announcement-reloaded = Объявления обновлены
announcement-scroll-hint = Колесо для просмотра по горизонтали
announcement-unread-count = Непрочитанных: { $arg0 }
announcement-updated = Объявления обновлены
announcement-view-all = Все объявления
announcement-view-command-description = Просмотреть объявления сервера
tip-hover = Подсказка сервера
tip-label = ПОДСКАЗКА
tip-reload-command-description = Перезагрузить tips.txt
tip-reload-count = { $arg0 ->
        [one] { $arg0 } запись
        [few] { $arg0 } записи
        [many] { $arg0 } записей
       *[other] { $arg0 } записи
    }
tip-reload-done = Подсказки перезагружены

## Community · Identity and social platforms

identity-admin-binding-not-found = Однозначная привязка игрока и платформы не найдена.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Для { $arg1 } удалено: { $arg2 ->
        [one] { $arg2 } привязка
        [few] { $arg2 } привязки
        [many] { $arg2 } привязок
       *[other] { $arg2 } привязки
    } ({ $arg0 }).
identity-admin-command-description = Просмотреть или отозвать внешние привязки
identity-admin-help = /bindadmin lookup <игрок-или-UUID>
    /bindadmin unlink <игрок-или-UUID> <платформа> confirm
identity-admin-lookup-empty = Привязки этого игрока не найдены.
identity-admin-lookup-title = Привязки игрока { $arg0 }:
identity-binding-details = · { $arg0 } · область { $arg1 } · { $arg2 }
identity-binding-not-owned = У вас нет привязок для этой платформы.
identity-binding-unlinked = Удалено: { $arg1 ->
        [one] { $arg1 } привязка
        [few] { $arg1 } привязки
        [many] { $arg1 } привязок
       *[other] { $arg1 } привязки
    } ({ $arg0 }).
identity-bindings-empty = У вас нет привязанных учётных записей внешних платформ.
identity-bindings-title = Привязанные внешние учётные записи:
identity-bindings-unlink-hint = Используйте /bind unlink <платформа>, чтобы удалить все привязки платформы.
identity-code-cancelled = Неиспользованный код привязки { $arg0 } отменён.
identity-code-copy = Нажмите, чтобы скопировать полную команду привязки
identity-code-expiry = Код действует 5 минут и используется один раз. Создание нового кода отменяет старый.
identity-code-issued = Код привязки для { $arg0 } создан:
identity-code-not-pending = Неиспользованного кода привязки { $arg0 } нет.
identity-code-redeem = В нужной группе { $arg0 } упомяните бота и отправьте: { $arg1 }
identity-command-description = Привязать Minecraft к учётной записи внешней платформы
identity-help = Команды привязки учётной записи:
    /bind qq — быстро создать команду привязки QQ
    /bind <платформа> — создать одноразовый код
    /bind list — показать существующие привязки
    /bind cancel <платформа> — отменить неиспользованный код
    /bind unlink <платформа> — удалить все привязки платформы
identity-notify-linked = Ваша личность { $arg0 } подтверждена. Просмотр: /bind list; удаление: /bind unlink { $arg0 }.
identity-notify-unlinked = Ваша привязка { $arg0 } удалена.
identity-platform-none = нет
identity-platforms = Поддерживаемые платформы: { $arg0 }
identity-scope-notice = Эта платформа разделяет учётные записи по группам. Выполните привязку в нужной группе; каждую группу нужно привязать отдельно.
identity-storage-error = Ошибка операции с базой привязок. Обратитесь к администратору.
identity-unavailable = Привязка учётных записей сейчас недоступна. Обратитесь к администратору.
identity-unsupported-platform = Неподдерживаемая платформа: { $arg0 }
social-admin-conversations-empty = Для { $arg0 } не обнаружено диалогов.
social-admin-conversations-title = Обнаруженные диалоги для { $arg0 }:
social-admin-help = /social platforms · /social status [платформа] · /social reload [платформа] · /social conversations <платформа>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Социальная платформа { $arg0 } перезагружена.
social-admin-reloaded-all = Все социальные платформы перезагружены.
social-admin-status = { $arg0 } · { $arg1 } · поколение { $arg2 } · диалоги { $arg3 }
social-admin-title = Социальные платформы
social-admin-unknown = Неизвестная социальная платформа: { $arg0 }
social-binding-already-linked = Эта личность уже привязана к игроку Minecraft { $arg0 }.
social-binding-code-invalid = Код подтверждения неверен или истёк. Создайте новый командой /bind { $arg0 } в Minecraft.
social-binding-external-in-use = Эта личность уже привязана к другому игроку Minecraft.
social-binding-help = Выполните /bind { $arg0 } в Minecraft.
    Скопируйте созданную полную команду.
    Отправьте эту команду сюда.
    Проверьте привязку командой /профиль.
social-binding-identity-missing = В этом сообщении нет надёжного идентификатора участника, поэтому привязка недоступна.
social-binding-linked = Выполнена привязка к игроку Minecraft { $arg0 }.
social-binding-not-bound = Эта платформенная личность не привязана.
social-binding-platform-mismatch = Этот код подтверждения выдан для другой платформы.
social-binding-player-scope-in-use = У этого игрока Minecraft в данном диалоге уже привязана другая личность.
social-binding-rate-limited = Слишком много неудачных попыток. Повторите примерно через { $arg0 ->
        [one] { $arg0 } минуту
        [few] { $arg0 } минуты
        [many] { $arg0 } минут
       *[other] { $arg0 } минуты
    }.
social-binding-status-unbound = Эта личность не привязана. Используйте /привязать с кодом подтверждения.
social-binding-unavailable = Привязка личности временно недоступна.
social-binding-unlink-prompt = Отвязка удалит связь этой платформенной личности с Minecraft. Для продолжения отправьте /отвязать подтвердить.
social-binding-unlinked = Привязка к игроку Minecraft { $arg0 } удалена.
social-command-description = Управление адаптерами социальных платформ
social-duration-days = { $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дня
        [many] { $arg0 } дней
       *[other] { $arg0 } дня
    } { $arg1 } ч. { $arg2 } мин.
social-duration-hours = { $arg0 } ч. { $arg1 } мин.
social-duration-minutes = { $arg0 } мин.
social-help-command-description = Показать этот список команд
social-link-command-description = Привязать учётную запись Minecraft кодом из игры
social-online-command-description = Показать игроков онлайн
social-profile-afk = В сети · AFK
social-profile-avatar-alt = Лицо Minecraft
social-profile-command-description = Показать свой профиль; полные участники ищут по имени, упоминанию или ответу
social-profile-first-joined-label = Первый вход
social-profile-language-label = Язык
social-profile-last-seen-label = Последний вход
social-profile-member-required = Только привязанные участники, модераторы или хранители могут смотреть профили других игроков.
social-profile-offline = Не в сети
social-profile-online = В сети · активен
social-profile-play-time-label = Время игры
social-profile-player-label = Игрок
social-profile-player-not-found = Этот игрок Minecraft не найден.
social-profile-presence-label = Статус
social-profile-target-ambiguous = Выберите одну цель: имя игрока, одно упоминание или одного автора ответа.
social-profile-target-unbound = Упомянутая личность или автор сообщения, на которое ответили, не привязаны к Minecraft.
social-profile-uuid-label = UUID
social-query-active-label = Активные
social-query-no-players = Сейчас на сервере нет игроков.
social-query-online-label = В сети
social-query-players-label = Игроки
social-query-unknown = Неизвестная команда. Отправьте /помощь, чтобы посмотреть доступные команды.
social-query-uptime-label = Время работы
social-reply-hidden = Ответ скрыт политикой безопасности содержимого сервера.
social-state-disabled = отключена
social-state-failed = ошибка
social-state-ready = готова
social-state-starting = запускается
social-state-stopped = остановлена
social-status-command-description = Показать игроков, TPS, MSPT и время работы
social-title-command-help = Справка по командам
social-title-content-hidden = Содержимое скрыто
social-title-identity-linking = Привязка аккаунта
social-title-identity-unlinking = Отвязка аккаунта
social-title-online-players = Игроки онлайн
social-title-player-profile = Профиль игрока
social-title-server-status = Состояние сервера
social-title-unknown-command = Неизвестная команда
social-unlink-command-description = Удалить эту привязку после подтверждения

## Community · AI and knowledge

ai-busy = ИИ обрабатывает слишком много запросов; повторите попытку чуть позже
ai-cleared = История диалога с ИИ очищена
ai-command-description = Задать вопрос игровому ИИ-помощнику
ai-disabled = ИИ-помощник отключён или не настроен
ai-knowledge-curation-failed = Ошибка систематизации знаний: { $arg0 }
ai-knowledge-curation-finished = Систематизация завершена: применено { $arg0 } · пропущено { $arg1 } · ошибок { $arg2 } · карантин { $arg3 } · обработано { $arg4 }
ai-knowledge-curation-started = Систематизация знаний начата.
ai-knowledge-disabled = База знаний отключена.
ai-knowledge-index-dirty = Нужно согласовать индекс; поиск личных воспоминаний закрыт до синхронизации.
ai-knowledge-last-failure = Последняя ошибка: { $arg0 }
ai-knowledge-last-sync = Последняя синхронизация: { $arg0 }
ai-knowledge-learning-disabled = Обучение базы знаний отключено.
ai-knowledge-operation-failed = Ошибка операции с базой знаний: { $arg0 }
ai-knowledge-operation-started = Операция с базой знаний начата.
ai-knowledge-protected = Документ { $arg0 } теперь защищён.
ai-knowledge-rollback = Документ { $arg0 } восстановлен как редакция { $arg1 }.
ai-knowledge-status = Знания: { $arg0 ->
        [one] { $arg0 } общий документ
        [few] { $arg0 } общих документа
        [many] { $arg0 } общих документов
       *[other] { $arg0 } общего документа
    } / { $arg1 ->
        [one] { $arg1 } фрагмент текста
        [few] { $arg1 } фрагмента текста
        [many] { $arg1 } фрагментов текста
       *[other] { $arg1 } фрагмента текста
    } · { $arg2 ->
        [one] { $arg2 } личный документ
        [few] { $arg2 } личных документа
        [many] { $arg2 } личных документов
       *[other] { $arg2 } личного документа
    } / { $arg3 ->
        [one] { $arg3 } фрагмент текста
        [few] { $arg3 } фрагмента текста
        [many] { $arg3 } фрагментов текста
       *[other] { $arg3 } фрагмента текста
    }
ai-knowledge-sync-failed = Ошибка синхронизации знаний: { $arg0 }
ai-knowledge-sync-started = Синхронизация знаний начата.
ai-knowledge-synchronized = { ai-knowledge-status } · Синхронизация завершена.
ai-knowledge-unprotected = Защита документа { $arg0 } снята.
ai-learning-failure = Ошибка обучения: { $arg0 }
ai-learning-status = Обучение: кандидаты { $arg0 } · извлечение { $arg1 } · очередь { $arg2 } · извлечений отброшено { $arg3 } · кандидатов отброшено { $arg4 } · карантин { $arg5 }
ai-memory-empty = Личных воспоминаний для { $arg0 } нет.
ai-memory-list = Личные воспоминания для { $arg0 }: { $arg1 }
ai-memory-not-found = Личное воспоминание { $arg0 } не найдено.
ai-memory-removed = Для { $arg1 } удалено навсегда: { $arg0 ->
        [one] { $arg0 } личное воспоминание
        [few] { $arg0 } личных воспоминания
        [many] { $arg0 } личных воспоминаний
       *[other] { $arg0 } личного воспоминания
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Неизвестный игрок в кеше: { $arg0 }
ai-prompt-too-long = Вопрос слишком длинный (не более { $arg0 ->
        [one] { $arg0 } символа
        [few] { $arg0 } символов
        [many] { $arg0 } символов
       *[other] { $arg0 } символа
    })
ai-reload-failed = Не удалось перезагрузить настройки ИИ: { $arg0 }
ai-reloaded = Настройки ИИ перезагружены ({ $arg0 })
ai-request-failed = Ошибка запроса к ИИ; повторите попытку позже
ai-social-command-description = Задать вопрос общему ИИ-помощнику
ai-social-question-required = Добавьте вопрос после команды ИИ
ai-status-disabled = ИИ отключён
ai-status-enabled = ИИ включён · провайдер { $arg0 } · модель { $arg1 } · инструменты { $arg2 }
ai-status-none = нет
ai-thinking = Думаю…
ai-usage = Использование: /ai <вопрос> · /ai clear · /ai status
ai-worker-status = Задачи ИИ: выполняется { $arg0 } · в очереди { $arg1 } · отброшено { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Поздравляем!</bold> Теперь вы <gold>Участник</gold>. Вам доступно больше команд и возможностей, включая упрощённую печать схем.</green>
governance-active-days-30 = Не менее 2 дней активности за 30 дней (сейчас { $arg0 })
governance-active-days-60 = Не менее 6 дней активности за 60 дней (сейчас { $arg0 })
governance-active-header = ─── Условия активного участника ───
governance-active-member = Сейчас участник
governance-active-member-age = Участник не менее 30 дней
governance-active-playtime = Не менее 6 часов полезной игры за 60 дней (сейчас { $arg0 })
governance-already-moderator = Вы уже модератор.
governance-ballot-recorded = Ваш тайный голос записан как «{ $arg0 }». До окончания голосования его можно изменить.
governance-ban-details = Предложил { $arg0 } · Срок { $arg1 } · Причина: { $arg2 }
governance-ban-suffix = · Срок { $arg0 } · Причина: { $arg1 }
governance-choice-abstain = Воздержался
governance-choice-no = Против
governance-choice-yes = За
governance-command-description = Показать статус управления и голосования
governance-custodian-no-nomination = Хранители не участвуют в выборах модераторов.
governance-custodian-no-resign = Порядок отставки модераторов не относится к хранителям.
# $arg0 (Number): duration; locale decides the unit form.
governance-days = { $arg0 ->
        [one] { $arg0 } день
        [few] { $arg0 } дня
        [many] { $arg0 } дней
       *[other] { $arg0 } дня
    }
governance-error-ban-already-active = У этого игрока уже есть действующая блокировка.
governance-error-ban-proposer-ineligible = Предлагать голосование о блокировке могут только активные участники.
governance-error-ban-self = Нельзя предложить голосование о собственной блокировке.
governance-error-candidate-ineligible = Кандидат в модераторы должен быть активным участником.
governance-error-nomination-not-open = У вас нет действующей заявки в модераторы.
governance-error-not-voter = Вас нет в закреплённом списке избирателей.
governance-error-pause-already-active = Повышение этого игрока уже приостановлено.
governance-error-pause-already-member = Этот игрок уже участник; приостанавливать повышение не нужно.
governance-error-pause-end-invalid = Приостановка повышения должна закончиться в будущем.
governance-error-player-unknown = Записи об этом игроке ещё нет.
governance-error-removal-requires-petition = Для голосования об отстранении нужно достаточно сторонников.
governance-error-removal-self = Нельзя поддержать собственное отстранение.
governance-error-removal-sponsor-ineligible = Поддержать отстранение могут только активные участники.
governance-error-removal-sponsors-too-few = Нужны как минимум два других активных участника.
governance-error-removal-vote-open = Голосование об отстранении этого модератора уже идёт.
governance-error-termination-reason-invalid = Эта причина завершения не подходит к голосованию.
governance-error-vote-already-open = По этому игроку уже идёт голосование такого вида.
governance-error-vote-closed = Голосование уже закрыто или завершено.
governance-error-vote-cooldown = Трёхдневный срок после нехватки кворума ещё не прошёл.
governance-error-vote-subject-unknown = Записи об участнике голосования ещё нет.
governance-error-vote-termination-closed = После срока голосование нельзя отозвать.
governance-error-vote-unknown = Голосование не найдено.
governance-error-voters-too-few = Для начала голосования нужны как минимум два активных участника.
governance-failure-majority = Голосов «за» не больше половины всех бюллетеней
governance-failure-minimum-yes = Недостаточно голосов «за»
governance-failure-none = Причина не указана
governance-failure-quorum = Кворум не достигнут
governance-failure-two-thirds = Голосов «за» меньше двух третей голосов без воздержавшихся
governance-history-header = ─── Недавние результаты ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
# $arg0 (Number): duration; locale decides the unit form.
governance-hours = { $arg0 ->
        [one] { $arg0 } час
        [few] { $arg0 } часа
        [many] { $arg0 } часов
       *[other] { $arg0 } часа
    }
governance-inactive-removal = Полномочия модератора для { $arg0 } прекращены автоматически: входа не было 180 дней.
governance-invalid-choice = Выберите yes, no или abstain.
governance-invalid-duration = Неверный срок. Укажите, например, 30m, 12h или 3d.
governance-kind-appointment = Назначение
governance-kind-ban = Блокировка
governance-kind-removal = Отстранение
governance-legacy-ban-kick = Прежнее решение сообщества запрещает вам вход. Срок: { $arg0 }. Причина: { $arg1 }
governance-menu-actions = Действия
governance-menu-back = Назад
governance-menu-cancel = Отмена
governance-menu-confirm = Подтвердить
governance-menu-confirm-nominate = Начать голосование о вашем назначении модератором?
governance-menu-confirm-resign = Уйти с поста модератора?
governance-menu-confirm-sponsor = Поддержать это заявление об отстранении?
governance-menu-confirm-withdraw = Отозвать свою заявку в модераторы?
governance-menu-find-player = Найти другого игрока
governance-menu-history = История голосований
governance-menu-loading = Загрузка статуса управления…
governance-menu-new-petition = Начать заявление об отстранении
governance-menu-nominate = Подать заявку в модераторы
governance-menu-nomination-open = Голосование о вашем назначении открыто
governance-menu-pause = Приостановить повышение новичка
governance-menu-pause-appeal = Порядок обжалования
governance-menu-pause-duration = Срок (например, 2d)
governance-menu-pause-reason = Причина
governance-menu-petition-row = { $arg0 } · поддержка { $arg1 }/{ $arg2 }
governance-menu-petitions = Заявления об отстранении
governance-menu-player-name = Имя игрока
governance-menu-players = Управление игроками
governance-menu-recent-results = Последние результаты голосования
governance-menu-refresh = Обновить
governance-menu-resign = Уйти с поста модератора
governance-menu-resume = Снять паузу повышения
governance-menu-resume-reason = Причина снятия
governance-menu-staff-only = Управлять игроками могут только модераторы и кураторы.
governance-menu-status = Ваш статус
governance-menu-target = Имя модератора
governance-menu-title = Управление сообществом
governance-menu-votes = Открытые голосования
governance-menu-withdraw = Отозвать заявку
# $arg0 (Number): duration; locale decides the unit form.
governance-minutes = { $arg0 ->
        [one] { $arg0 } минута
        [few] { $arg0 } минуты
        [many] { $arg0 } минут
       *[other] { $arg0 } минуты
    }
governance-no-active-ban = Нет действующей блокировки
governance-no-active-pause = Нет действующей приостановки повышения
governance-nomination-withdrawn = Заявка отозвана. Результат будет объявлен автоматически.
governance-none = Нет
governance-not-member = Не участник
governance-not-moderator = Сейчас вы не модератор.
governance-operation-error = Не удалось выполнить действие. Попробуйте снова.
governance-own-choice-closed = Ваш выбор: { $arg0 }
governance-own-choice-open = Ваш выбор: { $arg0 }; до окончания его можно изменить.
governance-own-not-voted = У вас было право голоса, но вы не голосовали.
governance-own-not-voted-hint = Вы ещё не голосовали. Откройте «Управление» и выберите это голосование.
governance-own-not-voted-open = Вы ещё не проголосовали.
governance-own-not-voter = Вас нет в списке избирателей этого голосования.
governance-own-voted = Вы проголосовали
governance-pause-cleared = Приостановка повышения игрока { $arg0 } снята.
governance-pause-details = Приостановка: { $arg0 } · До { $arg1 } · Обжалование: { $arg2 }
governance-pause-fields-required = Укажите причину и способ обжалования.
governance-pause-not-active = У игрока { $arg0 } нет действующей приостановки повышения.
governance-pause-notice = Ваше повышение приостановлено. Причина: { $arg0 } · До { $arg1 } · Обжалование: { $arg2 }
governance-pause-set = Повышение игрока { $arg0 } приостановлено до { $arg1 }.
governance-petition-already-sponsored = Вы уже поддержали это отстранение ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · Поддержка { $arg1 }/{ $arg2 } · До { $arg3 }
governance-petition-started = Запрос на отстранение { $arg0 }: поддержка { $arg1 }/{ $arg2 }; до { $arg3 }.
governance-petitions-header = ─── Запросы на отстранение ───
governance-player-first-join = Первый вход: { $arg0 }
governance-player-header = ─── { $arg0 } · Досье управления ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Последний вход: { $arg0 }
governance-player-member-since = Участник с: { $arg0 }
governance-player-never-joined = Игрок { $arg0 } ещё не был на этом сервере.
governance-promotion-account-age = С первого входа прошло не менее 3 дней
governance-promotion-header = ─── Условия повышения ───
governance-promotion-play-days = Полезная игра минимум в 2 дня (сейчас { $arg0 })
governance-promotion-playtime = Не менее 8 часов полезной игры (сейчас { $arg0 })
governance-resign-failed = Не удалось уйти в отставку. Обратитесь к хранителю.
governance-resigned = Полномочия модератора для { $arg0 } прекращены по собственному заявлению.
governance-result-announcement = Голосование #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = Не принято ({ $arg0 })
governance-result-open = Идёт
governance-result-passed = Принято
governance-result-tally = { $arg0 } · Избирателей { $arg1 }, участвовало { $arg2 }, за { $arg3 }, против { $arg4 }, воздержалось { $arg5 }
governance-result-terminated = Завершено ({ $arg0 })
governance-resume-notice = Приостановка вашего повышения снята. Причина: { $arg0 }
governance-resume-reason-required = Укажите причину снятия приостановки.
governance-role-lookup-error = Не удалось проверить статус модератора. Обратитесь к хранителю.
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } секунда
        [few] { $arg0 } секунды
        [many] { $arg0 } секунд
       *[other] { $arg0 } секунды
    }
governance-storage-error = Ошибка данных управления. Обратитесь к хранителю.
governance-target-not-moderator = { $arg0 } сейчас не модератор.
governance-termination-inactive = Модератор ушёл после долгого отсутствия
governance-termination-ineligible = Кандидат перестал быть активным участником
governance-termination-resigned = Модератор ушёл в отставку
governance-termination-unknown = Причина не указана
governance-termination-withdrew = Кандидат отозвал заявку
governance-vote-electorate = Избирателей { $arg0 } · Кворум { $arg1 }
governance-vote-finalized = Завершено: { $arg0 }
governance-vote-header = ─── Голосование #{ $arg0 } ───
governance-vote-in-progress = Идёт · Участвовало { $arg0 }; подробные итоги появятся после закрытия.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · До { $arg3 } · Избирателей { $arg4 } · Участвовало { $arg5 } · Кворум { $arg6 }{ $arg7 }
governance-vote-period = Начало { $arg0 } · Конец { $arg1 }
governance-vote-started = Голосование «{ $arg1 }» #{ $arg0 } началось: { $arg2 }; избирателей { $arg3 }, кворум { $arg4 }, до { $arg5 }. Подробности доступны в меню «Управление».
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Текущие тайные голосования ───

## World · Plots

plot-access-removed-for-you = Ваш доступ к участку { $arg0 } удалён.
plot-admin-created = Участок { $arg0 } зарегистрирован для { $arg1 } · { $arg2 }
plot-allowed = включено
plot-area-changed = Границы изменились. Обновите и подтвердите снова.
plot-area-confirm = Заменить границы участка «{ $arg0 }» выделением ({ $arg1 ->
        [one] { $arg1 } строительная ячейка
        [few] { $arg1 } строительные ячейки
        [many] { $arg1 } строительных ячеек
       *[other] { $arg1 } строительной ячейки
    })? Постройки останутся.
plot-area-hint = //pos1 → //pos2 → сохранить. Углы можно выбрать деревянным топором; добавить/вычесть для нескольких областей.
plot-area-replace-save = Заменить границы и сохранить
plot-area-save = Сохранить границы
plot-area-saved = Границы «{ $arg0 }» сохранены.
plot-arrival-removed = Точка прибытия удалена.
plot-arrival-set = Точка прибытия сохранена.
plot-arrival-text-saved = Заголовок при прибытии сохранён.
plot-atmosphere-saved = Настройки окружения сохранены.
plot-back = Назад
plot-board-auto-notify-result = Оповещения запланированы для { $arg0 ->
        [one] { $arg0 } игрока
        [few] { $arg0 } игроков
        [many] { $arg0 } игроков
       *[other] { $arg0 } игрока
    }.
plot-board-auto-registered-text = Участок «{ $arg0 }» зарегистрирован. Внешние координаты застроенных ячеек: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Запись { $arg0 }: { $arg1 } { $arg2 }, { $arg3 }, { $arg4 } — { $arg5 }
plot-board-empty = Записей сообщества пока нет.
plot-board-header = Объявления и отзывы (последние { $arg0 }; подробнее: /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Объявление
plot-board-kind-objection = Возражение
plot-board-menu = Доска сообщества
plot-board-notice-alert = { $arg0 } опубликовал уведомление: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } подал возражение: { $arg1 } · { $arg2 }
plot-board-rate-limited = Лимит публикаций исчерпан. Попробуйте позже.
plot-board-record-invalid = Запись не найдена, уже закрыта или действие недоступно.
plot-board-registered-alert = { $arg0 }: регистрация участка «{ $arg1 }»: { $arg2 }
plot-board-ruling-alert = По возражению { $arg0 } вынесено решение: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Решение: { $arg0 }
plot-board-saved = Запись { $arg0 } сохранена.
plot-board-status-elapsed = Опубликовано не менее 72 часов назад
plot-board-status-open = Открыто
plot-board-status-ruled = Решение вынесено
plot-board-status-withdrawn = Отозвано
plot-board-text-invalid = Описание: 12–1000 символов; решение: не менее 8.
plot-board-unavailable = Записи не загрузились. Повторите позже или откройте https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } отозвал возражение: { $arg1 } · { $arg2 }
plot-board-write-failed = Не удалось обновить доску. Попробуйте позже.
plot-command-blocked = Защита участка заблокировала команду. Используйте /fill или /setblock прямо на своём участке; сложные команды /execute должна проверить администрация.
plot-command-description = Управлять участками и общественными проектами
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } создан · { $arg1 }
        [feminine] { -plot } { $arg0 } создана · { $arg1 }
       *[other] { -plot } { $arg0 } создано · { $arg1 }
    }
plot-created-for-you = Для вас зарегистрирован участок { $arg0 }. Регистрация: { $arg1 }.
plot-deleted = Участок освобождён; постройка осталась.
plot-denied = выключено
plot-empty-summary = Здесь нет участка.
plot-error-admin-owner-only = Изменение участников с полномочиями управления требует управления участниками и правами.
plot-error-area-disconnected = Участок должен быть одной связной областью.
plot-error-args = Неверные или неполные аргументы. Используйте /plot help.
plot-error-arrival-location = Встаньте на безопасный пол в застроенной части проекта.
plot-error-arrival-missing = Точка прибытия не задана.
plot-error-arrival-private = Вы не можете посетить этот частный проект.
plot-error-arrival-text = Недопустимый заголовок. Одна строка; не более 32 видимых символов в заголовке и 64 в подзаголовке.
plot-error-arrival-unsafe = Точка прибытия недоступна или больше не безопасна.
plot-error-busy = Данные участка сохраняются. Повторите позже; операция не отправлена.
plot-error-flag = Неизвестное право. Выберите действие из списка прав.
plot-error-id = ID участка не найден или неоднозначен. Используйте /plot list.
plot-error-member-missing = Этот игрок уже не участник.
plot-error-member-required = Новые игроки могут просматривать участки и подавать возражения. Для создания проекта нужен статус участника.
plot-error-name = Название проекта должно содержать от 1 до 48 символов.
plot-error-notice-text = Объявления допускают до { $arg0 ->
        [one] { $arg0 } символа
        [few] { $arg0 } символов
        [many] { $arg0 } символов
       *[other] { $arg0 } символа
    } без ограничения числа или длины строк; управляющие символы запрещены.
plot-error-overlap = Область строительства пересекается с другим участком.
plot-error-owner-member = владелец участка · Права группы
plot-error-owner-only = выключено · Управление
plot-error-permission-changed = Права, участники или наследуемые правила изменились. Просмотрите изменения снова.
plot-error-permission-grant = Нельзя выдать полномочия управления, которых у вас нет.
plot-error-permission-owner-scope = Права владельца могут менять только владелец участка, владелец родительского участка или персонал сервера.
plot-error-player-unknown = Игрок не найден. Проверьте имя или UUID.
plot-error-release-owner-only = выключено · Удалять участок
plot-error-selection = Отметьте два угла командами //pos1 и //pos2.
plot-error-selection-changed = Выделение изменилось. Подтвердите снова.
plot-error-staff-only = Только модераторы или хранители могут управлять участками других игроков или регистрировать их.
plot-error-storage = Ошибка операции с участком. Повторите позже или обратитесь к администрации.
plot-error-subplot-arrival = Сначала перенесите точку прибытия родительского участка за пределы выбора.
plot-error-subplot-children = Освободите дочерние участки до освобождения родительского.
plot-error-subplot-depth = Допускается только один уровень дочерних участков.
plot-error-subplot-disconnected = Выбранные строительные ячейки не соединены. Разделите каждую часть отдельно.
plot-error-subplot-overlap = Выбранная область пересекается с другим дочерним участком.
plot-error-subplot-own-arrival = Оставьте точку прибытия внутри новой области или сначала перенесите её.
plot-error-subplot-owner = У вас нет прав на эту операцию с подучастком.
plot-error-subplot-parent = Отметьте оба угла внутри существующих строительных ячеек родительского участка.
plot-error-subplot-supervisor-member = Владелец родительского участка уже имеет здесь надзор и не нуждается в роли участника.
plot-error-time-format = Введите время в формате HH:mm (00:00–23:59).
plot-error-transfer-owner-only = выключено · Передавать владение
plot-error-transfer-self = Вы уже владеете этим участком.
plot-flag-set = { $arg0 }: { $arg1 }.
plot-help = Руководство по участкам
plot-here = Здесь: { $arg0 } · { $arg1 }
plot-invited = Доступ для { $arg0 } предоставлен.
plot-item-summary = Застроенная площадь { $arg0 } блоков² · участников: { $arg1 }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } блок²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Не отправлено
plot-member-draft-reset = Очистить настройки категории
plot-member-draft-role = Роль: { $arg0 }
plot-member-draft-submit = Просмотреть и подтвердить
plot-member-draft-title = Черновик участника · { $arg0 }
plot-member-permissions = Права
plot-member-removed = Участник проекта удалён.
plot-menu-access = Доступ и участники
plot-menu-access-by-settings = по настройкам доступа
plot-menu-admin = Добавить управляющего участком
plot-menu-arrival = Точка прибытия
plot-menu-arrival-subtitle = Подзаголовок (MiniMessage; необязательно)
plot-menu-arrival-text = Заголовок при прибытии
plot-menu-arrival-title = Заголовок (MiniMessage; пусто — название проекта)
plot-menu-atmosphere = Окружение
plot-menu-clear = Ясно
plot-menu-collaborator = Добавить соавтора
plot-menu-confirm-delete = Удалить регистрацию { $arg0 }? Защита, права участников и настройки будут удалены без возможности отмены. Постройки останутся.
plot-menu-confirm-subplot-transfer = Передать { $arg0 } игроку { $arg1 }? Владелец родительского участка сохранит надзор.
plot-menu-confirm-transfer = Передать управление участком { $arg0 } игроку { $arg1 }? Вы потеряете доступ; авторство построек не изменится.
plot-menu-confirm-withdraw = Отозвать этот отзыв? Отметка об отзыве останется публичной.
plot-menu-create = Создать { -plot(case: "accusative") }
plot-menu-create-for-player = Зарегистрировать участок игроку
plot-menu-custom-time = Своё время
plot-menu-delete = Удалить регистрацию участка
plot-menu-description = Описание
plot-menu-feedback = Оспорить это объявление
plot-menu-find-record = Найти запись
plot-menu-flag-container = Открывать контейнеры
plot-menu-flag-entity = Взаимодействовать с сущностями
plot-menu-follow-parent = Наследовать от родителя
plot-menu-follow-world = Как в мире
plot-menu-group-admin = управляющий участком
plot-menu-group-collaborator = Соавторы
plot-menu-group-member = Доступ участников
plot-menu-group-newcomer = Доступ новичков
plot-menu-group-owner = владелец участка
plot-menu-manage = Управление участками игроков
plot-menu-managed-empty = У этого игрока пока нет участков.
plot-menu-managed-title = Участки игрока { $arg0 }
plot-menu-members = Участники ({ $arg0 })
plot-menu-my-access = Ваш доступ: { $arg0 } · { $arg1 }
plot-menu-name = Название проекта
plot-menu-owner = Владелец: { $arg0 }
plot-menu-parent-supervisor = Владелец родительского участка: { $arg0 } · надзор
plot-menu-player = Имя игрока или UUID
plot-menu-project-kind = Тип проекта
plot-menu-public-destinations = Общедоступные места
plot-menu-public-destinations-empty = Пока нет общедоступных проектов с точкой прибытия.
plot-menu-rain = Осадки
plot-menu-record-id = ID записи; для старых записей укажите полный ID
plot-menu-related-notice = Открыть связанное уведомление
plot-menu-remove-arrival = Удалить точку прибытия
plot-menu-remove-member = Удалить участника
plot-menu-rename = Переименовать проект
plot-menu-rule = Записать решение
plot-menu-ruling = Объясните решение (8–1000 символов)
plot-menu-set-arrival = Установить точку прибытия здесь
plot-menu-set-role = Назначить роль { $arg0 }
plot-menu-settings = Настройки проекта
plot-menu-submit = Применить
plot-menu-subplot-create = Создать подучасток
plot-menu-subplot-of = Часть проекта { $arg0 }
plot-menu-subplot-parent = К родительскому участку: { $arg0 }
plot-menu-subplots = Дочерние участки ({ $arg0 })
plot-menu-time = Время
plot-menu-time-input = Время (HH:mm)
plot-menu-transfer = Передать участок
plot-menu-visit = Посетить проект
plot-menu-weather = Погода
plot-menu-withdraw = Отозвать отзыв
plot-model-fit = Показать все границы
plot-model-focus = Показать окрестности
plot-model-layer-down = Опустить срез на 1 блок
plot-model-layer-up = Поднять срез на 1 блок
plot-model-legend = Голубой: сохранено · Зелёный/золотой: углы 1/2 · Фиолетовый: границы для сохранения · Белый/красный: вы/направление
plot-model-level = Высота среза Y ≤ { $arg0 } (только предпросмотр)
plot-model-limited = Детализация модели снижена. Сфокусируйтесь на ближней области или прямоугольнике; режим границ позволяет проверить края.
plot-model-north-up = На виде сверху север вверху. Для поворота вернитесь к трёхмерному виду.
plot-model-perspective = Трёхмерный вид
plot-model-preparing = Вычисление границ и проверка участка в фоне… Автоматическое обновление после завершения.
plot-model-progress = Чтение модели: { $arg0 } %…
plot-model-rotate-left = ↶ Повернуть влево
plot-model-rotate-right = ↷ Повернуть вправо
plot-model-show-all = Показать полную высоту
plot-model-slice = Срез выше моего уровня
plot-model-terrain = Показать постройки и рельеф
plot-model-top = Вид сверху
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } незагруженный образец
        [few] { $arg0 } незагруженных образца
        [many] { $arg0 } незагруженных образцов
       *[other] { $arg0 } незагруженного образца
    } (серый цвет). Чанки не загружаются принудительно.
plot-model-wireframe = Только границы
plot-no-here = Здесь нет защищённого { -plot(case: "genitive") }.
plot-notice-board = Доска участка
plot-notice-cleared = Доска очищена
plot-notice-edit = Изменить доску
plot-notice-input = До { $arg0 ->
        [one] { $arg0 } символа
        [few] { $arg0 } символов
        [many] { $arg0 } символов
       *[other] { $arg0 } символа
    }; можно несколько строк; оставьте пустым для удаления
plot-notice-read = Прочитать объявление целиком
plot-notice-saved = Доска обновлена
plot-permission-all-actions = Все действия
plot-permission-animal = Уход, приручение и привязывание животных
plot-permission-break = Ломать блоки
plot-permission-bucket = Использовать вёдра для жидкостей и существ
plot-permission-category-build = Строительство и изменения
plot-permission-category-entity = Сущности
plot-permission-category-interaction = Взаимодействие с блоками
plot-permission-category-management = Управление
plot-permission-category-storage = Хранилища и предметы
plot-permission-change-children = Затронутые подучастки: { $arg0 }
plot-permission-change-confirm = Применить изменения
plot-permission-change-granted = Выдано: { $arg0 }
plot-permission-change-members = Затронутые участники: { $arg0 }
plot-permission-change-more = Ещё { $arg0 }
plot-permission-change-no-effect = Действующие права не меняются. Явные настройки заменят будущие наследуемые правила.
plot-permission-change-none = Нет
plot-permission-change-revoked = Отозвано: { $arg0 }
plot-permission-change-subject = Объект: { $arg0 }
plot-permission-change-title = Подтверждение изменения прав
plot-permission-create-subplot = Создать подучасток
plot-permission-decoration = Размещать и менять декорации
plot-permission-default-status = По умолчанию ({ $arg0 })
plot-permission-delete = Удалять участок
plot-permission-door = Двери и калитки
plot-permission-entity-damage = Наносить урон сущностям
plot-permission-entity-storage = Открывать хранилища сущностей
plot-permission-harvest = Собирать ягоды, плоды лиан и мёд
plot-permission-ignite = Разжигать огонь
plot-permission-item-drop = Выбрасывать предметы
plot-permission-item-pickup = Подбирать предметы с земли
plot-permission-manage-area = Изменять границы
plot-permission-manage-members = Управлять участниками
plot-permission-manage-permissions = Управлять правами
plot-permission-manage-settings = Изменять настройки участка
plot-permission-management = Настройки и владение участком
plot-permission-modify = Изменять блоки и удобрять
plot-permission-operation = Действие
plot-permission-personal = Личные права
plot-permission-personal-source = Личная настройка
plot-permission-place = Размещать блоки и транспорт
plot-permission-reset = Восстановить правила
plot-permission-result = Статус
plot-permission-ride = Ездить на транспорте и животных
plot-permission-select-state = Выберите состояние: { $arg0 }
plot-permission-sign = Редактировать таблички
plot-permission-source-group = Права группы
plot-permission-source-local = Локальная настройка
plot-permission-source-owner = Право владельца на восстановление
plot-permission-source-parent = Родительский участок
plot-permission-source-role = Правило роли
plot-permission-state-default = По умолчанию
plot-permission-switch = Кнопки, рычаги и плиты
plot-permission-trade = Торговать с жителями
plot-permission-transfer = Передавать владение
plot-permission-workstation = Использовать объекты и рабочие блоки
plot-pos1 = Первая точка
plot-pos2 = Вторая точка
plot-private = Частный проект
plot-public = Общественный проект
plot-public-set = Тип проекта: { $arg0 }.
plot-region-adjust = Изменить этот прямоугольник
plot-region-apply = Завершить изменение прямоугольника
plot-region-cancel = Отменить изменение прямоугольника
plot-region-changed = Выделение изменилось. Выберите прямоугольник заново.
plot-region-delete = Удалить этот прямоугольник
plot-region-delete-confirm = Удалить прямоугольник №{ $arg0 } из черновика? Постройки останутся. Границы изменятся только после сохранения; действие можно отменить.
plot-region-edit-hint = Измените два угла этого прямоугольника через //pos1 и //pos2. Остальные не изменятся. Завершите изменение, затем сохраните участок.
plot-region-finish-first = Сначала завершите или отмените изменение текущего прямоугольника.
plot-region-open = Управление прямоугольниками ({ $arg0 })
plot-region-select-hint = Выберите прямоугольник по номеру для фокуса и выделения. Серые линии показывают текущую страницу; после объединения номера могут измениться.
plot-renamed = Проект переименован.
plot-role-admin = управляющий участком
plot-role-changed-for-you = Ваша роль на участке { $arg0 } теперь: { $arg1 }.
plot-role-collaborator = соавтор
plot-role-owner = владелец { -plot(case: "genitive") }
plot-role-parent-owner = владелец родительского участка
plot-role-server-staff = администрация сервера
plot-selection-add = Добавить текущий параллелепипед
plot-selection-clear = Заново выбрать две точки
plot-selection-empty = Отметьте два угла командами //pos1 и //pos2.
plot-selection-exit = Завершить редактирование / просмотр
plot-selection-fit = Восстановить сохранённые границы
plot-selection-incomplete = Одна точка отмечена. Отметьте вторую; до отправки участок не изменится.
plot-selection-mode = Выбрать точки в мире
plot-selection-no-redo = Нет изменения выделения для повтора.
plot-selection-no-undo = Нет предыдущего изменения выделения для отмены.
plot-selection-open = Изменить границы
plot-selection-redo = Повторить последний шаг
plot-selection-replace = Заменить текущим параллелепипедом
plot-selection-session-hint = Присесть+F или /plot edit для возврата. Выход через 20 минут бездействия.
plot-selection-size = Выделение: { $arg0 } × { $arg1 } × { $arg2 } блоков (X/Y/Z)
plot-selection-stopped = Выделение завершено. Черновики сохраняются отдельно для каждого участка.
plot-selection-subtract = Вычесть текущий параллелепипед
plot-selection-undo = Отменить последний шаг
plot-subplot-created = Создан дочерний участок { $arg0 } · { $arg1 } блоков²
plot-subplot-created-for-you = Теперь вы владелец участка { $arg0 } внутри { $arg1 }.
plot-subplot-owner-changed = Участок { $arg0 } теперь принадлежит { $arg1 }.
plot-subplot-parent-transferred = Владелец родительского участка для вашего участка { $arg0 } сменился на { $arg1 }.
plot-subplot-released = Дочерний участок { $arg0 } освобождён; его область по-прежнему защищена родительским участком.
plot-title = Участки
plot-transferred = Участок { $arg0 } передан игроку { $arg1 }.
plot-transferred-to-you = Участок { $arg0 } передан вам. Отправитель: { $arg1 }.

## World · Vehicles

vehicle-admin-help = /vehicle — { vehicle-model-title }
    Модели: model | model help
    Захват: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Транспорт: spawn <model> [x y z] | reload
vehicle-command-description = Управлять транспортом из сущностей отображения
vehicle-controls = { $arg0 }: W/S и A/D — управление; колесо — передачи или газ; смена рук — режим; приседание — выход. /vehicle help · /vehicle dashboard — панель управления.
vehicle-dash-assisted = Помощь в стабилизации
vehicle-dash-automatic = Автоматический режим
vehicle-dash-contact = Опора на грунт / воду: { $arg0 }
vehicle-dash-details = Подробности
vehicle-dash-down = Понизить передачу −
vehicle-dash-drive = Управление
vehicle-dash-energy = Расход { $arg0 } ед./мин · запас хода ≈ { $arg1 } мин / { $arg2 } км
vehicle-dash-engine-start = Запустить двигатель
vehicle-dash-engine-stop = Заглушить двигатель
vehicle-dash-fuel = Топливо { $arg0 } / { $arg1 } ед. · { $arg2 } %
vehicle-dash-gear = Передача { $arg0 }
vehicle-dash-gearbox = Коробка передач
vehicle-dash-handbrake = Парковка / ручной тормоз: { $arg0 }
vehicle-dash-heading = Курс { $arg0 }°
vehicle-dash-health = Состояние { $arg0 } % · пассажиры { $arg1 } / { $arg2 }
vehicle-dash-input = Газ { $arg0 } % · тормоз { $arg1 } % · руль { $arg2 } %
vehicle-dash-manual = Ручной режим
vehicle-dash-mode-switch = Переключить режим: { $arg0 }
vehicle-dash-position = Начало координат { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Центрировать панель
vehicle-dash-rpm = Обороты { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Скорость { $arg0 } км/ч · предел { $arg1 }
vehicle-dash-throttle-down = Газ −5 %
vehicle-dash-throttle-up = Газ +5 %
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Без помощи
vehicle-dash-unavailable = Управление недоступно; откройте панель с водительского места.
vehicle-dash-up = Повысить передачу +
vehicle-dash-vertical = Высота { $arg0 } м · вертикальная скорость { $arg1 } м/с
vehicle-error = Ошибка операции с транспортом: { $arg0 }
vehicle-group-changed = Структура группы изменилась. Выберите её заново перед импортом.
vehicle-group-invalid = В группе есть неподдерживаемые, управляемые, отсутствующие или удалённые сущности. Все должны быть в этом мире в пределах 32 блоков.
vehicle-group-limit = Можно импортировать не более 128 элементов отображения и 256 участников группы.
vehicle-group-not-found = Группа не найдена. Выберите группу, наведитесь на сущность отображения блока или предмета либо укажите UUID сущности.
vehicle-group-selected = Выбрана группа: { $arg0 ->
        [one] { $arg0 } сущность отображения
        [few] { $arg0 } сущности отображения
        [many] { $arg0 } сущностей отображения
       *[other] { $arg0 } сущности отображения
    } · корень { $arg1 }
vehicle-help = /vehicle — Управление транспортом
    Посадка: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Движение: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    Панель: hud [on/off] | dashboard [drive/gearbox/details/off]
    Обслуживание: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } уз. · тяга { $arg1 } % · курс { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } км/ч · высота { $arg1 } м · набор { $arg2 } м/с · газ { $arg3 } % · { $arg4 }
vehicle-hud-status = Топливо { $arg0 } % · состояние { $arg1 } % · { $arg2 }
vehicle-model-add = Добавить компонент
vehicle-model-add-here = Добавить опору у моих ног
vehicle-model-anchor = Поместить начало предпросмотра сюда, по направлению взгляда
vehicle-model-animation = Анимация
vehicle-model-apply = Применить / подтвердить
vehicle-model-auto = Пересчитать границы столкновений
vehicle-model-block = Блок
vehicle-model-center-here = Установить центр масс у моих ног
vehicle-model-center-look = Установить центр масс на поверхность под прицелом
vehicle-model-check = Проверить структуру и параметры
vehicle-model-checked = Структура и параметры верны. При сохранении также проверяются данные отображения, существующий транспорт и столкновения с миром.
vehicle-model-clone = Клонировать модель
vehicle-model-colliders = Области столкновений
vehicle-model-copy = Копировать и изменить (копии сидений — пассажирские)
vehicle-model-corner-cancel = Отменить незавершённую область столкновений
vehicle-model-corner-first = Отметить первый угол у моих ног
vehicle-model-corner-first-look = Отметить первый угол на поверхности под прицелом
vehicle-model-corner-hint = Первый угол отмечен. Перейдите к противоположному углу с учётом высоты и введите /vehicle model collider corner2. Область выровнена по осям модели.
vehicle-model-corner-required = Сначала отметьте первый угол: /vehicle model collider corner1.
vehicle-model-corner-second = Использовать ноги как противоположный угол и добавить область
vehicle-model-corner-second-look = Использовать поверхность под прицелом как противоположный угол
vehicle-model-create = Создать { -vehicle-model(case: "accusative") }
vehicle-model-delete = Удалить модель
vehicle-model-delete-warning = Удалить модель навсегда? Нельзя удалить модель, пока существует использующий её транспорт.
vehicle-model-deleted = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } удалён
        [feminine] { -vehicle-model } удалена
       *[other] { -vehicle-model } удалено
    }
vehicle-model-discard = Отбросить черновик
vehicle-model-discard-warning = Отбросить все несохранённые изменения? Это действие нельзя отменить.
vehicle-model-discarded = Черновик отброшен
vehicle-model-draft = Несохранённый черновик
vehicle-model-driver = Водительское место
vehicle-model-driver-here = Добавить водительское место у моих ног
vehicle-model-edit = Изменить / продолжить черновик
vehicle-model-exit = Место выхода / точная настройка
vehicle-model-group = Добавить группу отображения Axiom
vehicle-model-help = /vehicle model — { vehicle-model-title }
    Модели: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Изменение: set <field> <value...> | part/seat/collider/support — Tab — операции и аргументы
    Черновик: preview [off] | undo | redo | save | cancel
    В мире: anchor | pick | here | look | snap [on/off] | move <index> <dx dy dz>
    { vehicle-model-position-hint }
    seat position/exit | part position | support add/set | collider position: here / look
    collider corner1/corner2 [look] | collider cancel — { vehicle-model-corner-hint }
    Другие действия: check | apply — { vehicle-model-save-continue }
vehicle-model-here = Использовать положение у моих ног
vehicle-model-history-empty = В этом направлении истории нет изменений.
vehicle-model-import = Импортировать группу отображения Axiom
vehicle-model-item = Предмет
vehicle-model-library = Сохранённые модели · черновики хранятся в памяти до сохранения
vehicle-model-look = Разместить прицеливанием (меню закроется; ПКМ — подтвердить)
vehicle-model-look-missing = Поверхность не найдена. Наведитесь на блок мира или элемент личного предпросмотра в пределах 32 блоков.
vehicle-model-manual = Ввести локальные координаты
vehicle-model-options = Параметры
vehicle-model-parameters = Параметры физики и двигателя
vehicle-model-parts = Элементы отображения
vehicle-model-passenger-here = Добавить пассажирское место у моих ног
vehicle-model-pick = Изменить элемент предпросмотра под прицелом
vehicle-model-pick-hint = Откройте предпросмотр и наведитесь на блок или предмет либо сначала выберите компонент в редакторе.
vehicle-model-place-cancelled = Размещение отменено: истёк срок, изменилось состояние редактора или выполнена отмена.
vehicle-model-place-hint = Размещение активно 30 секунд. Наведитесь на блок или предпросмотр; ПКМ основной рукой — подтвердить, приседание + ПКМ — отменить.
vehicle-model-position = Положение / точная настройка
vehicle-model-position-hint = Локальные X/Y/Z · here — у ног, look — на поверхности под прицелом · сетка использует шаг настройки.
vehicle-model-preview = Личный предпросмотр
vehicle-model-preview-failed = Предпросмотр недоступен; черновик сохранён: { $arg0 }
vehicle-model-preview-hint = Личный предпросмотр включён. Удаляется через пять минут без редактирования; /vehicle model preview off для закрытия.
vehicle-model-redo = Повторить отменённое изменение
vehicle-model-remove = Удалить компонент
vehicle-model-remove-warning = Удалить компонент? Следующие индексы сдвинутся; действие можно отменить.
vehicle-model-rotate = Повернуть компонент
vehicle-model-rotate-hint = Относительный поворот: X — тангаж, Y — рыскание, Z — крен. Положение не меняется.
vehicle-model-save = Проверить и сохранить
vehicle-model-save-continue = Сохранить и продолжить
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } сохранён
        [feminine] { -vehicle-model } сохранена
       *[other] { -vehicle-model } сохранено
    }
vehicle-model-scale = Масштабировать компонент
vehicle-model-scale-all = Масштаб всех осей × { $arg0 }
vehicle-model-scale-hint = Умножение или деление длин локальных осей, а не размеров габаритов в мире.
vehicle-model-seats = Сиденья и выходы
vehicle-model-selected = Импортировать выбранные элементы
vehicle-model-size = Изменить размер области столкновений
vehicle-model-size-hint = Полные ширина, высота и длина; центр остаётся на месте.
vehicle-model-snap-off = Привязка к сетке: выключена
vehicle-model-snap-on = Привязка к сетке: включена
vehicle-model-step = Изменить шаг настройки
vehicle-model-supports = Точки опоры на грунт
vehicle-model-text = Текст
vehicle-model-title = Управление моделями транспорта
vehicle-model-tools = Инструменты в мире
vehicle-model-transform-step = Изменить шаг настройки
vehicle-model-undo = Отменить последнее изменение
vehicle-result = Транспорт: { $arg0 }
vehicle-selection-info = Выделено: { $arg0 ->
        [one] { $arg0 } сущность отображения
        [few] { $arg0 } сущности отображения
        [many] { $arg0 } сущностей отображения
       *[other] { $arg0 } сущности отображения
    } · группа { $arg1 } · начало { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Столкновение / препятствие
vehicle-status-damage = Критическое повреждение транспорта
vehicle-status-disabled = Транспорт выведен из строя
vehicle-status-engine-off = Двигатель выключен
vehicle-status-engine-on = Двигатель работает
vehicle-status-gear-limit = Достигнут предел управления
vehicle-status-grounded = Судно на мели
vehicle-status-low-fuel = Мало топлива
vehicle-status-manual-only = Сначала включите ручной режим
vehicle-status-no-fuel = Нет топлива
vehicle-status-overrev = Понижение передачи превысит допустимые обороты
vehicle-status-shifting = Переключение передачи
vehicle-status-stall = Двигатель заглох
vehicle-status-stop-first = Остановитесь перед сменой направления или парковкой
vehicle-value-off = Выкл.
vehicle-value-on = Вкл.

## World · Elevators, regeneration and spawn

async-regen-already-running = Восстановление уже выполняется; используйте /regen status или /regen cancel
async-regen-apply-expired = Нет действительного плана; сначала выполните /regen preview
async-regen-apply-plan = Применить сохранённый план
async-regen-apply-started = Применяется { $arg0 } к сохранённому выделению: { $arg1 }
async-regen-awaiting-apply = План ожидает применения: { $arg0 } · { $arg1 } · истекает через { $arg2 ->
        [one] { $arg2 } секунду
        [few] { $arg2 } секунды
        [many] { $arg2 } секунд
       *[other] { $arg2 } секунды
    }
async-regen-block-preview-progress = предпросмотр блоков · проверено чанков: { $arg0 }/{ $arg1 } · изменений: { $arg2 } · блок-сущностей под угрозой: { $arg3 }
async-regen-block-preview-summary = Предпросмотр: { $arg0 ->
        [one] { $arg0 } изменение блока
        [few] { $arg0 } изменения блоков
        [many] { $arg0 } изменений блоков
       *[other] { $arg0 } изменения блока
    } для { $arg2 }; риск перезаписи: { $arg1 ->
        [one] { $arg1 } существующая блок-сущность
        [few] { $arg1 } существующие блок-сущности
        [many] { $arg1 } существующих блок-сущностей
       *[other] { $arg1 } существующей блок-сущности
    }
async-regen-cancelled = Восстановление отменено. Уже применённые изменения ({ $arg0 }) останутся в мире
async-regen-command-description = Асинхронно восстановить выделение WorldEdit
async-regen-completed = Восстановление завершено: изменено блоков: { $arg0 }; время: { $arg1 }
async-regen-failed = Восстановление остановлено из-за внутренней ошибки. Уже применённые изменения ({ $arg0 }) останутся в мире
async-regen-filter-all = все типы блоков
async-regen-invalid-blocks = Неизвестные типы или не блоки: { $arg0 }
async-regen-not-running = У вас нет выполняющегося восстановления
async-regen-nothing-to-apply = Выделение уже соответствует этому плану; применять нечего
async-regen-plan-cancel = Отмена
async-regen-plan-dialog-title = Применить план регенерации
async-regen-preview-cancelled = Предпросмотр отменён; данные мира не изменялись
async-regen-preview-started = Предпросмотр начат: выделение { $arg0 ->
        [one] { $arg0 } блок
        [few] { $arg0 } блока
        [many] { $arg0 } блоков
       *[other] { $arg0 } блока
    } · область { $arg1 ->
        [one] { $arg1 } чанк
        [few] { $arg1 } чанка
        [many] { $arg1 } чанков
       *[other] { $arg1 } чанка
    } · режим { $arg2 }
async-regen-progress = { $arg0 } · запланировано чанков: { $arg1 }/{ $arg2 } · применено изменений: { $arg3 }
async-regen-safety-note = Вне сохранённого выделения ничего не изменится. Постройки внутри могут быть перезаписаны, а Vanilla не различает блоки игроков. Проверьте визуализацию и сделайте резервную копию перед применением.
async-regen-scope-blocks = перегенерировать блоки, соответствующие { $arg0 }
async-regen-selection-required = Сначала завершите выделение WorldEdit
async-regen-skipped-ungenerated = Пропущены ещё не созданные целевые чанки: { $arg0 ->
        [one] { $arg0 } чанк
        [few] { $arg0 } чанка
        [many] { $arg0 } чанков
       *[other] { $arg0 } чанка
    }. Они не создавались и не изменялись.
async-regen-start-failed = Не удалось запустить восстановление; подробности смотрите в журнале сервера
async-regen-state-applying = применение изменений
async-regen-state-cancelled = отменено
async-regen-state-completed = завершено
async-regen-state-failed = ошибка
async-regen-state-generating = генерация и сравнение чанков
async-regen-state-preparing = подготовка исходного мира
async-regen-status = { $arg0 } · запланировано чанков: { $arg1 }/{ $arg2 } · применено изменений: { $arg3 } из { $arg4 }
async-regen-structure-metadata-applied = Перенесено начал структур: { $arg0 }, ссылок чанков: { $arg1 }; /locate и зависящий от структур спавн смогут их распознавать
async-regen-upgrade-apply-progress = { $arg0 } · запланировано чанков: { $arg1 }/{ $arg2 } · применено блоков: { $arg3 }, ячеек биомов: { $arg4 }
async-regen-upgrade-boundary-note = Ячейки биомов на границе ({ $arg0 }) будут пропущены, чтобы не менять область вне выделения
async-regen-upgrade-completed = Обновление мира завершено: изменено блоков — { $arg0 }, ячеек биомов — { $arg1 }; время: { $arg2 }
async-regen-upgrade-preview-progress = предпросмотр · проверено чанков: { $arg0 }/{ $arg1 } · изменений блоков: { $arg2 } · ячеек биомов: { $arg3 } · структур: { $arg4 }
async-regen-upgrade-preview-summary = Предпросмотр: { $arg0 ->
        [one] { $arg0 } изменение блока
        [few] { $arg0 } изменения блоков
        [many] { $arg0 } изменений блоков
       *[other] { $arg0 } изменения блока
    }, { $arg1 ->
        [one] { $arg1 } ячейка биома
        [few] { $arg1 } ячейки биома
        [many] { $arg1 } ячеек биома
       *[other] { $arg1 } ячейки биома
    }, { $arg2 ->
        [one] { $arg2 } структура
        [few] { $arg2 } структуры
        [many] { $arg2 } структур
       *[other] { $arg2 } структуры
    } (полный перенос метаданных возможен: { $arg3 }); риск перезаписи: { $arg4 ->
        [one] { $arg4 } существующая блок-сущность
        [few] { $arg4 } существующие блок-сущности
        [many] { $arg4 } существующих блок-сущностей
       *[other] { $arg4 } существующей блок-сущности
    }
async-regen-upgrade-scope-biomes = только биомы
async-regen-upgrade-scope-terrain = рельеф, структуры и биомы
async-regen-usage = Использование: /regen preview blocks [типы...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Визуализация в { $arg0 }: голубой — граница выделения, оранжевый — выборка изменяемых блоков, красный — блок-сущность под риском. Эти частицы видны только вам.
async-regen-visualization-selection = Выделение в { $arg0 }: голубые частицы отмечают границу и видны только вам.
async-regen-world-edit-required = Для чтения выделения требуется WorldEdit
iron-elevator-down = ↓ Присесть
iron-elevator-up = ↑ Прыжок
spawn-beacon-color-command-description = Переключить смену цвета маяка на спауне
spawn-beacon-color-disabled = Смена цвета маяка на спауне выключена
spawn-beacon-color-enabled = Смена цвета маяка на спауне включена
spawn-command-description = Вернуться на спаун
spawn-world-missing = Мир спауна недоступен

## Entertainment · Music

music-action-add-queue = добавить в очередь
music-action-play-nearby = воспроизвести на ближайшем проигрывателе
music-action-play-now = воспроизвести сейчас на этом проигрывателе
music-action-take-disc = получить пластинку
music-added-to-queue-rich = Добавлено в очередь: { $music }
music-album = Альбом: { $arg0 }
music-all-in-queue = Все композиции уже есть в очередье
music-artist = Исполнитель: { $arg0 }
music-back = Назад
music-browser-left-click = Левая кнопка
music-browser-right-click = Правая кнопка
music-cache-clear-done = Кэш онлайн-музыки очищен
music-cache-clear-started = Очистка кэша онлайн-музыки...
music-cache-operation-failed = Ошибка операции с кэшем музыки: { $arg0 }
music-cache-status = Кэш онлайн-музыки: файлов — { $arg0 }, { $arg1 } / { $arg2 }
music-cached = В кэше - готово к воспроизведению
music-clear-queue = Очистить очередь
music-clear-queue-done = Очередь очищена ({ $arg0 ->
        [one] { $arg0 } композиция
        [few] { $arg0 } композиции
        [many] { $arg0 } композиций
       *[other] { $arg0 } композиции
    })
music-command-description = Локальная, онлайн- и NBS-музыка для проигрывателя
music-disc-left = ЛКМ: взять пластинку
music-disc-right = ПКМ: включить на ближайшем проигрывателе
music-duplicate-in-queue = Эта композиция уже есть в очередье
music-duration = Длительность: { $arg0 }
music-empty-library = Музыкальная библиотека пуста
music-empty-library-description = Нет доступных локальных аудиофайлов или композиций NBS
music-empty-nbs = Композиции NBS не найдены
music-empty-nbs-description = Переключитесь на всю музыку или добавьте файл NBS
music-empty-playlist = В этом плейлисте нет доступных композиций
music-empty-playlist-description = Убедитесь, что плейлист публичный и выбрана правильная платформа
music-empty-search = Подходящие композиции не найдены
music-empty-search-description = Попробуйте другой запрос или проверьте состояние пользовательских источников
music-finished = Завершено
music-format = Формат: { $arg0 }
music-inventory-full = Инвентарь заполнен. Не удалось выдать пластинку
music-jukebox-audible-field-conflict = Другой проигрыватель воспроизводит другую песню в пересекающейся зоне отчётной слышимости
music-jukebox-music-mode = Режим музыки
music-jukebox-play-order = Порядок воспроизведения
music-jukebox-range = Радиус воспроизведения
music-jukebox-range-value = Сейчас: { $arg0 ->
        [one] { $arg0 } блок
        [few] { $arg0 } блока
        [many] { $arg0 } блоков
       *[other] { $arg0 } блока
    }
music-jukebox-rhythm-mode = Режим ритма
music-jukebox-too-far = Вы слишком далеко от проигрывателя (не более { $arg0 ->
        [one] { $arg0 } блока
        [few] { $arg0 } блоков
        [many] { $arg0 } блоков
       *[other] { $arg0 } блока
    })
music-jukebox-tp-hover = Нажмите, чтобы телепортироваться к проигрывателю
music-jukebox-unavailable = Проигрыватель удалён или его чанк недоступен
music-jukebox-usage-mode = Режим проигрывателя
music-jukebox-volume = Громкость проигрывателя
music-jukebox-volume-value = Сейчас: { $arg0 }%
music-library-button = Музыкальная библиотека
music-library-random-mode = Случайно из фонотеки
music-library-random-mode-desc = Воспроизводить случайные композиции из всей фонотеки без немедленного повтора
music-loading-title = Загрузка
music-menu-nbs-title = Музыка NBS · Страница { $arg0 }/{ $arg1 }
music-menu-playlist-title = Плейлист: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = Поиск NBS: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-title = Поиск: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-title = Пластинки · Страница { $arg0 }/{ $arg1 }
music-mode-switched = Выбран режим «{ $arg0 }»
music-nearest-jukebox-missing = В радиусе { $arg0 ->
        [one] { $arg0 } блока
        [few] { $arg0 } блоков
        [many] { $arg0 } блоков
       *[other] { $arg0 } блока
    } нет проигрывателя
music-next-page = Следующая страница
music-no-disc = Нет пластинки
music-no-files = Нет доступных музыкальных файлов
music-no-search-results-rich = Композиции со строкой «{ $keyword }» не найдены
music-now-playing-rich = Сейчас играет: { $music } · проигрыватель { $location }
music-online-partial-failure = Ошибки источников: { $arg0 ->
        [one] { $arg0 } запрос
        [few] { $arg0 } запроса
        [many] { $arg0 } запросов
       *[other] { $arg0 } запроса
    }; показаны доступные результаты
music-online-rate-limited = Слишком много запросов музыки. Повторите через { $arg0 ->
        [one] { $arg0 } секунду
        [few] { $arg0 } секунды
        [many] { $arg0 } секунд
       *[other] { $arg0 } секунды
    }.
music-online-searching = Поиск «{ $arg0 }» в локальных и онлайн-источниках...
music-original-author = Автор оригинала: { $arg0 }
music-page-out-of-range = Такой страницы нет. Последняя страница: { $arg0 }
music-page-total = Всего: { $arg0 ->
        [one] { $arg0 } композиция
        [few] { $arg0 } композиции
        [many] { $arg0 } композиций
       *[other] { $arg0 } композиции
    }
music-page-usage-desc = Перейти на страницу
music-partial-results = Ошибки источников: { $arg0 ->
        [one] { $arg0 } запрос
        [few] { $arg0 } запроса
        [many] { $arg0 } запросов
       *[other] { $arg0 } запроса
    }; показаны доступные результаты
music-paused = Приостановлено
music-plasmo-voice-unavailable = Трек запущен, но вы не можете его услышать, потому что Plasmo Voice не установлен, не подключён или отключён
music-play-next = Следующая композиция
music-play-random = Случайная композиция
music-playback-failed = Не удалось воспроизвести «{ $arg0 }». Проверьте источник или музыкальный файл и повторите попытку.
music-playback-status = Состояние воспроизведения: { $arg0 }
music-playing = Воспроизводится
music-playing-now-rich = Сейчас играет: { $music }
music-playlist-import-button = Импортировать плейлист
music-playlist-import-done = «{ $arg0 }» импортирован: { $arg1 } из { $arg2 ->
        [one] { $arg2 } композиции
        [few] { $arg2 } композиций
        [many] { $arg2 } композиций
       *[other] { $arg2 } композиции
    }.
music-playlist-import-failed = Не удалось импортировать плейлист: { $arg0 }
music-playlist-import-prompt = Выберите платформу, затем вставьте ID или ссылку публичного плейлиста:
music-playlist-import-usage-desc = Импортировать публичный плейлист с другой музыкальной платформы
music-playlist-importing = Импорт плейлиста...
music-playlist-reference-prompt = Вставьте ID или ссылку на публичный плейлист
music-prev-page = Предыдущая страница
music-queue-empty = Очередь пуст
music-queue-move-down = Переместить ниже
music-queue-move-up = Переместить выше
music-queue-remove = Удалить песню
music-queue-summary = Очередь: { $arg0 ->
        [one] { $arg0 } композиция
        [few] { $arg0 } композиции
        [many] { $arg0 } композиций
       *[other] { $arg0 } композиции
    }
music-queue-summary-description = Страница { $arg0 }/{ $arg1 }
music-random-add = Добавить случайную композицию
music-random-added-to-queue-rich = Случайная композиция добавлена в очередь: { $music }
music-random-disc = Случайная пластинка
music-random-disc-got-rich = Получена случайная пластинка: { $music }
music-random-mode = Случайный список
music-random-mode-desc = Воспроизводить список случайно без немедленного повтора одной композиции
music-rank-down = Перемещено ниже
music-rank-up = Перемещено выше
music-reload-done = Перезагрузка завершена: { $arg0 ->
        [one] { $arg0 } локальная композиция
        [few] { $arg0 } локальные композиции
        [many] { $arg0 } локальных композиций
       *[other] { $arg0 } локальной композиции
    }; источники LX { $arg1 }/{ $arg2 }; подписок проверено { $arg3 }, обновлено { $arg4 }
music-reload-failed = Не удалось перезагрузить музыку: { $arg0 }
music-reload-issues = Проблемы при перезагрузке: { $arg0 }
music-reload-partial = Перезагрузка завершена с сохранением прежнего состояния: { $arg0 ->
        [one] { $arg0 } локальная композиция
        [few] { $arg0 } локальные композиции
        [many] { $arg0 } локальных композиций
       *[other] { $arg0 } локальной композиции
    }; источники LX { $arg1 }/{ $arg2 }; подписок проверено { $arg3 }, обновлено { $arg4 }
music-reload-started = Перезагрузка музыкальных файлов и пользовательских источников...
music-remote-source-error = ошибка обновления: { $arg0 }
music-remote-source-import-done = Подписка на источник LX импортирована: { $arg0 }
music-remote-source-import-failed = Не удалось импортировать источник LX: { $arg0 }
music-remote-source-import-started = Импорт подписки на источник LX...
music-remote-source-remove-done = Подписка на источник LX удалена: { $arg0 }
music-remote-source-remove-failed = Не удалось удалить подписку на источник LX: { $arg0 }
music-remote-source-remove-not-found = Подписка на источник LX не найдена: { $arg0 }
music-remote-source-update-done = Проверка: { $arg0 ->
        [one] { $arg0 } подписка
        [few] { $arg0 } подписки
        [many] { $arg0 } подписок
       *[other] { $arg0 } подписки
    }; обновлено: { $arg1 }; ошибок: { $arg2 }.
music-remote-source-update-failed = Не удалось обновить источник LX: { $arg0 }
music-remote-source-update-started = Проверка импортированных подписок на источники LX...
music-remote-source-updated-at = обновлено: { $arg0 }
music-remote-sources-title = Импортированные подписки на источники LX: { $arg0 }
music-removed-from-queue-rich = Удалено из очередьа: { $music }
music-repeat-one-mode = Повтор одной композиции
music-repeat-one-mode-desc = Повторять текущую композицию до ручного переключения
music-request-error = Ошибка запроса: { $arg0 }
music-rhythm-animation-offset = Сдвиг анимации { $arg0 } мс
music-rhythm-calibration = Калибровка задержки
music-rhythm-calibration-accumulating = Проверяем стабильность… продолжайте играть
music-rhythm-calibration-adapting = Подстраиваемся под ритм… продолжайте играть
music-rhythm-calibration-complete = Тест задержки завершён · ритм-игра готова
music-rhythm-calibration-done = Тест задержки завершён · настройки сохранены
music-rhythm-calibration-failed = Звук теста прервался. Тест безопасно завершён; запустите его снова
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Клавиатура визуально → Мышь визуально
music-rhythm-calibration-listen = Сначала прослушайте созданный ритм один раз · пока не нажимайте
music-rhythm-calibration-minecraft-instruction = Следуйте ритму Minecraft Note Block и нажимайте 1–4
music-rhythm-calibration-no-input = Ввод не обнаружен. Проверьте клавиши и громкость и продолжайте играть
music-rhythm-calibration-out-of-range = Измеренная задержка вне поддерживаемого диапазона. Проверьте настройки звука
music-rhythm-calibration-plasmo-instruction = Теперь повторяйте: следуйте ритму Plasmo Voice и нажимайте 1–4 на каждый удар
music-rhythm-calibration-plasmo-ready = Тест Minecraft Note Block завершён; сначала прослушайте тот же ритм через Plasmo Voice
music-rhythm-calibration-pointer-instruction = Нажимайте левую или правую кнопку мыши, когда нота пересекает линию.
music-rhythm-calibration-prompt-confirm = Начать полный тест
music-rhythm-calibration-prompt-confirm-hint = Авто возврат после завершения.
music-rhythm-calibration-prompt-later = Не сейчас
music-rhythm-calibration-prompt-reason = Сначала пройдите тест задержки.
music-rhythm-calibration-prompt-title = Перед игрой требуется тест задержки
music-rhythm-calibration-realigning = Звуковой поток прервался. Демонстрация повторится, готовые измерения сохранятся
music-rhythm-calibration-required = Перед запуском ритм-игры пройдите полный тест задержки
music-rhythm-calibration-reset-done = Калибровка задержки сброшена
music-rhythm-calibration-result-hold = Все тесты завершены. Проверьте результат перед автоматическим возвратом
music-rhythm-calibration-stage-minecraft = Нотный блок Minecraft
music-rhythm-calibration-stage-plasmo = Тест звука Plasmo Voice и ввода
music-rhythm-calibration-stage-pointer = Мышь
music-rhythm-calibration-stage-visual = Тест изображения и ввода
music-rhythm-calibration-visual-instruction = Нажимайте 1–4, когда нота пересекает линию.
music-rhythm-calibration-visual-preview = Просмотрите один полный цикл без ввода · начните после смены инструкции
music-rhythm-calibration-voice-required = Для полного теста Plasmo Voice должен быть подключён и включён
music-rhythm-chart-preparing = Подготовка полной карты…
music-rhythm-combo = Комбо ×{ $arg0 }
music-rhythm-difficulty-easy = Легко
music-rhythm-difficulty-expert = Эксперт
music-rhythm-difficulty-hard = Сложно
music-rhythm-difficulty-normal = Нормально
music-rhythm-exit = Выйти из ритм-игры
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
music-rhythm-get-ready = Приготовьтесь
music-rhythm-go = СТАРТ!
music-rhythm-good = Хорошо
music-rhythm-great = Отлично
music-rhythm-latency = Задержка { $arg0 } мс
music-rhythm-latency-test = Полный тест задержки
music-rhythm-miss = Промах
music-rhythm-mode-falling = Падающие ноты
music-rhythm-mode-radial = Радиальный натиск
music-rhythm-mode-spatial-aim = Пространственное прицеливание
music-rhythm-network-too-high = Сетевая задержка слишком высока для честной оценки ({ $arg0 } мс; предел { $arg1 } мс)
music-rhythm-network-warning = Сетевая задержка нестабильна ({ $arg0 } мс). Сеанс завершится, если она останется выше { $arg1 } мс
music-rhythm-no-beats = В этой песне не найдено подходящих ритмов
music-rhythm-perfect = Идеально
music-rhythm-preparing = Подождите, пока песня полностью кэшируется и будет проанализирован её ритм
music-rhythm-ready = Полная карта готова — начало с первого удара
music-rhythm-requires-playback = Сначала включите песню на этом проигрывателе
music-rhythm-reset-calibration = Сбросить калибровку задержки
music-rhythm-results = Результаты
music-rhythm-score = Счёт: { $arg0 }
music-rhythm-select-track = Выбрать трек для ритм-игры
music-rhythm-spatial-no-room = Недостаточно свободного места. Перейдите на более открытую площадку.
music-rhythm-spatial-slider-hint = Следуйте за движущейся звездой и нажимайте каждую точку · не удерживайте
music-rhythm-start-game = Начать игру
music-rhythm-waiting-for-player = Ожидание игрока для запуска ритм-игры
music-sample-rate = Частота дискретизации: { $arg0 }
music-search-button = Найти композицию
music-search-keyword-required = Поисковый запрос не может быть пустым
music-search-keyword-too-long = Слишком длинный запрос. Лимит: { $arg0 ->
        [one] { $arg0 } символ
        [few] { $arg0 } символа
        [many] { $arg0 } символов
       *[other] { $arg0 } символа
    }.
music-search-usage-desc = Найти композицию
music-section-all = Вся музыка
music-section-nbs = Музыка NBS
music-select-music = Выбрать музыку
music-sequential-mode = Повтор списка
music-sequential-mode-desc = Воспроизводить список по порядку и начинать заново с начала
music-size = Размер: { $arg0 }
music-sort-button = Сортировка
music-sort-current = Текущий: { $arg0 }
music-sort-default = По умолчанию
music-sort-most-played = Самые популярные
music-sort-recently-played = Недавно играли
music-sound-settings = Настройки звука
music-source-actions = действия: { $arg0 }
music-source-available = доступен
music-source-capabilities = возможности: { $arg0 }
music-source-error = ошибка: { $arg0 }
music-source-failures = ошибок подряд: { $arg0 }
music-source-retry = повтор через { $arg0 ->
        [one] { $arg0 } секунду
        [few] { $arg0 } секунды
        [many] { $arg0 } секунд
       *[other] { $arg0 } секунды
    }
music-source-unavailable = недоступен
music-sources-empty = Пользовательские источники музыки LX не найдены
music-sources-title = Пользовательские источники музыки LX: { $arg0 }
music-stop-eject = Остановить и извлечь
music-stop-eject-done = Воспроизведение остановлено, пластинка извлечена
music-stopped = Остановлено
music-track-details = Сведения о треке
music-unavailable-disc = Недоступная пластинка
music-unavailable-disc-description = Этой пластинки MIK нет в текущем каталоге, но воспроизведение можно остановить, а пластинку извлечь

## Entertainment · Anniversary

anniversary-accepted = Вы участвуете в розыгрыше. Первый раунд откроется после 4 часов онлайн вне AFK.
anniversary-admin-audit-action-bonus = Выдана дополнительная попытка за публикацию построек
anniversary-admin-audit-action-gift = Подарен приз события: { $arg0 }
anniversary-admin-audit-action-removal = Прежнее участие администратора удалено; возвращено: { $arg0 ->
        [one] { $arg0 } приз
        [few] { $arg0 } приза
        [many] { $arg0 } призов
       *[other] { $arg0 } приза
    }.
anniversary-admin-audit-action-unknown = Неизвестное действие: { $arg0 }
anniversary-admin-audit-empty = Записей аудита управления пока нет.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Исполнитель: { $arg0 } ({ $arg1 }); цель: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Аудит управления юбилеем (показано { $arg0 }/{ $arg1 })
anniversary-admin-bonus-cancelled = Завершено подарочным набором
anniversary-admin-bonus-pending = Выдано, ещё не разыграно
anniversary-admin-bonus-used = Разыграно
anniversary-admin-boss = Управление юбилеем · Введите { $arg0 }, чтобы увидеть прогресс
anniversary-admin-commands = Команды: /anniversary winners [страница] | /anniversary audit [игрок] | /anniversary bonus <игрок>
anniversary-admin-control = Динамическое управление: выпущено { $arg0 }; доступно сейчас { $arg1 }; базовая вероятность { $arg2 }; недавняя скорость { $arg3 }
anniversary-admin-draw-bonus = Бонус за публикацию построек
anniversary-admin-draw-detail = { $arg0 }: проведено { $arg1 }; результат { $arg2 }
anniversary-admin-draw-metrics = Вероятность { $arg0 }; случайное число { $arg1 }; версия алгоритма { $arg2 }
anniversary-admin-draw-replaced = Заменено подарочным набором; запас восстановлен
anniversary-admin-draw-round = Обычный раунд { $arg0 }
anniversary-admin-event = Состояние события: { $arg0 }; период: { $arg1 }
anniversary-admin-excluded = Администраторы юбилейного события не могут участвовать в розыгрыше. Открыт обзор управления.
anniversary-admin-none = Нет
anniversary-admin-opportunities = Проведено розыгрышей: { $arg0 }; готово сейчас: { $arg1 }; получено попыток: { $arg2 }; побед/поражений: { $arg3 }
anniversary-admin-participants = Участников: { $arg0 }
anniversary-admin-player-bag = Итоговая сумка события: { $arg0 }
anniversary-admin-player-bonus = Бонус за постройки: { $arg0 }; выдан: { $arg1 }; кем: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Подробности попыток розыгрыша
anniversary-admin-player-not-found = Участник события не найден: { $arg0 }
anniversary-admin-player-outcome = Побед: { $arg0 }; поражений: { $arg1 }; проведено розыгрышей: { $arg2 }; бонус за постройки: { $arg3 }
anniversary-admin-player-progress = Вступил: { $arg0 }; зачтённое онлайн-время: { $arg1 }; проведено обычных розыгрышей: { $arg2 }; готово обычных попыток: { $arg3 }
anniversary-admin-player-title = Аудит участника: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Идёт
anniversary-admin-state-ended = Завершено
anniversary-admin-state-scheduled = Запланировано
anniversary-admin-stock = Остаток: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | всего { $arg4 }
anniversary-admin-target-excluded = { $arg0 } — администратор юбилейного события и не может получить попытку розыгрыша.
anniversary-admin-title = Обзор управления пятым юбилеем
anniversary-admin-winners-empty = Фактически разыгранных выигрышных результатов пока нет.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · разыграно { $arg3 }
anniversary-admin-winners-title = Список выдачи призов (страница { $arg0 }/{ $arg1 }, призов: { $arg2 })
anniversary-bag-empty = Выигранных призов пока нет
anniversary-bag-item-locked = Сумка события доступна только для просмотра
anniversary-bag-item-name = Приз к пятилетию · { $arg0 }
anniversary-bag-not-participating = Вы ещё не участвуете в событии. Сначала введите { $arg0 }.
anniversary-bag-title = Сумка события к пятилетию
anniversary-bag-updated = Ваш приз находится в сумке события. Введите { $arg0 }, чтобы посмотреть.
anniversary-bonus-already-granted = Для { $arg0 } бонус за публикацию построек уже выдан; повторная выдача невозможна.
anniversary-bonus-boss-ready = Дополнительный розыгрыш за публикацию построек готов · Введите { $arg0 } для розыгрыша
anniversary-bonus-broadcast-won = [Юбилейный розыгрыш] Бонус за постройки принёс приз: { $arg0 } · { $arg1 }!
anniversary-bonus-draw-not-won = Дополнительный розыгрыш за публикацию построек: в этот раз без приза.
anniversary-bonus-draw-sold-out = Дополнительный розыгрыш за публикацию построек: приз не выдан, так как призовой фонд исчерпан.
anniversary-bonus-draw-won = Дополнительный розыгрыш за публикацию построек: поздравляем, вы выиграли { $arg0 }!
anniversary-bonus-gift-pack-final = Для { $arg0 } итоговым призом уже стал подарочный набор; бонусный розыгрыш недоступен.
anniversary-bonus-grant-not-active = Награду за публикацию построек нельзя выдать вне периода события.
anniversary-bonus-grant-success = Награда за публикацию построек выдана игроку { $arg0 }.
anniversary-bonus-granted = Вы получили одну дополнительную попытку за публикацию построек. Введите /anniversary, чтобы разыграть ее.
anniversary-bonus-participant-not-found = Участник события с именем { $arg0 } не найден
anniversary-bonus-status-ready = Награда за публикацию построек: одна дополнительная попытка готова.
anniversary-bonus-status-used = Награда за публикацию построек: дополнительная попытка использована.
anniversary-boss-completed = Юбилейный розыгрыш · Оба раунда завершены
anniversary-boss-gift-pack-final = Подарочный набор получен · Введите { $arg0 }, чтобы открыть сумку события
anniversary-boss-invite = Розыгрыш к пятилетию · Введите { $arg0 } для участия
anniversary-boss-progress = Юбилейный розыгрыш { $arg0 }/{ $arg1 } · Раунд { $arg2 } через { $arg3 }
anniversary-boss-ready = Раунд { $arg0 } готов · Введите { $arg1 } для розыгрыша
anniversary-broadcast-won = [Юбилейный розыгрыш] Победа в раунде { $arg1 }: { $arg0 } · приз: { $arg2 }!
anniversary-cancel = Не сейчас
anniversary-command-description = Участвовать в розыгрыше к пятилетию сервера
anniversary-confirm = Согласиться и участвовать
anniversary-confirm-hint = Начинает учет времени онлайн вне AFK
anniversary-delivery-note = Для доставки в материковый Китай предоставьте действительный SF ID, пригодный для отправки с защитой персональных данных. Для доставки за пределы материкового Китая предоставьте настоящее имя получателя, полный адрес и действующий номер телефона. Оплатите причитающуюся часть доставки, иначе право на приз будет утрачено.
anniversary-dialog-prizes = Призы: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } и 5 { $arg3 }
anniversary-dialog-time = Период события: { $arg0 }
anniversary-dialog-title = Правила розыгрыша к пятилетию
anniversary-draw-not-won = Раунд { $arg0 }: в этот раз без приза.
anniversary-draw-sold-out = Раунд { $arg0 }: приз не выдан, так как все призы закончились.
anniversary-draw-won = Раунд { $arg0 }: поздравляем, вы выиграли { $arg1 }!
anniversary-ended = Розыгрыш к пятилетию завершен
anniversary-ended-results = Ваши результаты розыгрыша: { $arg0 }
anniversary-gift-pack-final = Подарочный набор стал вашим итоговым призом. Он заменил и вернул в запас { $arg0 ->
        [one] { $arg0 } другой приз
        [few] { $arg0 } других приза
        [many] { $arg0 } других призов
       *[other] { $arg0 } другого приза
    }; все оставшиеся розыгрыши завершены.
anniversary-gift-prize-not-owned = У вас нет этого приза события: { $arg0 }
anniversary-gift-received = { $arg0 } подарил вам { $arg1 }. Приз находится в вашей сумке события.
anniversary-gift-self = Нельзя подарить приз самому себе.
anniversary-gift-success = Приз { $arg0 } подарен игроку { $arg1 }.
anniversary-gift-usage = Использование: { $arg0 }
anniversary-not-active = Событие к пятилетию сейчас не активно
anniversary-not-started = Событие к пятилетию еще не началось. Период: { $arg0 }
anniversary-prize-badge = значок с групповым фото к пятилетию
anniversary-prize-gift-pack = подарочный набор к пятилетию
anniversary-prize-keychain = брелок с логотипом
anniversary-prize-mug = кружка с логотипом
anniversary-prize-not-won = без приза
anniversary-prize-sold-out = призовой фонд исчерпан
anniversary-rule-1 = 1. После участия можно разыгрывать раз в 4 часа, всего два раза. Призы попадают в виртуальную сумку события, не более одного каждого вида. Подарочный набор остаётся единственным призом и завершает остальные розыгрыши.
anniversary-rule-2 = 2. Время AFK не засчитывается для получения права на розыгрыш. Запрещено любым способом обходить определение AFK.
anniversary-rule-3 = 3. Сервер оплачивает 80% общей стоимости доставки физических призов, но не более 50 CNY; победитель полностью оплачивает остаток после вычета этой суммы.
anniversary-rule-4 = 4. Для доставки в материковый Китай необходимо предоставить действительный SF ID, пригодный для отправки с защитой персональных данных. Для доставки за пределы материкового Китая необходимы настоящее имя получателя, полный адрес и действующий номер телефона. Если требуемые данные не предоставлены или причитающаяся часть доставки не оплачена, победитель лишается приза.
anniversary-rule-5 = 5. Повторное участие с альтернативными аккаунтами запрещено и ведет к дисквалификации.
anniversary-rule-6 = 6. Участие означает согласие со всеми правилами выше.
anniversary-rule-7 = 7. Принятые сайтом постройки дают 0,2 балла за перенос, 0,5 за переработку и 1 за оригинал; принятые до события умножаются на 0,8, а совместные — на долю вашего вклада и 1,5. При сумме не менее 2 баллов администратор вручную выдаёт одну дополнительную попытку.
anniversary-second-round-hint = Второй раунд откроется после 8 часов онлайн вне AFK суммарно.
anniversary-status-completed = Вы завершили оба раунда.
anniversary-status-gift-pack-final = Вы выиграли подарочный набор. Ваш итоговый приз определён, все оставшиеся розыгрыши завершены.
anniversary-status-progress = Завершено: { $arg0 }/{ $arg1 } раундов; действительное время онлайн: { $arg2 }
anniversary-status-remaining = До раунда { $arg0 } нужно еще { $arg1 } онлайн вне AFK.
anniversary-status-results = Результаты розыгрыша: { $arg0 }
anniversary-status-title = Прогресс юбилейного розыгрыша

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } уже заблокирован
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Бюллетень блокировки]
ban-command-description = Заблокировать игрока по фиксированной степени нарушения
ban-database-error = Ошибка базы блокировок. Проверьте журнал сервера.
ban-dialog-back = Назад
ban-dialog-cancel = Отмена
ban-dialog-confirm-action = Заблокировать игрока
ban-dialog-confirm-severity = Степень
ban-dialog-confirm-title = Подтверждение блокировки
ban-dialog-never-joined-confirm-action = Всё равно заблокировать
ban-dialog-never-joined-title = Игрок никогда не заходил
ban-dialog-never-joined-warning = { $arg0 } никогда не заходил на этот сервер. Проверьте имя, чтобы не заблокировать другого игрока.
ban-dialog-next = Далее
ban-dialog-player-input = Ввести имя игрока вручную
ban-dialog-player-manual = Ручной ввод
ban-dialog-player-online = Игроки сейчас на сервере
ban-dialog-select-title = Выбор игрока
ban-dialog-severity-title = Выбор степени блокировки
ban-history-empty = История блокировок для { $arg0 } отсутствует.
ban-history-title = История блокировок: { $arg0 }
ban-invalid-player = Некорректное имя игрока
ban-invalid-severity = Некорректная степень блокировки
ban-kick-appeal = Если это ошибка, подайте апелляцию в группе QQ 1138627074
ban-kick-server = MIK
ban-kick-title = Вы заблокированы на этом сервере
ban-not-banned = Для { $arg0 } нет действующей блокировки.
ban-pardon-reason-required = Укажите причину разблокировки
ban-permanent = Навсегда
ban-reason-required = Укажите причину блокировки
ban-self = Нельзя заблокировать себя
ban-severity-excusable = Простительно (30 дней)
ban-severity-extreme = Крайне серьёзное нарушение (1500 дней)
ban-severity-minor = Незначительное нарушение (150 дней)
ban-severity-severe = Серьёзное нарушение (360 дней)
ban-severity-warning = Предупреждение (3 дня)
ban-status-active = Действует
ban-status-expired = Срок истёк
ban-status-revoked = Снята
ban-success = { $arg0 } заблокирован до { $arg1 }
ban-sync-success = Блокировки синхронизированы: действующих { $arg0 } · исторических записей { $arg1 }
ban-system-source = Система
ban-unban-success = Блокировка для { $arg0 } снята
ban-unknown-reason = Причина не указана
ban-update-success = Поле блокировки для { $arg0 } обновлено: { $arg1 }
banlist-command-description = Показать список блокировок
banlist-copy = Нажмите, чтобы скопировать имя игрока
banlist-empty = Заблокированных игроков нет
banlist-expires = Истекает
banlist-invalid-page = Неверная страница. Всего: { $arg0 ->
        [one] { $arg0 } страница
        [few] { $arg0 } страницы
        [many] { $arg0 } страниц
       *[other] { $arg0 } страницы
    }.
banlist-next = Далее
banlist-page = Страница { $arg0 }/{ $arg1 } · записей: { $arg2 }
banlist-player = Игрок
banlist-previous = Назад
banlist-reason = Причина
banlist-source = { role-moderator }
banlist-title = Список блокировок
maintenance-command-description = Включить, выключить или проверить режим технического обслуживания
maintenance-set = Режим технического обслуживания { $arg0 }
maintenance-state-disabled = выключен
maintenance-state-enabled = включён
maintenance-status = Режим технического обслуживания: { $arg0 }
maintenance-unchanged = Режим технического обслуживания уже { $arg0 }
restriction-entity-too-large-mm = <red>Размер сущности превышает безопасный предел сервера</red>
restriction-foreign-uuid-mm = <red>Можно использовать UUID только приручённых вами мобов</red>
restriction-no-kill-e-mm = <red>Команда <white>/kill @e</white> запрещена</red>
restriction-no-newline-mm = <red>Сообщения не могут содержать переносы строк</red>
restriction-no-run-command-mm = <red>Сообщения не должны обманом побуждать игроков выполнять команды</red>
restriction-no-selector-mm = <red>У вас нет прав на целевые селекторы <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>У вас нет прав упоминать имена других игроков в командах</red>
tpsbar-command-description = Показать или скрыть строку TPS
tpsbar-hidden = Строка TPS скрыта
tpsbar-shown = Строка TPS показана
unban-command-description = Снять блокировку с сохранением истории
whitelist-already-whitelisted = { $arg0 } уже в белом списке
whitelist-invalid-name = Недопустимое имя игрока
whitelist-kick-apply-rich = Подать заявку на добавление в белый список: { $site }
whitelist-kick-title = Вас нет в белом списке
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = Игрок { $arg0 } временно добавлен в белый список на { $arg1 }
whitelist-temp-added-rich = Игрок { $player } временно добавлен в белый список на { $duration }
whitelist-temp-command-description = Временно добавить игрока в белый список на 1 час
whitelist-temp-duration = 1 час
whitelist-temp-usage-desc = Временно добавить игрока в белый список на 1 час

## Shell · Menus, server information and scripting

main-afk-current-afk = Сейчас AFK
main-afk-current-online = Сейчас в сети
main-afk-status = Статус AFK
main-announcements = Объявления
main-home = Точки дома
main-language = Язык
main-language-auto = Автоматически
main-links = Ссылки
main-map = Онлайн-карта
main-menu-title = Главное меню
main-music = Музыка
main-name-tag = Оформление имени
main-profile = Профиль
main-pvp = PvP
main-settings = Настройки
main-website = Сайт
main-wiki = Вики
skript-command-description = Показать поддержку серверных скриптов
skript-info-ai-option = Не хотите писать сами? Передайте этот запрос ИИ:
skript-info-copy = [Копировать запрос для ИИ]
skript-info-copy-hover = Копировать запрос с версиями, ссылками на документацию и правилами безопасности
skript-info-documentation = Документация по синтаксису и API
skript-info-review = Скрипты загружаются только после проверки и тестирования администратором
skript-info-supported = Доступно: { $arg0 }
skript-info-title = Серверные скрипты
skript-info-workflow = Напишите и проверьте полный файл .sk по документации, затем отправьте его администратору
stat-client-version = Версия клиента
stat-first-joined = Первый вход
stat-location = Координаты
stat-ping = Задержка
stat-play-time = Время в игре
stat-role = Роль
stat-world = Мир
weblogin-command-description = Подтвердить код входа в Mik Web
weblogin-confirmed = Вход в Mik Web подтверждён. Вернитесь в браузер, чтобы продолжить.
weblogin-invalid-code = Неверный код входа в Mik Web
