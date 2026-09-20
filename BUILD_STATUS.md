# Build status

This repository is configured for Android 13+ (API 33+) and targets Android 16 (API 36).

The environment used to prepare this package could not resolve external build hosts, so a local APK build could not be verified here. The included GitHub Actions workflow installs Android SDK 36 and Gradle 8.13 and builds `app-debug.apk` in the cloud.

Important implementation note: processed RGB/R/G/B live analysis and saving of the *filtered* frame are implemented. RAW capability detection is implemented, but a true RAW_SENSOR capture/demosaic pipeline is intentionally not presented as complete until it can be compiled and tested on hardware; RAW output is device-dependent and should not be treated as a guaranteed live preview.
