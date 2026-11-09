# Search

The behaviour of search as of Unicode 18.0 data (`u18.sqlite`, 41341 rows), and the regression suite that pins it. A replacement engine is judged against this document and the goldens.

Paths are relative to `app/src/main/kotlin/vadiole/unicode/`.

## Flow

- `SearchField` reports the raw text after every change, untrimmed (`ui/common/SearchField.kt:21`). The field is single line and asks the keyboard for ASCII, but pasted text arrives as is.
- `TableScreen.runSearch` (`ui/table/TableScreen.kt:721`) shows recents for an empty query. Otherwise it runs `SearchController.search(query, 64)`, shows the result, then runs `search(query)` without a limit and shows that (`:735`, `:738`). A new keystroke cancels the job.
- `SearchController.search` (`ui/table/search/SearchController.kt:37`) decides how to read the query and calls `UnicodeStorage`.
- All SQL runs on one thread, as `rawQuery` with bound string arguments (`data/UnicodeStorage.kt`).

## Data

- Table `char(id, code_point, name, name2, version, block_id, category)`. `id` order equals code point order, so "by id" below means "by code point".
- `name` is the Unicode name, `name2` the Unicode 1.0 name, empty for all but 1917 rows. Both are upper case ASCII.
- CJK ideographs, Hangul syllables, Tangut, surrogates and private use exist only as two range endpoint rows each, named like `<CJK Ideograph, First>`. No other code point in those ranges can be found.
- There are no aliases, abbreviations, keywords or block names in the search data.

## Reading the query

In order, on `trimmed = query.trim()` (`SearchController.kt:44`):

1. Empty query: no results (`:38`).
2. Code query (`parseCodeQuery`, `:95`). The digits are what follows `U+`, `0x` or `\u` (explicit, any case), or what follows a bare leading `u`/`U`, or the whole input when it has at least 2 characters. It is a code query when the digits are 1 to 8 hex digits, the value is at most 10FFFF and, unless explicit, at least one digit is 0-9 (`:119`). So `face`, `cafe`, `bad`, `add`, `dead`, `beef` are words, while `41`, `e9`, `a1`, `1f600`, `u41` are code points. A single character is never a code query.
3. Explicit code query: only the code point lookup runs, no name search (`:46`).
4. Bare code query: code point lookup first, then the name search, with name results that fall in the code ranges removed (`:54`, `:74`).
5. Otherwise, a character literal: when the untrimmed query is exactly one code point, that character is the first result, followed by the name search without it (`:57`, `:147`).
6. Name search on the trimmed query, split on `\s+` (`:67`, `:154`). One token uses the single word ranking, more tokens use the multi-word ranking. Repeated tokens are kept.

A prefix followed by anything that is not a code query (`U+`, `0x`, `U+ZZ`, `U+2192 arrow`, 9 digits) falls through to steps 5 and 6 and is searched as a name.

### Code point lookup

`findCharsByCodePointPrefix` (`UnicodeStorage.kt:260`). The digits match the code point with that value, plus every code point whose 4, 5 or 6 digit form starts with the digits (`SearchController.kt:121`): `41` matches U+0041, U+4100..41FF and U+41000..41FFF. Digits with a leading zero are only padded to 4 digits: `00e9` matches U+00E9 alone, `04` matches U+0004 and U+0400..04FF. Order: the exact value first, then ascending code point (`UnicodeStorage.kt:275`).

### Name matching

A row matches when every token is a substring of `name`, or every token is a substring of `name2` (`UnicodeStorage.kt:391`, `:316`). Tokens are upper cased with `String.uppercase()` and escaped for `LIKE` (`\`, `%`, `_`, `:402`), so matching is case insensitive and wildcards are literal. Token order does not matter and there is no phrase matching. A hyphen is an ordinary character, not a word break.

### Ranking, one token

`queryFindChars` (`UnicodeStorage.kt:389`), on `name` only:

1. `name` equals the token
2. `name` ends with the token as a word (`% TOKEN`)
3. `name` starts with the token as a word (`TOKEN %`)
4. `name` starts with the token (`TOKEN%`)
5. the token is a word inside `name` (`% TOKEN %`)
6. everything else: a word prefix or any substring in `name`, and every row matched through `name2`

Ties: by id.

### Ranking, multiple tokens

`findCharsByNameMultiWord` (`UnicodeStorage.kt:312`). Each token scores 0 when it is a whole word of `name` or `name2`, 1 when a word of either starts with it, else 2. Rows are ordered by the sum, then by id. There is no tier for an exact or complete name.

### Limits and the glyph filter

- The first pass adds `LIMIT 64` to each SQL query, the second pass has none.
- `isShown` (`UnicodeStorage.kt:411`) drops rows after the query, so after the limit: private use and surrogate rows always; other rows when "show unsupported characters" is off and `Paint.hasGlyph` is false (`SearchController.kt:15`). Controls, format characters, line and paragraph separators and variation selectors are always kept, except the prepended concatenation marks, which need a glyph.
- With the filter on, results depend on the fonts of the device. The goldens are captured with "show unsupported characters" on, which leaves only the private use and surrogate rule and is the same on every device.

## Quirks

Recorded in the goldens as they are. The ones a new engine should not copy are marked `+improve` in the query file or listed in `weaknesses.txt`.

- The first pass of a bare code query returns up to 64 code results plus up to 64 name results, 128 rows, and is not a prefix of the full list (`SearchController.kt:55`, `:69`). Example: `00`, `a1`.
- The limit is applied before the glyph filter, so with the filter on the first pass can show fewer than 64 rows, or none, while more exist (`UnicodeStorage.kt:277`, `:297`, `:330`).
- The character literal check reads the untrimmed query but the name search reads the trimmed one (`SearchController.kt:57`). `a` puts U+0061 first, ` a` puts U+0041 first, and a pasted `→` followed by a space finds nothing.
- Two or more code points are never read as characters: `e` + U+0301, an emoji with a modifier, a ZWJ sequence and a flag find nothing.
- A single token ranks "ends with the word" above "starts with the word": `copyright` puts U+2117 SOUND RECORDING COPYRIGHT above U+00A9 COPYRIGHT SIGN, and `less-than` puts U+003C at 24.
- A word prefix ranks with arbitrary substrings in tier 6: `arro` has U+2190 at 46, `arrow` has it at 3.
- A single token never ranks by `name2`, multiple tokens do. `slash` has U+002F SOLIDUS at 19 of 36.
- A repeated token switches to the multi-word ranking: `arrow arrow` orders differently from `arrow`.
- `uppercase()` expands special casing before matching: `ß` searches for `SS`, `ﬁ` for `FI`, `ı` for `I`.
- A NUL character ends the bound SQL string: U+0000 followed by `a` becomes the pattern `%` and matches every row (`UnicodeStorage.kt:301`).
- `trim()` and the `\s+` split disagree on U+0085: alone it splits into two empty tokens and matches every row (`SearchController.kt:44`, `:67`). The split treats U+00A0 and U+3000 as spaces on Android; `\s` on the JVM does not.
- More than 166 tokens need more than 999 SQL variables. SQLite before 3.32 (below API 31) throws `SQLiteException: too many SQL variables`, which is shown as a toast with the whole SQL statement (`ui/common/Screen.kt:16`) while the list keeps the results of the previous query; newer versions return results (`UnicodeStorage.kt:332`).
- A bare `u` followed by hex with a digit is a code query: `u41` is U+0041 (`SearchController.kt:101`).
- Decimal numbers are read as hex: `65` is U+0065, not U+0041.
- Range endpoint rows are ordinary results: `<`, `first>` and `ideograph,` find `<CJK Ideograph, First>`.
- `U+E000` finds U+E0001: the private use row is dropped, the prefix range is not.

## Regression suite

Files in `app/src/testDebug/resources/search/`, also bundled as assets of the debug app:

- `queries.txt`: `<category>[+improve] <query>`, one per line.
- `goldens.txt`: generated, one line per query, tab separated: query, total of the unlimited pass, code points of the first pass (limit 64), the first 200 code points of the unlimited pass. `-` is an empty list. A failed query is `query`, `error`, exception.
- `expectations.txt`: ranking rules that hold today and must hold for any engine.
- `weaknesses.txt`: ranking rules that fail today and that a better engine should meet. They never fail the suite.
- `allowed.txt`: reviewed intentional differences from the goldens, `<query><TAB><reason>`. An entry whose query matches again fails the suite.

Queries are escaped: `\\` backslash, `\s` a space at an edge or after another space, `\x{HEX}` any character outside printable ASCII.

The seam is `SearchEngine` (`app/src/debug/kotlin/vadiole/unicode/search/SearchEngine.kt`): a query and a limit in, ordered code points and a total out, a limit of 0 or less meaning no limit. `SearchSuite.verify` checks any engine against the files and reports per query what is missing, extra or reordered and the first differing position. A difference on a `+improve` query is reported but does not fail.

- On a device, against the live `SearchController`: `tools/search-golden.sh verify <serial>`.
- On the JVM: `./gradlew testDebugUnitTest` checks that the files are consistent and that the goldens pass their own suite. `SearchGoldenTest.engineMatchesGoldens` is skipped until an engine runs on the JVM; set `engine` there to enable it.
- After an intended change: `tools/search-golden.sh capture <serial>` rewrites `goldens.txt`; review the diff.

The goldens were captured on an API 33 emulator (SQLite 3.32.2). Two runs are identical, and an API 24 emulator (SQLite 3.9.2) differs in one query, the 200 token one above.
