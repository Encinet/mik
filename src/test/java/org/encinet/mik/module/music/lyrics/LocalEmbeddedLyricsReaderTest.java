package org.encinet.mik.module.music.lyrics;

import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalEmbeddedLyricsReaderTest {

    @Test
    void readsFirstNonBlankGenericLyricsValue() {
        Tag tag = tag(List.of("  ", "[00:01.00] Line \r\n"));

        assertEquals("[00:01.00] Line", LocalEmbeddedLyricsReader.extract(tag));
    }

    @Test
    void missingOrBrokenTagProducesNoLyrics() {
        assertNull(LocalEmbeddedLyricsReader.extract(null));
        Tag broken = (Tag) Proxy.newProxyInstance(Tag.class.getClassLoader(),
                new Class<?>[]{Tag.class}, (proxy, method, arguments) -> {
                    throw new IllegalStateException("broken tag");
                });
        assertNull(LocalEmbeddedLyricsReader.extract(broken));
    }

    private static Tag tag(List<String> lyrics) {
        return (Tag) Proxy.newProxyInstance(Tag.class.getClassLoader(),
                new Class<?>[]{Tag.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getAll")
                            && arguments[0] == FieldKey.LYRICS) {
                        return lyrics;
                    }
                    if (method.getName().equals("getFirst")
                            && arguments != null && arguments.length == 1
                            && arguments[0] == FieldKey.LYRICS) {
                        return lyrics.isEmpty() ? "" : lyrics.getFirst();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }
}
