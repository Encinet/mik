package org.encinet.mik.module.skript;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import ch.njol.skript.patterns.PatternCompiler;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MikSkriptModuleTest {

    @Test
    void registersAllMikPlayerStateSyntaxes() {
        org.skriptlang.skript.Skript skript = org.skriptlang.skript.Skript.of(
                MikSkriptModuleTest.class, "TestSkript");
        Function<Player, String> value = player -> "value";
        MikSkriptFacade facade = new MikSkriptFacade(null, value, value, value, null, null);

        MikSkriptModule.register(skript, facade);

        assertNotNull(skript.addon(MikSkriptModule.ADDON_NAME));
        SyntaxInfo.Expression<?, ?> strings = expression(skript, MikPlayerPropertyExpression.class);
        assertEquals(List.of(
                "[the] mik language of %players%",
                "%players%'[s] mik language",
                "[the] mik client version of %players%",
                "%players%'[s] mik client version",
                "[the] mik role of %players%",
                "%players%'[s] mik role",
                "[the] mik afk message of %players%",
                "%players%'[s] mik afk message",
                "[the] mik afk source of %players%",
                "%players%'[s] mik afk source",
                "[the] mik pvp override id of %players%",
                "%players%'[s] mik pvp override id",
                "[the] mik pvp override owner of %players%",
                "%players%'[s] mik pvp override owner"
        ), List.copyOf(strings.patterns()));
        assertInstanceOf(MikPlayerPropertyExpression.class, strings.instance());

        assertEquals(5, skript.syntaxRegistry().syntaxes(SyntaxRegistry.EXPRESSION).size());
        assertEquals(5, skript.syntaxRegistry().syntaxes(SyntaxRegistry.EFFECT).size());
        assertEquals(2, skript.syntaxRegistry().syntaxes(SyntaxRegistry.CONDITION).size());
        assertTrue(types(skript.syntaxRegistry().syntaxes(SyntaxRegistry.EXPRESSION)).containsAll(List.of(
                MikPlayerBooleanExpression.class,
                MikPlayerTimespanExpression.class,
                MikPvpOverridePriorityExpression.class,
                MikPvpOverrideIdsExpression.class
        )));
        assertTrue(types(skript.syntaxRegistry().syntaxes(SyntaxRegistry.EFFECT)).containsAll(List.of(
                MikAfkSetEffect.class,
                MikAfkClearEffect.class,
                MikPvpPreferenceEffect.class,
                MikPvpOverrideSetEffect.class,
                MikPvpOverrideClearEffect.class
        )));
        assertTrue(types(skript.syntaxRegistry().syntaxes(SyntaxRegistry.CONDITION)).containsAll(List.of(
                MikPlayerStateCondition.class,
                MikPvpOverrideCondition.class
        )));
        for (SyntaxInfo<?> info : skript.syntaxRegistry().elements()) {
            info.patterns().stream()
                    .map(MikSkriptModuleTest::withoutTypePlaceholders)
                    .forEach(PatternCompiler::compile);
        }
    }

    private static String withoutTypePlaceholders(String pattern) {
        return pattern.replaceAll("%[^%]+%", "value");
    }

    private static SyntaxInfo.Expression<?, ?> expression(
            org.skriptlang.skript.Skript skript, Class<?> type) {
        return skript.syntaxRegistry().syntaxes(SyntaxRegistry.EXPRESSION).stream()
                .filter(info -> info.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static List<Class<?>> types(Collection<? extends SyntaxInfo<?>> infos) {
        List<Class<?>> types = new java.util.ArrayList<>(infos.size());
        for (SyntaxInfo<?> info : infos) {
            types.add(info.type());
        }
        return List.copyOf(types);
    }
}
