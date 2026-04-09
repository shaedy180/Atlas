plugins {
    `java-library`
}

dependencies {
    api(project(":atlas-core"))
    api(project(":atlas-search"))
    compileOnly("org.jetbrains:annotations:26.0.2")
}
