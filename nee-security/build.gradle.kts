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
    implementation("io.fusionauth:fusionauth-jwt:3.5.4")
    // this is breaking xml parsers
    // implementation("com.uchuhimo:konf:0.23.0")
    testImplementation (Libs.Kotest.runnerJunit5Jvm)
}

apply(from = "../publish-mpp.gradle.kts")

