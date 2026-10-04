import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider

plugins {
    id("dg-java-conventions")
    id("xyz.wagyourtail.unimined")
}

dependencies {
    implementation(project(":common"))
}

// Identical packet recipes live once; each module compiles only its selected sources against its own API.
val sharedPacketRoot = rootProject.file("shared/packetshapes")
val packetSourceManifest = sharedPacketRoot.resolve("${project.name}.sources")
if (packetSourceManifest.isFile) {
    val paths = packetSourceManifest.readLines().filter { it.isNotBlank() }
    val selected = paths.map { sharedPacketRoot.resolve(it).canonicalFile }.toSet()
    sourceSets.main {
        java.srcDirs(paths.map { sharedPacketRoot.resolve(it.substringBefore('/')) }.toSet())
        java.exclude { element ->
            !element.isDirectory && element.file.toPath().startsWith(sharedPacketRoot.toPath()) &&
                element.file.canonicalFile !in selected
        }
    }
}

tasks.withType<RemapJarTask> {
    onlyIf { false }
}
