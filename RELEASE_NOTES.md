# MT Analyze v1.2.0

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.2.0.jar
```

---

## Changes

### Message Repository (index and search)

A new **Repository** menu provides a local full-text index of parsed messages, based on
[Apache Lucene](https://lucene.apache.org/). The index is file-based; there is no server
component. It is shared across all tabs and sessions.

- **Index Messages** adds all messages of the active tab to the index. The operation runs
  in the background, reports progress and can be cancelled. Indexing is idempotent: a
  message is identified by a hash of its content, so re-indexing the same message replaces
  its entry instead of creating a duplicate.
- **Search Messages…** (`Ctrl+Shift+F`) executes a Lucene query and opens the result set
  in a new tab. Indexed fields include `raw_message`, `mt`, `sender`,
  `receiver`, `file_name` and per-tag fields such as `tag_20C` and `tag_35B`.
- **Clear Index…** removes all documents from the index.
- The index directory (default `~/.mtanalyze/swift-index`) and the maximum number of
  results (default 100) are configured under **Settings ▸ Advanced ▸ Lucene Search**.

### Copy Visible Messages to Tab

The Entries table context menu provides a new action that copies the messages currently
visible (that is, after filtering) to another open tab or to a new tab.

### Diff view tooltips

In the comparison view, hovering over a cell displays the ISO 15022 description for the
Sequence, Tag and Qualifier columns and for known qualifier/value combinations. For all
other value cells, the complete, untruncated content is shown.

---

# Older Releases

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
