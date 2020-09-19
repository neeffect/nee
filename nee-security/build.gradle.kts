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
    testImplementation (Libs.Kotest.runnerJunit5Jvm)
}



