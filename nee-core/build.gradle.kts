dependencies {
    api(Libs.Vavr.kotlin) {
        exclude("org.jetbrains.kotlin")
    }
    api(Libs.Haste.haste)

    implementation(Libs.Jackson.jacksonAnnotations)
    implementation(Libs.Kotlin.kotlinStdLib)
    implementation(Libs.Kotlin.coroutinesTest)
    implementation(Libs.Atomic.atomicFu)
    testImplementation(project(":nee-test:nee-core-test"))
    testImplementation(Libs.Kotest.runnerJunit5Jvm)
    testImplementation(Libs.Kotest.assertionsCoreJvm)
}


apply(from = "../publish-mpp.gradle.kts")
