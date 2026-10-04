package dev.minecraftdatagenerator.generators;

import com.google.gson.JsonElement;
import dev.minecraftdatagenerator.MinecraftDataGenerator;
import dev.minecraftdatagenerator.packetgen.PacketCorpus;
import dev.minecraftdatagenerator.packetgen.RuntimePacketAdapter;

/** The same generator entry point is loaded by every version module. */
public final class SharedPacketDataGenerator implements IDataGenerator {
    public static int exitCode;
    public String getDataName() { return "packetCorpus"; }
    public boolean isEnabled() { return System.getenv("PACKETGEN_MANUAL") == null && !"reflective".equals(System.getenv("PACKETGEN_MODE")); }
    public JsonElement generateDataJson() {
        var work = MinecraftDataGenerator.outputDirectory;
        try (var adapter = new RuntimePacketAdapter(MinecraftDataGenerator.runtimeVersion, work.resolve(".packetgen-cache"))) {
            var result = PacketCorpus.run(adapter, PacketCorpus.Options.environment(work));
            exitCode = switch (result.get("status").getAsString()) { case "complete" -> 0; case "incomplete" -> 2; default -> 1; };
            return result;
        } catch (Exception e) { throw new IllegalStateException("shared packet generation failed", e); }
    }
}
