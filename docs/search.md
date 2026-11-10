# Search

How search works on the flat data file (`assets/u18.bin`, Unicode 18.0, 172873 characters) and the regression suite that pins it.

Paths are relative to `app/src/main/kotlin/vadiole/unicode/`.

## Flow

- `SearchField` reports the raw text, untrimmed, once per frame: only the last text of a frame is searched, so a keyboard that rewrites the field through an empty state does not flash the recents. The field has no suggestions flag and no forced ASCII, so the keyboard opens in the user's language and does not autocorrect.
- `TableScreen.runSearch` shows recents for an empty query, otherwise calls `SearchController.search`.
- `UnicodeSearch` (`data/UnicodeSearch.kt`) owns one `search` thread. A keystroke stores the latest query and wakes the thread; queries typed in between are skipped and a result of an older generation is dropped. A query typed before the index is ready waits for it. A result remembers the engine it came from, so `SearchResults.match` stays valid after the index is replaced.
- One pass returns the first 64 results, the total and the position where CJK meanings begin. The full list is a second pass without a limit, requested when a row near the end of the page is bound.
- `CharSearch` (`search/CharSearch.kt`) is the engine. It is plain Kotlin and runs in JVM tests.

## Data and index

- The generator (`vadiole/generator`) writes the flat file; `data/UnicodeData.kt` reads it and holds the format constants both use. The header is a magic number, the format version, a CRC32 of all sections and the section lengths. The reader does not verify the CRC; it is the identity of the data in the cache keys. CJK meanings are the last two sections; they are read in the same pass when the index has to be built, otherwise only when a meaning is asked for.
- The index is built on the device from `SearchSource`s (`search/SearchIndex.kt`): sorted distinct words, delta coded postings with two position bits (the word starts the text, the word ends it), and per document its character, kind and word count. `UnicodeDataSource` is the English source, its `key` is `en`. `AnnotationSource` adds the short names and keywords of the loaded language (see Languages), its `key` is `<tag>@<checksum>` from the header of the file.
- Two lists of class words, on purpose different (`SearchIndex.TRAILING_WORDS` and `DROPPED_WORDS`): a trailing `SIGN`, `MARK`, `SYMBOL` or `SUIT` is not the last word of a text (`QUESTION MARK` ends with `question`, `BLACK HEART SUIT` with `heart`). A trailing `SIGN`, `MARK` or `ARROW` is not counted in the length of a text (`minus` is the whole of `MINUS SIGN`, `up` of the Unicode 1.0 name `UP ARROW`).
- The index is cached in `filesDir/cache/search` as one buffer with a CRC, keyed by `SearchIndex.FORMAT`, the data checksum, the app version code (the install time in debug builds) and the source keys. A mismatch or any read error rebuilds it. There is one file for English plus the current language, not one per language: a language change is rare and costs one rebuild in the background, a file per language would keep about 1.8 MB each and need pruning.
- `UnicodeSearch` starts the search thread from `UnicodeApp.onCreate`, which loads the data. The index is loaded or built on a `search-index` thread when the activity names the language (`ensure`), published before it is written to the cache, and the search thread warms it up only while no query is waiting.
- Documents, by kind (`SearchKind`): formal name, Unicode 1.0 name, formal alias (NameAliases correction, control, alternate, figment), abbreviation, alias (NamesList `=` lines), CLDR short name, CLDR keyword, block name, sub-block heading, CJK meaning (one document per sense, split on `;` and `,`). A keyword that repeats a word of the formal name is not stored.
- Block names, sub-block headings and the name prefix of a generated range (`CJK UNIFIED IDEOGRAPH`, `HANGUL SYLLABLE`, ...) are one document for a span of characters. The hex part and the Hangul syllable of a generated name are matched by computation, not stored.

## Reading the query

1. A query of exactly one code point, before or after trimming, is a character literal: that character is the first result, then the name search of it.
2. Trimming removes spaces and control characters. An empty query has no results. Unpaired surrogates are ignored.
3. Code query: the digits are what follows `U+`, `0x` or `\u` (explicit, any case, spaces after the prefix and a trailing `,.;:)` allowed), or what follows a bare leading `u`/`U`, or the whole input of at least 2 characters. It is a code query when the digits are 1 to 8 hex digits, the value is at most 10FFFF and, unless explicit, at least one digit is 0-9. `face`, `cafe`, `dead` are words; `41`, `e9`, `1f600`, `u41` are code points. Decimal numbers are read as hex.
4. Code results: the exact value first, then every code point whose 4, 5 or 6 digit form starts with the digits, ascending. Digits with a leading zero are only padded to 4 digits. A private use or surrogate value has no code results.
5. An explicit code query has only code results. A bare one continues with the name search, without the code results. Hex digits that are all letters (`fffd`, `feff`) are searched as a word first and read as a code point only when the word finds nothing.
6. Pasted symbols: a space separated chunk that contains a non-ASCII symbol, mark or format character is read as characters, each one a result in order (at most 16); the other chunks are searched as words. A query with non-ASCII text that finds nothing lists its characters the same way.
7. Words (`search/SearchTokens.kt`): runs of letters and digits in any script. Everything else separates words, so `less-than` is `less` and `than`. Repeated words count once, at most 16 words are used. The generator folds with the same code. Folding, one code point at a time:
   - lower case by `Character.toLowerCase`, which does not depend on the locale (`İ` is `i`);
   - Latin letters lose their diacritics (`é` is `e`, `ł` `l`, `ø` `o`, `đ` `d`, `ı` `i`), `ß` is `ss`, `æ` `ae`, `œ` `oe`. Letters of other scripts keep theirs: `й` is not `и`, `ά` is not `α`;
   - katakana is hiragana, half-width katakana is full-width (a half-width voiced mark joins its letter), full-width Latin letters and digits are ASCII, Greek final sigma is sigma;
   - a combining mark after a Latin letter is dropped; a combining accent (U+0300..036F) or kana voiced mark after another letter is composed with it when a composed letter exists (`и` + breve is `й`), otherwise dropped; the marks of U+0900..109F (Devanagari to Myanmar) stay in the word; other marks, variation selectors and format characters are dropped.
8. Symbol keywords: a query of symbols and digits with at least one symbol and no letters or spaces (`<3`, `+1`, `-1`) is also looked up, exactly, among the keywords that are written that way.

## Matching

- Every word must match some text of the character; different words may match different texts of the same character.
- A word matches a whole word, or the start of a word. Words of three or more characters also match inside a word. One and two character words match word starts only. A word that begins with kana or a CJK ideograph matches inside a word from one character: Japanese and Chinese are written without spaces, so a keyword is one word (`ハート` is found in `赤いハート`, `心` in `红心`). Three letters is the minimum everywhere else, German compounds included (`bahn` in `Straßenbahn`); two letters inside a word match too much.
- The last word of a query of several words is taken as unfinished: its word start matches count as whole words, ranked after the real whole words. Matches inside a word are not promoted. The rule is off when the last word is itself the start of another query word (`arrow arr`).
- Compound: a single word of at most 64 letters is also tried as two whole words, the first of at least 3 letters, the second of at least 2, both existing words (`thumbsup`, `rightarrow`, `checkmark`, `newline`). CJK meanings are never matched this way.
- Results are filtered by the set of characters the table shows, so "show unsupported characters" applies without asking the font per result.

## Ranking

Results are sorted by tier, then order, then, for queries of several words, length, then code point. A character takes its best text for each query word and then the worst of its words. The table is `CharSearch.key`, `matchExact` and the `TIER_` constants, row for row.

Levels: common characters are U+0020..00FF, U+2000..206F and every character with a CLDR annotation. Known characters are U+0100..024F, U+0300..04FF, U+2000..2BFF and U+3040..30FF. The others, and always the controls U+0000..001F, U+007F..009F and the APL symbols U+2336..237A, are other.

| # | Tier | What matched | Order inside the tier |
|---|---|---|---|
| 0 | literal | the typed character, the code results | as typed, then ascending |
| 1 | `EXACT` | the query is the whole formal name, Unicode 1.0 name or formal alias, or the abbreviation (not of a C1 control, nor of U+0001..0006, U+000E..001A, U+001C..001F) | complete, then unfinished last word |
| 2 | `EXACT_ALIAS` | the query is the whole CLDR short name, the whole alias of a common character, or a symbol keyword | complete, then unfinished last word |
| 3 | `WORD` | common or known character: a whole word of a name. Common character, one word typed: the start of the first or last word of the formal name. Any character: the whole keyword; a compound that is the whole name | 0: first or last word, start of at least 3 letters, compound. 1: middle word, start of 2 letters, keyword |
| 4 | `WORD_OTHER` | other character: a whole word of a name; the whole hex part of a generated name | 0: first or last word. 1: middle word |
| 5 | `ALIAS_WORD` | a whole word of an alias or short name | 0: first or last word. 1: middle |
| 6 | `START` | common or known character: the start of a word of a name | 0: first or last word. 1: middle |
| 7 | `START_OTHER` | other character: the same; the start of the hex part of a generated name | 0: first or last word. 1: middle |
| 8 | `GENERATED` | the syllable of a Hangul syllable name | 0: whole. 1: start |
| 9 | `ALIAS_START` | the start of a word of an alias or short name; an abbreviation that is not the whole query, or of one of the controls excluded in row 1 | 0: first or last word. 1: middle |
| 10 | `KEYWORD` | a word of a keyword | 0: whole. 1: start |
| 11 | `BLOCK` | a word or word start of the block name | |
| 12 | `SUBHEAD` | a word or word start of the sub-block heading | |
| 13 | `INSIDE_NAME` | inside a word of a name | |
| 14 | `INSIDE_ALIAS` | inside a word of an alias, short name or abbreviation | |
| 15 | `INSIDE_KEYWORD` | inside a word of a keyword | |
| 16 | `INSIDE_BLOCK` | inside a word of a block name or heading | |
| 17 | `SPLIT` | a compound that is not a whole name | by the tier the two words have |
| 18-21 | `MEANING` | CJK meaning: the whole sense, a whole word, a word start, inside a word | school characters, the Unihan core set, the rest; shorter senses first |

Rules the table follows:

- A name is the formal name, the Unicode 1.0 name or a formal alias. A whole word of a name is above every word start, alias word and keyword word, with two exceptions that are both in row 3: the typing rule below, and the level, which puts the whole words of other characters in row 4 (`sa` leads with kana, not Linear B). Common and known characters are not separated.
- Typing (row 3): while one word is being typed, a common character whose formal name starts or ends with it ranks with the whole words, so `cop` and `copy` both lead with U+00A9 and the first result does not change at the last letter. With 2 letters it ranks after the whole words (`po`: kana PO, then U+00A3).
- Several words: the order of every row below `MEANING` is replaced: 0, or 1 for an unfinished last word and for the name prefix of a generated range, so a whole keyword and a keyword start are not told apart. In rows 3 and 6 common characters then come before known ones (`letter a` leads with the Latin letters, not kana). Then names with fewer words come first; a text that is not the formal name counts one word more. A whole text match needs the words in the order typed, and an unfinished last word of an exact match needs at least 2 letters.
- A word start still counts as the first or last word of its text. `arr`, `arro` and `sum` depend on it: the characters whose name ends with the word being typed stay ahead of those with it in the middle.
- The whole sense of a CJK meaning (row 18) is approximated by the word count: every query word is a whole word of the sense and the sense has as many words as the query.
- A Hangul syllable part ranks in rows 4 and 7 when another query word starts `hangul` or `syllable`.
- A text of one word of one or two characters is never an exact alias, so the alias `a` of an obscure letter does not outrank U+0041.
- `SearchResults.meaningsStart` is the position of the first result that matched only by a CJK meaning. `CharSearch.match` gives the kind and text a result matched by.

## Languages

- The generator writes `res/raw-<locale>/annotations.txt` for every `res/values-<locale>` from CLDR 49 `annotations/<locale>.xml` (entries of one code point, U+FE0F removed) and a stub `res/raw/annotations.txt`. It fails when the two folder lists differ. The mapping is in `generator/build.sh`: `nb` is CLDR `no`, `pt` is `pt_PT` over `pt`, `pt-rBR` is `pt`, `zh-rCN` is `zh`. English stays in `u18.bin`.
- Format: the header `annotations 1 <tag> <crc32 of the rest>` (`none 0` in the stub), then one line per character: code point, short name, displayed keywords, search-only keywords, separated by tabs, keywords by `|`. A keyword equal to the short name is left out; digits and single symbols are search-only; keywords without letters are displayed last.
- No platform API tells which language's resources are loaded, so the language is read only from the header of the stream opened through the activity's `resources`. `UnicodeActivity` recreates itself in `onStart`, and on `ACTION_PACKAGE_REPLACED` while started, when `R.string.lang_tag` no longer equals the value read in `onCreate`; otherwise it calls `UnicodeSearch.ensure`.
- `ensure` compares the key of the header with the last one asked for. A new key builds an index for English plus that file while the old index keeps answering, replaces it at once, runs the active query again and drops a build that is no longer the latest. The stub has no key of its own, so English shown for a missing language split is cached as `en` and never under a language.
- A localized short name is an exact alias (row 2), a localized keyword a keyword (rows 3, 10, 15), exactly as the English ones. English queries can gain results from Latin words in a localized file (`smiley` in German).
- Details and search rows show the localized short name and keywords when the file has the character, the English ones otherwise.

## Regression suite

Files in `app/src/testDebug/resources/search/`, also bundled as assets of the debug app:

- `queries.txt`: `<category>[+improve] <query>`, one per line.
- `goldens.txt`: one line per query, tab separated: query, total, code points of the first pass (limit 64), the first 200 code points of the unlimited pass. `-` is an empty list. All lines are the results of the current engine.
- `expectations.txt`: ranking rules that must hold.
- `weaknesses.txt`: ranking rules that fail today. They never fail the suite, verify reports each as fixed or open; their queries are marked `+improve`.
- `allowed.txt`: reviewed differences from the goldens, `<query><TAB><reason>`. An entry whose query matches again fails the suite.

Queries are escaped: `\\` backslash, `\s` a space at an edge or after another space, `\x{HEX}` any character outside printable ASCII.

The seam is `SearchEngine` (`app/src/debug/kotlin/vadiole/unicode/search/SearchEngine.kt`): a query and a limit in, ordered code points and a total out, a limit of 0 or less meaning no limit. The suite runs with every character shown and English only. `LocalizedSearchTest` covers de, uk, ja and zh-CN, the folding and how far English results move when a language is added.

- On the JVM: `./gradlew testDebugUnitTest`. The report is `app/build/reports/search/verify.txt`.
- On a device: `tools/search-golden.sh verify <serial>`.
- After an intended change: `tools/search-golden.sh capture <serial>` rewrites `goldens.txt`; review the diff and empty `allowed.txt`.

Open weaknesses (`weaknesses.txt`): `bu` and `po` (kana BU and PO are whole words, BULLET and POUND SIGN word starts of 2 letters), `double qu` (DOUBLE QUESTION MARK is a formal name, U+0022 has `double quote` as an alias), `tick` and `divide` (✓ and ÷ have the word only as a keyword, other names have it as a word), `yogh` (the EZH letters have a Unicode 1.0 name with YOGH and lower code points).
