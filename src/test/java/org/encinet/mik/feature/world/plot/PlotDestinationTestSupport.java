package org.encinet.mik.feature.world.plot;

import java.lang.reflect.Proxy;
import java.util.function.BiFunction;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

final class PlotDestinationTestSupport {
    private PlotDestinationTestSupport() { }
    static LanguageService language() {
        return new LanguageService(null) {
            @Override public String t(Player player, Message message, Object... args) { return message.key(); }
        };
    }
    static LanguageService language(org.encinet.mik.shared.i18n.Language language) throws Exception {
        var bundle = org.encinet.mik.shared.i18n.FluentTestSupport.bundle(language);
        return new LanguageService(null) {
            @Override public String t(Player player, Message message, Object... args) {
                return org.encinet.mik.shared.i18n.FluentTestSupport.text(bundle, message, args);
            }
        };
    }
    static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getName().equals("equals")) return self == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(self);
            Object result = calls.apply(method.getName(), args == null ? new Object[0] : args);
            if (result != null || !method.getReturnType().isPrimitive() || method.getReturnType() == void.class) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == float.class) return 0f;
            if (method.getReturnType() == double.class) return 0d;
            if (method.getReturnType() == byte.class) return (byte) 0;
            if (method.getReturnType() == short.class) return (short) 0;
            return '\0';
        }));
    }
}
