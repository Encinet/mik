package org.encinet.mik.shell.menu;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

import java.util.List;
import java.util.Objects;

/** A read-only profile snapshot with one explicit route to identity details. */
record MainMenuProfile(Component avatar, Component name, Component identity, List<Fact> facts) {
    MainMenuProfile {
        Objects.requireNonNull(avatar); Objects.requireNonNull(name); Objects.requireNonNull(identity);
        facts = List.copyOf(facts);
    }

    record Fact(String key, Component label, Component value) {
        Fact { Objects.requireNonNull(key); Objects.requireNonNull(label); Objects.requireNonNull(value); }
    }

    UiNode content(Component identityHelp, FloatingMenuCommand openIdentity) {
        var person = Ui.column().key("person").gap(UiLength.ui(2))
                .child(Ui.text(name).key("player-name").textWidth(160).summaryLines(1)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch())
                .child(Ui.button(identity.append(Component.text(" ›"))).key("identity").summaryLines(1).role(UiRole.NAVIGATION)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).textWidth(160)
                        .tooltip(identityHelp).primary(openIdentity).build(), LinearSlot.stretch());
        var header = Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(6)), UiLayouts.column(UiLength.ui(4))))
                .key("profile-header")
                .child(Ui.text(avatar).key("avatar").textWidth(8).style(UiStyle.PLAIN.withTextScale(2)).build())
                .child(person.build()).build();
        var details = Ui.layout(UiLayouts.grid(List.of(UiTrack.intrinsic(), UiTrack.content()),
                UiLength.ui(6), UiLength.ui(2))).key("facts");
        for (var fact : facts) {
            details.child(Ui.text(fact.label()).key(fact.key() + ":label").textWidth(128).summaryLines(1)
                    .sizing(UiSizing.width(UiLength.ZERO, UiLength.ui(100)))
                    .style(UiStyle.PLAIN.withTextScale(.9)).alignment(FloatingMenuDecoration.Alignment.LEFT).build());
            details.child(Ui.text(fact.value()).key(fact.key()).textWidth(240).summaryLines(1)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT).build());
        }
        return Ui.column().key("profile-content").gap(UiLength.ui(6))
                .child(header, LinearSlot.stretch())
                .child(details.build(), LinearSlot.stretch()).build();
    }
}
