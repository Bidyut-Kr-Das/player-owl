plugins {
    id("com.android.application")
}

android {
    namespace = "com.quietplayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.quietplayer"
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
            // ponytail: debug key is enough to sideload on your own phone; add a real keystore before publishing.
            signingConfig = signingConfigs.getByName("debug")
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
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    // Prebuilt FFmpeg audio decoders: AC3, E-AC3, DTS, TrueHD. Phones often lack these, and MKV files often use them.
    // The version must match media3. GPLv3: publishing the app means publishing its source.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    testImplementation("junit:junit:4.13.2")
}
