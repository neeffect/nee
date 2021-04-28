import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}



dependencies {
    api(project(":nee-ctx-web-ktor"))
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(Libs.Ktor.serverCore)
    implementation(Libs.Ktor.serverTestHost)
    implementation(Libs.Ktor.jackson)
    implementation(Libs.Vavr.jackson)
    implementation(Libs.Jackson.jacksonModuleKotlin)
}


apply(from = "../../publish-mpp.gradle.kts")
