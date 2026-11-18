#!/bin/sh
set -eu

if [ $# -ne 2 ] || { [ "$1" != capture ] && [ "$1" != verify ]; }; then
    echo "usage: tools/search-golden.sh capture|verify <device serial>" >&2
    exit 2
fi

method=$1
serial=$2
root=$(cd "$(dirname "$0")/.." && pwd)
package=vadiole.unicode.debug
logs=${TMPDIR:-/tmp}/unicode-search-golden
mkdir -p "$logs"

cd "$root"
if ! ./gradlew assembleDebug > "$logs/gradle-assembleDebug.log" 2>&1; then
    tail -40 "$logs/gradle-assembleDebug.log" >&2
    exit 1
fi
adb -s "$serial" install -r app/build/outputs/apk/debug/*-debug.apk > "$logs/install.log"
adb -s "$serial" shell content call --uri content://$package.searchgolden --method "$method" | tee "$logs/$method.log"
grep -q "Result: Bundle" "$logs/$method.log"

if [ "$method" = capture ]; then
    adb -s "$serial" exec-out run-as $package cat files/search/goldens.txt > app/src/testDebug/resources/search/goldens.txt
    git --no-pager diff --stat -- app/src/testDebug/resources/search/goldens.txt
else
    adb -s "$serial" exec-out run-as $package cat files/search/verify.txt | tee "$logs/verify.txt"
    head -1 "$logs/verify.txt" | grep -q "^PASS"
fi
