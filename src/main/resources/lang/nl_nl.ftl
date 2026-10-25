### MIK · nl-nl
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = Perceel
    .gender = neuter

-vehicle-model = Model
    .gender = neuter

## Shared · General

back = Terug
back-to-main = Terug naar hoofdmenu
click-open = Klik om te openen
close = Sluiten
current-off = Huidig: uit
current-on = Huidig: aan
player-only = Dit commando kan alleen door spelers worden gebruikt
unknown = Onbekend
usage = Gebruik

## Shared · Language, roles and time

language-command-description = Open taalinstellingen
language-menu-title = Taalinstellingen
language-selected = Geselecteerd
language-set = Taal ingesteld op { $arg0 }
role-custodian = Beheerder
role-member = Lid
role-moderator = Moderator
role-new-player = Nieuwe speler
time-hours-minutes = { $arg0 }h { $arg1 }m
time-minutes = { $arg0 }m

## Shared · Menu presentation

bedrock-menu-back = Terug
bedrock-menu-hotkey = Snelactie (F)
bedrock-menu-primary = Primaire actie
bedrock-menu-scroll-down = Omlaag
bedrock-menu-scroll-up = Omhoog
bedrock-menu-secondary = Secundaire actie
interface-fov-apply = FOV toepassen
interface-fov-custom = Exacte waarde invoeren
interface-fov-description = Gebruik de FOV van je client; alleen de menupositie verandert. Standaard: 70°.
interface-fov-input = Client-FOV: geheel getal van 30 tot 110
interface-fov-invalid = Voer een geheel getal van 30 tot 110 in.
interface-fov-title = Beeldveld aanpassen (FOV)
interface-scale-current = Huidig: { $arg0 }
interface-scale-description = Tekstgrootte vermenigvuldigt de menugrootte; stel beide groepen in met het muiswiel.
interface-scale-layout-title = Menugrootte
interface-scale-menu-title = Interface en tekst
interface-scale-native-hint = Deze instellingen gelden voor ruimtelijke Java-menu’s. Tekst in native formulieren volgt je clientinstellingen.
interface-scale-preview = Tekstvoorbeeld Aa 123
interface-scale-reset = Beide terugzetten naar 100%
interface-scale-text-title = Tekstgrootte
menu-rhythm-game-display = Speelt ritmespel
menu-usage-display-mm = <aqua><bold>In menu</bold></aqua>
url-dialog-confirm = Openen
url-dialog-hint = De client vraagt nogmaals om bevestiging om de link te openen.
url-dialog-question-rich = { $label } openen?
url-dialog-title = Openen: { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Aanbevolen mods niet gedetecteerd: { $mods }. Klik op een naam om te downloaden; controleer bij geïnstalleerde mods of ze actief en compatibel zijn.
client-version-mismatch-mm = <gray>Serverversie <white>{ $serverVersion }</white>, huidige clientversie <white>{ $clientVersion }</white>.</gray>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> speler in de buurt is <gold>onzichtbaar</gold>
       *[other] <gold>{ $count }</gold> spelers in de buurt zijn <gold>onzichtbaar</gold>
    }</yellow> <gray>·</gray> <gray>dichtstbijzijnde: ongeveer { $distance ->
        [one] { $distance } blok
       *[other] { $distance } blokken
    } afstand</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = { $player } is via de achterdeur binnen geslopen
    .item-2 = { $player } heeft de geheime route gevonden
    .item-3 = { $player } is uit de struiken verschenen
presence-quit-templates =
    .item-1 = { $player } is stilletjes verdwenen
    .item-2 = { $player } is even gaan opslaan
    .item-3 = { $player } is door de realiteit teruggeroepen
welcome-first-join-mm = <gold><bold>Welkom bij MIK!</bold></gold><newline><gray>Begin op de <click:open_url:'https://mcmik.top'><yellow>website</yellow></click> en de <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>. Gebruik ook <yellow>/menu</yellow> om het hoofdmenu te openen.</gray>
welcome-return-mm = <gold><bold>Welkom terug!</bold></gold><newline><gray>Je laatste bezoek was <yellow>{ $arg0 ->
        [one] { $arg0 } dag
       *[other] { $arg0 } dagen
    } geleden</yellow>. Gebruik <yellow>/menu</yellow> om veelgebruikte functies snel te vinden.</gray>

## Player · AFK

afk-already-afk-mm = <gray>Je bent al <gold>AFK</gold></gray>
afk-command-description = Zet je AFK-status aan
afk-default-statuses =
    .item-1 = is even weg van het toetsenbord
    .item-2 = is in energiebesparingsmodus gegaan
    .item-3 = is naar de achtergrond gegaan
    .item-4 = staart voor zich uit
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>is nu AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>liet een bericht achter:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>wijzigde de status in:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>is terug bij het toetsenbord</green>
    .item-2 = <gray>*</gray> <player> <green>is terug</green>
afk-not-afk-mm = <gray>Je bent momenteel niet <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>Een AFK-status mag maximaal { $arg0 ->
        [one] <white>{ $arg0 }</white> teken
       *[other] <white>{ $arg0 }</white> tekens
    } bevatten</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · overeenkomsten: { $arg2 ->
        [one] { $arg2 } vermelding
       *[other] { $arg2 } vermeldingen
    } · geschiedenis: { $arg3 }
player-association-header = [Login-koppeling] { $arg0 } deelt adresgeschiedenis met { $arg1 ->
        [one] { $arg1 } speler
       *[other] { $arg1 } spelers
    }.
player-association-hover = Historische netwerkgegevens komen overeen. Dit is alleen een hulpmiddel bij controle; adressen worden niet getoond.
player-association-more-addresses = Nog { $arg0 ->
        [one] { $arg0 } vermelding
       *[other] { $arg0 } vermeldingen
    }
player-association-more-players = Nog { $arg0 ->
        [one] { $arg0 } speler
       *[other] { $arg0 } spelers
    }
player-association-time-days = { $arg0 ->
        [one] { $arg0 } dag
       *[other] { $arg0 } dagen
    } geleden
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } vermelding
       *[other] { $arg1 } vermeldingen
    })
player-association-time-separator = ,
player-association-time-today = vandaag

## Player · Profile and tab list

nametag-clear-all-error = Fout bij wissen van naamlabels. Probeer het later opnieuw
nametag-clear-all-success = ✔ Alle naamlabels gewist
nametag-clear-error = Fout bij wissen van { $arg0 }
nametag-clear-success = ✔ Je { $arg0 } is gewist
nametag-click-fill-command = Klik om het commando in te vullen
nametag-command-description = Pas naamlabels aan
nametag-copy-hover = Kopieer de MiniMessage-bron naar het klembord
nametag-copy-label = [kopie]
nametag-effect-label = Resultaat
nametag-empty-content-rich = Inhoud mag niet leeg zijn. Gebruik { $command } om die te wissen
nametag-format-docs = Opmaakdocumentatie
nametag-leading-label = Naamvoorvoegsel
nametag-no-permission = Je hebt geen toestemming om dit commando te gebruiken
nametag-online-editor = Online editor
nametag-preview-label = Voorbeeld
nametag-sample-message = voorbeeldbericht
nametag-set-error = Fout bij instellen van { $arg0 }. Probeer het later opnieuw
nametag-set-success = ✔ { $arg0 } ingesteld
nametag-title = Naamlabels
nametag-too-long = { $arg0 } is te lang. Maximale lengte: { $arg1 ->
        [one] { $arg1 } teken
       *[other] { $arg1 } tekens
    }
nametag-trailing-label = Naamachtervoegsel
nametag-unset = (niet ingesteld)
nametag-unsupported-placeholder-rich = Alleen de PlaceholderAPI-variabelen { $supported } worden ondersteund, niet { $unsupported }
nametag-user-data-load-failed = Gebruikersgegevens konden niet worden geladen. Probeer het later opnieuw
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Online spelers: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Ga terug naar je vorige teleportatie- of overlijdenslocatie
back-empty-mm = <gray>Geen terugkeerlocatie beschikbaar</gray>
back-pending-mm = <gray>Teleporteren, even geduld</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>{ $steps ->
        [one] { $steps } stap
       *[other] { $steps } stappen
    } teruggegaan</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>Teleportatie mislukt. Probeer het opnieuw</red>
back-undo-empty-mm = <gray>Geen <white>/back</white> om ongedaan te maken</gray>
back-undo-success-mm = <green>Laatste <white>/back</white> ongedaan gemaakt</green>
back-undo-world-missing-mm = <red>Doelwereld is niet beschikbaar. Kan niet ongedaan maken</red>
back-world-missing-mm = <red>Doelwereld is niet beschikbaar. Kan niet terugkeren</red>
boundary-near-mm = <yellow>Je nadert de wereldgrens</yellow>
boundary-reached-mm = <red>Je hebt de wereldgrens bereikt</red>
fly-command-description = Persoonlijk vliegen schakelen
fly-disabled = Vliegen uitgeschakeld
fly-enabled = Vliegen ingeschakeld
fly-native-mode = Je /fly-toegang is uitgeschakeld. Deze spelmodus staat vliegen nog steeds toe.
fly-speed-click = Vliegsnelheid instellen op { $arg0 }x
fly-speed-current = Vliegsnelheid: { $arg0 }x
fly-speed-presets = Voorinstellingen
fly-speed-reset = Vliegsnelheid hersteld naar 1x
fly-speed-reset-action = Herstellen
fly-speed-reset-click = Vliegsnelheid herstellen naar 1x
fly-speed-set = Vliegsnelheid ingesteld op { $arg0 }x
fly-state-disabled = Uitgeschakeld
fly-state-enabled = Ingeschakeld
fly-status = Vliegen: { $arg0 } | Snelheid: { $arg1 }x
flyspeed-command-description = Persoonlijke vliegsnelheid bekijken of instellen
home-action-create = Thuislocatie maken
home-action-delete = Thuislocatie verwijderen
home-action-icon = Icoon uit hand instellen
home-action-teleport = Teleporteren
home-action-update = Locatie bijwerken
home-already-exists = Er bestaat al een thuislocatie met deze naam
home-back = Terug
home-confirm-delete = Verwijderen bevestigen
home-confirm-delete-detail-rich = Verwijder thuislocatie { $home }
home-confirm-delete-warning = Dit kan niet ongedaan worden gemaakt
home-confirm-update = Wijziging bevestigen
home-confirm-update-detail-rich = Thuislocatie { $home } bijwerken naar deze locatie
home-delete-command-description = Verwijder een thuislocatie
home-delete-usage-desc = Verwijder een thuislocatie
home-deleted-rich = Thuislocatie { $home } is verwijderd
home-distance = Afstand: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    }
home-distance-other-world = andere wereld
home-empty-description = Ga staan waar je een thuislocatie wilt opslaan
home-empty-title = Nog geen thuislocaties
home-icon = Pictogram: { $arg0 }{ $arg1 }
home-icon-default-note = · standaard
home-icon-hand-invalid = Het voorwerp in je hoofdhand kan niet als pictogram voor een thuislocatie worden gebruikt
home-icon-updated-rich = Pictogram van thuislocatie { $home } bijgewerkt
home-invalid-icon = Dit materiaal kan niet als pictogram voor een thuislocatie worden gebruikt
home-invalid-name = Namen van thuislocaties mogen geen < > : bevatten
home-list-rich = Thuislocaties: { $homes }
home-location = Locatie: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = De locatiegegevens van deze thuislocatie zijn ongeldig
home-max-reached = Je kunt maximaal { $arg0 } thuislocaties instellen
home-menu-title = Thuislocaties
home-no-homes = Je hebt nog geen thuislocaties ingesteld
home-not-found-rich = Thuislocatie { $home } bestaat niet
home-set-command-description = Stel een thuislocatie in op je huidige locatie
home-set-rich = Thuislocatie { $home } is ingesteld
home-set-usage-desc = Stel een thuislocatie in op je huidige locatie
home-summary-count = Ingesteld: { $arg0 } / { $arg1 }
home-teleport-command-description = Teleporteer naar een thuislocatie
home-teleport-failed = Teleportatie mislukt. Probeer het opnieuw
home-teleported-rich = Geteleporteerd naar thuislocatie { $home }
home-updated-rich = Locatie van thuislocatie { $home } is bijgewerkt
home-usage-book = Commandohulp
home-usage-delhome = /delhome <name>  Verwijder een thuislocatie
home-usage-home = /home tp <name>  Teleporteer direct
home-usage-icon = /home icon <material> <name>  Stel een pictogram in
home-usage-name-rule = Namen van thuislocaties mogen geen < > : bevatten
home-usage-sethome = /sethome <name>  Sla je huidige locatie op
home-world = Wereld: { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Maak de laatste /back ongedaan
teleport-accept-command-description = Het openstaande teleportatieverzoek accepteren
teleport-allow = Teleportaties door andere spelers
teleport-block-afk = Blokkeren tijdens AFK
teleport-denied = { $player } weigert momenteel teleportaties door gewone spelers
teleport-deny-command-description = Het openstaande teleportatieverzoek weigeren
teleport-menu-title = Teleportatieprivacy
teleport-moved-here = Je bent hierheen geteleporteerd door { $arg0 }
teleport-policy-always-allow = Altijd toestaan
teleport-policy-always-deny = Altijd weigeren
teleport-policy-require-consent = Mijn toestemming vereisen
teleport-request-accept = Accepteren
teleport-request-approved = { $player } heeft je teleportatieverzoek geaccepteerd
teleport-request-deny-action = Weigeren
teleport-request-expired = Het teleportatieverzoek is verlopen
teleport-request-failed = De teleportatie kon niet worden voltooid. Probeer het opnieuw
teleport-request-none = Je hebt geen openstaand teleportatieverzoek
teleport-request-received = { $sender } wil je teleporteren: { $command }
teleport-request-rejected = Teleportatieverzoek geweigerd
teleport-request-sent = Teleportatieverzoek naar { $player } verzonden
teleport-summary-afk = Altijd toegestaan, geweigerd tijdens AFK
teleport-summary-allow = Teleportaties door gewone spelers altijd toegestaan
teleport-summary-consent = Teleportaties door gewone spelers vereisen mijn toestemming
teleport-summary-deny = Teleportaties door gewone spelers altijd geweigerd
tpany-command-description = Teleporteer naar de locatie van een speler, online of offline
tpany-done-offline-rich = Geteleporteerd naar de laatst bekende locatie van { $player }: { $world } { $location }
tpany-done-online-rich = Geteleporteerd naar de huidige locatie van { $player }
tpany-not-found-rich = Locatie van { $player } niet gevonden
tpany-usage = Gebruik
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teleporteer naar de locatie van een speler, inclusief offlinecache

## Player · PVP

pvp-admin-menu-title = PvP-beheer - pagina { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = Bereden wezens kunnen worden aangevallen
pvp-allow-mounted-damage-desc = Als dit is ingeschakeld, kunnen andere spelers een wezen aanvallen waarop je rijdt als jullie allebei PvP hebben ingeschakeld
pvp-auto-enable-desc = Val een speler met PvP ingeschakeld twee keer achter elkaar aan
pvp-auto-enable-label = Automatisch inschakelen
pvp-auto-enable-warning-actionbar-mm = <yellow>Val nog een keer aan om PvP automatisch in te schakelen</yellow>
pvp-auto-enabled-actionbar-mm = <green>PvP automatisch ingeschakeld</green>
pvp-back-admin = Terug naar PvP-beheer
pvp-combat-locked-mm = <red>Je kunt PvP niet uitschakelen tijdens een gevecht</red> <dark_gray>|</dark_gray> <gray>Resterend: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> seconde
       *[other] <yellow>{ $arg0 }</yellow> seconden
    }</gray>
pvp-combat-tag-label = Gevechtscooldown
pvp-combat-tag-value = { $arg0 } sec.
pvp-command-description = Schakel je PvP-status in of uit
pvp-enable = PvP inschakelen
pvp-enable-desc = Als dit is ingeschakeld, kun je andere spelers met PvP ingeschakeld aanvallen en zij jou ook
pvp-enable-on-death = PvP inschakelen bij overlijden
pvp-enable-on-death-desc = Als dit is ingeschakeld, wordt PvP automatisch ingeschakeld wanneer je sterft
pvp-enable-on-death-label = Inschakelen bij overlijden
pvp-enabled-on-death-mm = <yellow>PvP is ingeschakeld omdat je bent gestorven</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Open PvP-beheer voor online spelers</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Open PvP-instellingen</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Beheer een online speler</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Bekijk je PvP-status</gray>
pvp-help-title-mm = <gold><bold>PvP-commando's</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Schakel je PvP-status in of uit</gray>
pvp-menu-title = PvP-instellingen
pvp-mob-protected-actionbar-mm = <yellow>Dit wezen is beschermd tegen PvP-schade</yellow>
pvp-mob-protection-label = Bescherming van wezens
pvp-mounted-damage-label = Schade aan bereden wezens
pvp-next-page = Volgende pagina
pvp-no-permission-mm = <red>Je hebt geen toestemming om de PvP-status van een andere speler te beheren</red>
pvp-player-not-found-mm = <red>Online speler niet gevonden</red>
pvp-prev-page = Vorige pagina
pvp-protect-mobs = Bescherm je wezens
pvp-protect-mobs-desc = Bescherm wezens die je hebt getemd of waarop je rijdt
pvp-set-by-staff-mm = <aqua>Een teamlid heeft je PvP-status ingesteld op</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = PvP-status van { $arg0 } ingesteld op { $arg1 }
pvp-set-other-rich = PvP-status van { $player } ingesteld op { $state }
pvp-state-label = PvP
pvp-state-off = Uit
pvp-state-on = Aan
pvp-status-player = Speler: { $arg0 }
pvp-status-player-rich = Speler: { $player }
pvp-status-title-mm = <gold><bold>PvP-status</bold></gold>
pvp-summary-disabled = PvP uitgeschakeld
pvp-summary-enabled = PvP ingeschakeld
pvp-target-disabled-actionbar-mm = <gray>Deze speler heeft PvP niet ingeschakeld</gray>
pvp-target-menu-title = PvP-instellingen - { $arg0 }
pvp-toggled-off-mm = <gray>PvP uitgeschakeld</gray> <dark_gray>|</dark_gray> <gray>Andere spelers kunnen je niet aanvallen</gray>
pvp-toggled-on-mm = <green>PvP ingeschakeld</green> <dark_gray>|</dark_gray> <gray>Je kunt nu vechten met andere spelers met PvP ingeschakeld</gray>

## Player · Utilities

hat-command-description = Draag het voorwerp in je hoofdhand
hat-empty-hand-mm = <red>Geen voorwerp in je hoofdhand</red> <gray>Houd een blok of voorwerp vast en gebruik /hat</gray>
hat-success-mm = <green>Je draagt nu het voorwerp uit je hoofdhand</green> <dark_gray>|</dark_gray> <gray>Je hoofdhand en helmvak zijn verwisseld</gray>
removeitems-command-description = Verwijder gedropte voorwerpen
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Verwijderd: { $countValue ->
        [one] { $count } gedropt voorwerp
       *[other] { $count } gedropte voorwerpen
    } · wereld { $world } { $location } { $radius }
removeitems-no-world = Wereld kon niet worden bepaald
removeitems-radius = straal: { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    }
self-kick-command-description = Jezelf van de server kicken
self-kick-reason-too-long = De reden mag niet langer zijn dan { $arg0 ->
        [one] { $arg0 } teken
       *[other] { $arg0 } tekens
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Je hebt de server op eigen verzoek verlaten. Reden: { $reason }
       *[third] { $player } heeft zichzelf om deze reden van de server gekickt: { $reason }
    }
    .item-1 = { $player } heeft zichzelf eruit gegooid en de reden was zowaar: { $reason }
    .item-2 = { $player } heeft zichzelf echt vanwege { $reason } van de server gekickt!
    .item-3 = { $player } heeft de server vrijwillig verlaten met deze uitleg: { $reason }
    .item-4 = { $player } koos voor een zelf-kick: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Je hebt de server op eigen verzoek verlaten.
       *[third] { $player } heeft zichzelf van de server gekickt.
    }
    .item-1 = { $player } heeft het voor elkaar gekregen zichzelf eruit te gooien!
    .item-2 = { $player } heeft de server zonder een woord verlaten.
    .item-3 = { $player } voerde een vlekkeloze zelf-kick uit.
    .item-4 = { $player } drukte eigenhandig op de knop om de server te verlaten.
trash-command-description = Open de prullenbak
trash-title = Prullenbak
trash-title-hint = Richt op een voorwerp en druk op F om die stapel te verwijderen

## Community · Chat and mentions

chat-copy-hover = Linksklik om bericht te kopiëren
chat-delay-3s = 3 seconden
chat-delay-5s = 5 seconden
chat-delay-7s = 7 seconden
chat-delay-cancel-command-description = Annuleer wachtende vertraagde berichten
chat-delay-cancelled = Wachtend bericht geannuleerd
chat-delay-current = Huidige chatvertraging: { $arg0 }
chat-delay-nothing-to-cancel = Geen wachtend bericht om te annuleren
chat-delay-off = Uit
chat-delay-preview-marker = [Wachtend]
chat-delay-queued = Bericht wordt over { $arg0 } verzonden; gebruik /cancel of /c om te annuleren
chat-item-empty-hover = Geen voorwerp in je hoofdhand
chat-mention-all-hover = Breng elke zichtbare speler in dit kanaal op de hoogte
chat-msg-command-description = Stuur een privébericht en schakel naar privéchat
chat-player-hover = Linksklik om een privébericht te starten
chat-private-enter = Overgeschakeld naar privéchat met { $arg0 }
chat-private-label = MSG
chat-private-no-reply-target = Geen recente privéchatpartner is online
chat-private-self = Je kunt geen privéchat met jezelf starten
chat-private-target-not-found = Online speler niet gevonden: { $arg0 }
chat-private-target-offline = Privéchatpartner is offline; teruggeschakeld naar openbare chat
chat-public-command-description = Schakel naar de openbare chat
chat-public-enter = Overgeschakeld naar openbare chat
chat-reply-command-description = Beantwoord je laatste privéchatpartner
chat-settings-menu-title = Chatinstellingen
chat-social-account-label = Platformaccount
chat-social-bound-player-label = Gekoppelde speler
chat-social-name-label = Sociale naam
chat-social-unbound = Niet gekoppeld aan een Minecraft-speler
chat-staff-command-description = Schakel naar de teamchat
chat-staff-permission-missing = Geen toestemming voor teamchat; teruggeschakeld naar openbare chat
mention-action-bar = Werkbalktitel
mention-action-bar-text = { $sender } heeft je vermeld
mention-alerts = Vermeldingsmeldingen
mention-menu-title = Vermeldingsmeldingen
mention-mute-afk = Dempen tijdens AFK
mention-sound = Meldingsgeluid
mention-summary-action-bar = werkbalktitel
mention-summary-disabled = Meldingen uitgeschakeld
mention-summary-enabled = Meldingen ingeschakeld
mention-summary-enabled-afk = Meldingen ingeschakeld, gedempt tijdens AFK
mention-summary-modes-afk = { $arg0 }, gedempt tijdens AFK
mention-summary-sound = geluid
staffchat-enter = Teamchat betreden

## Community · Announcements and tips

announcement-empty = Geen aankondigingen
announcement-first-page = Nieuwste berichten bereikt
announcement-last-page = Oudste berichten bereikt
announcement-more-count = { $arg0 } meer niet getoond
announcement-new-count = { $arg0 } nieuw
announcement-newer = Nieuwere berichten
announcement-older = Oudere berichten
announcement-reload = Aankondigingen herladen
announcement-reload-command-description = Aankondigingsbestand herladen
announcement-reload-failed = Kon aankondigingen niet herladen; de vorige blijven beschikbaar
announcement-reloaded = Aankondigingen herladen
announcement-scroll-hint = Scroll om horizontaal te bladeren
announcement-unread-count = { $arg0 } ongelezen
announcement-updated = Aankondigingen bijgewerkt
announcement-view-all = Alle aankondigingen bekijken
announcement-view-command-description = Serveraankondigingen bekijken
tip-hover = Servertip
tip-label = TIP
tip-reload-command-description = tips.txt opnieuw laden
tip-reload-count = { $arg0 ->
        [one] { $arg0 } vermelding
       *[other] { $arg0 } vermeldingen
    }
tip-reload-done = Tips opnieuw geladen

## Community · Identity and social platforms

identity-admin-binding-not-found = Geen eenduidige speler- en platformkoppeling gevonden.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Voor { $arg1 } verwijderd: { $arg2 ->
        [one] { $arg2 } identiteitskoppeling
       *[other] { $arg2 } identiteitskoppelingen
    } ({ $arg0 }).
identity-admin-command-description = Externe identiteitskoppelingen bekijken of intrekken
identity-admin-help = /bindadmin lookup <speler-of-UUID>
    /bindadmin unlink <speler-of-UUID> <platform> confirm
identity-admin-lookup-empty = Voor die speler zijn geen koppelingen gevonden.
identity-admin-lookup-title = Koppelingen van { $arg0 }:
identity-binding-details = · { $arg0 } · bereik { $arg1 } · { $arg2 }
identity-binding-not-owned = Je hebt geen koppelingen voor dat platform.
identity-binding-unlinked = Verwijderd: { $arg1 ->
        [one] { $arg1 } identiteitskoppeling
       *[other] { $arg1 } identiteitskoppelingen
    } ({ $arg0 }).
identity-bindings-empty = Je hebt geen externe platformaccounts gekoppeld.
identity-bindings-title = Gekoppelde externe identiteiten:
identity-bindings-unlink-hint = Gebruik /bind unlink <platform> om alle koppelingen van een platform te verwijderen.
identity-code-cancelled = De ongebruikte koppelcode voor { $arg0 } is geannuleerd.
identity-code-copy = Klik om de volledige koppelopdracht te kopiëren
identity-code-expiry = De code is 5 minuten geldig en kan één keer worden gebruikt. Een nieuwe code maakt hem ongeldig.
identity-code-issued = Koppelcode voor { $arg0 } aangemaakt:
identity-code-not-pending = Er is geen ongebruikte koppelcode voor { $arg0 }.
identity-code-redeem = Vermeld de bot in de gekozen { $arg0 }-groep en stuur: { $arg1 }
identity-command-description = Minecraft aan een externe platformidentiteit koppelen
identity-help = Opdrachten voor identiteitskoppeling:
    /bind qq — snel een QQ-koppelopdracht maken
    /bind <platform> — een eenmalige code maken
    /bind list — bestaande koppelingen tonen
    /bind cancel <platform> — een ongebruikte code annuleren
    /bind unlink <platform> — alle koppelingen van een platform verwijderen
identity-notify-linked = Je { $arg0}-identiteit is bevestigd. Bekijk haar met /bind list of verwijder haar met /bind unlink { $arg0 }.
identity-notify-unlinked = Je { $arg0}-identiteitskoppeling is verwijderd.
identity-platform-none = geen
identity-platforms = Ondersteunde platforms: { $arg0 }
identity-scope-notice = Dit platform scheidt identiteiten per groep. Koppel in de groep waar je de bot gebruikt; elke groep moet apart worden gekoppeld.
identity-storage-error = De databasebewerking voor identiteitskoppeling is mislukt. Neem contact op met een beheerder.
identity-unavailable = Identiteiten koppelen is momenteel niet beschikbaar. Neem contact op met een beheerder.
identity-unsupported-platform = Niet-ondersteund platform: { $arg0 }
social-admin-conversations-empty = Geen gesprekken waargenomen voor { $arg0 }.
social-admin-conversations-title = Waargenomen gesprekken voor { $arg0 }:
social-admin-help = /social platforms · /social status [platform] · /social reload [platform] · /social conversations <platform>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Sociaal platform { $arg0 } is opnieuw geladen.
social-admin-reloaded-all = Alle sociale platforms zijn opnieuw geladen.
social-admin-status = { $arg0 } · { $arg1 } · generatie { $arg2 } · gesprekken { $arg3 }
social-admin-title = Sociale platforms
social-admin-unknown = Onbekend sociaal platform: { $arg0 }
social-binding-already-linked = Deze identiteit is al gekoppeld aan Minecraft-speler { $arg0 }.
social-binding-code-invalid = De verificatiecode is ongeldig of verlopen. Genereer in Minecraft een nieuwe met /bind { $arg0 }.
social-binding-external-in-use = Deze identiteit is al gekoppeld aan een andere Minecraft-speler.
social-binding-help = Voer in Minecraft /bind { $arg0 } uit.
    Kopieer de volledige gegenereerde opdracht.
    Stuur de opdracht hier.
    Gebruik /profiel om de koppeling te controleren.
social-binding-identity-missing = Dit bericht bevat geen betrouwbare ledenidentiteit; koppelen is daarom niet mogelijk.
social-binding-linked = Gekoppeld aan Minecraft-speler { $arg0 }.
social-binding-not-bound = Deze platformidentiteit is niet gekoppeld.
social-binding-platform-mismatch = Deze verificatiecode is voor een ander platform uitgegeven.
social-binding-player-scope-in-use = Die Minecraft-speler heeft in dit gesprek al een andere gekoppelde identiteit.
social-binding-rate-limited = Te veel mislukte pogingen. Probeer het over ongeveer { $arg0 ->
        [one] { $arg0 } minuut
       *[other] { $arg0 } minuten
    } opnieuw.
social-binding-status-unbound = Deze identiteit is niet gekoppeld. Gebruik /koppelen met een verificatiecode.
social-binding-unavailable = Identiteitskoppeling is tijdelijk niet beschikbaar.
social-binding-unlink-prompt = Ontkoppelen verwijdert de relatie tussen deze platformidentiteit en Minecraft. Stuur /ontkoppelen bevestigen om door te gaan.
social-binding-unlinked = Ontkoppeld van Minecraft-speler { $arg0 }.
social-command-description = Sociaalplatformadapters beheren
social-duration-days = { $arg0 ->
        [one] { $arg0 } dag
       *[other] { $arg0 } dagen
    } { $arg1 } uur { $arg2 ->
        [one] { $arg2 } minuut
       *[other] { $arg2 } minuten
    }
social-duration-hours = { $arg0 } uur { $arg1 ->
        [one] { $arg1 } minuut
       *[other] { $arg1 } minuten
    }
social-duration-minutes = { $arg0 ->
        [one] { $arg0 } minuut
       *[other] { $arg0 } minuten
    }
social-help-command-description = Deze opdrachtenlijst tonen
social-link-command-description = Je Minecraft-account met een spelcode koppelen
social-online-command-description = Online spelers tonen
social-profile-afk = Online · AFK
social-profile-avatar-alt = Minecraft-gezicht
social-profile-command-description = Je profiel tonen; volwaardige leden zoeken op naam, vermelding of antwoord
social-profile-first-joined-label = Eerste deelname
social-profile-language-label = Taal
social-profile-last-seen-label = Laatst gezien
social-profile-member-required = Alleen gekoppelde leden, moderators of beheerders kunnen profielen van andere spelers bekijken.
social-profile-offline = Offline
social-profile-online = Online · actief
social-profile-play-time-label = Speeltijd
social-profile-player-label = Speler
social-profile-player-not-found = Die Minecraft-speler is niet gevonden.
social-profile-presence-label = Status
social-profile-target-ambiguous = Kies precies één doel: een spelersnaam, één vermelding of één beantwoorde gebruiker.
social-profile-target-unbound = De genoemde of beantwoorde identiteit is niet aan Minecraft gekoppeld.
social-profile-uuid-label = UUID
social-query-active-label = Actief
social-query-no-players = Er zijn momenteel geen spelers online.
social-query-online-label = Online
social-query-players-label = Spelers
social-query-unknown = Onbekende opdracht. Stuur /hulp om de beschikbare opdrachten te bekijken.
social-query-uptime-label = Bedrijfstijd
social-reply-hidden = Dit antwoord is verborgen door het inhoudsveiligheidsbeleid van de server.
social-state-disabled = uitgeschakeld
social-state-failed = mislukt
social-state-ready = gereed
social-state-starting = wordt gestart
social-state-stopped = gestopt
social-status-command-description = Spelers, TPS, MSPT en looptijd tonen
social-title-command-help = Opdrachthulp
social-title-content-hidden = Inhoud verborgen
social-title-identity-linking = Account koppelen
social-title-identity-unlinking = Account ontkoppelen
social-title-online-players = Online spelers
social-title-player-profile = Spelersprofiel
social-title-server-status = Serverstatus
social-title-unknown-command = Onbekende opdracht
social-unlink-command-description = Deze identiteitskoppeling na bevestiging verwijderen

## Community · AI and knowledge

ai-busy = De AI verwerkt te veel aanvragen; probeer het zo opnieuw
ai-cleared = AI-gespreksgeschiedenis gewist
ai-command-description = Vraag de AI-assistent in het spel
ai-disabled = De AI-assistent is uitgeschakeld of niet geconfigureerd
ai-knowledge-curation-failed = Kennisordening mislukt: { $arg0 }
ai-knowledge-curation-finished = Ordening voltooid: toegepast { $arg0 } · overgeslagen { $arg1 } · mislukt { $arg2 } · quarantaine { $arg3 } · verwerkt { $arg4 }
ai-knowledge-curation-started = Kennisordening gestart.
ai-knowledge-disabled = Kennisopslag is uitgeschakeld.
ai-knowledge-index-dirty = De index moet worden afgestemd; privé-opvraging blijft geblokkeerd tot synchronisatie.
ai-knowledge-last-failure = Laatste fout: { $arg0 }
ai-knowledge-last-sync = Laatste synchronisatie: { $arg0 }
ai-knowledge-learning-disabled = Kennisleren is uitgeschakeld.
ai-knowledge-operation-failed = Kennisbewerking mislukt: { $arg0 }
ai-knowledge-operation-started = Kennisbewerking gestart.
ai-knowledge-protected = Kennisdocument { $arg0 } is nu beschermd.
ai-knowledge-rollback = Kennisdocument { $arg0 } hersteld als revisie { $arg1 }.
ai-knowledge-status = Kennis: { $arg0 ->
        [one] { $arg0 } openbaar document
       *[other] { $arg0 } openbare documenten
    } / { $arg1 ->
        [one] { $arg1 } tekstfragment
       *[other] { $arg1 } tekstfragmenten
    } · { $arg2 ->
        [one] { $arg2 } privédocument
       *[other] { $arg2 } privédocumenten
    } / { $arg3 ->
        [one] { $arg3 } tekstfragment
       *[other] { $arg3 } tekstfragmenten
    }
ai-knowledge-sync-failed = Kennissynchronisatie mislukt: { $arg0 }
ai-knowledge-sync-started = Kennissynchronisatie gestart.
ai-knowledge-synchronized = { ai-knowledge-status } · Synchronisatie voltooid.
ai-knowledge-unprotected = Kennisdocument { $arg0 } is niet meer beschermd.
ai-learning-failure = Leerfout: { $arg0 }
ai-learning-status = Leren: kandidaten { $arg0 } · extractie { $arg1 } · wachtrij { $arg2 } · extracties verworpen { $arg3 } · kandidaten verworpen { $arg4 } · quarantaine { $arg5 }
ai-memory-empty = Geen privéherinneringen voor { $arg0 }.
ai-memory-list = Privéherinneringen voor { $arg0 }: { $arg1 }
ai-memory-not-found = Privéherinnering { $arg0 } niet gevonden.
ai-memory-removed = Voor { $arg1 } definitief verwijderd: { $arg0 ->
        [one] { $arg0 } privéherinnering
       *[other] { $arg0 } privéherinneringen
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Onbekende speler in de cache: { $arg0 }
ai-prompt-too-long = De vraag is te lang (maximaal { $arg0 ->
        [one] { $arg0 } teken
       *[other] { $arg0 } tekens
    })
ai-reload-failed = AI-configuratie kon niet worden herladen: { $arg0 }
ai-reloaded = AI-configuratie herladen ({ $arg0 })
ai-request-failed = De AI-aanvraag is mislukt; probeer het later opnieuw
ai-social-command-description = De gedeelde AI-assistent iets vragen
ai-social-question-required = Zet een vraag achter de AI-opdracht
ai-status-disabled = AI is uitgeschakeld
ai-status-enabled = AI actief · aanbieder { $arg0 } · model { $arg1 } · hulpmiddelen { $arg2 }
ai-status-none = geen
ai-thinking = Bezig met nadenken…
ai-usage = Gebruik: /ai <vraag> · /ai clear · /ai status
ai-worker-status = AI-taken: actief { $arg0 } · in wachtrij { $arg1 } · verworpen { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Gefeliciteerd!</bold> Je bent nu <gold>Lid</gold>. Je hebt nu toegang tot meer commando's en functies, waaronder bouwschema's eenvoudig afdrukken.</green>
governance-active-days-30 = Minstens 2 actieve dagen in 30 dagen (nu { $arg0 })
governance-active-days-60 = Minstens 6 actieve dagen in 60 dagen (nu { $arg0 })
governance-active-header = ─── Voorwaarden voor actief lid ───
governance-active-member = Momenteel lid
governance-active-member-age = Minstens 30 dagen lid
governance-active-playtime = Minstens 6 uur effectieve speeltijd in 60 dagen (nu { $arg0 })
governance-already-moderator = Je bent al moderator.
governance-ballot-recorded = Je anonieme stem is vastgelegd als { $arg0 }. Tot de deadline kun je die wijzigen.
governance-ban-details = Voorgesteld door { $arg0 } · Duur { $arg1 } · Reden: { $arg2 }
governance-ban-suffix = · Duur { $arg0 } · Reden: { $arg1 }
governance-choice-abstain = Onthouding
governance-choice-no = Nee
governance-choice-yes = Ja
governance-command-description = Bestuursstatus en stemmingen bekijken
governance-custodian-no-nomination = Beheerders doen niet mee aan moderatorverkiezingen.
governance-custodian-no-resign = De ontslagprocedure voor moderators geldt niet voor beheerders.
# $arg0 (Number): duration; locale decides the unit form.
governance-days = { $arg0 ->
        [one] { $arg0 } dag
       *[other] { $arg0 } dagen
    }
governance-error-ban-already-active = Deze speler heeft al een actieve ban.
governance-error-ban-proposer-ineligible = Alleen actieve leden mogen een banstemming voorstellen.
governance-error-ban-self = Je kunt geen banstemming tegen jezelf voorstellen.
governance-error-candidate-ineligible = Een kandidaat-moderator moet actief lid zijn.
governance-error-nomination-not-open = Je hebt geen lopende kandidatuur.
governance-error-not-voter = Je staat niet op de vastgezette kiezerslijst.
governance-error-pause-already-active = De promotie van deze speler is al opgeschort.
governance-error-pause-already-member = Deze speler is al lid; een promotiepauze is niet nodig.
governance-error-pause-end-invalid = De promotiepauze moet in de toekomst eindigen.
governance-error-player-unknown = Deze speler heeft nog geen bestuursdossier.
governance-error-removal-requires-petition = Een afzettingsstemming vereist voldoende steun.
governance-error-removal-self = Je kunt je eigen afzetting niet steunen.
governance-error-removal-sponsor-ineligible = Alleen actieve leden mogen een afzetting steunen.
governance-error-removal-sponsors-too-few = Er zijn minstens twee andere actieve leden nodig.
governance-error-removal-vote-open = Er loopt al een afzettingsstemming voor deze moderator.
governance-error-termination-reason-invalid = Deze beëindigingsreden past niet bij deze stemming.
governance-error-vote-already-open = Er loopt al een stemming van dit type voor deze speler.
governance-error-vote-closed = Deze stemming is gesloten of beëindigd.
governance-error-vote-cooldown = De wachttijd van drie dagen na te lage opkomst is nog niet voorbij.
governance-error-vote-subject-unknown = De betrokkene heeft nog geen bestuursdossier.
governance-error-vote-termination-closed = Na de deadline kan de stemming niet meer worden ingetrokken.
governance-error-vote-unknown = Stemming niet gevonden.
governance-error-voters-too-few = Voor een stemming zijn minstens twee actieve leden nodig.
governance-failure-majority = Ja-stemmen waren niet meer dan de helft van alle stemmen
governance-failure-minimum-yes = Te weinig ja-stemmen
governance-failure-none = Geen reden vastgelegd
governance-failure-quorum = Vereiste opkomst niet gehaald
governance-failure-two-thirds = Ja-stemmen haalden geen twee derde van de niet-onthoudingen
governance-history-header = ─── Recente uitslagen ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
# $arg0 (Number): duration; locale decides the unit form.
governance-hours = { $arg0 } uur
governance-inactive-removal = { $arg0 } is na 180 dagen zonder inloggen automatisch gestopt als moderator.
governance-invalid-choice = Kies yes, no of abstain.
governance-invalid-duration = Ongeldige duur. Gebruik bijvoorbeeld 30m, 12h of 3d.
governance-kind-appointment = Benoeming
governance-kind-ban = Ban
governance-kind-removal = Afzetting
governance-legacy-ban-kick = Een eerdere bestuursstemming is aangenomen. Je hebt een ban voor { $arg0 }. Reden: { $arg1 }
governance-menu-actions = Acties
governance-menu-back = Terug
governance-menu-cancel = Annuleren
governance-menu-confirm = Bevestigen
governance-menu-confirm-nominate = Een stemming over je benoeming als moderator starten?
governance-menu-confirm-resign = Aftreden als moderator?
governance-menu-confirm-sponsor = Dit afzettingsverzoek steunen?
governance-menu-confirm-withdraw = Je aanmelding als moderator intrekken?
governance-menu-find-player = Andere speler zoeken
governance-menu-history = Stemgeschiedenis
governance-menu-loading = Bestuursstatus laden…
governance-menu-new-petition = Afzettingsverzoek starten
governance-menu-nominate = Aanmelden als moderator
governance-menu-nomination-open = Je benoemingsstemming loopt
governance-menu-pause = Promotie van nieuwkomer pauzeren
governance-menu-pause-appeal = Beroepsroute
governance-menu-pause-duration = Duur (bijv. 2d)
governance-menu-pause-reason = Reden
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } steunbetuigingen
governance-menu-petitions = Afzettingsverzoeken
governance-menu-player-name = Spelernaam
governance-menu-players = Spelerbeheer
governance-menu-recent-results = Recente stemuitslagen
governance-menu-refresh = Vernieuwen
governance-menu-resign = Aftreden als moderator
governance-menu-resume = Promotiepauze opheffen
governance-menu-resume-reason = Reden voor opheffing
governance-menu-staff-only = Alleen moderators en beheerders kunnen spelers beheren.
governance-menu-status = Jouw status
governance-menu-target = Moderatornaam
governance-menu-title = Gemeenschapsbestuur
governance-menu-votes = Lopende stemmingen
governance-menu-withdraw = Aanmelding intrekken
# $arg0 (Number): duration; locale decides the unit form.
governance-minutes = { $arg0 ->
        [one] { $arg0 } minuut
       *[other] { $arg0 } minuten
    }
governance-no-active-ban = Geen actieve ban
governance-no-active-pause = Geen actieve promotiepauze
governance-nomination-withdrawn = Je kandidatuur is ingetrokken. De uitslag wordt automatisch bekendgemaakt.
governance-none = Geen
governance-not-member = Geen lid
governance-not-moderator = Je bent momenteel geen moderator.
governance-operation-error = Bestuursactie mislukt. Probeer het opnieuw.
governance-own-choice-closed = Jouw keuze: { $arg0 }
governance-own-choice-open = Jouw keuze: { $arg0 }; je kunt die voor de deadline wijzigen.
governance-own-not-voted = Je mocht stemmen, maar hebt niet gestemd.
governance-own-not-voted-hint = Je hebt nog niet gestemd. Open Bestuur en kies deze stemming.
governance-own-not-voted-open = Je hebt nog niet gestemd.
governance-own-not-voter = Je staat niet op de kiezerslijst voor deze stemming.
governance-own-voted = Je hebt gestemd
governance-pause-cleared = Promotiepauze voor { $arg0 } opgeheven.
governance-pause-details = Pauze: { $arg0 } · Tot { $arg1 } · Bezwaar: { $arg2 }
governance-pause-fields-required = Geef een reden en een manier om bezwaar te maken.
governance-pause-not-active = { $arg0 } heeft geen actieve promotiepauze.
governance-pause-notice = Je promotie is gepauzeerd. Reden: { $arg0 } · Tot { $arg1 } · Bezwaar: { $arg2 }
governance-pause-set = Promotie van { $arg0 } gepauzeerd tot { $arg1 }.
governance-petition-already-sponsored = Je steunt deze afzetting al ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } steunbetuigingen · Tot { $arg3 }
governance-petition-started = Afzettingsverzoek voor { $arg0 }: { $arg1 }/{ $arg2 } steunbetuigingen; tot { $arg3 }.
governance-petitions-header = ─── Afzettingsverzoeken ───
governance-player-first-join = Eerste deelname: { $arg0 }
governance-player-header = ─── { $arg0 } · Bestuursdossier ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Laatst ingelogd: { $arg0 }
governance-player-member-since = Lid sinds: { $arg0 }
governance-player-never-joined = Speler { $arg0 } heeft deze server nog nooit bezocht.
governance-promotion-account-age = Eerste deelname minstens 3 dagen geleden
governance-promotion-header = ─── Promotievoorwaarden ───
governance-promotion-play-days = Effectief gespeeld op minstens 2 dagen (nu { $arg0 })
governance-promotion-playtime = Minstens 8 uur effectieve speeltijd (nu { $arg0 })
governance-resign-failed = Aftreden mislukt. Neem contact op met een beheerder.
governance-resigned = { $arg0 } is gestopt als moderator.
governance-result-announcement = Stemming #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = Afgewezen ({ $arg0 })
governance-result-open = Loopt
governance-result-passed = Aangenomen
governance-result-tally = { $arg0 } · Kiezers { $arg1 }, deelname { $arg2 }, ja { $arg3 }, nee { $arg4 }, onthouding { $arg5 }
governance-result-terminated = Beëindigd ({ $arg0 })
governance-resume-notice = Je promotiepauze is opgeheven. Reden: { $arg0 }
governance-resume-reason-required = Geef een reden voor het opheffen.
governance-role-lookup-error = Moderatorstatus kon niet worden gecontroleerd. Neem contact op met een beheerder.
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    }
governance-storage-error = Bestuursgegevens konden niet worden verwerkt. Neem contact op met een beheerder.
governance-target-not-moderator = { $arg0 } is momenteel geen moderator.
governance-termination-inactive = Moderator vertrokken na lange afwezigheid
governance-termination-ineligible = Kandidaat verloor de status van actief lid
governance-termination-resigned = Moderator trad af
governance-termination-unknown = Geen reden vastgelegd
governance-termination-withdrew = Kandidaat trok kandidatuur in
governance-vote-electorate = Kiezers { $arg0 } · Vereiste opkomst { $arg1 }
governance-vote-finalized = Afgerond: { $arg0 }
governance-vote-header = ─── Stemming #{ $arg0 } ───
governance-vote-in-progress = Loopt · { $arg0 } deelnemers; stemverdeling volgt na afloop.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · Sluit { $arg3 } · Kiezers { $arg4 } · Deelname { $arg5 } · Vereist { $arg6 }{ $arg7 }
governance-vote-period = Begon { $arg0 } · Sluit { $arg1 }
governance-vote-started = Stemming voor { $arg1 } #{ $arg0 } gestart: { $arg2 }; kiezers { $arg3 }, vereiste opkomst { $arg4 }, sluit { $arg5 }. Open Bestuur voor details.
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Lopende anonieme stemmingen ───

## World · Plots

plot-access-removed-for-you = Je toegang tot perceel { $arg0 } is verwijderd.
plot-admin-created = { $arg0 } geregistreerd voor { $arg1 } · { $arg2 }
plot-allowed = ingeschakeld
plot-area-changed = De grenzen zijn gewijzigd. Vernieuw en bevestig opnieuw.
plot-area-confirm = Grenzen van ‘{ $arg0 }’ vervangen door de selectie ({ $arg1 ->
        [one] { $arg1 } bouwcel
       *[other] { $arg1 } bouwcellen
    })? Bouwwerken blijven staan.
plot-area-hint = //pos1 → //pos2 → opslaan. Selecteer hoeken ook met de houten bijl; toevoegen/aftrekken voor meerdere gebieden.
plot-area-replace-save = Grenzen vervangen en opslaan
plot-area-save = Grenzen opslaan
plot-area-saved = Grenzen van “{ $arg0 }” opgeslagen.
plot-arrival-removed = Aankomstpunt verwijderd.
plot-arrival-set = Aankomstpunt opgeslagen.
plot-arrival-text-saved = Aankomsttitel opgeslagen.
plot-atmosphere-saved = Omgeving opgeslagen.
plot-back = Terug
plot-board-auto-notify-result = Meldingen voor { $arg0 ->
        [one] { $arg0 } speler
       *[other] { $arg0 } spelers
    } ingepland.
plot-board-auto-registered-text = Perceel „{ $arg0 }” is geregistreerd. Buitenste coördinaten van bebouwde cellen: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Bericht van { $arg0 } bij { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }: { $arg5 }
plot-board-empty = Er zijn nog geen gemeenschapsberichten.
plot-board-header = Berichten en reacties van de gemeenschap (laatste { $arg0 }; details: /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Mededeling
plot-board-kind-objection = Bezwaar
plot-board-menu = Gemeenschapsbord
plot-board-notice-alert = { $arg0 } heeft een kennisgeving geplaatst: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } heeft bezwaar gemaakt: { $arg1 } · { $arg2 }
plot-board-rate-limited = Limiet voor berichten bereikt. Probeer het later opnieuw.
plot-board-record-invalid = Bericht niet gevonden, al afgehandeld of handeling niet toegestaan.
plot-board-registered-alert = { $arg0 } heeft perceel „{ $arg1 }” geregistreerd: { $arg2 }
plot-board-ruling-alert = Er is beslist over het bezwaar van { $arg0 }: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Besluit: { $arg0 }
plot-board-saved = Bericht { $arg0 } opgeslagen.
plot-board-status-elapsed = Minstens 72 uur geleden gepubliceerd
plot-board-status-open = Openstaand
plot-board-status-ruled = Beslist
plot-board-status-withdrawn = Ingetrokken
plot-board-text-invalid = Beschrijving: 12–1000 tekens; besluit: minimaal 8 tekens.
plot-board-unavailable = Berichten zijn nog niet geladen. Probeer later opnieuw of ga naar https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } heeft een bezwaar ingetrokken: { $arg1 } · { $arg2 }
plot-board-write-failed = Het bord kon niet worden bijgewerkt. Probeer het later opnieuw.
plot-command-blocked = Perceelbeveiliging blokkeerde dit commando. Gebruik /fill of /setblock rechtstreeks in je eigen perceel; laat ingewikkelde /execute-commando’s door het serverteam beoordelen.
plot-command-description = Bouwpercelen en openbare projecten beheren
plot-created = Perceel { $arg0 } aangemaakt · { $arg1 }
plot-created-for-you = { $arg1 } heeft perceel { $arg0 } voor je geregistreerd.
plot-deleted = Perceel vrijgegeven; het bouwwerk blijft staan.
plot-denied = uitgeschakeld
plot-empty-summary = Hier is geen perceel.
plot-error-admin-owner-only = Leden met beheermogelijkheden wijzigen vereist leden- en rechtenbeheer.
plot-error-area-disconnected = Het perceel moet één aaneengesloten gebied zijn.
plot-error-args = Ongeldige of ontbrekende argumenten. Gebruik /plot help.
plot-error-arrival-location = Ga op een veilige vloer binnen het bebouwde deel van dit project staan.
plot-error-arrival-missing = Geen aankomstpunt ingesteld.
plot-error-arrival-private = Je kunt dit privéproject niet bezoeken.
plot-error-arrival-text = Ongeldige titel. Gebruik één regel; maximaal 32 zichtbare tekens voor de titel en 64 voor de ondertitel.
plot-error-arrival-unsafe = Dit aankomstpunt is niet beschikbaar of niet meer veilig.
plot-error-busy = Perceelgegevens worden opgeslagen. Probeer het later opnieuw; deze actie is niet ingediend.
plot-error-flag = Onbekend recht. Kies een actie uit de rechtenlijst.
plot-error-id = Perceel-ID ontbreekt of is dubbelzinnig. Gebruik /plot list.
plot-error-member-missing = Deze speler is geen lid meer.
plot-error-member-required = Nieuwe spelers kunnen percelen bekijken en bezwaar maken. Word lid om een project te maken.
plot-error-name = Projectnamen moeten 1–48 tekens bevatten.
plot-error-notice-text = Mededelingen mogen maximaal { $arg0 ->
        [one] { $arg0 } teken
       *[other] { $arg0 } tekens
    } bevatten, zonder limiet op het aantal regels of de regellengte; controletekens zijn niet toegestaan.
plot-error-overlap = Dit bouwgebied overlapt een ander perceel.
plot-error-owner-member = perceeleigenaar · Groepsrechten
plot-error-owner-only = uitgeschakeld · Beheer
plot-error-permission-changed = Rechten, leden of overerving gewijzigd. Bekijk de wijzigingen opnieuw.
plot-error-permission-grant = Je kunt geen beheermogelijkheden verlenen die je niet hebt.
plot-error-permission-owner-scope = Alleen de eigenaar, de eigenaar van het bovenliggende perceel of serverpersoneel mag eigenaarsrechten wijzigen.
plot-error-player-unknown = Speler niet gevonden. Controleer de naam of UUID.
plot-error-release-owner-only = uitgeschakeld · Perceel verwijderen
plot-error-selection = Markeer twee hoeken met //pos1 en //pos2.
plot-error-selection-changed = De selectie is gewijzigd. Bevestig opnieuw.
plot-error-staff-only = Alleen moderators of beheerders kunnen percelen van andere spelers beheren of registreren.
plot-error-storage = Perceelactie mislukt. Probeer het later opnieuw of neem contact op met het serverteam.
plot-error-subplot-arrival = Verplaats eerst het aankomstpunt van het hoofdperceel buiten de selectie.
plot-error-subplot-children = Geef deelpercelen vrij voordat je het hoofdperceel vrijgeeft.
plot-error-subplot-depth = Deelpercelen kunnen maar één niveau diep zijn.
plot-error-subplot-disconnected = De geselecteerde bouwcellen zijn niet verbonden. Verdeel elk deel apart.
plot-error-subplot-overlap = De selectie overlapt een ander deelperceel.
plot-error-subplot-own-arrival = Houd het aankomstpunt binnen het nieuwe gebied of verplaats het eerst.
plot-error-subplot-owner = Je hebt geen toestemming voor deze deelperceelbewerking.
plot-error-subplot-parent = Markeer beide hoeken binnen bestaande bouwcellen van het hoofdperceel.
plot-error-subplot-supervisor-member = De eigenaar van het hoofdperceel heeft hier al toezicht en heeft geen ledenrol nodig.
plot-error-time-format = Voer een tijd in als HH:mm (00:00–23:59).
plot-error-transfer-owner-only = uitgeschakeld · Eigendom overdragen
plot-error-transfer-self = Je bent al eigenaar van dit perceel.
plot-flag-set = { $arg0 }: { $arg1 }.
plot-help = Perceelgids
plot-here = Hier: { $arg0 } · { $arg1 }
plot-invited = Toegang verleend aan { $arg0 }.
plot-item-summary = Bebouwd oppervlak { $arg0 } blok² · { $arg1 ->
        [one] { $arg1 } lid
       *[other] { $arg1 } leden
    }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } blok²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Niet ingediend
plot-member-draft-reset = Categorie-instellingen wissen
plot-member-draft-role = Rol: { $arg0 }
plot-member-draft-submit = Bekijken en bevestigen
plot-member-draft-title = Ledenconcept · { $arg0 }
plot-member-permissions = Rechten
plot-member-removed = Projectlid verwijderd.
plot-menu-access = Toegang en leden
plot-menu-access-by-settings = volgens toegangsinstellingen
plot-menu-admin = Perceelbeheerder toevoegen
plot-menu-arrival = Aankomstpunt
plot-menu-arrival-subtitle = Ondertitel (MiniMessage; optioneel)
plot-menu-arrival-text = Aankomsttitel
plot-menu-arrival-title = Titel (MiniMessage; leeg = projectnaam)
plot-menu-atmosphere = Omgeving
plot-menu-clear = Helder
plot-menu-collaborator = Medewerker toevoegen
plot-menu-confirm-delete = De registratie van { $arg0 } verwijderen? Bescherming, ledenrechten en instellingen worden onherroepelijk verwijderd. Bouwwerken blijven staan.
plot-menu-confirm-subplot-transfer = { $arg0 } overdragen aan { $arg1 }? De eigenaar van het hoofdperceel houdt toezicht.
plot-menu-confirm-transfer = Het beheer van { $arg0 } overdragen aan { $arg1 }? Je verliest toegang; bestaand auteurschap blijft ongewijzigd.
plot-menu-confirm-withdraw = Deze reactie intrekken? De intrekking blijft openbaar zichtbaar.
plot-menu-create = Perceel maken
plot-menu-create-for-player = Perceel voor deze speler registreren
plot-menu-custom-time = Eigen tijd
plot-menu-delete = Perceelregistratie verwijderen
plot-menu-description = Beschrijving
plot-menu-feedback = Bezwaar maken tegen dit bericht
plot-menu-find-record = Zoek een bericht
plot-menu-flag-container = Containers openen
plot-menu-flag-entity = Entiteiten bedienen
plot-menu-follow-parent = Hoofdperceel volgen
plot-menu-follow-world = Wereld volgen
plot-menu-group-admin = perceelbeheerder
plot-menu-group-collaborator = Medewerkers
plot-menu-group-member = Standaard voor leden
plot-menu-group-newcomer = Standaard voor nieuwkomers
plot-menu-group-owner = perceeleigenaar
plot-menu-manage = Percelen van spelers beheren
plot-menu-managed-empty = Deze speler heeft nog geen percelen.
plot-menu-managed-title = Percelen van { $arg0 }
plot-menu-members = Leden ({ $arg0 })
plot-menu-my-access = Jouw toegang: { $arg0 } · { $arg1 }
plot-menu-name = Projectnaam
plot-menu-owner = Eigenaar: { $arg0 }
plot-menu-parent-supervisor = Eigenaar hoofdperceel: { $arg0 } · toezicht
plot-menu-player = Spelersnaam of UUID
plot-menu-project-kind = Projecttype
plot-menu-public-destinations = Openbare bestemmingen
plot-menu-public-destinations-empty = Er zijn nog geen openbare projecten met een aankomstpunt.
plot-menu-rain = Neerslag
plot-menu-record-id = Bericht-ID; gebruik de volledige ID voor oudere berichten
plot-menu-related-notice = Bijbehorend bericht bekijken
plot-menu-remove-arrival = Aankomstpunt verwijderen
plot-menu-remove-member = Lid verwijderen
plot-menu-rename = Project hernoemen
plot-menu-rule = Besluit vastleggen
plot-menu-ruling = Licht het besluit toe (8–1000 tekens)
plot-menu-set-arrival = Aankomstpunt hier instellen
plot-menu-set-role = Instellen als { $arg0 }
plot-menu-settings = Projectinstellingen
plot-menu-submit = Toepassen
plot-menu-subplot-create = Deelperceel maken
plot-menu-subplot-of = Onderdeel van { $arg0 }
plot-menu-subplot-parent = Terug naar hoofdperceel: { $arg0 }
plot-menu-subplots = Deelpercelen ({ $arg0 })
plot-menu-time = Tijd
plot-menu-time-input = Tijd (HH:mm)
plot-menu-transfer = Perceel overdragen
plot-menu-visit = Project bezoeken
plot-menu-weather = Weer
plot-menu-withdraw = Reactie intrekken
plot-model-fit = Alle grenzen inpassen
plot-model-focus = Mijn omgeving tonen
plot-model-layer-down = Doorsnede 1 blok verlagen
plot-model-layer-up = Doorsnede 1 blok verhogen
plot-model-legend = Cyaan: opgeslagen · Groen/goud: hoeken 1/2 · Paars: op te slaan grenzen · Wit/rood: jij/richting
plot-model-level = Doorsnede Y ≤ { $arg0 } (alleen voorbeeld)
plot-model-limited = Bemonstering of detail verminderd. Richt op de omgeving of een rechthoek voor meer detail; gebruik alleen grenzen om randen te bekijken.
plot-model-north-up = Bovenaanzicht toont het noorden boven. Schakel terug naar 3D om te draaien.
plot-model-perspective = 3D-weergave
plot-model-preparing = Grenzen berekenen en gebied controleren op de achtergrond… Wordt automatisch bijgewerkt zodra het klaar is.
plot-model-progress = Model lezen: { $arg0 }%…
plot-model-rotate-left = ↶ Links draaien
plot-model-rotate-right = ↷ Rechts draaien
plot-model-show-all = Volledige hoogte tonen
plot-model-slice = Boven mijn niveau afsnijden
plot-model-terrain = Bouwwerken en terrein tonen
plot-model-top = Bovenaanzicht
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } ongeladen monster
       *[other] { $arg0 } ongeladen monsters
    } (grijs). Chunks worden niet geforceerd geladen.
plot-model-wireframe = Alleen grenzen
plot-no-here = Hier is geen beschermd perceel.
plot-notice-board = Perceelbord
plot-notice-cleared = Perceelbord gewist
plot-notice-edit = Perceelbord bewerken
plot-notice-input = Maximaal { $arg0 ->
        [one] { $arg0 } teken
       *[other] { $arg0 } tekens
    }; meerdere regels toegestaan; laat leeg om te wissen
plot-notice-read = Volledige mededeling lezen
plot-notice-saved = Perceelbord bijgewerkt
plot-permission-all-actions = Alle acties
plot-permission-animal = Dieren verzorgen, temmen en aanlijnen
plot-permission-break = Blokken breken
plot-permission-bucket = Emmers voor vloeistoffen en dieren gebruiken
plot-permission-category-build = Bouwen en wijzigen
plot-permission-category-entity = Entiteiten
plot-permission-category-interaction = Blokinteractie
plot-permission-category-management = Beheer
plot-permission-category-storage = Opslag en voorwerpen
plot-permission-change-children = Betrokken subpercelen: { $arg0 }
plot-permission-change-confirm = Wijzigingen toepassen
plot-permission-change-granted = Toegekend: { $arg0 }
plot-permission-change-members = Betrokken leden: { $arg0 }
plot-permission-change-more = Nog { $arg0 }
plot-permission-change-no-effect = Effectieve rechten blijven gelijk. Expliciete instellingen vervangen toekomstige overerving.
plot-permission-change-none = Geen
plot-permission-change-revoked = Ingetrokken: { $arg0 }
plot-permission-change-subject = Doel: { $arg0 }
plot-permission-change-title = Rechtenwijzigingen bevestigen
plot-permission-create-subplot = Deelperceel maken
plot-permission-decoration = Decoraties plaatsen en bewerken
plot-permission-default-status = Standaard ({ $arg0 })
plot-permission-delete = Perceel verwijderen
plot-permission-door = Deuren en poorten
plot-permission-entity-damage = Entiteiten beschadigen
plot-permission-entity-storage = Entiteitenopslag openen
plot-permission-harvest = Bessen, ranken en honing oogsten
plot-permission-ignite = Vuur aansteken
plot-permission-item-drop = Voorwerpen laten vallen
plot-permission-item-pickup = Voorwerpen van de grond pakken
plot-permission-manage-area = Grenzen wijzigen
plot-permission-manage-members = Leden beheren
plot-permission-manage-permissions = Rechten beheren
plot-permission-manage-settings = Perceelinstellingen wijzigen
plot-permission-management = Perceelinstellingen en eigendom
plot-permission-modify = Blokken wijzigen en bemesten
plot-permission-operation = Actie
plot-permission-personal = Individuele rechten
plot-permission-personal-source = Individuele instelling
plot-permission-place = Blokken en voertuigen plaatsen
plot-permission-reset = Standaarden herstellen
plot-permission-result = Status
plot-permission-ride = Voertuigen en dieren berijden
plot-permission-select-state = Kies status: { $arg0 }
plot-permission-sign = Borden bewerken
plot-permission-source-group = Groepsrechten
plot-permission-source-local = Lokale instelling
plot-permission-source-owner = Herstelrecht van de eigenaar
plot-permission-source-parent = Bovenliggend perceel
plot-permission-source-role = Rolstandaard
plot-permission-state-default = Standaard
plot-permission-switch = Knoppen, hendels en drukplaten
plot-permission-trade = Handelen met dorpelingen
plot-permission-transfer = Eigendom overdragen
plot-permission-workstation = Faciliteiten en werkstations gebruiken
plot-pos1 = Eerste punt
plot-pos2 = Tweede punt
plot-private = Privéproject
plot-public = Openbaar project
plot-public-set = Projecttype: { $arg0 }.
plot-region-adjust = Deze rechthoek aanpassen
plot-region-apply = Aanpassing van rechthoek afronden
plot-region-cancel = Aanpassing van rechthoek annuleren
plot-region-changed = De selectie is gewijzigd. Selecteer de rechthoek opnieuw.
plot-region-delete = Deze rechthoek verwijderen
plot-region-delete-confirm = Rechthoek #{ $arg0 } uit het concept verwijderen? Gebouwen blijven staan. Grenzen veranderen pas na opslaan; je kunt dit ongedaan maken.
plot-region-edit-hint = Pas deze rechthoek aan met //pos1 en //pos2. Andere rechthoeken blijven gelijk. Rond de aanpassing af en sla daarna het perceel op.
plot-region-finish-first = Rond eerst de huidige rechthoekaanpassing af of annuleer deze.
plot-region-open = Rechthoeken beheren ({ $arg0 })
plot-region-select-hint = Selecteer een genummerde rechthoek om erop te focussen en deze te markeren. Grijze lijnen tonen deze pagina; samenvoegen kan de nummers veranderen.
plot-renamed = Project hernoemd.
plot-role-admin = perceelbeheerder
plot-role-changed-for-you = Je rol op perceel { $arg0 } is nu { $arg1 }.
plot-role-collaborator = medewerker
plot-role-owner = perceeleigenaar
plot-role-parent-owner = eigenaar van het hoofdperceel
plot-role-server-staff = serverbeheer
plot-selection-add = Huidige doos toevoegen
plot-selection-clear = Beide punten opnieuw kiezen
plot-selection-empty = Markeer twee hoeken met //pos1 en //pos2.
plot-selection-exit = Bewerken / voorbeeld afsluiten
plot-selection-fit = Opgeslagen grenzen herstellen
plot-selection-incomplete = Eén punt is gemarkeerd. Markeer het andere; vóór verzenden verandert niets.
plot-selection-mode = Punten in de wereld kiezen
plot-selection-no-redo = Geen selectiewijziging om opnieuw uit te voeren.
plot-selection-no-undo = Geen eerdere selectiewijziging om ongedaan te maken.
plot-selection-open = Grenzen bewerken
plot-selection-redo = Laatste stap opnieuw uitvoeren
plot-selection-replace = Vervangen door huidige doos
plot-selection-session-hint = Sluipen+F of /plot edit om terug te keren. Stopt na 20 minuten inactiviteit.
plot-selection-size = Selectie: { $arg0 } × { $arg1 } × { $arg2 } blokken (X/Y/Z)
plot-selection-stopped = Selectie beëindigd. Concepten blijven per perceel bewaard.
plot-selection-subtract = Huidige doos aftrekken
plot-selection-undo = Laatste stap ongedaan
plot-subplot-created = Deelperceel { $arg0 } gemaakt · { $arg1 } blok²
plot-subplot-created-for-you = Je bent nu eigenaar van deelperceel { $arg0 } binnen { $arg1 }.
plot-subplot-owner-changed = Deelperceel { $arg0 } is nu van { $arg1 }.
plot-subplot-parent-transferred = Het hoofdperceel van jouw deelperceel { $arg0 } is nu van { $arg1 }.
plot-subplot-released = Deelperceel { $arg0 } is vrijgegeven; het gebied blijft beschermd door het hoofdperceel.
plot-title = Percelen
plot-transferred = Perceel { $arg0 } overgedragen aan { $arg1 }.
plot-transferred-to-you = { $arg1 } heeft perceel { $arg0 } aan jou overgedragen.

## World · Vehicles

vehicle-admin-help = /vehicle — { vehicle-model-title }
    Modellen: model | model help
    Vastleggen: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Voertuigen: spawn <model> [x y z] | reload
vehicle-command-description = Voertuigen van weergave-entiteiten besturen en beheren
vehicle-controls = { $arg0 }: W/S en A/D besturen; scrollen schakelt of wijzigt gas; handwissel verandert modus; sluipen stapt uit. /vehicle help · /vehicle dashboard opent de bediening.
vehicle-dash-assisted = Stabiliteitshulp
vehicle-dash-automatic = Automatisch
vehicle-dash-contact = Steun op grond / water: { $arg0 }
vehicle-dash-details = Details
vehicle-dash-down = Terugschakelen −
vehicle-dash-drive = Rijden
vehicle-dash-energy = Verbruik { $arg0 } E/min · geschat bereik { $arg1 } min / { $arg2 } km
vehicle-dash-engine-start = Motor starten
vehicle-dash-engine-stop = Motor uitzetten
vehicle-dash-fuel = Brandstof { $arg0 } / { $arg1 } E · { $arg2 }%
vehicle-dash-gear = Versnelling { $arg0 }
vehicle-dash-gearbox = Versnellingsbak
vehicle-dash-handbrake = Parkeren / handrem: { $arg0 }
vehicle-dash-heading = Koers { $arg0 }°
vehicle-dash-health = Conditie { $arg0 }% · inzittenden { $arg1 } / { $arg2 }
vehicle-dash-input = Gas { $arg0 }% · rem { $arg1 }% · stuur { $arg2 }%
vehicle-dash-manual = Handmatig
vehicle-dash-mode-switch = Overschakelen naar { $arg0 }
vehicle-dash-position = Oorsprong { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Paneel opnieuw centreren
vehicle-dash-rpm = Toerental { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Snelheid { $arg0 } km/h · limiet { $arg1 }
vehicle-dash-throttle-down = Gas −5%
vehicle-dash-throttle-up = Gas +5%
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Zonder rijhulp
vehicle-dash-unavailable = Besturing niet beschikbaar; open het paneel opnieuw vanuit de bestuurdersstoel.
vehicle-dash-up = Opschakelen +
vehicle-dash-vertical = Hoogte { $arg0 } m · verticale snelheid { $arg1 } m/s
vehicle-error = Voertuigactie mislukt: { $arg0 }
vehicle-group-changed = De groepsstructuur is gewijzigd. Selecteer de groep opnieuw voordat je importeert.
vehicle-group-invalid = De groep bevat ongeschikte, beheerde, ontbrekende of te verre entiteiten. Alle leden moeten binnen 32 blokken in deze wereld zijn.
vehicle-group-limit = Importeer maximaal 128 weergaven en in totaal 256 groepsleden.
vehicle-group-not-found = Geen weergavegroep gevonden. Selecteer een groep, richt op een blok- of voorwerpweergave of geef een entiteits-UUID op.
vehicle-group-selected = Geselecteerde groep: { $arg0 ->
        [one] { $arg0 } weergave-entiteit
       *[other] { $arg0 } weergave-entiteiten
    } · hoofd { $arg1 }
vehicle-help = /vehicle — Voertuigbediening
    Instappen: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Rijden: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    Weergave: hud [on/off] | dashboard [drive/gearbox/details/off]
    Onderhoud: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } kn · stuwkracht { $arg1 }% · koers { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · hoogte { $arg1 } m · stijgsnelheid { $arg2 } m/s · gas { $arg3 }% · { $arg4 }
vehicle-hud-status = Brandstof { $arg0 }% · conditie { $arg1 }% · { $arg2 }
vehicle-model-add = Onderdeel toevoegen
vehicle-model-add-here = Steunpunt bij mijn voeten toevoegen
vehicle-model-anchor = Voorbeeldoorsprong hier plaatsen, in mijn kijkrichting
vehicle-model-animation = Animatie
vehicle-model-apply = Toepassen / bevestigen
vehicle-model-auto = Botsingsgrenzen opnieuw berekenen
vehicle-model-block = Blok
vehicle-model-center-here = Zwaartepunt bij mijn voeten plaatsen
vehicle-model-center-look = Zwaartepunt op het gerichte oppervlak plaatsen
vehicle-model-check = Structuur en instellingen controleren
vehicle-model-checked = Structuur en instellingen zijn geldig. Opslaan controleert ook weergavegegevens, bestaande voertuigen en botsingen met de wereld.
vehicle-model-clone = Model klonen
vehicle-model-colliders = Botsingsvakken
vehicle-model-copy = Kopiëren en bewerken (gekopieerde stoelen zijn passagiersstoelen)
vehicle-model-corner-cancel = Lopend botsingsvak annuleren
vehicle-model-corner-first = Eerste hoek bij mijn voeten markeren
vehicle-model-corner-first-look = Eerste hoek op het gerichte oppervlak markeren
vehicle-model-corner-hint = Eerste hoek gemarkeerd. Ga naar de tegenoverliggende hoek, ook qua hoogte, en gebruik /vehicle model collider corner2. Het vak volgt de modelassen.
vehicle-model-corner-required = Markeer eerst de eerste hoek met /vehicle model collider corner1.
vehicle-model-corner-second = Mijn voeten als tegenoverliggende hoek gebruiken en vak toevoegen
vehicle-model-corner-second-look = Gericht oppervlak als tegenoverliggende hoek gebruiken
vehicle-model-create = Model maken
vehicle-model-delete = Model verwijderen
vehicle-model-delete-warning = Dit model definitief verwijderen? Dat kan niet zolang er voertuigen van bestaan.
vehicle-model-deleted = Model verwijderd
vehicle-model-discard = Concept verwerpen
vehicle-model-discard-warning = Alle niet-opgeslagen wijzigingen verwerpen? Dit kan niet ongedaan worden gemaakt.
vehicle-model-discarded = Concept verworpen
vehicle-model-draft = Niet-opgeslagen concept
vehicle-model-driver = Bestuurdersstoel
vehicle-model-driver-here = Bestuurdersstoel bij mijn voeten toevoegen
vehicle-model-edit = Concept bewerken / hervatten
vehicle-model-exit = Uitstappunt / fijnafstelling
vehicle-model-group = Axiom-weergavegroep toevoegen
vehicle-model-help = /vehicle model — { vehicle-model-title }
    Modellen: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Bewerken: set <field> <value...> | part/seat/collider/support — Tab toont bewerkingen en argumenten
    Concept: preview [off] | undo | redo | save | cancel
    In de wereld: anchor | pick | here | look | snap [on/off] | move <index> <dx dy dz>
    { vehicle-model-position-hint }
    seat position/exit | part position | support add/set | collider position: here / look
    collider corner1/corner2 [look] | collider cancel — { vehicle-model-corner-hint }
    Meer acties: check | apply — { vehicle-model-save-continue }
vehicle-model-here = Mijn huidige voetpositie gebruiken
vehicle-model-history-empty = Geen wijziging beschikbaar in deze richting van de geschiedenis.
vehicle-model-import = Axiom-weergavegroep importeren
vehicle-model-item = Voorwerp
vehicle-model-library = Opgeslagen modellen · concepten blijven in het geheugen tot je ze opslaat
vehicle-model-look = Plaatsen door te richten (menu sluit; rechtsklik bevestigt)
vehicle-model-look-missing = Geen oppervlak geraakt. Richt op een wereldblok of je privévoorbeeld binnen 32 blokken.
vehicle-model-manual = Lokale coördinaten invoeren
vehicle-model-options = Opties
vehicle-model-parameters = Fysica- en motorinstellingen
vehicle-model-parts = Weergaveonderdelen
vehicle-model-passenger-here = Passagiersstoel bij mijn voeten toevoegen
vehicle-model-pick = Het voorbeeldonderdeel bewerken waarnaar ik kijk
vehicle-model-pick-hint = Open een voorbeeld en kijk naar een blok- of voorwerponderdeel, of selecteer eerst een onderdeel in de editor.
vehicle-model-place-cancelled = Plaatsing geannuleerd: verlopen, bewerkingsstatus gewijzigd of handmatig geannuleerd.
vehicle-model-place-hint = Richtplaatsing is 30 seconden actief. Richt op een wereldblok of je voorbeeld; rechtsklik met de hoofdhand bevestigt, sluipen + rechtsklik annuleert.
vehicle-model-position = Positie / fijnafstelling
vehicle-model-position-hint = Lokale X/Y/Z · here gebruikt je voeten, look een geraakt oppervlak · het optionele raster volgt de afstelstap.
vehicle-model-preview = Privévoorbeeld
vehicle-model-preview-failed = Voorbeeld niet beschikbaar; je concept blijft behouden: { $arg0 }
vehicle-model-preview-hint = Privévoorbeeld aan. Verdwijnt na vijf minuten zonder bewerken; /vehicle model preview off om te sluiten.
vehicle-model-redo = Laatste ongedaan gemaakte wijziging herstellen
vehicle-model-remove = Onderdeel verwijderen
vehicle-model-remove-warning = Dit onderdeel verwijderen? Volgende indexnummers schuiven op; ongedaan maken blijft mogelijk.
vehicle-model-rotate = Onderdeel draaien
vehicle-model-rotate-hint = Relatieve lokale rotatie: X kantelen, Y draaien, Z rollen. De positie blijft gelijk.
vehicle-model-save = Controleren en opslaan
vehicle-model-save-continue = Opslaan en verder bewerken
vehicle-model-saved = Opgeslagen
vehicle-model-scale = Onderdeel schalen
vehicle-model-scale-all = Alle assen schalen × { $arg0 }
vehicle-model-scale-hint = Vermenigvuldig of deel lokale aslengten; dit zijn geen wereldafmetingen.
vehicle-model-seats = Stoelen en uitstappunten
vehicle-model-selected = Geselecteerde weergaven importeren
vehicle-model-size = Botsingsvak vergroten of verkleinen
vehicle-model-size-hint = Volledige breedte, hoogte en lengte; het middelpunt blijft gelijk.
vehicle-model-snap-off = Rasteruitlijning: uit
vehicle-model-snap-on = Rasteruitlijning: aan
vehicle-model-step = Afstelstap wisselen
vehicle-model-supports = Steunpunten op de grond
vehicle-model-text = Tekst
vehicle-model-title = Voertuigmodellen beheren
vehicle-model-tools = Bewerken in de wereld
vehicle-model-transform-step = Afstelstap wijzigen
vehicle-model-undo = Laatste wijziging ongedaan maken
vehicle-result = Voertuig: { $arg0 }
vehicle-selection-info = Selectie: { $arg0 ->
        [one] { $arg0 } weergave-entiteit
       *[other] { $arg0 } weergave-entiteiten
    } · groep { $arg1 } · oorsprong { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Botsing / obstakel
vehicle-status-damage = Voertuig ernstig beschadigd
vehicle-status-disabled = Voertuig uitgeschakeld
vehicle-status-engine-off = Motor uit
vehicle-status-engine-on = Motor draait
vehicle-status-gear-limit = Bedieningslimiet bereikt
vehicle-status-grounded = Boot aan de grond
vehicle-status-low-fuel = Weinig brandstof
vehicle-status-manual-only = Schakel eerst over naar handmatig
vehicle-status-no-fuel = Geen brandstof
vehicle-status-overrev = Terugschakelen zou de motor overtoeren laten maken
vehicle-status-shifting = Bezig met schakelen
vehicle-status-stall = Motor afgeslagen
vehicle-status-stop-first = Stop voordat je van rijrichting wisselt of parkeert
vehicle-value-off = Uit
vehicle-value-on = Aan

## World · Elevators, regeneration and spawn

async-regen-already-running = Er wordt al een regeneratie uitgevoerd; gebruik /regen status of /regen cancel
async-regen-apply-expired = Er is geen geldig plan; voer eerst /regen preview uit
async-regen-apply-plan = Opgeslagen plan toepassen
async-regen-apply-started = { $arg0 } wordt toegepast op de opgeslagen selectie: { $arg1 }
async-regen-awaiting-apply = Plan wacht op toepassing: { $arg0 } · { $arg1 } · verloopt over { $arg2 ->
        [one] { $arg2 } seconde
       *[other] { $arg2 } seconden
    }
async-regen-block-preview-progress = blokvoorbeeld · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } gecontroleerd · { $arg2 ->
        [one] { $arg2 } blokwijziging
       *[other] { $arg2 } blokwijzigingen
    } · { $arg3 ->
        [one] { $arg3 } blokentiteit
       *[other] { $arg3 } blokentiteiten
    } met risico
async-regen-block-preview-summary = Voorbeeld: { $arg0 ->
        [one] { $arg0 } blokwijziging
       *[other] { $arg0 } blokwijzigingen
    } voor { $arg2 }; risico op overschrijven: { $arg1 ->
        [one] { $arg1 } bestaande blokentiteit
       *[other] { $arg1 } bestaande blokentiteiten
    }
async-regen-cancelled = Regeneratie geannuleerd. De { $arg0 } al toegepaste wijzigingen blijven in de wereld
async-regen-command-description = Een WorldEdit-selectie asynchroon regenereren
async-regen-completed = Regeneratie voltooid: { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    } gewijzigd in { $arg1 }
async-regen-failed = Regeneratie gestopt door een interne fout. De { $arg0 } al toegepaste wijzigingen blijven in de wereld
async-regen-filter-all = alle bloktypen
async-regen-invalid-blocks = Onbekende typen of geen bloktypen: { $arg0 }
async-regen-not-running = Je hebt geen actieve regeneratie
async-regen-nothing-to-apply = De selectie komt al overeen met dit plan; er hoeft niets te worden toegepast
async-regen-plan-cancel = Annuleren
async-regen-plan-dialog-title = Regeneratieplan toepassen
async-regen-preview-cancelled = Voorbeeld geannuleerd; er zijn geen wereldgegevens gewijzigd
async-regen-preview-started = Voorbeeld gestart: selectie { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    } · gebied { $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } · modus { $arg2 }
async-regen-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } gepland · { $arg3 ->
        [one] { $arg3 } wijziging
       *[other] { $arg3 } wijzigingen
    } toegepast
async-regen-safety-note = Buiten de opgeslagen selectie verandert niets. Bouwwerken erin kunnen worden overschreven en Vanilla herkent geen door spelers geplaatste blokken. Controleer de visualisatie en maak een back-up voordat je toepast.
async-regen-scope-blocks = blokken regenereren die overeenkomen met { $arg0 }
async-regen-selection-required = Maak eerst een volledige WorldEdit-selectie
async-regen-skipped-ungenerated = Niet-gegenereerde doelchunks overgeslagen: { $arg0 ->
        [one] { $arg0 } chunk
       *[other] { $arg0 } chunks
    }. Er zijn geen doelchunks gemaakt of gewijzigd.
async-regen-start-failed = De regeneratie kon niet worden gestart; bekijk het serverlogboek voor details
async-regen-state-applying = wijzigingen toepassen
async-regen-state-cancelled = geannuleerd
async-regen-state-completed = voltooid
async-regen-state-failed = mislukt
async-regen-state-generating = chunks genereren en vergelijken
async-regen-state-preparing = bronwereld voorbereiden
async-regen-status = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } gepland · { $arg3 } van { $arg4 ->
        [one] { $arg4 } gevonden wijziging
       *[other] { $arg4 } gevonden wijzigingen
    } toegepast
async-regen-structure-metadata-applied = { $arg0 ->
        [one] { $arg0 } structuurstart
       *[other] { $arg0 } structuurstarts
    } en { $arg1 ->
        [one] { $arg1 } chunkverwijzing
       *[other] { $arg1 } chunkverwijzingen
    } overgedragen; /locate en structuurafhankelijke spawning herkennen ze
async-regen-upgrade-apply-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } gepland · { $arg3 ->
        [one] { $arg3 } blok
       *[other] { $arg3 } blokken
    } en { $arg4 ->
        [one] { $arg4 } bioomcel
       *[other] { $arg4 } bioomcellen
    } toegepast
async-regen-upgrade-boundary-note = { $arg0 ->
        [one] { $arg0 } bioomcel
       *[other] { $arg0 } bioomcellen
    } op de selectiegrens worden overgeslagen om wijzigingen buiten de selectie te voorkomen
async-regen-upgrade-completed = Wereldupgrade voltooid: { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    } en { $arg1 ->
        [one] { $arg1 } bioomcel
       *[other] { $arg1 } bioomcellen
    } gewijzigd in { $arg2 }
async-regen-upgrade-preview-progress = upgradevoorbeeld · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } gecontroleerd · { $arg2 ->
        [one] { $arg2 } blokwijziging
       *[other] { $arg2 } blokwijzigingen
    } · { $arg3 ->
        [one] { $arg3 } bioomcel
       *[other] { $arg3 } bioomcellen
    } · { $arg4 ->
        [one] { $arg4 } structuur
       *[other] { $arg4 } structuren
    }
async-regen-upgrade-preview-summary = Voorbeeld: { $arg0 ->
        [one] { $arg0 } blokwijziging
       *[other] { $arg0 } blokwijzigingen
    }, { $arg1 ->
        [one] { $arg1 } bioomcel
       *[other] { $arg1 } bioomcellen
    }, { $arg2 ->
        [one] { $arg2 } structuur
       *[other] { $arg2 } structuren
    } (geschikt voor volledige metadataoverdracht: { $arg3 }); risico op overschrijven: { $arg4 ->
        [one] { $arg4 } bestaande blokentiteit
       *[other] { $arg4 } bestaande blokentiteiten
    }
async-regen-upgrade-scope-biomes = alleen biomen
async-regen-upgrade-scope-terrain = terrein, structuren en biomen
async-regen-usage = Gebruik: /regen preview blocks [typen...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Visualisatie in { $arg0 }: cyaan = selectiegrens, oranje = steekproef van gewijzigde blokken, rood = bedreigde blokentiteit. Alleen jij ziet deze deeltjes.
async-regen-visualization-selection = Selectie in { $arg0 }: cyaan deeltjes markeren de grens. Alleen jij ziet ze.
async-regen-world-edit-required = WorldEdit is vereist om de selectie te lezen
iron-elevator-down = ↓ Sluipen
iron-elevator-up = ↑ Springen
spawn-beacon-color-command-description = Schakel de kleurcyclus van het spawnbaken in of uit
spawn-beacon-color-disabled = Kleurcyclus van het spawnbaken uitgeschakeld
spawn-beacon-color-enabled = Kleurcyclus van het spawnbaken ingeschakeld
spawn-command-description = Terug naar de spawn
spawn-world-missing = Spawnwereld is niet beschikbaar

## Entertainment · Music

music-action-add-queue = Aan wachtrij toevoegen
music-action-play-nearby = Afspelen op de dichtstbijzijnde jukebox
music-action-play-now = Nu op deze jukebox afspelen
music-action-take-disc = Muziekplaat pakken
music-added-to-queue-rich = Toegevoegd aan wachtrij: { $music }
music-album = Album: { $arg0 }
music-all-in-queue = Alle nummers staan al in de wachtrij
music-artist = Artiest: { $arg0 }
music-back = Terug
music-browser-left-click = Linksklik
music-browser-right-click = Rechtsklik
music-cache-clear-done = Online muziekcache gewist
music-cache-clear-started = Online muziekcache wissen...
music-cache-operation-failed = Muziekcachebewerking mislukt: { $arg0 }
music-cache-status = Online muziekcache: { $arg0 ->
        [one] { $arg0 } bestand
       *[other] { $arg0 } bestanden
    }, { $arg1 } / { $arg2 }
music-cached = Gecachet - direct afspeelbaar
music-clear-queue = Wachtrij wissen
music-clear-queue-done = Wachtrij geleegd ({ $arg0 ->
        [one] { $arg0 } nummer
       *[other] { $arg0 } nummers
    })
music-command-description = Lokale, online en NBS-muziek voor platenspelers
music-disc-left = Linksklik: pak de muziekplaat
music-disc-right = Rechtsklik: speel af op de dichtstbijzijnde platenspeler
music-duplicate-in-queue = Dit nummer staat al in de wachtrij
music-duration = Duur: { $arg0 }
music-empty-library = Muziekbibliotheek is leeg
music-empty-library-description = Geen lokale audiobestanden of NBS-nummers beschikbaar
music-empty-nbs = Geen NBS-nummers gevonden
music-empty-nbs-description = Schakel naar alle muziek of voeg een NBS-bestand toe
music-empty-playlist = Deze afspeellijst bevat geen beschikbare nummers
music-empty-playlist-description = Controleer of de afspeellijst openbaar is en het juiste platform is geselecteerd
music-empty-search = Geen overeenkomende nummers
music-empty-search-description = Probeer een ander trefwoord of controleer de status van aangepaste bronnen
music-finished = Afgelopen
music-format = Formaat: { $arg0 }
music-inventory-full = Inventaris is vol. Muziekplaat kon niet worden gegeven
music-jukebox-audible-field-conflict = Een andere platenspeler speelt een ander nummer in een overlappend duidelijk hoorbaar gebied
music-jukebox-music-mode = Muziekmodus
music-jukebox-play-order = Afspeelvolgorde
music-jukebox-range = Afspeelbereik
music-jukebox-range-value = Huidig: { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    }
music-jukebox-rhythm-mode = Ritmemodus
music-jukebox-too-far = Je bent te ver van de platenspeler verwijderd (maximaal { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    })
music-jukebox-tp-hover = Klik om naar de platenspeler te teleporteren
music-jukebox-unavailable = De platenspeler is verwijderd of de chunk ervan is niet beschikbaar
music-jukebox-usage-mode = Jukeboxgebruik
music-jukebox-volume = Volume van jukebox
music-jukebox-volume-value = Huidig: { $arg0 }%
music-library-button = Muziekbibliotheek
music-library-random-mode = Bibliotheek willekeurig
music-library-random-mode-desc = Speel willekeurig uit de volledige muziekbibliotheek zonder hetzelfde nummer direct te herhalen
music-loading-title = Laden
music-menu-nbs-title = NBS-muziek - pagina { $arg0 }/{ $arg1 }
music-menu-playlist-title = Afspeellijst: { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = NBS zoeken: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-title = Zoeken: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-title = Muziekplaten - pagina { $arg0 }/{ $arg1 }
music-mode-switched = Gewisseld naar { $arg0 }
music-nearest-jukebox-missing = Geen platenspeler gevonden binnen { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    }
music-next-page = Volgende pagina
music-no-disc = Geen muziekplaat
music-no-files = Geen muziekbestanden beschikbaar
music-no-search-results-rich = Geen nummers met "{ $keyword }" gevonden
music-now-playing-rich = Wordt nu afgespeeld: { $music } bij platenspeler { $location }
music-online-partial-failure = Mislukte bronaanvragen: { $arg0 ->
        [one] { $arg0 } aanvraag
       *[other] { $arg0 } aanvragen
    }; beschikbare resultaten worden getoond
music-online-rate-limited = Te veel muziekaanvragen. Probeer het over { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    } opnieuw.
music-online-searching = Lokale en online bronnen doorzoeken naar "{ $arg0 }"...
music-original-author = Oorspronkelijke auteur: { $arg0 }
music-page-out-of-range = Pagina buiten bereik. Hoogste pagina: { $arg0 }
music-page-total = { $arg0 ->
        [one] { $arg0 } nummer
       *[other] { $arg0 } nummers
    } in totaal
music-page-usage-desc = Ga naar een pagina
music-partial-results = Mislukte bronaanvragen: { $arg0 ->
        [one] { $arg0 } aanvraag
       *[other] { $arg0 } aanvragen
    }; beschikbare resultaten worden getoond
music-paused = Gepauzeerd
music-plasmo-voice-unavailable = Het nummer is gestart, maar je kunt het niet horen omdat Plasmo Voice niet is geïnstalleerd, verbonden of ingeschakeld
music-play-next = Volgende afspelen
music-play-random = Willekeurig afspelen
music-playback-failed = "{ $arg0 }" kon niet worden afgespeeld. Controleer de bron of het muziekbestand en probeer het opnieuw.
music-playback-status = Afspeelstatus: { $arg0 }
music-playing = Wordt afgespeeld
music-playing-now-rich = Wordt nu afgespeeld: { $music }
music-playlist-import-button = Afspeellijst importeren
music-playlist-import-done = ‘{ $arg0 }’ geïmporteerd: { $arg1 } van { $arg2 ->
        [one] { $arg2 } nummer
       *[other] { $arg2 } nummers
    }.
music-playlist-import-failed = Importeren van afspeellijst mislukt: { $arg0 }
music-playlist-import-prompt = Kies een platform en plak daarna een openbare afspeellijst-ID of link:
music-playlist-import-usage-desc = Een openbare afspeellijst van een ander muziekplatform importeren
music-playlist-importing = Afspeellijst importeren...
music-playlist-reference-prompt = Plak de ID of link van een openbare afspeellijst
music-prev-page = Vorige pagina
music-queue-empty = Wachtrij is leeg
music-queue-move-down = Omlaag verplaatsen
music-queue-move-up = Omhoog verplaatsen
music-queue-remove = Nummer verwijderen
music-queue-summary = Wachtrij: { $arg0 ->
        [one] { $arg0 } nummer
       *[other] { $arg0 } nummers
    }
music-queue-summary-description = Pagina { $arg0 }/{ $arg1 }
music-random-add = Willekeurig nummer toevoegen
music-random-added-to-queue-rich = Willekeurig aan wachtrij toegevoegd: { $music }
music-random-disc = Willekeurige muziekplaat
music-random-disc-got-rich = Willekeurige muziekplaat ontvangen: { $music }
music-random-mode = Lijst willekeurig
music-random-mode-desc = Speel de lijst willekeurig af zonder hetzelfde nummer direct te herhalen
music-rank-down = Omlaag verplaatst
music-rank-up = Omhoog verplaatst
music-reload-done = Opnieuw laden voltooid: { $arg0 ->
        [one] { $arg0 } lokaal nummer
       *[other] { $arg0 } lokale nummers
    }; LX-bronnen beschikbaar { $arg1 }/{ $arg2 }; abonnementen gecontroleerd { $arg3 }, bijgewerkt { $arg4 }
music-reload-failed = Muziek opnieuw laden mislukt: { $arg0 }
music-reload-issues = Problemen bij opnieuw laden: { $arg0 }
music-reload-partial = Opnieuw laden voltooid met behoud van eerdere gegevens: { $arg0 ->
        [one] { $arg0 } lokaal nummer
       *[other] { $arg0 } lokale nummers
    }; LX-bronnen beschikbaar { $arg1 }/{ $arg2 }; abonnementen gecontroleerd { $arg3 }, bijgewerkt { $arg4 }
music-reload-started = Muziekbestanden en aangepaste bronnen opnieuw laden...
music-remote-source-error = updatefout: { $arg0 }
music-remote-source-import-done = LX-bronabonnement geïmporteerd: { $arg0 }
music-remote-source-import-failed = Importeren van LX-bron mislukt: { $arg0 }
music-remote-source-import-started = LX-bronabonnement importeren...
music-remote-source-remove-done = LX-bronabonnement verwijderd: { $arg0 }
music-remote-source-remove-failed = LX-bronabonnement kon niet worden verwijderd: { $arg0 }
music-remote-source-remove-not-found = LX-bronabonnement niet gevonden: { $arg0 }
music-remote-source-update-done = Gecontroleerd: { $arg0 ->
        [one] { $arg0 } abonnement
       *[other] { $arg0 } abonnementen
    }; bijgewerkt: { $arg1 }; mislukt: { $arg2 }.
music-remote-source-update-failed = Bijwerken van LX-bron mislukt: { $arg0 }
music-remote-source-update-started = Geïmporteerde LX-bronabonnementen controleren...
music-remote-source-updated-at = bijgewerkt: { $arg0 }
music-remote-sources-title = Geïmporteerde LX-bronabonnementen: { $arg0 }
music-removed-from-queue-rich = Uit wachtrij verwijderd: { $music }
music-repeat-one-mode = Eén nummer herhalen
music-repeat-one-mode-desc = Herhaal het huidige nummer tot je handmatig doorgaat
music-request-error = Aanvraag mislukt: { $arg0 }
music-rhythm-animation-offset = Animatieverschuiving { $arg0 } ms
music-rhythm-calibration = Latentiekalibratie
music-rhythm-calibration-accumulating = Stabiele timing wordt bevestigd… blijf meespelen
music-rhythm-calibration-adapting = Ritme wordt afgestemd… blijf meespelen
music-rhythm-calibration-complete = Latentietest voltooid · ritmespel gereed
music-rhythm-calibration-done = Latentietest voltooid · instellingen opgeslagen
music-rhythm-calibration-failed = De audio van de test stopte onverwacht. De test is veilig gesloten; start hem opnieuw
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Toetsenbord visueel → Muis visueel
music-rhythm-calibration-listen = Luister eenmaal naar het gemaakte ritme · druk nog niet
music-rhythm-calibration-minecraft-instruction = Volg het Minecraft-ritme en druk 1–4
music-rhythm-calibration-no-input = Geen invoer gedetecteerd. Controleer toetsen en volume en blijf meespelen
music-rhythm-calibration-out-of-range = De gemeten vertraging valt buiten het ondersteunde bereik. Controleer de audio-instellingen
music-rhythm-calibration-plasmo-instruction = Nu meedoen: herhaal het Plasmo Voice-ritme en druk 1–4 bij elke slag
music-rhythm-calibration-plasmo-ready = Minecraft-test voltooid; luister eerst via Plasmo Voice naar hetzelfde ritme
music-rhythm-calibration-pointer-instruction = Klik links of rechts wanneer de noot de lijn passeert.
music-rhythm-calibration-prompt-confirm = Volledige test starten
music-rhythm-calibration-prompt-confirm-hint = Keert automatisch terug na afloop.
music-rhythm-calibration-prompt-later = Niet nu
music-rhythm-calibration-prompt-reason = Voer eerst een latentie-test uit.
music-rhythm-calibration-prompt-title = Latentietest vereist vóór het spelen
music-rhythm-calibration-realigning = De audiostream werd onderbroken. De demonstratie wordt herhaald en voltooide metingen blijven behouden
music-rhythm-calibration-required = Voltooi de volledige latentietest voordat je het ritmespel start
music-rhythm-calibration-reset-done = Latentiekalibratie is gereset
music-rhythm-calibration-result-hold = Alle tests voltooid. Bekijk het resultaat; je keert daarna automatisch terug
music-rhythm-calibration-stage-minecraft = Minecraft-nootblok
music-rhythm-calibration-stage-plasmo = Plasmo Voice-audio- en invoertest
music-rhythm-calibration-stage-pointer = Muis
music-rhythm-calibration-stage-visual = Beeld- en invoertest
music-rhythm-calibration-visual-instruction = Druk op 1–4 wanneer de noot de lijn passeert.
music-rhythm-calibration-visual-preview = Bekijk één volledige reeks zonder invoer · begin zodra de instructie verandert
music-rhythm-calibration-voice-required = De volledige test vereist een verbonden en ingeschakelde Plasmo Voice
music-rhythm-chart-preparing = Volledige beatmap voorbereiden…
music-rhythm-combo = Combo ×{ $arg0 }
music-rhythm-difficulty-easy = Makkelijk
music-rhythm-difficulty-expert = Expert
music-rhythm-difficulty-hard = Moeilijk
music-rhythm-difficulty-normal = Normaal
music-rhythm-exit = Ritmespel verlaten
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
music-rhythm-get-ready = Maak je klaar
music-rhythm-go = START!
music-rhythm-good = Goed
music-rhythm-great = Geweldig
music-rhythm-latency = Latentie { $arg0 } ms
music-rhythm-latency-test = Volledige latentietest
music-rhythm-miss = Gemist
music-rhythm-mode-falling = Vallende noten
music-rhythm-mode-radial = Radiale aanval
music-rhythm-mode-spatial-aim = Ruimtelijk richten
music-rhythm-network-too-high = De netwerklatentie is te hoog voor een eerlijke beoordeling ({ $arg0 } ms; limiet { $arg1 } ms)
music-rhythm-network-warning = De netwerklatentie is instabiel ({ $arg0 } ms). De sessie stopt als dit boven { $arg1 } ms blijft
music-rhythm-no-beats = In dit nummer zijn geen speelbare beats gevonden
music-rhythm-perfect = Perfect
music-rhythm-preparing = Wacht tot het nummer volledig is gecachet en ritmisch is geanalyseerd
music-rhythm-ready = Volledige beatmap gereed — start op de eerste tel
music-rhythm-requires-playback = Speel eerst een nummer af op deze platenspeler
music-rhythm-reset-calibration = Latentiekalibratie resetten
music-rhythm-results = Resultaten
music-rhythm-score = Score: { $arg0 }
music-rhythm-select-track = Ritmenummer kiezen
music-rhythm-spatial-no-room = Er is niet genoeg vrije ruimte. Ga naar een opener gebied.
music-rhythm-spatial-slider-hint = Volg de bewegende ster en klik elk controlepunt · niet vasthouden
music-rhythm-start-game = Spel starten
music-rhythm-waiting-for-player = Wachten tot een speler het ritmespel start
music-sample-rate = Bemonsteringsfrequentie: { $arg0 }
music-search-button = Nummers zoeken
music-search-keyword-required = De zoekterm mag niet leeg zijn
music-search-keyword-too-long = Zoekterm te lang. Limiet: { $arg0 ->
        [one] { $arg0 } teken
       *[other] { $arg0 } tekens
    }.
music-search-usage-desc = Zoek nummers
music-section-all = Alle muziek
music-section-nbs = NBS-muziek
music-select-music = Muziek selecteren
music-sequential-mode = Lijst herhalen
music-sequential-mode-desc = Speel de lijst op volgorde af en begin daarna opnieuw
music-size = Grootte: { $arg0 }
music-sort-button = Sorteren
music-sort-current = Huidig: { $arg0 }
music-sort-default = Standaardvolgorde
music-sort-most-played = Meest afgespeeld
music-sort-recently-played = Recent afgespeeld
music-sound-settings = Geluidsinstellingen
music-source-actions = acties: { $arg0 }
music-source-available = beschikbaar
music-source-capabilities = mogelijkheden: { $arg0 }
music-source-error = fout: { $arg0 }
music-source-failures = fouten: { $arg0 }
music-source-retry = opnieuw over { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    }
music-source-unavailable = niet beschikbaar
music-sources-empty = Geen aangepaste LX-muziekbronnen gevonden
music-sources-title = Aangepaste LX-muziekbronnen: { $arg0 }
music-stop-eject = Stoppen en uitwerpen
music-stop-eject-done = Afspelen gestopt en muziekplaat uitgeworpen
music-stopped = Gestopt
music-track-details = Nummerdetails
music-unavailable-disc = Niet-beschikbare muziekplaat
music-unavailable-disc-description = Deze MIK-muziekplaat staat niet in de huidige catalogus; hij kan nog steeds worden gestopt en uitgeworpen

## Entertainment · Anniversary

anniversary-accepted = Je doet mee aan de jubileumtrekking. De eerste trekking wordt na 4 online uren buiten AFK vrijgegeven.
anniversary-admin-audit-action-bonus = Bonustrekkingskans voor bouwinzending toegekend
anniversary-admin-audit-action-gift = Evenementprijs geschonken: { $arg0 }
anniversary-admin-audit-action-removal = Eerdere beheerdersdeelname verwijderd en { $arg0 ->
        [one] { $arg0 } prijs
       *[other] { $arg0 } prijzen
    } teruggeboekt.
anniversary-admin-audit-action-unknown = Onbekende actie: { $arg0 }
anniversary-admin-audit-empty = Nog geen beheerauditregels.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Uitvoerder: { $arg0 } ({ $arg1 }); doel: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Jubileumbeheeraudit ({ $arg0 }/{ $arg1 } weergegeven)
anniversary-admin-bonus-cancelled = Beëindigd door cadeaupakket
anniversary-admin-bonus-pending = Toegekend, nog niet getrokken
anniversary-admin-bonus-used = Getrokken
anniversary-admin-boss = Jubileumbeheer · Gebruik { $arg0 } om de voortgang te bekijken
anniversary-admin-commands = Opdrachten: /anniversary winners [pagina] | /anniversary audit [speler] | /anniversary bonus <speler>
anniversary-admin-control = Dynamische regeling: vrijgegeven { $arg0 }; nu beschikbaar { $arg1 }; basiskans { $arg2 }; recent tempo { $arg3 }
anniversary-admin-draw-bonus = Bonus voor bouwinzending
anniversary-admin-draw-detail = { $arg0 }: getrokken om { $arg1 }; resultaat { $arg2 }
anniversary-admin-draw-metrics = Kans { $arg0 }; willekeurige waarde { $arg1 }; algoritmeversie { $arg2 }
anniversary-admin-draw-replaced = Vervangen door cadeaupakket; voorraad hersteld
anniversary-admin-draw-round = Normale ronde { $arg0 }
anniversary-admin-event = Evenementstatus: { $arg0 }; periode: { $arg1 }
anniversary-admin-excluded = Jubileumbeheerders kunnen niet deelnemen aan de trekking. Het beheeroverzicht is geopend.
anniversary-admin-none = Geen
anniversary-admin-opportunities = Uitgevoerde trekkingen: { $arg0 }; nu klaar: { $arg1 }; verdiende kansen: { $arg2 }; gewonnen/verloren: { $arg3 }
anniversary-admin-participants = Deelnemers: { $arg0 }
anniversary-admin-player-bag = Definitieve evenemententas: { $arg0 }
anniversary-admin-player-bonus = Bouwbonus: { $arg0 }; toegekend: { $arg1 }; door: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Details van trekkingskansen
anniversary-admin-player-not-found = Evenementdeelnemer niet gevonden: { $arg0 }
anniversary-admin-player-outcome = Gewonnen: { $arg0 }; verloren: { $arg1 }; trekkingen uitgevoerd: { $arg2 }; bouwbonus: { $arg3 }
anniversary-admin-player-progress = Deelgenomen: { $arg0 }; geldige onlinetijd: { $arg1 }; normale trekkingen uitgevoerd: { $arg2 }; normale trekkingen nu klaar: { $arg3 }
anniversary-admin-player-title = Deelnemersaudit: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Actief
anniversary-admin-state-ended = Beëindigd
anniversary-admin-state-scheduled = Gepland
anniversary-admin-stock = Resterende voorraad: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | totaal { $arg4 }
anniversary-admin-target-excluded = { $arg0 } is jubileumbeheerder en kan geen trekkingskans ontvangen.
anniversary-admin-title = Beheeroverzicht vijfjarig jubileum
anniversary-admin-winners-empty = Er zijn nog geen daadwerkelijk getrokken winnaars.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · getrokken om { $arg3 }
anniversary-admin-winners-title = Prijsuitgiftelijst (pagina { $arg0 }/{ $arg1 }, { $arg2 ->
        [one] { $arg2 } prijs
       *[other] { $arg2 } prijzen
    })
anniversary-bag-empty = Nog geen prijzen gewonnen
anniversary-bag-item-locked = De evenemententas is alleen-lezen
anniversary-bag-item-name = Jubileumprijs · { $arg0 }
anniversary-bag-not-participating = Je doet nog niet mee. Gebruik eerst { $arg0 }.
anniversary-bag-title = Evenemententas voor het vijfjarig jubileum
anniversary-bag-updated = Je prijs staat in de evenemententas. Gebruik { $arg0 } om deze te bekijken.
anniversary-bonus-already-granted = { $arg0 } heeft de bonus voor bouwinzendingen al ontvangen en kan deze niet opnieuw krijgen.
anniversary-bonus-boss-ready = Bonustrekking voor bouwinzendingen klaar · Gebruik { $arg0 } om te trekken
anniversary-bonus-broadcast-won = [Jubileumtrekking] { $arg0 } won { $arg1 } via de bonus voor bouwinzendingen!
anniversary-bonus-draw-not-won = Bonustrekking voor bouwinzendingen: deze keer geen prijs.
anniversary-bonus-draw-sold-out = Bonustrekking voor bouwinzendingen: geen prijs, omdat alle prijzen op zijn.
anniversary-bonus-draw-won = Bonustrekking voor bouwinzendingen: gefeliciteerd, je hebt { $arg0 } gewonnen!
anniversary-bonus-gift-pack-final = { $arg0 } heeft al het cadeaupakket gewonnen en kan geen bonustrekking ontvangen.
anniversary-bonus-grant-not-active = De bonus voor bouwinzendingen kan buiten de evenementperiode niet worden toegekend.
anniversary-bonus-grant-success = Bonus voor bouwinzendingen toegekend aan { $arg0 }.
anniversary-bonus-granted = Je hebt één extra trekking gekregen voor geaccepteerde bouwinzendingen. Gebruik /anniversary om te trekken.
anniversary-bonus-participant-not-found = Geen deelnemer met de naam { $arg0 } gevonden
anniversary-bonus-status-ready = Bonus voor bouwinzendingen: één extra trekking is klaar.
anniversary-bonus-status-used = Bonus voor bouwinzendingen: de extra trekking is gebruikt.
anniversary-boss-completed = Jubileumtrekking · Beide rondes voltooid
anniversary-boss-gift-pack-final = Cadeaupakket gewonnen · Gebruik { $arg0 } om de evenemententas te bekijken
anniversary-boss-invite = Trekking vijfjarig jubileum · Gebruik { $arg0 } om mee te doen
anniversary-boss-progress = Jubileumtrekking { $arg0 }/{ $arg1 } · Ronde { $arg2 } over { $arg3 }
anniversary-boss-ready = Ronde { $arg0 } is klaar · Gebruik { $arg1 } om te trekken
anniversary-broadcast-won = [Jubileumtrekking] { $arg0 } won { $arg2 } in ronde { $arg1 }!
anniversary-cancel = Niet nu
anniversary-command-description = Doe mee aan de trekking voor het vijfjarig serverjubileum
anniversary-confirm = Akkoord en deelnemen
anniversary-confirm-hint = Start het tellen van online tijd buiten AFK
anniversary-delivery-note = Geef voor een bestemming op het Chinese vasteland een geldige SF ID op die privacybeschermde verzending ondersteunt. Geef daarbuiten de echte naam van de ontvanger, het volledige adres en een geldig telefoonnummer op. Betaal het verschuldigde verzenddeel, anders vervalt de prijs.
anniversary-dialog-prizes = Prijzen: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } en 5 { $arg3 }
anniversary-dialog-time = Evenementperiode: { $arg0 }
anniversary-dialog-title = Regels van de jubileumtrekking
anniversary-draw-not-won = Ronde { $arg0 }: deze keer geen prijs.
anniversary-draw-sold-out = Ronde { $arg0 }: geen prijs, omdat alle prijzen op zijn.
anniversary-draw-won = Ronde { $arg0 }: gefeliciteerd, je hebt { $arg1 } gewonnen!
anniversary-ended = De trekking voor het vijfjarig jubileum is afgelopen
anniversary-ended-results = Jouw trekkingsresultaten: { $arg0 }
anniversary-gift-pack-final = Het cadeaupakket is je uiteindelijke prijs. Het verving { $arg0 ->
        [one] { $arg0 } andere prijs
       *[other] { $arg0 } andere prijzen
    } en herstelde de voorraad; alle resterende trekkingen zijn beëindigd.
anniversary-gift-prize-not-owned = Je bezit deze evenementprijs niet: { $arg0 }
anniversary-gift-received = { $arg0 } heeft je { $arg1 } geschonken. De prijs staat nu in je evenemententas.
anniversary-gift-self = Je kunt geen prijs aan jezelf schenken.
anniversary-gift-success = { $arg0 } is aan { $arg1 } geschonken.
anniversary-gift-usage = Gebruik: { $arg0 }
anniversary-not-active = Het jubileumevenement is nu niet actief
anniversary-not-started = Het jubileumevenement is nog niet begonnen. Periode: { $arg0 }
anniversary-prize-badge = groepsfotobadge voor het vijfjarig jubileum
anniversary-prize-gift-pack = cadeaupakket voor het vijfjarig jubileum
anniversary-prize-keychain = sleutelhanger met logo
anniversary-prize-mug = mok met logo
anniversary-prize-not-won = geen prijs
anniversary-prize-sold-out = lege prijzenpot
anniversary-rule-1 = 1. Na deelname mag je elke 4 uur trekken, in totaal twee keer. Prijzen gaan naar de virtuele evenemententas, maximaal één per soort. Het cadeaupakket blijft als enige prijs over en beëindigt alle resterende trekkingen.
anniversary-rule-2 = 2. Tijd waarin je AFK bent telt niet mee voor de trekking. Het omzeilen van de AFK-detectie is op geen enkele wijze toegestaan.
anniversary-rule-3 = 3. De server betaalt 80% van de totale verzendkosten van fysieke prijzen, met een maximum van 50 CNY; de winnaar betaalt het volledige resterende bedrag nadat deze bijdrage is afgetrokken.
anniversary-rule-4 = 4. Voor bestemmingen op het Chinese vasteland is een geldige SF ID vereist die privacybeschermde verzending ondersteunt. Voor bestemmingen buiten het Chinese vasteland zijn de echte naam van de ontvanger, het volledige adres en een geldig telefoonnummer vereist. Bij ontbrekende gegevens of niet-betaling van het verschuldigde verzenddeel vervalt de prijs.
anniversary-rule-5 = 5. Meerdere deelnames met alternatieve accounts zijn verboden en leiden tot uitsluiting.
anniversary-rule-6 = 6. Door deel te nemen ga je akkoord met alle bovenstaande regels.
anniversary-rule-7 = 7. Door de website geaccepteerde bouwwerken tellen voor 0,2 punt bij overnames, 0,5 bij bewerkingen en 1 bij originelen; vóór het evenement geaccepteerde werken worden met 0,8 vermenigvuldigd en gezamenlijke werken met 1,5 maal jouw bijdragepercentage. Vanaf 2 punten krijg je één extra trekking, handmatig toegekend door een beheerder.
anniversary-second-round-hint = De tweede trekking wordt na in totaal 8 online uren buiten AFK vrijgegeven.
anniversary-status-completed = Je hebt beide trekkingsrondes voltooid.
anniversary-status-gift-pack-final = Je hebt het cadeaupakket gewonnen. Je uiteindelijke prijs staat vast en alle resterende trekkingen zijn beëindigd.
anniversary-status-progress = Voltooid: { $arg0 }/{ $arg1 } rondes; geldige online tijd: { $arg2 }
anniversary-status-remaining = Voor ronde { $arg0 } is nog { $arg1 } online tijd buiten AFK nodig.
anniversary-status-results = Trekkingsresultaten: { $arg0 }
anniversary-status-title = Voortgang jubileumtrekking

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } is al verbannen
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Banbericht]
ban-command-description = Verban een speler met een vast ernstniveau
ban-database-error = Actie op de blokkeringsdatabase mislukt. Bekijk het serverlogboek.
ban-dialog-back = Terug
ban-dialog-cancel = Annuleren
ban-dialog-confirm-action = Speler verbannen
ban-dialog-confirm-severity = Ernst
ban-dialog-confirm-title = Verbanning bevestigen
ban-dialog-never-joined-confirm-action = Toch verbannen
ban-dialog-never-joined-title = Speler is nooit toegetreden
ban-dialog-never-joined-warning = { $arg0 } is nooit op deze server geweest. Controleer de spelersnaam om te voorkomen dat je de verkeerde speler verbant.
ban-dialog-next = Volgende
ban-dialog-player-input = Spelersnaam handmatig invoeren
ban-dialog-player-manual = Handmatige invoer
ban-dialog-player-online = Spelers die nu online zijn
ban-dialog-select-title = Speler selecteren
ban-dialog-severity-title = Ernst selecteren
ban-history-empty = Geen blokkeringsgeschiedenis voor { $arg0 }.
ban-history-title = Blokkeringsgeschiedenis: { $arg0 }
ban-invalid-player = Ongeldige spelersnaam
ban-invalid-severity = Ongeldige ernst van verbanning
ban-kick-appeal = Maak bezwaar in QQ-groep 1138627074 als dit volgens jou een fout is
ban-kick-server = MIK
ban-kick-title = Je bent verbannen van deze server
ban-not-banned = { $arg0 } is momenteel niet geblokkeerd.
ban-pardon-reason-required = Een reden voor opheffing is verplicht
ban-permanent = Permanent
ban-reason-required = Een reden voor de verbanning is verplicht
ban-self = Je kunt jezelf niet verbannen
ban-severity-excusable = Verschoonbaar (30 dagen)
ban-severity-extreme = Extreme overtreding (1500 dagen)
ban-severity-minor = Lichte overtreding (150 dagen)
ban-severity-severe = Ernstige overtreding (360 dagen)
ban-severity-warning = Waarschuwing (3 dagen)
ban-status-active = Actief
ban-status-expired = Verlopen
ban-status-revoked = Opgeheven
ban-success = { $arg0 } is verbannen tot { $arg1 }
ban-sync-success = Blokkeringen gesynchroniseerd: actief { $arg0 } · historische vermeldingen { $arg1 }
ban-system-source = Systeem
ban-unban-success = Blokkering van { $arg0 } opgeheven
ban-unknown-reason = Geen reden opgegeven
ban-update-success = Blokkeringsveld van { $arg0 } bijgewerkt: { $arg1 }
banlist-command-description = Bekijk verbannen spelers
banlist-copy = Klik om de spelersnaam te kopiëren
banlist-empty = Er zijn geen verbannen spelers
banlist-expires = Verloopt
banlist-invalid-page = Ongeldige pagina. Totaal: { $arg0 ->
        [one] { $arg0 } pagina
       *[other] { $arg0 } pagina’s
    }.
banlist-next = Volgende
banlist-page = Pagina { $arg0 }/{ $arg1 } · { $arg2 ->
        [one] { $arg2 } vermelding
       *[other] { $arg2 } vermeldingen
    }
banlist-player = Speler
banlist-previous = Vorige
banlist-reason = Reden
banlist-source = { role-moderator }
banlist-title = Banlijst
maintenance-command-description = Schakel de onderhoudsmodus om of bekijk de status
maintenance-set = Onderhoudsmodus { $arg0 }
maintenance-state-disabled = uitgeschakeld
maintenance-state-enabled = ingeschakeld
maintenance-status = Onderhoudsmodus: { $arg0 }
maintenance-unchanged = De onderhoudsmodus is al { $arg0 }
restriction-entity-too-large-mm = <red>De entiteitsgrootte overschrijdt de veiligheidslimiet van de server</red>
restriction-foreign-uuid-mm = <red>Je kunt UUID's alleen gebruiken voor wezens die je hebt getemd</red>
restriction-no-kill-e-mm = <red>Het commando <white>/kill @e</white> is niet toegestaan</red>
restriction-no-newline-mm = <red>Berichten mogen geen regeleinden bevatten</red>
restriction-no-run-command-mm = <red>Berichten mogen spelers niet misleiden om commando's uit te voeren</red>
restriction-no-selector-mm = <red>Je hebt geen toestemming om doelselectoren <white>@e @a @r @p @n</white> te gebruiken</red>
restriction-other-player-name-mm = <red>Je hebt geen toestemming om namen van andere spelers in commando's te vermelden</red>
tpsbar-command-description = Toon of verberg de TPS-balk
tpsbar-hidden = TPS-balk verborgen
tpsbar-shown = TPS-balk getoond
unban-command-description = Speler deblokkeren en de geschiedenis bewaren
whitelist-already-whitelisted = { $arg0 } staat al op de witte lijst
whitelist-invalid-name = Ongeldige spelersnaam
whitelist-kick-apply-rich = Vraag toegang tot de witte lijst aan op { $site }
whitelist-kick-title = Je staat niet op de witte lijst
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = Tijdelijk aan de witte lijst toegevoegd: { $arg0 } voor { $arg1 }
whitelist-temp-added-rich = Tijdelijk aan de witte lijst toegevoegd: { $player } voor { $duration }
whitelist-temp-command-description = Voeg een tijdelijke vermelding van 1 uur aan de witte lijst toe
whitelist-temp-duration = 1 uur
whitelist-temp-usage-desc = Voeg een tijdelijke vermelding van 1 uur aan de witte lijst toe

## Shell · Menus, server information and scripting

main-afk-current-afk = Momenteel AFK
main-afk-current-online = Momenteel online
main-afk-status = AFK-status
main-announcements = Aankondigingen
main-home = Thuislocaties
main-language = Taal
main-language-auto = Automatisch
main-links = Links
main-map = Webkaart
main-menu-title = Hoofdmenu
main-music = Muziek
main-name-tag = Naamlabels
main-profile = Profiel
main-pvp = PvP
main-settings = Instellingen
main-website = Website
main-wiki = Wiki
skript-command-description = Scriptondersteuning van de server bekijken
skript-info-ai-option = Wil je het niet zelf schrijven? Geef deze prompt aan een AI:
skript-info-copy = [AI-prompt kopiëren]
skript-info-copy-hover = Prompt met versies, documentatielinks en veiligheidsregels kopiëren
skript-info-documentation = Syntax- en API-documentatie
skript-info-review = Scripts worden pas geladen na controle en testen door een beheerder
skript-info-supported = Beschikbaar: { $arg0 }
skript-info-title = Serverscripts
skript-info-workflow = Schrijf en controleer met de documentatie een volledig .sk-bestand en stuur het naar een beheerder
stat-client-version = Clientversie
stat-first-joined = Eerste keer online
stat-location = Locatie
stat-ping = Vertraging
stat-play-time = Speeltijd
stat-role = Rol
stat-world = Wereld
weblogin-command-description = Bevestig een Mik Web-inlogcode
weblogin-confirmed = Mik Web-login bevestigd. Ga terug naar je browser om door te gaan.
weblogin-invalid-code = Ongeldige Mik Web-inlogcode
