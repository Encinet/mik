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
cancel = Cancelar
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

interface-fov-apply = Aplicar FOV
interface-fov-custom = Personalizado
interface-fov-description = Introduce el FOV del juego para colocar el menú.
interface-fov-input = FOV del cliente: entero de 30 a 110
interface-fov-invalid = Introduce un número entero de 30 a 110.
interface-fov-title = Campo de visión
interface-reduced-motion = Reducir movimiento
interface-reduced-motion-hint = Reduce el movimiento y la oscilación del menú.
interface-scale-adjust-hint = Usa la rueda sobre un porcentaje para ajustarlo.
interface-scale-layout-title = Tamaño del menú
interface-scale-menu-title = Interfaz y texto
interface-scale-preview = Ejemplo Aa 123
interface-scale-reset = Restablecer al 100%
interface-scale-text-title = Tamaño del texto
interface-theme-apply = Guardar colores
interface-theme-charcoal = Carbón
interface-theme-color = Código de color (#RRGGBB)
interface-theme-custom = Personalizado
interface-theme-custom-hint = Copia todos los valores actuales a un tema personalizado independiente.
interface-theme-edit = Editar colores
interface-theme-forest = Bosque
interface-theme-hint = Se aplica al instante solo a tus menús.
interface-theme-invalid = Introduce un color #RRGGBB y una opacidad de 0 a 100.
interface-theme-opacity = Opacidad (0–100 %)
interface-theme-role-accent = Color de énfasis
interface-theme-role-button = Fondo del botón
interface-theme-role-button-disabled = Botón desactivado
interface-theme-role-button-hovered = Botón señalado
interface-theme-role-button-pressed = Botón pulsado
interface-theme-role-button-selected = Botón seleccionado
interface-theme-role-input = Fondo de entrada
interface-theme-role-sidebar = Fondo del panel lateral
interface-theme-role-toolbar = Fondo de herramientas
interface-theme-role-tooltip = Fondo de ayuda
interface-theme-role-window = Fondo de ventana
interface-theme-title = Tema del menú
interface-theme-warm = Cálido
menu-collapse = Contraer
menu-expand = Expandir
menu-finish-editing = Termina la edición primero.
menu-input-clear = Vaciar
menu-input-done = Listo
menu-input-empty = (vacío)
menu-input-single-line = Este campo solo admite una línea.
menu-input-too-long = Longitud: { $arg0 } / { $arg1 }. Acorta el texto antes de confirmar.
menu-query-cancel = Cancelar
menu-query-hint-rich = Pulsa { $chatKey } para escribir…
menu-query-single-line = Solo se admite una línea. Pulsa el campo para reintentar.
menu-query-too-long = Longitud: { $arg0 } / { $arg1 }. Pulsa el campo para reintentar.
menu-rhythm-game-display = Jugando al juego de ritmo
menu-search-empty = Sin coincidencias
menu-search-failed = Error de búsqueda
menu-search-limit = Mostrando { $arg0 } de { $arg1 }. Concreta la búsqueda.
menu-search-loading = Buscando…
menu-search-retry = Reintentar
menu-usage-display-mm = <aqua><bold>En el menú</bold></aqua>
url-dialog-confirm = Abrir
url-dialog-hint = El cliente pedirá otra confirmación para abrir el enlace.
url-dialog-question-rich = ¿Abrir { $label }?
url-dialog-title = Abrir { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Mods recomendados: { $mods }. Haz clic en un nombre para descargar.
client-tip-axiom-flight-speed-mm = <white>Mantén pulsada <yellow>Alt izquierda</yellow> para abrir el menú de <aqua>Axiom</aqua> y arrastra el control vertical de la derecha para ajustar la velocidad de vuelo. También puedes usar <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click>.</white>
client-tip-axiom-intro-rich = { $mod } te permite copiar y pegar construcciones, modificar el terreno y editar entidades de visualización. Haz clic en su nombre para descargarlo.
client-version-behind-mm = <white>La versión de tu cliente <yellow>{ $clientVersion }</yellow> es anterior a la del servidor <green>{ $serverVersion }</green>. Te recomendamos actualizar el cliente.</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>Hay { $count ->
        [one] <gold>{ $count }</gold> jugador <gold>invisible</gold> cerca
       *[other] <gold>{ $count }</gold> jugadores <gold>invisibles</gold> cerca
    }</yellow> <gray>·</gray> <gray>el más cercano está a aproximadamente { $distance ->
        [one] { $distance } bloque
       *[other] { $distance } bloques
    }</gray>
player-afk-label = Ausente
player-bedrock-label = Bedrock
player-monitor-label = Supervisión
player-web-label = Web
presence-join-templates =
    .item-1 = ¡Din, don! ¡{ $player } está en línea!
    .item-2 = ¡Qué bien, es { $player }!
    .item-3 = ¡Hola, hola, { $player }!
# Broadcast to remaining players; the departing player does not receive this message.
presence-quit-templates =
    .item-1 = { $player } se ha desconectado.
    .item-2 = ¡Din, don! { $player } está fuera de línea
    .item-3 = En línea -1. { $player } se ha desconectado
welcome-first-join-mm = <gold><bold>¡Te damos la bienvenida a MIK!</bold></gold><newline><gray>Empieza por el <click:open_url:'https://mcmik.top'><yellow>sitio web</yellow></click> y la <click:open_url:'https://mcmik.top/wiki'><yellow>wiki</yellow></click>; también puedes usar <yellow>/menu</yellow> para abrir el menú principal.</gray>
welcome-return-mm = <gold><bold>¡Qué alegría verte de nuevo!</bold></gold><newline><gray>Tu última visita fue hace <yellow>{ $arg0 ->
        [one] { $arg0 } día
       *[other] { $arg0 } días
    }</yellow>. Usa <yellow>/menu</yellow> para acceder rápidamente a las funciones habituales.</gray>

## Player · Guidance

tip-label = CONSEJO

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


## Player · Account security
password-already-connected = Esta cuenta ya está conectada.
password-already-set = Ya tienes una contraseña. Usa /password change para cambiarla.
password-busy = Demasiados intentos. Espera y vuelve a intentarlo.
password-change = Cambiar contraseña
password-change-done = Contraseña cambiada.
password-checking = Comprobando la seguridad de la cuenta…
password-command-description = Gestionar la contraseña de la cuenta
password-confirm = Confirmar nueva contraseña
password-conflict = La seguridad de la cuenta ha cambiado. Repite la operación.
password-cooldown = La verificación de la cuenta está restringida temporalmente. Vuelve a intentarlo en { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }.
password-current = Contraseña actual
password-denied = No puedes restablecer la contraseña de esta cuenta.
password-disabled = Contraseña: sin configurar
password-enabled = Contraseña: configurada
password-invalid = Contraseña incorrecta. Inténtalo de nuevo.
password-keep-role = Los moderadores y custodios no pueden quitar su contraseña.
password-login = Iniciar sesión
password-login-expired = Tu verificación de inicio de sesión ha caducado. Vuelve a conectarte.
password-login-hint = Introduce la contraseña de tu cuenta en este servidor. Si la has olvidado, pide al equipo que la restablezca.
password-mismatch = Las nuevas contraseñas no coinciden.
password-new = Nueva contraseña
password-nomination-required = Configura una contraseña antes de solicitar ser moderador: /password set
password-not-set = No tienes contraseña. Usa /password set para establecer una.
password-remove = Eliminar contraseña
password-remove-done = Contraseña eliminada.
password-remove-question = Al quitar la contraseña, ya no se comprobará al iniciar sesión. Introduce tu contraseña actual para confirmar.
password-required-application = No puedes quitar la contraseña mientras haya una solicitud al equipo o un nombramiento pendiente.
password-required-role = Las cuentas del equipo requieren contraseña. Pide a una persona autorizada que la restablezca antes de entrar.
password-reset = Restablecer contraseña
password-reset-console = Contraseña de { $arg0 } restablecida: { $arg1 }. Entrégala por un canal de confianza tras verificar la identidad.
password-reset-done = Contraseña de { $arg0 } restablecida. Cópiala y entrégala tras verificar la identidad; no se podrá volver a consultar.
password-reset-question = ¿Restablecer la contraseña de { $arg0 }? La anterior dejará de funcionar y se cerrarán sus conexiones. Confirma con tu propia contraseña.
password-retry = Volver a intentar
password-rules = Usa entre { $arg0 } y { $arg1 } caracteres. No se permiten nombres de jugador ni contraseñas comunes.
password-set = Configurar contraseña
password-set-done = Contraseña establecida.
password-set-first = Configurar contraseña antes de solicitar
password-storage = La seguridad de cuentas no está disponible. Inténtalo más tarde o contacta con el propietario.
password-timeout = La verificación se canceló o caducó. Vuelve a conectarte para intentarlo.
password-tip-set-mm = Usa <gold><u>/password</u></gold> para establecer una <yellow>contraseña de inicio de sesión</yellow> y proteger tu cuenta.
password-title = Seguridad de la cuenta

## Player · Profile and tab list

nametag-clear = Vaciar
nametag-clear-hint = Elimina valores personalizados; los estilos heredados pueden permanecer.
nametag-command-description = Personalizar etiquetas de nombre
nametag-confirm = Confirmar
nametag-conflict = El valor actual ha cambiado. Compara el efecto actual y confirma de nuevo para sustituirlo.
nametag-copy-hint = Enviar el código del prefijo y sufijo al chat
nametag-copy-label = [copiar]
nametag-copy-sent = El código del estilo de { $arg0 } está en el chat.
nametag-current = Efecto actual
nametag-edit-ended = La edición ha terminado. Haz clic de nuevo en el campo para continuar.
nametag-edit-prompt = Pulsa T para editar { $arg0 } con MiniMessage (privado).
nametag-finish-field = Primero confirma o cancela el campo que estás editando.
nametag-input-invalid = Formato no válido. Envía MiniMessage en una sola línea o selecciona Vaciar.
nametag-leading-label = Prefijo de nombre
nametag-mine = Mi etiqueta
nametag-next = Siguiente
nametag-no-changes = Sin cambios
nametag-no-permission = Los miembros y el equipo pueden editar etiquetas. Puedes seguir viendo estilos.
nametag-no-players = Ningún jugador ha configurado un estilo de nombre
nametag-page = Página { $arg0 } / { $arg1 }
nametag-players = Estilos de jugadores
nametag-preview-label = Vista previa
nametag-previous = Anterior
nametag-refresh = Actualizar
nametag-saving = Guardando…
nametag-set-error = No se pudo guardar: { $arg0 }. Reinténtalo; el efecto actual se muestra arriba.
nametag-set-success = ✔ { $arg0 } establecido
nametag-styles-failed = No se pudieron cargar los estilos. Actualiza para reintentar.
nametag-styles-loading = Cargando estilos de jugadores…
nametag-tip-customize-mm = Usa <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> para personalizar el <light_purple>prefijo y sufijo de tu nombre</light_purple>.
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
back-tip-return-mm = Usa <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> para volver a tu ubicación anterior al último teletransporte o muerte.
back-tip-undo-mm = Usa <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> para deshacer tu último <yellow>/back</yellow>.
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
fly-speed-limited = La carga de exploración limita temporalmente el vuelo a { $arg0 }x; tu ajuste sigue en { $arg1 }x.
fly-speed-presets = Preajustes
fly-speed-reset = Velocidad de vuelo restablecida a 1x
fly-speed-reset-action = Restablecer
fly-speed-reset-click = Restablecer velocidad de vuelo a 1x
fly-speed-set = Velocidad de vuelo ajustada a { $arg0 }x
fly-state-disabled = Desactivado
fly-state-enabled = Activado
fly-status = Vuelo: { $arg0 } | Velocidad: { $arg1 }x
home-action-create = Crear hogar
home-action-delete = Eliminar hogar
home-action-teleport = Teletransportarse
home-action-update = Actualizar ubicación
home-already-exists = Ya existe un hogar con este nombre
home-confirm-delete = Confirmar eliminación
home-confirm-delete-detail-rich = Eliminar hogar { $home }
home-confirm-delete-warning = Esto no se puede deshacer
home-confirm-update = Confirmar actualización
home-confirm-update-detail-rich = Actualizar hogar { $home } a esta ubicación
home-delete-command-description = Elimina un hogar
home-deleted-rich = Hogar { $home } eliminado
home-distance = Distancia: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } bloque
       *[other] { $arg0 } bloques
    }
home-distance-other-world = otro mundo
home-empty-description = Colócate donde quieras guardar un hogar
home-empty-title = Aún no hay hogares
home-invalid-name = Los nombres de los hogares no pueden contener los caracteres < > :
home-list-rich = Hogares: { $homes }
home-location = Ubicación: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = Los datos de ubicación de este hogar no son válidos
home-max-reached = Puedes establecer un máximo de { $arg0 ->
        [one] { $arg0 } hogar
       *[other] { $arg0 } hogares
    }
home-menu-title = Hogares
home-no-homes = Aún no has establecido ningún hogar
home-not-found-rich = El hogar { $home } no existe
home-search-placeholder = Buscar nombres de casas…
home-search-empty = No hay hogares coincidentes
home-set-command-description = Establece un hogar en tu ubicación actual
home-set-rich = Hogar { $home } establecido
home-summary-count = Establecidos: { $arg0 } / { $arg1 }
home-teleport-failed = No se ha podido teletransportar. Inténtalo de nuevo
home-teleported-rich = Te has teletransportado al hogar { $home }
home-tip-create-mm = Guarda tu ubicación con <green><u><click:suggest_command:'/sethome '>/sethome nombre</click></u></green> y vuelve con <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>.
home-tip-return-mm = Abre <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>, selecciona un hogar y pulsa «{ home-action-teleport }».
home-updated-rich = Ubicación del hogar { $home } actualizada
home-usage-book = Ayuda de comando
home-usage-delhome = /delhome <name>  Eliminar un hogar
home-usage-home = /home <name>  Teletransportarse directamente
home-usage-name-rule = Los nombres de los hogares no pueden contener < > :
home-usage-sethome = /sethome <name>  Guarda tu ubicación actual
home-world = Mundo: { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Deshacer el último /back
teleport-accept-command-description = Aceptar la solicitud de teletransporte pendiente
teleport-allow = Teletransportes de otros jugadores
teleport-bare-entity-selector = Para evitar teletransportes accidentales, @e requiere corchetes. Usa un selector explícito como @e[type=…].
teleport-block-afk = Bloquear mientras AFK
teleport-denied = { $player } no permite que otros jugadores le teletransporten
teleport-deny-command-description = Rechazar la solicitud de teletransporte pendiente
teleport-force-command-description = Forzar teletransporte ignorando las preferencias del jugador (solo administradores)
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
tpany-command-description = Teletransportarse a la ubicación de un jugador, en línea o sin conexión
tpany-done-offline-rich = Te has teletransportado a la última ubicación conocida de { $player } { $world } { $location }
tpany-done-online-rich = Te has teletransportado a la ubicación actual de { $player }
tpany-not-found-rich = No se ha podido encontrar la ubicación de { $player }
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teletranspórtate a la ubicación de un jugador, incluida su ubicación guardada si está desconectado

## Player · PVP

pvp-admin-menu-title = Administración de JcJ
pvp-allow-mounted-damage = Las criaturas montadas pueden ser atacadas
pvp-allow-mounted-damage-desc = Si se activa, otro jugador puede atacar a la criatura que montas mientras ambos tenéis el JcJ activado
pvp-auto-enable-desc = Ataca dos veces seguidas a un jugador con JcJ activado
pvp-auto-enable-label = Activación automática
pvp-auto-enable-warning-actionbar-mm = <yellow>Ataca una vez más para activar el JcJ automáticamente.</yellow>
pvp-auto-enabled-actionbar-mm = <green>JcJ activado automáticamente</green>
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
pvp-no-permission-mm = <red>No tienes permiso para gestionar el estado JcJ de otro jugador.</red>
pvp-player-not-found-mm = <red>Jugador en línea no encontrado</red>
pvp-protect-mobs = Protege a tus criaturas
pvp-protect-mobs-desc = Protege a las criaturas que has domesticado o que estás montando
pvp-quick-combat-cooldown = Bloqueo de combate: { $arg0 ->
        [one] queda { $arg0 } segundo
       *[other] quedan { $arg0 } segundos
    }
pvp-quick-override = Una regla temporal controla el JcJ. Puedes cambiar tus preferencias en la configuración.
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
pvp-target-disabled-actionbar-mm = <yellow>Ese jugador no tiene el JcJ activado.</yellow>
pvp-target-menu-title = Configuración JcJ - { $arg0 }
pvp-tip-protection-mm = Activa o desactiva tu PVP con <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple>. <green>Ambos jugadores deben activarlo</green> para combatir.
pvp-toggled-off-mm = <gray>JcJ desactivado</gray> <dark_gray>|</dark_gray> <gray>Los demás jugadores no pueden atacarte.</gray>
pvp-toggled-on-mm = <green>JcJ activado</green> <dark_gray>|</dark_gray> <gray>Ya puedes luchar contra otros jugadores con JcJ activado.</gray>

## Player · Effects

effect-cleanup-all = [Eliminar todos los efectos]
effect-cleanup-blocked = Solo puedes eliminar efectos en modo Creativo o Espectador y fuera de un combate PVP.
effect-cleanup-expired = Esta solicitud de eliminación ha caducado.
effect-cleanup-negative = [Eliminar efectos negativos]
effect-cleanup-prompt-rich = Has recibido efectos negativos por { $source }. ¿Quieres eliminarlos?
effect-cleanup-result = { $count ->
        [one] Se ha eliminado { $count } efecto.
       *[other] Se han eliminado { $count } efectos.
    }
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] una nube de poción persistente
        [ARROW] una flecha
        [ATTACK] un ataque
        [COMMAND] un comando
        [FOOD] comida
        [PLUGIN] un complemento
        [POTION_DRINK] beber una poción
        [POTION_SPLASH] una poción arrojadiza
        [WARDEN] un warden
        [WITHER_ROSE] una rosa del Wither
       *[OTHER] una causa desconocida
    }

## Player · Trash

trash-clear = Vaciar
trash-clear-hint = Haz clic para eliminar todos los objetos ahora.
    También se eliminan automáticamente { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    } después de cerrar.
trash-command-description = Abrir la papelera
trash-sort = Ordenar
trash-sort-hint = Combinar pilas iguales y ordenar por tipo de objeto
trash-tip-dispose-mm = Usa <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> para abrir la <yellow>papelera</yellow> y poner dentro los objetos que no quieras.
trash-title = Papelera

## Player · Utilities

hat-command-description = Ponte el objeto de tu mano principal
hat-empty-hand-mm = <red>No tienes ningún objeto en la mano principal.</red> <gray>Sujeta un bloque u objeto y usa /hat</gray>
hat-success-mm = <green>Te has puesto el objeto de la mano principal</green> <dark_gray>|</dark_gray> <gray>Se han intercambiado el objeto de tu mano y el de la ranura del casco.</gray>
hat-tip-wear-mm = Usa <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> para ponerte en la cabeza el objeto de tu <yellow>mano principal</yellow>.
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
removeitems-tip-usage-mm = Usa <aqua><u><click:suggest_command:'/rmitems '>/rmitems [radio]</click></u></aqua> para eliminar los <yellow>objetos tirados</yellow> cercanos.
self-kick-command-description = Expulsarte del servidor
self-kick-reason-too-long = El motivo de expulsión no puede superar { $arg0 ->
        [one] { $arg0 } carácter
       *[other] { $arg0 } caracteres
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Has salido del servidor por tu cuenta. Motivo: { $reason }
       *[third] { $player } ha salido del servidor por su cuenta. Motivo: { $reason }
    }
    .item-1 = { $player } ha salido del servidor por su cuenta. Motivo: { $reason }
    .item-2 = { $player } se ha desconectado con un mensaje: { $reason }
    .item-3 = { $player } ha salido del servidor con un mensaje: { $reason }
    .item-4 = { $player } se ha despedido con una nota: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Has salido del servidor por tu cuenta.
       *[third] { $player } ha salido del servidor por su cuenta.
    }
    .item-1 = { $player } ha salido del servidor por su cuenta.
    .item-2 = { $player } se ha desconectado.
    .item-3 = ¡Din, don! { $player } está fuera de línea
    .item-4 = En línea -1. { $player } se ha desconectado

## Community · Chat and mentions

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
chat-private-target-offline = Tu interlocutor está desconectado; espera o elige otro canal
chat-private-tip-channel-mm = Usa <aqua><u><click:suggest_command:'/msg '>/msg jugador</click></u></aqua> para entrar al chat privado y <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> para volver al chat público.
chat-public-command-description = Cambiar canal de chat público
chat-public-enter = Cambiado al chat público
chat-reply-command-description = Responde a tu último objetivo de chat privado
chat-settings-menu-title = Configuración de chat
chat-social-account-label = Cuenta de la plataforma
chat-social-bound-player-label = Jugador vinculado
chat-social-name-label = Nombre social
chat-social-unbound = No está vinculado a ningún jugador de Minecraft
chat-staff-command-description = Cambiar canal de chat del personal
chat-staff-label = Equipo
chat-staff-permission-missing = No tienes permiso para usar el chat del equipo; has vuelto al chat público
mention-action-bar = Aviso en la barra de acción
mention-action-bar-text = { $sender } te ha mencionado
mention-alerts = Alertas de mención
mention-menu-title = Alertas de mención
mention-mute-afk = Silenciar mientras AFK
mention-sound = Sonido de alerta
staffchat-enter = Te has unido al chat del equipo

## Community · Announcements

announcement-count = { $arg0 ->
        [one] { $arg0 } anuncio
       *[other] { $arg0 } anuncios
    }
announcement-empty = No hay anuncios
announcement-first-page = Avisos más recientes
announcement-last-page = Avisos más antiguos
announcement-latest = Último anuncio
announcement-list-newer = Grupo más reciente
announcement-list-older = Grupo anterior
announcement-more-count = { $arg0 } más sin mostrar
announcement-new-count = { $arg0 } nuevos
announcement-newer = Avisos más recientes
announcement-next-page = Página siguiente
announcement-older = Avisos anteriores
announcement-page = Página { $arg0 } / { $arg1 }
announcement-previous-page = Página anterior
announcement-reload = Recargar anuncios
announcement-reload-command-description = Recargar archivo de anuncios
announcement-reload-failed = No se pudieron recargar los anuncios; se conservan los anteriores
announcement-reloaded = Anuncios recargados
announcement-unread-count = { $arg0 } sin leer
announcement-updated = Anuncios actualizados
announcement-view-all = Ver todos los anuncios
announcement-view-command-description = Ver anuncios del servidor

## Community · Identity and social platforms

identity-admission-bind-rich = { $platform }: { $command }
identity-admission-instructions = Elige una plataforma y envía el comando correspondiente:
identity-admission-pending = Tu vínculo actual aún no se puede verificar. Inténtalo más tarde o vincula una cuenta de otra plataforma.
identity-admission-reconnect = Vuelve a conectarte al servidor tras vincular la cuenta.
identity-admission-required = Vincula una cuenta de la comunidad para entrar.
identity-admission-revoked = Ya no tienes ningún vínculo válido con la comunidad. Vuelve a conectarte para obtener instrucciones.
identity-admin-binding-not-found = El vínculo seleccionado ya no existe.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Se eliminó el vínculo seleccionado de { $arg0 } de { $arg1 }.
identity-admin-command-description = Consultar o revocar vínculos de identidad externos
identity-admin-help = /bindadmin lookup <jugador-o-UUID>
    /bindadmin unlink <binding-id> confirm
identity-admin-lookup-empty = No se encontraron vínculos para ese jugador.
identity-admin-lookup-title = Vínculos de { $arg0 }:
identity-binding-community = Comunidad #{ $arg0 }
identity-binding-info = Vinculado el: { $arg0 }
    ID del vínculo: { $arg1 }
identity-binding-label-rich = { $kind ->
    [community] { $platform } · { $account } · { $community }
   *[account] { $platform } · { $account }
    }
identity-binding-not-owned = El vínculo seleccionado ya no existe o no te pertenece.
identity-binding-reference-rich = { $label } · #{ $reference }
identity-binding-unlinked = Se eliminó el vínculo seleccionado de { $arg0 }.
identity-binding-unnamed = Cuenta sin nombre
identity-bindings-empty = No has vinculado ninguna cuenta de una plataforma externa.
identity-bindings-title = Cuentas vinculadas
identity-code-cancelled = Se canceló el código de vínculo de { $arg0 } sin usar.
identity-code-copy = Haz clic para copiar el comando de vinculación completo
identity-code-expiry = Validez del código: { $arg0 ->
    [one] { $arg0 } minuto
   *[other] { $arg0 } minutos
    }
identity-code-not-pending = No hay ningún código de vínculo de { $arg0 } sin usar.
identity-code-redeem-rich = Envía al bot de tu comunidad de { $platform }: { $command }
identity-command-description = Vincular Minecraft con una identidad de una plataforma externa
identity-community-join-rich = Únete a una comunidad: { $link }
identity-help = Comandos de vinculación de identidad:
    /bind qq — generar rápidamente un comando de vinculación de QQ
    /bind <plataforma> — generar un código de un solo uso
    /bind list — mostrar los vínculos existentes
    /bind cancel <plataforma> — cancelar un código sin usar
    /bind unlink — Elegir un vínculo para eliminar
identity-notify-linked = Tu identidad de { $arg0 } está vinculada. Usa /bind list para ver y gestionar tus vínculos.
identity-notify-unlinked = Se eliminó tu vínculo de identidad de { $arg0 }.
identity-platform-none = ninguna
identity-platforms = Plataformas compatibles: { $arg0 }
identity-storage-error = La operación de la base de datos de vínculos ha fallado. Contacta con un administrador.
identity-unavailable = La vinculación de identidades no está disponible. Contacta con un administrador.
identity-unlink-action = [Desvincular]
identity-unlink-hint = Eliminar solo este vínculo seleccionado.
identity-unsupported-platform = Plataforma no compatible: { $arg0 }
social-admin-conversations-empty = No se observaron conversaciones para { $arg0 }.
social-admin-conversations-title = Conversaciones observadas para { $arg0 }:
social-admin-help = /social platforms · /social status [plataforma] · /social reload [plataforma] · /social conversations <plataforma>
social-admin-metrics = Entrantes { $arg0 } / reintentos { $arg1 } · salientes { $arg2 } / contrapresión { $arg3 } · fallos { $arg4 }
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Se recargó la plataforma social { $arg0 }.
social-admin-reloaded-all = Se recargaron todas las plataformas sociales.
social-admin-status = { $arg0 } · { $arg1 } · generación { $arg2 } · conversaciones { $arg3 }
social-admin-title = Plataformas sociales
social-admin-unknown = Plataforma social desconocida: { $arg0 }
social-binding-already-linked = Ya tienes vinculada la cuenta { $arg0 }. Envía { $arg1 } para ver tu perfil.
social-binding-confirmation-expired = La confirmación ha caducado o la cuenta vinculada ha cambiado. Solicita una nueva confirmación:
social-binding-code-invalid = Este código no es válido o ha caducado. Vuelve a conectarte o ejecuta { $arg0 } dentro del juego.
social-binding-external-in-use = Esta identidad ya está vinculada a otro jugador de Minecraft.
social-binding-help = Conéctate al servidor de Minecraft y envía aquí el comando de vinculación completo de la pantalla de acceso. Si ya estás jugando, obténlo con { $arg0 }.
social-binding-identity-missing = Este mensaje no contiene una identidad de miembro fiable, por lo que no se puede vincular.
social-binding-link-completed = La cuenta se ha vinculado. Vuelve a conectarte para continuar el acceso.
social-binding-linked = Tu cuenta de juego { $arg0 } está vinculada. Vuelve a conectarte para continuar el acceso.
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
social-binding-unlinked = Se desvinculó del jugador de Minecraft { $arg0 }.
social-command-description = Gestionar adaptadores de plataformas sociales
social-command-invalid-arguments = Los argumentos no son válidos. Usa:
social-command-usage = Este comando no necesita argumentos. Envía:
social-crypto-command-description = Administrar la verificación de dispositivos cifrados de Matrix
social-crypto-comparison-not-ready = La comparación SAS aún no está lista.
social-crypto-device = { $arg0 } / { $arg1 }
    Ed25519: { $arg2 }
social-crypto-failed = Falló la verificación del dispositivo. Revisa el registro del servidor.
social-crypto-no-active = No hay ninguna verificación de dispositivo en curso.
social-crypto-not-accepting = Esta verificación no está esperando aceptación.
social-crypto-not-running = Matrix no está en ejecución.
social-crypto-requested = Verificación solicitada: { $arg0 }
social-crypto-state = { $arg0 ->
    [initializing] Inicializando
    [verified] Verificado
    [unverified] Sin verificar
    [no_cross_signing] Firma cruzada desactivada
    [requested] Solicitado
    [ready] Listo
    [started] Iniciado
    [comparison] Esperando comparación
    [waiting] Esperando finalización
    [done] Completado
    [cancelled] Cancelado
   *[unknown] Desconocido
    [valid] Clave válida
    [cross_signed] Con firma cruzada
    [not_cross_signed] Sin firma cruzada
    [blocked] Bloqueado
    [invalid] Clave no válida
    }
social-crypto-status = Verificación: { $arg0 }
    Versión de la copia de claves: { $arg1 }
social-crypto-target = { $arg0 } / { $arg1 }: { $arg2 } · { $arg3 }
social-crypto-transaction = { $arg0 }: { $arg1 } / { $arg2 } · { $arg3 }
social-crypto-transaction-required = Indica el ID completo de la transacción de verificación actual.
social-crypto-unavailable = No disponible
social-crypto-updated = Verificación actualizada; consúltala con matrixcrypto status.
social-crypto-usage = matrixcrypto status [id-usuario id-dispositivo]
    matrixcrypto verify <id-usuario> <id-dispositivo>
    matrixcrypto accept|confirm|cancel <id-transacción>
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
social-profile-avatar-alt = Rostro de Minecraft
social-profile-command-description = Ver tu perfil; los miembros plenos pueden buscar por nombre, mención o respuesta
social-profile-command-parameters = [jugador]
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
social-query-metric = { $arg0 }
social-query-no-players = No hay jugadores en línea.
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (Ausente)
   *[active] { $arg0 }
    }
social-query-online-summary = { $arg0 ->
    [one] Ahora hay { $arg0 } jugador conectado.
   *[other] Ahora hay { $arg0 } jugadores conectados.
    }{ $arg1 ->
    [0] { "" }
   *[other] { " " }En AFK: { $arg1 }.
    }
social-query-players-label = Jugadores
social-query-population = En línea { $arg0 }/{ $arg1 } · Activos { $arg2 } · Ausente { $arg3 }
social-query-unknown = Comando desconocido. Envía { $arg0 } para ver los comandos disponibles.
social-query-uptime-label = Tiempo activo
social-query-version-label = Versión
social-reply-hidden = La política de seguridad de contenido del servidor ocultó esta respuesta.
social-reply-unavailable = Ahora no se puede mostrar el resultado. Comprueba los cambios de cuenta con el comando de perfil antes de volver a solicitarlos.
social-state-disabled = desactivada
social-state-failed = fallida
social-state-ready = lista
social-state-starting = iniciando
social-state-stopped = detenida
social-status-command-description = Ver jugadores, TPS, MSPT y tiempo activo
social-title-command-help = Ayuda de comandos
social-title-content-hidden = Contenido oculto
social-title-player-profile = Perfil del jugador
social-title-server-status = Estado del servidor
social-unlink-command-description = Eliminar este vínculo de identidad tras confirmarlo
social-unlink-command-parameters = [{ social-unlink-confirmation } <confirmación>]
social-unlink-confirmation = confirmar

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
ai-social-command-parameters = <pregunta>
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
governance-active-playtime = Al menos { $arg1 } de juego efectivo en 60 días (actualmente { $arg0 })
governance-activity-action = { $arg0 ->
    [confirm] Confirmar apoyo
    [vote] Leer y votar
    *[details] Ver detalles
    }
governance-activity-all = Ver todas ({ $arg0 })
governance-activity-closed = Finalizado
governance-activity-confirm-petition = ¿Apoyar esta petición? Al alcanzar los apoyos necesarios se abrirá una votación de destitución.
governance-activity-needed = { $arg0 ->
    [one] Falta { $arg0 } apoyo
    *[other] Faltan { $arg0 } apoyos
    }
governance-activity-proposal = { $arg0 ->
    [appointment] Nombrar moderador a { $arg1 }
    [removal] Destituir a { $arg1 } como moderador
    [petition] Petición para destituir a { $arg1 }
    *[ban] Votación para vetar a { $arg1 }
    }
governance-activity-remaining = { $arg1 ->
    [days] Tiempo restante: { governance-days }
    [hours] Tiempo restante: { governance-hours }
    *[minutes] Tiempo restante: { governance-minutes }
    }
governance-activity-status = { $arg0 ->
    [available] Puedes votar
    [voted] Votado
    [join] Puedes apoyar
    [joined] Apoyado
    [closed] Finalizado
    *[view] Solo consulta
    }
governance-already-moderator = Ya eres moderador.
governance-application-status = { $arg0 ->
    [open] Hay una votación de nombramiento en curso
    [pending] Aprobada · nombramiento pendiente
    [blocked] Aún faltan requisitos
   *[ready] Puedes presentar tu candidatura
    }
governance-ballot-recorded = Tu voto anónimo se ha registrado como { $arg0 }. Puedes cambiarlo antes del cierre.
governance-ban-details = Propuesto por { $arg0 } · Duración { $arg1 } · Motivo: { $arg2 }
governance-ban-suffix = · Duración { $arg0 } · Motivo: { $arg1 }
governance-choice-abstain = Abstención
governance-choice-no = No
governance-choice-yes = Sí
governance-command-description = Ver estado de gobernanza y votaciones
governance-custodian-no-nomination = Los custodios no participan en las elecciones de moderadores.
governance-custodian-no-resign = La renuncia de moderadores no se aplica a los custodios.
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
governance-error-removal-petition-closed = Esta petición ha finalizado o ha sido sustituida.
governance-error-removal-requires-petition = Una votación de destitución necesita suficientes apoyos.
governance-error-removal-self = No puedes apoyar tu propia destitución.
governance-error-removal-sponsor-ineligible = Solo los miembros activos pueden apoyar una destitución.
governance-error-removal-sponsors-too-few = Se necesitan al menos otros dos miembros activos.
governance-error-removal-target-not-moderator = Este jugador ya no es un moderador que se pueda destituir.
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
governance-menu-already-sponsored = Ya apoyada
governance-menu-application = Estado de la candidatura
governance-menu-completed = Finalizados
governance-menu-confirm = Confirmar
governance-menu-confirm-nominate = Presentar la candidatura inicia una votación para tu nombramiento como moderador.
governance-menu-confirm-resign = ¿Renunciar como moderador?
governance-menu-confirm-sponsor = ¿Apoyar esta petición de destitución?
governance-menu-confirm-withdraw = ¿Retirar tu candidatura a moderador?
governance-menu-custodian-duties = Mantenimiento y administración del servidor.
governance-menu-identity = Rol
governance-menu-identity-hint = Requisitos de ascenso y registro de actividad.
governance-menu-join-petition = Apoyar petición de destitución
governance-menu-loading = Cargando estado de gobierno…
governance-menu-matters = Asuntos de la comunidad
governance-menu-moderator-duties = Moderación y ayuda a jugadores.
governance-menu-nominate = Postularse a moderador
governance-menu-offline = Desconectado
governance-menu-ongoing = En curso
governance-menu-online = Conectado
governance-menu-pause = Pausar ascenso de nuevos jugadores
governance-menu-pause-appeal = Vía de apelación
governance-menu-pause-duration = Duración (p. ej. 2d)
governance-menu-pause-reason = Motivo
governance-menu-pause-until = Finaliza
governance-menu-pending-application = Aprobado · pendiente de aplicación
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } apoyos
governance-menu-petitions = Peticiones de destitución
governance-menu-player-name = Nombre del jugador
governance-menu-players = Gestión de jugadores
governance-menu-refresh = Actualizar
governance-menu-related = Registros relacionados recientes
governance-menu-related-matters = Ver asuntos relacionados
governance-menu-resign = Renunciar como moderador
governance-menu-resume = Levantar pausa del ascenso
governance-menu-resume-reason = Motivo para levantarla
governance-menu-staff-only = Solo moderadores y custodios pueden gestionar jugadores.
governance-menu-staff-search = Buscar personal
governance-menu-subject-filter = { $arg0 } · Quitar filtro ×
governance-menu-team = Equipo público
governance-menu-team-empty = No hay personal que coincida
governance-menu-title = Gobierno de la comunidad
governance-menu-withdraw = Retirar candidatura
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
governance-seconds = { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }
governance-storage-error = No se pudieron procesar los datos de gobernanza. Contacta a un custodio.
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

plot-admin-created = Se registró { $arg0 } para { $arg1 } · { $arg2 }
plot-allowed = activado
plot-area-changed = Los límites cambiaron. Actualiza y vuelve a confirmar.
plot-area-confirm = ¿Sustituir los límites de «{ $arg0 }» por la selección ({ $arg1 ->
        [one] { $arg1 } celda de construcción
       *[other] { $arg1 } celdas de construcción
    })? Las construcciones se conservan.
plot-area-hint = Marca las esquinas con los clics izquierdo/derecho del hacha de madera o //pos1 y //pos2; vuelve para guardar.
plot-area-replace-save = Sustituir límites y guardar
plot-area-save = Guardar límites
plot-area-saved = Límites de «{ $arg0 }» guardados.
plot-atmosphere-saved = Ambiente guardado.
plot-command-blocked = La protección bloqueó este comando. Usa /fill o /setblock directamente en tu parcela; pide al equipo que revise los comandos /execute complejos.
plot-command-description = Gestionar parcelas de construcción
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } creado · { $arg1 }
        [feminine] { -plot } { $arg0 } creada · { $arg1 }
       *[other] { -plot } { $arg0 } creado · { $arg1 }
    }
plot-deleted = Parcela liberada; la construcción permanece.
plot-denied = desactivado
plot-error-admin-owner-only = Cambiar miembros con facultades de gestión requiere gestionar miembros y permisos.
plot-error-area-disconnected = La parcela debe formar una zona conectada.
plot-error-args = Argumentos inválidos o incompletos. Usa /plot help.
plot-error-busy = Se están guardando datos. Reinténtalo más tarde; esta operación no se ha enviado.
plot-error-flag = Permiso desconocido. Elige una operación de la lista de permisos.
plot-error-id = El ID no existe o es ambiguo. Usa /plot list.
plot-error-member-missing = Este jugador ya no es miembro.
plot-error-member-required = Debes ser miembro para crear una parcela.
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
plot-error-subplot-children = Libera las subparcelas antes de liberar la parcela principal.
plot-error-subplot-depth = Las subparcelas solo admiten un nivel.
plot-error-subplot-disconnected = Las celdas seleccionadas no están conectadas. Divide cada parte por separado.
plot-error-subplot-overlap = La selección se solapa con otra subparcela.
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
plot-menu-access = Miembros y permisos
plot-menu-add-member = Añadir miembro
plot-menu-atmosphere = Ambiente
plot-menu-clear = Despejado
plot-menu-confirm-delete = ¿Eliminar el registro de { $arg0 }? Se eliminarán la protección, los permisos de miembros y los ajustes. No se puede deshacer; las construcciones permanecerán.
plot-menu-confirm-subplot-transfer = ¿Transferir { $arg0 } a { $arg1 }? El dueño de la parcela principal conserva la supervisión.
plot-menu-confirm-transfer = ¿Transferir la gestión de { $arg0 } a { $arg1 }? Perderás el acceso; la autoría existente no cambia.
plot-menu-create = Crear parcela
plot-menu-create-for-player = Registrar parcela para este jugador
plot-menu-custom-time = Hora personalizada
plot-menu-delete = Eliminar registro de parcela
plot-menu-flag-container = Abrir contenedores
plot-menu-flag-entity = Interacción con entidades
plot-menu-follow-parent = Seguir a la parcela principal
plot-menu-follow-world = Seguir al mundo
plot-menu-group-collaborator = Colaboradores
plot-menu-group-member = Acceso de miembros
plot-menu-group-newcomer = Acceso de principiantes
plot-menu-here = Ubicación actual
plot-menu-manage = Gestionar parcelas de jugadores
plot-menu-managed-empty = Este jugador aún no tiene parcelas.
plot-menu-managed-title = Parcelas de { $arg0 }
plot-menu-members = Miembros ({ $arg0 })
plot-menu-name = Nombre del proyecto
plot-menu-owner = Propietario: { $arg0 }
plot-menu-parent-supervisor = Dueño de la parcela principal: { $arg0 } · supervisión
plot-menu-player = Nombre de jugador o UUID
plot-menu-rain = Precipitación
plot-menu-remove-member = Eliminar miembro
plot-menu-rename = Renombrar proyecto
plot-menu-set-role = Establecer como { $arg0 }
plot-menu-settings = Ajustes del proyecto
plot-menu-submit = Aplicar
plot-menu-subplot-of = Parte de { $arg0 }
plot-menu-subplot-parent = Volver a la parcela principal: { $arg0 }
plot-menu-subplots = Subparcelas ({ $arg0 })
plot-menu-time = Hora
plot-menu-time-input = Hora (HH:mm)
plot-menu-transfer = Transferir parcela
plot-menu-weather = Clima
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
plot-private = Privado
plot-public = Público
plot-region-adjust = Ajustar este rectángulo
plot-region-apply = Terminar ajuste del rectángulo
plot-region-cancel = Cancelar ajuste del rectángulo
plot-region-changed = La selección ha cambiado. Selecciona de nuevo el rectángulo.
plot-region-delete = Eliminar este rectángulo
plot-region-delete-confirm = ¿Quitar el rectángulo #{ $arg0 } del borrador? Los edificios permanecen. Los límites solo cambian al guardar; se puede deshacer.
plot-region-edit-hint = Ajusta este rectángulo con //pos1 y //pos2 y guarda.
plot-region-finish-first = Primero termina o cancela el ajuste actual del rectángulo.
plot-region-open = Gestionar rectángulos ({ $arg0 })
plot-region-select-hint = Selecciona un rectángulo numerado para enfocarlo y resaltarlo. Las líneas grises muestran esta página; al unirlos pueden cambiar los números.
plot-renamed = Proyecto renombrado.
plot-role-admin = administrador de parcela
plot-role-collaborator = colaborador
plot-role-owner = dueño de la parcela
plot-role-parent-owner = dueño de la parcela principal
plot-role-server-staff = personal del servidor
plot-selection-add = Añadir caja actual
plot-selection-clear = Reiniciar los dos puntos
plot-selection-empty = Marca dos esquinas con //pos1 y //pos2.
plot-selection-fit = Restaurar límites guardados
plot-selection-incomplete = Hay un punto marcado. Marca el otro; nada cambia antes de enviar.
plot-selection-redo = Rehacer último paso
plot-selection-replace = Reemplazar con caja actual
plot-selection-session-hint = Agacharse+F o /plot edit para volver. Sale tras 20 minutos de inactividad.
plot-selection-size = Selección: { $arg0 } × { $arg1 } × { $arg2 } bloques (X/Y/Z)
plot-selection-stopped = Selección terminada. Los borradores se conservan por parcela.
plot-selection-subtract = Restar caja actual
plot-selection-undo = Deshacer último paso
plot-subplot-created = Subparcela { $arg0 } creada · { $arg1 } bloque²
plot-title = Parcelas
plot-transferred = Parcela { $arg0 } transferida a { $arg1 }.
vehicle-command-description = Conducir y gestionar vehículos de entidades de visualización
vehicle-menu-label =
    { $arg0 ->
        [vehicle] Controles del vehículo
        [driving] Conducir y subir
        [maintenance] Mantenimiento y acceso
        [public-status] Acceso público
        [private-status] Acceso privado
        [home] Vehículos
        [nearby] Vehículos cercanos
        [fleet] Todos los vehículos accesibles
        [selection] Selección del modelo
        [origin] Fijar origen a mis pies
        [group] Seleccionar grupo de visualización
        [radius] Radio de selección (0–16 m)
        [displays] Explorar entidades cercanas
        [clear] Borrar selección y origen
        [capture] Crear modelo de la selección
        [toggle-selection] Alternar selección
        [public] Hacer público
        [private] Hacer privado
        [reload] Recargar modelos desde archivos
        [leave] Bajar del vehículo
        [passenger] Asiento de pasajero
        [fuel] Repostar · sostener carbón
        [repair] Reparar · sostener lingote de hierro
        [access] Acceso
        [remove] Eliminar vehículo
        [hud] Mostrar HUD de conducción
        [hide-preview] Ocultar vista 3D
        [service-hint] Acércate, para el motor y sostén el material requerido.
        [nearby-hint] Acércate a 32 bloques y sal del asiento actual.
        [use-required] Requiere mik.vehicle.use.
        [remove-hint] Detén el vehículo y baja a todos.
        [reload-hint] Todos los vehículos deben estar vacíos y apagados. Se conservan borradores; reabre los modelos modificados.
        [pick-hint] Apunta a una entidad y haz clic derecho. Agacharse y clic derecho cancela; caduca en 30 segundos.
        [spawn-hint] Coordenadas del mundo; cuatro bloques delante por defecto. Orientación según la vista horizontal al generar.
        [zoom-in] Ampliar
        [zoom-out] Reducir
        [frame] Recolocar delante
        [offset] Mover por desplazamiento
       *[other] { vehicle-command-description }
    }

vehicle-controls = { $arg0 }: W/S y A/D para conducir; rueda para marchas o acelerador; intercambiar manos cambia el modo; agacharse para salir. /vehicle
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
vehicle-hud-boat = { $arg0 } kn · empuje { $arg1 } % · rumbo { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · altitud { $arg1 } m · velocidad vertical { $arg2 } m/s · acelerador { $arg3 } % · { $arg4 }
vehicle-hud-status = Combustible { $arg0 } % · estado { $arg1 } % · { $arg2 }
vehicle-model-center = Centro de masa
vehicle-model-choice = { $arg0 ->
    [CAR] Coche
    [BOAT] Lancha
    [PLANE] Avión
    [BODY] Carrocería
    [FRONT_WHEEL] Rueda directriz
    [WHEEL] Rueda
    [PROPELLER] Hélice
    [RUDDER] Timón
    [FIXED] Fijo
    [VERTICAL] Mantener vertical
    [HORIZONTAL] Mantener horizontal
    [CENTER] Mirar a la cámara
    [NONE] Pose original
    [THIRDPERSON_LEFTHAND] Tercera persona · izquierda
    [THIRDPERSON_RIGHTHAND] Tercera persona · derecha
    [FIRSTPERSON_LEFTHAND] Primera persona · izquierda
    [FIRSTPERSON_RIGHTHAND] Primera persona · derecha
    [HEAD] Cabeza
    [GUI] Inventario
    [GROUND] Suelo
    [LEFT] Izquierda
    [RIGHT] Derecha
    [false] Desactivado
    [true] Activado
    [TEXT_CENTER] Centrado
   *[other] { $arg0 }
    }
vehicle-model-field = { $arg0 ->
    [kind] Tipo de vehículo
    [mass] Masa
    [maximum-speed] Velocidad máxima (km/h)
    [support-length] Recorrido de apoyo (m)
    [grip] Agarre
    [center-of-mass] Centro de masa
    [inertia] Inercia de rotación X/Y/Z
    [engine-torque] Par máximo
    [engine-idle-rpm] Ralentí (RPM)
    [engine-redline-rpm] Límite de revoluciones (RPM)
    [engine-inertia] Inercia del motor
    [engine-wheel-radius] Radio de rueda (m)
    [engine-final-drive] Relación final
    [engine-reverse-ratio] Relación de marcha atrás
    [engine-ratios] Relaciones de avance
    [engine-shift-seconds] Tiempo de cambio (s)
    [fuel-capacity] Capacidad de combustible
    [fuel-consumption] Consumo (/s)
    [buoyancy] Flotabilidad
    [wing-area] Superficie alar (m²)
    [lift] Coeficiente de sustentación
    [animation] Función de animación
    [billboard] Orientación a la cámara
    [item-transform] Pose del objeto
    [alignment] Alineación del texto
    [block-light] Luz de bloques
    [sky-light] Luz del cielo
    [background] Fondo (ARGB)
    [line-width] Anchura del texto
    [opacity] Opacidad (0–255)
    [shadow] Sombra del texto
    [see-through] Texto visible a través
    [x] X
    [y] Y
    [z] Z
    [size-x] Anchura completa X (m)
    [size-y] Altura completa Y (m)
    [size-z] Longitud completa Z (m)
   *[other] { $arg0 }
    }
vehicle-model-field-range = Intervalo permitido: 0 < valor ≤ { $arg0 }
vehicle-model-front = Frente del vehículo (+Z)
vehicle-model-front-choice = { $arg0 ->
    [negative-x] Lado −X actual
    [back] Parte trasera actual (−Z)
    [positive-x] Lado +X actual
    [look] Usar mi mirada horizontal (redondeada a 90°)
   *[other] Usar este lado como frente
    }
vehicle-model-front-hint = Elige la flecha en 3D. La carrocería permanece en su sitio; asientos, salidas y puntos físicos cambian de ejes juntos. Los cambios en incrementos de 90° se pueden deshacer.
vehicle-model-handle = { $arg0 ->
    [ROTATE] { $arg1 } { $arg2 } { $arg3 }°
   *[other] { $arg1 } { $arg2 } { $arg3 }
    }
vehicle-model-marker = { $arg0 ->
    [driver] Asiento del conductor #{ $arg1 }
    [passenger] Asiento del pasajero #{ $arg1 }
    [exit] Salida #{ $arg1 }
    [colliders] Caja de colisión #{ $arg1 }
    [supports] Punto de apoyo #{ $arg1 }
    [center] Centro de masa
    [parts] Pieza visual #{ $arg1 }
   *[other] { $arg1 }
    }
vehicle-model-parameter-group = { $arg0 ->
    [body] Carrocería y contacto
    [engine] Motor y transmisión
    [fuel] Combustible
    [boat] Flotabilidad del barco
    [plane] Sustentación del avión
    [power] Potencia del motor
   *[other] { $arg0 }
    }
vehicle-model-parameter-help = { $arg0 ->
    [body] Masa, velocidad y contacto con el suelo. El centro de masa se ajusta en 3D. Solo cambia el borrador hasta guardar.
    [engine] Revoluciones, par, ruedas y relaciones. Las relaciones de avance deben disminuir; el límite debe superar 1,5 × el ralentí.
    [fuel] La capacidad usa unidades de combustible del juego; el consumo es por segundo.
    [boat] La flotabilidad se aplica a barcos. Se conservan los valores de otros tipos.
    [plane] La superficie alar y la sustentación se aplican a aviones. Se conservan los demás valores.
    [power] Revoluciones y par del tipo actual. Se conservan los ajustes de cambio de los coches.
   *[other] { $arg0 }
    }
vehicle-model-property-help = { $arg0 ->
    [block-light] −1 usa la luz del mundo. Para sustituirla, ambos valores deben estar entre 0 y 15.
    [sky-light] −1 usa la luz del mundo. Para sustituirla, ambos valores deben estar entre 0 y 15.
    [background] Entero ARGB con signo, por ejemplo −16777216 para negro opaco.
    [line-width] Anchura de línea: 1–2048 píxeles.
    [opacity] 0 es transparente; 255 es opaco.
   *[other] { $arg0 }
    }
vehicle-model-spatial = Entrar en selección 3D
vehicle-model-spatial-hint = Clic derecho en una marca para seleccionar o ajustar. Agacharse + clic derecho vuelve a los ajustes. La rueda cambia el paso de ajuste.
vehicle-model-spawn = Generar este modelo
vehicle-model-add = Añadir componente
vehicle-model-add-here = Añadir apoyo a mis pies
vehicle-model-anchor = Recolocar delante
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
vehicle-model-corner-hint = { vehicle-model-corner-first } → { vehicle-model-corner-second }
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
vehicle-model-overview = Resumen del borrador
vehicle-model-panel = Volver al panel de edición
vehicle-model-editor-help = { $arg0 ->
   *[overview] Elige una categoría abajo, después un objeto y una herramienta. Guardar y Deshacer permanecen al pie.
    [parts] Gestiona bloques, objetos y textos visibles. Selecciona una pieza para cambiar su contenido o transformación.
    [seats] Selecciona un asiento para ajustar su posición y salida. Debe haber un único asiento de conductor.
    [colliders] Selecciona una caja de colisión para cambiar su centro y dimensiones completas. Añade una caja o ajústala al modelo.
    [supports] Selecciona un punto de contacto para moverlo. Estos puntos proporcionan apoyo en tierra o flotabilidad.
    [parameters] Elige un grupo de parámetros y después un campo para editar su valor.
    [tools] Ajusta la vista previa, selecciona objetos en el mundo o comprueba el borrador. Descartar requiere confirmación.
    }
vehicle-model-section-count = { $arg0 ->
   *[parts] { vehicle-model-parts } · { $arg1 }
    [seats] { vehicle-model-seats } · { $arg1 }
    [colliders] { vehicle-model-colliders } · { $arg1 }
    [supports] { vehicle-model-supports } · { $arg1 }
    }
vehicle-model-content-action = { $arg0 ->
   *[block] Definir contenido del bloque
    [item] Usar objeto de la mano principal
    [text] Definir contenido del texto
    }
vehicle-model-exact-input = Introducir valores exactos
vehicle-model-axis-value = { $arg0 } · { $arg1 }
vehicle-model-component-details = Detalles del componente
vehicle-model-selection-unavailable = El objeto ha cambiado o se ha eliminado; vuelve atrás y elígelo de nuevo
vehicle-model-exit = Salida / ajuste fino
vehicle-model-group = Añadir grupo de Axiom de la mano principal
vehicle-model-hand-invalid = El objeto de visualización de Axiom no es válido o contiene entidades o datos no compatibles.
vehicle-model-hand-required = Sostén un objeto de entidad de visualización de Axiom en la mano principal y vuelve a importar.
vehicle-model-here = Usar la posición actual de mis pies
vehicle-model-history-empty = No hay cambios en esta dirección del historial.
vehicle-model-import = Importar grupo de Axiom de la mano principal
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
vehicle-model-position-hint = Coordenadas locales: +Y hacia arriba, +Z hacia el frente. Los botones mueven por pasos; la entrada exacta fija la posición absoluta.
vehicle-model-preview = Vista previa privada
vehicle-model-preview-failed = Vista previa no disponible; se conserva el borrador: { $arg0 }
vehicle-model-preview-hint = Vista previa visible. Entra en selección 3D para seleccionar y ajustar el modelo directamente.
vehicle-model-redo = Rehacer
vehicle-model-remove = Eliminar componente
vehicle-model-remove-warning = ¿Eliminar este componente? Los índices posteriores cambian; se puede deshacer.
vehicle-model-rotate = Rotar componente
vehicle-model-rotate-hint = Rotación local relativa: X inclinación, Y orientación, Z alabeo. La posición no cambia.
vehicle-model-save = Guardar y terminar
vehicle-model-save-continue = Guardar y seguir editando
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } guardado
        [feminine] { -vehicle-model } guardada
       *[other] { -vehicle-model } guardado
    }
vehicle-model-scale = Escalar componente
vehicle-model-scale-all = Escalar todos los ejes { $arg0 } { $arg1 }
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
vehicle-model-tools = Vista previa y herramientas
vehicle-model-transform-step = Cambiar paso de ajuste
vehicle-model-undo = Deshacer
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
async-regen-command-description = Regenerar de forma asíncrona una selección de FastAsyncWorldEdit
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
async-regen-selection-required = Completa primero una selección de FastAsyncWorldEdit
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
async-regen-fawe-required = Se necesita FastAsyncWorldEdit para leer la selección
building-map-layer = Construcciones
building-map-open = Ver construcción
building-map-builders = Construido por { $arg0 }
building-map-original-author = Autor original: { $arg0 }
building-map-type-original = Original
building-map-type-derivative = Obra derivada
building-map-type-replica = Réplica
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ Agacharse
iron-elevator-tip-mm = Alinea { -iron-elevator-block } en vertical para crear un ascensor. Ponte encima: <green>salta para subir</green> y <aqua>agáchate para bajar</aqua>. Con dos o más plantas aparece un <gold>panel de plantas</gold>.
iron-elevator-up = ↑ Saltar
spawn-beacon-color-command-description = Cambiar el ciclo de color del faro del punto de aparición
spawn-beacon-color-disabled = Ciclo de color del faro del punto de aparición desactivado
spawn-beacon-color-enabled = Ciclo de color del faro del punto de aparición activado
spawn-command-description = Volver al punto de aparición
spawn-tip-return-mm = Usa <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> para volver al <yellow>punto de aparición del servidor</yellow>.
spawn-world-missing = El mundo del punto de aparición no está disponible

## Entertainment · Music

music-album = Álbum: { $arg0 }
music-artist = Artista: { $arg0 }
music-command-description = Música local, en línea y NBS para tocadiscos
music-disc-left = Clic izquierdo: tomar el disco
music-disc-right = Clic derecho: reproducir en el tocadiscos más cercano
music-duration = Duración: { $arg0 }
music-format = Formato: { $arg0 }
music-jukebox-audible-field-conflict = Otro tocadiscos reproduce una canción diferente en una zona claramente audible superpuesta
music-jukebox-tp-hover = Haz clic para teletransportarte al tocadiscos
music-jukebox-unavailable = El tocadiscos se eliminó o su chunk no está disponible
music-now-playing-rich = En reproducción: { $music } en el tocadiscos { $location }
music-online-rate-limited = Demasiadas solicitudes de música. Reinténtalo en { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }.
music-original-author = Autor original: { $arg0 }
music-plasmo-voice-unavailable = La canción ha empezado, pero no puedes oírla porque Plasmo Voice no está instalado, conectado o activado
music-playback-failed = No se pudo reproducir "{ $arg0 }". Comprueba la fuente o el archivo de música e inténtalo de nuevo.
music-reload-failed = Error al recargar la música: { $arg0 }
music-reload-started = Recargando archivos de música y fuentes personalizadas...
music-rhythm-animation-offset = Desfase de animación { $arg0 } ms
music-rhythm-calibration-accumulating = Confirmando un ritmo estable… sigue tocando
music-rhythm-calibration-adapting = Ajustando el ritmo… sigue tocando
music-rhythm-calibration-audio-instruction = Pulsa una de las teclas del 1 al 4 en cada golpe del ritmo.
music-rhythm-calibration-done = Prueba de latencia completa · ajustes guardados
music-rhythm-calibration-failed = El audio de la prueba se interrumpió. La prueba se cerró de forma segura; vuelve a iniciarla
music-rhythm-calibration-intro = { music-rhythm-calibration-stage-minecraft } → { music-rhythm-calibration-stage-plasmo } → { music-rhythm-calibration-stage-visual } → { music-rhythm-calibration-stage-pointer }
music-rhythm-calibration-listen = Escucha el ritmo completo una vez; todavía no pulses ninguna tecla.
music-rhythm-calibration-no-input = No se ha detectado una entrada válida. Usa el control indicado para esta fase en cada señal.
music-rhythm-calibration-out-of-range = El retraso medido está fuera del rango admitido. Revisa los ajustes de audio
music-rhythm-calibration-plasmo-ready = Prueba Minecraft Note Block completa; primero escucha el mismo ritmo por Plasmo Voice
music-rhythm-calibration-pointer-instruction = Haz clic izquierdo o derecho cuando la nota cruce la línea.
music-rhythm-calibration-realigning = El audio se interrumpió. Se repetirá la demostración y se conservarán las muestras completas
music-rhythm-calibration-required = Completa la prueba de latencia antes de iniciar el juego de ritmo
music-rhythm-calibration-reset-done = Se reinició la calibración de latencia
music-rhythm-calibration-result-hold = Prueba completada. Revisa los resultados; volverás automáticamente en un momento.
music-rhythm-calibration-stage-minecraft = Bloque musical de Minecraft
music-rhythm-calibration-stage-plasmo = Plasmo Voice
music-rhythm-calibration-stage-pointer = Visual · ratón
music-rhythm-calibration-stage-visual = Visual · teclado
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
music-rhythm-requires-playback = Primero reproduce una canción en este tocadiscos
music-rhythm-results = Resultados
music-rhythm-score = Puntuación: { $arg0 }
music-rhythm-spatial-no-room = No hay suficiente espacio para la puntería espacial. Ve a una zona más abierta.
music-rhythm-spatial-slider-hint = Sigue la estrella móvil y pulsa cada punto · no mantengas pulsado
music-rhythm-start = Iniciar partida
music-sample-rate = Frecuencia de muestreo: { $arg0 }
music-size = Tamaño: { $arg0 }
music-unavailable-disc = Disco no disponible

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
ban-menu-confirm-action = Expulsar jugador
ban-menu-confirm-severity = Gravedad
ban-menu-confirm-title = Confirmar expulsión
ban-menu-never-joined-confirm-action = Expulsar de todos modos
ban-menu-never-joined-title = El jugador nunca se ha conectado
ban-menu-never-joined-warning = { $arg0 } nunca se ha conectado a este servidor. Verifica el nombre para evitar expulsar al jugador equivocado.
ban-menu-player-input = Introducir nombre manualmente
ban-menu-select-title = Seleccionar jugador
ban-menu-severity-title = Seleccionar gravedad
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
maintenance-kick = El servidor está en mantenimiento
maintenance-set = Modo de mantenimiento { $arg0 }
maintenance-state-disabled = desactivado
maintenance-state-enabled = activado
maintenance-status = Modo de mantenimiento: { $arg0 }
maintenance-unchanged = El modo de mantenimiento ya está { $arg0 }
monitor-command-description = Supervisa jugadores al azar o a uno indicado; stop termina
monitor-return-failed = No se pudo regresar. La vigilancia sigue activa; usa /monitor stop para reintentar.
monitor-returning = Regresando a tu ubicación original…
monitor-started = Vigilancia activada. Usa /monitor para terminar.
monitor-stopped = Vigilancia terminada; has regresado a tu ubicación original.
monitor-unavailable = No se puede iniciar la vigilancia. Sal del vehículo y comprueba que se permita el modo Espectador.
monitor-waiting = Vigilancia: esperando a un jugador disponible…
monitor-watching = Vigilancia: { $arg0 }
performance-kick-title = El servidor está sufriendo un retraso grave
performance-kick-reason = Se te ha desconectado temporalmente para ayudar al servidor a recuperarse
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
tpsbar-tip-status-mm = Usa <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> para mostrar u ocultar la barra de <yellow>TPS, MSPT y ping</yellow>.
unban-command-description = Indultar a un jugador y conservar su historial

## Shell · Menus, server information and scripting

main-afk-status =
    { $arg0 ->
        [afk] AFK: Ausente
       *[online] AFK: En línea
    }
main-pvp-status =
    { $arg0 ->
        [on] PVP: Activado
       *[off] PVP: Desactivado
    }
main-announcements = Anuncios
main-home = Hogares
main-language = Idioma
main-language-auto = Automático
main-links = Enlaces
main-map = Mapa web
main-menu-title = Menú principal
main-music = Música
main-name-tag = Etiquetas de nombre
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
stat-ping-value = { $arg0 } ms
stat-play-time = Tiempo de juego
stat-role = Rol
stat-world = Mundo
web-account-title = Cuenta web y claves de acceso
web-account-confirm = Confirmar
weblogin-question = ¿Confirmar esta verificación de identidad web? Confirma solo acciones que hayas iniciado tú en mcmik.top. Nunca introduzcas códigos de otras personas.
weblogin-command-description = Confirmar un código de verificación de identidad web de Mik
weblogin-confirmed = Identidad web verificada. Vuelve al navegador para continuar.
weblogin-invalid-code = Código de verificación de identidad web no válido


# Music workspace
music-space-label =
    { $arg0 ->
        [title] Espacio musical
        [devices] Dispositivos
        [all-devices] Todos los dispositivos
        [use-device] Usar este dispositivo
        [current-device] Dispositivo actual
        [manage-device] Administrar
        [back-devices] Volver a dispositivos
        [group-with-devices] Agrupar con otros dispositivos
        [pick-world-member] Elegir un dispositivo en el mundo
        [group-world-pick-hint] Haz clic derecho en un tocadiscos mientras te agachas con la mano vacía para añadirlo; /music vuelve al menú.
        [group-member-standalone] Elige un tocadiscos que aún no pertenezca a un grupo de sincronización.
        [group-selection-help] Selecciona tocadiscos abajo o haz clic derecho en otro mientras te agachas con la mano vacía. Se agrupan al confirmar.
        [group-source-help] Usa la cola y la posición del dispositivo elegido. Crear detiene la reproducción; Crear y reproducir la reanuda.
        [device-actions] Acciones
        [device-status] Estado
        [sync-group] Grupo sincronizado
        [single-device] Tocadiscos
        [device-location] Ubicación
        [visit-device] Teletransportarse al lado
        [teleporting] Teletransportando…
        [recorded-device] Guardado
        [nearby-device] Cercano
        [group-select-members] Selecciona al menos dos tocadiscos
        [group-name-required] Escribe el nombre del grupo
        [direction-front] Delante
        [direction-front-right] Delante a la derecha
        [direction-right] A la derecha
        [direction-back-right] Detrás a la derecha
        [direction-back] Detrás
        [direction-back-left] Detrás a la izquierda
        [direction-left] A la izquierda
        [direction-front-left] Delante a la izquierda
        [direction-here] Aquí
        [direction-above] Arriba
        [direction-below] Abajo
        [no-devices] Sin dispositivos
        [choose-members] Elegir dispositivos del grupo
        [create-group] Crear grupo sincronizado
        [add-members] Añadir dispositivos
        [create-play] Crear y reproducir
        [create-and-append] Crear y añadir canciones al final
        [device-queue] Cola
        [device-members] Miembros
        [available] Disponible
        [play] Reproducir
        [retry-outputs] Reintentar salidas
        [dissolve-group] Disolver grupo
        [remove-member] Quitar del grupo
        [removed-stopped] Los dispositivos retirados se detienen y conservan el contenido elegido.
        [restore-previous] Restaurar contenido anterior al grupo
        [keep-group-content] Conservar contenido del grupo
        [queue] Cola de reproducción
        [back] { back }
        [close] { close }
        [unavailable] El almacenamiento de música no está disponible
        [saving] Guardando…
        [saved] Guardado
        [apply] { plot-menu-submit }
        [cancel] { cancel }
        [select-page] Seleccionar esta página
        [select-all] Seleccionar todos los resultados
        [clear-tracks] Quitar selección de canciones
        [add-playlist] Añadir a lista
        [description] Descripción
        [make-private] Hacer privada
        [publish] Hacer pública
        [delete] Eliminar lista
        [favorite] Añadir o quitar de favoritos
        [copy] Copiar lista
        [share] Compartir comando de la lista
        [recent] Reproducidas recientemente
        [favorites] Listas favoritas
        [public] Pública
        [private] Privada
        [new-playlist] Crear lista
        [name] Nombre
        [song-title] Canción
        [track-count] Pistas
        [visibility] Visibilidad
        [no-playlists] No hay listas
        [fetching-playlist] Cargando lista…
        [artist] Artista
        [duration] Duración
        [track-type] Tipo
        [type-online] En línea
        [undo] { vehicle-model-undo }
        [remove] Eliminar entradas seleccionadas
        [clear-recents] Borrar mi historial de reproducción
        [clear-entries] Quitar selección de entradas
        [search-tracks] Buscar canciones, artistas…
        [search-playlists] Buscar listas…
        [public-playlists] Listas públicas
        [searching] Buscando…
        [insert] Insertar y reproducir ahora
        [append] Añadir a la cola
        [choose-targets] Elegir salida
        [group-source] Usar la cola y el progreso de
        [removed-targets] Se han eliminado algunos destinos; vuelve a elegirlos
        [offline] Desconectado
        [device-discovery-hint] Agáchate y haz clic derecho en una gramola con la mano vacía para abrirla.
        [empty] Sin canciones
        [queue-empty] No hay canciones en cola
        [queue-current] Canción actual de la cola digital
        [pause] Pausar
        [resume] Reanudar
        [stop] Detener y conservar la lista
        [previous] Canción anterior / reiniciar
        [clear] Vaciar pendientes; detener tras la canción actual
        [seek] Posición
        [more] Más
        [volume-control] Volumen
        [group-volume] Volumen del grupo
        [timeline-help] Rueda ±5 s · Clic derecho para introducir tiempo
        [timeline-read-only] No se puede cambiar la posición
        [seek-format] Introduce m:ss, h:mm:ss o segundos
        [record] Guardar para control remoto
        [forget] Eliminar mis dispositivos guardados
        [alias] Alias personal del dispositivo
        [sound] Ajustes de sonido
        [rhythm] Salas de ritmo
        [room-players] Jugadores
        [room-player-status] Estado
        [room-preparation] Mi preparación
        [room-ready] Preparado
        [room-cancel-ready] Cancelar preparación
        [room-playing] En partida
        [room-score-ready] Partitura lista
        [room-select-one] Selecciona una canción con la selección múltiple de la biblioteca.
        [room-end-confirmation] Esto termina la sala para todos los participantes.
        [create-room] Crear sala
        [no-rhythm-devices] No hay tocadiscos cerca. Primero haz clic derecho en uno mientras te agachas y con la mano vacía.
        [calibration] Calibración de latencia
        [volume] Volumen (0–100)
        [range] Alcance (8–256 bloques)
        [mode] Modo de reproducción
        [save-queue] Guardar cola como lista
        [sequential] En orden
        [repeat-all] Repetir lista
        [repeat-one] Repetir canción
        [shuffle] Aleatorio sin repeticiones
        [import] Importar lista
        [link] Enlace de la lista
        [partial-import] Solo se ha obtenido parte de la lista. Guarda esa parte si quieres conservarla.
        [import-preview] Vista previa de los metadatos obtenidos; no se ha descargado audio
        [import-new] Crear y abrir
        [import-merge] Combinar con lista
        [stopped] Detenido
        [loading] Preparando audio
        [cancel-loading] Cancelar preparación
        [playing] Reproduciendo
        [paused] En pausa
        [finished] Finalizada
        [retry-failed] Reintentar reproducción
        [select-song] Reproduce las canciones seleccionadas o añádelas juntas a una lista.
        [select-toggle] Seleccionar esta entrada
        [deselect] Deseleccionar esta entrada
        [selected] Seleccionada
        [place-before] Colocar la selección antes de esta entrada
        [eject] Expulsar disco físico
        [make-disc] Crear disco de música firmado
        [gain] Volumen del grupo (0–100)
        [retry-import] Volver a obtener la lista
        [join-room] Unirse a la sala
        [ready] Preparado
        [waiting] En espera
        [room-mode] Modo de juego
        [room-difficulty] Dificultad
        [late-join] Unirse a la partida en curso
        [leave-room] Salir de la sala
        [select-room-track] Usar canción seleccionada
        [start-room] Iniciar partida
        [end-room] Terminar sala
        [base-queue] Lista de reproducción principal
        [priority-queue] Peticiones de una sola reproducción
        [falling] { music-rhythm-mode-falling }
        [radial] { music-rhythm-mode-radial }
        [spatial-aim] { music-rhythm-mode-spatial-aim }
        [easy] { music-rhythm-difficulty-easy }
        [normal] { music-rhythm-difficulty-normal }
        [hard] { music-rhythm-difficulty-hard }
        [expert] { music-rhythm-difficulty-expert }
        [waiting-for-player] Esperando a que los jugadores se preparen
        [preparing] Preparando audio y mapa de notas
        [no-beats] No hay ritmos jugables
        [catalog] Catálogo
        [my-playlists] Mis listas
        [playlist] Lista
        [output] Salida
        [close-panel] Cerrar panel
        [playlist-settings] Ajustes de lista
        [multi-select] Seleccionar
        [move] Mover
        [move-hint] Elige una canción para colocar la selección antes
        [cancel-move] Cancelar movimiento
        [move-end] Colocar al final
        [cancel-selection] Cancelar selección
        [track-details] Detalles de la canción
        [skip-next] Saltar a la siguiente
        [device-settings] Ajustes de dispositivos
        [already-added] Ya incluida
        [current-track] Pista actual
        [shuffle-candidates] Candidatas aleatorias
        [import-by-id] Importar por ID
        [playlist-id] ID de la lista
       *[unknown] Espacio musical
    }

music-space-error =
    { $arg0 ->
        [UNAVAILABLE] La música no está disponible. Vuelve a abrirla más tarde.
        [INTERNAL] Error interno de música; pide a un administrador que revise el registro del servidor
        [NOT_FOUND] La entrada seleccionada ya no existe.
        [FORBIDDEN] Necesitas permiso para controlar o editar.
        [CONFLICT] El contenido ha cambiado. Actualiza la vista y vuelve a seleccionar.
        [LIMIT] Se ha alcanzado el límite de recursos de música.
        [INVALID] Comprueba el nombre, la selección o el intervalo numérico.
        [OFFLINE] Dispositivo no disponible: carga su chunk o expulsa el disco físico.
        [OVERLAP] Las zonas de audición se solapan. Detén otro dispositivo, reduce el alcance o crea un grupo sincronizado.
        [UNSUPPORTED] Este medio no admite esa acción.
        [BUSY] El dispositivo, la solicitud o la sala de ritmo están ocupados. Inténtalo de nuevo cuando termine la operación.
        [NO_SAFE_DESTINATION] No hay un lugar seguro junto a este tocadiscos
        [TELEPORT_FAILED] El teletransporte falló o fue bloqueado
       *[UNKNOWN] La operación ha fallado
    }

music-space-selected-tracks =
    { $arg0 ->
        [one] { $arg0 } canción seleccionada
       *[other] { $arg0 } canciones seleccionadas
    }
music-space-track-count =
    { $arg0 ->
        [one] { $arg0 } pista
        *[other] { $arg0 } pistas
    }
music-space-device-count =
    { $arg0 ->
        [one] { $arg0 } tocadiscos
       *[other] { $arg0 } tocadiscos
    }
music-space-playback-target = Reproduciendo en: { $arg0 }
music-space-online-count = En línea: { $arg0 }/{ $arg1 } tocadiscos
music-space-device-distance = { $arg0 } · { $arg1 } m
music-space-device-height = { $arg0 ->
    [above] { $arg1 } m más arriba
   *[below] { $arg1 } m más abajo
    }
music-space-device-coordinates = { $arg0 } · X { $arg1 } · Y { $arg2 } · Z { $arg3 }
music-space-sound-value = { $arg0 ->
        [volume] { $arg1 ->
           *[other] { $arg1 }%
        }
       *[range] { $arg1 ->
            [one] { $arg1 } bloque
           *[other] { $arg1 } bloques
        }
    }
plot-permission-music-control = Controlar dispositivos musicales

music-partial-results = Solicitudes a fuentes fallidas: { $arg0 ->
        [one] { $arg0 } solicitud
       *[other] { $arg0 } solicitudes
    }; se muestran los resultados disponibles

webchat-tab-label = { $arg0 ->
    [1] { player-web-label }
   *[other] { player-web-label } ×{ $arg0 }
    }
webchat-joined = { $arg0 } se unió al chat web
webchat-left = { $arg0 } salió del chat web
chat-rate-limited = Estás enviando demasiado rápido. Espera un momento.

plot-map-corner-a = Esquina A
plot-map-corner-b = Esquina B
plot-map-corner-hint = Pulsa para centrar esta esquina. Clic derecho: coordenadas; rueda: Y ±4.
plot-map-coordinates = Coordenadas x,y,z
plot-map-zoom-in = Acercar
plot-map-zoom-out = Alejar
plot-map-fit = Ver área
plot-map-near = Cerca de mí
plot-map-layer = Capa Y { $arg0 }…{ $arg1 }
plot-map-layer-hint = Rueda: cambiar capa; clic: tu capa. El terreno siempre muestra la superficie.
plot-map-north = Norte
plot-map-west = Oeste
plot-map-east = Este
plot-map-south = Sur
plot-map-scale = 1 píxel = { $arg0 } × { $arg0 } bloques
plot-map-progress = Leyendo mapa { $arg0 }%…
plot-map-unknown = Muestras de superficie sin cargar: { $arg0 }
plot-map-legend = Cian: conservado · Morado: añadido · Naranja: eliminado · Verde: prisma · A/B: esquinas · Flecha: tú
plot-selection-validating = Calculando límites y validando el área en segundo plano… Se actualizará automáticamente al terminar.

plot-map-open = Ver mapa de Minecraft

plot-map-handheld-hint = Mapa en la mano secundaria; selecciona con un hacha de madera. Agacharse + F vuelve al panel.

plot-map-readonly-hint = Solo vista del mapa. Vuelve al panel y elige Ajustar prisma para cambiar las esquinas.

plot-destinations = Puntos de teletransporte
plot-destination-add = Añadir ubicación actual
plot-destination-confirm-delete = ¿Eliminar el punto «{ $arg0 }»?
plot-destination-delete = Eliminar punto
plot-destination-deleted = Punto de teletransporte eliminado.
plot-destination-description = Descripción (opcional)
plot-destination-edit = Editar nombre y descripción
plot-destination-empty = No hay puntos de teletransporte disponibles.
plot-destination-error-changed = Este punto ha cambiado. Vuelve a abrirlo.
plot-destination-error-description = La descripción admite hasta 512 caracteres y no permite caracteres de control.
plot-destination-error-location = Sitúate en terreno seguro dentro de esta parcela.
plot-destination-error-missing = Este punto ya no existe.
plot-destination-error-name = El nombre debe tener entre 1 y 48 caracteres en una sola línea.
plot-destination-error-private = Este punto es privado y solo está disponible para miembros de la parcela.
plot-destination-error-unsafe = Este punto no está disponible o no es seguro.
plot-destination-loading = Cargando punto de teletransporte…
plot-destination-move = Usar ubicación actual
plot-destination-name = Nombre
plot-destination-public-empty = Todavía no hay puntos públicos.
plot-destination-public-off = Público: no
plot-destination-public-on = Público: sí
plot-destination-saved = Punto de teletransporte guardado.
plot-destination-teleport = Teletransportarse
plot-error-subplot-destination = Mueve o elimina primero los puntos de la parcela principal dentro del área seleccionada.
plot-error-subplot-own-destination = Mantén los puntos dentro del área nueva o muévelos o elimínalos primero.

plot-destination-manage = Gestionar
plot-directory-owned = En propiedad · { $arg0 }
plot-directory-participating = Participación · { $arg0 }
plot-directory-search = Buscar parcelas
plot-directory-search-value = Búsqueda: { $arg0 }
plot-directory-search-field = Nombre o ID de parcela
plot-directory-clear-search = Borrar búsqueda
plot-directory-empty = Todavía no hay parcelas en esta categoría.
plot-directory-no-match = No hay parcelas coincidentes. Modifica o borra la búsqueda.
plot-workspace-area = Editar área
plot-workspace-area-hint = Ajustar el área protegida de esta parcela
plot-workspace-subplots = Subparcelas
plot-workspace-subplots-hint = Consultar, crear y gestionar subdivisiones
plot-workspace-access-hint = Gestionar miembros, roles y permisos de acciones
plot-workspace-settings-hint = Nombre, anuncio, entorno y propiedad
plot-workspace-destinations-hint = Consultar y usar los puntos de teletransporte de esta parcela
plot-workspace-role = Tu rol: { $arg0 }
plot-workspace-ownership = Propiedad y eliminación
plot-workspace-id = ID de parcela: { $arg0 }
plot-workspace-groups = Grupos de permisos
plot-workspace-permissions = Permisos de acciones
plot-workspace-other-world = Ve primero al mundo de esta parcela.

plot-destination-titles = Títulos de llegada
plot-destination-title = Título
plot-destination-subtitle = Subtítulo
plot-destination-title-hint = Se muestran al completar el viaje. Admiten colores, estilos y degradados de MiniMessage. Déjalos vacíos para ocultarlos; la vista previa no guarda.
plot-destination-title-preview = Vista previa a pantalla completa
plot-destination-title-unset = Sin definir
plot-destination-title-save = Guardar títulos
plot-destination-title-unchanged = No hay cambios sin guardar.
plot-destination-title-apply = Aplicar al borrador
plot-destination-error-title = El título debe tener una línea, hasta 32 caracteres visibles y 512 caracteres de código MiniMessage.
plot-destination-error-subtitle = El subtítulo debe tener una línea, hasta 64 caracteres visibles y 512 caracteres de código MiniMessage.
