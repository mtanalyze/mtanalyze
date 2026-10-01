# MT Analyze
[![Release Build](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml/badge.svg)](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.mtanalyze/mtanalyze)](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze)
[![SonarQube](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=alert_status)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)

An open-source desktop tool for analyzing SWIFT MT messages.

---

## Getting Started

**Requirements:** Java 17 or higher.

MT Analyze is a single self-contained JAR — no installation, no admin rights.

1. Download the latest `MT-Analyze-<version>.jar` from [Releases](https://github.com/mtanalyze/mtanalyze/releases).
2. Double-click it, or run:

```bash
java -jar MT-Analyze-2.0.6.jar
```

### Memory Settings for Large Message Volumes

All loaded messages are kept in memory as parsed objects — roughly **16 KB of heap per message** (measured with ~40k MT 5xx messages; about 70 % of it is strings). Without `-Xmx`, the JVM caps the heap at a quarter of physical RAM (e.g. 2 GB on an 8 GB machine), which is not enough for more than about 60k messages.

| Messages | Live heap (approx.) | Recommended `-Xmx` |
|----------|---------------------|--------------------|
| 50k      | 0.8 GB              | 2g                 |
| 100k     | 1.6 GB              | 3g–4g              |
| 150k     | 2.4 GB              | 4g–5g              |

Leave at least 2–3 GB of RAM for the OS and the Lucene index (it is memory-mapped outside the heap). Example for 150k messages on an 8 GB machine:

```bash
java -Xms2g -Xmx5g -XX:+UseStringDeduplication -XX:+HeapDumpOnOutOfMemoryError -jar MT-Analyze-2.0.6.jar
```

- `-Xms2g` avoids repeated heap resizing while loading.
- `-XX:+UseStringDeduplication` shares identical string contents (tag names, qualifiers, BICs, ISINs) and noticeably reduces heap usage.
- G1 is the default collector and works well; ZGC is not recommended on machines with only 2 CPU cores.

On Windows, a `MT-Analyze.bat` next to the jar makes the settings permanent (`javaw` starts without a console window):

```bat
@echo off
start "" javaw -Xms2g -Xmx5g -XX:+UseStringDeduplication -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=%TEMP% -jar "%~dp0MT-Analyze-2.0.6.jar"
```

To check the effective settings and actual usage of a running instance:

```bash
jcmd <pid> VM.flags            # look for MaxHeapSize
jcmd <pid> GC.class_histogram  # live objects after a full GC
```

## Supported MT Types

| Group                              | MT Types                   |
|------------------------------------|-----------------------------|
| Securities Statements              | MT 535, 536, 537           |
| Settlement Instructions            | MT 540, 541, 542, 543      |
| Settlement Confirmations           | MT 544, 545, 546, 547      |
| Settlement Advise                  | MT 548                     |

---

## Dependencies

- **[Prowide Core](https://github.com/prowide/prowide-core)** SRU2025-10.3.14 — SWIFT MT parsing (Apache 2.0)
- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)** 3.7.2 — flat look and feel with dark mode (Apache 2.0)
- **[Apache POI](https://poi.apache.org/)** 5.5.1 — Excel export (Apache 2.0)
- **[Apache Lucene](https://lucene.apache.org/)** 9.12.3 — local message index and full-text search (Apache 2.0)
- **[Elasticsearch Java API Client](https://github.com/elastic/elasticsearch-java)** 9.5.3 — remote message index and full-text search (Apache 2.0)
- **[java-keyring](https://github.com/javakeyring/java-keyring)** 1.0.4 — OS credential store access for the Elasticsearch password (BSD-style)
- **[SLF4J](https://www.slf4j.org/)** 2.0.7 (`slf4j-nop`) — silences transitive libraries' logging output; MT Analyze doesn't use SLF4J logging itself (MIT)

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

## Contributing

Bug reports, feature requests and pull requests are welcome. Please open an [issue](https://github.com/mtanalyze/mtanalyze/issues) first to discuss larger changes.

---

## License

Copyright 2026 Centerscout GmbH. Licensed under the [Apache License 2.0](LICENSE).

SWIFT is a registered trademark of S.W.I.F.T. SCRL. MT Analyze is an independent open source project and is not affiliated with S.W.I.F.T. SCRL.
