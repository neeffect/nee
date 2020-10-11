
object Libs {
    const val kotlin_version = "1.4.0"
    const val liquibase_version="3.6.1"
    const val h2_version="1.4.193"

    object H2 {
        private const val version = "1.4.193"
        const val  h2 = "com.h2database:h2:$version"
    }

    object Kotlin {
        const val kotlinStdLib =  "org.jetbrains.kotlin:kotlin-stdlib-jdk8"

    }

    object Ktor {
        private const val version = "1.4.0"
        const val serverCore = "io.ktor:ktor-server-core:$version"
        const val  serverTestHost ="io.ktor:ktor-server-test-host:$version"

    }

    object Vavr {
        private const val version = "0.10.2"
        const val kotlin = "io.vavr:vavr-kotlin:$version"
        const val jackson = "io.vavr:vavr-jackson:$version"
    }

    object Haste {
        private const val version = "0.2.1"
        const val haste = "io.github.krasnoludkolo:haste:$version"
    }

    object Jackson {
        private const val version = "2.11.3"
        const val jacksonModuleKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:$version"
    }

    object Kotest {
        private const val version = "4.2.5"
        private const val consoleVersion = "4.1.3.2"
        const val runnerJunit5Jvm ="io.kotest:kotest-runner-junit5-jvm:$version"
        const val assertionsCoreJvm = "io.kotest:kotest-assertions-core-jvm:$version"
        const val runnerConsoleJvm = "io.kotest:kotest-runner-console-jvm:${consoleVersion}"
    }

    object Slf4J {
        private const val version = "1.7.28"
        const val api =  "org.slf4j:slf4j-api:$version"
    }


    object Liquibase {
        private const val version = "3.6.1"
        const val core = "org.liquibase:liquibase-core:$version"
    }
}
