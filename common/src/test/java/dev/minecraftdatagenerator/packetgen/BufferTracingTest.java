package dev.minecraftdatagenerator.packetgen;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Run with a selected Netty runtime on the classpath; no Minecraft bootstrap is required. */
public final class BufferTracingTest {
    public static void main(String[] args) throws Exception {
        try (var generated = new GeneratedBuffers(Class.forName("io.netty.buffer.DuplicatedByteBuf"), Files.createTempDirectory("buffer-trace-test-"))) {
            Object outer = generated.output();
            try {
                Object raw = Reflect.call(outer, "unwrap");
                BufferTrace recorder = new BufferTrace(); recorder.begin();
                try {
                    var outerCall = BufferTrace.before(raw, "i32", 42, false, false);
                    try { Reflect.call(raw, "writeInt", 42); } finally { BufferTrace.after(outerCall); }
                    try { Reflect.call(Class.forName("io.netty.buffer.ByteBufUtil"), "writeUtf8", raw, "é"); }
                    catch (NoSuchMethodException e) { Reflect.call(raw, "writeBytes", "é".getBytes(StandardCharsets.UTF_8)); }
                    Reflect.call(raw, "writeLong", Long.MIN_VALUE);
                } finally { BufferTrace.end(); }
                int length = ((Number) Reflect.call(raw, "writerIndex")).intValue();
                if (length != 14 || !recorder.coverage(length).get("complete").getAsBoolean()) throw new AssertionError("underlying-buffer writes were missed: " + recorder.coverage(length) + " " + recorder.primitives());
                if (recorder.primitives().size() < 3) throw new AssertionError("primitive operations were collapsed");
                System.out.println("Buffer tracing passed: " + recorder.primitives());
            } finally { Reflect.call(outer, "release"); }
        }
    }
}
