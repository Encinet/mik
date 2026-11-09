### MIK · pt-br
# Counts are numbers. Usernames, IDs, commands and links remain literal.
# Selectors and grammatical forms belong to this locale. Keep default variants.

## Shared terminology · grammatical metadata is private to Fluent

-plot = Terreno
    .gender = masculine

-vehicle-model = Modelo
    .gender = masculine

## Shared · General

back = Voltar
back-to-main = Voltar ao menu principal
cancel = Cancelar
click-open = Clique para abrir
close = Fechar
current-off = Atual: desativado
current-on = Atual: ativado
player-only = Este comando só pode ser usado por jogadores
unknown = Desconhecido
usage = Uso

## Shared · Language, roles and time

language-command-description = Abrir as configurações de idioma
language-menu-title = Configurações de idioma
language-selected = Selecionado
language-set = Idioma alterado para { $arg0 }
role-custodian = Responsável
role-member = Membro
role-moderator = Moderador
role-new-player = Novo jogador
time-hours-minutes = { $arg0 }h { $arg1 }min
time-minutes = { $arg0 }min

## Shared · Menu presentation

interface-fov-apply = Aplicar FOV
interface-fov-custom = Personalizado
interface-fov-description = Informe o FOV das configurações do jogo para posicionar o menu.
interface-fov-input = FOV do cliente: inteiro de 30 a 110
interface-fov-invalid = Insira um número inteiro de 30 a 110.
interface-fov-title = Campo de visão
interface-reduced-motion = Reduzir movimento
interface-reduced-motion-hint = Reduz o movimento e a oscilação do menu.
interface-scale-adjust-hint = Role sobre uma porcentagem para ajustar.
interface-scale-layout-title = Tamanho do menu
interface-scale-menu-title = Interface e texto
interface-scale-preview = Exemplo Aa 123
interface-scale-reset = Restaurar para 100%
interface-scale-text-title = Tamanho do texto
interface-theme-apply = Salvar cores
interface-theme-charcoal = Carvão
interface-theme-color = Cor (#RRGGBB)
interface-theme-custom = Personalizado
interface-theme-custom-hint = Copia todos os valores atuais para um tema personalizado independente.
interface-theme-edit = Editar cores
interface-theme-forest = Floresta
interface-theme-hint = Aplica-se imediatamente apenas aos seus menus.
interface-theme-invalid = Digite uma cor #RRGGBB e uma opacidade de 0 a 100.
interface-theme-opacity = Opacidade (0–100%)
interface-theme-role-accent = Cor de destaque
interface-theme-role-button = Fundo do botão
interface-theme-role-button-disabled = Botão desativado
interface-theme-role-button-hovered = Botão apontado
interface-theme-role-button-pressed = Botão pressionado
interface-theme-role-button-selected = Botão selecionado
interface-theme-role-input = Fundo de entrada
interface-theme-role-sidebar = Fundo da barra lateral
interface-theme-role-toolbar = Fundo das ferramentas
interface-theme-role-tooltip = Fundo da dica
interface-theme-role-window = Fundo da janela
interface-theme-title = Tema do menu
interface-theme-warm = Quente
menu-collapse = Recolher
menu-expand = Expandir
menu-finish-editing = Conclua a edição primeiro.
menu-input-clear = Limpar
menu-input-done = Concluir
menu-input-empty = (vazio)
menu-input-single-line = Este campo aceita apenas uma linha.
menu-input-too-long = Comprimento: { $arg0 } / { $arg1 }. Encurte o texto antes de confirmar.
menu-query-cancel = Cancelar
menu-query-hint-rich = Pressione { $chatKey } para digitar…
menu-query-single-line = Apenas uma linha é permitida. Clique no campo para tentar novamente.
menu-query-too-long = Comprimento: { $arg0 } / { $arg1 }. Clique no campo para tentar novamente.
menu-rhythm-game-display = Jogando jogo de ritmo
menu-search-empty = Nenhum resultado
menu-search-failed = Falha na busca
menu-search-limit = Exibindo { $arg0 } de { $arg1 }. Refine a busca.
menu-search-loading = Buscando…
menu-search-retry = Tentar novamente
menu-usage-display-mm = <aqua><bold>No menu</bold></aqua>
url-dialog-confirm = Abrir
url-dialog-hint = O cliente pedirá outra confirmação para abrir o link.
url-dialog-question-rich = Abrir { $label }?
url-dialog-title = Abrir { $arg0 }

## Player · Client and presence

client-mods-undetected-rich = Mods recomendados: { $mods }. Clique em um nome para baixar.
client-tip-axiom-flight-speed-mm = <white>Segure <yellow>Alt esquerdo</yellow> para abrir o menu do <aqua>Axiom</aqua> e arraste o controle vertical à direita para ajustar a velocidade de voo. Você também pode usar <click:suggest_command:'/fly speed '><aqua><underlined>/fly speed</underlined></aqua></click>.</white>
client-tip-axiom-intro-rich = { $mod } permite copiar e colar construções, modificar o terreno e editar entidades de exibição. Clique no nome para baixar o mod.
client-version-behind-mm = <white>A versão do seu cliente <yellow>{ $clientVersion }</yellow> é mais antiga que a do servidor <green>{ $serverVersion }</green>. Considere atualizar.</white>
# $count, $distance (Number): nearby players and rounded distance in blocks.
invisibility-nearby-mm = <yellow>{ $count ->
        [one] <gold>{ $count }</gold> jogador próximo está <gold>invisível</gold>
       *[other] <gold>{ $count }</gold> jogadores próximos estão <gold>invisíveis</gold>
    }</yellow> <gray>·</gray> <gray>o mais próximo está a cerca de { $distance ->
        [one] { $distance } bloco
       *[other] { $distance } blocos
    }</gray>
player-bedrock-label = Bedrock
player-web-label = Web
presence-join-templates =
    .item-1 = Trim! { $player } tá online!
    .item-2 = Oba, é { $player }!
    .item-3 = Oi, { $player }! Chega mais!
# Broadcast to remaining players; the departing player does not receive this message.
presence-quit-templates =
    .item-1 = { $player } se desconectou.
    .item-2 = Trim! { $player } tá offline
    .item-3 = Online -1. { $player } se desconectou
welcome-first-join-mm = <gold><bold>Boas-vindas ao MIK!</bold></gold><newline><gray>Comece pelo <click:open_url:'https://mcmik.top'><yellow>site</yellow></click> e pela <click:open_url:'https://mcmik.top/wiki'><yellow>Wiki</yellow></click>; você também pode usar <yellow>/menu</yellow> para abrir o menu principal.</gray>
welcome-return-mm = <gold><bold>Boas-vindas de volta!</bold></gold><newline><gray>Sua última visita foi há <yellow>{ $arg0 ->
        [one] { $arg0 } dia
       *[other] { $arg0 } dias
    }</yellow>. Use <yellow>/menu</yellow> para encontrar rapidamente os recursos mais usados.</gray>

## Player · Guidance

tip-label = DICA

## Player · AFK

afk-already-afk-mm = <gray>Você já está <gold>AFK</gold></gray>
afk-command-description = Entrar no modo AFK
afk-default-statuses =
    .item-1 = se afastou do teclado
    .item-2 = entrou no modo de economia de energia
    .item-3 = passou para segundo plano
    .item-4 = está olhando para o nada
afk-display-title-mm = <gold><bold>AFK</bold></gold>
afk-display-with-message-mm = <gold><bold>AFK</bold></gold> <gray>·</gray> <white><message></white>
afk-enter-custom-templates =
    .item-1 = <gray>*</gray> <player> <gray>agora está AFK:</gray> <status>
    .item-2 = <gray>*</gray> <player> <gray>deixou uma mensagem:</gray> <status>
    .item-3 = <gray>*</gray> <player> <gray>alterou o status para:</gray> <status>
afk-enter-default-templates =
    .item-1 = <gray>*</gray> <player> <yellow><status></yellow>
afk-exit-templates =
    .item-1 = <gray>*</gray> <player> <green>voltou ao teclado</green>
    .item-2 = <gray>*</gray> <player> <green>voltou</green>
afk-not-afk-mm = <gray>Você não está <gold>AFK</gold> no momento</gray>
afk-status-too-long-mm = <red>O status AFK pode ter no máximo { $arg0 ->
        [one] <white>{ $arg0 }</white> caractere
       *[other] <white>{ $arg0 }</white> caracteres
    }</red>

## Player · Address association

player-association-entry = { $arg0 }. { $arg1 } · correspondências: { $arg2 ->
        [one] { $arg2 } registro
       *[other] { $arg2 } registros
    } · histórico: { $arg3 }
player-association-header = [Associação de login] { $arg0 } compartilha histórico de endereços com { $arg1 ->
        [one] { $arg1 } jogador
       *[other] { $arg1 } jogadores
    }.
player-association-hover = Há registros históricos de rede em comum. Isto serve apenas como apoio à análise; os endereços não são exibidos.
player-association-more-addresses = Mais { $arg0 ->
        [one] { $arg0 } registro
       *[other] { $arg0 } registros
    }
player-association-more-players = Mais { $arg0 ->
        [one] { $arg0 } jogador
       *[other] { $arg0 } jogadores
    }
player-association-time-days = há { $arg0 ->
        [one] { $arg0 } dia
       *[other] { $arg0 } dias
    }
player-association-time-occurrences = { $arg0 } ({ $arg1 ->
        [one] { $arg1 } registro
       *[other] { $arg1 } registros
    })
player-association-time-separator = ,
player-association-time-today = hoje


## Player · Account security
password-already-connected = Esta conta já está conectada.
password-already-set = Uma senha já está definida. Use /password change para alterá-la.
password-busy = Muitas tentativas. Aguarde e tente novamente.
password-change = Alterar senha
password-change-done = Senha alterada.
password-checking = Verificando a segurança da conta…
password-command-description = Gerenciar a proteção da conta por senha
password-confirm = Confirmar nova senha
password-conflict = A segurança da conta mudou. Repita a operação.
password-cooldown = A verificação da conta está temporariamente limitada. Tente novamente em { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }.
password-current = Senha atual
password-denied = Você não pode redefinir a senha desta conta.
password-disabled = Senha: não definida
password-enabled = Senha: definida
password-invalid = Senha incorreta. Tente novamente.
password-keep-role = Moderadores e responsáveis não podem remover suas senhas.
password-login = Entrar
password-login-expired = Sua verificação de acesso expirou. Reconecte-se ao servidor.
password-login-hint = Digite a senha deste servidor. Esqueceu? Peça à equipe para redefini-la.
password-mismatch = As novas senhas não coincidem.
password-new = Nova senha
password-nomination-required = Defina uma senha antes de se candidatar a moderador: /password set
password-not-set = Nenhuma senha definida. Use /password set para definir uma.
password-remove = Remover senha
password-remove-done = Senha removida.
password-remove-question = Ao remover a senha, ela não será mais verificada ao entrar. Digite sua senha atual para confirmar.
password-required-application = Você não pode remover a senha durante uma candidatura à equipe ou nomeação pendente.
password-required-role = Contas da equipe exigem senha. Peça a uma pessoa autorizada para redefini-la antes de entrar.
password-reset = Redefinir senha
password-reset-console = Senha de { $arg0 } redefinida: { $arg1 }. Entregue por um canal confiável após verificar a identidade.
password-reset-done = Senha de { $arg0 } redefinida. Copie agora e entregue após verificar a identidade; não será possível consultá-la novamente.
password-reset-question = Redefinir a senha de { $arg0 }? A antiga deixará de funcionar e as conexões serão encerradas. Confirme com sua própria senha.
password-retry = Tentar novamente
password-rules = Use de { $arg0 } a { $arg1 } caracteres. Nomes de jogadores e senhas comuns não são permitidos.
password-set = Definir senha
password-set-done = Senha definida.
password-set-first = Definir senha antes da candidatura
password-storage = A segurança de contas está indisponível. Tente mais tarde ou contate o proprietário.
password-timeout = A verificação foi cancelada ou expirou. Conecte-se novamente para tentar.
password-tip-set-mm = Use <gold><u>/password</u></gold> para definir uma <yellow>senha de acesso</yellow> e proteger sua conta.
password-title = Segurança da conta

## Player · Profile and tab list

nametag-cancel = Cancelar
nametag-clear = Limpar
nametag-clear-hint = Remove valores personalizados; estilos herdados podem permanecer.
nametag-command-description = Personalizar etiquetas de nome
nametag-confirm = Confirmar
nametag-conflict = O valor atual mudou. Compare o efeito atual e confirme novamente para substituí-lo.
nametag-copy-hint = Enviar o código do prefixo e sufixo ao chat
nametag-copy-label = [copiar]
nametag-copy-sent = O código do estilo de { $arg0 } está no chat.
nametag-current = Efeito atual
nametag-edit-ended = A edição terminou. Clique no campo novamente para continuar.
nametag-edit-prompt = Pressione T para editar { $arg0 } com MiniMessage (privado).
nametag-finish-field = Primeiro confirme ou cancele o campo que está editando.
nametag-input-invalid = Formato inválido. Envie MiniMessage em uma única linha ou selecione Limpar.
nametag-leading-label = Prefixo do nome
nametag-mine = Minha etiqueta
nametag-next = Próxima
nametag-no-changes = Sem alterações
nametag-no-permission = Membros e a equipe podem editar etiquetas. Você ainda pode ver estilos.
nametag-no-players = Nenhum jogador configurou um estilo de nome
nametag-page = Página { $arg0 } / { $arg1 }
nametag-players = Estilos de jogadores
nametag-preview-label = Visualização
nametag-previous = Anterior
nametag-refresh = Atualizar
nametag-saving = Salvando…
nametag-set-error = Não foi possível salvar: { $arg0 }. Tente novamente; o efeito atual aparece acima.
nametag-set-success = ✔ { $arg0 } definido
nametag-styles-failed = Não foi possível carregar os estilos. Atualize para tentar novamente.
nametag-styles-loading = Carregando estilos dos jogadores…
nametag-tip-customize-mm = Use <gold><u><click:suggest_command:'/nametag'>/nametag</click></u></gold> para personalizar o <light_purple>prefixo e o sufixo do seu nome</light_purple>.
nametag-title = Etiquetas de nome
nametag-too-long = { $arg0 } é muito longo. O limite é de { $arg1 ->
        [one] { $arg1 } caractere
       *[other] { $arg1 } caracteres
    }
nametag-trailing-label = Sufixo do nome
nametag-unset = (não definido)
nametag-unsupported-placeholder-rich = Apenas as variáveis PlaceholderAPI { $supported } são compatíveis, não { $unsupported }
nametag-user-data-load-failed = Não foi possível carregar os dados do usuário. Tente novamente mais tarde
tablist-footer-mspt-rich = MSPT: { $mspt }
tablist-footer-online-rich = Jogadores online: { $online } / { $max }
tablist-footer-ping-rich = { stat-ping }: { $ping } ms
tablist-footer-tps-rich = TPS: { $tps }

## Player · Movement and teleportation

back-command-description = Retornar ao local anterior de teletransporte ou morte
back-empty-mm = <gray>Nenhum local de retorno disponível</gray>
back-pending-mm = <gray>Teletransportando, aguarde</gray>
# $steps, $remaining, $max (Number): consumed history, remaining history and limit.
back-success-mm = <green>Você voltou { $steps ->
        [one] { $steps } passo
       *[other] { $steps } passos
    }</green> <gray>·</gray> <gray>{ $remaining }/{ $max }</gray>
back-teleport-failed-mm = <red>Falha no teletransporte. Tente novamente</red>
back-tip-return-mm = Use <yellow><u><click:suggest_command:'/back'>/back</click></u></yellow> para voltar à posição anterior ao último teletransporte ou à morte.
back-tip-undo-mm = Use <light_purple><u><click:suggest_command:'/reback'>/reback</click></u></light_purple> para desfazer seu último <yellow>/back</yellow>.
back-undo-empty-mm = <gray>Nenhum <white>/back</white> para desfazer</gray>
back-undo-success-mm = <green>Último <white>/back</white> desfeito</green>
back-undo-world-missing-mm = <red>O mundo de destino não está disponível. Não é possível desfazer</red>
back-world-missing-mm = <red>O mundo de destino não está disponível. Não é possível retornar</red>
boundary-near-mm = <yellow>Você está se aproximando da barreira do mundo</yellow>
boundary-reached-mm = <red>Você alcançou a barreira do mundo</red>
fly-command-description = Alternar voo pessoal
fly-disabled = Voo desativado
fly-enabled = Voo ativado
fly-native-mode = Seu acesso a /fly foi desativado. Este modo de jogo ainda permite voar.
fly-speed-click = Definir velocidade de voo para { $arg0 }x
fly-speed-current = Velocidade de voo: { $arg0 }x
fly-speed-limited = A carga de exploração limita o voo temporariamente a { $arg0 }x; sua configuração continua em { $arg1 }x.
fly-speed-presets = Predefinições
fly-speed-reset = Velocidade de voo redefinida para 1x
fly-speed-reset-action = Redefinir
fly-speed-reset-click = Redefinir velocidade de voo para 1x
fly-speed-set = Velocidade de voo definida para { $arg0 }x
fly-state-disabled = Desativado
fly-state-enabled = Ativado
fly-status = Voo: { $arg0 } | Velocidade: { $arg1 }x
home-action-create = Criar casa
home-action-delete = Excluir casa
home-action-teleport = Teleportar
home-action-update = Atualizar local
home-already-exists = Já existe uma casa com este nome
home-back = Voltar
home-confirm-delete = Confirmar exclusão
home-confirm-delete-detail-rich = Excluir a casa { $home }
home-confirm-delete-warning = Esta ação não pode ser desfeita
home-confirm-update = Confirmar atualização
home-confirm-update-detail-rich = Atualizar a casa { $home } para este local
home-delete-command-description = Excluir uma casa
home-delete-usage-desc = Excluir uma casa
home-deleted-rich = A casa { $home } foi excluída
home-distance = Distância: { $arg0 }
home-distance-blocks = { $arg0 ->
        [one] { $arg0 } bloco
       *[other] { $arg0 } blocos
    }
home-distance-other-world = outro mundo
home-empty-description = Fique no local em que deseja salvar uma casa
home-empty-title = Nenhuma casa definida
home-invalid-name = Nomes de casas não podem conter os caracteres < > :
home-list-rich = Casas: { $homes }
home-location = Localização: { $arg0 }, { $arg1 }, { $arg2 }
home-location-invalid = Os dados de localização desta casa são inválidos
home-max-reached = Você pode definir no máximo { $arg0 } casas
home-menu-title = Casas
home-no-homes = Você ainda não definiu nenhuma casa
home-not-found-rich = A casa { $home } não existe
home-search-placeholder = Buscar nomes de casas…
home-search-empty = Nenhuma casa correspondente
home-set-command-description = Definir uma casa na localização atual
home-set-rich = A casa { $home } foi definida
home-set-usage-desc = Definir uma casa na localização atual
home-summary-count = Definidas: { $arg0 } / { $arg1 }
home-teleport-failed = Falha no teletransporte. Tente novamente
home-teleported-rich = Teletransportado para a casa { $home }
home-tip-create-mm = Salve sua posição com <green><u><click:suggest_command:'/sethome '>/sethome nome</click></u></green> e volte com <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>.
home-tip-return-mm = Abra <aqua><u><click:suggest_command:'/home'>/home</click></u></aqua>, selecione uma casa e escolha “{ home-action-teleport }”.
home-updated-rich = A localização da casa { $home } foi atualizada
home-usage-book = Ajuda de comandos
home-usage-delhome = /delhome <name>  Excluir uma casa
home-usage-home = /home <name>  Teletransportar diretamente
home-usage-name-rule = Nomes de casas não podem conter < > :
home-usage-sethome = /sethome <name>  Salvar sua localização atual
home-world = Mundo: { $arg0 }
home-world-end = · O End
home-world-nether = · Nether
reback-command-description = Desfazer o último /back
teleport-accept-command-description = Aceitar a solicitação de teletransporte pendente
teleport-allow = Teletransportes por outros jogadores
teleport-bare-entity-selector = Para evitar teleportes acidentais, @e exige colchetes. Use um seletor explícito como @e[type=…].
teleport-block-afk = Bloquear enquanto AFK
teleport-denied = { $player } bloqueia teletransportes por outros jogadores no momento
teleport-deny-command-description = Recusar a solicitação de teletransporte pendente
teleport-force-command-description = Forçar teletransporte ignorando as preferências do jogador (somente administradores)
teleport-menu-title = Privacidade de teletransporte
teleport-moved-here = { $arg0 } teletransportou você para cá
teleport-policy-always-allow = Sempre permitir
teleport-policy-always-deny = Sempre recusar
teleport-policy-require-consent = Exigir minha aprovação
teleport-request-accept = Aceitar
teleport-request-approved = { $player } aceitou sua solicitação de teletransporte
teleport-request-deny-action = Recusar
teleport-request-expired = A solicitação de teletransporte expirou
teleport-request-failed = Não foi possível concluir o teletransporte. Tente novamente
teleport-request-none = Você não tem solicitação de teletransporte pendente
teleport-request-received = { $sender } quer teletransportar você: { $command }
teleport-request-rejected = Solicitação de teletransporte recusada
teleport-request-sent = Solicitação de teletransporte enviada para { $player }
tpany-command-description = Teletransportar para a localização de um jogador, online ou offline
tpany-done-offline-rich = Teletransportado para a última localização conhecida de { $player }: { $world } { $location }
tpany-done-online-rich = Teletransportado para a localização atual de { $player }
tpany-not-found-rich = Não foi possível encontrar a localização de { $player }
tpany-usage = Uso
tpany-usage-command = /tpany <player>
tpany-usage-desc = Teletransportar para a localização de um jogador, incluindo o cache offline

## Player · PVP

pvp-admin-menu-title = Administração de PvP
pvp-allow-mounted-damage = Criaturas montadas podem ser atacadas
pvp-allow-mounted-damage-desc = Quando ativado, outro jogador pode atacar uma criatura que você está montando enquanto ambos estiverem com PvP ativado
pvp-auto-enable-desc = Ataque duas vezes seguidas um jogador com PvP ativado
pvp-auto-enable-label = Ativação automática
pvp-auto-enable-warning-actionbar-mm = <yellow>Ataque mais uma vez para ativar o PvP automaticamente</yellow>
pvp-auto-enabled-actionbar-mm = <green>PvP ativado automaticamente</green>
pvp-combat-locked-mm = <red>Você não pode desativar o PvP em combate</red> <dark_gray>|</dark_gray> <gray>Tempo restante: { $arg0 ->
        [one] <yellow>{ $arg0 }</yellow> segundo
       *[other] <yellow>{ $arg0 }</yellow> segundos
    }</gray>
pvp-combat-tag-label = Tempo de combate
pvp-combat-tag-value = { $arg0 } s
pvp-command-description = Alternar status de PvP
pvp-enable = Ativar PvP
pvp-enable-desc = Quando ativado, você pode atacar outros jogadores com PvP ativado, e eles podem atacar você
pvp-enable-on-death = Ativar PvP ao morrer
pvp-enable-on-death-desc = Quando ativado, o PvP será ativado automaticamente quando você morrer
pvp-enable-on-death-label = Ativar ao morrer
pvp-enabled-on-death-mm = <yellow>O PvP foi ativado porque você morreu</yellow>
pvp-help-admin-mm = <aqua>/pvp admin</aqua> <gray>Abrir a administração de PvP dos jogadores online</gray>
pvp-help-gui-mm = <aqua>/pvp gui</aqua> <gray>Abrir as configurações de PvP</gray>
pvp-help-staff-control-mm = <aqua>/pvp admin player on|off|toggle|status|gui</aqua> <gray>Gerenciar um jogador online</gray>
pvp-help-status-mm = <aqua>/pvp status</aqua> <gray>Ver seu status de PvP</gray>
pvp-help-title-mm = <gold><bold>Comandos de PvP</bold></gold>
pvp-help-toggle-mm = <aqua>/pvp</aqua> <gray>Alternar seu status de PvP</gray>
pvp-menu-title = Configurações de PvP
pvp-mob-protected-actionbar-mm = <yellow>Esta criatura está protegida contra dano de PvP</yellow>
pvp-mob-protection-label = Proteção de criaturas
pvp-mounted-damage-label = Dano a criaturas montadas
pvp-no-permission-mm = <red>Você não tem permissão para gerenciar o status de PvP de outro jogador</red>
pvp-player-not-found-mm = <red>Jogador online não encontrado</red>
pvp-protect-mobs = Proteger suas criaturas
pvp-protect-mobs-desc = Proteger criaturas que você domesticou ou está montando
pvp-quick-combat-cooldown = Bloqueio de combate: { $arg0 ->
        [one] falta { $arg0 } segundo
       *[other] faltam { $arg0 } segundos
    }
pvp-quick-override = Uma regra temporária controla o PvP. Você pode alterar suas preferências nas configurações.
pvp-set-by-staff-mm = <aqua>A equipe alterou seu status de PvP para</aqua> <yellow>{ $arg0 }</yellow>
pvp-set-other = Status de PvP de { $arg0 } alterado para { $arg1 }
pvp-set-other-rich = Status de PvP de { $player } alterado para { $state }
pvp-state-label = PvP
pvp-state-off = Desativado
pvp-state-on = Ativado
pvp-status-player = Jogador: { $arg0 }
pvp-status-player-rich = Jogador: { $player }
pvp-status-title-mm = <gold><bold>Status de PvP</bold></gold>
pvp-summary-disabled = PvP desativado
pvp-summary-enabled = PvP ativado
pvp-target-disabled-actionbar-mm = <yellow>Esse jogador não está com o PvP ativado</yellow>
pvp-target-menu-title = Configurações de PvP - { $arg0 }
pvp-tip-protection-mm = Ative ou desative seu PVP com <light_purple><u><click:suggest_command:'/pvp'>/pvp</click></u></light_purple>. <green>Os dois jogadores precisam ativá-lo</green> para lutar.
pvp-toggled-off-mm = <gray>PvP desativado</gray> <dark_gray>|</dark_gray> <gray>Outros jogadores não podem atacar você</gray>
pvp-toggled-on-mm = <green>PvP ativado</green> <dark_gray>|</dark_gray> <gray>Agora você pode lutar contra outros jogadores com PvP ativado</gray>

## Player · Effects

effect-cleanup-all = [Remover todos os efeitos]
effect-cleanup-blocked = Só é possível remover efeitos no modo Criativo ou Espectador e fora de combate PVP.
effect-cleanup-expired = Esta solicitação de remoção expirou.
effect-cleanup-negative = [Remover efeitos negativos]
effect-cleanup-prompt-rich = Você recebeu efeitos negativos por { $source }. Deseja removê-los?
effect-cleanup-result = { $count ->
        [one] { $count } efeito removido.
       *[other] { $count } efeitos removidos.
    }
effect-cleanup-source = { $cause ->
        [AREA_EFFECT_CLOUD] uma nuvem de poção prolongada
        [ARROW] uma flecha
        [ATTACK] um ataque
        [COMMAND] um comando
        [FOOD] comida
        [PLUGIN] um plugin
        [POTION_DRINK] beber uma poção
        [POTION_SPLASH] uma poção arremessável
        [WARDEN] um defensor
        [WITHER_ROSE] uma rosa do Wither
       *[OTHER] uma causa desconhecida
    }

## Player · Trash

trash-clear = Esvaziar
trash-clear-hint = Clique para excluir todos os itens agora;
    caso contrário, serão excluídos automaticamente { $arg0 } segundos após fechar.
trash-command-description = Abrir a lixeira
trash-sort = Organizar
trash-sort-hint = Combinar pilhas iguais e ordenar os itens por tipo
trash-tip-dispose-mm = Use <aqua><u><click:suggest_command:'/trash'>/trash</click></u></aqua> para abrir a <yellow>lixeira</yellow> e colocar os itens que não quiser mais.
trash-title = Lixeira

## Player · Utilities

hat-command-description = Usar como chapéu o item da mão principal
hat-empty-hand-mm = <red>Nenhum item na mão principal</red> <gray>Segure um bloco ou item e use /hat</gray>
hat-success-mm = <green>Você está usando o item da mão principal</green> <dark_gray>|</dark_gray> <gray>Os espaços da mão e do capacete foram trocados</gray>
hat-tip-wear-mm = Use <light_purple><u><click:suggest_command:'/hat'>/hat</click></u></light_purple> para colocar na cabeça o item da <yellow>mão principal</yellow>.
removeitems-command-description = Remover itens no chão
# $count: styled display; $countValue (Number): plural selector.
removeitems-done-rich = Remoção concluída: { $countValue ->
        [one] { $count } item no chão
       *[other] { $count } itens no chão
    } · mundo { $world } { $location } { $radius }
removeitems-no-world = Não foi possível determinar o mundo
removeitems-radius = raio de { $arg0 ->
        [one] { $arg0 } bloco
       *[other] { $arg0 } blocos
    }
removeitems-tip-usage-mm = Use <aqua><u><click:suggest_command:'/rmitems '>/rmitems [raio]</click></u></aqua> para remover <yellow>itens no chão</yellow> por perto.
self-kick-command-description = Expulsar a si mesmo do servidor
self-kick-reason-too-long = O motivo não pode exceder { $arg0 ->
        [one] { $arg0 } caractere
       *[other] { $arg0 } caracteres
    }
# $person: second for the actor; third for other viewers and console.
self-kick-with-reason = { $person ->
        [second] Você saiu do servidor por vontade própria. Motivo: { $reason }
       *[third] { $player } saiu do servidor por vontade própria. Motivo: { $reason }
    }
    .item-1 = { $player } saiu do servidor por vontade própria. Motivo: { $reason }
    .item-2 = { $player } se desconectou com uma mensagem: { $reason }
    .item-3 = { $player } saiu do servidor com uma mensagem: { $reason }
    .item-4 = { $player } se despediu com um recado: { $reason }
# $person: second for the actor; third for other viewers and console.
self-kick-without-reason = { $person ->
        [second] Você saiu do servidor por vontade própria.
       *[third] { $player } saiu do servidor por vontade própria.
    }
    .item-1 = { $player } saiu do servidor por vontade própria.
    .item-2 = { $player } se desconectou.
    .item-3 = Trim! { $player } tá offline
    .item-4 = Online -1. { $player } se desconectou

## Community · Chat and mentions

chat-copy-hover = Clique esquerdo: copiar mensagem
chat-delay-3s = 3 segundos
chat-delay-5s = 5 segundos
chat-delay-7s = 7 segundos
chat-delay-cancel-command-description = Cancelar mensagens pendentes com atraso
chat-delay-cancelled = Mensagem pendente cancelada
chat-delay-current = Atraso atual do chat: { $arg0 }
chat-delay-nothing-to-cancel = Nenhuma mensagem pendente para cancelar
chat-delay-off = Desativado
chat-delay-preview-marker = [Pendente]
chat-delay-queued = A mensagem será enviada em { $arg0 }; use /cancel ou /c para cancelá-la
chat-item-empty-hover = Nenhum item na mão principal
chat-mention-all-hover = Notificar todos os jogadores visíveis neste canal
chat-msg-command-description = Enviar uma mensagem privada e entrar no chat privado
chat-player-hover = Clique esquerdo: iniciar mensagem privada
chat-private-enter = Você entrou no chat privado com { $arg0 }
chat-private-label = MSG
chat-private-no-reply-target = Nenhum destinatário recente de chat privado está online
chat-private-self = Você não pode iniciar um chat privado consigo mesmo
chat-private-target-not-found = Jogador online não encontrado: { $arg0 }
chat-private-target-offline = Seu contato está offline; aguarde ou escolha outro canal
chat-private-tip-channel-mm = Use <aqua><u><click:suggest_command:'/msg '>/msg jogador</click></u></aqua> para entrar no chat privado e <yellow><u><click:suggest_command:'/public'>/public</click></u></yellow> para voltar ao chat público.
chat-public-command-description = Entrar no canal de chat público
chat-public-enter = Você entrou no chat público
chat-reply-command-description = Responder ao seu último destinatário de chat privado
chat-settings-menu-title = Configurações do chat
chat-social-account-label = Conta da plataforma
chat-social-bound-player-label = Jogador vinculado
chat-social-name-label = Nome social
chat-social-unbound = Não vinculado a um jogador do Minecraft
chat-staff-command-description = Entrar no canal de chat da equipe
chat-staff-label = Equipe
chat-staff-permission-missing = Sem permissão para o chat da equipe; você voltou ao chat público
mention-action-bar = Aviso na barra de ação
mention-action-bar-text = { $sender } mencionou você
mention-alerts = Alertas de menção
mention-menu-title = Alertas de menção
mention-mute-afk = Silenciar enquanto AFK
mention-sound = Som de alerta
staffchat-enter = Você entrou no chat da equipe

## Community · Announcements

announcement-count = { $arg0 ->
        [one] { $arg0 } anúncio
       *[other] { $arg0 } anúncios
    }
announcement-empty = Sem avisos
announcement-first-page = Nos avisos mais recentes
announcement-last-page = Nos avisos mais antigos
announcement-latest = Último anúncio
announcement-list-newer = Grupo mais recente
announcement-list-older = Grupo anterior
announcement-more-count = Mais { $arg0 } não exibidos
announcement-new-count = { $arg0 } novos
announcement-newer = Avisos mais recentes
announcement-next-page = Próxima página
announcement-older = Avisos anteriores
announcement-page = Página { $arg0 } / { $arg1 }
announcement-previous-page = Página anterior
announcement-reload = Recarregar avisos
announcement-reload-command-description = Recarregar arquivo de avisos
announcement-reload-failed = Não foi possível recarregar os avisos; os anteriores foram mantidos
announcement-reloaded = Avisos recarregados
announcement-unread-count = { $arg0 } não lidos
announcement-updated = Avisos atualizados
announcement-view-all = Ver todos os avisos
announcement-view-command-description = Ver avisos do servidor

## Community · Identity and social platforms

identity-admin-binding-not-found = Nenhum vínculo inequívoco de jogador e plataforma foi encontrado.
identity-admin-binding-owner = { $arg0 } [{ $arg1 }]
identity-admin-binding-unlinked = Remoção para { $arg1 }: { $arg2 ->
        [one] { $arg2 } vínculo de identidade
       *[other] { $arg2 } vínculos de identidade
    } ({ $arg0 }).
identity-admin-command-description = Consultar ou revogar vínculos de identidade externos
identity-admin-help = /bindadmin lookup <jogador-ou-UUID>
    /bindadmin unlink <jogador-ou-UUID> <plataforma> confirm
identity-admin-lookup-empty = Nenhum vínculo foi encontrado para esse jogador.
identity-admin-lookup-title = Vínculos de { $arg0 }:
identity-binding-details = · { $arg0 } · escopo { $arg1 } · { $arg2 }
identity-binding-not-owned = Você não possui vínculos nessa plataforma.
identity-binding-unlinked = Remoção concluída: { $arg1 ->
        [one] { $arg1 } vínculo de identidade
       *[other] { $arg1 } vínculos de identidade
    } ({ $arg0 }).
identity-bindings-empty = Você não vinculou nenhuma conta de plataforma externa.
identity-bindings-title = Identidades externas vinculadas:
identity-bindings-unlink-hint = Use /bind unlink <plataforma> para remover todos os vínculos de uma plataforma.
identity-code-cancelled = O código de vínculo não utilizado do { $arg0 } foi cancelado.
identity-code-copy = Clique para copiar o comando de vínculo completo
identity-code-expiry = O código é válido por 5 minutos e só pode ser usado uma vez. Gerar outro o invalida.
identity-code-issued = Código de vínculo do { $arg0 } gerado:
identity-code-not-pending = Não há código de vínculo não utilizado do { $arg0 }.
identity-code-redeem = No grupo { $arg0 } escolhido, mencione o bot e envie: { $arg1 }
identity-command-description = Vincular o Minecraft a uma identidade de plataforma externa
identity-help = Comandos de vínculo de identidade:
    /bind qq — gerar rapidamente um comando de vínculo do QQ
    /bind <plataforma> — gerar um código de uso único
    /bind list — mostrar vínculos existentes
    /bind cancel <plataforma> — cancelar um código não utilizado
    /bind unlink <plataforma> — remover todos os vínculos de uma plataforma
identity-notify-linked = Sua identidade do { $arg0 } foi confirmada. Use /bind list para vê-la ou /bind unlink { $arg0 } para removê-la.
identity-notify-unlinked = Seu vínculo de identidade do { $arg0 } foi removido.
identity-platform-none = nenhuma
identity-platforms = Plataformas compatíveis: { $arg0 }
identity-scope-notice = Esta plataforma separa identidades por grupo. Faça o vínculo no grupo onde usará o bot; cada grupo precisa ser vinculado separadamente.
identity-storage-error = A operação no banco de vínculos falhou. Contate um administrador.
identity-unavailable = O vínculo de identidades está indisponível no momento. Contate um administrador.
identity-unsupported-platform = Plataforma não compatível: { $arg0 }
social-admin-conversations-empty = Nenhuma conversa observada para { $arg0 }.
social-admin-conversations-title = Conversas observadas para { $arg0 }:
social-admin-help = /social platforms · /social status [plataforma] · /social reload [plataforma] · /social conversations <plataforma>
social-admin-platform = - { $arg0 } ({ $arg1 }): { $arg2 }
social-admin-reloaded = A plataforma social { $arg0 } foi recarregada.
social-admin-reloaded-all = Todas as plataformas sociais foram recarregadas.
social-admin-status = { $arg0 } · { $arg1 } · geração { $arg2 } · conversas { $arg3 }
social-admin-title = Plataformas sociais
social-admin-unknown = Plataforma social desconhecida: { $arg0 }
social-binding-already-linked = Esta identidade já está associada ao jogador do Minecraft { $arg0 }.
social-binding-code-invalid = O código de verificação é inválido ou expirou. Gere outro com /bind { $arg0 } no Minecraft.
social-binding-external-in-use = Esta identidade já está associada a outro jogador do Minecraft.
social-binding-help = Execute /bind { $arg0 } no Minecraft.
    Copie o comando completo gerado.
    Envie o comando aqui.
    Use /meuperfil para verificar a associação.
social-binding-identity-missing = Esta mensagem não contém uma identidade de membro confiável; por isso, a associação não está disponível.
social-binding-linked = Associado ao jogador do Minecraft { $arg0 }.
social-binding-not-bound = Esta identidade da plataforma não está associada.
social-binding-platform-mismatch = Este código de verificação foi emitido para outra plataforma.
social-binding-player-scope-in-use = Esse jogador do Minecraft já tem outra identidade associada nesta conversa.
social-binding-rate-limited = Muitas tentativas falharam. Tente novamente em cerca de { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minutos
    }.
social-binding-status-unbound = Esta identidade não está associada. Use /associar com um código de verificação.
social-binding-unavailable = A associação de identidade está temporariamente indisponível.
social-binding-unlink-prompt = Desassociar remove a relação entre esta identidade e o Minecraft. Envie /desassociar confirmar para continuar.
social-binding-unlinked = Desassociado do jogador do Minecraft { $arg0 }.
social-command-description = Gerenciar adaptadores de plataformas sociais
social-duration-days = { $arg0 ->
        [one] { $arg0 } dia
       *[other] { $arg0 } dias
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
social-link-command-description = Associar sua conta do Minecraft com um código do jogo
social-online-command-description = Mostrar jogadores online
social-profile-afk = Online · AFK
social-profile-avatar-alt = Rosto do Minecraft
social-profile-command-description = Mostrar seu perfil; membros efetivos buscam por nome, menção ou resposta
social-profile-first-joined-label = Primeiro acesso
social-profile-language-label = Idioma
social-profile-last-seen-label = Visto por último
social-profile-member-required = Somente membros, moderadores ou responsáveis vinculados podem ver o perfil de outro jogador.
social-profile-offline = Offline
social-profile-online = Online · ativo
social-profile-play-time-label = Tempo de jogo
social-profile-player-label = Jogador
social-profile-player-not-found = Esse jogador do Minecraft não foi encontrado.
social-profile-presence-label = Presença
social-profile-target-ambiguous = Escolha apenas um alvo: nome de jogador, uma menção ou um usuário respondido.
social-profile-target-unbound = A identidade mencionada ou respondida não está associada ao Minecraft.
social-profile-uuid-label = UUID
social-query-active-label = Ativos
social-query-no-players = Nenhum jogador está online no momento.
social-query-online-label = Online
social-query-players-label = Jogadores
social-query-unknown = Comando desconhecido. Envie /ajuda para ver os comandos disponíveis.
social-query-uptime-label = Tempo de atividade
social-reply-hidden = Esta resposta foi ocultada pela política de segurança de conteúdo do servidor.
social-state-disabled = desativada
social-state-failed = falhou
social-state-ready = pronta
social-state-starting = iniciando
social-state-stopped = parada
social-status-command-description = Mostrar jogadores, TPS, MSPT e atividade
social-title-command-help = Ajuda de comandos
social-title-content-hidden = Conteúdo oculto
social-title-identity-linking = Associar conta
social-title-identity-unlinking = Desassociar conta
social-title-online-players = Jogadores online
social-title-player-profile = Perfil do jogador
social-title-server-status = Status do servidor
social-title-unknown-command = Comando desconhecido
social-unlink-command-description = Remover esta associação após confirmação

## Community · AI and knowledge

ai-busy = A IA está processando solicitações demais; tente novamente em breve
ai-cleared = Histórico da conversa com a IA apagado
ai-command-description = Perguntar ao assistente de IA no jogo
ai-disabled = O assistente de IA está desativado ou não configurado
ai-knowledge-curation-failed = Falha ao organizar o conhecimento: { $arg0 }
ai-knowledge-curation-finished = Organização concluída: aplicados { $arg0 } · ignorados { $arg1 } · falhas { $arg2 } · isolados { $arg3 } · processados { $arg4 }
ai-knowledge-curation-started = Organização do conhecimento iniciada.
ai-knowledge-disabled = A base de conhecimento está desativada.
ai-knowledge-index-dirty = É necessário reconciliar o índice; a consulta privada fica bloqueada até sincronizar.
ai-knowledge-last-failure = Última falha: { $arg0 }
ai-knowledge-last-sync = Última sincronização: { $arg0 }
ai-knowledge-learning-disabled = O aprendizado de conhecimento está desativado.
ai-knowledge-operation-failed = Falha na operação de conhecimento: { $arg0 }
ai-knowledge-operation-started = Operação de conhecimento iniciada.
ai-knowledge-protected = O documento { $arg0 } agora está protegido.
ai-knowledge-rollback = Documento { $arg0 } restaurado como revisão { $arg1 }.
ai-knowledge-status = Conhecimento: { $arg0 ->
        [one] { $arg0 } documento público
       *[other] { $arg0 } documentos públicos
    } / { $arg1 ->
        [one] { $arg1 } trecho de texto
       *[other] { $arg1 } trechos de texto
    } · { $arg2 ->
        [one] { $arg2 } documento privado
       *[other] { $arg2 } documentos privados
    } / { $arg3 ->
        [one] { $arg3 } trecho de texto
       *[other] { $arg3 } trechos de texto
    }
ai-knowledge-sync-failed = Falha ao sincronizar o conhecimento: { $arg0 }
ai-knowledge-sync-started = Sincronização do conhecimento iniciada.
ai-knowledge-synchronized = { ai-knowledge-status } · Sincronização concluída.
ai-knowledge-unprotected = O documento { $arg0 } não está mais protegido.
ai-learning-failure = Falha no aprendizado: { $arg0 }
ai-learning-status = Aprendizado: candidatos { $arg0 } · extraindo { $arg1 } · na fila { $arg2 } · extrações descartadas { $arg3 } · candidatos descartados { $arg4 } · isolados { $arg5 }
ai-memory-empty = Não há memórias privadas de { $arg0 }.
ai-memory-list = Memórias privadas de { $arg0 }: { $arg1 }
ai-memory-not-found = Memória privada { $arg0 } não encontrada.
ai-memory-removed = Remoção permanente para { $arg1 }: { $arg0 ->
        [one] { $arg0 } memória privada
       *[other] { $arg0 } memórias privadas
    }.
ai-memory-show = { $arg0 } — { $arg1 }
ai-player-unknown = Jogador desconhecido no cache: { $arg0 }
ai-prompt-too-long = A pergunta é longa demais (máximo de { $arg0 ->
        [one] { $arg0 } caractere
       *[other] { $arg0 } caracteres
    })
ai-reload-failed = Falha ao recarregar a configuração da IA: { $arg0 }
ai-reloaded = Configuração da IA recarregada ({ $arg0 })
ai-request-failed = A solicitação à IA falhou; tente novamente mais tarde
ai-social-command-description = Perguntar ao assistente de IA compartilhado
ai-social-question-required = Inclua uma pergunta após o comando de IA
ai-status-disabled = A IA está desativada
ai-status-enabled = IA ativa · provedor { $arg0 } · modelo { $arg1 } · ferramentas { $arg2 }
ai-status-none = nenhum
ai-thinking = Pensando…
ai-usage = Uso: /ai <pergunta> · /ai clear · /ai status
ai-worker-status = Tarefas de IA: ativas { $arg0 } · na fila { $arg1 } · descartadas { $arg2 }

## Community · Governance

autopromote-success-mm = <green><bold>Parabéns!</bold> Você agora é <gold>Membro</gold>. Agora tem acesso a mais comandos e recursos, incluindo a impressão simplificada de esquemas.</green>
governance-active-days-30 = Pelo menos 2 dias ativos em 30 dias (atual: { $arg0 })
governance-active-days-60 = Pelo menos 6 dias ativos em 60 dias (atual: { $arg0 })
governance-active-header = ─── Elegibilidade de membro ativo ───
governance-active-member = É membro atualmente
governance-active-member-age = Membro há pelo menos 30 dias
governance-active-playtime = Pelo menos 6 horas de jogo efetivo em 60 dias (atual: { $arg0 })
governance-activity-action = { $arg0 ->
    [confirm] Confirmar apoio
    [vote] Ler e votar
    *[details] Ver detalhes
    }
governance-activity-all = Ver todas ({ $arg0 })
governance-activity-closed = Encerrado
governance-activity-confirm-petition = Apoiar esta petição? Ao atingir o número de apoios necessário, será aberta uma votação de destituição.
governance-activity-needed = { $arg0 ->
    [one] Falta { $arg0 } apoio
    *[other] Faltam { $arg0 } apoios
    }
governance-activity-proposal = { $arg0 ->
    [appointment] Nomear { $arg1 } como moderador
    [removal] Destituir { $arg1 } da moderação
    [petition] Petição para destituir { $arg1 }
    *[ban] Votação para banir { $arg1 }
    }
governance-activity-remaining = { $arg1 ->
    [days] Tempo restante: { governance-days }
    [hours] Tempo restante: { governance-hours }
    *[minutes] Tempo restante: { governance-minutes }
    }
governance-activity-status = { $arg0 ->
    [available] Pode votar
    [voted] Voto registrado
    [join] Pode apoiar
    [joined] Apoio registrado
    [closed] Encerrado
    *[view] Somente leitura
    }
governance-already-moderator = Você já é moderador.
governance-application-status = { $arg0 ->
    [open] Há uma votação de nomeação em andamento
    [pending] Aprovada · nomeação pendente
    [blocked] Requisitos ainda não cumpridos
   *[ready] Você pode se candidatar
    }
governance-ballot-recorded = Seu voto anônimo foi registrado como { $arg0 }. Você pode alterá-lo até o prazo final.
governance-ban-details = Proposto por { $arg0 } · Duração { $arg1 } · Motivo: { $arg2 }
governance-ban-suffix = · Duração { $arg0 } · Motivo: { $arg1 }
governance-choice-abstain = Abstenção
governance-choice-no = Não
governance-choice-yes = Sim
governance-command-description = Ver situação de governança e votações
governance-custodian-no-nomination = Responsáveis técnicos não participam das eleições de moderadores.
governance-custodian-no-resign = A renúncia de moderadores não se aplica aos responsáveis técnicos.
governance-days = { $arg0 ->
        [one] { $arg0 } dia
       *[other] { $arg0 } dias
    }
governance-error-ban-already-active = Este jogador já tem um banimento ativo.
governance-error-ban-proposer-ineligible = Só membros ativos podem propor votação de banimento.
governance-error-ban-self = Você não pode propor votação para banir a si mesmo.
governance-error-candidate-ineligible = O candidato a moderador deve ser membro ativo.
governance-error-nomination-not-open = Você não tem candidatura em andamento.
governance-error-not-voter = Você não está na lista de eleitores fixada para esta votação.
governance-error-pause-already-active = Este jogador já tem uma suspensão de promoção ativa.
governance-error-pause-already-member = Este jogador já é membro; a promoção não pode ser suspensa.
governance-error-pause-end-invalid = A suspensão de promoção deve terminar no futuro.
governance-error-player-unknown = Este jogador ainda não tem registro de governança.
governance-error-removal-petition-closed = Esta petição foi encerrada ou substituída.
governance-error-removal-requires-petition = Uma votação de destituição exige apoiadores suficientes.
governance-error-removal-self = Você não pode apoiar sua própria destituição.
governance-error-removal-sponsor-ineligible = Só membros ativos podem apoiar uma destituição.
governance-error-removal-sponsors-too-few = São necessários pelo menos outros dois membros ativos.
governance-error-removal-target-not-moderator = Este jogador não é mais um moderador que possa ser destituído.
governance-error-removal-vote-open = Já existe votação de destituição em andamento para este moderador.
governance-error-termination-reason-invalid = Este motivo de encerramento não se aplica à votação.
governance-error-vote-already-open = Já existe uma votação deste tipo para este jogador.
governance-error-vote-closed = Esta votação já foi encerrada.
governance-error-vote-cooldown = Ainda não terminou o intervalo de três dias após falta de quórum.
governance-error-vote-subject-unknown = O alvo ainda não tem registro de governança.
governance-error-vote-termination-closed = Após o prazo, a votação não pode ser retirada.
governance-error-vote-unknown = Votação não encontrada.
governance-error-voters-too-few = São necessários pelo menos dois membros ativos para iniciar uma votação.
governance-failure-majority = Os votos favoráveis não superaram metade dos votos
governance-failure-minimum-yes = Votos favoráveis insuficientes
governance-failure-none = Nenhum motivo registrado
governance-failure-quorum = Quórum não atingido
governance-failure-two-thirds = Os votos favoráveis não chegaram a dois terços dos votos sem abstenção
governance-history-header = ─── Resultados recentes ───
governance-history-row = #{ $arg0 } { $arg1 } { $arg2 } · { $arg3 }{ $arg4 }
governance-hours = { $arg0 ->
        [one] { $arg0 } hora
       *[other] { $arg0 } horas
    }
governance-inactive-removal = { $arg0 } deixou automaticamente a moderação após 180 dias sem entrar.
governance-invalid-choice = Escolha yes, no ou abstain.
governance-invalid-duration = Duração inválida. Use, por exemplo, 30m, 12h ou 3d.
governance-kind-appointment = Nomeação
governance-kind-ban = Banimento
governance-kind-removal = Destituição
governance-legacy-ban-kick = Uma votação anterior foi aprovada. Você está banido por { $arg0 }. Motivo: { $arg1 }
governance-menu-already-sponsored = Já apoiada
governance-menu-application = Status da candidatura
governance-menu-back = Voltar
governance-menu-cancel = Cancelar
governance-menu-completed = Concluídos
governance-menu-confirm = Confirmar
governance-menu-confirm-nominate = A candidatura inicia uma votação para sua nomeação como moderador.
governance-menu-confirm-resign = Renunciar ao cargo de moderador?
governance-menu-confirm-sponsor = Apoiar este pedido de remoção?
governance-menu-confirm-withdraw = Retirar sua candidatura a moderador?
governance-menu-custodian-duties = Manutenção e administração do servidor.
governance-menu-identity = Função
governance-menu-identity-hint = Requisitos de promoção e histórico de atividade.
governance-menu-join-petition = Apoiar pedido de destituição
governance-menu-loading = Carregando estado da governança…
governance-menu-matters = Assuntos da comunidade
governance-menu-moderator-duties = Moderação e suporte aos jogadores.
governance-menu-nominate = Candidatar-se a moderador
governance-menu-offline = Desconectado
governance-menu-ongoing = Em andamento
governance-menu-online = Conectado
governance-menu-pause = Pausar promoção de novatos
governance-menu-pause-appeal = Caminho de recurso
governance-menu-pause-duration = Duração (ex.: 2d)
governance-menu-pause-reason = Motivo
governance-menu-pause-until = Termina em
governance-menu-pending-application = Aprovado · aguardando aplicação
governance-menu-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } apoios
governance-menu-petitions = Pedidos de remoção
governance-menu-player-name = Nome do jogador
governance-menu-players = Gestão de jogadores
governance-menu-refresh = Atualizar
governance-menu-related = Registros relacionados recentes
governance-menu-related-matters = Ver assuntos relacionados
governance-menu-resign = Renunciar como moderador
governance-menu-resume = Encerrar pausa da promoção
governance-menu-resume-reason = Motivo do encerramento
governance-menu-staff-only = Somente moderadores e responsáveis podem gerir jogadores.
governance-menu-staff-search = Buscar equipe
governance-menu-subject-filter = { $arg0 } · Limpar filtro ×
governance-menu-team = Equipe pública
governance-menu-team-empty = Nenhum membro correspondente
governance-menu-title = Governança da comunidade
governance-menu-withdraw = Retirar candidatura
governance-minutes = { $arg0 ->
        [one] { $arg0 } minuto
       *[other] { $arg0 } minutos
    }
governance-no-active-ban = Sem banimento ativo
governance-no-active-pause = Sem suspensão de promoção ativa
governance-nomination-withdrawn = Candidatura retirada. O resultado será anunciado automaticamente.
governance-none = Nenhum
governance-not-member = Não é membro
governance-not-moderator = Você não é moderador no momento.
governance-operation-error = Falha na operação de governança. Tente novamente.
governance-own-choice-closed = Sua escolha: { $arg0 }
governance-own-choice-open = Sua escolha: { $arg0 }; pode mudar até o prazo final.
governance-own-not-voted = Você podia votar, mas não votou.
governance-own-not-voted-hint = Você ainda não votou. Abra Governança e selecione esta votação.
governance-own-not-voter = Você não está na lista de eleitores desta votação.
governance-own-voted = Você já votou
governance-pause-cleared = Suspensão de promoção removida para { $arg0 }.
governance-pause-details = Suspensão: { $arg0 } · Até { $arg1 } · Recurso: { $arg2 }
governance-pause-fields-required = Informe um motivo e uma forma de recurso.
governance-pause-not-active = { $arg0 } não tem suspensão de promoção ativa.
governance-pause-notice = Sua promoção está suspensa. Motivo: { $arg0 } · Até { $arg1 } · Recurso: { $arg2 }
governance-pause-set = Promoção de { $arg0 } suspensa até { $arg1 }.
governance-petition-already-sponsored = Você já apoiou esta destituição ({ $arg0 }/{ $arg1 }).
governance-petition-row = { $arg0 } · { $arg1 }/{ $arg2 } apoiadores · Até { $arg3 }
governance-petition-started = Pedido de destituição de { $arg0 }: { $arg1 }/{ $arg2 } apoiadores; até { $arg3 }.
governance-petitions-header = ─── Pedidos de destituição ───
governance-player-first-join = Primeira entrada: { $arg0 }
governance-player-header = ─── { $arg0 } · Perfil de governança ───
governance-player-id = UUID: { $arg0 }
governance-player-last-login = Última entrada: { $arg0 }
governance-player-member-since = Membro desde: { $arg0 }
governance-player-never-joined = O jogador { $arg0 } nunca entrou neste servidor.
governance-promotion-account-age = Primeira entrada há pelo menos 3 dias
governance-promotion-header = ─── Requisitos de promoção ───
governance-promotion-play-days = Jogo efetivo em pelo menos 2 dias (atual: { $arg0 })
governance-promotion-playtime = Pelo menos 8 horas de jogo efetivo (atual: { $arg0 })
governance-resign-failed = Não foi possível renunciar. Contate um responsável técnico.
governance-resigned = { $arg0 } renunciou à moderação.
governance-result-announcement = Votação #{ $arg0 } { $arg1 } ({ $arg2 }): { $arg3 }{ $arg4 }
governance-result-failed = Rejeitada ({ $arg0 })
governance-result-open = Em andamento
governance-result-passed = Aprovada
governance-result-tally = { $arg0 } · Eleitores { $arg1 }, participação { $arg2 }, sim { $arg3 }, não { $arg4 }, abstenção { $arg5 }
governance-result-terminated = Encerrada ({ $arg0 })
governance-resume-notice = Sua suspensão de promoção foi removida. Motivo: { $arg0 }
governance-resume-reason-required = Informe o motivo para remover a suspensão.
governance-role-lookup-error = Não foi possível verificar a moderação. Contate um responsável técnico.
governance-seconds = { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }
governance-storage-error = Falha nos dados de governança. Contate um responsável técnico.
governance-termination-inactive = Moderador saiu por inatividade prolongada
governance-termination-ineligible = O candidato perdeu a condição de membro ativo
governance-termination-resigned = O moderador renunciou
governance-termination-unknown = Nenhum motivo registrado
governance-termination-withdrew = O candidato retirou a candidatura
governance-vote-electorate = Eleitores { $arg0 } · Quórum { $arg1 }
governance-vote-finalized = Concluída: { $arg0 }
governance-vote-header = ─── Votação #{ $arg0 } ───
governance-vote-in-progress = Em andamento · { $arg0 } participantes; detalhes após o encerramento.
governance-vote-list-row = #{ $arg0 } { $arg1 } { $arg2 } · Fecha { $arg3 } · Eleitores { $arg4 } · Participantes { $arg5 } · Quórum { $arg6 }{ $arg7 }
governance-vote-period = Abertura { $arg0 } · Fechamento { $arg1 }
governance-vote-started = Votação de { $arg1 } #{ $arg0 } iniciada: { $arg2 }; eleitores { $arg3 }, quórum { $arg4 }, fecha { $arg5 }. Abra Governança para ver os detalhes.
governance-vote-subject = { $arg0 } · { $arg1 }
governance-votes-header = ─── Votações anônimas em andamento ───

## World · Plots

plot-access-removed-for-you = Seu acesso ao terreno { $arg0 } foi removido.
plot-admin-created = { $arg0 } registrado para { $arg1 } · { $arg2 }
plot-allowed = ativado
plot-area-changed = Os limites mudaram. Atualize e confirme novamente.
plot-area-confirm = Substituir os limites de “{ $arg0 }” pela seleção ({ $arg1 ->
        [one] { $arg1 } célula de construção
       *[other] { $arg1 } células de construção
    })? As construções permanecem.
plot-area-hint = Marque os cantos com cliques esquerdo/direito do machado de madeira ou //pos1 e //pos2; volte para salvar.
plot-area-replace-save = Substituir limites e salvar
plot-area-save = Salvar limites
plot-area-saved = Limites de “{ $arg0 }” salvos.
plot-atmosphere-saved = Ambiente salvo.
plot-back = Voltar
plot-board-auto-notify-result = Avisos programados para { $arg0 ->
        [one] { $arg0 } jogador
       *[other] { $arg0 } jogadores
    }.
plot-board-auto-registered-text = O terreno “{ $arg0 }” foi cadastrado. Coordenadas externas das células construídas: X { $arg1 }–{ $arg2 }, Y { $arg3 }–{ $arg4 }, Z { $arg5 }–{ $arg6 }.
plot-board-detail = Registro de { $arg0 } em { $arg1 } { $arg2 }, { $arg3 }, { $arg4 }: { $arg5 }
plot-board-empty = Ainda não há registros da comunidade.
plot-board-header = Avisos e comentários da comunidade (últimos { $arg0 }; detalhes: /plot board ID)
plot-board-item = { $arg0 } { $arg1 } · { $arg2 } · { $arg3 }
plot-board-kind-notice = Aviso
plot-board-kind-objection = Objeção
plot-board-loading = Carregando publicação…
plot-board-menu = Mural da comunidade
plot-board-notice-alert = { $arg0 } publicou um aviso: { $arg1 } · { $arg2 }
plot-board-objection-alert = { $arg0 } apresentou uma objeção: { $arg1 } · { $arg2 }
plot-board-rate-limited = Limite de publicações atingido. Tente mais tarde.
plot-board-record-invalid = Registro inexistente, já resolvido ou ação não permitida.
plot-board-registered-alert = { $arg0 } cadastrou o terreno “{ $arg1 }”: { $arg2 }
plot-board-ruling-alert = Foi publicada uma decisão sobre a objeção de { $arg0 }: { $arg1 } · { $arg2 }
plot-board-ruling-detail = Decisão: { $arg0 }
plot-board-saved = Registro { $arg0 } salvo.
plot-board-status-elapsed = Publicado há pelo menos 72 horas
plot-board-status-open = Pendente
plot-board-status-ruled = Decidido
plot-board-status-withdrawn = Retirada
plot-board-text-invalid = A descrição deve ter 12–1000 caracteres; a decisão, pelo menos 8.
plot-board-unavailable = O mural não carregou. Tente mais tarde ou acesse https://mcmik.top/community/notices .
plot-board-withdrawal-alert = { $arg0 } retirou uma objeção: { $arg1 } · { $arg2 }
plot-board-write-failed = Não foi possível atualizar o mural. Tente mais tarde.
plot-command-blocked = A proteção bloqueou este comando. Use /fill ou /setblock diretamente no seu terreno; peça à equipe que revise comandos /execute complexos.
plot-command-description = Gerenciar terrenos de construção
plot-created = { -plot.gender ->
        [masculine] { -plot } { $arg0 } criado · { $arg1 }
        [feminine] { -plot } { $arg0 } criada · { $arg1 }
       *[other] { -plot } { $arg0 } criado · { $arg1 }
    }
plot-created-for-you = { $arg1 } registrou o terreno { $arg0 } para você.
plot-deleted = Terreno liberado; a construção permanece.
plot-denied = desativado
plot-error-admin-owner-only = Alterar membros com capacidades de gestão exige gerenciar membros e permissões.
plot-error-area-disconnected = O terreno deve formar uma área conectada.
plot-error-args = Argumentos inválidos ou ausentes. Use /plot help.
plot-error-busy = Os dados estão sendo salvos. Tente mais tarde; esta operação não foi enviada.
plot-error-flag = Permissão desconhecida. Escolha uma operação na lista de permissões.
plot-error-id = ID do terreno inexistente ou ambíguo. Use /plot list.
plot-error-member-missing = Este jogador não é mais membro.
plot-error-member-required = Novos jogadores podem ver terrenos e apresentar objeções. É preciso ser membro para criar um projeto.
plot-error-name = O nome do projeto deve ter de 1 a 48 caracteres.
plot-error-notice-text = Os avisos permitem até { $arg0 ->
        [one] { $arg0 } caractere
       *[other] { $arg0 } caracteres
    }, sem limite de linhas ou de comprimento por linha; caracteres de controle não são permitidos.
plot-error-overlap = A área de construção se sobrepõe a outro terreno.
plot-error-owner-member = dono do terreno · Permissões do grupo
plot-error-owner-only = desativado · Administração
plot-error-permission-changed = Permissões, membros ou regras herdadas mudaram. Confira as alterações novamente.
plot-error-permission-grant = Você não pode conceder capacidades de gestão que não possui.
plot-error-permission-owner-scope = Somente o proprietário, o dono do terreno superior ou a equipe podem editar permissões do proprietário.
plot-error-player-unknown = Jogador desconhecido. Confira o nome ou UUID.
plot-error-release-owner-only = desativado · Excluir terreno
plot-error-selection = Marque dois cantos com //pos1 e //pos2.
plot-error-selection-changed = A seleção mudou. Confirme novamente.
plot-error-staff-only = Só moderadores ou responsáveis podem gerenciar ou registrar terrenos de outros jogadores.
plot-error-storage = Falha na operação do terreno. Tente mais tarde ou contate a equipe.
plot-error-subplot-children = Libere os subterrenos antes de liberar o terreno principal.
plot-error-subplot-depth = Subterrenos só podem ter um nível.
plot-error-subplot-disconnected = As células selecionadas não estão conectadas. Divida cada parte separadamente.
plot-error-subplot-overlap = A seleção se sobrepõe a outro subterreno.
plot-error-subplot-owner = Você não tem permissão para esta operação de subterreno.
plot-error-subplot-parent = Marque os dois cantos nas células construídas do terreno principal.
plot-error-subplot-supervisor-member = O dono do terreno principal já supervisiona esta área e não precisa de um cargo de membro.
plot-error-time-format = Digite uma hora HH:mm (00:00–23:59).
plot-error-transfer-owner-only = desativado · Transferir propriedade
plot-error-transfer-self = Você já é dono deste terreno.
plot-flag-set = { $arg0 }: { $arg1 }.
plot-help = Guia de terrenos
plot-here = Aqui: { $arg0 } · { $arg1 }
plot-invited = Acesso concedido a { $arg0 }.
plot-item-summary = Área construída { $arg0 } bloco² · { $arg1 ->
        [one] { $arg1 } membro
       *[other] { $arg1 } membros
    }
plot-list-item = { $arg0 } · { $arg1 } · { $arg2 } bloco²
plot-marked = { $arg0 }: { $arg1 }
plot-member-draft-hint = Não enviado
plot-member-draft-reset = Limpar configurações da categoria
plot-member-draft-role = Função: { $arg0 }
plot-member-draft-submit = Conferir e confirmar
plot-member-draft-title = Rascunho de membro · { $arg0 }
plot-member-permissions = Permissões
plot-member-removed = Membro removido do projeto.
plot-menu-access = Membros e permissões
plot-menu-add-member = Adicionar membro
plot-menu-atmosphere = Ambiente
plot-menu-clear = Céu limpo
plot-menu-confirm-delete = Excluir o registro de { $arg0 }? Proteção, permissões dos membros e configurações serão removidas sem possibilidade de desfazer. As construções permanecerão.
plot-menu-confirm-subplot-transfer = Transferir { $arg0 } para { $arg1 }? O dono do terreno principal mantém a supervisão.
plot-menu-confirm-transfer = Transferir a gestão de { $arg0 } para { $arg1 }? Você perderá o acesso; a autoria existente permanece.
plot-menu-confirm-withdraw = Retirar esta resposta? A retirada continuará visível ao público.
plot-menu-create = Criar terreno
plot-menu-create-for-player = Registrar terreno para este jogador
plot-menu-custom-time = Hora personalizada
plot-menu-delete = Excluir registro do terreno
plot-menu-description = Descrição
plot-menu-feedback = Contestar este aviso
plot-menu-find-record = Buscar registro
plot-menu-flag-container = Abrir contêineres
plot-menu-flag-entity = Interagir com entidades
plot-menu-follow-parent = Seguir terreno principal
plot-menu-follow-world = Seguir o mundo
plot-menu-group-admin = administrador do terreno
plot-menu-group-collaborator = Colaboradores
plot-menu-group-member = Padrão para membros
plot-menu-group-newcomer = Padrão para novatos
plot-menu-group-owner = dono do terreno
plot-menu-here = Localização atual
plot-menu-manage = Gerenciar terrenos de jogadores
plot-menu-managed-empty = Este jogador ainda não tem terrenos.
plot-menu-managed-title = Terrenos de { $arg0 }
plot-menu-members = Membros ({ $arg0 })
plot-menu-name = Nome do projeto
plot-menu-owner = Proprietário: { $arg0 }
plot-menu-parent-supervisor = Dono do terreno principal: { $arg0 } · supervisão
plot-menu-player = Nome do jogador ou UUID
plot-menu-rain = Precipitação
plot-menu-record-search = ID ou título
plot-menu-related-notice = Ver aviso relacionado
plot-menu-remove-member = Remover membro
plot-menu-rename = Renomear projeto
plot-menu-rule = Registrar decisão
plot-menu-ruling = Explique a decisão (8–1000 caracteres)
plot-menu-set-role = Definir como { $arg0 }
plot-menu-settings = Configurações do projeto
plot-menu-submit = Aplicar
plot-menu-subplot-create = Criar subterreno
plot-menu-subplot-of = Parte de { $arg0 }
plot-menu-subplot-parent = Voltar ao terreno principal: { $arg0 }
plot-menu-subplots = Subterrenos ({ $arg0 })
plot-menu-time = Hora
plot-menu-time-input = Hora (HH:mm)
plot-menu-transfer = Transferir terreno
plot-menu-weather = Clima
plot-menu-withdraw = Retirar resposta
plot-no-here = Não há terreno protegido neste local.
plot-notice-board = Mural do terreno
plot-notice-cleared = Mural limpo
plot-notice-edit = Editar mural
plot-notice-input = Até { $arg0 ->
        [one] { $arg0 } caractere
       *[other] { $arg0 } caracteres
    }; várias linhas permitidas; deixe em branco para limpar
plot-notice-read = Ler aviso completo
plot-notice-saved = Mural atualizado
plot-permission-all-actions = Todas as ações
plot-permission-animal = Cuidar, domar e prender animais
plot-permission-break = Quebrar blocos
plot-permission-bucket = Usar baldes para líquidos e criaturas
plot-permission-category-build = Construção e alterações
plot-permission-category-entity = Entidades
plot-permission-category-interaction = Interação com blocos
plot-permission-category-management = Administração
plot-permission-category-storage = Armazenamento e itens
plot-permission-change-children = Subterrenos afetados: { $arg0 }
plot-permission-change-confirm = Aplicar alterações
plot-permission-change-granted = Concedidas: { $arg0 }
plot-permission-change-members = Membros afetados: { $arg0 }
plot-permission-change-more = Mais { $arg0 }
plot-permission-change-no-effect = As permissões efetivas não mudam. Configurações explícitas substituem a herança futura.
plot-permission-change-none = Nenhuma
plot-permission-change-revoked = Revogadas: { $arg0 }
plot-permission-change-subject = Destino: { $arg0 }
plot-permission-change-title = Confirmar alterações de permissões
plot-permission-create-subplot = Criar subterreno
plot-permission-decoration = Colocar e editar decorações
plot-permission-default-status = Padrão ({ $arg0 })
plot-permission-delete = Excluir terreno
plot-permission-door = Portas e portões
plot-permission-entity-damage = Ferir entidades
plot-permission-entity-storage = Abrir depósitos de entidades
plot-permission-harvest = Colher bagas, vinhas e mel
plot-permission-ignite = Acender fogo
plot-permission-item-drop = Soltar itens
plot-permission-item-pickup = Recolher itens do chão
plot-permission-manage-area = Editar limites
plot-permission-manage-members = Gerenciar membros
plot-permission-manage-permissions = Gerenciar permissões
plot-permission-manage-settings = Editar configurações do terreno
plot-permission-modify = Modificar blocos e fertilizar
plot-permission-operation = Ação
plot-permission-personal = Permissões individuais
plot-permission-personal-source = Configuração individual
plot-permission-place = Colocar blocos e veículos
plot-permission-reset = Restaurar padrões
plot-permission-result = Estado
plot-permission-ride = Montar veículos e animais
plot-permission-select-state = Escolher estado: { $arg0 }
plot-permission-sign = Editar placas
plot-permission-source-group = Permissões do grupo
plot-permission-source-local = Configuração local
plot-permission-source-owner = Direito de recuperação do proprietário
plot-permission-source-parent = Terreno superior
plot-permission-source-role = Padrão da função
plot-permission-state-default = Padrão
plot-permission-switch = Botões, alavancas e placas
plot-permission-trade = Negociar com aldeões
plot-permission-transfer = Transferir propriedade
plot-permission-workstation = Usar instalações e bancadas
plot-pos1 = Primeiro ponto
plot-pos2 = Segundo ponto
plot-private = Privado
plot-public = Público
plot-region-adjust = Ajustar este retângulo
plot-region-apply = Concluir ajuste do retângulo
plot-region-cancel = Cancelar ajuste do retângulo
plot-region-changed = A seleção mudou. Selecione o retângulo novamente.
plot-region-delete = Excluir este retângulo
plot-region-delete-confirm = Remover o retângulo #{ $arg0 } do rascunho? Os prédios permanecem. Os limites só mudam após salvar o terreno; é possível desfazer.
plot-region-edit-hint = Ajuste este retângulo com //pos1 e //pos2 e salve.
plot-region-finish-first = Primeiro conclua ou cancele o ajuste atual do retângulo.
plot-region-open = Gerenciar retângulos ({ $arg0 })
plot-region-select-hint = Selecione um retângulo numerado para focar e destacar. Linhas cinzas mostram esta página; a união pode mudar os números.
plot-renamed = Projeto renomeado.
plot-role-admin = administrador do terreno
plot-role-changed-for-you = Seu papel no terreno { $arg0 } agora é { $arg1 }.
plot-role-collaborator = colaborador
plot-role-owner = dono do terreno
plot-role-parent-owner = dono do terreno principal
plot-role-server-staff = equipe do servidor
plot-selection-add = Adicionar caixa atual
plot-selection-clear = Redefinir os dois pontos
plot-selection-empty = Marque dois cantos com //pos1 e //pos2.
plot-selection-fit = Restaurar limites salvos
plot-selection-incomplete = Um ponto foi marcado. Marque o outro; nada muda antes do envio.
plot-selection-redo = Refazer último passo
plot-selection-replace = Substituir pela caixa atual
plot-selection-session-hint = Agachar+F ou /plot edit para voltar. Sai após 20 minutos de inatividade.
plot-selection-size = Seleção: { $arg0 } × { $arg1 } × { $arg2 } blocos (X/Y/Z)
plot-selection-stopped = Seleção encerrada. Os rascunhos ficam salvos por terreno.
plot-selection-subtract = Subtrair caixa atual
plot-selection-undo = Desfazer último passo
plot-subplot-created = Subterreno { $arg0 } criado · { $arg1 } bloco²
plot-subplot-created-for-you = Você agora é dono do subterreno { $arg0 } dentro de { $arg1 }.
plot-subplot-owner-changed = O subterreno { $arg0 } agora pertence a { $arg1 }.
plot-subplot-parent-transferred = O terreno principal do seu subterreno { $arg0 } agora pertence a { $arg1 }.
plot-subplot-released = O subterreno { $arg0 } foi liberado; a área continua protegida pelo terreno principal.
plot-title = Terrenos
plot-transferred = Terreno { $arg0 } transferido para { $arg1 }.
plot-transferred-to-you = { $arg1 } transferiu o terreno { $arg0 } para você.

## World · Vehicles

vehicle-command-description = Dirigir e gerenciar veículos de entidades de exibição
vehicle-menu-label =
    { $arg0 ->
        [vehicle] Controles do veículo
        [driving] Dirigir e embarcar
        [maintenance] Manutenção e acesso
        [public-status] Acesso público
        [private-status] Acesso privado
        [home] Veículos
        [nearby] Veículos próximos
        [fleet] Todos os veículos acessíveis
        [selection] Seleção do modelo
        [origin] Definir origem aos meus pés
        [group] Selecionar grupo de exibição
        [radius] Raio de seleção (0–16 m)
        [displays] Explorar entidades próximas
        [clear] Limpar seleção e origem
        [capture] Criar modelo da seleção
        [toggle-selection] Alternar seleção
        [public] Tornar público
        [private] Tornar privado
        [reload] Recarregar modelos dos arquivos
        [leave] Sair do veículo
        [passenger] Assento de passageiro
        [fuel] Abastecer · segurar carvão
        [repair] Reparar · segurar barra de ferro
        [access] Acesso
        [remove] Remover veículo
        [hud] Mostrar HUD de direção
        [hide-preview] Ocultar prévia 3D
        [service-hint] Aproxime-se, desligue o motor e segure o material necessário.
        [nearby-hint] Aproxime-se a 32 blocos e saia do assento atual.
        [use-required] Requer mik.vehicle.use.
        [remove-hint] Pare o veículo e faça todos saírem.
        [reload-hint] Todos os veículos devem estar vazios e desligados. Rascunhos são mantidos; reabra modelos alterados.
        [pick-hint] Mire em uma entidade e clique com o botão direito. Agachar e clicar cancela; expira em 30 segundos.
        [spawn-hint] Coordenadas do mundo; quatro blocos à frente por padrão. Orientação conforme a visão horizontal ao gerar.
        [zoom-in] Ampliar
        [zoom-out] Reduzir
        [frame] Reposicionar à frente
        [offset] Mover por deslocamento
       *[other] { vehicle-command-description }
    }

vehicle-controls = { $arg0 }: W/S e A/D controlam; a roda troca marchas ou ajusta o acelerador; trocar de mão muda o modo; agachar sai. /vehicle
vehicle-dash-assisted = Assistência de estabilidade
vehicle-dash-automatic = Automático
vehicle-dash-contact = Apoio no solo / água: { $arg0 }
vehicle-dash-details = Detalhes
vehicle-dash-down = Reduzir marcha −
vehicle-dash-drive = Condução
vehicle-dash-energy = Consumo { $arg0 } U/min · autonomia estimada { $arg1 } min / { $arg2 } km
vehicle-dash-engine-start = Ligar motor
vehicle-dash-engine-stop = Desligar motor
vehicle-dash-fuel = Combustível { $arg0 } / { $arg1 } U · { $arg2 }%
vehicle-dash-gear = Marcha { $arg0 }
vehicle-dash-gearbox = Câmbio
vehicle-dash-handbrake = Estacionamento / freio de mão: { $arg0 }
vehicle-dash-heading = Rumo { $arg0 }°
vehicle-dash-health = Condição { $arg0 }% · ocupantes { $arg1 } / { $arg2 }
vehicle-dash-input = Acelerador { $arg0 }% · freio { $arg1 }% · direção { $arg2 }%
vehicle-dash-manual = Manual
vehicle-dash-mode-switch = Mudar para { $arg0 }
vehicle-dash-position = Origem { $arg0 }, { $arg1 }, { $arg2 }
vehicle-dash-recenter = Centralizar painel
vehicle-dash-rpm = RPM { $arg0 } / { $arg1 }
vehicle-dash-shift = { $arg0 } → { $arg1 } · { $arg2 }% · { $arg3 } s
vehicle-dash-speed = Velocidade { $arg0 } km/h · limite { $arg1 }
vehicle-dash-throttle-down = Acelerador −5%
vehicle-dash-throttle-up = Acelerador +5%
vehicle-dash-title = { $arg0 }
vehicle-dash-unassisted = Sem assistência
vehicle-dash-unavailable = Controles indisponíveis; reabra a partir do assento do motorista.
vehicle-dash-up = Subir marcha +
vehicle-dash-vertical = Altitude { $arg0 } m · velocidade vertical { $arg1 } m/s
vehicle-error = Falha na operação do veículo: { $arg0 }
vehicle-group-changed = A estrutura do grupo mudou. Selecione-o novamente antes de importar.
vehicle-group-invalid = O grupo contém entidades incompatíveis, gerenciadas, ausentes ou distantes. Todas devem estar neste mundo a até 32 blocos.
vehicle-group-limit = Importe no máximo 128 elementos de exibição e 256 membros no total.
vehicle-group-not-found = Nenhum grupo encontrado. Selecione um grupo, mire uma entidade de exibição de bloco ou item ou informe seu UUID.
vehicle-group-selected = Grupo selecionado: { $arg0 ->
        [one] { $arg0 } entidade de exibição
       *[other] { $arg0 } entidades de exibição
    } · raiz { $arg1 }
vehicle-hud-boat = { $arg0 } nós · impulso { $arg1 }% · rumo { $arg2 }°
vehicle-hud-car = { $arg0 } km/h  ·  { $arg1 }  ·  { $arg2 } RPM · { $arg3 }
vehicle-hud-plane = { $arg0 } km/h · altitude { $arg1 } m · velocidade vertical { $arg2 } m/s · acelerador { $arg3 }% · { $arg4 }
vehicle-hud-status = Combustível { $arg0 }% · condição { $arg1 }% · { $arg2 }
vehicle-model-center = Centro de massa
vehicle-model-choice = { $arg0 ->
    [CAR] Carro
    [BOAT] Lancha
    [PLANE] Avião
    [BODY] Carroceria
    [FRONT_WHEEL] Roda direcional
    [WHEEL] Roda
    [PROPELLER] Hélice
    [RUDDER] Leme
    [FIXED] Fixo
    [VERTICAL] Manter vertical
    [HORIZONTAL] Manter horizontal
    [CENTER] Virar para a câmera
    [NONE] Pose original
    [THIRDPERSON_LEFTHAND] Terceira pessoa · esquerda
    [THIRDPERSON_RIGHTHAND] Terceira pessoa · direita
    [FIRSTPERSON_LEFTHAND] Primeira pessoa · esquerda
    [FIRSTPERSON_RIGHTHAND] Primeira pessoa · direita
    [HEAD] Cabeça
    [GUI] Inventário
    [GROUND] Chão
    [LEFT] Esquerda
    [RIGHT] Direita
    [false] Desativado
    [true] Ativado
    [TEXT_CENTER] Centralizado
   *[other] { $arg0 }
    }
vehicle-model-field = { $arg0 ->
    [kind] Tipo de veículo
    [mass] Massa
    [maximum-speed] Velocidade máxima (km/h)
    [support-length] Curso dos apoios (m)
    [grip] Aderência
    [center-of-mass] Centro de massa
    [inertia] Inércia de rotação X/Y/Z
    [engine-torque] Torque máximo
    [engine-idle-rpm] Marcha lenta (RPM)
    [engine-redline-rpm] Limite de giro (RPM)
    [engine-inertia] Inércia do motor
    [engine-wheel-radius] Raio da roda (m)
    [engine-final-drive] Relação final
    [engine-reverse-ratio] Relação de ré
    [engine-ratios] Relações à frente
    [engine-shift-seconds] Tempo de troca (s)
    [fuel-capacity] Capacidade de combustível
    [fuel-consumption] Consumo (/s)
    [buoyancy] Flutuabilidade
    [wing-area] Área da asa (m²)
    [lift] Coeficiente de sustentação
    [animation] Função da animação
    [billboard] Orientação à câmera
    [item-transform] Pose do item
    [alignment] Alinhamento do texto
    [block-light] Luz dos blocos
    [sky-light] Luz do céu
    [background] Fundo (ARGB)
    [line-width] Largura do texto
    [opacity] Opacidade (0–255)
    [shadow] Sombra do texto
    [see-through] Texto visível através
    [x] X
    [y] Y
    [z] Z
    [size-x] Largura total X (m)
    [size-y] Altura total Y (m)
    [size-z] Comprimento total Z (m)
   *[other] { $arg0 }
    }
vehicle-model-field-range = Intervalo permitido: 0 < valor ≤ { $arg0 }
vehicle-model-front = Frente do veículo (+Z)
vehicle-model-front-choice = { $arg0 ->
    [negative-x] Lado −X atual
    [back] Traseira atual (−Z)
    [positive-x] Lado +X atual
    [look] Usar minha visão horizontal (arredondada a 90°)
   *[other] Definir este lado como frente
    }
vehicle-model-front-hint = Escolha a seta em 3D. A carroceria fica no lugar; bancos, saídas e pontos físicos mudam de eixos juntos. As mudanças em passos de 90° podem ser desfeitas.
vehicle-model-handle = { $arg0 ->
    [ROTATE] { $arg1 } { $arg2 } { $arg3 }°
   *[other] { $arg1 } { $arg2 } { $arg3 }
    }
vehicle-model-marker = { $arg0 ->
    [driver] Banco do motorista #{ $arg1 }
    [passenger] Banco do passageiro #{ $arg1 }
    [exit] Saída #{ $arg1 }
    [colliders] Caixa de colisão #{ $arg1 }
    [supports] Ponto de apoio #{ $arg1 }
    [center] Centro de massa
    [parts] Peça visual #{ $arg1 }
   *[other] { $arg1 }
    }
vehicle-model-parameter-group = { $arg0 ->
    [body] Carroceria e contato
    [engine] Motor e transmissão
    [fuel] Combustível
    [boat] Flutuabilidade do barco
    [plane] Sustentação do avião
    [power] Potência do motor
   *[other] { $arg0 }
    }
vehicle-model-parameter-help = { $arg0 ->
    [body] Massa, velocidade e contato com o chão. O centro de massa pode ser ajustado em 3D. Apenas o rascunho muda antes de salvar.
    [engine] Giro, torque, rodas e relações. As relações à frente devem diminuir; o limite deve superar 1,5 × a marcha lenta.
    [fuel] A capacidade usa unidades de combustível do jogo; o consumo é por segundo.
    [boat] A flutuabilidade se aplica a barcos. Os parâmetros de outros tipos são mantidos.
    [plane] Área da asa e sustentação se aplicam a aviões. Os demais parâmetros são mantidos.
    [power] Giro e torque do tipo atual. Os ajustes de câmbio dos carros são mantidos.
   *[other] { $arg0 }
    }
vehicle-model-property-help = { $arg0 ->
    [block-light] −1 usa a luz do mundo. Para substituí-la, ajuste ambos os valores entre 0 e 15.
    [sky-light] −1 usa a luz do mundo. Para substituí-la, ajuste ambos os valores entre 0 e 15.
    [background] Inteiro ARGB com sinal, por exemplo −16777216 para preto opaco.
    [line-width] Largura de linha: 1–2048 pixels.
    [opacity] 0 é transparente; 255 é opaco.
   *[other] { $arg0 }
    }
vehicle-model-spatial = Entrar na seleção 3D
vehicle-model-spatial-hint = Clique com o botão direito num marcador para selecionar ou ajustar. Agachar + botão direito volta às configurações. A roda do mouse muda o passo de ajuste.
vehicle-model-spawn = Gerar este modelo
vehicle-model-add = Adicionar componente
vehicle-model-add-here = Adicionar apoio aos meus pés
vehicle-model-anchor = Reposicionar à frente
vehicle-model-animation = Animação
vehicle-model-apply = Aplicar / confirmar
vehicle-model-auto = Recalcular limites de colisão
vehicle-model-block = Bloco
vehicle-model-center-here = Definir centro de massa aos meus pés
vehicle-model-center-look = Definir centro de massa na superfície apontada
vehicle-model-check = Verificar estrutura e parâmetros
vehicle-model-checked = Estrutura e parâmetros válidos. Ao salvar, também são verificados dados de exibição, veículos existentes e colisões no mundo.
vehicle-model-clone = Clonar modelo
vehicle-model-colliders = Caixas de colisão
vehicle-model-copy = Duplicar e editar (assentos copiados são de passageiro)
vehicle-model-corner-cancel = Cancelar caixa de colisão pendente
vehicle-model-corner-first = Marcar primeiro canto aos meus pés
vehicle-model-corner-first-look = Marcar primeiro canto na superfície apontada
vehicle-model-corner-hint = { vehicle-model-corner-first } → { vehicle-model-corner-second }
vehicle-model-corner-required = { vehicle-model-corner-first }
vehicle-model-corner-second = Usar meus pés como canto oposto e adicionar caixa
vehicle-model-corner-second-look = Usar a superfície apontada como canto oposto
vehicle-model-create = Criar modelo
vehicle-model-delete = Excluir modelo
vehicle-model-delete-warning = Excluir este modelo permanentemente? Não é possível se houver veículos que o utilizem.
vehicle-model-deleted = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } excluído
        [feminine] { -vehicle-model } excluída
       *[other] { -vehicle-model } excluído
    }
vehicle-model-discard = Descartar rascunho
vehicle-model-discard-warning = Descartar todas as alterações não salvas? Não é possível desfazer.
vehicle-model-discarded = Rascunho descartado
vehicle-model-draft = Rascunho não salvo
vehicle-model-driver = Assento do motorista
vehicle-model-driver-here = Adicionar assento do motorista aos meus pés
vehicle-model-edit = Editar / retomar rascunho
vehicle-model-overview = Resumo do rascunho
vehicle-model-panel = Voltar ao painel de edição
vehicle-model-editor-help = { $arg0 ->
   *[overview] Escolha uma categoria abaixo, depois um objeto e uma ferramenta. Salvar e Desfazer ficam na parte inferior.
    [parts] Gerencie blocos, itens e textos visíveis. Selecione uma peça para alterar seu conteúdo ou transformação.
    [seats] Selecione um assento para definir a posição e a saída. É necessário exatamente um assento de motorista.
    [colliders] Selecione uma caixa de colisão para alterar o centro e as dimensões completas. Adicione uma caixa ou ajuste-a ao modelo.
    [supports] Selecione um ponto de contato para movê-lo. Esses pontos fornecem apoio no solo ou flutuação.
    [parameters] Escolha um grupo de parâmetros e depois um campo para editar seu valor.
    [tools] Ajuste a prévia, selecione objetos no mundo ou verifique o rascunho. Descartar exige confirmação.
    }
vehicle-model-section-count = { $arg0 ->
   *[parts] { vehicle-model-parts } · { $arg1 }
    [seats] { vehicle-model-seats } · { $arg1 }
    [colliders] { vehicle-model-colliders } · { $arg1 }
    [supports] { vehicle-model-supports } · { $arg1 }
    }
vehicle-model-content-action = { $arg0 ->
   *[block] Definir conteúdo do bloco
    [item] Usar item da mão principal
    [text] Definir conteúdo do texto
    }
vehicle-model-exact-input = Inserir valores exatos
vehicle-model-axis-value = { $arg0 } · { $arg1 }
vehicle-model-component-details = Detalhes do componente
vehicle-model-selection-unavailable = Objeto alterado ou removido; volte e escolha novamente
vehicle-model-exit = Posição de saída / ajuste fino
vehicle-model-group = Adicionar grupo Axiom da mão principal
vehicle-model-hand-invalid = O item de exibição Axiom é inválido ou contém entidades ou dados não suportados.
vehicle-model-hand-required = Segure um item de entidade de exibição Axiom na mão principal e tente importar novamente.
vehicle-model-here = Usar a posição atual dos meus pés
vehicle-model-history-empty = Nenhuma edição disponível nessa direção do histórico.
vehicle-model-import = Importar grupo Axiom da mão principal
vehicle-model-item = Item
vehicle-model-library = Modelos salvos · rascunhos ficam na memória até serem salvos
vehicle-model-look = Posicionar mirando (fecha o menu; clique direito confirma)
vehicle-model-look-missing = Nenhuma superfície atingida. Mire um bloco do mundo ou uma peça da sua prévia a até 32 blocos.
vehicle-model-manual = Inserir coordenadas locais
vehicle-model-options = Opções
vehicle-model-parameters = Parâmetros de física e motor
vehicle-model-parts = Elementos de exibição
vehicle-model-passenger-here = Adicionar assento de passageiro aos meus pés
vehicle-model-pick = Editar a peça da prévia para a qual você olha
vehicle-model-pick-hint = Abra uma prévia e olhe para uma peça de bloco ou item, ou selecione antes um componente no editor.
vehicle-model-place-cancelled = Posicionamento cancelado: prazo expirado, estado de edição alterado ou cancelamento manual.
vehicle-model-place-hint = Posicionamento ativo por 30 segundos. Mire um bloco ou sua prévia; clique direito com a mão principal confirma, agachar + clique direito cancela.
vehicle-model-position = Posição / ajuste fino
vehicle-model-position-hint = Coordenadas locais: +Y para cima, +Z para a frente. Os botões movem por incrementos; a entrada exata define a posição absoluta.
vehicle-model-preview = Prévia privada
vehicle-model-preview-failed = Prévia indisponível; seu rascunho foi mantido: { $arg0 }
vehicle-model-preview-hint = Prévia exibida. Entre na seleção 3D para selecionar e ajustar o modelo diretamente.
vehicle-model-redo = Refazer
vehicle-model-remove = Remover componente
vehicle-model-remove-warning = Remover este componente? Os índices seguintes mudam; é possível desfazer.
vehicle-model-rotate = Girar componente
vehicle-model-rotate-hint = Rotação local relativa: X inclinação, Y guinada, Z rolagem. A posição não muda.
vehicle-model-save = Salvar e encerrar
vehicle-model-save-continue = Salvar e continuar editando
vehicle-model-saved = { -vehicle-model.gender ->
        [masculine] { -vehicle-model } salvo
        [feminine] { -vehicle-model } salva
       *[other] { -vehicle-model } salvo
    }
vehicle-model-scale = Escalonar componente
vehicle-model-scale-all = Escalonar todos os eixos { $arg0 } { $arg1 }
vehicle-model-scale-hint = Multiplica ou divide os comprimentos dos eixos locais, não as dimensões dos limites no mundo.
vehicle-model-seats = Assentos e saídas
vehicle-model-selected = Importar elementos selecionados
vehicle-model-size = Redimensionar caixa de colisão
vehicle-model-size-hint = Largura, altura e comprimento totais; o centro permanece no mesmo lugar.
vehicle-model-snap-off = Ajuste à grade: desativado
vehicle-model-snap-on = Ajuste à grade: ativado
vehicle-model-step = Alterar passo de ajuste
vehicle-model-supports = Pontos de apoio no solo
vehicle-model-text = Texto
vehicle-model-title = Gerenciar modelos de veículos
vehicle-model-tools = Prévia e ferramentas
vehicle-model-transform-step = Alterar passo de ajuste
vehicle-model-undo = Desfazer
vehicle-result = Veículo: { $arg0 }
vehicle-selection-info = Seleção: { $arg0 ->
        [one] { $arg0 } entidade de exibição
       *[other] { $arg0 } entidades de exibição
    } · grupo { $arg1 } · origem { $arg2 }, { $arg3 }, { $arg4 }
vehicle-status-contact = Colisão / obstáculo
vehicle-status-damage = Veículo com danos críticos
vehicle-status-disabled = Veículo desativado
vehicle-status-engine-off = Motor desligado
vehicle-status-engine-on = Motor ligado
vehicle-status-gear-limit = Limite de controle atingido
vehicle-status-grounded = Barco encalhado
vehicle-status-low-fuel = Pouco combustível
vehicle-status-manual-only = Mude primeiro para o modo manual
vehicle-status-no-fuel = Sem combustível
vehicle-status-overrev = Reduzir a marcha excederia o giro do motor
vehicle-status-shifting = Trocando marcha
vehicle-status-stall = Motor apagado
vehicle-status-stop-first = Pare antes de inverter o sentido ou estacionar
vehicle-value-off = Desativado
vehicle-value-on = Ativado

## World · Elevators, regeneration and spawn

async-regen-already-running = Você já tem uma regeneração em andamento; use /regen status ou /regen cancel
async-regen-apply-expired = Não há plano válido; execute /regen preview primeiro
async-regen-apply-plan = Aplicar plano salvo
async-regen-apply-started = Aplicando { $arg0 } à seleção salva: { $arg1 }
async-regen-awaiting-apply = Plano aguardando aplicação: { $arg0 } · { $arg1 } · expira em { $arg2 ->
        [one] { $arg2 } segundo
       *[other] { $arg2 } segundos
    }
async-regen-block-preview-progress = prévia de blocos · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } verificados · { $arg2 } alterações · { $arg3 ->
        [one] { $arg3 } entidade de bloco
       *[other] { $arg3 } entidades de bloco
    } em risco
async-regen-block-preview-summary = Prévia: { $arg0 ->
        [one] { $arg0 } alteração de bloco
       *[other] { $arg0 } alterações de blocos
    } para { $arg2 }; risco de sobrescrita: { $arg1 ->
        [one] { $arg1 } entidade de bloco existente
       *[other] { $arg1 } entidades de bloco existentes
    }
async-regen-cancelled = Regeneração cancelada. As { $arg0 ->
        [one] { $arg0 } alteração
       *[other] { $arg0 } alterações
    } já aplicadas permanecem no mundo
async-regen-command-description = Regenerar uma seleção do FastAsyncWorldEdit de forma assíncrona
async-regen-completed = Regeneração concluída: { $arg0 ->
        [one] { $arg0 } bloco
       *[other] { $arg0 } blocos
    } alterados em { $arg1 }
async-regen-failed = Regeneração interrompida por um erro interno. As { $arg0 ->
        [one] { $arg0 } alteração
       *[other] { $arg0 } alterações
    } já aplicadas permanecem no mundo
async-regen-filter-all = todos os tipos de bloco
async-regen-invalid-blocks = Tipos desconhecidos ou que não são blocos: { $arg0 }
async-regen-not-running = Você não tem uma regeneração em andamento
async-regen-nothing-to-apply = A seleção já corresponde a este plano; não há nada para aplicar
async-regen-plan-cancel = Cancelar
async-regen-plan-dialog-title = Aplicar plano de regeneração
async-regen-preview-cancelled = Prévia cancelada; nenhum dado do mundo foi alterado
async-regen-preview-started = Prévia iniciada: seleção { $arg0 ->
        [one] { $arg0 } bloco
       *[other] { $arg0 } blocos
    } · área { $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } · modo { $arg2 }
async-regen-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } planejados · { $arg3 ->
        [one] { $arg3 } alteração
       *[other] { $arg3 } alterações
    } aplicadas
async-regen-safety-note = Nada fora da seleção salva será alterado. Construções internas podem ser sobrescritas e o Vanilla não identifica blocos colocados por jogadores. Confira a visualização e faça um backup antes de aplicar.
async-regen-scope-blocks = regenerar blocos correspondentes a { $arg0 }
async-regen-selection-required = Conclua primeiro uma seleção do FastAsyncWorldEdit
async-regen-skipped-ungenerated = Chunks de destino nunca gerados ignorados: { $arg0 ->
        [one] { $arg0 } chunk
       *[other] { $arg0 } chunks
    }. Nenhum chunk de destino foi criado ou alterado.
async-regen-start-failed = Não foi possível iniciar a regeneração; consulte o registro do servidor
async-regen-state-applying = aplicando alterações
async-regen-state-cancelled = cancelada
async-regen-state-completed = concluída
async-regen-state-failed = falhou
async-regen-state-generating = gerando e comparando chunks
async-regen-state-preparing = preparando o mundo de origem
async-regen-status = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } planejados · { $arg3 } de { $arg4 } alterações encontradas aplicadas
async-regen-structure-metadata-applied = Migrados { $arg0 } inícios de estrutura e { $arg1 } referências de chunk; /locate e a geração dependente de estruturas podem reconhecê-los
async-regen-upgrade-apply-progress = { $arg0 } · { $arg1 }/{ $arg2 ->
        [one] { $arg2 } chunk
       *[other] { $arg2 } chunks
    } planejados · { $arg3 ->
        [one] { $arg3 } bloco
       *[other] { $arg3 } blocos
    } e { $arg4 ->
        [one] { $arg4 } célula de bioma
       *[other] { $arg4 } células de bioma
    } aplicados
async-regen-upgrade-boundary-note = { $arg0 ->
        [one] { $arg0 } célula de bioma
       *[other] { $arg0 } células de bioma
    } no limite serão ignoradas para impedir alterações fora da seleção
async-regen-upgrade-completed = Atualização concluída: { $arg0 ->
        [one] { $arg0 } bloco
       *[other] { $arg0 } blocos
    } e { $arg1 ->
        [one] { $arg1 } célula de bioma
       *[other] { $arg1 } células de bioma
    } alterados em { $arg2 }
async-regen-upgrade-preview-progress = prévia · { $arg0 }/{ $arg1 ->
        [one] { $arg1 } chunk
       *[other] { $arg1 } chunks
    } verificados · { $arg2 } alterações de bloco · { $arg3 ->
        [one] { $arg3 } célula de bioma
       *[other] { $arg3 } células de bioma
    } · { $arg4 ->
        [one] { $arg4 } estrutura
       *[other] { $arg4 } estruturas
    }
async-regen-upgrade-preview-summary = Prévia: { $arg0 ->
        [one] { $arg0 } alteração de bloco
       *[other] { $arg0 } alterações de blocos
    }, { $arg1 ->
        [one] { $arg1 } célula de bioma
       *[other] { $arg1 } células de bioma
    }, { $arg2 ->
        [one] { $arg2 } estrutura
       *[other] { $arg2 } estruturas
    } (aptas à migração completa de metadados: { $arg3 }); risco de sobrescrita: { $arg4 ->
        [one] { $arg4 } entidade de bloco existente
       *[other] { $arg4 } entidades de bloco existentes
    }
async-regen-upgrade-scope-biomes = somente biomas
async-regen-upgrade-scope-terrain = terreno, estruturas e biomas
async-regen-usage = Uso: /regen preview blocks [tipos...] | /regen preview upgrade | /regen preview biomes | /regen show | /regen apply | /regen status | /regen cancel
async-regen-visualization-legend = Visualização em { $arg0 }: ciano = limite da seleção, laranja = amostra dos blocos alterados, vermelho = entidade de bloco em risco. Só você vê estas partículas.
async-regen-visualization-selection = Seleção em { $arg0 }: partículas ciano marcam o limite. Só você as vê.
async-regen-fawe-required = O FastAsyncWorldEdit é necessário para ler a seleção
building-map-layer = Construções
building-map-open = Ver construção
building-map-builders = Construído por { $arg0 }
building-map-original-author = Autor original: { $arg0 }
building-map-type-original = Criação original
building-map-type-derivative = Obra derivada
building-map-type-replica = Réplica
-iron-elevator-block = <yellow><hover:show_item:'minecraft:iron_block'><lang:block.minecraft.iron_block></lang></hover></yellow>
iron-elevator-down = ↓ Agachar
iron-elevator-tip-mm = Alinhe { -iron-elevator-block } na vertical para criar um elevador. Fique sobre o bloco: <green>pule para subir</green> e <aqua>agache para descer</aqua>. Com dois ou mais andares, aparece um <gold>painel de andares</gold>.
iron-elevator-up = ↑ Pular
spawn-beacon-color-command-description = Alternar o ciclo de cores do sinalizador da área inicial
spawn-beacon-color-disabled = Ciclo de cores do sinalizador da área inicial desativado
spawn-beacon-color-enabled = Ciclo de cores do sinalizador da área inicial ativado
spawn-command-description = Retornar à área inicial
spawn-tip-return-mm = Use <aqua><u><click:suggest_command:'/spawn'>/spawn</click></u></aqua> para voltar ao <yellow>ponto de spawn do servidor</yellow>.
spawn-world-missing = O mundo da área inicial não está disponível

## Entertainment · Music

music-album = Álbum: { $arg0 }
music-artist = Artista: { $arg0 }
music-back = Voltar
music-command-description = Música local, online e NBS para Toca-Discos
music-disc-left = Clique esquerdo: pegar o disco
music-disc-right = Clique direito: tocar no Toca-Discos mais próximo
music-duration = Duração: { $arg0 }
music-format = Formato: { $arg0 }
music-jukebox-audible-field-conflict = Outro toca-discos está tocando uma música diferente em uma área claramente audível sobreposta
music-jukebox-tp-hover = Clique para se teletransportar ao Toca-Discos
music-jukebox-unavailable = O Toca-Discos foi removido ou seu chunk não está disponível
music-now-playing-rich = Tocando agora: { $music } no Toca-Discos { $location }
music-online-rate-limited = Muitas solicitações de música. Tente novamente em { $arg0 ->
        [one] { $arg0 } segundo
       *[other] { $arg0 } segundos
    }.
music-original-author = Autor original: { $arg0 }
music-plasmo-voice-unavailable = A música começou, mas você não pode ouvi-la porque o Plasmo Voice não está instalado, conectado ou ativado
music-playback-failed = Falha ao reproduzir "{ $arg0 }". Verifique a fonte ou o arquivo de música e tente novamente.
music-reload-failed = Falha ao recarregar a música: { $arg0 }
music-reload-started = Recarregando arquivos de música e fontes personalizadas...
music-rhythm-animation-offset = Deslocamento da animação { $arg0 } ms
music-rhythm-calibration-accumulating = Confirmando ritmo estável… continue acompanhando
music-rhythm-calibration-adapting = Ajustando ao ritmo… continue acompanhando
music-rhythm-calibration-done = Teste de latência concluído · configurações salvas
music-rhythm-calibration-failed = O áudio do teste foi interrompido. O teste foi encerrado com segurança; inicie novamente
music-rhythm-calibration-intro = Minecraft Note Block → Plasmo Voice → Teclado visual → Mouse visual
music-rhythm-calibration-listen = Ouça uma vez o ritmo gerado · ainda não pressione
music-rhythm-calibration-minecraft-instruction = Repita o ritmo Minecraft Note Block e pressione 1–4
music-rhythm-calibration-no-input = Nenhuma entrada detectada. Verifique as teclas e o volume e continue acompanhando
music-rhythm-calibration-out-of-range = O atraso medido está fora do intervalo aceito. Verifique as configurações de áudio
music-rhythm-calibration-plasmo-instruction = Agora acompanhe: repita o ritmo Plasmo Voice e pressione 1–4 em cada batida
music-rhythm-calibration-plasmo-ready = Teste Minecraft Note Block concluído; primeiro ouça o mesmo ritmo pelo Plasmo Voice
music-rhythm-calibration-pointer-instruction = Clique com o botão esquerdo ou direito quando a nota cruzar a linha.
music-rhythm-calibration-realigning = O áudio foi interrompido. A demonstração será repetida e as amostras concluídas serão mantidas
music-rhythm-calibration-required = Conclua o teste de latência antes de iniciar o jogo de ritmo
music-rhythm-calibration-reset-done = A calibração de latência foi redefinida
music-rhythm-calibration-result-hold = Todos os testes terminaram. Confira o resultado antes de voltar automaticamente
music-rhythm-calibration-stage-minecraft = Bloco musical do Minecraft
music-rhythm-calibration-stage-plasmo = Teste de áudio Plasmo Voice e entrada
music-rhythm-calibration-stage-pointer = Mouse
music-rhythm-calibration-stage-visual = Teste visual e de entrada
music-rhythm-calibration-visual-instruction = Pressione 1–4 quando a nota cruzar a linha.
music-rhythm-calibration-visual-preview = Observe uma sequência completa sem entrada · comece quando a instrução mudar
music-rhythm-calibration-voice-required = O teste completo requer o Plasmo Voice conectado e ativado
music-rhythm-chart-preparing = Preparando o mapa completo…
music-rhythm-combo = Combo ×{ $arg0 }
music-rhythm-difficulty-easy = Fácil
music-rhythm-difficulty-expert = Especialista
music-rhythm-difficulty-hard = Difícil
music-rhythm-difficulty-normal = Normal
music-rhythm-exit = Sair do jogo de ritmo
music-rhythm-get-ready = Prepare-se
music-rhythm-go = VAI!
music-rhythm-good = Bom
music-rhythm-great = Ótimo
music-rhythm-latency = Latência { $arg0 } ms
music-rhythm-latency-test = Teste completo de latência
music-rhythm-miss = Errou
music-rhythm-mode-falling = Notas em queda
music-rhythm-mode-radial = Ataque radial
music-rhythm-mode-spatial-aim = Mira espacial
music-rhythm-network-too-high = A latência de rede está alta demais para um julgamento justo ({ $arg0 } ms; limite { $arg1 } ms)
music-rhythm-network-warning = A latência de rede está instável ({ $arg0 } ms). A sessão terminará se continuar acima de { $arg1 } ms
music-rhythm-no-beats = Nenhuma batida jogável foi detectada nesta música
music-rhythm-perfect = Perfeito
music-rhythm-requires-playback = Primeiro toque uma música neste toca-discos
music-rhythm-results = Resultados
music-rhythm-score = Pontuação: { $arg0 }
music-rhythm-spatial-no-room = Não há espaço livre suficiente. Vá para uma área mais aberta.
music-rhythm-spatial-slider-hint = Siga a estrela em movimento e clique em cada ponto · não segure
music-rhythm-start = Iniciar partida
music-sample-rate = Taxa de amostragem: { $arg0 }
music-size = Tamanho: { $arg0 }
music-unavailable-disc = Disco indisponível

## Entertainment · Anniversary

anniversary-accepted = Você entrou no sorteio. A primeira rodada libera após 4 horas online sem AFK.
anniversary-admin-audit-action-bonus = Concedeu oportunidade bônus por construção
anniversary-admin-audit-action-gift = Presenteou prêmio do evento: { $arg0 }
anniversary-admin-audit-action-removal = Participação anterior de administrador removida; devolução ao estoque: { $arg0 ->
        [one] { $arg0 } prêmio
       *[other] { $arg0 } prêmios
    }.
anniversary-admin-audit-action-unknown = Ação desconhecida: { $arg0 }
anniversary-admin-audit-empty = Ainda não há registros de auditoria de gestão.
anniversary-admin-audit-entry = [{ $arg0 }] { $arg1 }
anniversary-admin-audit-identities = Autor: { $arg0 } ({ $arg1 }); alvo: { $arg2 } ({ $arg3 })
anniversary-admin-audit-title = Auditoria de gestão do aniversário (mostrando { $arg0 }/{ $arg1 })
anniversary-admin-bonus-cancelled = Encerrado pelo pacote de presentes
anniversary-admin-bonus-pending = Concedido, ainda não sorteado
anniversary-admin-bonus-used = Sorteado
anniversary-admin-boss = Gestão do aniversário · Use { $arg0 } para ver o progresso
anniversary-admin-commands = Comandos: /anniversary winners [página] | /anniversary audit [jogador] | /anniversary bonus <jogador>
anniversary-admin-control = Controle dinâmico: liberado { $arg0 }; disponível agora { $arg1 }; probabilidade base { $arg2 }; ritmo recente { $arg3 }
anniversary-admin-draw-bonus = Bônus por construção
anniversary-admin-draw-detail = { $arg0 }: sorteado em { $arg1 }; resultado { $arg2 }
anniversary-admin-draw-metrics = Probabilidade { $arg0 }; valor aleatório { $arg1 }; versão do algoritmo { $arg2 }
anniversary-admin-draw-replaced = Substituído pelo pacote; estoque restaurado
anniversary-admin-draw-round = Rodada normal { $arg0 }
anniversary-admin-event = Estado do evento: { $arg0 }; período: { $arg1 }
anniversary-admin-excluded = Administradores do aniversário não podem participar do sorteio. O painel de gestão foi aberto.
anniversary-admin-none = Nenhum
anniversary-admin-opportunities = Sorteios realizados: { $arg0 }; disponíveis agora: { $arg1 }; oportunidades obtidas: { $arg2 }; vitórias/derrotas: { $arg3 }
anniversary-admin-participants = Participantes: { $arg0 }
anniversary-admin-player-bag = Bolsa final do evento: { $arg0 }
anniversary-admin-player-bonus = Bônus de construção: { $arg0 }; concedido: { $arg1 }; por: { $arg2 } ({ $arg3 })
anniversary-admin-player-history = Detalhes das oportunidades de sorteio
anniversary-admin-player-not-found = Participante do evento não encontrado: { $arg0 }
anniversary-admin-player-outcome = Vitórias: { $arg0 }; derrotas: { $arg1 }; sorteios realizados: { $arg2 }; bônus de construção: { $arg3 }
anniversary-admin-player-progress = Participou em: { $arg0 }; tempo online válido: { $arg1 }; sorteios normais realizados: { $arg2 }; sorteios normais disponíveis: { $arg3 }
anniversary-admin-player-title = Auditoria do participante: { $arg0 } ({ $arg1 })
anniversary-admin-state-active = Ativo
anniversary-admin-state-ended = Encerrado
anniversary-admin-state-scheduled = Agendado
anniversary-admin-stock = Estoque restante: { $arg0 } | { $arg1 } | { $arg2 } | { $arg3 } | total { $arg4 }
anniversary-admin-target-excluded = { $arg0 } é administrador do aniversário e não pode receber oportunidades de sorteio.
anniversary-admin-title = Visão geral da gestão do quinto aniversário
anniversary-admin-winners-empty = Ainda não há prêmio obtido em sorteio realizado.
anniversary-admin-winners-entry = { $arg0 } ({ $arg1 }) · { $arg2 } · sorteado em { $arg3 }
anniversary-admin-winners-title = Lista de entrega de prêmios (página { $arg0 }/{ $arg1 }, { $arg2 ->
        [one] { $arg2 } prêmio
       *[other] { $arg2 } prêmios
    })
anniversary-bag-empty = Ainda não há prêmios ganhos
anniversary-bag-item-locked = A bolsa do evento é somente leitura
anniversary-bag-item-name = Prêmio de cinco anos · { $arg0 }
anniversary-bag-not-participating = Você ainda não participa do evento. Use { $arg0 } primeiro.
anniversary-bag-title = Bolsa do evento de cinco anos
anniversary-bag-updated = Seu prêmio está na bolsa do evento. Use { $arg0 } para vê-la.
anniversary-bonus-already-granted = { $arg0 } já recebeu o sorteio adicional por construções e não pode recebê-lo novamente.
anniversary-bonus-boss-ready = Sorteio bônus de construções pronto · Use { $arg0 } para sortear
anniversary-bonus-broadcast-won = [Sorteio de aniversário] { $arg0 } ganhou { $arg1 } com o bônus de construções!
anniversary-bonus-draw-not-won = Sorteio bônus de construções: nenhum prêmio desta vez.
anniversary-bonus-draw-sold-out = Sorteio bônus de construções: nenhum prêmio, pois todos já foram sorteados.
anniversary-bonus-draw-won = Sorteio bônus de construções: parabéns, você ganhou { $arg0 }!
anniversary-bonus-gift-pack-final = { $arg0 } já ganhou o pacote de presentes e não pode receber um sorteio bônus.
anniversary-bonus-grant-not-active = O sorteio adicional por construções não pode ser concedido fora do período do evento.
anniversary-bonus-grant-success = Sorteio adicional por construções concedido a { $arg0 }.
anniversary-bonus-granted = Você recebeu um sorteio adicional pelas construções aceitas. Use /anniversary para sortear.
anniversary-bonus-participant-not-found = Nenhum participante chamado { $arg0 } foi encontrado
anniversary-bonus-status-ready = Bônus de construções: um sorteio adicional está pronto.
anniversary-bonus-status-used = Bônus de construções: o sorteio adicional foi usado.
anniversary-boss-completed = Sorteio de aniversário · Duas rodadas concluídas
anniversary-boss-gift-pack-final = Pacote de presentes ganho · Use { $arg0 } para ver a bolsa do evento
anniversary-boss-invite = Sorteio de cinco anos · Use { $arg0 } para participar
anniversary-boss-progress = Sorteio de aniversário { $arg0 }/{ $arg1 } · Rodada { $arg2 } em { $arg3 }
anniversary-boss-ready = Rodada { $arg0 } pronta · Use { $arg1 } para sortear
anniversary-broadcast-won = [Sorteio de aniversário] { $arg0 } ganhou { $arg2 } na rodada { $arg1 }!
anniversary-cancel = Agora não
anniversary-command-description = Participar do sorteio de cinco anos do servidor
anniversary-confirm = Concordar e participar
anniversary-confirm-hint = Começa a contar o tempo online sem AFK
anniversary-delivery-note = Para um destino na China continental, forneça um SF ID válido compatível com envio com proteção de privacidade. Para um destino fora da China continental, forneça o nome real do destinatário, o endereço completo e um telefone válido. Pague a parte devida do frete, ou o prêmio será perdido.
anniversary-dialog-prizes = Prêmios: 20 { $arg0 }, 10 { $arg1 }, 20 { $arg2 } e 5 { $arg3 }
anniversary-dialog-time = Período do evento: { $arg0 }
anniversary-dialog-title = Regras do sorteio de cinco anos
anniversary-draw-not-won = Rodada { $arg0 }: nenhum prêmio desta vez.
anniversary-draw-sold-out = Rodada { $arg0 }: nenhum prêmio, pois todos já foram sorteados.
anniversary-draw-won = Rodada { $arg0 }: parabéns, você ganhou { $arg1 }!
anniversary-ended = O sorteio de cinco anos terminou
anniversary-ended-results = Seus resultados: { $arg0 }
anniversary-gift-pack-final = O pacote de presentes é seu prêmio final. Ele substituiu e devolveu ao estoque { $arg0 ->
        [one] { $arg0 } outro prêmio
       *[other] { $arg0 } outros prêmios
    }; todos os sorteios restantes foram encerrados.
anniversary-gift-prize-not-owned = Você não possui este prêmio do evento: { $arg0 }
anniversary-gift-received = { $arg0 } presenteou você com { $arg1 }. O prêmio está na sua bolsa do evento.
anniversary-gift-self = Você não pode presentear a si mesmo.
anniversary-gift-success = Você presenteou { $arg0 } para { $arg1 }.
anniversary-gift-usage = Uso: { $arg0 }
anniversary-not-active = O evento de cinco anos não está ativo agora
anniversary-not-started = O evento de cinco anos ainda não começou. Período: { $arg0 }
anniversary-prize-badge = broche com foto do grupo de cinco anos
anniversary-prize-gift-pack = pacote de presentes de cinco anos
anniversary-prize-keychain = chaveiro com o logo
anniversary-prize-mug = caneca com o logo
anniversary-prize-not-won = sem prêmio
anniversary-prize-sold-out = prêmios esgotados
anniversary-rule-1 = 1. Após participar, você pode sortear a cada 4 horas, duas vezes. Os prêmios vão para a bolsa virtual do evento, no máximo um de cada tipo. O pacote de presentes fica como único prêmio e encerra os sorteios restantes.
anniversary-rule-2 = 2. O tempo AFK não conta para liberar os sorteios. É proibido burlar a detecção de AFK por qualquer meio.
anniversary-rule-3 = 3. O servidor cobre 80% do custo total de frete dos prêmios físicos, limitado a CNY 50; o ganhador paga integralmente o saldo restante após a dedução dessa contribuição.
anniversary-rule-4 = 4. Para destinos na China continental, é preciso fornecer um SF ID válido compatível com envio com proteção de privacidade. Para destinos fora da China continental, é preciso fornecer o nome real do destinatário, o endereço completo e um telefone válido. Não fornecer os dados exigidos ou não pagar a parte devida do frete implica a perda do prêmio.
anniversary-rule-5 = 5. Participações repetidas com contas alternativas são proibidas e causam desclassificação.
anniversary-rule-6 = 6. Ao participar, você concorda com todas as regras acima.
anniversary-rule-7 = 7. Construções aceitas no site valem 0,2 para republicações, 0,5 para adaptações e 1 para originais; as aceitas antes do evento são multiplicadas por 0,8, e construções coletivas por 1,5 vezes sua porcentagem de contribuição. Um total de pelo menos 2 concede um sorteio adicional, entregue manualmente por um administrador.
anniversary-second-round-hint = A segunda rodada libera após 8 horas totais online sem AFK.
anniversary-status-completed = Você concluiu as duas rodadas.
anniversary-status-gift-pack-final = Você ganhou o pacote de presentes. Seu prêmio final está definido e todos os sorteios restantes foram encerrados.
anniversary-status-progress = Concluídas: { $arg0 }/{ $arg1 } rodadas; tempo online válido: { $arg2 }
anniversary-status-remaining = Faltam { $arg1 } online sem AFK para a rodada { $arg0 }.
anniversary-status-results = Resultados: { $arg0 }
anniversary-status-title = Progresso do sorteio de aniversário

## Server · Admission, bans, safety and performance

ban-already-banned = { $arg0 } já está banido
ban-broadcast-operator = { role-moderator }
ban-broadcast-prefix = [Boletim de banimento]
ban-command-description = Banir um jogador com um nível de gravidade fixo
ban-database-error = Falha na operação do banco de banimentos. Consulte o log do servidor.
ban-menu-back = Voltar
ban-menu-confirm-action = Banir jogador
ban-menu-confirm-severity = Gravidade
ban-menu-confirm-title = Confirmar banimento
ban-menu-never-joined-confirm-action = Banir mesmo assim
ban-menu-never-joined-title = O jogador nunca entrou
ban-menu-never-joined-warning = { $arg0 } nunca entrou neste servidor. Verifique o nome para evitar banir o jogador errado.
ban-menu-player-input = Digitar nome manualmente
ban-menu-select-title = Selecionar jogador
ban-menu-severity-title = Selecionar gravidade
ban-history-empty = Não há histórico de banimentos de { $arg0 }.
ban-history-title = Histórico de banimentos: { $arg0 }
ban-invalid-player = Nome de jogador inválido
ban-invalid-severity = Gravidade de banimento inválida
ban-kick-appeal = Se achar que foi um erro, recorra no grupo QQ 1138627074
ban-kick-server = MIK
ban-kick-title = Você foi banido deste servidor
ban-not-banned = { $arg0 } não está banido no momento.
ban-pardon-reason-required = É necessário informar um motivo para o perdão
ban-permanent = Permanente
ban-reason-required = É necessário informar um motivo para o banimento
ban-self = Você não pode banir a si mesmo
ban-severity-excusable = Justificável (30 dias)
ban-severity-extreme = Infração extrema (1500 dias)
ban-severity-minor = Infração leve (150 dias)
ban-severity-severe = Infração grave (360 dias)
ban-severity-warning = Advertência (3 dias)
ban-status-active = Ativo
ban-status-expired = Expirado
ban-status-revoked = Revogado
ban-success = Banimento aplicado a { $arg0 } até { $arg1 }.
ban-sync-success = Banimentos sincronizados: ativos { $arg0 } · registros históricos { $arg1 }
ban-system-source = Sistema
ban-unban-success = Banimento de { $arg0 } revogado
ban-unknown-reason = Nenhum motivo informado
ban-update-success = Campo de banimento de { $arg0 } atualizado: { $arg1 }
banlist-command-description = Ver banimentos de jogadores
banlist-copy = Clique para copiar o nome
banlist-empty = Não há jogadores banidos
banlist-expires = Expira
banlist-invalid-page = Página inválida. Total: { $arg0 ->
        [one] { $arg0 } página
       *[other] { $arg0 } páginas
    }.
banlist-next = Próxima
banlist-page = Página { $arg0 }/{ $arg1 } · { $arg2 ->
        [one] { $arg2 } registro
       *[other] { $arg2 } registros
    }
banlist-player = Jogador
banlist-previous = Anterior
banlist-reason = Motivo
banlist-source = { role-moderator }
banlist-title = Lista de banimentos
maintenance-command-description = Ativar, desativar ou consultar o modo de manutenção
maintenance-kick = O servidor está em manutenção
maintenance-set = Modo de manutenção { $arg0 }
maintenance-state-disabled = desativado
maintenance-state-enabled = ativado
maintenance-status = Modo de manutenção: { $arg0 }
maintenance-unchanged = O modo de manutenção já está { $arg0 }
performance-kick-title = O servidor está com atraso severo
performance-kick-reason = Você foi desconectado temporariamente para ajudar o servidor a se recuperar
restriction-entity-too-large-mm = <red>O tamanho da entidade excede o limite de segurança do servidor</red>
restriction-foreign-uuid-mm = <red>Você só pode usar UUIDs de criaturas que domesticou</red>
restriction-no-kill-e-mm = <red>O comando <white>/kill @e</white> não é permitido</red>
restriction-no-newline-mm = <red>Mensagens não podem conter quebras de linha</red>
restriction-no-run-command-mm = <red>Mensagens não podem induzir jogadores a executar comandos</red>
restriction-no-selector-mm = <red>Você não tem permissão para usar os seletores de alvo <white>@e @a @r @p @n</white></red>
restriction-other-player-name-mm = <red>Você não tem permissão para mencionar nomes de outros jogadores em comandos</red>
tpsbar-command-description = Mostrar ou ocultar a barra de TPS
tpsbar-hidden = Barra de TPS oculta
tpsbar-shown = Barra de TPS exibida
tpsbar-tip-status-mm = Use <green><u><click:suggest_command:'/tpsbar'>/tpsbar</click></u></green> para mostrar ou ocultar a barra de <yellow>TPS, MSPT e ping</yellow>.
unban-command-description = Desbanir jogador mantendo o histórico
whitelist-already-whitelisted = { $arg0 } já está na lista branca
whitelist-invalid-name = Formato de nome de jogador inválido
whitelist-kick-apply-rich = Solicite acesso à lista branca em { $site }
whitelist-kick-title = Você não está na lista branca
whitelist-kick-website-rich = { main-website }: { $site }
whitelist-temp-added-plain = Entrada temporária na lista branca adicionada para { $arg0 } por { $arg1 }
whitelist-temp-added-rich = Entrada temporária na lista branca adicionada para { $player } por { $duration }
whitelist-temp-command-description = Adicionar uma entrada temporária de 1 hora à lista branca
whitelist-temp-duration = 1 hora
whitelist-temp-usage-desc = Adicionar uma entrada temporária de 1 hora à lista branca

## Shell · Menus, server information and scripting

main-afk-status =
    { $arg0 ->
        [afk] AFK: Ausente
       *[online] AFK: Online
    }
main-pvp-status =
    { $arg0 ->
        [on] PVP: Ativado
       *[off] PVP: Desativado
    }
main-announcements = Anúncios
main-home = Casas
main-language = Idioma
main-language-auto = Automático
main-links = Links
main-map = Mapa web
main-menu-title = Menu principal
main-music = Música
main-name-tag = Etiquetas de nome
main-settings = Configurações
main-website = Site
main-wiki = Wiki
skript-command-description = Ver o suporte a scripts do servidor
skript-info-ai-option = Não quer escrever? Entregue este prompt a uma IA:
skript-info-copy = [Copiar prompt de IA]
skript-info-copy-hover = Copiar um prompt com versões, links da documentação e regras de segurança
skript-info-documentation = Documentação de sintaxe e API
skript-info-review = Os scripts só são carregados após revisão e testes por um administrador
skript-info-supported = Disponível: { $arg0 }
skript-info-title = Scripts do servidor
skript-info-workflow = Escreva e revise um arquivo .sk completo com a documentação e envie-o a um administrador
stat-client-version = Versão do cliente
stat-first-joined = Primeira entrada
stat-location = Localização
stat-ping = Latência
stat-ping-value = { $arg0 } ms
stat-play-time = Tempo de jogo
stat-role = Cargo
stat-world = Mundo
web-account-title = Conta web e chaves de acesso
web-account-confirm = Confirmar
weblogin-question = Confirmar esta verificação de identidade web? Confirme apenas ações que você iniciou em mcmik.top. Nunca digite um código para outra pessoa.
weblogin-command-description = Confirmar um código de verificação de identidade web do Mik
weblogin-confirmed = Identidade web verificada. Volte ao navegador para continuar.
weblogin-invalid-code = Código de verificação de identidade web inválido


# Music workspace
music-space-label =
    { $arg0 ->
        [title] Espaço musical
        [devices] Dispositivos
        [all-devices] Todos os dispositivos
        [use-device] Usar este dispositivo
        [current-device] Dispositivo atual
        [manage-device] Gerenciar
        [back-devices] Voltar aos dispositivos
        [group-with-devices] Agrupar com outros dispositivos
        [pick-world-member] Escolher um dispositivo no mundo
        [group-world-pick-hint] Agache e clique com o botão direito numa jukebox com a mão vazia para adicioná-la; /music volta ao menu.
        [group-member-standalone] Escolha uma jukebox que ainda não pertença a um grupo de sincronização.
        [group-selection-help] Selecione as jukeboxes abaixo ou clique com o botão direito em outra enquanto se agacha com a mão vazia. O grupo é criado ao confirmar.
        [group-source-help] Usa a fila e a posição do dispositivo escolhido. Criar interrompe a reprodução; Criar e reproduzir a retoma.
        [device-actions] Ações
        [device-status] Estado
        [sync-group] Grupo sincronizado
        [single-device] Jukebox
        [device-location] Localização
        [visit-device] Teleportar para perto
        [teleporting] Teleportando…
        [recorded-device] Salvo
        [nearby-device] Por perto
        [group-select-members] Selecione pelo menos duas jukeboxes
        [group-name-required] Digite o nome do grupo
        [direction-front] À frente
        [direction-front-right] À frente à direita
        [direction-right] À direita
        [direction-back-right] Atrás à direita
        [direction-back] Atrás
        [direction-back-left] Atrás à esquerda
        [direction-left] À esquerda
        [direction-front-left] À frente à esquerda
        [direction-here] Aqui
        [direction-above] Acima
        [direction-below] Abaixo
        [no-devices] Nenhum dispositivo
        [choose-members] Escolher dispositivos do grupo
        [create-group] Criar grupo sincronizado
        [add-members] Adicionar dispositivos
        [create-play] Criar e reproduzir
        [create-and-append] Criar e adicionar músicas ao fim
        [device-queue] Fila
        [device-members] Membros
        [available] Disponível
        [play] Reproduzir
        [retry-outputs] Tentar saídas novamente
        [dissolve-group] Desfazer grupo
        [remove-member] Remover do grupo
        [removed-stopped] Os dispositivos removidos param e mantêm o conteúdo escolhido.
        [restore-previous] Restaurar conteúdo anterior ao grupo
        [keep-group-content] Manter conteúdo do grupo
        [queue] Fila de reprodução
        [back] { ban-menu-back }
        [close] { close }
        [unavailable] Music storage is unavailable
        [saving] Salvando…
        [saved] Salvo
        [apply] { plot-menu-submit }
        [cancel] { cancel }
        [select-page] Select this page
        [select-all] Select all results
        [clear-tracks] Clear song selection
        [add-playlist] Adicionar
        [description] { plot-menu-description }
        [make-private] Make private
        [publish] Make public
        [delete] Delete playlist
        [favorite] Toggle favorite
        [copy] Copy playlist
        [share] Share playlist command
        [recent] Tocadas recentemente
        [favorites] Favorite playlists
        [public] Pública
        [private] Privada
        [new-playlist] Criar playlist
        [name] Nome
        [song-title] Música
        [track-count] Faixas
        [visibility] Visibilidade
        [no-playlists] Nenhuma playlist
        [fetching-playlist] Carregando playlist…
        [artist] Artista
        [duration] Duração
        [track-type] Tipo
        [type-online] Online
        [undo] { vehicle-model-undo }
        [remove] Remove selected entries
        [clear-recents] Clear my play history
        [clear-entries] Clear entry selection
        [search-tracks] Buscar músicas, artistas…
        [search-playlists] Buscar playlists…
        [public-playlists] Public playlists
        [searching] Searching…
        [insert] Inserir e reproduzir agora
        [append] Adicionar à fila
        [choose-targets] Escolher saída
        [group-source] Usar fila e progresso de
        [removed-targets] Alguns destinos foram removidos; selecione-os novamente
        [offline] Desconectado
        [device-discovery-hint] Agache e clique com o botão direito em uma jukebox com a mão vazia para abri-la.
        [empty] Sem músicas
        [queue-empty] Não há músicas na fila
        [queue-current] Música atual da fila digital
        [pause] Pause
        [resume] Resume
        [stop] Stop; keep playback list
        [previous] Previous song / restart
        [clear] Clear pending; stop after current song
        [seek] Posição
        [more] Mais
        [volume-control] Volume
        [group-volume] Volume do grupo
        [timeline-help] Rolar ±5 s · Clique direito para inserir o tempo
        [timeline-read-only] Não é possível alterar a posição
        [seek-format] Digite m:ss, h:mm:ss ou segundos
        [record] Salvar para controle remoto
        [forget] Remover meus dispositivos registrados
        [alias] Apelido pessoal do dispositivo
        [sound] Configurações de som
        [rhythm] Salas de ritmo
        [room-players] Jogadores
        [room-player-status] Estado
        [room-preparation] Minha preparação
        [room-ready] Pronto
        [room-cancel-ready] Cancelar preparação
        [room-playing] Em jogo
        [room-score-ready] Partitura pronta
        [room-select-one] Selecione uma música usando a seleção múltipla da biblioteca.
        [room-end-confirmation] Isso encerra a sala para todos os participantes.
        [create-room] Criar sala
        [no-rhythm-devices] Nenhuma jukebox por perto. Primeiro agache e clique com o botão direito em uma jukebox com a mão vazia.
        [calibration] Calibração de latência
        [volume] Volume (0–100)
        [range] Range (8–256 blocks)
        [mode] Modo de reprodução
        [save-queue] Salvar fila como playlist
        [sequential] Sequential
        [repeat-all] Repetir lista
        [repeat-one] Repeat song
        [shuffle] Shuffle without repeats
        [import] Importar playlist
        [link] Link da playlist
        [partial-import] Only part of the playlist was fetched; save the fetched part explicitly
        [import-preview] Preview fetched metadata; no audio was downloaded
        [import-new] Criar e abrir
        [import-merge] Mesclar na playlist
        [stopped] Parado
        [loading] Preparing audio
        [cancel-loading] Cancelar preparação
        [playing] Reproduzindo
        [paused] Pausada
        [finished] Concluída
        [retry-failed] Tentar reprodução novamente
        [select-song] Reproduza as músicas selecionadas ou adicione-as juntas a uma lista.
        [select-toggle] Selecionar esta entrada
        [deselect] Desmarcar esta entrada
        [selected] Selecionada
        [place-before] Mover seleção para antes desta entrada
        [eject] Eject physical record
        [make-disc] Create signed music disc
        [gain] Group volume (0–100)
        [retry-import] Fetch playlist again
        [join-room] Entrar na sala
        [ready] Pronto
        [waiting] Waiting
        [room-mode] Modo de jogo
        [room-difficulty] Dificuldade
        [late-join] Join the running game
        [leave-room] Leave room
        [select-room-track] Usar música selecionada
        [start-room] Iniciar jogo
        [end-room] Encerrar sala
        [base-queue] Base playback list
        [priority-queue] One-time requests
        [falling] Falling lanes
        [radial] Radial approach
        [spatial-aim] Spatial aim
        [easy] Easy
        [normal] Normal
        [hard] Hard
        [expert] Expert
        [waiting-for-player] Waiting for preparation
        [preparing] Preparing audio and chart
        [no-beats] No playable beats
        [catalog] Catálogo
        [my-playlists] Minhas playlists
        [playlist] Playlist
        [output] Saída
        [close-panel] Fechar painel
        [playlist-settings] Ajustes da playlist
        [multi-select] Selecionar
        [move] Mover
        [move-hint] Escolha uma música para colocar a seleção antes dela
        [cancel-move] Cancelar movimento
        [move-end] Colocar no final
        [cancel-selection] Cancelar seleção
        [track-details] Detalhes da música
        [skip-next] Pular para a próxima
        [device-settings] Ajustes dos dispositivos
        [already-added] Já adicionada
        [current-track] Faixa atual
        [shuffle-candidates] Candidatas aleatórias
        [import-by-id] Importar por ID
        [playlist-id] ID da playlist
       *[unknown] Espaço musical
    }

music-space-error =
    { $arg0 ->
        [UNAVAILABLE] Music is unavailable; reopen later
        [INTERNAL] Erro interno na música; peça a um administrador para verificar o registro do servidor
        [NOT_FOUND] The selected item no longer exists
        [FORBIDDEN] Control or edit permission is required
        [CONFLICT] Content changed; refresh and select again
        [LIMIT] The music resource limit was reached
        [INVALID] Check the name, selection or numeric range
        [OFFLINE] Device unavailable: load its chunk or eject its physical record
        [OVERLAP] Audible areas overlap: stop another device, reduce range or create a sync group
        [UNSUPPORTED] This media does not support that action
        [BUSY] Device, request or rhythm room is busy; retry after it finishes
        [NO_SAFE_DESTINATION] Não há um local seguro ao lado desta jukebox
        [TELEPORT_FAILED] O teletransporte falhou ou foi bloqueado
       *[UNKNOWN] Operation failed
    }

music-space-selected-tracks =
    { $arg0 ->
        [one] { $arg0 } música selecionada
       *[other] { $arg0 } músicas selecionadas
    }
music-space-track-count =
    { $arg0 ->
        [one] { $arg0 } faixa
        *[other] { $arg0 } faixas
    }
music-space-device-count =
    { $arg0 ->
        [one] { $arg0 } toca-discos
       *[other] { $arg0 } toca-discos
    }
music-space-playback-target = Reproduzindo em: { $arg0 }
music-space-online-count = Online: { $arg0 }/{ $arg1 } { $arg1 ->
        [one] jukebox
       *[other] jukeboxes
    }
music-space-device-distance = { $arg0 } · { $arg1 } m
music-space-device-height = { $arg0 ->
    [above] { $arg1 } m acima
   *[below] { $arg1 } m abaixo
    }
music-space-device-coordinates = { $arg0 } · X { $arg1 } · Y { $arg2 } · Z { $arg3 }
music-space-sound-value = { $arg0 ->
        [volume] { $arg1 ->
           *[other] { $arg1 }%
        }
       *[range] { $arg1 ->
            [one] { $arg1 } bloco
           *[other] { $arg1 } blocos
        }
    }
plot-permission-music-control = Controlar dispositivos musicais

music-partial-results = Solicitações a fontes com falha: { $arg0 ->
        [one] { $arg0 } solicitação
       *[other] { $arg0 } solicitações
    }; os resultados disponíveis são exibidos

webchat-tab-label = { $arg0 ->
    [1] { player-web-label }
   *[other] { player-web-label } ×{ $arg0 }
    }
webchat-joined = { $arg0 } entrou no chat web
webchat-left = { $arg0 } saiu do chat web
chat-rate-limited = Você está enviando muito rápido. Aguarde um momento.

plot-map-corner-a = Canto A
plot-map-corner-b = Canto B
plot-map-corner-hint = Clique para centralizar este canto. Botão direito: coordenadas; roda: Y ±4.
plot-map-coordinates = Coordenadas x,y,z
plot-map-zoom-in = Ampliar
plot-map-zoom-out = Reduzir
plot-map-fit = Ver área
plot-map-near = Perto de mim
plot-map-layer = Camada Y { $arg0 }…{ $arg1 }
plot-map-layer-hint = Roda: mudar camada; clique: sua camada. O terreno sempre mostra a superfície.
plot-map-north = Norte
plot-map-west = Oeste
plot-map-east = Leste
plot-map-south = Sul
plot-map-scale = 1 pixel = { $arg0 } × { $arg0 } blocos
plot-map-progress = Lendo mapa { $arg0 }%…
plot-map-unknown = Amostras de superfície não carregadas: { $arg0 }
plot-map-legend = Ciano: mantido · Roxo: adicionado · Laranja: removido · Verde: prisma · A/B: cantos · Seta: você
plot-selection-validating = Calculando limites e validando a área em segundo plano… Atualização automática ao concluir.

plot-map-open = Ver mapa do Minecraft

plot-map-handheld-hint = Mapa na mão secundária; selecione com um machado de madeira. Agachar + F volta ao painel.

plot-map-readonly-hint = Apenas visualização do mapa. Volte ao painel e escolha Ajustar prisma para mudar os cantos.

plot-destinations = Pontos de teletransporte
plot-destination-add = Adicionar local atual
plot-destination-confirm-delete = Excluir o ponto “{ $arg0 }”?
plot-destination-delete = Excluir ponto
plot-destination-deleted = Ponto de teletransporte excluído.
plot-destination-description = Descrição (opcional)
plot-destination-edit = Editar nome e descrição
plot-destination-empty = Nenhum ponto de teletransporte disponível.
plot-destination-error-changed = Este ponto mudou. Abra-o novamente.
plot-destination-error-description = A descrição permite até 512 caracteres e não aceita caracteres de controle.
plot-destination-error-location = Fique em terreno seguro dentro deste terreno.
plot-destination-error-missing = Este ponto não existe mais.
plot-destination-error-name = O nome deve ter de 1 a 48 caracteres em uma linha.
plot-destination-error-private = Este ponto é privado e está disponível apenas para membros do terreno.
plot-destination-error-unsafe = Este ponto está indisponível ou não é seguro.
plot-destination-loading = Carregando ponto…
plot-destination-move = Usar local atual
plot-destination-name = Nome
plot-destination-public-empty = Ainda não há pontos públicos.
plot-destination-public-off = Público: não
plot-destination-public-on = Público: sim
plot-destination-saved = Ponto de teletransporte salvo.
plot-destination-teleport = Teletransportar
plot-error-subplot-destination = Mova ou exclua primeiro os pontos do terreno principal na área selecionada.
plot-error-subplot-own-destination = Mantenha os pontos dentro da nova área ou mova-os ou exclua-os primeiro.

plot-destination-manage = Gerenciar
plot-directory-owned = Meus terrenos · { $arg0 }
plot-directory-participating = Participações · { $arg0 }
plot-directory-search = Buscar terrenos
plot-directory-search-value = Busca: { $arg0 }
plot-directory-search-field = Nome ou ID do terreno
plot-directory-clear-search = Limpar busca
plot-directory-empty = Ainda não há terrenos nesta categoria.
plot-directory-no-match = Nenhum terreno encontrado. Altere ou limpe a busca.
plot-workspace-area = Editar área
plot-workspace-area-hint = Ajustar a área protegida deste terreno
plot-workspace-subplots = Subterrenos
plot-workspace-subplots-hint = Ver, criar e gerenciar subdivisões
plot-workspace-access-hint = Gerenciar membros, funções e permissões de ações
plot-workspace-settings-hint = Nome, aviso, ambiente e propriedade
plot-workspace-destinations-hint = Ver e usar os pontos de teletransporte deste terreno
plot-workspace-role = Sua função: { $arg0 }
plot-workspace-ownership = Propriedade e exclusão
plot-workspace-id = ID do terreno: { $arg0 }
plot-workspace-groups = Grupos de permissões
plot-workspace-permissions = Permissões de ações
plot-workspace-other-world = Vá primeiro ao mundo deste terreno.

plot-destination-titles = Títulos de chegada
plot-destination-title = Título
plot-destination-subtitle = Subtítulo
plot-destination-title-hint = Exibidos após um teletransporte bem-sucedido. Aceitam cores, estilos e gradientes do MiniMessage. Deixe em branco para ocultar; a prévia não salva.
plot-destination-title-preview = Prévia em tela cheia
plot-destination-title-unset = Não definido
plot-destination-title-save = Salvar títulos
plot-destination-title-unchanged = Nenhuma alteração pendente.
plot-destination-title-apply = Aplicar ao rascunho
plot-destination-error-title = O título deve ter uma linha, até 32 caracteres visíveis e 512 caracteres de código MiniMessage.
plot-destination-error-subtitle = O subtítulo deve ter uma linha, até 64 caracteres visíveis e 512 caracteres de código MiniMessage.
