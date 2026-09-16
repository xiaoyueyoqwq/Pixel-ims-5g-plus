# Project Build Notes

- Gradle's `compileSdk { version = release(37) }` requires the SDK package
  `platforms;android-37.0` and Build Tools `37.0.0`.
- Release signing uses a private keystore, never `debug.keystore`.
  CI reads `SIGNING_KEYSTORE` (base64), `SIGNING_KEY_ALIAS`,
  `SIGNING_STORE_PASSWORD`, and `SIGNING_KEY_PASSWORD` from Actions
  repository secrets. Local `assembleRelease` can use gitignored
  `keystore.properties` (see `keystore.properties.example`).
- If those values are missing, `assembleRelease` falls back to the
  debug keystore so PRs still compile. Tagged `v*` releases must not
  ship that fallback.
- Switching from a debug-signed install to the release key requires
  uninstalling the existing package first. Debug APKs cannot update a
  release-signed install.
