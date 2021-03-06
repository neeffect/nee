import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":nee-core"))
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(Libs.Kotlin.reflect)
    implementation(Libs.Vavr.kotlin) {
        exclude("org.jetbrains.kotlin")
    }
    implementation(Libs.Ktor.clientCore)
    implementation(Libs.Ktor.clientJsonJvm)

    implementation(Libs.Ktor.clientJackson) {
        exclude("org.jetbrains.kotlin")
    }
    implementation(Libs.Kotlin.coroutinesJdk8)
    api("io.fusionauth:fusionauth-jwt:4.0.1")

    testImplementation(Libs.Kotest.runnerJunit5Jvm)
    testImplementation(Libs.Ktor.clientMockJvm)

    implementation(Libs.Hoplite.core)
    implementation(Libs.Hoplite.yaml)
}

apply(from = "../publish-mpp.gradle.kts")

