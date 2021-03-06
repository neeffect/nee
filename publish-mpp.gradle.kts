apply(plugin = "java")
apply(plugin = "java-library")
apply(plugin = "maven-publish")
apply(plugin = "signing")
apply(plugin = "org.jetbrains.dokka")
apply(plugin = "com.bmuschko.nexus")
repositories {
    mavenCentral()
}

val ossrhUsername: String by project
val ossrhPassword: String by project
val signingKey: String? by project
val signingPassword: String? by project

fun Project.publishing(action: PublishingExtension.() -> Unit) =
    configure(action)

fun Project.signing(configure: SigningExtension.() -> Unit): Unit =
    configure(configure)

val dokka = tasks.named("dokka")
val javadoc = tasks.named("javadoc")
//val sources = tasks.named("kotlinSourcesJar")

val publications: PublicationContainer = (extensions.getByName("publishing") as PublishingExtension).publications

signing {
    setRequired({
        (Ci.isRelease) && gradle.taskGraph.hasTask("publish")
    })

    if (signingKey != null && signingPassword != null) {
        @Suppress("UnstableApiUsage")
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    if (Ci.isRelease) {
        useGpgCmd()
        sign(publications)
    }
}

val javadocJar2 by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles java doc to jar"
    archiveClassifier.set("javadoc")
    from(javadoc)
}
val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(dokka)
}
//val sourcesJar by tasks.creating(Jar::class) {
//    group = JavaBasePlugin.DOCUMENTATION_GROUP
//    description = "Assembles sources"
//    archiveClassifier.set("sources")
//    from(the<SourceSetContainer>()["main"].allSource)
//}


publishing {
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            name = "deploy"
            url = if (Ci.isRelease) releasesRepoUrl else snapshotsRepoUrl
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: ossrhUsername
                password = System.getenv("OSSRH_PASSWORD") ?: ossrhPassword
            }
        }
    }

    publications.withType<MavenPublication>().forEach {
        it.apply {
            if (Ci.isRelease) {
                artifact(tasks["dokkaJar"])
            }
            artifact(tasks["kotlinSourcesJar"])

            pom {
                name.set("Neefect")
                description.set("Nee")
                url.set("http://www.github.com/neeffect/nee/")

                scm {
                    connection.set("scm:git:http://www.github.com/neeffect/nee/")
                    developerConnection.set("scm:git:http://github.com/jarekratajski/")
                    url.set("http://www.github.com/neeffect/nee/")
                }

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("jarekratajski")
                        name.set("Jarek Ratajski")
                        email.set("jratajski@gmail.com")
                    }
                }
            }
        }
    }
}
