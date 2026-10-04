package dev.minecraftdatagenerator;

import dev.minecraftdatagenerator.generators.DataGenerators;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.logging.Logger;

public class MinecraftDataGenerator {
    public static String runtimeVersion;
    public static Path outputDirectory;
    private static final Logger LOGGER = Logger.getLogger(MinecraftDataGenerator.class.getSimpleName());

    public static void start(String versionName, Path serverRootDirectory) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
        MinecraftDataGenerator.LOGGER.info("Starting data generation for version %s".formatted(versionName));
        // Private working files (summary, plan, caches); the packet streams go to PACKETGEN_OUTPUT_DIR.
        Path dataDumpDirectory = serverRootDirectory.resolve("minecraft-data").resolve(versionName);
        String work = System.getenv("PACKETGEN_WORK_DIR");
        if (work != null && !work.isBlank()) dataDumpDirectory = Path.of(work);
        runtimeVersion = versionName;
        outputDirectory = dataDumpDirectory.toAbsolutePath().normalize();
        boolean success = DataGenerators.runDataGenerators(dataDumpDirectory);
        int packetExit = dev.minecraftdatagenerator.generators.SharedPacketDataGenerator.exitCode;
        if (success && packetExit == 0) {
            MinecraftDataGenerator.LOGGER.info("Data generation successful!");
        } else if (success && packetExit == 2) {
            MinecraftDataGenerator.LOGGER.warning("Packet corpus incomplete; see summary.json for stop reasons and unresolved domains.");
        } else {
            MinecraftDataGenerator.LOGGER.severe("Data generation failed!");
        }
        Runtime.getRuntime().halt(success ? packetExit : 1);
    }
}
