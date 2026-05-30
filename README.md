# MT Analyze
**The fast way through a stack of SWIFT MT messages.**

[![Release Build](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml/badge.svg)](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.mtanalyze/mtanalyze)](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze)
[![SonarQube](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=alert_status)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)

An open-source desktop tool for analyzing SWIFT MT messages. Load instructions, statements and confirmations of any MT type into one table, see the ISO 15022 meaning of every field, and compare messages side by side.

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

> [!IMPORTANT]
> **CSV & regional settings.** Exported CSV files use `;` as the field separator and `.` as the decimal separator by default — the convention in many European countries where `,` is the decimal mark. If your regional settings (e.g. UK/US English) use `,` as the list separator, columns won't split correctly in Excel. Change the **Field separator** to `,` under **Settings → General → CSV Export** so the file matches your locale.

---

## Features

- **Entries Table** — any mix of MT types and files in one sortable, filterable table.
- **Tag View** — full tag breakdown with ISO 15022 descriptions; enable **Components** to expand multi-part fields.
- **Diff View** — compare two or more messages; deviating cells highlighted.
- **Source View** — raw SWIFT message with colour-coded blocks and tags.

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
| `mt.export.sender` | _(empty)_ | Default sender BIC stamped into exported MT messages (also editable in Settings) |
| `mt.export.receiver` | _(empty)_ | Default receiver BIC stamped into exported MT messages (also editable in Settings) |

---

## Dependencies

- **[Prowide Core](https://github.com/prowide/prowide-core)** SRU2025-10.3.14 — SWIFT MT parsing (Apache 2.0)
- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)** 3.7.1 — flat look and feel with dark mode (Apache 2.0)
- **[Apache POI](https://poi.apache.org/)** 5.3.0 — Excel (XLSX) export (Apache 2.0)

---

## About the Developer

MT Analyze is built and maintained by **Ralf Schwarz**, an independent banking-IT consultant and business analyst specialising in securities trading and settlement systems for international banks and asset managers.

It is a personal open-source project, developed independently in the author's own time and not connected to any client engagement or employer.

Get in touch on **[LinkedIn](https://www.linkedin.com/in/ralfschwarz/)**.

---

## Contributing

Bug reports, feature requests and pull requests are welcome. Please open an [issue](https://github.com/mtanalyze/mtanalyze/issues) first to discuss larger changes.

---

## License

Copyright 2026 Ralf Schwarz. Licensed under the [Apache License 2.0](LICENSE).

SWIFT is a registered trademark of S.W.I.F.T. SCRL. MT Analyze is an independent open source project and is not affiliated with S.W.I.F.T. SCRL.
