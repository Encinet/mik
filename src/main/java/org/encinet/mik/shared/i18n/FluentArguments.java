package org.encinet.mik.shared.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.quickwrite.fluent4j.ast.pattern.ArgumentList;
import net.quickwrite.fluent4j.container.ArgumentListBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps numeric selector values separate from formatted text and rich components. */
final class FluentArguments {
    private FluentArguments() { }

    static ArgumentList positional(Object... values) {
        var builder = ArgumentListBuilder.builder();
        for (int i = 0; i < values.length; i++) add(builder, "arg" + i, values[i]);
        return builder.build();
    }

    static ArgumentList named(TextArg... values) {
        var builder = ArgumentListBuilder.builder();
        for (TextArg value : values) add(builder, value.name(), value.value());
        return builder.build();
    }

    static RichArguments rich(TextArg[] selectors, RichArg... values) {
        var builder = ArgumentListBuilder.builder();
        for (TextArg selector : selectors) add(builder, selector.name(), selector.value());
        Map<String, Component> components = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            RichArg value = values[i];
            String token = "[[MIK_RICH_" + i + "]]";
            builder.add(value.name(), token);
            if (value.numericValue() != null) {
                addNumber(builder, value.name() + "Value", value.numericValue());
            }
            components.put(token, value.component());
        }
        return new RichArguments(builder.build(), Map.copyOf(components));
    }

    private static void add(ArgumentList.Builder builder, String name, Object value) {
        if (value instanceof Number number) {
            addNumber(builder, name, number);
        } else {
            builder.add(name, String.valueOf(value));
        }
    }

    private static void addNumber(ArgumentList.Builder builder, String name, Number value) {
        switch (value) {
            case BigDecimal decimal -> builder.add(name, decimal);
            case BigInteger integer -> builder.add(name, new BigDecimal(integer));
            case Byte _, Short _, Integer _, Long _ -> builder.add(name, value.longValue());
            case Float _, Double _ -> {
                double number = value.doubleValue();
                if (Double.isFinite(number)) builder.add(name, number);
                else builder.add(name, value.toString());
            }
            default -> builder.add(name, new BigDecimal(value.toString()));
        }
    }

    record RichArguments(ArgumentList arguments, Map<String, Component> components) {
        Component render(String text, NamedTextColor baseColor) {
            Component result = Component.empty();
            int index = 0;
            while (index < text.length()) {
                String nextToken = null;
                int nextIndex = -1;
                for (String token : components.keySet()) {
                    int found = text.indexOf(token, index);
                    if (found >= 0 && (nextIndex < 0 || found < nextIndex)) {
                        nextIndex = found;
                        nextToken = token;
                    }
                }
                if (nextToken == null) {
                    result = result.append(Component.text(text.substring(index), baseColor));
                    break;
                }
                if (nextIndex > index) {
                    result = result.append(Component.text(text.substring(index, nextIndex), baseColor));
                }
                result = result.append(components.get(nextToken));
                index = nextIndex + nextToken.length();
            }
            return result;
        }
    }
}
