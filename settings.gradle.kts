pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForge" }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get()
    }
}

rootProject.name = "Atlas"

include("atlas-core")
include("atlas-api")
include("atlas-search")
include("atlas-ui")
include("atlas-loader-fabric")
// include("atlas-loader-neoforge") // Uncomment when NeoForge port begins
