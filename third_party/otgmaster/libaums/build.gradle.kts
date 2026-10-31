plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }
    namespace = "me.jahnen.libaums.core"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 34

        externalNativeBuild {
            cmake {
                abiFilters += listOf("armeabi-v7a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api("androidx.annotation:annotation:1.6.0")
    api("androidx.core:core-ktx:1.9.0")
}
