# Repository Guidelines

## Project Structure & Module Organization
- Root module: `:app` (Android, Kotlin, Jetpack Compose).
- Source: `app/src/main` (`AndroidManifest.xml`, `res/`, `assets/`).
- Tests: unit `app/src/test`, instrumentation/UI `app/src/androidTest`.
- Assets: `app/src/main/assets/bible_verses.json`.
- Build scripts: `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- Build debug APK: `./gradlew :app:assembleDebug` (Windows: `gradlew.bat`).
- Run unit tests: `./gradlew :app:testDebugUnitTest`.
- Run instrumentation/UI tests (device/emulator required): `./gradlew :app:connectedAndroidTest`.
- Clean project: `./gradlew clean`.
- Common output: `app/build/outputs/apk/debug/`.

## Coding Style & Naming Conventions
- Language: Kotlin (JVM 11), UI with Compose, MVVM + clean architecture.
- Indentation: 4 spaces; line width ~100–120 chars.
- Names: `PascalCase` for Composables/classes, `camelCase` for functions/vars, `UPPER_SNAKE_CASE` for constants, packages lowercase.
- Compose: prefer small, previewable Composables; hoist state; use `@Preview` where feasible.
- Persistence: Room + KSP; keep `@Entity`, `@Dao` in `data` layer; map to domain models.

## Testing Guidelines
- Frameworks: JUnit4, MockK, kotlinx-coroutines-test, Ktor client mock; UI: Espresso + Compose UI test.
- Naming: mirror package, suffix `*Test.kt` (unit) and `*AndroidTest.kt` (UI/instrumentation).
- Coroutines: use `runTest` and TestDispatchers; avoid real delays/network.
- UI: prefer semantics selectors; keep tests deterministic and hermetic.

## Commit & Pull Request Guidelines
- Messages: imperative, concise, scope-first (e.g., "Settings: persist theme on exit").
- Group related changes; keep diffs minimal; include rationale in body when needed.
- PRs: clear description, linked issues (`Closes #123`), screenshots for UI changes (before/after), and test coverage for new logic.

## Security & Configuration Tips
- Secrets plugin loads keys from `secrets.properties` (not committed). Example:
  - `GEMINI_API_KEY=...`, `ESV_BIBLE_API_KEY=...`.
- Do not hardcode tokens or check in OAuth JSON; prefer `local.properties`/`secrets.properties`.
- Validate licenses and avoid shipping large unused assets; lint with `./gradlew lint` when applicable.

