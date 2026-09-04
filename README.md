# MT Analyze
**The fast way through a stack of SWIFT MT messages.**

[![Release Build](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml/badge.svg)](https://github.com/mtanalyze/mtanalyze/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.mtanalyze/mtanalyze)](https://central.sonatype.com/artifact/com.mtanalyze/mtanalyze)
[![SonarQube](https://sonarcloud.io/api/project_badges/measure?project=mtanalyze_mtanalyze&metric=alert_status)](https://sonarcloud.io/summary/overall?id=mtanalyze_mtanalyze)

An open-source desktop tool for analyzing SWIFT MT messages. Load instructions, statements and confirmations of any MT type into one table, see the ISO 15022 meaning of every field, and compare messages side by side.

![MT Analyze GUI](doc/images/v1_1_mdi.png)


---

## Getting Started

**Requirements:** Java 17 or higher.

MT Analyze is a single self-contained JAR — no installation, no admin rights.

1. Download the latest `MT-Analyze-<version>.jar` from [Releases](https://github.com/mtanalyze/mtanalyze/releases).
2. Double-click it, or run:

```bash
java -jar MT-Analyze-1.1.1.jar
```

*(Optional)* Each [Releases](https://github.com/mtanalyze/mtanalyze/releases) page shows a SHA256 digest next to the JAR asset, if you'd like to verify the download — compare it against the output of:

```bash
sha256sum MT-Analyze-<version>.jar
```
```powershell
Get-FileHash MT-Analyze-<version>.jar -Algorithm SHA256
```

## Supported MT Types

| Group                              | MT Types                   |
|------------------------------------|-----------------------------|
| Trade Confirmation and Allegement  | MT 509, 514, 515, 517, 518 |
| Transaction Processing             | MT 530                     |
| Securities Statements              | MT 535, 536, 537           |
| Settlement Instructions            | MT 540, 541, 542, 543      |
| Settlement Confirmations           | MT 544, 545, 546, 547      |
| Settlement Advise                  | MT 548                     |
| Triparty Agent                     | MT 527, 558, 569           |
| Corporate Actions                  | MT 564, 565, 566, 567, 568 |
| Settlement Allegement              | MT 578                     |
| Cash Statements                    | MT 940, 950                |

---

## Dependencies

- **[Prowide Core](https://github.com/prowide/prowide-core)** SRU2025-10.3.14 — SWIFT MT parsing (Apache 2.0)
- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)** 3.7.2 — flat look and feel with dark mode (Apache 2.0)
- **[Apache POI](https://poi.apache.org/)** 5.5.1 — Excel export (Apache 2.0)

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
  -Dartifact=com.mtanalyze:mtanalyze:1.1.1:jar:all \
  -DoutputDirectory=.

java -jar mtanalyze-1.1.1-all.jar
```
---

## Contributing

Bug reports, feature requests and pull requests are welcome. Please open an [issue](https://github.com/mtanalyze/mtanalyze/issues) first to discuss larger changes.

---

## License

Copyright 2026 Centerscout GmbH. Licensed under the [Apache License 2.0](LICENSE).

SWIFT is a registered trademark of S.W.I.F.T. SCRL. MT Analyze is an independent open source project and is not affiliated with S.W.I.F.T. SCRL.
