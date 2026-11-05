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

## Screenshots

```sh
adb exec-out screencap -p > shot.png
```

Open a character's details with the share link, combined with a preset if needed:

```sh
adb shell am start -S -n $A -a android.intent.action.VIEW -d "https://unicode.vadiole.me/share?c=945" --es debug.preset all
```

The settings and search buttons have content descriptions, so `uiautomator dump` gives their bounds for `adb shell input tap`. Character cells have none.
