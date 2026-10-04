package dev.minecraftdatagenerator.packetgen;

/**
 * One traced buffer operation: body offset, length, kind and, for numeric and position writes,
 * the vanilla argument ({@code int[]{x, y, z}} for block positions). Shared by the modern
 * version-module tracers and the legacy generated buffers.
 */
public record TraceOp(int off, int len, String kind, Object value) {
    public int end() { return off + len; }
}
