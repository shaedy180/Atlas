plugins {
    id("net.fabricmc.fabric-loom")
}

// Client code shared by both loaders. It only sees vanilla Minecraft, so a loader
// specific import fails here first. The loaders compile these sources again
// against their own game jar.
val compat = property("compat") as String

sourceSets.main {
    java.srcDir("src/compat/$compat/java")
}

dependencies {
    "minecraft"("com.mojang:minecraft:${property("minecraft_version")}")
    compileOnly("net.fabricmc:fabric-loader:${property("loader_version")}")

    implementation(project(":atlas-core"))
    implementation(project(":atlas-api"))
    implementation(project(":atlas-search"))
    implementation(project(":atlas-ui"))
}

loom {
    runConfigs.configureEach {
        isIdeConfigGenerated = false
    }
}
