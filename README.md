# MT Analyze
**SWIFT MT analysis for migration and change projects — and day-to-day business use.**

[![Release Build](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml/badge.svg)](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml)
[![SonarQube](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=alert_status)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=security_rating)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)

MT Analyze is a lightweight open-source desktop tool for analysing SWIFT MT messages (ISO 15022) and posting data. It looks like a spreadsheet — sort, filter, copy & paste. Inside it natively understands SWIFT MT: tags, qualifiers, components and sequences are decoded and annotated with descriptions from the ISO 15022 Data Field Dictionary SR2025, built in. No access to myStandards required. No prior SWIFT MT knowledge needed.

Statement messages such as MT 536 or MT 940 — which carry many entries per message — and single-entry messages such as settlement instructions or confirmations are handled uniformly: any mix loads into one table, one row per entry, ready to sort, filter, and compare across the full dataset.

Built on [Prowide Core](https://github.com/prowide/prowide-core). Minimal dependencies. Self-contained JAR — no installation, no admin rights.

---

## Screenshots

**Entries table with Tag View**
![Tag View](doc/images/tag_view.png)

**Entries table with Components View**
![Tag View](doc/images/component_view.png)


**Entries table with Diff View**
![Tag View](doc/images/diff_view.png)


**Entries table with Source View**
![Source View](doc/images/source_view.png)

---

## Getting Started

**Requirements:** Java 17 or higher.

MT Analyze is a single self-contained JAR — all libraries are bundled inside. No installation, no admin rights required. An optional `mtanalyze.properties` file placed next to the JAR controls advanced settings (see [Configuration](#configuration)).

1. Go to [Releases](https://github.com/mtanalyze/mtanalyze/releases)
2. Download the latest `MT-Analyze-<version>.jar`
3. Double-click the JAR, or run:

```bash
java -jar MT-Analyze-1.0.0.jar
```


---

## Features

- **Entries Table** — FIN objects from any mix of MT types and files loaded into a single sortable, filterable table. Each row is one FIN object; columns are the decoded tag/qualifier fields.
- **Tag View** (`Ctrl+4`) — full tag breakdown of a selected message: sequence, tag name, qualifier, and decoded value, with optional Component View expanding multi-component fields.
- **Diff View** (`Ctrl+5`) — select two or more rows to compare FIN objects side by side; deviating cells are highlighted. Toggle "Differences only" to restrict to changed fields.
- **Source View** (`Ctrl+6`) — raw SWIFT MT message with colour-coded block delimiters, tags, qualifiers, and indented sequence blocks.
- **Filtering** — Filter Row (Excel-style checkbox list per column) and Quick Filter (expression language: `=EUR`, `!=`, `^DE`, `$00`, `10-20`, `+` for OR). Filters combine as AND or OR. Right-click any filter button to convert to Quick Filter or clear all column filters in one step.
- **File Explorer** — collapsible folder tree panel; drag a folder to add it as a root, multi-select files to open them at once (`Ctrl+E`).
- **Sessions** — save and restore a set of loaded messages as a `.mtd` file (plain SWIFT MT text).
- **Reference Search** — resolves linked settlement messages (MT 540–548) by SEME, RELA, PREV, and TRCI reference fields.
- **Bookmarks** — right-click any row to bookmark it with a note; bookmarks persist across sessions.
- **Log File Import** — load SWIFT messages embedded in application log files; multi-line messages are reassembled automatically.
- **Paste & Parse** — paste raw SWIFT text directly via **Edit → Paste MT Snippet**. Includes a Fix Encoding button that corrects Mainframe EBCDIC CP273 mis-conversions (`ä→{`, `ü→}`).
- **Export** — CSV (full or component-expanded), and raw MT file export with configurable BIC headers.
- **User Dictionary** — custom qualifier-value descriptions that extend the built-in ISO 15022 dictionary.
- **Account Mapping** — lookup table mapping SAFE identifiers to account numbers and descriptions.

---

## Supported Message Types

MT Analyze uses the [Prowide Core](https://github.com/prowide/prowide-core) parser and supports the full range of SWIFT MT message types. The types most relevant to securities post-trade are:

| Type       | Description                                      |
|------------|--------------------------------------------------|
| MT 527     | Triparty Collateral Instruction                  |
| MT 535     | Statement of Holdings                            |
| MT 536     | Statement of Transactions                        |
| MT 537     | Statement of Pending Transactions                |
| MT 540–543 | Settlement Instructions                          |
| MT 544–547 | Settlement Confirmations                         |
| MT 548     | Settlement Status and Processing Advice          |
| MT 558     | Triparty Collateral Status and Processing Advice |
| MT 940     | Customer Statement Message                       |

Files containing multiple message types are supported. If the message type cannot be detected automatically, MT Analyze will prompt the user to select it.

---

## Configuration

Place an `mtanalyze.properties` file next to the JAR to override defaults. All settings are optional.

| Property | Default | Description |
|---|---|---|
| `max.entries` | `1000` | Maximum entries loaded per session |
| `log.swift.start` | `{1:` | String marking the start of an embedded SWIFT message in log files |
| `log.newline.token` | `\n` | Token representing a newline within a log-embedded message |
| `experimental.mode` | `false` | Enable work-in-progress features |

A documented template is available in [`doc/mtanalyze.properties`](doc/mtanalyze.properties).

---

## Dependencies

Two dependencies. That is all.

- **[Prowide Core](https://github.com/prowide/prowide-core)** SRU2025-10.3.14 — SWIFT MT message parsing (Apache License 2.0)
- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)** 3.7.1 — modern flat look and feel with dark mode support (Apache License 2.0)

---

## Security

Every release ships:
- **Automated release build** — JAR is built from source via GitHub Actions on each release; no pre-built binaries committed to the repository
- **SonarCloud** scan results (badges above)
- **GitHub Dependabot** — automated dependency vulnerability alerts and pull requests

---

## Contributing

Bug reports, feature requests, and pull requests are welcome. Please open an [issue](https://github.com/mtanalyze/mtanalyze/issues) first to discuss larger changes.

---

## Trademarks

SWIFT is a registered trademark of S.W.I.F.T. SCRL. MT Analyze is an independent open source project and is not affiliated with, sponsored by, or endorsed by S.W.I.F.T. SCRL or any other organisation mentioned in this documentation.

---

## License

Copyright 2026 [Centerscout GmbH](https://www.centerscout.de)

Licensed under the [Apache License 2.0](LICENSE).
