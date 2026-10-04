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
        Path dataDumpDirectory = serverRootDirectory.resolve("minecraft-data").resolve(versionName);
        String packetOutput = System.getenv("PACKETGEN_OUTPUT_DIR");
        if (packetOutput != null && !packetOutput.isBlank()) dataDumpDirectory = Path.of(packetOutput).resolve(versionName);
        runtimeVersion = versionName;
        outputDirectory = dataDumpDirectory.toAbsolutePath().normalize();
        boolean success = DataGenerators.runDataGenerators(dataDumpDirectory);
        int packetExit = dev.minecraftdatagenerator.generators.SharedPacketDataGenerator.exitCode;
        if (success && packetExit == 0) {
            MinecraftDataGenerator.LOGGER.info("Data generation successful!");
        } else if (success && packetExit == 2) {
            MinecraftDataGenerator.LOGGER.warning("Packet corpus incomplete; remaining combinations are checkpointed for resume.");
        } else {
            MinecraftDataGenerator.LOGGER.severe("Data generation failed!");
        }
        Runtime.getRuntime().halt(success ? packetExit : 1);
    }
}
