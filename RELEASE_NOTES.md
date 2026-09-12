# MT Analyze v2.0.0

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-2.0.0.jar
```

---

## Changes

### Elasticsearch support — indexed search at scale

MT Analyze now scales from a handful of files up to large message archives. Alongside the
existing embedded Apache Lucene index (its own **Lucene** menu), the menu bar gained a
second, independent index backed by Elasticsearch, in its own **Elasticsearch** menu — point
it at a local or remote cluster and index/search collections far beyond what a single
desktop's file-based Lucene index comfortably handles. Both menus offer matching actions:

- **Index Messages** / **Search Messages...** (`Ctrl+Shift+E`) / **Clear Index...** — same
  three actions as the Lucene menu, now for Elasticsearch.
- Search uses Elasticsearch's `query_string` syntax, close to Lucene's classic
  `QueryParser` syntax, so query habits carry over between the two.
- Indexing is idempotent, same as Lucene: a message is identified by a hash of its
  content, so re-indexing the same message replaces its entry instead of creating a
  duplicate.
- Connection (host, port, scheme, credentials) and index name are configured under
  **Settings ▸ Advanced ▸ Elasticsearch**.

### Index Statistics

Both the **Lucene** and **Elasticsearch** menus gained a **Statistics...** item, opening a
dialog that shows how many messages are currently indexed in each backend. The two counts
are fetched independently, so a slow or unreachable Elasticsearch cluster never delays the
(near-instant) Lucene count from showing.

### Elasticsearch password stored in the OS keyring

The Elasticsearch password is no longer saved in plain text alongside the other settings.
It now goes into the operating system's own credential store — Windows Credential Manager,
macOS Keychain, or the Freedesktop Secret Service/KWallet on Linux — via
[java-keyring](https://github.com/javakeyring/java-keyring).

- A password already saved from an earlier version is picked up automatically the first
  time Settings reads it, moved into the OS keyring, and removed from the plain-text
  preference — no re-entry needed.
- On a system without any supported OS keyring backend, MT Analyze falls back to the same
  plain-text storage used before, so the app keeps working everywhere.

---

# Older Releases

- **v1.2.5** — Redesigned Column Chooser: two-list transfer UI (Available/Visible, drag-to-reorder) with ISO 15022 tooltips; dialog now centers on the correct monitor in multi-monitor setups.
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
