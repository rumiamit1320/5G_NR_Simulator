plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("com.example.nrsimulator.CoreRegressionMainKt")
}

/** Runs the nr-core regression gate (CoreRegressionMain) on the main source set. */
tasks.register<JavaExec>("coreRegression") {
    group = "verification"
    description = "Runs the nr-core regression gate (CoreRegressionMain)."
    mainClass.set("com.example.nrsimulator.CoreRegressionMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}