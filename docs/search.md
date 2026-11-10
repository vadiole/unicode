# Search

How search works on the flat data file (`assets/u18.bin`, Unicode 18.0, 172873 characters) and the regression suite that pins it.

Paths are relative to `app/src/main/kotlin/vadiole/unicode/`.

## Flow

- `SearchField` reports the raw text after every change, untrimmed.
- `TableScreen.runSearch` shows recents for an empty query, otherwise calls `SearchController.search`.
- `UnicodeSearch` (`data/UnicodeSearch.kt`) owns one `search` thread. A keystroke stores the latest query and wakes the thread; queries typed in between are skipped and a result of an older generation is dropped. A query typed before the index is ready waits for it.
- One pass returns the first 64 results, the total and the position where CJK meanings begin. The full list is a second pass without a limit, requested when a row near the end of the page is bound.
- `CharSearch` (`search/CharSearch.kt`) is the engine. It is plain Kotlin and runs in JVM tests.

## Data and index

- The generator (`vadiole/generator`) writes the flat file; `data/UnicodeData.kt` reads it. CJK meanings are the last two sections and are only read while the index is built or when a meaning is asked for.
- The index is built on the device from `SearchSource`s (`search/SearchIndex.kt`): sorted distinct words, delta coded postings with two position bits (first word, last word of the text), and per document its character, kind and word count. `UnicodeDataSource` is the only source; its `key` is `en`.
- The index is cached in `filesDir/cache/search` as raw arrays with a CRC, keyed by data version, app version code and the source keys. A mismatch or any read error rebuilds it.
- Documents, by kind (`SearchKind`): formal name, Unicode 1.0 name, formal alias (NameAliases correction, control, alternate, figment), abbreviation, alias (NamesList `=` lines), CLDR short name, CLDR keyword, block name, sub-block heading, CJK meaning (one document per sense, split on `;` and `,`).
- Block names, sub-block headings and the name prefix of a generated range (`CJK UNIFIED IDEOGRAPH`, `HANGUL SYLLABLE`, ...) are one document for a span of characters. The hex part and the Hangul syllable of a generated name are matched by computation, not stored.

## Reading the query

1. A query of exactly one code point, before or after trimming, is a character literal: that character is the first result, then the name search of it.
2. Trimming removes spaces and control characters. An empty query has no results.
3. Code query: the digits are what follows `U+`, `0x` or `\u` (explicit, any case), or what follows a bare leading `u`/`U`, or the whole input of at least 2 characters. It is a code query when the digits are 1 to 8 hex digits, the value is at most 10FFFF and, unless explicit, at least one digit is 0-9. `face`, `cafe`, `dead` are words; `41`, `e9`, `1f600`, `u41` are code points. Decimal numbers are read as hex.
4. Code results: the exact value first, then every code point whose 4, 5 or 6 digit form starts with the digits, ascending. Digits with a leading zero are only padded to 4 digits. A private use or surrogate value has no code results.
5. An explicit code query has only code results. A bare one continues with the name search, without the code results.
6. Pasted symbols: a space separated chunk that contains a non-ASCII symbol, mark or format character is read as characters, each one a result in order (at most 16); the other chunks are searched as words. A query with non-ASCII text that finds nothing lists its characters the same way.
7. Words (`search/SearchTokens.kt`): runs of letters and digits, lower cased, Latin letters with diacritics folded to the base letter, combining marks dropped. Everything else separates words, so `less-than` is `less` and `than`. Repeated words count once, at most 16 words are used. A chunk of only symbols, or of digits and symbols (`!`, `<3`, `+1`, `4:30`), is one word; only keywords are indexed that way.

## Matching

- Every word must match some text of the character; different words may match different texts of the same character.
- A word matches a whole word, or the start of a word. Words of three or more characters also match inside a word, which ranks below every word start match. One and two character words match word starts only.
- A single word of six or more letters is also tried as two whole words (`thumbsup`, `rightarrow`, `checkmark`).
- Results are filtered by the set of characters the table shows, so "show unsupported characters" applies without asking the font per result.

## Ranking

Best first. A character takes its best text per word and its worst word.

1. the typed character, or the code results
2. the whole formal name, Unicode 1.0 name, formal alias or abbreviation
3. the whole CLDR short name, or the whole alias of a common character
4. word matches of known characters: name as a whole word (text ends with it, starts with it, has it inside), name by word start, alias or short name as a whole word, by word start, keyword exactly, keyword by word
5. the same for the other characters, names with fewer words first
6. block name, then sub-block heading
7. matches inside a word: name, alias, keyword, block
8. CJK meanings: the whole sense, a whole word, a word start, inside a word; school characters, then the Unihan core set, then the rest; shorter senses first

Details:

- Common characters have a CLDR annotation or are below U+0100. Known characters are the common ones plus U+0100..024F, Greek, Cyrillic and U+2000..2BFF. Ties inside a tier go to common characters where the tier does not order by word position, then to code point order.
- A text of one word of one or two characters never counts as the whole text, so the alias `a` of an obscure letter does not outrank U+0041.
- A trailing `SIGN`, `MARK` or `SYMBOL` does not count as the last word: `QUESTION MARK` ends with `question`.
- `SearchResults.meaningsStart` is the position of the first result that matched only by a CJK meaning. `CharSearch.match` gives the kind and text a result matched by.

## Regression suite

Files in `app/src/testDebug/resources/search/`, also bundled as assets of the debug app:

- `queries.txt`: `<category>[+improve] <query>`, one per line.
- `goldens.txt`: one line per query, tab separated: query, total, code points of the first pass (limit 64), the first 200 code points of the unlimited pass. `-` is an empty list. The lines of queries listed in `allowed.txt` or marked `+improve` are still the results of the SQLite search this engine replaced; the others are current.
- `expectations.txt`: ranking rules that must hold.
- `weaknesses.txt`: ranking rules the SQLite search failed. They never fail the suite, verify reports each as fixed or open.
- `allowed.txt`: reviewed differences from the goldens, `<query><TAB><reason>`. An entry whose query matches again fails the suite.

Queries are escaped: `\\` backslash, `\s` a space at an edge or after another space, `\x{HEX}` any character outside printable ASCII.

The seam is `SearchEngine` (`app/src/debug/kotlin/vadiole/unicode/search/SearchEngine.kt`): a query and a limit in, ordered code points and a total out, a limit of 0 or less meaning no limit. The suite runs with every character shown.

- On the JVM: `./gradlew testDebugUnitTest`. The report is `app/build/reports/search/verify.txt`.
- On a device: `tools/search-golden.sh verify <serial>`.
- After an intended change: `tools/search-golden.sh capture <serial>` rewrites `goldens.txt`; review the diff and empty `allowed.txt`.

Open weaknesses: `heart` (♥ has the word inside its name, 25 names end with it), `minus` (U+2212 comes after U+002D, U+00B1 and the superscript and subscript minus), `tick` (no source has the word for ✓), `up` (U+2191 is `UPWARDS ARROW`).
