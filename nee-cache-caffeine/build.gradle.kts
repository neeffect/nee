import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}


dependencies {
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    implementation(project(":nee-core"))
    api("com.github.ben-manes.caffeine:caffeine:2.5.5")
}

apply(from = "../publish-mpp.gradle.kts")
