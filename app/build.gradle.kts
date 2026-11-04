plugins {
    id("com.android.application")
}

android {
    namespace = "com.playerowl"
    compileSdk {
        version = release(37)
    }

    // Release signing comes from the environment, so the keystore and its passwords never touch git.
    val keystore = System.getenv("PLAYEROWL_KEYSTORE")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("PLAYEROWL_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("PLAYEROWL_KEY_ALIAS")
            keyPassword = System.getenv("PLAYEROWL_KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.playerowl"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // A local build without the keystore falls back to the debug key, to sideload on your own phone.
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
            ndk {
                // ponytail: 64-bit only keeps the APK small; add armeabi-v7a if you ever target old 32-bit phones.
                abiFilters += "arm64-v8a"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Compressing halves the APK. Android then unpacks the native library at install,
        // so the installed app takes more space than an uncompressed build would.
        resources.excludes += "kotlin/**"
        dex.useLegacyPackaging = true
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    // Prebuilt FFmpeg audio decoders: AC3, E-AC3, DTS, TrueHD. Phones often lack these, and MKV files often use them.
    // The version must match media3. GPLv3: publishing the app means publishing its source.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    testImplementation("junit:junit:4.13.2")
}
