package org.encinet.mik.feature.community.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.i18n.TextArg;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformAdapter;
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformAdapter;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class IdentityBindingCommandPresenterTest {
    @Test
    void englishLinkPromptsShowTheJoinPageAndCopyTheDisplayedCommandForEachPlatform() {
        var translations = FluentTestSupport.languages();
        var languages = new LanguageService(null) {
            @Override public Language language(Player player) { return Language.EN_US; }
            @Override public String t(Language language, Message message, Object... args) {
                return translations.t(language, message, args);
            }
            @Override public Component rich(Language language, Message message, NamedTextColor baseColor,
                                            TextArg[] selectors, RichArg... values) {
                return translations.rich(language, message, baseColor, selectors, values);
            }
        };
        var presenter = new IdentityBindingCommandPresenter(languages, mock(IdentityBindingRuntime.class));
        for (var platform : List.of(QqPlatformAdapter.IDENTITY_PLATFORM, MatrixPlatformAdapter.IDENTITY_PLATFORM)) {
            Player player = mock(Player.class);
            var issue = new IdentityLinkCode(platform.id(), "000123", Instant.now().plusSeconds(300));
            presenter.codeIssued(player, platform, issue);
            var sent = ArgumentCaptor.forClass(Component.class);
            verify(player, times(3)).sendMessage(sent.capture());
            var rows = sent.getAllValues();
            String command = platform.redemptionInstruction().replace("{code}", issue.code());
            String output = rows.stream().map(PlainTextComponentSerializer.plainText()::serialize)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(output.contains("Join a community: https://mcmik.top/join"), output);
            assertTrue(output.contains(command), output);
            assertTrue(output.endsWith("Code validity: 5 minutes"), output);
            assertFalse(output.contains("/绑定"), output);
            assertTrue(clicks(rows.get(0)).contains(ClickEvent.openUrl("https://mcmik.top/join")));
            assertTrue(clicks(rows.get(1)).contains(ClickEvent.copyToClipboard(command)));
            IdentityPromptAssertions.assertCodeEmphasis(rows.get(1), 1);
            var linkRuns = IdentityPromptAssertions.runs(rows.get(0));
            assertTrue(linkRuns.stream().filter(run -> run.content().contains("Join a community:"))
                    .allMatch(run -> run.color().equals(NamedTextColor.WHITE) && run.clickEvent() == null));
            assertTrue(linkRuns.stream().anyMatch(run -> run.content().equals("https://mcmik.top/join")
                    && run.color().equals(NamedTextColor.AQUA)
                    && run.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE
                    && ClickEvent.openUrl("https://mcmik.top/join").equals(run.clickEvent())));
        }
    }

    @Test
    void eachRowShowsItsContextAndTargetsExactlyThatBinding() {
        var runtime = mock(IdentityBindingRuntime.class);
        when(runtime.platform("scoped")).thenReturn(Optional.of(new IdentityPlatform("scoped", "Scoped", "/bind {code}")));
        var languages = new LanguageService(null) {
            @Override public String t(Language language, Message message, Object... args) {
                return message.name() + " " + Arrays.toString(args);
            }
        };
        var presenter = new IdentityBindingCommandPresenter(languages, runtime);
        UUID owner = UUID.randomUUID();
        for (String group : List.of("Builders", "Visitors")) {
            var binding = new IdentityBinding(UUID.randomUUID(), owner, "Alice",
                    new ExternalIdentityKey("scoped", "private-app", "private-group-" + group, "private-member"),
                    "External Alice", group, Instant.EPOCH, Instant.EPOCH);
            Component ownRow = presenter.bindingLine(binding, Language.DEFAULT, false);
            String visible = PlainTextComponentSerializer.plainText().serialize(ownRow);
            assertTrue(visible.contains(group));
            assertFalse(visible.contains("private-"));
            assertFalse(visible.contains(binding.bindingId().toString()));
            assertEquals(List.of(ClickEvent.runCommand("/bind unlink " + binding.bindingId())), clicks(ownRow));
            assertEquals(List.of(ClickEvent.runCommand("/bindadmin unlink " + binding.bindingId() + " confirm")),
                    clicks(presenter.bindingLine(binding, Language.DEFAULT, true)));
        }
    }

    private static List<ClickEvent<?>> clicks(Component component) {
        return Stream.concat(component.clickEvent() == null ? Stream.empty() : Stream.of(component.clickEvent()),
                component.children().stream().flatMap(child -> clicks(child).stream())).toList();
    }
}
