# MT Analyze v1.0.18

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.18.jar
```

---

## Changes

### MT 530, MT 564-569 Corporate Actions and Transaction Processing

MT 530 (Transaction Processing Command), MT 565 (Corporate Action Instruction), MT 566 (Corporate Action
Confirmation), MT 567 (Corporate Action Status and Processing Advice), MT 568 (Corporate Action Narrative)
and MT 569 (Triparty Collateral and Exposure Statement) are now supported, alongside the existing MT 564.

MT 530, MT 567 and MT 569 use the same wrapper-less row layout as MT 537/MT 564, with one table row per
`:16R:REQD` (530) or `:16R:STAT` (567) sequence. For MT 569, only the Transaction Details (`TRANSDET`)
sequence is mandatory — Valuation Details (`VALDET`) and, nested underneath it, Securities Details
(`SECDET`) are both optional — so the row is `VALDET` when present (with any nested `SECDET` folded into
that row), falling back to `SECDET` or `TRANSDET` when the message omits it. MT 565, MT 566 and MT 568
carry a single instruction/confirmation/narrative per message and use the same flat, single-row layout as
MT 578.

Auto-detection recognizes each type from its distinguishing sequence qualifier (`:16R:REQD`, `:16R:CAINST`,
`:16R:CACONF`, `:16R:STAT`, `:16R:USECU`), checked after the existing MT 527/535/536/537/558/564/578/940/950
rules and after MT 569's `:16R:SUME` marker (checked before MT 537's `:16R:TRANSDET` rule, since MT 569 also
carries a nested `TRANSDET` sequence).

---

# MT Analyze v1.0.17

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.17.jar
```

---

## Changes

### MT 578 Settlement Allegement

MT 578 files are now supported, using the same flat, single-row layout as the MT 540-548 settlement instructions and confirmations. Auto-detection recognizes MT 578 via its paired `:22H::PAYM//` and `:22H::REDE//` tags in the TRADDET sequence.

---

# MT Analyze v1.0.16

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.16.jar
```

---

## Changes

### Local NVD Dependency Scanning

An optional `owasp` Maven profile adds OWASP Dependency-Check, letting dependencies be scanned against the NVD locally with `mvn -P owasp verify -Dnvd.api.key=<your-key>` (report at `target/dependency-check-report.html`). The Sonatype OSS Index analyzer is disabled, since it needs separate credentials unrelated to NVD scanning; six log4j-core-only CVEs that Dependency-Check misattributes to the transitive `log4j-api` dependency are suppressed via `owasp-suppressions.xml`.

### Dependency Updates

- **FlatLaf** 3.7.1 → 3.7.2
- **Apache POI** 5.4.1 → 5.5.1
- **log4j-api** (transitive, via Apache POI) pinned to 2.26.1, fixing CVE-2026-49844

---

# MT Analyze v1.0.15

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.15.jar
```

---

## Changes

### Removed mtanalyze.properties, Settings Now Fully in Settings Dialog

**Max. entries**, the log-file import markers (**SWIFT start marker**, **Newline token**), and **Enable experimental features** are now editable on a new **Advanced** tab in the Settings dialog, alongside the Sender/Receiver BIC fields. Support for the external `mtanalyze.properties` file has been removed entirely — all settings are now stored the same way as the rest of the app's preferences and take effect immediately, without restarting. Anyone with an existing `mtanalyze.properties` file should copy its values into the Settings dialog once; the file itself is no longer read.

### Restored Excel Export

The **File → Save Excel…** menu item is back, along with the Apache POI dependency it relies on. It writes a pivoted `.xlsx` workbook of component data, the same shape produced by **File → Export → Export CSV (Components)…**.

---

# MT Analyze v1.0.14

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.14.jar
```

---

## Changes

### MT 564 Corporate Action Notification

MT 564 files are now supported, alongside the existing wrapper-less row modes for statements. Each `:16R:CAOPTN…:16S:CAOPTN` block becomes one row, with header-level fields (GENL, USECU, CADETL) inherited by every row and tags inside a row labelled by their nearest enclosing sequence (TRANSDET, LINK, SETPRTY, STAT, REAS, SECMOVE, CASHMOVE…). Auto-detection recognizes MT 564 via its `:16R:CAOPTN` tag, and a notification with no CAOPTN block at all is shown as a single row instead of an empty table.

### Off-Screen Window Position Fix

The main window no longer restores to a saved position left over from a monitor that is no longer connected, or after a resolution change. Previously this could make the application appear not to open at all; the saved position is now only restored if it still overlaps a currently connected screen, falling back to the default centered bounds otherwise.

---

# MT Analyze v1.0.13

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.13.jar
```

---

## Changes

### 95R (Party Identification) Support in Name-Value Import

Name-Value content — single-line and multi-line — now also recognizes the short tag form `5R`, as used in place of `95R` in some exports, and expands it to the standard `:95R:` field when converted to block 4.

---

# MT Analyze v1.0.12

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.12.jar
```

---

## Changes

### MT 537 Statement of Pending Transactions

MT 537 files now parse with one row per `:16R:TRANS…:16S:TRANS` block, alongside the existing MT 535/536 row modes. Fields inside a row are labelled by their nearest enclosing sequence (TRANSDET, LINK, SETPRTY, STAT, REAS…), and header-level GENL fields are inherited by every row, matching the behaviour already in place for MT 536.

### MT 535 Auto-Detection Fix

Auto-detecting the message type from content (used for pasted text and files without a reliable header) now also recognizes MT 535 via its `:16R:SUBBAL` tag. Previously only MT 536 (`:16R:SUBSAFE`) was recognized this way, so MT 535 messages without a usable header could be mis-detected.

---

# MT Analyze v1.0.11

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.11.jar
```

---

## Changes

### Hide Empty Columns

The Entries table's column header right-click menu gained a **Hide Empty Columns** item. It hides every currently visible column that has no non-blank value across the rows currently shown (respecting active filters), in one click instead of hiding columns individually.

### Build from Source & Maven Central

The README now documents building from source with Maven (`mvn clean package`) and pulling signed release jars directly from [Maven Central](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze) instead of a direct download link.

---

# MT Analyze v1.0.9

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.9.jar
```

---

## Changes

### PSET Directory Lookup

The Entries table and tooltips now resolve **Place of Settlement** BICs (field `95a::PSET//…`) against a bundled directory of ~175 central securities depositories (`pset.csv`), showing the CSD name, market and country instead of a bare BIC. Both 8- and 11-character BICs resolve, falling back from an exact match to the 8-character institution prefix.

### Export Visible MT Messages

**File → Export → Export MT Messages (Visible)…** exports only the rows currently shown in the Entries table — i.e. whatever survives the active filters and search — instead of every loaded message. The existing **Export MT Messages (All)…** item is unchanged.

### Column Chooser — Select All / None per Group

Each column group in the Column Chooser now has **All** / **None** links in its header to toggle every checkbox in that group at once, instead of clicking each column individually.

---

# MT Analyze v1.0.8

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.8.jar
```

---

## Changes

### New App Icon

The application icon and window icon were redrawn as a bold "MT" mark on a dark-blue rounded square, replacing the previous document-and-magnifying-glass artwork, to match the branding used on the landing page favicon.

---

# MT Analyze v1.0.7

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.7.jar
```

---

## Changes

### Copy Table in Diff View

The Diff View's right-click menu gained a **Copy Table** item. It copies the comparison table to the clipboard in two formats at once: plain tab-separated text (for pasting into Excel or Notepad) and HTML with the same yellow diff highlighting shown on screen (for pasting a formatted table into Word or Outlook).

---

# MT Analyze v1.0.6

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.6.jar
```

---

## Changes

### Truncated Message Recovery, Take Two

Messages that are cut off **mid-tag** in block 4 (not just missing the closing `-}`) previously still failed to parse even after the v1.0.4 truncation repair, discarding the entire message. The last incomplete line of block 4 is now dropped and the parse retried, recovering the well-formed tags before the break instead of losing the whole entry.

### Large Single-Column CSV Import Fix

Single-column CSV SWIFT exports are now detected and streamed cell-by-cell during **directory import** as well as direct file load — previously only direct file load used the streaming path, so large CSV exports dropped into a folder import could exhaust memory. CSV files are also now excluded from the "one message per file" assumption directory import otherwise makes.

### Combined Filter OR Mode

Quick-filter OR mode (**Match any filter**) now spans both filter rows: a row is included if *any* active filter matches, whether it's a column drop-filter checkbox or a quick-filter expression. Previously OR mode only combined the quick-filter expressions and always required drop-filters to match separately, which made the OR toggle behave inconsistently once both filter kinds were in use.

---

# MT Analyze v1.0.5

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.5.jar
```

---

## Changes

### Removed Excel Export

The **File → Save Excel…** menu item and its underlying Apache POI dependency have been removed. Component data can be exported via **File → Export → Export CSV (Components)…**, which produces the same pivoted output as a standard CSV file.

---

# MT Analyze v1.0.4

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.0.4.jar
```

---

## Changes

### Truncated Message Recovery

SWIFT files that end without the closing `-}` of block 4 — for example, a mainframe export that was cut off mid-transfer — are now accepted rather than silently discarded. The parser detects the missing closer, appends it automatically, and passes the repaired message to Prowide for parsing. This applies to all import paths: direct file load, single-column CSV, and log file streaming.

For MT 536 messages that end inside an open `:16R:` sequence (no matching `:16S:`), the partial transaction entry is now committed to the Entries table instead of being dropped.

### Large CSV File Streaming

Single-column CSV files containing Mainframe-encoded SWIFT messages (one quoted cell per row) are now read in 8 KB chunks. The full file is never materialised in memory, making it practical to open export files several hundred megabytes in size. A progress dialog shows the number of messages parsed so far, and the import can be cancelled at any point.

---

# MT Analyze v1.0.3

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-v1.0.3.jar
```

---

## Changes

### Generate MT 54x Settlement Confirmation from MT 536

Right-click any **MT 536** (Statement of Transactions) row in the Entries table to generate the matching settlement-confirmation message. The target type is derived automatically from the transaction's settlement indicators (`:22H::REDE//` and `:22H::PAYM//`):

| REDE | PAYM | Confirmation |
|------|------|------------------------------------|
| RECE | FREE | MT 544 — Receive Free |
| RECE | APMT | MT 545 — Receive Against Payment |
| DELI | FREE | MT 546 — Deliver Free |
| DELI | APMT | MT 547 — Deliver Against Payment |

The salient settlement fields — financial instrument (35B), safekeeping account (97A::SAFE), settled quantity (36B), trade/settlement dates, settlement narrative (70E) and settlement parties — are carried over into the standard **GENL / TRADDET / FIAC / SETDET** sequences. The generated message opens in a copyable source view and is intended for inspection, not straight-through processing. On MT 536 rows that carry no transaction details (TRANSDET) the menu item is shown disabled so the feature stays discoverable.

### Drag & Drop Appends to the Entries Table

Dropping one or more files onto the Entries table now **appends** the parsed messages to whatever is already loaded instead of replacing it. This makes it easy to combine messages from several files by dragging them in one after another. **File → Open** and the explorer tree still load fresh (replace).

### Log File Import — Streaming & MT Type Filter

Log files are now read line by line instead of being loaded entirely into memory. Large log files no longer cause high memory usage during import.

When importing a log file a dialog asks which MT types to include:

- Enter a comma-separated list (e.g. `536,548`) to restrict the import to those types.
- Leave the field empty to import all message types.
- Cancel the dialog to abort the import without any changes.

The filter is applied while streaming, so unmatched messages are never parsed.

### Performance Improvements

- **Dictionary lookup cache** — qualifier/value descriptions are cached (LRU, 512 entries) so repeated lookups during table rendering are served from memory instead of re-scanning the dictionary.
- **Debounced column resize** — resizing a table column no longer triggers a layout repack on every pixel change; it is debounced with a 120 ms timer.
- **Lazy Notes submenu** — the *Available Notes* submenu is only populated when it is opened, not when the context menu is built.

---

# MT Analyze v1.0.2

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-v1.0.2.jar
```

---

## Changes

### Validate SWIFT File

New **File → Validate SWIFT File…** menu item. Loads any SWIFT FIN file and shows a scrollable diagnostic report:

- **Prowide log** — WARNING and SEVERE messages emitted by the parser (JUL capture)
- **Parser errors** — `SwiftParser.getErrors()` output; exceptions are shown inline
- **Block structure** — presence/absence of blocks 1–5 with content preview
- **Message info** — type, sender, receiver, LT address, session and sequence numbers
- **JSON** — `AbstractMT.toJson()` and `SwiftMessage.toJson()` output

The report can be copied to the clipboard in one click. Use this to inspect malformed or non-standard files without loading them into the entries table.

### Attach Block 5

New **File → Attach Block 5…** menu item. Reads a complete SWIFT FIN file, lets you configure the MAC value (default `00000000`), and writes the modified file with a `{5:{MAC:…}}` trailer block:

- If Block 5 is absent, it is appended after the closing `-}` of Block 4.
- If Block 5 is present but has no MAC tag, MAC is inserted.
- If Block 5 already contains a MAC tag, the value is replaced; other trailer tags (e.g. `CHK`, `PDE`) are preserved.

The output is written to a new file (default: original name with `_mac` suffix). Useful for adding a dummy trailer to files that were stripped of Block 5 before use in testing environments.

### Parser Log Notifications

Prowide Core WARNING and SEVERE log messages are now captured during all normal import paths — file load, directory import, log file import, and paste — and surfaced as notifications in the **Notifications** panel. The panel opens and expands automatically when parser warnings arrive so they are never missed.

Parse exceptions that previously propagated silently are now caught, and the exception message is shown as a notification rather than being swallowed or crashing the EDT.

---

# MT Analyze v1.0.1

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-v1.0.1.jar
```

---

## Changes

### Entry Notes

- **Add Note** — right-click any row in the Entries table to attach a free-text note to that entry.
- **Edit Note** — opens the Tag View and activates the Note field for direct editing.
- **Available Notes** — submenu listing all unique notes already used across entries; click one to apply it to the selected row. Useful for stamping a standard CSD remark or status across multiple entries.
- Notes are persisted in `.mtd` session files as `:70E::NOTE//text` (valid ISO 15022 tag format) and restored on load.

### User Dictionary — Excel-editable CSV

- Custom qualifier-value descriptions (e.g. CSD identifiers, proprietary codes) can be maintained in `~/.mtanalyze/user_qualifier_values.csv`.
- The file is written with a UTF-8 BOM so Excel opens it without an import wizard.
- Entries added via **Settings → User Dictionary** are saved to this file automatically.
- Any of the four built-in dictionary files (`dict_qualifier_values.csv`, `dict_qualifiers.csv`, `dict_tags.csv`, `dict_components.csv`) can be replaced entirely by placing a file with the same name in `~/.mtanalyze/`.

---

# MT Analyze v1.0.0

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-v1.0.0.jar
```

---

## Changes

Initial public release.
