chat-content-audio = [Аудио]
chat-content-card = [Карточка]
chat-content-contact = [Контакт]
chat-content-emote = [Действие]
chat-content-face = [Эмодзи]
chat-content-file = [Файл]
chat-content-history = [История чата]
chat-content-image = [Изображение]
chat-content-location = [Местоположение]
chat-content-message = [Сообщение]
chat-content-multiple = [Несколько сообщений]
chat-content-music = [Музыка]
chat-content-notice = [Уведомление]
chat-content-reply = [Цитата]
chat-content-sticker = [Стикер]
chat-content-video = [Видео]

social-binding-already-linked = Аккаунт уже привязан к { $arg0 }. Отправьте { $arg1 }, чтобы посмотреть профиль.
social-binding-confirmation-expired = Подтверждение устарело или привязанный аккаунт изменился. Запросите новое подтверждение:
social-binding-code-invalid = Код недействителен или просрочен. Подключитесь заново или выполните { $arg0 } в игре.
social-binding-external-in-use = Эта личность уже привязана к другому игроку Minecraft.
social-binding-help = Подключитесь к серверу Minecraft и отправьте сюда полную команду привязки с экрана входа. Если вы уже в игре, получите её командой { $arg0 }.
social-binding-link-completed = Привязка аккаунта завершена. Подключитесь заново, чтобы продолжить вход.
social-binding-membership-required = Вступите в подключённое сообщество на этой платформе и повторите привязку.
social-binding-membership-unavailable = Сейчас не удаётся проверить участие в сообществе. Повторите попытку позже или обратитесь к администратору сервера.
social-binding-not-bound = Вы ещё не привязали игровой аккаунт.
social-binding-platform-mismatch = Этот код подтверждения выдан для другой платформы.
social-binding-player-scope-in-use = У этого игрока Minecraft уже есть другая привязка в той же области учётной записи платформы.
social-binding-rate-limited = Слишком много неудачных попыток. Повторите примерно через { $arg0 ->
        [one] { $arg0 } минуту
        [few] { $arg0 } минуты
        [many] { $arg0 } минут
       *[other] { $arg0 } минуты
    }.
social-binding-unavailable = Привязка личности временно недоступна.
social-binding-unlink-completed = Аккаунт отвязан. Без другой действующей привязки к сообществу нужно снова привязать аккаунт перед входом.
social-binding-unlink-prompt = Этот аккаунт платформы будет отвязан от { $arg0 }. Без другой действующей привязки к сообществу вы потеряете доступ к серверу; текущее подключение будет закрыто. Для подтверждения отправьте:
social-command-usage = Эта команда не принимает аргументы. Отправьте:
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
social-link-command-parameters = <код>
social-online-command-description = Показать игроков онлайн
social-profile-afk = В сети · AFK
social-profile-command-description = Показать свой профиль; полные участники ищут по имени, упоминанию или ответу
social-profile-command-parameters = [игрок]
social-profile-first-joined-label = Первый вход
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
social-query-no-players = Сейчас на сервере нет игроков.
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (Отошли)
   *[active] { $arg0 }
    }
social-query-population = В сети { $arg0 }/{ $arg1 } · Активные { $arg2 } · Отошли { $arg3 }
social-query-online-summary = Сейчас онлайн: { $arg0 }.{ $arg1 ->
    [0] { "" }
   *[other] { " " }Из них AFK: { $arg1 }.
    }
social-query-players-label = Игроки
social-query-unknown = Неизвестная команда. Отправьте { $arg0 }, чтобы посмотреть доступные команды.
social-query-uptime-label = Время работы
social-query-version-label = Версия
social-reply-unavailable = Результат пока нельзя показать. Проверьте изменения аккаунта командой профиля, прежде чем запрашивать их повторно.
social-status-command-description = Показать игроков, TPS, MSPT и время работы
social-title-command-help = Справка по командам
social-title-player-profile = Профиль игрока
social-title-server-status = Состояние сервера
social-unlink-command-description = Удалить эту привязку после подтверждения
social-unlink-command-parameters = [{ social-unlink-confirmation } <код-подтверждения>]
social-unlink-confirmation = подтвердить
ai-busy = ИИ обрабатывает слишком много запросов; повторите попытку чуть позже
ai-disabled = ИИ-помощник отключён или не настроен
ai-question-button-unavailable = Кнопка этого вопроса больше недоступна.
ai-request-failed = Ошибка запроса к ИИ; повторите попытку позже
ai-social-command-description = Задать вопрос общему ИИ-помощнику
ai-social-command-parameters = <вопрос>
ai-social-question-required = Добавьте вопрос после команды ИИ
ai-suggested-questions = Предлагаемые вопросы

social-aliases-online = онлайн игроки
social-aliases-status = статус
social-aliases-profile = профиль
social-aliases-help = помощь
social-aliases-bind = привязать
social-aliases-unbind = отвязать
social-aliases-ai = ии
