package dev.minecraftdatagenerator.packetgen;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Hashes full runtime artifacts, including nested classes and mapped game implementations. */
final class RuntimeFingerprint {
    static String of(String identity, Collection<Class<?>> classes) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        hash.update(identity.getBytes(StandardCharsets.UTF_8));
        hash.update(System.getProperty("java.runtime.version").getBytes(StandardCharsets.UTF_8));
        Set<Path> locations = new TreeSet<>();
        for (Class<?> cls : classes) locations.add(Path.of(cls.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
        for (Path location : locations) {
            if (Files.isDirectory(location)) {
                try (var files = Files.walk(location)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                        hash.update(location.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                        digest(hash, file);
                    }
                }
            } else digest(hash, location);
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void digest(MessageDigest hash, Path file) throws Exception {
        try (var in = Files.newInputStream(file)) {
            byte[] block = new byte[65536]; int n;
            while ((n = in.read(block)) != -1) hash.update(block, 0, n);
        }
    }
}
