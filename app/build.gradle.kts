plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rabidstudios.punchtheclown"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rabidstudios.punchtheclown"
        minSdk = 26
        targetSdk = 36
        versionCode = 82
        versionName = "0.5.3-m5-alpha"
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
        create("playInternal") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("ciDebug")
            matchingFallbacks += listOf("release")
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
    implementation("com.google.android.gms:play-services-games-v2:22.1.0")
    implementation("com.google.android.gms:play-services-ads:24.8.0")
    implementation("com.android.billingclient:billing:9.1.0")
}
