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

bedrock-menu-back = Indietro
bedrock-menu-hotkey = Azione rapida (F)
bedrock-menu-primary = Azione principale
bedrock-menu-scroll-down = Giù
bedrock-menu-scroll-up = Su
bedrock-menu-secondary = Azione secondaria
interface-fov-apply = Applica FOV
interface-fov-custom = Inserisci un valore preciso
interface-fov-description = Usa il FOV del client; cambia solo la posizione del menu. Predefinito: 70°.
interface-fov-input = FOV del client: intero da 30 a 110
interface-fov-invalid = Inserisci un numero intero da 30 a 110.
interface-fov-title = Adattamento visuale (FOV)
interface-scale-current = Attuale: { $arg0 }
interface-scale-description = La dimensione del testo moltiplica quella del menu; regola entrambi i gruppi con la rotella.
interface-scale-layout-title = Dimensione menu
interface-scale-menu-title = Interfaccia e testo
interface-scale-native-hint = Queste impostazioni riguardano i menu spaziali Java. Il testo dei moduli nativi dipende dal client.
interface-scale-preview = Anteprima testo Aa 123
interface-scale-reset = Ripristina entrambi al 100%
interface-scale-text-title = Dimensione testo
menu-rhythm-game-display = Gioca al gioco ritmico
menu-usage-display-mm = <aqua><bold>Nel menu</bold></aqua>
url-dialog-confirm = Apri
url-dialog-hint = Il client chiederà un’altra conferma per aprire il collegamento.
url-dialog-question-rich = Aprire { $label }?
url-dialog-title = Apri { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Mod consigliate non rilevate: { $mods }. Fai clic sul nome per scaricarle; se già installate, verifica che siano attive e compatibili.
client-version-mismatch-mm = <gray>Versione del server <white>{ $serverVersion }</white>, versione attuale del client <white>{ $clientVersion }</white>.</gray>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> giocatore nelle vicinanze è <gold>invisibile</gold>
       *[other] <gold>{ $count }</gold> giocatori nelle vicinanze sono <gold>invisibili</gold>
    }</yellow> <gray>·</gray> <gray>il più vicino è a circa { $distance ->
        [one] { $distance } blocco
       *[other] { $distance } blocchi
    }</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = { $player } entra dalla porta sul retro
    .item-2 = { $player } trova il sentiero segreto
    .item-3 = { $player } spunta dai cespugli
presence-quit-templates =
    .item-1 = { $player } sparisce in punta di piedi
    .item-2 = { $player } esce per salvare la partita
    .item-3 = Il mondo reale richiama { $player }
welcome-first-join-mm = <gold><bold>Ti diamo il benvenuto in MIK!</bold></gold><newline><gray>Inizia con il <click:open_url:'https://mcmik.top'><yellow>sito web</yellow></click> e <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>; puoi anche usare <yellow>/menu</yellow> per aprire il menu principale.</gray>
welcome-return-mm = <gold><bold>Ti diamo di nuovo il benvenuto!</bold></gold><newline><gray>La tua ultima visita risale a <yellow>{ $arg0 ->
        [one] { $arg0 } giorno
       *[other] { $arg0 } giorni
    } fa</yellow>. Usa <yellow>/menu</yellow> per trovare rapidamente le funzioni più comuni.</gray>

## Player · AFK

afk-already-afk-mm = <gray>Sei già <gold>AFK</gold></gray>
afk-command-description = Attiva lo stato AFK
afk-default-statuses =
    .item-1 = si è allontanato dalla tastiera
    .item-2 = entrato in modalità di risparmio energetico
    .item-3 = passato allo sfondo
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
    .item-1 = <gray>*</gray> <player> <green>tornato alla tastiera</green>
    .item-2 = <gray>*</gray> <player> <green>è tornato</green>
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

## Player · Profile and tab list

nametag-clear-all-error = Errore durante la cancellazione delle etichette nome. Riprova più tardi
nametag-clear-all-success = ✔ Tutte le etichette nome cancellate
nametag-clear-error = Errore durante la cancellazione { $arg0 }
nametag-clear-success = ✔ { $arg0 } cancellato
nametag-click-fill-command = Clicca per inserire il comando
nametag-command-description = Personalizza le etichette con i nomi
nametag-copy-hover = Copia l'origine MiniMessage negli appunti
nametag-copy-label = [copia]
nametag-effect-label = Risultato
nametag-empty-content-rich = Il contenuto non può essere vuoto. Per cancellarlo, usa { $command }
nametag-format-docs = Guida alla formattazione
nametag-leading-label = Prefisso del nome
nametag-no-permission = Non hai l'autorizzazione per utilizzare questo comando
nametag-online-editor = Editor online
nametag-preview-label = Anteprima
nametag-sample-message = messaggio di esempio
nametag-set-error = Errore durante l'impostazione di { $arg0 }. Riprova più tardi
nametag-set-success = ✔ { $arg0 } impostato
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
fly-speed-presets = Preimpostazioni
fly-speed-reset = Velocità di volo reimpostata a 1x
fly-speed-reset-action = Reimposta
fly-speed-reset-click = Reimposta la velocità di volo a 1x
fly-speed-set = Velocità di volo impostata a { $arg0 }x
fly-state-disabled = Disattivato
fly-state-enabled = Attivato
fly-status = Volo: { $arg0 } | Velocità: { $arg1 }x
flyspeed-command-description = Visualizza o imposta la velocità di volo personale
home-action-create = Crea casa
home-action-delete = Elimina casa
home-action-icon = Usa oggetto come icona
home-action-teleport = Teletrasportati
home-action-update = Aggiorna posizione
home-already-exists = Esiste già una casa con questo nome
home-back = Indietro
home-confirm-delete = Conferma eliminazione
home-confirm-delete-detail-rich = Elimina casa { $home }
home-confirm-delete-warning = Questa operazione non può essere annullata
home-confirm-update = Conferma aggiornamento
home-confirm-update-detail-rich = Aggiorna la casa { $home } a questa posizione
home-delete-command-description = Elimina una casa
home-delete-usage-desc = Elimina una casa
home-deleted-rich = Casa { $home } eliminata
home-distance = Distanza: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    }
home-distance-other-world = mondo diverso
home-empty-description = Stai dove vuoi salvare una casa
home-empty-title = Nessuna casa ancora
home-icon = Icona: { $arg0 }{ $arg1 }
home-icon-default-note = · predefinito
home-icon-hand-invalid = L'oggetto nella mano principale non può essere usato come icona della casa
home-icon-updated-rich = Icona della casa { $home } aggiornata
home-invalid-icon = Questo materiale non può essere usato come icona della casa
home-invalid-name = I nomi delle case non possono contenere < > : caratteri
home-list-rich = Case: { $homes }
home-location = Posizione: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = I dati sulla posizione di questa casa non sono validi
home-max-reached = Puoi impostare al massimo { $arg0 } case
home-menu-title = Case
home-no-homes = Non hai ancora impostato nessuna casa
home-not-found-rich = Casa { $home } non esiste
home-set-command-description = Imposta una casa nella tua posizione attuale
home-set-rich = Casa { $home } impostata
home-set-usage-desc = Imposta una casa nella tua posizione attuale
home-summary-count = Imposta: { $arg0 } / { $arg1 }
home-teleport-command-description = Teletrasportati a una casa
home-teleport-failed = Teletrasporto fallito. Riprova
home-teleported-rich = Teletrasportato a casa { $home }
home-updated-rich = Posizione della casa { $home } aggiornata
home-usage-book = Guida ai comandi
home-usage-delhome = /delhome <name>  Elimina una casa
home-usage-home = /home tp <name>  Teletrasportati direttamente
home-usage-icon = /home icon <material> <name>  Imposta un'icona
home-usage-name-rule = I nomi delle case non possono contenere < > :
home-usage-sethome = /sethome <name>  Salva la tua posizione attuale
home-world = Mondo: { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Annulla l'ultimo /back
teleport-accept-command-description = Accetta la richiesta di teletrasporto in sospeso
teleport-allow = Teletrasporti da altri giocatori
teleport-block-afk = Blocca mentre AFK
teleport-denied = { $player } attualmente rifiuta i normali teletrasporti dei giocatori
teleport-deny-command-description = Rifiuta la richiesta di teletrasporto in sospeso
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
teleport-summary-afk = Sempre consentiti, rifiutati mentre AFK
teleport-summary-allow = Teletrasporti dei giocatori normali sempre consentiti
teleport-summary-consent = I teletrasporti dei giocatori normali richiedono la mia approvazione
teleport-summary-deny = Teletrasporti dei giocatori normali sempre rifiutati
tpany-command-description = Teletrasportati nella posizione di un giocatore, online o offline
tpany-done-offline-rich = Teletrasportato all'ultima posizione nota di { $player } { $world } { $location }
tpany-done-online-rich = Teletrasportato alla posizione attuale di { $player }
tpany-not-found-rich = Impossibile trovare la posizione di { $player }
tpany-usage = Uso
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teletrasporto nella posizione del giocatore, inclusa la cache offline

## Player · PVP

pvp-admin-menu-title = Amministrazione PVP - Pagina { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = I mob montati possono essere attaccati
pvp-allow-mounted-damage-desc = Se abilitato, un altro giocatore può attaccare un mob che stai cavalcando mentre entrambi avete il PVP abilitato
pvp-auto-enable-desc = Attacca un giocatore abilitato al PVP due volte di seguito
pvp-auto-enable-label = Abilitazione automatica
pvp-auto-enable-warning-actionbar-mm = <yellow>Attacca ancora una volta per abilitare automaticamente il PVP</yellow>
pvp-auto-enabled-actionbar-mm = <green>PVP abilitato automaticamente</green>
pvp-back-admin = Torna all'amministrazione PVP
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
pvp-next-page = Pagina successiva
pvp-no-permission-mm = <red>Non hai il permesso di gestire lo stato PVP di un altro giocatore</red>
pvp-player-not-found-mm = <red>Giocatore online non trovato</red>
pvp-prev-page = Pagina precedente
pvp-protect-mobs = Proteggi i tuoi mob
pvp-protect-mobs-desc = Proteggi i mob che hai domato o che stai cavalcando
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
pvp-target-disabled-actionbar-mm = <gray>Quel giocatore non ha il PVP abilitato</gray>
pvp-target-menu-title = Impostazioni PvP - { $arg0 }
pvp-toggled-off-mm = <gray>PvP disabilitato</gray> <dark_gray>|</dark_gray> <gray>Gli altri giocatori non possono attaccarti</gray>
pvp-toggled-on-mm = <green>PVP abilitato</green> <dark_gray>|</dark_gray> <gray>Ora puoi combattere altri giocatori abilitati al PVP</gray>

## Player · Utilities

hat-command-description = Indossa l'oggetto con la mano principale
hat-empty-hand-mm = <red>Nessun oggetto nella mano principale</red> <gray>Impugna un blocco o un oggetto, poi usa /hat</gray>
hat-success-mm = <green>Oggetto della mano principale indossato</green> <dark_gray>|</dark_gray> <gray>La mano e lo slot dell'elmo sono stati scambiati</gray>
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
self-kick-command-description = Espellerti dal server
self-kick-reason-too-long = Il motivo non può superare { $arg0 ->
        [one] { $arg0 } carattere
       *[other] { $arg0 } caratteri
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Hai lasciato il server per tua scelta. Motivo: { $reason }
       *[third] { $player } lascia volontariamente il server: { $reason }
    }
    .item-1 = { $player } sceglie un’uscita a sorpresa: { $reason }
    .item-2 = { $player } decide di uscire per { $reason }!
    .item-3 = { $player } lascia il server con questa spiegazione: { $reason }
    .item-4 = { $player } richiede la propria espulsione: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Hai lasciato il server per tua scelta.
       *[third] { $player } lascia volontariamente il server.
    }
    .item-1 = { $player } sceglie l’auto-espulsione!
    .item-2 = { $player } esce senza dire nulla.
    .item-3 = { $player } esegue un’uscita impeccabile.
    .item-4 = { $player } preme il proprio pulsante di uscita.
trash-command-description = Apri il cestino
trash-title = Cestino
trash-title-hint = Mira un oggetto e premi F per eliminare la pila

## Community · Chat and mentions

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
chat-private-target-offline = La destinazione della chat privata è offline; tornato alla chat pubblica
chat-public-command-description = Cambia canale di chat pubblica
chat-public-enter = Passato alla chat pubblica
chat-reply-command-description = Rispondi alla tua ultima chat privata target
chat-settings-menu-title = Impostazioni della chat
chat-social-account-label = Account della piattaforma
chat-social-bound-player-label = Giocatore collegato
chat-social-name-label = Nome social
chat-social-unbound = Non collegato a un giocatore Minecraft
chat-staff-command-description = Cambia canale di chat dello staff
chat-staff-permission-missing = Manca il permesso di chat del personale; tornato alla chat pubblica
mention-action-bar = Richiesta della barra delle azioni
mention-action-bar-text = { $sender } ti ha menzionato
mention-alerts = Avvisi di menzione
mention-menu-title = Avvisi di menzione
mention-mute-afk = Disattiva audio mentre AFK
mention-sound = Suono di avviso
mention-summary-action-bar = barra delle azioni
mention-summary-disabled = Avvisi disabilitati
mention-summary-enabled = Avvisi abilitati
mention-summary-enabled-afk = Avvisi abilitati, disattivati mentre AFK
mention-summary-modes-afk = { $arg0 }, disattivato mentre AFK
mention-summary-sound = suono
staffchat-enter = Sei entrato nella chat dello staff

## Community · Announcements and tips

announcement-empty = Nessun annuncio
announcement-first-page = Avvisi più recenti raggiunti
announcement-last-page = Avvisi più vecchi raggiunti
announcement-more-count = Altri { $arg0 } non mostrati
announcement-new-count = { $arg0 } nuovi
announcement-newer = Avvisi più recenti
announcement-older = Avvisi precedenti
announcement-reload = Ricarica annunci
announcement-reload-command-description = Ricarica il file degli annunci
announcement-reload-failed = Impossibile ricaricare gli annunci; quelli precedenti restano disponibili
announcement-reloaded = Annunci ricaricati
announcement-scroll-hint = Scorri per sfogliare in orizzontale
announcement-unread-count = { $arg0 } non letti
announcement-updated = Annunci aggiornati
announcement-view-all = Vedi tutti gli annunci
announcement-view-command-description = Vedi gli annunci del server
tip-hover = Suggerimento per il server
tip-label = CONSIGLIO
tip-reload-command-description = Ricarica tips.txt
tip-reload-count = { $arg0 ->
        [one] { $arg0 } voce
       *[other] { $arg0 } voci
    }
tip-reload-done = Suggerimenti ricaricati

## Community · Identity and social platforms

identity-admin-binding-not-found = Nessun collegamento giocatore-piattaforma univoco trovato.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Rimozione per { $arg1 }: { $arg2 ->
        [one] { $arg2 } collegamento d’identità
       *[other] { $arg2 } collegamenti d’identità
    } ({ $arg0 }).
identity-admin-command-description = Controlla o revoca i collegamenti di identità esterni
identity-admin-help = /bindadmin lookup <giocatore-o-UUID>
    /bindadmin unlink <giocatore-o-UUID> <piattaforma> confirm
identity-admin-lookup-empty = Non sono stati trovati collegamenti per questo giocatore.
identity-admin-lookup-title = Collegamenti di { $arg0 }:
identity-binding-details = · { $arg0 } · ambito { $arg1 } · { $arg2 }
identity-binding-not-owned = Non hai collegamenti per questa piattaforma.
identity-binding-unlinked = Rimozione completata: { $arg1 ->
        [one] { $arg1 } collegamento d’identità
       *[other] { $arg1 } collegamenti d’identità
    } ({ $arg0 }).
identity-bindings-empty = Non hai collegato account di piattaforme esterne.
identity-bindings-title = Identità esterne collegate:
identity-bindings-unlink-hint = Usa /bind unlink <piattaforma> per rimuovere tutti i collegamenti di una piattaforma.
identity-code-cancelled = Il codice di collegamento { $arg0 } inutilizzato è stato annullato.
identity-code-copy = Clicca per copiare il comando di collegamento completo
identity-code-expiry = Il codice è valido per 5 minuti e può essere usato una sola volta. Generarne un altro lo invalida.
identity-code-issued = Codice di collegamento per { $arg0 } generato:
identity-code-not-pending = Non ci sono codici di collegamento { $arg0 } inutilizzati.
identity-code-redeem = Nel gruppo { $arg0 } scelto, menziona il bot e invia: { $arg1 }
identity-command-description = Collega Minecraft a un'identità di una piattaforma esterna
identity-help = Comandi per collegare l'identità:
    /bind qq — genera rapidamente un comando di collegamento QQ
    /bind <piattaforma> — genera un codice monouso
    /bind list — mostra i collegamenti esistenti
    /bind cancel <piattaforma> — annulla un codice inutilizzato
    /bind unlink <piattaforma> — rimuove tutti i collegamenti di una piattaforma
identity-notify-linked = La tua identità { $arg0 } è stata verificata. Usa /bind list per vederla o /bind unlink { $arg0 } per rimuoverla.
identity-notify-unlinked = Il collegamento della tua identità { $arg0 } è stato rimosso.
identity-platform-none = nessuna
identity-platforms = Piattaforme supportate: { $arg0 }
identity-scope-notice = Questa piattaforma separa le identità per gruppo. Collega l'account nel gruppo in cui userai il bot; ogni gruppo va collegato separatamente.
identity-storage-error = L'operazione sul database dei collegamenti non è riuscita. Contatta un amministratore.
identity-unavailable = Il collegamento delle identità non è disponibile. Contatta un amministratore.
identity-unsupported-platform = Piattaforma non supportata: { $arg0 }
social-admin-conversations-empty = Nessuna conversazione osservata per { $arg0 }.
social-admin-conversations-title = Conversazioni osservate per { $arg0 }:
social-admin-help = /social platforms · /social status [piattaforma] · /social reload [piattaforma] · /social conversations <piattaforma>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Piattaforma social { $arg0 } ricaricata.
social-admin-reloaded-all = Tutte le piattaforme social sono state ricaricate.
social-admin-status = { $arg0 } · { $arg1 } · generazione { $arg2 } · conversazioni { $arg3 }
social-admin-title = Piattaforme social
social-admin-unknown = Piattaforma social sconosciuta: { $arg0 }
social-binding-already-linked = Questa identità è già collegata al giocatore Minecraft { $arg0 }.
social-binding-code-invalid = Il codice di verifica non è valido o è scaduto. Generane uno nuovo con /bind { $arg0 } in Minecraft.
social-binding-external-in-use = Questa identità è già collegata a un altro giocatore Minecraft.
social-binding-help = Esegui /bind { $arg0 } in Minecraft.
    Copia il comando completo generato.
    Invia qui il comando.
    Usa /profilo per verificare il collegamento.
social-binding-identity-missing = Questo messaggio non contiene un’identità membro attendibile, quindi il collegamento non è disponibile.
social-binding-linked = Collegato al giocatore Minecraft { $arg0 }.
social-binding-not-bound = Questa identità della piattaforma non è collegata.
social-binding-platform-mismatch = Questo codice di verifica è stato emesso per un’altra piattaforma.
social-binding-player-scope-in-use = Quel giocatore Minecraft ha già un’altra identità collegata in questa conversazione.
social-binding-rate-limited = Troppi tentativi non riusciti. Riprova tra circa { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minuti
    }.
social-binding-status-unbound = Questa identità non è collegata. Usa /collega con un codice di verifica.
social-binding-unavailable = Il collegamento dell’identità non è temporaneamente disponibile.
social-binding-unlink-prompt = Scollegando verrà rimossa l’associazione tra questa identità e Minecraft. Invia /scollega conferma per continuare.
social-binding-unlinked = Scollegato dal giocatore Minecraft { $arg0 }.
social-command-description = Gestisci gli adattatori delle piattaforme social
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
social-online-command-description = Mostra i giocatori online
social-profile-afk = Online · AFK
social-profile-avatar-alt = Volto Minecraft
social-profile-command-description = Mostra il tuo profilo; i membri effettivi cercano per nome, menzione o risposta
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
social-query-active-label = Attivi
social-query-no-players = Nessun giocatore è attualmente online.
social-query-online-label = Online
social-query-players-label = Giocatori
social-query-unknown = Comando sconosciuto. Invia /aiuto per vedere i comandi disponibili.
social-query-uptime-label = Tempo di attività
social-reply-hidden = Questa risposta è stata nascosta dalle regole di sicurezza dei contenuti del server.
social-state-disabled = disabilitata
social-state-failed = errore
social-state-ready = pronta
social-state-starting = avvio
social-state-stopped = arrestata
social-status-command-description = Mostra giocatori, TPS, MSPT e attività
social-title-command-help = Guida ai comandi
social-title-content-hidden = Contenuto nascosto
social-title-identity-linking = Collegamento account
social-title-identity-unlinking = Scollegamento account
social-title-online-players = Giocatori online
social-title-player-profile = Profilo giocatore
social-title-server-status = Stato del server
social-title-unknown-command = Comando sconosciuto
social-unlink-command-description = Rimuovi questo collegamento dopo la conferma

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
governance-active-playtime = Almeno 6 ore di gioco effettivo in 60 giorni (attualmente { $arg0 })
governance-already-moderator = Sei già moderatore.
governance-ballot-recorded = Il tuo voto anonimo è stato registrato come { $arg0 }. Puoi cambiarlo prima della scadenza.
governance-ban-details = Proposto da { $arg0 } · Durata { $arg1 } · Motivo: { $arg2 }
governance-ban-suffix = · Durata { $arg0 } · Motivo: { $arg1 }
governance-choice-abstain = Astensione
governance-choice-no = No
governance-choice-yes = Sì
governance-command-description = Mostra stato di governance e votazioni
governance-custodian-no-nomination = I custodi non partecipano alle elezioni dei moderatori.
governance-custodian-no-resign = La procedura di dimissioni dei moderatori non si applica ai custodi.
# $arg0 (Number): duration; locale decides the unit form.
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
governance-error-removal-requires-petition = Un voto di revoca richiede abbastanza sostenitori.
governance-error-removal-self = Non puoi sostenere la tua revoca.
governance-error-removal-sponsor-ineligible = Solo i membri attivi possono sostenere una revoca.
governance-error-removal-sponsors-too-few = Servono almeno altri due membri attivi.
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
# $arg0 (Number): duration; locale decides the unit form.
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
governance-menu-actions = Azioni
governance-menu-back = Indietro
governance-menu-cancel = Annulla
governance-menu-confirm = Conferma
governance-menu-confirm-nominate = Avviare una votazione per la tua nomina a moderatore?
governance-menu-confirm-resign = Dimettersi da moderatore?
governance-menu-confirm-sponsor = Sostenere questa richiesta di rimozione?
governance-menu-confirm-withdraw = Ritirare la candidatura a moderatore?
governance-menu-find-player = Cerca un altro giocatore
governance-menu-history = Storico votazioni
governance-menu-loading = Caricamento stato della governance…
governance-menu-new-petition = Avvia richiesta di rimozione
governance-menu-nominate = Candidati a moderatore
governance-menu-nomination-open = La tua votazione di nomina è aperta
governance-menu-pause = Sospendi la promozione
governance-menu-pause-appeal = Canale di ricorso
governance-menu-pause-duration = Durata (es. 2d)
governance-menu-pause-reason = Motivo
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } sostenitori
governance-menu-petitions = Richieste di rimozione
governance-menu-player-name = Nome del giocatore
governance-menu-players = Gestione giocatori
governance-menu-recent-results = Risultati recenti
governance-menu-refresh = Aggiorna
governance-menu-resign = Dimettiti da moderatore
governance-menu-resume = Rimuovi la sospensione
governance-menu-resume-reason = Motivo della rimozione
governance-menu-staff-only = Solo moderatori e custodi possono gestire i giocatori.
governance-menu-status = Il tuo stato
governance-menu-target = Nome del moderatore
governance-menu-title = Governo della comunità
governance-menu-votes = Votazioni aperte
governance-menu-withdraw = Ritira candidatura
# $arg0 (Number): duration; locale decides the unit form.
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
governance-own-not-voted-open = Non hai ancora votato.
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
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } secondo
       *[other] { $arg0 } secondi
    }
governance-storage-error = Errore nei dati di governance. Contatta un custode.
governance-target-not-moderator = { $arg0 } non è moderatore al momento.
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

plot-access-removed-for-you = Il tuo accesso al lotto { $arg0 } è stato rimosso.
plot-admin-created = Registrato { $arg0 } per { $arg1 } · { $arg2 }
plot-allowed = attivo
plot-area-changed = I confini sono cambiati. Aggiorna e conferma di nuovo.
plot-area-confirm = Sostituire i confini di «{ $arg0 }» con la selezione ({ $arg1 ->
        [one] { $arg1 } cella edificabile
       *[other] { $arg1 } celle edificabili
    })? Le costruzioni restano.
plot-area-hint = //pos1 → //pos2 → salva. Anche l’ascia di legno seleziona gli angoli; aggiungi/sottrai per più aree.
plot-area-replace-save = Sostituisci confini e salva
plot-area-save = Salva confini
plot-area-saved = Confini di «{ $arg0 }» salvati.
plot-arrival-removed = Punto di arrivo rimosso.
plot-arrival-set = Punto di arrivo salvato.
plot-arrival-text-saved = Titolo di arrivo salvato.
plot-atmosphere-saved = Ambiente salvato.
plot-back = Indietro
plot-board-auto-notify-result = Avvisi programmati per { $arg0 ->
        [one] { $arg0 } giocatore
       *[other] { $arg0 } giocatori
    }.
plot-board-auto-registered-text = Il lotto «{ $arg0 }» è stato registrato. Coordinate del riquadro delle celle costruite: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Avviso di { $arg0 } a { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }: { $arg5 }
plot-board-empty = Non ci sono ancora voci nella bacheca.
plot-board-header = Avvisi e commenti della comunità (ultimi { $arg0 }; dettagli: /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Avviso
plot-board-kind-objection = Obiezione
plot-board-menu = Bacheca della comunità
plot-board-notice-alert = { $arg0 } ha pubblicato un avviso: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } ha presentato un’obiezione: { $arg1 } · { $arg2 }
plot-board-rate-limited = Limite di pubblicazioni raggiunto. Riprova più tardi.
plot-board-record-invalid = Avviso non trovato, già risolto o azione non consentita.
plot-board-registered-alert = { $arg0 } ha registrato il lotto «{ $arg1 }»: { $arg2 }
plot-board-ruling-alert = È stata pubblicata una decisione sull’obiezione di { $arg0 }: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Decisione: { $arg0 }
plot-board-saved = Voce { $arg0 } salvata.
plot-board-status-elapsed = Pubblicato almeno 72 ore fa
plot-board-status-open = In attesa
plot-board-status-ruled = Deciso
plot-board-status-withdrawn = Ritirata
plot-board-text-invalid = La descrizione deve avere 12–1000 caratteri; la decisione almeno 8.
plot-board-unavailable = Bacheca non caricata. Riprova più tardi o visita https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } ha ritirato un’obiezione: { $arg1 } · { $arg2 }
plot-board-write-failed = Impossibile aggiornare la bacheca. Riprova più tardi.
plot-command-blocked = La protezione ha bloccato questo comando. Usa /fill o /setblock direttamente nel tuo lotto; chiedi allo staff di verificare comandi /execute complessi.
plot-command-description = Gestisci lotti edificabili e progetti pubblici
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } creato · { $arg1 }
        [feminine] { -plot } { $arg0 } creata · { $arg1 }
       *[other] { -plot } { $arg0 } creato · { $arg1 }
    }
plot-created-for-you = { $arg1 } ha registrato il lotto { $arg0 } per te.
plot-deleted = Terreno rilasciato; la costruzione resta.
plot-denied = disattivo
plot-empty-summary = Nessun lotto qui.
plot-error-admin-owner-only = Modificare membri con capacità gestionali richiede la gestione di membri e permessi.
plot-error-area-disconnected = Il terreno deve formare una zona connessa.
plot-error-args = Argomenti non validi o mancanti. Usa /plot help.
plot-error-arrival-location = Posizionati su un pavimento sicuro nell'area costruita del progetto.
plot-error-arrival-missing = Nessun punto di arrivo impostato.
plot-error-arrival-private = Non puoi visitare questo progetto privato.
plot-error-arrival-text = Titolo non valido. Usa una sola riga; massimo 32 caratteri visibili nel titolo e 64 nel sottotitolo.
plot-error-arrival-unsafe = Questo punto di arrivo non è disponibile o non è più sicuro.
plot-error-busy = Salvataggio in corso. Riprova più tardi; questa operazione non è stata inviata.
plot-error-flag = Permesso sconosciuto. Scegli un’operazione dalla lista dei permessi.
plot-error-id = ID del lotto inesistente o ambiguo. Usa /plot list.
plot-error-member-missing = Questo giocatore non è più un membro.
plot-error-member-required = I nuovi giocatori possono vedere i lotti e presentare obiezioni. Per creare un progetto serve il ruolo di membro.
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
plot-error-subplot-arrival = Sposta prima il punto di arrivo del lotto principale fuori dalla selezione.
plot-error-subplot-children = Rilascia i sottolotti prima del lotto principale.
plot-error-subplot-depth = I sottolotti possono avere un solo livello.
plot-error-subplot-disconnected = Le celle selezionate non sono collegate. Dividi ogni parte separatamente.
plot-error-subplot-overlap = La selezione si sovrappone a un altro sottolotto.
plot-error-subplot-own-arrival = Il punto di arrivo deve restare nella nuova area; altrimenti spostalo prima.
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
plot-menu-access = Accesso e membri
plot-menu-access-by-settings = secondo le impostazioni
plot-menu-admin = Aggiungi amministratore del lotto
plot-menu-arrival = Punto di arrivo
plot-menu-arrival-subtitle = Sottotitolo (MiniMessage; facoltativo)
plot-menu-arrival-text = Titolo di arrivo
plot-menu-arrival-title = Titolo (MiniMessage; vuoto = nome del progetto)
plot-menu-atmosphere = Ambiente
plot-menu-clear = Sereno
plot-menu-collaborator = Aggiungi collaboratore
plot-menu-confirm-delete = Eliminare la registrazione di { $arg0 }? Protezione, permessi dei membri e impostazioni saranno rimossi definitivamente. Le costruzioni resteranno.
plot-menu-confirm-subplot-transfer = Trasferire { $arg0 } a { $arg1 }? Il proprietario del lotto principale mantiene la supervisione.
plot-menu-confirm-transfer = Trasferire la gestione di { $arg0 } a { $arg1 }? Perderai l'accesso; la paternità delle opere resta invariata.
plot-menu-confirm-withdraw = Ritirare questo feedback? Il ritiro resterà visibile pubblicamente.
plot-menu-create = Crea lotto
plot-menu-create-for-player = Registra un lotto per questo giocatore
plot-menu-custom-time = Ora personalizzata
plot-menu-delete = Elimina registrazione del terreno
plot-menu-description = Descrizione
plot-menu-feedback = Contestare questo avviso
plot-menu-find-record = Cerca una voce
plot-menu-flag-container = Aprire i contenitori
plot-menu-flag-entity = Interagire con entità
plot-menu-follow-parent = Segui il lotto principale
plot-menu-follow-world = Segui il mondo
plot-menu-group-admin = amministratore del lotto
plot-menu-group-collaborator = Collaboratori
plot-menu-group-member = Accesso dei membri
plot-menu-group-newcomer = Accesso dei nuovi
plot-menu-group-owner = proprietario del lotto
plot-menu-manage = Gestisci lotti dei giocatori
plot-menu-managed-empty = Questo giocatore non ha ancora lotti.
plot-menu-managed-title = Lotti di { $arg0 }
plot-menu-members = Membri ({ $arg0 })
plot-menu-my-access = I tuoi permessi: { $arg0 } · { $arg1 }
plot-menu-name = Nome del progetto
plot-menu-owner = Proprietario: { $arg0 }
plot-menu-parent-supervisor = Proprietario del lotto principale: { $arg0 } · supervisione
plot-menu-player = Nome giocatore o UUID
plot-menu-project-kind = Tipo di progetto
plot-menu-public-destinations = Destinazioni pubbliche
plot-menu-public-destinations-empty = Nessun progetto pubblico ha ancora un punto di arrivo.
plot-menu-rain = Precipitazioni
plot-menu-record-id = ID della voce; per quelle meno recenti usa l'ID completo
plot-menu-related-notice = Vedi avviso collegato
plot-menu-remove-arrival = Rimuovi il punto di arrivo
plot-menu-remove-member = Rimuovi membro
plot-menu-rename = Rinomina progetto
plot-menu-rule = Registra una decisione
plot-menu-ruling = Motiva la decisione (8–1000 caratteri)
plot-menu-set-arrival = Imposta qui l'arrivo
plot-menu-set-role = Imposta come { $arg0 }
plot-menu-settings = Impostazioni del progetto
plot-menu-submit = Applica
plot-menu-subplot-create = Crea sottoappezzamento
plot-menu-subplot-of = Parte di { $arg0 }
plot-menu-subplot-parent = Torna al lotto principale: { $arg0 }
plot-menu-subplots = Sottolotti ({ $arg0 })
plot-menu-time = Ora
plot-menu-time-input = Ora (HH:mm)
plot-menu-transfer = Trasferisci il lotto
plot-menu-visit = Visita il progetto
plot-menu-weather = Meteo
plot-menu-withdraw = Ritira il feedback
plot-model-fit = Inquadra tutti i confini
plot-model-focus = Mostra i miei dintorni
plot-model-layer-down = Abbassa il taglio di 1 blocco
plot-model-layer-up = Alza il taglio di 1 blocco
plot-model-legend = Ciano: salvato · Verde/oro: angoli 1/2 · Viola: confini da salvare · Bianco/rosso: tu/direzione
plot-model-level = Taglio Y ≤ { $arg0 } (solo anteprima)
plot-model-limited = Campionamento o dettagli ridotti. Inquadra una zona vicina o un rettangolo per più dettagli; usa solo i confini per verificare i bordi.
plot-model-north-up = La vista dall’alto ha il nord in alto. Torna alla vista 3D per ruotare.
plot-model-perspective = Vista 3D
plot-model-preparing = Calcolo dei confini e verifica dell'area in background… Aggiornamento automatico al termine.
plot-model-progress = Lettura modello: { $arg0 }%…
plot-model-rotate-left = ↶ Ruota a sinistra
plot-model-rotate-right = ↷ Ruota a destra
plot-model-show-all = Mostra altezza completa
plot-model-slice = Taglia sopra il mio livello
plot-model-terrain = Mostra costruzioni e terreno
plot-model-top = Vista dall’alto
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } campione non caricato
       *[other] { $arg0 } campioni non caricati
    } (colore grigio). I chunk non vengono caricati forzatamente.
plot-model-wireframe = Solo confini
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
plot-permission-management = Impostazioni e proprietà del lotto
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
plot-private = Progetto privato
plot-public = Progetto pubblico
plot-public-set = Tipo di progetto: { $arg0 }.
plot-region-adjust = Modifica questo rettangolo
plot-region-apply = Completa modifica del rettangolo
plot-region-cancel = Annulla modifica del rettangolo
plot-region-changed = La selezione è cambiata. Seleziona nuovamente il rettangolo.
plot-region-delete = Elimina questo rettangolo
plot-region-delete-confirm = Rimuovere il rettangolo #{ $arg0 } dalla bozza? Gli edifici restano. I confini cambiano solo dopo il salvataggio; puoi annullare l’azione.
plot-region-edit-hint = Modifica questo rettangolo con //pos1 e //pos2. Gli altri restano invariati. Completa la modifica, poi salva il lotto.
plot-region-finish-first = Prima completa o annulla la modifica del rettangolo in corso.
plot-region-open = Gestisci rettangoli ({ $arg0 })
plot-region-select-hint = Seleziona un rettangolo numerato per metterlo a fuoco ed evidenziarlo. I contorni grigi mostrano questa pagina; l’unione può cambiare i numeri.
plot-renamed = Progetto rinominato.
plot-role-admin = amministratore del lotto
plot-role-changed-for-you = Il tuo ruolo nel lotto { $arg0 } ora è { $arg1 }.
plot-role-collaborator = collaboratore
plot-role-owner = proprietario del lotto
plot-role-parent-owner = proprietario del lotto principale
plot-role-server-staff = staff del server
plot-selection-add = Aggiungi riquadro attuale
plot-selection-clear = Reimposta i due punti
plot-selection-empty = Segna due angoli con //pos1 e //pos2.
plot-selection-exit = Esci da modifica / anteprima
plot-selection-fit = Ripristina i confini salvati
plot-selection-incomplete = Un punto è segnato. Segna l'altro; nulla cambia prima dell'invio.
plot-selection-mode = Scegli i punti nel mondo
plot-selection-no-redo = Nessuna modifica da ripristinare.
plot-selection-no-undo = Nessuna modifica precedente da annullare.
plot-selection-open = Modifica confini
plot-selection-redo = Ripeti ultimo passo
plot-selection-replace = Sostituisci con riquadro attuale
plot-selection-session-hint = Accovacciati+F o /plot edit per tornare. Uscita dopo 20 minuti di inattività.
plot-selection-size = Selezione: { $arg0 } × { $arg1 } × { $arg2 } blocchi (X/Y/Z)
plot-selection-stopped = Selezione terminata. Le bozze restano salvate per ogni lotto.
plot-selection-subtract = Sottrai riquadro attuale
plot-selection-undo = Annulla ultimo passo
plot-subplot-created = Sottolotto { $arg0 } creato · { $arg1 } blocco²
plot-subplot-created-for-you = Ora possiedi il sottolotto { $arg0 } dentro { $arg1 }.
plot-subplot-owner-changed = Il sottolotto { $arg0 } ora appartiene a { $arg1 }.
plot-subplot-parent-transferred = Il lotto principale del tuo sottolotto { $arg0 } ora appartiene a { $arg1 }.
plot-subplot-released = Il sottolotto { $arg0 } è stato rilasciato; l'area resta protetta dal lotto principale.
plot-title = Terreni
plot-transferred = Lotto { $arg0 } trasferito a { $arg1 }.
plot-transferred-to-you = { $arg1 } ti ha trasferito il lotto { $arg0 }.

## World · Vehicles

vehicle-admin-help = /vehicle — { vehicle-model-title }
    Modelli: model | model help
    Cattura: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Veicoli: spawn <model> [x y z] | reload
vehicle-command-description = Guida e gestisci veicoli composti da entità di visualizzazione
vehicle-controls = { $arg0 }: W/S e A/D per guidare; rotella per marce o acceleratore; scambia mano per la modalità; accovacciati per uscire. /vehicle help · /vehicle dashboard apre i comandi.
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
vehicle-help = /vehicle — Controllo veicoli
    Sali: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Guida: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    Pannello: hud [on/off] | dashboard [drive/gearbox/details/off]
    Manutenzione: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } kn · spinta { $arg1 }% · direzione { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · quota { $arg1 } m · velocità verticale { $arg2 } m/s · gas { $arg3 }% · { $arg4 }
vehicle-hud-status = Carburante { $arg0 }% · condizione { $arg1 }% · { $arg2 }
vehicle-model-add = Aggiungi componente
vehicle-model-add-here = Aggiungi appoggio ai miei piedi
vehicle-model-anchor = Posiziona qui l’origine dell’anteprima, seguendo la tua direzione
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
vehicle-model-corner-hint = Primo angolo segnato. Raggiungi quello opposto, anche in altezza, poi usa /vehicle model collider corner2. Il volume segue gli assi del modello.
vehicle-model-corner-required = Segna prima il primo angolo con /vehicle model collider corner1.
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
vehicle-model-exit = Posizione d’uscita / regolazione fine
vehicle-model-group = Aggiungi gruppo di visualizzazione Axiom
vehicle-model-help = /vehicle model — { vehicle-model-title }
    Modelli: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Modifica: set <field> <value...> | part/seat/collider/support — premi Tab per operazioni e argomenti
    Bozza: preview [off] | undo | redo | save | cancel
    Nel mondo: anchor | pick | here | look | snap [on/off] | move <index> <dx dy dz>
    { vehicle-model-position-hint }
    seat position/exit | part position | support add/set | collider position: here / look
    collider corner1/corner2 [look] | collider cancel — { vehicle-model-corner-hint }
    Altre azioni: check | apply — { vehicle-model-save-continue }
vehicle-model-here = Usa la posizione attuale dei piedi
vehicle-model-history-empty = Nessuna modifica disponibile in questa direzione della cronologia.
vehicle-model-import = Importa gruppo di visualizzazione Axiom
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
vehicle-model-position-hint = X/Y/Z locali · here usa i piedi, look la superficie mirata · la griglia opzionale segue il passo di regolazione.
vehicle-model-preview = Anteprima privata
vehicle-model-preview-failed = Anteprima non disponibile; la bozza è conservata: { $arg0 }
vehicle-model-preview-hint = Anteprima privata attiva. Rimossa dopo cinque minuti senza modifiche; /vehicle model preview off per chiudere.
vehicle-model-redo = Ripeti l’ultima modifica annullata
vehicle-model-remove = Rimuovi componente
vehicle-model-remove-warning = Rimuovere questo componente? Gli indici successivi cambieranno; puoi annullare l’operazione.
vehicle-model-rotate = Ruota componente
vehicle-model-rotate-hint = Rotazione locale relativa: X beccheggio, Y imbardata, Z rollio. La posizione resta invariata.
vehicle-model-save = Verifica e salva
vehicle-model-save-continue = Salva e continua a modificare
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } salvato
        [feminine] { -vehicle-model } salvata
       *[other] { -vehicle-model } salvato
    }
vehicle-model-scale = Ridimensiona componente
vehicle-model-scale-all = Ridimensiona tutti gli assi × { $arg0 }
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
vehicle-model-tools = Strumenti nel mondo
vehicle-model-transform-step = Cambia passo di regolazione
vehicle-model-undo = Annulla l’ultima modifica
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
async-regen-command-description = Rigenerare in modo asincrono una selezione WorldEdit
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
async-regen-plan-cancel = Annulla
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
async-regen-selection-required = Completa prima una selezione WorldEdit
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
async-regen-world-edit-required = WorldEdit è necessario per leggere la selezione
iron-elevator-down = ↓ Accovacciati
iron-elevator-up = ↑ Salta
spawn-beacon-color-command-description = Attiva/disattiva il ciclo del colore del faro di spawn
spawn-beacon-color-disabled = Rotazione del colore del faro dello spawn disattivata
spawn-beacon-color-enabled = Rotazione del colore del faro dello spawn attivata
spawn-command-description = Ritorna allo spawn
spawn-world-missing = Il mondo di spawn non è disponibile

## Entertainment · Music

music-action-add-queue = Aggiungi alla coda
music-action-play-nearby = Riproduci nel jukebox più vicino
music-action-play-now = Riproduci ora in questo jukebox
music-action-take-disc = Prendi il disco
music-added-to-queue-rich = Aggiunto alla coda: { $music }
music-album = Album: { $arg0 }
music-all-in-queue = Tutti i brani sono già nella coda
music-artist = Artista: { $arg0 }
music-back = Indietro
music-browser-left-click = Clic sinistro
music-browser-right-click = Clic destro
music-cache-clear-done = Cache della musica online svuotata
music-cache-clear-started = Pulizia della cache della musica online...
music-cache-operation-failed = Operazione sulla cache musicale non riuscita: { $arg0 }
music-cache-status = Cache della musica online: { $arg0 } file, { $arg1 } / { $arg2 }
music-cached = In cache - pronto per la riproduzione
music-clear-queue = Cancella coda
music-clear-queue-done = Coda svuotata ({ $arg0 ->
        [one] { $arg0 } brano
       *[other] { $arg0 } brani
    })
music-command-description = Musica locale, online e NBS per giradischi
music-disc-left = Clic sinistro: prendi il disco
music-disc-right = Clic destro: riproduci sul giradischi più vicino
music-duplicate-in-queue = Questo brano è già nella coda
music-duration = Durata: { $arg0 }
music-empty-library = La libreria musicale è vuota
music-empty-library-description = Non sono disponibili file audio locali o brani NBS
music-empty-nbs = Nessun brano NBS trovato
music-empty-nbs-description = Passa a tutta la musica o aggiungi un file NBS
music-empty-playlist = Questa playlist non contiene brani disponibili
music-empty-playlist-description = Verifica che la playlist sia pubblica e che la piattaforma selezionata sia corretta
music-empty-search = Nessun brano corrispondente
music-empty-search-description = Prova un'altra parola chiave o controlla lo stato delle sorgenti personalizzate
music-finished = Terminata
music-format = Formato: { $arg0 }
music-inventory-full = L'inventario è pieno. Impossibile fornire il disco musicale
music-jukebox-audible-field-conflict = Un altro giradischi riproduce un brano diverso in un'area chiaramente udibile sovrapposta
music-jukebox-music-mode = Modalità musica
music-jukebox-play-order = Ordine di riproduzione
music-jukebox-range = Raggio di riproduzione
music-jukebox-range-value = Attuale: { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    }
music-jukebox-rhythm-mode = Modalità ritmo
music-jukebox-too-far = Sei troppo lontano dal giradischi (massimo { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    })
music-jukebox-tp-hover = Clicca per teletrasportarti al giradischi
music-jukebox-unavailable = Il giradischi è stato rimosso o il suo chunk non è disponibile
music-jukebox-usage-mode = Uso del jukebox
music-jukebox-volume = Volume del jukebox
music-jukebox-volume-value = Attuale: { $arg0 }%
music-library-button = Libreria musicale
music-library-random-mode = Libreria casuale
music-library-random-mode-desc = Riproduci casualmente dall'intera libreria musicale senza ripetere subito lo stesso brano
music-loading-title = Caricamento
music-menu-nbs-title = Musica NBS - Pagina { $arg0 }/{ $arg1 }
music-menu-playlist-title = Playlist: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = Ricerca NBS: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-title = Cerca: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-title = Dischi musicali - Pagina { $arg0 }/{ $arg1 }
music-mode-switched = Passato a { $arg0 }
music-nearest-jukebox-missing = Nessun giradischi trovato entro { $arg0 ->
        [one] { $arg0 } blocco
       *[other] { $arg0 } blocchi
    }
music-next-page = Pagina successiva
music-no-disc = Nessun disco
music-no-files = Non sono disponibili file musicali
music-no-search-results-rich = Nessun brano trovato contenente "{ $keyword }"
music-now-playing-rich = In riproduzione: { $music } al giradischi { $location }
music-online-partial-failure = Richieste alle sorgenti non riuscite: { $arg0 ->
        [one] { $arg0 } richiesta
       *[other] { $arg0 } richieste
    }; sono mostrati i risultati disponibili
music-online-rate-limited = Troppe richieste musicali. Riprova tra { $arg0 ->
        [one] { $arg0 } secondo
       *[other] { $arg0 } secondi
    }.
music-online-searching = Ricerca di "{ $arg0 }" nelle sorgenti locali e online...
music-original-author = Autore originale: { $arg0 }
music-page-out-of-range = Pagina non valida. L'ultima pagina è { $arg0 }
music-page-total = { $arg0 ->
        [one] { $arg0 } brano
       *[other] { $arg0 } brani
    } in totale
music-page-usage-desc = Passa a una pagina
music-partial-results = Richieste alle sorgenti non riuscite: { $arg0 ->
        [one] { $arg0 } richiesta
       *[other] { $arg0 } richieste
    }; sono mostrati i risultati disponibili
music-paused = In pausa
music-plasmo-voice-unavailable = Il brano è iniziato, ma non puoi sentirlo perché Plasmo Voice non è installato, connesso o attivo
music-play-next = Riproduci il prossimo
music-play-random = Riproduzione casuale
music-playback-failed = Impossibile riprodurre "{ $arg0 }". Controlla la sorgente o il file musicale e riprova.
music-playback-status = Stato riproduzione: { $arg0 }
music-playing = In riproduzione
music-playing-now-rich = In riproduzione: { $music }
music-playlist-import-button = Importa playlist
music-playlist-import-done = Importazione di «{ $arg0 }» completata: { $arg1 } su { $arg2 ->
        [one] { $arg2 } brano
       *[other] { $arg2 } brani
    }.
music-playlist-import-failed = Importazione playlist non riuscita: { $arg0 }
music-playlist-import-prompt = Scegli una piattaforma, quindi incolla l'ID o il link di una playlist pubblica:
music-playlist-import-usage-desc = Importa una playlist pubblica da un'altra piattaforma musicale
music-playlist-importing = Importazione playlist...
music-playlist-reference-prompt = Incolla l'ID o il link di una playlist pubblica
music-prev-page = Pagina precedente
music-queue-empty = La coda è vuota
music-queue-move-down = Sposta giù
music-queue-move-up = Sposta su
music-queue-remove = Rimuovi brano
music-queue-summary = Coda: { $arg0 ->
        [one] { $arg0 } brano
       *[other] { $arg0 } brani
    }
music-queue-summary-description = Pagina { $arg0 }/{ $arg1 }
music-random-add = Aggiungi brano casuale
music-random-added-to-queue-rich = Aggiunto casualmente alla coda: { $music }
music-random-disc = Disco casuale
music-random-disc-got-rich = Disco musicale casuale ottenuto: { $music }
music-random-mode = Lista casuale
music-random-mode-desc = Riproduci la lista in ordine casuale senza ripetere subito lo stesso brano
music-rank-down = Spostato verso il basso
music-rank-up = Spostato in alto
music-reload-done = Ricaricamento completato: { $arg0 ->
        [one] { $arg0 } brano locale
       *[other] { $arg0 } brani locali
    }; sorgenti LX disponibili { $arg1 }/{ $arg2 }; abbonamenti controllati { $arg3 }, aggiornati { $arg4 }
music-reload-failed = Ricaricamento della musica non riuscito: { $arg0 }
music-reload-issues = Problemi di ricaricamento: { $arg0 }
music-reload-partial = Ricaricamento completato conservando lo stato precedente: { $arg0 ->
        [one] { $arg0 } brano locale
       *[other] { $arg0 } brani locali
    }; sorgenti LX disponibili { $arg1 }/{ $arg2 }; abbonamenti controllati { $arg3 }, aggiornati { $arg4 }
music-reload-started = Ricaricamento dei file musicali e delle sorgenti personalizzate...
music-remote-source-error = errore di aggiornamento: { $arg0 }
music-remote-source-import-done = Sottoscrizione della sorgente LX importata: { $arg0 }
music-remote-source-import-failed = Importazione della sorgente LX non riuscita: { $arg0 }
music-remote-source-import-started = Importazione della sottoscrizione della sorgente LX...
music-remote-source-remove-done = Sottoscrizione della sorgente LX rimossa: { $arg0 }
music-remote-source-remove-failed = Impossibile rimuovere la sottoscrizione della sorgente LX: { $arg0 }
music-remote-source-remove-not-found = Sottoscrizione della sorgente LX non trovata: { $arg0 }
music-remote-source-update-done = Verifica: { $arg0 ->
        [one] { $arg0 } abbonamento
       *[other] { $arg0 } abbonamenti
    }; aggiornati: { $arg1 }; errori: { $arg2 }.
music-remote-source-update-failed = Aggiornamento della sorgente LX non riuscito: { $arg0 }
music-remote-source-update-started = Verifica delle sottoscrizioni delle sorgenti LX importate...
music-remote-source-updated-at = aggiornato: { $arg0 }
music-remote-sources-title = Sottoscrizioni di sorgenti LX importate: { $arg0 }
music-removed-from-queue-rich = Rimosso dalla coda: { $music }
music-repeat-one-mode = Ripeti un brano
music-repeat-one-mode-desc = Ripeti il brano corrente finché non passi manualmente al successivo
music-request-error = Richiesta non riuscita: { $arg0 }
music-rhythm-animation-offset = Scostamento animazione { $arg0 } ms
music-rhythm-calibration = Calibrazione latenza
music-rhythm-calibration-accumulating = Conferma del ritmo stabile… continua a suonare
music-rhythm-calibration-adapting = Sincronizzazione in corso… continua a seguire il ritmo
music-rhythm-calibration-complete = Test di latenza completato · gioco ritmico pronto
music-rhythm-calibration-done = Test di latenza completato · impostazioni salvate
music-rhythm-calibration-failed = L'audio del test si è interrotto. Il test è stato chiuso in sicurezza; avvialo di nuovo
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Tastiera visiva → Mouse visivo
music-rhythm-calibration-listen = Ascolta una volta il ritmo generato · non premere ancora
music-rhythm-calibration-minecraft-instruction = Segui il ritmo Minecraft Note Block e premi 1–4
music-rhythm-calibration-no-input = Nessun input rilevato. Controlla i tasti e il volume, poi continua
music-rhythm-calibration-out-of-range = Il ritardo misurato è fuori dall'intervallo supportato. Controlla le impostazioni audio
music-rhythm-calibration-plasmo-instruction = Ora segui: ripeti il ritmo Plasmo Voice e premi 1–4 a ogni colpo
music-rhythm-calibration-plasmo-ready = Test Minecraft Note Block completato; prima ascolta lo stesso ritmo tramite Plasmo Voice
music-rhythm-calibration-pointer-instruction = Fai clic sinistro o destro quando la nota attraversa la linea.
music-rhythm-calibration-prompt-confirm = Avvia test completo
music-rhythm-calibration-prompt-confirm-hint = Ritorno automatico al termine.
music-rhythm-calibration-prompt-later = Non ora
music-rhythm-calibration-prompt-reason = Fai un test di latenza prima di giocare.
music-rhythm-calibration-prompt-title = Test di latenza richiesto prima di giocare
music-rhythm-calibration-realigning = Il flusso audio si è interrotto. La dimostrazione riparte e i campioni completi restano salvati
music-rhythm-calibration-required = Completa il test di latenza prima di avviare il gioco ritmico
music-rhythm-calibration-reset-done = Calibrazione della latenza azzerata
music-rhythm-calibration-result-hold = Tutti i test sono completati. Controlla il risultato prima del ritorno automatico
music-rhythm-calibration-stage-minecraft = Blocco musicale di Minecraft
music-rhythm-calibration-stage-plasmo = Test audio Plasmo Voice e input
music-rhythm-calibration-stage-pointer = Mouse
music-rhythm-calibration-stage-visual = Test visivo e di input
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
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
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
music-rhythm-preparing = Attendi che il brano sia memorizzato completamente e analizzato nel ritmo
music-rhythm-ready = Mappa completa pronta — inizio dal primo battito
music-rhythm-requires-playback = Prima riproduci un brano su questo giradischi
music-rhythm-reset-calibration = Reimposta calibrazione latenza
music-rhythm-results = Risultati
music-rhythm-score = Punteggio: { $arg0 }
music-rhythm-select-track = Seleziona brano ritmico
music-rhythm-spatial-no-room = Non c’è abbastanza spazio per la mira spaziale. Spostati in un’area più aperta.
music-rhythm-spatial-slider-hint = Segui la stella mobile e clicca ogni punto · non tenere premuto
music-rhythm-start-game = Avvia gioco
music-rhythm-waiting-for-player = In attesa che un giocatore avvii il gioco ritmico
music-sample-rate = Frequenza di campionamento: { $arg0 }
music-search-button = Cerca brani
music-search-keyword-required = La parola chiave di ricerca non può essere vuota
music-search-keyword-too-long = Ricerca troppo lunga. Limite: { $arg0 ->
        [one] { $arg0 } carattere
       *[other] { $arg0 } caratteri
    }.
music-search-usage-desc = Cerca brani
music-section-all = Tutta la musica
music-section-nbs = Musica NBS
music-select-music = Seleziona musica
music-sequential-mode = Ripeti lista
music-sequential-mode-desc = Riproduci la lista in ordine e ricomincia dall'inizio
music-size = Dimensioni: { $arg0 }
music-sort-button = Ordina
music-sort-current = Attuale: { $arg0 }
music-sort-default = Ordine predefinito
music-sort-most-played = Più riprodotti
music-sort-recently-played = Riprodotti di recente
music-sound-settings = Impostazioni audio
music-source-actions = azioni: { $arg0 }
music-source-available = disponibile
music-source-capabilities = capacità: { $arg0 }
music-source-error = errore: { $arg0 }
music-source-failures = errori: { $arg0 }
music-source-retry = riprova tra { $arg0 ->
        [one] { $arg0 } secondo
       *[other] { $arg0 } secondi
    }
music-source-unavailable = non disponibile
music-sources-empty = Nessuna sorgente musicale LX personalizzata trovata
music-sources-title = Sorgenti musicali LX personalizzate: { $arg0 }
music-stop-eject = Ferma ed espelli
music-stop-eject-done = Riproduzione fermata e disco espulso
music-stopped = Arrestato
music-track-details = Dettagli del brano
music-unavailable-disc = Disco non disponibile
music-unavailable-disc-description = Questo disco MIK non è nel catalogo attuale; può comunque essere fermato ed espulso

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
ban-dialog-back = Indietro
ban-dialog-cancel = Annulla
ban-dialog-confirm-action = Escludi giocatore
ban-dialog-confirm-severity = Gravità
ban-dialog-confirm-title = Conferma esclusione
ban-dialog-never-joined-confirm-action = Escludi comunque
ban-dialog-never-joined-title = Il giocatore non è mai entrato
ban-dialog-never-joined-warning = Nessun accesso al server registrato per { $arg0 }. Verifica il nome per evitare di escludere la persona sbagliata.
ban-dialog-next = Avanti
ban-dialog-player-input = Inserisci manualmente il nome
ban-dialog-player-manual = Inserimento manuale
ban-dialog-player-online = Giocatori attualmente online
ban-dialog-select-title = Seleziona giocatore
ban-dialog-severity-title = Seleziona gravità
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
maintenance-set = Modalità manutenzione { $arg0 }
maintenance-state-disabled = disattivata
maintenance-state-enabled = attivata
maintenance-status = Modalità manutenzione: { $arg0 }
maintenance-unchanged = La modalità manutenzione è già { $arg0 }
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
unban-command-description = Revoca un ban mantenendo lo storico
whitelist-already-whitelisted = { $arg0 } è già nella lista bianca
whitelist-invalid-name = Formato del nome del giocatore non valido
whitelist-kick-apply-rich = Candidati a { $site } per la lista bianca
whitelist-kick-title = Non sei nella whitelist
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = Aggiunta whitelist temporanea per { $arg0 } per { $arg1 }
whitelist-temp-added-rich = Aggiunta whitelist temporanea per { $player } per { $duration }
whitelist-temp-command-description = Aggiungi una voce temporanea nella whitelist di 1 ora
whitelist-temp-duration = 1 ora
whitelist-temp-usage-desc = Aggiungi una voce temporanea nella whitelist di 1 ora

## Shell · Menus, server information and scripting

main-afk-current-afk = Attualmente AFK
main-afk-current-online = Attualmente online
main-afk-status = Stato AFK
main-announcements = Annunci
main-home = Case
main-language = Lingua
main-language-auto = Automatico
main-links = Link
main-map = Mappa web
main-menu-title = Menu principale
main-music = Musica
main-name-tag = Etichette nome
main-profile = Profilo
main-pvp = PvP
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
stat-play-time = Tempo di gioco
stat-role = Ruolo
stat-world = Mondo
weblogin-command-description = Conferma un codice di accesso Mik Web
weblogin-confirmed = Confermato il login di Mik Web. Torna al tuo browser per continuare.
weblogin-invalid-code = Codice di accesso web non valido
