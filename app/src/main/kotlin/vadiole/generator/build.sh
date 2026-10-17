#!/bin/sh
# Usage: build.sh <db name>, e.g. build.sh u18
# Inputs from https://www.unicode.org/Public/<version>/ucd/:
#   input/data.txt = UnicodeData.txt, input/block.txt = Blocks.txt, input/version.txt = DerivedAge.txt
set -e
name=${1:?db name}
root=app/src/main/kotlin/vadiole/generator
cd "$(dirname "$0")/../../../../../.."
tmp=$(mktemp -d)
kotlinc "$root/generator.kt" -include-runtime -d "$tmp/generator.jar" 2>/dev/null
java -jar "$tmp/generator.jar"
sqlite3 "$tmp/$name.sqlite" <<SQL
CREATE TABLE IF NOT EXISTS "block" (
    "id"         INTEGER NOT NULL,
    "name"       TEXT NOT NULL,
    "start"      INTEGER NOT NULL,
    "end"        INTEGER NOT NULL,
    PRIMARY KEY("id")
);
CREATE TABLE IF NOT EXISTS "char" (
    "id"         INTEGER NOT NULL,
    "code_point" INTEGER NOT NULL,
    "name"       TEXT NOT NULL,
    "name2"      TEXT,
    "version"    TEXT NOT NULL,
    "block_id"   INTEGER, category TEXT,
    PRIMARY KEY("id")
);
.mode csv
.separator ";"
.import $root/output/block.csv block
.import $root/output/char.csv char
VACUUM;
SQL
rm -f "app/src/main/assets/$name.sqlite.zip"
(cd "$tmp" && zip -q -9 "$OLDPWD/app/src/main/assets/$name.sqlite.zip" "$name.sqlite")
rm -rf "$tmp"
