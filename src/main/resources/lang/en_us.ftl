### MIK · en-us
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = plot
    .gender = other

-vehicle-model = model
    .gender = other

## Shared · General

back = Back
back-to-main = Back to main menu
click-open = Click to open
close = Close
current-off = Current: Off
current-on = Current: On
password-already-connected = This account is already connected.
password-already-set = A password is already set. Use /password change to change it.
password-busy = Too many attempts. Wait a moment and try again.
password-change = Change password
password-change-done = Password changed.
password-checking = Checking account security…
password-command-description = Manage account password protection
password-confirm = Confirm new password
password-conflict = Account security changed. Try the operation again.
password-cooldown = Account verification is temporarily restricted. Try again in { $arg0 ->
        [one] { $arg0 } second
       *[other] { $arg0 } seconds
    }.
password-current = Current password
password-denied = You cannot reset this account’s password.
password-disabled = Password: not set
password-enabled = Password: set
password-invalid = Incorrect password. Try again.
password-keep-role = Moderators and custodians cannot remove their passwords.
password-login = Log in
password-login-expired = Your login verification has expired. Reconnect to the server.
password-login-hint = Enter this server’s password. Forgot it? Ask staff to reset it.
password-mismatch = The new passwords do not match.
password-new = New password
password-nomination-required = Set an account password before applying for moderator: /password set
password-not-set = No password is set. Use /password set to set one.
password-remove = Remove password
password-remove-done = Password removed.
password-remove-question = Removing your password disables password checks at login. Enter your current password to confirm.
password-required-application = You cannot remove your password while a staff application or appointment is pending.
password-required-role = Staff accounts require a password. Ask an authorized staff member to reset it before logging in.
password-reset = Reset password
password-reset-console = Password reset for { $arg0 }: { $arg1 }. Deliver it to the verified owner through a trusted channel.
password-reset-done = Password reset for { $arg0 }. Copy the new password now and deliver it to the verified owner; it cannot be viewed again.
password-reset-question = Reset the password for { $arg0 }? The old password will stop working and their connections will be closed. Enter your own password to confirm.
password-retry = Try again
password-rules = Use { $arg0 }–{ $arg1 } characters. Player names and common passwords are not allowed.
password-set = Set password
password-set-done = Password set.
password-set-first = Set password before applying
password-storage = Account security is unavailable. Try again later or contact the server owner.
password-timeout = Password verification was cancelled or timed out. Reconnect to try again.
password-tip-set-mm = Use <gold><u>/password</u></gold> to set a <yellow>login password</yellow> and protect your account.
password-title = Account security

player-only = This command can only be used by players
unknown = Unknown
usage = Usage

## Shared · Language, roles and time

language-command-description = Open language settings
language-menu-title = Language Settings
language-selected = Selected
language-set = Language set to { $arg0 }
role-custodian = Custodian
role-member = Member
role-moderator = Moderator
role-new-player = New Player
time-hours-minutes = { $arg0 }h { $arg1 }m
time-minutes = { $arg0 }m

## Shared · Menu presentation

bedrock-menu-back = Back
bedrock-menu-hotkey = Shortcut action (F)
bedrock-menu-primary = Primary action
bedrock-menu-scroll-down = Scroll down
bedrock-menu-scroll-up = Scroll up
bedrock-menu-secondary = Secondary action
interface-fov-apply = Apply FOV
interface-fov-custom = Enter an exact value
interface-fov-description = Match your client FOV; only menu placement changes. Default: 70°.
interface-fov-input = Client FOV: an integer from 30 to 110
interface-fov-invalid = Enter a whole number from 30 to 110.
interface-fov-title = View fitting (FOV)
interface-scale-current = Current: { $arg0 }
interface-scale-description = Text size multiplies menu size; adjust either group with the mouse wheel.
interface-scale-layout-title = Menu size
interface-scale-menu-title = Interface & Text
interface-scale-native-hint = These settings affect Java spatial menus. Native form text follows your client settings.
interface-scale-preview = Text preview Aa 123
interface-scale-reset = Reset both to 100%
interface-scale-text-title = Text size
menu-rhythm-game-display = Playing Rhythm Game
menu-usage-display-mm = <aqua><bold>In Menu</bold></aqua>
url-dialog-confirm = Open
url-dialog-hint = Your client will ask you to confirm opening the link again.
url-dialog-question-rich = Open { $label }?
url-dialog-title = Open { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Recommended mods: { $mods }. Click a name to download.
client-tip-axiom-flight-speed-mm = <white>Hold <yellow>Left Alt</yellow> to open the <aqua>Axiom</aqua> menu, then drag the vertical slider on the right to adjust flight speed. You can also use <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click>.</white>
client-tip-axiom-intro-rich = { $mod } is a building mod with blueprint copy and paste, terrain and display entity editing, no-update placement, noclip, and multiple saved hotbars. Click its name to download.
client-tip-voxy-intro-rich = { $mod } renders more distant scenery with low overhead. Worth trying on modest and powerful PCs alike. Click its name to download.
client-version-behind-mm = <white>Your client <yellow>{ $clientVersion }</yellow> is older than the server <green>{ $serverVersion }</green>. Consider updating.</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> nearby player is <gold>invisible</gold>
       *[other] <gold>{ $count }</gold> nearby players are <gold>invisible</gold>
    }</yellow> <gray>·</gray> <gray>nearest about { $distance ->
        [one] { $distance } block
       *[other] { $distance } blocks
    }</gray>
player-bedrock-label = Bedrock
presence-join-templates =
    .item-1 = { $player } slipped into the server unnoticed
    .item-2 = { $player } crossed a chunk border and arrived
    .item-3 = { $player } arrived by way of the grass blocks
presence-quit-templates =
    .item-1 = { $player } slipped away while nobody was looking
    .item-2 = { $player } stepped away from this world for now
    .item-3 = { $player } returned to the real world
welcome-first-join-mm = <gold><bold>Welcome to MIK!</bold></gold><newline><gray>Start with the <click:open_url:'https://mcmik.top'><yellow>website</yellow></click> and <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>; you can also use <yellow>/menu</yellow> to open the main menu.</gray>
welcome-return-mm = <gold><bold>Welcome back!</bold></gold><newline><gray>Your last visit was <yellow>{ $arg0 ->
        [one] { $arg0 } day
       *[other] { $arg0 } days
    } ago</yellow>. Use <yellow>/menu</yellow> to quickly find common features.</gray>

## Player · Guidance

tip-label = TIP

## Player · AFK

afk-already-afk-mm = <gray>You are already <gold>AFK</gold></gray>
afk-command-description = Enter AFK status
afk-default-statuses =
    .item-1 = stepped away from the keyboard
    .item-2 = entered power-saving mode
    .item-3 = switched to the background
    .item-4 = staring into space
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>is now AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>left a note:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>changed their status to:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>returned to the keyboard</green>
    .item-2 = <gray>*</gray> <player> <green>is back</green>
afk-not-afk-mm = <gray>You are not currently <gold>AFK</gold></gray>
afk-status-too-long-mm = <red>AFK status can be at most { $arg0 ->
        [one] <white>{ $arg0 }</white> character
       *[other] <white>{ $arg0 }</white> characters
    }</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · matches: { $arg2 ->
        [one] { $arg2 } record
       *[other] { $arg2 } records
    } · their history: { $arg3 }
player-association-header = [Login association] { $arg0 } shares address history with { $arg1 ->
        [one] { $arg1 } player
       *[other] { $arg1 } players
    }.
player-association-hover = Historical network records overlap. This is only an investigation aid; addresses are not shown.
player-association-more-addresses = { $arg0 ->
        [one] { $arg0 } record
       *[other] { $arg0 } records
    } more
player-association-more-players = { $arg0 ->
        [one] { $arg0 } player
       *[other] { $arg0 } players
    } more
player-association-time-days = { $arg0 ->
        [one] { $arg0 } day
       *[other] { $arg0 } days
    } ago
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } record
       *[other] { $arg1 } records
    })
player-association-time-separator = ,
player-association-time-today = today

## Player · Profile and tab list

nametag-clear-all-error = Error while clearing name tags. Please try again later
nametag-clear-all-success = ✔ Cleared all name tags
nametag-clear-error = Error while clearing { $arg0 }
nametag-clear-success = ✔ Cleared your { $arg0 }
nametag-click-fill-command = Click to fill the command
nametag-command-description = Customize name tags
nametag-copy-hover = Copy the MiniMessage source to clipboard
nametag-copy-label = [copy]
nametag-effect-label = Result
nametag-empty-content-rich = Content cannot be empty. To clear it, use { $command }
nametag-format-docs = Formatting docs
nametag-leading-label = Name prefix
nametag-no-permission = You do not have permission to use this command
nametag-online-editor = Online editor
nametag-preview-label = Preview
nametag-sample-message = sample message
nametag-set-error = Error while setting { $arg0 }. Please try again later
nametag-set-success = ✔ { $arg0 } set
nametag-tip-customize-mm = Use <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> to customize your <light_purple>name prefix and suffix</light_purple>.
nametag-title = Name Tags
nametag-too-long = { $arg0 } is too long. Maximum length is { $arg1 ->
        [one] { $arg1 } character
       *[other] { $arg1 } characters
    }
nametag-trailing-label = Name suffix
nametag-unset = (not set)
nametag-unsupported-placeholder-rich = Only { $supported } PlaceholderAPI variables are supported, not { $unsupported }
nametag-user-data-load-failed = Could not load user data. Please try again later
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Online players: { $online } / { $max }
tablist-footer-ping-rich = Ping: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Return to your previous teleport/death location
back-empty-mm = <gray>No return location available</gray>
back-pending-mm = <gray>Teleporting, please wait</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>Went back { $steps ->
        [one] { $steps } step
       *[other] { $steps } steps
    }</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>Teleport failed. Please try again</red>
back-tip-return-mm = Use <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> to return to your location before the last teleport or death.
back-tip-undo-mm = Use <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> to undo your last <yellow>/back</yellow>.
back-undo-empty-mm = <gray>No <white>/back</white> to undo</gray>
back-undo-success-mm = <green>Undid the last <white>/back</white></green>
back-undo-world-missing-mm = <red>Target world is unavailable. Cannot undo</red>
back-world-missing-mm = <red>Target world is unavailable. Cannot return</red>
boundary-near-mm = <yellow>You are approaching the world border</yellow>
boundary-reached-mm = <red>You have reached the world border</red>
fly-command-description = Toggle personal flight
fly-disabled = Flight disabled
fly-enabled = Flight enabled
fly-native-mode = Your /fly access is disabled. This game mode still permits flight.
fly-speed-click = Set flight speed to { $arg0 }x
fly-speed-current = Flight speed: { $arg0 }x
fly-speed-presets = Presets
fly-speed-reset = Flight speed reset to 1x
fly-speed-reset-action = Reset
fly-speed-reset-click = Reset flight speed to 1x
fly-speed-set = Flight speed set to { $arg0 }x
fly-state-disabled = Disabled
fly-state-enabled = Enabled
fly-status = Flight: { $arg0 } | Speed: { $arg1 }x
flyspeed-command-description = View or set personal flight speed
home-action-create = Create home
home-action-delete = Delete home
home-action-icon = Set icon from hand
home-action-teleport = Teleport
home-action-update = Update location
home-already-exists = A home with this name already exists
home-back = Back
home-confirm-delete = Confirm Delete
home-confirm-delete-detail-rich = Delete home { $home }
home-confirm-delete-warning = This cannot be undone
home-confirm-update = Confirm Update
home-confirm-update-detail-rich = Update home { $home } to this location
home-delete-command-description = Delete a home
home-delete-usage-desc = Delete a home
home-deleted-rich = Home { $home } has been deleted
home-distance = Distance: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    }
home-distance-other-world = different world
home-empty-description = Stand where you want to save a home
home-empty-title = No homes yet
home-icon = Icon: { $arg0 }{ $arg1 }
home-icon-default-note = · default
home-icon-hand-invalid = The item in your main hand cannot be used as a home icon
home-icon-updated-rich = Home { $home } icon has been updated
home-invalid-icon = This material cannot be used as a home icon
home-invalid-name = Home names cannot contain < > : characters
home-list-rich = Homes: { $homes }
home-location = Location: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = This home's location data is invalid
home-max-reached = You can set at most { $arg0 } homes
home-menu-title = Homes
home-no-homes = You have not set any homes yet
home-not-found-rich = Home { $home } does not exist
home-set-command-description = Set a home at your current location
home-set-rich = Home { $home } has been set
home-set-usage-desc = Set a home at your current location
home-summary-count = Set: { $arg0 } / { $arg1 }
home-teleport-command-description = Teleport to a home
home-teleport-failed = Teleport failed. Please try again
home-teleported-rich = Teleported to home { $home }
home-tip-create-mm = Save your location with <green><u><click:suggest_command:'/sethome '>/sethome name</click></u></green>, then return with <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>.
home-tip-return-mm = Open <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua> and select a home to teleport.
home-updated-rich = Home { $home } location has been updated
home-usage-book = Command Help
home-usage-delhome = /delhome <name>  Delete a home
home-usage-home = /home tp <name>  Teleport directly
home-usage-icon = /home icon <material> <name>  Set an icon
home-usage-name-rule = Home names cannot contain < > :
home-usage-sethome = /sethome <name>  Save your current location
home-world = World: { $arg0 }
home-world-end = · End
home-world-nether = · Nether
reback-command-description = Undo the last /back
teleport-accept-command-description = Accept the pending teleport request
teleport-allow = Teleports by other players
teleport-bare-entity-selector = To prevent accidental teleports, @e requires brackets. Use an explicit selector such as @e[type=…].
teleport-block-afk = Block while AFK
teleport-denied = { $player } currently rejects teleports by other players
teleport-deny-command-description = Deny the pending teleport request
teleport-force-command-description = Force teleport, ignoring player preferences (staff only)
teleport-menu-title = Teleport Privacy
teleport-moved-here = You were teleported here by { $arg0 }
teleport-policy-always-allow = Always allow
teleport-policy-always-deny = Always deny
teleport-policy-require-consent = Require my approval
teleport-request-accept = Accept
teleport-request-approved = { $player } accepted your teleport request
teleport-request-deny-action = Deny
teleport-request-expired = The teleport request expired
teleport-request-failed = The teleport could not be completed. Please try again
teleport-request-none = You have no pending teleport request
teleport-request-received = { $sender } wants to teleport you: { $command }
teleport-request-rejected = Teleport request denied
teleport-request-sent = Teleport request sent to { $player }
teleport-summary-afk = Always allowed, denied while AFK
teleport-summary-allow = Always allowing teleports by other players
teleport-summary-consent = Teleports by other players require my approval
teleport-summary-deny = Always denying teleports by other players
tpany-command-description = Teleport to a player location, online or offline
tpany-done-offline-rich = Teleported to { $player }'s last known location { $world } { $location }
tpany-done-online-rich = Teleported to { $player }'s current location
tpany-not-found-rich = Could not find { $player }'s location
tpany-usage = Usage
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teleport to a player location, including offline cache

## Player · PVP

pvp-admin-menu-title = PVP Admin - Page { $arg0 }/{ $arg1 }
pvp-allow-mounted-damage = Mounted mobs can be attacked
pvp-allow-mounted-damage-desc = When enabled, another player can attack a mob you are riding while both of you have PVP enabled
pvp-auto-enable-desc = Attack a PVP-enabled player twice in a row
pvp-auto-enable-label = Auto enable
pvp-auto-enable-warning-actionbar-mm = <yellow>Attack once more to enable PVP automatically</yellow>
pvp-auto-enabled-actionbar-mm = <green>PVP enabled automatically</green>
pvp-back-admin = Back to PVP Admin
pvp-combat-locked-mm = <red>You cannot disable PVP in combat</red> <dark_gray>|</dark_gray> <gray>Time remaining: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> second
       *[other] <yellow>{ $arg0 }</yellow> seconds
    }</gray>
pvp-combat-tag-label = Combat cooldown
pvp-combat-tag-value = { $arg0 } sec
pvp-command-description = Toggle PVP status
pvp-enable = Enable PVP
pvp-enable-desc = When enabled, you can attack other PVP-enabled players, and they can attack you
pvp-enable-on-death = Enable PVP on death
pvp-enable-on-death-desc = When enabled, PVP turns on automatically when you die
pvp-enable-on-death-label = Enable on death
pvp-enabled-on-death-mm = <yellow>PVP was enabled because you died</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Open online player PVP admin</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Open PVP settings</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Manage an online player</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>View your PVP status</gray>
pvp-help-title-mm = <gold><bold>PVP Commands</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Toggle your PVP status</gray>
pvp-menu-title = PVP Settings
pvp-mob-protected-actionbar-mm = <yellow>This mob is protected from PVP damage</yellow>
pvp-mob-protection-label = Mob protection
pvp-mounted-damage-label = Mounted mob damage
pvp-next-page = Next Page
pvp-no-permission-mm = <red>You do not have permission to manage another player's PVP status</red>
pvp-player-not-found-mm = <red>Online player not found</red>
pvp-prev-page = Previous Page
pvp-protect-mobs = Protect your mobs
pvp-protect-mobs-desc = Protect mobs you have tamed or are riding
pvp-set-by-staff-mm = <aqua>Staff set your PVP status to</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = Set { $arg0 }'s PVP status to { $arg1 }
pvp-set-other-rich = Set { $player }'s PVP status to { $state }
pvp-state-label = PVP
pvp-state-off = Off
pvp-state-on = On
pvp-status-player = Player: { $arg0 }
pvp-status-player-rich = Player: { $player }
pvp-status-title-mm = <gold><bold>PVP Status</bold></gold>
pvp-summary-disabled = PVP disabled
pvp-summary-enabled = PVP enabled
pvp-target-disabled-actionbar-mm = <yellow>That player does not have PVP enabled</yellow>
pvp-target-menu-title = PVP Settings - { $arg0 }
pvp-tip-protection-mm = Toggle your PVP with <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple>. <green>Both players must enable it</green> to fight.
pvp-toggled-off-mm = <gray>PVP disabled</gray> <dark_gray>|</dark_gray> <gray>Other players cannot attack you</gray>
pvp-toggled-on-mm = <green>PVP enabled</green> <dark_gray>|</dark_gray> <gray>You can now fight other PVP-enabled players</gray>

## Player · Effects

effect-cleanup-all = [Clear all effects]
effect-cleanup-blocked = Effects can only be cleared in Creative or Spectator mode outside PVP combat.
effect-cleanup-expired = This cleanup request has expired.
effect-cleanup-negative = [Clear negative effects]
effect-cleanup-prompt-rich = You gained negative effects from { $source }. Clear them?
effect-cleanup-result = { $count ->
        [one] Cleared { $count } effect.
       *[other] Cleared { $count } effects.
    }
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] a lingering potion cloud
        [ARROW] an arrow
        [ATTACK] an attack
        [COMMAND] a command
        [FOOD] food
        [PLUGIN] a plugin
        [POTION_DRINK] drinking a potion
        [POTION_SPLASH] a splash potion
        [WARDEN] a warden
        [WITHER_ROSE] a wither rose
       *[OTHER] an unknown source
    }

## Player · Trash

trash-clear = Clear
trash-clear-hint = Click to clear all items now;
    otherwise, they are cleared automatically { $arg0 } seconds after closing.
trash-command-description = Open trash
trash-sort = Sort
trash-sort-hint = Merge matching stacks and sort items by type
trash-tip-dispose-mm = Use <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> to open the <yellow>trash</yellow> and put unwanted items inside.
trash-title = Trash

## Player · Utilities

hat-command-description = Wear the item in your main hand
hat-empty-hand-mm = <red>No item in your main hand</red> <gray>Hold a block or item, then use /hat</gray>
hat-success-mm = <green>Wearing the item from your main hand</green> <dark_gray>|</dark_gray> <gray>Your hand and helmet slot were swapped</gray>
hat-tip-wear-mm = Use <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> to wear the item in your <yellow>main hand</yellow> on your head.
removeitems-command-description = Remove dropped items
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Removed { $countValue ->
        [one] { $count } dropped item
       *[other] { $count } dropped items
    } in world { $world } { $location } { $radius }
removeitems-no-world = Could not determine the world
removeitems-radius = radius { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    }
removeitems-tip-usage-mm = Use <aqua><u><click:suggest_command:'/rmitems '>/rmitems [radius]</click></u></aqua> to clear nearby <yellow>dropped items</yellow>.
self-kick-command-description = Kick yourself from the server
self-kick-reason-too-long = The kick reason cannot exceed { $arg0 ->
        [one] { $arg0 } character
       *[other] { $arg0 } characters
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] You left the server at your own request. Reason: { $reason }
       *[third] { $player } kicked themselves from the server because: { $reason }
    }
    .item-1 = { $player } kicked themselves out, and the reason was somehow: { $reason }
    .item-2 = { $player } actually kicked themselves because of { $reason }!
    .item-3 = { $player } left the server voluntarily and left this explanation: { $reason }
    .item-4 = { $player } chose to self-kick: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] You left the server at your own request.
       *[third] { $player } kicked themselves from the server.
    }
    .item-1 = { $player } somehow kicked themselves out!
    .item-2 = { $player } left the server without saying a word.
    .item-3 = { $player } performed a flawless self-kick.
    .item-4 = { $player } pressed the leave-server button themselves.

## Community · Chat and mentions

chat-copy-hover = Left-click to copy message
chat-delay-3s = 3 seconds
chat-delay-5s = 5 seconds
chat-delay-7s = 7 seconds
chat-delay-cancel-command-description = Cancel pending delayed messages
chat-delay-cancelled = Cancelled pending message
chat-delay-current = Current chat delay: { $arg0 }
chat-delay-nothing-to-cancel = No pending message to cancel
chat-delay-off = Off
chat-delay-preview-marker = [Pending]
chat-delay-queued = Message will be sent in { $arg0 }; use /cancel or /c to cancel
chat-item-empty-hover = No item in main hand
chat-mention-all-hover = Notify every visible player in this channel
chat-msg-command-description = Send a private message and switch to private chat
chat-player-hover = Left-click to start a private message
chat-private-enter = Switched to private chat with { $arg0 }
chat-private-label = MSG
chat-private-no-reply-target = No recent private chat target is online
chat-private-self = You cannot start a private chat with yourself
chat-private-target-not-found = Online player not found: { $arg0 }
chat-private-target-offline = Private chat target is offline; returned to public chat
chat-private-tip-channel-mm = Use <aqua><u><click:suggest_command:'/msg '>/msg player</click></u></aqua> to enter private chat; <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> returns to public chat.
chat-public-command-description = Switch public chat channel
chat-public-enter = Switched to public chat
chat-reply-command-description = Reply to your latest private chat target
chat-settings-menu-title = Chat Settings
chat-social-account-label = Platform account
chat-social-bound-player-label = Linked player
chat-social-name-label = Social name
chat-social-unbound = Not linked to a Minecraft player
chat-staff-command-description = Switch staff chat channel
chat-staff-permission-missing = Staff chat permission missing; returned to public chat
mention-action-bar = Action bar prompt
mention-action-bar-text = { $sender } mentioned you
mention-alerts = Mention alerts
mention-menu-title = Mention Alerts
mention-mute-afk = Mute while AFK
mention-sound = Alert sound
mention-summary-action-bar = action bar
mention-summary-disabled = Alerts disabled
mention-summary-enabled = Alerts enabled
mention-summary-enabled-afk = Alerts enabled, muted while AFK
mention-summary-modes-afk = { $arg0 }, muted while AFK
mention-summary-sound = sound
staffchat-enter = Joined staff chat

## Community · Announcements

announcement-empty = No announcements
announcement-first-page = At the newest notices
announcement-last-page = At the oldest notices
announcement-more-count = { $arg0 } more not shown
announcement-new-count = { $arg0 } new
announcement-newer = Newer notices
announcement-older = Older notices
announcement-reload = Reload announcements
announcement-reload-command-description = Reload announcement file
announcement-reload-failed = Could not reload announcements; previous announcements are still available
announcement-reloaded = Announcements reloaded
announcement-scroll-hint = Scroll to browse horizontally
announcement-unread-count = { $arg0 } unread
announcement-updated = Announcements updated
announcement-view-all = View all announcements
announcement-view-command-description = View server announcements

## Community · Identity and social platforms

identity-admin-binding-not-found = No unambiguous player and platform link was found.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Removed { $arg2 ->
        [one] { $arg2 } identity link
       *[other] { $arg2 } identity links
    } ({ $arg0 }) for player { $arg1 }.
identity-admin-command-description = Inspect or revoke external identity links
identity-admin-help = /bindadmin lookup <player-or-UUID>
    /bindadmin unlink <player-or-UUID> <platform> confirm
identity-admin-lookup-empty = No identity links were found for that player.
identity-admin-lookup-title = Identity links for { $arg0 }:
identity-binding-details = · { $arg0 } · scope { $arg1 } · { $arg2 }
identity-binding-not-owned = You have no links for that platform.
identity-binding-unlinked = Removed { $arg1 ->
        [one] { $arg1 } identity link
       *[other] { $arg1 } identity links
    } ({ $arg0 }).
identity-bindings-empty = You have not linked any external platform accounts.
identity-bindings-title = Linked external identities:
identity-bindings-unlink-hint = Use /bind unlink <platform> to remove all of your links for a platform.
identity-code-cancelled = The unused { $arg0 } link code was cancelled.
identity-code-copy = Click to copy the complete linking command
identity-code-expiry = The code is valid for 5 minutes and can be used once. Generating another code invalidates it.
identity-code-issued = { $arg0 } link code generated:
identity-code-not-pending = There is no unused { $arg0 } link code.
identity-code-redeem = In the target { $arg0 } group, mention the bot and send: { $arg1 }
identity-command-description = Link Minecraft to an external platform identity
identity-help = Identity linking commands:
    /bind qq — quickly generate a QQ linking command
    /bind <platform> — generate a one-time code
    /bind list — show existing links
    /bind cancel <platform> — cancel an unused code
    /bind unlink <platform> — remove all links for a platform
identity-notify-linked = Your { $arg0 } identity was verified. Use /bind list to inspect it or /bind unlink { $arg0 } to remove the platform links.
identity-notify-unlinked = Your { $arg0 } identity link was removed.
identity-platform-none = none
identity-platforms = Supported platforms: { $arg0 }
identity-scope-notice = This platform isolates identities by group. Link in the group where you will use the bot; each group must be linked separately.
identity-storage-error = The identity-link database operation failed. Contact an administrator.
identity-unavailable = Identity linking is currently unavailable. Contact an administrator.
identity-unsupported-platform = Unsupported platform: { $arg0 }
social-admin-conversations-empty = No conversations observed for { $arg0 }.
social-admin-conversations-title = Observed conversations for { $arg0 }:
social-admin-help = /social platforms · /social status [platform] · /social reload [platform] · /social conversations <platform>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = Reloaded social platform { $arg0 }.
social-admin-reloaded-all = Reloaded all social platforms.
social-admin-status = { $arg0 } · { $arg1 } · generation { $arg2 } · conversations { $arg3 }
social-admin-title = Social platforms
social-admin-unknown = Unknown social platform: { $arg0 }
social-binding-already-linked = This platform identity is already linked to Minecraft player { $arg0 }.
social-binding-code-invalid = The verification code is invalid or expired. Generate a new one with /bind { $arg0 } in Minecraft.
social-binding-external-in-use = This social identity is already linked to another Minecraft player.
social-binding-help = Run /bind { $arg0 } in Minecraft.
    Copy the complete generated command.
    Send the command here.
    Use /profile to verify the link.
social-binding-identity-missing = This message has no trusted member identity, so identity binding is unavailable.
social-binding-linked = Linked to Minecraft player { $arg0 }.
social-binding-not-bound = This social identity is not linked.
social-binding-platform-mismatch = This verification code was issued for another platform.
social-binding-player-scope-in-use = That Minecraft player already has a different identity linked in this conversation.
social-binding-rate-limited = Too many failed attempts. Try again in about { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }.
social-binding-status-unbound = This social identity is not linked. Use /bind with a verification code.
social-binding-unavailable = Identity binding is temporarily unavailable.
social-binding-unlink-prompt = Unlinking removes this social identity from Minecraft. Send /unbind confirm to continue.
social-binding-unlinked = Unlinked from Minecraft player { $arg0 }.
social-command-description = Manage social platform adapters
social-duration-days = { $arg0 ->
        [one] { $arg0 } day
       *[other] { $arg0 } days
    } { $arg1 ->
        [one] { $arg1 } hour
       *[other] { $arg1 } hours
    } { $arg2 ->
        [one] { $arg2 } minute
       *[other] { $arg2 } minutes
    }
social-duration-hours = { $arg0 ->
        [one] { $arg0 } hour
       *[other] { $arg0 } hours
    } { $arg1 ->
        [one] { $arg1 } minute
       *[other] { $arg1 } minutes
    }
social-duration-minutes = { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }
social-help-command-description = Show this command list
social-link-command-description = Link your Minecraft account with an in-game code
social-online-command-description = Show online players
social-profile-afk = Online · AFK
social-profile-avatar-alt = Minecraft face
social-profile-command-description = Show your profile; full members can query by player name, mention, or reply
social-profile-first-joined-label = First joined
social-profile-language-label = Language
social-profile-last-seen-label = Last seen
social-profile-member-required = Only linked members, moderators, or custodians can view another player's profile.
social-profile-offline = Offline
social-profile-online = Online · active
social-profile-play-time-label = Play time
social-profile-player-label = Player
social-profile-player-not-found = That Minecraft player was not found.
social-profile-presence-label = Presence
social-profile-target-ambiguous = Choose exactly one target: a player name, one mention, or one replied-to user.
social-profile-target-unbound = The mentioned or replied-to identity is not linked to Minecraft.
social-profile-uuid-label = UUID
social-query-active-label = Active
social-query-no-players = No players are currently online.
social-query-online-label = Online
social-query-players-label = Players
social-query-unknown = Unknown command. Send /help to view available commands.
social-query-uptime-label = Uptime
social-reply-hidden = This reply was hidden by the server content-safety policy.
social-state-disabled = disabled
social-state-failed = failed
social-state-ready = ready
social-state-starting = starting
social-state-stopped = stopped
social-status-command-description = Show players, TPS, MSPT and uptime
social-title-command-help = Command help
social-title-content-hidden = Content hidden
social-title-identity-linking = Link account
social-title-identity-unlinking = Unlink account
social-title-online-players = Online players
social-title-player-profile = Player profile
social-title-server-status = Server status
social-title-unknown-command = Unknown command
social-unlink-command-description = Remove this identity link after confirmation

## Community · AI and knowledge

ai-busy = The AI is handling too many requests; try again shortly
ai-cleared = AI conversation history cleared
ai-command-description = Ask the in-game AI assistant
ai-disabled = The AI assistant is disabled or not configured
ai-knowledge-curation-failed = Knowledge curation failed: { $arg0 }
ai-knowledge-curation-finished = Knowledge curation finished: { $arg0 } applied, { $arg1 } skipped, { $arg2 } failed, { $arg3 } quarantined, { $arg4 } processed.
ai-knowledge-curation-started = Knowledge curation started.
ai-knowledge-disabled = Knowledge is disabled.
ai-knowledge-index-dirty = Index reconciliation required; private recall is blocked until synchronization.
ai-knowledge-last-failure = Last failure: { $arg0 }
ai-knowledge-last-sync = Last sync: { $arg0 }
ai-knowledge-learning-disabled = Knowledge learning is disabled.
ai-knowledge-operation-failed = Knowledge operation failed: { $arg0 }
ai-knowledge-operation-started = Knowledge operation started.
ai-knowledge-protected = Knowledge document { $arg0 } is now protected.
ai-knowledge-rollback = Knowledge document { $arg0 } restored as revision { $arg1 }.
ai-knowledge-status = Knowledge: { $arg0 ->
        [one] { $arg0 } public document
       *[other] { $arg0 } public documents
    } / { $arg1 ->
        [one] { $arg1 } text fragment
       *[other] { $arg1 } text fragments
    } · { $arg2 ->
        [one] { $arg2 } private document
       *[other] { $arg2 } private documents
    } / { $arg3 ->
        [one] { $arg3 } text fragment
       *[other] { $arg3 } text fragments
    }
ai-knowledge-sync-failed = Knowledge synchronization failed: { $arg0 }
ai-knowledge-sync-started = Knowledge synchronization started.
ai-knowledge-synchronized = { ai-knowledge-status } · Synchronization complete.
ai-knowledge-unprotected = Knowledge document { $arg0 } is no longer protected.
ai-learning-failure = Learning failure: { $arg0 }
ai-learning-status = Learning: candidates { $arg0 }, extracting { $arg1 }, queued { $arg2 }, extraction tasks dropped { $arg3 }, candidates dropped { $arg4 }, quarantined { $arg5 }
ai-memory-empty = No private memories for { $arg0 }.
ai-memory-list = Private memories for { $arg0 }: { $arg1 }
ai-memory-not-found = Private memory { $arg0 } was not found.
ai-memory-removed = Permanently removed { $arg0 ->
        [one] { $arg0 } private memory record
       *[other] { $arg0 } private memory records
    } for { $arg1 }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Unknown cached player: { $arg0 }
ai-prompt-too-long = The question is too long (maximum { $arg0 ->
        [one] { $arg0 } character
       *[other] { $arg0 } characters
    })
ai-reload-failed = Failed to reload AI configuration: { $arg0 }
ai-reloaded = AI configuration reloaded ({ $arg0 })
ai-request-failed = The AI request failed; try again later
ai-social-command-description = Ask the shared AI assistant
ai-social-question-required = Include a question after the AI command
ai-status-disabled = AI is disabled
ai-status-enabled = AI enabled · provider { $arg0 } · model { $arg1 } · tools { $arg2 }
ai-status-none = none
ai-thinking = Thinking…
ai-usage = Usage: /ai <question> · /ai clear · /ai status
ai-worker-status = AI tasks: running { $arg0 }, queued { $arg1 }, dropped { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Congratulations!</bold> You are now a <gold>Member</gold>. You now have access to more commands and features, including schematic easy printing.</green>
governance-active-days-30 = At least 2 active days in 30 days ({ $arg0 })
governance-active-days-60 = At least 6 active days in 60 days ({ $arg0 })
governance-active-header = ─── Active member eligibility ───
governance-active-member = Currently a member
governance-active-member-age = Member for at least 30 days
governance-active-playtime = At least 6 hours of effective play in 60 days ({ $arg0 })
governance-already-moderator = You are already a moderator.
governance-ballot-recorded = Your anonymous vote is recorded as { $arg0 }. You may change it before the deadline.
governance-ban-details = Proposed by { $arg0 } · Duration { $arg1 } · Reason: { $arg2 }
governance-ban-suffix = · Duration { $arg0 } · Reason: { $arg1 }
governance-choice-abstain = Abstain
governance-choice-no = No
governance-choice-yes = Yes
governance-command-description = View governance status and votes
governance-custodian-no-nomination = Custodians do not take part in moderator elections.
governance-custodian-no-resign = Custodians do not use the moderator resignation process.
# $arg0 (Number): duration; locale decides the unit form.
governance-days = { $arg0 ->
        [one] { $arg0 } day
       *[other] { $arg0 } days
    }
governance-error-ban-already-active = This player already has an active ban.
governance-error-ban-proposer-ineligible = Only active members may propose a ban vote.
governance-error-ban-self = You cannot propose a ban vote against yourself.
governance-error-candidate-ineligible = A moderator candidate must be an active member.
governance-error-nomination-not-open = You have no open moderator nomination.
governance-error-not-voter = You are not on this vote's frozen voter list.
governance-error-pause-already-active = This player already has an active promotion pause.
governance-error-pause-already-member = This player is already a member; promotion cannot be paused.
governance-error-pause-end-invalid = The promotion pause must end in the future.
governance-error-player-unknown = This player has no governance record yet.
governance-error-removal-requires-petition = A removal vote requires enough sponsors.
governance-error-removal-self = You cannot sponsor your own removal.
governance-error-removal-sponsor-ineligible = Only active members may sponsor a removal.
governance-error-removal-sponsors-too-few = At least two other active members are needed.
governance-error-removal-vote-open = This moderator already has an open removal vote.
governance-error-termination-reason-invalid = This reason does not apply to this vote.
governance-error-vote-already-open = This player already has an open vote of this kind.
governance-error-vote-closed = This vote has closed or been terminated.
governance-error-vote-cooldown = The three-day retry period after a failed quorum has not ended.
governance-error-vote-subject-unknown = The subject has no governance record yet.
governance-error-vote-termination-closed = The vote deadline has passed; it can no longer be withdrawn.
governance-error-vote-unknown = Vote not found.
governance-error-voters-too-few = At least two active members are needed to start a vote.
governance-failure-majority = Yes did not exceed half of all ballots
governance-failure-minimum-yes = Too few yes votes
governance-failure-none = No reason recorded
governance-failure-quorum = Quorum not met
governance-failure-two-thirds = Yes did not reach two-thirds of non-abstaining ballots
governance-history-header = ─── Recent vote results ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
# $arg0 (Number): duration; locale decides the unit form.
governance-hours = { $arg0 ->
        [one] { $arg0 } hour
       *[other] { $arg0 } hours
    }
governance-inactive-removal = { $arg0 } has automatically left the moderator role after 180 days without logging in.
governance-invalid-choice = Choose yes, no, or abstain.
governance-invalid-duration = Invalid duration. Use a value such as 30m, 12h, or 3d.
governance-kind-appointment = Appointment
governance-kind-ban = Ban
governance-kind-removal = Removal
governance-legacy-ban-kick = A previous governance vote passed. You are banned for { $arg0 }. Reason: { $arg1 }
governance-menu-actions = Actions
governance-menu-back = Back
governance-menu-cancel = Cancel
governance-menu-confirm = Confirm
governance-menu-confirm-nominate = Start a moderator appointment vote for yourself?
governance-menu-confirm-resign = Resign as moderator?
governance-menu-confirm-sponsor = Support this removal petition?
governance-menu-confirm-withdraw = Withdraw your moderator application?
governance-menu-find-player = Find another player
governance-menu-history = Vote history
governance-menu-loading = Loading governance status…
governance-menu-new-petition = Start removal petition
governance-menu-nominate = Apply for moderator
governance-menu-nomination-open = Your appointment vote is open
governance-menu-pause = Pause newcomer promotion
governance-menu-pause-appeal = Appeal path
governance-menu-pause-duration = Duration (e.g. 2d)
governance-menu-pause-reason = Reason
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } supporters
governance-menu-petitions = Removal petitions
governance-menu-player-name = Player name
governance-menu-players = Player management
governance-menu-recent-results = Recent vote results
governance-menu-refresh = Refresh
governance-menu-resign = Resign as moderator
governance-menu-resume = Lift promotion pause
governance-menu-resume-reason = Reason for lifting
governance-menu-staff-only = Only moderators and custodians can manage players.
governance-menu-status = Your status
governance-menu-target = Moderator name
governance-menu-title = Governance
governance-menu-votes = Open votes
governance-menu-withdraw = Withdraw application
# $arg0 (Number): duration; locale decides the unit form.
governance-minutes = { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }
governance-no-active-ban = No active ban
governance-no-active-pause = No active promotion pause
governance-nomination-withdrawn = Your nomination was withdrawn. The result will be announced automatically.
governance-none = None
governance-not-member = Not a member
governance-not-moderator = You are not a moderator.
governance-operation-error = Governance operation failed. Please try again.
governance-own-choice-closed = Your choice: { $arg0 }
governance-own-choice-open = Your choice: { $arg0 }; you can change it before the deadline.
governance-own-not-voted = You were eligible but did not vote.
governance-own-not-voted-hint = You have not voted. Open Governance and select this vote.
governance-own-not-voted-open = You have not voted yet.
governance-own-not-voter = You are not on this vote's voter list.
governance-own-voted = You voted
governance-pause-cleared = Promotion pause lifted for { $arg0 }.
governance-pause-details = Pause: { $arg0 } · Until { $arg1 } · Appeal: { $arg2 }
governance-pause-fields-required = Provide a reason and an appeal path.
governance-pause-not-active = { $arg0 } has no active promotion pause.
governance-pause-notice = Your promotion is paused. Reason: { $arg0 } · Until { $arg1 } · Appeal: { $arg2 }
governance-pause-set = Promotion for { $arg0 } is paused until { $arg1 }.
governance-petition-already-sponsored = You already sponsored this removal ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } sponsors · Until { $arg3 }
governance-petition-started = Removal petition for { $arg0 }: { $arg1 }/{ $arg2 } sponsors; until { $arg3 }.
governance-petitions-header = ─── Removal petitions ───
governance-player-first-join = First joined: { $arg0 }
governance-player-header = ─── { $arg0 } · Governance ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Last login: { $arg0 }
governance-player-member-since = Member since: { $arg0 }
governance-player-never-joined = Player { $arg0 } has never joined this server.
governance-promotion-account-age = First joined at least 3 days ago
governance-promotion-header = ─── Promotion eligibility ───
governance-promotion-play-days = Effective play on at least 2 days ({ $arg0 })
governance-promotion-playtime = At least 8 hours of effective play ({ $arg0 })
governance-resign-failed = Resignation failed. Please contact a custodian.
governance-resigned = { $arg0 } has resigned as moderator.
governance-result-announcement = Vote #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = Failed ({ $arg0 })
governance-result-open = Open
governance-result-passed = Passed
governance-result-tally = { $arg0 } · Voters { $arg1 }, participated { $arg2 }, yes { $arg3 }, no { $arg4 }, abstained { $arg5 }
governance-result-terminated = Terminated ({ $arg0 })
governance-resume-notice = Your promotion pause was lifted. Reason: { $arg0 }
governance-resume-reason-required = Provide a reason for lifting the pause.
governance-role-lookup-error = Could not verify moderator status. Please contact a custodian.
# $arg0 (Number): duration; locale decides the unit form.
governance-seconds = { $arg0 ->
        [one] { $arg0 } second
       *[other] { $arg0 } seconds
    }
governance-storage-error = Governance data operation failed. Please contact a custodian.
governance-target-not-moderator = { $arg0 } is not a moderator.
governance-termination-inactive = Moderator left after extended inactivity
governance-termination-ineligible = Candidate lost active-member status
governance-termination-resigned = Moderator resigned
governance-termination-unknown = Reason not recorded
governance-termination-withdrew = Candidate withdrew
governance-vote-electorate = Voters { $arg0 } · Quorum { $arg1 }
governance-vote-finalized = Finalized: { $arg0 }
governance-vote-header = ─── Vote #{ $arg0 } ───
governance-vote-in-progress = Open · { $arg0 } participated; category totals are shown after closing.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · Closes { $arg3 } · Voters { $arg4 } · Participated { $arg5 } · Quorum { $arg6 }{ $arg7 }
governance-vote-period = Opened { $arg0 } · Closes { $arg1 }
governance-vote-started = { $arg1 } vote #{ $arg0 } has started: { $arg2 }; voters { $arg3 }, quorum { $arg4 }, closes { $arg5 }. Open Governance to see the details.
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Open anonymous votes ───

## World · Plots

plot-access-removed-for-you = Your access to plot { $arg0 } was removed.
plot-admin-created = Registered { $arg0 } for { $arg1 } · { $arg2 }
plot-allowed = enabled
plot-area-changed = The boundaries changed while confirming. Refresh and try again.
plot-area-confirm = Replace the boundaries of “{ $arg0 }” with this selection ({ $arg1 ->
        [one] { $arg1 } building cell
       *[other] { $arg1 } building cells
    })? Buildings remain.
plot-area-hint = //pos1 → //pos2 → save. Wooden axe clicks also select corners; add/subtract for multiple areas.
plot-area-replace-save = Replace boundaries and save
plot-area-save = Save boundaries
plot-area-saved = Saved boundaries for “{ $arg0 }”.
plot-arrival-removed = Arrival point removed.
plot-arrival-set = Arrival point saved.
plot-arrival-text-saved = Arrival title saved.
plot-atmosphere-saved = Atmosphere saved.
plot-back = Back
plot-board-auto-notify-result = Alerts scheduled for { $arg0 ->
        [one] { $arg0 } player
       *[other] { $arg0 } players
    }.
plot-board-auto-registered-text = Plot "{ $arg0 }" was registered. Built-cell bounding coordinates: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Board record by { $arg0 } at { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }: { $arg5 }
plot-board-empty = No community records yet.
plot-board-header = Community notices and feedback (latest { $arg0 }; use /plot board ID for details)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Notice
plot-board-kind-objection = Objection
plot-board-menu = Community board
plot-board-notice-alert = { $arg0 } posted a notice: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } raised an objection: { $arg1 } · { $arg2 }
plot-board-rate-limited = Board posting limit reached. Try again later.
plot-board-record-invalid = Record not found, already resolved, or this action is not allowed.
plot-board-registered-alert = { $arg0 } registered plot "{ $arg1 }": { $arg2 }
plot-board-ruling-alert = A ruling was posted on { $arg0 }’s objection: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Ruling: { $arg0 }
plot-board-saved = Record { $arg0 } saved.
plot-board-status-elapsed = Posted at least 72 hours ago
plot-board-status-open = Open
plot-board-status-ruled = Ruled
plot-board-status-withdrawn = Withdrawn
plot-board-text-invalid = Write 12–1000 characters (a ruling needs at least 8).
plot-board-unavailable = Board records have not loaded. Retry later or visit https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } withdrew an objection: { $arg1 } · { $arg2 }
plot-board-write-failed = Could not update the board. Try again later.
plot-command-blocked = Plot protection blocked this command. Use /fill or /setblock directly inside your own plot; ask staff to review complex /execute commands.
plot-command-description = Manage building plots and public projects
plot-created = Created plot { $arg0 } · { $arg1 }
plot-created-for-you = { $arg1 } registered plot { $arg0 } for you.
plot-deleted = Plot released; the building itself was not deleted.
plot-denied = disabled
plot-empty-summary = No plot here.
plot-error-admin-owner-only = Changing members with management abilities requires member and permission management.
plot-error-area-disconnected = The plot must form one connected area.
plot-error-args = Invalid or missing arguments. Use /plot help.
plot-error-arrival-location = Stand on a safe floor within this project's built area.
plot-error-arrival-missing = No arrival point set.
plot-error-arrival-private = You cannot visit this private project.
plot-error-arrival-text = Invalid title. Use one line; up to 32 visible characters for the title and 64 for the subtitle.
plot-error-arrival-unsafe = This arrival point is unavailable or no longer safe.
plot-error-busy = Plot data is being saved. Please retry later; this operation was not submitted.
plot-error-flag = Unknown permission. Choose an operation from the permission list.
plot-error-id = Plot ID was not found or is ambiguous. Use /plot list.
plot-error-member-missing = This player is no longer a member.
plot-error-member-required = New players can view plots and raise objections. Become a Member to create a project.
plot-error-name = Project names must contain 1–48 characters.
plot-error-notice-text = Notices allow up to { $arg0 ->
        [one] { $arg0 } character
       *[other] { $arg0 } characters
    }, with no line count or per-line limit; control characters are not allowed.
plot-error-overlap = This building area overlaps another plot.
plot-error-owner-member = Configure the owner group instead of inviting the owner as a member.
plot-error-owner-only = You lack the management permission required for this operation.
plot-error-permission-changed = Permissions or members changed. Preview the changes again.
plot-error-permission-grant = You cannot grant management abilities you do not hold.
plot-error-permission-owner-scope = Only the plot owner, parent owner or server staff can edit owner permissions.
plot-error-player-unknown = No known player found. Check the name or UUID.
plot-error-release-owner-only = You lack permission to delete this plot.
plot-error-selection = Mark two corners with //pos1 and //pos2.
plot-error-selection-changed = Selection changed. Confirm again.
plot-error-staff-only = Only moderators or custodians can manage or register plots for other players.
plot-error-storage = Plot operation failed. Retry later or contact staff.
plot-error-subplot-arrival = Move the parent project's arrival point outside this selection first.
plot-error-subplot-children = Release the subplots before releasing their parent project.
plot-error-subplot-depth = Subplots can only be one level deep.
plot-error-subplot-disconnected = The selected building cells are disconnected. Divide each separate part on its own.
plot-error-subplot-overlap = The selection overlaps another subplot.
plot-error-subplot-own-arrival = Keep this subplot's arrival point inside the new area or move it first.
plot-error-subplot-owner = You lack permission for this subplot operation.
plot-error-subplot-parent = Mark both corners inside the parent's existing building cells.
plot-error-subplot-supervisor-member = The parent owner already has oversight here and does not need a member role.
plot-error-time-format = Enter a time as HH:mm (00:00–23:59).
plot-error-transfer-owner-only = You lack permission to transfer ownership.
plot-error-transfer-self = You already own this plot.
plot-flag-set = Set { $arg0 } = { $arg1 }.
plot-help = Help
plot-here = Here: { $arg0 } · { $arg1 }
plot-invited = Granted { $arg0 } access.
plot-item-summary = { $arg0 } block² · { $arg1 ->
        [one] { $arg1 } member
       *[other] { $arg1 } members
    }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } block²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Not submitted
plot-member-draft-reset = Clear category overrides
plot-member-draft-role = Role: { $arg0 }
plot-member-draft-submit = Preview and confirm
plot-member-draft-title = Member draft · { $arg0 }
plot-member-permissions = Permissions
plot-member-removed = Project member removed.
plot-menu-access = Access and members
plot-menu-access-by-settings = access by settings
plot-menu-admin = Add plot admin
plot-menu-arrival = Arrival point
plot-menu-arrival-subtitle = Subtitle (MiniMessage; optional)
plot-menu-arrival-text = Arrival title
plot-menu-arrival-title = Title (MiniMessage; blank uses project name)
plot-menu-atmosphere = Atmosphere
plot-menu-clear = Clear
plot-menu-collaborator = Add collaborator
plot-menu-confirm-delete = Delete the registration for { $arg0 }? Protection, member permissions and settings will be removed. This cannot be undone; buildings will remain.
plot-menu-confirm-subplot-transfer = Transfer { $arg0 } to { $arg1 }? The parent owner keeps oversight.
plot-menu-confirm-transfer = Transfer control of { $arg0 } to { $arg1 }? You will lose access. Existing authorship stays unchanged.
plot-menu-confirm-withdraw = Withdraw this feedback? Its withdrawn status will remain visible.
plot-menu-create = Create { -plot }
plot-menu-create-for-player = Register a plot for this player
plot-menu-custom-time = Custom time
plot-menu-delete = Delete plot registration
plot-menu-description = Description
plot-menu-feedback = Object to this notice
plot-menu-find-record = Find a record
plot-menu-flag-container = Open containers
plot-menu-flag-entity = Entity interaction
plot-menu-follow-parent = Follow parent
plot-menu-follow-world = Follow world
plot-menu-group-admin = plot admin
plot-menu-group-collaborator = Collaborators
plot-menu-group-member = Member defaults
plot-menu-group-newcomer = Newcomer defaults
plot-menu-group-owner = plot owner
plot-menu-manage = Manage member plots
plot-menu-managed-empty = This player has no plots yet.
plot-menu-managed-title = { $arg0 }'s plots
plot-menu-members = Members ({ $arg0 })
plot-menu-my-access = Your access: { $arg0 } · { $arg1 }
plot-menu-name = Project name
plot-menu-owner = Owner: { $arg0 }
plot-menu-parent-supervisor = Parent owner: { $arg0 } · oversight
plot-menu-player = Player name or UUID
plot-menu-project-kind = Project type
plot-menu-public-destinations = Public destinations
plot-menu-public-destinations-empty = No public projects with arrival points yet.
plot-menu-rain = Precipitation
plot-menu-record-id = Record ID; use the full ID for older records
plot-menu-related-notice = View related notice
plot-menu-remove-arrival = Remove arrival point
plot-menu-remove-member = Remove member
plot-menu-rename = Rename project
plot-menu-rule = Record ruling
plot-menu-ruling = Explain the ruling (8–1000 characters)
plot-menu-set-arrival = Set arrival here
plot-menu-set-role = Set as { $arg0 }
plot-menu-settings = Settings
plot-menu-submit = Apply
plot-menu-subplot-create = Create subplot
plot-menu-subplot-of = Part of { $arg0 }
plot-menu-subplot-parent = Parent project: { $arg0 }
plot-menu-subplots = Subplots ({ $arg0 })
plot-menu-time = Time
plot-menu-time-input = Time (HH:mm)
plot-menu-transfer = Transfer plot ownership
plot-menu-visit = Visit project
plot-menu-weather = Weather
plot-menu-withdraw = Withdraw feedback
plot-model-fit = Fit all boundaries
plot-model-focus = Focus near me
plot-model-layer-down = Lower cut by 1 block
plot-model-layer-up = Raise cut by 1 block
plot-model-legend = Cyan: saved · Green/gold: corners 1/2 · Purple: boundaries to save · White/red: you/facing
plot-model-level = Cut Y ≤ { $arg0 }
plot-model-limited = Preview details reduced
plot-model-north-up = Top view is north-up. Switch back to 3D to rotate.
plot-model-perspective = 3D view
plot-model-preparing = Validating boundaries…
plot-model-progress = Reading model { $arg0 }%…
plot-model-rotate-left = ↶ Rotate left
plot-model-rotate-right = ↷ Rotate right
plot-model-show-all = Show full height
plot-model-slice = Cut above my level
plot-model-terrain = Show buildings and terrain
plot-model-top = Top view
plot-model-unknown = { $arg0 ->
        [one] { $arg0 } unloaded sample
       *[other] { $arg0 } unloaded samples
    } (gray). Chunks are not loaded forcibly.
plot-model-wireframe = Boundaries only
plot-no-here = No protected plot at this location.
plot-notice-board = Plot notice board
plot-notice-cleared = Plot notice cleared
plot-notice-edit = Edit plot notice
plot-notice-input = Up to { $arg0 ->
        [one] { $arg0 } character
       *[other] { $arg0 } characters
    }; multiple lines allowed; leave blank to clear
plot-notice-read = Read full notice
plot-notice-saved = Plot notice updated
plot-permission-all-actions = All operations
plot-permission-animal = Care for, tame and leash animals
plot-permission-break = Break blocks
plot-permission-bucket = Use buckets for liquids and creatures
plot-permission-category-build = Construction
plot-permission-category-entity = Entities
plot-permission-category-interaction = Block interaction
plot-permission-category-management = Management
plot-permission-category-storage = Storage and items
plot-permission-change-children = Affected subplots: { $arg0 }
plot-permission-change-confirm = Apply changes
plot-permission-change-granted = Granted: { $arg0 }
plot-permission-change-members = Affected members: { $arg0 }
plot-permission-change-more = { $arg0 } more
plot-permission-change-no-effect = Effective permissions are unchanged. Explicit settings override future inheritance.
plot-permission-change-none = None
plot-permission-change-revoked = Revoked: { $arg0 }
plot-permission-change-subject = Target: { $arg0 }
plot-permission-change-title = Confirm permission changes
plot-permission-create-subplot = Create subplot
plot-permission-decoration = Place and edit decorations
plot-permission-default-status = Default ({ $arg0 })
plot-permission-delete = Delete plot
plot-permission-door = Doors and gates
plot-permission-entity-damage = Damage entities
plot-permission-entity-storage = Open entity storage
plot-permission-harvest = Harvest berries, vines and honey
plot-permission-ignite = Ignite fires
plot-permission-item-drop = Drop items
plot-permission-item-pickup = Pick up ground items
plot-permission-manage-area = Edit area
plot-permission-manage-members = Manage members
plot-permission-manage-permissions = Manage permissions
plot-permission-manage-settings = Edit plot settings
plot-permission-management = Plot settings and ownership
plot-permission-modify = Modify blocks and fertilize
plot-permission-operation = Operation
plot-permission-personal = Individual permissions
plot-permission-personal-source = Individual override
plot-permission-place = Place blocks and vehicles
plot-permission-reset = Restore defaults
plot-permission-result = Status
plot-permission-ride = Ride vehicles and animals
plot-permission-select-state = Choose state: { $arg0 }
plot-permission-sign = Edit signs
plot-permission-source-group = Group permissions
plot-permission-source-local = Local override
plot-permission-source-owner = Owner recovery authority
plot-permission-source-parent = Parent plot
plot-permission-source-role = Role default
plot-permission-state-default = Default
plot-permission-switch = Buttons, levers and pressure plates
plot-permission-trade = Trade with villagers
plot-permission-transfer = Transfer ownership
plot-permission-workstation = Use facilities and workstations
plot-pos1 = First point
plot-pos2 = Second point
plot-private = Private project
plot-public = Public project
plot-public-set = Project type: { $arg0 }.
plot-region-adjust = Adjust this rectangle
plot-region-apply = Finish rectangle adjustment
plot-region-cancel = Cancel rectangle adjustment
plot-region-changed = The selection changed. Select the rectangle again.
plot-region-delete = Delete this rectangle
plot-region-delete-confirm = Remove rectangle #{ $arg0 } from the draft? Buildings remain. Boundaries change only after saving the plot; this can be undone.
plot-region-edit-hint = Use //pos1 and //pos2 to adjust this rectangle. Other rectangles stay unchanged. Finish the adjustment, then save the plot.
plot-region-finish-first = Finish or cancel the current rectangle adjustment first.
plot-region-open = Manage rectangles ({ $arg0 })
plot-region-select-hint = Select a rectangle to edit.
plot-renamed = Project renamed.
plot-role-admin = plot admin
plot-role-changed-for-you = Your role on plot { $arg0 } is now { $arg1 }.
plot-role-collaborator = collaborator
plot-role-owner = plot owner
plot-role-parent-owner = parent owner
plot-role-server-staff = server staff
plot-selection-add = Add current box
plot-selection-clear = Reset two points
plot-selection-empty = No points selected.
plot-selection-exit = Exit editor
plot-selection-fit = Restore saved boundaries
plot-selection-incomplete = Select both corners.
plot-selection-mode = Select in the world
plot-selection-no-redo = No selection edit to redo.
plot-selection-no-undo = No earlier selection edit to undo.
plot-selection-open = Edit boundaries
plot-selection-redo = Redo last step
plot-selection-replace = Replace with current box
plot-selection-session-hint = Sneak+F or /plot edit to return. Exits after 20 minutes of inactivity.
plot-selection-size = Selection: { $arg0 } × { $arg1 } × { $arg2 } blocks (X/Y/Z)
plot-selection-stopped = World selection ended. Your selection drafts remain saved per plot.
plot-selection-subtract = Subtract current box
plot-selection-undo = Undo last step
plot-subplot-created = Created subplot { $arg0 } · { $arg1 } block²
plot-subplot-created-for-you = You now own subplot { $arg0 } within { $arg1 }.
plot-subplot-owner-changed = The owner of subplot { $arg0 } is now { $arg1 }.
plot-subplot-parent-transferred = The parent owner supervising your subplot { $arg0 } is now { $arg1 }.
plot-subplot-released = Subplot { $arg0 } was released; its area remains protected by the parent project.
plot-title = Plots
plot-transferred = Transferred plot { $arg0 } to { $arg1 }.
plot-transferred-to-you = { $arg1 } transferred plot { $arg0 } to you.

## World · Vehicles

vehicle-admin-help = /vehicle — model and instance management
    Models: model (open editor) | model help (editing commands)
    Capture: origin | select <uuid>/radius <radius>/group [uuid]/info/clear | capture <id> car/boat/plane
    Instances: spawn <model> [x y z] | reload
vehicle-command-description = Drive and manage display-entity vehicles
vehicle-controls = { $arg0 }: W/S and A/D control; scroll shifts gears or changes throttle; swap-hand changes mode; sneak exits. /vehicle help /vehicle dashboard opens clickable driving controls.
vehicle-dash-assisted = Stability assist
vehicle-dash-automatic = Automatic
vehicle-dash-contact = Ground / water support: { $arg0 }
vehicle-dash-details = Details
vehicle-dash-down = Downshift −
vehicle-dash-drive = Driving
vehicle-dash-energy = Use { $arg0 } U/min · est. endurance { $arg1 } min / range { $arg2 } km
vehicle-dash-engine-start = Start engine
vehicle-dash-engine-stop = Stop engine
vehicle-dash-fuel = Fuel { $arg0 } / { $arg1 } U · { $arg2 }%
vehicle-dash-gear = Gear { $arg0 }
vehicle-dash-gearbox = Gearbox
vehicle-dash-handbrake = Parking / handbrake: { $arg0 }
vehicle-dash-heading = Heading { $arg0 }°
vehicle-dash-health = Condition { $arg0 }% · occupants { $arg1 } / { $arg2 }
vehicle-dash-input = Throttle { $arg0 }% · brake { $arg1 }% · steer { $arg2 }%
vehicle-dash-manual = Manual
vehicle-dash-mode-switch = Switch to { $arg0 }
vehicle-dash-position = Origin { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Recenter panel
vehicle-dash-rpm = RPM { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Speed { $arg0 } km/h · limit { $arg1 }
vehicle-dash-throttle-down = Throttle −5%
vehicle-dash-throttle-up = Throttle +5%
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Unassisted
vehicle-dash-unavailable = Driver controls unavailable; reopen from the driver seat.
vehicle-dash-up = Upshift +
vehicle-dash-vertical = Altitude { $arg0 } m · vertical speed { $arg1 } m/s
vehicle-error = Vehicle operation failed: { $arg0 }
vehicle-group-changed = Group structure changed. Select the group again before importing.
vehicle-group-invalid = Group contains unsupported, managed, missing, or out-of-range entities. Every member must be within 32 blocks in this world.
vehicle-group-limit = Import at most 128 displays and 256 total group members.
vehicle-group-not-found = No display group found. Select a group first, aim at a block/item display, or specify an entity UUID.
vehicle-group-selected = Selected group: { $arg0 ->
        [one] { $arg0 } display entity
       *[other] { $arg0 } display entities
    } · root { $arg1 }
vehicle-help = /vehicle — driving and vehicle operations
    Ride: list | ride [uuid] | passenger [uuid] | leave | inspect [uuid]
    Drive: engine | mode [automatic/manual/assisted] | gear P/R/N/D/up/down/number | throttle up/down
    HUD: hud [on/off] | dashboard [drive/gearbox/details/off]
    Maintain: fuel | repair | access public/private | remove [uuid]
vehicle-hud-boat = { $arg0 } kn  ·  Thrust { $arg1 }%  ·  Heading { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h  ·  Alt { $arg1 } m  ·  VS { $arg2 } m/s  ·  Throttle { $arg3 }%  ·  { $arg4 }
vehicle-hud-status = Fuel { $arg0 }%  ·  Condition { $arg1 }%  ·  { $arg2 }
vehicle-model-add = Add component
vehicle-model-add-here = Add support at my feet
vehicle-model-anchor = Place preview origin here (use your facing direction)
vehicle-model-animation = Animation
vehicle-model-apply = Apply / confirm
vehicle-model-auto = Rebuild collision bounds
vehicle-model-block = Block
vehicle-model-center-here = Set center of mass at my feet
vehicle-model-center-look = Set center of mass on the surface I look at
vehicle-model-check = Check structure and parameters
vehicle-model-checked = Structure and parameters are valid. Saving also checks display data, existing vehicles and world collisions.
vehicle-model-clone = Clone model
vehicle-model-colliders = Collision boxes
vehicle-model-copy = Duplicate and edit (copied seats are passengers)
vehicle-model-corner-cancel = Cancel the pending collision box
vehicle-model-corner-first = Mark first box corner at my feet
vehicle-model-corner-first-look = Mark the first corner on the surface I look at
vehicle-model-corner-hint = First corner marked. Move to the opposite corner (including height), then /vehicle model collider corner2. The box follows the model axes.
vehicle-model-corner-required = Mark the first corner with /vehicle model collider corner1 first.
vehicle-model-corner-second = Use my feet as opposite corner and add box
vehicle-model-corner-second-look = Use the surface I look at as opposite corner
vehicle-model-create = Create { -vehicle-model }
vehicle-model-delete = Delete model
vehicle-model-delete-warning = Permanently delete this model? Models with existing vehicles cannot be deleted.
vehicle-model-deleted = Model deleted
vehicle-model-discard = Discard draft
vehicle-model-discard-warning = Discard all unsaved changes? This cannot be undone.
vehicle-model-discarded = Draft discarded
vehicle-model-draft = Unsaved draft
vehicle-model-driver = Driver seat
vehicle-model-driver-here = Add driver seat at my feet
vehicle-model-edit = Edit / resume draft
vehicle-model-exit = Exit position / fine adjustment
vehicle-model-group = Add Axiom display group
vehicle-model-help = /vehicle model — model editing
    Models: menu | list | info [id] | create <id> car/boat/plane | import <id> car/boat/plane [uuid] | edit <id> | clone <id> <new-id> | delete <id>
    Edit: set <field> <value...> | part/seat/collider/support — press Tab for operations and arguments
    Draft: preview [off] | undo | save | cancel
    In-world: anchor | pick | here; seat position/exit, part position, support add/set and collider position accept here; collider corner1/corner2 creates a box; move <index> <dx dy dz> adjusts position
    More: look uses a hit surface | snap [on/off] | redo | check | apply saves without closing; position shortcuts accept look instead of here; collider corner1/corner2 [look], collider cancel
vehicle-model-here = Use my current feet position
vehicle-model-history-empty = No edit is available in this history direction.
vehicle-model-import = Import Axiom display group
vehicle-model-item = Item
vehicle-model-library = Saved models · drafts stay in memory until saved
vehicle-model-look = Aim placement (menu closes; right-click to confirm)
vehicle-model-look-missing = Look at a world block or a part in your private preview within 32 blocks. No surface was hit.
vehicle-model-manual = Enter local coordinates
vehicle-model-options = Options
vehicle-model-parameters = Physics and engine parameters
vehicle-model-parts = Display parts
vehicle-model-passenger-here = Add passenger seat at my feet
vehicle-model-pick = Edit the preview part you are looking at
vehicle-model-pick-hint = Open a preview and look at a block or item part, or select a component in the editor first.
vehicle-model-place-cancelled = Aim placement cancelled (expired, editing state changed or explicitly cancelled).
vehicle-model-place-hint = Aim placement is armed for 30 seconds. Look at a world block or your preview, then right-click with your main hand to confirm; sneak-right-click cancels.
vehicle-model-position = Position / fine adjustment
vehicle-model-position-hint = Local X/Y/Z · here uses feet, look uses a hit surface · optional grid follows the adjustment step.
vehicle-model-preview = Private preview
vehicle-model-preview-failed = Preview unavailable; your draft is retained: { $arg0 }
vehicle-model-preview-hint = Private preview enabled. Clears after five minutes without editing; /vehicle model preview off to close.
vehicle-model-redo = Redo last undone edit
vehicle-model-remove = Remove component
vehicle-model-remove-warning = Remove this component? Following indices shift; undo is available.
vehicle-model-rotate = Rotate component
vehicle-model-rotate-hint = Relative local rotation: X pitch, Y yaw, Z roll. Position stays unchanged.
vehicle-model-save = Validate and save
vehicle-model-save-continue = Save and continue editing
vehicle-model-saved = Saved
vehicle-model-scale = Scale component
vehicle-model-scale-all = Scale all axes × { $arg0 }
vehicle-model-scale-hint = Multiply or divide local axis lengths. Values are not world bounding dimensions.
vehicle-model-seats = Seats and exits
vehicle-model-selected = Import selected displays
vehicle-model-size = Resize collision box
vehicle-model-size-hint = Full width, height and length; resize around the unchanged center.
vehicle-model-snap-off = Grid snap: off
vehicle-model-snap-on = Grid snap: on
vehicle-model-step = Cycle adjustment step
vehicle-model-supports = Ground support points
vehicle-model-text = Text
vehicle-model-title = Vehicle model manager
vehicle-model-tools = In-world editing tools
vehicle-model-transform-step = Change adjustment step
vehicle-model-undo = Undo last edit
vehicle-result = Vehicle: { $arg0 }
vehicle-selection-info = Selection: { $arg0 ->
        [one] { $arg0 } display entity
       *[other] { $arg0 } display entities
    } · group { $arg1 } · origin { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Collision / obstruction
vehicle-status-damage = Critical vehicle damage
vehicle-status-disabled = Vehicle disabled
vehicle-status-engine-off = Engine off
vehicle-status-engine-on = Engine running
vehicle-status-gear-limit = Control limit reached
vehicle-status-grounded = Boat grounded
vehicle-status-low-fuel = Low fuel
vehicle-status-manual-only = Switch to manual mode first
vehicle-status-no-fuel = No fuel
vehicle-status-overrev = Downshift would over-rev engine
vehicle-status-shifting = Shift in progress
vehicle-status-stall = Stall
vehicle-status-stop-first = Stop before shifting direction / parking
vehicle-value-off = Off
vehicle-value-on = On

## World · Elevators, regeneration and spawn

async-regen-already-running = You already have a regeneration task running; use /regen status or /regen cancel
async-regen-apply-expired = There is no valid plan to apply; run /regen preview first
async-regen-apply-plan = Apply stored plan
async-regen-apply-started = Applying { $arg0 } to the stored selection: { $arg1 }
async-regen-awaiting-apply = Plan awaiting application: { $arg0 } · { $arg1 } · expires in { $arg2 ->
        [one] { $arg2 } second
       *[other] { $arg2 } seconds
    }
async-regen-block-preview-progress = block preview · scanned { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } · { $arg2 ->
        [one] { $arg2 } block change
       *[other] { $arg2 } block changes
    } · { $arg3 ->
        [one] { $arg3 } block entity
       *[other] { $arg3 } block entities
    } at risk
async-regen-block-preview-summary = Preview: { $arg0 ->
        [one] { $arg0 } block change
       *[other] { $arg0 } block changes
    } matching { $arg2 }; overwrite risk: { $arg1 ->
        [one] { $arg1 } existing block entity
       *[other] { $arg1 } existing block entities
    }
async-regen-cancelled = Regeneration cancelled. The { $arg0 ->
        [one] { $arg0 } change
       *[other] { $arg0 } changes
    } already applied remain in the world
async-regen-command-description = Regenerate a FastAsyncWorldEdit selection asynchronously
async-regen-completed = Regeneration completed: { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    } changed in { $arg1 }
async-regen-failed = Regeneration stopped because of an internal error. The { $arg0 ->
        [one] { $arg0 } change
       *[other] { $arg0 } changes
    } already applied remain in the world
async-regen-filter-all = all block types
async-regen-invalid-blocks = Unknown or non-block types: { $arg0 }
async-regen-not-running = You do not have a regeneration task running
async-regen-nothing-to-apply = The selection already matches this plan; nothing needs to be applied
async-regen-plan-cancel = Cancel
async-regen-plan-dialog-title = Apply regeneration plan
async-regen-preview-cancelled = Preview cancelled; no world data was changed
async-regen-preview-started = Preview started: selection { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    } · area { $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } · mode { $arg2 }
async-regen-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } planned · { $arg3 ->
        [one] { $arg3 } change
       *[other] { $arg3 } changes
    } applied
async-regen-safety-note = Nothing outside the stored selection will change. Buildings inside it may be overwritten, and vanilla does not identify player-placed blocks. Inspect the visualization and make a backup before applying.
async-regen-scope-blocks = regenerate blocks matching { $arg0 }
async-regen-selection-required = Make a complete FastAsyncWorldEdit selection first
async-regen-skipped-ungenerated = Skipped ungenerated target chunks: { $arg0 ->
        [one] { $arg0 } chunk
       *[other] { $arg0 } chunks
    }. No target chunks were created or modified.
async-regen-start-failed = Regeneration could not be started; check the server log for details
async-regen-state-applying = applying changes
async-regen-state-cancelled = cancelled
async-regen-state-completed = completed
async-regen-state-failed = failed
async-regen-state-generating = generating and comparing chunks
async-regen-state-preparing = preparing source world
async-regen-status = { $arg0 } · planned { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } · applied { $arg3 } of { $arg4 ->
        [one] { $arg4 } discovered change
       *[other] { $arg4 } discovered changes
    }
async-regen-structure-metadata-applied = Migrated { $arg0 ->
        [one] { $arg0 } structure start
       *[other] { $arg0 } structure starts
    } and { $arg1 ->
        [one] { $arg1 } chunk reference
       *[other] { $arg1 } chunk references
    }; /locate and structure-dependent spawning can recognize them
async-regen-upgrade-apply-progress = { $arg0 } · planned { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } · applied { $arg3 ->
        [one] { $arg3 } block
       *[other] { $arg3 } blocks
    } and { $arg4 ->
        [one] { $arg4 } biome cell
       *[other] { $arg4 } biome cells
    }
async-regen-upgrade-boundary-note = { $arg0 ->
        [one] { $arg0 } biome cell
       *[other] { $arg0 } biome cells
    } touching the selection boundary will be skipped to prevent changes outside the selection
async-regen-upgrade-completed = World upgrade completed: { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    } and { $arg1 ->
        [one] { $arg1 } biome cell
       *[other] { $arg1 } biome cells
    } changed in { $arg2 }
async-regen-upgrade-preview-progress = upgrade preview · scanned { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } · { $arg2 ->
        [one] { $arg2 } block change
       *[other] { $arg2 } block changes
    } · { $arg3 ->
        [one] { $arg3 } biome cell
       *[other] { $arg3 } biome cells
    } · { $arg4 ->
        [one] { $arg4 } structure
       *[other] { $arg4 } structures
    }
async-regen-upgrade-preview-summary = Preview: { $arg0 ->
        [one] { $arg0 } block change
       *[other] { $arg0 } block changes
    }, { $arg1 ->
        [one] { $arg1 } biome cell
       *[other] { $arg1 } biome cells
    }, { $arg2 ->
        [one] { $arg2 } structure
       *[other] { $arg2 } structures
    } (eligible for complete metadata migration: { $arg3 }); overwrite risk: { $arg4 ->
        [one] { $arg4 } existing block entity
       *[other] { $arg4 } existing block entities
    }
async-regen-upgrade-scope-biomes = biomes only
async-regen-upgrade-scope-terrain = terrain, structures, and biomes
async-regen-usage = Usage: /regen preview blocks [types...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Visualization in { $arg0 }: cyan = selection boundary; orange = changed-block sample; red = block entity at risk. Only you can see these particles.
async-regen-visualization-selection = Selection in { $arg0 }: cyan particles mark its boundary. Only you can see them.
async-regen-fawe-required = FastAsyncWorldEdit is required to read the selection
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ Sneak
iron-elevator-tip-mm = Align { -iron-elevator-block } vertically to create an elevator. Stand on { -iron-elevator-block }: <green>jump to go up</green>, <aqua>sneak to go down</aqua>. Elevators with more than two floors show a <gold>floor panel</gold>.
iron-elevator-up = ↑ Jump
spawn-beacon-color-command-description = Toggle spawn beacon color cycling
spawn-beacon-color-disabled = Spawn beacon color cycling disabled
spawn-beacon-color-enabled = Spawn beacon color cycling enabled
spawn-command-description = Return to spawn
spawn-tip-return-mm = Use <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> to return to the <yellow>server spawn</yellow>.
spawn-world-missing = Spawn world is unavailable

## Entertainment · Music

music-action-add-queue = Add to the queue
music-action-play-nearby = Play on the nearest jukebox
music-action-play-now = Play now on this jukebox
music-action-take-disc = Take the disc
music-added-to-queue-rich = Added to queue: { $music }
music-album = Album: { $arg0 }
music-all-in-queue = Every song is already in the queue
music-artist = Artist: { $arg0 }
music-back = Back
music-browser-left-click = Left click
music-browser-right-click = Right click
music-cache-clear-done = Online music cache cleared
music-cache-clear-started = Clearing the online music cache...
music-cache-operation-failed = Music cache operation failed: { $arg0 }
music-cache-status = Online music cache: { $arg0 ->
        [one] { $arg0 } file
       *[other] { $arg0 } files
    }, { $arg1 } / { $arg2 }
music-cached = Cached - ready to play
music-clear-queue = Clear Queue
music-clear-queue-done = Cleared the queue ({ $arg0 ->
        [one] { $arg0 } song
       *[other] { $arg0 } songs
    })
music-command-description = Local, online, and NBS jukebox music
music-disc-left = Left click: take the disc
music-disc-right = Right click: play on the nearest jukebox
music-duplicate-in-queue = This song is already in the queue
music-duration = Duration: { $arg0 }
music-empty-library = Music library is empty
music-empty-library-description = No local audio or NBS tracks are available
music-empty-nbs = No NBS tracks found
music-empty-nbs-description = Switch to all music or add an NBS file
music-empty-playlist = This playlist has no available tracks
music-empty-playlist-description = Check that the playlist is public and that the selected platform is correct
music-empty-search = No matching songs
music-empty-search-description = Try another keyword or check custom-source status
music-finished = Finished
music-format = Format: { $arg0 }
music-inventory-full = Inventory is full. Could not give the music disc
music-jukebox-audible-field-conflict = Another jukebox is playing a different song in an overlapping clearly audible area
music-jukebox-music-mode = Music mode
music-jukebox-play-order = Playback order
music-jukebox-range = Playback Range
music-jukebox-range-value = Current: { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    }
music-jukebox-rhythm-mode = Rhythm mode
music-jukebox-too-far = You are too far from the jukebox (maximum { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    })
music-jukebox-tp-hover = Click to teleport to the jukebox
music-jukebox-unavailable = The jukebox was removed or its chunk is unavailable
music-jukebox-usage-mode = Jukebox use
music-jukebox-volume = Jukebox Volume
music-jukebox-volume-value = Current: { $arg0 }%
music-library-button = Music Library
music-library-random-mode = Shuffle Library
music-library-random-mode-desc = Play randomly from the entire music library without immediately repeating a track
music-loading-title = Loading
music-menu-nbs-title = NBS Music - Page { $arg0 }/{ $arg1 }
music-menu-playlist-title = Playlist: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-nbs-title = NBS Search: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-search-title = Search: { $arg0 } - { $arg1 }/{ $arg2 }
music-menu-title = Music Discs - Page { $arg0 }/{ $arg1 }
music-mode-switched = Switched to { $arg0 }
music-nearest-jukebox-missing = No jukebox found within { $arg0 ->
        [one] { $arg0 } block
       *[other] { $arg0 } blocks
    }
music-next-page = Next Page
music-no-disc = No disc
music-no-files = No music files are available
music-no-search-results-rich = No songs found containing "{ $keyword }"
music-now-playing-rich = Now playing: { $music } at jukebox { $location }
music-online-partial-failure = Failed source requests: { $arg0 ->
        [one] { $arg0 } request
       *[other] { $arg0 } requests
    }; available results are shown
music-online-rate-limited = Too many music requests. Try again in { $arg0 ->
        [one] { $arg0 } second
       *[other] { $arg0 } seconds
    }.
music-online-searching = Searching local and online sources for "{ $arg0 }"...
music-original-author = Original author: { $arg0 }
music-page-out-of-range = Page out of range. Max page is { $arg0 }
music-page-total = { $arg0 ->
        [one] { $arg0 } song
       *[other] { $arg0 } songs
    } total
music-page-usage-desc = Jump to a page
music-partial-results = Failed source requests: { $arg0 ->
        [one] { $arg0 } request
       *[other] { $arg0 } requests
    }; available results are shown
music-paused = Paused
music-plasmo-voice-unavailable = The song has started, but you cannot hear it because Plasmo Voice is not installed, connected, or enabled
music-play-next = Play Next
music-play-random = Play Random
music-playback-failed = Failed to play "{ $arg0 }". Check the source or music file and try again.
music-playback-status = Playback status: { $arg0 }
music-playing = Playing
music-playing-now-rich = Now playing: { $music }
music-playlist-import-button = Import playlist
music-playlist-import-done = Imported “{ $arg0 }”: { $arg1 } of { $arg2 ->
        [one] { $arg2 } song
       *[other] { $arg2 } songs
    }.
music-playlist-import-failed = Playlist import failed: { $arg0 }
music-playlist-import-prompt = Choose a platform, then paste a public playlist ID or link:
music-playlist-import-usage-desc = Import a public playlist from another music platform
music-playlist-importing = Importing playlist...
music-playlist-reference-prompt = Paste a public playlist ID or link
music-prev-page = Previous Page
music-queue-empty = Queue is empty
music-queue-move-down = Move down
music-queue-move-up = Move up
music-queue-remove = Remove song
music-queue-summary = Queue: { $arg0 ->
        [one] { $arg0 } song
       *[other] { $arg0 } songs
    }
music-queue-summary-description = Page { $arg0 }/{ $arg1 }
music-random-add = Add Random Song
music-random-added-to-queue-rich = Randomly added to queue: { $music }
music-random-disc = Random Disc
music-random-disc-got-rich = Got random music disc: { $music }
music-random-mode = Shuffle List
music-random-mode-desc = Play the playlist in random order without immediately repeating a track
music-rank-down = Moved down
music-rank-up = Moved up
music-reload-done = Reload complete: { $arg0 ->
        [one] { $arg0 } local track
       *[other] { $arg0 } local tracks
    }; LX sources available { $arg1 }/{ $arg2 }; subscriptions checked { $arg3 }, updated { $arg4 }
music-reload-failed = Music reload failed: { $arg0 }
music-reload-issues = Reload issues: { $arg0 }
music-reload-partial = Reload complete with retained state: { $arg0 ->
        [one] { $arg0 } local track
       *[other] { $arg0 } local tracks
    }; LX sources available { $arg1 }/{ $arg2 }; subscriptions checked { $arg3 }, updated { $arg4 }
music-reload-started = Reloading music files and custom sources...
music-remote-source-error = update error: { $arg0 }
music-remote-source-import-done = Imported LX source subscription: { $arg0 }
music-remote-source-import-failed = LX source import failed: { $arg0 }
music-remote-source-import-started = Importing LX source subscription...
music-remote-source-remove-done = Removed LX source subscription: { $arg0 }
music-remote-source-remove-failed = Could not remove LX source subscription: { $arg0 }
music-remote-source-remove-not-found = LX source subscription was not found: { $arg0 }
music-remote-source-update-done = Checked: { $arg0 ->
        [one] { $arg0 } subscription
       *[other] { $arg0 } subscriptions
    }; updated: { $arg1 }; failed: { $arg2 }.
music-remote-source-update-failed = LX source update failed: { $arg0 }
music-remote-source-update-started = Checking imported LX source subscriptions...
music-remote-source-updated-at = updated: { $arg0 }
music-remote-sources-title = Imported LX source subscriptions: { $arg0 }
music-removed-from-queue-rich = Removed from queue: { $music }
music-repeat-one-mode = Repeat One
music-repeat-one-mode-desc = Repeat the current track until you manually skip it
music-request-error = Request failed: { $arg0 }
music-rhythm-animation-offset = Animation { $arg0 } ms
music-rhythm-calibration = Latency Calibration
music-rhythm-calibration-accumulating = Good, keep the same rhythm to keep collecting samples
music-rhythm-calibration-adapting = Keep following the beat; confidence is still building
music-rhythm-calibration-complete = Delay test complete
music-rhythm-calibration-done = Delay test complete
music-rhythm-calibration-failed = The delay-test audio stopped unexpectedly. The test was safely closed; please start it again.
music-rhythm-calibration-intro = Stage order: Minecraft Note Block → Plasmo Voice → Keyboard visual → Mouse visual
music-rhythm-calibration-listen = Listen once to the pattern, then tap each cue consistently.
music-rhythm-calibration-minecraft-instruction = Repeat the rhythm and press 1–4
music-rhythm-calibration-no-input = No valid input detected. Use the control shown for this stage on every cue.
music-rhythm-calibration-out-of-range = The measured delay is outside the supported range. Check your audio setup or exit and retry.
music-rhythm-calibration-plasmo-instruction = Continue the same rhythm on Plasmo Voice and keep your keying pattern stable; this stage filters unstable samples.
music-rhythm-calibration-plasmo-ready = Minecraft Note Block stage done. Keep the same rhythm and continue on Plasmo Voice.
music-rhythm-calibration-pointer-instruction = Left- or right-click when blocks cross the hit line.
music-rhythm-calibration-prompt-confirm = Start delay test
music-rhythm-calibration-prompt-confirm-hint = Automatically returns to rhythm mode after enough stable samples
music-rhythm-calibration-prompt-later = Later
music-rhythm-calibration-prompt-reason = Run delay test before rhythm mode. It calibrates hit timing and animation for Minecraft Note Block + Plasmo Voice so judgments are consistent across both.
music-rhythm-calibration-prompt-title = Delay Test
music-rhythm-calibration-realigning = Voice stream dropped; replaying
music-rhythm-calibration-required = Delay test required before starting
music-rhythm-calibration-reset-done = Delay calibration reset
music-rhythm-calibration-result-hold = Test complete, hold the line until results apply
music-rhythm-calibration-stage-minecraft = Minecraft Note Block
music-rhythm-calibration-stage-plasmo = Plasmo Voice
music-rhythm-calibration-stage-pointer = Mouse
music-rhythm-calibration-stage-visual = Visual
music-rhythm-calibration-visual-instruction = Press 1–4 when blocks cross the hit line.
music-rhythm-calibration-visual-preview = Watch one full cue pattern without input. Start only when the instruction changes.
music-rhythm-calibration-voice-required = The complete delay test requires Plasmo Voice to be connected and enabled.
music-rhythm-chart-preparing = Preparing complete chart…
music-rhythm-combo = Combo ×{ $arg0 }
music-rhythm-difficulty-easy = Easy
music-rhythm-difficulty-expert = Expert
music-rhythm-difficulty-hard = Hard
music-rhythm-difficulty-normal = Normal
music-rhythm-exit = Exit rhythm game
music-rhythm-game-description = 1 · 2 · 3 · 4  /  360°
music-rhythm-get-ready = Get ready
music-rhythm-go = GO!
music-rhythm-good = Good
music-rhythm-great = Great
music-rhythm-latency = Latency { $arg0 } ms
music-rhythm-latency-test = Delay Test
music-rhythm-miss = Miss
music-rhythm-mode-falling = Falling Notes
music-rhythm-mode-radial = Radial Rush
music-rhythm-mode-spatial-aim = Spatial Aim
music-rhythm-network-too-high = Network delay is too high for fair timing ({ $arg0 } ms; limit { $arg1 } ms)
music-rhythm-network-warning = Network delay is unstable ({ $arg0 } ms). This session will stop if it stays above { $arg1 } ms
music-rhythm-no-beats = No playable beats were found in this track
music-rhythm-perfect = Perfect
music-rhythm-preparing = Wait for the complete song to be cached and rhythm-analyzed
music-rhythm-ready = Complete chart ready — start on the first beat
music-rhythm-requires-playback = Start a song on this jukebox first
music-rhythm-reset-calibration = Reset latency calibration
music-rhythm-results = Results
music-rhythm-score = Score: { $arg0 }
music-rhythm-select-track = Select Rhythm Track
music-rhythm-spatial-no-room = Not enough open space for Spatial Aim. Move to a clearer area.
music-rhythm-spatial-slider-hint = Trace the moving star and click each checkpoint · no holding
music-rhythm-start-game = Start Game
music-rhythm-waiting-for-player = Waiting for a player to start the rhythm game
music-sample-rate = Sample rate: { $arg0 }
music-search-button = Search Songs
music-search-keyword-required = Search keyword must not be empty
music-search-keyword-too-long = Search query too long. Limit: { $arg0 ->
        [one] { $arg0 } character
       *[other] { $arg0 } characters
    }.
music-search-usage-desc = Search songs
music-section-all = All Music
music-section-nbs = NBS Music
music-select-music = Select Music
music-sequential-mode = Repeat List
music-sequential-mode-desc = Play the playlist in order and restart at the beginning
music-size = Size: { $arg0 }
music-sort-button = Sort
music-sort-current = Current: { $arg0 }
music-sort-default = Default order
music-sort-most-played = Most played
music-sort-recently-played = Recently played
music-sound-settings = Sound settings
music-source-actions = actions: { $arg0 }
music-source-available = available
music-source-capabilities = capabilities: { $arg0 }
music-source-error = error: { $arg0 }
music-source-failures = failures: { $arg0 }
music-source-retry = retry in { $arg0 ->
        [one] { $arg0 } second
       *[other] { $arg0 } seconds
    }
music-source-unavailable = unavailable
music-sources-empty = No LX custom music source scripts were found
music-sources-title = LX custom music sources: { $arg0 }
music-stop-eject = Stop and Eject
music-stop-eject-done = Stopped playback and ejected the disc
music-stopped = Stopped
music-track-details = Track details
music-unavailable-disc = Unavailable Disc
music-unavailable-disc-description = This MIK disc is not in the current catalog; it can still be stopped and ejected

## Entertainment · Anniversary

anniversary-accepted = You joined the fifth-anniversary drawing. The first draw unlocks after 4 non-AFK online hours.
anniversary-admin-audit-action-bonus = Granted a building-submission bonus opportunity
anniversary-admin-audit-action-gift = Gifted event prize: { $arg0 }
anniversary-admin-audit-action-removal = Removed historical administrator participation and restored { $arg0 ->
        [one] { $arg0 } prize
       *[other] { $arg0 } prizes
    }.
anniversary-admin-audit-action-unknown = Unknown action: { $arg0 }
anniversary-admin-audit-empty = No management audit entries yet.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Actor: { $arg0 } ({ $arg1 }); target: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Anniversary management audit (showing { $arg0 }/{ $arg1 })
anniversary-admin-bonus-cancelled = Ended by gift pack
anniversary-admin-bonus-pending = Granted, not drawn yet
anniversary-admin-bonus-used = Drawn
anniversary-admin-boss = Anniversary management · Enter { $arg0 } to view progress
anniversary-admin-commands = Commands: /anniversary winners [page] | /anniversary audit [player] | /anniversary bonus <player>
anniversary-admin-control = Dynamic control: released { $arg0 }; available now { $arg1 }; base probability { $arg2 }; recent rate { $arg3 }
anniversary-admin-draw-bonus = Building-submission bonus
anniversary-admin-draw-detail = { $arg0 }: drawn at { $arg1 }; result { $arg2 }
anniversary-admin-draw-metrics = Probability { $arg0 }; roll { $arg1 }; algorithm version { $arg2 }
anniversary-admin-draw-replaced = Replaced by gift pack; stock restored
anniversary-admin-draw-round = Regular round { $arg0 }
anniversary-admin-event = Event status: { $arg0 }; period: { $arg1 }
anniversary-admin-excluded = Anniversary administrators cannot enter the drawing. The management overview has been opened instead.
anniversary-admin-none = None
anniversary-admin-opportunities = Draws performed: { $arg0 }; ready now: { $arg1 }; opportunities earned: { $arg2 }; wins/losses: { $arg3 }
anniversary-admin-participants = Participants: { $arg0 }
anniversary-admin-player-bag = Final event bag: { $arg0 }
anniversary-admin-player-bonus = Building bonus: { $arg0 }; granted: { $arg1 }; grantor: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Drawing opportunity details
anniversary-admin-player-not-found = Could not find event participant: { $arg0 }
anniversary-admin-player-outcome = Wins: { $arg0 }; losses: { $arg1 }; draws performed: { $arg2 }; building bonus: { $arg3 }
anniversary-admin-player-progress = Joined: { $arg0 }; eligible online time: { $arg1 }; regular draws performed: { $arg2 }; regular draws ready now: { $arg3 }
anniversary-admin-player-title = Participant audit: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Active
anniversary-admin-state-ended = Ended
anniversary-admin-state-scheduled = Scheduled
anniversary-admin-stock = Remaining stock: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | total { $arg4 }
anniversary-admin-target-excluded = { $arg0 } is an anniversary administrator and cannot receive a drawing opportunity.
anniversary-admin-title = Fifth-Anniversary Management Overview
anniversary-admin-winners-empty = No winning result has been drawn yet.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · drawn at { $arg3 }
anniversary-admin-winners-title = Prize fulfillment list (page { $arg0 }/{ $arg1 }, { $arg2 ->
        [one] { $arg2 } prize
       *[other] { $arg2 } prizes
    })
anniversary-bag-empty = No winning prizes yet
anniversary-bag-item-locked = The event bag is read-only
anniversary-bag-item-name = Fifth-anniversary prize · { $arg0 }
anniversary-bag-not-participating = You have not joined the event. Enter { $arg0 } first.
anniversary-bag-title = Fifth-Anniversary Event Bag
anniversary-bag-updated = Your prize is in the event bag. Enter { $arg0 } to view it.
anniversary-bonus-already-granted = { $arg0 } has already received the building-submission bonus and cannot receive it again.
anniversary-bonus-boss-ready = Building-submission bonus draw is ready · Enter { $arg0 } to draw
anniversary-bonus-broadcast-won = [Anniversary Drawing] { $arg0 } won { $arg1 } through the building-submission bonus!
anniversary-bonus-draw-not-won = Building-submission bonus draw: no prize this time.
anniversary-bonus-draw-sold-out = Building-submission bonus draw: no prize was awarded because the prize pool is empty.
anniversary-bonus-draw-won = Building-submission bonus draw: congratulations, you won { $arg0 }!
anniversary-bonus-gift-pack-final = { $arg0 } already won the gift pack and cannot receive a building bonus draw.
anniversary-bonus-grant-not-active = The building-submission bonus cannot be granted outside the event period.
anniversary-bonus-grant-success = Granted the building-submission bonus to { $arg0 }.
anniversary-bonus-granted = You received one extra draw for the building-submission bonus. Enter /anniversary to draw.
anniversary-bonus-participant-not-found = Could not find an event participant named { $arg0 }
anniversary-bonus-status-ready = Building-submission bonus: one extra draw is ready.
anniversary-bonus-status-used = Building-submission bonus: the extra draw has been used.
anniversary-boss-completed = Fifth-anniversary drawing · Both rounds completed
anniversary-boss-gift-pack-final = Fifth-anniversary gift pack won · Enter { $arg0 } to view your event bag
anniversary-boss-invite = Fifth-anniversary merchandise drawing · Enter { $arg0 } to join
anniversary-boss-progress = Anniversary drawing { $arg0 }/{ $arg1 } · Round { $arg2 } in { $arg3 }
anniversary-boss-ready = Anniversary round { $arg0 } is ready · Enter { $arg1 } to draw
anniversary-broadcast-won = [Anniversary Drawing] { $arg0 } won { $arg2 } in round { $arg1 }!
anniversary-cancel = Not now
anniversary-command-description = Join the server fifth-anniversary merchandise drawing
anniversary-confirm = Agree and join
anniversary-confirm-hint = Starts counting non-AFK online time after confirmation
anniversary-delivery-note = For a destination in mainland China, provide a valid SF ID that supports privacy-protected shipping. For a destination outside mainland China, provide the recipient's real name, complete address, and valid phone number. Pay the required shipping share, or the prize will be forfeited.
anniversary-dialog-prizes = Prizes: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 }, and 5 { $arg3 }
anniversary-dialog-time = Event period: { $arg0 }
anniversary-dialog-title = Fifth-Anniversary Merchandise Drawing Rules
anniversary-draw-not-won = Round { $arg0 }: no prize this time.
anniversary-draw-sold-out = Round { $arg0 }: no prize was awarded because the prize pool is empty.
anniversary-draw-won = Round { $arg0 }: congratulations, you won { $arg1 }!
anniversary-ended = The fifth-anniversary drawing has ended
anniversary-ended-results = Your drawing results: { $arg0 }
anniversary-gift-pack-final = The gift pack is your final prize. It replaced and returned { $arg0 ->
        [one] { $arg0 } other prize
       *[other] { $arg0 } other prizes
    } to stock; all remaining draws have ended.
anniversary-gift-prize-not-owned = You do not own this event prize: { $arg0 }
anniversary-gift-received = { $arg0 } gifted you { $arg1 }. It is now in your event bag.
anniversary-gift-self = You cannot gift a prize to yourself.
anniversary-gift-success = Gifted { $arg0 } to { $arg1 }.
anniversary-gift-usage = Usage: { $arg0 }
anniversary-not-active = The fifth-anniversary event is not active now
anniversary-not-started = The fifth-anniversary event has not started. Event period: { $arg0 }
anniversary-prize-badge = fifth-anniversary group-photo badge
anniversary-prize-gift-pack = fifth-anniversary gift pack
anniversary-prize-keychain = Logo keychain
anniversary-prize-mug = Logo mug
anniversary-prize-not-won = no prize
anniversary-prize-sold-out = sold-out prize pool
anniversary-rule-1 = 1. After joining, you may draw every 4 hours for two rounds. Won prizes go into the event-only virtual bag, with at most one of each type. Winning the gift pack leaves only that pack and ends all remaining draws.
anniversary-rule-2 = 2. Time spent AFK does not count toward drawing eligibility. Circumventing AFK detection by any means is prohibited.
anniversary-rule-3 = 3. The server covers 80% of the total shipping cost for physical merchandise, capped at CNY 50; the winner pays the entire balance after this contribution is deducted.
anniversary-rule-4 = 4. For destinations in mainland China, winners must provide a valid SF ID that supports privacy-protected shipping. For destinations outside mainland China, winners must provide the recipient's real name, complete address, and valid phone number. Failure to provide the required information or pay the required shipping share forfeits the prize.
anniversary-rule-5 = 5. Repeated participation through alternate accounts is prohibited and will result in disqualification.
anniversary-rule-6 = 6. Participating means that you agree to all rules above.
anniversary-rule-7 = 7. Website-accepted builds score 0.2 for reposts, 0.5 for adaptations, and 1 for originals; builds accepted before the event are multiplied by 0.8, and shared builds by 1.5 times your contribution share. A total of at least 2 grants one extra draw, issued manually by an administrator.
anniversary-second-round-hint = The second draw unlocks after 8 total non-AFK online hours.
anniversary-status-completed = You have completed both drawing rounds.
anniversary-status-gift-pack-final = You won the gift pack. Your final prize is fixed and all remaining draws have ended.
anniversary-status-progress = Completed: { $arg0 }/{ $arg1 } rounds; eligible online time: { $arg2 }
anniversary-status-remaining = Round { $arg0 } needs another { $arg1 } of non-AFK online time.
anniversary-status-results = Drawing results: { $arg0 }
anniversary-status-title = Fifth-Anniversary Drawing Progress

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } is already banned
ban-broadcast-operator = Moderator
ban-broadcast-prefix = [Ban bulletin]
ban-command-description = Ban a player using a fixed severity level
ban-database-error = The ban database operation failed. Check the server log
ban-dialog-back = Back
ban-dialog-cancel = Cancel
ban-dialog-confirm-action = Ban Player
ban-dialog-confirm-severity = Severity
ban-dialog-confirm-title = Confirm Ban
ban-dialog-never-joined-confirm-action = Ban Anyway
ban-dialog-never-joined-title = Player Has Never Joined
ban-dialog-never-joined-warning = { $arg0 } has never joined this server. Verify the player name to avoid banning the wrong player.
ban-dialog-next = Next
ban-dialog-player-input = Enter player name manually
ban-dialog-player-manual = Manual entry
ban-dialog-player-online = Players currently online
ban-dialog-select-title = Select Player
ban-dialog-severity-title = Select Ban Severity
ban-history-empty = No ban history exists for { $arg0 }
ban-history-title = Ban History: { $arg0 }
ban-invalid-player = Invalid player name
ban-invalid-severity = Invalid ban severity
ban-kick-appeal = Appeal in QQ group 1138627074 if you believe this is a mistake
ban-kick-server = MIK
ban-kick-title = You are banned from this server
ban-not-banned = { $arg0 } is not currently banned
ban-pardon-reason-required = A pardon reason is required
ban-permanent = Permanent
ban-reason-required = A ban reason is required
ban-self = You cannot ban yourself
ban-severity-excusable = Excusable (30 days)
ban-severity-extreme = Extreme violation (1500 days)
ban-severity-minor = Minor violation (150 days)
ban-severity-severe = Severe violation (360 days)
ban-severity-warning = Warning (3 days)
ban-status-active = Active
ban-status-expired = Expired
ban-status-revoked = Pardoned
ban-success = Banned { $arg0 } until { $arg1 }
ban-sync-success = { $arg1 ->
        [one] Ban synchronization complete: { $arg0 } active; { $arg1 } historical record.
       *[other] Ban synchronization complete: { $arg0 } active; { $arg1 } historical records.
    }
ban-system-source = System
ban-unban-success = Pardoned { $arg0 }
ban-unknown-reason = No reason provided
ban-update-success = Updated { $arg0 } ban field: { $arg1 }
banlist-command-description = View profile bans
banlist-copy = Click to copy player name
banlist-empty = There are no banned players
banlist-expires = Expires
banlist-invalid-page = Invalid page. Total: { $arg0 ->
        [one] { $arg0 } page
       *[other] { $arg0 } pages
    }.
banlist-next = Next
banlist-page = Page { $arg0 }/{ $arg1 } · { $arg2 ->
        [one] { $arg2 } record
       *[other] { $arg2 } records
    }
banlist-player = Player
banlist-previous = Previous
banlist-reason = Reason
banlist-source = Moderator
banlist-title = Ban List
maintenance-command-description = Toggle or check maintenance mode
maintenance-set = Maintenance mode { $arg0 }
maintenance-state-disabled = disabled
maintenance-state-enabled = enabled
maintenance-status = Maintenance mode: { $arg0 }
maintenance-unchanged = Maintenance mode is already { $arg0 }
restriction-entity-too-large-mm = <red>Entity size exceeds the server safety limit</red>
restriction-foreign-uuid-mm = <red>You can only use UUIDs for mobs you have tamed</red>
restriction-no-kill-e-mm = <red>The <white>/kill @e</white> command is not allowed</red>
restriction-no-newline-mm = <red>Messages cannot contain line breaks</red>
restriction-no-run-command-mm = <red>Messages cannot trick players into running commands</red>
restriction-no-selector-mm = <red>You do not have permission to use target selectors <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>You do not have permission to mention other player names in commands</red>
tpsbar-command-description = Show or hide the TPS bar
tpsbar-hidden = TPS bar hidden
tpsbar-shown = TPS bar shown
tpsbar-tip-status-mm = Use <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> to toggle the <yellow>TPS, MSPT and ping</yellow> bar.
unban-command-description = Pardon a player and keep the ban history
whitelist-already-whitelisted = { $arg0 } is already whitelisted
whitelist-invalid-name = Invalid player name format
whitelist-kick-apply-rich = Apply at { $site } for whitelist
whitelist-kick-title = You are not whitelisted
whitelist-kick-website-rich = Website: { $site }
whitelist-temp-added-plain = Added temporary whitelist for { $arg0 } for { $arg1 }
whitelist-temp-added-rich = Added temporary whitelist for { $player } for { $duration }
whitelist-temp-command-description = Add a 1-hour temporary whitelist entry
whitelist-temp-duration = 1 hour
whitelist-temp-usage-desc = Add a 1-hour temporary whitelist entry

## Shell · Menus, server information and scripting

main-afk-current-afk = Currently AFK
main-afk-current-online = Currently online
main-afk-status = AFK Status
main-announcements = Announcements
main-home = Homes
main-language = Language
main-language-auto = Automatic
main-links = Links
main-map = Web Map
main-menu-title = Main Menu
main-music = Music
main-name-tag = Name Tags
main-profile = Profile
main-pvp = PVP
main-settings = Settings
main-website = Website
main-wiki = Wiki
skript-command-description = View server scripting support
skript-info-ai-option = Don't want to write it yourself? Give this prompt to an AI:
skript-info-copy = [Copy AI prompt]
skript-info-copy-hover = Copy a prompt containing versions, documentation links, and safety rules
skript-info-documentation = Syntax and API documentation
skript-info-review = Scripts are loaded only after administrator review and testing
skript-info-supported = Available: { $arg0 }
skript-info-title = Server Scripts
skript-info-workflow = Write and check a complete .sk file using the documentation, then send it to an administrator
stat-client-version = Client
stat-first-joined = First Joined
stat-location = Location
stat-ping = Ping
stat-play-time = Play Time
stat-role = Role
stat-world = World
weblogin-command-description = Confirm a Mik Web login code
weblogin-confirmed = Mik Web login confirmed. Return to your browser to continue.
weblogin-invalid-code = Invalid web login code
