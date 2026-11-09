package org.encinet.mik.feature.community.governance.platform.paper.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiLength;
import java.util.UUID;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerState.*;

/** Stateless player-report presentation; the owner supplies request-checked commands and reading state. */
final class GovernancePlayerScenes {
    enum Command { PAUSE, RESUME, PASSWORD, REFRESH }
    private final GovernanceText text;
    private final GovernancePlayerReport reports;
    GovernancePlayerScenes(GovernanceText text) { this.text = text; reports = new GovernancePlayerReport(text); }

    MenuScene render(Language language,GovernancePlayerState state,java.util.function.Consumer<UiReadingPosition> read,
                     java.util.function.BiConsumer<Player,Command> actions) {
        return switch (state) {
            case Loading loading -> loading(language,loading.target());
            case Failed failed -> failure(language,failed,actions);
            case Loaded loaded -> report(language,loaded,read,actions);
        };
    }

    private MenuScene report(Language language,Loaded source,java.util.function.Consumer<UiReadingPosition> read,
                             java.util.function.BiConsumer<Player,Command> actions) {
        UUID targetId = source.target();
        var promotion = source.data().eligibility() instanceof GovernancePlayerReport.Eligibility.Promotion value ? value.value() : null;
        var blocks = reports.blocks(language,source.data(),true).stream().map(block -> Ui.text(block.text()).key(block.key())
                .textWidth(4096).alignment(FloatingMenuDecoration.Alignment.LEFT).keepAccessible().build()).toList();
        var reading = Ui.document().key("report").blocks(blocks).gap(UiLength.ui(4)).readingBody().position(source.position(),read).build();
        MenuScene.Builder menu = MenuScene.screen("governance-player")
                .appearance(FloatingMenuAppearance.DEFAULT)
                .stableAnchor()
                .framing(FloatingMenuFraming.COMFORTABLE)
                .content(GovernanceMenuComposition.player(targetId,reading));
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_PLAYERS,
                NamedTextColor.AQUA)).region("heading").keepAccessible();
        if (promotion != null) {
            if (promotion.activePause() == null) {
                menu.control("pause",
                                label(language, Message.GOVERNANCE_MENU_PAUSE, NamedTextColor.YELLOW))
                        .region("actions")
                        .primary((viewer, _) -> { actions.accept(viewer,Command.PAUSE); });
            } else {
                menu.control("resume",
                                label(language, Message.GOVERNANCE_MENU_RESUME, NamedTextColor.GREEN))
                        .region("actions")
                        .primary((viewer, _) -> { actions.accept(viewer,Command.RESUME); });
            }
        }
        menu.control("password-security", label(language, Message.PASSWORD_TITLE, NamedTextColor.YELLOW))
                .region("actions").primary((viewer, _) -> {
                    actions.accept(viewer,Command.PASSWORD);
                });
        menu.control("refresh",
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.AQUA))
                .region("navigation").keepAccessible().primary((viewer, _) -> { actions.accept(viewer,Command.REFRESH); });
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private MenuScene loading(Language language,UUID targetId) {
        MenuScene.Builder menu = MenuScene.screen("governance-player")
                .loading(true)
                .appearance(FloatingMenuAppearance.DEFAULT)
                .stableAnchor()
                .framing(FloatingMenuFraming.COMFORTABLE)
                .content(GovernanceMenuComposition.status(targetId));
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_PLAYERS,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("status", label(language, Message.GOVERNANCE_MENU_LOADING,
                NamedTextColor.WHITE)).region("status").keepAccessible();
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private MenuScene failure(Language language,Failed source,java.util.function.BiConsumer<Player,Command> actions) {
        UUID targetId = source.target(); Message reason = source.reason();
        MenuScene.Builder menu = MenuScene.screen("governance-player")
                .appearance(FloatingMenuAppearance.DEFAULT)
                .stableAnchor()
                .framing(FloatingMenuFraming.COMFORTABLE)
                .content(GovernanceMenuComposition.status(targetId));
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_PLAYERS,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("error", label(language, reason, NamedTextColor.RED))
                .region("status").keepAccessible();
        menu.control("refresh",
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.AQUA))
                .region("navigation").keepAccessible().primary((viewer, _) -> { actions.accept(viewer,Command.REFRESH); });
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private Component label(Language language,Message message,NamedTextColor color) {
        return Component.text(text.t(language,message),color);
    }
}
