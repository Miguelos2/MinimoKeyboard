plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miguel.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.miguel.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 39
        versionName = "1.49"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_22
        targetCompatibility = JavaVersion.VERSION_22
    }

    kotlinOptions { jvmTarget = "22" }
}
