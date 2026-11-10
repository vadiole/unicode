#!/bin/sh
# Usage: build.sh
# Writes app/src/main/assets/u18.bin from the files in input/. A missing input is downloaded from the versioned URLs below
# and checked for its end marker. names.txt and unihan.txt are filtered extracts of NamesList.txt and Unihan.zip.
set -e
ucd=https://www.unicode.org/Public/18.0.0/ucd
cldr=https://raw.githubusercontent.com/unicode-org/cldr/release-49/common
root=app/src/main/kotlin/vadiole/generator
cd "$(dirname "$0")/../../../../../.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fetch() {
    curl -fsSL --retry 3 -o "$tmp/$1" "$2"
    tail -c 200 "$tmp/$1" | grep -q "$3" || { echo "incomplete download: $2" >&2; exit 1; }
}

plain() {
    [ -f "$root/input/$1" ] && return
    fetch "$1" "$2" "$3"
    mv "$tmp/$1" "$root/input/$1"
}

plain data.txt "$ucd/UnicodeData.txt" '^10FFFD;'
plain block.txt "$ucd/Blocks.txt" '# EOF'
plain version.txt "$ucd/DerivedAge.txt" '# EOF'
plain aliases.txt "$ucd/NameAliases.txt" '# EOF'
plain annotations.xml "$cldr/annotations/en.xml" '</ldml>'
if [ ! -f "$root/input/names.txt" ]; then
    fetch NamesList.txt "$ucd/NamesList.txt" '^10FFFF'
    awk '/^@@\t/ || /^@\t\t/ || /^\t= / { print; next } /^[0-9A-F]+\t/ { print substr($0, 1, index($0, "\t") - 1) }' "$tmp/NamesList.txt" > "$root/input/names.txt"
fi
if [ ! -f "$root/input/unihan.txt" ]; then
    curl -fsSL --retry 3 -o "$tmp/Unihan.zip" "$ucd/Unihan.zip"
    unzip -q "$tmp/Unihan.zip" Unihan_Readings.txt Unihan_DictionaryLikeData.txt Unihan_OtherMappings.txt -d "$tmp/unihan"
    for file in Readings DictionaryLikeData OtherMappings; do
        tail -c 200 "$tmp/unihan/Unihan_$file.txt" | grep -q '# EOF' || { echo "incomplete Unihan_$file.txt" >&2; exit 1; }
    done
    {
        grep -h "	kDefinition	" "$tmp/unihan/Unihan_Readings.txt"
        grep -hE "	(kUnihanCore2020|kGradeLevel|kJoyoKanji|kKoreanEducationHanja|kHanyuPinlu)	" "$tmp/unihan/Unihan_Readings.txt" "$tmp/unihan/Unihan_DictionaryLikeData.txt" "$tmp/unihan/Unihan_OtherMappings.txt" | cut -f1,2
    } > "$root/input/unihan.txt"
fi

kotlinc "$root/generator.kt" -include-runtime -d "$tmp/generator.jar" 2>/dev/null
java -jar "$tmp/generator.jar"
