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
include(":pluginApi")
val addonRuntimeDir=System.getenv("FM_ADDONS_DIR")?.let{file(it).resolve("runtime")}?:file("../../Android-Eklentiler/runtime")
if(addonRuntimeDir.exists()){include(":addonRuntime");project(":addonRuntime").projectDir=addonRuntimeDir}
if (providers.gradleProperty("includeYoutubeProbe").orNull == "true") include(":youtubeProbe")
// Historical experiments are opt-in and cannot affect a normal Android build.
if (providers.gradleProperty("includeSpike").orNull == "true") include(":app")
