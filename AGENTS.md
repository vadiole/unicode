## Unicode App

- An Android app for searching, viewing, and copying characters from Unicode.
- Kotlin, minSdk 23, UI with code and custom views, minimal dependencies, single activity.

## General

- Application-scoped singletons `unicodeStorage`, `userConfig`, `recentRepository` provided via the `UnicodeApp` companion object.
- Data comes from one flat file, `assets/u18.bin`, written by `vadiole/generator` (`build.sh`) and read into memory once per process by `UnicodeStorage` (`data/UnicodeData.kt`); CJK meanings are read on demand.
- Search is an in-memory word index (`search/`, plain Kotlin) built on the device, cached in `filesDir/cache/search` and queried on its own thread by `unicodeStorage.search`; see `docs/search.md`.
- Screens extend `Screen : FrameLayout, CoroutineScope` and cancel their scope in `onDetachedFromWindow`.
- UI is views in kotlin, navigation via custom `NavigationView : FrameLayout`.
- `Nav` manages the screen stack and screen transitions, including predictive back; `NavigationView` coordinates screens and the character details sheet.
- Motion uses our own springs (`ui/common/Spring.kt` core, `SpringAnimator` frame driver), rounded surfaces use Squircle.
- A plain `Controller` class owns data for a screen; a `Delegate` interface forwards events up.
- Performance is high: no heavy operations in often called methods like drawing or touch.
- Memory usage: no boxing for large data, object reuse if possible.
- `ui/common/`: UI component library, e.g. `Dimensions.kt`, `View.kt`, `Typeface.kt`, `Screen.kt`, `Squircle.kt`, `TopBar.kt` etc.
- colors in `colors.xml`
- no xml layouts, no compose, no ContextCompat
- Tests are the search regression suite (`docs/search.md`) and the spring contract (`SpringTest`): JVM tests in `app/src/testDebug` run with `./gradlew testDebugUnitTest`, JUnit is the only test dependency. Do not add other tests without asking.
- After changing search or its data run `tools/search-golden.sh verify <serial>`.
- After completing a task or fix, build and launch the app: `./gradlew installDebug && adb shell am start -n vadiole.unicode.debug/vadiole.unicode.ui.UnicodeActivity` 
- Device testing (debug config presets, per-app language, screenshots, search goldens): `docs/testing.md`.
- Release: `./gradlew assembleRelease bundleRelease`, signed from `unicode.signing.*` in `~/.gradle/gradle.properties`; the R8 mapping is copied to `app/mapping/`.

## UI interaction quality

- Hit area ≥ 44dp even when the visual is smaller — expand touch, not visuals.
- Edge-adjacent draggables: set `systemGestureExclusionRects` (max 200 dp) so the system gesture doesn't steal the touch.
- Haptics on meaningful boundaries (selection change, threshold cross), throttled (~17ms on API 34+, ~30ms below).
- Non-spring motion uses overshoot/decelerate interpolators; linear only for cross-fades.
- Direct manipulation: when grabbing an existing element, preserve the touch offset so it stays under the finger;
- Reference: `ui/table/FastScrollView.kt`.

## Git

- `dev` is the default branch to branch from
- Name branches simply, after the user's request. Each branch is a new scope, so agree on the name with the user first.
- Commit finished work, scoped so the history reads like a changelog; amend or squash when it makes sense. Messages are one-line, lowercase, high-level goals or outcomes, e.g. `added the new settings screen with customization options`, `fixed caching`, `simplified code so it's easier to support`.
- Stage explicit paths; don't commit unrelated changes.
- Create PRs on explicit request from the user, or with an explicit ok when it's the only step left.
- PR description is a minimal one-sentence summary of the outcome, plus risks or critical info.

## Meta rules

- Suggest reasonable updates to AGENTS.md and docs as the project evolves.