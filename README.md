# MT Analyze
[![Release Build](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml/badge.svg)](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.mtanalyze/mtanalyze)](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze)
[![SonarQube](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=alert_status)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)

An open-source desktop tool for analyzing SWIFT MT messages — from a handful of files up to large archives — and keeping them in a searchable message store. Load instructions, statements and confirmations of any of 32 supported MT types (SWIFT categories 5 and 9) into a single table, resolve the ISO 15022 meaning of every field, and compare messages side by side. Indexed messages stay queryable across tabs and sessions through two interchangeable full-text search engines: an embedded Apache Lucene index for local, file-based search, or Elasticsearch when the volume calls for a real search cluster.

Open and Save read and write the standard SWIFT RJE bulk-message format (via Prowide's `RJEReader`/`RJEWriter`), so files stay interoperable with other SWIFT tooling.

---

## Getting Started

**Requirements:** Java 17 or higher.

MT Analyze is a single self-contained JAR — no installation, no admin rights.

1. Download the latest `MT-Analyze-<version>.jar` from [Releases](https://github.com/mtanalyze/mtanalyze/releases).
2. Double-click it, or run:

```bash
java -jar MT-Analyze-2.0.2.jar
```

*(Optional)* Each [Releases](https://github.com/mtanalyze/mtanalyze/releases) page shows a SHA256 digest next to the JAR asset, if you'd like to verify the download — compare it against the output of:

```bash
sha256sum MT-Analyze-<version>.jar
```
```powershell
Get-FileHash MT-Analyze-<version>.jar -Algorithm SHA256
```

## Supported MT Types

MT Analyze parses 32 message types from SWIFT categories 5 (securities markets) and 9 (cash management) — settlement, corporate actions and cash reporting:

| Group                              | MT Types                   |
|------------------------------------|-----------------------------|
| Registration                       | MT 500, 501                |
| Trade Confirmation and Allegement  | MT 509, 514, 515, 517, 518 |
| Transaction Processing             | MT 530                     |
| Securities Statements              | MT 535, 536, 537           |
| Settlement Instructions            | MT 540, 541, 542, 543      |
| Settlement Confirmations           | MT 544, 545, 546, 547      |
| Settlement Advise                  | MT 548                     |
| Triparty Agent                     | MT 527, 558, 569           |
| Corporate Actions                  | MT 564, 565, 566, 567, 568 |
| Settlement Allegement              | MT 578                     |
| Free Format Message                | MT 599                     |
| Cash Statements                    | MT 940, 950                |

---

## Import Formats

Beyond native SWIFT text, MT Analyze recognizes and converts several other shapes
automatically — no manual pre-processing needed:

- **Name-Value** (`TAG:QUAL=VALUE;...`) and multi-line Name-Value exports.
- **Fixed-width "Append-Text" printouts** — tag, description and value in fixed columns,
  with no `:16R:`/`:16S:` markers of their own (the shape of many custodians'
  Depotumsatz-style reports). For MT 536 the sequence structure (GENL/SUBSAFE/FIN/TRAN,
  including the nested LINK and SETPRTY subsequences) is reconstructed from field
  identity, so it parses into the same one-row-per-transaction table as a native message
  instead of one flat, undifferentiated row.
- **Log files** with embedded SWIFT messages, using a configurable start marker and
  newline token (**Settings ▸ Advanced ▸ Log File Import**).
- A bare **Block 4** body with no header at all (as shown in most SWIFT/CSD specification
  examples) gets a synthetic header built automatically, with the MT type auto-detected
  from its tags where possible.

Use **File ▸ Import** or the **Paste** dialog (`Ctrl+V`) to bring any of these in.

## Import Masking

**Settings ▸ Advanced ▸ Masking** has two independent checkboxes, both off by default:

- **Mask 19xx tag values (digits → 9)** — replaces every digit in the value of a tag whose
  name starts with `19` (e.g. `19A` Sum of Amount, `19B` Amount) with `9` at import time.
- **Mask 36xx tag values (digits → 9)** — same replacement for a tag whose name starts
  with `36` (e.g. `36B` Quantity of Financial Instrument, `36D` Quantity (Digital Asset)).

Either way, the field keeps its original format but not its actual figure. Each setting
only affects messages parsed after it's turned on; already-open tabs keep their original
values. Since masking happens once, at import, it carries through everywhere that message
is used afterwards — the Entries table, Excel/CSV export, the raw/source view, and the
Lucene and Elasticsearch indexes.

---

## Entries Table

Right-click a column header, or a cell, for layout and per-column analysis:

- **Show Components** (header context menu, or the row context menu's Display section)
  switches the whole table between one column per SWIFT tag and one column per field
  component — e.g. `98A:PAYD` splits into its own `Date`, `Time`, ... columns, the same
  breakdown the Excel "Components" export uses. The Tag and Components layouts remember
  their own column order and visibility independently, so switching back and forth doesn't
  lose either one.
- **Column Statistics** (row context menu, for the column under the cursor) shows Count
  and Distinct Count for that column, plus Min/Max/Sum/Average when every visible value is
  numeric, or Min/Max when every value is a date — computed on demand, over the currently
  filtered/sorted rows only. Can be turned off under **Settings ▸ General**.

---

## Message Repository (Index & Search)

The **Lucene** and **Elasticsearch** menus each provide a full-text index of parsed
messages, in two independent engines you can use side by side — pick whichever fits the
volume at hand. Both menus offer the same five core actions (Lucene additionally has a
dedicated MT 536 transaction index, see below) and are shared across all tabs and
sessions. Each engine is **off by default** and must be turned on independently under
**Settings ▸ Lucene** / **Settings ▸ Elasticsearch** (**Enable Lucene Search** / **Enable
Elasticsearch**) — an engine left disabled has no menu and no keyboard shortcut.

### Lucene

A local, file-based index built on [Apache Lucene](https://lucene.apache.org/). No server
component — just a directory on disk.

- **Index Messages** adds all messages of the active *MT Entries* tab to the index. The
  operation runs in the background, reports progress and can be cancelled. Indexing is
  idempotent: a message is identified by a hash of its content, so re-indexing the same
  message replaces its entry instead of creating a duplicate.
- **Search Messages…** (`Ctrl+Shift+F`) executes a Lucene query and opens the result set
  in a new tab.
- **Clear Index…** removes all documents from the index.
- **Check Repository...** checks every message of the active tab against the index (by the
  same content hash used for indexing) and reports how many are already indexed versus new.
- **Statistics…** shows how many messages are currently indexed.

The index directory (default `~/.mtanalyze/swift-index`) and the maximum number of
results (default 100) are configured under **Settings ▸ Lucene**.

#### Query syntax

Queries use the classic Lucene
[`QueryParser`](https://lucene.apache.org/core/9_12_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html)
syntax (boolean operators, grouping, phrases, ranges, wildcards, negation). A term without
a `field:` prefix is matched against **`raw_message`**.

| Field | Contents | Matching |
|---|---|---|
| `raw_message` | the complete message text | analyzed, lowercased |
| `mt` | e.g. `536` | verbatim, case-sensitive |
| `sender` / `receiver` | LT address, e.g. `BANKUS33AXXX` | verbatim, case-sensitive |
| `file_name` | source label (tab title) | verbatim |
| `tag_<name>` | one SWIFT tag value, e.g. `tag_20C`, `tag_35B` | analyzed; split on `:` `/` `,` and whitespace, lowercased |
| `tags_all` | every indexed tag value combined | analyzed |

Only content-bearing tags are indexed, namely those whose name starts with `20`, `35`,
`70`, `94`, `95`, `97` or `98` (references, instrument/ISIN, narratives, places, parties,
accounts, dates). Structural tags (`16R`/`16S`, `23G`, `22F`, …) are not indexed.

Examples:

```
mt:536 AND tag_35B:US0378331005
raw_message:"APPLE INC"
sender:BANKUS33AXXX
tag_20C:seme AND mt:(536 OR 537)
tag_98A:[20210101 TO 20211231] NOT tag_23G:CANC
```

#### MT 536 transaction index

Alongside the general-purpose index above, the Lucene menu has three actions dedicated to
MT 536 statements:

- **Index MT 536 Entries** isolates every MT 536 row of the active tab down to its own
  single-transaction message — the same pruning **Isolate Entry in New Tab** uses, dropping
  every other `TRAN`/`TRANSDET` block of the statement — and indexes each isolated
  transaction into its own index (default `~/.mtanalyze/swift-index-mt536`), kept separate
  from the general-purpose one so a transaction-level search never mixes with whole-message
  hits.
- **Search MT 536 Entries…** opens a structured mask — Reference (`20C`), ISIN (`35B`),
  Settlement/Trade Date (`98A`), Safekeeping Account (`97A`), Narrative (`70E`) and
  Sender/Receiver BIC — plus the same free-form Lucene query box as **Search Messages**,
  for anything the structured fields don't cover. Any combination of fields and/or query
  can be filled in; everything given is ANDed together. Matching transactions load into a
  new tab.
- **Clear MT 536 Index…** removes all documents from the MT 536 index.

### Elasticsearch

A remote/local [Elasticsearch](https://www.elastic.co/elasticsearch) index for message
volumes beyond what a single desktop's file-based Lucene index comfortably handles —
point MT Analyze at a cluster you run or manage.

- **Index Messages** adds all messages of the active *MT Entries* tab to the configured
  index. Same progress bar / cancel and content-hash idempotency as Lucene.
- **Search Messages…** (`Ctrl+Shift+E`) executes an Elasticsearch `query_string` query
  (close to Lucene's syntax) and opens the result set in a new tab.
- **Clear Index…** removes all documents from the index.
- **Check Repository...** checks every message of the active tab against the index (by the
  same content hash used for indexing) and reports how many are already indexed versus new.
  This item and its Lucene counterpart open the same dialog, which checks each backend
  independently.
- **Statistics…** shows how many messages are currently indexed. This item and its Lucene
  counterpart open the same dialog, which fetches both counts independently so a
  slow/unreachable Elasticsearch cluster never delays the Lucene count from showing. A
  backend that's turned off shows as **Disabled** instead of being queried.

Host, port, scheme, credentials, index name (default `swift-messages`) and the maximum
number of results (default 100) are configured under **Settings ▸ Elasticsearch**. Unlike
Lucene this is a real server connection — indexing and searching
report a network error if the cluster is unreachable.

The basic-auth password is stored in the OS credential store (Windows Credential Manager,
macOS Keychain, or the Freedesktop Secret Service/KWallet on Linux) via
[java-keyring](https://github.com/javakeyring/java-keyring), not in plain text alongside
the other settings; it falls back to a plain-text preference only on systems without a
supported OS keyring backend.

---

## Dependencies

- **[Prowide Core](https://github.com/prowide/prowide-core)** SRU2025-10.3.14 — SWIFT MT parsing (Apache 2.0)
- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)** 3.7.2 — flat look and feel with dark mode (Apache 2.0)
- **[Apache POI](https://poi.apache.org/)** 5.5.1 — Excel export (Apache 2.0)
- **[Apache Lucene](https://lucene.apache.org/)** 9.12.3 — local message index and full-text search (Apache 2.0)
- **[Elasticsearch Java API Client](https://github.com/elastic/elasticsearch-java)** 9.5.3 — remote message index and full-text search (Apache 2.0)
- **[java-keyring](https://github.com/javakeyring/java-keyring)** 1.0.4 — OS credential store access for the Elasticsearch password (BSD-style)
- **[SLF4J](https://www.slf4j.org/)** 2.0.7 (`slf4j-nop`) — silences transitive libraries' logging output; MT Analyze doesn't use SLF4J logging itself (MIT)

The full transitive dependency graph (SBOM), including versions pulled in indirectly, is visible on GitHub's [Dependency graph](https://github.com/mtanalyze/mtanalyze/network/dependencies). A standalone [CycloneDX](https://cyclonedx.org/) SBOM file can be generated locally with the optional `sbom` profile:

```bash
mvn -P sbom package
```

Written to `target/bom.json`.

---

## Build from Source

**Requirements:** JDK 17+, Maven 3.9+.

```bash
git clone https://github.com/mtanalyze/mtanalyze.git
cd mtanalyze
mvn clean package
```

The self-contained jar is produced at `target/MT-Analyze-<version>.jar`.

A local NVD vulnerability scan of all dependencies can be run with the optional `owasp` profile (requires a free [NVD API key](https://nvd.nist.gov/developers/request-an-api-key)):

```bash
mvn -P owasp verify -Dnvd.api.key=<your-key>
```

The report is written to `target/dependency-check-report.html`.

---

## Use it from Maven Central

MT Analyze is published on [Maven Central](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze) — every release is GPG-signed and passes Sonatype's Central Publisher validation before it goes live, so you can pull the jar straight from Central instead of trusting a random download link.

```bash
mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy \
  -Dartifact=com.mtanalyze:mtanalyze:2.0.2:jar:all \
  -DoutputDirectory=.

java -jar mtanalyze-2.0.2-all.jar
```
---

## Contributing

Bug reports, feature requests and pull requests are welcome. Please open an [issue](https://github.com/mtanalyze/mtanalyze/issues) first to discuss larger changes.

---

## License

Copyright 2026 Centerscout GmbH. Licensed under the [Apache License 2.0](LICENSE).

SWIFT is a registered trademark of S.W.I.F.T. SCRL. MT Analyze is an independent open source project and is not affiliated with S.W.I.F.T. SCRL.
