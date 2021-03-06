object Ci {

    // this is the version used for building snapshots
    // .buildnumber-snapshot will be appended
    private const val snapshotBase = "0.6.8"

    private val githubBuildNumber = System.getenv("GITHUB_RUN_NUMBER")

    private val snapshotVersion = when (githubBuildNumber) {
        null -> "$snapshotBase-LOCAL"
        else -> "$snapshotBase.${githubBuildNumber}-SNAPSHOT"
    }

    private val releaseVersion = System.getenv("RELEASE_VERSION")

    val isRelease = releaseVersion != null
    val publishVersion = (releaseVersion ?: snapshotVersion).also {
        println("release is ${releaseVersion}")
        println("publishVersion is ${it}")
    }
}


