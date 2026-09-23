plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rabidstudios.punchtheclown"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rabidstudios.punchtheclown"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.2.6-m2-beta"
    }

    signingConfigs {
        create("ciDebug") {
            // TEST BUILDS ONLY. Production signing must use a private release key.
            storeFile = file("../build-assets/punch-the-clown-ci-debug.keystore")
            storePassword = "punchtheclown"
            keyAlias = "punchdebug"
            keyPassword = "punchtheclown"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("ciDebug")
        }
        release {
            isMinifyEnabled = false
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
