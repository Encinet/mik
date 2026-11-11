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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class IdentityBindingCommandPresenterTest {
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void englishLinkPromptsShowTheJoinPageAndCopyTheDisplayedCommandForEachPlatform() {
        var presenter = new IdentityBindingCommandPresenter(languages(Language.EN_US), mock(IdentityBindingRuntime.class));
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
    void accountRowsShowOnlyTheAccountAndActionWithDetailsOnHoverAndNoFooter() {
        var runtime = mock(IdentityBindingRuntime.class);
        when(runtime.platform("qq")).thenReturn(Optional.of(QqPlatformAdapter.IDENTITY_PLATFORM));
        when(runtime.platform("matrix")).thenReturn(Optional.of(MatrixPlatformAdapter.IDENTITY_PLATFORM));
        var bindings = List.of(
                binding(UUID.randomUUID(), new ExternalIdentityKey("qq", "private-app", "", "private-member"), "Alice", ""),
                binding(UUID.randomUUID(), new ExternalIdentityKey("matrix", "example.org", "", "@alice:example.org"), "Alice", ""));
        for (var language : List.of(Language.EN_US, Language.ZH_CN)) {
            var presenter = new IdentityBindingCommandPresenter(languages(language), runtime);
            var rows = ownRows(presenter, bindings);
            String action = language == Language.EN_US ? "[Unlink]" : "[解绑]";
            assertEquals("QQ · Alice " + action, plain(rows.get(0)));
            assertEquals("Matrix · Alice " + action, plain(rows.get(1)));
            for (int index = 0; index < rows.size(); index++) {
                Component row = rows.get(index);
                var binding = bindings.get(index);
                assertEquals(List.of(ClickEvent.runCommand("/bind unlink " + binding.bindingId())), clicks(row));
                String details = plain(assertInstanceOf(Component.class, row.hoverEvent().value()));
                assertTrue(details.contains(language == Language.EN_US ? "Linked on:" : "绑定时间："), details);
                assertTrue(details.contains(binding.bindingId().toString()), details);
                var runs = IdentityPromptAssertions.runs(row);
                assertTrue(runs.stream().noneMatch(run -> run.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE));
                assertTrue(runs.stream().filter(run -> run.clickEvent() != null).allMatch(run ->
                        run.content().equals(action) && run.color().equals(NamedTextColor.YELLOW)
                                && run.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE));
                assertTrue(runs.stream().anyMatch(run -> run.content().equals("Alice")
                        && run.color().equals(NamedTextColor.WHITE) && run.clickEvent() == null));
                assertTrue(runs.stream().filter(run -> List.of("QQ", "Matrix").contains(run.content()))
                        .allMatch(run -> run.color().equals(NamedTextColor.AQUA)));
            }
        }
    }

    @Test
    void eachRowShowsItsContextAndTargetsExactlyThatBinding() {
        var runtime = mock(IdentityBindingRuntime.class);
        when(runtime.platform("scoped")).thenReturn(Optional.of(new IdentityPlatform("scoped", "Scoped", "/bind {code}")));
        var presenter = new IdentityBindingCommandPresenter(languages(Language.EN_US), runtime);
        var bindings = Stream.of("Builders", "Visitors", "").map(group -> binding(UUID.randomUUID(),
                new ExternalIdentityKey("scoped", "private-app", "private-group-" + group, "private-member"),
                "External Alice", group)).toList();
        var rows = ownRows(presenter, bindings);
        Player admin = mock(Player.class);
        presenter.adminLookup(admin, "Alice", bindings);
        var sent = ArgumentCaptor.forClass(Component.class);
        verify(admin, times(4)).sendMessage(sent.capture());
        for (int index = 0; index < bindings.size(); index++) {
            var binding = bindings.get(index);
            Component ownRow = rows.get(index);
            String visible = plain(ownRow);
            assertTrue(visible.contains(binding.scopeDisplayName().isBlank() ? "Community #" : binding.scopeDisplayName()));
            assertFalse(visible.contains("private-"));
            assertFalse(visible.contains(binding.bindingId().toString()));
            assertEquals(List.of(ClickEvent.runCommand("/bind unlink " + binding.bindingId())), clicks(ownRow));
            assertEquals(List.of(ClickEvent.runCommand("/bindadmin unlink " + binding.bindingId() + " confirm")),
                    clicks(sent.getAllValues().get(index + 1)));
        }
    }

    @Test
    void identicalLabelsReceiveDistinctReferencesSharedWithCommandCompletion() {
        var runtime = mock(IdentityBindingRuntime.class);
        when(runtime.platform("qq")).thenReturn(Optional.of(QqPlatformAdapter.IDENTITY_PLATFORM));
        var presenter = new IdentityBindingCommandPresenter(languages(Language.EN_US), runtime);
        var bindings = List.of(
                binding(UUID.fromString("12345678-aaaa-0000-0000-000000000000"),
                        new ExternalIdentityKey("qq", "private-app-one", "", "private-member"), "", ""),
                binding(UUID.fromString("12345678-bbbb-0000-0000-000000000000"),
                        new ExternalIdentityKey("qq", "private-app-two", "", "private-member"), "", ""));
        var rows = ownRows(presenter, bindings);
        var labels = presenter.selectionLabels(mock(Player.class), bindings);
        assertNotEquals(labels.get(bindings.get(0).bindingId()), labels.get(bindings.get(1).bindingId()));
        for (int index = 0; index < bindings.size(); index++) {
            String label = labels.get(bindings.get(index).bindingId());
            assertTrue(label.startsWith("QQ · Unnamed account · #"), label);
            assertFalse(label.contains("private-"), label);
            assertEquals(label + " [Unlink]", plain(rows.get(index)));
        }
    }

    private static List<Component> ownRows(IdentityBindingCommandPresenter presenter, List<IdentityBinding> bindings) {
        Player player = mock(Player.class);
        presenter.ownBindings(player, bindings);
        var sent = ArgumentCaptor.forClass(Component.class);
        verify(player, times(bindings.size() + 1)).sendMessage(sent.capture());
        return sent.getAllValues().subList(1, bindings.size() + 1);
    }

    private static IdentityBinding binding(UUID id, ExternalIdentityKey key, String name, String community) {
        return new IdentityBinding(id, OWNER, "Alice", key, name, community, Instant.EPOCH, Instant.EPOCH);
    }

    private static LanguageService languages(Language selected) {
        var translations = FluentTestSupport.languages();
        return new LanguageService(null) {
            @Override public Language language(Player player) { return selected; }
            @Override public String t(Language language, Message message, Object... args) {
                return translations.t(language, message, args);
            }
            @Override public Component rich(Language language, Message message, NamedTextColor baseColor,
                                            TextArg[] selectors, RichArg... values) {
                return translations.rich(language, message, baseColor, selectors, values);
            }
        };
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<ClickEvent<?>> clicks(Component component) {
        return Stream.concat(component.clickEvent() == null ? Stream.empty() : Stream.of(component.clickEvent()),
                component.children().stream().flatMap(child -> clicks(child).stream())).toList();
    }
}
