# MT Analyze v1.2.3

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.2.3.jar
```

---

## Changes

### Open and Save use the standard SWIFT RJE format

**Open FIN MT Bulk Messages...** (`Ctrl+O`) and **Save FIN MT Bulk Messages...** (`Ctrl+S`,
renamed from **Open...**/**Save...**) now read and write files using Prowide's `RJEReader`/
`RJEWriter`, the standard format for bulk files of FIN MT messages (messages separated by a
line containing only `$`). Files without a separator (plain concatenated messages) still
open correctly. **Save** now writes every message loaded in the active tab to a single file,
not just the currently selected one. **Import**/**Append**/**Export** are unchanged and keep
handling CSV, log and Name-Value formats as before.

### Rename Tab

Double-click a tab, or right-click it and choose **Rename Tab...**, to give it a custom name.

### Save Excel column headers

Column headers in the Excel export now follow the same naming convention as the MT Entries
table (`sequence tag:qualifier`, e.g. `B1 22F:CAEV`), with the component name appended since
each tag is split into one column per component. Nested sequences are now resolved to
Prowide's letter-path code instead of the raw qualifier.

### Sort Columns

The column header context menu has a new **Sort Columns** action: it keeps the pinned
**Typ**/**MT** columns first and sorts the rest by sequence (e.g. `A`, `B1`, ...).

---

# Older Releases

- **v1.2.2** — Added **Remove Duplicates**: removes exact duplicate messages from a tab (compared with Prowide's `SwiftMessageComparator`, keeping the first occurrence), reporting the SEME references of removed messages.
- **v1.2.1** — Indexed messages are now identified by a hash of their content instead of type+sender+SEME, so the individual pages of a paginated statement no longer collapse into a single index entry.
- **v1.2.0** — Message Repository: local Lucene-based full-text index and search of parsed messages (Index Messages, Search Messages, Clear Index); Copy Visible Messages to Tab; Diff view tooltips showing ISO 15022 descriptions.
- **v1.1.1** — MT column pinned as the leftmost column in the Entries table; **Save...** renames the tab to the saved file name; fixed a potential NullPointerException while parsing certain messages.
- **v1.1.0** — Multiple documents as tabs: each tab is an independent workspace with its own Entries table, filters, column layout and Detail panel.
- **v1.0.18** — MT 530, MT 564-569 corporate actions and transaction processing support; log import MT type filter now accepts numeric ranges.
- **v1.0.17** — MT 578 Settlement Allegement support.
- **v1.0.16** — Optional OWASP dependency-check Maven profile; FlatLaf, Apache POI and log4j-api dependency updates.
- **v1.0.15** — Settings dialog now holds all config (mtanalyze.properties removed); restored Excel export.
- **v1.0.14** — MT 564 Corporate Action Notification support; fixed off-screen window position restore.
- **v1.0.13** — Name-Value import recognizes the short `5R` tag form for 95R (Party Identification).
- **v1.0.12** — MT 537 Statement of Pending Transactions support; fixed MT 535 auto-detection.
- **v1.0.11** — Added Hide Empty Columns; documented building from source with Maven and Maven Central releases.
- **v1.0.9** — PSET directory lookup, Export Visible MT Messages, Column Chooser Select All/None per group.
- **v1.0.8** — New application icon.
- **v1.0.7** — Copy Table in Diff View.
- **v1.0.6** — Better truncated-message recovery, fixed large single-column CSV import, combined filter OR mode across both filter rows.
- **v1.0.5** — Removed Excel export.
- **v1.0.4** — Truncated message recovery; large CSV file streaming with progress and cancel.
- **v1.0.3** — Generate MT 54x confirmation from MT 536; drag & drop appends; log file streaming with MT type filter; performance improvements.
- **v1.0.2** — Validate SWIFT File, Attach Block 5, parser log notifications.
- **v1.0.1** — Entry notes; user dictionary as Excel-editable CSV.
- **v1.0.0** — Initial public release.
