package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.util.SimpleLiteral;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class MikSkriptInputTest {
    @Test void typedValuesKeepTheCurrentExpressionAndOptionalValuesRemainAbsent() {
        var input = MikSkriptInput.of(new SimpleLiteral<>("home", true), String.class);
        assertEquals("home", input.single(null));
        assertEquals(List.of("home"), input.values(null));
        assertTrue(input.isSingle());
        assertNull(MikSkriptInput.of(null, String.class));

        var invalid = MikSkriptInput.of(new SimpleLiteral<>(42, true), String.class);
        assertThrows(ClassCastException.class, () -> invalid.single(null));
        assertThrows(ClassCastException.class, () -> invalid.values(null));
    }

    @Test void conditionsRetainSkriptAndOrAndNegationSemantics() {
        Predicate<String> accepts = "yes"::equals;
        for (boolean and : new boolean[]{false, true}) {
            var expression = new SimpleLiteral<>(new String[]{"yes", "no"}, String.class, and);
            var input = MikSkriptInput.of(expression, String.class);
            for (boolean negated : new boolean[]{false, true})
                assertEquals(expression.check(null, accepts, negated), input.check(null, accepts, negated));
        }
    }
}
