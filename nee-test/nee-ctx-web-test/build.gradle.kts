import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}



dependencies {
    api(project(":nee-ctx-web-ktor"))
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(Libs.Ktor.serverCore)
    implementation(Libs.Ktor.serverTestHost)

}
