### MIK · fr-fr
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = Parcelle
    .gender = feminine

-vehicle-model = Modèle
    .gender = masculine

## Shared · General

back = Retour
back-to-main = Retour au menu principal
click-open = Cliquez pour ouvrir
close = Fermer
current-off = État actuel : désactivé
current-on = État actuel : activé
player-only = Cette commande ne peut être utilisée que par les joueurs
unknown = Inconnu
usage = Utilisation

## Shared · Language, roles and time

language-command-description = Ouvrir les paramètres de langue
language-menu-title = Paramètres de langue
language-selected = Sélectionné
language-set = Langue définie sur { $arg0 }
role-custodian = Gardien
role-member = Membre
role-moderator = Modérateur
role-new-player = Nouveau joueur
time-hours-minutes = { $arg0 }h { $arg1 }m
time-minutes = { $arg0 }m

## Shared · Menu presentation

bedrock-menu-back = Retour
bedrock-menu-hotkey = Action rapide (F)
bedrock-menu-primary = Action principale
bedrock-menu-scroll-down = Descendre
bedrock-menu-scroll-up = Monter
bedrock-menu-secondary = Action secondaire
interface-fov-apply = Appliquer le FOV
interface-fov-custom = Saisir une valeur exacte
interface-fov-description = Correspond au FOV du client ; seule la position du menu change. Par défaut : 70°.
interface-fov-input = FOV du client : entier de 30 à 110
interface-fov-invalid = Saisis un nombre entier de 30 à 110.
interface-fov-title = Adaptation du champ de vision
interface-scale-current = Actuel : { $arg0 }
interface-scale-description = La taille du texte multiplie celle du menu ; les deux groupes se règlent à la molette.
interface-scale-layout-title = Taille du menu
interface-scale-menu-title = Interface et texte
interface-scale-native-hint = Ces réglages concernent les menus spatiaux Java. Le texte des formulaires natifs dépend du client.
interface-scale-preview = Aperçu du texte Aa 123
interface-scale-reset = Rétablir les deux à 100%
interface-scale-text-title = Taille du texte
menu-rhythm-game-display = Joue au jeu de rythme
menu-usage-display-mm = <aqua><bold>Dans le menu</bold></aqua>
url-dialog-confirm = Ouvrir
url-dialog-hint = Le client demandera une seconde confirmation pour ouvrir le lien.
url-dialog-question-rich = Ouvrir { $label } ?
url-dialog-title = Ouvrir { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Mods recommandés : { $mods }. Cliquez sur un nom pour télécharger.
client-version-behind-mm = <white>Votre client <yellow>{ $clientVersion }</yellow> est plus ancien que le serveur <green>{ $serverVersion }</green>. Une mise à jour est conseillée.</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> joueur proche est <gold>invisible</gold>
       *[other] <gold>{ $count }</gold> joueurs proches sont <gold>invisibles</gold>
    }</yellow> <gray>·</gray> <gray>le plus proche à environ { $distance ->
        [one] { $distance } bloc
       *[other] { $distance } blocs
    }</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = { $player } se faufile par la porte de derrière
    .item-2 = { $player } découvre le chemin secret
    .item-3 = { $player } surgit des buissons
presence-quit-templates =
    .item-1 = { $player } part sur la pointe des pieds
    .item-2 = { $player } part sauvegarder la partie
    .item-3 = Le monde réel rappelle { $player }
welcome-first-join-mm = <gold><bold>Bienvenue sur MIK !</bold></gold><newline><gray>Commencez par consulter le <click:open_url:'https://mcmik.top'><yellow>site web</yellow></click> et le <click:open_url:'https://mcmik.top/wiki'><yellow>wiki</yellow></click>. Vous pouvez aussi utiliser <yellow>/menu</yellow> pour ouvrir le menu principal.</gray>
welcome-return-mm = <gold><bold>Bon retour parmi nous !</bold></gold><newline><gray>Votre dernière visite remonte à <yellow>{ $arg0 ->
        [one] { $arg0 } jour
       *[other] { $arg0 } jours
    }</yellow>. Utilisez <yellow>/menu</yellow> pour accéder rapidement aux fonctions courantes.</gray>

## Player · Guidance

tip-label = CONSEIL

## Player · AFK

afk-already-afk-mm = <gray>Vous êtes déjà <gold>AFK</gold></gray>
afk-command-description = Activer le statut AFK
afk-default-statuses =
    .item-1 = s'est éloigné du clavier
    .item-2 = entré en mode d'économie d'énergie
    .item-3 = passé à l'arrière-plan
    .item-4 = regarde dans le vide
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>est maintenant AFK :</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>a laissé un message :</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>a changé son statut en :</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>est de retour au clavier</green>
    .item-2 = <gray>*</gray> <player> <green>est de retour</green>
afk-not-afk-mm = <gray>Vous n'êtes pas actuellement <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>Le statut AFK ne peut pas dépasser { $arg0 ->
        [one] <white>{ $arg0 }</white> caractère
       *[other] <white>{ $arg0 }</white> caractères
    }</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · correspondances : { $arg2 ->
        [one] { $arg2 } enregistrement
       *[other] { $arg2 } enregistrements
    } · historique : { $arg3 }
player-association-header = [Association de connexion] { $arg0 } partage un historique d’adresses avec { $arg1 ->
        [one] { $arg1 } joueur
       *[other] { $arg1 } joueurs
    }.
player-association-hover = Des historiques réseau correspondent. Ceci sert uniquement d'aide à la vérification ; les adresses ne sont pas affichées.
player-association-more-addresses = Encore { $arg0 ->
        [one] { $arg0 } enregistrement
       *[other] { $arg0 } enregistrements
    }
player-association-more-players = Encore { $arg0 ->
        [one] { $arg0 } joueur
       *[other] { $arg0 } joueurs
    }
player-association-time-days = il y a { $arg0 ->
        [one] { $arg0 } jour
       *[other] { $arg0 } jours
    }
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } enregistrement
       *[other] { $arg1 } enregistrements
    })
player-association-time-separator = ,
player-association-time-today = aujourd'hui

## Player · Profile and tab list

nametag-clear-all-error = Erreur lors de la suppression des étiquettes. Veuillez réessayer plus tard
nametag-clear-all-success = ✔ Toutes les étiquettes ont été effacées
nametag-clear-error = Erreur lors de la suppression { $arg0 }
nametag-clear-success = ✔ { $arg0 } effacé
nametag-click-fill-command = Cliquez pour remplir la commande
nametag-command-description = Personnaliser les étiquettes
nametag-copy-hover = Copier le code MiniMessage dans le presse-papiers
nametag-copy-label = [copie]
nametag-effect-label = Résultat
nametag-empty-content-rich = Le contenu ne peut pas être vide. Pour l'effacer, utilisez { $command }
nametag-format-docs = Documentation du formatage
nametag-leading-label = Préfixe du nom
nametag-no-permission = Vous n'êtes pas autorisé à utiliser cette commande
nametag-online-editor = Éditeur en ligne
nametag-preview-label = Aperçu
nametag-sample-message = exemple de message
nametag-set-error = Erreur lors de la définition de { $arg0 }. Veuillez réessayer plus tard
nametag-set-success = ✔ { $arg0 } défini
nametag-tip-customize-mm = Utilise <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> pour personnaliser le <light_purple>préfixe et le suffixe de ton nom</light_purple>.
nametag-title = Étiquettes de nom
nametag-too-long = { $arg0 } est trop long. Longueur maximale : { $arg1 ->
        [one] { $arg1 } caractère
       *[other] { $arg1 } caractères
    }
nametag-trailing-label = Suffixe du nom
nametag-unset = (non défini)
nametag-unsupported-placeholder-rich = Seules les variables PlaceholderAPI { $supported } sont prises en charge, pas { $unsupported }
nametag-user-data-load-failed = Impossible de charger les données utilisateur. Veuillez réessayer plus tard
tablist-footer-mspt-rich = MSPT : { $mspt }
tablist-footer-online-rich = Joueurs en ligne : { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS : { $tps }

## Player · Movement and teleportation

back-command-description = Retourner au précédent lieu de téléportation ou de mort
back-empty-mm = <gray>Aucun lieu de retour disponible</gray>
back-pending-mm = <gray>Téléportation, veuillez patienter</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>Retour de { $steps ->
        [one] { $steps } position
       *[other] { $steps } positions
    }</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>La téléportation a échoué. Veuillez réessayer</red>
back-tip-return-mm = Utilisez <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> pour revenir à votre position avant la dernière téléportation ou mort.
back-tip-undo-mm = Utilisez <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> pour annuler votre dernier <yellow>/back</yellow>.
back-undo-empty-mm = <gray>Aucun <white>/back</white> à annuler</gray>
back-undo-success-mm = <green>Dernier <white>/back</white> annulé</green>
back-undo-world-missing-mm = <red>Le monde cible n'est pas disponible. Impossible d'annuler</red>
back-world-missing-mm = <red>Le monde cible n'est pas disponible. Impossible de revenir</red>
boundary-near-mm = <yellow>Vous approchez de la frontière du monde</yellow>
boundary-reached-mm = <red>Vous avez atteint la frontière du monde</red>
fly-command-description = Activer ou désactiver le vol personnel
fly-disabled = Vol désactivé
fly-enabled = Vol activé
fly-native-mode = Votre accès /fly est désactivé. Ce mode de jeu permet toujours de voler.
fly-speed-click = Régler la vitesse de vol sur { $arg0 }x
fly-speed-current = Vitesse de vol : { $arg0 }x
fly-speed-presets = Préréglages
fly-speed-reset = Vitesse de vol réinitialisée à 1x
fly-speed-reset-action = Réinitialiser
fly-speed-reset-click = Réinitialiser la vitesse de vol à 1x
fly-speed-set = Vitesse de vol réglée sur { $arg0 }x
fly-state-disabled = Désactivé
fly-state-enabled = Activé
fly-status = Vol : { $arg0 } | Vitesse : { $arg1 }x
flyspeed-command-description = Voir ou régler la vitesse de vol personnelle
home-action-create = Créer un domicile
home-action-delete = Supprimer le domicile
home-action-icon = Définir l'icône en main
home-action-teleport = Se téléporter
home-action-update = Mettre à jour la position
home-already-exists = Un domicile porte déjà ce nom
home-back = Retour
home-confirm-delete = Confirmer la suppression
home-confirm-delete-detail-rich = Supprimer la maison { $home }
home-confirm-delete-warning = Cette action est irréversible
home-confirm-update = Confirmer la modification
home-confirm-update-detail-rich = Modifier la maison { $home } vers cet emplacement
home-delete-command-description = Supprimer une maison
home-delete-usage-desc = Supprimer une maison
home-deleted-rich = Maison { $home } supprimée
home-distance = Distance : { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } bloc
       *[other] { $arg0 } blocs
    }
home-distance-other-world = autre monde
home-empty-description = Placez-vous à l'endroit où enregistrer une maison
home-empty-title = Pas encore de maisons
home-icon = Icône : { $arg0 }{ $arg1 }
home-icon-default-note = · par défaut
home-icon-hand-invalid = L'objet dans votre main principale ne peut pas être utilisé comme icône de maison
home-icon-updated-rich = Icône de la maison { $home } mise à jour
home-invalid-icon = Ce matériau ne peut pas être utilisé comme icône de maison
home-invalid-name = Les noms de maison ne peuvent pas contenir les caractères < > :
home-list-rich = Maisons : { $homes }
home-location = Localisation : { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = Les données de localisation de cette maison ne sont pas valides
home-max-reached = Vous pouvez définir au maximum { $arg0 } maisons
home-menu-title = Maisons
home-no-homes = Vous n'avez pas encore défini de maisons
home-not-found-rich = La maison { $home } n'existe pas
home-set-command-description = Définir une maison à votre emplacement actuel
home-set-rich = Maison { $home } définie
home-set-usage-desc = Définir une maison à votre emplacement actuel
home-summary-count = Définies : { $arg0 } / { $arg1 }
home-teleport-command-description = Se téléporter vers une maison
home-teleport-failed = La téléportation a échoué. Veuillez réessayer
home-teleported-rich = Téléportation vers la maison { $home } effectuée
home-tip-create-mm = Enregistrez votre position avec <green><u><click:suggest_command:'/sethome '>/sethome nom</click></u></green>, puis revenez avec <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>.
home-tip-return-mm = Ouvrez <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> et choisissez un foyer pour vous téléporter.
home-updated-rich = Emplacement de la maison { $home } mis à jour
home-usage-book = Aide sur les commandes
home-usage-delhome = /delhome <name>  Supprimer une maison
home-usage-home = /home tp <name>  Téléportez-vous directement
home-usage-icon = /home icon <material> <name>  Définir une icône
home-usage-name-rule = Les noms de maison ne peuvent pas contenir < > :
home-usage-sethome = /sethome <name>  Enregistrez votre position actuelle
home-world = Monde : { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Annuler le dernier /back
teleport-accept-command-description = Accepter la demande de téléportation en attente
teleport-allow = Téléportations par d'autres joueurs
teleport-block-afk = Bloquer pendant AFK
teleport-denied = { $player } refuse actuellement les téléportations par d'autres joueurs
teleport-deny-command-description = Refuser la demande de téléportation en attente
teleport-force-command-description = Forcer la téléportation en ignorant les préférences du joueur (administrateurs uniquement)
teleport-menu-title = Confidentialité de téléportation
teleport-moved-here = Vous avez été téléporté ici par { $arg0 }
teleport-policy-always-allow = Toujours autoriser
teleport-policy-always-deny = Toujours refuser
teleport-policy-require-consent = Exiger mon accord
teleport-request-accept = Accepter
teleport-request-approved = { $player } a accepté votre demande de téléportation
teleport-request-deny-action = Refuser
teleport-request-expired = La demande de téléportation a expiré
teleport-request-failed = La téléportation n'a pas pu être effectuée. Veuillez réessayer
teleport-request-none = Vous n'avez aucune demande de téléportation en attente
teleport-request-received = { $sender } souhaite vous téléporter : { $command }
teleport-request-rejected = Demande de téléportation refusée
teleport-request-sent = Demande de téléportation envoyée à { $player }
teleport-summary-afk = Toujours autorisées, sauf pendant l'AFK
teleport-summary-allow = Téléportations par d'autres joueurs toujours autorisées
teleport-summary-consent = Les téléportations par d'autres joueurs exigent mon accord
teleport-summary-deny = Téléportations par d'autres joueurs toujours refusées
tpany-command-description = Se téléporter vers la position d'un joueur, en ligne ou hors ligne
tpany-done-offline-rich = Téléportation vers la dernière position connue de { $player } effectuée { $world } { $location }
tpany-done-online-rich = Téléportation vers la position actuelle de { $player } effectuée
tpany-not-found-rich = Position de { $player } introuvable
tpany-usage = Utilisation
tpany-usage-command = /tpany <player>
tpany-usage-desc = Se téléporter vers la position d'un joueur, y compris sa position hors ligne enregistrée

## Player · PVP

pvp-admin-menu-title = Gestion du JcJ - Page { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = Les montures peuvent être attaquées
pvp-allow-mounted-damage-desc = Si cette option est activée, un autre joueur peut attaquer la créature que vous chevauchez lorsque vous avez tous les deux activé le JcJ
pvp-auto-enable-desc = Attaquer deux fois de suite un joueur ayant activé le JcJ
pvp-auto-enable-label = Activation automatique
pvp-auto-enable-warning-actionbar-mm = <yellow>Attaquez encore une fois pour activer automatiquement le JcJ</yellow>
pvp-auto-enabled-actionbar-mm = <green>JcJ activé automatiquement</green>
pvp-back-admin = Retour à la gestion du JcJ
pvp-combat-locked-mm = <red>Vous ne pouvez pas désactiver le JcJ en combat</red> <dark_gray>|</dark_gray> <gray>Temps restant : { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> seconde
       *[other] <yellow>{ $arg0 }</yellow> secondes
    }</gray>
pvp-combat-tag-label = Temps de recharge du combat
pvp-combat-tag-value = { $arg0 } seconde
pvp-command-description = Modifier le statut JcJ
pvp-enable = Activer le PvP
pvp-enable-desc = Si cette option est activée, vous pouvez attaquer les autres joueurs ayant activé le JcJ, et inversement
pvp-enable-on-death = Activer le JcJ à la mort
pvp-enable-on-death-desc = Si cette option est activée, le JcJ s'active automatiquement à votre mort
pvp-enable-on-death-label = Activation à la mort
pvp-enabled-on-death-mm = <yellow>Le JcJ a été activé à votre mort</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Ouvrir la gestion du JcJ des joueurs en ligne</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Ouvrir les paramètres JcJ</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Gérer un joueur en ligne</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Afficher votre statut JcJ</gray>
pvp-help-title-mm = <gold><bold>Commandes JcJ</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Modifier votre statut JcJ</gray>
pvp-menu-title = Paramètres PvP
pvp-mob-protected-actionbar-mm = <yellow>Cette créature est protégée des dégâts JcJ</yellow>
pvp-mob-protection-label = Protection des créatures
pvp-mounted-damage-label = Dégâts aux montures
pvp-next-page = Page suivante
pvp-no-permission-mm = <red>Vous n'êtes pas autorisé à gérer le statut JcJ d'un autre joueur</red>
pvp-player-not-found-mm = <red>Joueur en ligne introuvable</red>
pvp-prev-page = Page précédente
pvp-protect-mobs = Protéger vos créatures
pvp-protect-mobs-desc = Protéger les créatures que vous avez apprivoisées ou que vous chevauchez
pvp-set-by-staff-mm = <aqua>L'équipe a défini votre statut JcJ sur</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = Statut JcJ de { $arg0 } défini sur { $arg1 }
pvp-set-other-rich = Statut JcJ de { $player } défini sur { $state }
pvp-state-label = JcJ
pvp-state-off = Désactivé
pvp-state-on = Activé
pvp-status-player = Joueur : { $arg0 }
pvp-status-player-rich = Joueur : { $player }
pvp-status-title-mm = <gold><bold>Statut JcJ</bold></gold>
pvp-summary-disabled = JcJ désactivé
pvp-summary-enabled = JcJ activé
pvp-target-disabled-actionbar-mm = <yellow>Ce joueur n'a pas activé le JcJ</yellow>
pvp-target-menu-title = Paramètres JcJ - { $arg0 }
pvp-tip-protection-mm = Activez ou désactivez votre PVP avec <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple>. <green>Les deux joueurs doivent l’activer</green> pour combattre.
pvp-toggled-off-mm = <gray>JcJ désactivé</gray> <dark_gray>|</dark_gray> <gray>Les autres joueurs ne peuvent pas vous attaquer</gray>
pvp-toggled-on-mm = <green>JcJ activé</green> <dark_gray>|</dark_gray> <gray>Vous pouvez désormais combattre les autres joueurs ayant activé le JcJ</gray>

## Player · Effects

effect-cleanup-all = [Supprimer tous les effets]
effect-cleanup-blocked = Les effets peuvent être supprimés uniquement en mode Créatif ou Spectateur, hors combat PVP.
effect-cleanup-expired = Cette demande de suppression a expiré.
effect-cleanup-negative = [Supprimer les effets négatifs]
effect-cleanup-prompt-rich = Vous avez reçu des effets négatifs à cause de { $source }. Les supprimer ?
effect-cleanup-result = { $count ->
        [one] { $count } effet supprimé.
       *[other] { $count } effets supprimés.
    }
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] ce nuage de potion persistante
        [ARROW] cette flèche
        [ATTACK] cette attaque
        [COMMAND] cette commande
        [FOOD] cette nourriture
        [PLUGIN] ce plugin
        [POTION_DRINK] cette potion bue
        [POTION_SPLASH] cette potion jetable
        [WARDEN] ce Warden
        [WITHER_ROSE] cette rose de Wither
       *[OTHER] cette source inconnue
    }

## Player · Trash

trash-clear = Vider
trash-clear-hint = Cliquez pour supprimer tous les objets immédiatement ;
    sinon, ils seront supprimés automatiquement { $arg0 } secondes après la fermeture.
trash-command-description = Ouvrir la corbeille
trash-sort = Trier
trash-sort-hint = Fusionner les piles identiques et trier les objets par type
trash-tip-dispose-mm = Ouvrez la <yellow>corbeille</yellow> avec <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> et déposez-y les objets dont vous ne voulez plus.
trash-title = Corbeille

## Player · Utilities

hat-command-description = Porter l'objet tenu dans votre main principale
hat-empty-hand-mm = <red>Aucun objet dans votre main principale</red> <gray>Tenez un bloc ou un objet, puis utilisez /hat</gray>
hat-success-mm = <green>Objet de la main principale équipé</green> <dark_gray>|</dark_gray> <gray>Les objets de votre main et de votre emplacement de casque ont été échangés</gray>
hat-tip-wear-mm = Utilise <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> pour porter l’objet de ta <yellow>main principale</yellow> sur la tête.
removeitems-command-description = Supprimer les objets au sol
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Suppression effectuée : { $countValue ->
        [one] { $count } objet au sol
       *[other] { $count } objets au sol
    } · monde { $world } { $location } { $radius }
removeitems-no-world = Impossible de déterminer le monde
removeitems-radius = rayon { $arg0 ->
        [one] { $arg0 } bloc
       *[other] { $arg0 } blocs
    }
removeitems-tip-usage-mm = Utilise <aqua><u><click:suggest_command:'/rmitems '>/rmitems [rayon]</click></u></aqua> pour supprimer les <yellow>objets au sol</yellow> à proximité.
self-kick-command-description = Te déconnecter du serveur
self-kick-reason-too-long = La raison ne peut pas dépasser { $arg0 ->
        [one] { $arg0 } caractère
       *[other] { $arg0 } caractères
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Vous avez quitté le serveur de votre propre initiative. Motif : { $reason }
       *[third] { $player } quitte volontairement le serveur : { $reason }
    }
    .item-1 = { $player } choisit une sortie surprise : { $reason }
    .item-2 = { $player } choisit de partir à cause de { $reason } !
    .item-3 = { $player } quitte le serveur avec cette explication : { $reason }
    .item-4 = { $player } demande sa propre expulsion : { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Vous avez quitté le serveur de votre propre initiative.
       *[third] { $player } quitte volontairement le serveur.
    }
    .item-1 = { $player } choisit l’auto-expulsion !
    .item-2 = { $player } part sans un mot.
    .item-3 = { $player } réussit une sortie impeccable.
    .item-4 = { $player } appuie sur son propre bouton de sortie.

## Community · Chat and mentions

chat-copy-hover = Clic gauche pour copier le message
chat-delay-3s = 3 secondes
chat-delay-5s = 5 secondes
chat-delay-7s = 7 secondes
chat-delay-cancel-command-description = Annuler les messages retardés en attente
chat-delay-cancelled = Message en attente annulé
chat-delay-current = Délai actuel du chat : { $arg0 }
chat-delay-nothing-to-cancel = Aucun message en attente à annuler
chat-delay-off = Désactivé
chat-delay-preview-marker = [En attente]
chat-delay-queued = Le message sera envoyé dans { $arg0 } ; utilisez /cancel ou /c pour annuler
chat-item-empty-hover = Aucun objet en main principale
chat-mention-all-hover = Avertissez tous les joueurs visibles sur cette chaîne
chat-msg-command-description = Envoyez un message privé et passez au chat privé
chat-player-hover = Clic gauche pour démarrer un message privé
chat-private-enter = Passé au chat privé avec { $arg0 }
chat-private-label = MSG
chat-private-no-reply-target = Aucune cible de chat privé récente n'est en ligne
chat-private-self = Vous ne pouvez pas démarrer une conversation privée avec vous-même
chat-private-target-not-found = Joueur en ligne introuvable : { $arg0 }
chat-private-target-offline = La cible du chat privé est hors ligne ; est revenu au chat public
chat-private-tip-channel-mm = Utilise <aqua><u><click:suggest_command:'/msg '>/msg joueur</click></u></aqua> pour passer en discussion privée et <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> pour revenir au canal public.
chat-public-command-description = Changer de canal de discussion public
chat-public-enter = Passé au chat public
chat-reply-command-description = Répondez à votre dernière cible de chat privé
chat-settings-menu-title = Paramètres de discussion
chat-social-account-label = Compte de la plateforme
chat-social-bound-player-label = Joueur lié
chat-social-name-label = Nom social
chat-social-unbound = Non lié à un joueur Minecraft
chat-staff-command-description = Changer de canal de discussion du personnel
chat-staff-permission-missing = Permission de discussion de l'équipe manquante ; retour au chat public
mention-action-bar = Notification dans la barre d'action
mention-action-bar-text = { $sender } vous a mentionné
mention-alerts = Alertes de mention
mention-menu-title = Alertes de mention
mention-mute-afk = Couper pendant l'AFK
mention-sound = Son d'alerte
mention-summary-action-bar = barre d'action
mention-summary-disabled = Alertes désactivées
mention-summary-enabled = Alertes activées
mention-summary-enabled-afk = Alertes activées, désactivées pendant l'AFK
mention-summary-modes-afk = { $arg0 }, coupées pendant l'AFK
mention-summary-sound = son
staffchat-enter = Discussion de l'équipe rejointe

## Community · Announcements

announcement-empty = Aucune annonce
announcement-first-page = Annonces les plus récentes
announcement-last-page = Annonces les plus anciennes
announcement-more-count = { $arg0 } autres non affichées
announcement-new-count = { $arg0 } nouvelles
announcement-newer = Annonces plus récentes
announcement-older = Annonces plus anciennes
announcement-reload = Recharger les annonces
announcement-reload-command-description = Recharger le fichier des annonces
announcement-reload-failed = Impossible de recharger les annonces ; les précédentes restent disponibles
announcement-reloaded = Annonces rechargées
announcement-scroll-hint = Molette pour défiler latéralement
announcement-unread-count = { $arg0 } non lues
announcement-updated = Annonces mises à jour
announcement-view-all = Voir toutes les annonces
announcement-view-command-description = Voir les annonces du serveur

## Community · Identity and social platforms

identity-admin-binding-not-found = Aucune liaison joueur-plateforme non ambiguë n'a été trouvée.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Suppression pour { $arg1 } : { $arg2 ->
        [one] { $arg2 } liaison d’identité
       *[other] { $arg2 } liaisons d’identité
    } ({ $arg0 }).
identity-admin-command-description = Consulter ou révoquer les liaisons d'identité externes
identity-admin-help = /bindadmin lookup <joueur-ou-UUID>
    /bindadmin unlink <joueur-ou-UUID> <plateforme> confirm
identity-admin-lookup-empty = Aucune liaison trouvée pour ce joueur.
identity-admin-lookup-title = Liaisons de { $arg0 } :
identity-binding-details = · { $arg0 } · portée { $arg1 } · { $arg2 }
identity-binding-not-owned = Vous n'avez aucune liaison pour cette plateforme.
identity-binding-unlinked = Suppression effectuée : { $arg1 ->
        [one] { $arg1 } liaison d’identité
       *[other] { $arg1 } liaisons d’identité
    } ({ $arg0 }).
identity-bindings-empty = Vous n'avez lié aucun compte de plateforme externe.
identity-bindings-title = Identités externes liées :
identity-bindings-unlink-hint = Utilisez /bind unlink <plateforme> pour supprimer toutes les liaisons d'une plateforme.
identity-code-cancelled = Le code de liaison { $arg0 } inutilisé a été annulé.
identity-code-copy = Cliquer pour copier la commande de liaison complète
identity-code-expiry = Le code est valable 5 minutes et utilisable une seule fois. En générer un autre l'invalide.
identity-code-issued = Code de liaison { $arg0 } généré :
identity-code-not-pending = Il n'y a aucun code de liaison { $arg0 } inutilisé.
identity-code-redeem = Dans le groupe { $arg0 } choisi, mentionnez le bot et envoyez : { $arg1 }
identity-command-description = Lier Minecraft à une identité de plateforme externe
identity-help = Commandes de liaison d'identité :
    /bind qq — générer rapidement une commande de liaison QQ
    /bind <plateforme> — générer un code à usage unique
    /bind list — afficher les liaisons existantes
    /bind cancel <plateforme> — annuler un code inutilisé
    /bind unlink <plateforme> — supprimer toutes les liaisons d'une plateforme
identity-notify-linked = Votre identité { $arg0 } a été vérifiée. Utilisez /bind list pour la voir ou /bind unlink { $arg0 } pour la supprimer.
identity-notify-unlinked = Votre liaison d'identité { $arg0 } a été supprimée.
identity-platform-none = aucune
identity-platforms = Plateformes prises en charge : { $arg0 }
identity-scope-notice = Cette plateforme sépare les identités par groupe. Effectuez la liaison dans le groupe où vous utiliserez le bot ; chaque groupe doit être lié séparément.
identity-storage-error = L'opération de base de données des liaisons a échoué. Contactez un administrateur.
identity-unavailable = La liaison d'identité est actuellement indisponible. Contactez un administrateur.
identity-unsupported-platform = Plateforme non prise en charge : { $arg0 }
social-admin-conversations-empty = Aucune conversation observée pour { $arg0 }.
social-admin-conversations-title = Conversations observées pour { $arg0 } :
social-admin-help = /social platforms · /social status [plateforme] · /social reload [plateforme] · /social conversations <plateforme>
social-admin-platform = - { $arg0 } ({ $arg1 }) : { $arg2 }
social-admin-reloaded = La plateforme sociale { $arg0 } a été rechargée.
social-admin-reloaded-all = Toutes les plateformes sociales ont été rechargées.
social-admin-status = { $arg0 } · { $arg1 } · génération { $arg2 } · conversations { $arg3 }
social-admin-title = Plateformes sociales
social-admin-unknown = Plateforme sociale inconnue : { $arg0 }
social-binding-already-linked = Cette identité est déjà liée au joueur Minecraft { $arg0 }.
social-binding-code-invalid = Le code de vérification est invalide ou expiré. Générez-en un nouveau avec /bind { $arg0 } dans Minecraft.
social-binding-external-in-use = Cette identité est déjà liée à un autre joueur Minecraft.
social-binding-help = Exécutez /bind { $arg0 } dans Minecraft.
    Copiez la commande complète générée.
    Envoyez la commande ici.
    Utilisez /monprofil pour vérifier la liaison.
social-binding-identity-missing = Ce message ne contient aucune identité de membre fiable ; la liaison est donc impossible.
social-binding-linked = Lié au joueur Minecraft { $arg0 }.
social-binding-not-bound = Cette identité de plateforme n’est pas liée.
social-binding-platform-mismatch = Ce code de vérification a été émis pour une autre plateforme.
social-binding-player-scope-in-use = Ce joueur Minecraft possède déjà une autre identité liée dans cette conversation.
social-binding-rate-limited = Trop de tentatives ont échoué. Réessayez dans environ { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }.
social-binding-status-unbound = Cette identité n’est pas liée. Utilisez /lier avec un code de vérification.
social-binding-unavailable = La liaison d’identité est temporairement indisponible.
social-binding-unlink-prompt = La dissociation supprime le lien entre cette identité et Minecraft. Envoyez /délier confirmer pour continuer.
social-binding-unlinked = Dissocié du joueur Minecraft { $arg0 }.
social-command-description = Gérer les adaptateurs de plateformes sociales
social-duration-days = { $arg0 ->
        [one] { $arg0 } jour
       *[other] { $arg0 } jours
    } { $arg1 ->
        [one] { $arg1 } heure
       *[other] { $arg1 } heures
    } { $arg2 ->
        [one] { $arg2 } minute
       *[other] { $arg2 } minutes
    }
social-duration-hours = { $arg0 ->
        [one] { $arg0 } heure
       *[other] { $arg0 } heures
    } { $arg1 ->
        [one] { $arg1 } minute
       *[other] { $arg1 } minutes
    }
social-duration-minutes = { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }
social-help-command-description = Afficher cette liste de commandes
social-link-command-description = Lier votre compte Minecraft avec un code du jeu
social-online-command-description = Afficher les joueurs en ligne
social-profile-afk = En ligne · AFK
social-profile-avatar-alt = Visage Minecraft
social-profile-command-description = Afficher votre profil ; les membres confirmés peuvent chercher par nom, mention ou réponse
social-profile-first-joined-label = Première connexion
social-profile-language-label = Langue
social-profile-last-seen-label = Dernière présence
social-profile-member-required = Seuls les membres, modérateurs ou gardiens liés peuvent consulter le profil d’un autre joueur.
social-profile-offline = Hors ligne
social-profile-online = En ligne · actif
social-profile-play-time-label = Temps de jeu
social-profile-player-label = Joueur
social-profile-player-not-found = Ce joueur Minecraft est introuvable.
social-profile-presence-label = Présence
social-profile-target-ambiguous = Choisissez une seule cible : un nom de joueur, une mention ou l’auteur du message répondu.
social-profile-target-unbound = L’identité mentionnée ou destinataire de la réponse n’est pas liée à Minecraft.
social-profile-uuid-label = UUID
social-query-active-label = Actifs
social-query-no-players = Aucun joueur n’est actuellement en ligne.
social-query-online-label = En ligne
social-query-players-label = Joueurs
social-query-unknown = Commande inconnue. Envoyez /aide pour afficher les commandes disponibles.
social-query-uptime-label = Durée de fonctionnement
social-reply-hidden = Cette réponse a été masquée par la politique de sécurité du contenu du serveur.
social-state-disabled = désactivée
social-state-failed = échec
social-state-ready = prête
social-state-starting = démarrage
social-state-stopped = arrêtée
social-status-command-description = Afficher les joueurs, TPS, MSPT et la durée
social-title-command-help = Aide des commandes
social-title-content-hidden = Contenu masqué
social-title-identity-linking = Liaison du compte
social-title-identity-unlinking = Dissociation du compte
social-title-online-players = Joueurs en ligne
social-title-player-profile = Profil du joueur
social-title-server-status = État du serveur
social-title-unknown-command = Commande inconnue
social-unlink-command-description = Supprimer ce lien d’identité après confirmation

## Community · AI and knowledge

ai-busy = L’IA traite trop de demandes ; réessayez dans un instant
ai-cleared = Historique de conversation IA effacé
ai-command-description = Interroger l’assistant IA en jeu
ai-disabled = L’assistant IA est désactivé ou non configuré
ai-knowledge-curation-failed = Échec de l’organisation des connaissances : { $arg0 }
ai-knowledge-curation-finished = Organisation terminée : appliqués { $arg0 } · ignorés { $arg1 } · échecs { $arg2 } · quarantaine { $arg3 } · traités { $arg4 }
ai-knowledge-curation-started = Organisation des connaissances démarrée.
ai-knowledge-disabled = La base de connaissances est désactivée.
ai-knowledge-index-dirty = Réconciliation de l’index nécessaire ; recherche privée bloquée jusqu’à la synchronisation.
ai-knowledge-last-failure = Dernier échec : { $arg0 }
ai-knowledge-last-sync = Dernière synchronisation : { $arg0 }
ai-knowledge-learning-disabled = L’apprentissage des connaissances est désactivé.
ai-knowledge-operation-failed = Échec de l’opération sur les connaissances : { $arg0 }
ai-knowledge-operation-started = Opération sur les connaissances démarrée.
ai-knowledge-protected = Le document { $arg0 } est désormais protégé.
ai-knowledge-rollback = Document { $arg0 } restauré en révision { $arg1 }.
ai-knowledge-status = Connaissances : { $arg0 ->
        [one] { $arg0 } document public
       *[other] { $arg0 } documents publics
    } / { $arg1 ->
        [one] { $arg1 } fragment de texte
       *[other] { $arg1 } fragments de texte
    } · { $arg2 ->
        [one] { $arg2 } document privé
       *[other] { $arg2 } documents privés
    } / { $arg3 ->
        [one] { $arg3 } fragment de texte
       *[other] { $arg3 } fragments de texte
    }
ai-knowledge-sync-failed = Échec de la synchronisation des connaissances : { $arg0 }
ai-knowledge-sync-started = Synchronisation des connaissances démarrée.
ai-knowledge-synchronized = { ai-knowledge-status } · Synchronisation terminée.
ai-knowledge-unprotected = Le document { $arg0 } n’est plus protégé.
ai-learning-failure = Échec de l’apprentissage : { $arg0 }
ai-learning-status = Apprentissage : candidats { $arg0 } · extraction { $arg1 } · en attente { $arg2 } · extractions abandonnées { $arg3 } · candidats abandonnés { $arg4 } · quarantaine { $arg5 }
ai-memory-empty = Aucun souvenir privé pour { $arg0 }.
ai-memory-list = Souvenirs privés pour { $arg0 } : { $arg1 }
ai-memory-not-found = Souvenir privé { $arg0 } introuvable.
ai-memory-removed = Suppression définitive pour { $arg1 } : { $arg0 ->
        [one] { $arg0 } souvenir privé
       *[other] { $arg0 } souvenirs privés
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Joueur inconnu dans le cache : { $arg0 }
ai-prompt-too-long = La question est trop longue ({ $arg0 ->
        [one] { $arg0 } caractère
       *[other] { $arg0 } caractères
    } maximum)
ai-reload-failed = Échec du rechargement de la configuration IA : { $arg0 }
ai-reloaded = Configuration IA rechargée ({ $arg0 })
ai-request-failed = La requête IA a échoué ; réessayez plus tard
ai-social-command-description = Interroger l’assistant IA partagé
ai-social-question-required = Ajoutez une question après la commande IA
ai-status-disabled = L’IA est désactivée
ai-status-enabled = IA active · fournisseur { $arg0 } · modèle { $arg1 } · outils { $arg2 }
ai-status-none = aucun
ai-thinking = Réflexion…
ai-usage = Utilisation : /ai <question> · /ai clear · /ai status
ai-worker-status = Tâches IA : actives { $arg0 } · en attente { $arg1 } · abandonnées { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Félicitations !</bold> Vous êtes maintenant <gold>Membre</gold>. Vous avez accès à davantage de commandes et de fonctionnalités, dont l'impression facile de schémas.</green>
governance-active-days-30 = Au moins 2 jours actifs sur 30 (actuellement { $arg0 })
governance-active-days-60 = Au moins 6 jours actifs sur 60 (actuellement { $arg0 })
governance-active-header = ─── Éligibilité de membre actif ───
governance-active-member = Actuellement membre
governance-active-member-age = Membre depuis au moins 30 jours
governance-active-playtime = Au moins 6 heures de jeu effectif sur 60 jours (actuellement { $arg0 })
governance-already-moderator = Vous avez déjà le rôle de modération.
governance-ballot-recorded = Votre vote anonyme est enregistré : { $arg0 }. Vous pouvez le modifier avant la clôture.
governance-ban-details = Proposé par { $arg0 } · Durée { $arg1 } · Motif : { $arg2 }
governance-ban-suffix = · Durée { $arg0 } · Motif : { $arg1 }
governance-choice-abstain = Abstention
governance-choice-no = Non
governance-choice-yes = Oui
governance-command-description = Voir les droits de gouvernance et les votes
governance-custodian-no-nomination = Les responsables techniques ne participent pas aux élections des modérateurs.
governance-custodian-no-resign = La procédure de démission des modérateurs ne concerne pas les responsables techniques.
# $arg0 (Number): duration; locale decides the unit form.
governance-days = { $arg0 ->
        [one] { $arg0 } jour
       *[other] { $arg0 } jours
    }
governance-error-ban-already-active = Ce joueur fait déjà l'objet d'une exclusion active.
governance-error-ban-proposer-ineligible = Seuls les membres actifs peuvent proposer un vote d'exclusion.
governance-error-ban-self = Vous ne pouvez pas proposer votre propre exclusion.
governance-error-candidate-ineligible = Un candidat à la modération doit être membre actif.
governance-error-nomination-not-open = Vous n’avez aucune candidature en cours.
governance-error-not-voter = Vous ne figurez pas sur la liste électorale figée pour ce vote.
governance-error-pause-already-active = La promotion de ce joueur est déjà suspendue.
governance-error-pause-already-member = Ce joueur est déjà membre ; sa promotion ne peut pas être suspendue.
governance-error-pause-end-invalid = La suspension de promotion doit se terminer dans le futur.
governance-error-player-unknown = Ce joueur n'a pas encore de dossier de gouvernance.
governance-error-removal-requires-petition = Un vote de révocation nécessite assez de soutiens.
governance-error-removal-self = Vous ne pouvez pas soutenir votre propre révocation.
governance-error-removal-sponsor-ineligible = Seuls les membres actifs peuvent soutenir une révocation.
governance-error-removal-sponsors-too-few = Il faut au moins deux autres membres actifs.
governance-error-removal-vote-open = Un vote de révocation est déjà en cours pour ce modérateur.
governance-error-termination-reason-invalid = Ce motif de clôture ne s'applique pas à ce vote.
governance-error-vote-already-open = Un vote de ce type est déjà en cours pour ce joueur.
governance-error-vote-closed = Ce vote est clos ou terminé.
governance-error-vote-cooldown = Le délai de trois jours après un quorum non atteint n'est pas écoulé.
governance-error-vote-subject-unknown = La personne concernée n'a pas encore de dossier de gouvernance.
governance-error-vote-termination-closed = Le délai de vote est passé ; il ne peut plus être retiré.
governance-error-vote-unknown = Vote introuvable.
governance-error-voters-too-few = Il faut au moins deux membres actifs pour ouvrir un vote.
governance-failure-majority = Les oui ne dépassent pas la moitié des bulletins
governance-failure-minimum-yes = Pas assez de votes oui
governance-failure-none = Aucun motif enregistré
governance-failure-quorum = Quorum non atteint
governance-failure-two-thirds = Les oui n'atteignent pas deux tiers des suffrages hors abstention
governance-history-header = ─── Résultats récents ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
# $arg0 (Number): duration; locale decides the unit form.
governance-hours = { $arg0 ->
        [one] { $arg0 } heure
       *[other] { $arg0 } heures
    }
governance-inactive-removal = { $arg0 } a quitté automatiquement la modération après 180 jours sans connexion.
governance-invalid-choice = Choisis yes, no ou abstain.
governance-invalid-duration = Durée invalide. Utilise par exemple 30m, 12h ou 3d.
governance-kind-appointment = Nomination
governance-kind-ban = Exclusion
governance-kind-removal = Révocation
governance-legacy-ban-kick = Une ancienne décision de gouvernance vous interdit l’accès. Durée : { $arg0 }. Motif : { $arg1 }
governance-menu-actions = Actions
governance-menu-back = Retour
governance-menu-cancel = Annuler
governance-menu-confirm = Confirmer
governance-menu-confirm-nominate = Lancer un vote pour votre nomination comme modérateur ?
governance-menu-confirm-resign = Démissionner du poste de modérateur ?
governance-menu-confirm-sponsor = Soutenir cette demande de révocation ?
governance-menu-confirm-withdraw = Retirer votre candidature ?
governance-menu-find-player = Chercher un autre joueur
governance-menu-history = Historique des votes
governance-menu-loading = Chargement du statut de gouvernance…
governance-menu-new-petition = Lancer une demande de révocation
governance-menu-nominate = Postuler comme modérateur
governance-menu-nomination-open = Votre vote de nomination est en cours
governance-menu-pause = Suspendre la promotion
governance-menu-pause-appeal = Voie de recours
governance-menu-pause-duration = Durée (ex. 2d)
governance-menu-pause-reason = Motif
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } soutiens
governance-menu-petitions = Demandes de révocation
governance-menu-player-name = Nom du joueur
governance-menu-players = Gestion des joueurs
governance-menu-recent-results = Résultats récents
governance-menu-refresh = Actualiser
governance-menu-resign = Démissionner
governance-menu-resume = Lever la suspension
governance-menu-resume-reason = Motif de la levée
governance-menu-staff-only = Seuls les modérateurs et responsables peuvent gérer les joueurs.
governance-menu-status = Votre statut
governance-menu-target = Nom du modérateur
governance-menu-title = Gouvernance communautaire
governance-menu-votes = Votes en cours
governance-menu-withdraw = Retirer la candidature
# $arg0 (Number): duration; locale decides the unit form.
governance-minutes = { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }
governance-no-active-ban = Aucune exclusion active
governance-no-active-pause = Aucune suspension de promotion active
governance-nomination-withdrawn = Ta candidature a été retirée. Le résultat sera annoncé automatiquement.
governance-none = Aucun
governance-not-member = Pas membre
governance-not-moderator = Vous n’avez pas le rôle de modération actuellement.
governance-operation-error = L'opération de gouvernance a échoué. Réessaie.
governance-own-choice-closed = Ton choix : { $arg0 }
governance-own-choice-open = Ton choix : { $arg0 } ; modifiable avant la clôture.
governance-own-not-voted = Vous aviez le droit de voter, mais vous n’avez pas voté.
governance-own-not-voted-hint = Vous n’avez pas voté. Ouvrez la gouvernance et sélectionnez ce vote.
governance-own-not-voted-open = Vous n’avez pas encore voté.
governance-own-not-voter = Vous ne figurez pas sur la liste électorale de ce vote.
governance-own-voted = Vous avez voté
governance-pause-cleared = Suspension de promotion levée pour { $arg0 }.
governance-pause-details = Suspension : { $arg0 } · Jusqu'au { $arg1 } · Recours : { $arg2 }
governance-pause-fields-required = Indique un motif et une voie de recours.
governance-pause-not-active = { $arg0 } n'a aucune suspension de promotion active.
governance-pause-notice = Ta promotion est suspendue. Motif : { $arg0 } · Jusqu'au { $arg1 } · Recours : { $arg2 }
governance-pause-set = Promotion de { $arg0 } suspendue jusqu'au { $arg1 }.
governance-petition-already-sponsored = Vous soutenez déjà cette révocation ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } soutiens · Jusqu'au { $arg3 }
governance-petition-started = Demande de révocation de { $arg0 } : { $arg1 }/{ $arg2 } soutiens ; jusqu'au { $arg3 }.
governance-petitions-header = ─── Demandes de révocation ───
governance-player-first-join = Première connexion : { $arg0 }
governance-player-header = ─── { $arg0 } · Dossier de gouvernance ───
governance-player-id = UUID : { $arg0 }
governance-player-last-login = Dernière connexion : { $arg0 }
governance-player-member-since = Membre depuis : { $arg0 }
governance-player-never-joined = Le joueur { $arg0 } n'a jamais rejoint ce serveur.
governance-promotion-account-age = Première connexion il y a au moins 3 jours
governance-promotion-header = ─── Conditions de promotion ───
governance-promotion-play-days = Jeu effectif sur au moins 2 jours (actuellement { $arg0 })
governance-promotion-playtime = Au moins 8 heures de jeu effectif (actuellement { $arg0 })
governance-resign-failed = Démission impossible. Contacte un responsable technique.
governance-resigned = { $arg0 } a démissionné de la modération.
governance-result-announcement = Vote #{ $arg0 } { $arg1 } ({ $arg2 }) : { $arg3 }{ $arg4 }
governance-result-failed = Rejeté ({ $arg0 })
governance-result-open = En cours
governance-result-passed = Adopté
governance-result-tally = { $arg0 } · Électeurs { $arg1 }, participation { $arg2 }, oui { $arg3 }, non { $arg4 }, abstention { $arg5 }
governance-result-terminated = Terminé ({ $arg0 })
governance-resume-notice = Ta suspension de promotion a été levée. Motif : { $arg0 }
governance-resume-reason-required = Indique pourquoi la suspension est levée.
governance-role-lookup-error = Statut de modérateur non vérifiable. Contacte un responsable technique.
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } secondes
    }
governance-storage-error = Erreur de données de gouvernance. Contacte un responsable technique.
governance-target-not-moderator = { $arg0 } n'est pas modérateur actuellement.
governance-termination-inactive = Modérateur parti après une longue inactivité
governance-termination-ineligible = Le candidat a perdu son statut de membre actif
governance-termination-resigned = Le modérateur a démissionné
governance-termination-unknown = Aucun motif enregistré
governance-termination-withdrew = Le candidat a retiré sa candidature
governance-vote-electorate = Électeurs { $arg0 } · Quorum { $arg1 }
governance-vote-finalized = Clôturé : { $arg0 }
governance-vote-header = ─── Vote #{ $arg0 } ───
governance-vote-in-progress = En cours · { $arg0 } participants ; détail des voix publié après clôture.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · Clôture { $arg3 } · Électeurs { $arg4 } · Participation { $arg5 } · Quorum { $arg6 }{ $arg7 }
governance-vote-period = Ouverture { $arg0 } · Clôture { $arg1 }
governance-vote-started = Vote de { $arg1 } #{ $arg0 } ouvert : { $arg2 } ; électeurs { $arg3 }, quorum { $arg4 }, clôture { $arg5 }. Ouvre Gouvernance pour les détails.
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Votes anonymes en cours ───

## World · Plots

plot-access-removed-for-you = Votre accès à la parcelle { $arg0 } a été retiré.
plot-admin-created = { $arg0 } enregistré pour { $arg1 } · { $arg2 }
plot-allowed = activé
plot-area-changed = Les limites ont changé. Actualisez et confirmez à nouveau.
plot-area-confirm = Remplacer les limites de « { $arg0 } » par la sélection ({ $arg1 ->
        [one] { $arg1 } cellule de construction
       *[other] { $arg1 } cellules de construction
    }) ? Les bâtiments restent en place.
plot-area-hint = //pos1 → //pos2 → enregistrer. La hache en bois sélectionne aussi les coins ; ajouter/soustraire pour plusieurs zones.
plot-area-replace-save = Remplacer les limites et enregistrer
plot-area-save = Enregistrer les limites
plot-area-saved = Limites de « { $arg0 } » enregistrées.
plot-arrival-removed = Point d'arrivée supprimé.
plot-arrival-set = Point d'arrivée enregistré.
plot-arrival-text-saved = Titre d'arrivée enregistré.
plot-atmosphere-saved = Ambiance enregistrée.
plot-back = Retour
plot-board-auto-notify-result = Alertes prévues pour { $arg0 ->
        [one] { $arg0 } joueur
       *[other] { $arg0 } joueurs
    }.
plot-board-auto-registered-text = La parcelle « { $arg0 } » a été enregistrée. Cadre des cellules construites : X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Avis de { $arg0 } à { $arg1 } { $arg2 }, { $arg3 }, { $arg4 } : { $arg5 }
plot-board-empty = Aucune annonce communautaire pour le moment.
plot-board-header = Avis et retours de la communauté ({ $arg0 } récents ; détails : /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Annonce
plot-board-kind-objection = Objection
plot-board-menu = Tableau communautaire
plot-board-notice-alert = { $arg0 } a publié un avis : { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } a formulé une objection : { $arg1 } · { $arg2 }
plot-board-rate-limited = Limite de publications atteinte. Réessayez plus tard.
plot-board-record-invalid = Avis introuvable, déjà traité ou action non autorisée.
plot-board-registered-alert = { $arg0 } a enregistré la parcelle « { $arg1 } » : { $arg2 }
plot-board-ruling-alert = Une décision concerne l’objection de { $arg0 } : { $arg1 } · { $arg2 }
plot-board-ruling-detail = Décision : { $arg0 }
plot-board-saved = Entrée { $arg0 } enregistrée.
plot-board-status-elapsed = Publié il y a au moins 72 heures
plot-board-status-open = En attente
plot-board-status-ruled = Décision rendue
plot-board-status-withdrawn = Retirée
plot-board-text-invalid = La description doit contenir 12 à 1000 caractères (8 minimum pour une décision).
plot-board-unavailable = Les avis ne sont pas chargés. Réessayez ou consultez https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } a retiré une objection : { $arg1 } · { $arg2 }
plot-board-write-failed = Impossible de mettre à jour le tableau. Réessayez plus tard.
plot-command-blocked = La protection a bloqué cette commande. Utilisez /fill ou /setblock directement dans votre parcelle ; faites vérifier les commandes /execute complexes par l’équipe.
plot-command-description = Gérer les parcelles et les projets publics
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } créé · { $arg1 }
        [feminine] { -plot } { $arg0 } créée · { $arg1 }
       *[other] { -plot } { $arg0 } créé · { $arg1 }
    }
plot-created-for-you = { $arg1 } a enregistré la parcelle { $arg0 } pour vous.
plot-deleted = Parcelle libérée ; le bâtiment reste en place.
plot-denied = désactivé
plot-empty-summary = Aucune parcelle ici.
plot-error-admin-owner-only = Modifier des membres ayant des pouvoirs de gestion exige la gestion des membres et des droits.
plot-error-area-disconnected = La parcelle doit former une zone continue.
plot-error-args = Arguments invalides ou manquants. Utilisez /plot help.
plot-error-arrival-location = Placez-vous sur un sol sûr dans la zone construite du projet.
plot-error-arrival-missing = Aucun point d'arrivée défini.
plot-error-arrival-private = Vous ne pouvez pas visiter ce projet privé.
plot-error-arrival-text = Titre invalide. Une seule ligne ; 32 caractères visibles au maximum pour le titre et 64 pour le sous-titre.
plot-error-arrival-unsafe = Ce point d'arrivée est indisponible ou n'est plus sûr.
plot-error-busy = Enregistrement en cours. Réessayez plus tard ; cette opération n’a pas été envoyée.
plot-error-flag = Droit inconnu. Choisissez une opération dans la liste des permissions.
plot-error-id = Identifiant absent ou ambigu. Utilisez /plot list.
plot-error-member-missing = Ce joueur n’est plus membre.
plot-error-member-required = Les nouveaux joueurs peuvent consulter les parcelles et déposer des objections. Le statut de membre est requis pour créer un projet.
plot-error-name = Le nom du projet doit contenir de 1 à 48 caractères.
plot-error-notice-text = Les annonces acceptent jusqu’à { $arg0 ->
        [one] { $arg0 } caractère
       *[other] { $arg0 } caractères
    }, sans limite de lignes ni de longueur par ligne ; les caractères de contrôle sont interdits.
plot-error-overlap = Cette zone de construction chevauche une autre parcelle.
plot-error-owner-member = propriétaire de la parcelle · Droits du groupe
plot-error-owner-only = désactivé · Administration
plot-error-permission-changed = Les droits, membres ou règles héritées ont changé. Prévisualisez à nouveau.
plot-error-permission-grant = Vous ne pouvez pas accorder des pouvoirs de gestion que vous ne possédez pas.
plot-error-permission-owner-scope = Seuls le propriétaire, le propriétaire du terrain parent ou le personnel peuvent modifier les droits du propriétaire.
plot-error-player-unknown = Joueur inconnu. Vérifiez le nom ou UUID.
plot-error-release-owner-only = désactivé · Supprimer la parcelle
plot-error-selection = Marquez deux coins avec //pos1 et //pos2.
plot-error-selection-changed = La sélection a changé. Confirme à nouveau.
plot-error-staff-only = Seuls les modérateurs ou gardiens peuvent gérer ou enregistrer les parcelles des autres joueurs.
plot-error-storage = Échec de l’opération. Réessayez plus tard ou contactez l’équipe du serveur.
plot-error-subplot-arrival = Déplacez d'abord le point d'arrivée de la parcelle mère hors de la sélection.
plot-error-subplot-children = Libérez les sous-parcelles avant de libérer la parcelle mère.
plot-error-subplot-depth = Les sous-parcelles sont limitées à un seul niveau.
plot-error-subplot-disconnected = Les cellules sélectionnées ne sont pas reliées. Divisez chaque partie séparément.
plot-error-subplot-overlap = La sélection chevauche une autre sous-parcelle.
plot-error-subplot-own-arrival = Gardez le point d'arrivée dans la nouvelle zone ou déplacez-le d'abord.
plot-error-subplot-owner = Vous n’avez pas la permission requise pour cette opération sur une sous-parcelle.
plot-error-subplot-parent = Marquez les deux coins dans les cellules construites de la parcelle mère.
plot-error-subplot-supervisor-member = Le propriétaire de la parcelle mère supervise déjà cette zone et n'a pas besoin d'un rôle de membre.
plot-error-time-format = Saisissez une heure HH:mm (00:00–23:59).
plot-error-transfer-owner-only = désactivé · Transférer la propriété
plot-error-transfer-self = Vous possédez déjà cette parcelle.
plot-flag-set = { $arg0 } : { $arg1 }.
plot-help = Guide des parcelles
plot-here = Ici : { $arg0 } · { $arg1 }
plot-invited = Accès accordé à { $arg0 }.
plot-item-summary = Surface construite { $arg0 } bloc² · { $arg1 ->
        [one] { $arg1 } membre
       *[other] { $arg1 } membres
    }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } bloc²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Non soumis
plot-member-draft-reset = Effacer les réglages de catégorie
plot-member-draft-role = Rôle : { $arg0 }
plot-member-draft-submit = Vérifier et confirmer
plot-member-draft-title = Brouillon de membre · { $arg0 }
plot-member-permissions = Permissions
plot-member-removed = Membre retiré du projet.
plot-menu-access = Accès et membres
plot-menu-access-by-settings = selon les réglages d'accès
plot-menu-admin = Ajouter un administrateur de parcelle
plot-menu-arrival = Point d'arrivée
plot-menu-arrival-subtitle = Sous-titre (MiniMessage ; facultatif)
plot-menu-arrival-text = Titre d'arrivée
plot-menu-arrival-title = Titre (MiniMessage ; vide = nom du projet)
plot-menu-atmosphere = Ambiance
plot-menu-clear = Ciel dégagé
plot-menu-collaborator = Ajouter une personne à la collaboration
plot-menu-confirm-delete = Supprimer l’enregistrement de { $arg0 } ? Protection, droits des membres et paramètres seront supprimés sans possibilité d’annulation. Les bâtiments resteront en place.
plot-menu-confirm-subplot-transfer = Transférer { $arg0 } à { $arg1 } ? Le propriétaire de la parcelle mère garde la supervision.
plot-menu-confirm-transfer = Transférer la gestion de { $arg0 } à { $arg1 } ? Vous perdrez l'accès ; les crédits des œuvres restent inchangés.
plot-menu-confirm-withdraw = Retirer cet avis ? Son retrait restera visible publiquement.
plot-menu-create = Créer une parcelle
plot-menu-create-for-player = Enregistrer une parcelle pour ce joueur
plot-menu-custom-time = Heure personnalisée
plot-menu-delete = Supprimer l’enregistrement de la parcelle
plot-menu-description = Description
plot-menu-feedback = Contester cet avis
plot-menu-find-record = Chercher une annonce
plot-menu-flag-container = Ouvrir les conteneurs
plot-menu-flag-entity = Interagir avec les entités
plot-menu-follow-parent = Suivre la parcelle mère
plot-menu-follow-world = Suivre le monde
plot-menu-group-admin = administrateur de parcelle
plot-menu-group-collaborator = Collaborateurs
plot-menu-group-member = Accès des membres
plot-menu-group-newcomer = Accès des nouveaux
plot-menu-group-owner = propriétaire de la parcelle
plot-menu-manage = Gérer les parcelles des joueurs
plot-menu-managed-empty = Ce joueur n'a pas encore de parcelle.
plot-menu-managed-title = Parcelles de { $arg0 }
plot-menu-members = Membres ({ $arg0 })
plot-menu-my-access = Vos droits : { $arg0 } · { $arg1 }
plot-menu-name = Nom du projet
plot-menu-owner = Propriétaire : { $arg0 }
plot-menu-parent-supervisor = Propriétaire de la parcelle mère : { $arg0 } · supervision
plot-menu-player = Nom de joueur ou UUID
plot-menu-project-kind = Type de projet
plot-menu-public-destinations = Destinations publiques
plot-menu-public-destinations-empty = Aucun projet public avec un point d'arrivée pour le moment.
plot-menu-rain = Précipitations
plot-menu-record-id = Identifiant de l'annonce ; saisir l'identifiant complet pour les anciennes
plot-menu-related-notice = Voir l’annonce liée
plot-menu-remove-arrival = Supprimer le point d'arrivée
plot-menu-remove-member = Retirer un membre
plot-menu-rename = Renommer le projet
plot-menu-rule = Enregistrer une décision
plot-menu-ruling = Motiver la décision (8 à 1000 caractères)
plot-menu-set-arrival = Définir l'arrivée ici
plot-menu-set-role = Définir comme { $arg0 }
plot-menu-settings = Paramètres du projet
plot-menu-submit = Appliquer
plot-menu-subplot-create = Créer une sous-parcelle
plot-menu-subplot-of = Partie de { $arg0 }
plot-menu-subplot-parent = Parcelle mère : { $arg0 }
plot-menu-subplots = Sous-parcelles ({ $arg0 })
plot-menu-time = Heure
plot-menu-time-input = Heure (HH:mm)
plot-menu-transfer = Transférer la parcelle
plot-menu-visit = Visiter le projet
plot-menu-weather = Météo
plot-menu-withdraw = Retirer l'avis
plot-model-fit = Cadrer toutes les limites
plot-model-focus = Centrer sur mes environs
plot-model-layer-down = Descendre la coupe d’un bloc
plot-model-layer-up = Monter la coupe d’un bloc
plot-model-legend = Cyan : enregistré · Vert/or : coins 1/2 · Violet : limites à enregistrer · Blanc/rouge : vous/direction
plot-model-level = Coupe à Y ≤ { $arg0 } (aperçu uniquement)
plot-model-limited = Échantillonnage ou détails réduits. Ciblez une zone proche ou un rectangle pour affiner ; utilisez les limites seules pour vérifier les bords.
plot-model-north-up = La vue de dessus est orientée au nord. Repassez en 3D pour tourner.
plot-model-perspective = Vue 3D
plot-model-preparing = Calcul des limites et validation de la zone en arrière-plan… Mise à jour automatique une fois terminé.
plot-model-progress = Lecture du modèle : { $arg0 } %…
plot-model-rotate-left = ↶ Tourner à gauche
plot-model-rotate-right = ↷ Tourner à droite
plot-model-show-all = Afficher toute la hauteur
plot-model-slice = Couper au-dessus de mon niveau
plot-model-terrain = Afficher les constructions et le terrain
plot-model-top = Vue de dessus
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } échantillon non chargé
       *[other] { $arg0 } échantillons non chargés
    } (gris). Aucun chunk n’est chargé de force.
plot-model-wireframe = Limites seules
plot-no-here = Aucune parcelle protégée à cet endroit.
plot-notice-board = Panneau de la parcelle
plot-notice-cleared = Panneau effacé
plot-notice-edit = Modifier le panneau
plot-notice-input = Jusqu’à { $arg0 ->
        [one] { $arg0 } caractère
       *[other] { $arg0 } caractères
    } ; plusieurs lignes autorisées ; laisser vide pour effacer
plot-notice-read = Lire l’annonce complète
plot-notice-saved = Panneau mis à jour
plot-permission-all-actions = Toutes les actions
plot-permission-animal = Soigner, apprivoiser et attacher les animaux
plot-permission-break = Casser des blocs
plot-permission-bucket = Utiliser des seaux pour liquides et créatures
plot-permission-category-build = Construction et modification
plot-permission-category-entity = Entités
plot-permission-category-interaction = Interaction avec les blocs
plot-permission-category-management = Administration
plot-permission-category-storage = Stockage et objets
plot-permission-change-children = Sous-terrains concernés : { $arg0 }
plot-permission-change-confirm = Appliquer les changements
plot-permission-change-granted = Accordés : { $arg0 }
plot-permission-change-members = Membres concernés : { $arg0 }
plot-permission-change-more = { $arg0 } de plus
plot-permission-change-no-effect = Les droits effectifs restent identiques. Les réglages explicites remplacent tout héritage futur.
plot-permission-change-none = Aucun
plot-permission-change-revoked = Révoqués : { $arg0 }
plot-permission-change-subject = Cible : { $arg0 }
plot-permission-change-title = Confirmer les changements de droits
plot-permission-create-subplot = Créer une sous-parcelle
plot-permission-decoration = Placer et modifier les décorations
plot-permission-default-status = Par défaut ({ $arg0 })
plot-permission-delete = Supprimer la parcelle
plot-permission-door = Portes et portillons
plot-permission-entity-damage = Blesser les entités
plot-permission-entity-storage = Ouvrir le stockage des entités
plot-permission-harvest = Récolter baies, lianes et miel
plot-permission-ignite = Allumer un feu
plot-permission-item-drop = Jeter des objets
plot-permission-item-pickup = Ramasser les objets au sol
plot-permission-manage-area = Modifier les limites
plot-permission-manage-members = Gérer les membres
plot-permission-manage-permissions = Gérer les permissions
plot-permission-manage-settings = Modifier les réglages de la parcelle
plot-permission-management = Réglages et propriété de la parcelle
plot-permission-modify = Modifier les blocs et fertiliser
plot-permission-operation = Action
plot-permission-personal = Droits individuels
plot-permission-personal-source = Réglage individuel
plot-permission-place = Placer des blocs et véhicules
plot-permission-reset = Rétablir les valeurs par défaut
plot-permission-result = État
plot-permission-ride = Monter véhicules et animaux
plot-permission-select-state = Choisir un état : { $arg0 }
plot-permission-sign = Modifier les panneaux
plot-permission-source-group = Droits du groupe
plot-permission-source-local = Réglage local
plot-permission-source-owner = Droit de récupération du propriétaire
plot-permission-source-parent = Parcelle parente
plot-permission-source-role = Valeur du rôle
plot-permission-state-default = Par défaut
plot-permission-switch = Boutons, leviers et plaques
plot-permission-trade = Commercer avec les villageois
plot-permission-transfer = Transférer la propriété
plot-permission-workstation = Utiliser les installations et ateliers
plot-pos1 = Premier point
plot-pos2 = Deuxième point
plot-private = Projet privé
plot-public = Projet public
plot-public-set = Type de projet : { $arg0 }.
plot-region-adjust = Ajuster ce rectangle
plot-region-apply = Terminer l’ajustement du rectangle
plot-region-cancel = Annuler l’ajustement du rectangle
plot-region-changed = La sélection a changé. Sélectionnez de nouveau le rectangle.
plot-region-delete = Supprimer ce rectangle
plot-region-delete-confirm = Retirer le rectangle n°{ $arg0 } du brouillon ? Les bâtiments restent. Les limites changent uniquement après enregistrement ; cette action peut être annulée.
plot-region-edit-hint = Ajustez ce rectangle avec //pos1 et //pos2. Les autres restent inchangés. Terminez l’ajustement, puis enregistrez la parcelle.
plot-region-finish-first = Terminez ou annulez d’abord l’ajustement du rectangle en cours.
plot-region-open = Gérer les rectangles ({ $arg0 })
plot-region-select-hint = Sélectionnez un rectangle numéroté pour le centrer et le surligner. Les contours gris montrent cette page ; une fusion peut modifier les numéros.
plot-renamed = Projet renommé.
plot-role-admin = administrateur de parcelle
plot-role-changed-for-you = Votre rôle sur la parcelle { $arg0 } est désormais { $arg1 }.
plot-role-collaborator = collaboration
plot-role-owner = propriétaire de la parcelle
plot-role-parent-owner = propriétaire de la parcelle mère
plot-role-server-staff = équipe du serveur
plot-selection-add = Ajouter le pavé actuel
plot-selection-clear = Réinitialiser les deux points
plot-selection-empty = Marquez deux coins avec //pos1 et //pos2.
plot-selection-exit = Quitter l’édition / l’aperçu
plot-selection-fit = Restaurer les limites enregistrées
plot-selection-incomplete = Un point est marqué. Marquez l'autre ; rien ne change avant l'envoi.
plot-selection-mode = Choisir les points sur place
plot-selection-no-redo = Aucune modification de sélection à rétablir.
plot-selection-no-undo = Aucune modification précédente à annuler.
plot-selection-open = Modifier les limites
plot-selection-redo = Rétablir la dernière étape
plot-selection-replace = Remplacer par le pavé actuel
plot-selection-session-hint = S’accroupir+F ou /plot edit pour revenir. Fermeture après 20 minutes d’inactivité.
plot-selection-size = Sélection : { $arg0 } × { $arg1 } × { $arg2 } blocs (X/Y/Z)
plot-selection-stopped = Sélection terminée. Les brouillons sont conservés par parcelle.
plot-selection-subtract = Soustraire le pavé actuel
plot-selection-undo = Annuler la dernière étape
plot-subplot-created = Sous-parcelle { $arg0 } créée · { $arg1 } bloc²
plot-subplot-created-for-you = Vous possédez maintenant la sous-parcelle { $arg0 } dans { $arg1 }.
plot-subplot-owner-changed = La sous-parcelle { $arg0 } appartient désormais à { $arg1 }.
plot-subplot-parent-transferred = La parcelle mère de votre sous-parcelle { $arg0 } appartient désormais à { $arg1 }.
plot-subplot-released = La sous-parcelle { $arg0 } a été libérée ; sa zone reste protégée par la parcelle mère.
plot-title = Parcelles
plot-transferred = Parcelle { $arg0 } transférée à { $arg1 }.
plot-transferred-to-you = { $arg1 } vous a transféré la parcelle { $arg0 }.

## World · Vehicles

vehicle-admin-help = /vehicle — { vehicle-model-title }
    Modèles: model | model help
    Capture: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Véhicules: spawn <model> [x y z] | reload
vehicle-command-description = Conduire et gérer des véhicules composés d’entités d’affichage
vehicle-controls = { $arg0 } : W/S et A/D pour conduire ; molette pour les rapports ou les gaz ; échange de main pour le mode ; accroupissement pour sortir. /vehicle help · /vehicle dashboard ouvre les commandes.
vehicle-dash-assisted = Aide à la stabilité
vehicle-dash-automatic = Automatique
vehicle-dash-contact = Appui au sol / sur l’eau : { $arg0 }
vehicle-dash-details = Détails
vehicle-dash-down = Rapport inférieur −
vehicle-dash-drive = Conduite
vehicle-dash-energy = Consommation { $arg0 } U/min · autonomie estimée { $arg1 } min / { $arg2 } km
vehicle-dash-engine-start = Démarrer le moteur
vehicle-dash-engine-stop = Arrêter le moteur
vehicle-dash-fuel = Carburant { $arg0 } / { $arg1 } U · { $arg2 } %
vehicle-dash-gear = Rapport { $arg0 }
vehicle-dash-gearbox = Boîte de vitesses
vehicle-dash-handbrake = Stationnement / frein à main : { $arg0 }
vehicle-dash-heading = Cap { $arg0 }°
vehicle-dash-health = État { $arg0 } % · occupants { $arg1 } / { $arg2 }
vehicle-dash-input = Accélérateur { $arg0 } % · frein { $arg1 } % · direction { $arg2 } %
vehicle-dash-manual = Manuel
vehicle-dash-mode-switch = Passer à { $arg0 }
vehicle-dash-position = Origine { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Recentrer le panneau
vehicle-dash-rpm = Régime { $arg0 } / { $arg1 } tr/min
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Vitesse { $arg0 } km/h · limite { $arg1 }
vehicle-dash-throttle-down = Accélérateur −5 %
vehicle-dash-throttle-up = Accélérateur +5 %
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Sans assistance
vehicle-dash-unavailable = Commandes indisponibles ; rouvrez le panneau depuis le siège conducteur.
vehicle-dash-up = Rapport supérieur +
vehicle-dash-vertical = Altitude { $arg0 } m · vitesse verticale { $arg1 } m/s
vehicle-error = Échec de l’opération du véhicule : { $arg0 }
vehicle-group-changed = La structure du groupe a changé. Sélectionnez-le à nouveau avant l’importation.
vehicle-group-invalid = Le groupe contient des entités incompatibles, gérées, absentes ou trop éloignées. Toutes doivent être dans ce monde à 32 blocs maximum.
vehicle-group-limit = Importez au plus 128 affichages et 256 membres de groupe au total.
vehicle-group-not-found = Aucun groupe trouvé. Sélectionnez-en un, visez une entité d’affichage de bloc ou d’objet, ou indiquez son UUID.
vehicle-group-selected = Groupe sélectionné : { $arg0 ->
        [one] { $arg0 } entité d’affichage
       *[other] { $arg0 } entités d’affichage
    } · racine { $arg1 }
vehicle-help = /vehicle — Commandes des véhicules
    Monter: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Conduire: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    Affichage: hud [on/off] | dashboard [drive/gearbox/details/off]
    Entretien: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } nd · poussée { $arg1 } % · cap { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · altitude { $arg1 } m · vitesse verticale { $arg2 } m/s · gaz { $arg3 } % · { $arg4 }
vehicle-hud-status = Carburant { $arg0 } % · état { $arg1 } % · { $arg2 }
vehicle-model-add = Ajouter un composant
vehicle-model-add-here = Ajouter un appui à mes pieds
vehicle-model-anchor = Placer ici l’origine de l’aperçu, selon votre orientation
vehicle-model-animation = Animation
vehicle-model-apply = Appliquer / confirmer
vehicle-model-auto = Recalculer les limites de collision
vehicle-model-block = Bloc
vehicle-model-center-here = Placer le centre de masse à mes pieds
vehicle-model-center-look = Placer le centre de masse sur la surface visée
vehicle-model-check = Vérifier la structure et les paramètres
vehicle-model-checked = Structure et paramètres valides. L’enregistrement vérifie aussi les données d’affichage, les véhicules existants et les collisions avec le monde.
vehicle-model-clone = Dupliquer le modèle
vehicle-model-colliders = Boîtes de collision
vehicle-model-copy = Dupliquer et modifier (sièges copiés réservés aux passagers)
vehicle-model-corner-cancel = Annuler la boîte de collision en attente
vehicle-model-corner-first = Marquer le premier coin à mes pieds
vehicle-model-corner-first-look = Marquer le premier coin sur la surface visée
vehicle-model-corner-hint = Premier coin marqué. Placez-vous au coin opposé, hauteur comprise, puis utilisez /vehicle model collider corner2. La boîte suit les axes du modèle.
vehicle-model-corner-required = Marquez d’abord le premier coin avec /vehicle model collider corner1.
vehicle-model-corner-second = Utiliser mes pieds comme coin opposé et ajouter la boîte
vehicle-model-corner-second-look = Utiliser la surface visée comme coin opposé
vehicle-model-create = Créer un modèle
vehicle-model-delete = Supprimer le modèle
vehicle-model-delete-warning = Supprimer définitivement ce modèle ? Impossible si des véhicules l’utilisent encore.
vehicle-model-deleted = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } supprimé
        [feminine] { -vehicle-model } supprimée
       *[other] { -vehicle-model } supprimé
    }
vehicle-model-discard = Abandonner le brouillon
vehicle-model-discard-warning = Abandonner toutes les modifications non enregistrées ? Cette action est irréversible.
vehicle-model-discarded = Brouillon abandonné
vehicle-model-draft = Brouillon non enregistré
vehicle-model-driver = Siège conducteur
vehicle-model-driver-here = Ajouter le siège conducteur à mes pieds
vehicle-model-edit = Modifier / reprendre le brouillon
vehicle-model-exit = Position de sortie / réglage fin
vehicle-model-group = Ajouter un groupe d’affichage Axiom
vehicle-model-help = /vehicle model — { vehicle-model-title }
    Modèles: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Modifier: set <field> <value...> | part/seat/collider/support — Tab affiche les opérations et arguments
    Brouillon: preview [off] | undo | redo | save | cancel
    Dans le monde: anchor | pick | here | look | snap [on/off] | move <index> <dx dy dz>
    { vehicle-model-position-hint }
    seat position/exit | part position | support add/set | collider position: here / look
    collider corner1/corner2 [look] | collider cancel — { vehicle-model-corner-hint }
    Autres actions: check | apply — { vehicle-model-save-continue }
vehicle-model-here = Utiliser la position de mes pieds
vehicle-model-history-empty = Aucune modification disponible dans ce sens de l’historique.
vehicle-model-import = Importer un groupe d’affichage Axiom
vehicle-model-item = Objet
vehicle-model-library = Modèles enregistrés · brouillons conservés en mémoire jusqu’à l’enregistrement
vehicle-model-look = Placer en visant (ferme le menu ; clic droit pour confirmer)
vehicle-model-look-missing = Aucune surface visée. Regardez un bloc du monde ou une pièce de votre aperçu à 32 blocs maximum.
vehicle-model-manual = Saisir les coordonnées locales
vehicle-model-options = Options
vehicle-model-parameters = Paramètres physiques et moteur
vehicle-model-parts = Éléments d’affichage
vehicle-model-passenger-here = Ajouter un siège passager à mes pieds
vehicle-model-pick = Modifier la pièce de l’aperçu que vous regardez
vehicle-model-pick-hint = Ouvrez un aperçu et regardez une pièce de type bloc ou objet, ou sélectionnez d’abord un composant dans l’éditeur.
vehicle-model-place-cancelled = Placement annulé : délai expiré, état d’édition modifié ou annulation manuelle.
vehicle-model-place-hint = Placement en visant actif pendant 30 secondes. Visez un bloc ou votre aperçu ; clic droit de la main principale pour confirmer, accroupissement + clic droit pour annuler.
vehicle-model-position = Position / réglage fin
vehicle-model-position-hint = X/Y/Z locaux · here utilise vos pieds, look la surface visée · la grille facultative suit le pas de réglage.
vehicle-model-preview = Aperçu privé
vehicle-model-preview-failed = Aperçu indisponible ; votre brouillon est conservé : { $arg0 }
vehicle-model-preview-hint = Aperçu privé activé. Effacé après cinq minutes sans modification ; /vehicle model preview off pour fermer.
vehicle-model-redo = Rétablir la dernière modification
vehicle-model-remove = Supprimer le composant
vehicle-model-remove-warning = Supprimer ce composant ? Les indices suivants seront décalés ; l’action peut être annulée.
vehicle-model-rotate = Tourner le composant
vehicle-model-rotate-hint = Rotation locale relative : X tangage, Y lacet, Z roulis. La position reste inchangée.
vehicle-model-save = Vérifier et enregistrer
vehicle-model-save-continue = Enregistrer et continuer
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } enregistré
        [feminine] { -vehicle-model } enregistrée
       *[other] { -vehicle-model } enregistré
    }
vehicle-model-scale = Redimensionner le composant
vehicle-model-scale-all = Redimensionner tous les axes × { $arg0 }
vehicle-model-scale-hint = Multiplie ou divise les longueurs des axes locaux, pas les dimensions englobantes dans le monde.
vehicle-model-seats = Sièges et sorties
vehicle-model-selected = Importer les affichages sélectionnés
vehicle-model-size = Redimensionner la boîte de collision
vehicle-model-size-hint = Largeur, hauteur et longueur totales ; le centre reste inchangé.
vehicle-model-snap-off = Alignement sur la grille : désactivé
vehicle-model-snap-on = Alignement sur la grille : activé
vehicle-model-step = Changer le pas de réglage
vehicle-model-supports = Points d’appui au sol
vehicle-model-text = Texte
vehicle-model-title = Gestion des modèles de véhicules
vehicle-model-tools = Outils dans le monde
vehicle-model-transform-step = Modifier le pas de réglage
vehicle-model-undo = Annuler la dernière modification
vehicle-result = Véhicule : { $arg0 }
vehicle-selection-info = Sélection : { $arg0 ->
        [one] { $arg0 } entité d’affichage
       *[other] { $arg0 } entités d’affichage
    } · groupe { $arg1 } · origine { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Collision / obstacle
vehicle-status-damage = Véhicule gravement endommagé
vehicle-status-disabled = Véhicule hors service
vehicle-status-engine-off = Moteur arrêté
vehicle-status-engine-on = Moteur en marche
vehicle-status-gear-limit = Limite de commande atteinte
vehicle-status-grounded = Bateau échoué
vehicle-status-low-fuel = Carburant faible
vehicle-status-manual-only = Passez d’abord en mode manuel
vehicle-status-no-fuel = Plus de carburant
vehicle-status-overrev = Rétrograder ferait surrégimer le moteur
vehicle-status-shifting = Changement de rapport en cours
vehicle-status-stall = Moteur calé
vehicle-status-stop-first = Arrêtez-vous avant d’inverser la marche ou de stationner
vehicle-value-off = Désactivé
vehicle-value-on = Activé

## World · Elevators, regeneration and spawn

async-regen-already-running = Une régénération est déjà en cours ; utilise /regen status ou /regen cancel
async-regen-apply-expired = Aucun plan valide à appliquer ; exécute d'abord /regen preview
async-regen-apply-plan = Appliquer le plan enregistré
async-regen-apply-started = Application de { $arg0 } à la sélection enregistrée : { $arg1 }
async-regen-awaiting-apply = Plan en attente : { $arg0 } · { $arg1 } · expire dans { $arg2 ->
        [one] { $arg2 } seconde
       *[other] { $arg2 } secondes
    }
async-regen-block-preview-progress = aperçu des blocs · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk analysé
       *[other] { $arg1 } chunks analysés
    } · { $arg2 ->
        [one] { $arg2 } modification de bloc
       *[other] { $arg2 } modifications de blocs
    } · { $arg3 ->
        [one] { $arg3 } entité de bloc
       *[other] { $arg3 } entités de bloc
    } à risque
async-regen-block-preview-summary = Aperçu : { $arg0 ->
        [one] { $arg0 } modification de bloc
       *[other] { $arg0 } modifications de blocs
    } pour { $arg2 } ; risque d’écrasement : { $arg1 ->
        [one] { $arg1 } entité de bloc existante
       *[other] { $arg1 } entités de bloc existantes
    }
async-regen-cancelled = Régénération annulée. Les { $arg0 ->
        [one] { $arg0 } changement
       *[other] { $arg0 } changements
    } déjà appliqués restent dans le monde
async-regen-command-description = Régénérer une sélection FastAsyncWorldEdit de façon asynchrone
async-regen-completed = Régénération terminée : { $arg0 ->
        [one] { $arg0 } bloc modifié
       *[other] { $arg0 } blocs modifiés
    } en { $arg1 }
async-regen-failed = Régénération arrêtée à cause d'une erreur interne. Les { $arg0 ->
        [one] { $arg0 } changement
       *[other] { $arg0 } changements
    } déjà appliqués restent dans le monde
async-regen-filter-all = tous les types de blocs
async-regen-invalid-blocks = Types inconnus ou qui ne sont pas des blocs : { $arg0 }
async-regen-not-running = Aucune régénération n'est en cours
async-regen-nothing-to-apply = La sélection correspond déjà à ce plan ; rien n'est à appliquer
async-regen-plan-cancel = Annuler
async-regen-plan-dialog-title = Appliquer le plan de régénération
async-regen-preview-cancelled = Aperçu annulé ; aucune donnée du monde n'a été modifiée
async-regen-preview-started = Aperçu lancé : sélection { $arg0 ->
        [one] { $arg0 } bloc
       *[other] { $arg0 } blocs
    } · zone { $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } · mode { $arg2 }
async-regen-progress = { $arg0 } · planification { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } · application : { $arg3 ->
        [one] { $arg3 } changement
       *[other] { $arg3 } changements
    }
async-regen-safety-note = Rien ne sera modifié hors de la sélection enregistrée. Les constructions à l'intérieur peuvent être écrasées et Vanilla n'identifie pas les blocs placés par les joueurs. Vérifie la visualisation et sauvegarde avant d'appliquer.
async-regen-scope-blocks = régénérer les blocs correspondant à { $arg0 }
async-regen-selection-required = Termine d'abord une sélection FastAsyncWorldEdit
async-regen-skipped-ungenerated = Chunks cibles jamais générés ignorés : { $arg0 ->
        [one] { $arg0 } chunk
       *[other] { $arg0 } chunks
    }. Aucun chunk cible n’a été créé ni modifié.
async-regen-start-failed = Impossible de lancer la régénération ; consulte le journal du serveur
async-regen-state-applying = application des changements
async-regen-state-cancelled = annulée
async-regen-state-completed = terminée
async-regen-state-failed = échouée
async-regen-state-generating = génération et comparaison des chunks
async-regen-state-preparing = préparation du monde source
async-regen-status = { $arg0 } · planification { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } · application { $arg3 }/{ $arg4 ->
        [one] { $arg4 } changement
       *[other] { $arg4 } changements
    }
async-regen-structure-metadata-applied = Métadonnées transférées : { $arg0 ->
        [one] { $arg0 } début de structure
       *[other] { $arg0 } débuts de structures
    } et { $arg1 ->
        [one] { $arg1 } référence de chunk
       *[other] { $arg1 } références de chunks
    } ; /locate et l’apparition liée aux structures peuvent les reconnaître
async-regen-upgrade-apply-progress = { $arg0 } · planification { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } · application : { $arg3 ->
        [one] { $arg3 } bloc
       *[other] { $arg3 } blocs
    } et { $arg4 ->
        [one] { $arg4 } cellule de biome
       *[other] { $arg4 } cellules de biome
    }
async-regen-upgrade-boundary-note = { $arg0 ->
        [one] { $arg0 } cellule de biome touchant la limite sera ignorée afin de ne rien modifier hors de la sélection
       *[other] { $arg0 } cellules de biome touchant la limite seront ignorées afin de ne rien modifier hors de la sélection
    }
async-regen-upgrade-completed = Mise à niveau terminée : { $arg0 ->
        [one] { $arg0 } bloc
       *[other] { $arg0 } blocs
    } et { $arg1 ->
        [one] { $arg1 } cellule de biome
       *[other] { $arg1 } cellules de biome
    } modifiés en { $arg2 }
async-regen-upgrade-preview-progress = aperçu · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk analysé
       *[other] { $arg1 } chunks analysés
    } · { $arg2 ->
        [one] { $arg2 } modification de bloc
       *[other] { $arg2 } modifications de blocs
    } · { $arg3 ->
        [one] { $arg3 } cellule de biome
       *[other] { $arg3 } cellules de biome
    } · { $arg4 ->
        [one] { $arg4 } structure
       *[other] { $arg4 } structures
    }
async-regen-upgrade-preview-summary = Aperçu : { $arg0 ->
        [one] { $arg0 } modification de bloc
       *[other] { $arg0 } modifications de blocs
    }, { $arg1 ->
        [one] { $arg1 } cellule de biome
       *[other] { $arg1 } cellules de biome
    }, { $arg2 ->
        [one] { $arg2 } structure
       *[other] { $arg2 } structures
    } (migration complète des métadonnées possible : { $arg3 }) ; risque d’écrasement : { $arg4 ->
        [one] { $arg4 } entité de bloc existante
       *[other] { $arg4 } entités de bloc existantes
    }
async-regen-upgrade-scope-biomes = biomes uniquement
async-regen-upgrade-scope-terrain = terrain, structures et biomes
async-regen-usage = Utilisation : /regen preview blocks [types...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Visualisation dans { $arg0 } : cyan = limite de sélection, orange = échantillon de blocs modifiés, rouge = entité de bloc à risque. Toi seul vois ces particules.
async-regen-visualization-selection = Sélection dans { $arg0 } : les particules cyan indiquent sa limite. Toi seul les vois.
async-regen-fawe-required = FastAsyncWorldEdit est requis pour lire la sélection
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ S'accroupir
iron-elevator-tip-mm = Alignez des blocs { -iron-elevator-block } verticalement pour créer un ascenseur. Placez-vous dessus : <green>sautez pour monter</green>, <aqua>accroupissez-vous pour descendre</aqua>. Au-delà de deux étages, un <gold>panneau de sélection</gold> apparaît.
iron-elevator-up = ↑ Sauter
spawn-beacon-color-command-description = Activer ou désactiver le changement de couleur de la balise du point d'apparition
spawn-beacon-color-disabled = Changement de couleur de la balise du point d'apparition désactivé
spawn-beacon-color-enabled = Changement de couleur de la balise du point d'apparition activé
spawn-command-description = Retourner au point d'apparition
spawn-tip-return-mm = Utilisez <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> pour revenir au <yellow>point d’apparition du serveur</yellow>.
spawn-world-missing = Le monde du point d'apparition n'est pas disponible

## Entertainment · Music

music-action-add-queue = Ajouter à la file d'attente
music-action-play-nearby = Lire sur le jukebox le plus proche
music-action-play-now = Lire maintenant sur ce jukebox
music-action-take-disc = Prendre le disque
music-added-to-queue-rich = Ajouté à la file d'attente : { $music }
music-album = Album : { $arg0 }
music-all-in-queue = Tous les morceaux sont déjà dans la file d'attente
music-artist = Artiste : { $arg0 }
music-back = Retour
music-browser-left-click = Clic gauche
music-browser-right-click = Clic droit
music-cache-clear-done = Cache de musique en ligne nettoyé
music-cache-clear-started = Nettoyage du cache de musique en ligne...
music-cache-operation-failed = Échec de l'opération de cache musical : { $arg0 }
music-cache-status = Cache de musique en ligne : { $arg0 ->
        [one] { $arg0 } fichier
       *[other] { $arg0 } fichiers
    }, { $arg1 } / { $arg2 }
music-cached = En cache - prêt à lire
music-clear-queue = Effacer la file d'attente
music-clear-queue-done = File vidée ({ $arg0 ->
        [one] { $arg0 } morceau
       *[other] { $arg0 } morceaux
    })
music-command-description = Musique locale, en ligne et NBS pour jukebox
music-disc-left = Clic gauche : prenez le disque
music-disc-right = Clic droit : jouer sur le jukebox le plus proche
music-duplicate-in-queue = Cette chanson est déjà dans la file d'attente
music-duration = Durée : { $arg0 }
music-empty-library = La bibliothèque musicale est vide
music-empty-library-description = Aucun fichier audio local ni morceau NBS disponible
music-empty-nbs = Aucun morceau NBS trouvé
music-empty-nbs-description = Passez à toute la musique ou ajoutez un fichier NBS
music-empty-playlist = Cette playlist ne contient aucun titre disponible
music-empty-playlist-description = Vérifiez que la playlist est publique et que la plateforme sélectionnée est correcte
music-empty-search = Aucune chanson correspondante
music-empty-search-description = Essayez un autre mot-clé ou vérifiez l'état des sources personnalisées
music-finished = Terminée
music-format = Format : { $arg0 }
music-inventory-full = L'inventaire est plein. Impossible de donner le disque de musique
music-jukebox-audible-field-conflict = Un autre jukebox joue un morceau différent dans une zone clairement audible qui se chevauche
music-jukebox-music-mode = Mode musique
music-jukebox-play-order = Ordre de lecture
music-jukebox-range = Portée de lecture
music-jukebox-range-value = Actuelle : { $arg0 ->
        [one] { $arg0 } bloc
       *[other] { $arg0 } blocs
    }
music-jukebox-rhythm-mode = Mode rythme
music-jukebox-too-far = Vous êtes trop loin du jukebox ({ $arg0 ->
        [one] { $arg0 } bloc
       *[other] { $arg0 } blocs
    } maximum)
music-jukebox-tp-hover = Cliquez pour vous téléporter au jukebox
music-jukebox-unavailable = Le jukebox a été retiré ou son chunk n'est pas disponible
music-jukebox-usage-mode = Usage du jukebox
music-jukebox-volume = Volume du jukebox
music-jukebox-volume-value = Actuel : { $arg0 }%
music-library-button = Bibliothèque musicale
music-library-random-mode = Bibliothèque aléatoire
music-library-random-mode-desc = Lire aléatoirement toute la bibliothèque musicale sans répéter immédiatement le même titre
music-loading-title = Chargement
music-menu-nbs-title = Musique NBS - Page { $arg0 }/{ $arg1 }
music-menu-playlist-title = Liste de lecture : { $arg0 } · { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = Recherche NBS : { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-title = Rechercher : { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-title = Disques de musique - Page { $arg0 }/{ $arg1 }
music-mode-switched = Mode défini sur { $arg0 }
music-nearest-jukebox-missing = Aucun jukebox trouvé dans un rayon de { $arg0 ->
        [one] { $arg0 } bloc
       *[other] { $arg0 } blocs
    }
music-next-page = Page suivante
music-no-disc = Pas de disque
music-no-files = Aucun fichier musical n'est disponible
music-no-search-results-rich = Aucune chanson trouvée contenant "{ $keyword }"
music-now-playing-rich = Lecture en cours : { $music } sur le jukebox { $location }
music-online-partial-failure = { $arg0 ->
        [one] { $arg0 } requête a échoué ; les résultats disponibles sont affichés
       *[other] { $arg0 } requêtes ont échoué ; les résultats disponibles sont affichés
    }
music-online-rate-limited = Trop de requêtes musicales. Réessayez dans { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } secondes
    }.
music-online-searching = Recherche de "{ $arg0 }" dans les sources locales et en ligne...
music-original-author = Auteur original : { $arg0 }
music-page-out-of-range = Page invalide. Dernière page : { $arg0 }
music-page-total = { $arg0 ->
        [one] { $arg0 } morceau
       *[other] { $arg0 } morceaux
    } au total
music-page-usage-desc = Aller à une page
music-partial-results = { $arg0 ->
        [one] { $arg0 } requête a échoué ; les résultats disponibles sont affichés
       *[other] { $arg0 } requêtes ont échoué ; les résultats disponibles sont affichés
    }
music-paused = En pause
music-plasmo-voice-unavailable = La chanson a démarré, mais vous ne pouvez pas l'entendre car Plasmo Voice n'est pas installé, connecté ou activé
music-play-next = Jouer ensuite
music-play-random = Lecture aléatoire
music-playback-failed = Impossible de lire "{ $arg0 }". Vérifiez la source ou le fichier musical et réessayez.
music-playback-status = État de lecture : { $arg0 }
music-playing = En lecture
music-playing-now-rich = Lecture en cours : { $music }
music-playlist-import-button = Importer une playlist
music-playlist-import-done = Import de « { $arg0 } » terminé : { $arg1 } sur { $arg2 ->
        [one] { $arg2 } morceau
       *[other] { $arg2 } morceaux
    }.
music-playlist-import-failed = Échec de l’importation de la playlist : { $arg0 }
music-playlist-import-prompt = Choisissez une plateforme, puis collez l’identifiant ou le lien d’une playlist publique :
music-playlist-import-usage-desc = Importer une playlist publique depuis une autre plateforme musicale
music-playlist-importing = Importation de la playlist...
music-playlist-reference-prompt = Collez l'ID ou le lien d'une playlist publique
music-prev-page = Page précédente
music-queue-empty = La file d'attente est vide
music-queue-move-down = Descendre
music-queue-move-up = Monter
music-queue-remove = Retirer le morceau
music-queue-summary = File : { $arg0 ->
        [one] { $arg0 } morceau
       *[other] { $arg0 } morceaux
    }
music-queue-summary-description = Page { $arg0 }/{ $arg1 }
music-random-add = Ajouter une chanson aléatoire
music-random-added-to-queue-rich = Ajout aléatoire à la file d'attente : { $music }
music-random-disc = Disque aléatoire
music-random-disc-got-rich = Disque de musique aléatoire obtenu : { $music }
music-random-mode = Liste aléatoire
music-random-mode-desc = Lire la liste aléatoirement sans répéter immédiatement le même titre
music-rank-down = Déplacé vers le bas
music-rank-up = Déplacé vers le haut
music-reload-done = Rechargement terminé : { $arg0 ->
        [one] { $arg0 } morceau local
       *[other] { $arg0 } morceaux locaux
    } ; sources LX disponibles { $arg1 }/{ $arg2 } ; abonnements vérifiés { $arg3 }, mis à jour { $arg4 }
music-reload-failed = Échec du rechargement de la musique : { $arg0 }
music-reload-issues = Problèmes de rechargement : { $arg0 }
music-reload-partial = Rechargement terminé en conservant l’état précédent : { $arg0 ->
        [one] { $arg0 } morceau local
       *[other] { $arg0 } morceaux locaux
    } ; sources LX disponibles { $arg1 }/{ $arg2 } ; abonnements vérifiés { $arg3 }, mis à jour { $arg4 }
music-reload-started = Rechargement des fichiers musicaux et des sources personnalisées...
music-remote-source-error = erreur de mise à jour : { $arg0 }
music-remote-source-import-done = Abonnement de source LX importé : { $arg0 }
music-remote-source-import-failed = Échec de l'importation de la source LX : { $arg0 }
music-remote-source-import-started = Importation de l'abonnement de source LX...
music-remote-source-remove-done = Abonnement de source LX supprimé : { $arg0 }
music-remote-source-remove-failed = Impossible de supprimer l'abonnement de source LX : { $arg0 }
music-remote-source-remove-not-found = Abonnement de source LX introuvable : { $arg0 }
music-remote-source-update-done = Vérification : { $arg0 ->
        [one] { $arg0 } abonnement
       *[other] { $arg0 } abonnements
    } ; mises à jour : { $arg1 } ; échecs : { $arg2 }.
music-remote-source-update-failed = Échec de la mise à jour de la source LX : { $arg0 }
music-remote-source-update-started = Vérification des abonnements de sources LX importés...
music-remote-source-updated-at = mis à jour : { $arg0 }
music-remote-sources-title = Abonnements de sources LX importés : { $arg0 }
music-removed-from-queue-rich = Supprimé de la file d'attente : { $music }
music-repeat-one-mode = Répéter un titre
music-repeat-one-mode-desc = Répéter le titre actuel jusqu'au passage manuel au suivant
music-request-error = Échec de la demande : { $arg0 }
music-rhythm-animation-offset = Décalage d’animation { $arg0 } ms
music-rhythm-calibration = Calibrage de la latence
music-rhythm-calibration-accumulating = Confirmation du rythme stable… continuez à jouer
music-rhythm-calibration-adapting = Synchronisation en cours… continuez à suivre le rythme
music-rhythm-calibration-complete = Test de latence terminé · jeu de rythme prêt
music-rhythm-calibration-done = Test de latence terminé · réglages enregistrés
music-rhythm-calibration-failed = L'audio du test s'est interrompu. Le test a été fermé en sécurité ; veuillez le relancer
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Clavier visuel → Souris visuelle
music-rhythm-calibration-listen = Écoutez une fois le rythme généré · n'appuyez pas encore
music-rhythm-calibration-minecraft-instruction = Répétez le rythme Minecraft Note Block et appuyez sur 1–4
music-rhythm-calibration-no-input = Aucune entrée détectée. Vérifiez les touches et le volume, puis continuez
music-rhythm-calibration-out-of-range = Le délai mesuré dépasse la plage prise en charge. Vérifiez les réglages audio
music-rhythm-calibration-plasmo-instruction = À vous : répétez le rythme Plasmo Voice et appuyez sur 1–4 à chaque battement
music-rhythm-calibration-plasmo-ready = Test Minecraft Note Block terminé ; écoutez d'abord le même rythme via Plasmo Voice
music-rhythm-calibration-pointer-instruction = Clique à gauche ou à droite quand la note franchit la ligne.
music-rhythm-calibration-prompt-confirm = Démarrer le test complet
music-rhythm-calibration-prompt-confirm-hint = Retour automatique à la fin.
music-rhythm-calibration-prompt-later = Pas maintenant
music-rhythm-calibration-prompt-reason = Faites un test de latence avant de jouer.
music-rhythm-calibration-prompt-title = Test de latence requis avant de jouer
music-rhythm-calibration-realigning = Le flux audio a été interrompu. La démonstration reprend et les mesures terminées sont conservées
music-rhythm-calibration-required = Terminez le test complet de latence avant de lancer le jeu de rythme
music-rhythm-calibration-reset-done = Le calibrage de latence a été réinitialisé
music-rhythm-calibration-result-hold = Tous les tests sont terminés. Consultez le résultat avant le retour automatique
music-rhythm-calibration-stage-minecraft = Bloc musical de Minecraft
music-rhythm-calibration-stage-plasmo = Test audio Plasmo Voice et entrée
music-rhythm-calibration-stage-pointer = Souris
music-rhythm-calibration-stage-visual = Test visuel et d'entrée
music-rhythm-calibration-visual-instruction = Appuie sur 1–4 quand la note franchit la ligne.
music-rhythm-calibration-visual-preview = Observez une séquence complète sans agir · commencez lorsque l'instruction change
music-rhythm-calibration-voice-required = Le test complet nécessite Plasmo Voice connecté et activé
music-rhythm-chart-preparing = Préparation de la partition complète…
music-rhythm-combo = Combo ×{ $arg0 }
music-rhythm-difficulty-easy = Facile
music-rhythm-difficulty-expert = Expert
music-rhythm-difficulty-hard = Difficile
music-rhythm-difficulty-normal = Normal
music-rhythm-exit = Quitter le jeu de rythme
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
music-rhythm-get-ready = Préparez-vous
music-rhythm-go = PARTEZ !
music-rhythm-good = Bien
music-rhythm-great = Excellent
music-rhythm-latency = Latence { $arg0 } ms
music-rhythm-latency-test = Test complet de latence
music-rhythm-miss = Raté
music-rhythm-mode-falling = Notes descendantes
music-rhythm-mode-radial = Assaut radial
music-rhythm-mode-spatial-aim = Visée spatiale
music-rhythm-network-too-high = La latence réseau est trop élevée pour un jugement équitable ({ $arg0 } ms ; limite { $arg1 } ms)
music-rhythm-network-warning = La latence réseau est instable ({ $arg0 } ms). La session s'arrêtera si elle reste au-dessus de { $arg1 } ms
music-rhythm-no-beats = Aucun rythme jouable n'a été détecté dans ce morceau
music-rhythm-perfect = Parfait
music-rhythm-preparing = Attendez la mise en cache complète et l'analyse rythmique du morceau
music-rhythm-ready = Partition complète prête — départ dès le premier temps
music-rhythm-requires-playback = Lancez d'abord un morceau sur ce jukebox
music-rhythm-reset-calibration = Réinitialiser le calibrage de latence
music-rhythm-results = Résultats
music-rhythm-score = Score : { $arg0 }
music-rhythm-select-track = Choisir un morceau rythmique
music-rhythm-spatial-no-room = Il n’y a pas assez d’espace pour la visée spatiale. Allez dans une zone plus ouverte.
music-rhythm-spatial-slider-hint = Suis l’étoile mobile et clique chaque point · ne maintiens pas
music-rhythm-start-game = Lancer le jeu
music-rhythm-waiting-for-player = En attente d'un joueur pour lancer le jeu de rythme
music-sample-rate = Taux d'échantillonnage : { $arg0 }
music-search-button = Rechercher des chansons
music-search-keyword-required = Le mot-clé de recherche ne peut pas être vide
music-search-keyword-too-long = Recherche trop longue. Limite : { $arg0 ->
        [one] { $arg0 } caractère
       *[other] { $arg0 } caractères
    }.
music-search-usage-desc = Rechercher des chansons
music-section-all = Toute la musique
music-section-nbs = Musique NBS
music-select-music = Sélectionnez la musique
music-sequential-mode = Répéter la liste
music-sequential-mode-desc = Lire la liste dans l'ordre puis reprendre au début
music-size = Taille : { $arg0 }
music-sort-button = Trier
music-sort-current = Actuel : { $arg0 }
music-sort-default = Ordre par défaut
music-sort-most-played = Les plus écoutés
music-sort-recently-played = Écoutés récemment
music-sound-settings = Réglages du son
music-source-actions = actions : { $arg0 }
music-source-available = disponible
music-source-capabilities = capacités : { $arg0 }
music-source-error = erreur : { $arg0 }
music-source-failures = échecs : { $arg0 }
music-source-retry = nouvel essai dans { $arg0 ->
        [one] { $arg0 } seconde
       *[other] { $arg0 } secondes
    }
music-source-unavailable = indisponible
music-sources-empty = Aucune source musicale LX personnalisée trouvée
music-sources-title = Sources musicales LX personnalisées : { $arg0 }
music-stop-eject = Arrêter et éjecter
music-stop-eject-done = Lecture arrêtée et disque éjecté
music-stopped = Arrêté
music-track-details = Détails du morceau
music-unavailable-disc = Disque indisponible
music-unavailable-disc-description = Ce disque MIK ne figure pas dans le catalogue actuel ; il peut toujours être arrêté et éjecté

## Entertainment · Anniversary

anniversary-accepted = Vous participez au tirage. La première manche se débloque après 4 heures en ligne hors AFK.
anniversary-admin-audit-action-bonus = Chance bonus de construction accordée
anniversary-admin-audit-action-gift = Lot de l'événement offert : { $arg0 }
anniversary-admin-audit-action-removal = Ancienne participation d’un administrateur supprimée ; stock rétabli : { $arg0 ->
        [one] { $arg0 } lot
       *[other] { $arg0 } lots
    }.
anniversary-admin-audit-action-unknown = Action inconnue : { $arg0 }
anniversary-admin-audit-empty = Aucun enregistrement d'audit de gestion.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Auteur : { $arg0 } ({ $arg1 }) ; cible : { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Audit de gestion de l'anniversaire ({ $arg0 }/{ $arg1 } affichés)
anniversary-admin-bonus-cancelled = Terminé par le coffret
anniversary-admin-bonus-pending = Accordé, pas encore tiré
anniversary-admin-bonus-used = Tiré
anniversary-admin-boss = Gestion de l'anniversaire · Utilise { $arg0 } pour voir la progression
anniversary-admin-commands = Commandes : /anniversary winners [page] | /anniversary audit [joueur] | /anniversary bonus <joueur>
anniversary-admin-control = Contrôle dynamique : libéré { $arg0 } ; disponible maintenant { $arg1 } ; probabilité de base { $arg2 } ; rythme récent { $arg3 }
anniversary-admin-draw-bonus = Bonus de construction
anniversary-admin-draw-detail = { $arg0 } : tiré à { $arg1 } ; résultat { $arg2 }
anniversary-admin-draw-metrics = Probabilité { $arg0 } ; valeur aléatoire { $arg1 } ; version de l'algorithme { $arg2 }
anniversary-admin-draw-replaced = Remplacé par le coffret ; stock rétabli
anniversary-admin-draw-round = Manche normale { $arg0 }
anniversary-admin-event = État de l'événement : { $arg0 } ; période : { $arg1 }
anniversary-admin-excluded = Les administrateurs de l'anniversaire ne peuvent pas participer au tirage. La vue de gestion a été ouverte.
anniversary-admin-none = Aucun
anniversary-admin-opportunities = Tirages effectués : { $arg0 } ; prêts maintenant : { $arg1 } ; chances obtenues : { $arg2 } ; gains/pertes : { $arg3 }
anniversary-admin-participants = Participants : { $arg0 }
anniversary-admin-player-bag = Sac final de l'événement : { $arg0 }
anniversary-admin-player-bonus = Bonus de construction : { $arg0 } ; accordé : { $arg1 } ; par : { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Détails des chances de tirage
anniversary-admin-player-not-found = Participant à l'événement introuvable : { $arg0 }
anniversary-admin-player-outcome = Gains : { $arg0 } ; pertes : { $arg1 } ; tirages effectués : { $arg2 } ; bonus de construction : { $arg3 }
anniversary-admin-player-progress = Inscription : { $arg0 } ; temps en ligne éligible : { $arg1 } ; tirages normaux effectués : { $arg2 } ; tirages normaux prêts : { $arg3 }
anniversary-admin-player-title = Audit du participant : { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Actif
anniversary-admin-state-ended = Terminé
anniversary-admin-state-scheduled = Planifié
anniversary-admin-stock = Stock restant : { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | total { $arg4 }
anniversary-admin-target-excluded = { $arg0 } est administrateur de l'anniversaire et ne peut pas recevoir de chance de tirage.
anniversary-admin-title = Vue de gestion du cinquième anniversaire
anniversary-admin-winners-empty = Aucun gain n'a encore été réellement tiré.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · tiré à { $arg3 }
anniversary-admin-winners-title = Liste de remise des lots (page { $arg0 }/{ $arg1 }, { $arg2 ->
        [one] { $arg2 } lot
       *[other] { $arg2 } lots
    })
anniversary-bag-empty = Aucun lot gagné pour le moment
anniversary-bag-item-locked = Le sac de l'événement est en lecture seule
anniversary-bag-item-name = Lot du cinquième anniversaire · { $arg0 }
anniversary-bag-not-participating = Vous ne participez pas encore. Utilisez d’abord { $arg0 }.
anniversary-bag-title = Sac de l'événement du cinquième anniversaire
anniversary-bag-updated = Ton lot est dans le sac de l'événement. Utilise { $arg0 } pour le voir.
anniversary-bonus-already-granted = { $arg0 } a déjà reçu le tirage supplémentaire pour les constructions et ne peut pas le recevoir à nouveau.
anniversary-bonus-boss-ready = Tirage bonus de construction prêt · Utilise { $arg0 } pour tirer
anniversary-bonus-broadcast-won = [Tirage anniversaire] { $arg0 } a gagné { $arg1 } grâce au bonus de construction !
anniversary-bonus-draw-not-won = Tirage bonus de construction : aucun lot cette fois-ci.
anniversary-bonus-draw-sold-out = Tirage bonus de construction : aucun lot, car tous les lots ont été attribués.
anniversary-bonus-draw-won = Tirage bonus de construction : félicitations, vous avez gagné { $arg0 } !
anniversary-bonus-gift-pack-final = { $arg0 } a déjà gagné le coffret et ne peut plus recevoir de tirage bonus.
anniversary-bonus-grant-not-active = Le tirage supplémentaire pour les constructions ne peut pas être accordé hors de la période de l'événement.
anniversary-bonus-grant-success = Tirage supplémentaire pour les constructions accordé à { $arg0 }.
anniversary-bonus-granted = Vous avez reçu un tirage supplémentaire pour vos constructions acceptées. Utilisez /anniversary pour participer au tirage.
anniversary-bonus-participant-not-found = Aucun participant nommé { $arg0 } n'a été trouvé
anniversary-bonus-status-ready = Bonus de construction : un tirage supplémentaire est prêt.
anniversary-bonus-status-used = Bonus de construction : le tirage supplémentaire a été utilisé.
anniversary-boss-completed = Tirage anniversaire · Deux manches terminées
anniversary-boss-gift-pack-final = Coffret cadeau gagné · Utilise { $arg0 } pour voir le sac de l'événement
anniversary-boss-invite = Tirage du cinquième anniversaire · Utilise { $arg0 } pour participer
anniversary-boss-progress = Tirage anniversaire { $arg0 }/{ $arg1 } · Manche { $arg2 } dans { $arg3 }
anniversary-boss-ready = Manche { $arg0 } prête · Utilise { $arg1 } pour tirer
anniversary-broadcast-won = [Tirage anniversaire] { $arg0 } a gagné { $arg2 } à la manche { $arg1 } !
anniversary-cancel = Pas maintenant
anniversary-command-description = Participer au tirage du cinquième anniversaire du serveur
anniversary-confirm = Accepter et participer
anniversary-confirm-hint = Lance le décompte du temps en ligne hors AFK
anniversary-delivery-note = Pour une destination en Chine continentale, fournissez un identifiant SF valide compatible avec l’expédition protégée. Hors de Chine continentale, fournissez le vrai nom du destinataire, son adresse complète et un numéro valide. Payez votre part des frais, sinon le lot sera perdu.
anniversary-dialog-prizes = Lots : 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } et 5 { $arg3 }
anniversary-dialog-time = Période de l'événement : { $arg0 }
anniversary-dialog-title = Règles du tirage du cinquième anniversaire
anniversary-draw-not-won = Manche { $arg0 } : aucun lot cette fois-ci.
anniversary-draw-sold-out = Manche { $arg0 } : aucun lot, car tous les lots ont été attribués.
anniversary-draw-won = Manche { $arg0 } : félicitations, vous avez gagné { $arg1 } !
anniversary-ended = Le tirage du cinquième anniversaire est terminé
anniversary-ended-results = Tes résultats : { $arg0 }
anniversary-gift-pack-final = Le coffret est votre lot final. Il a remplacé et remis en stock { $arg0 ->
        [one] { $arg0 } autre lot
       *[other] { $arg0 } autres lots
    } ; tous les tirages restants sont terminés.
anniversary-gift-prize-not-owned = Vous ne possédez pas ce lot de l’événement : { $arg0 }
anniversary-gift-received = { $arg0 } vous a offert { $arg1 }. Le lot est maintenant dans votre sac de l’événement.
anniversary-gift-self = Vous ne pouvez pas vous offrir un lot à vous-même.
anniversary-gift-success = Vous avez offert { $arg0 } à { $arg1 }.
anniversary-gift-usage = Utilisation : { $arg0 }
anniversary-not-active = L'événement du cinquième anniversaire n'est pas actif actuellement
anniversary-not-started = L'événement du cinquième anniversaire n'a pas encore commencé. Période : { $arg0 }
anniversary-prize-badge = badge photo de groupe du cinquième anniversaire
anniversary-prize-gift-pack = coffret cadeau du cinquième anniversaire
anniversary-prize-keychain = porte-clés avec logo
anniversary-prize-mug = mug avec logo
anniversary-prize-not-won = aucun lot
anniversary-prize-sold-out = lots épuisés
anniversary-rule-1 = 1. Après inscription, vous pouvez participer au tirage toutes les 4 heures, sur deux manches. Les lots vont dans le sac virtuel de l’événement, avec un seul exemplaire de chaque type. Gagner le coffret ne laisse que ce lot et met fin aux tirages restants.
anniversary-rule-2 = 2. Le temps passé AFK ne compte pas pour débloquer les tirages. Il est interdit de contourner la détection AFK par quelque moyen que ce soit.
anniversary-rule-3 = 3. Le serveur prend en charge 80 % du coût total d'envoi des lots physiques, dans la limite de 50 CNY ; le gagnant paie intégralement le solde restant après déduction de cette prise en charge.
anniversary-rule-4 = 4. Pour une destination en Chine continentale, les gagnants doivent fournir un identifiant SF valide compatible avec l'expédition protégée. Pour une destination hors de Chine continentale, ils doivent fournir le vrai nom du destinataire, son adresse complète et un numéro valide. Sans les informations requises ou le paiement de leur part, le lot est perdu.
anniversary-rule-5 = 5. Les participations multiples avec d'autres comptes sont interdites et entraînent la disqualification.
anniversary-rule-6 = 6. Participer signifie accepter toutes les règles ci-dessus.
anniversary-rule-7 = 7. Les constructions acceptées sur le site valent 0,2 point pour les reprises, 0,5 pour les adaptations et 1 pour les originales ; celles acceptées avant l'événement sont multipliées par 0,8, et les constructions communes par 1,5 fois votre part de contribution. Un total d'au moins 2 donne un tirage supplémentaire, accordé manuellement par un administrateur.
anniversary-second-round-hint = La deuxième manche se débloque après 8 heures cumulées en ligne hors AFK.
anniversary-status-completed = Vous avez terminé les deux manches.
anniversary-status-gift-pack-final = Vous avez gagné le coffret cadeau. Votre lot final est fixé et tous les tirages restants sont terminés.
anniversary-status-progress = Manches terminées : { $arg0 }/{ $arg1 } ; temps en ligne valide : { $arg2 }
anniversary-status-remaining = Il reste { $arg1 } en ligne hors AFK avant la manche { $arg0 }.
anniversary-status-results = Résultats : { $arg0 }
anniversary-status-title = Progression du tirage anniversaire

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } est déjà banni
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Bulletin de bannissement]
ban-command-description = Bannir un joueur selon un niveau de gravité fixe
ban-database-error = Échec de la base des exclusions. Consultez le journal du serveur.
ban-dialog-back = Retour
ban-dialog-cancel = Annuler
ban-dialog-confirm-action = Bannir le joueur
ban-dialog-confirm-severity = Gravité
ban-dialog-confirm-title = Confirmer le bannissement
ban-dialog-never-joined-confirm-action = Bannir quand même
ban-dialog-never-joined-title = Le joueur ne s'est jamais connecté
ban-dialog-never-joined-warning = Aucune connexion à ce serveur n’est enregistrée pour { $arg0 }. Vérifiez le nom pour éviter d’exclure la mauvaise personne.
ban-dialog-next = Suivant
ban-dialog-player-input = Saisir un nom manuellement
ban-dialog-player-manual = Saisie manuelle
ban-dialog-player-online = Joueurs actuellement connectés
ban-dialog-select-title = Sélectionner un joueur
ban-dialog-severity-title = Sélectionner la gravité
ban-history-empty = Aucun historique d’exclusion pour { $arg0 }.
ban-history-title = Historique des exclusions : { $arg0 }
ban-invalid-player = Nom de joueur invalide
ban-invalid-severity = Niveau de bannissement invalide
ban-kick-appeal = En cas d'erreur, faites appel dans le groupe QQ 1138627074
ban-kick-server = MIK
ban-kick-title = Vous êtes banni de ce serveur
ban-not-banned = { $arg0 } n’a pas d’exclusion active.
ban-pardon-reason-required = Un motif de révocation est requis
ban-permanent = Permanent
ban-reason-required = Un motif de bannissement est requis
ban-self = Vous ne pouvez pas vous bannir vous-même
ban-severity-excusable = Excusable (30 jours)
ban-severity-extreme = Infraction extrême (1500 jours)
ban-severity-minor = Infraction mineure (150 jours)
ban-severity-severe = Infraction grave (360 jours)
ban-severity-warning = Avertissement (3 jours)
ban-status-active = Actif
ban-status-expired = Expirée
ban-status-revoked = Levée
ban-success = { $arg0 } est banni jusqu'au { $arg1 }
ban-sync-success = Exclusions synchronisées : actives { $arg0 } · historique { $arg1 }
ban-system-source = Système
ban-unban-success = Exclusion levée pour { $arg0 }
ban-unknown-reason = Aucun motif indiqué
ban-update-success = Champ d’exclusion mis à jour pour { $arg0 } : { $arg1 }
banlist-command-description = Voir les bannissements de joueurs
banlist-copy = Cliquer pour copier le nom du joueur
banlist-empty = Aucun joueur n'est banni
banlist-expires = Expiration
banlist-invalid-page = Page invalide. Total : { $arg0 ->
        [one] { $arg0 } page
       *[other] { $arg0 } pages
    }.
banlist-next = Suivant
banlist-page = Page { $arg0 }/{ $arg1 } · { $arg2 ->
        [one] { $arg2 } entrée
       *[other] { $arg2 } entrées
    }
banlist-player = Joueur
banlist-previous = Précédent
banlist-reason = Motif
banlist-source = { role-moderator }
banlist-title = Liste des bannissements
maintenance-command-description = Activer, désactiver ou consulter le mode maintenance
maintenance-set = Mode maintenance { $arg0 }
maintenance-state-disabled = désactivé
maintenance-state-enabled = activé
maintenance-status = Mode maintenance : { $arg0 }
maintenance-unchanged = Le mode maintenance est déjà { $arg0 }
restriction-entity-too-large-mm = <red>La taille de l'entité dépasse la limite de sécurité du serveur</red>
restriction-foreign-uuid-mm = <red>Vous ne pouvez utiliser les UUID que pour les monstres que vous avez apprivoisés</red>
restriction-no-kill-e-mm = <red>La commande <white>/kill @e</white> n'est pas autorisée</red>
restriction-no-newline-mm = <red>Les messages ne peuvent pas contenir de sauts de ligne</red>
restriction-no-run-command-mm = <red>Les messages ne peuvent pas inciter les joueurs à exécuter des commandes</red>
restriction-no-selector-mm = <red>Vous n'êtes pas autorisé à utiliser les sélecteurs de cible <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>Vous n'êtes pas autorisé à mentionner les noms d'autres joueurs dans les commandes</red>
tpsbar-command-description = Afficher ou masquer la barre TPS
tpsbar-hidden = Barre TPS masquée
tpsbar-shown = Barre TPS affichée
tpsbar-tip-status-mm = Utilise <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> pour afficher ou masquer la barre <yellow>TPS, MSPT et ping</yellow>.
unban-command-description = Lever une exclusion en conservant l’historique
whitelist-already-whitelisted = { $arg0 } est déjà sur liste blanche
whitelist-invalid-name = Format de nom de joueur invalide
whitelist-kick-apply-rich = Demandez votre ajout à la liste blanche sur { $site }
whitelist-kick-title = Vous n'êtes pas sur la liste blanche
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = { $arg0 } ajouté temporairement à la liste blanche pour { $arg1 }
whitelist-temp-added-rich = { $player } ajouté temporairement à la liste blanche pour { $duration }
whitelist-temp-command-description = Ajouter une entrée temporaire d'une heure sur la liste blanche
whitelist-temp-duration = 1 heure
whitelist-temp-usage-desc = Ajouter une entrée temporaire d'une heure sur la liste blanche

## Shell · Menus, server information and scripting

main-afk-current-afk = Actuellement AFK
main-afk-current-online = Actuellement en ligne
main-afk-status = Statut AFK
main-announcements = Annonces
main-home = Maisons
main-language = Langue
main-language-auto = Automatique
main-links = Liens
main-map = Carte en ligne
main-menu-title = Menu principal
main-music = Musique
main-name-tag = Étiquettes
main-profile = Profil
main-pvp = JcJ
main-settings = Paramètres
main-website = Site web
main-wiki = Wiki
skript-command-description = Voir la prise en charge des scripts du serveur
skript-info-ai-option = Vous ne voulez pas l'écrire ? Donnez ce prompt à une IA :
skript-info-copy = [Copier le prompt IA]
skript-info-copy-hover = Copier un prompt contenant les versions, les liens de documentation et les règles de sécurité
skript-info-documentation = Documentation de syntaxe et d'API
skript-info-review = Les scripts ne sont chargés qu'après vérification et test par un administrateur
skript-info-supported = Disponible : { $arg0 }
skript-info-title = Scripts du serveur
skript-info-workflow = Écrivez et vérifiez un fichier .sk complet avec la documentation, puis envoyez-le à un administrateur
stat-client-version = Version du client
stat-first-joined = Première connexion
stat-location = Emplacement
stat-ping = Latence
stat-play-time = Temps de jeu
stat-role = Rôle
stat-world = Monde
weblogin-command-description = Confirmer un code de connexion Mik Web
weblogin-confirmed = Connexion Mik Web confirmée. Revenez à votre navigateur pour continuer.
weblogin-invalid-code = Code de connexion Web invalide
