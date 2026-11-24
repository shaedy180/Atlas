plugins {
    id("net.neoforged.moddev")
}

val minecraftVersion = property("minecraft_version") as String
val common = project(":atlas-common")
val pureModules = listOf(":atlas-core", ":atlas-api", ":atlas-search", ":atlas-ui")

sourceSets.main {
    java.srcDir(common.file("src/main/java"))
    java.srcDir(common.file("src/compat/${property("compat")}/java"))
    resources.srcDir(common.file("src/main/resources"))
}

neoForge {
    version = property("neoforge_version") as String

    runs {
        register("client") {
            client()
        }
    }

    mods {
        register("atlas") {
            sourceSet(sourceSets.main.get())
            pureModules.forEach { sourceSet(project(it).sourceSets.main.get()) }
        }
    }
}

dependencies {
    pureModules.forEach { implementation(project(it)) }
}

val metadata = mapOf(
    "version" to project.version.toString(),
    "minecraft_range" to property("neoforge_minecraft_range").toString(),
)

tasks.processResources {
    inputs.properties(metadata)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(metadata)
    }
}

tasks.jar {
    archiveBaseName.set("atlas-neoforge")
    archiveVersion.set("${project.version}+$minecraftVersion")

    pureModules.forEach { from(project(it).sourceSets.main.get().output) }

    manifest {
        attributes("Implementation-Version" to project.version)
    }
}
