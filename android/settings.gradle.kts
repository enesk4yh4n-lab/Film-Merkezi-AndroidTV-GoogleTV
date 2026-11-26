pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") } }
    }
}
rootProject.name = "FilmMerkeziAndroid"
include(":native")
if (providers.gradleProperty("includeYoutubeProbe").orNull == "true") include(":youtubeProbe")
// Historical experiments are opt-in and cannot affect a normal Android build.
if (providers.gradleProperty("includeSpike").orNull == "true") include(":app")
