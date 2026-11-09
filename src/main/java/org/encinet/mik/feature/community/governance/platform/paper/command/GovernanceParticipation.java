package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.voting.model.VoteChoice;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.menu.ui.*;

/** Equal-weight choices shared by the complete workspace and compact embedded participation. */
final class GovernanceParticipation {
    private GovernanceParticipation() { }

    static UiNode choices(GovernanceText text, Language language, VoteChoice selected,
                          Component disabled, Consumer<VoteChoice> cast) {
        var choices = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(8))).key("ballot");
        for (var choice : VoteChoice.values()) {
            var button = Ui.button(Component.text(text.choice(language, choice), NamedTextColor.WHITE))
                    .key("choice:" + choice).selected(selected == choice).primary((_,_) -> cast.accept(choice));
            if (disabled != null) button.disabled(disabled);
            choices.child(button.build());
        }
        return choices.build();
    }
}
