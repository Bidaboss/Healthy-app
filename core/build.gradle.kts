import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Les tests relisent les exemples officiels de docs/examples.
    systemProperty("healthy.examplesDir", rootProject.file("docs/examples").absolutePath)
    inputs.dir(rootProject.file("docs/examples"))
    // Test réseau Open Food Facts : seulement à la demande (workflow manuel).
    val offLive = System.getenv("HEALTHY_OFF_LIVE") ?: "false"
    systemProperty("healthy.offLive", offLive)
    inputs.property("healthy.offLive", offLive)
    testLogging { showStandardStreams = true }
}
