package org.encinet.mik.feature.community.governance.platform.paper.command;

import net.kyori.adventure.text.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.promotion.PromotionEligibility;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.PlayerRole;

/** Reusable identity progress; the host owns its surface and navigation. */
final class GovernanceIdentityPanel implements MenuPanel {
    private final Player player;
    private final MembershipService membership;
    private final GovernanceCommandContext context;
    private final GovernanceText text;
    private final Runnable redraw;
    private IdentityView view;
    private boolean active = true, pending, failed;
    private long generation;
    private UiReadingPosition position = UiReadingPosition.START;

    GovernanceIdentityPanel(Player player, MembershipService membership, GovernanceCommandContext context,
                            GovernanceText text, Runnable redraw) {
        this.player = player; this.membership = membership; this.context = context; this.text = text; this.redraw = redraw;
        load();
    }

    private void load() {
        if (!active || pending) return;
        pending = true; failed = false;
        long request = ++generation;
        PlayerRole role = PlayerRole.resolve(player);
        var known = context.snapshot(player).withMember(role != PlayerRole.DEFAULT);
        context.submit(player, () -> {
            context.ensureKnown(known);
            var participation = membership.participation(known.playerId());
            var promotion = known.member() ? null : membership.promotionEligibility(known.playerId());
            var eligibility = known.member() ? membership.activeMemberEligibility(known.playerId()) : null;
            var loaded = new IdentityView(role, participation, promotion, eligibility);
            context.onMain(() -> {
                if (!active || request != generation) return;
                if (view == null || view.role() != loaded.role()) position = UiReadingPosition.START;
                view = loaded; pending = false; redraw.run();
            });
        }, () -> {
            if (!active || request != generation) return;
            pending = false; failed = true; redraw.run();
        });
    }

    @Override public MenuScene render() {
        if (!active) return MenuScene.screen("governance-identity-closed").content(Ui.column().build()).build();
        IdentityView rendered = view;
        return definition(text, text.language(player), rendered, failed,
                position, selected -> { if (active && view == rendered) { position = selected; redraw.run(); } });
    }

    @Override public UiNode headerActions() {
        return active ? refresh(text, text.language(player), pending, () -> { if (active) { load(); redraw.run(); } }) : null;
    }

    @Override public void close() { active = false; generation++; }

    static MenuScene definition(GovernanceText text, Language language, IdentityView view,
                                boolean failed, UiReadingPosition position, Consumer<UiReadingPosition> selectPosition) {
        var content = Ui.layout(UiLayouts.contentColumn(UiKey.named("identity-progress"),UiLength.ui(8))).key("identity-detail");
        var items = new ArrayList<UiNode>();
        if (failed) items.add(Ui.text(label(text, language, Message.GOVERNANCE_OPERATION_ERROR, NamedTextColor.RED)).key("error").build());
        if (view == null) {
            if (!failed) items.add(Ui.text(label(text, language, Message.GOVERNANCE_MENU_LOADING, NamedTextColor.YELLOW)).key("loading").build());
        } else {
            content.child(Ui.text(label(text, language, view.role().label(), NamedTextColor.AQUA)).key("current-role").build());
            var details = identityDetails(text,language,view);
            for (int index=0; index<details.size(); index++) items.add(Ui.text(details.get(index)).key("status:"+index).textWidth(240)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT).build());
            if (view.promotion() != null && view.promotion().activePause() != null) {
                var pause = view.promotion().activePause();
                items.add(pauseField(text,language,"pause-until",Message.GOVERNANCE_MENU_PAUSE_UNTIL,text.time(language,pause.endsAt())));
                items.add(pauseField(text,language,"pause-reason",Message.GOVERNANCE_MENU_PAUSE_REASON,pause.reason()));
                items.add(pauseField(text,language,"pause-appeal",Message.GOVERNANCE_MENU_PAUSE_APPEAL,pause.appealPath()));
            }
        }
        var reading = Ui.document().key("identity-progress").blocks(items).position(position, selectPosition).build();
        return MenuScene.screen("governance-identity", label(text, language, Message.GOVERNANCE_MENU_IDENTITY, NamedTextColor.GOLD))
                .content(UiTemplates.focus(content.child(reading).build(), reading.key())).build();
    }

    private static UiNode refresh(GovernanceText text, Language language, boolean pending, Runnable reload) {
        var button = Ui.button(pending ? UiIcons.LOADING : UiIcons.REFRESH,
                label(text, language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.WHITE))
                .key("refresh").primary((_,_) -> reload.run());
        if (pending) button.disabled(label(text, language, Message.GOVERNANCE_MENU_LOADING, NamedTextColor.YELLOW));
        return button.build();
    }

    private static List<Component> identityDetails(GovernanceText text, Language language, IdentityView view) {
        var status = new ArrayList<Component>();
        if (view.promotion() != null) {
            var check = view.promotion();
            status.add(text.line(language,check.accountAgeMet(),Message.GOVERNANCE_PROMOTION_ACCOUNT_AGE));
            status.add(text.line(language,check.playtimeMet(),Message.GOVERNANCE_PROMOTION_PLAYTIME,
                    text.hours(language,view.participation().lifetimeSeconds())));
            status.add(text.line(language,check.playDaysMet(),Message.GOVERNANCE_PROMOTION_PLAY_DAYS,view.participation().lifetimePlayDays()));
            status.add(text.line(language,check.noActiveBan(),Message.GOVERNANCE_NO_ACTIVE_BAN));
            status.add(text.line(language,check.noActivePause(),Message.GOVERNANCE_NO_ACTIVE_PAUSE));
        } else {
            GovernanceQualifications.active(text, language, view.active(), view.participation())
                    .forEach(requirement -> status.add(requirement.text()));
        }
        return List.copyOf(status);
    }

    private static UiNode pauseField(GovernanceText text, Language language, String key, Message heading, String value) {
        return Ui.text(label(text,language,heading,NamedTextColor.AQUA).append(Component.newline())
                        .append(Component.text(value,NamedTextColor.YELLOW))).key(key).textWidth(240)
                .alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }

    private static Component label(GovernanceText text, Language language, Message message, NamedTextColor color) {
        return Component.text(text.t(language, message), color);
    }

    record IdentityView(PlayerRole role, ParticipationSummary participation,
                        PromotionEligibility promotion, ActiveMemberEligibility active) { }
}
