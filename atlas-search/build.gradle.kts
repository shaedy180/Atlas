plugins {
    `java-library`
}

dependencies {
    api(project(":atlas-core"))
    compileOnly("org.jetbrains:annotations:26.0.2")
}
