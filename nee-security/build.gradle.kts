import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":nee-core"))
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(Libs.Vavr.kotlin) {
            exclude("org.jetbrains.kotlin")
    }
    implementation(Libs.Ktor.clientCore)
    implementation(Libs.Ktor.clientJsonJvm)

    implementation(Libs.Ktor.clientJackson) {
        exclude("org.jetbrains.kotlin")
    }
    implementation(Libs.Kotlin.coroutinesJdk8)
    implementation("io.fusionauth:fusionauth-jwt:4.0.1")
    // this is breaking xml parsers
    // implementation("com.uchuhimo:konf:0.23.0")
    testImplementation (Libs.Kotest.runnerJunit5Jvm)
    testImplementation(Libs.Ktor.clientMockJvm)
}

apply(from = "../publish-mpp.gradle.kts")

