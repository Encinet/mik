package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Instant;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.*;

/** Shared fixtures for activity behavior and the real main-menu composition. */
public final class GovernanceActivityTestSupport {
    static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    static final UUID VIEWER = new UUID(0, 99);
    static final GovernanceActivityScenes.Actions NO_ACTIONS = new GovernanceActivityScenes.Actions() {
        public void open(GovernanceMatter matter, UiKey source, boolean confirm) { }
        public void cast(GovernanceMatter matter, VoteChoice choice) { }
        public void join(GovernanceMatter matter) { }
        public void all() { }
        public void reload() { }
    };
    private GovernanceActivityTestSupport() { }

    public static UiNode content(Language language, int count) throws Exception {
        return scenes(language, state(count), false, NO_ACTIONS).content();
    }

    public static MenuScene withDetails(MenuScene host, Language language, int count) throws Exception {
        var state = state(count);
        var view = scenes(language,state,false,NO_ACTIONS);
        var matter = state.displayed().getFirst();
        return host.toBuilder().popover(MenuPopover.attached(GovernanceActivityScenes.source(matter),view.proposal(matter),
                view.details(matter,false,UiReadingPosition.START,_ -> {},() -> {}),Component.text("Close"))).build();
    }

    static GovernanceActivityState state(int count) {
        var state = new GovernanceActivityState();
        state.update(new GovernanceActivityData(java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> vote(index + 1, true, index % 2 == 0 ? null : VoteChoice.YES)).toList(), true));
        return state;
    }

    static GovernanceMatter vote(long id, boolean voter, VoteChoice choice) {
        var proposal = new VoteProposal.Appointment(new UUID(0,id), "Long_Player_Name");
        var vote = new GovernanceVote(id, proposal, NOW.minusSeconds(1), NOW.plusSeconds(172_800), VoteStatus.OPEN,
                4096, VotingPolicy.quorum(4096), new VoteTally(1337, 23, 19), null, VoteFailure.NONE, null);
        return GovernanceMatter.vote(new Ballot(vote,voter,choice));
    }

    static GovernanceActivityScenes scenes(Language language, GovernanceActivityState state, boolean busy,
                                           GovernanceActivityScenes.Actions actions) throws Exception {
        return new GovernanceActivityScenes(text(language),language,VIEWER,state,NOW,busy,null,actions);
    }

    static GovernanceText text(Language language) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        return new GovernanceText(new LanguageService(null) {
            @Override public String t(Language selected,Message message,Object... args) { return FluentTestSupport.text(bundle,message,args); }
        });
    }

    static MenuScene scene(UiNode content) { return MenuScene.screen("activity-fixture").content(content).build(); }
}
