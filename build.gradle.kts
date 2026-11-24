plugins {
    id("net.fabricmc.fabric-loom") apply false
    `java-library`
}

val mod_version: String by project
val mod_group: String by project

allprojects {
    group = mod_group
    version = mod_version

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

// Plain Java modules carry the logic and have unit tests.
configure(subprojects.filter { it.name in setOf("atlas-core", "atlas-api", "atlas-search", "atlas-ui") }) {
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
