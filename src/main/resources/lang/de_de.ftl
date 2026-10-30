### MIK · de-de
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = { $case ->
       *[nominative] Grundstück
        [genitive] Grundstücks
        [dative] Grundstück
        [accusative] Grundstück
    }
    .gender = neuter

-vehicle-model = { $case ->
       *[nominative] Modell
        [genitive] Modells
        [dative] Modell
        [accusative] Modell
    }
    .gender = neuter

## Shared · General

back = Zurück
back-to-main = Zurück zum Hauptmenü
click-open = Zum Öffnen klicken
close = Schließen
current-off = Aktuell: Aus
current-on = Aktuell: Ein
password-already-connected = Dieses Konto ist bereits verbunden.
password-already-set = Ein Passwort ist bereits eingerichtet. Ändere es mit /password change.
password-busy = Zu viele Versuche. Bitte später erneut versuchen.
password-change = Passwort ändern
password-change-done = Passwort geändert.
password-checking = Kontosicherheit wird geprüft…
password-command-description = Passwortschutz des Kontos verwalten
password-confirm = Neues Passwort bestätigen
password-conflict = Die Kontosicherheit wurde geändert. Wiederhole den Vorgang.
password-cooldown = Die Kontoprüfung ist vorübergehend eingeschränkt. Versuche es in { $arg0 ->
        [one] { $arg0 } Sekunde
       *[other] { $arg0 } Sekunden
    } erneut.
password-current = Aktuelles Passwort
password-denied = Du darfst das Passwort dieses Kontos nicht zurücksetzen.
password-disabled = Passwort: nicht eingerichtet
password-enabled = Passwort: eingerichtet
password-invalid = Falsches Passwort. Bitte erneut versuchen.
password-keep-role = Moderatoren und Betreuer können ihr Passwort nicht entfernen.
password-login = Anmelden
password-login-expired = Deine Anmeldebestätigung ist abgelaufen. Verbinde dich erneut.
password-login-hint = Gib das Serverpasswort ein. Vergessen? Bitte das Team um ein neues Passwort.
password-mismatch = Die neuen Passwörter stimmen nicht überein.
password-new = Neues Passwort
password-nomination-required = Richte vor der Bewerbung als Moderator ein Passwort ein: /password set
password-not-set = Es ist kein Passwort eingerichtet. Lege eines mit /password set fest.
password-remove = Passwort entfernen
password-remove-done = Passwort entfernt.
password-remove-question = Nach dem Entfernen wird beim Anmelden kein Passwort mehr geprüft. Gib zur Bestätigung dein aktuelles Passwort ein.
password-required-application = Während einer laufenden Teambewerbung oder ausstehenden Ernennung kannst du dein Passwort nicht entfernen.
password-required-role = Teamkonten benötigen ein Passwort. Bitte ein berechtigtes Teammitglied vor der Anmeldung um ein neues Passwort.
password-reset = Passwort zurücksetzen
password-reset-console = Passwort für { $arg0 } zurückgesetzt: { $arg1 }. Übergib es nach Identitätsprüfung über einen vertrauenswürdigen Kanal.
password-reset-done = Passwort für { $arg0 } zurückgesetzt. Kopiere es jetzt und übergib es nach Identitätsprüfung; es kann nicht erneut angezeigt werden.
password-reset-question = Passwort von { $arg0 } zurücksetzen? Das alte Passwort wird ungültig und die Verbindungen werden getrennt. Bestätige mit deinem eigenen Passwort.
password-retry = Erneut versuchen
password-rules = Verwende { $arg0 }–{ $arg1 } Zeichen. Spielernamen und häufige Passwörter sind nicht erlaubt.
password-set = Passwort einrichten
password-set-done = Passwort eingerichtet.
password-set-first = Vor der Bewerbung Passwort einrichten
password-storage = Die Kontosicherheit ist nicht verfügbar. Versuche es später oder kontaktiere den Serverbetreiber.
password-timeout = Die Passwortprüfung wurde abgebrochen oder ist abgelaufen. Verbinde dich erneut.
password-tip-set-mm = Lege mit <gold><u>/password</u></gold> ein <yellow>Anmeldepasswort</yellow> fest, um dein Konto zu schützen.
password-title = Kontosicherheit

player-only = Dieser Befehl kann nur von Spielern verwendet werden
unknown = Unbekannt
usage = Verwendung

## Shared · Language, roles and time

language-command-description = Spracheinstellungen öffnen
language-menu-title = Spracheinstellungen
language-selected = Ausgewählt
language-set = Sprache auf { $arg0 } gesetzt
role-custodian = Kustos
role-member = Mitglied
role-moderator = Moderator
role-new-player = Neuer Spieler
time-hours-minutes = { $arg0 }h { $arg1 }m
time-minutes = { $arg0 }m

## Shared · Menu presentation

bedrock-menu-back = Zurück
bedrock-menu-hotkey = Kurzbefehl (F)
bedrock-menu-primary = Hauptaktion
bedrock-menu-scroll-down = Nach unten
bedrock-menu-scroll-up = Nach oben
bedrock-menu-secondary = Weitere Aktion
interface-fov-apply = FOV anwenden
interface-fov-custom = Genauen Wert eingeben
interface-fov-description = An das Client-FOV anpassen; nur die Menüposition ändert sich. Standard: 70°.
interface-fov-input = Client-FOV: ganze Zahl von 30 bis 110
interface-fov-invalid = Gib eine ganze Zahl von 30 bis 110 ein.
interface-fov-title = Sichtfeld-Anpassung (FOV)
interface-scale-current = Aktuell: { $arg0 }
interface-scale-description = Textgröße multipliziert die Menügröße; beide Gruppen sind per Mausrad einstellbar.
interface-scale-layout-title = Menügröße
interface-scale-menu-title = Oberfläche & Text
interface-scale-native-hint = Diese Einstellungen gelten für Java-Raummenüs. Der Text nativer Formulare folgt deinen Client-Einstellungen.
interface-scale-preview = Textvorschau Aa 123
interface-scale-reset = Beide auf 100% zurücksetzen
interface-scale-text-title = Textgröße
menu-rhythm-game-display = Spielt Rhythmusspiel
menu-usage-display-mm = <aqua><bold>Im Menü</bold></aqua>
url-dialog-confirm = Öffnen
url-dialog-hint = Der Client fragt erneut nach der Bestätigung zum Öffnen des Links.
url-dialog-question-rich = { $label } öffnen?
url-dialog-title = { $arg0 } öffnen

## Player · Client and presence

client-mods-undetected-rich = Empfohlene Mods: { $mods }. Klicke zum Herunterladen auf einen Namen.
client-tip-axiom-flight-speed-mm = <white>Halte <yellow>die linke Alt-Taste</yellow> gedrückt, um das <aqua>Axiom</aqua>-Menü zu öffnen. Passe die Fluggeschwindigkeit mit dem senkrechten Regler rechts oder mit <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click> an.</white>
client-tip-axiom-intro-rich = { $mod } ist eine Bauhilfe mit kopierbaren Bauplänen, Gelände- und Anzeigeentitätsbearbeitung, Blockplatzierung ohne Updates, Bewegung durch Wände und mehreren gespeicherten Schnellzugriffsleisten. Klicke zum Herunterladen auf den Namen.
client-tip-voxy-intro-rich = { $mod } stellt ferne Landschaften mit wenig Aufwand dar und ist auf schwächeren wie stärkeren PCs einen Versuch wert. Klicke zum Herunterladen auf den Namen.
client-version-behind-mm = <white>Dein Client <yellow>{ $clientVersion }</yellow> ist älter als der Server <green>{ $serverVersion }</green>. Ein Update wird empfohlen.</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> Spieler in deiner Nähe ist <gold>unsichtbar</gold>
       *[other] <gold>{ $count }</gold> Spieler in deiner Nähe sind <gold>unsichtbar</gold>
    }</yellow> <gray>·</gray> <gray>nächster etwa { $distance ->
        [one] { $distance } Block
       *[other] { $distance } Blöcke
    } entfernt</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = { $player } ist durch die Hintertür auf den Server geschlichen
    .item-2 = { $player } hat offenbar den geheimen Weg gefunden
    .item-3 = { $player } ist gerade aus einem Busch aufgetaucht
presence-quit-templates =
    .item-1 = { $player } ist leise durch die Hintertür verschwunden
    .item-2 = { $player } hat sich zum Speichern ausgeloggt
    .item-3 = { $player } wurde von der Realität zurückgerufen
welcome-first-join-mm = <gold><bold>Willkommen bei MIK!</bold></gold><newline><gray>Schau zuerst auf die <click:open_url:'https://mcmik.top'><yellow>Website</yellow></click> und ins <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>. Mit <yellow>/menu</yellow> öffnest du das Hauptmenü.</gray>
welcome-return-mm = <gold><bold>Willkommen zurück!</bold></gold><newline><gray>Dein letzter Besuch war vor <yellow>{ $arg0 ->
        [one] { $arg0 } Tag
       *[other] { $arg0 } Tagen
    }</yellow>. Mit <yellow>/menu</yellow> findest du häufig verwendete Funktionen.</gray>

## Player · Guidance

tip-label = TIPP

## Player · AFK

afk-already-afk-mm = <gray>Du bist bereits <gold>AFK</gold></gray>
afk-command-description = AFK-Status aktivieren
afk-default-statuses =
    .item-1 = ist kurz von der Tastatur weg
    .item-2 = in den Energiesparmodus gewechselt
    .item-3 = läuft im Hintergrund
    .item-4 = starrt ins Leere
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>ist jetzt AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>hat eine Notiz hinterlassen:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>hat den Status geändert:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>zur Tastatur zurückgekehrt</green>
    .item-2 = <gray>*</gray> <player> <green>ist zurück</green>
afk-not-afk-mm = <gray>Du bist derzeit nicht <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>Der AFK-Status darf höchstens <white>{ $arg0 }</white> Zeichen lang sein</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · Übereinstimmungen: { $arg2 ->
        [one] { $arg2 } Eintrag
       *[other] { $arg2 } Einträge
    } · Verlauf: { $arg3 }
player-association-header = [Login-Verknüpfung] { $arg0 } · gemeinsame Adresshistorie: { $arg1 } Spieler.
player-association-hover = Historische Netzwerkdaten stimmen überein. Dies ist nur ein Prüfhinweis; Adressen werden nicht angezeigt.
player-association-more-addresses = Weitere { $arg0 ->
        [one] { $arg0 } Eintrag
       *[other] { $arg0 } Einträge
    }
player-association-more-players = Weitere { $arg0 } Spieler
player-association-time-days = vor { $arg0 ->
        [one] { $arg0 } Tag
       *[other] { $arg0 } Tagen
    }
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } Eintrag
       *[other] { $arg1 } Einträge
    })
player-association-time-separator = ,
player-association-time-today = heute

## Player · Profile and tab list

nametag-clear-all-error = Fehler beim Löschen der Namensschilder. Bitte versuche es später erneut
nametag-clear-all-success = ✔ Alle Namensschilder gelöscht
nametag-clear-error = Fehler beim Löschen von { $arg0 }
nametag-clear-success = ✔ { $arg0 } gelöscht
nametag-click-fill-command = Klicken, um den Befehl einzufügen
nametag-command-description = Namensschilder individuell anpassen
nametag-copy-hover = MiniMessage-Quelltext in die Zwischenablage kopieren
nametag-copy-label = [kopieren]
nametag-effect-label = Ergebnis
nametag-empty-content-rich = Der Inhalt darf nicht leer sein. Verwende zum Löschen { $command }
nametag-format-docs = Formatierungsdokumentation
nametag-leading-label = Namenspräfix
nametag-no-permission = Du darfst diesen Befehl nicht verwenden
nametag-online-editor = Online-Editor
nametag-preview-label = Vorschau
nametag-sample-message = Beispielnachricht
nametag-set-error = Fehler beim Festlegen von { $arg0 }. Bitte versuche es später erneut
nametag-set-success = ✔ { $arg0 } festgelegt
nametag-tip-customize-mm = Passe mit <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> dein <light_purple>Namenspräfix und -suffix</light_purple> an.
nametag-title = Namensschilder
nametag-too-long = { $arg0 } ist zu lang. Die maximale Länge beträgt { $arg1 } Zeichen
nametag-trailing-label = Namenszusatz
nametag-unset = (nicht festgelegt)
nametag-unsupported-placeholder-rich = Nur die PlaceholderAPI-Variablen { $supported } werden unterstützt, nicht { $unsupported }
nametag-user-data-load-failed = Benutzerdaten konnten nicht geladen werden. Bitte versuche es später erneut
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Online-Spieler: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Zur vorherigen Teleportations- oder Todesposition zurückkehren
back-empty-mm = <gray>Keine Rückkehrposition verfügbar</gray>
back-pending-mm = <gray>Teleportation läuft, bitte warten</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>{ $steps ->
        [one] { $steps } Schritt
       *[other] { $steps } Schritte
    } zurückgegangen</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>Teleportation fehlgeschlagen. Bitte versuche es erneut</red>
back-tip-return-mm = Mit <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> kehrst du zum Standort vor dem letzten Teleport oder Tod zurück.
back-tip-undo-mm = Mit <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> machst du dein letztes <yellow>/back</yellow> rückgängig.
back-undo-empty-mm = <gray>Kein <white>/back</white> zum Rückgängigmachen</gray>
back-undo-success-mm = <green>Letztes <white>/back</white> rückgängig gemacht</green>
back-undo-world-missing-mm = <red>Die Zielwelt ist nicht verfügbar. Rückgängig machen nicht möglich</red>
back-world-missing-mm = <red>Die Zielwelt ist nicht verfügbar. Rückkehr nicht möglich</red>
boundary-near-mm = <yellow>Du näherst dich der Weltgrenze</yellow>
boundary-reached-mm = <red>Du hast die Weltgrenze erreicht</red>
fly-command-description = Persoenliches Fliegen umschalten
fly-disabled = Fliegen deaktiviert
fly-enabled = Fliegen aktiviert
fly-native-mode = Dein /fly-Zugang ist deaktiviert. Dieser Spielmodus erlaubt weiterhin das Fliegen.
fly-speed-click = Fluggeschwindigkeit auf { $arg0 }x setzen
fly-speed-current = Fluggeschwindigkeit: { $arg0 }x
fly-speed-presets = Voreinstellungen
fly-speed-reset = Fluggeschwindigkeit auf 1x zurueckgesetzt
fly-speed-reset-action = Zuruecksetzen
fly-speed-reset-click = Fluggeschwindigkeit auf 1x zuruecksetzen
fly-speed-set = Fluggeschwindigkeit auf { $arg0 }x gesetzt
fly-state-disabled = Deaktiviert
fly-state-enabled = Aktiviert
fly-status = Fliegen: { $arg0 } | Geschwindigkeit: { $arg1 }x
flyspeed-command-description = Persoenliche Fluggeschwindigkeit anzeigen oder setzen
home-action-create = Zuhause erstellen
home-action-delete = Zuhause löschen
home-action-icon = Symbol aus Hand setzen
home-action-teleport = Teleportieren
home-action-update = Standort aktualisieren
home-already-exists = Ein Zuhause mit diesem Namen gibt es bereits
home-back = Zurück
home-confirm-delete = Löschen bestätigen
home-confirm-delete-detail-rich = Homepunkt { $home } löschen
home-confirm-delete-warning = Dies kann nicht rückgängig gemacht werden
home-confirm-update = Aktualisierung bestätigen
home-confirm-update-detail-rich = Homepunkt { $home } auf diesen Ort aktualisieren
home-delete-command-description = Homepunkt löschen
home-delete-usage-desc = Homepunkt löschen
home-deleted-rich = Homepunkt { $home } gelöscht
home-distance = Entfernung: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcke
    }
home-distance-other-world = andere Welt
home-empty-description = Stell dich dorthin, wo du einen Homepunkt speichern möchtest
home-empty-title = Noch keine Homepunkte
home-icon = Symbol: { $arg0 }{ $arg1 }
home-icon-default-note = · Standard
home-icon-hand-invalid = Der Gegenstand in deiner Haupthand kann nicht als Homepunkt-Symbol verwendet werden
home-icon-updated-rich = Symbol von Homepunkt { $home } aktualisiert
home-invalid-icon = Dieses Material kann nicht als Homepunkt-Symbol verwendet werden
home-invalid-name = Homepunkt-Namen dürfen die Zeichen < > : nicht enthalten
home-list-rich = Homepunkte: { $homes }
home-location = Position: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = Die Positionsdaten dieses Homepunkts sind ungültig
home-max-reached = Du kannst höchstens { $arg0 } Homepunkte setzen
home-menu-title = Homepunkte
home-no-homes = Du hast noch keine Homepunkte gesetzt
home-not-found-rich = Homepunkt { $home } existiert nicht
home-set-command-description = Homepunkt an deiner aktuellen Position setzen
home-set-rich = Homepunkt { $home } gesetzt
home-set-usage-desc = Homepunkt an deiner aktuellen Position setzen
home-summary-count = Gesetzt: { $arg0 } / { $arg1 }
home-teleport-command-description = Zu einem Homepunkt teleportieren
home-teleport-failed = Teleportation fehlgeschlagen. Bitte versuche es erneut
home-teleported-rich = Zu Homepunkt { $home } teleportiert
home-tip-create-mm = Speichere deinen Standort mit <green><u><click:suggest_command:'/sethome '>/sethome Name</click></u></green> und kehre mit <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> zurück.
home-tip-return-mm = Öffne <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> und wähle ein Zuhause zum Teleportieren.
home-updated-rich = Position von Homepunkt { $home } aktualisiert
home-usage-book = Befehlshilfe
home-usage-delhome = /delhome <name>  Homepunkt löschen
home-usage-home = /home tp <name>  Direkt teleportieren
home-usage-icon = /home icon <material> <name>  Symbol festlegen
home-usage-name-rule = Homepunkt-Namen dürfen < > : nicht enthalten
home-usage-sethome = /sethome <name>  Aktuelle Position speichern
home-world = Welt: { $arg0 }
home-world-end = · Das Ende
home-world-nether = · Nether
reback-command-description = Letztes /back rückgängig machen
teleport-accept-command-description = Die ausstehende Teleportanfrage annehmen
teleport-allow = Teleportationen durch andere Spieler
teleport-bare-entity-selector = Um versehentliche Teleportationen zu vermeiden, erfordert @e eckige Klammern. Verwende einen ausdrücklichen Selektor wie @e[type=…].
teleport-block-afk = Während AFK blockieren
teleport-denied = { $player } lehnt derzeit Teleportationen durch andere Spieler ab
teleport-deny-command-description = Die ausstehende Teleportanfrage ablehnen
teleport-force-command-description = Teleportation ohne Beachtung der Spielereinstellungen erzwingen (nur für Administratoren)
teleport-menu-title = Teleport-Privatsphäre
teleport-moved-here = Du wurdest von { $arg0 } hierher teleportiert
teleport-policy-always-allow = Immer erlauben
teleport-policy-always-deny = Immer ablehnen
teleport-policy-require-consent = Meine Zustimmung erforderlich
teleport-request-accept = Annehmen
teleport-request-approved = { $player } hat deine Teleportanfrage angenommen
teleport-request-deny-action = Ablehnen
teleport-request-expired = Die Teleportanfrage ist abgelaufen
teleport-request-failed = Die Teleportation konnte nicht ausgeführt werden. Bitte versuche es erneut
teleport-request-none = Du hast keine ausstehende Teleportanfrage
teleport-request-received = { $sender } möchte dich teleportieren: { $command }
teleport-request-rejected = Teleportanfrage abgelehnt
teleport-request-sent = Teleportanfrage an { $player } gesendet
teleport-summary-afk = Immer erlaubt, während AFK abgelehnt
teleport-summary-allow = Teleportationen durch andere Spieler immer erlaubt
teleport-summary-consent = Teleportationen durch andere Spieler benötigen meine Zustimmung
teleport-summary-deny = Teleportationen durch andere Spieler immer abgelehnt
tpany-command-description = Zur Position eines Spielers teleportieren, online oder offline
tpany-done-offline-rich = Zur letzten bekannten Position von { $player } teleportiert: { $world } { $location }
tpany-done-online-rich = Zur aktuellen Position von { $player } teleportiert
tpany-not-found-rich = Position von { $player } nicht gefunden
tpany-usage = Verwendung
tpany-usage-command = /tpany <player>
tpany-usage-desc = Zur gespeicherten Position eines Spielers teleportieren, auch wenn er offline ist

## Player · PVP

pvp-admin-menu-title = PVP-Verwaltung - Seite { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = Berittene Mobs können angegriffen werden
pvp-allow-mounted-damage-desc = Wenn aktiviert, kann ein anderer Spieler dein Reittier angreifen, wenn ihr beide PVP aktiviert habt
pvp-auto-enable-desc = Greife einen Spieler mit aktiviertem PVP zweimal hintereinander an
pvp-auto-enable-label = Automatische Aktivierung
pvp-auto-enable-warning-actionbar-mm = <yellow>Greife noch einmal an, um PVP automatisch zu aktivieren</yellow>
pvp-auto-enabled-actionbar-mm = <green>PVP automatisch aktiviert</green>
pvp-back-admin = Zurück zur PVP-Verwaltung
pvp-combat-locked-mm = <red>Du kannst PVP im Kampf nicht deaktivieren</red> <dark_gray>|</dark_gray> <gray>Verbleibend: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> Sekunde
       *[other] <yellow>{ $arg0 }</yellow> Sekunden
    }</gray>
pvp-combat-tag-label = Abklingzeit im Kampf
pvp-combat-tag-value = { $arg0 } Sek.
pvp-command-description = PVP-Status umschalten
pvp-enable = PVP aktivieren
pvp-enable-desc = Wenn aktiviert, kannst du andere Spieler mit aktiviertem PVP angreifen und sie können dich angreifen
pvp-enable-on-death = Aktiviere PVP beim Tod
pvp-enable-on-death-desc = Wenn aktiviert, wird PVP nach deinem Tod automatisch eingeschaltet
pvp-enable-on-death-label = Bei Tod aktivieren
pvp-enabled-on-death-mm = <yellow>PVP wurde aktiviert, weil du gestorben bist</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>PVP-Verwaltung für Online-Spieler öffnen</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>PVP-Einstellungen öffnen</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Online-Spieler verwalten</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Deinen PVP-Status anzeigen</gray>
pvp-help-title-mm = <gold><bold>PVP-Befehle</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Deinen PVP-Status umschalten</gray>
pvp-menu-title = PVP-Einstellungen
pvp-mob-protected-actionbar-mm = <yellow>Dieser Mob ist vor PVP-Schaden geschützt</yellow>
pvp-mob-protection-label = Mob-Schutz
pvp-mounted-damage-label = Schaden an Reittieren
pvp-next-page = Nächste Seite
pvp-no-permission-mm = <red>Du darfst den PVP-Status anderer Spieler nicht verwalten</red>
pvp-player-not-found-mm = <red>Online-Spieler nicht gefunden</red>
pvp-prev-page = Vorherige Seite
pvp-protect-mobs = Beschütze deine Mobs
pvp-protect-mobs-desc = Beschütze Mobs, die du gezähmt hast oder auf denen du reitest
pvp-set-by-staff-mm = <aqua>Das Team hat deinen PVP-Status gesetzt auf</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = PVP-Status von { $arg0 } auf { $arg1 } gesetzt
pvp-set-other-rich = PVP-Status von { $player } auf { $state } gesetzt
pvp-state-label = PVP
pvp-state-off = Aus
pvp-state-on = Ein
pvp-status-player = Spieler: { $arg0 }
pvp-status-player-rich = Spieler: { $player }
pvp-status-title-mm = <gold><bold>PVP-Status</bold></gold>
pvp-summary-disabled = PVP deaktiviert
pvp-summary-enabled = PVP aktiviert
pvp-target-disabled-actionbar-mm = <yellow>Für diesen Spieler ist PVP nicht aktiviert</yellow>
pvp-target-menu-title = PVP-Einstellungen - { $arg0 }
pvp-tip-protection-mm = Mit <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple> schaltest du dein PVP um. Zum Kämpfen müssen es <green>beide Spieler aktivieren</green>.
pvp-toggled-off-mm = <gray>PVP deaktiviert</gray> <dark_gray>|</dark_gray> <gray>Andere Spieler können dich nicht angreifen</gray>
pvp-toggled-on-mm = <green>PVP aktiviert</green> <dark_gray>|</dark_gray> <gray>Du kannst jetzt gegen andere Spieler mit aktiviertem PVP kämpfen</gray>

## Player · Effects

effect-cleanup-all = [Alle Effekte entfernen]
effect-cleanup-blocked = Effekte können nur im Kreativ- oder Zuschauermodus außerhalb eines PVP-Kampfes entfernt werden.
effect-cleanup-expired = Diese Anfrage zum Entfernen ist abgelaufen.
effect-cleanup-negative = [Negative Effekte entfernen]
effect-cleanup-prompt-rich = Du hast durch { $source } negative Effekte erhalten. Entfernen?
effect-cleanup-result = { $count ->
        [one] { $count } Effekt entfernt.
       *[other] { $count } Effekte entfernt.
    }
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] eine verweilende Trankwolke
        [ARROW] einen Pfeil
        [ATTACK] einen Angriff
        [COMMAND] einen Befehl
        [FOOD] Nahrung
        [PLUGIN] ein Plugin
        [POTION_DRINK] das Trinken eines Tranks
        [POTION_SPLASH] einen Wurftrank
        [WARDEN] einen Wärter
        [WITHER_ROSE] eine Witherrose
       *[OTHER] eine unbekannte Ursache
    }

## Player · Trash

trash-clear = Leeren
trash-clear-hint = Klicken, um alle Gegenstände sofort zu löschen;
    andernfalls werden sie { $arg0 } Sekunden nach dem Schließen automatisch gelöscht.
trash-command-description = Mülleimer öffnen
trash-sort = Sortieren
trash-sort-hint = Gleiche Stapel zusammenführen und nach Gegenstandstyp sortieren
trash-tip-dispose-mm = Öffne mit <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> den <yellow>Mülleimer</yellow> und lege unerwünschte Gegenstände hinein.
trash-title = Mülleimer

## Player · Utilities

hat-command-description = Gegenstand aus deiner Haupthand als Hut tragen
hat-empty-hand-mm = <red>Kein Gegenstand in deiner Haupthand</red> <gray>Halte einen Block oder Gegenstand und verwende dann /hat</gray>
hat-success-mm = <green>Gegenstand aus deiner Haupthand aufgesetzt</green> <dark_gray>|</dark_gray> <gray>Haupthand und Kopfrüstung wurden vertauscht</gray>
hat-tip-wear-mm = Mit <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> trägst du den Gegenstand in deiner <yellow>Haupthand</yellow> auf dem Kopf.
removeitems-command-description = Fallengelassene Gegenstände entfernen
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Entfernt: { $countValue ->
        [one] { $count } fallengelassener Gegenstand
       *[other] { $count } fallengelassene Gegenstände
    } · Welt { $world } { $location } { $radius }
removeitems-no-world = Welt konnte nicht ermittelt werden
removeitems-radius = Radius { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcke
    }
removeitems-tip-usage-mm = Entferne mit <aqua><u><click:suggest_command:'/rmitems '>/rmitems [Radius]</click></u></aqua> <yellow>herumliegende Gegenstände</yellow> in der Nähe.
self-kick-command-description = Dich selbst vom Server kicken
self-kick-reason-too-long = Der Kick-Grund darf höchstens { $arg0 } Zeichen lang sein
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Du hast den Server auf eigenen Wunsch verlassen. Grund: { $reason }
       *[third] { $player } hat sich selbst vom Server gekickt. Grund: { $reason }
    }
    .item-1 = { $player } hat sich selbst rausgeworfen, und der Grund ist tatsächlich: { $reason }
    .item-2 = { $player } hat sich tatsächlich wegen { $reason } vom Server gekickt!
    .item-3 = { $player } hat den Server freiwillig verlassen und diese Begründung hinterlassen: { $reason }
    .item-4 = { $player } hat den Selbst-Kick gewählt: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Du hast den Server auf eigenen Wunsch verlassen.
       *[third] { $player } hat sich selbst vom Server gekickt.
    }
    .item-1 = { $player } hat sich tatsächlich selbst rausgeworfen!
    .item-2 = { $player } hat den Server wortlos verlassen.
    .item-3 = { $player } hat einen tadellosen Selbst-Kick hingelegt.
    .item-4 = { $player } hat eigenhändig den Server-verlassen-Knopf gedrückt.

## Community · Chat and mentions

chat-copy-hover = Linksklick zum Kopieren der Nachricht
chat-delay-3s = 3 Sekunden
chat-delay-5s = 5 Sekunden
chat-delay-7s = 7 Sekunden
chat-delay-cancel-command-description = Ausstehende verzögerte Nachrichten abbrechen
chat-delay-cancelled = Ausstehende Nachricht abgebrochen
chat-delay-current = Aktuelle Chat-Verzögerung: { $arg0 }
chat-delay-nothing-to-cancel = Keine ausstehende Nachricht zum Abbrechen
chat-delay-off = Aus
chat-delay-preview-marker = [Ausstehend]
chat-delay-queued = Nachricht wird in { $arg0 } gesendet; mit /cancel oder /c abbrechen
chat-item-empty-hover = Kein Gegenstand in der Haupthand
chat-mention-all-hover = Alle sichtbaren Spieler in diesem Kanal benachrichtigen
chat-msg-command-description = Private Nachricht senden und zum privaten Chat wechseln
chat-player-hover = Linksklick, um eine private Nachricht zu beginnen
chat-private-enter = Zum privaten Chat mit { $arg0 } gewechselt
chat-private-label = MSG
chat-private-no-reply-target = Kein kürzlich angeschriebener Spieler ist online
chat-private-self = Du kannst keinen privaten Chat mit dir selbst starten
chat-private-target-not-found = Online-Spieler nicht gefunden: { $arg0 }
chat-private-target-offline = Der andere Spieler ist offline; zum öffentlichen Chat zurückgekehrt
chat-private-tip-channel-mm = Mit <aqua><u><click:suggest_command:'/msg '>/msg Spieler</click></u></aqua> in den privaten Chat wechseln, mit <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> zurück in den öffentlichen Chat.
chat-public-command-description = Öffentlichen Chat-Kanal wechseln
chat-public-enter = Zum öffentlichen Chat gewechselt
chat-reply-command-description = Dem zuletzt angeschriebenen Spieler antworten
chat-settings-menu-title = Chat-Einstellungen
chat-social-account-label = Plattformkonto
chat-social-bound-player-label = Verknüpfter Spieler
chat-social-name-label = Anzeigename
chat-social-unbound = Mit keinem Minecraft-Spieler verknüpft
chat-staff-command-description = Zum Teamchat wechseln
chat-staff-permission-missing = Berechtigung für den Teamchat fehlt; zum öffentlichen Chat zurückgekehrt
mention-action-bar = Hinweis in der Aktionsleiste
mention-action-bar-text = { $sender } hat dich erwähnt
mention-alerts = Erwähnungsbenachrichtigungen
mention-menu-title = Erwähnungsbenachrichtigungen
mention-mute-afk = Während AFK stummschalten
mention-sound = Benachrichtigungston
mention-summary-action-bar = Aktionsleiste
mention-summary-disabled = Benachrichtigungen deaktiviert
mention-summary-enabled = Benachrichtigungen aktiviert
mention-summary-enabled-afk = Benachrichtigungen aktiviert, während AFK stummgeschaltet
mention-summary-modes-afk = { $arg0 }, während AFK stummgeschaltet
mention-summary-sound = Ton
staffchat-enter = Teamchat beigetreten

## Community · Announcements

announcement-empty = Keine Ankündigungen
announcement-first-page = Neueste Mitteilungen erreicht
announcement-last-page = Älteste Mitteilungen erreicht
announcement-more-count = { $arg0 } weitere nicht angezeigt
announcement-new-count = { $arg0 } neu
announcement-newer = Neuere Mitteilungen
announcement-older = Ältere Mitteilungen
announcement-reload = Ankündigungen neu laden
announcement-reload-command-description = Ankündigungsdatei neu laden
announcement-reload-failed = Ankündigungen konnten nicht neu geladen werden; die bisherigen bleiben erhalten
announcement-reloaded = Ankündigungen neu geladen
announcement-scroll-hint = Scrollen zum seitlichen Blättern
announcement-unread-count = { $arg0 } ungelesen
announcement-updated = Ankündigungen aktualisiert
announcement-view-all = Alle Ankündigungen ansehen
announcement-view-command-description = Serverankündigungen ansehen

## Community · Identity and social platforms

identity-admin-binding-not-found = Keine eindeutige Spieler- und Plattformverknüpfung gefunden.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Für { $arg1 } entfernt: { $arg2 ->
        [one] { $arg2 } Identitätsverknüpfung
       *[other] { $arg2 } Identitätsverknüpfungen
    } ({ $arg0 }).
identity-admin-command-description = Externe Identitätsverknüpfungen prüfen oder aufheben
identity-admin-help = /bindadmin lookup <Spieler-oder-UUID>
    /bindadmin unlink <Spieler-oder-UUID> <Plattform> confirm
identity-admin-lookup-empty = Für diesen Spieler wurden keine Verknüpfungen gefunden.
identity-admin-lookup-title = Verknüpfungen von { $arg0 }:
identity-binding-details = · { $arg0 } · Bereich { $arg1 } · { $arg2 }
identity-binding-not-owned = Du hast keine Verknüpfung für diese Plattform.
identity-binding-unlinked = Entfernt: { $arg1 ->
        [one] { $arg1 } Identitätsverknüpfung
       *[other] { $arg1 } Identitätsverknüpfungen
    } ({ $arg0 }).
identity-bindings-empty = Du hast keine externen Plattformkonten verknüpft.
identity-bindings-title = Verknüpfte externe Identitäten:
identity-bindings-unlink-hint = Mit /bind unlink <Plattform> entfernst du alle Verknüpfungen einer Plattform.
identity-code-cancelled = Der ungenutzte Verknüpfungscode für { $arg0 } wurde verworfen.
identity-code-copy = Klicken, um den vollständigen Verknüpfungsbefehl zu kopieren
identity-code-expiry = Der Code ist 5 Minuten gültig und einmalig verwendbar. Ein neuer Code macht ihn ungültig.
identity-code-issued = Verknüpfungscode für { $arg0 } erstellt:
identity-code-not-pending = Es gibt keinen ungenutzten Verknüpfungscode für { $arg0 }.
identity-code-redeem = In der gewünschten { $arg0 }-Gruppe den Bot erwähnen und senden: { $arg1 }
identity-command-description = Minecraft mit einer externen Plattformidentität verknüpfen
identity-help = Befehle zur Identitätsverknüpfung:
    /bind qq — schnell einen QQ-Verknüpfungsbefehl erzeugen
    /bind <Plattform> — einmaligen Code erzeugen
    /bind list — bestehende Verknüpfungen anzeigen
    /bind cancel <Plattform> — ungenutzten Code verwerfen
    /bind unlink <Plattform> — alle Verknüpfungen einer Plattform entfernen
identity-notify-linked = Deine { $arg0 }-Identität wurde bestätigt. Mit /bind list ansehen oder mit /bind unlink { $arg0 } entfernen.
identity-notify-unlinked = Deine { $arg0 }-Identitätsverknüpfung wurde entfernt.
identity-platform-none = keine
identity-platforms = Unterstützte Plattformen: { $arg0 }
identity-scope-notice = Diese Plattform trennt Identitäten nach Gruppe. Verknüpfe das Konto in der gewünschten Gruppe; jede weitere Gruppe muss separat verknüpft werden.
identity-storage-error = Der Datenbankvorgang für die Identitätsverknüpfung ist fehlgeschlagen. Wende dich an die Administration.
identity-unavailable = Die Identitätsverknüpfung ist derzeit nicht verfügbar. Wende dich an die Administration.
identity-unsupported-platform = Nicht unterstützte Plattform: { $arg0 }
social-admin-conversations-empty = Für { $arg0 } wurden keine Konversationen erfasst.
social-admin-conversations-title = Erfasste Konversationen für { $arg0 }:
social-admin-help = /social platforms · /social status [Plattform] · /social reload [Plattform] · /social conversations <Plattform>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Soziale Plattform { $arg0 } wurde neu geladen.
social-admin-reloaded-all = Alle sozialen Plattformen wurden neu geladen.
social-admin-status = { $arg0 } · { $arg1 } · Generation { $arg2 } · Konversationen { $arg3 }
social-admin-title = Soziale Plattformen
social-admin-unknown = Unbekannte soziale Plattform: { $arg0 }
social-binding-already-linked = Diese Plattformidentität ist bereits mit Minecraft-Spieler { $arg0 } verknüpft.
social-binding-code-invalid = Der Bestätigungscode ist ungültig oder abgelaufen. Erzeuge in Minecraft mit /bind { $arg0 } einen neuen.
social-binding-external-in-use = Diese Plattformidentität ist bereits mit einem anderen Minecraft-Spieler verknüpft.
social-binding-help = Führe in Minecraft /bind { $arg0 } aus.
    Kopiere den erzeugten vollständigen Befehl.
    Sende den Befehl hier.
    Prüfe die Verknüpfung mit /profil.
social-binding-identity-missing = Diese Nachricht enthält keine vertrauenswürdige Mitgliederidentität; eine Verknüpfung ist daher nicht möglich.
social-binding-linked = Mit Minecraft-Spieler { $arg0 } verknüpft.
social-binding-not-bound = Diese Plattformidentität ist nicht verknüpft.
social-binding-platform-mismatch = Dieser Bestätigungscode wurde für eine andere Plattform ausgestellt.
social-binding-player-scope-in-use = Dieser Minecraft-Spieler hat in dieser Konversation bereits eine andere Identität verknüpft.
social-binding-rate-limited = Zu viele fehlgeschlagene Versuche. Versuche es in etwa { $arg0 ->
        [one] { $arg0 } Minute
       *[other] { $arg0 } Minuten
    } erneut.
social-binding-status-unbound = Diese Plattformidentität ist nicht verknüpft. Verwende /binden mit einem Bestätigungscode.
social-binding-unavailable = Die Identitätsverknüpfung ist vorübergehend nicht verfügbar.
social-binding-unlink-prompt = Beim Trennen wird diese Plattformidentität von Minecraft gelöst. Sende zum Fortfahren /trennen bestätigen.
social-binding-unlinked = Verknüpfung mit Minecraft-Spieler { $arg0 } wurde entfernt.
social-command-description = Adapter für soziale Plattformen verwalten
social-duration-days = { $arg0 ->
        [one] { $arg0 } Tag
       *[other] { $arg0 } Tage
    } { $arg1 ->
        [one] { $arg1 } Stunde
       *[other] { $arg1 } Stunden
    } { $arg2 ->
        [one] { $arg2 } Minute
       *[other] { $arg2 } Minuten
    }
social-duration-hours = { $arg0 ->
        [one] { $arg0 } Stunde
       *[other] { $arg0 } Stunden
    } { $arg1 ->
        [one] { $arg1 } Minute
       *[other] { $arg1 } Minuten
    }
social-duration-minutes = { $arg0 ->
        [one] { $arg0 } Minute
       *[other] { $arg0 } Minuten
    }
social-help-command-description = Diese Befehlsliste anzeigen
social-link-command-description = Dein Minecraft-Konto mit einem Spielcode verknüpfen
social-online-command-description = Online-Spieler anzeigen
social-profile-afk = Online · AFK
social-profile-avatar-alt = Minecraft-Gesicht
social-profile-command-description = Dein Profil anzeigen; Vollmitglieder können per Spielername, Erwähnung oder Antwort suchen
social-profile-first-joined-label = Erster Beitritt
social-profile-language-label = Sprache
social-profile-last-seen-label = Zuletzt gesehen
social-profile-member-required = Nur verknüpfte Mitglieder, Moderatoren oder Kustoden können Profile anderer Spieler anzeigen.
social-profile-offline = Offline
social-profile-online = Online · aktiv
social-profile-play-time-label = Spielzeit
social-profile-player-label = Spieler
social-profile-player-not-found = Dieser Minecraft-Spieler wurde nicht gefunden.
social-profile-presence-label = Status
social-profile-target-ambiguous = Wähle genau ein Ziel: einen Spielernamen, eine Erwähnung oder einen beantworteten Benutzer.
social-profile-target-unbound = Die erwähnte oder beantwortete Identität ist nicht mit Minecraft verknüpft.
social-profile-uuid-label = UUID
social-query-active-label = Aktiv
social-query-no-players = Derzeit sind keine Spieler online.
social-query-online-label = Online
social-query-players-label = Spieler
social-query-unknown = Unbekannter Befehl. Sende /hilfe, um die verfügbaren Befehle anzuzeigen.
social-query-uptime-label = Laufzeit
social-reply-hidden = Diese Antwort wurde durch die Inhaltsrichtlinie des Servers ausgeblendet.
social-state-disabled = deaktiviert
social-state-failed = fehlgeschlagen
social-state-ready = bereit
social-state-starting = wird gestartet
social-state-stopped = gestoppt
social-status-command-description = Spieler, TPS, MSPT und Laufzeit anzeigen
social-title-command-help = Befehlshilfe
social-title-content-hidden = Inhalt ausgeblendet
social-title-identity-linking = Konto verknüpfen
social-title-identity-unlinking = Konto trennen
social-title-online-players = Online-Spieler
social-title-player-profile = Spielerprofil
social-title-server-status = Serverstatus
social-title-unknown-command = Unbekannter Befehl
social-unlink-command-description = Diese Identitätsverknüpfung nach Bestätigung entfernen

## Community · AI and knowledge

ai-busy = Die KI bearbeitet zu viele Anfragen; bitte gleich erneut versuchen
ai-cleared = KI-Gesprächsverlauf gelöscht
ai-command-description = Den KI-Assistenten im Spiel fragen
ai-disabled = Der KI-Assistent ist deaktiviert oder nicht konfiguriert
ai-knowledge-curation-failed = Wissenspflege fehlgeschlagen: { $arg0 }
ai-knowledge-curation-finished = Wissenspflege beendet: angewandt { $arg0 } · übersprungen { $arg1 } · fehlgeschlagen { $arg2 } · Quarantäne { $arg3 } · verarbeitet { $arg4 }
ai-knowledge-curation-started = Wissenspflege gestartet.
ai-knowledge-disabled = Wissensspeicher deaktiviert.
ai-knowledge-index-dirty = Indexabgleich erforderlich; private Suche bleibt bis zur Synchronisierung gesperrt.
ai-knowledge-last-failure = Letzter Fehler: { $arg0 }
ai-knowledge-last-sync = Letzte Synchronisierung: { $arg0 }
ai-knowledge-learning-disabled = Wissenslernen deaktiviert.
ai-knowledge-operation-failed = Wissensaktion fehlgeschlagen: { $arg0 }
ai-knowledge-operation-started = Wissensaktion gestartet.
ai-knowledge-protected = Wissensdokument { $arg0 } ist jetzt geschützt.
ai-knowledge-rollback = Wissensdokument { $arg0 } als Revision { $arg1 } wiederhergestellt.
ai-knowledge-status = Wissen: { $arg0 ->
        [one] { $arg0 } öffentliches Dokument
       *[other] { $arg0 } öffentliche Dokumente
    } / { $arg1 ->
        [one] { $arg1 } Textabschnitt
       *[other] { $arg1 } Textabschnitte
    } · { $arg2 ->
        [one] { $arg2 } privates Dokument
       *[other] { $arg2 } private Dokumente
    } / { $arg3 ->
        [one] { $arg3 } Textabschnitt
       *[other] { $arg3 } Textabschnitte
    }
ai-knowledge-sync-failed = Wissenssynchronisierung fehlgeschlagen: { $arg0 }
ai-knowledge-sync-started = Wissenssynchronisierung gestartet.
ai-knowledge-synchronized = { ai-knowledge-status } · Synchronisierung abgeschlossen.
ai-knowledge-unprotected = Wissensdokument { $arg0 } ist nicht mehr geschützt.
ai-learning-failure = Lernfehler: { $arg0 }
ai-learning-status = Lernen: Kandidaten { $arg0 } · Extraktion { $arg1 } · wartend { $arg2 } · verworfene Extraktionen { $arg3 } · verworfene Kandidaten { $arg4 } · Quarantäne { $arg5 }
ai-memory-empty = Keine privaten Erinnerungen für { $arg0 }.
ai-memory-list = Private Erinnerungen für { $arg0 }: { $arg1 }
ai-memory-not-found = Private Erinnerung { $arg0 } nicht gefunden.
ai-memory-removed = Für { $arg1 } endgültig entfernt: { $arg0 ->
        [one] { $arg0 } private Erinnerung
       *[other] { $arg0 } private Erinnerungen
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Unbekannter zwischengespeicherter Spieler: { $arg0 }
ai-prompt-too-long = Die Frage ist zu lang (höchstens { $arg0 } Zeichen)
ai-reload-failed = KI-Konfiguration konnte nicht neu geladen werden: { $arg0 }
ai-reloaded = KI-Konfiguration neu geladen ({ $arg0 })
ai-request-failed = Die KI-Anfrage ist fehlgeschlagen; bitte später erneut versuchen
ai-social-command-description = Den gemeinsamen KI-Assistenten fragen
ai-social-question-required = Bitte gib nach dem KI-Befehl eine Frage ein
ai-status-disabled = KI ist deaktiviert
ai-status-enabled = KI aktiv · Anbieter { $arg0 } · Modell { $arg1 } · Werkzeuge { $arg2 }
ai-status-none = keine
ai-thinking = Denkt nach…
ai-usage = Verwendung: /ai <Frage> · /ai clear · /ai status
ai-worker-status = KI-Aufgaben: aktiv { $arg0 } · wartend { $arg1 } · verworfen { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Glückwunsch!</bold> Du bist jetzt <gold>Mitglied</gold> und hast Zugriff auf weitere Befehle und Funktionen, darunter den einfachen Schematikdruck.</green>
governance-active-days-30 = Mindestens 2 aktive Tage in 30 Tagen (derzeit { $arg0 })
governance-active-days-60 = Mindestens 6 aktive Tage in 60 Tagen (derzeit { $arg0 })
governance-active-header = ─── Berechtigung als aktives Mitglied ───
governance-active-member = Derzeit Mitglied
governance-active-member-age = Seit mindestens 30 Tagen Mitglied
governance-active-playtime = Mindestens 6 Stunden wirksame Spielzeit in 60 Tagen (derzeit { $arg0 })
governance-already-moderator = Du bist bereits Moderator.
governance-ballot-recorded = Deine anonyme Stimme wurde als { $arg0 } erfasst. Bis zum Fristende kannst du sie ändern.
governance-ban-details = Vorgeschlagen von { $arg0 } · Dauer { $arg1 } · Grund: { $arg2 }
governance-ban-suffix = · Dauer { $arg0 } · Grund: { $arg1 }
governance-choice-abstain = Enthaltung
governance-choice-no = Nein
governance-choice-yes = Ja
governance-command-description = Governance-Status und Abstimmungen anzeigen
governance-custodian-no-nomination = Betreiber nehmen nicht an der Wahl von Moderatoren teil.
governance-custodian-no-resign = Für Betreiber gilt das Rücktrittsverfahren für Moderatoren nicht.
# $arg0 (Number): duration; locale decides the unit form.
governance-days = { $arg0 ->
        [one] { $arg0 } Tag
       *[other] { $arg0 } Tage
    }
governance-error-ban-already-active = Dieser Spieler hat bereits eine aktive Sperre.
governance-error-ban-proposer-ineligible = Nur aktive Mitglieder dürfen eine Sperrabstimmung beantragen.
governance-error-ban-self = Du kannst keine Sperrabstimmung gegen dich selbst beantragen.
governance-error-candidate-ineligible = Ein Kandidat für die Moderation muss aktives Mitglied sein.
governance-error-nomination-not-open = Du hast keine laufende Kandidatur.
governance-error-not-voter = Du stehst nicht auf der eingefrorenen Wählerliste.
governance-error-pause-already-active = Für diesen Spieler besteht bereits eine aktive Beförderungspause.
governance-error-pause-already-member = Dieser Spieler ist bereits Mitglied; eine Beförderungspause ist nicht nötig.
governance-error-pause-end-invalid = Die Beförderungspause muss in der Zukunft enden.
governance-error-player-unknown = Für diesen Spieler gibt es noch keinen Governance-Eintrag.
governance-error-removal-requires-petition = Eine Abwahl benötigt genügend Unterstützer.
governance-error-removal-self = Du kannst deine eigene Abwahl nicht unterstützen.
governance-error-removal-sponsor-ineligible = Nur aktive Mitglieder dürfen eine Abwahl unterstützen.
governance-error-removal-sponsors-too-few = Mindestens zwei andere aktive Mitglieder sind erforderlich.
governance-error-removal-vote-open = Für diesen Moderator läuft bereits eine Abwahl.
governance-error-termination-reason-invalid = Dieser Beendigungsgrund gilt nicht für diese Abstimmung.
governance-error-vote-already-open = Für diesen Spieler läuft bereits eine Abstimmung dieser Art.
governance-error-vote-closed = Diese Abstimmung ist geschlossen oder beendet.
governance-error-vote-cooldown = Die dreitägige Wartezeit nach verfehlter Beteiligung ist noch nicht vorbei.
governance-error-vote-subject-unknown = Für die betroffene Person gibt es noch keinen Governance-Eintrag.
governance-error-vote-termination-closed = Nach Ablauf der Frist kann die Abstimmung nicht zurückgezogen werden.
governance-error-vote-unknown = Abstimmung nicht gefunden.
governance-error-voters-too-few = Für eine Abstimmung sind mindestens zwei aktive Mitglieder nötig.
governance-failure-majority = Ja-Stimmen übersteigen nicht die Hälfte aller Stimmen
governance-failure-minimum-yes = Zu wenige Ja-Stimmen
governance-failure-none = Kein Grund erfasst
governance-failure-quorum = Mindestbeteiligung nicht erreicht
governance-failure-two-thirds = Ja-Stimmen erreichen nicht zwei Drittel der nicht enthaltenden Stimmen
governance-history-header = ─── Letzte Abstimmungsergebnisse ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
# $arg0 (Number): duration; locale decides the unit form.
governance-hours = { $arg0 ->
        [one] { $arg0 } Stunde
       *[other] { $arg0 } Stunden
    }
governance-inactive-removal = { $arg0 } hat nach 180 Tagen ohne Anmeldung die Moderation automatisch verlassen.
governance-invalid-choice = Wähle yes, no oder abstain.
governance-invalid-duration = Ungültige Dauer. Verwende zum Beispiel 30m, 12h oder 3d.
governance-kind-appointment = Ernennung
governance-kind-ban = Sperre
governance-kind-removal = Abwahl
governance-legacy-ban-kick = Eine frühere Governance-Abstimmung wurde angenommen. Du bist für { $arg0 } gesperrt. Grund: { $arg1 }
governance-menu-actions = Aktionen
governance-menu-back = Zurück
governance-menu-cancel = Abbrechen
governance-menu-confirm = Bestätigen
governance-menu-confirm-nominate = Eine Abstimmung über deine Ernennung zum Moderator starten?
governance-menu-confirm-resign = Als Moderator zurücktreten?
governance-menu-confirm-sponsor = Diesen Abwahlantrag unterstützen?
governance-menu-confirm-withdraw = Deine Bewerbung als Moderator zurückziehen?
governance-menu-find-player = Anderen Spieler suchen
governance-menu-history = Abstimmungsverlauf
governance-menu-loading = Governance-Status wird geladen…
governance-menu-new-petition = Abwahlantrag starten
governance-menu-nominate = Als Moderator bewerben
governance-menu-nomination-open = Deine Ernennungsabstimmung läuft
governance-menu-pause = Aufstieg pausieren
governance-menu-pause-appeal = Beschwerdeweg
governance-menu-pause-duration = Dauer (z. B. 2d)
governance-menu-pause-reason = Begründung
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } Unterstützer
governance-menu-petitions = Abwahlanträge
governance-menu-player-name = Spielername
governance-menu-players = Spielerverwaltung
governance-menu-recent-results = Neueste Abstimmungsergebnisse
governance-menu-refresh = Aktualisieren
governance-menu-resign = Als Moderator zurücktreten
governance-menu-resume = Aufstiegspause aufheben
governance-menu-resume-reason = Grund für Aufhebung
governance-menu-staff-only = Nur Moderatoren und Verwalter dürfen Spieler verwalten.
governance-menu-status = Dein Status
governance-menu-target = Moderatorname
governance-menu-title = Gemeinschaftsverwaltung
governance-menu-votes = Laufende Abstimmungen
governance-menu-withdraw = Bewerbung zurückziehen
# $arg0 (Number): duration; locale decides the unit form.
governance-minutes = { $arg0 ->
        [one] { $arg0 } Minute
       *[other] { $arg0 } Minuten
    }
governance-no-active-ban = Keine aktive Sperre
governance-no-active-pause = Keine aktive Beförderungspause
governance-nomination-withdrawn = Deine Kandidatur wurde zurückgezogen. Das Ergebnis wird automatisch bekanntgegeben.
governance-none = Keine
governance-not-member = Kein Mitglied
governance-not-moderator = Du bist derzeit kein Moderator.
governance-operation-error = Governance-Aktion fehlgeschlagen. Bitte versuche es erneut.
governance-own-choice-closed = Deine Wahl: { $arg0 }
governance-own-choice-open = Deine Wahl: { $arg0 }; bis zum Fristende änderbar.
governance-own-not-voted = Du warst wahlberechtigt, hast aber nicht abgestimmt.
governance-own-not-voted-hint = Du hast noch nicht abgestimmt. Öffne Governance und wähle diese Abstimmung.
governance-own-not-voted-open = Du hast noch nicht abgestimmt.
governance-own-not-voter = Du stehst nicht auf der Wählerliste dieser Abstimmung.
governance-own-voted = Du hast abgestimmt
governance-pause-cleared = Beförderungspause für { $arg0 } aufgehoben.
governance-pause-details = Pause: { $arg0 } · Bis { $arg1 } · Einspruch: { $arg2 }
governance-pause-fields-required = Gib einen Grund und einen Einspruchsweg an.
governance-pause-not-active = Für { $arg0 } besteht keine aktive Beförderungspause.
governance-pause-notice = Deine Beförderung ist pausiert. Grund: { $arg0 } · Bis { $arg1 } · Einspruch: { $arg2 }
governance-pause-set = Beförderung für { $arg0 } bis { $arg1 } pausiert.
governance-petition-already-sponsored = Du unterstützt diese Abwahl bereits ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } Unterstützer · Bis { $arg3 }
governance-petition-started = Abwahlantrag für { $arg0 }: { $arg1 }/{ $arg2 } Unterstützer; bis { $arg3 }.
governance-petitions-header = ─── Abwahlanträge ───
governance-player-first-join = Erster Beitritt: { $arg0 }
governance-player-header = ─── { $arg0 } · Governance-Profil ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Letzte Anmeldung: { $arg0 }
governance-player-member-since = Mitglied seit: { $arg0 }
governance-player-never-joined = Spieler { $arg0 } war noch nie auf diesem Server.
governance-promotion-account-age = Erster Beitritt liegt mindestens 3 Tage zurück
governance-promotion-header = ─── Beförderungsvoraussetzungen ───
governance-promotion-play-days = Wirksame Spielzeit an mindestens 2 Tagen (derzeit { $arg0 })
governance-promotion-playtime = Mindestens 8 Stunden wirksame Spielzeit (derzeit { $arg0 })
governance-resign-failed = Rücktritt fehlgeschlagen. Bitte wende dich an einen Betreiber.
governance-resigned = { $arg0 } ist als Moderator zurückgetreten.
governance-result-announcement = Abstimmung #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = Abgelehnt ({ $arg0 })
governance-result-open = Läuft
governance-result-passed = Angenommen
governance-result-tally = { $arg0 } · Wähler { $arg1 }, beteiligt { $arg2 }, Ja { $arg3 }, Nein { $arg4 }, Enthaltung { $arg5 }
governance-result-terminated = Beendet ({ $arg0 })
governance-resume-notice = Deine Beförderungspause wurde aufgehoben. Grund: { $arg0 }
governance-resume-reason-required = Gib einen Grund für die Aufhebung an.
governance-role-lookup-error = Moderatorenstatus konnte nicht geprüft werden. Bitte wende dich an einen Betreiber.
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } Sekunde
       *[other] { $arg0 } Sekunden
    }
governance-storage-error = Governance-Daten konnten nicht verarbeitet werden. Bitte wende dich an einen Betreiber.
governance-target-not-moderator = { $arg0 } ist derzeit kein Moderator.
governance-termination-inactive = Moderator wegen langer Abwesenheit ausgeschieden
governance-termination-ineligible = Kandidat verlor den Status als aktives Mitglied
governance-termination-resigned = Moderator ist zurückgetreten
governance-termination-unknown = Kein Grund erfasst
governance-termination-withdrew = Kandidat zog seine Bewerbung zurück
governance-vote-electorate = Wähler { $arg0 } · Mindestbeteiligung { $arg1 }
governance-vote-finalized = Abgeschlossen: { $arg0 }
governance-vote-header = ─── Abstimmung #{ $arg0 } ───
governance-vote-in-progress = Läuft · { $arg0 } beteiligt; Einzelzahlen werden nach dem Ende veröffentlicht.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · Ende { $arg3 } · Wähler { $arg4 } · Beteiligt { $arg5 } · Mindestzahl { $arg6 }{ $arg7 }
governance-vote-period = Beginn { $arg0 } · Ende { $arg1 }
governance-vote-started = { $arg1 }-Abstimmung #{ $arg0 } gestartet: { $arg2 }; Wähler { $arg3 }, Mindestbeteiligung { $arg4 }, Ende { $arg5 }. Öffne Governance für Details.
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Laufende anonyme Abstimmungen ───

## World · Plots

plot-access-removed-for-you = Dein Zugang zu Grundstück { $arg0 } wurde entfernt.
plot-admin-created = { $arg0 } für { $arg1 } erfasst · { $arg2 }
plot-allowed = aktiviert
plot-area-changed = Grenzen wurden geändert. Aktualisieren und erneut bestätigen.
plot-area-confirm = Grenzen von „{ $arg0 }“ durch die Auswahl ({ $arg1 ->
        [one] { $arg1 } Bauzelle
       *[other] { $arg1 } Bauzellen
    }) ersetzen? Bauwerke bleiben erhalten.
plot-area-hint = //pos1 → //pos2 → speichern. Ecken auch per Holzaxt wählen; mehrere Bereiche hinzufügen/abziehen.
plot-area-replace-save = Grenzen ersetzen und speichern
plot-area-save = Grenzen speichern
plot-area-saved = Grenzen von „{ $arg0 }“ gespeichert.
plot-arrival-removed = Ankunftspunkt entfernt.
plot-arrival-set = Ankunftspunkt gespeichert.
plot-arrival-text-saved = Ankunftstitel gespeichert.
plot-atmosphere-saved = Umgebung gespeichert.
plot-back = Zurück
plot-board-auto-notify-result = Benachrichtigungen für { $arg0 } Spieler vorgemerkt.
plot-board-auto-registered-text = Grundstück „{ $arg0 }“ wurde registriert. Koordinaten des Rahmens bebauter Zellen: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Eintrag von { $arg0 } bei { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }: { $arg5 }
plot-board-empty = Noch keine Gemeinschaftseinträge.
plot-board-header = Gemeinschaftseinträge (letzte { $arg0 }; Details: /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Bekanntmachung
plot-board-kind-objection = Einspruch
plot-board-menu = Gemeinschaftstafel
plot-board-notice-alert = { $arg0 } hat eine Bekanntmachung veröffentlicht: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } hat Einspruch erhoben: { $arg1 } · { $arg2 }
plot-board-rate-limited = Limit für Einträge erreicht. Versuche es später.
plot-board-record-invalid = Eintrag nicht gefunden, bereits erledigt oder Aktion nicht erlaubt.
plot-board-registered-alert = { $arg0 } hat Grundstück „{ $arg1 }“ registriert: { $arg2 }
plot-board-ruling-alert = Zum Einspruch von { $arg0 } wurde entschieden: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Entscheidung: { $arg0 }
plot-board-saved = Eintrag { $arg0 } gespeichert.
plot-board-status-elapsed = Vor mindestens 72 Stunden veröffentlicht
plot-board-status-open = Offen
plot-board-status-ruled = Entschieden
plot-board-status-withdrawn = Zurückgezogen
plot-board-text-invalid = Beschreibung: 12–1000 Zeichen; Entscheidung: mindestens 8 Zeichen.
plot-board-unavailable = Einträge wurden nicht geladen. Versuche es später oder besuche https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } hat einen Einspruch zurückgezogen: { $arg1 } · { $arg2 }
plot-board-write-failed = Das Board konnte nicht aktualisiert werden. Versuche es später.
plot-command-blocked = Der Grundstücksschutz blockiert diesen Befehl. Nutze /fill oder /setblock direkt im eigenen Grundstück; komplexe /execute-Befehle muss das Serverteam prüfen.
plot-command-description = Baugrundstücke und öffentliche Projekte verwalten
plot-created = Grundstück { $arg0 } erstellt · { $arg1 }
plot-created-for-you = { $arg1 } hat das Grundstück { $arg0 } für dich erfasst.
plot-deleted = Grundstück freigegeben; das Bauwerk bleibt erhalten.
plot-denied = deaktiviert
plot-empty-summary = Hier ist kein Grundstück.
plot-error-admin-owner-only = Mitglieder mit Verwaltungsrechten zu ändern erfordert Mitglieder- und Rechteverwaltung.
plot-error-area-disconnected = Das Grundstück muss zusammenhängend sein.
plot-error-args = Ungültige oder fehlende Argumente. Nutze /plot help.
plot-error-arrival-location = Stehe auf sicherem Boden innerhalb des bebauten Projektbereichs.
plot-error-arrival-missing = Kein Ankunftspunkt festgelegt.
plot-error-arrival-private = Du darfst dieses private Projekt nicht besuchen.
plot-error-arrival-text = Ungültiger Titel. Nur eine Zeile; höchstens 32 sichtbare Zeichen im Titel und 64 im Untertitel.
plot-error-arrival-unsafe = Dieser Ankunftspunkt ist nicht verfügbar oder nicht mehr sicher.
plot-error-busy = Grundstücksdaten werden gespeichert. Versuche es später erneut; diese Aktion wurde nicht eingereicht.
plot-error-flag = Unbekanntes Recht. Eine Aktion aus der Berechtigungsliste wählen.
plot-error-id = Grundstücks-ID fehlt oder ist mehrdeutig. Nutze /plot list.
plot-error-member-missing = Dieser Spieler ist kein Mitglied mehr.
plot-error-member-required = Neue Spieler können Grundstücke ansehen und Einspruch erheben. Zum Erstellen eines Projekts ist der Mitgliedsstatus nötig.
plot-error-name = Projektnamen müssen 1–48 Zeichen enthalten.
plot-error-notice-text = Mitteilungen dürfen bis zu { $arg0 } Zeichen enthalten, ohne Begrenzung der Zeilenzahl oder Zeilenlänge; Steuerzeichen sind nicht erlaubt.
plot-error-overlap = Der Baubereich überschneidet ein anderes Grundstück.
plot-error-owner-member = Grundstückseigentümer · Gruppenrechte
plot-error-owner-only = deaktiviert · Verwaltung
plot-error-permission-changed = Rechte, Mitglieder oder Vererbung geändert. Änderungen erneut prüfen.
plot-error-permission-grant = Du kannst keine Verwaltungsrechte erteilen, die du nicht besitzt.
plot-error-permission-owner-scope = Nur Grundstückseigentümer, übergeordnete Eigentümer oder Serverpersonal dürfen Eigentümerrechte ändern.
plot-error-player-unknown = Spieler unbekannt. Prüfe Namen oder UUID.
plot-error-release-owner-only = deaktiviert · Grundstück löschen
plot-error-selection = Markiere zwei Ecken mit //pos1 und //pos2.
plot-error-selection-changed = Die Auswahl hat sich geändert. Bitte erneut bestätigen.
plot-error-staff-only = Nur Moderatoren oder Kustoden können Grundstücke anderer Spieler verwalten oder erfassen.
plot-error-storage = Grundstücksaktion fehlgeschlagen. Versuche es später erneut oder kontaktiere das Serverteam.
plot-error-subplot-arrival = Verschiebe zuerst den Ankunftspunkt des Hauptgrundstücks aus der Auswahl.
plot-error-subplot-children = Gib die Teilgrundstücke frei, bevor du das Hauptgrundstück freigibst.
plot-error-subplot-depth = Teilgrundstücke sind nur auf einer Ebene möglich.
plot-error-subplot-disconnected = Die ausgewählten Bauzellen sind nicht verbunden. Teile die Bereiche einzeln auf.
plot-error-subplot-overlap = Die Auswahl überschneidet sich mit einem anderen Teilgrundstück.
plot-error-subplot-own-arrival = Der Ankunftspunkt dieses Teilgrundstücks muss im neuen Bereich bleiben. Verschiebe ihn sonst zuerst.
plot-error-subplot-owner = Dir fehlt die Berechtigung für diesen Teilgrundstück-Vorgang.
plot-error-subplot-parent = Markiere beide Ecken innerhalb vorhandener Bauzellen des Hauptgrundstücks.
plot-error-subplot-supervisor-member = Der Eigentümer des Hauptgrundstücks hat hier bereits Aufsicht und braucht keine Mitgliedsrolle.
plot-error-time-format = Uhrzeit als HH:mm (00:00–23:59) eingeben.
plot-error-transfer-owner-only = deaktiviert · Eigentum übertragen
plot-error-transfer-self = Du besitzt dieses Grundstück bereits.
plot-flag-set = { $arg0 }: { $arg1 }.
plot-help = Grundstückshilfe
plot-here = Hier: { $arg0 } · { $arg1 }
plot-invited = Zugriff für { $arg0 } gewährt.
plot-item-summary = Bebaute Fläche { $arg0 } Block² · { $arg1 ->
        [one] { $arg1 } Mitglied
       *[other] { $arg1 } Mitglieder
    }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } Block²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Noch nicht eingereicht
plot-member-draft-reset = Kategorieeinstellungen löschen
plot-member-draft-role = Rolle: { $arg0 }
plot-member-draft-submit = Prüfen und bestätigen
plot-member-draft-title = Mitgliederentwurf · { $arg0 }
plot-member-permissions = Berechtigungen
plot-member-removed = Projektmitglied entfernt.
plot-menu-access = Zugriff und Mitglieder
plot-menu-access-by-settings = nach Zugriffseinstellungen
plot-menu-admin = Grundstücksverwalter hinzufügen
plot-menu-arrival = Ankunftspunkt
plot-menu-arrival-subtitle = Untertitel (MiniMessage; optional)
plot-menu-arrival-text = Ankunftstitel
plot-menu-arrival-title = Titel (MiniMessage; leer = Projektname)
plot-menu-atmosphere = Umgebung
plot-menu-clear = Klar
plot-menu-collaborator = Mitwirkende hinzufügen
plot-menu-confirm-delete = Registrierung von { $arg0 } löschen? Schutz, Mitgliederrechte und Einstellungen werden unwiderruflich entfernt. Bauwerke bleiben erhalten.
plot-menu-confirm-subplot-transfer = { $arg0 } an { $arg1 } übertragen? Der Eigentümer des Hauptgrundstücks behält die Aufsicht.
plot-menu-confirm-transfer = Die Verwaltung von { $arg0 } an { $arg1 } übertragen? Du verlierst den Zugang; bestehende Urheberschaft bleibt bestehen.
plot-menu-confirm-withdraw = Diese Rückmeldung zurückziehen? Der Rückzug bleibt öffentlich sichtbar.
plot-menu-create = { -plot(case: "accusative") } erstellen
plot-menu-create-for-player = Grundstück für diesen Spieler erfassen
plot-menu-custom-time = Eigene Uhrzeit
plot-menu-delete = Grundstücksregistrierung löschen
plot-menu-description = Beschreibung
plot-menu-feedback = Dieser Bekanntmachung widersprechen
plot-menu-find-record = Eintrag suchen
plot-menu-flag-container = Behälter öffnen
plot-menu-flag-entity = Entitäten bedienen
plot-menu-follow-parent = Hauptgrundstück folgen
plot-menu-follow-world = Der Welt folgen
plot-menu-group-admin = Grundstücksverwalter
plot-menu-group-collaborator = Mitwirkende
plot-menu-group-member = Standard für Mitglieder
plot-menu-group-newcomer = Standard für Neulinge
plot-menu-group-owner = Grundstückseigentümer
plot-menu-manage = Spielergrundstücke verwalten
plot-menu-managed-empty = Dieser Spieler hat noch keine Grundstücke.
plot-menu-managed-title = Grundstücke von { $arg0 }
plot-menu-members = Mitglieder ({ $arg0 })
plot-menu-my-access = Dein Zugriff: { $arg0 } · { $arg1 }
plot-menu-name = Projektname
plot-menu-owner = Eigentümer: { $arg0 }
plot-menu-parent-supervisor = Eigentümer des Hauptgrundstücks: { $arg0 } · Aufsicht
plot-menu-player = Spielername oder UUID
plot-menu-project-kind = Projektart
plot-menu-public-destinations = Öffentliche Reiseziele
plot-menu-public-destinations-empty = Noch keine öffentlichen Projekte mit Ankunftspunkt.
plot-menu-rain = Niederschlag
plot-menu-record-id = Eintrags-ID; für ältere Einträge die vollständige ID eingeben
plot-menu-related-notice = Zugehörigen Hinweis ansehen
plot-menu-remove-arrival = Ankunftspunkt entfernen
plot-menu-remove-member = Mitglied entfernen
plot-menu-rename = Projekt umbenennen
plot-menu-rule = Entscheidung eintragen
plot-menu-ruling = Entscheidung begründen (8–1000 Zeichen)
plot-menu-set-arrival = Ankunftspunkt hier setzen
plot-menu-set-role = Als { $arg0 } festlegen
plot-menu-settings = Projekteinstellungen
plot-menu-submit = Anwenden
plot-menu-subplot-create = Teilgrundstück erstellen
plot-menu-subplot-of = Teil von { $arg0 }
plot-menu-subplot-parent = Zum Hauptgrundstück: { $arg0 }
plot-menu-subplots = Teilgrundstücke ({ $arg0 })
plot-menu-time = Uhrzeit
plot-menu-time-input = Uhrzeit (HH:mm)
plot-menu-transfer = Grundstück übertragen
plot-menu-visit = Projekt besuchen
plot-menu-weather = Wetter
plot-menu-withdraw = Rückmeldung zurückziehen
plot-model-fit = Alle Grenzen einpassen
plot-model-focus = Umgebung fokussieren
plot-model-layer-down = Schnitthöhe um 1 Block senken
plot-model-layer-up = Schnitthöhe um 1 Block anheben
plot-model-legend = Türkis: gespeichert · Grün/Gold: Ecken 1/2 · Violett: zu speichernde Grenzen · Weiß/Rot: du/Blickrichtung
plot-model-level = Schnitt bei Y ≤ { $arg0 } (nur Vorschau)
plot-model-limited = Modellabtastung oder Details reduziert. Fokussiere die Nähe oder ein Rechteck für mehr Details; nutze die Grenzansicht zur Kantenprüfung.
plot-model-north-up = Draufsicht mit Norden oben. Wechsle zum Drehen in die 3D-Ansicht.
plot-model-perspective = 3D-Ansicht
plot-model-preparing = Grenzen und Bereich werden im Hintergrund geprüft… Automatische Aktualisierung nach Abschluss.
plot-model-progress = Modell wird gelesen: { $arg0 } %…
plot-model-rotate-left = ↶ Links drehen
plot-model-rotate-right = ↷ Rechts drehen
plot-model-show-all = Volle Höhe zeigen
plot-model-slice = Über meiner Ebene schneiden
plot-model-terrain = Bauwerke und Gelände anzeigen
plot-model-top = Draufsicht
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } ungeladene Probe
       *[other] { $arg0 } ungeladene Proben
    } (grau). Chunks werden nicht zwangsweise geladen.
plot-model-wireframe = Nur Grenzen
plot-no-here = Hier ist kein geschütztes Grundstück.
plot-notice-board = Grundstücksaushang
plot-notice-cleared = Aushang gelöscht
plot-notice-edit = Aushang bearbeiten
plot-notice-input = Bis zu { $arg0 } Zeichen; mehrere Zeilen erlaubt; zum Löschen leer lassen
plot-notice-read = Vollständige Mitteilung lesen
plot-notice-saved = Aushang aktualisiert
plot-permission-all-actions = Alle Aktionen
plot-permission-animal = Tiere pflegen, zähmen und anleinen
plot-permission-break = Blöcke abbauen
plot-permission-bucket = Eimer für Flüssigkeiten und Tiere nutzen
plot-permission-category-build = Bauen und Ändern
plot-permission-category-entity = Entitäten
plot-permission-category-interaction = Blockinteraktion
plot-permission-category-management = Verwaltung
plot-permission-category-storage = Lager und Gegenstände
plot-permission-change-children = Betroffene Untergrundstücke: { $arg0 }
plot-permission-change-confirm = Änderungen anwenden
plot-permission-change-granted = Erteilt: { $arg0 }
plot-permission-change-members = Betroffene Mitglieder: { $arg0 }
plot-permission-change-more = { $arg0 } weitere
plot-permission-change-no-effect = Wirksame Rechte unverändert. Explizite Einstellungen überschreiben künftige Vererbung.
plot-permission-change-none = Keine
plot-permission-change-revoked = Entzogen: { $arg0 }
plot-permission-change-subject = Ziel: { $arg0 }
plot-permission-change-title = Rechteänderungen bestätigen
plot-permission-create-subplot = Teilgrundstück erstellen
plot-permission-decoration = Dekorationen platzieren und ändern
plot-permission-default-status = Standard ({ $arg0 })
plot-permission-delete = Grundstück löschen
plot-permission-door = Türen und Tore
plot-permission-entity-damage = Entitäten verletzen
plot-permission-entity-storage = Entitätenlager öffnen
plot-permission-harvest = Beeren, Ranken und Honig ernten
plot-permission-ignite = Feuer entzünden
plot-permission-item-drop = Gegenstände fallen lassen
plot-permission-item-pickup = Bodenobjekte aufheben
plot-permission-manage-area = Grenzen ändern
plot-permission-manage-members = Mitglieder verwalten
plot-permission-manage-permissions = Berechtigungen verwalten
plot-permission-manage-settings = Grundstückeinstellungen ändern
plot-permission-management = Grundstückseinstellungen und Eigentum
plot-permission-modify = Blöcke ändern und düngen
plot-permission-operation = Aktion
plot-permission-personal = Individuelle Rechte
plot-permission-personal-source = Persönliche Einstellung
plot-permission-place = Blöcke und Fahrzeuge platzieren
plot-permission-reset = Standards wiederherstellen
plot-permission-result = Status
plot-permission-ride = Fahrzeuge und Tiere reiten
plot-permission-select-state = Zustand wählen: { $arg0 }
plot-permission-sign = Schilder bearbeiten
plot-permission-source-group = Gruppenrechte
plot-permission-source-local = Lokale Einstellung
plot-permission-source-owner = Wiederherstellungsrecht des Eigentümers
plot-permission-source-parent = Übergeordnetes Grundstück
plot-permission-source-role = Rollenstandard
plot-permission-state-default = Standard
plot-permission-switch = Knöpfe, Hebel und Druckplatten
plot-permission-trade = Mit Dorfbewohnern handeln
plot-permission-transfer = Eigentum übertragen
plot-permission-workstation = Einrichtungen und Werkstationen nutzen
plot-pos1 = Erster Punkt
plot-pos2 = Zweiter Punkt
plot-private = Privates Projekt
plot-public = Öffentliches Projekt
plot-public-set = Projektart: { $arg0 }.
plot-region-adjust = Dieses Rechteck anpassen
plot-region-apply = Rechteckanpassung abschließen
plot-region-cancel = Rechteckanpassung abbrechen
plot-region-changed = Die Auswahl wurde geändert. Wähle das Rechteck erneut.
plot-region-delete = Dieses Rechteck löschen
plot-region-delete-confirm = Rechteck #{ $arg0 } aus dem Entwurf entfernen? Gebäude bleiben. Grenzen ändern sich erst beim Speichern; dies lässt sich rückgängig machen.
plot-region-edit-hint = Passe dieses Rechteck mit //pos1 und //pos2 an. Andere Rechtecke bleiben unverändert. Schließe die Anpassung ab und speichere dann das Grundstück.
plot-region-finish-first = Schließe die aktuelle Rechteckanpassung zuerst ab oder brich sie ab.
plot-region-open = Rechtecke verwalten ({ $arg0 })
plot-region-select-hint = Wähle ein nummeriertes Rechteck zum Fokussieren und Hervorheben. Graue Linien zeigen diese Seite; Zusammenführen kann die Nummern ändern.
plot-renamed = Projekt umbenannt.
plot-role-admin = Grundstücksverwalter
plot-role-changed-for-you = Deine Rolle auf Grundstück { $arg0 } ist jetzt { $arg1 }.
plot-role-collaborator = Mitwirkende
plot-role-owner = Eigentümer des { -plot(case: "genitive") }
plot-role-parent-owner = Eigentümer des Hauptgrundstücks
plot-role-server-staff = Serverteam
plot-selection-add = Aktuellen Quader hinzufügen
plot-selection-clear = Beide Punkte zurücksetzen
plot-selection-empty = Markiere zwei Ecken mit //pos1 und //pos2.
plot-selection-exit = Bearbeitung / Vorschau beenden
plot-selection-fit = Gespeicherte Grenzen wiederherstellen
plot-selection-incomplete = Ein Punkt ist markiert. Markiere den zweiten; vor dem Absenden ändert sich nichts.
plot-selection-mode = Punkte vor Ort wählen
plot-selection-no-redo = Keine Auswahländerung zum Wiederholen.
plot-selection-no-undo = Keine frühere Auswahländerung zum Rückgängigmachen.
plot-selection-open = Grenzen bearbeiten
plot-selection-redo = Letzten Schritt wiederholen
plot-selection-replace = Durch aktuellen Quader ersetzen
plot-selection-session-hint = Schleichen+F oder /plot edit zum Zurückkehren. Ende nach 20 Minuten Inaktivität.
plot-selection-size = Auswahl: { $arg0 } × { $arg1 } × { $arg2 } Blöcke (X/Y/Z)
plot-selection-stopped = Auswahl beendet. Entwürfe bleiben je Grundstück gespeichert.
plot-selection-subtract = Aktuellen Quader abziehen
plot-selection-undo = Letzten Schritt rückgängig
plot-subplot-created = Teilgrundstück { $arg0 } erstellt · { $arg1 } Block²
plot-subplot-created-for-you = Du besitzt jetzt das Teilgrundstück { $arg0 } in { $arg1 }.
plot-subplot-owner-changed = Das Teilgrundstück { $arg0 } gehört jetzt { $arg1 }.
plot-subplot-parent-transferred = Das Hauptgrundstück deines Teilgrundstücks { $arg0 } gehört jetzt { $arg1 }.
plot-subplot-released = Das Teilgrundstück { $arg0 } wurde freigegeben. Sein Bereich bleibt durch das Hauptgrundstück geschützt.
plot-title = Grundstücke
plot-transferred = Grundstück { $arg0 } an { $arg1 } übertragen.
plot-transferred-to-you = { $arg1 } hat Grundstück { $arg0 } an dich übertragen.

## World · Vehicles

vehicle-admin-help = /vehicle — { vehicle-model-title }
    Modelle: model | model help
    Erfassung: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Fahrzeuge: spawn <model> [x y z] | reload
vehicle-command-description = Anzeige-Fahrzeuge fahren und verwalten
vehicle-controls = { $arg0 }: W/S und A/D steuern; Mausrad schaltet oder ändert Gas; Handwechsel wechselt den Modus; Schleichen steigt aus. /vehicle help · /vehicle dashboard öffnet die Fahrsteuerung.
vehicle-dash-assisted = Stabilitätshilfe
vehicle-dash-automatic = Automatik
vehicle-dash-contact = Boden- / Wasserkontakt: { $arg0 }
vehicle-dash-details = Details
vehicle-dash-down = Herunterschalten −
vehicle-dash-drive = Fahren
vehicle-dash-energy = Verbrauch { $arg0 } E/min · geschätzt { $arg1 } min / { $arg2 } km
vehicle-dash-engine-start = Motor starten
vehicle-dash-engine-stop = Motor abstellen
vehicle-dash-fuel = Kraftstoff { $arg0 } / { $arg1 } E · { $arg2 } %
vehicle-dash-gear = Gang { $arg0 }
vehicle-dash-gearbox = Getriebe
vehicle-dash-handbrake = Parken / Handbremse: { $arg0 }
vehicle-dash-heading = Kurs { $arg0 }°
vehicle-dash-health = Zustand { $arg0 } % · Insassen { $arg1 } / { $arg2 }
vehicle-dash-input = Gas { $arg0 } % · Bremse { $arg1 } % · Lenkung { $arg2 } %
vehicle-dash-manual = Manuell
vehicle-dash-mode-switch = Zu { $arg0 } wechseln
vehicle-dash-position = Ursprung { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Bedienfeld neu zentrieren
vehicle-dash-rpm = Drehzahl { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Tempo { $arg0 } km/h · Limit { $arg1 }
vehicle-dash-throttle-down = Gas −5 %
vehicle-dash-throttle-up = Gas +5 %
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Ohne Fahrhilfe
vehicle-dash-unavailable = Fahrersteuerung nicht verfügbar; öffne sie vom Fahrersitz aus erneut.
vehicle-dash-up = Hochschalten +
vehicle-dash-vertical = Höhe { $arg0 } m · Steigrate { $arg1 } m/s
vehicle-error = Fahrzeugaktion fehlgeschlagen: { $arg0 }
vehicle-group-changed = Gruppenstruktur geändert. Wähle die Gruppe vor dem Import erneut.
vehicle-group-invalid = Die Gruppe enthält nicht unterstützte, verwaltete, fehlende oder zu weit entfernte Entitäten. Alle müssen in dieser Welt innerhalb von 32 Blöcken liegen.
vehicle-group-limit = Höchstens 128 Anzeigen und insgesamt 256 Gruppenmitglieder importieren.
vehicle-group-not-found = Keine Anzeigegruppe gefunden. Wähle eine Gruppe, visiere eine Block- oder Gegenstandsanzeige an oder gib eine Entitäts-UUID an.
vehicle-group-selected = Ausgewählte Gruppe: { $arg0 ->
        [one] { $arg0 } Anzeige-Entität
       *[other] { $arg0 } Anzeige-Entitäten
    } · Wurzel { $arg1 }
vehicle-help = /vehicle — Fahrzeugsteuerung
    Fahren: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Antrieb: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    Anzeige: hud [on/off] | dashboard [drive/gearbox/details/off]
    Wartung: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } kn · Schub { $arg1 } % · Kurs { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · Höhe { $arg1 } m · Steigrate { $arg2 } m/s · Gas { $arg3 } % · { $arg4 }
vehicle-hud-status = Kraftstoff { $arg0 } % · Zustand { $arg1 } % · { $arg2 }
vehicle-model-add = Bauteil hinzufügen
vehicle-model-add-here = Stützpunkt an meinen Füßen hinzufügen
vehicle-model-anchor = Vorschauursprung hier setzen (in deine Blickrichtung)
vehicle-model-animation = Animation
vehicle-model-apply = Anwenden / bestätigen
vehicle-model-auto = Kollisionsgrenzen neu berechnen
vehicle-model-block = Block
vehicle-model-center-here = Schwerpunkt an meine Füße setzen
vehicle-model-center-look = Schwerpunkt auf anvisierte Fläche setzen
vehicle-model-check = Struktur und Parameter prüfen
vehicle-model-checked = Struktur und Parameter gültig. Beim Speichern werden zusätzlich Anzeigedaten, bestehende Fahrzeuge und Weltkollisionen geprüft.
vehicle-model-clone = Modell kopieren
vehicle-model-colliders = Kollisionsboxen
vehicle-model-copy = Kopieren und bearbeiten (Sitzkopien sind Beifahrersitze)
vehicle-model-corner-cancel = Ausstehende Kollisionsbox verwerfen
vehicle-model-corner-first = Erste Boxecke an meinen Füßen markieren
vehicle-model-corner-first-look = Erste Ecke auf anvisierter Fläche markieren
vehicle-model-corner-hint = Erste Ecke markiert. Gehe zur gegenüberliegenden Ecke, auch in der Höhe, und nutze /vehicle model collider corner2. Die Box folgt den Modellachsen.
vehicle-model-corner-required = Markiere zuerst die erste Ecke mit /vehicle model collider corner1.
vehicle-model-corner-second = Meine Füße als Gegenecke nutzen und Box hinzufügen
vehicle-model-corner-second-look = Anvisierte Fläche als Gegenecke nutzen
vehicle-model-create = { -vehicle-model(case: "accusative") } erstellen
vehicle-model-delete = Modell löschen
vehicle-model-delete-warning = Modell endgültig löschen? Modelle mit vorhandenen Fahrzeugen können nicht gelöscht werden.
vehicle-model-deleted = Modell gelöscht
vehicle-model-discard = Entwurf verwerfen
vehicle-model-discard-warning = Alle ungespeicherten Änderungen verwerfen? Dies kann nicht rückgängig gemacht werden.
vehicle-model-discarded = Entwurf verworfen
vehicle-model-draft = Ungespeicherter Entwurf
vehicle-model-driver = Fahrersitz
vehicle-model-driver-here = Fahrersitz an meinen Füßen hinzufügen
vehicle-model-edit = Entwurf bearbeiten / fortsetzen
vehicle-model-exit = Ausstiegsposition / Feinanpassung
vehicle-model-group = Axiom-Anzeigegruppe hinzufügen
vehicle-model-help = /vehicle model — { vehicle-model-title }
    Modelle: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Bearbeiten: set <field> <value...> | part/seat/collider/support — Tab zeigt Operationen und Argumente
    Entwurf: preview [off] | undo | redo | save | cancel
    In der Welt: anchor | pick | here | look | snap [on/off] | move <index> <dx dy dz>
    { vehicle-model-position-hint }
    seat position/exit | part position | support add/set | collider position: here / look
    collider corner1/corner2 [look] | collider cancel — { vehicle-model-corner-hint }
    Weitere Aktionen: check | apply — { vehicle-model-save-continue }
vehicle-model-here = Aktuelle Fußposition verwenden
vehicle-model-history-empty = Keine Änderung in dieser Verlaufsrichtung vorhanden.
vehicle-model-import = Axiom-Anzeigegruppe importieren
vehicle-model-item = Gegenstand
vehicle-model-library = Gespeicherte Modelle · Entwürfe bleiben bis zum Speichern nur im Arbeitsspeicher
vehicle-model-look = Zielplatzierung (Menü schließt; Rechtsklick bestätigt)
vehicle-model-look-missing = Keine Fläche getroffen. Visiere einen Weltblock oder ein Vorschau-Bauteil innerhalb von 32 Blöcken an.
vehicle-model-manual = Lokale Koordinaten eingeben
vehicle-model-options = Optionen
vehicle-model-parameters = Physik- und Motorparameter
vehicle-model-parts = Anzeigeelemente
vehicle-model-passenger-here = Beifahrersitz an meinen Füßen hinzufügen
vehicle-model-pick = Anvisiertes Vorschau-Bauteil bearbeiten
vehicle-model-pick-hint = Öffne eine Vorschau und visiere ein Block- oder Gegenstandsbauteil an, oder wähle zuerst ein Bauteil im Editor.
vehicle-model-place-cancelled = Zielplatzierung abgebrochen (abgelaufen, Bearbeitungszustand geändert oder manuell abgebrochen).
vehicle-model-place-hint = Zielplatzierung 30 Sekunden aktiv. Visiere einen Weltblock oder die Vorschau an; Rechtsklick mit der Haupthand bestätigt, Schleichen + Rechtsklick bricht ab.
vehicle-model-position = Position / Feinanpassung
vehicle-model-position-hint = Lokales X/Y/Z · here nutzt deine Füße, look eine getroffene Fläche · das optionale Raster folgt der Schrittweite.
vehicle-model-preview = Private Vorschau
vehicle-model-preview-failed = Vorschau nicht verfügbar; der Entwurf bleibt erhalten: { $arg0 }
vehicle-model-preview-hint = Private Vorschau aktiv. Nach fünf Minuten ohne Bearbeitung entfernt; /vehicle model preview off zum Schließen.
vehicle-model-redo = Rückgängig gemachte Änderung wiederholen
vehicle-model-remove = Bauteil entfernen
vehicle-model-remove-warning = Bauteil entfernen? Nachfolgende Indizes verschieben sich; Rückgängigmachen ist möglich.
vehicle-model-rotate = Bauteil drehen
vehicle-model-rotate-hint = Relative lokale Drehung: X = Neigung, Y = Gier, Z = Rollen. Die Position bleibt gleich.
vehicle-model-save = Prüfen und speichern
vehicle-model-save-continue = Speichern und weiterbearbeiten
vehicle-model-saved = Gespeichert
vehicle-model-scale = Bauteil skalieren
vehicle-model-scale-all = Alle Achsen skalieren × { $arg0 }
vehicle-model-scale-hint = Lokale Achsenlängen multiplizieren oder teilen; dies sind keine Welt-Abmessungen.
vehicle-model-seats = Sitze und Ausstiege
vehicle-model-selected = Ausgewählte Anzeigen importieren
vehicle-model-size = Kollisionsbox skalieren
vehicle-model-size-hint = Gesamte Breite, Höhe und Länge ändern; der Mittelpunkt bleibt gleich.
vehicle-model-snap-off = Rasterfang: aus
vehicle-model-snap-on = Rasterfang: an
vehicle-model-step = Schrittweite wechseln
vehicle-model-supports = Bodenkontaktpunkte
vehicle-model-text = Text
vehicle-model-title = Fahrzeugmodelle verwalten
vehicle-model-tools = Werkzeuge in der Welt
vehicle-model-transform-step = Schrittweite ändern
vehicle-model-undo = Letzte Änderung rückgängig machen
vehicle-result = Fahrzeug: { $arg0 }
vehicle-selection-info = Auswahl: { $arg0 ->
        [one] { $arg0 } Anzeige-Entität
       *[other] { $arg0 } Anzeige-Entitäten
    } · Gruppe { $arg1 } · Ursprung { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Kollision / Hindernis
vehicle-status-damage = Kritischer Fahrzeugschaden
vehicle-status-disabled = Fahrzeug stillgelegt
vehicle-status-engine-off = Motor aus
vehicle-status-engine-on = Motor läuft
vehicle-status-gear-limit = Schaltgrenze erreicht
vehicle-status-grounded = Boot auf Grund
vehicle-status-low-fuel = Wenig Kraftstoff
vehicle-status-manual-only = Zuerst in den manuellen Modus wechseln
vehicle-status-no-fuel = Kein Kraftstoff
vehicle-status-overrev = Herunterschalten würde den Motor überdrehen
vehicle-status-shifting = Schaltvorgang läuft
vehicle-status-stall = Motor abgewürgt
vehicle-status-stop-first = Vor Richtungswechsel oder Parken anhalten
vehicle-value-off = Aus
vehicle-value-on = Ein

## World · Elevators, regeneration and spawn

async-regen-already-running = Es läuft bereits eine Regeneration; nutze /regen status oder /regen cancel
async-regen-apply-expired = Es gibt keinen gültigen Plan; führe zuerst /regen preview aus
async-regen-apply-plan = Gespeicherten Plan anwenden
async-regen-apply-started = { $arg0 } wird auf die gespeicherte Auswahl angewendet: { $arg1 }
async-regen-awaiting-apply = Plan wartet auf Anwendung: { $arg0 } · { $arg1 } · läuft in { $arg2 ->
        [one] { $arg2 } Sekunde
       *[other] { $arg2 } Sekunden
    } ab
async-regen-block-preview-progress = Blockvorschau · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } Chunk
       *[other] { $arg1 } Chunks
    } geprüft · { $arg2 ->
        [one] { $arg2 } Änderung
       *[other] { $arg2 } Änderungen
    } · { $arg3 ->
        [one] { $arg3 } gefährdete Blockentität
       *[other] { $arg3 } gefährdete Blockentitäten
    }
async-regen-block-preview-summary = Vorschau: { $arg0 ->
        [one] { $arg0 } Blockänderung
       *[other] { $arg0 } Blockänderungen
    } für { $arg2 }; Überschreibungsrisiko: { $arg1 ->
        [one] { $arg1 } vorhandene Blockentität
       *[other] { $arg1 } vorhandene Blockentitäten
    }
async-regen-cancelled = Regeneration abgebrochen. Die bereits angewendeten { $arg0 ->
        [one] { $arg0 } Änderung
       *[other] { $arg0 } Änderungen
    } bleiben in der Welt
async-regen-command-description = Eine FastAsyncWorldEdit-Auswahl asynchron regenerieren
async-regen-completed = Regeneration abgeschlossen: { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcke
    } in { $arg1 } geändert
async-regen-failed = Regeneration wegen eines internen Fehlers beendet. Die bereits angewendeten { $arg0 ->
        [one] { $arg0 } Änderung
       *[other] { $arg0 } Änderungen
    } bleiben in der Welt
async-regen-filter-all = alle Blocktypen
async-regen-invalid-blocks = Unbekannte oder ungültige Blocktypen: { $arg0 }
async-regen-not-running = Du hast keine laufende Regeneration
async-regen-nothing-to-apply = Die Auswahl entspricht diesem Plan bereits; es ist nichts anzuwenden
async-regen-plan-cancel = Abbrechen
async-regen-plan-dialog-title = Regenerationsplan anwenden
async-regen-preview-cancelled = Vorschau abgebrochen; keine Weltdaten wurden geändert
async-regen-preview-started = Vorschau gestartet: Auswahl { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcke
    } · Bereich { $arg1 ->
        [one] { $arg1 } Chunk
       *[other] { $arg1 } Chunks
    } · Modus { $arg2 }
async-regen-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } Chunk
       *[other] { $arg2 } Chunks
    } geplant · { $arg3 ->
        [one] { $arg3 } Änderung
       *[other] { $arg3 } Änderungen
    } angewendet
async-regen-safety-note = Außerhalb der gespeicherten Auswahl wird nichts geändert. Gebäude darin können überschrieben werden; Vanilla erkennt keine von Spielern gesetzten Blöcke. Prüfe die Visualisierung und erstelle vor dem Anwenden ein Backup.
async-regen-scope-blocks = passende Blöcke regenerieren: { $arg0 }
async-regen-selection-required = Erstelle zuerst eine vollständige FastAsyncWorldEdit-Auswahl
async-regen-skipped-ungenerated = Nicht generierte Zielchunks übersprungen: { $arg0 ->
        [one] { $arg0 } Chunk
       *[other] { $arg0 } Chunks
    }. Es wurden keine Zielchunks erstellt oder geändert.
async-regen-start-failed = Die Regeneration konnte nicht gestartet werden; Details stehen im Serverprotokoll
async-regen-state-applying = Änderungen werden angewendet
async-regen-state-cancelled = abgebrochen
async-regen-state-completed = abgeschlossen
async-regen-state-failed = fehlgeschlagen
async-regen-state-generating = Chunks werden erzeugt und verglichen
async-regen-state-preparing = Quellwelt wird vorbereitet
async-regen-status = { $arg0 } · Planung { $arg1 }/{ $arg2 ->
        [one] { $arg2 } Chunk
       *[other] { $arg2 } Chunks
    } · angewendet { $arg3 }/{ $arg4 ->
        [one] { $arg4 } Änderung
       *[other] { $arg4 } Änderungen
    }
async-regen-structure-metadata-applied = { $arg0 ->
        [one] { $arg0 } Strukturstart
       *[other] { $arg0 } Strukturstarts
    } und { $arg1 ->
        [one] { $arg1 } Chunk-Referenz
       *[other] { $arg1 } Chunk-Referenzen
    } übertragen; /locate und strukturabhängiges Spawning erkennen sie
async-regen-upgrade-apply-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } Chunk
       *[other] { $arg2 } Chunks
    } geplant · { $arg3 ->
        [one] { $arg3 } Block
       *[other] { $arg3 } Blöcke
    } und { $arg4 ->
        [one] { $arg4 } Biomzelle
       *[other] { $arg4 } Biomzellen
    } angewendet
async-regen-upgrade-boundary-note = { $arg0 ->
        [one] { $arg0 } Biomzelle an der Auswahlgrenze wird übersprungen, damit außerhalb nichts geändert wird
       *[other] { $arg0 } Biomzellen an der Auswahlgrenze werden übersprungen, damit außerhalb nichts geändert wird
    }
async-regen-upgrade-completed = Welt-Upgrade abgeschlossen: { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcke
    } und { $arg1 ->
        [one] { $arg1 } Biomzelle
       *[other] { $arg1 } Biomzellen
    } in { $arg2 } geändert
async-regen-upgrade-preview-progress = Upgrade-Vorschau · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } Chunk
       *[other] { $arg1 } Chunks
    } geprüft · { $arg2 ->
        [one] { $arg2 } Blockänderung
       *[other] { $arg2 } Blockänderungen
    } · { $arg3 ->
        [one] { $arg3 } Biomzelle
       *[other] { $arg3 } Biomzellen
    } · { $arg4 ->
        [one] { $arg4 } Struktur
       *[other] { $arg4 } Strukturen
    }
async-regen-upgrade-preview-summary = Vorschau: { $arg0 ->
        [one] { $arg0 } Blockänderung
       *[other] { $arg0 } Blockänderungen
    }, { $arg1 ->
        [one] { $arg1 } Biomzelle
       *[other] { $arg1 } Biomzellen
    }, { $arg2 ->
        [one] { $arg2 } Struktur
       *[other] { $arg2 } Strukturen
    } (für vollständige Metadatenübertragung geeignet: { $arg3 }); Überschreibungsrisiko: { $arg4 ->
        [one] { $arg4 } vorhandene Blockentität
       *[other] { $arg4 } vorhandene Blockentitäten
    }
async-regen-upgrade-scope-biomes = nur Biome
async-regen-upgrade-scope-terrain = Gelände, Strukturen und Biome
async-regen-usage = Verwendung: /regen preview blocks [Typen...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Visualisierung in { $arg0 }: Cyan = Auswahlgrenze, Orange = Stichprobe geänderter Blöcke, Rot = gefährdete Blockentität. Nur du siehst diese Partikel.
async-regen-visualization-selection = Auswahl in { $arg0 }: Cyanfarbene Partikel markieren die Grenze. Nur du siehst sie.
async-regen-fawe-required = FastAsyncWorldEdit wird zum Lesen der Auswahl benötigt
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ Schleichen
iron-elevator-tip-mm = Baue einen Aufzug aus senkrecht übereinander angeordneten { -iron-elevator-block }. Stelle dich darauf: <green>Springen fährt hoch</green>, <aqua>Schleichen fährt runter</aqua>. Bei mehr als zwei Etagen erscheint eine <gold>Etagenauswahl</gold>.
iron-elevator-up = ↑ Springen
spawn-beacon-color-command-description = Farbwechsel des Spawn-Leuchtfeuers umschalten
spawn-beacon-color-disabled = Farbwechsel des Spawn-Leuchtfeuers deaktiviert
spawn-beacon-color-enabled = Farbwechsel des Spawn-Leuchtfeuers aktiviert
spawn-command-description = Zum Spawn zurückkehren
spawn-tip-return-mm = Mit <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> kehrst du zum <yellow>Server-Spawn</yellow> zurück.
spawn-world-missing = Die Spawn-Welt ist nicht verfügbar

## Entertainment · Music

music-action-add-queue = Zur Warteschlange hinzufügen
music-action-play-nearby = Am nächsten Plattenspieler abspielen
music-action-play-now = Jetzt an diesem Plattenspieler abspielen
music-action-take-disc = Schallplatte nehmen
music-added-to-queue-rich = Zur Warteschlange hinzugefügt: { $music }
music-album = Album: { $arg0 }
music-all-in-queue = Alle Lieder sind bereits in der Warteschlange
music-artist = Künstler: { $arg0 }
music-back = Zurück
music-browser-left-click = Linksklick
music-browser-right-click = Rechtsklick
music-cache-clear-done = Online-Musikcache geleert
music-cache-clear-started = Der Online-Musikcache wird geleert...
music-cache-operation-failed = Cache-Vorgang für Musik fehlgeschlagen: { $arg0 }
music-cache-status = Online-Musikcache: { $arg0 ->
        [one] { $arg0 } Datei
       *[other] { $arg0 } Dateien
    }, { $arg1 } / { $arg2 }
music-cached = Im Cache - sofort abspielbar
music-clear-queue = Warteschlange leeren
music-clear-queue-done = Warteschlange geleert ({ $arg0 ->
        [one] { $arg0 } Lied
       *[other] { $arg0 } Lieder
    })
music-command-description = Lokale, Online- und NBS-Musik für Plattenspieler
music-disc-left = Linksklick: Schallplatte nehmen
music-disc-right = Rechtsklick: auf dem nächsten Plattenspieler abspielen
music-duplicate-in-queue = Dieses Lied ist bereits in der Warteschlange
music-duration = Dauer: { $arg0 }
music-empty-library = Die Musikbibliothek ist leer
music-empty-library-description = Keine lokalen Audiodateien oder NBS-Titel verfügbar
music-empty-nbs = Keine NBS-Titel gefunden
music-empty-nbs-description = Wechsle zu aller Musik oder füge eine NBS-Datei hinzu
music-empty-playlist = Diese Playlist enthält keine verfügbaren Titel
music-empty-playlist-description = Prüfe, ob die Playlist öffentlich und die richtige Plattform ausgewählt ist
music-empty-search = Keine passenden Lieder
music-empty-search-description = Versuche ein anderes Schlüsselwort oder prüfe den Status benutzerdefinierter Quellen
music-finished = Beendet
music-format = Format: { $arg0 }
music-inventory-full = Dein Inventar ist voll. Die Schallplatte konnte nicht hinzugefügt werden
music-jukebox-audible-field-conflict = Ein anderer Plattenspieler spielt in einem überlappenden deutlich hörbaren Bereich ein anderes Lied
music-jukebox-music-mode = Musikmodus
music-jukebox-play-order = Wiedergabereihenfolge
music-jukebox-range = Wiedergabereich
music-jukebox-range-value = Aktuell: { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcke
    }
music-jukebox-rhythm-mode = Rhythmusmodus
music-jukebox-too-far = Du bist zu weit vom Plattenspieler entfernt (maximal { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcke
    })
music-jukebox-tp-hover = Zum Plattenspieler teleportieren
music-jukebox-unavailable = Der Plattenspieler wurde entfernt oder sein Chunk ist nicht verfügbar
music-jukebox-usage-mode = Jukebox-Nutzung
music-jukebox-volume = Lautstärke der Musikbox
music-jukebox-volume-value = Aktuell: { $arg0 }%
music-library-button = Musikbibliothek
music-library-random-mode = Musikbibliothek mischen
music-library-random-mode-desc = Zufällig aus der gesamten Musikbibliothek abspielen, ohne ein Lied direkt zu wiederholen
music-loading-title = Wird geladen
music-menu-nbs-title = NBS-Musik - Seite { $arg0 }/{ $arg1 }
music-menu-playlist-title = Wiedergabeliste: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = NBS-Suche: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-title = Suche: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-title = Schallplatten - Seite { $arg0 }/{ $arg1 }
music-mode-switched = Zu { $arg0 }
music-nearest-jukebox-missing = Im Umkreis von { $arg0 ->
        [one] { $arg0 } Block
       *[other] { $arg0 } Blöcken
    } wurde kein Plattenspieler gefunden
music-next-page = Nächste Seite
music-no-disc = Keine Schallplatte
music-no-files = Es sind keine Musikdateien verfügbar
music-no-search-results-rich = Keine Lieder mit "{ $keyword }" gefunden
music-now-playing-rich = Läuft gerade: { $music } am Plattenspieler { $location }
music-online-partial-failure = Fehlgeschlagene Quellenanfragen: { $arg0 ->
        [one] { $arg0 } Anfrage
       *[other] { $arg0 } Anfragen
    }; verfügbare Ergebnisse werden angezeigt
music-online-rate-limited = Zu viele Musikanfragen. Versuche es in { $arg0 ->
        [one] { $arg0 } Sekunde
       *[other] { $arg0 } Sekunden
    } erneut.
music-online-searching = Lokale und Online-Quellen werden nach "{ $arg0 }" durchsucht...
music-original-author = Originalautor: { $arg0 }
music-page-out-of-range = Seite außerhalb des Bereichs. Letzte Seite: { $arg0 }
music-page-total = Insgesamt { $arg0 ->
        [one] { $arg0 } Lied
       *[other] { $arg0 } Lieder
    }
music-page-usage-desc = Zu einer Seite springen
music-partial-results = Fehlgeschlagene Quellenanfragen: { $arg0 ->
        [one] { $arg0 } Anfrage
       *[other] { $arg0 } Anfragen
    }; verfügbare Ergebnisse werden angezeigt
music-paused = Pausiert
music-plasmo-voice-unavailable = Der Song wurde gestartet, aber du kannst ihn nicht hören, weil Plasmo Voice nicht installiert, verbunden oder aktiviert ist
music-play-next = Als nächstes spielen
music-play-random = Zufällig abspielen
music-playback-failed = "{ $arg0 }" konnte nicht abgespielt werden. Prüfe die Quelle oder Musikdatei und versuche es erneut.
music-playback-status = Wiedergabestatus: { $arg0 }
music-playing = Wird abgespielt
music-playing-now-rich = Wird jetzt abgespielt: { $music }
music-playlist-import-button = Playlist importieren
music-playlist-import-done = „{ $arg0 }“ importiert: { $arg1 } von { $arg2 ->
        [one] { $arg2 } Lied
       *[other] { $arg2 } Liedern
    }.
music-playlist-import-failed = Playlist-Import fehlgeschlagen: { $arg0 }
music-playlist-import-prompt = Wähle eine Plattform und füge dann eine öffentliche Playlist-ID oder einen Link ein:
music-playlist-import-usage-desc = Öffentliche Playlist von einer anderen Musikplattform importieren
music-playlist-importing = Playlist wird importiert...
music-playlist-reference-prompt = Öffentliche Playlist-ID oder Link einfügen
music-prev-page = Vorherige Seite
music-queue-empty = Die Warteschlange ist leer
music-queue-move-down = Nach unten
music-queue-move-up = Nach oben
music-queue-remove = Lied entfernen
music-queue-summary = Warteschlange: { $arg0 ->
        [one] { $arg0 } Lied
       *[other] { $arg0 } Lieder
    }
music-queue-summary-description = Seite { $arg0 }/{ $arg1 }
music-random-add = Zufälliges Lied hinzufügen
music-random-added-to-queue-rich = Zufällig zur Warteschlange hinzugefügt: { $music }
music-random-disc = Zufällige Schallplatte
music-random-disc-got-rich = Zufällige Schallplatte erhalten: { $music }
music-random-mode = Wiedergabeliste mischen
music-random-mode-desc = Die Wiedergabeliste zufällig abspielen, ohne ein Lied direkt zu wiederholen
music-rank-down = Nach unten verschoben
music-rank-up = Nach oben verschoben
music-reload-done = Neuladen abgeschlossen: { $arg0 ->
        [one] { $arg0 } lokaler Titel
       *[other] { $arg0 } lokale Titel
    }; LX-Quellen verfügbar { $arg1 }/{ $arg2 }; Abonnements geprüft { $arg3 }, aktualisiert { $arg4 }
music-reload-failed = Neuladen der Musik fehlgeschlagen: { $arg0 }
music-reload-issues = Probleme beim Neuladen: { $arg0 }
music-reload-partial = Neuladen mit beibehaltenem Zustand abgeschlossen: { $arg0 ->
        [one] { $arg0 } lokaler Titel
       *[other] { $arg0 } lokale Titel
    }; LX-Quellen verfügbar { $arg1 }/{ $arg2 }; Abonnements geprüft { $arg3 }, aktualisiert { $arg4 }
music-reload-started = Musikdateien und benutzerdefinierte Quellen werden neu geladen...
music-remote-source-error = Aktualisierungsfehler: { $arg0 }
music-remote-source-import-done = LX-Quellenabonnement importiert: { $arg0 }
music-remote-source-import-failed = Import der LX-Quelle fehlgeschlagen: { $arg0 }
music-remote-source-import-started = LX-Quellenabonnement wird importiert...
music-remote-source-remove-done = LX-Quellenabonnement entfernt: { $arg0 }
music-remote-source-remove-failed = LX-Quellenabonnement konnte nicht entfernt werden: { $arg0 }
music-remote-source-remove-not-found = LX-Quellenabonnement nicht gefunden: { $arg0 }
music-remote-source-update-done = Geprüft: { $arg0 ->
        [one] { $arg0 } Abonnement
       *[other] { $arg0 } Abonnements
    }; aktualisiert: { $arg1 }; fehlgeschlagen: { $arg2 }.
music-remote-source-update-failed = Aktualisierung der LX-Quelle fehlgeschlagen: { $arg0 }
music-remote-source-update-started = Importierte LX-Quellenabonnements werden geprüft...
music-remote-source-updated-at = aktualisiert: { $arg0 }
music-remote-sources-title = Importierte LX-Quellenabonnements: { $arg0 }
music-removed-from-queue-rich = Aus der Warteschlange entfernt: { $music }
music-repeat-one-mode = Ein Lied wiederholen
music-repeat-one-mode-desc = Das aktuelle Lied wiederholen, bis du manuell weiterschaltest
music-request-error = Anfrage fehlgeschlagen: { $arg0 }
music-rhythm-animation-offset = Animationsversatz { $arg0 } ms
music-rhythm-calibration = Latenzkalibrierung
music-rhythm-calibration-accumulating = Stabiles Timing wird bestätigt… bitte weiterspielen
music-rhythm-calibration-adapting = Rhythmus wird angepasst… bitte weiterspielen
music-rhythm-calibration-complete = Latenztest abgeschlossen · Rhythmusspiel bereit
music-rhythm-calibration-done = Latenztest abgeschlossen · Einstellungen gespeichert
music-rhythm-calibration-failed = Das Audio des Latenztests wurde unerwartet unterbrochen. Der Test wurde sicher beendet; bitte erneut starten
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Tastatur visuell → Maus visuell
music-rhythm-calibration-listen = Höre dir den erzeugten Rhythmus einmal an · noch nicht drücken
music-rhythm-calibration-minecraft-instruction = Folge dem Minecraft-Rhythmus und drücke 1–4
music-rhythm-calibration-no-input = Keine Eingabe erkannt. Prüfe Tasten und Lautstärke und spiele weiter
music-rhythm-calibration-out-of-range = Die gemessene Verzögerung liegt außerhalb des unterstützten Bereichs. Prüfe die Audioeinstellungen
music-rhythm-calibration-plasmo-instruction = Jetzt mitspielen: Wiederhole den Plasmo-Voice-Rhythmus und drücke bei jedem Schlag 1–4
music-rhythm-calibration-plasmo-ready = Minecraft-Test fertig; höre denselben Rhythmus zuerst über Plasmo Voice
music-rhythm-calibration-pointer-instruction = Klicke links oder rechts, wenn die Blöcke die Trefferlinie überqueren.
music-rhythm-calibration-prompt-confirm = Vollständigen Test starten
music-rhythm-calibration-prompt-confirm-hint = Automatisch zurück nach Abschluss.
music-rhythm-calibration-prompt-later = Nicht jetzt
music-rhythm-calibration-prompt-reason = Starte einen Latenztest vor dem Spielen.
music-rhythm-calibration-prompt-title = Vor dem Spielen ist ein Latenztest erforderlich
music-rhythm-calibration-realigning = Der Audiostream wurde unterbrochen. Die Demonstration wird wiederholt; fertige Messwerte bleiben erhalten
music-rhythm-calibration-required = Vor dem Rhythmusspiel muss der vollständige Latenztest abgeschlossen werden
music-rhythm-calibration-reset-done = Latenzkalibrierung wurde zurückgesetzt
music-rhythm-calibration-result-hold = Alle Tests abgeschlossen. Ergebnis prüfen; danach geht es automatisch zurück
music-rhythm-calibration-stage-minecraft = Minecraft-Notenblock
music-rhythm-calibration-stage-plasmo = Plasmo-Voice-Audio- und Eingabetest
music-rhythm-calibration-stage-pointer = Maus
music-rhythm-calibration-stage-visual = Bild- und Eingabetest
music-rhythm-calibration-visual-instruction = Drücke 1–4, wenn die Blöcke die Trefferlinie überqueren.
music-rhythm-calibration-visual-preview = Beobachte einen vollständigen Durchlauf ohne Eingabe · erst bei geänderter Anweisung beginnen
music-rhythm-calibration-voice-required = Für den vollständigen Latenztest muss Plasmo Voice verbunden und aktiviert sein
music-rhythm-chart-preparing = Vollständige Beatmap wird vorbereitet…
music-rhythm-combo = Kombo ×{ $arg0 }
music-rhythm-difficulty-easy = Leicht
music-rhythm-difficulty-expert = Experte
music-rhythm-difficulty-hard = Schwer
music-rhythm-difficulty-normal = Normal
music-rhythm-exit = Rhythmusspiel verlassen
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
music-rhythm-get-ready = Bereit machen
music-rhythm-go = LOS!
music-rhythm-good = Gut
music-rhythm-great = Großartig
music-rhythm-latency = Latenz { $arg0 } ms
music-rhythm-latency-test = Vollständiger Latenztest
music-rhythm-miss = Verfehlt
music-rhythm-mode-falling = Fallende Noten
music-rhythm-mode-radial = Rundum-Angriff
music-rhythm-mode-spatial-aim = Räumliches Zielen
music-rhythm-network-too-high = Die Netzwerklatenz ist für eine faire Wertung zu hoch ({ $arg0 } ms; Limit { $arg1 } ms)
music-rhythm-network-warning = Die Netzwerklatenz schwankt ({ $arg0 } ms). Bleibt sie über { $arg1 } ms, wird die Sitzung sicher beendet
music-rhythm-no-beats = In diesem Titel wurden keine spielbaren Takte erkannt
music-rhythm-perfect = Perfekt
music-rhythm-preparing = Warte, bis der Titel vollständig zwischengespeichert und rhythmisch analysiert ist
music-rhythm-ready = Vollständige Beatmap bereit — Start ab dem ersten Schlag
music-rhythm-requires-playback = Spiele zuerst einen Titel auf diesem Plattenspieler ab
music-rhythm-reset-calibration = Latenzkalibrierung zurücksetzen
music-rhythm-results = Ergebnis
music-rhythm-score = Punkte: { $arg0 }
music-rhythm-select-track = Rhythmus-Titel wählen
music-rhythm-spatial-no-room = Für räumliches Zielen ist hier nicht genug Platz. Gehe in einen offeneren Bereich.
music-rhythm-spatial-slider-hint = Folge dem bewegten Stern und klicke jeden Kontrollpunkt · nicht halten
music-rhythm-start-game = Spiel starten
music-rhythm-waiting-for-player = Warte auf einen Spieler, der das Rhythmusspiel startet
music-sample-rate = Abtastrate: { $arg0 }
music-search-button = Lieder suchen
music-search-keyword-required = Der Suchbegriff darf nicht leer sein
music-search-keyword-too-long = Suchbegriff zu lang. Limit: { $arg0 } Zeichen.
music-search-usage-desc = Lieder suchen
music-section-all = Alle Musik
music-section-nbs = NBS-Musik
music-select-music = Musik auswählen
music-sequential-mode = Wiedergabeliste wiederholen
music-sequential-mode-desc = Die Wiedergabeliste der Reihe nach abspielen und am Anfang neu beginnen
music-size = Größe: { $arg0 }
music-sort-button = Sortierung
music-sort-current = Aktuell: { $arg0 }
music-sort-default = Standardsortierung
music-sort-most-played = Meistgespielt
music-sort-recently-played = Zuletzt gespielt
music-sound-settings = Toneinstellungen
music-source-actions = Aktionen: { $arg0 }
music-source-available = verfügbar
music-source-capabilities = Fähigkeiten: { $arg0 }
music-source-error = Fehlerursache: { $arg0 }
music-source-failures = Fehler: { $arg0 }
music-source-retry = erneut in { $arg0 ->
        [one] { $arg0 } Sekunde
       *[other] { $arg0 } Sekunden
    }
music-source-unavailable = nicht verfügbar
music-sources-empty = Keine benutzerdefinierten LX-Musikquellen gefunden
music-sources-title = Benutzerdefinierte LX-Musikquellen: { $arg0 }
music-stop-eject = Anhalten und auswerfen
music-stop-eject-done = Wiedergabe angehalten und Schallplatte ausgeworfen
music-stopped = Angehalten
music-track-details = Titeldetails
music-unavailable-disc = Nicht verfügbare Schallplatte
music-unavailable-disc-description = Diese MIK-Schallplatte ist nicht im aktuellen Katalog; sie kann dennoch angehalten und ausgeworfen werden

## Entertainment · Anniversary

anniversary-accepted = Du nimmst an der Jubiläumsverlosung teil. Die erste Ziehung wird nach 4 Online-Stunden ohne AFK freigeschaltet.
anniversary-admin-audit-action-bonus = Bonuschance für eine Bauwerk-Einreichung vergeben
anniversary-admin-audit-action-gift = Event-Preis verschenkt: { $arg0 }
anniversary-admin-audit-action-removal = Frühere Administratorteilnahme entfernt und { $arg0 ->
        [one] { $arg0 } Preis
       *[other] { $arg0 } Preise
    } zurückgebucht.
anniversary-admin-audit-action-unknown = Unbekannte Aktion: { $arg0 }
anniversary-admin-audit-empty = Noch keine Verwaltungsprotokolleinträge.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Ausführender: { $arg0 } ({ $arg1 }); Ziel: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Jubiläums-Verwaltungsprotokoll ({ $arg0 }/{ $arg1 } angezeigt)
anniversary-admin-bonus-cancelled = Durch Geschenkpaket beendet
anniversary-admin-bonus-pending = Vergeben, noch nicht gezogen
anniversary-admin-bonus-used = Gezogen
anniversary-admin-boss = Jubiläumsverwaltung · Mit { $arg0 } Fortschritt anzeigen
anniversary-admin-commands = Befehle: /anniversary winners [Seite] | /anniversary audit [Spieler] | /anniversary bonus <Spieler>
anniversary-admin-control = Dynamische Steuerung: freigegeben { $arg0 }; jetzt verfügbar { $arg1 }; Basiswahrscheinlichkeit { $arg2 }; letzte Rate { $arg3 }
anniversary-admin-draw-bonus = Bonus für Bauwerk-Einreichung
anniversary-admin-draw-detail = { $arg0 }: gezogen um { $arg1 }; Ergebnis { $arg2 }
anniversary-admin-draw-metrics = Wahrscheinlichkeit { $arg0 }; Zufallswert { $arg1 }; Algorithmusversion { $arg2 }
anniversary-admin-draw-replaced = Durch Geschenkpaket ersetzt; Bestand zurückgebucht
anniversary-admin-draw-round = Reguläre Runde { $arg0 }
anniversary-admin-event = Veranstaltungsstatus: { $arg0 }; Zeitraum: { $arg1 }
anniversary-admin-excluded = Jubiläumsadministratoren dürfen nicht an der Ziehung teilnehmen. Stattdessen wurde die Verwaltungsübersicht geöffnet.
anniversary-admin-none = Keine
anniversary-admin-opportunities = Durchgeführte Ziehungen: { $arg0 }; jetzt bereit: { $arg1 }; erhaltene Chancen: { $arg2 }; Gewinne/Nieten: { $arg3 }
anniversary-admin-participants = Teilnehmende: { $arg0 }
anniversary-admin-player-bag = Endgültige Event-Tasche: { $arg0 }
anniversary-admin-player-bonus = Bauwerk-Bonus: { $arg0 }; vergeben: { $arg1 }; vergeben von: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Details der Ziehungschancen
anniversary-admin-player-not-found = Veranstaltungsteilnehmer nicht gefunden: { $arg0 }
anniversary-admin-player-outcome = Gewinne: { $arg0 }; Nieten: { $arg1 }; durchgeführte Ziehungen: { $arg2 }; Bauwerk-Bonus: { $arg3 }
anniversary-admin-player-progress = Beigetreten: { $arg0 }; anrechenbare Onlinezeit: { $arg1 }; reguläre Ziehungen: { $arg2 }; jetzt bereite reguläre Chancen: { $arg3 }
anniversary-admin-player-title = Teilnehmerprüfung: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Aktiv
anniversary-admin-state-ended = Beendet
anniversary-admin-state-scheduled = Geplant
anniversary-admin-stock = Restbestand: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | gesamt { $arg4 }
anniversary-admin-target-excluded = { $arg0 } ist Jubiläumsadministrator und darf keine Ziehungschance erhalten.
anniversary-admin-title = Verwaltungsübersicht zum fünften Jubiläum
anniversary-admin-winners-empty = Noch keine tatsächlich gezogenen Gewinne.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · gezogen um { $arg3 }
anniversary-admin-winners-title = Preisausgabeliste (Seite { $arg0 }/{ $arg1 }, { $arg2 ->
        [one] { $arg2 } Preis
       *[other] { $arg2 } Preise
    })
anniversary-bag-empty = Noch keine gewonnenen Preise
anniversary-bag-item-locked = Die Event-Tasche ist schreibgeschützt
anniversary-bag-item-name = Jubiläumspreis · { $arg0 }
anniversary-bag-not-participating = Du nimmst noch nicht teil. Verwende zuerst { $arg0 }.
anniversary-bag-title = Event-Tasche zum fünften Jubiläum
anniversary-bag-updated = Dein Preis liegt in der Event-Tasche. Öffne sie mit { $arg0 }.
anniversary-bonus-already-granted = { $arg0 } hat den Bonus für Bauwerk-Einreichungen bereits erhalten und kann ihn nicht erneut bekommen.
anniversary-bonus-boss-ready = Bonusziehung für Bauwerk-Einreichungen bereit · Mit { $arg0 } ziehen
anniversary-bonus-broadcast-won = [Jubiläumsverlosung] { $arg0 } hat durch den Bauwerk-Bonus { $arg1 } gewonnen!
anniversary-bonus-draw-not-won = Bonusziehung für Bauwerk-Einreichungen: Diesmal kein Gewinn.
anniversary-bonus-draw-sold-out = Bonusziehung: Kein Gewinn, da alle Preise vergeben sind.
anniversary-bonus-draw-won = Bonusziehung für Bauwerk-Einreichungen: Glückwunsch, du hast { $arg0 } gewonnen!
anniversary-bonus-gift-pack-final = { $arg0 } hat bereits das Geschenkpaket gewonnen und kann keine Bonusziehung erhalten.
anniversary-bonus-grant-not-active = Der Bonus für Bauwerk-Einreichungen kann außerhalb des Eventzeitraums nicht vergeben werden.
anniversary-bonus-grant-success = Bonus für Bauwerk-Einreichungen an { $arg0 } vergeben.
anniversary-bonus-granted = Du hast eine zusätzliche Ziehung für angenommene Bauwerk-Einreichungen erhalten. Nutze /anniversary zum Ziehen.
anniversary-bonus-participant-not-found = Kein Eventteilnehmer namens { $arg0 } gefunden
anniversary-bonus-status-ready = Bonus für Bauwerk-Einreichungen: Eine zusätzliche Ziehung ist bereit.
anniversary-bonus-status-used = Bonus für Bauwerk-Einreichungen: Die zusätzliche Ziehung wurde verwendet.
anniversary-boss-completed = Jubiläumsverlosung · Beide Runden abgeschlossen
anniversary-boss-gift-pack-final = Geschenkpaket gewonnen · Mit { $arg0 } Event-Tasche öffnen
anniversary-boss-invite = Verlosung zum fünften Jubiläum · Mit { $arg0 } teilnehmen
anniversary-boss-progress = Jubiläumsverlosung { $arg0 }/{ $arg1 } · Runde { $arg2 } in { $arg3 }
anniversary-boss-ready = Jubiläumsrunde { $arg0 } ist bereit · Mit { $arg1 } ziehen
anniversary-broadcast-won = [Jubiläumsverlosung] { $arg0 } hat in Runde { $arg1 } { $arg2 } gewonnen!
anniversary-cancel = Jetzt nicht
anniversary-command-description = An der Verlosung zum fünften Serverjubiläum teilnehmen
anniversary-confirm = Zustimmen und teilnehmen
anniversary-confirm-hint = Startet die Zählung der Online-Zeit ohne AFK
anniversary-delivery-note = Für ein Ziel auf dem chinesischen Festland gib eine gültige SF-ID für datenschutzgeschützten Versand an. Außerhalb des chinesischen Festlands sind echter Empfängername, vollständige Adresse und gültige Telefonnummer nötig. Bezahle deinen Versandanteil, sonst verfällt der Preis.
anniversary-dialog-prizes = Preise: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } und 5 { $arg3 }
anniversary-dialog-time = Eventzeitraum: { $arg0 }
anniversary-dialog-title = Regeln der Jubiläumsverlosung
anniversary-draw-not-won = Runde { $arg0 }: Diesmal kein Gewinn.
anniversary-draw-sold-out = Runde { $arg0 }: Kein Gewinn, da alle Preise vergeben sind.
anniversary-draw-won = Runde { $arg0 }: Glückwunsch, du hast { $arg1 } gewonnen!
anniversary-ended = Die Verlosung zum fünften Jubiläum ist beendet
anniversary-ended-results = Deine Verlosungsergebnisse: { $arg0 }
anniversary-gift-pack-final = Das Geschenkpaket ist dein endgültiger Preis. Es hat { $arg0 ->
        [one] { $arg0 } anderen Preis
       *[other] { $arg0 } andere Preise
    } ersetzt und dem Bestand wieder gutgeschrieben; alle übrigen Ziehungen sind beendet.
anniversary-gift-prize-not-owned = Dieser Event-Preis gehört dir nicht: { $arg0 }
anniversary-gift-received = { $arg0 } hat dir { $arg1 } geschenkt. Der Preis liegt jetzt in deiner Event-Tasche.
anniversary-gift-self = Du kannst dir keinen Preis selbst schenken.
anniversary-gift-success = { $arg0 } wurde an { $arg1 } verschenkt.
anniversary-gift-usage = Verwendung: { $arg0 }
anniversary-not-active = Das Jubiläumsevent ist derzeit nicht aktiv
anniversary-not-started = Das Jubiläumsevent hat noch nicht begonnen. Zeitraum: { $arg0 }
anniversary-prize-badge = Gruppenfoto-Anstecker zum fünften Jubiläum
anniversary-prize-gift-pack = Geschenkpaket zum fünften Jubiläum
anniversary-prize-keychain = Logo-Schlüsselanhänger
anniversary-prize-mug = Logo-Tasse
anniversary-prize-not-won = kein Gewinn
anniversary-prize-sold-out = ausverkaufter Preispool
anniversary-rule-1 = 1. Nach der Teilnahme kannst du alle 4 Stunden ziehen, insgesamt zweimal. Gewinne kommen in die virtuelle Event-Tasche, jede Preisart höchstens einmal. Das Geschenkpaket bleibt als einziger Preis und beendet alle übrigen Ziehungen.
anniversary-rule-2 = 2. AFK-Zeit zählt nicht für die Ziehungsberechtigung. Das Umgehen der AFK-Erkennung ist auf jede Weise verboten.
anniversary-rule-3 = 3. Der Server übernimmt 80 % der gesamten Versandkosten für Sachpreise, höchstens jedoch 50 CNY; den nach Abzug dieses Betrags verbleibenden Rest zahlt der Gewinner vollständig.
anniversary-rule-4 = 4. Für Ziele auf dem chinesischen Festland ist eine gültige SF-ID für datenschutzgeschützten Versand erforderlich. Für Ziele außerhalb des chinesischen Festlands sind echter Empfängername, vollständige Adresse und gültige Telefonnummer erforderlich. Ohne die verlangten Angaben oder Zahlung des Versandanteils verfällt der Preis.
anniversary-rule-5 = 5. Mehrfachteilnahme mit zusätzlichen Konten ist verboten und führt zum Ausschluss.
anniversary-rule-6 = 6. Mit der Teilnahme stimmst du allen obigen Regeln zu.
anniversary-rule-7 = 7. Auf der Website angenommene Bauwerke zählen 0,2 Punkte für Übernahmen, 0,5 für Bearbeitungen und 1 für Originale; vor dem Event angenommene Bauwerke werden mit 0,8 multipliziert, gemeinsame Bauwerke mit dem 1,5-Fachen deines Beitragsanteils. Ab 2 Punkten gibt es eine zusätzliche Ziehung, manuell von einem Administrator vergeben.
anniversary-second-round-hint = Die zweite Ziehung wird nach insgesamt 8 Online-Stunden ohne AFK freigeschaltet.
anniversary-status-completed = Du hast beide Ziehungsrunden abgeschlossen.
anniversary-status-gift-pack-final = Du hast das Geschenkpaket gewonnen. Dein endgültiger Preis steht fest und alle übrigen Ziehungen sind beendet.
anniversary-status-progress = Abgeschlossen: { $arg0 }/{ $arg1 } Runden; gültige Online-Zeit: { $arg2 }
anniversary-status-remaining = Für Runde { $arg0 } fehlen noch { $arg1 } Online-Zeit ohne AFK.
anniversary-status-results = Verlosungsergebnisse: { $arg0 }
anniversary-status-title = Fortschritt der Jubiläumsverlosung

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } ist bereits gesperrt
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Sperrmitteilung]
ban-command-description = Einen Spieler nach fester Schweregradstufe sperren
ban-database-error = Sperrdatenbank-Aktion fehlgeschlagen. Prüfe das Serverprotokoll.
ban-dialog-back = Zurück
ban-dialog-cancel = Abbrechen
ban-dialog-confirm-action = Spieler sperren
ban-dialog-confirm-severity = Schweregrad
ban-dialog-confirm-title = Sperre bestätigen
ban-dialog-never-joined-confirm-action = Trotzdem sperren
ban-dialog-never-joined-title = Spieler war noch nie auf dem Server
ban-dialog-never-joined-warning = { $arg0 } war noch nie auf diesem Server. Prüfe den Spielernamen, damit du nicht den falschen Spieler sperrst.
ban-dialog-next = Weiter
ban-dialog-player-input = Spielernamen manuell eingeben
ban-dialog-player-manual = Manuelle Eingabe
ban-dialog-player-online = Spieler, die gerade online sind
ban-dialog-select-title = Spieler auswählen
ban-dialog-severity-title = Sperrstufe auswählen
ban-history-empty = Kein Sperrverlauf für { $arg0 } vorhanden.
ban-history-title = Sperrverlauf: { $arg0 }
ban-invalid-player = Ungültiger Spielername
ban-invalid-severity = Ungültige Sperrstufe
ban-kick-appeal = Wende dich bei einem Irrtum in der QQ-Gruppe 1138627074 an die Administration
ban-kick-server = MIK
ban-kick-title = Du bist auf diesem Server gesperrt
ban-not-banned = { $arg0 } ist derzeit nicht gesperrt.
ban-pardon-reason-required = Ein Entsperrungsgrund ist erforderlich
ban-permanent = Dauerhaft
ban-reason-required = Ein Sperrgrund ist erforderlich
ban-self = Du kannst dich nicht selbst sperren
ban-severity-excusable = Entschuldbar (30 Tage)
ban-severity-extreme = Extrem schwerer Verstoß (1500 Tage)
ban-severity-minor = Leichter Verstoß (150 Tage)
ban-severity-severe = Schwerer Verstoß (360 Tage)
ban-severity-warning = Verwarnung (3 Tage)
ban-status-active = Aktiv
ban-status-expired = Abgelaufen
ban-status-revoked = Aufgehoben
ban-success = { $arg0 } wurde bis { $arg1 } gesperrt
ban-sync-success = Sperren synchronisiert: aktiv { $arg0 } · historische Einträge { $arg1 }
ban-system-source = System
ban-unban-success = Sperre für { $arg0 } aufgehoben
ban-unknown-reason = Kein Grund angegeben
ban-update-success = Sperrfeld für { $arg0 } aktualisiert: { $arg1 }
banlist-command-description = Spielersperren anzeigen
banlist-copy = Klicken, um den Spielernamen zu kopieren
banlist-empty = Es gibt keine gesperrten Spieler
banlist-expires = Ablauf
banlist-invalid-page = Ungültige Seite. Insgesamt: { $arg0 ->
        [one] { $arg0 } Seite
       *[other] { $arg0 } Seiten
    }.
banlist-next = Weiter
banlist-page = Seite { $arg0 }/{ $arg1 } · { $arg2 ->
        [one] { $arg2 } Eintrag
       *[other] { $arg2 } Einträge
    }
banlist-player = Spieler
banlist-previous = Zurück
banlist-reason = Grund
banlist-source = { role-moderator }
banlist-title = Sperrliste
maintenance-command-description = Wartungsmodus umschalten oder Status anzeigen
maintenance-set = Wartungsmodus wurde { $arg0 }
maintenance-state-disabled = deaktiviert
maintenance-state-enabled = aktiviert
maintenance-status = Wartungsmodus: { $arg0 }
maintenance-unchanged = Der Wartungsmodus ist bereits { $arg0 }
restriction-entity-too-large-mm = <red>Die Größe der Entität überschreitet das Sicherheitslimit des Servers</red>
restriction-foreign-uuid-mm = <red>Du kannst UUIDs nur für Mobs verwenden, die du gezähmt hast</red>
restriction-no-kill-e-mm = <red>Der Befehl <white>/kill @e</white> ist nicht erlaubt</red>
restriction-no-newline-mm = <red>Nachrichten dürfen keine Zeilenumbrüche enthalten</red>
restriction-no-run-command-mm = <red>Nachrichten dürfen Spieler nicht zum Ausführen von Befehlen verleiten</red>
restriction-no-selector-mm = <red>Du darfst die Zielselektoren <white>@e @a @r @p @n</white> nicht verwenden</red>
restriction-other-player-name-mm = <red>Du darfst in Befehlen keine Namen anderer Spieler erwähnen</red>
tpsbar-command-description = TPS-Leiste ein- oder ausblenden
tpsbar-hidden = TPS-Leiste ausgeblendet
tpsbar-shown = TPS-Leiste angezeigt
tpsbar-tip-status-mm = Schalte mit <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> die Anzeige für <yellow>TPS, MSPT und Ping</yellow> ein oder aus.
unban-command-description = Spieler entsperren und Sperrverlauf behalten
whitelist-already-whitelisted = { $arg0 } ist bereits auf der Whitelist
whitelist-invalid-name = Ungültiges Spielernamenformat
whitelist-kick-apply-rich = Bewirb dich unter { $site } für die Whitelist
whitelist-kick-title = Du stehst nicht auf der Whitelist
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = { $arg0 } für { $arg1 } vorübergehend zur Whitelist hinzugefügt
whitelist-temp-added-rich = { $player } für { $duration } vorübergehend zur Whitelist hinzugefügt
whitelist-temp-command-description = Temporären Whitelist-Eintrag für eine Stunde hinzufügen
whitelist-temp-duration = 1 Stunde
whitelist-temp-usage-desc = Temporären Whitelist-Eintrag für eine Stunde hinzufügen

## Shell · Menus, server information and scripting

main-afk-current-afk = Derzeit AFK
main-afk-current-online = Derzeit online
main-afk-status = AFK-Status
main-announcements = Ankündigungen
main-home = Homepunkte
main-language = Sprache
main-language-auto = Automatisch
main-links = Links
main-map = Webkarte
main-menu-title = Hauptmenü
main-music = Musik
main-name-tag = Namensschilder
main-profile = Profil
main-pvp = PVP
main-settings = Einstellungen
main-website = Webseite
main-wiki = Wiki
skript-command-description = Skript-Unterstützung des Servers anzeigen
skript-info-ai-option = Nicht selbst schreiben? Diesen Prompt an eine KI geben:
skript-info-copy = [KI-Prompt kopieren]
skript-info-copy-hover = Prompt mit Versionen, Dokumentationslinks und Sicherheitsregeln kopieren
skript-info-documentation = Syntax- und API-Dokumentation
skript-info-review = Skripte werden erst nach Prüfung und Test durch die Administration geladen
skript-info-supported = Verfügbar: { $arg0 }
skript-info-title = Server-Skripte
skript-info-workflow = Eine vollständige .sk-Datei anhand der Dokumentation schreiben und prüfen, dann an die Administration senden
stat-client-version = Clientversion
stat-first-joined = Erstmals beigetreten
stat-location = Position
stat-ping = Latenz
stat-play-time = Spielzeit
stat-role = Rolle
stat-world = Welt
weblogin-command-description = Mik-Web-Anmeldecode bestätigen
weblogin-confirmed = Mik-Web-Anmeldung bestätigt. Kehre zum Fortfahren zu deinem Browser zurück.
weblogin-invalid-code = Ungültiger Web-Anmeldecode
