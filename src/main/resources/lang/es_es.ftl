### MIK · es-es
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = Parcela
    .gender = feminine

-vehicle-model = Modelo
    .gender = masculine

## Shared · General

back = Volver
back-to-main = Volver al menú principal
click-open = Haz clic para abrir
close = Cerrar
current-off = Actual: desactivado
current-on = Actual: activado
player-only = Este comando solo lo pueden usar los jugadores
unknown = Desconocido
usage = Uso

## Shared · Language, roles and time

language-command-description = Abrir configuración de idioma
language-menu-title = Configuración de idioma
language-selected = Seleccionado
language-set = Idioma cambiado a { $arg0 }
role-custodian = Custodio
role-member = Miembro
role-moderator = Moderador
role-new-player = Nuevo jugador
time-hours-minutes = { $arg0 }h { $arg1 }m
time-minutes = { $arg0 }m

## Shared · Menu presentation

bedrock-menu-back = Volver
bedrock-menu-hotkey = Acción rápida (F)
bedrock-menu-primary = Acción principal
bedrock-menu-scroll-down = Bajar
bedrock-menu-scroll-up = Subir
bedrock-menu-secondary = Acción secundaria
interface-fov-apply = Aplicar FOV
interface-fov-custom = Introducir un valor exacto
interface-fov-description = Iguala el FOV del cliente; solo cambia la posición del menú. Predeterminado: 70°.
interface-fov-input = FOV del cliente: entero de 30 a 110
interface-fov-invalid = Introduce un número entero de 30 a 110.
interface-fov-title = Adaptación del campo de visión
interface-scale-current = Actual: { $arg0 }
interface-scale-description = El tamaño del texto multiplica el del menú; ajusta ambos grupos con la rueda.
interface-scale-layout-title = Tamaño del menú
interface-scale-menu-title = Interfaz y texto
interface-scale-native-hint = Estos ajustes afectan a los menús espaciales de Java. El texto de los formularios nativos depende del cliente.
interface-scale-preview = Vista previa del texto Aa 123
interface-scale-reset = Restablecer ambos al 100%
interface-scale-text-title = Tamaño del texto
menu-rhythm-game-display = Jugando al juego de ritmo
menu-usage-display-mm = <aqua><bold>En el menú</bold></aqua>
url-dialog-confirm = Abrir
url-dialog-hint = El cliente pedirá otra confirmación para abrir el enlace.
url-dialog-question-rich = ¿Abrir { $label }?
url-dialog-title = Abrir { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Mods recomendados no detectados: { $mods }. Pulsa un nombre para descargarlos; si ya están instalados, comprueba que estén activados y sean compatibles.
client-version-mismatch-mm = <gray>Versión del servidor <white>{ $serverVersion }</white>, versión actual del cliente <white>{ $clientVersion }</white>.</gray>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>Hay { $count ->
        [one] <gold>{ $count }</gold> jugador <gold>invisible</gold> cerca
       *[other] <gold>{ $count }</gold> jugadores <gold>invisibles</gold> cerca
    }</yellow> <gray>·</gray> <gray>el más cercano está a aproximadamente { $distance ->
        [one] { $distance } bloque
       *[other] { $distance } bloques
    }</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = { $player } ha entrado por la puerta trasera
    .item-2 = { $player } ha encontrado el camino secreto
    .item-3 = { $player } ha aparecido desde los arbustos
presence-quit-templates =
    .item-1 = { $player } sale de puntillas
    .item-2 = { $player } sale para guardar la partida
    .item-3 = El mundo real llama a { $player }
welcome-first-join-mm = <gold><bold>¡Te damos la bienvenida a MIK!</bold></gold><newline><gray>Empieza por el <click:open_url:'https://mcmik.top'><yellow>sitio web</yellow></click> y la <click:open_url:'https://mcmik.top/wiki'><yellow>wiki</yellow></click>; también puedes usar <yellow>/menu</yellow> para abrir el menú principal.</gray>
welcome-return-mm = <gold><bold>¡Qué alegría verte de nuevo!</bold></gold><newline><gray>Tu última visita fue hace <yellow>{ $arg0 ->
        [one] { $arg0 } día
       *[other] { $arg0 } días
    }</yellow>. Usa <yellow>/menu</yellow> para acceder rápidamente a las funciones habituales.</gray>

## Player · AFK

afk-already-afk-mm = <gray>Ya estás en <gold>AFK</gold></gray>
afk-command-description = Activar estado AFK
afk-default-statuses =
    .item-1 = se alejó del teclado
    .item-2 = entró en modo de ahorro de energía
    .item-3 = pasó a segundo plano
    .item-4 = se quedó mirando al vacío
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>ahora está AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>dejó una nota:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>cambió su estado a:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>volvió al teclado</green>
    .item-2 = <gray>*</gray> <player> <green>ha vuelto</green>
afk-not-afk-mm = <gray>Ahora mismo no estás <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>El estado AFK puede tener como máximo { $arg0 ->
        [one] <white>{ $arg0 }</white> carácter
       *[other] <white>{ $arg0 }</white> caracteres
    }</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · coincidencias: { $arg2 ->
        [one] { $arg2 } registro
       *[other] { $arg2 } registros
    } · historial: { $arg3 }
player-association-header = [Asociación de acceso] { $arg0 } comparte historial de direcciones con { $arg1 ->
        [one] { $arg1 } jugador
       *[other] { $arg1 } jugadores
    }.
player-association-hover = Coinciden registros históricos de red. Es solo una ayuda de revisión; no se muestran direcciones.
player-association-more-addresses = { $arg0 ->
        [one] { $arg0 } registro
       *[other] { $arg0 } registros
    } más
player-association-more-players = { $arg0 ->
        [one] { $arg0 } jugador
       *[other] { $arg0 } jugadores
    } más
player-association-time-days = hace { $arg0 ->
        [one] { $arg0 } día
       *[other] { $arg0 } días
    }
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } registro
       *[other] { $arg1 } registros
    })
player-association-time-separator = ,
player-association-time-today = hoy

## Player · Profile and tab list

nametag-clear-all-error = Error al borrar las etiquetas de nombre. Inténtalo de nuevo más tarde
nametag-clear-all-success = ✔ Todas las etiquetas de nombre borradas
nametag-clear-error = Error al borrar { $arg0 }
nametag-clear-success = ✔ { $arg0 } borrado
nametag-click-fill-command = Haz clic para completar el comando
nametag-command-description = Personalizar etiquetas de nombre
nametag-copy-hover = Copia el código de MiniMessage al portapapeles
nametag-copy-label = [copiar]
nametag-effect-label = Resultado
nametag-empty-content-rich = El contenido no puede estar vacío. Para borrarlo, usa { $command }
nametag-format-docs = Documentación de formato
nametag-leading-label = Prefijo de nombre
nametag-no-permission = No tienes permiso para usar este comando
nametag-online-editor = Editor en línea
nametag-preview-label = Vista previa
nametag-sample-message = mensaje de muestra
nametag-set-error = Error al establecer { $arg0 }. Inténtalo de nuevo más tarde
nametag-set-success = ✔ { $arg0 } establecido
nametag-title = Etiquetas de nombre
nametag-too-long = { $arg0 } es demasiado largo. La longitud máxima es de { $arg1 ->
        [one] { $arg1 } carácter
       *[other] { $arg1 } caracteres
    }
nametag-trailing-label = Sufijo de nombre
nametag-unset = (no establecido)
nametag-unsupported-placeholder-rich = Solo se admiten las variables de PlaceholderAPI { $supported }, no { $unsupported }
nametag-user-data-load-failed = No se han podido cargar los datos del usuario. Inténtalo de nuevo más tarde
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Jugadores en línea: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Vuelve a tu ubicación anterior de teletransporte o muerte
back-empty-mm = <gray>No hay ninguna ubicación a la que volver</gray>
back-pending-mm = <gray>Teletransportando, espera un momento</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>Has retrocedido { $steps ->
        [one] { $steps } paso
       *[other] { $steps } pasos
    }</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>No se ha podido teletransportar. Inténtalo de nuevo</red>
back-undo-empty-mm = <gray>No hay ningún <white>/back</white> que deshacer</gray>
back-undo-success-mm = <green>Has deshecho el último <white>/back</white></green>
back-undo-world-missing-mm = <red>El mundo de destino no está disponible. No puedes deshacerlo</red>
back-world-missing-mm = <red>El mundo de destino no está disponible. No puedes volver</red>
boundary-near-mm = <yellow>Te estás acercando a la frontera mundial.</yellow>
boundary-reached-mm = <red>Has llegado a la frontera mundial.</red>
fly-command-description = Alternar vuelo personal
fly-disabled = Vuelo desactivado
fly-enabled = Vuelo activado
fly-native-mode = Tu acceso de /fly se ha desactivado. Este modo de juego sigue permitiendo volar.
fly-speed-click = Ajustar velocidad de vuelo a { $arg0 }x
fly-speed-current = Velocidad de vuelo: { $arg0 }x
fly-speed-presets = Preajustes
fly-speed-reset = Velocidad de vuelo restablecida a 1x
fly-speed-reset-action = Restablecer
fly-speed-reset-click = Restablecer velocidad de vuelo a 1x
fly-speed-set = Velocidad de vuelo ajustada a { $arg0 }x
fly-state-disabled = Desactivado
fly-state-enabled = Activado
fly-status = Vuelo: { $arg0 } | Velocidad: { $arg1 }x
flyspeed-command-description = Ver o ajustar la velocidad de vuelo personal
home-action-create = Crear hogar
home-action-delete = Eliminar hogar
home-action-icon = Usar objeto como icono
home-action-teleport = Teletransportarse
home-action-update = Actualizar ubicación
home-already-exists = Ya existe un hogar con este nombre
home-back = Atrás
home-confirm-delete = Confirmar eliminación
home-confirm-delete-detail-rich = Eliminar hogar { $home }
home-confirm-delete-warning = Esto no se puede deshacer
home-confirm-update = Confirmar actualización
home-confirm-update-detail-rich = Actualizar hogar { $home } a esta ubicación
home-delete-command-description = Elimina un hogar
home-delete-usage-desc = Elimina un hogar
home-deleted-rich = Hogar { $home } eliminado
home-distance = Distancia: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    }
home-distance-other-world = otro mundo
home-empty-description = Colócate donde quieras guardar un hogar
home-empty-title = Aún no hay hogares
home-icon = Icono: { $arg0 }{ $arg1 }
home-icon-default-note = · predeterminado
home-icon-hand-invalid = El objeto de tu mano principal no se puede usar como icono de hogar
home-icon-updated-rich = Icono del hogar { $home } actualizado
home-invalid-icon = Este material no se puede usar como icono de hogar
home-invalid-name = Los nombres de los hogares no pueden contener los caracteres < > :
home-list-rich = Hogares: { $homes }
home-location = Ubicación: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = Los datos de ubicación de este hogar no son válidos
home-max-reached = Puedes establecer un máximo de { $arg0 } hogares
home-menu-title = Hogares
home-no-homes = Aún no has establecido ningún hogar
home-not-found-rich = El hogar { $home } no existe
home-set-command-description = Establece un hogar en tu ubicación actual
home-set-rich = Hogar { $home } establecido
home-set-usage-desc = Establece un hogar en tu ubicación actual
home-summary-count = Establecidos: { $arg0 } / { $arg1 }
home-teleport-command-description = Teletranspórtate a un hogar
home-teleport-failed = No se ha podido teletransportar. Inténtalo de nuevo
home-teleported-rich = Te has teletransportado al hogar { $home }
home-updated-rich = Ubicación del hogar { $home } actualizada
home-usage-book = Ayuda de comando
home-usage-delhome = /delhome <name>  Eliminar un hogar
home-usage-home = /home tp <name>  Teletransportarse directamente
home-usage-icon = /home icon <material> <name>  Establecer un icono
home-usage-name-rule = Los nombres de los hogares no pueden contener < > :
home-usage-sethome = /sethome <name>  Guarda tu ubicación actual
home-world = Mundo: { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Deshacer el último /back
teleport-accept-command-description = Aceptar la solicitud de teletransporte pendiente
teleport-allow = Teletransportes de otros jugadores
teleport-block-afk = Bloquear mientras AFK
teleport-denied = { $player } no permite que los jugadores normales le teletransporten
teleport-deny-command-description = Rechazar la solicitud de teletransporte pendiente
teleport-menu-title = Privacidad del teletransporte
teleport-moved-here = { $arg0 } te ha teletransportado hasta aquí
teleport-policy-always-allow = Permitir siempre
teleport-policy-always-deny = Rechazar siempre
teleport-policy-require-consent = Requerir mi aprobación
teleport-request-accept = Aceptar
teleport-request-approved = { $player } aceptó tu solicitud de teletransporte
teleport-request-deny-action = Rechazar
teleport-request-expired = La solicitud de teletransporte ha caducado
teleport-request-failed = No se pudo completar el teletransporte. Inténtalo de nuevo
teleport-request-none = No tienes ninguna solicitud de teletransporte pendiente
teleport-request-received = { $sender } quiere teletransportarte: { $command }
teleport-request-rejected = Solicitud de teletransporte rechazada
teleport-request-sent = Solicitud de teletransporte enviada a { $player }
teleport-summary-afk = Siempre permitidos, salvo mientras estás AFK
teleport-summary-allow = Teletransportes de jugadores normales siempre permitidos
teleport-summary-consent = Los teletransportes de jugadores normales requieren mi aprobación
teleport-summary-deny = Teletransportes de jugadores normales siempre rechazados
tpany-command-description = Teletransportarse a la ubicación de un jugador, en línea o sin conexión
tpany-done-offline-rich = Te has teletransportado a la última ubicación conocida de { $player } { $world } { $location }
tpany-done-online-rich = Te has teletransportado a la ubicación actual de { $player }
tpany-not-found-rich = No se ha podido encontrar la ubicación de { $player }
tpany-usage = Uso
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teletranspórtate a la ubicación de un jugador, incluida su ubicación guardada si está desconectado

## Player · PVP

pvp-admin-menu-title = Administración de JcJ - Página { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = Las criaturas montadas pueden ser atacadas
pvp-allow-mounted-damage-desc = Si se activa, otro jugador puede atacar a la criatura que montas mientras ambos tenéis el JcJ activado
pvp-auto-enable-desc = Ataca dos veces seguidas a un jugador con JcJ activado
pvp-auto-enable-label = Activación automática
pvp-auto-enable-warning-actionbar-mm = <yellow>Ataca una vez más para activar el JcJ automáticamente.</yellow>
pvp-auto-enabled-actionbar-mm = <green>JcJ activado automáticamente</green>
pvp-back-admin = Volver a la administración de JcJ
pvp-combat-locked-mm = <red>No puedes desactivar el JcJ durante un combate.</red> <dark_gray>|</dark_gray> <gray>Tiempo restante: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> segundo
       *[other] <yellow>{ $arg0 }</yellow> segundos
    }</gray>
pvp-combat-tag-label = Enfriamiento de combate
pvp-combat-tag-value = { $arg0 } s
pvp-command-description = Cambiar estado JcJ
pvp-enable = Activar JcJ
pvp-enable-desc = Si se activa, puedes atacar a otros jugadores con JcJ activado y ellos pueden atacarte
pvp-enable-on-death = Activar JcJ al morir
pvp-enable-on-death-desc = Si se activa, el JcJ se activa automáticamente cuando mueres
pvp-enable-on-death-label = Activar al morir
pvp-enabled-on-death-mm = <yellow>El JcJ se ha activado porque has muerto</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Abrir la administración de JcJ de jugadores en línea</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Abrir configuración de JcJ</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Administrar un jugador en línea</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Ver tu estado JcJ</gray>
pvp-help-title-mm = <gold><bold>Comandos JcJ</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Cambia tu estado JcJ</gray>
pvp-menu-title = Configuración JcJ
pvp-mob-protected-actionbar-mm = <yellow>Esta criatura está protegida del daño JcJ.</yellow>
pvp-mob-protection-label = Protección de criaturas
pvp-mounted-damage-label = Daño a criaturas montadas
pvp-next-page = Página siguiente
pvp-no-permission-mm = <red>No tienes permiso para gestionar el estado JcJ de otro jugador.</red>
pvp-player-not-found-mm = <red>Jugador en línea no encontrado</red>
pvp-prev-page = Página anterior
pvp-protect-mobs = Protege a tus criaturas
pvp-protect-mobs-desc = Protege a las criaturas que has domesticado o que estás montando
pvp-set-by-staff-mm = <aqua>El equipo ha cambiado tu estado JcJ a</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = Estado JcJ de { $arg0 } cambiado a { $arg1 }
pvp-set-other-rich = Estado JcJ de { $player } cambiado a { $state }
pvp-state-label = JcJ
pvp-state-off = Desactivado
pvp-state-on = Activado
pvp-status-player = Jugador: { $arg0 }
pvp-status-player-rich = Jugador: { $player }
pvp-status-title-mm = <gold><bold>Estado JcJ</bold></gold>
pvp-summary-disabled = JcJ desactivado
pvp-summary-enabled = JcJ activado
pvp-target-disabled-actionbar-mm = <gray>Ese jugador no tiene el JcJ activado.</gray>
pvp-target-menu-title = Configuración JcJ - { $arg0 }
pvp-toggled-off-mm = <gray>JcJ desactivado</gray> <dark_gray>|</dark_gray> <gray>Los demás jugadores no pueden atacarte.</gray>
pvp-toggled-on-mm = <green>JcJ activado</green> <dark_gray>|</dark_gray> <gray>Ya puedes luchar contra otros jugadores con JcJ activado.</gray>

## Player · Utilities

hat-command-description = Ponte el objeto de tu mano principal
hat-empty-hand-mm = <red>No tienes ningún objeto en la mano principal.</red> <gray>Sujeta un bloque u objeto y usa /hat</gray>
hat-success-mm = <green>Te has puesto el objeto de la mano principal</green> <dark_gray>|</dark_gray> <gray>Se han intercambiado el objeto de tu mano y el de la ranura del casco.</gray>
removeitems-command-description = Eliminar objetos tirados
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Eliminado: { $countValue ->
        [one] { $count } objeto tirado
       *[other] { $count } objetos tirados
    } · mundo { $world } { $location } { $radius }
removeitems-no-world = No se ha podido determinar el mundo
removeitems-radius = radio { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    }
self-kick-command-description = Expulsarte del servidor
self-kick-reason-too-long = El motivo de expulsión no puede superar { $arg0 ->
        [one] { $arg0 } carácter
       *[other] { $arg0 } caracteres
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Has salido del servidor por tu cuenta. Motivo: { $reason }
       *[third] { $player } se expulsó del servidor por este motivo: { $reason }
    }
    .item-1 = { $player } se expulsó y, sorprendentemente, el motivo fue: { $reason }
    .item-2 = ¡{ $player } se expulsó del servidor por { $reason }!
    .item-3 = { $player } abandonó voluntariamente el servidor y dejó esta explicación: { $reason }
    .item-4 = { $player } eligió autoexpulsarse: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Has salido del servidor por tu cuenta.
       *[third] { $player } se expulsó del servidor.
    }
    .item-1 = ¡{ $player } consiguió expulsarse a sí mismo!
    .item-2 = { $player } abandonó el servidor sin decir una palabra.
    .item-3 = { $player } ejecutó una autoexpulsión impecable.
    .item-4 = { $player } pulsó personalmente el botón para salir del servidor.
trash-command-description = Abrir la papelera
trash-title = Papelera
trash-title-hint = Apunta a un objeto y pulsa F para borrar la pila

## Community · Chat and mentions

chat-copy-hover = Haz clic izquierdo para copiar el mensaje
chat-delay-3s = 3 segundos
chat-delay-5s = 5 segundos
chat-delay-7s = 7 segundos
chat-delay-cancel-command-description = Cancelar mensajes retrasados pendientes
chat-delay-cancelled = Mensaje pendiente cancelado
chat-delay-current = Retraso actual del chat: { $arg0 }
chat-delay-nothing-to-cancel = No hay mensajes pendientes para cancelar
chat-delay-off = Desactivado
chat-delay-preview-marker = [Pendiente]
chat-delay-queued = El mensaje se enviará en { $arg0 }; usa /cancel o /c para cancelarlo
chat-item-empty-hover = No tienes ningún objeto en la mano principal
chat-mention-all-hover = Notificar a todos los jugadores visibles en este canal.
chat-msg-command-description = Envía un mensaje privado y cambia al chat privado
chat-player-hover = Haz clic izquierdo para iniciar un mensaje privado
chat-private-enter = Cambiado al chat privado con { $arg0 }
chat-private-label = MSG
chat-private-no-reply-target = Ningún destinatario reciente del chat privado está conectado
chat-private-self = No puedes iniciar un chat privado contigo mismo
chat-private-target-not-found = Jugador en línea no encontrado: { $arg0 }
chat-private-target-offline = El destinatario del chat privado está desconectado; has vuelto al chat público
chat-public-command-description = Cambiar canal de chat público
chat-public-enter = Cambiado al chat público
chat-reply-command-description = Responde a tu último objetivo de chat privado
chat-settings-menu-title = Configuración de chat
chat-social-account-label = Cuenta de la plataforma
chat-social-bound-player-label = Jugador vinculado
chat-social-name-label = Nombre social
chat-social-unbound = No está vinculado a ningún jugador de Minecraft
chat-staff-command-description = Cambiar canal de chat del personal
chat-staff-permission-missing = No tienes permiso para usar el chat del equipo; has vuelto al chat público
mention-action-bar = Aviso en la barra de acción
mention-action-bar-text = { $sender } te ha mencionado
mention-alerts = Alertas de mención
mention-menu-title = Alertas de mención
mention-mute-afk = Silenciar mientras AFK
mention-sound = Sonido de alerta
mention-summary-action-bar = barra de acción
mention-summary-disabled = Alertas deshabilitadas
mention-summary-enabled = Alertas habilitadas
mention-summary-enabled-afk = Alertas habilitadas, silenciadas mientras AFK
mention-summary-modes-afk = { $arg0 }, silenciado mientras AFK
mention-summary-sound = sonido
staffchat-enter = Te has unido al chat del equipo

## Community · Announcements and tips

announcement-empty = No hay anuncios
announcement-first-page = Avisos más recientes
announcement-last-page = Avisos más antiguos
announcement-more-count = { $arg0 } más sin mostrar
announcement-new-count = { $arg0 } nuevos
announcement-newer = Avisos más recientes
announcement-older = Avisos anteriores
announcement-reload = Recargar anuncios
announcement-reload-command-description = Recargar archivo de anuncios
announcement-reload-failed = No se pudieron recargar los anuncios; se conservan los anteriores
announcement-reloaded = Anuncios recargados
announcement-scroll-hint = Rueda para navegar lateralmente
announcement-unread-count = { $arg0 } sin leer
announcement-updated = Anuncios actualizados
announcement-view-all = Ver todos los anuncios
announcement-view-command-description = Ver anuncios del servidor
tip-hover = consejo del servidor
tip-label = CONSEJO
tip-reload-command-description = Recargar consejos.txt
tip-reload-count = { $arg0 ->
        [one] { $arg0 } entrada
       *[other] { $arg0 } entradas
    }
tip-reload-done = Consejos recargados

## Community · Identity and social platforms

identity-admin-binding-not-found = No se encontró un jugador y plataforma inequívocos.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Eliminación para { $arg1 }: { $arg2 ->
        [one] { $arg2 } vínculo de identidad
       *[other] { $arg2 } vínculos de identidad
    } ({ $arg0 }).
identity-admin-command-description = Consultar o revocar vínculos de identidad externos
identity-admin-help = /bindadmin lookup <jugador-o-UUID>
    /bindadmin unlink <jugador-o-UUID> <plataforma> confirm
identity-admin-lookup-empty = No se encontraron vínculos para ese jugador.
identity-admin-lookup-title = Vínculos de { $arg0 }:
identity-binding-details = · { $arg0 } · ámbito { $arg1 } · { $arg2 }
identity-binding-not-owned = No tienes vínculos para esa plataforma.
identity-binding-unlinked = Eliminación completada: { $arg1 ->
        [one] { $arg1 } vínculo de identidad
       *[other] { $arg1 } vínculos de identidad
    } ({ $arg0 }).
identity-bindings-empty = No has vinculado ninguna cuenta de una plataforma externa.
identity-bindings-title = Identidades externas vinculadas:
identity-bindings-unlink-hint = Usa /bind unlink <plataforma> para eliminar todos los vínculos de una plataforma.
identity-code-cancelled = Se canceló el código de vínculo de { $arg0 } sin usar.
identity-code-copy = Haz clic para copiar el comando de vinculación completo
identity-code-expiry = El código es válido durante 5 minutos y solo puede usarse una vez. Generar otro lo invalida.
identity-code-issued = Código de vínculo de { $arg0 } generado:
identity-code-not-pending = No hay ningún código de vínculo de { $arg0 } sin usar.
identity-code-redeem = En el grupo de { $arg0 } elegido, menciona al bot y envía: { $arg1 }
identity-command-description = Vincular Minecraft con una identidad de una plataforma externa
identity-help = Comandos de vinculación de identidad:
    /bind qq — generar rápidamente un comando de vinculación de QQ
    /bind <plataforma> — generar un código de un solo uso
    /bind list — mostrar los vínculos existentes
    /bind cancel <plataforma> — cancelar un código sin usar
    /bind unlink <plataforma> — eliminar todos los vínculos de una plataforma
identity-notify-linked = Se verificó tu identidad de { $arg0 }. Usa /bind list para verla o /bind unlink { $arg0 } para eliminarla.
identity-notify-unlinked = Se eliminó tu vínculo de identidad de { $arg0 }.
identity-platform-none = ninguna
identity-platforms = Plataformas compatibles: { $arg0 }
identity-scope-notice = Esta plataforma separa las identidades por grupo. Vincula la cuenta en el grupo donde usarás el bot; cada grupo requiere su propio vínculo.
identity-storage-error = La operación de la base de datos de vínculos ha fallado. Contacta con un administrador.
identity-unavailable = La vinculación de identidades no está disponible. Contacta con un administrador.
identity-unsupported-platform = Plataforma no compatible: { $arg0 }
social-admin-conversations-empty = No se observaron conversaciones para { $arg0 }.
social-admin-conversations-title = Conversaciones observadas para { $arg0 }:
social-admin-help = /social platforms · /social status [plataforma] · /social reload [plataforma] · /social conversations <plataforma>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Se recargó la plataforma social { $arg0 }.
social-admin-reloaded-all = Se recargaron todas las plataformas sociales.
social-admin-status = { $arg0 } · { $arg1 } · generación { $arg2 } · conversaciones { $arg3 }
social-admin-title = Plataformas sociales
social-admin-unknown = Plataforma social desconocida: { $arg0 }
social-binding-already-linked = Esta identidad ya está vinculada al jugador de Minecraft { $arg0 }.
social-binding-code-invalid = El código de verificación no es válido o ha caducado. Genera otro con /bind { $arg0 } en Minecraft.
social-binding-external-in-use = Esta identidad ya está vinculada a otro jugador de Minecraft.
social-binding-help = Ejecuta /bind { $arg0 } en Minecraft.
    Copia el comando completo generado.
    Envía el comando aquí.
    Usa /perfil para comprobar la vinculación.
social-binding-identity-missing = Este mensaje no contiene una identidad de miembro fiable, por lo que no se puede vincular.
social-binding-linked = Vinculada al jugador de Minecraft { $arg0 }.
social-binding-not-bound = Esta identidad de plataforma no está vinculada.
social-binding-platform-mismatch = Este código de verificación se emitió para otra plataforma.
social-binding-player-scope-in-use = Ese jugador de Minecraft ya tiene otra identidad vinculada en esta conversación.
social-binding-rate-limited = Demasiados intentos fallidos. Inténtalo de nuevo en unos { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minutos
    }.
social-binding-status-unbound = Esta identidad no está vinculada. Usa /vincular con un código de verificación.
social-binding-unavailable = La vinculación de identidad no está disponible temporalmente.
social-binding-unlink-prompt = Desvincular elimina la relación entre esta identidad y Minecraft. Envía /desvincular confirmar para continuar.
social-binding-unlinked = Se desvinculó del jugador de Minecraft { $arg0 }.
social-command-description = Gestionar adaptadores de plataformas sociales
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
social-online-command-description = Ver jugadores conectados
social-profile-afk = En línea · AFK
social-profile-avatar-alt = Rostro de Minecraft
social-profile-command-description = Ver tu perfil; los miembros plenos pueden buscar por nombre, mención o respuesta
social-profile-first-joined-label = Primera conexión
social-profile-language-label = Idioma
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
social-query-active-label = Activos
social-query-no-players = No hay jugadores en línea.
social-query-online-label = En línea
social-query-players-label = Jugadores
social-query-unknown = Comando desconocido. Envía /ayuda para ver los comandos disponibles.
social-query-uptime-label = Tiempo activo
social-reply-hidden = La política de seguridad de contenido del servidor ocultó esta respuesta.
social-state-disabled = desactivada
social-state-failed = fallida
social-state-ready = lista
social-state-starting = iniciando
social-state-stopped = detenida
social-status-command-description = Ver jugadores, TPS, MSPT y tiempo activo
social-title-command-help = Ayuda de comandos
social-title-content-hidden = Contenido oculto
social-title-identity-linking = Vincular cuenta
social-title-identity-unlinking = Desvincular cuenta
social-title-online-players = Jugadores en línea
social-title-player-profile = Perfil del jugador
social-title-server-status = Estado del servidor
social-title-unknown-command = Comando desconocido
social-unlink-command-description = Eliminar este vínculo de identidad tras confirmarlo

## Community · AI and knowledge

ai-busy = La IA está procesando demasiadas solicitudes; inténtalo de nuevo pronto
ai-cleared = Historial de conversación de IA borrado
ai-command-description = Preguntar al asistente de IA del juego
ai-disabled = El asistente de IA está desactivado o no está configurado
ai-knowledge-curation-failed = Falló la organización del conocimiento: { $arg0 }
ai-knowledge-curation-finished = Organización finalizada: aplicados { $arg0 } · omitidos { $arg1 } · fallidos { $arg2 } · aislados { $arg3 } · procesados { $arg4 }
ai-knowledge-curation-started = Organización del conocimiento iniciada.
ai-knowledge-disabled = La base de conocimiento está desactivada.
ai-knowledge-index-dirty = Es necesario conciliar el índice; la recuperación privada queda bloqueada hasta sincronizar.
ai-knowledge-last-failure = Último error: { $arg0 }
ai-knowledge-last-sync = Última sincronización: { $arg0 }
ai-knowledge-learning-disabled = El aprendizaje de conocimiento está desactivado.
ai-knowledge-operation-failed = Falló la operación de conocimiento: { $arg0 }
ai-knowledge-operation-started = Operación de conocimiento iniciada.
ai-knowledge-protected = El documento { $arg0 } ahora está protegido.
ai-knowledge-rollback = Documento { $arg0 } restaurado como revisión { $arg1 }.
ai-knowledge-status = Conocimiento: { $arg0 ->
        [one] { $arg0 } documento público
       *[other] { $arg0 } documentos públicos
    } / { $arg1 ->
        [one] { $arg1 } fragmento de texto
       *[other] { $arg1 } fragmentos de texto
    } · { $arg2 ->
        [one] { $arg2 } documento privado
       *[other] { $arg2 } documentos privados
    } / { $arg3 ->
        [one] { $arg3 } fragmento de texto
       *[other] { $arg3 } fragmentos de texto
    }
ai-knowledge-sync-failed = Falló la sincronización del conocimiento: { $arg0 }
ai-knowledge-sync-started = Sincronización del conocimiento iniciada.
ai-knowledge-synchronized = { ai-knowledge-status } · Sincronización completada.
ai-knowledge-unprotected = El documento { $arg0 } ya no está protegido.
ai-learning-failure = Error de aprendizaje: { $arg0 }
ai-learning-status = Aprendizaje: candidatos { $arg0 } · extrayendo { $arg1 } · en cola { $arg2 } · extracciones descartadas { $arg3 } · candidatos descartados { $arg4 } · aislados { $arg5 }
ai-memory-empty = No hay recuerdos privados de { $arg0 }.
ai-memory-list = Recuerdos privados de { $arg0 }: { $arg1 }
ai-memory-not-found = No se encontró el recuerdo privado { $arg0 }.
ai-memory-removed = Eliminación permanente para { $arg1 }: { $arg0 ->
        [one] { $arg0 } recuerdo privado
       *[other] { $arg0 } recuerdos privados
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Jugador desconocido en la caché: { $arg0 }
ai-prompt-too-long = La pregunta es demasiado larga (máximo { $arg0 ->
        [one] { $arg0 } carácter
       *[other] { $arg0 } caracteres
    })
ai-reload-failed = No se pudo recargar la configuración de IA: { $arg0 }
ai-reloaded = Configuración de IA recargada ({ $arg0 })
ai-request-failed = La solicitud de IA falló; inténtalo más tarde
ai-social-command-description = Preguntar al asistente de IA compartido
ai-social-question-required = Incluye una pregunta después del comando de IA
ai-status-disabled = La IA está desactivada
ai-status-enabled = IA activa · proveedor { $arg0 } · modelo { $arg1 } · herramientas { $arg2 }
ai-status-none = ninguno
ai-thinking = Pensando…
ai-usage = Uso: /ai <pregunta> · /ai clear · /ai status
ai-worker-status = Tareas de IA: activas { $arg0 } · en cola { $arg1 } · descartadas { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>¡Enhorabuena!</bold> Ahora eres <gold>Miembro</gold>. Ya tienes acceso a más comandos y funciones, incluida la impresión fácil de esquemas.</green>
governance-active-days-30 = Al menos 2 días activos en 30 días (actualmente { $arg0 })
governance-active-days-60 = Al menos 6 días activos en 60 días (actualmente { $arg0 })
governance-active-header = ─── Requisitos de miembro activo ───
governance-active-member = Es miembro actualmente
governance-active-member-age = Miembro desde hace al menos 30 días
governance-active-playtime = Al menos 6 horas de juego efectivo en 60 días (actualmente { $arg0 })
governance-already-moderator = Ya eres moderador.
governance-ballot-recorded = Tu voto anónimo se ha registrado como { $arg0 }. Puedes cambiarlo antes del cierre.
governance-ban-details = Propuesto por { $arg0 } · Duración { $arg1 } · Motivo: { $arg2 }
governance-ban-suffix = · Duración { $arg0 } · Motivo: { $arg1 }
governance-choice-abstain = Abstención
governance-choice-no = No
governance-choice-yes = Sí
governance-command-description = Ver estado de gobernanza y votaciones
governance-custodian-no-nomination = Los custodios no participan en las elecciones de moderadores.
governance-custodian-no-resign = La renuncia de moderadores no se aplica a los custodios.
# $arg0 (Number): duration; locale decides the unit form.
governance-days = { $arg0 ->
        [one] { $arg0 } día
       *[other] { $arg0 } días
    }
governance-error-ban-already-active = Este jugador ya tiene una sanción activa.
governance-error-ban-proposer-ineligible = Solo los miembros activos pueden proponer una votación de sanción.
governance-error-ban-self = No puedes proponer una votación para sancionarte a ti mismo.
governance-error-candidate-ineligible = Un candidato a moderador debe ser miembro activo.
governance-error-nomination-not-open = No tienes ninguna candidatura abierta.
governance-error-not-voter = No estás en el censo de votantes fijado para esta votación.
governance-error-pause-already-active = Este jugador ya tiene una pausa de ascenso activa.
governance-error-pause-already-member = Este jugador ya es miembro; no se puede pausar su ascenso.
governance-error-pause-end-invalid = La pausa de ascenso debe terminar en el futuro.
governance-error-player-unknown = Este jugador aún no tiene historial de gobernanza.
governance-error-removal-requires-petition = Una votación de destitución necesita suficientes apoyos.
governance-error-removal-self = No puedes apoyar tu propia destitución.
governance-error-removal-sponsor-ineligible = Solo los miembros activos pueden apoyar una destitución.
governance-error-removal-sponsors-too-few = Se necesitan al menos otros dos miembros activos.
governance-error-removal-vote-open = Ya hay una votación de destitución abierta para este moderador.
governance-error-termination-reason-invalid = Este motivo de cierre no corresponde a esta votación.
governance-error-vote-already-open = Ya hay una votación abierta de este tipo para este jugador.
governance-error-vote-closed = Esta votación ya cerró o terminó.
governance-error-vote-cooldown = Aún no han pasado los tres días de espera tras no alcanzar el quórum.
governance-error-vote-subject-unknown = La persona afectada aún no tiene historial de gobernanza.
governance-error-vote-termination-closed = Tras el cierre ya no se puede retirar la votación.
governance-error-vote-unknown = Votación no encontrada.
governance-error-voters-too-few = Se necesitan al menos dos miembros activos para iniciar una votación.
governance-failure-majority = Los síes no superaron la mitad de los votos emitidos
governance-failure-minimum-yes = No hubo suficientes votos a favor
governance-failure-none = Sin motivo registrado
governance-failure-quorum = No se alcanzó el quórum
governance-failure-two-thirds = Los síes no alcanzaron dos tercios de los votos no abstencionistas
governance-history-header = ─── Resultados recientes ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
# $arg0 (Number): duration; locale decides the unit form.
governance-hours = { $arg0 ->
        [one] { $arg0 } hora
       *[other] { $arg0 } horas
    }
governance-inactive-removal = { $arg0 } ha dejado automáticamente la moderación tras 180 días sin entrar.
governance-invalid-choice = Elige yes, no o abstain.
governance-invalid-duration = Duración no válida. Usa, por ejemplo, 30m, 12h o 3d.
governance-kind-appointment = Nombramiento
governance-kind-ban = Sanción
governance-kind-removal = Destitución
governance-legacy-ban-kick = Se aprobó una votación anterior. Has sido sancionado por { $arg0 }. Motivo: { $arg1 }
governance-menu-actions = Acciones
governance-menu-back = Volver
governance-menu-cancel = Cancelar
governance-menu-confirm = Confirmar
governance-menu-confirm-nominate = ¿Iniciar una votación para tu nombramiento como moderador?
governance-menu-confirm-resign = ¿Renunciar como moderador?
governance-menu-confirm-sponsor = ¿Apoyar esta petición de destitución?
governance-menu-confirm-withdraw = ¿Retirar tu candidatura a moderador?
governance-menu-find-player = Buscar otro jugador
governance-menu-history = Historial de votaciones
governance-menu-loading = Cargando estado de gobierno…
governance-menu-new-petition = Iniciar petición de destitución
governance-menu-nominate = Postularse a moderador
governance-menu-nomination-open = Tu votación de nombramiento está abierta
governance-menu-pause = Pausar ascenso de nuevos jugadores
governance-menu-pause-appeal = Vía de apelación
governance-menu-pause-duration = Duración (p. ej. 2d)
governance-menu-pause-reason = Motivo
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } apoyos
governance-menu-petitions = Peticiones de destitución
governance-menu-player-name = Nombre del jugador
governance-menu-players = Gestión de jugadores
governance-menu-recent-results = Resultados recientes
governance-menu-refresh = Actualizar
governance-menu-resign = Renunciar como moderador
governance-menu-resume = Levantar pausa del ascenso
governance-menu-resume-reason = Motivo para levantarla
governance-menu-staff-only = Solo moderadores y custodios pueden gestionar jugadores.
governance-menu-status = Tu estado
governance-menu-target = Nombre del moderador
governance-menu-title = Gobierno comunitario
governance-menu-votes = Votaciones abiertas
governance-menu-withdraw = Retirar candidatura
# $arg0 (Number): duration; locale decides the unit form.
governance-minutes = { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minutos
    }
governance-no-active-ban = Sin sanción activa
governance-no-active-pause = Sin pausa de ascenso activa
governance-nomination-withdrawn = Has retirado tu candidatura. El resultado se anunciará automáticamente.
governance-none = Ninguno
governance-not-member = No es miembro
governance-not-moderator = Actualmente no eres moderador.
governance-operation-error = La operación de gobernanza falló. Inténtalo de nuevo.
governance-own-choice-closed = Tu elección: { $arg0 }
governance-own-choice-open = Tu elección: { $arg0 }; puedes cambiarla antes del cierre.
governance-own-not-voted = Podías votar, pero no lo hiciste.
governance-own-not-voted-hint = Aún no has votado. Abre Gobierno y selecciona esta votación.
governance-own-not-voted-open = Aún no has votado.
governance-own-not-voter = No estás en el censo de esta votación.
governance-own-voted = Ya votaste
governance-pause-cleared = Pausa de ascenso levantada para { $arg0 }.
governance-pause-details = Pausa: { $arg0 } · Hasta { $arg1 } · Recurso: { $arg2 }
governance-pause-fields-required = Indica un motivo y una vía de recurso.
governance-pause-not-active = { $arg0 } no tiene una pausa de ascenso activa.
governance-pause-notice = Tu ascenso está pausado. Motivo: { $arg0 } · Hasta { $arg1 } · Recurso: { $arg2 }
governance-pause-set = Ascenso de { $arg0 } pausado hasta { $arg1 }.
governance-petition-already-sponsored = Ya apoyaste esta destitución ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } apoyos · Hasta { $arg3 }
governance-petition-started = Solicitud de destitución de { $arg0 }: { $arg1 }/{ $arg2 } apoyos; hasta { $arg3 }.
governance-petitions-header = ─── Solicitudes de destitución ───
governance-player-first-join = Primera entrada: { $arg0 }
governance-player-header = ─── { $arg0 } · Perfil de gobernanza ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Última entrada: { $arg0 }
governance-player-member-since = Miembro desde: { $arg0 }
governance-player-never-joined = El jugador { $arg0 } nunca ha entrado en este servidor.
governance-promotion-account-age = Primera entrada hace al menos 3 días
governance-promotion-header = ─── Requisitos de ascenso ───
governance-promotion-play-days = Juego efectivo en al menos 2 días (actualmente { $arg0 })
governance-promotion-playtime = Al menos 8 horas de juego efectivo (actualmente { $arg0 })
governance-resign-failed = No se pudo renunciar. Contacta a un custodio.
governance-resigned = { $arg0 } ha renunciado como moderador.
governance-result-announcement = Votación #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = No aprobada ({ $arg0 })
governance-result-open = En curso
governance-result-passed = Aprobada
governance-result-tally = { $arg0 } · Votantes { $arg1 }, participación { $arg2 }, sí { $arg3 }, no { $arg4 }, abstenciones { $arg5 }
governance-result-terminated = Finalizada ({ $arg0 })
governance-resume-notice = Se levantó tu pausa de ascenso. Motivo: { $arg0 }
governance-resume-reason-required = Indica el motivo para levantar la pausa.
governance-role-lookup-error = No se pudo comprobar la moderación. Contacta a un custodio.
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }
governance-storage-error = No se pudieron procesar los datos de gobernanza. Contacta a un custodio.
governance-target-not-moderator = { $arg0 } no es moderador actualmente.
governance-termination-inactive = Moderador retirado por inactividad prolongada
governance-termination-ineligible = El candidato perdió su condición de miembro activo
governance-termination-resigned = El moderador renunció
governance-termination-unknown = Sin motivo registrado
governance-termination-withdrew = El candidato retiró su candidatura
governance-vote-electorate = Votantes { $arg0 } · Quórum { $arg1 }
governance-vote-finalized = Cerrada: { $arg0 }
governance-vote-header = ─── Votación #{ $arg0 } ───
governance-vote-in-progress = En curso · { $arg0 } participantes; el desglose se publica al cerrar.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · Cierra { $arg3 } · Votantes { $arg4 } · Participación { $arg5 } · Quórum { $arg6 }{ $arg7 }
governance-vote-period = Inicio { $arg0 } · Cierre { $arg1 }
governance-vote-started = Votación de { $arg1 } #{ $arg0 } iniciada: { $arg2 }; votantes { $arg3 }, quórum { $arg4 }, cierra { $arg5 }. Abre Gobierno para ver los detalles.
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Votaciones anónimas abiertas ───

## World · Plots

plot-access-removed-for-you = Se retiró tu acceso a la parcela { $arg0 }.
plot-admin-created = Se registró { $arg0 } para { $arg1 } · { $arg2 }
plot-allowed = activado
plot-area-changed = Los límites cambiaron. Actualiza y vuelve a confirmar.
plot-area-confirm = ¿Sustituir los límites de «{ $arg0 }» por la selección ({ $arg1 ->
        [one] { $arg1 } celda de construcción
       *[other] { $arg1 } celdas de construcción
    })? Las construcciones se conservan.
plot-area-hint = //pos1 → //pos2 → guardar. También puedes seleccionar esquinas con el hacha de madera; añadir/restar para varias áreas.
plot-area-replace-save = Sustituir límites y guardar
plot-area-save = Guardar límites
plot-area-saved = Límites de «{ $arg0 }» guardados.
plot-arrival-removed = Punto de llegada eliminado.
plot-arrival-set = Punto de llegada guardado.
plot-arrival-text-saved = Título de llegada guardado.
plot-atmosphere-saved = Ambiente guardado.
plot-back = Volver
plot-board-auto-notify-result = Avisos programados para { $arg0 ->
        [one] { $arg0 } jugador
       *[other] { $arg0 } jugadores
    }.
plot-board-auto-registered-text = Se registró la parcela «{ $arg0 }». Marco de coordenadas de las celdas construidas: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Registro de { $arg0 } en { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }: { $arg5 }
plot-board-empty = Aún no hay registros comunitarios.
plot-board-header = Avisos y comentarios de la comunidad (últimos { $arg0 }; detalles: /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Aviso
plot-board-kind-objection = Objeción
plot-board-menu = Tablón comunitario
plot-board-notice-alert = { $arg0 } publicó un aviso: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } presentó una objeción: { $arg1 } · { $arg2 }
plot-board-rate-limited = Límite de publicaciones alcanzado. Inténtalo más tarde.
plot-board-record-invalid = Registro inexistente, ya resuelto o acción no permitida.
plot-board-registered-alert = { $arg0 } registró la parcela «{ $arg1 }»: { $arg2 }
plot-board-ruling-alert = Se dictó una resolución sobre la objeción de { $arg0 }: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Resolución: { $arg0 }
plot-board-saved = Registro { $arg0 } guardado.
plot-board-status-elapsed = Publicado hace al menos 72 horas
plot-board-status-open = Pendiente
plot-board-status-ruled = Resuelta
plot-board-status-withdrawn = Retirada
plot-board-text-invalid = La descripción debe tener de 12 a 1000 caracteres; la resolución, al menos 8.
plot-board-unavailable = No se han cargado los registros. Inténtalo más tarde o visita https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } retiró una objeción: { $arg1 } · { $arg2 }
plot-board-write-failed = No se pudo actualizar el tablón. Inténtalo más tarde.
plot-command-blocked = La protección bloqueó este comando. Usa /fill o /setblock directamente en tu parcela; pide al equipo que revise los comandos /execute complejos.
plot-command-description = Gestionar parcelas de construcción y proyectos públicos
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } creado · { $arg1 }
        [feminine] { -plot } { $arg0 } creada · { $arg1 }
       *[other] { -plot } { $arg0 } creado · { $arg1 }
    }
plot-created-for-you = { $arg1 } registró la parcela { $arg0 } para ti.
plot-deleted = Parcela liberada; la construcción permanece.
plot-denied = desactivado
plot-empty-summary = No hay una parcela aquí.
plot-error-admin-owner-only = Cambiar miembros con facultades de gestión requiere gestionar miembros y permisos.
plot-error-area-disconnected = La parcela debe formar una zona conectada.
plot-error-args = Argumentos inválidos o incompletos. Usa /plot help.
plot-error-arrival-location = Sitúate en un suelo seguro dentro de la zona construida del proyecto.
plot-error-arrival-missing = No hay punto de llegada.
plot-error-arrival-private = No puedes visitar este proyecto privado.
plot-error-arrival-text = Título no válido. Usa una sola línea: hasta 32 caracteres visibles en el título y 64 en el subtítulo.
plot-error-arrival-unsafe = Este punto de llegada no está disponible o ya no es seguro.
plot-error-busy = Se están guardando datos. Reinténtalo más tarde; esta operación no se ha enviado.
plot-error-flag = Permiso desconocido. Elige una operación de la lista de permisos.
plot-error-id = El ID no existe o es ambiguo. Usa /plot list.
plot-error-member-missing = Este jugador ya no es miembro.
plot-error-member-required = Los nuevos jugadores pueden ver parcelas y presentar objeciones. Debes ser miembro para crear un proyecto.
plot-error-name = El nombre del proyecto debe tener entre 1 y 48 caracteres.
plot-error-notice-text = Los avisos admiten hasta { $arg0 ->
        [one] { $arg0 } carácter
       *[other] { $arg0 } caracteres
    }, sin límite de líneas ni de longitud por línea; no se permiten caracteres de control.
plot-error-overlap = El área de construcción se solapa con otra parcela.
plot-error-owner-member = dueño de la parcela · Permisos del grupo
plot-error-owner-only = desactivado · Administración
plot-error-permission-changed = Los permisos, miembros o reglas heredadas cambiaron. Revisa los cambios de nuevo.
plot-error-permission-grant = No puedes conceder facultades de gestión que no tienes.
plot-error-permission-owner-scope = Solo el propietario, el propietario de la parcela superior o el personal pueden editar los permisos del propietario.
plot-error-player-unknown = No se encontró a ese jugador. Comprueba el nombre o UUID.
plot-error-release-owner-only = desactivado · Eliminar parcela
plot-error-selection = Marca dos esquinas con //pos1 y //pos2.
plot-error-selection-changed = La selección ha cambiado. Confirma de nuevo.
plot-error-staff-only = Solo moderadores o custodios pueden gestionar o registrar parcelas de otros jugadores.
plot-error-storage = Falló la operación de la parcela. Reinténtalo más tarde o contacta con el equipo.
plot-error-subplot-arrival = Mueve antes el punto de llegada de la parcela principal fuera de la selección.
plot-error-subplot-children = Libera las subparcelas antes de liberar la parcela principal.
plot-error-subplot-depth = Las subparcelas solo admiten un nivel.
plot-error-subplot-disconnected = Las celdas seleccionadas no están conectadas. Divide cada parte por separado.
plot-error-subplot-overlap = La selección se solapa con otra subparcela.
plot-error-subplot-own-arrival = Deja el punto de llegada dentro de la nueva área o muévelo primero.
plot-error-subplot-owner = No tienes permiso para esta operación de subparcela.
plot-error-subplot-parent = Marca ambas esquinas dentro de las celdas construidas de la parcela principal.
plot-error-subplot-supervisor-member = El dueño de la parcela principal ya supervisa esta zona y no necesita un rol de miembro.
plot-error-time-format = Introduce una hora HH:mm (00:00–23:59).
plot-error-transfer-owner-only = desactivado · Transferir propiedad
plot-error-transfer-self = Ya eres propietario de esta parcela.
plot-flag-set = { $arg0 }: { $arg1 }.
plot-help = Guía de parcelas
plot-here = Aquí: { $arg0 } · { $arg1 }
plot-invited = Acceso concedido a { $arg0 }.
plot-item-summary = Área construida { $arg0 } bloque² · { $arg1 ->
        [one] { $arg1 } miembro
       *[other] { $arg1 } miembros
    }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } bloque²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Sin enviar
plot-member-draft-reset = Borrar ajustes de categoría
plot-member-draft-role = Rol: { $arg0 }
plot-member-draft-submit = Revisar y confirmar
plot-member-draft-title = Borrador de miembro · { $arg0 }
plot-member-permissions = Permisos
plot-member-removed = Miembro del proyecto eliminado.
plot-menu-access = Acceso y miembros
plot-menu-access-by-settings = según los ajustes de acceso
plot-menu-admin = Añadir administrador de parcela
plot-menu-arrival = Punto de llegada
plot-menu-arrival-subtitle = Subtítulo (MiniMessage; opcional)
plot-menu-arrival-text = Título de llegada
plot-menu-arrival-title = Título (MiniMessage; vacío = nombre del proyecto)
plot-menu-atmosphere = Ambiente
plot-menu-clear = Despejado
plot-menu-collaborator = Añadir colaborador
plot-menu-confirm-delete = ¿Eliminar el registro de { $arg0 }? Se eliminarán la protección, los permisos de miembros y los ajustes. No se puede deshacer; las construcciones permanecerán.
plot-menu-confirm-subplot-transfer = ¿Transferir { $arg0 } a { $arg1 }? El dueño de la parcela principal conserva la supervisión.
plot-menu-confirm-transfer = ¿Transferir la gestión de { $arg0 } a { $arg1 }? Perderás el acceso; la autoría existente no cambia.
plot-menu-confirm-withdraw = ¿Retirar esta respuesta? El estado de retirada seguirá siendo público.
plot-menu-create = Crear parcela
plot-menu-create-for-player = Registrar parcela para este jugador
plot-menu-custom-time = Hora personalizada
plot-menu-delete = Eliminar registro de parcela
plot-menu-description = Descripción
plot-menu-feedback = Objetar este aviso
plot-menu-find-record = Buscar un registro
plot-menu-flag-container = Abrir contenedores
plot-menu-flag-entity = Interacción con entidades
plot-menu-follow-parent = Seguir a la parcela principal
plot-menu-follow-world = Seguir al mundo
plot-menu-group-admin = administrador de parcela
plot-menu-group-collaborator = Colaboradores
plot-menu-group-member = Acceso de miembros
plot-menu-group-newcomer = Acceso de principiantes
plot-menu-group-owner = dueño de la parcela
plot-menu-manage = Gestionar parcelas de jugadores
plot-menu-managed-empty = Este jugador aún no tiene parcelas.
plot-menu-managed-title = Parcelas de { $arg0 }
plot-menu-members = Miembros ({ $arg0 })
plot-menu-my-access = Tu acceso: { $arg0 } · { $arg1 }
plot-menu-name = Nombre del proyecto
plot-menu-owner = Propietario: { $arg0 }
plot-menu-parent-supervisor = Dueño de la parcela principal: { $arg0 } · supervisión
plot-menu-player = Nombre de jugador o UUID
plot-menu-project-kind = Tipo de proyecto
plot-menu-public-destinations = Destinos públicos
plot-menu-public-destinations-empty = Aún no hay proyectos públicos con punto de llegada.
plot-menu-rain = Precipitación
plot-menu-record-id = ID del registro; para registros antiguos, escribe el ID completo
plot-menu-related-notice = Ver aviso relacionado
plot-menu-remove-arrival = Quitar punto de llegada
plot-menu-remove-member = Eliminar miembro
plot-menu-rename = Renombrar proyecto
plot-menu-rule = Registrar resolución
plot-menu-ruling = Explica la resolución (8–1000 caracteres)
plot-menu-set-arrival = Fijar llegada aquí
plot-menu-set-role = Establecer como { $arg0 }
plot-menu-settings = Ajustes del proyecto
plot-menu-submit = Aplicar
plot-menu-subplot-create = Crear subparcela
plot-menu-subplot-of = Parte de { $arg0 }
plot-menu-subplot-parent = Volver a la parcela principal: { $arg0 }
plot-menu-subplots = Subparcelas ({ $arg0 })
plot-menu-time = Hora
plot-menu-time-input = Hora (HH:mm)
plot-menu-transfer = Transferir parcela
plot-menu-visit = Visitar proyecto
plot-menu-weather = Clima
plot-menu-withdraw = Retirar respuesta
plot-model-fit = Encuadrar todos los límites
plot-model-focus = Enfocar mi entorno
plot-model-layer-down = Bajar corte 1 bloque
plot-model-layer-up = Subir corte 1 bloque
plot-model-legend = Cian: guardado · Verde/dorado: esquinas 1/2 · Morado: límites a guardar · Blanco/rojo: tú/dirección
plot-model-level = Corte Y ≤ { $arg0 } (solo vista previa)
plot-model-limited = Se redujo el muestreo o el detalle. Enfoca una zona cercana o un rectángulo para ver más; usa solo límites para revisar los bordes.
plot-model-north-up = La vista superior tiene el norte arriba. Vuelve a 3D para rotar.
plot-model-perspective = Vista 3D
plot-model-preparing = Calculando límites y validando el área en segundo plano… Se actualizará automáticamente al terminar.
plot-model-progress = Leyendo modelo: { $arg0 } %…
plot-model-rotate-left = ↶ Girar a la izquierda
plot-model-rotate-right = ↷ Girar a la derecha
plot-model-show-all = Mostrar altura completa
plot-model-slice = Cortar sobre mi nivel
plot-model-terrain = Mostrar construcciones y terreno
plot-model-top = Vista superior
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } muestra sin cargar
       *[other] { $arg0 } muestras sin cargar
    } (gris). No se fuerza la carga de chunks.
plot-model-wireframe = Solo límites
plot-no-here = No hay ninguna parcela protegida aquí.
plot-notice-board = Tablón de la parcela
plot-notice-cleared = Tablón borrado
plot-notice-edit = Editar el tablón
plot-notice-input = Hasta { $arg0 ->
        [one] { $arg0 } carácter
       *[other] { $arg0 } caracteres
    }; se permiten varias líneas; deja en blanco para borrar
plot-notice-read = Leer aviso completo
plot-notice-saved = Tablón actualizado
plot-permission-all-actions = Todas las acciones
plot-permission-animal = Cuidar, domesticar y atar animales
plot-permission-break = Romper bloques
plot-permission-bucket = Usar cubos para líquidos y criaturas
plot-permission-category-build = Construcción y cambios
plot-permission-category-entity = Entidades
plot-permission-category-interaction = Interacción con bloques
plot-permission-category-management = Administración
plot-permission-category-storage = Almacenamiento y objetos
plot-permission-change-children = Subparcelas afectadas: { $arg0 }
plot-permission-change-confirm = Aplicar cambios
plot-permission-change-granted = Concedidos: { $arg0 }
plot-permission-change-members = Miembros afectados: { $arg0 }
plot-permission-change-more = { $arg0 } más
plot-permission-change-no-effect = Los permisos efectivos no cambian. Los ajustes explícitos prevalecerán sobre la herencia futura.
plot-permission-change-none = Ninguno
plot-permission-change-revoked = Revocados: { $arg0 }
plot-permission-change-subject = Destino: { $arg0 }
plot-permission-change-title = Confirmar cambios de permisos
plot-permission-create-subplot = Crear subparcela
plot-permission-decoration = Colocar y editar decoraciones
plot-permission-default-status = Predeterminado ({ $arg0 })
plot-permission-delete = Eliminar parcela
plot-permission-door = Puertas y vallas
plot-permission-entity-damage = Dañar entidades
plot-permission-entity-storage = Abrir almacenes de entidades
plot-permission-harvest = Recolectar bayas, enredaderas y miel
plot-permission-ignite = Encender fuego
plot-permission-item-drop = Soltar objetos
plot-permission-item-pickup = Recoger objetos del suelo
plot-permission-manage-area = Editar límites
plot-permission-manage-members = Gestionar miembros
plot-permission-manage-permissions = Gestionar permisos
plot-permission-manage-settings = Editar ajustes de parcela
plot-permission-management = Ajustes y propiedad de la parcela
plot-permission-modify = Modificar bloques y fertilizar
plot-permission-operation = Acción
plot-permission-personal = Permisos individuales
plot-permission-personal-source = Ajuste individual
plot-permission-place = Colocar bloques y vehículos
plot-permission-reset = Restablecer valores
plot-permission-result = Estado
plot-permission-ride = Montar vehículos y animales
plot-permission-select-state = Elegir estado: { $arg0 }
plot-permission-sign = Editar carteles
plot-permission-source-group = Permisos del grupo
plot-permission-source-local = Ajuste local
plot-permission-source-owner = Derecho de recuperación del propietario
plot-permission-source-parent = Parcela superior
plot-permission-source-role = Valor del rol
plot-permission-state-default = Predeterminado
plot-permission-switch = Botones, palancas y placas
plot-permission-trade = Comerciar con aldeanos
plot-permission-transfer = Transferir propiedad
plot-permission-workstation = Usar instalaciones y estaciones de trabajo
plot-pos1 = Primer punto
plot-pos2 = Segundo punto
plot-private = Proyecto privado
plot-public = Proyecto público
plot-public-set = Tipo de proyecto: { $arg0 }.
plot-region-adjust = Ajustar este rectángulo
plot-region-apply = Terminar ajuste del rectángulo
plot-region-cancel = Cancelar ajuste del rectángulo
plot-region-changed = La selección ha cambiado. Selecciona de nuevo el rectángulo.
plot-region-delete = Eliminar este rectángulo
plot-region-delete-confirm = ¿Quitar el rectángulo #{ $arg0 } del borrador? Los edificios permanecen. Los límites solo cambian al guardar; se puede deshacer.
plot-region-edit-hint = Ajusta este rectángulo con //pos1 y //pos2. Los demás no cambian. Termina el ajuste y después guarda la parcela.
plot-region-finish-first = Primero termina o cancela el ajuste actual del rectángulo.
plot-region-open = Gestionar rectángulos ({ $arg0 })
plot-region-select-hint = Selecciona un rectángulo numerado para enfocarlo y resaltarlo. Las líneas grises muestran esta página; al unirlos pueden cambiar los números.
plot-renamed = Proyecto renombrado.
plot-role-admin = administrador de parcela
plot-role-changed-for-you = Tu función en la parcela { $arg0 } ahora es { $arg1 }.
plot-role-collaborator = colaborador
plot-role-owner = dueño de la parcela
plot-role-parent-owner = dueño de la parcela principal
plot-role-server-staff = personal del servidor
plot-selection-add = Añadir caja actual
plot-selection-clear = Reiniciar los dos puntos
plot-selection-empty = Marca dos esquinas con //pos1 y //pos2.
plot-selection-exit = Salir de edición / vista previa
plot-selection-fit = Restaurar límites guardados
plot-selection-incomplete = Hay un punto marcado. Marca el otro; nada cambia antes de enviar.
plot-selection-mode = Elegir puntos en el mundo
plot-selection-no-redo = No hay cambios de selección que rehacer.
plot-selection-no-undo = No hay cambios anteriores de selección que deshacer.
plot-selection-open = Editar límites
plot-selection-redo = Rehacer último paso
plot-selection-replace = Reemplazar con caja actual
plot-selection-session-hint = Agacharse+F o /plot edit para volver. Sale tras 20 minutos de inactividad.
plot-selection-size = Selección: { $arg0 } × { $arg1 } × { $arg2 } bloques (X/Y/Z)
plot-selection-stopped = Selección terminada. Los borradores se conservan por parcela.
plot-selection-subtract = Restar caja actual
plot-selection-undo = Deshacer último paso
plot-subplot-created = Subparcela { $arg0 } creada · { $arg1 } bloque²
plot-subplot-created-for-you = Ahora eres propietario de la subparcela { $arg0 } dentro de { $arg1 }.
plot-subplot-owner-changed = La subparcela { $arg0 } ahora pertenece a { $arg1 }.
plot-subplot-parent-transferred = La parcela principal de tu subparcela { $arg0 } ahora pertenece a { $arg1 }.
plot-subplot-released = Se liberó la subparcela { $arg0 }; la parcela principal sigue protegiendo esa área.
plot-title = Parcelas
plot-transferred = Parcela { $arg0 } transferida a { $arg1 }.
plot-transferred-to-you = { $arg1 } te transfirió la parcela { $arg0 }.

## World · Vehicles

vehicle-admin-help = /vehicle — { vehicle-model-title }
    Modelos: model | model help
    Captura: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Vehículos: spawn <model> [x y z] | reload
vehicle-command-description = Conducir y gestionar vehículos de entidades de visualización
vehicle-controls = { $arg0 }: W/S y A/D para conducir; rueda para marchas o acelerador; intercambiar manos cambia el modo; agacharse para salir. /vehicle help · /vehicle dashboard abre los controles.
vehicle-dash-assisted = Asistencia de estabilidad
vehicle-dash-automatic = Automático
vehicle-dash-contact = Apoyo en suelo / agua: { $arg0 }
vehicle-dash-details = Detalles
vehicle-dash-down = Bajar marcha −
vehicle-dash-drive = Conducción
vehicle-dash-energy = Consumo { $arg0 } U/min · autonomía estimada { $arg1 } min / { $arg2 } km
vehicle-dash-engine-start = Arrancar motor
vehicle-dash-engine-stop = Apagar motor
vehicle-dash-fuel = Combustible { $arg0 } / { $arg1 } U · { $arg2 } %
vehicle-dash-gear = Marcha { $arg0 }
vehicle-dash-gearbox = Caja de cambios
vehicle-dash-handbrake = Estacionamiento / freno de mano: { $arg0 }
vehicle-dash-heading = Rumbo { $arg0 }°
vehicle-dash-health = Estado { $arg0 } % · ocupantes { $arg1 } / { $arg2 }
vehicle-dash-input = Acelerador { $arg0 } % · freno { $arg1 } % · dirección { $arg2 } %
vehicle-dash-manual = Manual
vehicle-dash-mode-switch = Cambiar a { $arg0 }
vehicle-dash-position = Origen { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Centrar panel
vehicle-dash-rpm = RPM { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Velocidad { $arg0 } km/h · límite { $arg1 }
vehicle-dash-throttle-down = Acelerador −5 %
vehicle-dash-throttle-up = Acelerador +5 %
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Sin asistencia
vehicle-dash-unavailable = Los controles no están disponibles; vuelve a abrirlos desde el asiento del conductor.
vehicle-dash-up = Subir marcha +
vehicle-dash-vertical = Altitud { $arg0 } m · velocidad vertical { $arg1 } m/s
vehicle-error = Falló la operación del vehículo: { $arg0 }
vehicle-group-changed = La estructura del grupo cambió. Vuelve a seleccionarlo antes de importar.
vehicle-group-invalid = El grupo contiene entidades no compatibles, gestionadas, ausentes o lejanas. Todas deben estar en este mundo a 32 bloques como máximo.
vehicle-group-limit = Importa como máximo 128 entidades de visualización y 256 miembros en total.
vehicle-group-not-found = No se encontró un grupo. Selecciona uno, apunta a una entidad de visualización de bloque u objeto o indica su UUID.
vehicle-group-selected = Grupo seleccionado: { $arg0 ->
        [one] { $arg0 } entidad de visualización
       *[other] { $arg0 } entidades de visualización
    } · raíz { $arg1 }
vehicle-help = /vehicle — Control de vehículos
    Montar: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Conducir: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    Panel: hud [on/off] | dashboard [drive/gearbox/details/off]
    Mantenimiento: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } kn · empuje { $arg1 } % · rumbo { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · altitud { $arg1 } m · velocidad vertical { $arg2 } m/s · acelerador { $arg3 } % · { $arg4 }
vehicle-hud-status = Combustible { $arg0 } % · estado { $arg1 } % · { $arg2 }
vehicle-model-add = Añadir componente
vehicle-model-add-here = Añadir apoyo a mis pies
vehicle-model-anchor = Situar aquí el origen de la vista previa, con tu orientación
vehicle-model-animation = Animación
vehicle-model-apply = Aplicar / confirmar
vehicle-model-auto = Recalcular límites de colisión
vehicle-model-block = Bloque
vehicle-model-center-here = Situar centro de masa a mis pies
vehicle-model-center-look = Situar centro de masa en la superficie apuntada
vehicle-model-check = Comprobar estructura y parámetros
vehicle-model-checked = Estructura y parámetros válidos. Al guardar también se comprueban datos de visualización, vehículos existentes y colisiones con el mundo.
vehicle-model-clone = Clonar modelo
vehicle-model-colliders = Cajas de colisión
vehicle-model-copy = Duplicar y editar (los asientos copiados son de pasajero)
vehicle-model-corner-cancel = Cancelar caja de colisión pendiente
vehicle-model-corner-first = Marcar primera esquina a mis pies
vehicle-model-corner-first-look = Marcar primera esquina en la superficie apuntada
vehicle-model-corner-hint = Primera esquina marcada. Ve a la esquina opuesta, incluida su altura, y usa /vehicle model collider corner2. La caja sigue los ejes del modelo.
vehicle-model-corner-required = Marca primero la primera esquina con /vehicle model collider corner1.
vehicle-model-corner-second = Usar mis pies como esquina opuesta y añadir caja
vehicle-model-corner-second-look = Usar la superficie apuntada como esquina opuesta
vehicle-model-create = Crear modelo
vehicle-model-delete = Eliminar modelo
vehicle-model-delete-warning = ¿Eliminar este modelo permanentemente? No se puede si hay vehículos que lo usan.
vehicle-model-deleted = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } eliminado
        [feminine] { -vehicle-model } eliminada
       *[other] { -vehicle-model } eliminado
    }
vehicle-model-discard = Descartar borrador
vehicle-model-discard-warning = ¿Descartar todos los cambios sin guardar? No se puede deshacer.
vehicle-model-discarded = Borrador descartado
vehicle-model-draft = Borrador sin guardar
vehicle-model-driver = Asiento de conductor
vehicle-model-driver-here = Añadir asiento de conductor a mis pies
vehicle-model-edit = Editar / continuar borrador
vehicle-model-exit = Salida / ajuste fino
vehicle-model-group = Añadir grupo de Axiom
vehicle-model-help = /vehicle model — { vehicle-model-title }
    Modelos: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Editar: set <field> <value...> | part/seat/collider/support — pulsa Tab para operaciones y argumentos
    Borrador: preview [off] | undo | redo | save | cancel
    En el mundo: anchor | pick | here | look | snap [on/off] | move <index> <dx dy dz>
    { vehicle-model-position-hint }
    seat position/exit | part position | support add/set | collider position: here / look
    collider corner1/corner2 [look] | collider cancel — { vehicle-model-corner-hint }
    Más acciones: check | apply — { vehicle-model-save-continue }
vehicle-model-here = Usar la posición actual de mis pies
vehicle-model-history-empty = No hay cambios en esta dirección del historial.
vehicle-model-import = Importar grupo de Axiom
vehicle-model-item = Objeto
vehicle-model-library = Modelos guardados · los borradores permanecen en memoria hasta guardarse
vehicle-model-look = Colocar apuntando (cierra el menú; clic derecho para confirmar)
vehicle-model-look-missing = No se encontró superficie. Apunta a un bloque del mundo o una pieza de tu vista previa a 32 bloques como máximo.
vehicle-model-manual = Introducir coordenadas locales
vehicle-model-options = Opciones
vehicle-model-parameters = Parámetros físicos y del motor
vehicle-model-parts = Elementos de visualización
vehicle-model-passenger-here = Añadir asiento de pasajero a mis pies
vehicle-model-pick = Editar la pieza de la vista previa que estás mirando
vehicle-model-pick-hint = Abre una vista previa y apunta a una pieza de bloque u objeto, o selecciona primero un componente en el editor.
vehicle-model-place-cancelled = Colocación cancelada por caducidad, cambio de edición o cancelación manual.
vehicle-model-place-hint = Colocación por apuntado activa durante 30 segundos. Apunta a un bloque o a tu vista previa; clic derecho con la mano principal confirma y agacharse + clic derecho cancela.
vehicle-model-position = Posición / ajuste fino
vehicle-model-position-hint = X/Y/Z locales · here usa tus pies; look, la superficie apuntada · la cuadrícula opcional sigue el paso de ajuste.
vehicle-model-preview = Vista previa privada
vehicle-model-preview-failed = Vista previa no disponible; se conserva el borrador: { $arg0 }
vehicle-model-preview-hint = Vista previa privada activada. Se borra tras cinco minutos sin editar; /vehicle model preview off para cerrar.
vehicle-model-redo = Rehacer último cambio
vehicle-model-remove = Eliminar componente
vehicle-model-remove-warning = ¿Eliminar este componente? Los índices posteriores cambian; se puede deshacer.
vehicle-model-rotate = Rotar componente
vehicle-model-rotate-hint = Rotación local relativa: X inclinación, Y orientación, Z alabeo. La posición no cambia.
vehicle-model-save = Validar y guardar
vehicle-model-save-continue = Guardar y seguir editando
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } guardado
        [feminine] { -vehicle-model } guardada
       *[other] { -vehicle-model } guardado
    }
vehicle-model-scale = Escalar componente
vehicle-model-scale-all = Escalar todos los ejes × { $arg0 }
vehicle-model-scale-hint = Multiplica o divide las longitudes de los ejes locales; no son dimensiones de la caja global.
vehicle-model-seats = Asientos y salidas
vehicle-model-selected = Importar elementos seleccionados
vehicle-model-size = Cambiar tamaño de colisión
vehicle-model-size-hint = Anchura, altura y longitud completas; cambia el tamaño sin mover el centro.
vehicle-model-snap-off = Ajuste a cuadrícula: desactivado
vehicle-model-snap-on = Ajuste a cuadrícula: activado
vehicle-model-step = Cambiar paso de ajuste
vehicle-model-supports = Puntos de apoyo
vehicle-model-text = Texto
vehicle-model-title = Gestión de modelos de vehículos
vehicle-model-tools = Herramientas en el mundo
vehicle-model-transform-step = Cambiar paso de ajuste
vehicle-model-undo = Deshacer último cambio
vehicle-result = Vehículo: { $arg0 }
vehicle-selection-info = Selección: { $arg0 ->
        [one] { $arg0 } entidad de visualización
       *[other] { $arg0 } entidades de visualización
    } · grupo { $arg1 } · origen { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Colisión / obstáculo
vehicle-status-damage = Daños críticos del vehículo
vehicle-status-disabled = Vehículo inhabilitado
vehicle-status-engine-off = Motor apagado
vehicle-status-engine-on = Motor en marcha
vehicle-status-gear-limit = Límite de control alcanzado
vehicle-status-grounded = Embarcación encallada
vehicle-status-low-fuel = Poco combustible
vehicle-status-manual-only = Cambia primero al modo manual
vehicle-status-no-fuel = Sin combustible
vehicle-status-overrev = Reducir marcha excedería las RPM del motor
vehicle-status-shifting = Cambio de marcha en curso
vehicle-status-stall = Motor calado
vehicle-status-stop-first = Detente antes de cambiar de sentido o estacionar
vehicle-value-off = Desactivado
vehicle-value-on = Activado

## World · Elevators, regeneration and spawn

async-regen-already-running = Ya tienes una regeneración en curso; usa /regen status o /regen cancel
async-regen-apply-expired = No hay un plan válido; ejecuta primero /regen preview
async-regen-apply-plan = Aplicar plan guardado
async-regen-apply-started = Aplicando { $arg0 } a la selección guardada: { $arg1 }
async-regen-awaiting-apply = Plan pendiente de aplicar: { $arg0 } · { $arg1 } · caduca en { $arg2 ->
        [one] { $arg2 } segundo
       *[other] { $arg2 } segundos
    }
async-regen-block-preview-progress = vista previa de bloques · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } analizados · { $arg2 } cambios · { $arg3 ->
        [one] { $arg3 } entidad de bloque
       *[other] { $arg3 } entidades de bloque
    } en riesgo
async-regen-block-preview-summary = Vista previa: { $arg0 ->
        [one] { $arg0 } cambio de bloque
       *[other] { $arg0 } cambios de bloques
    } para { $arg2 }; riesgo de sobrescritura: { $arg1 ->
        [one] { $arg1 } entidad de bloque existente
       *[other] { $arg1 } entidades de bloque existentes
    }
async-regen-cancelled = Regeneración cancelada. Los { $arg0 ->
        [one] { $arg0 } cambio
       *[other] { $arg0 } cambios
    } ya aplicados permanecen en el mundo
async-regen-command-description = Regenerar de forma asíncrona una selección de WorldEdit
async-regen-completed = Regeneración completada: { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    } cambiados en { $arg1 }
async-regen-failed = Regeneración detenida por un error interno. Los { $arg0 ->
        [one] { $arg0 } cambio
       *[other] { $arg0 } cambios
    } ya aplicados permanecen en el mundo
async-regen-filter-all = todos los tipos de bloque
async-regen-invalid-blocks = Tipos desconocidos o que no son bloques: { $arg0 }
async-regen-not-running = No tienes ninguna regeneración en curso
async-regen-nothing-to-apply = La selección ya coincide con este plan; no hay nada que aplicar
async-regen-plan-cancel = Cancelar
async-regen-plan-dialog-title = Aplicar plan de regeneración
async-regen-preview-cancelled = Vista previa cancelada; no se modificaron datos del mundo
async-regen-preview-started = Vista previa iniciada: selección { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    } · zona { $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } · modo { $arg2 }
async-regen-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } planificados · { $arg3 ->
        [one] { $arg3 } cambio
       *[other] { $arg3 } cambios
    } aplicados
async-regen-safety-note = No cambiará nada fuera de la selección guardada. Las construcciones dentro pueden sobrescribirse y Vanilla no identifica los bloques colocados por jugadores. Revisa la visualización y haz una copia antes de aplicar.
async-regen-scope-blocks = regenerar bloques que coincidan con { $arg0 }
async-regen-selection-required = Completa primero una selección de WorldEdit
async-regen-skipped-ungenerated = Chunks de destino nunca generados omitidos: { $arg0 ->
        [one] { $arg0 } chunk
       *[other] { $arg0 } chunks
    }. No se creó ni modificó ninguno.
async-regen-start-failed = No se pudo iniciar la regeneración; consulta el registro del servidor
async-regen-state-applying = aplicando cambios
async-regen-state-cancelled = cancelada
async-regen-state-completed = completada
async-regen-state-failed = fallida
async-regen-state-generating = generando y comparando chunks
async-regen-state-preparing = preparando el mundo de origen
async-regen-status = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } planificados · { $arg3 } de { $arg4 } cambios encontrados aplicados
async-regen-structure-metadata-applied = Se migraron { $arg0 } inicios de estructura y { $arg1 } referencias de chunk; /locate y la generación dependiente de estructuras pueden reconocerlos
async-regen-upgrade-apply-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } planificados · { $arg3 ->
        [one] { $arg3 } bloque
       *[other] { $arg3 } bloques
    } y { $arg4 ->
        [one] { $arg4 } celda de bioma
       *[other] { $arg4 } celdas de bioma
    } aplicados
async-regen-upgrade-boundary-note = Se omitirán { $arg0 ->
        [one] { $arg0 } celda de bioma
       *[other] { $arg0 } celdas de bioma
    } que tocan el límite para no modificar nada fuera de la selección
async-regen-upgrade-completed = Actualización completada: { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    } y { $arg1 ->
        [one] { $arg1 } celda de bioma
       *[other] { $arg1 } celdas de bioma
    } cambiados en { $arg2 }
async-regen-upgrade-preview-progress = vista previa · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } analizados · { $arg2 } cambios de bloque · { $arg3 ->
        [one] { $arg3 } celda de bioma
       *[other] { $arg3 } celdas de bioma
    } · { $arg4 ->
        [one] { $arg4 } estructura
       *[other] { $arg4 } estructuras
    }
async-regen-upgrade-preview-summary = Vista previa: { $arg0 ->
        [one] { $arg0 } cambio de bloque
       *[other] { $arg0 } cambios de bloques
    }, { $arg1 ->
        [one] { $arg1 } celda de bioma
       *[other] { $arg1 } celdas de bioma
    }, { $arg2 ->
        [one] { $arg2 } estructura
       *[other] { $arg2 } estructuras
    } (aptas para migrar todos los metadatos: { $arg3 }); riesgo de sobrescritura: { $arg4 ->
        [one] { $arg4 } entidad de bloque existente
       *[other] { $arg4 } entidades de bloque existentes
    }
async-regen-upgrade-scope-biomes = solo biomas
async-regen-upgrade-scope-terrain = terreno, estructuras y biomas
async-regen-usage = Uso: /regen preview blocks [tipos...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Visualización en { $arg0 }: cian = límite de selección, naranja = muestra de bloques que cambiarán, rojo = entidad de bloque en riesgo. Solo tú ves estas partículas.
async-regen-visualization-selection = Selección en { $arg0 }: las partículas cian marcan el límite. Solo tú las ves.
async-regen-world-edit-required = Se necesita WorldEdit para leer la selección
iron-elevator-down = ↓ Agacharse
iron-elevator-up = ↑ Saltar
spawn-beacon-color-command-description = Cambiar el ciclo de color del faro del punto de aparición
spawn-beacon-color-disabled = Ciclo de color del faro del punto de aparición desactivado
spawn-beacon-color-enabled = Ciclo de color del faro del punto de aparición activado
spawn-command-description = Volver al punto de aparición
spawn-world-missing = El mundo del punto de aparición no está disponible

## Entertainment · Music

music-action-add-queue = Añadir a la cola
music-action-play-nearby = Reproducir en el tocadiscos más cercano
music-action-play-now = Reproducir ahora en este tocadiscos
music-action-take-disc = Obtener el disco
music-added-to-queue-rich = Añadida a la cola: { $music }
music-album = Álbum: { $arg0 }
music-all-in-queue = Todas las canciones están ya en la cola
music-artist = Artista: { $arg0 }
music-back = Atrás
music-browser-left-click = Clic izquierdo
music-browser-right-click = Clic derecho
music-cache-clear-done = Caché de música en línea limpiada
music-cache-clear-started = Limpiando la caché de música en línea...
music-cache-operation-failed = Error en la operación de caché de música: { $arg0 }
music-cache-status = Caché de música en línea: { $arg0 ->
        [one] { $arg0 } archivo
       *[other] { $arg0 } archivos
    }, { $arg1 } / { $arg2 }
music-cached = En caché - listo para reproducir
music-clear-queue = Borrar cola
music-clear-queue-done = Cola vaciada ({ $arg0 ->
        [one] { $arg0 } canción
       *[other] { $arg0 } canciones
    })
music-command-description = Música local, en línea y NBS para tocadiscos
music-disc-left = Clic izquierdo: tomar el disco
music-disc-right = Clic derecho: reproducir en el tocadiscos más cercano
music-duplicate-in-queue = Esta canción ya está en la cola
music-duration = Duración: { $arg0 }
music-empty-library = La biblioteca musical está vacía
music-empty-library-description = No hay pistas de audio locales ni NBS disponibles
music-empty-nbs = No se encontraron pistas NBS
music-empty-nbs-description = Cambia a toda la música o añade un archivo NBS
music-empty-playlist = Esta lista no contiene canciones disponibles
music-empty-playlist-description = Comprueba que la lista sea pública y que la plataforma seleccionada sea correcta
music-empty-search = No hay canciones coincidentes
music-empty-search-description = Prueba otra palabra clave o comprueba el estado de las fuentes personalizadas
music-finished = Finalizada
music-format = Formato: { $arg0 }
music-inventory-full = El inventario está lleno. No se ha podido entregar el disco de música
music-jukebox-audible-field-conflict = Otro tocadiscos reproduce una canción diferente en una zona claramente audible superpuesta
music-jukebox-music-mode = Modo música
music-jukebox-play-order = Orden de reproducción
music-jukebox-range = Alcance de reproducción
music-jukebox-range-value = Actual: { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    }
music-jukebox-rhythm-mode = Modo ritmo
music-jukebox-too-far = Estás demasiado lejos del tocadiscos (máximo { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    })
music-jukebox-tp-hover = Haz clic para teletransportarte al tocadiscos
music-jukebox-unavailable = El tocadiscos se eliminó o su chunk no está disponible
music-jukebox-usage-mode = Uso del tocadiscos
music-jukebox-volume = Volumen del tocadiscos
music-jukebox-volume-value = Actual: { $arg0 }%
music-library-button = Biblioteca musical
music-library-random-mode = Biblioteca aleatoria
music-library-random-mode-desc = Reproduce al azar desde toda la biblioteca musical sin repetir inmediatamente la misma canción
music-loading-title = Cargando
music-menu-nbs-title = Música NBS - Página { $arg0 }/{ $arg1 }
music-menu-playlist-title = Lista de reproducción: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = Búsqueda NBS: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-title = Buscar: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-title = Discos de música - Página { $arg0 }/{ $arg1 }
music-mode-switched = Cambiado a { $arg0 }
music-nearest-jukebox-missing = No se ha encontrado ningún tocadiscos en un radio de { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    }
music-next-page = Página siguiente
music-no-disc = Sin disco
music-no-files = No hay archivos de música disponibles
music-no-search-results-rich = No se encontraron canciones que contengan "{ $keyword }"
music-now-playing-rich = En reproducción: { $music } en el tocadiscos { $location }
music-online-partial-failure = Solicitudes a fuentes fallidas: { $arg0 ->
        [one] { $arg0 } solicitud
       *[other] { $arg0 } solicitudes
    }; se muestran los resultados disponibles
music-online-rate-limited = Demasiadas solicitudes de música. Reinténtalo en { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }.
music-online-searching = Buscando "{ $arg0 }" en fuentes locales y en línea...
music-original-author = Autor original: { $arg0 }
music-page-out-of-range = Página fuera de rango. La última página es la { $arg0 }
music-page-total = { $arg0 ->
        [one] { $arg0 } canción
       *[other] { $arg0 } canciones
    } en total
music-page-usage-desc = Saltar a una página
music-partial-results = Solicitudes a fuentes fallidas: { $arg0 ->
        [one] { $arg0 } solicitud
       *[other] { $arg0 } solicitudes
    }; se muestran los resultados disponibles
music-paused = En pausa
music-plasmo-voice-unavailable = La canción ha empezado, pero no puedes oírla porque Plasmo Voice no está instalado, conectado o activado
music-play-next = Reproducir siguiente
music-play-random = Reproducir aleatoria
music-playback-failed = No se pudo reproducir "{ $arg0 }". Comprueba la fuente o el archivo de música e inténtalo de nuevo.
music-playback-status = Estado de reproducción: { $arg0 }
music-playing = Reproduciendo
music-playing-now-rich = Reproduciendo: { $music }
music-playlist-import-button = Importar lista
music-playlist-import-done = «{ $arg0 }» importada: { $arg1 } de { $arg2 ->
        [one] { $arg2 } canción
       *[other] { $arg2 } canciones
    }.
music-playlist-import-failed = Error al importar la lista: { $arg0 }
music-playlist-import-prompt = Elige una plataforma y pega el ID o enlace de una lista pública:
music-playlist-import-usage-desc = Importar una lista pública desde otra plataforma musical
music-playlist-importing = Importando lista...
music-playlist-reference-prompt = Pega el ID o enlace de una lista pública
music-prev-page = Página anterior
music-queue-empty = La cola está vacía
music-queue-move-down = Bajar
music-queue-move-up = Subir
music-queue-remove = Quitar canción
music-queue-summary = Cola: { $arg0 ->
        [one] { $arg0 } canción
       *[other] { $arg0 } canciones
    }
music-queue-summary-description = Página { $arg0 }/{ $arg1 }
music-random-add = Añadir canción aleatoria
music-random-added-to-queue-rich = Añadida al azar a la cola: { $music }
music-random-disc = Disco aleatorio
music-random-disc-got-rich = Has obtenido un disco de música aleatorio: { $music }
music-random-mode = Lista aleatoria
music-random-mode-desc = Reproduce la lista al azar sin repetir inmediatamente la misma canción
music-rank-down = Bajada
music-rank-up = Subida
music-reload-done = Recarga completada: { $arg0 ->
        [one] { $arg0 } pista local
       *[other] { $arg0 } pistas locales
    }; fuentes LX disponibles { $arg1 }/{ $arg2 }; suscripciones comprobadas { $arg3 }, actualizadas { $arg4 }
music-reload-failed = Error al recargar la música: { $arg0 }
music-reload-issues = Problemas de recarga: { $arg0 }
music-reload-partial = Recarga completada conservando el estado anterior: { $arg0 ->
        [one] { $arg0 } pista local
       *[other] { $arg0 } pistas locales
    }; fuentes LX disponibles { $arg1 }/{ $arg2 }; suscripciones comprobadas { $arg3 }, actualizadas { $arg4 }
music-reload-started = Recargando archivos de música y fuentes personalizadas...
music-remote-source-error = error de actualización: { $arg0 }
music-remote-source-import-done = Suscripción de fuente LX importada: { $arg0 }
music-remote-source-import-failed = Error al importar la fuente LX: { $arg0 }
music-remote-source-import-started = Importando suscripción de fuente LX...
music-remote-source-remove-done = Suscripción de fuente LX eliminada: { $arg0 }
music-remote-source-remove-failed = No se pudo eliminar la suscripción de fuente LX: { $arg0 }
music-remote-source-remove-not-found = No se encontró la suscripción de fuente LX: { $arg0 }
music-remote-source-update-done = Comprobación: { $arg0 ->
        [one] { $arg0 } suscripción
       *[other] { $arg0 } suscripciones
    }; actualizadas: { $arg1 }; fallidas: { $arg2 }.
music-remote-source-update-failed = Error al actualizar la fuente LX: { $arg0 }
music-remote-source-update-started = Comprobando suscripciones de fuentes LX importadas...
music-remote-source-updated-at = actualizado: { $arg0 }
music-remote-sources-title = Suscripciones de fuentes LX importadas: { $arg0 }
music-removed-from-queue-rich = Eliminada de la cola: { $music }
music-repeat-one-mode = Repetir una canción
music-repeat-one-mode-desc = Repite la canción actual hasta que avances manualmente
music-request-error = Solicitud fallida: { $arg0 }
music-rhythm-animation-offset = Desfase de animación { $arg0 } ms
music-rhythm-calibration = Calibración de latencia
music-rhythm-calibration-accumulating = Confirmando un ritmo estable… sigue tocando
music-rhythm-calibration-adapting = Ajustando el ritmo… sigue tocando
music-rhythm-calibration-complete = Prueba de latencia completa · juego de ritmo listo
music-rhythm-calibration-done = Prueba de latencia completa · ajustes guardados
music-rhythm-calibration-failed = El audio de la prueba se interrumpió. La prueba se cerró de forma segura; vuelve a iniciarla
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Teclado visual → Ratón visual
music-rhythm-calibration-listen = Escucha una vez el ritmo generado · todavía no pulses
music-rhythm-calibration-minecraft-instruction = Repite el ritmo de Minecraft Note Block y pulsa 1–4
music-rhythm-calibration-no-input = No se detectó ninguna entrada. Revisa las teclas y el volumen y sigue el ritmo
music-rhythm-calibration-out-of-range = El retraso medido está fuera del rango admitido. Revisa los ajustes de audio
music-rhythm-calibration-plasmo-instruction = Sigue ahora: repite el ritmo de Plasmo Voice y pulsa 1–4 en cada golpe
music-rhythm-calibration-plasmo-ready = Prueba Minecraft Note Block completa; primero escucha el mismo ritmo por Plasmo Voice
music-rhythm-calibration-pointer-instruction = Haz clic izquierdo o derecho cuando la nota cruce la línea.
music-rhythm-calibration-prompt-confirm = Iniciar prueba completa
music-rhythm-calibration-prompt-confirm-hint = Vuelve aquí al terminar.
music-rhythm-calibration-prompt-later = Ahora no
music-rhythm-calibration-prompt-reason = Haz un test de latencia antes de jugar.
music-rhythm-calibration-prompt-title = Se requiere una prueba de latencia antes de jugar
music-rhythm-calibration-realigning = El audio se interrumpió. Se repetirá la demostración y se conservarán las muestras completas
music-rhythm-calibration-required = Completa la prueba de latencia antes de iniciar el juego de ritmo
music-rhythm-calibration-reset-done = Se reinició la calibración de latencia
music-rhythm-calibration-result-hold = Todas las pruebas terminaron. Revisa el resultado antes de volver automáticamente
music-rhythm-calibration-stage-minecraft = Bloque musical de Minecraft
music-rhythm-calibration-stage-plasmo = Prueba de audio Plasmo Voice y entrada
music-rhythm-calibration-stage-pointer = Ratón
music-rhythm-calibration-stage-visual = Prueba visual y de entrada
music-rhythm-calibration-visual-instruction = Pulsa 1–4 cuando la nota cruce la línea.
music-rhythm-calibration-visual-preview = Observa una secuencia completa sin introducir nada · empieza cuando cambie la instrucción
music-rhythm-calibration-voice-required = La prueba completa requiere Plasmo Voice conectado y activado
music-rhythm-chart-preparing = Preparando el mapa completo…
music-rhythm-combo = Combo ×{ $arg0 }
music-rhythm-difficulty-easy = Fácil
music-rhythm-difficulty-expert = Experto
music-rhythm-difficulty-hard = Difícil
music-rhythm-difficulty-normal = Normal
music-rhythm-exit = Salir del juego de ritmo
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
music-rhythm-get-ready = Prepárate
music-rhythm-go = ¡YA!
music-rhythm-good = Bien
music-rhythm-great = Genial
music-rhythm-latency = Latencia { $arg0 } ms
music-rhythm-latency-test = Prueba completa de latencia
music-rhythm-miss = Fallo
music-rhythm-mode-falling = Notas descendentes
music-rhythm-mode-radial = Ataque radial
music-rhythm-mode-spatial-aim = Puntería espacial
music-rhythm-network-too-high = La latencia de red es demasiado alta para una puntuación justa ({ $arg0 } ms; límite { $arg1 } ms)
music-rhythm-network-warning = La latencia de red es inestable ({ $arg0 } ms). La sesión terminará si sigue por encima de { $arg1 } ms
music-rhythm-no-beats = No se detectaron ritmos jugables en esta canción
music-rhythm-perfect = Perfecto
music-rhythm-preparing = Espera a que la canción se almacene por completo y se analice su ritmo
music-rhythm-ready = Mapa completo listo — empieza desde el primer pulso
music-rhythm-requires-playback = Primero reproduce una canción en este tocadiscos
music-rhythm-reset-calibration = Restablecer calibración de latencia
music-rhythm-results = Resultados
music-rhythm-score = Puntuación: { $arg0 }
music-rhythm-select-track = Seleccionar canción rítmica
music-rhythm-spatial-no-room = No hay suficiente espacio para la puntería espacial. Ve a una zona más abierta.
music-rhythm-spatial-slider-hint = Sigue la estrella móvil y pulsa cada punto · no mantengas pulsado
music-rhythm-start-game = Iniciar juego
music-rhythm-waiting-for-player = Esperando a que un jugador inicie el juego de ritmo
music-sample-rate = Frecuencia de muestreo: { $arg0 }
music-search-button = Buscar canciones
music-search-keyword-required = La palabra clave de búsqueda no puede estar vacía
music-search-keyword-too-long = Búsqueda demasiado larga. Límite: { $arg0 ->
        [one] { $arg0 } carácter
       *[other] { $arg0 } caracteres
    }.
music-search-usage-desc = Buscar canciones
music-section-all = Toda la música
music-section-nbs = Música NBS
music-select-music = Seleccionar música
music-sequential-mode = Repetir lista
music-sequential-mode-desc = Reproduce la lista en orden y vuelve al principio al terminar
music-size = Tamaño: { $arg0 }
music-sort-button = Ordenar
music-sort-current = Actual: { $arg0 }
music-sort-default = Orden predeterminado
music-sort-most-played = Más reproducidas
music-sort-recently-played = Reproducidas recientemente
music-sound-settings = Ajustes de sonido
music-source-actions = acciones: { $arg0 }
music-source-available = disponible
music-source-capabilities = capacidades: { $arg0 }
music-source-error = error: { $arg0 }
music-source-failures = fallos: { $arg0 }
music-source-retry = reintento en { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }
music-source-unavailable = no disponible
music-sources-empty = No se encontraron fuentes de música LX personalizadas
music-sources-title = Fuentes de música LX personalizadas: { $arg0 }
music-stop-eject = Detener y expulsar
music-stop-eject-done = Reproducción detenida y disco expulsado
music-stopped = Detenido
music-track-details = Detalles de la canción
music-unavailable-disc = Disco no disponible
music-unavailable-disc-description = Este disco MIK no está en el catálogo actual; aun así se puede detener y expulsar

## Entertainment · Anniversary

anniversary-accepted = Te has unido al sorteo. La primera ronda se desbloquea tras 4 horas en línea sin AFK.
anniversary-admin-audit-action-bonus = Concedió una oportunidad adicional por construcción
anniversary-admin-audit-action-gift = Regaló un premio del evento: { $arg0 }
anniversary-admin-audit-action-removal = Participación histórica de administrador eliminada; devolución al stock: { $arg0 ->
        [one] { $arg0 } premio
       *[other] { $arg0 } premios
    }.
anniversary-admin-audit-action-unknown = Acción desconocida: { $arg0 }
anniversary-admin-audit-empty = Aún no hay registros de auditoría de gestión.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Autor: { $arg0 } ({ $arg1 }); objetivo: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Auditoría de gestión del aniversario (mostrando { $arg0 }/{ $arg1 })
anniversary-admin-bonus-cancelled = Finalizado por paquete de regalo
anniversary-admin-bonus-pending = Concedido, aún sin sortear
anniversary-admin-bonus-used = Sorteado
anniversary-admin-boss = Gestión del aniversario · Usa { $arg0 } para ver el progreso
anniversary-admin-commands = Comandos: /anniversary winners [página] | /anniversary audit [jugador] | /anniversary bonus <jugador>
anniversary-admin-control = Control dinámico: liberado { $arg0 }; disponible ahora { $arg1 }; probabilidad base { $arg2 }; ritmo reciente { $arg3 }
anniversary-admin-draw-bonus = Bono por construcción
anniversary-admin-draw-detail = { $arg0 }: sorteado a las { $arg1 }; resultado { $arg2 }
anniversary-admin-draw-metrics = Probabilidad { $arg0 }; valor aleatorio { $arg1 }; versión del algoritmo { $arg2 }
anniversary-admin-draw-replaced = Sustituido por el paquete; stock devuelto
anniversary-admin-draw-round = Ronda normal { $arg0 }
anniversary-admin-event = Estado del evento: { $arg0 }; periodo: { $arg1 }
anniversary-admin-excluded = Los administradores del aniversario no pueden participar en el sorteo. Se ha abierto el resumen de gestión.
anniversary-admin-none = Ninguno
anniversary-admin-opportunities = Sorteos realizados: { $arg0 }; disponibles ahora: { $arg1 }; oportunidades obtenidas: { $arg2 }; premios/fallos: { $arg3 }
anniversary-admin-participants = Participantes: { $arg0 }
anniversary-admin-player-bag = Bolsa final del evento: { $arg0 }
anniversary-admin-player-bonus = Bono de construcción: { $arg0 }; concedido: { $arg1 }; concedido por: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Detalles de las oportunidades de sorteo
anniversary-admin-player-not-found = No se encontró al participante del evento: { $arg0 }
anniversary-admin-player-outcome = Premios: { $arg0 }; fallos: { $arg1 }; sorteos realizados: { $arg2 }; bono de construcción: { $arg3 }
anniversary-admin-player-progress = Se unió: { $arg0 }; tiempo válido en línea: { $arg1 }; sorteos normales realizados: { $arg2 }; sorteos normales disponibles: { $arg3 }
anniversary-admin-player-title = Auditoría del participante: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Activo
anniversary-admin-state-ended = Finalizado
anniversary-admin-state-scheduled = Programado
anniversary-admin-stock = Stock restante: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | total { $arg4 }
anniversary-admin-target-excluded = { $arg0 } es administrador del aniversario y no puede recibir oportunidades de sorteo.
anniversary-admin-title = Resumen de gestión del quinto aniversario
anniversary-admin-winners-empty = Aún no hay premios obtenidos en sorteos realizados.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · sorteado a las { $arg3 }
anniversary-admin-winners-title = Lista de entrega de premios (página { $arg0 }/{ $arg1 }, { $arg2 ->
        [one] { $arg2 } premio
       *[other] { $arg2 } premios
    })
anniversary-bag-empty = Aún no hay premios ganados
anniversary-bag-item-locked = La bolsa del evento es de solo lectura
anniversary-bag-item-name = Premio del quinto aniversario · { $arg0 }
anniversary-bag-not-participating = Aún no participas en el evento. Usa primero { $arg0 }.
anniversary-bag-title = Bolsa del evento del quinto aniversario
anniversary-bag-updated = Tu premio está en la bolsa del evento. Usa { $arg0 } para verla.
anniversary-bonus-already-granted = { $arg0 } ya recibió el sorteo adicional por construcciones y no puede recibirlo otra vez.
anniversary-bonus-boss-ready = Sorteo adicional por construcciones listo · Usa { $arg0 } para sortear
anniversary-bonus-broadcast-won = [Sorteo del aniversario] ¡{ $arg0 } ganó { $arg1 } con el premio por construcciones!
anniversary-bonus-draw-not-won = Sorteo adicional por construcciones: esta vez no hay premio.
anniversary-bonus-draw-sold-out = Sorteo adicional por construcciones: no hay premio porque se han agotado todos.
anniversary-bonus-draw-won = Sorteo adicional por construcciones: ¡enhorabuena, has ganado { $arg0 }!
anniversary-bonus-gift-pack-final = { $arg0 } ya ganó el paquete de regalo y no puede recibir un sorteo adicional.
anniversary-bonus-grant-not-active = El sorteo adicional por construcciones no se puede conceder fuera del periodo del evento.
anniversary-bonus-grant-success = Sorteo adicional por construcciones concedido a { $arg0 }.
anniversary-bonus-granted = Has recibido un sorteo adicional por tus construcciones aceptadas. Usa /anniversary para sortear.
anniversary-bonus-participant-not-found = No se encontró ningún participante llamado { $arg0 }
anniversary-bonus-status-ready = Premio por construcciones: hay un sorteo adicional listo.
anniversary-bonus-status-used = Premio por construcciones: el sorteo adicional ya se usó.
anniversary-boss-completed = Sorteo del aniversario · Dos rondas completadas
anniversary-boss-gift-pack-final = Paquete de regalo obtenido · Usa { $arg0 } para ver la bolsa del evento
anniversary-boss-invite = Sorteo del quinto aniversario · Usa { $arg0 } para participar
anniversary-boss-progress = Sorteo del aniversario { $arg0 }/{ $arg1 } · Ronda { $arg2 } en { $arg3 }
anniversary-boss-ready = Ronda { $arg0 } lista · Usa { $arg1 } para sortear
anniversary-broadcast-won = [Sorteo del aniversario] ¡{ $arg0 } ganó { $arg2 } en la ronda { $arg1 }!
anniversary-cancel = Ahora no
anniversary-command-description = Participar en el sorteo del quinto aniversario del servidor
anniversary-confirm = Aceptar y participar
anniversary-confirm-hint = Empieza a contar el tiempo en línea sin AFK
anniversary-delivery-note = Para un destino en China continental, proporciona un ID de SF válido para envío con privacidad. Fuera de China continental, proporciona nombre real del destinatario, dirección completa y teléfono válido. Paga tu parte del envío, o perderás el premio.
anniversary-dialog-prizes = Premios: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } y 5 { $arg3 }
anniversary-dialog-time = Periodo del evento: { $arg0 }
anniversary-dialog-title = Reglas del sorteo del quinto aniversario
anniversary-draw-not-won = Ronda { $arg0 }: esta vez no hay premio.
anniversary-draw-sold-out = Ronda { $arg0 }: no hay premio porque se han agotado todos.
anniversary-draw-won = Ronda { $arg0 }: ¡enhorabuena, has ganado { $arg1 }!
anniversary-ended = El sorteo del quinto aniversario ha terminado
anniversary-ended-results = Tus resultados del sorteo: { $arg0 }
anniversary-gift-pack-final = El paquete de regalo es tu premio final. Sustituyó y devolvió al stock { $arg0 ->
        [one] { $arg0 } otro premio
       *[other] { $arg0 } otros premios
    }; todos los sorteos restantes han terminado.
anniversary-gift-prize-not-owned = No posees este premio del evento: { $arg0 }
anniversary-gift-received = { $arg0 } te regaló { $arg1 }. Ya está en tu bolsa del evento.
anniversary-gift-self = No puedes regalarte un premio a ti mismo.
anniversary-gift-success = Regalaste { $arg0 } a { $arg1 }.
anniversary-gift-usage = Uso: { $arg0 }
anniversary-not-active = El evento del quinto aniversario no está activo ahora
anniversary-not-started = El evento del quinto aniversario aún no ha comenzado. Periodo: { $arg0 }
anniversary-prize-badge = chapa con foto de grupo del quinto aniversario
anniversary-prize-gift-pack = paquete de regalo del quinto aniversario
anniversary-prize-keychain = llavero con el logo
anniversary-prize-mug = taza con el logo
anniversary-prize-not-won = sin premio
anniversary-prize-sold-out = premios agotados
anniversary-rule-1 = 1. Tras participar, puedes sortear cada 4 horas, dos veces. Los premios van a la bolsa virtual del evento, con uno como máximo de cada tipo. El paquete de regalo queda como único premio y finaliza los sorteos restantes.
anniversary-rule-2 = 2. El tiempo AFK no cuenta para desbloquear los sorteos. Está prohibido eludir la detección AFK por cualquier medio.
anniversary-rule-3 = 3. El servidor cubre el 80 % del coste total de envío de los premios físicos, con un máximo de 50 CNY; la persona ganadora paga íntegramente el saldo restante tras descontar esa aportación.
anniversary-rule-4 = 4. Para destinos en China continental, debes proporcionar un ID de SF válido que permita envío con privacidad. Para destinos fuera de China continental, debes proporcionar el nombre real del destinatario, dirección completa y teléfono válido. No proporcionar la información requerida o pagar tu parte del envío implica perder el premio.
anniversary-rule-5 = 5. Se prohíbe participar varias veces con cuentas alternativas; hacerlo implica la descalificación.
anniversary-rule-6 = 6. Al participar aceptas todas las reglas anteriores.
anniversary-rule-7 = 7. Las construcciones aceptadas en la web valen 0,2 por traslados, 0,5 por adaptaciones y 1 por originales; las aceptadas antes del evento se multiplican por 0,8, y las compartidas por 1,5 veces tu porcentaje de contribución. Con al menos 2 puntos se concede un sorteo adicional, entregado manualmente por un administrador.
anniversary-second-round-hint = La segunda ronda se desbloquea tras 8 horas totales en línea sin AFK.
anniversary-status-completed = Has completado las dos rondas.
anniversary-status-gift-pack-final = Has ganado el paquete de regalo. Tu premio final está fijado y todos los sorteos restantes han terminado.
anniversary-status-progress = Completadas: { $arg0 }/{ $arg1 } rondas; tiempo válido en línea: { $arg2 }
anniversary-status-remaining = Faltan { $arg1 } en línea sin AFK para la ronda { $arg0 }.
anniversary-status-results = Resultados del sorteo: { $arg0 }
anniversary-status-title = Progreso del sorteo del aniversario

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } ya está expulsado
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Boletín de sanción]
ban-command-description = Expulsar a un jugador con un nivel de gravedad fijo
ban-database-error = Falló la operación de la base de sanciones. Revisa el registro del servidor.
ban-dialog-back = Atrás
ban-dialog-cancel = Cancelar
ban-dialog-confirm-action = Expulsar jugador
ban-dialog-confirm-severity = Gravedad
ban-dialog-confirm-title = Confirmar expulsión
ban-dialog-never-joined-confirm-action = Expulsar de todos modos
ban-dialog-never-joined-title = El jugador nunca se ha conectado
ban-dialog-never-joined-warning = { $arg0 } nunca se ha conectado a este servidor. Verifica el nombre para evitar expulsar al jugador equivocado.
ban-dialog-next = Siguiente
ban-dialog-player-input = Introducir nombre manualmente
ban-dialog-player-manual = Entrada manual
ban-dialog-player-online = Jugadores conectados
ban-dialog-select-title = Seleccionar jugador
ban-dialog-severity-title = Seleccionar gravedad
ban-history-empty = No hay historial de sanciones de { $arg0 }.
ban-history-title = Historial de sanciones: { $arg0 }
ban-invalid-player = Nombre de jugador no válido
ban-invalid-severity = Gravedad de expulsión no válida
ban-kick-appeal = Si crees que es un error, apela en el grupo de QQ 1138627074
ban-kick-server = MIK
ban-kick-title = Has sido expulsado de este servidor
ban-not-banned = { $arg0 } no tiene ninguna sanción activa.
ban-pardon-reason-required = Debes indicar un motivo para retirar la expulsión
ban-permanent = Permanente
ban-reason-required = Debes indicar un motivo para la expulsión
ban-self = No puedes expulsarte a ti mismo
ban-severity-excusable = Justificable (30 días)
ban-severity-extreme = Infracción extrema (1500 días)
ban-severity-minor = Infracción leve (150 días)
ban-severity-severe = Infracción grave (360 días)
ban-severity-warning = Advertencia (3 días)
ban-status-active = Activa
ban-status-expired = Caducada
ban-status-revoked = Revocada
ban-success = Se expulsó a { $arg0 } hasta { $arg1 }
ban-sync-success = Sanciones sincronizadas: activas { $arg0 } · registros históricos { $arg1 }
ban-system-source = Sistema
ban-unban-success = Sanción de { $arg0 } revocada
ban-unknown-reason = Sin motivo
ban-update-success = Campo de sanción de { $arg0 } actualizado: { $arg1 }
banlist-command-description = Ver expulsiones de jugadores
banlist-copy = Haz clic para copiar el nombre
banlist-empty = No hay jugadores expulsados
banlist-expires = Caduca
banlist-invalid-page = Página inválida. Total: { $arg0 ->
        [one] { $arg0 } página
       *[other] { $arg0 } páginas
    }.
banlist-next = Siguiente
banlist-page = Página { $arg0 }/{ $arg1 } · { $arg2 ->
        [one] { $arg2 } registro
       *[other] { $arg2 } registros
    }
banlist-player = Jugador
banlist-previous = Anterior
banlist-reason = Motivo
banlist-source = { role-moderator }
banlist-title = Lista de expulsados
maintenance-command-description = Activar, desactivar o consultar el modo de mantenimiento
maintenance-set = Modo de mantenimiento { $arg0 }
maintenance-state-disabled = desactivado
maintenance-state-enabled = activado
maintenance-status = Modo de mantenimiento: { $arg0 }
maintenance-unchanged = El modo de mantenimiento ya está { $arg0 }
restriction-entity-too-large-mm = <red>El tamaño de la entidad supera el límite de seguridad del servidor</red>
restriction-foreign-uuid-mm = <red>Solo puedes usar UUID para mobs que hayas domesticado</red>
restriction-no-kill-e-mm = <red>El comando <white>/kill @e</white> no está permitido</red>
restriction-no-newline-mm = <red>Los mensajes no pueden contener saltos de línea</red>
restriction-no-run-command-mm = <red>Los mensajes no pueden engañar a los jugadores para que ejecuten comandos.</red>
restriction-no-selector-mm = <red>No tienes permiso para utilizar selectores de objetivos <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>No tienes permiso para mencionar los nombres de otros jugadores en los comandos.</red>
tpsbar-command-description = Mostrar u ocultar la barra TPS
tpsbar-hidden = Barra TPS oculta
tpsbar-shown = Se muestra la barra TPS
unban-command-description = Indultar a un jugador y conservar su historial
whitelist-already-whitelisted = { $arg0 } ya está en la lista blanca
whitelist-invalid-name = Formato de nombre de jugador no válido
whitelist-kick-apply-rich = Solicita acceso a la lista blanca en { $site }
whitelist-kick-title = No estás en la lista blanca
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = { $arg0 } añadido temporalmente a la lista blanca durante { $arg1 }
whitelist-temp-added-rich = { $player } añadido temporalmente a la lista blanca durante { $duration }
whitelist-temp-command-description = Agregar una entrada de lista blanca temporal de 1 hora
whitelist-temp-duration = 1 hora
whitelist-temp-usage-desc = Agregar una entrada de lista blanca temporal de 1 hora

## Shell · Menus, server information and scripting

main-afk-current-afk = Actualmente AFK
main-afk-current-online = Actualmente en línea
main-afk-status = Estado AFK
main-announcements = Anuncios
main-home = Hogares
main-language = Idioma
main-language-auto = Automático
main-links = Enlaces
main-map = Mapa web
main-menu-title = Menú principal
main-music = Música
main-name-tag = Etiquetas de nombre
main-profile = Perfil
main-pvp = JcJ
main-settings = Ajustes
main-website = Sitio web
main-wiki = Wiki
skript-command-description = Ver la compatibilidad con scripts del servidor
skript-info-ai-option = ¿No quieres escribirlo? Entrega estas instrucciones a una IA:
skript-info-copy = [Copiar instrucciones para IA]
skript-info-copy-hover = Copiar instrucciones con versiones, documentación y reglas de seguridad
skript-info-documentation = Documentación de sintaxis y API
skript-info-review = Los scripts solo se cargan tras la revisión y las pruebas de un administrador
skript-info-supported = Disponible: { $arg0 }
skript-info-title = Scripts del servidor
skript-info-workflow = Escribe y revisa un archivo .sk completo con la documentación y envíalo a un administrador
stat-client-version = Versión del cliente
stat-first-joined = Primera conexión
stat-location = Ubicación
stat-ping = Latencia
stat-play-time = Tiempo de juego
stat-role = Rol
stat-world = Mundo
weblogin-command-description = Confirmar un código de inicio de sesión de Mik Web
weblogin-confirmed = Inicio de sesión de Mik Web confirmado. Vuelve al navegador para continuar.
weblogin-invalid-code = Código de inicio de sesión web no válido
