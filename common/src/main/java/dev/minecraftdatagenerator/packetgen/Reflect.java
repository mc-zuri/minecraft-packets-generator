package dev.minecraftdatagenerator.packetgen;

import java.lang.reflect.*;
import java.util.*;

/** Cached access to mapped runtime APIs; never infers a serializer from a Java field type. */
public final class Reflect {
    private Reflect() {}
    private record Call(String name, List<Class<?>> arguments, boolean statik) {}
    private static final ClassValue<Map<Call, Method>> METHODS = new ClassValue<>() {
        protected Map<Call, Method> computeValue(Class<?> type) { return new HashMap<>(); }
    };
    private static final ClassValue<List<Method>> CANDIDATES = new ClassValue<>() {
        protected List<Method> computeValue(Class<?> type) {
            List<Method> methods = new ArrayList<>(Arrays.asList(type.getMethods()));
            for (Class<?> c = type; c != null; c = c.getSuperclass()) methods.addAll(Arrays.asList(c.getDeclaredMethods()));
            methods.sort(Comparator.comparing(Method::isBridge).thenComparing(Method::toGenericString));
            return List.copyOf(methods);
        }
    };
    public static Class<?> cls(String... names) {
        for (String name : names) try { return Class.forName(name); } catch (ClassNotFoundException ignored) {}
        return null;
    }
    public static Object call(Object receiver, String name, Object... args) throws Exception {
        Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
        boolean statik = receiver instanceof Class<?>;
        Call key = new Call(name, Arrays.stream(args).map(a -> a == null ? Void.class : a.getClass()).toList(), statik);
        Map<Call, Method> cache = METHODS.get(type);
        Method method = cache.get(key);
        if (method == null) {
            if (cache.containsKey(key)) throw new NoSuchMethodException(type.getName() + "." + name + key.arguments());
            for (Method m : CANDIDATES.get(type)) {
                if (m.getName().equals(name) && Modifier.isStatic(m.getModifiers()) == statik && accepts(m.getParameterTypes(), args) && m.trySetAccessible()) { method = m; break; }
            }
            cache.put(key, method);
            if (method == null) throw new NoSuchMethodException(type.getName() + "." + name + key.arguments());
        }
        try { return method.invoke(statik ? null : receiver, args); }
        catch (InvocationTargetException e) { throw cause(e); }
    }
    public static Object maybe(Object receiver, String... names) {
        if (receiver == null) return null;
        for (String name : names) try { return call(receiver, name); } catch (Exception ignored) {}
        return null;
    }
    public static Object field(Object receiver, String... names) throws Exception {
        Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
        for (String name : names) for (Class<?> c = type; c != null; c = c.getSuperclass()) try {
            Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(receiver instanceof Class<?> ? null : receiver);
        } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(type.getName() + Arrays.toString(names));
    }
    public static Object construct(Class<?> cls, Object... args) throws Exception {
        if (cls == null) throw new ClassNotFoundException("missing fixture type");
        for (Constructor<?> c : cls.getDeclaredConstructors()) if (accepts(c.getParameterTypes(), args) && c.trySetAccessible()) {
            try { return c.newInstance(args); } catch (InvocationTargetException e) { throw cause(e); }
        }
        throw new NoSuchMethodException("constructor " + cls.getName() + Arrays.toString(args));
    }
    public static boolean accepts(Class<?>[] params, Object[] args) {
        if (params.length != args.length) return false;
        for (int i = 0; i < params.length; i++) if (args[i] == null ? params[i].isPrimitive() : !boxed(params[i]).isInstance(args[i])) return false;
        return true;
    }
    private static Class<?> boxed(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class; if (t == long.class) return Long.class;
        if (t == boolean.class) return Boolean.class; if (t == float.class) return Float.class;
        if (t == double.class) return Double.class; if (t == byte.class) return Byte.class;
        if (t == short.class) return Short.class; if (t == char.class) return Character.class;
        return t;
    }
    public static Exception cause(Throwable e) {
        while (e instanceof InvocationTargetException && e.getCause() != null) e = e.getCause();
        return e instanceof Exception x ? x : new RuntimeException(e);
    }
    public static String error(Throwable e) { return cause(e).getClass().getSimpleName() + ": " + cause(e).getMessage(); }
}
