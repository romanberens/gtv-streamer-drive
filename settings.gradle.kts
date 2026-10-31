pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
        }
    }
}

rootProject.name = "KidsTv"

include(":app")
include(":core:adminapi")
include(":core:catalog")
include(":core:common")
include(":core:diagnostics")
include(":core:models")
include(":core:persistence")
include(":core:playback")
include(":core:settings")
include(":profile-switcher")
include(":nas-browser")
