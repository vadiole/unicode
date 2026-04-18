## Project
- An Android app for searching, viewing, and copying characters from Unicode.
- Kotlin, minSdk 26, UI with code and custom views, minimal dependencies, single activity.

## General
- Application-scoped singletons `unicodeStorage`, `userConfig`, `recentRepository` provided via the `UnicodeApp` companion object.
- Data comes from a read-only database, `assets/u17.sqlite.zip`, unzipped into `filesDir/sql/` on first open.
- Screens extend `Screen : FrameLayout, CoroutineScope` and cancel their scope in `onDetachedFromWindow`.
- UI is views in kotlin, navigation via custom `NavigationView : FrameLayout`.
- Motion uses spring animations (`androidx.dynamicanimation`), rounded surfaces use Squircle.
- A plain `Controller` class owns data for a screen; a `Delegate` interface forwards events up.
- Performance is high: no heavy operations in often called methods like drawing or touch.
- Memory usage: no boxing for large data, object reuse if possible.
- `ui/common/`: UI component library, e.g. `Dp.kt`, `Typeface.kt`, `Screen.kt`, `Squircle.kt`, `TopBar.kt` etc.
- colors in `colors.xml`
- no tests, no xml layouts, no compose, no ContextCompat
- normal gradle build and launch

## UI interaction quality
- Hit area ≥ 44dp even when the visual is smaller — expand touch, not visuals.
- Edge-adjacent draggables: set `systemGestureExclusionRects` (max 200 dp) so the system gesture doesn't steal the touch.
- Haptics on meaningful boundaries (selection change, threshold cross), throttled (~17ms on API 34+, ~30ms below).
- Non-spring motion uses overshoot/decelerate interpolators; linear only for cross-fades.
- Direct manipulation: when grabbing an existing element, preserve the touch offset so it stays under the finger;
- Reference: `ui/table/FastScrollView.kt`.
