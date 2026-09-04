# MT Analyze v1.1.0

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-1.1.0.jar
```

---

## Changes

### Multiple Documents as Tabs

MT Analyze can now show several documents at once, each in its own tab. Every tab is a fully independent
workspace with its own Entries table, filters, column layout and Detail panel. Click **+** on the tab strip
or press **Ctrl+N** for a new empty tab; **File → Open…** loads a file into a new tab, while actions like
**Append**, **Reload**, **Save** and Export apply to the active tab. Close a tab with the **×** on the tab
itself; closing the last tab opens a fresh empty one.

---

# Older Releases

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
