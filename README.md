<div align="center">

<img src="docs/logo.svg" alt="Player Owl logo" width="120">

# Player Owl

**A tiny Android video player. No ads. No tracking. No internet permission.**

[![Tech stack](https://skillicons.dev/icons?i=kotlin,androidstudio,gradle,git)](https://skillicons.dev)

![APK size](https://img.shields.io/badge/APK-1.3_MB-9CC2FF?style=for-the-badge)
![Ads](https://img.shields.io/badge/ads-none-2ECC71?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Engine](https://img.shields.io/badge/Media3-ExoPlayer-7F52FF?style=for-the-badge)

</div>

## Gestures

| Gesture | Action |
|:---|:---|
| Single tap | Show or hide the controls |
| Swipe up or down on the right | Volume |
| Swipe up or down on the left | Brightness |
| Drag left or right, starting away from the side edges | Seek |

The back gesture works from the left and right edges of the screen, so a drag that starts there does not seek.

## Controls

Play or pause, and step to the previous or next video in the same folder. The buttons sit just above the timeline, at the bottom of the screen.

## Resume

Close the app at any moment, with or without pausing. The last video you watched picks up where you left it.

## Plays

MP4, MKV, WebM, MOV, TS, AVI, FLV and MPG, including AC3, E-AC3, DTS and TrueHD audio.
Video decoding uses your phone's hardware.

## Build

Open the project in Android Studio and press Run, or use the command line:

```sh
./gradlew test             # run the unit tests
./gradlew assembleRelease  # app/build/outputs/apk/release/app-release.apk
```

## Release

Pushing a tag such as `v1.1.0` runs the tests, builds a signed APK and publishes it as a GitHub release. Increase `versionCode` and `versionName` in `app/build.gradle.kts` first, or phones will not offer the new APK as an update.

The workflow needs these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Keep the keystore safe, because every future update must be signed with the same key.

## Learn more

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces connect.

## License note

The FFmpeg audio decoder is GPLv3, so if you publish this app, you must also publish its source.
