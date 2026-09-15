# MT Analyze v2.0.2

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-2.0.2.jar
```

---

## Changes

### 36xx tag masking (quantity fields)

**Settings ▸ Advanced ▸ Masking** gained a second checkbox, **Mask 36xx tag values (digits
→ 9)**, alongside the existing 19xx one — same digit-to-`9` replacement, applied instead to
tags whose name starts with `36` (`36B` Quantity of Financial Instrument, `36D` Quantity
(Digital Asset)). Off by default, only affects messages parsed after it's turned on, and
reaches everywhere the masked value is used (Entries table, exports, raw/source view,
Lucene/Elasticsearch indexes) — same behavior as 19xx masking, controlled independently.

### MT 536 Append-Text (Depotumsatz) import now structured, not flat

The fixed-width "Append-Text" printout format (tag + description in columns 6–30 + value
from column 31, no `:16R:`/`:16S:` markers of its own — the shape of many German
custodians' Depotumsatz reports) previously converted to a completely flat block 4, which
collapsed the whole statement into a single, unstructured row (or numbered duplicate
columns) instead of a proper transaction row. For MT 536 (detected from the printout's own
`536: ...` header line), the converter now infers the GENL/SUBSAFE/FIN/TRAN sequence
boundaries from field identity and wraps the content accordingly, so it parses into the
same one-row-per-transaction shape as a native MT 536. GENL runs up to the first `35B`;
Sequence B (SUBSAFE) is opened and closed empty right before FIN, matching how real
single-account extracts carry the safekeeping account at GENL level rather than under
SUBSAFE. Within TRAN, its own LINK (repeating `13a`/`20a` reference pairs), TRANSDET and
SETPRTY (repeating — one occurrence per settlement party, each carrying its own optional
account/reference) subsequences are nested the same way a native MT 536 would, rather than
left flat, so multiple parties and multiple linkage references stay distinguishable
instead of colliding into numbered duplicate columns. Every other MT type keeps the
previous flat conversion, unchanged. None of the GENL/SUBSAFE/FIN/TRAN/LINK/TRANSDET/SETPRTY
block names are hand-maintained: they're read via reflection from Prowide's generated
MT 536 sequence classes (`ProwideSequences`, the same mechanism the Name-Value importer
uses), so this stays correct even across future SRU changes.

Separately, a value spanning several physical lines (e.g. a multi-line `35B` or `70E`) is
no longer truncated to its first line — continuation lines (content reaching column 31 but
carrying no tag of their own) are now appended to the previous field's value instead of
being discarded. This applies to Append-Text import generally, not just MT 536.

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

### Index MT 536 Entries — a dedicated transaction index with its own search mask

The **Lucene** menu gained three more actions, alongside the existing general-purpose
index: **Index MT 536 Entries**, **Search MT 536 Entries...** and **Clear MT 536 Index...**.
Where **Index Messages** indexes each *message* as one document, **Index MT 536 Entries**
indexes each *transaction*: it reuses the existing "Isolate Entry in New Tab" pruning to
split every MT 536 row in the active tab's Entries table down to its own single-transaction
message (dropping every other `TRAN`/`TRANSDET` block of the statement, exactly as Isolate
Entry does), then indexes each isolated transaction into its own index, kept separate from
the general-purpose one so a transaction-level search never mixes with whole-message hits.
**Search MT 536 Entries...** adds a structured mask — Reference (`20C`), ISIN (`35B`),
Settlement/Trade Date (`98A`), Safekeeping Account (`97A`), Narrative (`70E`) and
Sender/Receiver BIC — on top of the same free-form Lucene query box **Search Messages**
has, for anything the structured fields don't cover; any combination of fields and/or
query can be filled in, all ANDed together. Matching transactions load into a new tab the
same way **Search Messages** does.

### Index Statistics

Both the **Lucene** and **Elasticsearch** menus gained a **Statistics...** item, opening a
dialog that shows how many messages are currently indexed in each backend. The two counts
are fetched independently, so a slow or unreachable Elasticsearch cluster never delays the
(near-instant) Lucene count from showing. A backend turned off under **Settings ▸
Advanced** shows as **Disabled** instead of attempting a count.

### MT 500 / MT 501 support

Added parsing support for **MT 500** (Instruction to Register) and **MT 501**
(Confirmation of Registration or Modification of Registration Details). Each **Client
Details** (`CLTDET`) block becomes one row in the Entries table, with the General
Information and Registration Details sequences carried into every row — the same pattern
already used for MT 530, MT 564, MT 567 and MT 569. Selectable from the MT type dropdown
(**MT 500**, **MT 501**) for content without its own SWIFT header; the two message types
share an identical tag structure, so — like MT 540-548 — auto-detection from content alone
isn't possible and the type must come from the message header or an explicit selection.

### ISO 15022 tooltip dictionary greatly expanded

The built-in dictionary behind ISO 15022 tooltips (Tags/Components view, column headers,
Column Chooser) was completed from SWIFT's official *ISO15022 Data Field Dictionary
SR2025*, filtered to qualifiers and coded values actually reachable by this app's
supported MT types: qualifier descriptions grew from 87 to 563 entries, and coded-value
descriptions (e.g. every `CAEV`, `ADDB`, `STAT` code word) from 291 to 1,662. A handful of
previously-undocumented tags used by MT 500/501/599 (`11A`, `12A`, `12B`, `20D`, `36D`,
`92A`, `94D`, `94G`, `95U`) also gained tag-level descriptions.

### MT 599 support

Added **MT 599** (Free Format Message) — the simplest message type in the standard: just
Transaction Reference (`20`), an optional Related Reference (`21`), and a free-text
Narrative (`79`), no sequences at all. Selectable from the MT type dropdown for content
without its own SWIFT header.

### Check Repository

Both the **Lucene** and **Elasticsearch** menus gained a **Check Repository...** item. It
checks every message in the active MT Entries tab against that backend's index — by the
same content-hash identity **Index Messages** uses to stay idempotent — and reports how
many are already indexed versus new, without indexing anything itself. A backend turned
off under **Settings ▸ Advanced** shows as **Disabled**; results for Lucene and
Elasticsearch are fetched independently.

### Mask 19xx tag values on import

**Settings ▸ Advanced ▸ Masking** gained a **Mask 19xx tag values (digits → 9)** checkbox,
off by default. When turned on, every digit in the value of a tag whose name starts with
`19` (e.g. `19A` Sum of Amount, `19B` Amount) is replaced with `9` at import time — the
field's format is preserved but the actual figure is not. The masking is applied once,
while a message is parsed, so it carries through everywhere that message is used
afterwards: the Entries table, Excel/CSV export, the raw/source view, and the Lucene and
Elasticsearch indexes. Already-open tabs are unaffected by a later change to the setting;
only messages imported after it's turned on are masked.

### Isolate Entry in New Tab

The MT Entries row context menu gained **Isolate Entry in New Tab**. It copies the row's
message into a new tab, keeping only that one entry and dropping every other entry
belonging to the same message — e.g. every other transaction of a multi-entry MT 536
statement, along with any unrelated financial-instrument blocks that don't carry a
transaction of their own. The original tab and message are left untouched. Useful for
pulling a single entry out of a large statement to inspect, export or share on its own.

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

### Enable/disable Lucene or Elasticsearch independently

**Settings ▸ Advanced** now has an **Enable Lucene Search** / **Enable Elasticsearch**
checkbox for each engine. Both are **disabled by default**; turning one off (or leaving
it off) hides its menu entirely — including its keyboard shortcut (`Ctrl+Shift+F` /
`Ctrl+Shift+E`) — so the menu bar doesn't offer full-text search until it's switched on
for the engine you actually want to use.

### Startup warning silenced

Removed the harmless but confusing `SLF4J: No SLF4J providers were found` message printed
to the console on every startup (an SLF4J-based library, pulled in transitively, had no
logging backend configured). MT Analyze doesn't surface logs of its own, so a no-op
provider is now bundled.

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
