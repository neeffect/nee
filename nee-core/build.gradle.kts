

dependencies {
    api (Libs.Vavr.kotlin) {
        exclude("org.jetbrains.kotlin")
    }
    implementation(Libs.Haste.haste)
    testImplementation (Libs.Kotest.runnerJunit5Jvm)
    testImplementation (Libs.Kotest.assertionsCoreJvm)

    implementation (Libs.Kotlin.kotlinStdLib)
}


