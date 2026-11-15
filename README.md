# BananaCard

BananaCard is a native Android (Kotlin + Jetpack Compose) port of FlashAI, a
flashcard app built on spaced repetition. Scheduling uses the FSRS-6 algorithm,
and all data stays on the device.

## Modules

- `:domain` is pure Kotlin/JVM with no Android dependency. It holds the FSRS-6
  port, review planning and streak logic.
- `:app` is the Android application (flavors `dev` and `prod`). It uses Room
  for storage, DataStore for settings, Hilt for dependency injection and
  Compose for the UI.

## Common commands

```bash
# Domain unit tests
./gradlew :domain:test

# App unit tests (Robolectric)
./gradlew :app:testDevDebugUnitTest

# Lint
./gradlew :app:lintDevDebug

# Build the dev debug APK
./gradlew :app:assembleDevDebug

# Install the dev debug build on a connected device or emulator
./gradlew :app:installDevDebug
```

The `ci` workflow (`.github/workflows/ci.yml`) runs the tests, lint and dev
debug build on every pull request and on pushes to `main`.

## FSRS fixtures

The domain tests compare the Kotlin FSRS port against reference outputs. To
regenerate those fixtures, see [tools/fsrs-fixtures/README.md](tools/fsrs-fixtures/README.md).

## Attribution

The FSRS port is derived from open-source FSRS implementations released under
the MIT license. The license text is in [third_party/fsrs/LICENSE](third_party/fsrs/LICENSE).

## Documentation

- [Android native port design](docs/superpowers/specs/2026-11-15-android-native-port-design.md)
- [Implementation plans](docs/superpowers/plans/)
