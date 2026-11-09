package org.encinet.mik.feature.entertainment.music.application;

import java.lang.reflect.Proxy;

final class MusicDeviceTestSupport {
    private MusicDeviceTestSupport() { }
    interface Call {
        Object invoke(String name, Object[] args);
    }
    static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
                    if (method.getName().equals("equals"))
                        return self == args[0];
                    if (method.getName().equals("hashCode"))
                        return System.identityHashCode(self);
                    Object value = call.invoke(method.getName(), args == null ? new Object[0] : args);
                    if (value != null)
                        return value;
                    Class<?> result = method.getReturnType();
                    if (!result.isPrimitive() || result == void.class)
                        return null;
                    if (result == boolean.class)
                        return false;
                    if (result == int.class)
                        return 0;
                    if (result == long.class)
                        return 0L;
                    if (result == double.class)
                        return 0.0;
                    if (result == float.class)
                        return 0f;
                    if (result == byte.class)
                        return (byte) 0;
                    if (result == short.class)
                        return (short) 0;
                    return '\0';
                }));
    }
}
