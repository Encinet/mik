chat-content-audio = [Audio]
chat-content-card = [Tarjeta]
chat-content-contact = [Contacto]
chat-content-emote = [Acción]
chat-content-face = [Emoji]
chat-content-file = [Archivo]
chat-content-history = [Historial del chat]
chat-content-image = [Imagen]
chat-content-location = [Ubicación]
chat-content-message = [Mensaje]
chat-content-multiple = [Varios mensajes]
chat-content-music = [Música]
chat-content-notice = [Aviso]
chat-content-reply = [Mensaje citado]
chat-content-sticker = [Pegatina]
chat-content-video = [Vídeo]

social-binding-already-linked = Ya tienes vinculada la cuenta { $arg0 }. Envía { $arg1 } para ver tu perfil.
social-binding-confirmation-expired = La confirmación ha caducado o la cuenta vinculada ha cambiado. Solicita una nueva confirmación:
social-binding-code-invalid = Este código no es válido o ha caducado. Vuelve a conectarte o ejecuta { $arg0 } dentro del juego.
social-binding-external-in-use = Esta identidad ya está vinculada a otro jugador de Minecraft.
social-binding-help = Conéctate al servidor de Minecraft y envía aquí el comando de vinculación completo de la pantalla de acceso. Si ya estás jugando, obténlo con { $arg0 }.
social-binding-link-completed = La cuenta se ha vinculado. Vuelve a conectarte para continuar el acceso.
social-binding-membership-required = Únete a una comunidad conectada en esta plataforma y vuelve a intentar la vinculación.
social-binding-membership-unavailable = No se puede verificar la pertenencia a la comunidad en este momento. Inténtalo más tarde o contacta con la administración del servidor.
social-binding-not-bound = Aún no has vinculado una cuenta de juego.
social-binding-platform-mismatch = Este código de verificación se emitió para otra plataforma.
social-binding-player-scope-in-use = Ese jugador de Minecraft ya tiene otra identidad vinculada en el mismo ámbito de cuenta de la plataforma.
social-binding-rate-limited = Demasiados intentos fallidos. Inténtalo de nuevo en unos { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minutos
    }.
social-binding-unavailable = La vinculación de identidad no está disponible temporalmente.
social-binding-unlink-completed = La cuenta se ha desvinculado. Sin otra vinculación comunitaria válida, tendrás que vincular una cuenta antes de entrar.
social-binding-unlink-prompt = Se desvinculará esta cuenta de plataforma de { $arg0 }. Sin otra vinculación comunitaria válida, perderás el acceso al servidor y se cerrará tu conexión si estás jugando. Para confirmar, envía:
social-command-usage = Este comando no necesita argumentos. Envía:
social-duration-days = { $arg0 ->
        [one] { $arg0 } día
       *[other] { $arg0 } días
    } { $arg1 ->
        [one] { $arg1 } hora
       *[other] { $arg1 } horas
    } { $arg2 ->
        [one] { $arg2 } minuto
       *[other] { $arg2 } minutos
    }
social-duration-hours = { $arg0 ->
        [one] { $arg0 } hora
       *[other] { $arg0 } horas
    } { $arg1 ->
        [one] { $arg1 } minuto
       *[other] { $arg1 } minutos
    }
social-duration-minutes = { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minutos
    }
social-help-command-description = Mostrar esta lista de comandos
social-link-command-description = Vincular tu cuenta de Minecraft con un código del juego
social-link-command-parameters = <código>
social-online-command-description = Ver jugadores conectados
social-profile-afk = En línea · AFK
social-profile-command-description = Ver tu perfil; los miembros plenos pueden buscar por nombre, mención o respuesta
social-profile-command-parameters = [jugador]
social-profile-first-joined-label = Primera conexión
social-profile-last-seen-label = Última conexión
social-profile-member-required = Solo los miembros, moderadores o custodios vinculados pueden ver perfiles de otros jugadores.
social-profile-offline = Desconectado
social-profile-online = En línea · activo
social-profile-play-time-label = Tiempo de juego
social-profile-player-label = Jugador
social-profile-player-not-found = No se encontró ese jugador de Minecraft.
social-profile-presence-label = Estado
social-profile-target-ambiguous = Elige un solo objetivo: un nombre de jugador, una mención o un usuario respondido.
social-profile-target-unbound = La identidad mencionada o respondida no está vinculada a Minecraft.
social-profile-uuid-label = UUID
social-query-no-players = No hay jugadores en línea.
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (Ausente)
   *[active] { $arg0 }
    }
social-query-population = En línea { $arg0 }/{ $arg1 } · Activos { $arg2 } · Ausente { $arg3 }
social-query-online-summary = { $arg0 ->
    [one] Ahora hay { $arg0 } jugador conectado.
   *[other] Ahora hay { $arg0 } jugadores conectados.
    }{ $arg1 ->
    [0] { "" }
   *[other] { " " }En AFK: { $arg1 }.
    }
social-query-players-label = Jugadores
social-query-unknown = Comando desconocido. Envía { $arg0 } para ver los comandos disponibles.
social-query-uptime-label = Tiempo activo
social-query-version-label = Versión
social-reply-unavailable = Ahora no se puede mostrar el resultado. Comprueba los cambios de cuenta con el comando de perfil antes de volver a solicitarlos.
social-status-command-description = Ver jugadores, TPS, MSPT y tiempo activo
social-title-command-help = Ayuda de comandos
social-title-player-profile = Perfil del jugador
social-title-server-status = Estado del servidor
social-unlink-command-description = Eliminar este vínculo de identidad tras confirmarlo
social-unlink-command-parameters = [{ social-unlink-confirmation } <confirmación>]
social-unlink-confirmation = confirmar
ai-busy = La IA está procesando demasiadas solicitudes; inténtalo de nuevo pronto
ai-disabled = El asistente de IA está desactivado o no está configurado
ai-question-button-unavailable = El botón de esta pregunta ya no está disponible.
ai-request-failed = La solicitud de IA falló; inténtalo más tarde
ai-social-command-description = Preguntar al asistente de IA compartido
ai-social-command-parameters = <pregunta>
ai-social-question-required = Incluye una pregunta después del comando de IA
ai-suggested-questions = Preguntas sugeridas

social-aliases-online = enlínea jugadores
social-aliases-status = estado
social-aliases-profile = perfil
social-aliases-help = ayuda
social-aliases-bind = vincular
social-aliases-unbind = desvincular
social-aliases-ai = ia
