plugins {
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion = property("minecraft_version") as String
val common = project(":atlas-common")
val pureModules = listOf(":atlas-core", ":atlas-api", ":atlas-search", ":atlas-ui")

sourceSets.main {
    java.srcDir(common.file("src/main/java"))
    java.srcDir(common.file("src/compat/${property("compat")}/java"))
    java.srcDir("src/textinput/${property("fabric_text_input")}/java")
    resources.srcDir(common.file("src/main/resources"))
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    pureModules.forEach { implementation(project(it)) }
}

loom {
    runs {
        named("client") {
            client()
            configName = "Atlas Client"
        }
        named("server") {
            server()
            configName = "Atlas Server"
        }
    }
}

val metadata = mapOf(
    "version" to project.version.toString(),
    "minecraft_range" to property("fabric_minecraft_range").toString(),
    "loader_min" to property("fabric_loader_min").toString(),
)

tasks.processResources {
    inputs.properties(metadata)
    filesMatching("fabric.mod.json") {
        expand(metadata)
    }
}

tasks.jar {
    archiveBaseName.set("atlas-fabric")
    archiveVersion.set("${project.version}+$minecraftVersion")

    pureModules.forEach { from(project(it).sourceSets.main.get().output) }

    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

// In-game test: ./gradlew :atlas-loader-fabric:runClientGameTest -Pmc=<version>
// Starts the client, creates a world, walks through the Atlas screens and saves
// screenshots to atlas-loader-fabric/build/run/clientGameTest/screenshots. Needs a display.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "atlas-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}
