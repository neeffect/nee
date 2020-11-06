import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}
dependencies {
    api(project(":nee-core"))
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(group = "com.mchange", name = "c3p0", version = "0.9.5.5")
    testRuntimeOnly(Libs.H2.h2)

    testImplementation(Libs.Kotest.runnerJunit5Jvm)
}

apply(from = "../publish-mpp.gradle.kts")
