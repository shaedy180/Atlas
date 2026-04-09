plugins {
    id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get() apply false
    `java-library`
}

val modVersion: String by project
val modGroup: String by project

allprojects {
    group = modGroup
    version = modVersion

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        val javaVersion = JavaVersion.toVersion(rootProject.property("java_version") as String)
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(rootProject.property("java_version") as String))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set((rootProject.property("java_version") as String).toInt())
    }

    tasks.withType<Jar> {
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${rootProject.property("mod_name")}" }
        }
    }
}
