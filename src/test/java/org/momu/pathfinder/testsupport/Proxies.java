package org.momu.pathfinder.testsupport;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Builds lightweight interface fakes. Much faster than Mockito for objects that are called millions of times.
 */
final class Proxies {
    interface Handler {
        Object handle(Object self, String method, Object[] args);
    }

    private Proxies() {
    }

    static <T> T fake(Class<T> type, Handler handler) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (self, method, args) -> dispatch(self, method, args == null ? new Object[0] : args, handler));
        return type.cast(proxy);
    }

    private static Object dispatch(Object self, Method method, Object[] args, Handler handler) {
        if (method.getName().equals("hashCode") && args.length == 0) {
            return System.identityHashCode(self);
        }
        if (method.getName().equals("equals") && args.length == 1) {
            return self == args[0];
        }
        Object result = handler.handle(self, method.getName(), args);
        if (result == null && method.getReturnType().isPrimitive()) {
            return defaultValue(method.getReturnType());
        }
        return result;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
