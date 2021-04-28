object Libs {
    const val kotlin_version = "1.4.32"

    object Atomic {
        private const val version = "0.15.0"
        const val atomicFu = "org.jetbrains.kotlinx:atomicfu:$version"
    }

    object H2 {
        private const val version = "1.4.200"
        const val h2 = "com.h2database:h2:$version"
    }

    object Kotlin {
        const val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
        private const val coroutinesVersion = "1.4.2"
        const val coroutinesJdk8 = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion"
        const val reflect = "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
        const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion"
    }

    object Ktor {
        private const val version = "1.5.1"
        const val serverCore = "io.ktor:ktor-server-core:$version"
        const val serverHostCommon = "io.ktor:ktor-server-host-common:$version"
        const val serverNetty = "io.ktor:ktor-server-netty:$version"
        const val clientCore = "io.ktor:ktor-client-core:$version"
        const val clientMockJvm = "io.ktor:ktor-client-mock-jvm:$version"
        const val clientJsonJvm = "io.ktor:ktor-client-json-jvm:$version"
        const val clientJson = "io.ktor:ktor-client-json:$version"
        const val clientJackson = "io.ktor:ktor-client-jackson:$version"
        const val jackson = "io.ktor:ktor-jackson:$version"
        const val serverTestHost = "io.ktor:ktor-server-test-host:$version"
    }

    object Vavr {
        private const val version = "0.10.2"
        const val kotlin = "io.vavr:vavr-kotlin:$version"
        const val jackson = "io.vavr:vavr-jackson:$version"
    }

    object Haste {
        private const val version = "0.3.1"
        const val haste = "io.github.krasnoludkolo:haste:$version"
    }

    object Jackson {
        private const val version = "2.12.1"
        const val jacksonModuleKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:$version"
        const val jacksonAnnotations = "com.fasterxml.jackson.core:jackson-annotations:$version"
        const val jacksonJsr310 = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.0"

    }

    object Kotest {
        private const val version = "4.4.1"
        const val runnerJunit5Jvm = "io.kotest:kotest-runner-junit5-jvm:$version"
        const val assertionsCoreJvm = "io.kotest:kotest-assertions-core-jvm:$version"
    }

    object Slf4J {
        private const val version = "1.7.30"
        const val api = "org.slf4j:slf4j-api:$version"
    }


    object Liquibase {
        private const val version = "4.3.1"
        const val core = "org.liquibase:liquibase-core:$version"
    }

    object Hoplite {
        private const val version = "1.4.0"
        const val core = "com.sksamuel.hoplite:hoplite-core:$version"
        const val yaml = "com.sksamuel.hoplite:hoplite-yaml:$version"
    }
}
