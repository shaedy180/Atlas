plugins {
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion: String = rootProject.property("minecraft_version") as String
val fabricApiVersion: String = rootProject.property("fabric_api_version") as String
val loaderVersion: String = rootProject.property("loader_version") as String

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":atlas-core"))
    implementation(project(":atlas-api"))
    implementation(project(":atlas-search"))
    implementation(project(":atlas-ui"))
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

tasks.jar {
    archiveBaseName.set("atlas")

    from(project(":atlas-core").sourceSets.main.get().output)
    from(project(":atlas-api").sourceSets.main.get().output)
    from(project(":atlas-search").sourceSets.main.get().output)
    from(project(":atlas-ui").sourceSets.main.get().output)

    manifest {
        attributes("Implementation-Version" to project.version)
    }
}
