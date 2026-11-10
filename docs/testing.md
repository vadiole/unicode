# Testing on a device

## Debug config

Debug builds apply `debug.*` launch extras to the app's settings before the first screen is built. `-S` force-stops the app first, so every launch is a cold start with the new state.

```sh
A=vadiole.unicode.debug/vadiole.unicode.ui.UnicodeActivity
adb shell content call --uri content://vadiole.unicode.debug.debugconfig --method help
adb shell am start -S -n $A --es debug.preset legacy --ez debug.searchAtTop true
```

`help` lists the presets with what each sets, and every setting with its `am` flag and current value.

## Per-app language

```sh
adb shell cmd locale set-app-locales vadiole.unicode.debug --locales pt-BR
adb shell cmd locale set-app-locales vadiole.unicode.debug --locales ""
```

The tag is BCP 47 (`pt-BR`, `zh-CN`), not the resource qualifier (`pt-rBR`). The first launch after an install or a locale change can still render the previous language, so check the visible text with `adb shell uiautomator dump` before taking a screenshot.

## Languages

The index key and where it came from are logged in debug builds:

```sh
adb logcat -s UnicodeSearch | grep "index "
```

`index en,de@<crc> ready from build` after a language change, `from cache` on the next launches, `index en` for English and for a language whose split is not installed. To test a missing split, build `./gradlew bundleDebug`, then `bundletool build-apks --connected-device` with a device spec that lists only some locales and `bundletool install-apks`.

## Screenshots

```sh
adb exec-out screencap -p > shot.png
```

Open a character's details with the share link, combined with a preset if needed:

```sh
adb shell am start -S -n $A -a android.intent.action.VIEW -d "https://unicode.vadiole.me/share?c=945" --es debug.preset all
```

The settings and search buttons have content descriptions, so `uiautomator dump` gives their bounds for `adb shell input tap`. Character cells have none.

## Search goldens

```sh
./gradlew testDebugUnitTest
tools/search-golden.sh verify <serial>
adb shell content call --uri content://vadiole.unicode.debug.searchgolden --method time
adb shell content call --uri content://vadiole.unicode.debug.searchgolden --method memory
```

The first runs the suite on the JVM and writes `app/build/reports/search/verify.txt`. `verify` installs the debug build and runs the same suite on the device, with every character shown and English only. `time` and `memory` include the language the running app has loaded, so open the app first. `--method find --arg <query>` searches with the running app's index and prints its language key, the first results and what the first one matched by. `time` reports the median and the slowest query of the suite, `memory` the Java heap held by the data, the index and the CJK meanings. `docs/search.md` describes the files and what to do after an intended change.
