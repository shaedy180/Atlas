pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForge" }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get()
        id("net.neoforged.moddev") version providers.gradleProperty("moddev_version").get()
    }
}

rootProject.name = "Atlas"

// One build targets one Minecraft version. The values in versions/<mc>.properties
// are exposed to every project as regular properties.
val mc = providers.gradleProperty("mc").get()
val versionFile = file("versions/$mc.properties")
if (!versionFile.isFile) {
    val known = file("versions").listFiles().orEmpty().map { it.name.removeSuffix(".properties") }.sorted()
    throw GradleException("Unsupported Minecraft version '$mc'. Known versions: $known")
}
val mcProps = java.util.Properties().apply { versionFile.reader().use { load(it) } }
gradle.beforeProject {
    mcProps.forEach { (key, value) -> extensions.extraProperties[key.toString()] = value }
}

include("atlas-core")
include("atlas-api")
include("atlas-search")
include("atlas-ui")
include("atlas-common")
include("atlas-loader-fabric")
if (mcProps.containsKey("neoforge_version")) {
    include("atlas-loader-neoforge")
}
