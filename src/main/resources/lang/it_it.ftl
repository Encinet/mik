### MIK · it-it
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = Lotto
    .gender = masculine

-vehicle-model = Modello
    .gender = masculine

## Shared · General

back = Indietro
back-to-main = Torna al menu principale
cancel = Annulla
click-open = Clicca per aprire
close = Chiudi
current-off = Stato: disattivo
current-on = Stato: attivo
player-only = Questo comando può essere utilizzato solo dai giocatori
unknown = Sconosciuto
usage = Uso

## Shared · Language, roles and time

language-command-description = Apri le impostazioni della lingua
language-menu-title = Impostazioni della lingua
language-selected = Selezionato
language-set = Lingua impostata su { $arg0 }
role-custodian = Custode
role-member = Membro
role-moderator = Moderatore
role-new-player = Nuovo giocatore
time-hours-minutes = { $arg0 }h { $arg1 }m
time-minutes = { $arg0 }m

## Shared · Menu presentation

interface-fov-apply = Applica FOV
interface-fov-custom = Personalizzato
interface-fov-description = Inserisci il FOV del gioco per posizionare il menu.
interface-fov-input = FOV del client: intero da 30 a 110
interface-fov-invalid = Inserisci un numero intero da 30 a 110.
interface-fov-title = Campo visivo
interface-reduced-motion = Riduci movimento
interface-reduced-motion-hint = Riduce movimenti e oscillazioni del menu.
interface-scale-adjust-hint = Scorri su una percentuale per regolarla.
interface-scale-layout-title = Dimensione menu
interface-scale-menu-title = Interfaccia e testo
interface-scale-preview = Esempio Aa 123
interface-scale-reset = Ripristina al 100%
interface-scale-text-title = Dimensione testo
interface-theme-apply = Salva colori
interface-theme-charcoal = Carbone
interface-theme-color = Colore (#RRGGBB)
interface-theme-custom = Personalizzato
interface-theme-custom-hint = Copia tutti i valori attuali in un tema personalizzato indipendente.
interface-theme-edit = Modifica colori
interface-theme-forest = Foresta
interface-theme-hint = Si applica subito solo ai tuoi menu.
interface-theme-invalid = Inserisci un colore #RRGGBB e un’opacità da 0 a 100.
interface-theme-opacity = Opacità (0–100%)
interface-theme-role-accent = Colore di risalto
interface-theme-role-button = Sfondo del pulsante
interface-theme-role-button-disabled = Pulsante disabilitato
interface-theme-role-button-hovered = Pulsante indicato
interface-theme-role-button-pressed = Pulsante premuto
interface-theme-role-button-selected = Pulsante selezionato
interface-theme-role-input = Sfondo di immissione
interface-theme-role-sidebar = Sfondo laterale
interface-theme-role-toolbar = Sfondo degli strumenti
interface-theme-role-tooltip = Sfondo del suggerimento
interface-theme-role-window = Sfondo della finestra
interface-theme-title = Tema del menu
interface-theme-warm = Caldo
menu-collapse = Comprimi
menu-expand = Espandi
menu-finish-editing = Termina prima la modifica.
menu-input-clear = Svuota
menu-input-done = Fine
menu-input-empty = (vuoto)
menu-input-single-line = Questo campo accetta una sola riga.
menu-input-too-long = Lunghezza: { $arg0 } / { $arg1 }. Accorcia il testo prima di confermare.
menu-query-cancel = Annulla
menu-query-hint-rich = Premi { $chatKey } per scrivere…
menu-query-single-line = È consentita una sola riga. Fai clic sul campo per riprovare.
menu-query-too-long = Lunghezza: { $arg0 } / { $arg1 }. Fai clic sul campo per riprovare.
menu-rhythm-game-display = Gioca al gioco ritmico
menu-search-empty = Nessun risultato
menu-search-failed = Ricerca non riuscita
menu-search-limit = Mostrati: { $arg0 } / { $arg1 }. Restringi la ricerca.
menu-search-loading = Ricerca…
menu-search-retry = Riprova
menu-usage-display-mm = <aqua><bold>Nel menu</bold></aqua>
url-dialog-confirm = Apri
url-dialog-hint = Il client chiederà un’altra conferma per aprire il collegamento.
url-dialog-question-rich = Aprire { $label }?
url-dialog-title = Apri { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Mod consigliate: { $mods }. Fai clic su un nome per scaricare.
client-tip-axiom-flight-speed-mm = <white>Tieni premuto <yellow>Alt sinistro</yellow> per aprire il menu di <aqua>Axiom</aqua>, poi trascina il cursore verticale a destra per regolare la velocità di volo. Puoi anche usare <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click>.</white>
client-tip-axiom-intro-rich = Con { $mod } puoi copiare e incollare costruzioni, modellare il terreno e modificare le entità di visualizzazione. Clicca sul nome per scaricarlo.
client-version-behind-mm = <white>La versione del tuo client <yellow>{ $clientVersion }</yellow> è precedente a quella del server <green>{ $serverVersion }</green>. Ti consigliamo di aggiornare il client.</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> giocatore nelle vicinanze è <gold>invisibile</gold>
       *[other] <gold>{ $count }</gold> giocatori nelle vicinanze sono <gold>invisibili</gold>
    }</yellow> <gray>·</gray> <gray>il più vicino è a circa { $distance ->
        [one] { $distance } blocco
       *[other] { $distance } blocchi
    }</gray>
player-bedrock-label = Bedrock
player-web-label = Web
presence-join-templates =
    .item-1 = Din don! { $player } è online!
    .item-2 = Evviva, c'è { $player }!
    .item-3 = Ciao { $player }, eccoti qui!
# Broadcast to remaining players; the departing player does not receive this message.
presence-quit-templates =
    .item-1 = { $player } si disconnette.
    .item-2 = Din don! { $player } è offline
    .item-3 = Online -1. { $player } si disconnette
welcome-first-join-mm = <gold><bold>Ti diamo il benvenuto in MIK!</bold></gold><newline><gray>Inizia con il <click:open_url:'https://mcmik.top'><yellow>sito web</yellow></click> e <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>; puoi anche usare <yellow>/menu</yellow> per aprire il menu principale.</gray>
welcome-return-mm = <gold><bold>Ti diamo di nuovo il benvenuto!</bold></gold><newline><gray>La tua ultima visita risale a <yellow>{ $arg0 ->
        [one] { $arg0 } giorno
       *[other] { $arg0 } giorni
    } fa</yellow>. Usa <yellow>/menu</yellow> per trovare rapidamente le funzioni più comuni.</gray>

## Player · Guidance

tip-label = CONSIGLIO

## Player · AFK

afk-already-afk-mm = <gray>Sei già <gold>AFK</gold></gray>
afk-command-description = Attiva lo stato AFK
afk-default-statuses =
    .item-1 = si allontana dalla tastiera
    .item-2 = entra in modalità risparmio energetico
    .item-3 = passa in secondo piano
    .item-4 = sta fissando il vuoto
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>ora è AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>ha lasciato una nota:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>ha cambiato il suo stato in:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>torna alla tastiera</green>
    .item-2 = <gray>*</gray> <player> <green>è di nuovo qui</green>
afk-not-afk-mm = <gray>Al momento non sei <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>Lo stato AFK può contenere al massimo { $arg0 ->
        [one] <white>{ $arg0 }</white> carattere
       *[other] <white>{ $arg0 }</white> caratteri
    }</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · corrispondenze: { $arg2 } record · storico: { $arg3 }
player-association-header = [Associazione accesso] { $arg0 } condivide lo storico degli indirizzi con { $arg1 ->
        [one] { $arg1 } giocatore
       *[other] { $arg1 } giocatori
    }.
player-association-hover = Alcuni record storici di rete coincidono. È solo un aiuto per la verifica; gli indirizzi non vengono mostrati.
player-association-more-addresses = Altri { $arg0 } record
player-association-more-players = Altri { $arg0 ->
        [one] { $arg0 } giocatore
       *[other] { $arg0 } giocatori
    }
player-association-time-days = { $arg0 ->
        [one] { $arg0 } giorno
       *[other] { $arg0 } giorni
    } fa
player-association-time-occurrences = { $arg0 } ({ $arg1 } record)
player-association-time-separator = ,
player-association-time-today = oggi


## Player · Account security
password-already-connected = Questo account è già connesso.
password-already-set = È già impostata una password. Usa /password change per cambiarla.
password-busy = Troppi tentativi. Attendi e riprova.
password-change = Modifica password
password-change-done = Password cambiata.
password-checking = Verifica della sicurezza dell’account…
password-command-description = Gestisci la protezione dell’account
password-confirm = Conferma nuova password
password-conflict = La sicurezza dell’account è cambiata. Ripeti l’operazione.
password-cooldown = La verifica dell’account è temporaneamente limitata. Riprova tra { $arg0 ->
        [one] { $arg0 } secondo
       *[other] { $arg0 } secondi
    }.
password-current = Password attuale
password-denied = Non puoi reimpostare la password di questo account.
password-disabled = Password: non impostata
password-enabled = Password: impostata
password-invalid = Password errata. Riprova.
password-keep-role = I moderatori e i custodi non possono rimuovere la propria password.
password-login = Accedi
password-login-expired = La verifica di accesso è scaduta. Riconnettiti al server.
password-login-hint = Inserisci la password del tuo account su questo server. Se l’hai dimenticata, chiedi allo staff di reimpostarla.
password-mismatch = Le nuove password non coincidono.
password-new = Nuova password
password-nomination-required = Imposta una password prima di candidarti come moderatore: /password set
password-not-set = Nessuna password impostata. Usa /password set per impostarne una.
password-remove = Rimuovi password
password-remove-done = Password rimossa.
password-remove-question = Rimuovendo la password, non verrà più richiesta per accedere. Inserisci la password attuale per confermare.
password-required-application = Non puoi rimuovere la password durante una candidatura allo staff o una nomina in attesa.
password-required-role = Gli account dello staff richiedono una password. Chiedi a una persona autorizzata di reimpostarla prima di accedere.
password-reset = Reimposta password
password-reset-console = Password di { $arg0 } reimpostata: { $arg1 }. Consegnala tramite un canale fidato dopo aver verificato l’identità.
password-reset-done = Password di { $arg0 } reimpostata. Copiala ora e consegnala dopo aver verificato l’identità; non sarà più consultabile.
password-reset-question = Reimpostare la password di { $arg0 }? La precedente non funzionerà più e le connessioni verranno chiuse. Conferma con la tua password.
password-retry = Riprova
password-rules = Usa da { $arg0 } a { $arg1 } caratteri. I nomi dei giocatori e le password comuni non sono consentiti.
password-set = Imposta password
password-set-done = Password impostata.
password-set-first = Imposta una password prima di candidarti
password-storage = La sicurezza degli account non è disponibile. Riprova più tardi o contatta il proprietario.
password-timeout = La verifica è stata annullata o è scaduta. Riconnettiti per riprovare.
password-tip-set-mm = Usa <gold><u>/password</u></gold> per impostare una <yellow>password di accesso</yellow> e proteggere il tuo account.
password-title = Sicurezza dell’account

## Player · Profile and tab list

nametag-clear = Svuota
nametag-clear-hint = Rimuove i valori personalizzati; gli stili ereditati possono restare.
nametag-command-description = Personalizza le etichette con i nomi
nametag-confirm = Conferma
nametag-conflict = Il valore attuale è cambiato. Confronta l’effetto attuale e conferma di nuovo per sostituirlo.
nametag-copy-hint = Invia il codice di prefisso e suffisso in chat
nametag-copy-label = [copia]
nametag-copy-sent = Il testo sorgente dello stile di { $arg0 } è in chat.
nametag-current = Effetto attuale
nametag-edit-ended = La modifica è terminata. Fai di nuovo clic sul campo per continuare.
nametag-edit-prompt = Premi T per modificare { $arg0 } con MiniMessage (privato).
nametag-finish-field = Prima conferma o annulla il campo che stai modificando.
nametag-input-invalid = Formato non valido. Invia MiniMessage su una sola riga o seleziona Svuota.
nametag-leading-label = Prefisso del nome
nametag-mine = La mia etichetta
nametag-next = Avanti
nametag-no-changes = Nessuna modifica
nametag-no-permission = I membri e lo staff possono modificare le etichette. Puoi comunque vedere gli stili.
nametag-no-players = Nessun giocatore ha configurato uno stile del nome
nametag-page = Pagina { $arg0 } / { $arg1 }
nametag-players = Stili dei giocatori
nametag-preview-label = Anteprima
nametag-previous = Indietro
nametag-refresh = Aggiorna
nametag-saving = Salvataggio…
nametag-set-error = Salvataggio non riuscito: { $arg0 }. Riprova; l’effetto attuale è mostrato sopra.
nametag-set-success = ✔ { $arg0 } impostato
nametag-styles-failed = Impossibile caricare gli stili. Aggiorna per riprovare.
nametag-styles-loading = Caricamento degli stili dei giocatori…
nametag-tip-customize-mm = Usa <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> per personalizzare il <light_purple>prefisso e il suffisso del tuo nome</light_purple>.
nametag-title = Etichette nome
nametag-too-long = { $arg0 } è troppo lungo. La lunghezza massima è di { $arg1 ->
        [one] { $arg1 } carattere
       *[other] { $arg1 } caratteri
    }
nametag-trailing-label = Suffisso del nome
nametag-unset = (non impostato)
nametag-unsupported-placeholder-rich = Sono supportate solo le variabili PlaceholderAPI { $supported }, non { $unsupported }
nametag-user-data-load-failed = Impossibile caricare i dati utente. Riprova più tardi
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Giocatori online: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Torna alla precedente posizione di teletrasporto o morte
back-empty-mm = <gray>Nessuna posizione a cui tornare</gray>
back-pending-mm = <gray>Teletrasporto in corso, attendi</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>Ritorno di { $steps ->
        [one] { $steps } posizione
       *[other] { $steps } posizioni
    }</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>Teletrasporto fallito. Riprova</red>
back-tip-return-mm = Usa <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> per tornare alla posizione precedente all’ultimo teletrasporto o alla morte.
back-tip-undo-mm = Usa <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> per annullare il tuo ultimo <yellow>/back</yellow>.
back-undo-empty-mm = <gray>Nessun <white>/back</white> da annullare</gray>
back-undo-success-mm = <green>Ultimo <white>/back</white> annullato</green>
back-undo-world-missing-mm = <red>Il mondo di destinazione non è disponibile. Impossibile annullare</red>
back-world-missing-mm = <red>Il mondo di destinazione non è disponibile. Impossibile tornare</red>
boundary-near-mm = <yellow>Ti stai avvicinando al confine del mondo</yellow>
boundary-reached-mm = <red>Hai raggiunto il confine del mondo</red>
fly-command-description = Attiva o disattiva il volo personale
fly-disabled = Volo disattivato
fly-enabled = Volo attivato
fly-native-mode = Il tuo accesso a /fly è disattivato. Questa modalità di gioco consente comunque di volare.
fly-speed-click = Imposta velocità di volo a { $arg0 }x
fly-speed-current = Velocità di volo: { $arg0 }x
fly-speed-limited = Il carico di esplorazione limita temporaneamente il volo a { $arg0 }x; la tua impostazione resta { $arg1 }x.
fly-speed-presets = Preimpostazioni
fly-speed-reset = Velocità di volo reimpostata a 1x
fly-speed-reset-action = Reimposta
fly-speed-reset-click = Reimposta la velocità di volo a 1x
fly-speed-set = Velocità di volo impostata a { $arg0 }x
fly-state-disabled = Disattivato
fly-state-enabled = Attivato
fly-status = Volo: { $arg0 } | Velocità: { $arg1 }x
home-action-create = Crea casa
home-action-delete = Elimina casa
home-action-teleport = Teletrasportati
home-action-update = Aggiorna posizione
home-already-exists = Esiste già una casa con questo nome
home-confirm-delete = Conferma eliminazione
home-confirm-delete-detail-rich = Elimina casa { $home }
home-confirm-delete-warning = Questa operazione non può essere annullata
home-confirm-update = Conferma aggiornamento
home-confirm-update-detail-rich = Aggiorna la casa { $home } a questa posizione
home-delete-command-description = Elimina una casa
home-deleted-rich = Casa { $home } eliminata
home-distance = Distanza: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    }
home-distance-other-world = mondo diverso
home-empty-description = Stai dove vuoi salvare una casa
home-empty-title = Nessuna casa ancora
home-invalid-name = I nomi delle case non possono contenere < > : caratteri
home-list-rich = Case: { $homes }
home-location = Posizione: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = I dati sulla posizione di questa casa non sono validi
home-max-reached = Puoi impostare al massimo { $arg0 ->
        [one] { $arg0 } casa
       *[other] { $arg0 } case
    }
home-menu-title = Case
home-no-homes = Non hai ancora impostato nessuna casa
home-not-found-rich = Casa { $home } non esiste
home-search-placeholder = Cerca nomi delle case…
home-search-empty = Nessuna casa corrispondente
home-set-command-description = Imposta una casa nella tua posizione attuale
home-set-rich = Casa { $home } impostata
home-summary-count = Imposta: { $arg0 } / { $arg1 }
home-teleport-failed = Teletrasporto fallito. Riprova
home-teleported-rich = Teletrasportato a casa { $home }
home-tip-create-mm = Salva la tua posizione con <green><u><click:suggest_command:'/sethome '>/sethome nome</click></u></green>, poi torna con <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>.
home-tip-return-mm = Apri <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>, seleziona una casa e premi «{ home-action-teleport }».
home-updated-rich = Posizione della casa { $home } aggiornata
home-usage-book = Guida ai comandi
home-usage-delhome = /delhome <name>  Elimina una casa
home-usage-home = /home <name>  Teletrasportati direttamente
home-usage-name-rule = I nomi delle case non possono contenere < > :
home-usage-sethome = /sethome <name>  Salva la tua posizione attuale
home-world = Mondo: { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Annulla l'ultimo /back
teleport-accept-command-description = Accetta la richiesta di teletrasporto in sospeso
teleport-allow = Teletrasporti da altri giocatori
teleport-bare-entity-selector = Per evitare teletrasporti accidentali, @e richiede parentesi quadre. Usa un selettore esplicito come @e[type=…].
teleport-block-afk = Blocca mentre AFK
teleport-denied = { $player } attualmente rifiuta i teletrasporti da altri giocatori
teleport-deny-command-description = Rifiuta la richiesta di teletrasporto in sospeso
teleport-force-command-description = Forza il teletrasporto ignorando le preferenze del giocatore (solo amministratori)
teleport-menu-title = Privacy del teletrasporto
teleport-moved-here = Sei stato teletrasportato qui da { $arg0 }
teleport-policy-always-allow = Consenti sempre
teleport-policy-always-deny = Rifiuta sempre
teleport-policy-require-consent = Richiedi la mia approvazione
teleport-request-accept = Accetta
teleport-request-approved = { $player } ha accettato la tua richiesta di teletrasporto
teleport-request-deny-action = Rifiuta
teleport-request-expired = La richiesta di teletrasporto è scaduta
teleport-request-failed = Impossibile completare il teletrasporto. Riprova
teleport-request-none = Non hai richieste di teletrasporto in sospeso
teleport-request-received = { $sender } vuole teletrasportarti: { $command }
teleport-request-rejected = Richiesta di teletrasporto rifiutata
teleport-request-sent = Richiesta di teletrasporto inviata a { $player }
tpany-command-description = Teletrasportati nella posizione di un giocatore, online o offline
tpany-done-offline-rich = Teletrasportato all'ultima posizione nota di { $player } { $world } { $location }
tpany-done-online-rich = Teletrasportato alla posizione attuale di { $player }
tpany-not-found-rich = Impossibile trovare la posizione di { $player }
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teletrasporto nella posizione del giocatore, inclusa la cache offline

## Player · PVP

pvp-admin-menu-title = Amministrazione PVP
pvp-allow-mounted-damage = I mob montati possono essere attaccati
pvp-allow-mounted-damage-desc = Se abilitato, un altro giocatore può attaccare un mob che stai cavalcando mentre entrambi avete il PVP abilitato
pvp-auto-enable-desc = Attacca un giocatore abilitato al PVP due volte di seguito
pvp-auto-enable-label = Abilitazione automatica
pvp-auto-enable-warning-actionbar-mm = <yellow>Attacca ancora una volta per abilitare automaticamente il PVP</yellow>
pvp-auto-enabled-actionbar-mm = <green>PVP abilitato automaticamente</green>
pvp-combat-locked-mm = <red>Non puoi disabilitare il PVP in combattimento</red> <dark_gray>|</dark_gray> <gray>Tempo rimanente: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> secondo
       *[other] <yellow>{ $arg0 }</yellow> secondi
    }</gray>
pvp-combat-tag-label = Tempo di recupero del combattimento
pvp-combat-tag-value = { $arg0 } s
pvp-command-description = Attiva/disattiva lo stato PvP
pvp-enable = Abilita il PvP
pvp-enable-desc = Quando abilitato, puoi attaccare altri giocatori abilitati al PVP e loro possono attaccare te
pvp-enable-on-death = Abilita il PVP alla morte
pvp-enable-on-death-desc = Se abilitato, il PVP si attiva automaticamente quando muori
pvp-enable-on-death-label = Abilita alla morte
pvp-enabled-on-death-mm = <yellow>Il PVP è stato abilitato perché sei morto</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Apri l'amministratore PVP del giocatore online</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Apri le impostazioni PVP</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Gestisci un giocatore online</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Visualizza il tuo stato PVP</gray>
pvp-help-title-mm = <gold><bold>Comandi PVP</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Attiva/disattiva il tuo stato PVP</gray>
pvp-menu-title = Impostazioni PvP
pvp-mob-protected-actionbar-mm = <yellow>Questo mob è protetto dai danni PVP</yellow>
pvp-mob-protection-label = Protezione dei mob
pvp-mounted-damage-label = Danno mob montato
pvp-no-permission-mm = <red>Non hai il permesso di gestire lo stato PVP di un altro giocatore</red>
pvp-player-not-found-mm = <red>Giocatore online non trovato</red>
pvp-protect-mobs = Proteggi i tuoi mob
pvp-protect-mobs-desc = Proteggi i mob che hai domato o che stai cavalcando
pvp-quick-combat-cooldown = Blocco di combattimento: { $arg0 ->
        [one] ancora { $arg0 } secondo
       *[other] ancora { $arg0 } secondi
    }
pvp-quick-override = Una regola temporanea controlla il PvP. Puoi modificare le tue preferenze nelle impostazioni.
pvp-set-by-staff-mm = <aqua>Lo staff imposta il tuo stato PVP su</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = Stato PVP di { $arg0 } impostato su { $arg1 }
pvp-set-other-rich = Stato PVP di { $player } impostato su { $state }
pvp-state-label = PvP
pvp-state-off = Disattivo
pvp-state-on = Attivo
pvp-status-player = Giocatore: { $arg0 }
pvp-status-player-rich = Giocatore: { $player }
pvp-status-title-mm = <gold><bold>Stato PvP</bold></gold>
pvp-summary-disabled = PvP disabilitato
pvp-summary-enabled = PVP abilitato
pvp-target-disabled-actionbar-mm = <yellow>Quel giocatore non ha il PVP abilitato</yellow>
pvp-target-menu-title = Impostazioni PvP - { $arg0 }
pvp-tip-protection-mm = Attiva o disattiva il tuo PVP con <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple>. <green>Entrambi i giocatori devono attivarlo</green> per combattere.
pvp-toggled-off-mm = <gray>PvP disabilitato</gray> <dark_gray>|</dark_gray> <gray>Gli altri giocatori non possono attaccarti</gray>
pvp-toggled-on-mm = <green>PVP abilitato</green> <dark_gray>|</dark_gray> <gray>Ora puoi combattere altri giocatori abilitati al PVP</gray>

## Player · Effects

effect-cleanup-all = [Rimuovi tutti gli effetti]
effect-cleanup-blocked = Puoi rimuovere gli effetti solo in modalità Creativa o Spettatore e fuori dai combattimenti PVP.
effect-cleanup-expired = Questa richiesta di rimozione è scaduta.
effect-cleanup-negative = [Rimuovi gli effetti negativi]
effect-cleanup-prompt-rich = Hai ricevuto effetti negativi a causa di { $source }. Vuoi rimuoverli?
effect-cleanup-result = { $count ->
        [one] Rimosso { $count } effetto.
       *[other] Rimossi { $count } effetti.
    }
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] una nube di pozione persistente
        [ARROW] una freccia
        [ATTACK] un attacco
        [COMMAND] un comando
        [FOOD] cibo
        [PLUGIN] un plugin
        [POTION_DRINK] una pozione bevuta
        [POTION_SPLASH] una pozione da lancio
        [WARDEN] un sorvegliante
        [WITHER_ROSE] una rosa del Wither
       *[OTHER] una causa sconosciuta
    }

## Player · Trash

trash-clear = Svuota
trash-clear-hint = Clicca per eliminare subito tutti gli oggetti.
    Vengono eliminati automaticamente anche { $arg0 ->
        [one] { $arg0 } secondo
       *[other] { $arg0 } secondi
    } dopo la chiusura.
trash-command-description = Apri il cestino
trash-sort = Ordina
trash-sort-hint = Unisci le pile uguali e ordina gli oggetti per tipo
trash-tip-dispose-mm = Usa <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> per aprire il <yellow>cestino</yellow> e metterci gli oggetti che non ti servono.
trash-title = Cestino

## Player · Utilities

hat-command-description = Indossa l'oggetto con la mano principale
hat-empty-hand-mm = <red>Nessun oggetto nella mano principale</red> <gray>Impugna un blocco o un oggetto, poi usa /hat</gray>
hat-success-mm = <green>Oggetto della mano principale indossato</green> <dark_gray>|</dark_gray> <gray>La mano e lo slot dell'elmo sono stati scambiati</gray>
hat-tip-wear-mm = Usa <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> per indossare in testa l’oggetto nella <yellow>mano principale</yellow>.
removeitems-command-description = Rimuovi gli oggetti caduti
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Rimozione completata: { $countValue ->
        [one] { $count } oggetto a terra
       *[other] { $count } oggetti a terra
    } · mondo { $world } { $location } { $radius }
removeitems-no-world = Impossibile determinare il mondo
removeitems-radius = raggio { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    }
removeitems-tip-usage-mm = Usa <aqua><u><click:suggest_command:'/rmitems '>/rmitems [raggio]</click></u></aqua> per rimuovere gli <yellow>oggetti a terra</yellow> nelle vicinanze.
self-kick-command-description = Espellerti dal server
self-kick-reason-too-long = Il motivo non può superare { $arg0 ->
        [one] { $arg0 } carattere
       *[other] { $arg0 } caratteri
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Hai lasciato il server per tua scelta. Motivo: { $reason }
       *[third] { $player } lascia il server per propria scelta. Motivo: { $reason }
    }
    .item-1 = { $player } lascia il server per propria scelta. Motivo: { $reason }
    .item-2 = { $player } si disconnette con un messaggio: { $reason }
    .item-3 = { $player } lascia il server con un messaggio: { $reason }
    .item-4 = { $player } saluta con una nota: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Hai lasciato il server per tua scelta.
       *[third] { $player } lascia il server per propria scelta.
    }
    .item-1 = { $player } lascia il server per propria scelta.
    .item-2 = { $player } si disconnette.
    .item-3 = Din don! { $player } è offline
    .item-4 = Online -1. { $player } si disconnette

## Community · Chat and mentions

chat-content-audio = [Audio]
chat-content-card = [Scheda]
chat-content-contact = [Contatto]
chat-content-emote = [Azione]
chat-content-face = [Emoji]
chat-content-file = [File]
chat-content-history = [Cronologia della chat]
chat-content-image = [Immagine]
chat-content-location = [Posizione]
chat-content-message = [Messaggio]
chat-content-multiple = [Più messaggi]
chat-content-music = [Musica]
chat-content-notice = [Avviso]
chat-content-reply = [Messaggio citato]
chat-content-sticker = [Adesivo]
chat-content-video = [Video]
chat-copy-hover = Clic sinistro per copiare il messaggio
chat-delay-3s = 3 secondi
chat-delay-5s = 5 secondi
chat-delay-7s = 7 secondi
chat-delay-cancel-command-description = Annulla i messaggi ritardati in sospeso
chat-delay-cancelled = Messaggio in sospeso annullato
chat-delay-current = Ritardo attuale della chat: { $arg0 }
chat-delay-nothing-to-cancel = Nessun messaggio in sospeso da annullare
chat-delay-off = Disattivato
chat-delay-preview-marker = [In attesa]
chat-delay-queued = Il messaggio verrà inviato { $arg0 }; utilizzare /cancel o /c per annullare
chat-item-empty-hover = Nessun oggetto nella mano principale
chat-mention-all-hover = Avvisa ogni giocatore visibile in questo canale
chat-msg-command-description = Invia un messaggio privato e passa alla chat privata
chat-player-hover = Clic sinistro per iniziare un messaggio privato
chat-private-enter = Passato alla chat privata con { $arg0 }
chat-private-label = MSG
chat-private-no-reply-target = Nessuna destinazione recente della chat privata è online
chat-private-self = Non puoi avviare una chat privata con te stesso
chat-private-target-not-found = Giocatore online non trovato: { $arg0 }
chat-private-target-offline = Il destinatario è offline; attendi oppure scegli un altro canale
chat-private-tip-channel-mm = Usa <aqua><u><click:suggest_command:'/msg '>/msg giocatore</click></u></aqua> per entrare nella chat privata e <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> per tornare a quella pubblica.
chat-public-command-description = Cambia canale di chat pubblica
chat-public-enter = Passato alla chat pubblica
chat-reply-command-description = Rispondi alla tua ultima chat privata target
chat-settings-menu-title = Impostazioni della chat
chat-social-account-label = Account della piattaforma
chat-social-bound-player-label = Giocatore collegato
chat-social-name-label = Nome social
chat-social-unbound = Non collegato a un giocatore Minecraft
chat-staff-command-description = Cambia canale di chat dello staff
chat-staff-label = Staff
chat-staff-permission-missing = Manca il permesso di chat del personale; tornato alla chat pubblica
mention-action-bar = Richiesta della barra delle azioni
mention-action-bar-text = { $sender } ti ha menzionato
mention-alerts = Avvisi di menzione
mention-menu-title = Avvisi di menzione
mention-mute-afk = Disattiva audio mentre AFK
mention-sound = Suono di avviso
staffchat-enter = Sei entrato nella chat dello staff

## Community · Announcements

announcement-count = { $arg0 ->
        [one] { $arg0 } annuncio
       *[other] { $arg0 } annunci
    }
announcement-empty = Nessun annuncio
announcement-first-page = Avvisi più recenti raggiunti
announcement-last-page = Avvisi più vecchi raggiunti
announcement-latest = Ultimo annuncio
announcement-list-newer = Gruppo più recente
announcement-list-older = Gruppo precedente
announcement-more-count = Altri { $arg0 } non mostrati
announcement-new-count = { $arg0 } nuovi
announcement-newer = Avvisi più recenti
announcement-next-page = Pagina successiva
announcement-older = Avvisi precedenti
announcement-page = Pagina { $arg0 } / { $arg1 }
announcement-previous-page = Pagina precedente
announcement-reload = Ricarica annunci
announcement-reload-command-description = Ricarica il file degli annunci
announcement-reload-failed = Impossibile ricaricare gli annunci; quelli precedenti restano disponibili
announcement-reloaded = Annunci ricaricati
announcement-unread-count = { $arg0 } non letti
announcement-updated = Annunci aggiornati
announcement-view-all = Vedi tutti gli annunci
announcement-view-command-description = Vedi gli annunci del server

## Community · Identity and social platforms

identity-admission-bind-rich = { $platform }: { $command }
identity-admission-instructions = Scegli una piattaforma e invia il suo comando al bot della comunità:
identity-admission-pending = Il collegamento esistente non può ancora essere verificato. Riprova più tardi o collega un account di un’altra piattaforma.
identity-admission-reconnect = Riconnettiti al server dopo il collegamento.
identity-admission-required = Collega un account della comunità per entrare.
identity-admission-revoked = Non hai più un collegamento valido con la comunità. Riconnettiti per ricevere le istruzioni.
identity-admin-binding-not-found = Il collegamento selezionato non esiste più.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Il collegamento { $arg0 } selezionato di { $arg1 } è stato rimosso.
identity-admin-command-description = Controlla o revoca i collegamenti di identità esterni
identity-admin-help = /bindadmin lookup <giocatore-o-UUID>
    /bindadmin unlink <binding-id> confirm
identity-admin-lookup-empty = Non sono stati trovati collegamenti per questo giocatore.
identity-admin-lookup-title = Collegamenti di { $arg0 }:
identity-binding-community = Comunità n. { $arg0 }
identity-binding-info = Collegato il: { $arg0 }
    ID del collegamento: { $arg1 }
identity-binding-label-rich = { $kind ->
    [community] { $platform } · { $account } · { $community }
   *[account] { $platform } · { $account }
    }
identity-binding-not-owned = Il collegamento selezionato non esiste più o non ti appartiene.
identity-binding-reference-rich = { $label } · #{ $reference }
identity-binding-unlinked = Il collegamento { $arg0 } selezionato è stato rimosso.
identity-binding-unnamed = Account senza nome
identity-bindings-empty = Non hai collegato account di piattaforme esterne.
identity-bindings-title = Account collegati
identity-code-cancelled = Il codice di collegamento { $arg0 } inutilizzato è stato annullato.
identity-code-copy = Clicca per copiare il comando di collegamento completo
identity-code-expiry = Validità del codice: { $arg0 ->
    [one] { $arg0 } minuto
   *[other] { $arg0 } minuti
    }
identity-code-not-pending = Non ci sono codici di collegamento { $arg0 } inutilizzati.
identity-code-redeem-rich = Invia al bot della tua comunità { $platform }: { $command }
identity-command-description = Collega Minecraft a un'identità di una piattaforma esterna
identity-community-join-rich = Unisciti a una comunità: { $link }
identity-help = Comandi per collegare l'identità:
    /bind qq — genera rapidamente un comando di collegamento QQ
    /bind <piattaforma> — genera un codice monouso
    /bind list — mostra i collegamenti esistenti
    /bind cancel <piattaforma> — annulla un codice inutilizzato
    /bind unlink — Scegli un collegamento da rimuovere
identity-notify-linked = La tua identità { $arg0 } è collegata. Usa /bind list per visualizzare e gestire i tuoi collegamenti.
identity-notify-unlinked = Il collegamento della tua identità { $arg0 } è stato rimosso.
identity-platform-none = nessuna
identity-platforms = Piattaforme supportate: { $arg0 }
identity-storage-error = L'operazione sul database dei collegamenti non è riuscita. Contatta un amministratore.
identity-unavailable = Il collegamento delle identità non è disponibile. Contatta un amministratore.
identity-unlink-action = [Scollega]
identity-unlink-hint = Rimuovi solo questo collegamento selezionato.
identity-unsupported-platform = Piattaforma non supportata: { $arg0 }
social-admin-conversations-empty = Nessuna conversazione osservata per { $arg0 }.
social-admin-conversations-title = Conversazioni osservate per { $arg0 }:
social-admin-help = /social platforms · /social status [piattaforma] · /social reload [piattaforma] · /social conversations <piattaforma>
social-admin-metrics = In entrata { $arg0 } / tentativi { $arg1 } · in uscita { $arg2 } / contropressione { $arg3 } · errori { $arg4 }
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Piattaforma social { $arg0 } ricaricata.
social-admin-reloaded-all = Tutte le piattaforme social sono state ricaricate.
social-admin-status = { $arg0 } · { $arg1 } · generazione { $arg2 } · conversazioni { $arg3 }
social-admin-title = Piattaforme social
social-admin-unknown = Piattaforma social sconosciuta: { $arg0 }
social-binding-already-linked = Il tuo account è già collegato a { $arg0 }. Invia { $arg1 } per vedere il profilo.
social-binding-confirmation-expired = La conferma è scaduta o l’account collegato è cambiato. Richiedi una nuova conferma:
social-binding-code-invalid = Questo codice non è valido o è scaduto. Riconnettiti oppure esegui { $arg0 } nel gioco.
social-binding-external-in-use = Questa identità è già collegata a un altro giocatore Minecraft.
social-binding-help = Connettiti al server Minecraft e invia qui il comando di collegamento completo della schermata di accesso. Se sei già in gioco, ottienilo con { $arg0 }.
social-binding-identity-missing = Questo messaggio non contiene un’identità membro attendibile, quindi il collegamento non è disponibile.
social-binding-link-completed = Il tuo account è stato collegato. Riconnettiti per proseguire con l’accesso.
social-binding-linked = Il tuo account di gioco { $arg0 } è collegato. Riconnettiti per proseguire con l’accesso.
social-binding-membership-required = Unisciti a una comunità collegata su questa piattaforma, poi riprova a collegare l’account.
social-binding-membership-unavailable = Al momento non è possibile verificare l’appartenenza alla comunità. Riprova più tardi o contatta l’amministrazione del server.
social-binding-not-bound = Non hai ancora collegato un account di gioco.
social-binding-platform-mismatch = Questo codice di verifica è stato emesso per un’altra piattaforma.
social-binding-player-scope-in-use = Questo giocatore Minecraft ha già un’altra identità collegata nello stesso ambito di account della piattaforma.
social-binding-rate-limited = Troppi tentativi non riusciti. Riprova tra circa { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minuti
    }.
social-binding-unavailable = Il collegamento dell’identità non è temporaneamente disponibile.
social-binding-unlink-completed = Il tuo account è stato scollegato. Senza un altro collegamento valido alla comunità devi collegare nuovamente un account prima di entrare.
social-binding-unlink-prompt = Questo account della piattaforma verrà scollegato da { $arg0 }. Senza un altro collegamento valido alla comunità perderai l’accesso al server e verrai disconnesso se sei online. Per confermare, invia:
social-binding-unlinked = Scollegato dal giocatore Minecraft { $arg0 }.
social-command-description = Gestisci gli adattatori delle piattaforme social
social-command-invalid-arguments = Gli argomenti non sono validi. Usa:
social-command-usage = Questo comando non richiede argomenti. Invia:
social-crypto-command-description = Gestisci la verifica dei dispositivi cifrati Matrix
social-crypto-comparison-not-ready = Il confronto SAS non è ancora pronto.
social-crypto-device = { $arg0 } / { $arg1 }
    Ed25519: { $arg2 }
social-crypto-failed = Verifica del dispositivo non riuscita. Controlla il registro del server.
social-crypto-no-active = Nessuna verifica del dispositivo in corso.
social-crypto-not-accepting = Questa verifica non è in attesa di accettazione.
social-crypto-not-running = Matrix non è in esecuzione.
social-crypto-requested = Verifica richiesta: { $arg0 }
social-crypto-state = { $arg0 ->
    [initializing] Inizializzazione
    [verified] Verificato
    [unverified] Non verificato
    [no_cross_signing] Firma incrociata disabilitata
    [requested] Richiesto
    [ready] Pronto
    [started] Avviato
    [comparison] In attesa di confronto
    [waiting] In attesa di completamento
    [done] Completato
    [cancelled] Annullato
   *[unknown] Sconosciuto
    [valid] Chiave valida
    [cross_signed] Con firma incrociata
    [not_cross_signed] Senza firma incrociata
    [blocked] Bloccato
    [invalid] Chiave non valida
    }
social-crypto-status = Verifica: { $arg0 }
    Versione del backup delle chiavi: { $arg1 }
social-crypto-target = { $arg0 } / { $arg1 }: { $arg2 } · { $arg3 }
social-crypto-transaction = { $arg0 }: { $arg1 } / { $arg2 } · { $arg3 }
social-crypto-transaction-required = Specifica l’ID completo della transazione di verifica corrente.
social-crypto-unavailable = Non disponibile
social-crypto-updated = Verifica aggiornata; consulta matrixcrypto status.
social-crypto-usage = matrixcrypto status [id-utente id-dispositivo]
    matrixcrypto verify <id-utente> <id-dispositivo>
    matrixcrypto accept|confirm|cancel <id-transazione>
social-duration-days = { $arg0 ->
        [one] { $arg0 } giorno
       *[other] { $arg0 } giorni
    } { $arg1 ->
        [one] { $arg1 } ora
       *[other] { $arg1 } ore
    } { $arg2 ->
        [one] { $arg2 } minuto
       *[other] { $arg2 } minuti
    }
social-duration-hours = { $arg0 ->
        [one] { $arg0 } ora
       *[other] { $arg0 } ore
    } { $arg1 ->
        [one] { $arg1 } minuto
       *[other] { $arg1 } minuti
    }
social-duration-minutes = { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minuti
    }
social-help-command-description = Mostra questo elenco di comandi
social-link-command-description = Collega il tuo account Minecraft con un codice di gioco
social-link-command-parameters = <codice>
social-online-command-description = Mostra i giocatori online
social-profile-afk = Online · AFK
social-profile-avatar-alt = Volto Minecraft
social-profile-command-description = Mostra il tuo profilo; i membri effettivi cercano per nome, menzione o risposta
social-profile-command-parameters = [giocatore]
social-profile-first-joined-label = Primo accesso
social-profile-language-label = Lingua
social-profile-last-seen-label = Ultima presenza
social-profile-member-required = Solo i membri, i moderatori o i custodi collegati possono vedere il profilo di un altro giocatore.
social-profile-offline = Offline
social-profile-online = Online · attivo
social-profile-play-time-label = Tempo di gioco
social-profile-player-label = Giocatore
social-profile-player-not-found = Giocatore Minecraft non trovato.
social-profile-presence-label = Presenza
social-profile-target-ambiguous = Scegli un solo obiettivo: un nome giocatore, una menzione o l’autore della risposta.
social-profile-target-unbound = L’identità menzionata o a cui hai risposto non è collegata a Minecraft.
social-profile-uuid-label = UUID
social-query-metric = { $arg0 }
social-query-no-players = Nessun giocatore è attualmente online.
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (Assente)
   *[active] { $arg0 }
    }
social-query-online-summary = { $arg0 ->
    [one] Al momento è online { $arg0 } giocatore.
   *[other] Al momento sono online { $arg0 } giocatori.
    }{ $arg1 ->
    [0] { "" }
   *[other] { " " }In AFK: { $arg1 }.
    }
social-query-players-label = Giocatori
social-query-population = Online { $arg0 }/{ $arg1 } · Attivi { $arg2 } · Assente { $arg3 }
social-query-unknown = Comando sconosciuto. Invia { $arg0 } per vedere i comandi disponibili.
social-query-uptime-label = Tempo di attività
social-query-version-label = Versione
social-reply-hidden = Questa risposta è stata nascosta dalle regole di sicurezza dei contenuti del server.
social-reply-unavailable = Al momento non è possibile mostrare il risultato. Verifica le modifiche all’account con il comando del profilo prima di richiederle di nuovo.
social-state-disabled = disabilitata
social-state-failed = errore
social-state-ready = pronta
social-state-starting = avvio
social-state-stopped = arrestata
social-status-command-description = Mostra giocatori, TPS, MSPT e attività
social-title-command-help = Guida ai comandi
social-title-content-hidden = Contenuto nascosto
social-title-player-profile = Profilo giocatore
social-title-server-status = Stato del server
social-unlink-command-description = Rimuovi questo collegamento dopo la conferma
social-unlink-command-parameters = [{ social-unlink-confirmation } <conferma>]
social-unlink-confirmation = conferma

## Community · AI and knowledge

ai-busy = L’IA sta gestendo troppe richieste; riprova tra poco
ai-cleared = Cronologia della conversazione IA cancellata
ai-command-description = Chiedi all’assistente IA nel gioco
ai-disabled = L’assistente IA è disattivato o non configurato
ai-knowledge-curation-failed = Organizzazione della conoscenza non riuscita: { $arg0 }
ai-knowledge-curation-finished = Organizzazione conclusa: applicati { $arg0 } · saltati { $arg1 } · falliti { $arg2 } · quarantena { $arg3 } · elaborati { $arg4 }
ai-knowledge-curation-started = Organizzazione della conoscenza avviata.
ai-knowledge-disabled = La base di conoscenza è disattivata.
ai-knowledge-index-dirty = È necessario riallineare l’indice; il recupero privato è bloccato fino alla sincronizzazione.
ai-knowledge-last-failure = Ultimo errore: { $arg0 }
ai-knowledge-last-sync = Ultima sincronizzazione: { $arg0 }
ai-knowledge-learning-disabled = L’apprendimento della conoscenza è disattivato.
ai-knowledge-operation-failed = Operazione sulla conoscenza non riuscita: { $arg0 }
ai-knowledge-operation-started = Operazione sulla conoscenza avviata.
ai-knowledge-protected = Il documento { $arg0 } ora è protetto.
ai-knowledge-rollback = Documento { $arg0 } ripristinato come revisione { $arg1 }.
ai-knowledge-status = Conoscenza: { $arg0 ->
        [one] { $arg0 } documento pubblico
       *[other] { $arg0 } documenti pubblici
    } / { $arg1 ->
        [one] { $arg1 } frammento di testo
       *[other] { $arg1 } frammenti di testo
    } · { $arg2 ->
        [one] { $arg2 } documento privato
       *[other] { $arg2 } documenti privati
    } / { $arg3 ->
        [one] { $arg3 } frammento di testo
       *[other] { $arg3 } frammenti di testo
    }
ai-knowledge-sync-failed = Sincronizzazione della conoscenza non riuscita: { $arg0 }
ai-knowledge-sync-started = Sincronizzazione della conoscenza avviata.
ai-knowledge-synchronized = { ai-knowledge-status } · Sincronizzazione completata.
ai-knowledge-unprotected = Il documento { $arg0 } non è più protetto.
ai-learning-failure = Errore di apprendimento: { $arg0 }
ai-learning-status = Apprendimento: candidati { $arg0 } · estrazione { $arg1 } · in coda { $arg2 } · estrazioni scartate { $arg3 } · candidati scartati { $arg4 } · quarantena { $arg5 }
ai-memory-empty = Nessuna memoria privata per { $arg0 }.
ai-memory-list = Memorie private per { $arg0 }: { $arg1 }
ai-memory-not-found = Memoria privata { $arg0 } non trovata.
ai-memory-removed = Eliminazione definitiva per { $arg1 }: { $arg0 ->
        [one] { $arg0 } memoria privata
       *[other] { $arg0 } memorie private
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Giocatore sconosciuto nella cache: { $arg0 }
ai-prompt-too-long = La domanda è troppo lunga (massimo { $arg0 ->
        [one] { $arg0 } carattere
       *[other] { $arg0 } caratteri
    })
ai-reload-failed = Impossibile ricaricare la configurazione IA: { $arg0 }
ai-reloaded = Configurazione IA ricaricata ({ $arg0 })
ai-request-failed = Richiesta IA non riuscita; riprova più tardi
ai-social-command-description = Chiedi all’assistente IA condiviso
ai-social-command-parameters = <domanda>
ai-social-question-required = Inserisci una domanda dopo il comando IA
ai-status-disabled = L’IA è disattivata
ai-status-enabled = IA attiva · provider { $arg0 } · modello { $arg1 } · strumenti { $arg2 }
ai-status-none = nessuno
ai-thinking = Sto pensando…
ai-usage = Uso: /ai <domanda> · /ai clear · /ai status
ai-worker-status = Attività IA: in esecuzione { $arg0 } · in coda { $arg1 } · scartate { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Congratulazioni!</bold> Ora sei un <gold>Membro</gold>. Ora hai accesso a più comandi e funzionalità, inclusa la stampa semplice di schemi.</green>
governance-active-days-30 = Almeno 2 giorni attivi negli ultimi 30 (attualmente { $arg0 })
governance-active-days-60 = Almeno 6 giorni attivi negli ultimi 60 (attualmente { $arg0 })
governance-active-header = ─── Requisiti di membro attivo ───
governance-active-member = Attualmente membro
governance-active-member-age = Membro da almeno 30 giorni
governance-active-playtime = Almeno { $arg1 } di gioco effettivo in 60 giorni (attualmente { $arg0 })
governance-activity-action = { $arg0 ->
    [confirm] Conferma il sostegno
    [vote] Leggi e vota
    *[details] Mostra dettagli
    }
governance-activity-all = Mostra tutte ({ $arg0 })
governance-activity-closed = Concluso
governance-activity-confirm-petition = Sostenere questa petizione? Al raggiungimento dei sostegni richiesti inizierà una votazione di revoca.
governance-activity-needed = { $arg0 ->
    [one] Serve ancora { $arg0 } sostenitore
    *[other] Servono ancora { $arg0 } sostenitori
    }
governance-activity-proposal = { $arg0 ->
    [appointment] Nominare { $arg1 } come moderatore
    [removal] Revocare il ruolo di moderatore a { $arg1 }
    [petition] Petizione per revocare { $arg1 }
    *[ban] Votazione per bandire { $arg1 }
    }
governance-activity-remaining = { $arg1 ->
    [days] Tempo restante: { governance-days }
    [hours] Tempo restante: { governance-hours }
    *[minutes] Tempo restante: { governance-minutes }
    }
governance-activity-status = { $arg0 ->
    [available] Puoi votare
    [voted] Voto registrato
    [join] Puoi sostenere
    [joined] Sostegno registrato
    [closed] Concluso
    *[view] Sola consultazione
    }
governance-already-moderator = Sei già moderatore.
governance-application-status = { $arg0 ->
    [open] È in corso una votazione di nomina
    [pending] Approvata · nomina in attesa
    [blocked] Requisiti non ancora soddisfatti
   *[ready] Puoi candidarti
    }
governance-ballot-recorded = Il tuo voto anonimo è stato registrato come { $arg0 }. Puoi cambiarlo prima della scadenza.
governance-ban-details = Proposto da { $arg0 } · Durata { $arg1 } · Motivo: { $arg2 }
governance-ban-suffix = · Durata { $arg0 } · Motivo: { $arg1 }
governance-choice-abstain = Astensione
governance-choice-no = No
governance-choice-yes = Sì
governance-command-description = Mostra stato di governance e votazioni
governance-custodian-no-nomination = I custodi non partecipano alle elezioni dei moderatori.
governance-custodian-no-resign = La procedura di dimissioni dei moderatori non si applica ai custodi.
governance-days = { $arg0 ->
        [one] { $arg0 } giorno
       *[other] { $arg0 } giorni
    }
governance-error-ban-already-active = Questo giocatore ha già un ban attivo.
governance-error-ban-proposer-ineligible = Solo i membri attivi possono proporre un voto di ban.
governance-error-ban-self = Non puoi proporre un voto per bannare te stesso.
governance-error-candidate-ineligible = Un candidato moderatore deve essere membro attivo.
governance-error-nomination-not-open = Non hai candidature aperte.
governance-error-not-voter = Non sei nell'elenco degli elettori fissato per questo voto.
governance-error-pause-already-active = La promozione di questo giocatore è già sospesa.
governance-error-pause-already-member = Questo giocatore è già membro; la promozione non può essere sospesa.
governance-error-pause-end-invalid = La sospensione della promozione deve terminare in futuro.
governance-error-player-unknown = Questo giocatore non ha ancora un profilo di governance.
governance-error-removal-petition-closed = Questa petizione è conclusa o è stata sostituita.
governance-error-removal-requires-petition = Un voto di revoca richiede abbastanza sostenitori.
governance-error-removal-self = Non puoi sostenere la tua revoca.
governance-error-removal-sponsor-ineligible = Solo i membri attivi possono sostenere una revoca.
governance-error-removal-sponsors-too-few = Servono almeno altri due membri attivi.
governance-error-removal-target-not-moderator = Questo giocatore non è più un moderatore revocabile.
governance-error-removal-vote-open = È già aperto un voto di revoca per questo moderatore.
governance-error-termination-reason-invalid = Questo motivo di chiusura non si applica al voto.
governance-error-vote-already-open = È già aperto un voto di questo tipo per il giocatore.
governance-error-vote-closed = Questo voto è chiuso o terminato.
governance-error-vote-cooldown = Non sono ancora passati tre giorni dal mancato quorum.
governance-error-vote-subject-unknown = Il soggetto non ha ancora un profilo di governance.
governance-error-vote-termination-closed = Dopo la scadenza non si può più ritirare il voto.
governance-error-vote-unknown = Voto non trovato.
governance-error-voters-too-few = Servono almeno due membri attivi per avviare un voto.
governance-failure-majority = I sì non superano la metà delle schede
governance-failure-minimum-yes = Troppi pochi sì
governance-failure-none = Nessun motivo registrato
governance-failure-quorum = Quorum non raggiunto
governance-failure-two-thirds = I sì non raggiungono due terzi dei voti non astenuti
governance-history-header = ─── Risultati recenti ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
governance-hours = { $arg0 ->
        [one] { $arg0 } ora
       *[other] { $arg0 } ore
    }
governance-inactive-removal = { $arg0 } ha lasciato automaticamente il ruolo di moderatore dopo 180 giorni senza accessi.
governance-invalid-choice = Scegli yes, no o abstain.
governance-invalid-duration = Durata non valida. Usa per esempio 30m, 12h o 3d.
governance-kind-appointment = Nomina
governance-kind-ban = Ban
governance-kind-removal = Revoca
governance-legacy-ban-kick = Una precedente decisione della comunità impedisce il tuo accesso. Durata: { $arg0 }. Motivo: { $arg1 }
governance-menu-already-sponsored = Già sostenuta
governance-menu-application = Stato della candidatura
governance-menu-completed = Conclusi
governance-menu-confirm = Conferma
governance-menu-confirm-nominate = La candidatura avvia una votazione per la tua nomina a moderatore.
governance-menu-confirm-resign = Dimettersi da moderatore?
governance-menu-confirm-sponsor = Sostenere questa richiesta di rimozione?
governance-menu-confirm-withdraw = Ritirare la candidatura a moderatore?
governance-menu-custodian-duties = Manutenzione e amministrazione del server.
governance-menu-identity = Ruolo
governance-menu-identity-hint = Requisiti di promozione e registro attività.
governance-menu-join-petition = Sostieni la richiesta di revoca
governance-menu-loading = Caricamento stato della governance…
governance-menu-matters = Questioni della comunità
governance-menu-moderator-duties = Moderazione e assistenza ai giocatori.
governance-menu-nominate = Candidati a moderatore
governance-menu-offline = Disconnesso
governance-menu-ongoing = In corso
governance-menu-online = Connesso
governance-menu-pause = Sospendi la promozione
governance-menu-pause-appeal = Canale di ricorso
governance-menu-pause-duration = Durata (es. 2d)
governance-menu-pause-reason = Motivo
governance-menu-pause-until = Scadenza
governance-menu-pending-application = Approvata · applicazione in attesa
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } sostenitori
governance-menu-petitions = Richieste di rimozione
governance-menu-player-name = Nome del giocatore
governance-menu-players = Gestione giocatori
governance-menu-refresh = Aggiorna
governance-menu-related = Registri correlati recenti
governance-menu-related-matters = Mostra questioni correlate
governance-menu-resign = Dimettiti da moderatore
governance-menu-resume = Rimuovi la sospensione
governance-menu-resume-reason = Motivo della rimozione
governance-menu-staff-only = Solo moderatori e custodi possono gestire i giocatori.
governance-menu-staff-search = Cerca personale
governance-menu-subject-filter = { $arg0 } · Rimuovi filtro ×
governance-menu-team = Personale pubblico
governance-menu-team-empty = Nessun membro corrispondente
governance-menu-title = Governo della comunità
governance-menu-withdraw = Ritira candidatura
governance-minutes = { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minuti
    }
governance-no-active-ban = Nessun ban attivo
governance-no-active-pause = Nessuna sospensione della promozione attiva
governance-nomination-withdrawn = Candidatura ritirata. Il risultato sarà annunciato automaticamente.
governance-none = Nessuno
governance-not-member = Non è membro
governance-not-moderator = Non sei moderatore al momento.
governance-operation-error = Operazione di governance fallita. Riprova.
governance-own-choice-closed = La tua scelta: { $arg0 }
governance-own-choice-open = La tua scelta: { $arg0 }; puoi cambiarla prima della scadenza.
governance-own-not-voted = Potevi votare, ma non hai votato.
governance-own-not-voted-hint = Non hai ancora votato. Apri Governance e seleziona questa votazione.
governance-own-not-voter = Non sei nell'elenco degli elettori di questo voto.
governance-own-voted = Hai votato
governance-pause-cleared = Sospensione della promozione rimossa per { $arg0 }.
governance-pause-details = Sospensione: { $arg0 } · Fino al { $arg1 } · Ricorso: { $arg2 }
governance-pause-fields-required = Indica un motivo e una via di ricorso.
governance-pause-not-active = { $arg0 } non ha una sospensione della promozione attiva.
governance-pause-notice = La tua promozione è sospesa. Motivo: { $arg0 } · Fino al { $arg1 } · Ricorso: { $arg2 }
governance-pause-set = Promozione di { $arg0 } sospesa fino al { $arg1 }.
governance-petition-already-sponsored = Hai già sostenuto questa revoca ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } sostenitori · Fino al { $arg3 }
governance-petition-started = Richiesta di revoca di { $arg0 }: { $arg1 }/{ $arg2 } sostenitori; fino al { $arg3 }.
governance-petitions-header = ─── Richieste di revoca ───
governance-player-first-join = Primo ingresso: { $arg0 }
governance-player-header = ─── { $arg0 } · Profilo di governance ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Ultimo accesso: { $arg0 }
governance-player-member-since = Membro dal: { $arg0 }
governance-player-never-joined = Il giocatore { $arg0 } non è mai entrato nel server.
governance-promotion-account-age = Primo ingresso almeno 3 giorni fa
governance-promotion-header = ─── Requisiti di promozione ───
governance-promotion-play-days = Gioco effettivo in almeno 2 giorni (attualmente { $arg0 })
governance-promotion-playtime = Almeno 8 ore di gioco effettivo (attualmente { $arg0 })
governance-resign-failed = Dimissioni non riuscite. Contatta un custode.
governance-resigned = Incarico di moderazione terminato su richiesta: { $arg0 }.
governance-result-announcement = Voto #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = Respinto ({ $arg0 })
governance-result-open = In corso
governance-result-passed = Approvato
governance-result-tally = { $arg0 } · Elettori { $arg1 }, partecipanti { $arg2 }, sì { $arg3 }, no { $arg4 }, astenuti { $arg5 }
governance-result-terminated = Terminato ({ $arg0 })
governance-resume-notice = La sospensione della tua promozione è stata rimossa. Motivo: { $arg0 }
governance-resume-reason-required = Indica il motivo della rimozione.
governance-role-lookup-error = Impossibile verificare il ruolo di moderatore. Contatta un custode.
governance-seconds = { $arg0 ->
        [one] { $arg0 } secondo
       *[other] { $arg0 } secondi
    }
governance-storage-error = Errore nei dati di governance. Contatta un custode.
governance-termination-inactive = Moderatore rimosso per lunga inattività
governance-termination-ineligible = Il candidato ha perso lo stato di membro attivo
governance-termination-resigned = Il moderatore si è dimesso
governance-termination-unknown = Nessun motivo registrato
governance-termination-withdrew = Il candidato ha ritirato la candidatura
governance-vote-electorate = Elettori { $arg0 } · Quorum { $arg1 }
governance-vote-finalized = Concluso: { $arg0 }
governance-vote-header = ─── Voto #{ $arg0 } ───
governance-vote-in-progress = In corso · { $arg0 } partecipanti; i dettagli saranno pubblicati alla chiusura.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · Scadenza { $arg3 } · Elettori { $arg4 } · Partecipanti { $arg5 } · Quorum { $arg6 }{ $arg7 }
governance-vote-period = Aperto { $arg0 } · Chiusura { $arg1 }
governance-vote-started = Voto di { $arg1 } #{ $arg0 } avviato: { $arg2 }; elettori { $arg3 }, quorum { $arg4 }, chiusura { $arg5 }. Apri Governance per i dettagli.
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Voti anonimi in corso ───

## World · Plots

plot-admin-created = Registrato { $arg0 } per { $arg1 } · { $arg2 }
plot-allowed = attivo
plot-area-changed = I confini sono cambiati. Aggiorna e conferma di nuovo.
plot-area-confirm = Sostituire i confini di «{ $arg0 }» con la selezione ({ $arg1 ->
        [one] { $arg1 } cella edificabile
       *[other] { $arg1 } celle edificabili
    })? Le costruzioni restano.
plot-area-hint = Seleziona gli angoli con i clic sinistro/destro dell’ascia di legno o //pos1 e //pos2; torna per salvare.
plot-area-replace-save = Sostituisci confini e salva
plot-area-save = Salva confini
plot-area-saved = Confini di «{ $arg0 }» salvati.
plot-atmosphere-saved = Ambiente salvato.
plot-command-blocked = La protezione ha bloccato questo comando. Usa /fill o /setblock direttamente nel tuo lotto; chiedi allo staff di verificare comandi /execute complessi.
plot-command-description = Gestisci lotti edificabili
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } creato · { $arg1 }
        [feminine] { -plot } { $arg0 } creata · { $arg1 }
       *[other] { -plot } { $arg0 } creato · { $arg1 }
    }
plot-deleted = Terreno rilasciato; la costruzione resta.
plot-denied = disattivo
plot-error-admin-owner-only = Modificare membri con capacità gestionali richiede la gestione di membri e permessi.
plot-error-area-disconnected = Il terreno deve formare una zona connessa.
plot-error-args = Argomenti non validi o mancanti. Usa /plot help.
plot-error-busy = Salvataggio in corso. Riprova più tardi; questa operazione non è stata inviata.
plot-error-flag = Permesso sconosciuto. Scegli un’operazione dalla lista dei permessi.
plot-error-id = ID del lotto inesistente o ambiguo. Usa /plot list.
plot-error-member-missing = Questo giocatore non è più un membro.
plot-error-member-required = Per creare un lotto serve il ruolo di membro.
plot-error-name = Il nome del progetto deve contenere da 1 a 48 caratteri.
plot-error-notice-text = Gli avvisi consentono fino a { $arg0 ->
        [one] { $arg0 } carattere
       *[other] { $arg0 } caratteri
    }, senza limiti di righe o di lunghezza per riga; i caratteri di controllo non sono ammessi.
plot-error-overlap = L’area edificabile si sovrappone a un altro lotto.
plot-error-owner-member = proprietario del lotto · Permessi del gruppo
plot-error-owner-only = disattivo · Gestione
plot-error-permission-changed = Permessi, membri o regole ereditate cambiati. Controlla di nuovo le modifiche.
plot-error-permission-grant = Non puoi concedere capacità gestionali che non possiedi.
plot-error-permission-owner-scope = Solo il proprietario, il proprietario del terreno superiore o lo staff possono modificare i permessi del proprietario.
plot-error-player-unknown = Giocatore sconosciuto. Controlla nome o UUID.
plot-error-release-owner-only = disattivo · Elimina lotto
plot-error-selection = Segna due angoli con //pos1 e //pos2.
plot-error-selection-changed = La selezione è cambiata. Conferma di nuovo.
plot-error-staff-only = Solo i moderatori o i custodi possono gestire o registrare i lotti di altri giocatori.
plot-error-storage = Operazione sul lotto non riuscita. Riprova più tardi o contatta lo staff.
plot-error-subplot-children = Rilascia i sottolotti prima del lotto principale.
plot-error-subplot-depth = I sottolotti possono avere un solo livello.
plot-error-subplot-disconnected = Le celle selezionate non sono collegate. Dividi ogni parte separatamente.
plot-error-subplot-overlap = La selezione si sovrappone a un altro sottolotto.
plot-error-subplot-owner = Non hai il permesso per questa operazione sul sottoappezzamento.
plot-error-subplot-parent = Segna entrambi gli angoli nelle celle costruite del lotto principale.
plot-error-subplot-supervisor-member = Il proprietario del lotto principale ha già la supervisione e non richiede un ruolo membro.
plot-error-time-format = Inserisci un'ora HH:mm (00:00–23:59).
plot-error-transfer-owner-only = disattivo · Trasferisci proprietà
plot-error-transfer-self = Sei già il proprietario di questo lotto.
plot-flag-set = { $arg0 }: { $arg1 }.
plot-help = Guida ai lotti
plot-here = Qui: { $arg0 } · { $arg1 }
plot-invited = Accesso concesso a { $arg0 }.
plot-item-summary = Area costruita { $arg0 } blocco² · { $arg1 ->
        [one] { $arg1 } membro
       *[other] { $arg1 } membri
    }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } blocco²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Non inviato
plot-member-draft-reset = Cancella impostazioni categoria
plot-member-draft-role = Ruolo: { $arg0 }
plot-member-draft-submit = Controlla e conferma
plot-member-draft-title = Bozza membro · { $arg0 }
plot-member-permissions = Permessi
plot-member-removed = Membro rimosso dal progetto.
plot-menu-access = Membri e permessi
plot-menu-add-member = Aggiungi membro
plot-menu-atmosphere = Ambiente
plot-menu-clear = Sereno
plot-menu-confirm-delete = Eliminare la registrazione di { $arg0 }? Protezione, permessi dei membri e impostazioni saranno rimossi definitivamente. Le costruzioni resteranno.
plot-menu-confirm-subplot-transfer = Trasferire { $arg0 } a { $arg1 }? Il proprietario del lotto principale mantiene la supervisione.
plot-menu-confirm-transfer = Trasferire la gestione di { $arg0 } a { $arg1 }? Perderai l'accesso; la paternità delle opere resta invariata.
plot-menu-create = Crea lotto
plot-menu-create-for-player = Registra un lotto per questo giocatore
plot-menu-custom-time = Ora personalizzata
plot-menu-delete = Elimina registrazione del terreno
plot-menu-flag-container = Aprire i contenitori
plot-menu-flag-entity = Interagire con entità
plot-menu-follow-parent = Segui il lotto principale
plot-menu-follow-world = Segui il mondo
plot-menu-group-collaborator = Collaboratori
plot-menu-group-member = Accesso dei membri
plot-menu-group-newcomer = Accesso dei nuovi
plot-menu-here = Posizione attuale
plot-menu-manage = Gestisci lotti dei giocatori
plot-menu-managed-empty = Questo giocatore non ha ancora lotti.
plot-menu-managed-title = Lotti di { $arg0 }
plot-menu-members = Membri ({ $arg0 })
plot-menu-name = Nome del progetto
plot-menu-owner = Proprietario: { $arg0 }
plot-menu-parent-supervisor = Proprietario del lotto principale: { $arg0 } · supervisione
plot-menu-player = Nome giocatore o UUID
plot-menu-rain = Precipitazioni
plot-menu-remove-member = Rimuovi membro
plot-menu-rename = Rinomina progetto
plot-menu-set-role = Imposta come { $arg0 }
plot-menu-settings = Impostazioni del progetto
plot-menu-submit = Applica
plot-menu-subplot-of = Parte di { $arg0 }
plot-menu-subplot-parent = Torna al lotto principale: { $arg0 }
plot-menu-subplots = Sottolotti ({ $arg0 })
plot-menu-time = Ora
plot-menu-time-input = Ora (HH:mm)
plot-menu-transfer = Trasferisci il lotto
plot-menu-weather = Meteo
plot-no-here = Qui non c’è alcun lotto protetto.
plot-notice-board = Bacheca del lotto
plot-notice-cleared = Bacheca cancellata
plot-notice-edit = Modifica la bacheca
plot-notice-input = Fino a { $arg0 ->
        [one] { $arg0 } carattere
       *[other] { $arg0 } caratteri
    }; più righe consentite; lascia vuoto per cancellare
plot-notice-read = Leggi l’avviso completo
plot-notice-saved = Bacheca aggiornata
plot-permission-all-actions = Tutte le azioni
plot-permission-animal = Curare, addomesticare e legare animali
plot-permission-break = Rompere blocchi
plot-permission-bucket = Usare secchi per liquidi e creature
plot-permission-category-build = Costruzione e modifiche
plot-permission-category-entity = Entità
plot-permission-category-interaction = Interazione con i blocchi
plot-permission-category-management = Gestione
plot-permission-category-storage = Depositi e oggetti
plot-permission-change-children = Sottoterreni interessati: { $arg0 }
plot-permission-change-confirm = Applica modifiche
plot-permission-change-granted = Concessi: { $arg0 }
plot-permission-change-members = Membri interessati: { $arg0 }
plot-permission-change-more = Altri { $arg0 }
plot-permission-change-no-effect = I permessi effettivi non cambiano. Le impostazioni esplicite prevalgono sull’ereditarietà futura.
plot-permission-change-none = Nessuno
plot-permission-change-revoked = Revocati: { $arg0 }
plot-permission-change-subject = Destinatario: { $arg0 }
plot-permission-change-title = Conferma modifiche ai permessi
plot-permission-create-subplot = Crea sottoappezzamento
plot-permission-decoration = Piazzare e modificare decorazioni
plot-permission-default-status = Predefinito ({ $arg0 })
plot-permission-delete = Elimina lotto
plot-permission-door = Porte e cancelli
plot-permission-entity-damage = Danneggiare entità
plot-permission-entity-storage = Aprire depositi delle entità
plot-permission-harvest = Raccogliere bacche, rampicanti e miele
plot-permission-ignite = Accendere fuochi
plot-permission-item-drop = Lasciare oggetti
plot-permission-item-pickup = Raccogliere oggetti a terra
plot-permission-manage-area = Modificare i confini
plot-permission-manage-members = Gestisci membri
plot-permission-manage-permissions = Gestisci permessi
plot-permission-manage-settings = Modifica impostazioni del lotto
plot-permission-modify = Modificare blocchi e fertilizzare
plot-permission-operation = Azione
plot-permission-personal = Permessi individuali
plot-permission-personal-source = Impostazione individuale
plot-permission-place = Piazzare blocchi e veicoli
plot-permission-reset = Ripristina valori
plot-permission-result = Stato
plot-permission-ride = Cavalcare veicoli e animali
plot-permission-select-state = Scegli stato: { $arg0 }
plot-permission-sign = Modificare cartelli
plot-permission-source-group = Permessi del gruppo
plot-permission-source-local = Impostazione locale
plot-permission-source-owner = Diritto di recupero del proprietario
plot-permission-source-parent = Lotto superiore
plot-permission-source-role = Valore del ruolo
plot-permission-state-default = Predefinito
plot-permission-switch = Pulsanti, leve e pedane
plot-permission-trade = Commerciare con abitanti
plot-permission-transfer = Trasferisci proprietà
plot-permission-workstation = Usare strutture e postazioni di lavoro
plot-pos1 = Primo punto
plot-pos2 = Secondo punto
plot-private = Privato
plot-public = Pubblico
plot-region-adjust = Modifica questo rettangolo
plot-region-apply = Completa modifica del rettangolo
plot-region-cancel = Annulla modifica del rettangolo
plot-region-changed = La selezione è cambiata. Seleziona nuovamente il rettangolo.
plot-region-delete = Elimina questo rettangolo
plot-region-delete-confirm = Rimuovere il rettangolo #{ $arg0 } dalla bozza? Gli edifici restano. I confini cambiano solo dopo il salvataggio; puoi annullare l’azione.
plot-region-edit-hint = Regola questo rettangolo con //pos1 e //pos2, poi salva.
plot-region-finish-first = Prima completa o annulla la modifica del rettangolo in corso.
plot-region-open = Gestisci rettangoli ({ $arg0 })
plot-region-select-hint = Seleziona un rettangolo numerato per metterlo a fuoco ed evidenziarlo. I contorni grigi mostrano questa pagina; l’unione può cambiare i numeri.
plot-renamed = Progetto rinominato.
plot-role-admin = amministratore del lotto
plot-role-collaborator = collaboratore
plot-role-owner = proprietario del lotto
plot-role-parent-owner = proprietario del lotto principale
plot-role-server-staff = staff del server
plot-selection-add = Aggiungi riquadro attuale
plot-selection-clear = Reimposta i due punti
plot-selection-empty = Segna due angoli con //pos1 e //pos2.
plot-selection-fit = Ripristina i confini salvati
plot-selection-incomplete = Un punto è segnato. Segna l'altro; nulla cambia prima dell'invio.
plot-selection-redo = Ripeti ultimo passo
plot-selection-replace = Sostituisci con riquadro attuale
plot-selection-session-hint = Accovacciati+F o /plot edit per tornare. Uscita dopo 20 minuti di inattività.
plot-selection-size = Selezione: { $arg0 } × { $arg1 } × { $arg2 } blocchi (X/Y/Z)
plot-selection-stopped = Selezione terminata. Le bozze restano salvate per ogni lotto.
plot-selection-subtract = Sottrai riquadro attuale
plot-selection-undo = Annulla ultimo passo
plot-subplot-created = Sottolotto { $arg0 } creato · { $arg1 } blocco²
plot-title = Terreni
plot-transferred = Lotto { $arg0 } trasferito a { $arg1 }.
vehicle-command-description = Guida e gestisci veicoli composti da entità di visualizzazione
vehicle-menu-label =
    { $arg0 ->
        [vehicle] Comandi del veicolo
        [driving] Guida e passeggeri
        [maintenance] Manutenzione e accesso
        [public-status] Accesso pubblico
        [private-status] Accesso privato
        [home] Veicoli
        [nearby] Veicoli vicini
        [fleet] Tutti i veicoli accessibili
        [selection] Selezione modello
        [origin] Imposta origine ai miei piedi
        [group] Seleziona gruppo di visualizzazione
        [radius] Raggio di selezione (0–16 m)
        [displays] Esplora entità vicine
        [clear] Cancella selezione e origine
        [capture] Crea modello dalla selezione
        [toggle-selection] Alterna selezione
        [public] Rendi pubblico
        [private] Rendi privato
        [reload] Ricarica modelli dai file
        [leave] Scendi
        [passenger] Sedile passeggero
        [fuel] Rifornisci · tieni carbone
        [repair] Ripara · tieni lingotto di ferro
        [access] Accesso
        [remove] Rimuovi veicolo
        [hud] Mostra HUD di guida
        [hide-preview] Nascondi anteprima 3D
        [service-hint] Avvicinati, ferma il motore e tieni il materiale richiesto.
        [nearby-hint] Avvicinati entro 32 blocchi e lascia il sedile attuale.
        [use-required] Richiede mik.vehicle.use.
        [remove-hint] Ferma il veicolo e fai scendere tutti.
        [reload-hint] Tutti i veicoli devono essere vuoti e spenti. Le bozze restano; riapri i modelli modificati.
        [pick-hint] Mira a un’entità e fai clic destro. Accovacciato + clic destro annulla; scade dopo 30 secondi.
        [spawn-hint] Coordinate del mondo; quattro blocchi davanti per impostazione predefinita. Orientamento secondo lo sguardo orizzontale alla creazione.
        [zoom-in] Ingrandisci
        [zoom-out] Riduci
        [frame] Riposiziona davanti
        [offset] Sposta relativamente
       *[other] { vehicle-command-description }
    }

vehicle-controls = { $arg0 }: W/S e A/D per guidare; rotella per marce o acceleratore; scambia mano per la modalità; accovacciati per uscire. /vehicle
vehicle-dash-assisted = Assistenza alla stabilità
vehicle-dash-automatic = Automatico
vehicle-dash-contact = Appoggio su terreno / acqua: { $arg0 }
vehicle-dash-details = Dettagli
vehicle-dash-down = Scala marcia −
vehicle-dash-drive = Guida
vehicle-dash-energy = Consumo { $arg0 } U/min · autonomia stimata { $arg1 } min / { $arg2 } km
vehicle-dash-engine-start = Avvia motore
vehicle-dash-engine-stop = Spegni motore
vehicle-dash-fuel = Carburante { $arg0 } / { $arg1 } U · { $arg2 }%
vehicle-dash-gear = Marcia { $arg0 }
vehicle-dash-gearbox = Cambio
vehicle-dash-handbrake = Parcheggio / freno a mano: { $arg0 }
vehicle-dash-heading = Direzione { $arg0 }°
vehicle-dash-health = Condizione { $arg0 }% · occupanti { $arg1 } / { $arg2 }
vehicle-dash-input = Acceleratore { $arg0 }% · freno { $arg1 }% · sterzo { $arg2 }%
vehicle-dash-manual = Manuale
vehicle-dash-mode-switch = Passa a { $arg0 }
vehicle-dash-position = Origine { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Ricentra pannello
vehicle-dash-rpm = Giri { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Velocità { $arg0 } km/h · limite { $arg1 }
vehicle-dash-throttle-down = Acceleratore −5%
vehicle-dash-throttle-up = Acceleratore +5%
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Senza assistenza
vehicle-dash-unavailable = Comandi non disponibili; riapri dal posto di guida.
vehicle-dash-up = Sali di marcia +
vehicle-dash-vertical = Quota { $arg0 } m · velocità verticale { $arg1 } m/s
vehicle-error = Operazione sul veicolo non riuscita: { $arg0 }
vehicle-group-changed = La struttura del gruppo è cambiata. Selezionalo di nuovo prima di importare.
vehicle-group-invalid = Il gruppo contiene entità non supportate, gestite, mancanti o troppo lontane. Tutte devono trovarsi in questo mondo entro 32 blocchi.
vehicle-group-limit = Importa al massimo 128 elementi di visualizzazione e 256 membri totali.
vehicle-group-not-found = Nessun gruppo trovato. Seleziona un gruppo, mira un’entità di visualizzazione blocco o oggetto oppure indica un UUID.
vehicle-group-selected = Gruppo selezionato: { $arg0 } entità di visualizzazione · radice { $arg1 }
vehicle-hud-boat = { $arg0 } kn · spinta { $arg1 }% · direzione { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · quota { $arg1 } m · velocità verticale { $arg2 } m/s · gas { $arg3 }% · { $arg4 }
vehicle-hud-status = Carburante { $arg0 }% · condizione { $arg1 }% · { $arg2 }
vehicle-model-center = Centro di massa
vehicle-model-choice = { $arg0 ->
    [CAR] Auto
    [BOAT] Motoscafo
    [PLANE] Aereo
    [BODY] Carrozzeria
    [FRONT_WHEEL] Ruota sterzante
    [WHEEL] Ruota
    [PROPELLER] Elica
    [RUDDER] Timone
    [FIXED] Fisso
    [VERTICAL] Mantieni verticale
    [HORIZONTAL] Mantieni orizzontale
    [CENTER] Verso la visuale
    [NONE] Posa originale
    [THIRDPERSON_LEFTHAND] Terza persona · sinistra
    [THIRDPERSON_RIGHTHAND] Terza persona · destra
    [FIRSTPERSON_LEFTHAND] Prima persona · sinistra
    [FIRSTPERSON_RIGHTHAND] Prima persona · destra
    [HEAD] Testa
    [GUI] Inventario
    [GROUND] Terra
    [LEFT] Sinistra
    [RIGHT] Destra
    [false] Disattivato
    [true] Attivato
    [TEXT_CENTER] Centrato
   *[other] { $arg0 }
    }
vehicle-model-field = { $arg0 ->
    [kind] Tipo di veicolo
    [mass] Massa
    [maximum-speed] Velocità massima (km/h)
    [support-length] Corsa dei supporti (m)
    [grip] Aderenza
    [center-of-mass] Centro di massa
    [inertia] Inerzia di rotazione X/Y/Z
    [engine-torque] Coppia massima
    [engine-idle-rpm] Minimo (RPM)
    [engine-redline-rpm] Limite di giri (RPM)
    [engine-inertia] Inerzia del motore
    [engine-wheel-radius] Raggio ruota (m)
    [engine-final-drive] Rapporto finale
    [engine-reverse-ratio] Rapporto retromarcia
    [engine-ratios] Rapporti avanti
    [engine-shift-seconds] Tempo di cambiata (s)
    [fuel-capacity] Capacità carburante
    [fuel-consumption] Consumo (/s)
    [buoyancy] Galleggiamento
    [wing-area] Superficie alare (m²)
    [lift] Coefficiente di portanza
    [animation] Ruolo animato
    [billboard] Orientamento alla visuale
    [item-transform] Posa dell’oggetto
    [alignment] Allineamento del testo
    [block-light] Luce dei blocchi
    [sky-light] Luce del cielo
    [background] Sfondo (ARGB)
    [line-width] Larghezza del testo
    [opacity] Opacità (0–255)
    [shadow] Ombra del testo
    [see-through] Testo visibile attraverso
    [x] X
    [y] Y
    [z] Z
    [size-x] Larghezza intera X (m)
    [size-y] Altezza intera Y (m)
    [size-z] Lunghezza intera Z (m)
   *[other] { $arg0 }
    }
vehicle-model-field-range = Intervallo ammesso: 0 < valore ≤ { $arg0 }
vehicle-model-front = Fronte del veicolo (+Z)
vehicle-model-front-choice = { $arg0 ->
    [negative-x] Lato −X attuale
    [back] Retro attuale (−Z)
    [positive-x] Lato +X attuale
    [look] Usa la mia visuale orizzontale (arrotondata a 90°)
   *[other] Imposta questo lato come fronte
    }
vehicle-model-front-hint = Scegli la freccia in 3D. La carrozzeria resta ferma; sedili, uscite e punti fisici cambiano assi insieme. Le modifiche a incrementi di 90° sono annullabili.
vehicle-model-handle = { $arg0 ->
    [ROTATE] { $arg1 } { $arg2 } { $arg3 }°
   *[other] { $arg1 } { $arg2 } { $arg3 }
    }
vehicle-model-marker = { $arg0 ->
    [driver] Sedile conducente #{ $arg1 }
    [passenger] Sedile passeggero #{ $arg1 }
    [exit] Uscita #{ $arg1 }
    [colliders] Volume di collisione #{ $arg1 }
    [supports] Punto di supporto #{ $arg1 }
    [center] Centro di massa
    [parts] Parte visiva #{ $arg1 }
   *[other] { $arg1 }
    }
vehicle-model-parameter-group = { $arg0 ->
    [body] Carrozzeria e contatto
    [engine] Motore e trasmissione
    [fuel] Carburante
    [boat] Galleggiamento barca
    [plane] Portanza aereo
    [power] Potenza del motore
   *[other] { $arg0 }
    }
vehicle-model-parameter-help = { $arg0 ->
    [body] Massa, velocità e contatto col suolo. Il centro di massa si regola in 3D. Solo la bozza cambia fino al salvataggio.
    [engine] Giri, coppia, ruote e rapporti. I rapporti avanti devono diminuire; il limite deve superare 1,5 × il minimo.
    [fuel] La capacità usa unità di carburante di gioco; il consumo è al secondo.
    [boat] Il galleggiamento vale per le barche. Gli altri parametri sono conservati.
    [plane] Superficie alare e portanza valgono per gli aerei. Gli altri parametri sono conservati.
    [power] Giri e coppia del tipo attuale. Le impostazioni del cambio per le auto sono conservate.
   *[other] { $arg0 }
    }
vehicle-model-property-help = { $arg0 ->
    [block-light] −1 usa la luce del mondo. Per sostituirla, imposta entrambi i valori tra 0 e 15.
    [sky-light] −1 usa la luce del mondo. Per sostituirla, imposta entrambi i valori tra 0 e 15.
    [background] Intero ARGB con segno, ad esempio −16777216 per nero opaco.
    [line-width] Larghezza riga: 1–2048 pixel.
    [opacity] 0 è trasparente; 255 è opaco.
   *[other] { $arg0 }
    }
vehicle-model-spatial = Apri selezione 3D
vehicle-model-spatial-hint = Clic destro su un indicatore per selezionare o regolare. Accovacciati + clic destro per tornare alle impostazioni. La rotellina cambia il passo di regolazione.
vehicle-model-spawn = Genera questo modello
vehicle-model-add = Aggiungi componente
vehicle-model-add-here = Aggiungi appoggio ai miei piedi
vehicle-model-anchor = Riposiziona davanti
vehicle-model-animation = Animazione
vehicle-model-apply = Applica / conferma
vehicle-model-auto = Ricalcola i limiti di collisione
vehicle-model-block = Blocco
vehicle-model-center-here = Imposta il baricentro ai miei piedi
vehicle-model-center-look = Imposta il baricentro sulla superficie mirata
vehicle-model-check = Controlla struttura e parametri
vehicle-model-checked = Struttura e parametri validi. Il salvataggio controlla anche dati di visualizzazione, veicoli esistenti e collisioni nel mondo.
vehicle-model-clone = Clona modello
vehicle-model-colliders = Volumi di collisione
vehicle-model-copy = Duplica e modifica (i sedili copiati sono per passeggeri)
vehicle-model-corner-cancel = Annulla il volume di collisione in attesa
vehicle-model-corner-first = Segna il primo angolo ai miei piedi
vehicle-model-corner-first-look = Segna il primo angolo sulla superficie mirata
vehicle-model-corner-hint = { vehicle-model-corner-first } → { vehicle-model-corner-second }
vehicle-model-corner-second = Usa i miei piedi come angolo opposto e aggiungi il volume
vehicle-model-corner-second-look = Usa la superficie mirata come angolo opposto
vehicle-model-create = Crea modello
vehicle-model-delete = Elimina modello
vehicle-model-delete-warning = Eliminare definitivamente questo modello? Non è possibile se esistono veicoli che lo usano.
vehicle-model-deleted = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } eliminato
        [feminine] { -vehicle-model } eliminata
       *[other] { -vehicle-model } eliminato
    }
vehicle-model-discard = Scarta bozza
vehicle-model-discard-warning = Scartare tutte le modifiche non salvate? L’azione non può essere annullata.
vehicle-model-discarded = Bozza scartata
vehicle-model-draft = Bozza non salvata
vehicle-model-driver = Posto guida
vehicle-model-driver-here = Aggiungi posto guida ai miei piedi
vehicle-model-edit = Modifica / riprendi bozza
vehicle-model-overview = Riepilogo della bozza
vehicle-model-panel = Torna al pannello di modifica
vehicle-model-editor-help = { $arg0 ->
   *[overview] Scegli una categoria qui sotto, poi un oggetto e uno strumento. Salva e Annulla restano in basso.
    [parts] Gestisci blocchi, oggetti e testi visibili. Seleziona una parte per modificarne contenuto o trasformazione.
    [seats] Seleziona un sedile per impostare posizione e uscita. È richiesto un solo posto di guida.
    [colliders] Seleziona un volume di collisione per modificarne centro e dimensioni complete. Aggiungine uno o adattalo al modello.
    [supports] Seleziona un punto di contatto per spostarlo. Questi punti servono per il supporto a terra o il galleggiamento.
    [parameters] Scegli un gruppo di parametri, poi un campo per modificarne il valore.
    [tools] Regola l’anteprima, seleziona oggetti nel mondo o verifica la bozza. Lo scarto richiede conferma.
    }
vehicle-model-section-count = { $arg0 ->
   *[parts] { vehicle-model-parts } · { $arg1 }
    [seats] { vehicle-model-seats } · { $arg1 }
    [colliders] { vehicle-model-colliders } · { $arg1 }
    [supports] { vehicle-model-supports } · { $arg1 }
    }
vehicle-model-content-action = { $arg0 ->
   *[block] Imposta contenuto del blocco
    [item] Usa oggetto nella mano principale
    [text] Imposta contenuto del testo
    }
vehicle-model-exact-input = Inserisci valori esatti
vehicle-model-axis-value = { $arg0 } · { $arg1 }
vehicle-model-component-details = Dettagli del componente
vehicle-model-selection-unavailable = Oggetto modificato o rimosso; torna indietro e scegli di nuovo
vehicle-model-exit = Posizione d’uscita / regolazione fine
vehicle-model-group = Aggiungi gruppo Axiom dalla mano principale
vehicle-model-hand-invalid = L’oggetto di visualizzazione Axiom non è valido o contiene entità o dati non supportati.
vehicle-model-hand-required = Tieni un oggetto entità di visualizzazione Axiom nella mano principale e riprova a importare.
vehicle-model-here = Usa la posizione attuale dei piedi
vehicle-model-history-empty = Nessuna modifica disponibile in questa direzione della cronologia.
vehicle-model-import = Importa gruppo Axiom dalla mano principale
vehicle-model-item = Oggetto
vehicle-model-library = Modelli salvati · le bozze restano in memoria finché non vengono salvate
vehicle-model-look = Posiziona mirando (chiude il menu; clic destro per confermare)
vehicle-model-look-missing = Nessuna superficie raggiunta. Guarda un blocco del mondo o una parte dell’anteprima entro 32 blocchi.
vehicle-model-manual = Inserisci coordinate locali
vehicle-model-options = Opzioni
vehicle-model-parameters = Parametri fisici e del motore
vehicle-model-parts = Elementi di visualizzazione
vehicle-model-passenger-here = Aggiungi posto passeggero ai miei piedi
vehicle-model-pick = Modifica il componente dell’anteprima che stai guardando
vehicle-model-pick-hint = Apri un’anteprima e guarda un componente blocco o oggetto, oppure seleziona prima un componente nell’editor.
vehicle-model-place-cancelled = Posizionamento annullato: tempo scaduto, stato di modifica cambiato o annullamento manuale.
vehicle-model-place-hint = Posizionamento attivo per 30 secondi. Mira un blocco o l’anteprima; clic destro con la mano principale conferma, accovacciati + clic destro annulla.
vehicle-model-position = Posizione / regolazione fine
vehicle-model-position-hint = Coordinate locali: +Y verso l’alto, +Z verso la parte anteriore. I pulsanti spostano per incrementi; l’inserimento esatto imposta la posizione assoluta.
vehicle-model-preview = Anteprima privata
vehicle-model-preview-failed = Anteprima non disponibile; la bozza è conservata: { $arg0 }
vehicle-model-preview-hint = Anteprima visibile. Apri la selezione 3D per selezionare e regolare direttamente il modello.
vehicle-model-redo = Ripeti
vehicle-model-remove = Rimuovi componente
vehicle-model-remove-warning = Rimuovere questo componente? Gli indici successivi cambieranno; puoi annullare l’operazione.
vehicle-model-rotate = Ruota componente
vehicle-model-rotate-hint = Rotazione locale relativa: X beccheggio, Y imbardata, Z rollio. La posizione resta invariata.
vehicle-model-save = Salva e termina
vehicle-model-save-continue = Salva e continua a modificare
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } salvato
        [feminine] { -vehicle-model } salvata
       *[other] { -vehicle-model } salvato
    }
vehicle-model-scale = Ridimensiona componente
vehicle-model-scale-all = Ridimensiona tutti gli assi { $arg0 } { $arg1 }
vehicle-model-scale-hint = Moltiplica o divide le lunghezze degli assi locali, non le dimensioni dei limiti nel mondo.
vehicle-model-seats = Sedili e uscite
vehicle-model-selected = Importa elementi selezionati
vehicle-model-size = Ridimensiona volume di collisione
vehicle-model-size-hint = Larghezza, altezza e lunghezza complete; il centro resta invariato.
vehicle-model-snap-off = Allineamento alla griglia: disattivo
vehicle-model-snap-on = Allineamento alla griglia: attivo
vehicle-model-step = Cambia passo di regolazione
vehicle-model-supports = Punti d’appoggio a terra
vehicle-model-text = Testo
vehicle-model-title = Gestione modelli dei veicoli
vehicle-model-tools = Anteprima e strumenti
vehicle-model-transform-step = Cambia passo di regolazione
vehicle-model-undo = Annulla
vehicle-result = Veicolo: { $arg0 }
vehicle-selection-info = Selezione: { $arg0 } entità di visualizzazione · gruppo { $arg1 } · origine { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Collisione / ostacolo
vehicle-status-damage = Veicolo gravemente danneggiato
vehicle-status-disabled = Veicolo fuori uso
vehicle-status-engine-off = Motore spento
vehicle-status-engine-on = Motore acceso
vehicle-status-gear-limit = Limite di controllo raggiunto
vehicle-status-grounded = Barca arenata
vehicle-status-low-fuel = Carburante in esaurimento
vehicle-status-manual-only = Passa prima alla modalità manuale
vehicle-status-no-fuel = Carburante esaurito
vehicle-status-overrev = Scalare porterebbe il motore fuori giri
vehicle-status-shifting = Cambio marcia in corso
vehicle-status-stall = Motore in stallo
vehicle-status-stop-first = Fermati prima di invertire la marcia o parcheggiare
vehicle-value-off = Disattivo
vehicle-value-on = Attivo

## World · Elevators, regeneration and spawn

async-regen-already-running = Hai già una rigenerazione in corso; usa /regen status o /regen cancel
async-regen-apply-expired = Non c'è un piano valido; esegui prima /regen preview
async-regen-apply-plan = Applica piano salvato
async-regen-apply-started = Applicazione di { $arg0 } alla selezione salvata: { $arg1 }
async-regen-awaiting-apply = Piano in attesa: { $arg0 } · { $arg1 } · scade tra { $arg2 ->
        [one] { $arg2 } secondo
       *[other] { $arg2 } secondi
    }
async-regen-block-preview-progress = Anteprima blocchi · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk analizzato
       *[other] { $arg1 } chunk analizzati
    } · { $arg2 ->
        [one] { $arg2 } modifica
       *[other] { $arg2 } modifiche
    } · { $arg3 } entità di blocco a rischio
async-regen-block-preview-summary = Anteprima: { $arg0 ->
        [one] { $arg0 } modifica di blocco
       *[other] { $arg0 } modifiche di blocchi
    } per { $arg2 }; rischio di sovrascrittura: { $arg1 ->
        [one] { $arg1 } entità di blocco esistente
       *[other] { $arg1 } entità di blocco esistenti
    }
async-regen-cancelled = Rigenerazione annullata. Le { $arg0 ->
        [one] { $arg0 } modifica
       *[other] { $arg0 } modifiche
    } già applicate restano nel mondo
async-regen-command-description = Rigenerare in modo asincrono una selezione FastAsyncWorldEdit
async-regen-completed = Rigenerazione completata: { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    } modificati in { $arg1 }
async-regen-failed = Rigenerazione interrotta per un errore interno. Le { $arg0 ->
        [one] { $arg0 } modifica
       *[other] { $arg0 } modifiche
    } già applicate restano nel mondo
async-regen-filter-all = tutti i tipi di blocco
async-regen-invalid-blocks = Tipi sconosciuti o non validi come blocchi: { $arg0 }
async-regen-not-running = Non hai una rigenerazione in corso
async-regen-nothing-to-apply = La selezione corrisponde già a questo piano; non c'è nulla da applicare
async-regen-plan-dialog-title = Applica piano di rigenerazione
async-regen-preview-cancelled = Anteprima annullata; nessun dato del mondo è stato modificato
async-regen-preview-started = Anteprima avviata: selezione { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    } · area { $arg1 } chunk · modalità { $arg2 }
async-regen-progress = { $arg0 } · pianificazione { $arg1 }/{ $arg2 } chunk · applicazione: { $arg3 ->
        [one] { $arg3 } modifica
       *[other] { $arg3 } modifiche
    }
async-regen-safety-note = Nulla fuori dalla selezione salvata verrà modificato. Le costruzioni interne possono essere sovrascritte e Vanilla non identifica i blocchi piazzati dai giocatori. Controlla la visualizzazione e crea un backup prima di applicare.
async-regen-scope-blocks = rigenera i blocchi corrispondenti a { $arg0 }
async-regen-selection-required = Completa prima una selezione FastAsyncWorldEdit
async-regen-skipped-ungenerated = Chunk di destinazione mai generati saltati: { $arg0 } chunk. Nessun chunk di destinazione è stato creato o modificato.
async-regen-start-failed = Impossibile avviare la rigenerazione; controlla il registro del server
async-regen-state-applying = applicazione delle modifiche
async-regen-state-cancelled = annullata
async-regen-state-completed = completata
async-regen-state-failed = fallita
async-regen-state-generating = generazione e confronto dei chunk
async-regen-state-preparing = preparazione del mondo sorgente
async-regen-status = { $arg0 } · pianificazione { $arg1 }/{ $arg2 } chunk · applicazione { $arg3 }/{ $arg4 ->
        [one] { $arg4 } modifica
       *[other] { $arg4 } modifiche
    }
async-regen-structure-metadata-applied = Metadati trasferiti: { $arg0 ->
        [one] { $arg0 } inizio di struttura
       *[other] { $arg0 } inizi di strutture
    } e { $arg1 ->
        [one] { $arg1 } riferimento di chunk
       *[other] { $arg1 } riferimenti di chunk
    }; /locate e la generazione legata alle strutture possono riconoscerli
async-regen-upgrade-apply-progress = { $arg0 } · pianificazione { $arg1 }/{ $arg2 } chunk · applicazione: { $arg3 ->
        [one] { $arg3 } blocco
       *[other] { $arg3 } blocchi
    } e { $arg4 ->
        [one] { $arg4 } cella di bioma
       *[other] { $arg4 } celle di bioma
    }
async-regen-upgrade-boundary-note = { $arg0 ->
        [one] { $arg0 } cella di bioma sul confine verrà ignorata per evitare modifiche fuori dalla selezione
       *[other] { $arg0 } celle di bioma sul confine verranno ignorate per evitare modifiche fuori dalla selezione
    }
async-regen-upgrade-completed = Aggiornamento completato: modifiche a { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    } e { $arg1 ->
        [one] { $arg1 } cella di bioma
       *[other] { $arg1 } celle di bioma
    } in { $arg2 }
async-regen-upgrade-preview-progress = Anteprima · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk analizzato
       *[other] { $arg1 } chunk analizzati
    } · { $arg2 ->
        [one] { $arg2 } modifica
       *[other] { $arg2 } modifiche
    } · { $arg3 ->
        [one] { $arg3 } cella di bioma
       *[other] { $arg3 } celle di bioma
    } · { $arg4 ->
        [one] { $arg4 } struttura
       *[other] { $arg4 } strutture
    }
async-regen-upgrade-preview-summary = Anteprima: { $arg0 ->
        [one] { $arg0 } modifica di blocco
       *[other] { $arg0 } modifiche di blocchi
    }, { $arg1 ->
        [one] { $arg1 } cella di bioma
       *[other] { $arg1 } celle di biomi
    }, { $arg2 ->
        [one] { $arg2 } struttura
       *[other] { $arg2 } strutture
    } (idonee alla migrazione completa dei metadati: { $arg3 }); rischio di sovrascrittura: { $arg4 ->
        [one] { $arg4 } entità di blocco esistente
       *[other] { $arg4 } entità di blocco esistenti
    }
async-regen-upgrade-scope-biomes = solo biomi
async-regen-upgrade-scope-terrain = terreno, strutture e biomi
async-regen-usage = Uso: /regen preview blocks [tipi...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Visualizzazione in { $arg0 }: ciano = confine della selezione, arancione = campione dei blocchi modificati, rosso = entità blocco a rischio. Solo tu vedi queste particelle.
async-regen-visualization-selection = Selezione in { $arg0 }: le particelle ciano ne indicano il confine. Solo tu le vedi.
async-regen-fawe-required = FastAsyncWorldEdit è necessario per leggere la selezione
building-map-layer = Costruzioni
building-map-open = Vedi costruzione
building-map-builders = Costruito da { $arg0 }
building-map-original-author = Autore originale: { $arg0 }
building-map-type-original = Creazione originale
building-map-type-derivative = Opera derivata
building-map-type-replica = Riproduzione
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ Accovacciati
iron-elevator-tip-mm = Allinea { -iron-elevator-block } in verticale per creare un ascensore. Sali sul blocco: <green>salta per salire</green> e <aqua>accovacciati per scendere</aqua>. Con almeno due piani appare un <gold>pannello dei piani</gold>.
iron-elevator-up = ↑ Salta
spawn-beacon-color-command-description = Attiva/disattiva il ciclo del colore del faro di spawn
spawn-beacon-color-disabled = Rotazione del colore del faro dello spawn disattivata
spawn-beacon-color-enabled = Rotazione del colore del faro dello spawn attivata
spawn-command-description = Ritorna allo spawn
spawn-tip-return-mm = Usa <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> per tornare al <yellow>punto di spawn del server</yellow>.
spawn-world-missing = Il mondo di spawn non è disponibile

## Entertainment · Music

music-album = Album: { $arg0 }
music-artist = Artista: { $arg0 }
music-command-description = Musica locale, online e NBS per giradischi
music-disc-left = Clic sinistro: prendi il disco
music-disc-right = Clic destro: riproduci sul giradischi più vicino
music-duration = Durata: { $arg0 }
music-format = Formato: { $arg0 }
music-jukebox-audible-field-conflict = Un altro giradischi riproduce un brano diverso in un'area chiaramente udibile sovrapposta
music-jukebox-tp-hover = Clicca per teletrasportarti al giradischi
music-jukebox-unavailable = Il giradischi è stato rimosso o il suo chunk non è disponibile
music-now-playing-rich = In riproduzione: { $music } al giradischi { $location }
music-online-rate-limited = Troppe richieste musicali. Riprova tra { $arg0 ->
        [one] { $arg0 } secondo
       *[other] { $arg0 } secondi
    }.
music-original-author = Autore originale: { $arg0 }
music-plasmo-voice-unavailable = Il brano è iniziato, ma non puoi sentirlo perché Plasmo Voice non è installato, connesso o attivo
music-playback-failed = Impossibile riprodurre "{ $arg0 }". Controlla la sorgente o il file musicale e riprova.
music-reload-failed = Ricaricamento della musica non riuscito: { $arg0 }
music-reload-started = Ricaricamento dei file musicali e delle sorgenti personalizzate...
music-rhythm-animation-offset = Scostamento animazione { $arg0 } ms
music-rhythm-calibration-accumulating = Conferma del ritmo stabile… continua a suonare
music-rhythm-calibration-adapting = Sincronizzazione in corso… continua a seguire il ritmo
music-rhythm-calibration-audio-instruction = Premi un tasto da 1 a 4 a ogni battito.
music-rhythm-calibration-done = Test di latenza completato · impostazioni salvate
music-rhythm-calibration-failed = L'audio del test si è interrotto. Il test è stato chiuso in sicurezza; avvialo di nuovo
music-rhythm-calibration-intro = { music-rhythm-calibration-stage-minecraft } → { music-rhythm-calibration-stage-plasmo } → { music-rhythm-calibration-stage-visual } → { music-rhythm-calibration-stage-pointer }
music-rhythm-calibration-listen = Ascolta una volta il ritmo completo, senza premere ancora alcun tasto.
music-rhythm-calibration-no-input = Nessun input valido rilevato. A ogni segnale usa il comando indicato per questa fase.
music-rhythm-calibration-out-of-range = Il ritardo misurato è fuori dall'intervallo supportato. Controlla le impostazioni audio
music-rhythm-calibration-plasmo-ready = Test Minecraft Note Block completato; prima ascolta lo stesso ritmo tramite Plasmo Voice
music-rhythm-calibration-pointer-instruction = Fai clic sinistro o destro quando la nota attraversa la linea.
music-rhythm-calibration-realigning = Il flusso audio si è interrotto. La dimostrazione riparte e i campioni completi restano salvati
music-rhythm-calibration-required = Completa il test di latenza prima di avviare il gioco ritmico
music-rhythm-calibration-reset-done = Calibrazione della latenza azzerata
music-rhythm-calibration-result-hold = Test completato. Controlla i risultati; tornerai automaticamente tra poco.
music-rhythm-calibration-stage-minecraft = Blocco musicale di Minecraft
music-rhythm-calibration-stage-plasmo = Plasmo Voice
music-rhythm-calibration-stage-pointer = Visivo · mouse
music-rhythm-calibration-stage-visual = Visivo · tastiera
music-rhythm-calibration-visual-instruction = Premi 1–4 quando la nota attraversa la linea.
music-rhythm-calibration-visual-preview = Osserva un'intera sequenza senza input · inizia quando cambia l'istruzione
music-rhythm-calibration-voice-required = Il test completo richiede Plasmo Voice connesso e attivo
music-rhythm-chart-preparing = Preparazione della mappa completa…
music-rhythm-combo = Combo ×{ $arg0 }
music-rhythm-difficulty-easy = Facile
music-rhythm-difficulty-expert = Esperto
music-rhythm-difficulty-hard = Difficile
music-rhythm-difficulty-normal = Normale
music-rhythm-exit = Esci dal gioco ritmico
music-rhythm-get-ready = Preparati
music-rhythm-go = VIA!
music-rhythm-good = Buono
music-rhythm-great = Ottimo
music-rhythm-latency = Latenza { $arg0 } ms
music-rhythm-latency-test = Test completo della latenza
music-rhythm-miss = Mancato
music-rhythm-mode-falling = Note in caduta
music-rhythm-mode-radial = Assalto radiale
music-rhythm-mode-spatial-aim = Mira spaziale
music-rhythm-network-too-high = La latenza di rete è troppo alta per un giudizio equo ({ $arg0 } ms; limite { $arg1 } ms)
music-rhythm-network-warning = La latenza di rete è instabile ({ $arg0 } ms). La sessione terminerà se resta sopra { $arg1 } ms
music-rhythm-no-beats = Nessun ritmo giocabile rilevato in questo brano
music-rhythm-perfect = Perfetto
music-rhythm-requires-playback = Prima riproduci un brano su questo giradischi
music-rhythm-results = Risultati
music-rhythm-score = Punteggio: { $arg0 }
music-rhythm-spatial-no-room = Non c’è abbastanza spazio per la mira spaziale. Spostati in un’area più aperta.
music-rhythm-spatial-slider-hint = Segui la stella mobile e clicca ogni punto · non tenere premuto
music-rhythm-start = Avvia partita
music-sample-rate = Frequenza di campionamento: { $arg0 }
music-size = Dimensioni: { $arg0 }
music-unavailable-disc = Disco non disponibile

## Entertainment · Anniversary

anniversary-accepted = Hai aderito all'estrazione. Il primo turno si sblocca dopo 4 ore online non AFK.
anniversary-admin-audit-action-bonus = Opportunità bonus per costruzioni assegnata
anniversary-admin-audit-action-gift = Premio evento regalato: { $arg0 }
anniversary-admin-audit-action-removal = Partecipazione passata di un amministratore rimossa; premi ripristinati: { $arg0 ->
        [one] { $arg0 } premio
       *[other] { $arg0 } premi
    }.
anniversary-admin-audit-action-unknown = Azione sconosciuta: { $arg0 }
anniversary-admin-audit-empty = Nessuna voce di audit della gestione.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Autore: { $arg0 } ({ $arg1 }); destinatario: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Audit gestione anniversario ({ $arg0 }/{ $arg1 } mostrati)
anniversary-admin-bonus-cancelled = Terminato dal pacco regalo
anniversary-admin-bonus-pending = Assegnato, non ancora estratto
anniversary-admin-bonus-used = Estratto
anniversary-admin-boss = Gestione anniversario · Usa { $arg0 } per vedere i progressi
anniversary-admin-commands = Comandi: /anniversary winners [pagina] | /anniversary audit [giocatore] | /anniversary bonus <giocatore>
anniversary-admin-control = Controllo dinamico: rilasciati { $arg0 }; disponibili ora { $arg1 }; probabilità base { $arg2 }; ritmo recente { $arg3 }
anniversary-admin-draw-bonus = Bonus per costruzioni
anniversary-admin-draw-detail = { $arg0 }: estratto alle { $arg1 }; risultato { $arg2 }
anniversary-admin-draw-metrics = Probabilità { $arg0 }; valore casuale { $arg1 }; versione algoritmo { $arg2 }
anniversary-admin-draw-replaced = Sostituito dal pacco; stock ripristinato
anniversary-admin-draw-round = Turno normale { $arg0 }
anniversary-admin-event = Stato evento: { $arg0 }; periodo: { $arg1 }
anniversary-admin-excluded = Gli amministratori dell'anniversario non possono partecipare all'estrazione. È stata aperta la panoramica di gestione.
anniversary-admin-none = Nessuno
anniversary-admin-opportunities = Estrazioni effettuate: { $arg0 }; pronte ora: { $arg1 }; opportunità ottenute: { $arg2 }; vittorie/sconfitte: { $arg3 }
anniversary-admin-participants = Partecipanti: { $arg0 }
anniversary-admin-player-bag = Borsa evento finale: { $arg0 }
anniversary-admin-player-bonus = Bonus costruzione: { $arg0 }; assegnato: { $arg1 }; da: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Dettagli delle opportunità di estrazione
anniversary-admin-player-not-found = Partecipante all'evento non trovato: { $arg0 }
anniversary-admin-player-outcome = Vittorie: { $arg0 }; sconfitte: { $arg1 }; estrazioni effettuate: { $arg2 }; bonus costruzione: { $arg3 }
anniversary-admin-player-progress = Iscrizione: { $arg0 }; tempo online valido: { $arg1 }; estrazioni normali effettuate: { $arg2 }; estrazioni normali pronte: { $arg3 }
anniversary-admin-player-title = Audit partecipante: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Attivo
anniversary-admin-state-ended = Terminato
anniversary-admin-state-scheduled = Programmato
anniversary-admin-stock = Scorte rimanenti: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | totale { $arg4 }
anniversary-admin-target-excluded = { $arg0 } è un amministratore dell'anniversario e non può ricevere opportunità di estrazione.
anniversary-admin-title = Panoramica di gestione del quinto anniversario
anniversary-admin-winners-empty = Nessuna vincita è stata ancora realmente estratta.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · estratto alle { $arg3 }
anniversary-admin-winners-title = Elenco consegna premi (pagina { $arg0 }/{ $arg1 }, { $arg2 ->
        [one] { $arg2 } premio
       *[other] { $arg2 } premi
    })
anniversary-bag-empty = Nessun premio vinto per ora
anniversary-bag-item-locked = La borsa evento è di sola lettura
anniversary-bag-item-name = Premio del quinto anniversario · { $arg0 }
anniversary-bag-not-participating = Non partecipi ancora all'evento. Usa prima { $arg0 }.
anniversary-bag-title = Borsa evento del quinto anniversario
anniversary-bag-updated = Il premio è nella borsa evento. Usa { $arg0 } per vederlo.
anniversary-bonus-already-granted = { $arg0 } ha già ricevuto l'estrazione aggiuntiva per le costruzioni e non può riceverla di nuovo.
anniversary-bonus-boss-ready = Estrazione bonus costruzioni pronta · Usa { $arg0 } per estrarre
anniversary-bonus-broadcast-won = [Estrazione anniversario] { $arg0 } ha vinto { $arg1 } con il bonus costruzioni!
anniversary-bonus-draw-not-won = Estrazione bonus costruzioni: nessun premio questa volta.
anniversary-bonus-draw-sold-out = Estrazione bonus costruzioni: nessun premio, perché sono tutti esauriti.
anniversary-bonus-draw-won = Estrazione bonus costruzioni: congratulazioni, hai vinto { $arg0 }!
anniversary-bonus-gift-pack-final = { $arg0 } ha già vinto il pacco regalo e non può ricevere un'estrazione bonus.
anniversary-bonus-grant-not-active = L'estrazione aggiuntiva per le costruzioni non può essere assegnata fuori dal periodo dell'evento.
anniversary-bonus-grant-success = Estrazione aggiuntiva per le costruzioni assegnata a { $arg0 }.
anniversary-bonus-granted = Hai ricevuto un'estrazione aggiuntiva per le costruzioni accettate. Usa /anniversary per estrarre.
anniversary-bonus-participant-not-found = Nessun partecipante chiamato { $arg0 } trovato
anniversary-bonus-status-ready = Bonus costruzioni: un'estrazione aggiuntiva è pronta.
anniversary-bonus-status-used = Bonus costruzioni: l'estrazione aggiuntiva è stata usata.
anniversary-boss-completed = Estrazione anniversario · Entrambi i turni completati
anniversary-boss-gift-pack-final = Pacco regalo vinto · Usa { $arg0 } per vedere la borsa evento
anniversary-boss-invite = Estrazione del quinto anniversario · Usa { $arg0 } per partecipare
anniversary-boss-progress = Estrazione anniversario { $arg0 }/{ $arg1 } · Turno { $arg2 } tra { $arg3 }
anniversary-boss-ready = Turno { $arg0 } pronto · Usa { $arg1 } per estrarre
anniversary-broadcast-won = [Estrazione anniversario] { $arg0 } ha vinto { $arg2 } al turno { $arg1 }!
anniversary-cancel = Non ora
anniversary-command-description = Partecipa all'estrazione per il quinto anniversario del server
anniversary-confirm = Accetta e partecipa
anniversary-confirm-hint = Avvia il conteggio del tempo online non AFK
anniversary-delivery-note = Per una destinazione nella Cina continentale, fornisci un ID SF valido per spedizioni con protezione della privacy. Fuori dalla Cina continentale, fornisci nome reale del destinatario, indirizzo completo e telefono valido. Paga la tua quota di spedizione, altrimenti perderai il premio.
anniversary-dialog-prizes = Premi: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } e 5 { $arg3 }
anniversary-dialog-time = Periodo dell'evento: { $arg0 }
anniversary-dialog-title = Regole dell'estrazione del quinto anniversario
anniversary-draw-not-won = Turno { $arg0 }: nessun premio questa volta.
anniversary-draw-sold-out = Turno { $arg0 }: nessun premio, perché sono tutti esauriti.
anniversary-draw-won = Turno { $arg0 }: congratulazioni, hai vinto { $arg1 }!
anniversary-ended = L'estrazione del quinto anniversario è terminata
anniversary-ended-results = I tuoi risultati: { $arg0 }
anniversary-gift-pack-final = Il pacco regalo è il tuo premio finale. Ha sostituito e restituito alle scorte { $arg0 ->
        [one] { $arg0 } altro premio
       *[other] { $arg0 } altri premi
    }; tutte le estrazioni rimanenti sono terminate.
anniversary-gift-prize-not-owned = Non possiedi questo premio evento: { $arg0 }
anniversary-gift-received = { $arg0 } ti ha regalato { $arg1 }. Ora è nella tua borsa evento.
anniversary-gift-self = Non puoi regalare un premio a te stesso.
anniversary-gift-success = Hai regalato { $arg0 } a { $arg1 }.
anniversary-gift-usage = Uso: { $arg0 }
anniversary-not-active = L'evento del quinto anniversario non è attivo ora
anniversary-not-started = L'evento del quinto anniversario non è ancora iniziato. Periodo: { $arg0 }
anniversary-prize-badge = spilla con foto di gruppo del quinto anniversario
anniversary-prize-gift-pack = pacco regalo del quinto anniversario
anniversary-prize-keychain = portachiavi con logo
anniversary-prize-mug = tazza con logo
anniversary-prize-not-won = nessun premio
anniversary-prize-sold-out = premi esauriti
anniversary-rule-1 = 1. Dopo l'adesione puoi estrarre ogni 4 ore, per due volte. I premi vanno nella borsa virtuale dell'evento, al massimo uno per tipo. Il pacco regalo resta l'unico premio e termina le estrazioni rimanenti.
anniversary-rule-2 = 2. Il tempo AFK non conta per sbloccare le estrazioni. È vietato aggirare il rilevamento AFK con qualsiasi mezzo.
anniversary-rule-3 = 3. Il server copre l'80% del costo totale di spedizione dei premi fisici, fino a un massimo di 50 CNY; il vincitore paga per intero il saldo restante dopo aver sottratto tale contributo.
anniversary-rule-4 = 4. Per destinazioni nella Cina continentale, devi fornire un ID SF valido per spedizioni con protezione della privacy. Per destinazioni fuori dalla Cina continentale, devi fornire nome reale del destinatario, indirizzo completo e telefono valido. Senza le informazioni richieste o il pagamento della tua quota, perderai il premio.
anniversary-rule-5 = 5. È vietato partecipare più volte con account alternativi; la violazione comporta la squalifica.
anniversary-rule-6 = 6. Partecipando accetti tutte le regole sopra indicate.
anniversary-rule-7 = 7. Le costruzioni accettate sul sito valgono 0,2 per le riproduzioni, 0,5 per le rielaborazioni e 1 per gli originali; quelle accettate prima dell'evento sono moltiplicate per 0,8 e le costruzioni condivise per 1,5 volte la tua quota di contributo. Un totale di almeno 2 assegna un'estrazione aggiuntiva, concessa manualmente da un amministratore.
anniversary-second-round-hint = Il secondo turno si sblocca dopo 8 ore totali online non AFK.
anniversary-status-completed = Hai completato entrambi i turni.
anniversary-status-gift-pack-final = Hai vinto il pacco regalo. Il premio finale è fissato e tutte le estrazioni rimanenti sono terminate.
anniversary-status-progress = Completati: { $arg0 }/{ $arg1 } turni; tempo online valido: { $arg2 }
anniversary-status-remaining = Mancano { $arg1 } online non AFK al turno { $arg0 }.
anniversary-status-results = Risultati: { $arg0 }
anniversary-status-title = Progresso dell'estrazione anniversario

## Server · Admission, bans, safety and performance

ban-already-banned = È già presente un ban per { $arg0 }.
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Bollettino di ban]
ban-command-description = Escludi un giocatore con un livello di gravità fisso
ban-database-error = Operazione sul database dei ban non riuscita. Controlla il log del server.
ban-menu-confirm-action = Escludi giocatore
ban-menu-confirm-severity = Gravità
ban-menu-confirm-title = Conferma esclusione
ban-menu-never-joined-confirm-action = Escludi comunque
ban-menu-never-joined-title = Il giocatore non è mai entrato
ban-menu-never-joined-warning = Nessun accesso al server registrato per { $arg0 }. Verifica il nome per evitare di escludere la persona sbagliata.
ban-menu-player-input = Inserisci manualmente il nome
ban-menu-select-title = Seleziona giocatore
ban-menu-severity-title = Seleziona gravità
ban-history-empty = Nessuno storico dei ban per { $arg0 }.
ban-history-title = Storico dei ban: { $arg0 }
ban-invalid-player = Nome giocatore non valido
ban-invalid-severity = Gravità del ban non valida
ban-kick-appeal = Se ritieni che sia un errore, presenta ricorso nel gruppo QQ 1138627074
ban-kick-server = MIK
ban-kick-title = Sei stato escluso da questo server
ban-not-banned = { $arg0 } non ha un ban attivo.
ban-pardon-reason-required = È necessario un motivo per la revoca
ban-permanent = Permanente
ban-reason-required = È necessario un motivo per il ban
ban-self = Non puoi escludere te stesso
ban-severity-excusable = Giustificabile (30 giorni)
ban-severity-extreme = Violazione estrema (1500 giorni)
ban-severity-minor = Violazione minore (150 giorni)
ban-severity-severe = Violazione grave (360 giorni)
ban-severity-warning = Avvertimento (3 giorni)
ban-status-active = Attivo
ban-status-expired = Scaduto
ban-status-revoked = Revocato
ban-success = Ban applicato a { $arg0 } fino al { $arg1 }.
ban-sync-success = Ban sincronizzati: attivi { $arg0 } · voci storiche { $arg1 }
ban-system-source = Sistema
ban-unban-success = Ban revocato per { $arg0 }
ban-unknown-reason = Nessun motivo fornito
ban-update-success = Campo del ban di { $arg0 } aggiornato: { $arg1 }
banlist-command-description = Visualizza le esclusioni dei giocatori
banlist-copy = Clicca per copiare il nome
banlist-empty = Non ci sono giocatori esclusi
banlist-expires = Scadenza
banlist-invalid-page = Pagina non valida. Totale: { $arg0 ->
        [one] { $arg0 } pagina
       *[other] { $arg0 } pagine
    }.
banlist-next = Successiva
banlist-page = Pagina { $arg0 }/{ $arg1 } · { $arg2 ->
        [one] { $arg2 } voce
       *[other] { $arg2 } voci
    }
banlist-player = Giocatore
banlist-previous = Precedente
banlist-reason = Motivo
banlist-source = { role-moderator }
banlist-title = Elenco esclusioni
maintenance-command-description = Attiva, disattiva o controlla la modalità manutenzione
maintenance-kick = Il server è in manutenzione
maintenance-set = Modalità manutenzione { $arg0 }
maintenance-state-disabled = disattivata
maintenance-state-enabled = attivata
maintenance-status = Modalità manutenzione: { $arg0 }
maintenance-unchanged = La modalità manutenzione è già { $arg0 }
performance-kick-title = Il server sta subendo forti rallentamenti
performance-kick-reason = Sei stato disconnesso temporaneamente per consentire al server di riprendersi
restriction-entity-too-large-mm = <red>La dimensione dell'entità supera il limite di sicurezza del server</red>
restriction-foreign-uuid-mm = <red>Puoi utilizzare gli UUID solo per i mob che hai domato</red>
restriction-no-kill-e-mm = <red>Il comando <white>/kill @e</white> non è consentito</red>
restriction-no-newline-mm = <red>I messaggi non possono contenere interruzioni di riga</red>
restriction-no-run-command-mm = <red>I messaggi non possono indurre i giocatori a eseguire comandi</red>
restriction-no-selector-mm = <red>Non hai l'autorizzazione per utilizzare i selettori di destinazione <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>Non hai il permesso di menzionare i nomi di altri giocatori nei comandi</red>
tpsbar-command-description = Mostra o nasconde la barra TPS
tpsbar-hidden = Barra TPS nascosta
tpsbar-shown = Barra TPS mostrata
tpsbar-tip-status-mm = Usa <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> per mostrare o nascondere la barra di <yellow>TPS, MSPT e ping</yellow>.
unban-command-description = Revoca un ban mantenendo lo storico

## Shell · Menus, server information and scripting

main-afk-status =
    { $arg0 ->
        [afk] AFK: Assente
       *[online] AFK: Online
    }
main-pvp-status =
    { $arg0 ->
        [on] PVP: Attivo
       *[off] PVP: Disattivo
    }
main-announcements = Annunci
main-home = Case
main-language = Lingua
main-language-auto = Automatico
main-links = Link
main-map = Mappa web
main-menu-title = Menu principale
main-music = Musica
main-name-tag = Etichette nome
main-settings = Impostazioni
main-website = Sito web
main-wiki = Wiki
skript-command-description = Visualizza il supporto agli script del server
skript-info-ai-option = Non vuoi scriverlo? Fornisci questo prompt a un'IA:
skript-info-copy = [Copia prompt IA]
skript-info-copy-hover = Copia un prompt con versioni, documentazione e regole di sicurezza
skript-info-documentation = Documentazione di sintassi e API
skript-info-review = Gli script vengono caricati solo dopo la revisione e il test di un amministratore
skript-info-supported = Disponibile: { $arg0 }
skript-info-title = Script del server
skript-info-workflow = Scrivi e verifica un file .sk completo con la documentazione, poi invialo a un amministratore
stat-client-version = Versione del client
stat-first-joined = Primo accesso
stat-location = Posizione
stat-ping = Latenza
stat-ping-value = { $arg0 } ms
stat-play-time = Tempo di gioco
stat-role = Ruolo
stat-world = Mondo
web-account-title = Account web e passkey
web-account-confirm = Conferma
weblogin-question = Confermare questa verifica di identità web? Conferma solo operazioni avviate personalmente su mcmik.top. Non inserire mai codici per altre persone.
weblogin-command-description = Conferma un codice di verifica di identità web Mik
weblogin-confirmed = Identità web verificata. Torna al browser per continuare.
weblogin-invalid-code = Codice di verifica di identità web non valido


# Music workspace
music-space-label =
    { $arg0 ->
        [title] Spazio musicale
        [devices] Dispositivi
        [all-devices] Tutti i dispositivi
        [use-device] Usa questo dispositivo
        [current-device] Dispositivo attuale
        [manage-device] Gestisci
        [back-devices] Torna ai dispositivi
        [group-with-devices] Raggruppa con altri dispositivi
        [pick-world-member] Scegli un dispositivo nel mondo
        [group-world-pick-hint] Fai clic destro su un giradischi mentre sei accovacciato con la mano vuota per aggiungerlo; /music torna al menu.
        [group-member-standalone] Scegli un giradischi che non appartiene già a un gruppo di sincronizzazione.
        [group-selection-help] Seleziona i giradischi qui sotto o fai clic destro su un altro mentre sei accovacciato con la mano vuota. Il gruppo viene creato alla conferma.
        [group-source-help] Usa la coda e la posizione del dispositivo scelto. Crea interrompe la riproduzione; Crea e riproduci la riprende.
        [device-actions] Azioni
        [device-status] Stato
        [sync-group] Gruppo sincronizzato
        [single-device] Jukebox
        [device-location] Posizione
        [visit-device] Teletrasportati accanto
        [teleporting] Teletrasporto in corso…
        [recorded-device] Memorizzato
        [nearby-device] Nelle vicinanze
        [group-select-members] Seleziona almeno due giradischi
        [group-name-required] Inserisci il nome del gruppo
        [direction-front] Davanti
        [direction-front-right] Davanti a destra
        [direction-right] A destra
        [direction-back-right] Dietro a destra
        [direction-back] Dietro
        [direction-back-left] Dietro a sinistra
        [direction-left] A sinistra
        [direction-front-left] Davanti a sinistra
        [direction-here] Qui
        [direction-above] Sopra
        [direction-below] Sotto
        [no-devices] Nessun dispositivo
        [choose-members] Scegli i dispositivi del gruppo
        [create-group] Crea gruppo sincronizzato
        [add-members] Aggiungi dispositivi
        [create-play] Crea e riproduci
        [create-and-append] Crea e accoda brani
        [device-queue] Coda
        [device-members] Membri
        [available] Disponibile
        [play] Riproduci
        [retry-outputs] Riprova le uscite
        [dissolve-group] Sciogli gruppo
        [remove-member] Rimuovi dal gruppo
        [removed-stopped] I dispositivi rimossi interrompono la riproduzione e mantengono il contenuto scelto.
        [restore-previous] Ripristina contenuto precedente al gruppo
        [keep-group-content] Mantieni contenuto del gruppo
        [queue] Coda di riproduzione
        [back] { back }
        [close] { close }
        [unavailable] L’archivio musicale non è disponibile
        [saving] Salvataggio…
        [saved] Salvato
        [apply] { plot-menu-submit }
        [cancel] { cancel }
        [select-page] Seleziona questa pagina
        [select-all] Seleziona tutti i risultati
        [clear-tracks] Deseleziona i brani
        [add-playlist] Aggiungi
        [description] Descrizione
        [make-private] Rendi privata
        [publish] Rendi pubblica
        [delete] Elimina playlist
        [favorite] Aggiungi o rimuovi dai preferiti
        [copy] Copia playlist
        [share] Condividi comando della playlist
        [recent] Riprodotti di recente
        [favorites] Playlist preferite
        [public] Pubblica
        [private] Privata
        [new-playlist] Crea playlist
        [name] Nome
        [song-title] Brano
        [track-count] Brani
        [visibility] Visibilità
        [no-playlists] Nessuna playlist
        [fetching-playlist] Caricamento della playlist…
        [artist] Artista
        [duration] Durata
        [track-type] Tipo
        [type-online] Online
        [undo] { vehicle-model-undo }
        [remove] Rimuovi le voci selezionate
        [clear-recents] Cancella la mia cronologia di ascolto
        [clear-entries] Deseleziona le voci
        [search-tracks] Cerca brani, artisti…
        [search-playlists] Cerca playlist…
        [public-playlists] Playlist pubbliche
        [searching] Ricerca in corso…
        [insert] Inserisci e riproduci subito
        [append] Aggiungi alla coda
        [choose-targets] Scegli uscita
        [group-source] Usa la coda e la posizione di
        [removed-targets] Alcune destinazioni sono state rimosse; selezionale di nuovo
        [offline] Non connesso
        [device-discovery-hint] Accovacciati e fai clic destro su un jukebox a mano vuota per aprirlo.
        [empty] Nessun brano
        [queue-empty] Nessun brano in coda
        [queue-current] Brano attuale della coda digitale
        [pause] Metti in pausa
        [resume] Riprendi
        [stop] Ferma e conserva la lista di riproduzione
        [previous] Brano precedente / ricomincia
        [clear] Svuota i brani in attesa; ferma dopo quello attuale
        [seek] Posizione
        [more] Altro
        [volume-control] Volume
        [group-volume] Volume del gruppo
        [timeline-help] Rotella ±5 s · Clic destro per inserire il tempo
        [timeline-read-only] Cambio di posizione non disponibile
        [seek-format] Inserisci m:ss, h:mm:ss o secondi
        [record] Salva per il controllo remoto
        [forget] Rimuovi i miei dispositivi memorizzati
        [alias] Alias personale del dispositivo
        [sound] Impostazioni audio
        [rhythm] Stanze ritmiche
        [room-players] Giocatori
        [room-player-status] Stato
        [room-preparation] La mia preparazione
        [room-ready] Pronto
        [room-cancel-ready] Annulla preparazione
        [room-playing] In partita
        [room-score-ready] Spartito pronto
        [room-select-one] Seleziona un brano usando la selezione multipla della libreria.
        [room-end-confirmation] Questo termina la stanza per tutti i partecipanti.
        [create-room] Crea stanza
        [no-rhythm-devices] Nessun jukebox nelle vicinanze. Prima fai clic destro su un jukebox accovacciandoti e a mano vuota.
        [calibration] Calibrazione latenza
        [volume] Volume (0–100)
        [range] Raggio (8–256 blocchi)
        [mode] Modalità di riproduzione
        [save-queue] Salva coda come playlist
        [sequential] In ordine
        [repeat-all] Ripeti lista
        [repeat-one] Ripeti brano
        [shuffle] Casuale senza ripetizioni
        [import] Importa playlist
        [link] Link della playlist
        [partial-import] È stata recuperata solo una parte della playlist. Salvala se vuoi conservarla.
        [import-preview] Anteprima dei metadati recuperati; non è stato scaricato alcun audio
        [import-new] Crea e apri
        [import-merge] Unisci a playlist
        [stopped] Arrestato
        [loading] Preparazione dell’audio
        [cancel-loading] Annulla preparazione
        [playing] In riproduzione
        [paused] In pausa
        [finished] Terminata
        [retry-failed] Riprova la riproduzione
        [select-song] Riproduci i brani selezionati o aggiungili insieme a una playlist.
        [select-toggle] Seleziona questa voce
        [deselect] Deseleziona questa voce
        [selected] Selezionata
        [place-before] Sposta la selezione prima di questa voce
        [eject] Espelli disco fisico
        [make-disc] Crea disco musicale firmato
        [gain] Volume del gruppo (0–100)
        [retry-import] Recupera di nuovo la playlist
        [join-room] Entra nella stanza
        [ready] Pronto
        [waiting] In attesa
        [room-mode] Modalità di gioco
        [room-difficulty] Difficoltà
        [late-join] Unisciti alla partita in corso
        [leave-room] Esci dalla stanza
        [select-room-track] Usa brano selezionato
        [start-room] Avvia partita
        [end-room] Termina stanza
        [base-queue] Lista di riproduzione principale
        [priority-queue] Richieste da riprodurre una sola volta
        [falling] { music-rhythm-mode-falling }
        [radial] { music-rhythm-mode-radial }
        [spatial-aim] { music-rhythm-mode-spatial-aim }
        [easy] { music-rhythm-difficulty-easy }
        [normal] { music-rhythm-difficulty-normal }
        [hard] { music-rhythm-difficulty-hard }
        [expert] { music-rhythm-difficulty-expert }
        [waiting-for-player] In attesa che i giocatori siano pronti
        [preparing] Preparazione dell’audio e delle note
        [no-beats] Nessun ritmo giocabile
        [catalog] Catalogo
        [my-playlists] Le mie playlist
        [playlist] Playlist
        [output] Uscita
        [close-panel] Chiudi pannello
        [playlist-settings] Impostazioni playlist
        [multi-select] Seleziona
        [move] Sposta
        [move-hint] Scegli un brano per inserire la selezione prima
        [cancel-move] Annulla spostamento
        [move-end] Metti in fondo
        [cancel-selection] Annulla selezione
        [track-details] Dettagli del brano
        [skip-next] Passa al successivo
        [device-settings] Impostazioni dispositivi
        [already-added] Già presente
        [current-track] Brano attuale
        [shuffle-candidates] Candidati casuali
        [import-by-id] Importa tramite ID
        [playlist-id] ID playlist
       *[unknown] Spazio musicale
    }

music-space-error =
    { $arg0 ->
        [UNAVAILABLE] La musica non è disponibile. Riapri il menu più tardi.
        [INTERNAL] Errore interno della musica; chiedi a un amministratore di controllare il registro del server
        [NOT_FOUND] La voce selezionata non esiste più.
        [FORBIDDEN] È necessario il permesso di controllo o modifica.
        [CONFLICT] Il contenuto è cambiato. Aggiorna la vista e seleziona di nuovo.
        [LIMIT] È stato raggiunto il limite delle risorse musicali.
        [INVALID] Controlla il nome, la selezione o l’intervallo numerico.
        [OFFLINE] Dispositivo non disponibile: carica il suo chunk o espelli il disco fisico.
        [OVERLAP] Le aree di ascolto si sovrappongono. Ferma un altro dispositivo, riduci il raggio o crea un gruppo sincronizzato.
        [UNSUPPORTED] Questo contenuto multimediale non supporta l’azione.
        [BUSY] È in corso un’operazione sul dispositivo, sulla richiesta o nella stanza ritmica. Riprova al termine.
        [NO_SAFE_DESTINATION] Non c’è un punto sicuro accanto a questo jukebox
        [TELEPORT_FAILED] Il teletrasporto non è riuscito o è stato bloccato
       *[UNKNOWN] Operazione non riuscita
    }

music-space-selected-tracks =
    { $arg0 ->
        [one] Selezionato { $arg0 } brano
       *[other] Selezionati { $arg0 } brani
    }
music-space-track-count =
    { $arg0 ->
        [one] { $arg0 } brano
        *[other] { $arg0 } brani
    }
music-space-device-count =
    { $arg0 ->
        [one] { $arg0 } jukebox
       *[other] { $arg0 } jukebox
    }
music-space-playback-target = Riproduzione su: { $arg0 }
music-space-online-count = Online: { $arg0 }/{ $arg1 } giradischi
music-space-device-distance = { $arg0 } · { $arg1 } m
music-space-device-height = { $arg0 ->
    [above] { $arg1 } m più in alto
   *[below] { $arg1 } m più in basso
    }
music-space-device-coordinates = { $arg0 } · X { $arg1 } · Y { $arg2 } · Z { $arg3 }
music-space-sound-value = { $arg0 ->
        [volume] { $arg1 ->
           *[other] { $arg1 }%
        }
       *[range] { $arg1 ->
            [one] { $arg1 } blocco
           *[other] { $arg1 } blocchi
        }
    }
plot-permission-music-control = Controllare dispositivi musicali

music-partial-results = Richieste alle sorgenti non riuscite: { $arg0 ->
        [one] { $arg0 } richiesta
       *[other] { $arg0 } richieste
    }; sono mostrati i risultati disponibili

webchat-tab-label = { $arg0 ->
    [1] { player-web-label }
   *[other] { player-web-label } ×{ $arg0 }
    }
webchat-joined = { $arg0 } è entrato nella chat web
webchat-left = { $arg0 } ha lasciato la chat web
chat-rate-limited = Stai inviando troppo velocemente. Attendi un momento.

plot-map-corner-a = Angolo A
plot-map-corner-b = Angolo B
plot-map-corner-hint = Clicca per centrare questo angolo. Clic destro: coordinate; rotella: Y ±4.
plot-map-coordinates = Coordinate x,y,z
plot-map-zoom-in = Ingrandisci
plot-map-zoom-out = Riduci
plot-map-fit = Intera area
plot-map-near = Vicino a me
plot-map-layer = Livello Y { $arg0 }…{ $arg1 }
plot-map-layer-hint = Rotella: cambia livello; clic: il tuo livello. Il terreno mostra sempre la superficie.
plot-map-north = Nord
plot-map-west = Ovest
plot-map-east = Est
plot-map-south = Sud
plot-map-scale = 1 pixel = { $arg0 } × { $arg0 } blocchi
plot-map-progress = Lettura mappa { $arg0 }%…
plot-map-unknown = Campioni di superficie non caricati: { $arg0 }
plot-map-legend = Ciano: mantenuto · Viola: aggiunto · Arancio: rimosso · Verde: prisma · A/B: angoli · Freccia: tu
plot-selection-validating = Calcolo dei confini e verifica dell'area in background… Aggiornamento automatico al termine.

plot-map-open = Vedi mappa Minecraft

plot-map-handheld-hint = Mappa nella mano secondaria; seleziona con l’ascia di legno. Accovacciati + F per tornare al pannello.

plot-map-readonly-hint = Solo anteprima della mappa. Torna al pannello e scegli Modifica prisma per cambiare gli angoli.

plot-destinations = Punti di teletrasporto
plot-destination-add = Aggiungi posizione attuale
plot-destination-confirm-delete = Eliminare il punto «{ $arg0 }»?
plot-destination-delete = Elimina punto
plot-destination-deleted = Punto di teletrasporto eliminato.
plot-destination-description = Descrizione (facoltativa)
plot-destination-edit = Modifica nome e descrizione
plot-destination-empty = Nessun punto di teletrasporto disponibile.
plot-destination-error-changed = Questo punto è cambiato. Riaprilo.
plot-destination-error-description = La descrizione ammette fino a 512 caratteri, senza caratteri di controllo.
plot-destination-error-location = Posizionati su terreno sicuro all’interno di questo lotto.
plot-destination-error-missing = Questo punto non esiste più.
plot-destination-error-name = Il nome deve contenere da 1 a 48 caratteri su una sola riga.
plot-destination-error-private = Questo punto è privato e riservato ai membri del lotto.
plot-destination-error-unsafe = Questo punto non è disponibile o non è sicuro.
plot-destination-loading = Caricamento del punto…
plot-destination-move = Usa posizione attuale
plot-destination-name = Nome
plot-destination-public-empty = Non ci sono ancora punti pubblici.
plot-destination-public-off = Pubblico: no
plot-destination-public-on = Pubblico: sì
plot-destination-saved = Punto di teletrasporto salvato.
plot-destination-teleport = Teletrasporta
plot-error-subplot-destination = Sposta o elimina prima i punti del lotto principale nell’area selezionata.
plot-error-subplot-own-destination = Mantieni i punti nella nuova area oppure spostali o eliminali prima.

plot-destination-manage = Gestisci
plot-directory-owned = Di proprietà · { $arg0 }
plot-directory-participating = Partecipazioni · { $arg0 }
plot-directory-search = Cerca lotti
plot-directory-search-value = Ricerca: { $arg0 }
plot-directory-search-field = Nome o ID del lotto
plot-directory-clear-search = Cancella ricerca
plot-directory-empty = Non ci sono ancora lotti in questa categoria.
plot-directory-no-match = Nessun lotto corrispondente. Modifica o cancella la ricerca.
plot-workspace-area = Modifica area
plot-workspace-area-hint = Regola l’area protetta di questo lotto
plot-workspace-subplots = Sottolotti
plot-workspace-subplots-hint = Consulta, crea e gestisci le suddivisioni
plot-workspace-access-hint = Gestisci membri, ruoli e permessi delle azioni
plot-workspace-settings-hint = Nome, avviso, ambiente e proprietà
plot-workspace-destinations-hint = Consulta e usa i punti di teletrasporto di questo lotto
plot-workspace-role = Il tuo ruolo: { $arg0 }
plot-workspace-ownership = Proprietà ed eliminazione
plot-workspace-id = ID del lotto: { $arg0 }
plot-workspace-groups = Gruppi di permessi
plot-workspace-permissions = Permessi delle azioni
plot-workspace-other-world = Raggiungi prima il mondo di questo lotto.

plot-destination-titles = Titoli di arrivo
plot-destination-title = Titolo
plot-destination-subtitle = Sottotitolo
plot-destination-title-hint = Mostrati dopo un teletrasporto riuscito. Supportano colori, stili e sfumature MiniMessage. Lascia vuoto per nascondere; l’anteprima non salva.
plot-destination-title-preview = Anteprima a schermo intero
plot-destination-title-unset = Non impostato
plot-destination-title-save = Salva titoli
plot-destination-title-unchanged = Nessuna modifica da salvare.
plot-destination-title-apply = Applica alla bozza
plot-destination-error-title = Il titolo deve occupare una riga: massimo 32 caratteri visibili e 512 caratteri di sorgente MiniMessage.
plot-destination-error-subtitle = Il sottotitolo deve occupare una riga: massimo 64 caratteri visibili e 512 caratteri di sorgente MiniMessage.
