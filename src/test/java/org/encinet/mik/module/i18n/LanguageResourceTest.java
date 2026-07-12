package org.encinet.mik.module.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import org.junit.jupiter.api.Test;

class LanguageResourceTest {

    private static final Pattern KEY = Pattern.compile("^([a-z0-9-]+)\\s*=", Pattern.MULTILINE);
    private static final Pattern VARIABLE = Pattern.compile("\\$([a-zA-Z][a-zA-Z0-9-]*)");

    @Test
    void everyLanguageHasTheSameMessagesAndVariables() throws Exception {
        Map<String, Set<String>> reference = parse(Language.ZH_CN);

        for (Language language : Language.values()) {
            Map<String, Set<String>> actual = parse(language);
            assertEquals(reference.keySet(), actual.keySet(), language.id() + " message keys");
            for (String key : reference.keySet()) {
                assertEquals(reference.get(key), actual.get(key), language.id() + ": " + key);
            }
        }
    }

    @Test
    void everyLanguageBuildsAFluentBundle() throws Exception {
        for (Language language : Language.values()) {
            String source = source(language);
            var resource = ResourceParserBuilder.defaultParser()
                    .parse(FluentIteratorFactory.fromString(source));
            FluentBundleBuilder.builder(language.locale())
                    .addResource(resource)
                    .addDefaultFunctions()
                    .build();
        }
    }

    private static Map<String, Set<String>> parse(Language language) throws IOException {
        String source = source(language);
        Matcher keys = KEY.matcher(source);
        Map<String, Set<String>> messages = new LinkedHashMap<>();
        while (keys.find()) {
            int valueStart = keys.end();
            int valueEnd = source.indexOf('\n', valueStart);
            if (valueEnd < 0) valueEnd = source.length();
            Matcher variables = VARIABLE.matcher(source.substring(valueStart, valueEnd));
            Set<String> names = new TreeSet<>();
            while (variables.find()) names.add(variables.group(1));
            messages.put(keys.group(1), names);
        }
        return messages;
    }

    private static String source(Language language) throws IOException {
        String path = "/lang/" + language.id() + ".ftl";
        try (InputStream input = LanguageResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
