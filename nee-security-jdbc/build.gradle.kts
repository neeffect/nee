import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":nee-core"))
    api(project(":nee-security"))
    implementation(project(":nee-jdbc"))

    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(Libs.Kotlin.kotlinStdLib)

    implementation(Libs.Vavr.kotlin) {
        exclude("org.jetbrains.kotlin")
    }
    testImplementation(project(":nee-test:nee-security-jdbc-test"))
    testImplementation(Libs.Kotest.runnerJunit5Jvm)
    testRuntimeOnly(Libs.H2.h2)
    testImplementation(Libs.Liquibase.core)

}



apply(from = "../publish-mpp.gradle.kts")
