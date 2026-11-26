chat-content-audio = [Audio]
chat-content-card = [Carte]
chat-content-contact = [Contact]
chat-content-emote = [Action]
chat-content-face = [Émoji]
chat-content-file = [Fichier]
chat-content-history = [Historique de discussion]
chat-content-image = [Image]
chat-content-location = [Position]
chat-content-multiple = [Plusieurs messages]
chat-content-music = [Musique]
chat-content-notice = [Notification]
chat-content-reply = [Message cité]
chat-content-sticker = [Autocollant]
chat-content-video = [Vidéo]

social-binding-already-linked = Ton compte est déjà lié à { $arg0 }. Envoie { $arg1 } pour consulter ton profil.
social-binding-confirmation-expired = Cette confirmation a expiré ou le compte associé a changé. Demandez une nouvelle confirmation :
social-binding-code-invalid = Ce code est invalide ou expiré. Reconnecte-toi ou utilise { $arg0 } dans le jeu.
social-binding-external-in-use = Cette identité est déjà liée à un autre joueur Minecraft.
social-binding-help = Connecte-toi au serveur Minecraft et envoie ici la commande de liaison complète affichée à la connexion. Si tu es déjà en jeu, utilise { $arg0 } pour l’obtenir.
social-binding-link-completed = Ton compte a été lié. Reconnecte-toi pour poursuivre la connexion.
social-binding-membership-required = Rejoignez une communauté reliée sur cette plateforme, puis réessayez de lier votre compte.
social-binding-membership-unavailable = L’appartenance à la communauté ne peut pas être vérifiée pour le moment. Réessayez plus tard ou contactez l’administration du serveur.
social-binding-not-bound = Tu n’as pas encore lié de compte de jeu.
social-binding-platform-mismatch = Ce code de vérification a été émis pour une autre plateforme.
social-binding-player-scope-in-use = Ce joueur Minecraft possède déjà une autre identité liée dans le même périmètre de compte de la plateforme.
social-binding-rate-limited = Trop de tentatives ont échoué. Réessayez dans environ { $arg0 ->
        [one] { $arg0 } minute
       *[other] { $arg0 } minutes
    }.
social-binding-unavailable = La liaison d’identité est temporairement indisponible.
social-binding-unlink-completed = Ton compte a été dissocié. Sans autre liaison communautaire valide, lie à nouveau un compte avant de rejoindre le serveur.
social-binding-unlink-prompt = Ce compte de plateforme sera dissocié de { $arg0 }. Sans autre liaison communautaire valide, tu perdras l’accès au serveur et ta connexion en cours sera fermée. Pour confirmer, envoie :
social-command-usage = Cette commande ne prend aucun argument. Envoie :
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
social-link-command-parameters = <code>
social-online-command-description = Afficher les joueurs en ligne
social-profile-afk = En ligne · AFK
social-profile-command-description = Afficher votre profil ; les membres confirmés peuvent chercher par nom, mention ou réponse
social-profile-command-parameters = [joueur]
social-profile-first-joined-label = Première connexion
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
social-query-no-players = Aucun joueur n’est actuellement en ligne.
social-query-online-player = { $arg1 ->
    [afk] { $arg0 } (Absent)
   *[active] { $arg0 }
    }
social-query-population = En ligne { $arg0 }/{ $arg1 } · Actifs { $arg2 } · Absent { $arg3 }
social-query-online-summary = { $arg0 ->
    [one] Il y a actuellement { $arg0 } joueur en ligne.
   *[other] Il y a actuellement { $arg0 } joueurs en ligne.
    }{ $arg1 ->
    [0] { "" }
   *[other] { " " }En AFK : { $arg1 }.
    }
social-query-players-label = Joueurs
social-query-unknown = Commande inconnue. Envoyez { $arg0 } pour afficher les commandes disponibles.
social-query-uptime-label = Durée de fonctionnement
social-query-version-label = Version
social-reply-unavailable = Le résultat ne peut pas être affiché pour le moment. Vérifie les changements de compte avec la commande de profil avant de les redemander.
social-status-command-description = Afficher les joueurs, TPS, MSPT et la durée
social-title-command-help = Aide des commandes
social-title-player-profile = Profil du joueur
social-title-server-status = État du serveur
social-unlink-command-description = Supprimer ce lien d’identité après confirmation
social-unlink-command-parameters = [{ social-unlink-confirmation } <confirmation>]
social-unlink-confirmation = confirmer
ai-busy = L’IA traite trop de demandes ; réessayez dans un instant
ai-disabled = L’assistant IA est désactivé ou non configuré
ai-question-button-unavailable = Le bouton de cette question n’est plus disponible.
ai-request-failed = La requête IA a échoué ; réessayez plus tard
ai-social-command-description = Interroger l’assistant IA partagé
ai-social-command-parameters = <question>
ai-social-question-required = Ajoutez une question après la commande IA
ai-suggested-questions = Questions suggérées
ai-thinking = L’IA réfléchit…

social-aliases-online = enligne joueurs
social-aliases-status = statut
social-aliases-profile = monprofil
social-aliases-help = aide
social-aliases-bind = lier
social-aliases-unbind = délier
social-aliases-ai = demandeia
