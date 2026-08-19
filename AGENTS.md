# Project Build Notes

- Gradle's `compileSdk { version = release(37) }` requires the SDK package
  `platforms;android-37.0` and Build Tools `37.0.0`.
- The release variant currently uses `signingConfigs["debug"]`. APKs are
  installable, but builds made with different debug keystores cannot update
  each other without uninstalling the existing package first.
