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
java -jar MT-Analyze-2.0.2.jar
```

## Supported MT Types

| Group                              | MT Types                   |
|------------------------------------|-----------------------------|
| Loan/Deposit Instruction           | MT 321                     |
| Netting Statement                  | MT 370                     |
| Foreign Exchange Orders            | MT 380, 381                |
| Registration                       | MT 500, 501, 519            |
| Registration Status                | MT 510                      |
| Order to Buy or Sell               | MT 502                     |
| Collateral Management              | MT 503, 504, 505, 506, 507 |
| Collateral Adjustment              | MT 581                     |
| Intra-Position Advice              | MT 508, 524, 538           |
| Client Advice of Execution         | MT 513                     |
| Securities Lending                 | MT 516, 526                |
| Trade Confirmation and Allegement  | MT 509, 514, 515, 517, 518 |
| Order and Activity Reports         | MT 575, 576                |
| Transaction Processing             | MT 530                     |
| Securities Statements              | MT 535, 536, 537           |
| Settlement Instructions            | MT 540, 541, 542, 543      |
| Settlement Confirmations           | MT 544, 545, 546, 547      |
| Settlement Advise                  | MT 548                     |
| Request for Statement/Status       | MT 549                     |
| Triparty Agent                     | MT 527, 558, 569           |
| Corporate Actions                  | MT 564, 565, 566, 567, 568 |
| Settlement Allegements             | MT 578, 586                |
| Charges and Adjustments            | MT 590, 591                |
| Request for Cancellation           | MT 592                     |
| Queries and Answers                | MT 595, 596                |
| Free Format / Proprietary Message  | MT 598, 599                |
| Standing Settlement Instructions   | MT 670, 671                |
| Cash Reports                       | MT 941, 942                |
| Cash Statements                    | MT 940, 950                |

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
