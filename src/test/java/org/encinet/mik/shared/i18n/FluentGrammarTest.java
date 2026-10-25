package org.encinet.mik.shared.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluentGrammarTest {
    private static final Pattern ENTRY = Pattern.compile(
            "(?m)^([a-z][a-z0-9-]*)\\s*=([^\\n]*(?:\\n[ \\t]+[^\\n]*)*)");
    private static final Pattern SELECTOR = Pattern.compile("\\{\\s*\\$(\\w+)\\s*->");
    private static final Pattern VARIABLE = Pattern.compile("\\$(\\w+)");
    private final Map<Language, FluentBundle> bundles = new EnumMap<>(Language.class);

    @Test
    void englishUsesOneAndOtherIncludingZero() throws IOException {
        assertEquals("0 seconds", text(Language.EN_US, Message.GOVERNANCE_SECONDS, 0));
        assertEquals("1 second", text(Language.EN_US, Message.GOVERNANCE_SECONDS, 1));
        assertEquals("2 seconds", text(Language.EN_US, Message.GOVERNANCE_SECONDS, 2));
        assertEquals("21 seconds", text(Language.EN_US, Message.GOVERNANCE_SECONDS, 21));
    }

    @Test
    void slavicCountsDistinguishOneFewManyAndFractions() throws IOException {
        Object[][] russian = {{0,"0 дней"},{1,"1 день"},{2,"2 дня"},{5,"5 дней"},
                {11,"11 дней"},{21,"21 день"},{101,"101 день"},
                {new BigDecimal("1.5"),"1,5 дня"}};
        for (Object[] example : russian) {
            assertEquals(example[1], text(Language.RU_RU, Message.GOVERNANCE_DAYS, example[0]));
        }
        assertEquals("2 дні", text(Language.UK_UA, Message.GOVERNANCE_DAYS, 2));
        assertEquals("5 днів", text(Language.UK_UA, Message.GOVERNANCE_DAYS, 5));
        assertEquals("21 день", text(Language.UK_UA, Message.GOVERNANCE_DAYS, 21));
        assertEquals("1,5 дня", text(Language.UK_UA, Message.GOVERNANCE_DAYS, new BigDecimal("1.5")));
    }

    @Test
    void frenchAndBrazilianPortugueseUseTheirOwnZeroAndFractionRules() throws IOException {
        assertEquals("0 seconde", text(Language.FR_FR, Message.GOVERNANCE_SECONDS, 0));
        assertEquals("1,5 seconde", text(Language.FR_FR, Message.GOVERNANCE_SECONDS, new BigDecimal("1.5")));
        assertEquals("2 secondes", text(Language.FR_FR, Message.GOVERNANCE_SECONDS, 2));
        assertEquals("0 segundo", text(Language.PT_BR, Message.GOVERNANCE_SECONDS, 0));
        assertEquals("2 segundos", text(Language.PT_BR, Message.GOVERNANCE_SECONDS, 2));
    }

    @Test
    void grammaticalCaseComesFromTheSentenceAndTermGenderControlsAgreement() throws IOException {
        assertEquals("vor 1 Tag", text(Language.DE_DE, Message.PLAYER_ASSOCIATION_TIME_DAYS, 1));
        assertEquals("vor 2 Tagen", text(Language.DE_DE, Message.PLAYER_ASSOCIATION_TIME_DAYS, 2));
        assertEquals("Создать участок", text(Language.RU_RU, Message.PLOT_MENU_CREATE));
        assertEquals("Створити ділянку", text(Language.UK_UA, Message.PLOT_MENU_CREATE));
        assertEquals("Eigentümer des Grundstücks", text(Language.DE_DE, Message.PLOT_ROLE_OWNER));
        assertEquals("Модель сохранена", text(Language.RU_RU, Message.VEHICLE_MODEL_SAVED));
        assertEquals("Модель збережена", text(Language.UK_UA, Message.VEHICLE_MODEL_SAVED));
        assertEquals("Parcelle Bridge créée · ID", text(Language.FR_FR, Message.PLOT_CREATED, "Bridge", "ID"));
        assertEquals("Parcela Bridge creada · ID", text(Language.ES_ES, Message.PLOT_CREATED, "Bridge", "ID"));
    }

    @Test
    void numbersRemainNumbersAcrossJavaNumericTypes() throws IOException {
        for (Number value : new Number[]{(byte)1,(short)1,1,1L,BigInteger.ONE,BigDecimal.ONE}) {
            assertEquals("1 second", text(Language.EN_US, Message.GOVERNANCE_SECONDS, value));
        }
        assertEquals("1,5 часа", text(Language.RU_RU, Message.GOVERNANCE_HOURS, new BigDecimal("1.5")));
    }

    @Test
    void richCountsKeepTheirValueWithoutLosingStyleOrClickEvents() throws IOException {
        Component count = Component.text("1", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/example"));
        var rich = FluentArguments.rich(new TextArg[0], RichArg.number("count",count,1),
                RichArg.component("world",Component.text("世界A")),
                RichArg.component("location",Component.text("[0, 1, 2]")),
                RichArg.component("radius",Component.text("r=1")));
        String resolved = bundle(Language.EN_US).resolveMessage(Message.REMOVEITEMS_DONE_RICH.key(),
                rich.arguments(),StringResultFactory.construct()).orElseThrow().toString();
        Component result = rich.render(resolved,NamedTextColor.GRAY);
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertTrue(plain.contains("1 dropped item in world 世界A"),plain);
        assertFalse(plain.contains("[[MIK_RICH_"));
        assertTrue(contains(result,count),"the original component retains its click event and style");
        String console = bundle(Language.EN_US).resolveMessage(Message.REMOVEITEMS_DONE_RICH.key(),
                FluentArguments.named(TextArg.of("count",1),TextArg.of("countValue",1),TextArg.of("world","世界A"),
                        TextArg.of("location","[0, 1, 2]"),TextArg.of("radius","r=1")),
                StringResultFactory.construct()).orElseThrow().toString();
        assertEquals(plain,console);
    }

    @Test
    void selfKickAddressesTheActorAndNamesTheActorForOtherViewers() throws IOException {
        Component player = Component.text("Literal_Name21", NamedTextColor.YELLOW);
        Component reason = Component.text("{ $not_a_variable } <red>", NamedTextColor.WHITE);
        for (Language language : Language.values()) {
            for (Message message : new Message[]{Message.SELF_KICK_WITH_REASON,
                    Message.SELF_KICK_WITHOUT_REASON}) {
                for (String person : new String[]{"second", "third"}) {
                    var arguments = FluentArguments.rich(new TextArg[]{TextArg.of("person", person)},
                            RichArg.component("player", player),
                            RichArg.component("reason", reason));
                    String resolved = bundle(language).resolveMessage(message.key(), arguments.arguments(),
                            StringResultFactory.construct()).orElseThrow().toString();
                    String plain = PlainTextComponentSerializer.plainText().serialize(
                            arguments.render(resolved, NamedTextColor.GRAY));
                    assertEquals(person.equals("third"), plain.contains("Literal_Name21"),
                            language.id() + " " + person + " " + message);
                    assertEquals(message == Message.SELF_KICK_WITH_REASON,
                            plain.contains("{ $not_a_variable } <red>"),
                            "user text is inserted literally, without Fluent or MiniMessage evaluation");
                }
            }
        }
    }

    @Test
    void platformNamesAreStringsAndNeverPluralSelectors() throws IOException {
        assertEquals("Your Matrix identity link was removed.",
                text(Language.EN_US, Message.IDENTITY_NOTIFY_UNLINKED, "Matrix"));
        assertEquals("Your 21 identity link was removed.",
                text(Language.EN_US, Message.IDENTITY_NOTIFY_UNLINKED, "21"));
    }

    @Test
    void frenchParticiplesAndVerbsFollowTheCountedObject() throws IOException {
        assertTrue(text(Language.FR_FR, Message.ASYNC_REGEN_COMPLETED, 1, "1 s")
                .contains("1 bloc modifié"));
        assertTrue(text(Language.FR_FR, Message.ASYNC_REGEN_COMPLETED, 2, "1 s")
                .contains("2 blocs modifiés"));
        assertTrue(text(Language.FR_FR, Message.MUSIC_PARTIAL_RESULTS, 1)
                .contains("1 requête a échoué"));
        assertTrue(text(Language.FR_FR, Message.MUSIC_PARTIAL_RESULTS, 2)
                .contains("2 requêtes ont échoué"));
    }

    @Test
    void nearbyInvisibilityUsesOneMessageForAllCountsAndDistances() throws IOException {
        assertTrue(plainNamed(Language.EN_US, Message.INVISIBILITY_NEARBY_MM,
                TextArg.of("count", 1), TextArg.of("distance", 1))
                .contains("1 nearby player is invisible · nearest about 1 block"));
        assertTrue(plainNamed(Language.EN_US, Message.INVISIBILITY_NEARBY_MM,
                TextArg.of("count", 2), TextArg.of("distance", 2))
                .contains("2 nearby players are invisible · nearest about 2 blocks"));
        assertTrue(plainNamed(Language.RU_RU, Message.INVISIBILITY_NEARBY_MM,
                TextArg.of("count", 21), TextArg.of("distance", 1))
                .contains("21 невидимый игрок"));
        assertTrue(plainNamed(Language.UK_UA, Message.INVISIBILITY_NEARBY_MM,
                TextArg.of("count", 22), TextArg.of("distance", 2))
                .contains("22 невидимі гравці"));
    }

    @Test
    void backHistoryUsesOneMessageForOneAndManySteps() throws IOException {
        assertEquals("Went back 1 step · 2/64", plainNamed(Language.EN_US, Message.BACK_SUCCESS_MM,
                TextArg.of("steps", 1), TextArg.of("remaining", 2), TextArg.of("max", 64)));
        assertEquals("Went back 21 steps · 2/64", plainNamed(Language.EN_US, Message.BACK_SUCCESS_MM,
                TextArg.of("steps", 21), TextArg.of("remaining", 2), TextArg.of("max", 64)));
        assertTrue(plainNamed(Language.RU_RU, Message.BACK_SUCCESS_MM,
                TextArg.of("steps", 21), TextArg.of("remaining", 2), TextArg.of("max", 64))
                .contains("21 шаг"));
        assertTrue(plainNamed(Language.UK_UA, Message.BACK_SUCCESS_MM,
                TextArg.of("steps", 22), TextArg.of("remaining", 2), TextArg.of("max", 64))
                .contains("22 кроки"));
    }

    @Test
    void everyNumericMessageResolvesZeroSingularPluralAndFractionBranches() throws IOException {
        for (Language language : Language.values()) {
            var entries = ENTRY.matcher(source(language));
            while (entries.find()) {
                String key=entries.group(1), body=entries.group(2);
                var selectors=SELECTOR.matcher(body).results().map(match -> match.group(1))
                        .filter(name -> !name.equals("person"))
                        .collect(java.util.stream.Collectors.toSet());
                if (selectors.isEmpty()) continue;
                var variables=VARIABLE.matcher(body).results().map(match -> match.group(1))
                        .collect(java.util.stream.Collectors.toSet());
                for (BigDecimal number : new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ONE,
                        BigDecimal.TWO,new BigDecimal("5"),new BigDecimal("11"),
                        new BigDecimal("21"),new BigDecimal("101"),new BigDecimal("1.5")}) {
                    var arguments=ArgumentListBuilder.builder();
                    for (String variable : variables) {
                        if (selectors.contains(variable)) arguments.add(variable,number);
                        else arguments.add(variable,"[["+variable+"]]");
                    }
                    String rendered=bundle(language).resolveMessage(key,arguments.build(),
                            StringResultFactory.construct()).orElseThrow().toString();
                    assertFalse(rendered.isBlank(),language.id()+" "+key);
                    assertFalse(rendered.contains("{ $"),language.id()+" "+key+" "+rendered);
                }
            }
        }
    }

    private static boolean contains(Component parent, Component expected) {
        return parent.equals(expected)||parent.children().stream().anyMatch(child -> contains(child,expected));
    }

    private String text(Language language,Message message,Object... arguments) throws IOException {
        return bundle(language).resolveMessage(message.key(),FluentArguments.positional(arguments),
                StringResultFactory.construct()).orElseThrow().toString();
    }

    private String plainNamed(Language language, Message message, TextArg... arguments) throws IOException {
        String formatted = bundle(language).resolveMessage(message.key(), FluentArguments.named(arguments),
                StringResultFactory.construct()).orElseThrow().toString();
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(formatted));
    }

    private FluentBundle bundle(Language language) throws IOException {
        FluentBundle loaded=bundles.get(language);
        if (loaded!=null) return loaded;
        var resource=ResourceParserBuilder.defaultParser().parse(FluentIteratorFactory.fromString(source(language)));
        loaded=FluentBundleBuilder.builder(language.locale()).addResource(resource).addDefaultFunctions().build();
        bundles.put(language,loaded);
        return loaded;
    }

    private static String source(Language language) throws IOException {
        try (var input=FluentGrammarTest.class.getClassLoader().getResourceAsStream("lang/"+language.id()+".ftl")) {
            if (input==null) throw new IOException("Missing locale "+language.id());
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
