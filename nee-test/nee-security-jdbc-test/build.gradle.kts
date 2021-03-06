import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":nee-jdbc"))
    api(project(":nee-security-jdbc"))
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(Libs.Kotlin.kotlinStdLib)
    implementation(Libs.Vavr.kotlin) {
        exclude("org.jetbrains.kotlin")
    }
    implementation(Libs.Liquibase.core)
    runtime(Libs.H2.h2)
}

apply(from = "../../publish-mpp.gradle.kts")
