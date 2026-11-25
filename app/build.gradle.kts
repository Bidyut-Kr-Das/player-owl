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
        versionCode = 2
        versionName = "1.1"
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
    // 1.11 reads the 'mfra' box, so fragmented MP4 downloads without a 'sidx' index can seek.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    // Bluetooth, headset and lock screen play, pause, next and previous.
    implementation("androidx.media3:media3-session:1.11.1")
    // Prebuilt FFmpeg audio decoders: AC3, E-AC3, DTS, TrueHD. Phones often lack these, and MKV files often use them.
    // The version should match media3. shortcut: no 1.11 build exists yet, so 1.9.0+1 runs on media3 1.11.1,
    // not yet tested with an AC3/DTS file; move to the matching version when jellyfin publishes it.
    // GPLv3: publishing the app means publishing its source.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    testImplementation("junit:junit:4.13.2")
}
