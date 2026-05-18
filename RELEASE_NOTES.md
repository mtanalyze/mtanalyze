# MT Analyze v0.9.2

## Download & Run

**Requirements:** Java 17 or higher

```bash
java -jar MT-Analyze-v0.9.2.jar
```

---

## Changes

- **Java 17** — minimum Java version reduced from 21 to 17
- **Configuration file** — optional `mtanalyze.properties` next to the JAR controls max entries, log import settings, and experimental mode; documented template included in `doc/`
- **Name-Value import** — MT type is now resolved per line from its own `MT=XXX` field; files with mixed MT types are parsed correctly
- **Fix Encoding** — the encoding correction in the Paste MT Snippet dialog now also handles the `"00ä` → `"{` Mainframe prefix pattern
- **Diff View** — entry columns are now labelled "Entry 1", "Entry 2", … instead of the internal sequence name (e.g. TRAN)
- **Detail panel** — toolbar button order changed to: Tags, Components, Diff, Source, Notifications
- **About dialog** — now shows a clickable link to the GitHub repository
- **Release builds** — JAR is now built automatically via GitHub Actions and attached to each release; no pre-built binaries committed to the repository