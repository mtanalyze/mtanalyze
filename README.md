# MT Analyze
**The fast way through a stack of SWIFT MT messages.**

[![Release Build](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml/badge.svg)](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.mtanalyze/mtanalyze)](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze)
[![SonarQube](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=alert_status)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)

An open-source desktop tool for analyzing SWIFT MT messages. Load instructions, statements and confirmations of any MT type into one table, see the ISO 15022 meaning of every field, and compare messages side by side. Built for analysis, migration and UAT work in banks.

![MT Analyze GUI](doc/images/gui.gif)

---

## Getting Started

**Requirements:** Java 17 or higher.

MT Analyze is a single self-contained JAR — no installation, no admin rights.

1. Download the latest `MT-Analyze-<version>.jar` from [Releases](https://github.com/mtanalyze/mtanalyze/releases).
2. Double-click it, or run:

```bash
java -jar MT-Analyze-1.0.2.jar
```

Also available on [Maven Central](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze) for mirroring via Nexus / Artifactory.

---

## Quick Start

1. **Load** — drag MT files onto the table, or use **File → Open**. Each message becomes one row.
2. **Read** — click a row to open **Tag View**, which shows every tag with its ISO 15022 description. No SWIFT knowledge needed.
3. **Compare** — select multiple rows, then **Diff View** highlights the differences.
4. **Filter** — click the filter icon on any column, or type an expression like `=TRAD`, `^CEDE`, `!=FREE`.

---

## Features

- **Entries Table** — any mix of MT types and files in one sortable, filterable table.
- **Tag View** — full tag breakdown with ISO 15022 descriptions; enable **Components** to expand multi-part fields.
- **Diff View** — compare two or more messages; deviating cells highlighted.
- **Source View** — raw SWIFT message with colour-coded blocks and tags.
- **Filtering** — Excel-style checkbox filters and a quick expression language (`=EUR`, `^DE`, `$00`, `10-20`, `+` for OR).
- **Notes & Bookmarks** — label any row with free text; both persist across sessions.
- **Sessions** — save and restore loaded messages as a `.mtd` file.
- **Reference Search** — links related settlement messages (MT 540–548) by SEME, RELA, PREV and TRCI.
- **Import** — files, folders, log files with embedded messages, or pasted MT snippets.
- **Export** — CSV (full or component-expanded) and raw MT files.
- **Validate SWIFT File** — diagnostic report for malformed or non-standard files without loading them.
- **User Dictionary** — add your own qualifier-value descriptions (see below).

---

## Supported Message Types

MT Analyze uses the [Prowide Core](https://github.com/prowide/prowide-core) parser and supports all SWIFT MT types. The most relevant for securities post-trade are MT 527, 535–537, 540–548, 558 and 940. Files with mixed types are supported; if a type can't be detected, you'll be prompted to pick it.

---

## Configuration

Optional: place an `mtanalyze.properties` file next to the JAR to override defaults (see the template in [`doc/mtanalyze.properties`](doc/mtanalyze.properties)).

| Property | Default | Description |
|---|---|---|
| `max.entries` | `1000` | Maximum entries loaded per session |
| `log.swift.start` | `{1:` | Marks the start of a SWIFT message in log files |
| `log.newline.token` | `\n` | Newline token within a log-embedded message |
| `experimental.mode` | `false` | Enable work-in-progress features |

---

## User Dictionary

MT Analyze ships with descriptions from the **ISO 15022 Data Field Dictionary SR2025**. You can extend them with your own entries for organisation-specific codes.

The easiest way is **Settings → User Dictionary** — entries take effect immediately. You can also edit `~/.mtanalyze/user_qualifier_values.csv` directly in Excel (semicolon-delimited, UTF-8). One row per entry:

| Qualifier | Value | Description |
|-----------|-------|-------------|
| SAFE | CEDELULL | Clearstream Luxembourg |
| PSTA | MACH | Matched |

User entries override the built-in dictionary. To replace a built-in dictionary entirely, drop a same-named CSV (`dict_qualifier_values.csv`, `dict_qualifiers.csv`, `dict_tags.csv`, `dict_components.csv`) into `~/.mtanalyze/`.

---

## Dependencies

- **[Prowide Core](https://github.com/prowide/prowide-core)** SRU2025-10.3.14 — SWIFT MT parsing (Apache 2.0)
- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)** 3.7.1 — flat look and feel with dark mode (Apache 2.0)

---

## Contributing

Bug reports, feature requests and pull requests are welcome. Please open an [issue](https://github.com/mtanalyze/mtanalyze/issues) first to discuss larger changes.

---

## License

Copyright 2026 [Centerscout GmbH](https://www.centerscout.de). Licensed under the [Apache License 2.0](LICENSE).

SWIFT is a registered trademark of S.W.I.F.T. SCRL. MT Analyze is an independent open source project and is not affiliated with S.W.I.F.T. SCRL.