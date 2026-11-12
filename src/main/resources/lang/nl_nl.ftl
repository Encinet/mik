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
cancel = Annuleren
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

interface-fov-apply = FOV toepassen
interface-fov-custom = Aangepast
interface-fov-description = Vul de FOV uit je spelinstellingen in voor de menupositie.
interface-fov-input = Client-FOV: geheel getal van 30 tot 110
interface-fov-invalid = Voer een geheel getal van 30 tot 110 in.
interface-fov-title = Beeldveld (FOV)
interface-reduced-motion = Beweging verminderen
interface-reduced-motion-hint = Vermindert beweging en schommeling van het menu.
interface-scale-adjust-hint = Scroll boven een percentage om het aan te passen.
interface-scale-layout-title = Menugrootte
interface-scale-menu-title = Interface en tekst
interface-scale-preview = Voorbeeld Aa 123
interface-scale-reset = Terug naar 100%
interface-scale-text-title = Tekstgrootte
interface-theme-apply = Kleuren opslaan
interface-theme-charcoal = Houtskool
interface-theme-color = Kleur (#RRGGBB)
interface-theme-custom = Aangepast
interface-theme-custom-hint = Kopieer alle huidige waarden naar een zelfstandig aangepast thema.
interface-theme-edit = Kleuren bewerken
interface-theme-forest = Bos
interface-theme-hint = Wordt direct alleen op jouw menu’s toegepast.
interface-theme-invalid = Voer een #RRGGBB-kleur en een dekking van 0 tot 100 in.
interface-theme-opacity = Dekking (0–100%)
interface-theme-role-accent = Accentkleur
interface-theme-role-button = Knopachtergrond
interface-theme-role-button-disabled = Uitgeschakelde knop
interface-theme-role-button-hovered = Aangewezen knop
interface-theme-role-button-pressed = Ingedrukte knop
interface-theme-role-button-selected = Geselecteerde knop
interface-theme-role-input = Invoerachtergrond
interface-theme-role-sidebar = Zijbalkachtergrond
interface-theme-role-toolbar = Werkbalkachtergrond
interface-theme-role-tooltip = Hintachtergrond
interface-theme-role-window = Vensterachtergrond
interface-theme-title = Menuthema
interface-theme-warm = Warm
menu-collapse = Inklappen
menu-expand = Uitklappen
menu-finish-editing = Rond eerst het bewerken af.
menu-input-clear = Leegmaken
menu-input-done = Gereed
menu-input-empty = (leeg)
menu-input-single-line = Dit veld staat slechts één regel toe.
menu-input-too-long = Lengte: { $arg0 } / { $arg1 }. Kort de tekst in voordat je bevestigt.
menu-query-cancel = Annuleren
menu-query-hint-rich = Druk op { $chatKey } om te typen…
menu-query-single-line = Slechts één regel toegestaan. Klik op het veld om opnieuw te proberen.
menu-query-too-long = Lengte: { $arg0 } / { $arg1 }. Klik op het veld om opnieuw te proberen.
menu-rhythm-game-display = Speelt ritmespel
menu-search-empty = Geen resultaten
menu-search-failed = Zoeken mislukt
menu-search-limit = Getoond: { $arg0 } / { $arg1 }. Verfijn de zoekopdracht.
menu-search-loading = Zoeken…
menu-search-retry = Opnieuw proberen
menu-usage-display-mm = <aqua><bold>In menu</bold></aqua>
url-dialog-confirm = Openen
url-dialog-hint = De client vraagt nogmaals om bevestiging om de link te openen.
url-dialog-question-rich = { $label } openen?
url-dialog-title = Openen: { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Aanbevolen mods: { $mods }. Klik op een naam om te downloaden.
client-tip-axiom-flight-speed-mm = <white>Houd de <yellow>linker Alt-toets</yellow> ingedrukt om het <aqua>Axiom</aqua>-menu te openen en sleep de verticale schuifregelaar rechts om je vliegsnelheid aan te passen. Je kunt ook <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click> gebruiken.</white>
client-tip-axiom-intro-rich = Met { $mod } kun je bouwwerken kopiëren en plakken, terrein vormgeven en weergave-entiteiten bewerken. Klik op de naam om de mod te downloaden.
client-version-behind-mm = <white>Je clientversie <yellow>{ $clientVersion }</yellow> is ouder dan de serverversie <green>{ $serverVersion }</green>. Een update wordt aangeraden.</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> speler in de buurt is <gold>onzichtbaar</gold>
       *[other] <gold>{ $count }</gold> spelers in de buurt zijn <gold>onzichtbaar</gold>
    }</yellow> <gray>·</gray> <gray>dichtstbijzijnde: ongeveer { $distance ->
        [one] { $distance } blok
       *[other] { $distance } blokken
    } afstand</gray>
player-bedrock-label = Bedrock
player-web-label = Web
presence-join-templates =
    .item-1 = Dingdong! { $player } is online!
    .item-2 = Hoera, daar is { $player }!
    .item-3 = Hoi hoi, { $player }!
# Broadcast to remaining players; the departing player does not receive this message.
presence-quit-templates =
    .item-1 = { $player } is uitgelogd.
    .item-2 = Dingdong! { $player } is offline
    .item-3 = Online -1. { $player } is uitgelogd
welcome-first-join-mm = <gold><bold>Welkom bij MIK!</bold></gold><newline><gray>Begin op de <click:open_url:'https://mcmik.top'><yellow>website</yellow></click> en de <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>. Gebruik ook <yellow>/menu</yellow> om het hoofdmenu te openen.</gray>
welcome-return-mm = <gold><bold>Welkom terug!</bold></gold><newline><gray>Je laatste bezoek was <yellow>{ $arg0 ->
        [one] { $arg0 } dag
       *[other] { $arg0 } dagen
    } geleden</yellow>. Gebruik <yellow>/menu</yellow> om veelgebruikte functies snel te vinden.</gray>

## Player · Guidance

tip-label = TIP

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


## Player · Account security
password-already-connected = Dit account is al verbonden.
password-already-set = Er is al een wachtwoord ingesteld. Wijzig het met /password change.
password-busy = Te veel pogingen. Wacht even en probeer het opnieuw.
password-change = Wachtwoord wijzigen
password-change-done = Wachtwoord gewijzigd.
password-checking = Accountbeveiliging controleren…
password-command-description = Wachtwoordbeveiliging van je account beheren
password-confirm = Nieuw wachtwoord bevestigen
password-conflict = De accountbeveiliging is gewijzigd. Herhaal de handeling.
password-cooldown = Accountverificatie is tijdelijk beperkt. Probeer het over { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    } opnieuw.
password-current = Huidig wachtwoord
password-denied = Je mag het wachtwoord van dit account niet opnieuw instellen.
password-disabled = Wachtwoord: niet ingesteld
password-enabled = Wachtwoord: ingesteld
password-invalid = Onjuist wachtwoord. Probeer opnieuw.
password-keep-role = Moderators en beheerders kunnen hun wachtwoord niet verwijderen.
password-login = Aanmelden
password-login-expired = Je inlogverificatie is verlopen. Verbind opnieuw met de server.
password-login-hint = Voer het wachtwoord van je account op deze server in. Ben je het vergeten? Vraag het team om het opnieuw in te stellen.
password-mismatch = De nieuwe wachtwoorden komen niet overeen.
password-new = Nieuw wachtwoord
password-nomination-required = Stel een wachtwoord in voordat je solliciteert als moderator: /password set
password-not-set = Er is geen wachtwoord ingesteld. Stel er een in met /password set.
password-remove = Wachtwoord verwijderen
password-remove-done = Wachtwoord verwijderd.
password-remove-question = Na het verwijderen wordt bij het inloggen geen wachtwoord meer gecontroleerd. Voer je huidige wachtwoord in om te bevestigen.
password-required-application = Je kunt je wachtwoord niet verwijderen tijdens een lopende teamkandidatuur of benoeming.
password-required-role = Teamaccounts vereisen een wachtwoord. Vraag een bevoegd teamlid om het opnieuw in te stellen voordat je aanmeldt.
password-reset = Wachtwoord opnieuw instellen
password-reset-console = Wachtwoord van { $arg0 } opnieuw ingesteld: { $arg1 }. Geef het na identiteitscontrole via een vertrouwd kanaal door.
password-reset-done = Wachtwoord van { $arg0 } opnieuw ingesteld. Kopieer het nu en geef het na identiteitscontrole door; opnieuw bekijken is niet mogelijk.
password-reset-question = Wachtwoord van { $arg0 } opnieuw instellen? Het oude vervalt en verbindingen worden gesloten. Bevestig met je eigen wachtwoord.
password-retry = Opnieuw proberen
password-rules = Gebruik { $arg0 }–{ $arg1 } tekens. Spelersnamen en veelgebruikte wachtwoorden zijn niet toegestaan.
password-set = Wachtwoord instellen
password-set-done = Wachtwoord ingesteld.
password-set-first = Wachtwoord instellen vóór sollicitatie
password-storage = Accountbeveiliging is niet beschikbaar. Probeer later opnieuw of neem contact op met de eigenaar.
password-timeout = De wachtwoordcontrole is geannuleerd of verlopen. Verbind opnieuw om het te proberen.
password-tip-set-mm = Stel met <gold><u>/password</u></gold> een <yellow>inlogwachtwoord</yellow> in om je account te beschermen.
password-title = Accountbeveiliging

## Player · Profile and tab list

nametag-clear = Wissen
nametag-clear-hint = Verwijdert eigen waarden; overgenomen stijlen kunnen blijven.
nametag-command-description = Pas naamlabels aan
nametag-confirm = Bevestigen
nametag-conflict = De huidige waarde is gewijzigd. Vergelijk de huidige weergave en bevestig opnieuw om deze te vervangen.
nametag-copy-hint = Stuur de bron van het voor- en achtervoegsel naar de chat
nametag-copy-label = [kopie]
nametag-copy-sent = De stijlbron van { $arg0 } staat in de chat.
nametag-current = Huidige weergave
nametag-edit-ended = Het bewerken is beëindigd. Klik opnieuw op het veld om verder te gaan.
nametag-edit-prompt = Druk op T: { $arg0 } bewerken met MiniMessage (privé).
nametag-finish-field = Bevestig of annuleer eerst het veld dat je bewerkt.
nametag-input-invalid = Ongeldige opmaak. Stuur MiniMessage op één regel of kies Wissen.
nametag-leading-label = Naamvoorvoegsel
nametag-mine = Mijn naamlabel
nametag-next = Volgende
nametag-no-changes = Geen wijzigingen
nametag-no-permission = Leden en beheerders kunnen naamlabels bewerken. Stijlen bekijken blijft mogelijk.
nametag-no-players = Geen spelers hebben een naamstijl ingesteld
nametag-page = Pagina { $arg0 } / { $arg1 }
nametag-players = Spelerstijlen
nametag-preview-label = Voorbeeld
nametag-previous = Vorige
nametag-refresh = Vernieuwen
nametag-saving = Opslaan…
nametag-set-error = Opslaan mislukt: { $arg0 }. Probeer opnieuw; de huidige weergave staat hierboven.
nametag-set-success = ✔ { $arg0 } ingesteld
nametag-styles-failed = Spelerstijlen konden niet worden geladen. Vernieuw om opnieuw te proberen.
nametag-styles-loading = Spelerstijlen laden…
nametag-tip-customize-mm = Pas met <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> het <light_purple>voor- en achtervoegsel van je naam</light_purple> aan.
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
back-tip-return-mm = Gebruik <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> om terug te keren naar je locatie vóór de laatste teleportatie of dood.
back-tip-undo-mm = Gebruik <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> om je laatste <yellow>/back</yellow> ongedaan te maken.
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
fly-speed-limited = Door de verkenningsbelasting is vliegen tijdelijk beperkt tot { $arg0 }x; je instelling blijft { $arg1 }x.
fly-speed-presets = Voorinstellingen
fly-speed-reset = Vliegsnelheid hersteld naar 1x
fly-speed-reset-action = Herstellen
fly-speed-reset-click = Vliegsnelheid herstellen naar 1x
fly-speed-set = Vliegsnelheid ingesteld op { $arg0 }x
fly-state-disabled = Uitgeschakeld
fly-state-enabled = Ingeschakeld
fly-status = Vliegen: { $arg0 } | Snelheid: { $arg1 }x
home-action-create = Thuislocatie maken
home-action-delete = Thuislocatie verwijderen
home-action-teleport = Teleporteren
home-action-update = Locatie bijwerken
home-already-exists = Er bestaat al een thuislocatie met deze naam
home-confirm-delete = Verwijderen bevestigen
home-confirm-delete-detail-rich = Verwijder thuislocatie { $home }
home-confirm-delete-warning = Dit kan niet ongedaan worden gemaakt
home-confirm-update = Wijziging bevestigen
home-confirm-update-detail-rich = Thuislocatie { $home } bijwerken naar deze locatie
home-delete-command-description = Verwijder een thuislocatie
home-deleted-rich = Thuislocatie { $home } is verwijderd
home-distance = Afstand: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    }
home-distance-other-world = andere wereld
home-empty-description = Ga staan waar je een thuislocatie wilt opslaan
home-empty-title = Nog geen thuislocaties
home-invalid-name = Namen van thuislocaties mogen geen < > : bevatten
home-list-rich = Thuislocaties: { $homes }
home-location = Locatie: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = De locatiegegevens van deze thuislocatie zijn ongeldig
home-max-reached = Je kunt maximaal { $arg0 ->
        [one] { $arg0 } thuislocatie
       *[other] { $arg0 } thuislocaties
    } instellen
home-menu-title = Thuislocaties
home-no-homes = Je hebt nog geen thuislocaties ingesteld
home-not-found-rich = Thuislocatie { $home } bestaat niet
home-search-placeholder = Zoek huisnamen…
home-search-empty = Geen overeenkomende thuislocaties
home-set-command-description = Stel een thuislocatie in op je huidige locatie
home-set-rich = Thuislocatie { $home } is ingesteld
home-summary-count = Ingesteld: { $arg0 } / { $arg1 }
home-teleport-failed = Teleportatie mislukt. Probeer het opnieuw
home-teleported-rich = Geteleporteerd naar thuislocatie { $home }
home-tip-create-mm = Sla je locatie op met <green><u><click:suggest_command:'/sethome '>/sethome naam</click></u></green> en keer terug met <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>.
home-tip-return-mm = Open <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>, selecteer een thuis en kies ‘{ home-action-teleport }’.
home-updated-rich = Locatie van thuislocatie { $home } is bijgewerkt
home-usage-book = Commandohulp
home-usage-delhome = /delhome <name>  Verwijder een thuislocatie
home-usage-home = /home <name>  Teleporteer direct
home-usage-name-rule = Namen van thuislocaties mogen geen < > : bevatten
home-usage-sethome = /sethome <name>  Sla je huidige locatie op
home-world = Wereld: { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Maak de laatste /back ongedaan
teleport-accept-command-description = Het openstaande teleportatieverzoek accepteren
teleport-allow = Teleportaties door andere spelers
teleport-bare-entity-selector = Om onbedoelde teleportaties te voorkomen, vereist @e vierkante haken. Gebruik een expliciete selector zoals @e[type=…].
teleport-block-afk = Blokkeren tijdens AFK
teleport-denied = { $player } weigert momenteel teleportaties door andere spelers
teleport-deny-command-description = Het openstaande teleportatieverzoek weigeren
teleport-force-command-description = Teleportatie forceren zonder rekening te houden met spelervoorkeuren (alleen beheerders)
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
tpany-command-description = Teleporteer naar de locatie van een speler, online of offline
tpany-done-offline-rich = Geteleporteerd naar de laatst bekende locatie van { $player }: { $world } { $location }
tpany-done-online-rich = Geteleporteerd naar de huidige locatie van { $player }
tpany-not-found-rich = Locatie van { $player } niet gevonden
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teleporteer naar de locatie van een speler, inclusief offlinecache

## Player · PVP

pvp-admin-menu-title = PvP-beheer
pvp-allow-mounted-damage = Bereden wezens kunnen worden aangevallen
pvp-allow-mounted-damage-desc = Als dit is ingeschakeld, kunnen andere spelers een wezen aanvallen waarop je rijdt als jullie allebei PvP hebben ingeschakeld
pvp-auto-enable-desc = Val een speler met PvP ingeschakeld twee keer achter elkaar aan
pvp-auto-enable-label = Automatisch inschakelen
pvp-auto-enable-warning-actionbar-mm = <yellow>Val nog een keer aan om PvP automatisch in te schakelen</yellow>
pvp-auto-enabled-actionbar-mm = <green>PvP automatisch ingeschakeld</green>
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
pvp-no-permission-mm = <red>Je hebt geen toestemming om de PvP-status van een andere speler te beheren</red>
pvp-player-not-found-mm = <red>Online speler niet gevonden</red>
pvp-protect-mobs = Bescherm je wezens
pvp-protect-mobs-desc = Bescherm wezens die je hebt getemd of waarop je rijdt
pvp-quick-combat-cooldown = Gevechtsblokkering: nog { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    }
pvp-quick-override = Een tijdelijke regel bepaalt PvP. Je kunt je voorkeuren in de instellingen aanpassen.
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
pvp-target-disabled-actionbar-mm = <yellow>Deze speler heeft PvP niet ingeschakeld</yellow>
pvp-target-menu-title = PvP-instellingen - { $arg0 }
pvp-tip-protection-mm = Schakel je PVP in of uit met <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple>. <green>Beide spelers moeten het inschakelen</green> om te vechten.
pvp-toggled-off-mm = <gray>PvP uitgeschakeld</gray> <dark_gray>|</dark_gray> <gray>Andere spelers kunnen je niet aanvallen</gray>
pvp-toggled-on-mm = <green>PvP ingeschakeld</green> <dark_gray>|</dark_gray> <gray>Je kunt nu vechten met andere spelers met PvP ingeschakeld</gray>

## Player · Effects

effect-cleanup-all = [Alle effecten verwijderen]
effect-cleanup-blocked = Je kunt effecten alleen verwijderen in Creatieve of Toeschouwermodus buiten een PVP-gevecht.
effect-cleanup-expired = Dit verwijderverzoek is verlopen.
effect-cleanup-negative = [Negatieve effecten verwijderen]
effect-cleanup-prompt-rich = Je hebt negatieve effecten gekregen door { $source }. Verwijderen?
effect-cleanup-result = { $count ->
        [one] { $count } effect verwijderd.
       *[other] { $count } effecten verwijderd.
    }
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] een aanhoudende drankwolk
        [ARROW] een pijl
        [ATTACK] een aanval
        [COMMAND] een commando
        [FOOD] voedsel
        [PLUGIN] een plug-in
        [POTION_DRINK] het drinken van een drank
        [POTION_SPLASH] een werpdrank
        [WARDEN] een warden
        [WITHER_ROSE] een witherroos
       *[OTHER] een onbekende oorzaak
    }

## Player · Trash

trash-clear = Legen
trash-clear-hint = Klik om alle voorwerpen direct te verwijderen.
    Ze worden ook automatisch { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    } na het sluiten verwijderd.
trash-command-description = Open de prullenbak
trash-sort = Sorteren
trash-sort-hint = Gelijke stapels samenvoegen en voorwerpen op type sorteren
trash-tip-dispose-mm = Open de <yellow>prullenbak</yellow> met <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> en stop er ongewenste voorwerpen in.
trash-title = Prullenbak

## Player · Utilities

hat-command-description = Draag het voorwerp in je hoofdhand
hat-empty-hand-mm = <red>Geen voorwerp in je hoofdhand</red> <gray>Houd een blok of voorwerp vast en gebruik /hat</gray>
hat-success-mm = <green>Je draagt nu het voorwerp uit je hoofdhand</green> <dark_gray>|</dark_gray> <gray>Je hoofdhand en helmvak zijn verwisseld</gray>
hat-tip-wear-mm = Draag het voorwerp in je <yellow>hoofdhand</yellow> op je hoofd met <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple>.
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
removeitems-tip-usage-mm = Verwijder met <aqua><u><click:suggest_command:'/rmitems '>/rmitems [straal]</click></u></aqua> <yellow>voorwerpen op de grond</yellow> in de buurt.
self-kick-command-description = Jezelf van de server kicken
self-kick-reason-too-long = De reden mag niet langer zijn dan { $arg0 ->
        [one] { $arg0 } teken
       *[other] { $arg0 } tekens
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Je hebt de server op eigen verzoek verlaten. Reden: { $reason }
       *[third] { $player } heeft de server op eigen verzoek verlaten. Reden: { $reason }
    }
    .item-1 = { $player } heeft de server op eigen verzoek verlaten. Reden: { $reason }
    .item-2 = { $player } is uitgelogd met een bericht: { $reason }
    .item-3 = { $player } heeft de server verlaten met een bericht: { $reason }
    .item-4 = { $player } neemt afscheid met een berichtje: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Je hebt de server op eigen verzoek verlaten.
       *[third] { $player } heeft de server op eigen verzoek verlaten.
    }
    .item-1 = { $player } heeft de server op eigen verzoek verlaten.
    .item-2 = { $player } is uitgelogd.
    .item-3 = Dingdong! { $player } is offline
    .item-4 = Online -1. { $player } is uitgelogd

## Community · Chat and mentions

chat-content-audio = [Audio]
chat-content-card = [Kaart]
chat-content-contact = [Contactpersoon]
chat-content-emote = [Actie]
chat-content-face = [Emoji]
chat-content-file = [Bestand]
chat-content-history = [Chatgeschiedenis]
chat-content-image = [Afbeelding]
chat-content-location = [Locatie]
chat-content-message = [Bericht]
chat-content-multiple = [Meerdere berichten]
chat-content-music = [Muziek]
chat-content-notice = [Melding]
chat-content-reply = [Geciteerd bericht]
chat-content-sticker = [Sticker]
chat-content-video = [Video]
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
chat-private-target-offline = Je gesprekspartner is offline; wacht of kies een ander kanaal
chat-private-tip-channel-mm = Gebruik <aqua><u><click:suggest_command:'/msg '>/msg speler</click></u></aqua> om naar privéchat te schakelen en <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> om terug te keren naar de openbare chat.
chat-public-command-description = Schakel naar de openbare chat
chat-public-enter = Overgeschakeld naar openbare chat
chat-reply-command-description = Beantwoord je laatste privéchatpartner
chat-settings-menu-title = Chatinstellingen
chat-social-account-label = Platformaccount
chat-social-bound-player-label = Gekoppelde speler
chat-social-name-label = Sociale naam
chat-social-unbound = Niet gekoppeld aan een Minecraft-speler
chat-staff-command-description = Schakel naar de teamchat
chat-staff-label = Team
chat-staff-permission-missing = Geen toestemming voor teamchat; teruggeschakeld naar openbare chat
mention-action-bar = Werkbalktitel
mention-action-bar-text = { $sender } heeft je vermeld
mention-alerts = Vermeldingsmeldingen
mention-menu-title = Vermeldingsmeldingen
mention-mute-afk = Dempen tijdens AFK
mention-sound = Meldingsgeluid
staffchat-enter = Teamchat betreden

## Community · Announcements

announcement-count = { $arg0 ->
        [one] { $arg0 } aankondiging
       *[other] { $arg0 } aankondigingen
    }
announcement-empty = Geen aankondigingen
announcement-first-page = Nieuwste berichten bereikt
announcement-last-page = Oudste berichten bereikt
announcement-latest = Nieuwste bericht
announcement-list-newer = Nieuwere groep
announcement-list-older = Oudere groep
announcement-more-count = { $arg0 } meer niet getoond
announcement-new-count = { $arg0 } nieuw
announcement-newer = Nieuwere berichten
announcement-next-page = Volgende pagina
announcement-older = Oudere berichten
announcement-page = Pagina { $arg0 } / { $arg1 }
announcement-previous-page = Vorige pagina
announcement-reload = Aankondigingen herladen
announcement-reload-command-description = Aankondigingsbestand herladen
announcement-reload-failed = Kon aankondigingen niet herladen; de vorige blijven beschikbaar
announcement-reloaded = Aankondigingen herladen
announcement-unread-count = { $arg0 } ongelezen
announcement-updated = Aankondigingen bijgewerkt
announcement-view-all = Alle aankondigingen bekijken
announcement-view-command-description = Serveraankondigingen bekijken

## Community · Identity and social platforms

identity-admission-bind-rich = { $platform }: { $command }
identity-admission-instructions = Kies een platform en stuur de bijbehorende opdracht naar de communitybot:
identity-admission-pending = Je bestaande koppeling kan nog niet worden gecontroleerd. Probeer het later opnieuw of koppel een account van een ander platform.
identity-admission-reconnect = Maak na het koppelen opnieuw verbinding met de server.
identity-admission-required = Koppel een communityaccount om de server te betreden.
identity-admission-revoked = Je hebt geen geldige communitykoppeling meer. Maak opnieuw verbinding voor de instructies.
identity-admin-binding-not-found = De geselecteerde koppeling bestaat niet meer.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = De geselecteerde { $arg0}-koppeling van { $arg1 } is verwijderd.
identity-admin-command-description = Externe identiteitskoppelingen bekijken of intrekken
identity-admin-help = /bindadmin lookup <speler-of-UUID>
    /bindadmin unlink <binding-id> confirm
identity-admin-lookup-empty = Voor die speler zijn geen koppelingen gevonden.
identity-admin-lookup-title = Koppelingen van { $arg0 }:
identity-binding-community = Community #{ $arg0 }
identity-binding-info = Gekoppeld op: { $arg0 }
    Koppelings-ID: { $arg1 }
identity-binding-label-rich = { $kind ->
    [community] { $platform } · { $account } · { $community }
   *[account] { $platform } · { $account }
    }
identity-binding-not-owned = De geselecteerde koppeling bestaat niet meer of is niet van jou.
identity-binding-reference-rich = { $label } · #{ $reference }
identity-binding-unlinked = De geselecteerde { $arg0}-koppeling is verwijderd.
identity-binding-unnamed = Naamloos account
identity-bindings-empty = Je hebt geen externe platformaccounts gekoppeld.
identity-bindings-title = Gekoppelde accounts
identity-code-cancelled = De ongebruikte koppelcode voor { $arg0 } is geannuleerd.
identity-code-copy = Klik om de volledige koppelopdracht te kopiëren
identity-code-expiry = Geldigheidsduur code: { $arg0 ->
    [one] { $arg0 } minuut
   *[other] { $arg0 } minuten
    }
identity-code-not-pending = Er is geen ongebruikte koppelcode voor { $arg0 }.
identity-code-redeem-rich = Stuur naar de bot van je { $platform }-community: { $command }
identity-command-description = Minecraft aan een externe platformidentiteit koppelen
identity-community-join-rich = Word lid van een community: { $link }
identity-help = Opdrachten voor identiteitskoppeling:
    /bind qq — snel een QQ-koppelopdracht maken
    /bind <platform> — een eenmalige code maken
    /bind list — bestaande koppelingen tonen
    /bind cancel <platform> — een ongebruikte code annuleren
    /bind unlink — Kies één koppeling om te verwijderen
identity-notify-linked = Je { $arg0}-identiteit is gekoppeld. Gebruik /bind list om je koppelingen te bekijken en te beheren.
identity-notify-unlinked = Je { $arg0}-identiteitskoppeling is verwijderd.
identity-platform-none = geen
identity-platforms = Ondersteunde platforms: { $arg0 }
identity-storage-error = De databasebewerking voor identiteitskoppeling is mislukt. Neem contact op met een beheerder.
identity-unavailable = Identiteiten koppelen is momenteel niet beschikbaar. Neem contact op met een beheerder.
identity-unlink-action = [Ontkoppelen]
identity-unlink-hint = Verwijder alleen deze geselecteerde koppeling.
identity-unsupported-platform = Niet-ondersteund platform: { $arg0 }
social-admin-conversations-empty = Geen gesprekken waargenomen voor { $arg0 }.
social-admin-conversations-title = Waargenomen gesprekken voor { $arg0 }:
social-admin-help = /social platforms · /social status [platform] · /social reload [platform] · /social conversations <platform>
social-admin-metrics = Inkomend { $arg0 } / herpogingen { $arg1 } · uitgaand { $arg2 } / tegendruk { $arg3 } · fouten { $arg4 }
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Sociaal platform { $arg0 } is opnieuw geladen.
social-admin-reloaded-all = Alle sociale platforms zijn opnieuw geladen.
social-admin-status = { $arg0 } · { $arg1 } · generatie { $arg2 } · gesprekken { $arg3 }
social-admin-title = Sociale platforms
social-admin-unknown = Onbekend sociaal platform: { $arg0 }
social-binding-already-linked = Je account is al gekoppeld aan { $arg0 }. Stuur { $arg1 } om je profiel te bekijken.
social-binding-confirmation-expired = Deze bevestiging is verlopen of het gekoppelde account is gewijzigd. Vraag een nieuwe bevestiging aan:
social-binding-code-invalid = Deze code is ongeldig of verlopen. Verbind opnieuw of voer { $arg0 } uit in het spel.
social-binding-external-in-use = Deze identiteit is al gekoppeld aan een andere Minecraft-speler.
social-binding-help = Verbind met de Minecraft-server en stuur hier de volledige koppelopdracht van het aanmeldscherm. Als je al speelt, haal je die op met { $arg0 }.
social-binding-identity-missing = Dit bericht bevat geen betrouwbare ledenidentiteit; koppelen is daarom niet mogelijk.
social-binding-link-completed = Je account is gekoppeld. Verbind opnieuw om verder in te loggen.
social-binding-linked = Je spelaccount { $arg0 } is gekoppeld. Verbind opnieuw om verder in te loggen.
social-binding-membership-required = Word lid van een aangesloten community op dit platform en probeer je account opnieuw te koppelen.
social-binding-membership-unavailable = Het communitylidmaatschap kan momenteel niet worden gecontroleerd. Probeer het later opnieuw of neem contact op met de serverbeheerder.
social-binding-not-bound = Je hebt nog geen spelaccount gekoppeld.
social-binding-platform-mismatch = Deze verificatiecode is voor een ander platform uitgegeven.
social-binding-player-scope-in-use = Deze Minecraft-speler heeft al een andere identiteit gekoppeld binnen hetzelfde platformaccountbereik.
social-binding-rate-limited = Te veel mislukte pogingen. Probeer het over ongeveer { $arg0 ->
        [one] { $arg0 } minuut
       *[other] { $arg0 } minuten
    } opnieuw.
social-binding-unavailable = Identiteitskoppeling is tijdelijk niet beschikbaar.
social-binding-unlink-completed = Je account is losgekoppeld. Zonder andere geldige communitykoppeling moet je opnieuw een account koppelen voordat je kunt deelnemen.
social-binding-unlink-prompt = Dit platformaccount wordt losgekoppeld van { $arg0 }. Zonder andere geldige communitykoppeling verlies je servertoegang en word je losgekoppeld als je online bent. Stuur ter bevestiging:
social-binding-unlinked = Ontkoppeld van Minecraft-speler { $arg0 }.
social-command-description = Sociaalplatformadapters beheren
social-command-invalid-arguments = De argumenten zijn ongeldig. Gebruik:
social-command-usage = Deze opdracht heeft geen argumenten nodig. Stuur:
social-crypto-command-description = Verificatie van versleutelde Matrix-apparaten beheren
social-crypto-comparison-not-ready = De SAS-vergelijking is nog niet klaar.
social-crypto-device = { $arg0 } / { $arg1 }
    Ed25519: { $arg2 }
social-crypto-failed = Apparaatverificatie mislukt. Bekijk het serverlogboek.
social-crypto-no-active = Er is geen apparaatverificatie bezig.
social-crypto-not-accepting = Deze verificatie wacht niet op acceptatie.
social-crypto-not-running = Matrix is niet actief.
social-crypto-requested = Verificatie aangevraagd: { $arg0 }
social-crypto-state = { $arg0 ->
    [initializing] Initialiseren
    [verified] Geverifieerd
    [unverified] Niet geverifieerd
    [no_cross_signing] Kruisondertekening uitgeschakeld
    [requested] Aangevraagd
    [ready] Gereed
    [started] Gestart
    [comparison] Wacht op vergelijking
    [waiting] Wacht op voltooiing
    [done] Voltooid
    [cancelled] Geannuleerd
   *[unknown] Onbekend
    [valid] Geldige sleutel
    [cross_signed] Kruisondertekend
    [not_cross_signed] Niet kruisondertekend
    [blocked] Geblokkeerd
    [invalid] Ongeldige sleutel
    }
social-crypto-status = Verificatie: { $arg0 }
    Versie van de sleutelback-up: { $arg1 }
social-crypto-target = { $arg0 } / { $arg1 }: { $arg2 } · { $arg3 }
social-crypto-transaction = { $arg0 }: { $arg1 } / { $arg2 } · { $arg3 }
social-crypto-transaction-required = Geef de volledige ID van de huidige verificatietransactie op.
social-crypto-unavailable = Niet beschikbaar
social-crypto-updated = Verificatie bijgewerkt; bekijk matrixcrypto status.
social-crypto-usage = matrixcrypto status [gebruikers-id apparaat-id]
    matrixcrypto verify <gebruikers-id> <apparaat-id>
    matrixcrypto accept|confirm|cancel <transactie-id>
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
social-link-command-parameters = <code>
social-online-command-description = Online spelers tonen
social-profile-afk = Online · AFK
social-profile-avatar-alt = Minecraft-gezicht
social-profile-command-description = Je profiel tonen; volwaardige leden zoeken op naam, vermelding of antwoord
social-profile-command-parameters = [speler]
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
social-query-metric = { $arg0 }
social-query-no-players = Er zijn momenteel geen spelers online.
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (Afwezig)
   *[active] { $arg0 }
    }
social-query-online-summary = { $arg0 ->
    [one] Er is nu { $arg0 } speler online.
   *[other] Er zijn nu { $arg0 } spelers online.
    }{ $arg1 ->
    [0] { "" }
   *[other] { " " }Daarvan AFK: { $arg1 }.
    }
social-query-players-label = Spelers
social-query-population = Online { $arg0 }/{ $arg1 } · Actief { $arg2 } · Afwezig { $arg3 }
social-query-unknown = Onbekende opdracht. Stuur { $arg0 } om de beschikbare opdrachten te bekijken.
social-query-uptime-label = Bedrijfstijd
social-query-version-label = Versie
social-reply-hidden = Dit antwoord is verborgen door het inhoudsveiligheidsbeleid van de server.
social-reply-unavailable = Het resultaat kan nu niet worden weergegeven. Controleer accountwijzigingen met de profielopdracht voordat je ze opnieuw aanvraagt.
social-state-disabled = uitgeschakeld
social-state-failed = mislukt
social-state-ready = gereed
social-state-starting = wordt gestart
social-state-stopped = gestopt
social-status-command-description = Spelers, TPS, MSPT en looptijd tonen
social-title-command-help = Opdrachthulp
social-title-content-hidden = Inhoud verborgen
social-title-player-profile = Spelersprofiel
social-title-server-status = Serverstatus
social-unlink-command-description = Deze identiteitskoppeling na bevestiging verwijderen
social-unlink-command-parameters = [{ social-unlink-confirmation } <bevestiging>]
social-unlink-confirmation = bevestigen

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
ai-social-command-parameters = <vraag>
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
governance-active-playtime = Minstens { $arg1 } effectieve speeltijd in 60 dagen (nu { $arg0 })
governance-activity-action = { $arg0 ->
    [confirm] Steun bevestigen
    [vote] Lezen en stemmen
    *[details] Details bekijken
    }
governance-activity-all = Alles bekijken ({ $arg0 })
governance-activity-closed = Afgelopen
governance-activity-confirm-petition = Deze petitie steunen? Bij voldoende steun begint een afzettingsstemming.
governance-activity-needed = { $arg0 ->
    [one] Nog { $arg0 } medestander nodig
    *[other] Nog { $arg0 } medestanders nodig
    }
governance-activity-proposal = { $arg0 ->
    [appointment] { $arg1 } tot moderator benoemen
    [removal] { $arg1 } als moderator afzetten
    [petition] Petitie om { $arg1 } af te zetten
    *[ban] Stemming om { $arg1 } te verbannen
    }
governance-activity-remaining = { $arg1 ->
    [days] Resterend: { governance-days }
    [hours] Resterend: { governance-hours }
    *[minutes] Resterend: { governance-minutes }
    }
governance-activity-status = { $arg0 ->
    [available] Stemmen mogelijk
    [voted] Gestemd
    [join] Steunen mogelijk
    [joined] Gesteund
    [closed] Afgelopen
    *[view] Alleen bekijken
    }
governance-already-moderator = Je bent al moderator.
governance-application-status = { $arg0 ->
    [open] Er loopt een benoemingsstemming
    [pending] Goedgekeurd · benoeming in afwachting
    [blocked] Vereisten nog niet vervuld
   *[ready] Je kunt solliciteren
    }
governance-ballot-recorded = Je anonieme stem is vastgelegd als { $arg0 }. Tot de deadline kun je die wijzigen.
governance-ban-details = Voorgesteld door { $arg0 } · Duur { $arg1 } · Reden: { $arg2 }
governance-ban-suffix = · Duur { $arg0 } · Reden: { $arg1 }
governance-choice-abstain = Onthouding
governance-choice-no = Nee
governance-choice-yes = Ja
governance-command-description = Bestuursstatus en stemmingen bekijken
governance-custodian-no-nomination = Beheerders doen niet mee aan moderatorverkiezingen.
governance-custodian-no-resign = De ontslagprocedure voor moderators geldt niet voor beheerders.
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
governance-error-removal-petition-closed = Deze petitie is afgelopen of vervangen.
governance-error-removal-requires-petition = Een afzettingsstemming vereist voldoende steun.
governance-error-removal-self = Je kunt je eigen afzetting niet steunen.
governance-error-removal-sponsor-ineligible = Alleen actieve leden mogen een afzetting steunen.
governance-error-removal-sponsors-too-few = Er zijn minstens twee andere actieve leden nodig.
governance-error-removal-target-not-moderator = Deze speler is geen afzetbare moderator meer.
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
governance-hours = { $arg0 } uur
governance-inactive-removal = { $arg0 } is na 180 dagen zonder inloggen automatisch gestopt als moderator.
governance-invalid-choice = Kies yes, no of abstain.
governance-invalid-duration = Ongeldige duur. Gebruik bijvoorbeeld 30m, 12h of 3d.
governance-kind-appointment = Benoeming
governance-kind-ban = Ban
governance-kind-removal = Afzetting
governance-legacy-ban-kick = Een eerdere bestuursstemming is aangenomen. Je hebt een ban voor { $arg0 }. Reden: { $arg1 }
governance-menu-already-sponsored = Al gesteund
governance-menu-application = Sollicitatiestatus
governance-menu-completed = Afgerond
governance-menu-confirm = Bevestigen
governance-menu-confirm-nominate = Je sollicitatie start een stemming over je benoeming als moderator.
governance-menu-confirm-resign = Aftreden als moderator?
governance-menu-confirm-sponsor = Dit afzettingsverzoek steunen?
governance-menu-confirm-withdraw = Je aanmelding als moderator intrekken?
governance-menu-custodian-duties = Serveronderhoud en beheer.
governance-menu-identity = Rol
governance-menu-identity-hint = Promotievoorwaarden en activiteitsgeschiedenis.
governance-menu-join-petition = Steun afzettingsverzoek
governance-menu-loading = Bestuursstatus laden…
governance-menu-matters = Gemeenschapszaken
governance-menu-moderator-duties = Moderatie en spelershulp.
governance-menu-nominate = Aanmelden als moderator
governance-menu-offline = Niet verbonden
governance-menu-ongoing = Lopend
governance-menu-online = Verbonden
governance-menu-pause = Promotie van nieuwkomer pauzeren
governance-menu-pause-appeal = Beroepsroute
governance-menu-pause-duration = Duur (bijv. 2d)
governance-menu-pause-reason = Reden
governance-menu-pause-until = Eindigt op
governance-menu-pending-application = Aangenomen · uitvoering wacht
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } steunbetuigingen
governance-menu-petitions = Afzettingsverzoeken
governance-menu-player-name = Spelernaam
governance-menu-players = Spelerbeheer
governance-menu-refresh = Vernieuwen
governance-menu-related = Recente bijbehorende dossiers
governance-menu-related-matters = Bekijk bijbehorende zaken
governance-menu-resign = Aftreden als moderator
governance-menu-resume = Promotiepauze opheffen
governance-menu-resume-reason = Reden voor opheffing
governance-menu-staff-only = Alleen moderators en beheerders kunnen spelers beheren.
governance-menu-staff-search = Team zoeken
governance-menu-subject-filter = { $arg0 } · Filter wissen ×
governance-menu-team = Openbaar team
governance-menu-team-empty = Geen overeenkomende teamleden
governance-menu-title = Gemeenschapsbestuur
governance-menu-withdraw = Aanmelding intrekken
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
governance-seconds = { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    }
governance-storage-error = Bestuursgegevens konden niet worden verwerkt. Neem contact op met een beheerder.
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

plot-admin-created = { $arg0 } geregistreerd voor { $arg1 } · { $arg2 }
plot-allowed = ingeschakeld
plot-area-changed = De grenzen zijn gewijzigd. Vernieuw en bevestig opnieuw.
plot-area-confirm = Grenzen van ‘{ $arg0 }’ vervangen door de selectie ({ $arg1 ->
        [one] { $arg1 } bouwcel
       *[other] { $arg1 } bouwcellen
    })? Bouwwerken blijven staan.
plot-area-hint = Kies hoeken met links/rechts klikken van de houten bijl of //pos1 en //pos2; keer terug om op te slaan.
plot-area-replace-save = Grenzen vervangen en opslaan
plot-area-save = Grenzen opslaan
plot-area-saved = Grenzen van “{ $arg0 }” opgeslagen.
plot-atmosphere-saved = Omgeving opgeslagen.
plot-command-blocked = Perceelbeveiliging blokkeerde dit commando. Gebruik /fill of /setblock rechtstreeks in je eigen perceel; laat ingewikkelde /execute-commando’s door het serverteam beoordelen.
plot-command-description = Bouwpercelen beheren
plot-created = Perceel { $arg0 } aangemaakt · { $arg1 }
plot-deleted = Perceel vrijgegeven; het bouwwerk blijft staan.
plot-denied = uitgeschakeld
plot-error-admin-owner-only = Leden met beheermogelijkheden wijzigen vereist leden- en rechtenbeheer.
plot-error-area-disconnected = Het perceel moet één aaneengesloten gebied zijn.
plot-error-args = Ongeldige of ontbrekende argumenten. Gebruik /plot help.
plot-error-busy = Perceelgegevens worden opgeslagen. Probeer het later opnieuw; deze actie is niet ingediend.
plot-error-flag = Onbekend recht. Kies een actie uit de rechtenlijst.
plot-error-id = Perceel-ID ontbreekt of is dubbelzinnig. Gebruik /plot list.
plot-error-member-missing = Deze speler is geen lid meer.
plot-error-member-required = Je moet lid zijn om een perceel te maken.
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
plot-error-subplot-children = Geef deelpercelen vrij voordat je het hoofdperceel vrijgeeft.
plot-error-subplot-depth = Deelpercelen kunnen maar één niveau diep zijn.
plot-error-subplot-disconnected = De geselecteerde bouwcellen zijn niet verbonden. Verdeel elk deel apart.
plot-error-subplot-overlap = De selectie overlapt een ander deelperceel.
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
plot-menu-access = Leden en rechten
plot-menu-add-member = Lid toevoegen
plot-menu-atmosphere = Omgeving
plot-menu-clear = Helder
plot-menu-confirm-delete = De registratie van { $arg0 } verwijderen? Bescherming, ledenrechten en instellingen worden onherroepelijk verwijderd. Bouwwerken blijven staan.
plot-menu-confirm-subplot-transfer = { $arg0 } overdragen aan { $arg1 }? De eigenaar van het hoofdperceel houdt toezicht.
plot-menu-confirm-transfer = Het beheer van { $arg0 } overdragen aan { $arg1 }? Je verliest toegang; bestaand auteurschap blijft ongewijzigd.
plot-menu-create = Perceel maken
plot-menu-create-for-player = Perceel voor deze speler registreren
plot-menu-custom-time = Eigen tijd
plot-menu-delete = Perceelregistratie verwijderen
plot-menu-flag-container = Containers openen
plot-menu-flag-entity = Entiteiten bedienen
plot-menu-follow-parent = Hoofdperceel volgen
plot-menu-follow-world = Wereld volgen
plot-menu-group-collaborator = Medewerkers
plot-menu-group-member = Standaard voor leden
plot-menu-group-newcomer = Standaard voor nieuwkomers
plot-menu-here = Huidige locatie
plot-menu-manage = Percelen van spelers beheren
plot-menu-managed-empty = Deze speler heeft nog geen percelen.
plot-menu-managed-title = Percelen van { $arg0 }
plot-menu-members = Leden ({ $arg0 })
plot-menu-name = Projectnaam
plot-menu-owner = Eigenaar: { $arg0 }
plot-menu-parent-supervisor = Eigenaar hoofdperceel: { $arg0 } · toezicht
plot-menu-player = Spelersnaam of UUID
plot-menu-rain = Neerslag
plot-menu-remove-member = Lid verwijderen
plot-menu-rename = Project hernoemen
plot-menu-set-role = Instellen als { $arg0 }
plot-menu-settings = Projectinstellingen
plot-menu-submit = Toepassen
plot-menu-subplot-of = Onderdeel van { $arg0 }
plot-menu-subplot-parent = Terug naar hoofdperceel: { $arg0 }
plot-menu-subplots = Deelpercelen ({ $arg0 })
plot-menu-time = Tijd
plot-menu-time-input = Tijd (HH:mm)
plot-menu-transfer = Perceel overdragen
plot-menu-weather = Weer
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
plot-private = Privé
plot-public = Openbaar
plot-region-adjust = Deze rechthoek aanpassen
plot-region-apply = Aanpassing van rechthoek afronden
plot-region-cancel = Aanpassing van rechthoek annuleren
plot-region-changed = De selectie is gewijzigd. Selecteer de rechthoek opnieuw.
plot-region-delete = Deze rechthoek verwijderen
plot-region-delete-confirm = Rechthoek #{ $arg0 } uit het concept verwijderen? Gebouwen blijven staan. Grenzen veranderen pas na opslaan; je kunt dit ongedaan maken.
plot-region-edit-hint = Pas deze rechthoek aan met //pos1 en //pos2 en sla op.
plot-region-finish-first = Rond eerst de huidige rechthoekaanpassing af of annuleer deze.
plot-region-open = Rechthoeken beheren ({ $arg0 })
plot-region-select-hint = Selecteer een genummerde rechthoek om erop te focussen en deze te markeren. Grijze lijnen tonen deze pagina; samenvoegen kan de nummers veranderen.
plot-renamed = Project hernoemd.
plot-role-admin = perceelbeheerder
plot-role-collaborator = medewerker
plot-role-owner = perceeleigenaar
plot-role-parent-owner = eigenaar van het hoofdperceel
plot-role-server-staff = serverbeheer
plot-selection-add = Huidige doos toevoegen
plot-selection-clear = Beide punten opnieuw kiezen
plot-selection-empty = Markeer twee hoeken met //pos1 en //pos2.
plot-selection-fit = Opgeslagen grenzen herstellen
plot-selection-incomplete = Eén punt is gemarkeerd. Markeer het andere; vóór verzenden verandert niets.
plot-selection-redo = Laatste stap opnieuw uitvoeren
plot-selection-replace = Vervangen door huidige doos
plot-selection-session-hint = Sluipen+F of /plot edit om terug te keren. Stopt na 20 minuten inactiviteit.
plot-selection-size = Selectie: { $arg0 } × { $arg1 } × { $arg2 } blokken (X/Y/Z)
plot-selection-stopped = Selectie beëindigd. Concepten blijven per perceel bewaard.
plot-selection-subtract = Huidige doos aftrekken
plot-selection-undo = Laatste stap ongedaan
plot-subplot-created = Deelperceel { $arg0 } gemaakt · { $arg1 } blok²
plot-title = Percelen
plot-transferred = Perceel { $arg0 } overgedragen aan { $arg1 }.
vehicle-command-description = Voertuigen van weergave-entiteiten besturen en beheren
vehicle-menu-label =
    { $arg0 ->
        [vehicle] Voertuigbediening
        [driving] Rijden en meerijden
        [maintenance] Onderhoud en toegang
        [public-status] Openbare toegang
        [private-status] Privétoegang
        [home] Voertuigen
        [nearby] Voertuigen dichtbij
        [fleet] Alle toegankelijke voertuigen
        [selection] Modelselectie
        [origin] Oorsprong bij mijn voeten
        [group] Weergavegroep selecteren
        [radius] Selectiestraal (0–16 m)
        [displays] Weergaven dichtbij bekijken
        [clear] Selectie en oorsprong wissen
        [capture] Model uit selectie maken
        [toggle-selection] Selectie wisselen
        [public] Openbaar maken
        [private] Privé maken
        [reload] Modellen uit bestanden herladen
        [leave] Uitstappen
        [passenger] Passagiersstoel
        [fuel] Tanken · houd kolen/houtskool
        [repair] Repareren · houd ijzerstaaf
        [access] Toegang
        [remove] Voertuig verwijderen
        [hud] Rij-HUD tonen
        [hide-preview] 3D-voorbeeld verbergen
        [service-hint] Kom dichtbij, stop de motor en houd het juiste materiaal vast.
        [nearby-hint] Kom binnen 32 blokken en verlaat je huidige stoel.
        [use-required] Vereist mik.vehicle.use.
        [remove-hint] Stop het voertuig en laat iedereen uitstappen.
        [reload-hint] Alle voertuigen moeten leeg en uitgeschakeld zijn. Concepten blijven; open gewijzigde modellen opnieuw.
        [pick-hint] Richt op een weergave en rechtsklik. Sluip-rechtsklik annuleert; verloopt na 30 seconden.
        [spawn-hint] Wereldcoördinaten; standaard vier blokken vooruit. Richting volgens je horizontale blik bij aanmaken.
        [zoom-in] Vergroten
        [zoom-out] Verkleinen
        [frame] Opnieuw voor mij plaatsen
        [offset] Relatief verplaatsen
       *[other] { vehicle-command-description }
    }

vehicle-controls = { $arg0 }: W/S en A/D besturen; scrollen schakelt of wijzigt gas; handwissel verandert modus; sluipen stapt uit. /vehicle
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
vehicle-hud-boat = { $arg0 } kn · stuwkracht { $arg1 }% · koers { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · hoogte { $arg1 } m · stijgsnelheid { $arg2 } m/s · gas { $arg3 }% · { $arg4 }
vehicle-hud-status = Brandstof { $arg0 }% · conditie { $arg1 }% · { $arg2 }
vehicle-model-center = Massamiddelpunt
vehicle-model-choice = { $arg0 ->
    [CAR] Auto
    [BOAT] Motorboot
    [PLANE] Vliegtuig
    [BODY] Carrosserie
    [FRONT_WHEEL] Gestuurd wiel
    [WHEEL] Wiel
    [PROPELLER] Propeller
    [RUDDER] Roer
    [FIXED] Vast
    [VERTICAL] Rechtop blijven
    [HORIZONTAL] Horizontaal blijven
    [CENTER] Naar de camera
    [NONE] Oorspronkelijke houding
    [THIRDPERSON_LEFTHAND] Derde persoon · links
    [THIRDPERSON_RIGHTHAND] Derde persoon · rechts
    [FIRSTPERSON_LEFTHAND] Eerste persoon · links
    [FIRSTPERSON_RIGHTHAND] Eerste persoon · rechts
    [HEAD] Hoofd
    [GUI] Inventaris
    [GROUND] Grond
    [LEFT] Links
    [RIGHT] Rechts
    [false] Uit
    [true] Aan
    [TEXT_CENTER] Gecentreerd
   *[other] { $arg0 }
    }
vehicle-model-field = { $arg0 ->
    [kind] Voertuigtype
    [mass] Massa
    [maximum-speed] Topsnelheid (km/h)
    [support-length] Steunweg (m)
    [grip] Grip
    [center-of-mass] Massamiddelpunt
    [inertia] Traagheidsmomenten X/Y/Z
    [engine-torque] Piekkoppel
    [engine-idle-rpm] Stationair toerental (RPM)
    [engine-redline-rpm] Toerentalgrens (RPM)
    [engine-inertia] Motortraagheid
    [engine-wheel-radius] Wielstraal (m)
    [engine-final-drive] Eindoverbrenging
    [engine-reverse-ratio] Achteruitverhouding
    [engine-ratios] Vooruitverhoudingen
    [engine-shift-seconds] Schakeltijd (s)
    [fuel-capacity] Brandstofcapaciteit
    [fuel-consumption] Verbruik (/s)
    [buoyancy] Drijfvermogen
    [wing-area] Vleugeloppervlak (m²)
    [lift] Liftcoëfficiënt
    [animation] Animatierol
    [billboard] Naar de camera richten
    [item-transform] Voorwerphouding
    [alignment] Tekstuitlijning
    [block-light] Bloklicht
    [sky-light] Hemellicht
    [background] Achtergrond (ARGB)
    [line-width] Tekstbreedte
    [opacity] Dekking (0–255)
    [shadow] Tekstschaduw
    [see-through] Tekst door objecten
    [x] X
    [y] Y
    [z] Z
    [size-x] Volledige breedte X (m)
    [size-y] Volledige hoogte Y (m)
    [size-z] Volledige lengte Z (m)
   *[other] { $arg0 }
    }
vehicle-model-field-range = Toegestaan bereik: 0 < waarde ≤ { $arg0 }
vehicle-model-front = Voorkant (+Z)
vehicle-model-front-choice = { $arg0 ->
    [negative-x] Huidige −X-kant
    [back] Huidige achterkant (−Z)
    [positive-x] Huidige +X-kant
    [look] Mijn horizontale kijkrichting gebruiken (op 90° afgerond)
   *[other] Deze kant als voorkant instellen
    }
vehicle-model-front-hint = Kies de pijl in 3D. De carrosserie blijft staan; stoelen, uitstappen en fysicapunten krijgen samen nieuwe assen. Stappen van 90° kun je ongedaan maken.
vehicle-model-handle = { $arg0 ->
    [ROTATE] { $arg1 } { $arg2 } { $arg3 }°
   *[other] { $arg1 } { $arg2 } { $arg3 }
    }
vehicle-model-marker = { $arg0 ->
    [driver] Bestuurdersstoel #{ $arg1 }
    [passenger] Passagiersstoel #{ $arg1 }
    [exit] Uitstap #{ $arg1 }
    [colliders] Botsingsvak #{ $arg1 }
    [supports] Steunpunt #{ $arg1 }
    [center] Massamiddelpunt
    [parts] Weergavedeel #{ $arg1 }
   *[other] { $arg1 }
    }
vehicle-model-parameter-group = { $arg0 ->
    [body] Carrosserie en bodemcontact
    [engine] Motor en transmissie
    [fuel] Brandstof
    [boat] Drijfvermogen van boten
    [plane] Lift van vliegtuigen
    [power] Motorvermogen
   *[other] { $arg0 }
    }
vehicle-model-parameter-help = { $arg0 ->
    [body] Massa, snelheid en bodemcontact instellen. Het massamiddelpunt is in 3D instelbaar. Alleen het concept verandert tot je opslaat.
    [engine] Toerental, koppel, wielen en verhoudingen. Vooruitverhoudingen moeten afnemen; de grens moet hoger zijn dan 1,5 × stationair.
    [fuel] Capaciteit gebruikt brandstofeenheden uit het spel; verbruik is per seconde.
    [boat] Drijfvermogen geldt voor boten. Waarden voor andere typen blijven bewaard.
    [plane] Vleugeloppervlak en lift gelden voor vliegtuigen. Andere waarden blijven bewaard.
    [power] Toerental en koppel voor dit voertuigtype. Transmissiewaarden voor auto’s blijven bewaard.
   *[other] { $arg0 }
    }
vehicle-model-property-help = { $arg0 ->
    [block-light] −1 gebruikt wereldlicht. Om dit te vervangen moeten beide lichtwaarden op 0–15 staan.
    [sky-light] −1 gebruikt wereldlicht. Om dit te vervangen moeten beide lichtwaarden op 0–15 staan.
    [background] ARGB-geheel getal met teken, bijvoorbeeld −16777216 voor dekkend zwart.
    [line-width] Regelbreedte: 1–2048 pixels.
    [opacity] 0 is doorzichtig; 255 is dekkend.
   *[other] { $arg0 }
    }
vehicle-model-spatial = 3D-selectie openen
vehicle-model-spatial-hint = Klik rechts op een markering om te kiezen of aan te passen. Sluipen + rechtsklik brengt je terug naar instellingen. Met het muiswiel wijzig je de stapgrootte.
vehicle-model-spawn = Dit model plaatsen
vehicle-model-add = Onderdeel toevoegen
vehicle-model-add-here = Steunpunt bij mijn voeten toevoegen
vehicle-model-anchor = Opnieuw voor mij plaatsen
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
vehicle-model-corner-hint = { vehicle-model-corner-first } → { vehicle-model-corner-second }
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
vehicle-model-overview = Conceptoverzicht
vehicle-model-panel = Terug naar bewerkingspaneel
vehicle-model-editor-help = { $arg0 ->
   *[overview] Kies hieronder een categorie, daarna een object en gereedschap. Opslaan en Ongedaan maken blijven onderaan.
    [parts] Beheer zichtbare blokken, voorwerpen en tekst. Kies een onderdeel om inhoud of transformatie te wijzigen.
    [seats] Kies een stoel om de zitplaats en uitstapplaats in te stellen. Er moet precies één bestuurdersstoel zijn.
    [colliders] Kies een botsingsbox om middelpunt en volledige afmetingen te wijzigen. Voeg een box toe of pas deze aan het model aan.
    [supports] Kies een contactpunt om het te verplaatsen. Deze punten zorgen voor grondsteun of drijfvermogen.
    [parameters] Kies een parametergroep en daarna een veld om de waarde te wijzigen.
    [tools] Pas het voorbeeld aan, kies objecten in de wereld of controleer het concept. Verwerpen vereist bevestiging.
    }
vehicle-model-section-count = { $arg0 ->
   *[parts] { vehicle-model-parts } · { $arg1 }
    [seats] { vehicle-model-seats } · { $arg1 }
    [colliders] { vehicle-model-colliders } · { $arg1 }
    [supports] { vehicle-model-supports } · { $arg1 }
    }
vehicle-model-content-action = { $arg0 ->
   *[block] Blokinhoud instellen
    [item] Voorwerp in hoofdhand gebruiken
    [text] Tekstinhoud instellen
    }
vehicle-model-exact-input = Exacte waarden invoeren
vehicle-model-axis-value = { $arg0 } · { $arg1 }
vehicle-model-component-details = Onderdeeldetails
vehicle-model-selection-unavailable = Object gewijzigd of verwijderd; ga terug en kies opnieuw
vehicle-model-exit = Uitstappunt / fijnafstelling
vehicle-model-group = Axiom-groep uit hoofdhand toevoegen
vehicle-model-hand-invalid = Het Axiom-weergavevoorwerp is ongeldig of bevat niet-ondersteunde entiteiten of weergavegegevens.
vehicle-model-hand-required = Houd een Axiom-weergavevoorwerp in je hoofdhand en importeer opnieuw.
vehicle-model-here = Mijn huidige voetpositie gebruiken
vehicle-model-history-empty = Geen wijziging beschikbaar in deze richting van de geschiedenis.
vehicle-model-import = Axiom-groep uit hoofdhand importeren
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
vehicle-model-position-hint = Lokale coördinaten: +Y is omhoog, +Z is de voorkant. Knoppen verplaatsen per stap; exacte invoer stelt de absolute positie in.
vehicle-model-preview = Privévoorbeeld
vehicle-model-preview-failed = Voorbeeld niet beschikbaar; je concept blijft behouden: { $arg0 }
vehicle-model-preview-hint = Voorbeeld getoond. Open 3D-selectie om het model rechtstreeks te selecteren en aan te passen.
vehicle-model-redo = Opnieuw uitvoeren
vehicle-model-remove = Onderdeel verwijderen
vehicle-model-remove-warning = Dit onderdeel verwijderen? Volgende indexnummers schuiven op; ongedaan maken blijft mogelijk.
vehicle-model-rotate = Onderdeel draaien
vehicle-model-rotate-hint = Relatieve lokale rotatie: X kantelen, Y draaien, Z rollen. De positie blijft gelijk.
vehicle-model-save = Opslaan en afsluiten
vehicle-model-save-continue = Opslaan en verder bewerken
vehicle-model-saved = Opgeslagen
vehicle-model-scale = Onderdeel schalen
vehicle-model-scale-all = Alle assen schalen { $arg0 } { $arg1 }
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
vehicle-model-tools = Voorbeeld en gereedschap
vehicle-model-transform-step = Afstelstap wijzigen
vehicle-model-undo = Ongedaan maken
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
async-regen-command-description = Een FastAsyncWorldEdit-selectie asynchroon regenereren
async-regen-completed = Regeneratie voltooid: { $arg0 ->
        [one] { $arg0 } blok
       *[other] { $arg0 } blokken
    } gewijzigd in { $arg1 }
async-regen-failed = Regeneratie gestopt door een interne fout. De { $arg0 } al toegepaste wijzigingen blijven in de wereld
async-regen-filter-all = alle bloktypen
async-regen-invalid-blocks = Onbekende typen of geen bloktypen: { $arg0 }
async-regen-not-running = Je hebt geen actieve regeneratie
async-regen-nothing-to-apply = De selectie komt al overeen met dit plan; er hoeft niets te worden toegepast
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
async-regen-selection-required = Maak eerst een volledige FastAsyncWorldEdit-selectie
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
async-regen-fawe-required = FastAsyncWorldEdit is vereist om de selectie te lezen
building-map-layer = Bouwwerken
building-map-open = Bouwwerk bekijken
building-map-builders = Gebouwd door { $arg0 }
building-map-original-author = Oorspronkelijke maker: { $arg0 }
building-map-type-original = Eigen creatie
building-map-type-derivative = Afgeleid werk
building-map-type-replica = Replica
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ Sluipen
iron-elevator-tip-mm = Zet { -iron-elevator-block } recht boven elkaar voor een lift. Ga erop staan: <green>spring om omhoog te gaan</green>, <aqua>sluip om omlaag te gaan</aqua>. Vanaf twee verdiepingen verschijnt een <gold>verdiepingenpaneel</gold>.
iron-elevator-up = ↑ Springen
spawn-beacon-color-command-description = Schakel de kleurcyclus van het spawnbaken in of uit
spawn-beacon-color-disabled = Kleurcyclus van het spawnbaken uitgeschakeld
spawn-beacon-color-enabled = Kleurcyclus van het spawnbaken ingeschakeld
spawn-command-description = Terug naar de spawn
spawn-tip-return-mm = Gebruik <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> om terug te keren naar het <yellow>spawnpunt van de server</yellow>.
spawn-world-missing = Spawnwereld is niet beschikbaar

## Entertainment · Music

music-album = Album: { $arg0 }
music-artist = Artiest: { $arg0 }
music-command-description = Lokale, online en NBS-muziek voor platenspelers
music-disc-left = Linksklik: pak de muziekplaat
music-disc-right = Rechtsklik: speel af op de dichtstbijzijnde platenspeler
music-duration = Duur: { $arg0 }
music-format = Formaat: { $arg0 }
music-jukebox-audible-field-conflict = Een andere platenspeler speelt een ander nummer in een overlappend duidelijk hoorbaar gebied
music-jukebox-tp-hover = Klik om naar de platenspeler te teleporteren
music-jukebox-unavailable = De platenspeler is verwijderd of de chunk ervan is niet beschikbaar
music-now-playing-rich = Wordt nu afgespeeld: { $music } bij platenspeler { $location }
music-online-rate-limited = Te veel muziekaanvragen. Probeer het over { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } seconden
    } opnieuw.
music-original-author = Oorspronkelijke auteur: { $arg0 }
music-plasmo-voice-unavailable = Het nummer is gestart, maar je kunt het niet horen omdat Plasmo Voice niet is geïnstalleerd, verbonden of ingeschakeld
music-playback-failed = "{ $arg0 }" kon niet worden afgespeeld. Controleer de bron of het muziekbestand en probeer het opnieuw.
music-reload-failed = Muziek opnieuw laden mislukt: { $arg0 }
music-reload-started = Muziekbestanden en aangepaste bronnen opnieuw laden...
music-rhythm-animation-offset = Animatieverschuiving { $arg0 } ms
music-rhythm-calibration-accumulating = Stabiele timing wordt bevestigd… blijf meespelen
music-rhythm-calibration-adapting = Ritme wordt afgestemd… blijf meespelen
music-rhythm-calibration-audio-instruction = Druk op elke tel op een van de toetsen 1–4.
music-rhythm-calibration-done = Latentietest voltooid · instellingen opgeslagen
music-rhythm-calibration-failed = De audio van de test stopte onverwacht. De test is veilig gesloten; start hem opnieuw
music-rhythm-calibration-intro = { music-rhythm-calibration-stage-minecraft } → { music-rhythm-calibration-stage-plasmo } → { music-rhythm-calibration-stage-visual } → { music-rhythm-calibration-stage-pointer }
music-rhythm-calibration-listen = Luister eenmaal naar het volledige ritme. Druk nog geen toetsen in.
music-rhythm-calibration-no-input = Geen geldige invoer herkend. Gebruik bij elk signaal de bediening die voor deze fase wordt getoond.
music-rhythm-calibration-out-of-range = De gemeten vertraging valt buiten het ondersteunde bereik. Controleer de audio-instellingen
music-rhythm-calibration-plasmo-ready = Minecraft-test voltooid; luister eerst via Plasmo Voice naar hetzelfde ritme
music-rhythm-calibration-pointer-instruction = Klik links of rechts wanneer de noot de lijn passeert.
music-rhythm-calibration-realigning = De audiostream werd onderbroken. De demonstratie wordt herhaald en voltooide metingen blijven behouden
music-rhythm-calibration-required = Voltooi de volledige latentietest voordat je het ritmespel start
music-rhythm-calibration-reset-done = Latentiekalibratie is gereset
music-rhythm-calibration-result-hold = Test voltooid. Bekijk de resultaten; je keert zo automatisch terug.
music-rhythm-calibration-stage-minecraft = Minecraft-nootblok
music-rhythm-calibration-stage-plasmo = Plasmo Voice
music-rhythm-calibration-stage-pointer = Beeld · muis
music-rhythm-calibration-stage-visual = Beeld · toetsenbord
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
music-rhythm-requires-playback = Speel eerst een nummer af op deze platenspeler
music-rhythm-results = Resultaten
music-rhythm-score = Score: { $arg0 }
music-rhythm-spatial-no-room = Er is niet genoeg vrije ruimte. Ga naar een opener gebied.
music-rhythm-spatial-slider-hint = Volg de bewegende ster en klik elk controlepunt · niet vasthouden
music-rhythm-start = Spel starten
music-sample-rate = Bemonsteringsfrequentie: { $arg0 }
music-size = Grootte: { $arg0 }
music-unavailable-disc = Niet-beschikbare muziekplaat

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
ban-menu-confirm-action = Speler verbannen
ban-menu-confirm-severity = Ernst
ban-menu-confirm-title = Verbanning bevestigen
ban-menu-never-joined-confirm-action = Toch verbannen
ban-menu-never-joined-title = Speler is nooit toegetreden
ban-menu-never-joined-warning = { $arg0 } is nooit op deze server geweest. Controleer de spelersnaam om te voorkomen dat je de verkeerde speler verbant.
ban-menu-player-input = Spelersnaam handmatig invoeren
ban-menu-select-title = Speler selecteren
ban-menu-severity-title = Ernst selecteren
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
maintenance-kick = De server is in onderhoud
maintenance-set = Onderhoudsmodus { $arg0 }
maintenance-state-disabled = uitgeschakeld
maintenance-state-enabled = ingeschakeld
maintenance-status = Onderhoudsmodus: { $arg0 }
maintenance-unchanged = De onderhoudsmodus is al { $arg0 }
performance-kick-title = De server is ernstig vertraagd
performance-kick-reason = Je verbinding is tijdelijk verbroken om de server te laten herstellen
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
tpsbar-tip-status-mm = Toon of verberg de balk met <yellow>TPS, MSPT en ping</yellow> via <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green>.
unban-command-description = Speler deblokkeren en de geschiedenis bewaren

## Shell · Menus, server information and scripting

main-afk-status =
    { $arg0 ->
        [afk] AFK: Afwezig
       *[online] AFK: Online
    }
main-pvp-status =
    { $arg0 ->
        [on] PVP: Aan
       *[off] PVP: Uit
    }
main-announcements = Aankondigingen
main-home = Thuislocaties
main-language = Taal
main-language-auto = Automatisch
main-links = Links
main-map = Webkaart
main-menu-title = Hoofdmenu
main-music = Muziek
main-name-tag = Naamlabels
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
stat-ping-value = { $arg0 } ms
stat-play-time = Speeltijd
stat-role = Rol
stat-world = Wereld
web-account-title = Webaccount en toegangssleutels
web-account-confirm = Bevestigen
weblogin-question = Deze identiteitsverificatie voor de website bevestigen? Bevestig alleen acties die je zelf op mcmik.top hebt gestart. Voer nooit een code voor iemand anders in.
weblogin-command-description = Een Mik-verificatiecode voor je webidentiteit bevestigen
weblogin-confirmed = Webidentiteit geverifieerd. Ga terug naar je browser om verder te gaan.
weblogin-invalid-code = Ongeldige verificatiecode voor je webidentiteit


# Music workspace
music-space-label =
    { $arg0 ->
        [title] Muziekruimte
        [devices] Afspeelapparaten
        [all-devices] Alle apparaten
        [use-device] Dit apparaat gebruiken
        [current-device] Huidig apparaat
        [manage-device] Beheren
        [back-devices] Terug naar apparaten
        [group-with-devices] Met andere apparaten groeperen
        [pick-world-member] Een apparaat in de wereld kiezen
        [group-world-pick-hint] Klik met lege hand sluipend met rechts op een jukebox om deze toe te voegen; /music opent het menu weer.
        [group-member-standalone] Kies een jukebox die nog niet bij een synchronisatiegroep hoort.
        [group-selection-help] Selecteer hieronder jukeboxen of klik in de wereld met lege hand sluipend met rechts op een andere. De groep ontstaat bij bevestiging.
        [group-source-help] Gebruik de wachtrij en positie van het gekozen apparaat. Aanmaken stopt het afspelen; Aanmaken en afspelen hervat het.
        [device-actions] Acties
        [device-status] Status
        [sync-group] Synchronisatiegroep
        [single-device] Jukebox
        [device-location] Locatie
        [visit-device] Ernaast teleporteren
        [teleporting] Bezig met teleporteren…
        [recorded-device] Onthouden
        [nearby-device] Dichtbij
        [group-select-members] Selecteer minstens twee jukeboxen
        [group-name-required] Voer een groepsnaam in
        [direction-front] Voor je
        [direction-front-right] Rechtsvoor
        [direction-right] Rechts
        [direction-back-right] Rechtsachter
        [direction-back] Achter je
        [direction-back-left] Linksachter
        [direction-left] Links
        [direction-front-left] Linksvoor
        [direction-here] Hier
        [direction-above] Boven
        [direction-below] Onder
        [no-devices] Geen apparaten
        [choose-members] Groepsapparaten kiezen
        [create-group] Synchronisatiegroep maken
        [add-members] Apparaten toevoegen
        [create-play] Maken en afspelen
        [create-and-append] Maken en nummers achteraan toevoegen
        [device-queue] Wachtrij
        [device-members] Leden
        [available] Beschikbaar
        [play] Afspelen
        [retry-outputs] Uitvoer opnieuw proberen
        [dissolve-group] Groep opheffen
        [remove-member] Uit groep verwijderen
        [removed-stopped] Verwijderde apparaten stoppen en behouden de gekozen inhoud.
        [restore-previous] Inhoud van vóór de groep herstellen
        [keep-group-content] Groepsinhoud behouden
        [queue] Wachtrij
        [back] { back }
        [close] { close }
        [unavailable] De muziekopslag is niet beschikbaar
        [saving] Opslaan…
        [saved] Opgeslagen
        [apply] { plot-menu-submit }
        [cancel] { cancel }
        [select-page] Deze pagina selecteren
        [select-all] Alle resultaten selecteren
        [clear-tracks] Nummerselectie opheffen
        [add-playlist] Toevoegen
        [description] Beschrijving
        [make-private] Privé maken
        [publish] Openbaar maken
        [delete] Afspeellijst verwijderen
        [favorite] Favoriet in- of uitschakelen
        [copy] Afspeellijst kopiëren
        [share] Opdracht voor afspeellijst delen
        [recent] Recent afgespeeld
        [favorites] Favoriete afspeellijsten
        [public] Openbaar
        [private] Privé
        [new-playlist] Afspeellijst maken
        [name] Naam
        [song-title] Nummer
        [track-count] Nummers
        [visibility] Zichtbaarheid
        [no-playlists] Geen afspeellijsten
        [fetching-playlist] Afspeellijst laden…
        [artist] Artiest
        [duration] Duur
        [track-type] Type
        [type-online] Online
        [undo] { vehicle-model-undo }
        [remove] Geselecteerde vermeldingen verwijderen
        [clear-recents] Mijn afspeelgeschiedenis wissen
        [clear-entries] Selectie van vermeldingen opheffen
        [search-tracks] Zoek nummers, artiesten…
        [search-playlists] Zoek afspeellijsten…
        [public-playlists] Openbare afspeellijsten
        [searching] Zoeken…
        [insert] Invoegen en nu afspelen
        [append] Toevoegen aan wachtrij
        [choose-targets] Uitvoer kiezen
        [group-source] Wachtrij en voortgang overnemen van
        [removed-targets] Sommige doelen zijn verwijderd; selecteer ze opnieuw
        [offline] Niet verbonden
        [device-discovery-hint] Sluip en rechtsklik met een lege hand op een jukebox om deze te openen.
        [empty] Geen nummers
        [queue-empty] Geen nummers in de wachtrij
        [queue-current] Huidig nummer in de digitale wachtrij
        [pause] Pauzeren
        [resume] Hervatten
        [stop] Stoppen en afspeellijst bewaren
        [previous] Vorig nummer / opnieuw beginnen
        [clear] Wachtrij wissen; stoppen na huidig nummer
        [seek] Positie
        [more] Meer
        [volume-control] Volume
        [group-volume] Groepsvolume
        [timeline-help] Scrollen ±5 s · Rechtsklik om tijd in te voeren
        [timeline-read-only] Positie wijzigen niet beschikbaar
        [seek-format] Voer m:ss, h:mm:ss of seconden in
        [record] Opslaan voor bediening op afstand
        [forget] Mijn opgeslagen apparaten vergeten
        [alias] Persoonlijke apparaatnaam
        [sound] Geluidsinstellingen
        [rhythm] Ritmekamers
        [room-players] Spelers
        [room-player-status] Status
        [room-preparation] Mijn voorbereiding
        [room-ready] Klaar
        [room-cancel-ready] Gereedheid annuleren
        [room-playing] In het spel
        [room-score-ready] Noten klaar
        [room-select-one] Selecteer één nummer met meervoudige selectie in de bibliotheek.
        [room-end-confirmation] Dit beëindigt de kamer voor alle deelnemers.
        [create-room] Kamer maken
        [no-rhythm-devices] Geen jukebox in de buurt. Sluip eerst en klik met een lege hand rechts op een jukebox.
        [calibration] Latentiekalibratie
        [volume] Volume (0–100)
        [range] Bereik (8–256 blokken)
        [mode] Afspeelmodus
        [save-queue] Wachtrij als afspeellijst opslaan
        [sequential] Op volgorde
        [repeat-all] Lijst herhalen
        [repeat-one] Nummer herhalen
        [shuffle] Willekeurig zonder herhalingen
        [import] Afspeellijst importeren
        [link] Afspeellijstlink
        [partial-import] Slechts een deel van de afspeellijst is opgehaald. Sla dat deel op als je het wilt bewaren.
        [import-preview] Voorbeeld van opgehaalde metadata; er is geen audio gedownload
        [import-new] Maken en openen
        [import-merge] Samenvoegen
        [stopped] Gestopt
        [loading] Audio voorbereiden
        [cancel-loading] Voorbereiding annuleren
        [playing] Wordt afgespeeld
        [paused] Gepauzeerd
        [finished] Afgelopen
        [retry-failed] Afspelen opnieuw proberen
        [select-song] Speel gekozen nummers samen af of voeg ze toe aan een afspeellijst.
        [select-toggle] Dit item selecteren
        [deselect] Dit item deselecteren
        [selected] Geselecteerd
        [place-before] Selectie vóór dit item plaatsen
        [eject] Geplaatste plaat uitwerpen
        [make-disc] Ondertekende muziekplaat maken
        [gain] Groepsvolume (0–100)
        [retry-import] Afspeellijst opnieuw ophalen
        [join-room] Deelnemen aan kamer
        [ready] Klaar
        [waiting] Wachten
        [room-mode] Spelmodus
        [room-difficulty] Moeilijkheid
        [late-join] Deelnemen aan het lopende spel
        [leave-room] Kamer verlaten
        [select-room-track] Geselecteerd nummer gebruiken
        [start-room] Spel starten
        [end-room] Kamer beëindigen
        [base-queue] Basisafspeellijst
        [priority-queue] Eenmalige verzoeknummers
        [falling] { music-rhythm-mode-falling }
        [radial] { music-rhythm-mode-radial }
        [spatial-aim] { music-rhythm-mode-spatial-aim }
        [easy] { music-rhythm-difficulty-easy }
        [normal] { music-rhythm-difficulty-normal }
        [hard] { music-rhythm-difficulty-hard }
        [expert] { music-rhythm-difficulty-expert }
        [waiting-for-player] Wachten tot spelers klaar zijn
        [preparing] Audio en noten voorbereiden
        [no-beats] Geen speelbare beats
        [catalog] Catalogus
        [my-playlists] Mijn afspeellijsten
        [playlist] Afspeellijst
        [output] Uitvoer
        [close-panel] Paneel sluiten
        [playlist-settings] Lijstinstellingen
        [multi-select] Selecteren
        [move] Verplaatsen
        [move-hint] Kies een nummer om de selectie ervoor te plaatsen
        [cancel-move] Verplaatsen annuleren
        [move-end] Aan einde plaatsen
        [cancel-selection] Selectie annuleren
        [track-details] Nummerdetails
        [skip-next] Naar volgend nummer
        [device-settings] Apparaatinstellingen
        [already-added] Al toegevoegd
        [current-track] Huidig nummer
        [shuffle-candidates] Willekeurige kandidaten
        [import-by-id] Via ID importeren
        [playlist-id] Afspeellijst-ID
       *[unknown] Muziekruimte
    }

music-space-error =
    { $arg0 ->
        [UNAVAILABLE] Muziek is niet beschikbaar. Open het menu later opnieuw.
        [INTERNAL] Interne muziekfout; vraag een beheerder het serverlogboek te bekijken
        [NOT_FOUND] De geselecteerde vermelding bestaat niet meer.
        [FORBIDDEN] Je hebt toestemming nodig om te bedienen of te bewerken.
        [CONFLICT] De inhoud is gewijzigd. Vernieuw de weergave en selecteer opnieuw.
        [LIMIT] De limiet voor muziekbronnen is bereikt.
        [INVALID] Controleer de naam, selectie of het getalbereik.
        [OFFLINE] Apparaat niet beschikbaar: laad de chunk of werp de geplaatste plaat uit.
        [OVERLAP] Hoorbare gebieden overlappen. Stop een ander apparaat, verklein het bereik of maak een synchronisatiegroep.
        [UNSUPPORTED] Dit medium ondersteunt die actie niet.
        [BUSY] Het apparaat, het verzoek of de ritmekamer is bezet. Probeer het opnieuw zodra de taak is voltooid.
        [NO_SAFE_DESTINATION] Geen veilige plek naast deze jukebox gevonden
        [TELEPORT_FAILED] Teleportatie is mislukt of geblokkeerd
       *[UNKNOWN] Actie mislukt
    }

music-space-selected-tracks =
    { $arg0 ->
        [one] { $arg0 } nummer geselecteerd
       *[other] { $arg0 } nummers geselecteerd
    }
music-space-track-count =
    { $arg0 ->
        [one] { $arg0 } nummer
        *[other] { $arg0 } nummers
    }
music-space-device-count =
    { $arg0 ->
        [one] { $arg0 } jukebox
       *[other] { $arg0 } jukeboxen
    }
music-space-playback-target = Afspelen op: { $arg0 }
music-space-online-count = Online: { $arg0 }/{ $arg1 } { $arg1 ->
        [one] jukebox
       *[other] jukeboxen
    }
music-space-device-distance = { $arg0 } · { $arg1 } m
music-space-device-height = { $arg0 ->
    [above] { $arg1 } m hoger
   *[below] { $arg1 } m lager
    }
music-space-device-coordinates = { $arg0 } · X { $arg1 } · Y { $arg2 } · Z { $arg3 }
music-space-sound-value = { $arg0 ->
        [volume] { $arg1 ->
           *[other] { $arg1 }%
        }
       *[range] { $arg1 ->
            [one] { $arg1 } blok
           *[other] { $arg1 } blokken
        }
    }
plot-permission-music-control = Muziekapparaten bedienen

music-partial-results = Mislukte bronaanvragen: { $arg0 ->
        [one] { $arg0 } aanvraag
       *[other] { $arg0 } aanvragen
    }; beschikbare resultaten worden getoond

webchat-tab-label = { $arg0 ->
    [1] { player-web-label }
   *[other] { player-web-label } ×{ $arg0 }
    }
webchat-joined = { $arg0 } neemt deel aan de webchat
webchat-left = { $arg0 } heeft de webchat verlaten
chat-rate-limited = Je verstuurt te snel. Wacht even.

plot-map-corner-a = Hoek A
plot-map-corner-b = Hoek B
plot-map-corner-hint = Klik om deze hoek te centreren. Rechtsklik: coördinaten; muiswiel: Y ±4.
plot-map-coordinates = Coördinaten x,y,z
plot-map-zoom-in = Inzoomen
plot-map-zoom-out = Uitzoomen
plot-map-fit = Hele gebied
plot-map-near = Bij mij
plot-map-layer = Laag Y { $arg0 }…{ $arg1 }
plot-map-layer-hint = Muiswiel: andere laag; klik: jouw laag. Terrein toont altijd het oppervlak.
plot-map-north = Noord
plot-map-west = West
plot-map-east = Oost
plot-map-south = Zuid
plot-map-scale = 1 pixel = { $arg0 } × { $arg0 } blokken
plot-map-progress = Kaart lezen { $arg0 }%…
plot-map-unknown = Ongeladen oppervlaktemonsters: { $arg0 }
plot-map-legend = Cyaan: behouden · Paars: toegevoegd · Oranje: verwijderd · Groen: blokvorm · A/B: hoeken · Pijl: jij
plot-selection-validating = Grenzen berekenen en gebied controleren op de achtergrond… Wordt automatisch bijgewerkt zodra het klaar is.

plot-map-open = Minecraft-kaart bekijken

plot-map-handheld-hint = Kaart in de tweede hand; selecteer met een houten bijl. Sluipen + F opent het paneel.

plot-map-readonly-hint = Alleen kaartweergave. Ga terug naar het paneel en kies Blokvorm aanpassen om hoeken te wijzigen.

plot-destinations = Teleportpunten
plot-destination-add = Huidige locatie toevoegen
plot-destination-confirm-delete = Teleportpunt ‘{ $arg0 }’ verwijderen?
plot-destination-delete = Teleportpunt verwijderen
plot-destination-deleted = Teleportpunt verwijderd.
plot-destination-description = Beschrijving (optioneel)
plot-destination-edit = Naam en beschrijving bewerken
plot-destination-empty = Geen teleportpunten beschikbaar.
plot-destination-error-changed = Dit teleportpunt is gewijzigd. Open het opnieuw.
plot-destination-error-description = De beschrijving mag maximaal 512 tekens bevatten, zonder besturingstekens.
plot-destination-error-location = Ga op veilige grond binnen dit perceel staan.
plot-destination-error-missing = Dit teleportpunt bestaat niet meer.
plot-destination-error-name = Gebruik een naam van 1–48 tekens op één regel.
plot-destination-error-private = Dit teleportpunt is privé en alleen beschikbaar voor perceelleden.
plot-destination-error-unsafe = Dit teleportpunt is niet beschikbaar of onveilig.
plot-destination-loading = Teleportpunt laden…
plot-destination-move = Huidige locatie gebruiken
plot-destination-name = Naam
plot-destination-public-empty = Er zijn nog geen openbare teleportpunten.
plot-destination-public-off = Openbaar: uit
plot-destination-public-on = Openbaar: aan
plot-destination-saved = Teleportpunt opgeslagen.
plot-destination-teleport = Teleporteren
plot-error-subplot-destination = Verplaats of verwijder eerst de teleportpunten van het bovenliggende perceel in het geselecteerde gebied.
plot-error-subplot-own-destination = Houd teleportpunten binnen het nieuwe gebied of verplaats of verwijder ze eerst.

plot-destination-manage = Beheren
plot-directory-owned = In bezit · { $arg0 }
plot-directory-participating = Deelname · { $arg0 }
plot-directory-search = Percelen zoeken
plot-directory-search-value = Zoeken: { $arg0 }
plot-directory-search-field = Perceelnaam of ID
plot-directory-clear-search = Zoekopdracht wissen
plot-directory-empty = Nog geen percelen in deze categorie.
plot-directory-no-match = Geen overeenkomende percelen. Wijzig of wis de zoekopdracht.
plot-workspace-area = Gebied bewerken
plot-workspace-area-hint = Het beschermde gebied van dit perceel aanpassen
plot-workspace-subplots = Subpercelen
plot-workspace-subplots-hint = Onderverdelingen bekijken, maken en beheren
plot-workspace-access-hint = Leden, rollen en actierechten beheren
plot-workspace-settings-hint = Naam, bericht, omgeving en eigendom
plot-workspace-destinations-hint = Teleportpunten van dit perceel bekijken en gebruiken
plot-workspace-role = Jouw rol: { $arg0 }
plot-workspace-ownership = Eigendom en verwijdering
plot-workspace-id = Perceel-ID: { $arg0 }
plot-workspace-groups = Rechtengroepen
plot-workspace-permissions = Actierechten
plot-workspace-other-world = Ga eerst naar de wereld van dit perceel.

plot-destination-titles = Aankomsttitels
plot-destination-title = Titel
plot-destination-subtitle = Ondertitel
plot-destination-title-hint = Zichtbaar na een geslaagde teleportatie. Ondersteunt MiniMessage-kleuren, stijlen en verlopen. Laat leeg om te verbergen; een voorbeeld slaat niets op.
plot-destination-title-preview = Voorbeeld op volledig scherm
plot-destination-title-unset = Niet ingesteld
plot-destination-title-save = Titels opslaan
plot-destination-title-unchanged = Geen niet-opgeslagen wijzigingen.
plot-destination-title-apply = Op concept toepassen
plot-destination-error-title = De titel moet één regel zijn: maximaal 32 zichtbare tekens en 512 tekens MiniMessage-brontekst.
plot-destination-error-subtitle = De ondertitel moet één regel zijn: maximaal 64 zichtbare tekens en 512 tekens MiniMessage-brontekst.
