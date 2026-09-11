# MT Analyze v1.2.5

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.2.5.jar
```

---

## Changes

### Redesigned Column Chooser

The **Column Chooser** dialog is now a two-list transfer UI: **Available** (hidden) and
**Visible** (shown, in display order) columns, with buttons to move columns between the
two lists and to reorder the **Visible** list, which can also be reordered by dragging.
Hovering a column shows its ISO 15022 description as a tooltip. The dialog now also
centers itself on the monitor that actually holds the main window, instead of
occasionally appearing on the wrong screen in multi-monitor setups.

---

# Older Releases

- **v1.2.3** — **Open FIN MT Bulk Messages...**/**Save FIN MT Bulk Messages...** now read and write the standard SWIFT RJE format via Prowide's `RJEReader`/`RJEWriter`, and **Save** writes every message in the active tab to a single file; **Rename Tab...**; Excel export column headers follow the same `sequence tag:qualifier` naming as the MT Entries table; column header context menu gained **Sort Columns**.
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
