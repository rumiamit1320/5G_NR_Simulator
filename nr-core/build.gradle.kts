plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

application {
    mainClass.set("com.example.nrsimulator.CoreRegressionMainKt")
}
