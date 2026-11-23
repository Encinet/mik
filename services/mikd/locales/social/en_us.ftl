chat-content-audio = [Audio]
chat-content-card = [Card]
chat-content-contact = [Contact]
chat-content-emote = [Action]
chat-content-face = [Emoji]
chat-content-file = [File]
chat-content-history = [Chat history]
chat-content-image = [Image]
chat-content-location = [Location]
chat-content-message = [Message]
chat-content-multiple = [Multiple messages]
chat-content-music = [Music]
chat-content-notice = [Notice]
chat-content-reply = [Quoted message]
chat-content-sticker = [Sticker]
chat-content-video = [Video]

social-binding-already-linked = You are already linked to { $arg0 }. Send { $arg1 } to view your profile.
social-binding-confirmation-expired = This confirmation has expired or the linked account has changed. Request a new confirmation:
social-binding-code-invalid = This code is invalid or expired. Reconnect for a new code, or run { $arg0 } in-game.
social-binding-external-in-use = This social identity is already linked to another Minecraft player.
social-binding-help = Connect to the Minecraft server and send the complete linking command from the login screen here. If you are already in-game, run { $arg0 } to get it.
social-binding-link-completed = Your account has been linked. Reconnect to continue logging in.
social-binding-membership-required = Join a connected community on this platform, then try linking again.
social-binding-membership-unavailable = Community membership cannot currently be verified. Try again later or contact a server administrator.
social-binding-not-bound = You have not linked a game account yet.
social-binding-platform-mismatch = This verification code was issued for another platform.
social-binding-player-scope-in-use = That Minecraft player already has another identity linked in the same platform account scope.
social-binding-rate-limited = Too many failed attempts. Try again in about { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }.
social-binding-unavailable = Identity binding is temporarily unavailable.
social-binding-unlink-completed = Your account has been unlinked. Without another valid community binding, link an account again before joining.
social-binding-unlink-prompt = This will unlink this platform account from { $arg0 }. Without another valid community binding, you will lose server access and be disconnected if online. To confirm, send:
social-command-usage = This command takes no arguments. Send:
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
social-link-command-parameters = <code>
social-online-command-description = Show online players
social-profile-afk = Online · AFK
social-profile-command-description = Show your profile; full members can query by player name, mention, or reply
social-profile-command-parameters = [player]
social-profile-first-joined-label = First joined
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
social-query-no-players = No players are currently online.
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (Away)
   *[active] { $arg0 }
    }
social-query-population = Online { $arg0 }/{ $arg1 } · Active { $arg2 } · Away { $arg3 }
social-query-online-summary = { $arg0 ->
    [one] There is { $arg0 } player online.
   *[other] There are { $arg0 } players online.
    }{ $arg1 ->
    [0] { "" }
   *[other] { " " }AFK: { $arg1 }.
    }
social-query-players-label = Players
social-query-unknown = Unknown command. Send { $arg0 } to view available commands.
social-query-uptime-label = Uptime
social-query-version-label = Version
social-reply-unavailable = The result cannot be displayed right now. Check account changes with the profile command before submitting them again.
social-status-command-description = Show players, TPS, MSPT and uptime
social-title-command-help = Command help
social-title-player-profile = Player profile
social-title-server-status = Server status
social-unlink-command-description = Remove this identity link after confirmation
social-unlink-command-parameters = [{ social-unlink-confirmation } <confirmation>]
social-unlink-confirmation = confirm
ai-busy = The AI is handling too many requests; try again shortly
ai-disabled = The AI assistant is disabled or not configured
ai-question-button-unavailable = This question button is no longer available.
ai-request-failed = The AI request failed; try again later
ai-social-command-description = Ask the shared AI assistant
ai-social-command-parameters = <question>
ai-social-question-required = Include a question after the AI command
ai-suggested-questions = Suggested questions

social-aliases-online = online players who
social-aliases-status = status server
social-aliases-profile = profile me
social-aliases-help = help
social-aliases-bind = bind
social-aliases-unbind = unbind
social-aliases-ai = ai askai
