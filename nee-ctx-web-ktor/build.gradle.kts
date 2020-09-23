import org.jetbrains.kotlin.config.KotlinCompilerVersion


plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))

    api(project(":nee-core"))
    api(project(":nee-jdbc"))
    implementation(project(":nee-security"))
    implementation(project(":nee-security-jdbc"))
    implementation(project(":nee-cache-caffeine"))

    implementation(Libs.Ktor.serverCore)
    implementation(Libs.Vavr.jackson)

    testImplementation(project (":nee-test:nee-security-jdbc-test"))
    testImplementation(Libs.Ktor.serverTestHost)
    testImplementation(Libs.Kotest.runnerJunit5Jvm)
    //testImplementation("io.kotest:kotest-runner-console-jvm:$kotest_version")
    testImplementation(Libs.Kotest.runnerConsoleJvm)
}
apply(from = "../publish-mpp.gradle.kts")
