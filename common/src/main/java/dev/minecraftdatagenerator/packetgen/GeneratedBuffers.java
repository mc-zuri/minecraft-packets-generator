package dev.minecraftdatagenerator.packetgen;

import io.github.classgraph.ClassGraph;
import javax.tools.ToolProvider;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

/** One source template compiled against the running version's buffer ABI (including Netty 4.0). */
public final class GeneratedBuffers implements AutoCloseable {
    private final Constructor<?> constructor;
    private final Constructor<?> rawConstructor;
    private final Class<?> unpooled;
    private final URLClassLoader loader;
    public GeneratedBuffers(Class<?> gameBuffer, Path cache) throws Exception {
        unpooled = Class.forName("io.netty.buffer.Unpooled");
        StringBuilder source = new StringBuilder("package dev.minecraftdatagenerator.packetgen.runtime;\n");
        source.append("import dev.minecraftdatagenerator.packetgen.BufferTrace;\npublic final class TracedBuffer extends ").append(gameBuffer.getName()).append(" {\n");
        source.append("public TracedBuffer(io.netty.buffer.ByteBuf source) { super(source); }\n");
        Map<String, Method> methods = new TreeMap<>();
        for (Method m : gameBuffer.getMethods()) {
            String signature = m.getName() + Arrays.toString(m.getParameterTypes());
            // Mapped game buffers may flag their covariant override as a bridge. The source
            // override still needs the narrowest return type, regardless of that bytecode flag.
            methods.merge(signature, m, (a, b) -> a.getReturnType().isAssignableFrom(b.getReturnType()) ? b : a);
        }
        for (Method m : methods.values()) {
            if (Modifier.isFinal(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
            String kind = classify(m); if (kind == null) continue;
            Class<?>[] params = m.getParameterTypes();
            boolean reading = m.getName().startsWith("read") && !kind.equals("view");
            boolean semantic = Set.of("varint", "varlong", "string", "blockpos").contains(kind);
            source.append("@Override public ").append(m.getReturnType().getCanonicalName()).append(' ').append(m.getName()).append('(');
            for (int i = 0; i < params.length; i++) { if (i > 0) source.append(','); source.append(params[i].getCanonicalName()).append(" p").append(i); }
            source.append(')');
            if (m.getExceptionTypes().length > 0) source.append(" throws ").append(String.join(",", Arrays.stream(m.getExceptionTypes()).map(Class::getCanonicalName).toList()));
            source.append(" { var t = BufferTrace.before(this,\"").append(kind).append("\",").append(!reading && params.length > 0 ? "p0" : "null").append(',').append(semantic).append(',').append(reading).append("); try { ");
            if (m.getReturnType() != void.class) source.append("return ");
            source.append("super.").append(m.getName()).append('(');
            for (int i = 0; i < params.length; i++) { if (i > 0) source.append(','); source.append('p').append(i); }
            source.append("); } finally { BufferTrace.after(t); } }\n");
        }
        source.append("}\n");
        String rawSource = rawBufferSource();
        String fingerprint = PacketCorpus.hash((source.toString() + rawSource).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path dir = cache.resolve(fingerprint); Files.createDirectories(dir);
        Path java = dir.resolve("TracedBuffer.java"); Files.writeString(java, source);
        Path rawJava = dir.resolve("TracedRawBuffer.java"); Files.writeString(rawJava, rawSource);
        if (ToolProvider.getSystemJavaCompiler() == null) throw new IllegalStateException("packet generation requires a JDK, not a JRE");
        String classpath;
        try (var scan = new ClassGraph().overrideClassLoaders(gameBuffer.getClassLoader(), GeneratedBuffers.class.getClassLoader()).scan()) {
            classpath = scan.getClasspath();
        }
        java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();
        int code = ToolProvider.getSystemJavaCompiler().run(null, errors, errors, "-proc:none", "--release", "21", "-classpath", classpath, "-d", dir.toString(), java.toString(), rawJava.toString());
        if (code != 0) throw new IllegalStateException("buffer adapter compilation: " + errors);
        loader = new URLClassLoader(new java.net.URL[]{dir.toUri().toURL()}, gameBuffer.getClassLoader());
        constructor = loader.loadClass("dev.minecraftdatagenerator.packetgen.runtime.TracedBuffer").getConstructor(Class.forName("io.netty.buffer.ByteBuf"));
        rawConstructor = loader.loadClass("dev.minecraftdatagenerator.packetgen.runtime.TracedRawBuffer").getConstructor(Class.forName("io.netty.buffer.ByteBuf"));
    }
    public Object output() throws Exception { return constructor.newInstance(rawConstructor.newInstance(Reflect.call(unpooled, "buffer"))); }
    public Object input(byte[] bytes) throws Exception { return constructor.newInstance(rawConstructor.newInstance(Reflect.call(unpooled, "wrappedBuffer", bytes))); }
    public void close() throws Exception { loader.close(); }

    private static String rawBufferSource() throws Exception {
        // A forwarding ByteBuf deliberately avoids AbstractByteBuf's unchecked native-store fast
        // paths. Static Minecraft/Netty helpers therefore call observable public write operations.
        // The same recorder suppresses nested outer/inner callbacks, preserving one primitive op.
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf");
        StringBuilder source = new StringBuilder("package dev.minecraftdatagenerator.packetgen.runtime;\nimport dev.minecraftdatagenerator.packetgen.BufferTrace;\npublic final class TracedRawBuffer extends io.netty.buffer.ByteBuf {\nprivate final io.netty.buffer.ByteBuf source;\npublic TracedRawBuffer(io.netty.buffer.ByteBuf source) { this.source = source; }\n");
        Map<String, Method> methods = new TreeMap<>();
        for (Method m : byteBuf.getMethods()) if (Modifier.isAbstract(m.getModifiers()) && !m.isBridge()) {
            String signature = m.getName() + Arrays.toString(m.getParameterTypes());
            methods.merge(signature, m, (a, b) -> a.getReturnType().isAssignableFrom(b.getReturnType()) ? b : a);
        }
        for (Method m : methods.values()) {
            if (m.getName().equals("compareTo") && Arrays.equals(m.getParameterTypes(), new Class<?>[]{Object.class})) continue;
            Class<?>[] params = m.getParameterTypes(); String kind = classify(m);
            boolean reading = m.getName().startsWith("read") && !"view".equals(kind);
            source.append("@Override public ").append(m.getReturnType().getCanonicalName()).append(' ').append(m.getName()).append('(');
            for (int i = 0; i < params.length; i++) { if (i > 0) source.append(','); source.append(params[i].getCanonicalName()).append(" p").append(i); }
            source.append(')');
            if (m.getExceptionTypes().length > 0) source.append(" throws ").append(String.join(",", Arrays.stream(m.getExceptionTypes()).map(Class::getCanonicalName).toList()));
            source.append(" { ");
            if (kind != null) source.append("var t = BufferTrace.before(this,\"").append(kind).append("\",").append(!reading && params.length > 0 ? "p0" : "null").append(",false,").append(reading).append("); try { ");
            if (m.getReturnType() == byteBuf) source.append("io.netty.buffer.ByteBuf value = ");
            else if (m.getReturnType() != void.class) source.append("return ");
            source.append("source.").append(m.getName()).append('(');
            for (int i = 0; i < params.length; i++) { if (i > 0) source.append(','); source.append('p').append(i); }
            source.append("); ");
            if (m.getReturnType() == byteBuf) source.append("return value == source ? this : value; ");
            if (kind != null) source.append("} finally { BufferTrace.after(t); } ");
            source.append("}\n");
        }
        return source.append("}\n").toString();
    }
    private static String classify(Method m) {
        String name = m.getName();
        if (name.startsWith("set") && !name.equals("setIndex") || Set.of("clear", "discardReadBytes", "discardSomeReadBytes", "resetWriterIndex", "setIndex").contains(name) || name.equals("writerIndex") && m.getParameterCount() == 1) return "patch";
        if (Set.of("slice", "retainedSlice", "duplicate", "retainedDuplicate", "readSlice", "readRetainedSlice", "array", "nioBuffer", "nioBuffers", "internalNioBuffer", "unwrap", "memoryAddress").contains(name)) return "view";
        if (name.equals("writeVarInt")) return "varint";
        if (name.equals("writeVarLong") || name.equals("method_10608") && Arrays.equals(m.getParameterTypes(), new Class<?>[]{long.class})) return "varlong";
        if (name.equals("writeString") || name.equals("writeUtf") || name.equals("method_7423") && Arrays.equals(m.getParameterTypes(), new Class<?>[]{String.class})) return "string";
        if (name.equals("writeBlockPos")) return "blockpos";
        if (name.startsWith("writeBytes") || name.equals("writeZero") || name.equals("writeCharSequence")) return "bytes";
        String suffix = name.replaceFirst("^(write|read)", "");
        if (suffix.equals(name)) return null;
        return switch (suffix) {
            case "Boolean" -> "bool"; case "Byte" -> "i8"; case "UnsignedByte" -> "u8";
            case "Short" -> "i16"; case "UnsignedShort" -> "u16"; case "ShortLE" -> "li16";
            case "Medium" -> "i24"; case "MediumLE" -> "li24";
            case "Int" -> "i32"; case "UnsignedInt" -> "u32"; case "IntLE" -> "li32";
            case "Long" -> "i64"; case "LongLE" -> "li64"; case "Char" -> "char";
            case "Float" -> "f32"; case "Double" -> "f64";
            default -> null;
        };
    }
}
