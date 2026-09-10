plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":nr-core"))
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("com.example.nrsimulator.web.WebServerKt")
}
